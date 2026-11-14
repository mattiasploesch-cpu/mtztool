# Workflows

| Datei | Auslöser | Zweck |
| --- | --- | --- |
| `build.yml` | Push auf `main`, PR, manuell | Debug-APK, Release-APK und Play-AAB bauen, Artefakte und `SHA256SUMS` hochladen |
| `pr-checks.yml` | Pull Requests | Konventioneller PR-Titel und Kotlin-Kompilierung |
| `codeql.yml` | Push, PR, wöchentlich | Statische Analyse für Kotlin |
| `release.yml` | Tag `v*`, manuell | Signierter Release, Play-AAB, `SHA256SUMS`, `update.json`, GitHub Release |
| `label-sync.yml` | Änderung an `labels.yml` | Labels aus `.github/labels.yml` synchronisieren |
| `stale.yml` | täglich | Inaktive Issues/PRs markieren und schließen |
| `auto-assign.yml` | neue Issues/PRs | Autor zuweisen, Maintainer als Reviewer |
| `welcome.yml` | erste Issues/PRs | Begrüßungstext |

## Benötigte Secrets (nur Release)

| Secret | Inhalt |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 des Release-Keystores |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | Key-Alias |
| `KEY_PASSWORD` | Passwort des Keys |

Ohne diese Secrets baut der Release-Workflow mit Debug-Signatur weiter und
markiert das im Log – er schlägt nicht hart fehl.

## Lokal

```bash
./scripts/build-release.sh              # alles
./scripts/build-release.sh --checksums-only
```
