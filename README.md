# MTZ Tool

Theme-Werkstatt für Xiaomi/HyperOS: Themes inspizieren, patchen, anwenden,
vom Gerät auslesen, eigene Themes bauen, in einer dauerhaften Bibliothek
verwalten und über GitHub teilen.

## Funktionen

- **Theme-Workspace**: `.mtz`/`.bak` einmal öffnen, alle Werkzeuge nutzen.
- **Theme Studio**: aktuelles Gerätetheme als `.mtz` pullen (Root/Shizuku),
  eigene Themes aus Schriftarten, Bildern, XML, Audio und beliebigen Dateien
  bauen, Zielpfade im Archiv bearbeiten.
- **Theme-Bibliothek**: importierte Themes bleiben dauerhaft in der App,
  bis sie gelöscht werden. Download aus dem `themes/`-Ordner des Repos, aus
  `themes/manifest.json` oder per direkter URL.
- **Theme-Upload**: erstellt einen Branch, committet das Theme nach `themes/`
  und öffnet einen Draft-PR zur Prüfung.
- **Rechte-Tool**: prüft `.mra`-Dateien gegen die Xiaomi-Signatur, zeigt die
  Abdeckung und hängt gültige Rechte an ein Theme. Eigene Signaturen sind
  technisch nicht möglich und werden klar abgelehnt.
- **GitHub in der App**: GitHub-Anmeldung per Device-Flow und Google-Anmeldung
  per PKCE im eingebetteten WebView, Issues erstellen, eigene Issues ansehen,
  PRs, Benachrichtigungen und Actions prüfen.
- **Issue-Tracker**: Bug, Feature und Crash haben unterschiedliche
  Pflichtfelder; Diagnose, Log und Einstellungen können angehängt werden.
- **Absturz-Erkennung**: uncaught exceptions werden gespeichert und beim
  nächsten Start automatisch (oder nach Bestätigung) als Issue gemeldet.
- **Adaptive Oberfläche**: erkennt Smartphone, Tablet, Faltbar, TV und Desktop
  und passt Spalten, Lesebreite und Werkzeuglisten an.
- **Build-Varianten**: `debug`, `release` und der separate, minimierte
  `playstore`-Build (nur auf ausdrücklichen Build).
- **Nightly-Builds**: täglich ein Pre-Release mit Debug- und Release-APK,
  Datums-Suffix und SHA-256; ohne Support, ohne Play-Upload.
- **Rollen und Rechte**: dokumentierte Hierarchie von Community bis Owner,
  maschinenlesbare Access-Policy, Rulesets und wöchentlicher Audit
  ([GOVERNANCE](.github/GOVERNANCE.md), [ACCESS_CONTROL](.github/ACCESS_CONTROL.md)).

## Builds

```bash
./gradlew :app:assembleDebug          # täglich
./gradlew :app:assembleRelease        # GitHub-Release-APK
./gradlew :app:bundlePlaystore        # Play-Store-AAB (R8, Shrinking, gestrippt)
./scripts/build-release.sh            # alles + SHA256SUMS + update.json
./scripts/build-nightly.sh             # Debug + Release mit nightly.YYYYMMDD
```

`assembleDebug` baut **nicht** den Play-Store-Build. Die Play-Variante ist ein
eigener Build-Typ mit `REQUEST_INSTALL_PACKAGES` entfernt, deaktiviertem
Self-Update und ohne native Debug-Symbole.

## Einrichtung

1. `local.properties` mit `sdk.dir=…` anlegen.
2. Optional `keystore.properties` + Keystore für signierte Releases.
3. Optional OAuth-Client-IDs setzen:
   ```bash
   ./gradlew :app:assembleRelease \
     -Pmtz.githubClientId=Iv1.… \
     -Pmtz.googleClientId=…apps.googleusercontent.com
   ```
   Oder in der App unter **Einstellungen → GitHub & Konten** eintragen.

## GitHub-Struktur

```
.github/
  ISSUE_TEMPLATE/   Bug, Feature, Crash, Theme, Zugriff/Rolle
  workflows/        Build, Nightly, Release, PR-Checks, CodeQL, Scorecard,
                    Dependency Review, Theme-Validierung, Access-Audit, …
  rulesets/         Branch- und Tag-Schutz als importierbares JSON
  GOVERNANCE.md     Rollenhierarchie und Entscheidungen
  ACCESS_CONTROL.md Rechte, Secrets, Rulesets, Audits, Offboarding
  access-policy.json  maschinenlesbare Soll-Rechte
  CODEOWNERS, labels.yml, labeler.yml, dependabot.yml, …
docs/               Architektur, Release, GitHub-Setup
scripts/            Release, Nightly, Theme-Validierung, Access-Setup/Audit
themes/             Auslieferungsordner für Theme-Browser und Uploads
app-themes/         App-spezifische Themes
```

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) ·
[docs/RELEASE.md](docs/RELEASE.md) ·
[docs/GITHUB_SETUP.md](docs/GITHUB_SETUP.md) ·
[.github/GOVERNANCE.md](.github/GOVERNANCE.md) ·
[.github/ACCESS_CONTROL.md](.github/ACCESS_CONTROL.md).

## Nightly installieren

Nightly-Builds liegen unter
[Releases → nightly](https://github.com/mattiasploesch-cpu/mtztool/releases/tag/nightly).
Immer `SHA256SUMS` prüfen. Nightly ist eine Vorabversion ohne Support und wird
nicht im Play Store veröffentlicht.

## Lizenz

Siehe Repository-Einstellungen. Shizuku von RikkaApps wird verwendet.
