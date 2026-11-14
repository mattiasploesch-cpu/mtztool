#!/usr/bin/env bash
# Builds the distributable artifacts for a GitHub release and the Play Store.
#
#   ./scripts/build-release.sh              # APK + AAB + SHA256SUMS + update.json
#   ./scripts/build-release.sh --checksums-only
#
# The Play Store build is a dedicated build type, so it is only produced when
# this script (or CI) explicitly asks for it. A normal Android Studio debug
# build never triggers it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DIST="$ROOT/dist"
VERSION_NAME="$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | head -1 | cut -d'"' -f2)"
VERSION_CODE="$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | head -1 | awk '{print $3}')"

mkdir -p "$DIST"

checksums_only=false
if [[ "${1:-}" == "--checksums-only" ]]; then
  checksums_only=true
fi

if [[ "$checksums_only" == false ]]; then
  echo "==> Building release APK and Play Store AAB ($VERSION_NAME / $VERSION_CODE)"
  ./gradlew --no-daemon \
    :app:assembleRelease \
    :app:bundlePlaystore \
    -Pmtz.playBuild=true
fi

echo "==> Collecting artifacts"
rm -f "$DIST/SHA256SUMS" "$DIST/update.json" "$DIST"/*.apk "$DIST"/*.aab

# Only release and Play Store outputs are distributable. Debug APKs stay out
# of dist/ on purpose.
for dir in \
  "$ROOT/app/build/outputs/apk/release" \
  "$ROOT/app/build/outputs/apk/playstore" \
  "$ROOT/app/build/outputs/bundle/playstoreRelease" \
  "$ROOT/app/build/outputs/bundle/playstore"; do
  if [ -d "$dir" ]; then
    find "$dir" -type f \( -name "*.apk" -o -name "*.aab" \) -print0 2>/dev/null \
      | while IFS= read -r -d '' file; do
          cp -f "$file" "$DIST/$(basename "$file")"
        done
  fi
done

UNIVERSAL="$(find "$DIST" -maxdepth 1 -type f -name '*universal*release*.apk' | head -1 || true)"
if [[ -z "$UNIVERSAL" ]]; then
  UNIVERSAL="$(find "$DIST" -maxdepth 1 -type f -name '*.apk' | head -1 || true)"
fi

if [[ -n "$UNIVERSAL" ]]; then
  SHA="$(sha256sum "$UNIVERSAL" | awk '{print $1}')"
  SIZE="$(stat -c%s "$UNIVERSAL")"
  cat > "$DIST/update.json" <<JSON
{
  "versionName": "$VERSION_NAME",
  "versionCode": $VERSION_CODE,
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "changelog": "Siehe GitHub-Release.",
  "apkUrl": "$(basename "$UNIVERSAL")",
  "apkSize": $SIZE,
  "sha256": "$SHA",
  "prerelease": false
}
JSON
fi

echo "==> Writing SHA256SUMS"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$DIST" && find . -maxdepth 1 -type f \
    \( -name "*.apk" -o -name "*.aab" -o -name "*.json" \) \
    -printf '%f\n' | sort | while read -r name; do sha256sum "$name"; done) \
    > "$DIST/SHA256SUMS"
else
  (cd "$DIST" && shasum -a 256 *.apk *.aab *.json > SHA256SUMS)
fi

echo
echo "Artifacts in $DIST:"
ls -1 "$DIST"
echo
cat "$DIST/SHA256SUMS"
