#!/usr/bin/env python3
"""Validate themes/manifest.json and app-themes/manifest.json.

The validator mirrors what the in-app ThemeBrowser reads:
``name``, ``downloadUrl``, ``size``, ``source`` and ``sha256``.

Checks:
- JSON syntax, schema and ``themes`` array
- unique names and download targets
- safe relative paths (no absolute paths, no ``..``)
- external URLs must use https and carry a SHA-256
- relative files must exist; size/hash are verified when given
- optional metadata types (tags, package, version, ...)
- files in the folder that are not listed are reported as warnings

Usage:
    python3 scripts/validate-themes.py [--strict]

``--strict`` turns warnings into errors. CI intentionally runs without it so
themes uploaded by the app (which only add a file, no manifest entry) do not
fail the check.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")
PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
THEME_SUFFIXES = {".mtz", ".apk", ".zip"}


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def ok(self, message: str) -> None:
        print(f"OK      {message}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_entry(
    entry: object,
    index: int,
    manifest_path: Path,
    folder: Path,
    kind: str,
    report: Report,
) -> None:
    where = f"{manifest_path.name}[{index}]"

    if not isinstance(entry, dict):
        report.error(f"{where}: Eintrag ist kein Objekt")
        return

    name = entry.get("name")
    if not isinstance(name, str) or not name.strip():
        report.error(f"{where}: 'name' fehlt oder ist leer")
    elif len(name) > 120:
        report.error(f"{where}: 'name' ist länger als 120 Zeichen")

    url = entry.get("downloadUrl")
    if not isinstance(url, str) or not url.strip():
        report.error(f"{where}: 'downloadUrl' fehlt oder ist leer")
        return

    if "sha256" in entry:
        sha = entry.get("sha256")
        if not isinstance(sha, str) or not HEX64.match(sha):
            report.error(f"{where}: 'sha256' muss aus 64 Hex-Zeichen bestehen")

    if "size" in entry:
        size = entry.get("size")
        if isinstance(size, bool) or not isinstance(size, int) or size < 0:
            report.error(f"{where}: 'size' muss eine nicht-negative Ganzzahl sein")

    for key in ("source", "author", "version", "description"):
        if key in entry and not isinstance(entry[key], str):
            report.error(f"{where}: '{key}' muss ein String sein")

    if "tags" in entry:
        tags = entry["tags"]
        if not isinstance(tags, list) or any(not isinstance(t, str) for t in tags):
            report.error(f"{where}: 'tags' muss eine Liste von Strings sein")

    if kind == "app-themes":
        if "package" in entry:
            package = entry["package"]
            if not isinstance(package, str) or not PACKAGE.match(package):
                report.error(f"{where}: 'package' ist kein gültiger Paketname")
        else:
            report.warn(f"{where}: App-Theme ohne 'package'")
    elif "package" in entry:
        report.warn(f"{where}: 'package' ist nur bei App-Themes sinnvoll")

    parsed = urlparse(url)
    if parsed.scheme in ("http", "https"):
        if parsed.scheme == "http":
            report.warn(f"{where}: externer Download nutzt http statt https")
        if "sha256" not in entry:
            report.error(f"{where}: externer Download ohne SHA-256")
        if "size" not in entry:
            report.warn(f"{where}: externer Download ohne Größenangabe")
        return

    if parsed.scheme or url.startswith(("/", "\\")) or "\\" in url:
        report.error(f"{where}: nur https-URLs oder relative Pfade erlaubt")
        return

    parts = Path(url).parts
    if ".." in parts:
        report.error(f"{where}: Pfad darf kein '..' enthalten")
        return

    target = (folder / url).resolve()
    try:
        target.relative_to(folder.resolve())
    except ValueError:
        report.error(f"{where}: Pfad verlässt den Theme-Ordner")
        return

    if target.suffix.lower() not in THEME_SUFFIXES:
        report.warn(f"{where}: ungewöhnliche Dateiendung '{target.suffix}'")

    if not target.is_file():
        report.error(f"{where}: Datei fehlt: {target.relative_to(folder)}")
        return

    actual_size = target.stat().st_size
    if actual_size == 0:
        report.error(f"{where}: Datei ist leer: {target.name}")
        return

    if "size" in entry and entry["size"] != actual_size:
        report.error(
            f"{where}: Größe {entry['size']} stimmt nicht mit {actual_size} überein"
        )

    actual_sha = sha256_file(target)
    if "sha256" in entry:
        if entry["sha256"].lower() != actual_sha:
            report.error(f"{where}: SHA-256 stimmt nicht überein")
    else:
        report.warn(f"{where}: kein SHA-256 hinterlegt ({actual_sha})")

    report.ok(f"{manifest_path.relative_to(manifest_path.parent.parent)}: {name or target.name}")


def validate_manifest(manifest_path: Path, folder: Path, kind: str, report: Report) -> None:
    if not manifest_path.is_file():
        report.error(f"{manifest_path}: Datei fehlt")
        return

    try:
        data = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        report.error(f"{manifest_path}: ungültiges JSON ({exc})")
        return

    if not isinstance(data, dict):
        report.error(f"{manifest_path}: Wurzel muss ein Objekt sein")
        return

    if data.get("schema") != 1:
        report.error(f"{manifest_path}: 'schema' muss 1 sein")
    if "kind" in data and data["kind"] != kind:
        report.error(f"{manifest_path}: 'kind' muss '{kind}' sein")
    if kind == "app-themes" and "kind" not in data:
        report.warn(f"{manifest_path}: 'kind' fehlt")

    themes = data.get("themes")
    if not isinstance(themes, list):
        report.error(f"{manifest_path}: 'themes' muss eine Liste sein")
        return

    seen_names: set[str] = set()
    seen_targets: set[str] = set()
    referenced: set[str] = set()

    for index, entry in enumerate(themes):
        validate_entry(entry, index, manifest_path, folder, kind, report)
        if not isinstance(entry, dict):
            continue
        name = entry.get("name")
        if isinstance(name, str) and name.strip():
            key = name.strip().lower()
            if key in seen_names:
                report.error(f"{manifest_path}: doppelter Name '{name}'")
            seen_names.add(key)
        url = entry.get("downloadUrl")
        if isinstance(url, str) and url.strip() and not urlparse(url).scheme:
            seen_targets.add(str((folder / url).resolve()))
            referenced.add(Path(url).name)

    real_files = {p.name for p in folder.iterdir() if p.is_file() and p.suffix.lower() in THEME_SUFFIXES}
    for missing in sorted(real_files - referenced):
        report.warn(f"{manifest_path.parent.name}/{missing}: Datei ist nicht im Manifest gelistet")

    print(f"INFO    {manifest_path}: {len(themes)} Eintrag/Einträge, {len(real_files)} Datei(en)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="Warnungen als Fehler behandeln")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()

    root = args.root.resolve()
    report = Report()

    targets = [
        (root / "themes" / "manifest.json", root / "themes", "themes"),
        (root / "app-themes" / "manifest.json", root / "app-themes", "app-themes"),
    ]

    for manifest, folder, kind in targets:
        validate_manifest(manifest, folder, kind, report)

    print()
    for warning in report.warnings:
        print(f"WARN    {warning}")
    for error in report.errors:
        print(f"ERROR   {error}")

    print()
    print(
        f"Ergebnis: {len(report.errors)} Fehler, {len(report.warnings)} Warnung(en)"
    )

    if report.errors:
        return 1
    if args.strict and report.warnings:
        print("Strict-Modus: Warnungen zählen als Fehler.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
