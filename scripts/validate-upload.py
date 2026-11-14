#!/usr/bin/env python3
"""Validates an upload directory before a theme PR can be approved."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys

SLUG = re.compile(r"^[a-z0-9][a-z0-9-]{2,60}$")
IMAGE_MAGIC = (b"\x89PNG\r\n\x1a\n", b"\xff\xd8\xff")


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", required=True)
    parser.add_argument("--folder", required=True, choices=["themes", "app-themes"])
    args = parser.parse_args()

    meta_path = os.path.join(args.dir, "meta.json" if args.folder == "themes" else "theme.json")
    if not os.path.isfile(meta_path):
        fail(f"Metadaten fehlen: {meta_path}")
    meta = json.load(open(meta_path, encoding="utf-8"))
    expected_kind = "theme" if args.folder == "themes" else "app-theme"
    if meta.get("kind") != expected_kind:
        fail(f"kind muss '{expected_kind}' sein.")
    slug = meta.get("slug", "")
    if not SLUG.match(slug):
        fail("Ungültiger slug.")
    if os.path.basename(os.path.abspath(args.dir)) != slug:
        fail("Ordnername muss dem slug entsprechen.")
    title = meta.get("title") or meta.get("name") or ""
    if not 3 <= len(title) <= 80:
        fail("Titel/Name braucht 3–80 Zeichen.")
    author = meta.get("author", "")
    if not author:
        fail("author fehlt.")

    allowed = {"meta.json", "theme.json", "theme.mtz", "preview.png", "preview.jpg"}
    actual = set(os.listdir(args.dir))
    unknown = actual - allowed
    if unknown:
        fail(f"Nicht erlaubte Dateien: {', '.join(sorted(unknown))}")

    if args.folder == "themes":
        theme_path = os.path.join(args.dir, "theme.mtz")
        if not os.path.isfile(theme_path):
            fail("theme.mtz fehlt.")
        size = os.path.getsize(theme_path)
        if size <= 0 or size > 320 * 1024 * 1024:
            fail("Theme-Größe ungültig (max. 320 MiB).")
        if meta.get("size") != size:
            fail("meta.size passt nicht zur Datei.")
        if meta.get("sha256", "").lower() != sha256(theme_path):
            fail("meta.sha256 passt nicht zur Datei.")
    else:
        if not isinstance(meta.get("colors"), dict) or not meta["colors"]:
            fail("colors fehlt.")
        for key, value in meta["colors"].items():
            if not re.match(r"^#[0-9A-Fa-f]{6}$", str(value)):
                fail(f"Farbe '{key}' ist kein #RRGGBB-Wert.")
        preview_path = os.path.join(os.path.dirname(meta_path), "preview.png")
        if not os.path.isfile(preview_path):
            fail("preview.png fehlt.")

    preview = meta.get("preview")
    if preview:
        preview_path = os.path.join(args.dir, str(preview))
        if not os.path.isfile(preview_path):
            fail(f"Preview fehlt: {preview}")
        if os.path.getsize(preview_path) > 3 * 1024 * 1024:
            fail("Preview ist größer als 3 MiB.")
        head = open(preview_path, "rb").read(8)
        if not any(head.startswith(magic) for magic in IMAGE_MAGIC):
            fail("Preview ist kein PNG/JPEG.")

    print(f"Upload gültig: {args.folder}/{slug} ({title})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
