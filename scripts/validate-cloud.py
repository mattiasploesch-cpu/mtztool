#!/usr/bin/env python3
"""Validates the files in a device-cloud backup or restore pull request."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

STATE_MAX = 256 * 1024
LOG_MAX = 96 * 1024
RESTORE_MAX = 24 * 1024
SECRET_MARKERS = ("token", "secret", "password", "keystore", "privatekey", "apikey", "authorization")
SECRET_PATTERNS = (
    re.compile(r"gh[pousr]_[A-Za-z0-9_]{20,}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
)
SLUG = re.compile(r"^[a-z0-9][a-z0-9._-]{2,80}$")
STAMP = re.compile(r"^[0-9]{8}-[0-9]{6}$")


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def check_text(name: str, value, max_length: int = 4000) -> None:
    if not isinstance(value, str) or not value.strip():
        fail(f"{name} fehlt oder ist kein Text.")
    if len(value) > max_length:
        fail(f"{name} ist länger als {max_length} Zeichen.")
    if any(ch in value for ch in "\r\n\x00"):
        fail(f"{name} enthält Steuerzeichen.")


def check_no_secrets(value, path: str = "") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            lowered = key.lower()
            if any(marker in lowered for marker in SECRET_MARKERS):
                fail(f"Feld '{path}{key}' sieht nach einem Geheimnis aus.")
            check_no_secrets(child, f"{path}{key}.")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            check_no_secrets(child, f"{path}{index}.")
    elif isinstance(value, str):
        for pattern in SECRET_PATTERNS:
            if pattern.search(value):
                fail(f"Textfeld '{path}' enthält ein Token oder einen privaten Schlüssel.")
        if len(value) > 4000:
            fail(f"Textfeld '{path}' ist zu lang.")


def load_json(path: Path, max_bytes: int) -> dict:
    raw = path.read_bytes()
    if len(raw) > max_bytes:
        fail(f"{path.name} ist größer als {max_bytes} Bytes.")
    try:
        data = json.loads(raw.decode("utf-8"))
    except Exception as error:  # noqa: BLE001
        fail(f"{path.name} ist kein gültiges JSON: {error}")
    if not isinstance(data, dict):
        fail(f"{path.name} muss ein JSON-Objekt sein.")
    return data


def validate_device(data: dict) -> None:
    device = data.get("device")
    if not isinstance(device, dict):
        fail("state.json: device muss ein Objekt sein.")
    check_text("device.deviceId", device.get("deviceId"), 81)
    check_text("device.owner", device.get("owner"), 39)
    check_text("device.label", device.get("label"), 80)
    if not SLUG.match(device["deviceId"]):
        fail("state.json: ungültige deviceId.")
    if not isinstance(data.get("settings"), dict):
        fail("state.json: settings muss ein Objekt sein.")
    if not isinstance(data.get("logs"), str):
        fail("state.json: logs muss Text sein.")
    if len(data["logs"]) > LOG_MAX:
        fail("state.json: logs ist zu groß.")
    check_text("state.updatedAt", data.get("updatedAt"), 40)
    if data.get("schema") != 1:
        fail("state.json: schema muss 1 sein.")
    check_no_secrets(device, "device.")
    check_no_secrets(data.get("settings"), "settings.")
    for pattern in SECRET_PATTERNS:
        if pattern.search(data["logs"]):
            fail("state.json: logs enthält ein Token oder einen privaten Schlüssel.")


def validate_log(path: Path) -> None:
    raw = path.read_bytes()
    if len(raw) > LOG_MAX:
        fail(f"{path.name} ist größer als {LOG_MAX} Bytes.")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        fail(f"{path.name} ist kein UTF-8: {error}")
    for pattern in SECRET_PATTERNS:
        if pattern.search(text):
            fail(f"{path.name} enthält ein Token oder einen privaten Schlüssel.")


def validate_restore(data: dict) -> None:
    for key in ("schema", "device", "sourceDevice", "sourceLabel", "restoredAt", "appVersion", "versionCode"):
        if key not in data:
            fail(f"Restore-Marker: Feld '{key}' fehlt.")
    if data.get("schema") != 1:
        fail("Restore-Marker: schema muss 1 sein.")
    check_text("restore.device", data["device"], 81)
    check_text("restore.sourceDevice", data["sourceDevice"], 81)
    check_text("restore.sourceLabel", data["sourceLabel"], 80)
    check_text("restore.restoredAt", data["restoredAt"], 40)
    check_text("restore.appVersion", data["appVersion"], 40)
    if not isinstance(data["versionCode"], int) or data["versionCode"] <= 0:
        fail("Restore-Marker: versionCode muss eine positive Ganzzahl sein.")
    check_no_secrets(data)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    args = parser.parse_args()

    root = Path(args.root)
    if not root.is_dir():
        fail(f"Verzeichnis fehlt: {root}")

    files = sorted(path for path in root.rglob("*") if path.is_file())
    if not files:
        fail("Keine Cloud-Dateien gefunden.")

    seen_state = set()
    for path in files:
        relative = path.relative_to(root).as_posix()
        match = re.match(
            r"^devices/([a-z0-9][a-z0-9._-]{2,80})/(state\.json|latest\.log|restores/([0-9]{8}-[0-9]{6})\.json)$",
            relative,
        )
        if not match:
            fail(f"Unerlaubter Cloud-Pfad: {relative}")
        slug, kind, stamp = match.groups()
        if kind == "state.json":
            data = load_json(path, STATE_MAX)
            validate_device(data)
            seen_state.add(slug)
        elif kind == "latest.log":
            validate_log(path)
        else:
            if not STAMP.match(stamp):
                fail(f"Ungültiger Restore-Zeitstempel: {stamp}")
            validate_restore(load_json(path, RESTORE_MAX))

    print(f"Device-Cloud-Dateien gültig: {len(files)} Datei(en)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
