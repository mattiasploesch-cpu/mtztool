# Workflows

Alle Workflows laufen mit minimalen `permissions:` und ohne Bypass-Regeln.
Pflicht-Checks für `main` stehen in [`rulesets/main.json`](rulesets/main.json).

## Build und Release

| Datei | Auslöser | Zweck | Schreibrechte |
| --- | --- | --- | --- |
| `build.yml` | Push `main`, PR, manuell | Debug-APK, Release-APK und Play-AAB, Artefakte + `SHA256SUMS` | keine |
| `pr-checks.yml` | PR | Konventioneller Titel, Kotlin-Kompilierung, `lintDebug` | keine |
| `wrapper-validation.yml` | Push, PR, manuell | Gradle-Wrapper-Checksumme (Pflicht-Check) | keine |
| `nightly.yml` | täglich 02:00 UTC, manuell | Debug-/Release-APK mit Datums-Suffix, Pre-Release `nightly` | `contents: write` |
| `release.yml` | Tag `v*`, manuell | Signierter Release, Play-AAB, SHA256, Provenance, GitHub Release | `contents`, `id-token`, `attestations` |

## Qualität und Sicherheit

| Datei | Auslöser | Zweck |
| --- | --- | --- |
| `codeql.yml` | Push, PR, wöchentlich | Statische Analyse Kotlin |
| `scorecard.yml` | Push `main`, wöchentlich, manuell | OpenSSF Scorecard → SARIF |
| `dependency-review.yml` | PR | Blockiert neue Abhängigkeiten mit hoher Schwere |
| `dependency-submission.yml` | Push `main`, manuell | Gradle-Dependency-Graph für Review |
| `theme-validation.yml` | Push, PR, manuell | `themes/`- und `app-themes/`-Manifeste + SHA-256 (Pflicht-Check) |

## Triage und Verwaltung

| Datei | Auslöser | Zweck |
| --- | --- | --- |
| `pr-labeler.yml` | PR | Bereichs-Labels anhand geänderter Pfade |
| `pr-size.yml` | PR | Größen-Labels `size/xs` … `size/xl` |
| `label-sync.yml` | Änderung `labels.yml` | Labels synchronisieren |
| `stale.yml` | täglich | Inaktive Issues/PRs markieren und schließen |
| `lock-threads.yml` | täglich | Geschlossene Threads nach 30 Tagen sperren |
| `auto-assign.yml` | neue Issues/PRs | Autor zuweisen, Owner als Reviewer |
| `welcome.yml` | erste Issues/PRs | Begrüßung (ohne PR-Code auszuführen) |
| `access-audit.yml` | wöchentlich, manuell | Rollen, Rulesets, Settings gegen Policy prüfen |

## Secrets

| Secret | Workflow | Inhalt |
| --- | --- | --- |
| `KEYSTORE_BASE64` | `release.yml` | Base64 des Release-Keystores |
| `KEYSTORE_PASSWORD` | `release.yml` | Store-Passwort |
| `KEY_ALIAS` | `release.yml` | Key-Alias |
| `KEY_PASSWORD` | `release.yml` | Key-Passwort |
| `ACCESS_AUDIT_TOKEN` | `access-audit.yml` | Fine-grained PAT: Administration read, Metadata read |

Die Keystore-Secrets sind ausschließlich im Environment `release` hinterlegt und
dort durch einen Required Reviewer geschützt. Nightly-Builds nutzen keine
Secrets; ohne Keystore werden sie debug-signiert.

Ohne `ACCESS_AUDIT_TOKEN` wird der Audit übersprungen und schlägt nicht jede
Woche fehl. Für vollständige Prüfungen den Token setzen.

## Nightly

- Zeitplan: täglich 02:00 UTC; GitHub kann Läufe verzögern.
- Manuell: Actions → Nightly → Run workflow, optional mit eigenem Suffix.
- Version: `2.1.0-nightly.YYYYMMDD`, gleicher `versionCode` wie stabil, damit
  ein späteres Stable-Update installierbar bleibt.
- Veröffentlichung: Pre-Release mit festem Tag `nightly`; Assets werden
  überschrieben (`--clobber`), es entsteht keine Release-Historie.
- Aufbewahrung: Actions-Artefakt 14 Tage, Pre-Release-Assets bis zum nächsten
  Lauf.
- Kein Support, keine Play-Veröffentlichung, kein AAB.

## Lokal

```bash
./scripts/build-release.sh              # Release + Play-AAB + Prüfsummen
./scripts/build-release.sh --checksums-only
./scripts/build-nightly.sh              # Debug + Release mit Datums-Suffix
python3 scripts/validate-themes.py      # Manifeste prüfen
./scripts/audit-access.sh               # Rechte-Audit (nur lesen)
./scripts/setup-github.sh               # Dry-Run der Rechte/Rulesets
```
