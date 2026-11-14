#!/usr/bin/env python3
"""Creates a content-defined delta between two APKs.

Format ``MTZPATCH1``::

    magic (9 bytes) | header length (uint32 BE) | UTF-8 JSON header |
    zlib( opcode* )

    opcode 0x01 COPY: varint oldOffset, varint length
    opcode 0x02 ADD:  varint length, raw bytes

Chunks are content-defined (gear rolling hash), so unchanged code and
resources are found even when earlier parts of the APK moved. The resulting
``.patch`` is downloaded by the app, applied to its installed base APK and
verified against the target SHA-256 before installation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import struct
import sys
import zlib
from datetime import datetime, timezone

MAGIC = b"MTZPATCH1"
MIN_CHUNK = 4 * 1024
MAX_CHUNK = 128 * 1024
MASK = (1 << 14) - 1  # average ~16 KiB
GEAR = [random.Random(0x4D545A + i).getrandbits(64) for i in range(256)]


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def chunks(data: bytes):
    start = 0
    rolling = 0
    for index, byte in enumerate(data):
        rolling = ((rolling << 1) + GEAR[byte]) & 0xFFFFFFFFFFFFFFFF
        size = index - start + 1
        if size >= MIN_CHUNK and ((rolling & MASK) == 0 or size >= MAX_CHUNK):
            yield start, data[start : index + 1]
            start = index + 1
            rolling = 0
    if start < len(data):
        yield start, data[start:]


def digest(chunk: bytes) -> bytes:
    return hashlib.blake2b(chunk, digest_size=16).digest()


def varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def build(old: bytes, new: bytes):
    index: dict[bytes, int] = {}
    for offset, chunk in chunks(old):
        index.setdefault(digest(chunk), offset)

    body = bytearray()
    copy_bytes = 0
    add_bytes = 0
    ops = 0
    for _, chunk in chunks(new):
        where = index.get(digest(chunk))
        if where is not None:
            body.append(0x01)
            body += varint(where)
            body += varint(len(chunk))
            copy_bytes += len(chunk)
        else:
            body.append(0x02)
            body += varint(len(chunk))
            body += chunk
            add_bytes += len(chunk)
        ops += 1
    return bytes(body), ops, copy_bytes, add_bytes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("old_apk")
    parser.add_argument("new_apk")
    parser.add_argument("output")
    parser.add_argument("--from-version", default="")
    parser.add_argument("--to-version", default="")
    parser.add_argument("--from-code", type=int, default=0)
    parser.add_argument("--to-code", type=int, default=0)
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()

    old = open(args.old_apk, "rb").read()
    new = open(args.new_apk, "rb").read()
    if not old or not new:
        raise SystemExit("Leere APK übergeben.")

    body, ops, copy_bytes, add_bytes = build(old, new)
    compressed = zlib.compress(body, 9)
    header = {
        "format": 1,
        "fromVersionName": args.from_version,
        "toVersionName": args.to_version,
        "fromVersionCode": args.from_code,
        "toVersionCode": args.to_code,
        "fromSha256": hashlib.sha256(old).hexdigest(),
        "toSha256": hashlib.sha256(new).hexdigest(),
        "baseSize": len(old),
        "toSize": len(new),
        "ops": ops,
        "copyBytes": copy_bytes,
        "addBytes": add_bytes,
        "createdAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    header_bytes = json.dumps(header, separators=(",", ":")).encode("utf-8")
    os.makedirs(os.path.dirname(os.path.abspath(args.output)) or ".", exist_ok=True)
    with open(args.output, "wb") as handle:
        handle.write(MAGIC)
        handle.write(struct.pack(">I", len(header_bytes)))
        handle.write(header_bytes)
        handle.write(compressed)

    patch_size = os.path.getsize(args.output)
    stats = {
        "patch": args.output,
        "patchSize": patch_size,
        "newSize": len(new),
        "savedPercent": round(100 - patch_size * 100 / len(new), 2),
        **header,
    }
    if args.json_out:
        open(args.json_out, "w", encoding="utf-8").write(json.dumps(stats, indent=2))
    print(json.dumps(stats, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
