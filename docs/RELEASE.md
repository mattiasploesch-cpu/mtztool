# Release

## Version

In `app/build.gradle.kts`:

```kotlin
versionCode = 20100
versionName = "2.1.0"
```

## Lokal

```bash
./scripts/build-release.sh
```

Ergebnis in `dist/`:

- `*.apk` (Release, inkl. Universal-APK)
- `*.aab` (Play-Store-Bundle)
- `SHA256SUMS`
- `update.json` (Version, Größe, SHA-256 des Universal-APK)

## Play Store

1. `./gradlew :app:bundlePlaystore` oder das Release-Skript ausführen.
2. `dist/*.aab` in der Play Console hochladen.
3. Der Play-Build enthält kein `REQUEST_INSTALL_PACKAGES`, kein Self-Update
   und keine nativen Debug-Symbole.

Die Play-Variante wird **nicht** bei `assembleDebug` oder `assembleRelease`
mitgebaut.

## GitHub Release

Tag pushen:

```bash
git tag v2.1.0
git push origin v2.1.0
```

Der Workflow `release.yml` baut, prüft `SHA256SUMS`, lädt APK/AAB hoch und
erstellt das Release. Signatur kommt aus den Secrets; ohne Secrets wird
debug-signiert und gewarnt.

## Prüfsummen

```bash
cd dist && sha256sum -c SHA256SUMS
```

## Checkliste vor dem Merge

- [ ] Debug-Build auf Smartphone
- [ ] Debug-Build auf Tablet/Faltbar
- [ ] Play-AAB gebaut und signiert
- [ ] `SHA256SUMS` verifiziert
- [ ] Keine Tokens/Keystores im Diff
