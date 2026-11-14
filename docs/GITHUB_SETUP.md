# GitHub-Einrichtung

## 1. OAuth-App für die In-App-Anmeldung

Die App nutzt den **Device Flow**. Dafür wird nur eine Client-ID benötigt,
kein Client-Secret.

1. GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**
   (oder „New GitHub App“ mit Device Flow aktiviert).
2. Name: `MTZ Tool`, Homepage: Repository-URL.
3. Device Flow aktivieren.
4. Client-ID kopieren.
5. In der App unter **Einstellungen → GitHub & Konten → GitHub-OAuth-Client-ID**
   eintragen oder beim Build setzen:
   ```bash
   ./gradlew :app:assembleDebug -Pmtz.githubClientId=Iv1.…
   ```

Benötigte Scopes: `repo`, `read:user`, `notifications`, `workflow`.

## 2. Google

1. Google Cloud Console → APIs & Services → Credentials → OAuth-Client-ID
   (Typ „Webanwendung“).
2. Redirect-URI: `http://localhost`.
3. Client-ID in der App oder per `-Pmtz.googleClientId=…` setzen.

Hinweis: Google blockiert OAuth in eingebetteten WebViews teilweise
(`disallowed_useragent`). Die App zeigt das an und bietet einen
Browser-Fallback. Google dient nur der Konto-Verknüpfung; GitHub-Funktionen
benötigen die GitHub-Anmeldung.

## 3. Rollen und Rechte

Die Regeln stehen in [GOVERNANCE.md](../.github/GOVERNANCE.md), die technische
Durchsetzung in [ACCESS_CONTROL.md](../.github/ACCESS_CONTROL.md). Quelle der
Wahrheit ist [`access-policy.json`](../.github/access-policy.json).

```bash
# 1. Plan ansehen (ändert nichts)
./scripts/setup-github.sh

# 2. Anwenden (angemeldeter Account muss Admin sein)
./scripts/setup-github.sh --apply

# 3. Prüfen
./scripts/audit-access.sh
```

Das Skript setzt:

- Collaborator-Rollen gemäß Policy (`admin`, `maintain`, `write`, `triage`,
  `read`),
- Squash-only-Merges, Branch-Löschen nach Merge, Auto-Merge,
- Issues und Discussions,
- Vulnerability Alerts,
- Workflow-Token auf `read` (keine PR-Freigabe durch Actions),
- Environment `release` mit Owner als Required Reviewer,
- die Rulesets aus `.github/rulesets/`.

Standard ist **Dry-Run**. `--prune` entfernt Collaborators, die nicht in der
Policy stehen; ohne den Schalter wird nur gewarnt. Entfernungen sind
destruktiv und sollten zuerst im Audit sichtbar sein.

> Das Repository gehört einem persönlichen Konto. GitHub-Teams gibt es nur in
> Organisationen; deshalb laufen Rollen über Repository-Permissions und
> `CODEOWNERS`. Die Migrationsschritte stehen in
> [ACCESS_CONTROL.md](../.github/ACCESS_CONTROL.md#9-migration-in-eine-organisation).

### Audit-Token

Der Audit braucht Leserechte auf Administration, die ein normales
`GITHUB_TOKEN` nicht hat. Fine-grained PAT erstellen:

- Repository: `mattiasploesch-cpu/mtztool`
- Repository permissions: **Administration: Read**, **Metadata: Read**
- Ablauf: 90 Tage

Als Secret `ACCESS_AUDIT_TOKEN` hinterlegen. Ohne den Token wird der
wöchentliche Audit übersprungen, statt rot zu werden.

## 4. Secrets und Release

| Secret | Wert |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 keystore/release.jks` |
| `KEYSTORE_PASSWORD` | Store-Passwort |
| `KEY_ALIAS` | Key-Alias |
| `KEY_PASSWORD` | Key-Passwort |
| `ACCESS_AUDIT_TOKEN` | Fine-grained PAT (siehe oben) |

- Secrets nur im Environment `release` hinterlegen, nicht auf Repository-Ebene.
- Required Reviewer für `release` ist der Owner; ohne Freigabe wird nicht
  signiert.
- Der Keystore und `keystore.properties` gehören **nicht** ins Repository.
  Falls sie historisch committet wurden: Key als kompromittiert betrachten,
  neuen Key erzeugen und Play App Signing rotieren.
- Release-Artefakte erhalten SHA-256 (`dist/SHA256SUMS`) und eine
  Build-Provenance-Attestation.

## 5. Nightly-Builds

Nightly läuft täglich um 02:00 UTC und manuell über **Actions → Nightly**.

```bash
# lokal dasselbe bauen
./scripts/build-nightly.sh
./scripts/build-nightly.sh nightly.20261114   # eigenes Suffix
```

- Version: `2.1.0-nightly.YYYYMMDD`, gleicher `versionCode` wie stabil.
- Assets: Debug-APK, Release-APK, `SHA256SUMS`, `update.json`.
- Veröffentlichung: Pre-Release mit festem Tag
  [`nightly`](https://github.com/mattiasploesch-cpu/mtztool/releases/tag/nightly);
  jede Nacht werden die Assets ersetzt.
- Kein Play-AAB, keine Secrets, kein Support.
- Installation: APK herunterladen, SHA-256 prüfen, installieren. Ein späteres
  Stable-Release lässt sich darüber installieren, solange der Signaturschlüssel
  gleich bleibt.

## 6. Rulesets und Pflicht-Checks

| Ruleset | Ziel | Inhalt |
| --- | --- | --- |
| `main-protection` | `main` | PR-Pflicht, 1 Approval, CODEOWNERS, letzter Push approved, lineare Historie, Squash-only, 5 Pflicht-Checks |
| `release-tags` | `v*` | Tags nicht löschen oder verschieben |

Pflicht-Checks: `PR title`, `Compile debug`, `Lint (debug)`,
`Wrapper validation`, `Theme manifests`.

Es gibt **keine Bypass-Actors**. Break-Glass ist in
[ACCESS_CONTROL.md](../.github/ACCESS_CONTROL.md#3-rulesets) beschrieben:
Ruleset kurz deaktivieren, Vorfall dokumentieren, sofort reaktivieren.

## 7. Theme-Ordner und Validierung

- `themes/*.mtz` erscheint im In-App-Browser.
- `app-themes/*` erscheint im App-Theme-Browser.
- `themes/manifest.json` und `app-themes/manifest.json` beschreiben externe
  Downloads und Metadaten.
- `python3 scripts/validate-themes.py` prüft Pfade, Größen und SHA-256.
  Externe URLs brauchen einen Hash; lokale Dateien werden geprüft.
- Uploads aus der App erzeugen `theme-upload/<timestamp>` und einen Draft-PR.
  Kein Auto-Merge.

## 8. Workflow-Berechtigungen für das Pushen

Der `gh`-Login muss den Scope `workflow` besitzen, sonst lehnt GitHub Änderungen
an `.github/workflows/**` ab:

```bash
gh auth refresh -s workflow
gh auth status
```
