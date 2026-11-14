#!/usr/bin/env python3
"""Builds the release changelog (Markdown + JSON) from git history.

The JSON variant is shipped as a release asset so the app can render the
changelog grouped by category and contributor without parsing Markdown.
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

SECTIONS = [
    ("features", "Features", {"feature"}),
    ("security", "Security", {"security"}),
    ("fixes", "Fixes", {"fix"}),
    ("performance", "Performance", {"performance"}),
    ("refactoring", "Refactoring", {"refactor"}),
    ("docs", "Documentation", {"docs"}),
    ("ci", "Build & CI", {"ci", "build", "test"}),
    ("other", "Other", {"chore", "other"}),
]

TYPE_MAP = {
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


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def last_tag(before: str | None = None) -> str:
    revision = before or "HEAD"
    try:
        return git("describe", "--tags", "--abbrev=0", "--match", "v*", revision)
    except subprocess.CalledProcessError:
        return ""


def commits_since(tag: str) -> list[dict]:
    revision = f"{tag}..HEAD" if tag else "HEAD"
    try:
        raw = git("log", revision, "--format=%H%x1f%s%x1f%b%x1f%an%x1e")
    except subprocess.CalledProcessError:
        return []

    result = []
    for record in raw.split("\x1e"):
        record = record.strip()
        if not record:
            continue
        parts = record.split("\x1f")
        if len(parts) < 4:
            continue
        sha, subject, body, author = parts[0], parts[1], parts[2], parts[3]
        if subject.startswith("Merge pull request") or subject.startswith("Merge branch"):
            continue
        match = re.match(r"^(\w+)(?:\(([^)]+)\))?(!)?:\s*(.+)$", subject)
        kind = TYPE_MAP.get(match.group(1).lower(), "other") if match else "other"
        scope = match.group(2) if match else ""
        summary = match.group(4) if match else subject
        pr_match = re.search(r"\(#(\d+)\)\s*$", summary)
        pr = pr_match.group(1) if pr_match else ""
        summary = re.sub(r"\s*\(#\d+\)\s*$", "", summary).strip()
        result.append(
            {
                "sha": sha[:7],
                "type": kind,
                "scope": scope,
                "title": summary,
                "author": author,
                "pr": pr,
                "breaking": bool(match and match.group(3)) or "BREAKING CHANGE" in body,
            }
        )
    return result


def build(version: str, previous: str, repo: str) -> dict:
    commits = commits_since(previous)
    sections = []
    contributors: set[str] = set()
    for section_id, title, types in SECTIONS:
        items = [
            {
                "title": commit["title"],
                "scope": commit["scope"],
                "author": commit["author"],
                "sha": commit["sha"],
                "pr": commit["pr"],
            }
            for commit in commits
            if commit["type"] in types
        ]
        for commit in commits:
            if commit["type"] in types:
                contributors.add(commit["author"])
        if items:
            sections.append({"id": section_id, "title": title, "items": items})

    compare = (
        f"https://github.com/{repo}/compare/{previous}...v{version}"
        if previous
        else f"https://github.com/{repo}/releases/tag/v{version}"
    )
    return {
        "version": version,
        "previousVersion": previous,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "compareUrl": compare,
        "contributors": sorted(contributors),
        "sections": sections,
        "commitCount": len(commits),
    }


def markdown(data: dict) -> str:
    lines = [f"# MTZ Tool {data['version']}", ""]
    if data["previousVersion"]:
        lines += [
            f"Änderungen seit `{data['previousVersion']}`. "
            f"{data['commitCount']} Commit(s) von {len(data['contributors'])} Mitwirkenden.",
            "",
        ]
    for section in data["sections"]:
        lines += [f"## {section['title']}", ""]
        for item in section["items"]:
            scope = f"**{item['scope']}**: " if item["scope"] else ""
            pr = f" ([#{item['pr']}](https://github.com/{os.environ.get('GITHUB_REPOSITORY', 'mattiasploesch-cpu/mtztool')}/pull/{item['pr']}))" if item["pr"] else ""
            lines.append(f"- {scope}{item['title']} — @{item['author']}{pr}")
        lines.append("")
    if not data["sections"]:
        lines += ["Keine benutzerrelevanten Änderungen.", ""]
    if data["contributors"]:
        lines += ["## Contributors", ""]
        lines += [f"- @{author}" for author in data["contributors"]]
        lines.append("")
    lines += [f"**Full Changelog**: {data['compareUrl']}", ""]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--previous", default=None)
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "mattiasploesch-cpu/mtztool"))
    parser.add_argument("--output-md", default="")
    parser.add_argument("--output-json", default="")
    args = parser.parse_args()

    previous = args.previous if args.previous is not None else last_tag()
    data = build(args.version.removeprefix("v"), previous.removeprefix("v") if previous else "", args.repo)

    md = markdown(data)
    js = json.dumps(data, indent=2, ensure_ascii=False)

    if args.output_md:
        os.makedirs(os.path.dirname(os.path.abspath(args.output_md)), exist_ok=True)
        open(args.output_md, "w", encoding="utf-8").write(md)
    if args.output_json:
        os.makedirs(os.path.dirname(os.path.abspath(args.output_json)), exist_ok=True)
        open(args.output_json, "w", encoding="utf-8").write(js)
    if not args.output_md and not args.output_json:
        print(md)
    return 0


if __name__ == "__main__":
    sys.exit(main())
