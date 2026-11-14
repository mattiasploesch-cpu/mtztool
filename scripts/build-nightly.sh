#!/usr/bin/env bash
# Builds a nightly APK pair (debug + release) with a date suffix.
#
#   ./scripts/build-nightly.sh                 # nightly.YYYYMMDD
#   ./scripts/build-nightly.sh nightly.20261114
#
# The nightly build deliberately skips the Play bundle: Play uploads are a
# separate, explicit build type and never part of routine automation.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SUFFIX="${1:-nightly.$(date -u +%Y%m%d)}"
if [[ ! "$SUFFIX" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Ungültiges Suffix: '$SUFFIX' (erlaubt: A-Z a-z 0-9 . _ -)" >&2
  exit 2
fi

BASE_VERSION="$(grep -o 'val appVersionName = "[^"]*"' app/build.gradle.kts | head -1 | cut -d'"' -f2)"
VERSION_CODE="$(grep -o 'val appVersionCode = [0-9]*' app/build.gradle.kts | head -1 | awk '{print $NF}')"
VERSION_NAME="$BASE_VERSION-$SUFFIX"
DIST="$ROOT/dist/nightly"

if [[ -z "$BASE_VERSION" || -z "$VERSION_CODE" ]]; then
  echo "Version konnte nicht aus app/build.gradle.kts gelesen werden." >&2
  exit 1
fi

echo "==> Nightly $VERSION_NAME ($VERSION_CODE)"
./gradlew --no-daemon \
  :app:assembleDebug \
  :app:assembleRelease \
  -Pmtz.versionNameSuffix="-$SUFFIX"

echo "==> Packaging nightly artifacts"
rm -rf "$DIST"
mkdir -p "$DIST"

DEBUG_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -1 || true)"
RELEASE_APK="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | head -1 || true)"

if [[ -z "$DEBUG_APK" || -z "$RELEASE_APK" ]]; then
  echo "Debug- oder Release-APK nicht gefunden." >&2
  exit 1
fi

cp -f "$DEBUG_APK" "$DIST/mtztool-$SUFFIX-debug.apk"
cp -f "$RELEASE_APK" "$DIST/mtztool-$SUFFIX-release.apk"

RELEASE_FILE="mtztool-$SUFFIX-release.apk"
SHA="$(sha256sum "$DIST/$RELEASE_FILE" | awk '{print $1}')"
SIZE="$(stat -c%s "$DIST/$RELEASE_FILE")"
COMMIT="${GITHUB_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}"

cat > "$DIST/update.json" <<JSON
{
  "versionName": "$VERSION_NAME",
  "versionCode": $VERSION_CODE,
  "channel": "nightly",
  "commit": "$COMMIT",
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "changelog": "Nightly-Build ohne Support. Siehe GitHub-Pre-Release.",
  "apkUrl": "$RELEASE_FILE",
  "apkSize": $SIZE,
  "sha256": "$SHA",
  "prerelease": true
}
JSON

(
  cd "$DIST"
  sha256sum ./*.apk > SHA256SUMS
)

echo
echo "Nightly artifacts in $DIST:"
ls -1 "$DIST"
echo
cat "$DIST/SHA256SUMS"
