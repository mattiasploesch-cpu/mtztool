#!/usr/bin/env python3
"""After approval, copies an upload into the canonical folder and adds JSON.

Runs in GitHub Actions with GH_TOKEN set. It never executes PR code: all
files are read through the contents API and only data files are written.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys


def gh(*args: str) -> str:
    return subprocess.check_output(["gh", *args], text=True).strip()


def api(path: str) -> dict:
    return json.loads(gh("api", path))


def download(repo: str, path: str, ref: str) -> bytes:
    data = api(f"repos/{repo}/contents/{path}?ref={ref}")
    return base64.b64decode(data["content"])


def put(repo: str, path: str, data: bytes, branch: str, message: str) -> None:
    payload = base64.b64encode(data).decode()
    try:
        existing = api(f"repos/{repo}/contents/{path}?ref={branch}")
        sha = existing.get("sha")
    except subprocess.CalledProcessError:
        sha = None
    args = [
        "api", "--method", "PUT", f"repos/{repo}/contents/{path}",
        "-f", f"message={message}",
        "-f", f"content={payload}",
        "-f", f"branch={branch}",
    ]
    if sha:
        args += ["-f", f"sha={sha}"]
    gh(*args)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--pr", required=True)
    parser.add_argument("--folder", required=True, choices=["themes", "app-themes"])
    args = parser.parse_args()

    pr = api(f"repos/{args.repo}/pulls/{args.pr}")
    branch = pr["head"]["ref"]
    files = api(f"repos/{args.repo}/pulls/{args.pr}/files?per_page=100")
    names = [entry["filename"] for entry in files]
    prefix = f"{args.folder}/uploads/"
    if not names or any(not name.startswith(prefix) for name in names):
        raise SystemExit("PR enthält Dateien außerhalb des Upload-Ordners.")

    meta_name = "meta.json" if args.folder == "themes" else "theme.json"
    meta_path = next(name for name in names if name.endswith(meta_name))
    upload_dir = os.path.dirname(meta_path)
    meta = json.loads(download(args.repo, meta_path, branch))
    slug = meta["slug"]

    if args.folder == "themes":
        theme_path = f"{upload_dir}/theme.mtz"
        theme = download(args.repo, theme_path, branch)
        put(args.repo, f"themes/{slug}.mtz", theme, branch, f"theme: publish {slug}")
        preview_name = meta.get("preview")
        preview_url = None
        if preview_name:
            preview = download(args.repo, f"{upload_dir}/{preview_name}", branch)
            ext = "jpg" if str(preview_name).endswith(".jpg") else "png"
            put(args.repo, f"themes/previews/{slug}.{ext}", preview, branch,
                f"theme: preview {slug}")
            preview_url = f"themes/previews/{slug}.{ext}"
        final = {
            "schema": 1,
            "kind": "theme",
            "slug": slug,
            "name": meta.get("title", slug),
            "title": meta.get("title", slug),
            "author": meta.get("author", ""),
            "description": meta.get("description", ""),
            "fileName": f"{slug}.mtz",
            "downloadUrl": f"themes/{slug}.mtz",
            "previewUrl": preview_url,
            "size": len(theme),
            "sha256": meta.get("sha256", ""),
            "approvedAt": meta.get("submittedAt", ""),
        }
        target = f"themes/{slug}.json"
    else:
        colors = meta.get("colors", {})
        preview = download(args.repo, f"{upload_dir}/preview.png", branch)
        put(args.repo, f"app-themes/previews/{slug}.png", preview, branch,
            f"app-theme: preview {slug}")
        final = {
            "schema": 1,
            "kind": "app-theme",
            "slug": slug,
            "name": meta.get("name", slug),
            "author": meta.get("author", ""),
            "description": meta.get("description", ""),
            "colors": colors,
            "previewUrl": f"app-themes/previews/{slug}.png",
        }
        target = f"app-themes/{slug}.json"

    put(args.repo, target, json.dumps(final, indent=2).encode(), branch,
        f"theme: publish metadata {slug}")
    print(f"Veröffentlicht: {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
