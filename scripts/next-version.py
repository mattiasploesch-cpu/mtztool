#!/usr/bin/env python3
"""Determines the next SemVer version from the commits since the last tag.

The bump level is derived from Conventional Commits:

* ``feat`` or a breaking change -> major/minor
* ``fix``, ``security`` or any maintenance commit -> patch

Every result gets a monotonically increasing versionCode derived from
``major * 10000 + minor * 100 + patch`` (2.1.0 -> 20100). The script never
writes source files; CI passes the values to Gradle with
``-Pmtz.versionName`` and ``-Pmtz.versionCode``.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GRADLE = os.path.join(ROOT, "app", "build.gradle.kts")

TYPE_LABELS = {
    "feat": "feature",
    "fix": "fix",
    "security": "security",
    "perf": "performance",
    "refactor": "refactor",
    "docs": "docs",
    "ci": "ci",
    "build": "build",
    "test": "test",
    "chore": "chore",
}

LEVEL_ORDER = {"patch": 0, "minor": 1, "major": 2}


def run_git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def read_current() -> tuple[str, int]:
    text = open(GRADLE, encoding="utf-8").read()
    name_match = re.search(r'val appVersionName = "([^"]+)"', text)
    code_match = re.search(r"val appVersionCode = (\d+)", text)
    if not name_match or not code_match:
        raise SystemExit("Version konnte nicht aus app/build.gradle.kts gelesen werden.")
    return name_match.group(1), int(code_match.group(1))


def last_tag() -> str:
    # Do not use `git describe`: a checked-out branch may be behind the tag
    # (for example while preparing a follow-up PR), and describe would then
    # report no tag and reuse an already published version.
    try:
        tags = run_git("tag", "--sort=-v:refname", "--list", "v*").splitlines()
    except subprocess.CalledProcessError:
        return ""
    for tag in tags:
        if re.fullmatch(r"v\d+\.\d+\.\d+", tag.strip()):
            return tag.strip()
    return ""


def version_code(name: str) -> int:
    cleaned = name.removeprefix("v").split("-")[0]
    parts = [int(p) for p in cleaned.split(".")]
    while len(parts) < 3:
        parts.append(0)
    major, minor, patch = parts[:3]
    if not (0 <= major <= 2100 and 0 <= minor <= 99 and 0 <= patch <= 99):
        raise SystemExit(
            f"Version {name} liegt außerhalb des Schemas major<=2100, minor/patch<=99."
        )
    return major * 10000 + minor * 100 + patch


def parse_version(name: str) -> tuple[int, int, int]:
    cleaned = name.removeprefix("v").split("-")[0]
    parts = [int(p) for p in cleaned.split(".")]
    while len(parts) < 3:
        parts.append(0)
    return parts[0], parts[1], parts[2]


def bump(name: str, level: str, current_code: int) -> str:
    major, minor, patch = parse_version(name)
    if level == "major":
        major, minor, patch = major + 1, 0, 0
    elif level == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1

    # Never produce a code that is not strictly larger than the installed base.
    while version_code(f"{major}.{minor}.{patch}") <= current_code:
        patch += 1
        if patch > 99:
            minor += 1
            patch = 0
    return f"{major}.{minor}.{patch}"


def parse_commits(since: str) -> list[dict]:
    revision = f"{since}..HEAD" if since else "HEAD"
    try:
        raw = run_git("log", revision, "--format=%H%x1f%s%x1f%b%x1f%an%x1e")
    except subprocess.CalledProcessError:
        return []

    commits = []
    for record in raw.split("\x1e"):
        record = record.strip()
        if not record:
            continue
        parts = record.split("\x1f")
        if len(parts) < 4:
            continue
        sha, subject, body, author = parts[0], parts[1], parts[2], parts[3]
        match = re.match(r"^(\w+)(?:\([^)]+\))?(!)?:\s*(.+)$", subject)
        kind = match.group(1).lower() if match else "other"
        breaking = bool(match and match.group(2)) or "BREAKING CHANGE" in body
        commits.append(
            {
                "sha": sha[:7],
                "subject": subject,
                "summary": match.group(3) if match else subject,
                "type": TYPE_LABELS.get(kind, "other"),
                "breaking": breaking,
                "author": author,
            }
        )
    return commits


def level_for(commits: list[dict], explicit: str | None) -> tuple[str, bool]:
    if explicit and explicit != "auto":
        return ("patch" if explicit == "security" else explicit), explicit == "security"

    security = any(c["type"] == "security" for c in commits)
    if any(c["breaking"] for c in commits):
        return "major", security
    if any(c["type"] == "feature" for c in commits):
        return "minor", security
    return "patch", security


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-tag", default=None, help="Tag, ab dem gezählt wird")
    parser.add_argument("--level", default="auto", choices=["auto", "major", "minor", "patch", "security"])
    parser.add_argument("--format", default="json", choices=["json", "name", "code", "github", "markdown"])
    parser.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT", ""))
    args = parser.parse_args()

    base_name, base_code = read_current()
    latest_tag = last_tag()
    tag = args.from_tag if args.from_tag is not None else latest_tag
    # The checked-in base can lag behind the newest release tag. Bump from the
    # newest tag so a release never reuses an existing version.
    if latest_tag:
        latest_name = latest_tag.removeprefix("v")
        try:
            if parse_version(latest_name) > parse_version(base_name):
                base_name = latest_name
                base_code = version_code(latest_name)
        except (ValueError, SystemExit):
            pass
    commits = parse_commits(tag)
    level, security = level_for(commits, args.level)
    name = bump(base_name, level, base_code)
    code = version_code(name)
    old_code = version_code(base_name)
    repo = os.environ.get("GITHUB_REPOSITORY", "mattiasploesch-cpu/mtztool")

    payload = {
        "versionName": name,
        "versionCode": code,
        "previousVersionName": base_name,
        "previousVersionCode": old_code,
        "level": level,
        "security": security,
        "fromTag": tag,
        "commitCount": len(commits),
        "commits": commits,
        "compareUrl": (
            f"https://github.com/{repo}/compare/{tag}...HEAD" if tag
            else f"https://github.com/{repo}/commits/main"
        ),
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    if args.format == "name":
        print(name)
    elif args.format == "code":
        print(code)
    elif args.format == "github":
        print(f"version={name}")
        print(f"code={code}")
        print(f"level={level}")
    elif args.format == "markdown":
        print(f"**Nächste Version:** `{name}` (`{code}`) · Bump: **{level}**")
        if security:
            print("\nEnthält Sicherheitsänderungen.")
        if commits:
            print(f"\n{len(commits)} Commit(s) seit `{tag or 'Anfang'}`:")
            for commit in commits[:20]:
                print(f"- `{commit['sha']}` {commit['subject']} — @{commit['author']}")
        else:
            print("\nKeine Commits seit dem letzten Tag.")
    else:
        print(json.dumps(payload, indent=2, ensure_ascii=False))

    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as handle:
            handle.write(f"version={name}\ncode={code}\nlevel={level}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
