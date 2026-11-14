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
    args = parser.parse_args()

    with open(args.manifest, encoding="utf-8") as handle:
        data = json.load(handle)

    data["patch"] = {
        "fromVersionCode": args.from_code,
        "toVersionCode": args.to_code,
        "fromVersionName": args.from_version,
        "toVersionName": args.to_version or data.get("versionName", ""),
        "url": os.path.basename(args.patch),
        "size": os.path.getsize(args.patch),
        "sha256": sha256(args.patch),
    }
    data["changelogUrl"] = args.changelog_url
    with open(args.manifest, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(f"Patch in {args.manifest} eingetragen: {os.path.basename(args.patch)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
