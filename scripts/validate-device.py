#!/usr/bin/env python3
"""Validates one /devices registration file before auto-merge.

The workflow only calls this with the content of a single *added* file from a
PR. This script is the second gate: strict schema, no secrets, no path tricks
and a size limit.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

ALLOWED = {
    "schema",
    "deviceId",
    "owner",
    "label",
    "manufacturer",
    "model",
    "android",
    "appVersion",
    "versionCode",
    "registeredAt",
    "lastSeen",
    "settings",
}
REQUIRED = {
    "schema",
    "deviceId",
    "owner",
    "label",
    "manufacturer",
    "model",
    "android",
    "appVersion",
    "versionCode",
    "registeredAt",
}
SECRET_MARKERS = ("token", "secret", "password", "keystore", "privatekey", "apikey", "authorization")
SLUG = re.compile(r"^[a-z0-9][a-z0-9._-]{2,80}$")
OWNER = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
MAX_BYTES = 24 * 1024


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def check_text(name: str, value, max_length: int = 80) -> None:
    if not isinstance(value, str) or not value.strip():
        fail(f"{name} fehlt oder ist kein Text.")
    if len(value) > max_length:
        fail(f"{name} ist länger als {max_length} Zeichen.")
    if any(ch in value for ch in "\r\n\x00"):
        fail(f"{name} enthält Steuerzeichen.")


def walk(value, path: str = "") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            lowered = key.lower()
            if any(marker in lowered for marker in SECRET_MARKERS):
                fail(f"Feld '{path}{key}' sieht nach einem Geheimnis aus.")
            walk(child, f"{path}{key}.")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk(child, f"{path}{index}.")
    elif isinstance(value, str) and len(value) > 4000:
        fail(f"Textfeld '{path}' ist zu lang.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("file")
    parser.add_argument("--filename", default="")
    args = parser.parse_args()

    raw = open(args.file, "rb").read()
    if len(raw) > MAX_BYTES:
        fail(f"Datei ist größer als {MAX_BYTES} Bytes.")
    try:
        data = json.loads(raw.decode("utf-8"))
    except Exception as error:  # noqa: BLE001
        fail(f"Ungültiges JSON: {error}")
    if not isinstance(data, dict):
        fail("Die Device-Datei muss ein JSON-Objekt sein.")

    unknown = set(data) - ALLOWED
    if unknown:
        fail(f"Unbekannte Felder: {', '.join(sorted(unknown))}")
    missing = REQUIRED - set(data)
    if missing:
        fail(f"Fehlende Felder: {', '.join(sorted(missing))}")
    if data.get("schema") != 1:
        fail("schema muss 1 sein.")

    check_text("deviceId", data["deviceId"])
    check_text("owner", data["owner"], 39)
    check_text("label", data["label"])
    check_text("manufacturer", data["manufacturer"])
    check_text("model", data["model"])
    check_text("android", data["android"], 24)
    check_text("appVersion", data["appVersion"], 40)
    check_text("registeredAt", data["registeredAt"], 40)
    if "lastSeen" in data:
        check_text("lastSeen", data["lastSeen"], 40)

    slug = data["deviceId"]
    if not SLUG.match(slug):
        fail("deviceId darf nur a-z, 0-9, Punkt, Unterstrich und Bindestrich enthalten.")
    if not OWNER.match(data["owner"]):
        fail("owner ist kein gültiger GitHub-Login.")
    if not isinstance(data["versionCode"], int) or data["versionCode"] <= 0:
        fail("versionCode muss eine positive Ganzzahl sein.")
    if "settings" in data and not isinstance(data["settings"], dict):
        fail("settings muss ein Objekt sein.")

    filename = args.filename or os.path.basename(args.file)
    if os.path.dirname(filename):
        filename = os.path.basename(filename)
    if filename != f"{slug}.json":
        fail(f"Dateiname muss '{slug}.json' sein, nicht '{filename}'.")
    expected_prefix = data["owner"].lower().replace("_", "-") + "-"
    if not slug.startswith(expected_prefix):
        fail(f"deviceId muss mit '{expected_prefix}' beginnen.")

    walk(data)
    print(f"Device-Datei gültig: {slug} (@{data['owner']}, {data['model']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
