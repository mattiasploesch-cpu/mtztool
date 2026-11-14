#!/usr/bin/env python3
"""Adds a generated patch and changelog URL to dist/update.json."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest")
    parser.add_argument("patch")
    parser.add_argument("--from-code", type=int, required=True)
    parser.add_argument("--to-code", type=int, required=True)
    parser.add_argument("--from-version", default="")
    parser.add_argument("--to-version", default="")
    parser.add_argument("--changelog-url", default="changelog.json")
    parser.add_argument(
        "--base-url",
        default="",
        help="Absolute release asset base URL; makes patch.url work in old app builds too",
    )
    args = parser.parse_args()

    with open(args.manifest, encoding="utf-8") as handle:
        data = json.load(handle)

    patch_name = os.path.basename(args.patch)
    patch_url = (
        f"{args.base_url.rstrip('/')}/{patch_name}"
        if args.base_url
        else patch_name
    )
    changelog_url = (
        f"{args.base_url.rstrip('/')}/{args.changelog_url}"
        if args.base_url and not args.changelog_url.startswith("http")
        else args.changelog_url
    )
    data["patch"] = {
        "fromVersionCode": args.from_code,
        "toVersionCode": args.to_code,
        "fromVersionName": args.from_version,
        "toVersionName": args.to_version or data.get("versionName", ""),
        "url": patch_url,
        "size": os.path.getsize(args.patch),
        "sha256": sha256(args.patch),
    }
    data["changelogUrl"] = changelog_url
    with open(args.manifest, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(f"Patch in {args.manifest} eingetragen: {os.path.basename(args.patch)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
