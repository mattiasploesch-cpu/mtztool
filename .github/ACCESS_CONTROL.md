# Access Control

Technische Durchsetzung der Rollen aus [GOVERNANCE.md](GOVERNANCE.md).
Grundsatz: **least privilege**, **keine geteilten Konten**, **keine
Dauer-Tokens**, **keine stillen Ausnahmen**.

## 1. Konten

| Regel | Durchsetzung |
| --- | --- |
| 2FA für alle Rollen ab Triage | GitHub-Organisation/Repo-Policy, Audit prüft `two_factor_authentication` |
| Owner mit Hardware-Key oder Passkey | manuell, jährliche Bestätigung |
| Keine geteilten Konten | eine Person = ein GitHub-Konto |
| Verifizierte E-Mail | GitHub-Standard |
| Kein Login als Owner auf fremden Geräten | Session-Review monatlich |

Das Repository gehört derzeit einem **persönlichen Konto**. Teams und
Organisationsrollen gibt es dort nicht; die Hierarchie wird über
Repository-Rollen (`admin`, `maintain`, `write`, `triage`, `read`) und
`CODEOWNERS` abgebildet. Die Migration in eine Organisation ist in
[Abschnitt 9](#9-migration-in-eine-organisation) beschrieben.

## 2. Repository-Rollen

Quelle der Wahrheit: [`access-policy.json`](access-policy.json).

| Policy-Schlüssel | GitHub-Rolle | Entspricht |
| --- | --- | --- |
| `admin` | `admin` | Owner (Stufe 5) |
| `maintain` | `maintain` | Maintainer (Stufe 4) |
| `write` | `write` | Maintainer mit Code-Schreibrecht (derzeit nur Owner) |
| `triage` | `triage` | Triage (Stufe 2) |
| `read` | `read` | Reviewer (Stufe 3) |

`write` ist bewusst **nicht** die Standardrolle für Reviewer: Wer reviewt,
soll nicht gleichzeitig ohne Review mergen können.

## 3. Rulesets

Die Rulesets liegen als importierbare JSON-Dateien in
[`rulesets/`](rulesets):

| Datei | Ziel | Regeln |
| --- | --- | --- |
| `main.json` | `refs/heads/main` | kein Löschen, kein Force-Push, lineare Historie, PR-Pflicht, 1 Approval, CODEOWNERS-Review, letzter Push muss approved sein, Konversationen aufgelöst, Squash-only, 4 Pflicht-Checks |
| `tags.json` | `refs/tags/v*` | Tags nicht löschen, nicht verschieben |

**Pflicht-Checks:** `PR title`, `Build & lint`, `Wrapper validation`,
`Theme manifests`.

**Keine Bypass-Actors.** Auch der Owner umgeht die Regeln nicht im
Normalbetrieb. Break-Glass:

1. Ruleset im UI kurzzeitig auf `disabled` setzen,
2. Grund, Umfang und Dauer im Issue `access` dokumentieren,
3. Änderung durchführen, Ruleset sofort wieder aktivieren,
4. spätestens nach 24 Stunden nachprüfen und im Audit vermerken.

## 4. CODEOWNERS

`CODEOWNERS` bildet Verantwortungsbereiche ab. Muster:

| Bereich | Owner |
| --- | --- |
| `*` (Fallback) | Owner |
| `app/src/main/kotlin/**/theme/**`, `themes/**` | Theme-Maintainer |
| `app/src/main/kotlin/**/github/**`, `.github/**` | Owner |
| `app/build.gradle.kts`, `gradle/**`, `scripts/**` | Owner |
| `docs/**`, `README.md` | Maintainer |

Regeln:

- Jede Person in `CODEOWNERS` muss in `access-policy.json` stehen.
- Neue Bereiche brauchen einen PR mit Label `access`.
- `CODEOWNERS` ersetzt keine Rulesets; es fordert nur Reviews an.
- Bei Abwesenheit des Owners kann ein Maintainer reviewer sein, aber nicht
  allein mergen, wenn der Bereich `Owner` verlangt.

## 5. Actions und Token

- Standard-Token ist **read-only**:
  `default_workflow_permissions=read`, `can_approve_pull_request_reviews=false`.
- Jeder Workflow deklariert eigene `permissions:` und nur das Nötigste.
- `pull_request_target` ist nur erlaubt, wenn **kein** PR-Code ausgecheckt
  oder ausgeführt wird (`welcome.yml` erfüllt das).
- Drittanbieter-Actions werden über Major-Tag gepinnt und von Dependabot
  aktualisiert. Für sicherheitskritische Actions ist ein voller Commit-SHA
  das Ziel; Abweichungen werden im PR begründet.
- Secrets sind ausschließlich im Environment `release` hinterlegt und dort
  durch einen Required Reviewer (Owner) geschützt.
- OIDC (`id-token: write`) wird nur für Build-Provenance/Attestation genutzt.

### Secret-Inventar

| Secret | Zweck | Rotation |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Release-Signatur | bei Verdacht, spätestens jährlich |
| `KEYSTORE_PASSWORD` | Keystore | mit dem Keystore |
| `KEY_ALIAS` | Key-Alias | mit dem Keystore |
| `KEY_PASSWORD` | Key | mit dem Keystore |
| `ACCESS_AUDIT_TOKEN` | Read-only Audit von Rollen/Rulesets (fine-grained: Administration read, Metadata read) | 90 Tage |

Der Keystore selbst und `keystore.properties` gehören **nicht** ins
Repository. Ist das historisch passiert, gilt der Key als kompromittiert:
neuen Key erzeugen, Play App Signing rotieren lassen, alte Artefakte
kennzeichnen.

## 6. Releases

- Environment `release` mit Required Reviewer = Owner.
- Tag `v*` nur vom Owner oder nach Freigabe.
- Release-Artefakte werden mit SHA-256 und Build-Provenance veröffentlicht.
- Nightly-Pre-Releases (`nightly`-Tag) sind nicht signiert und nicht
  supportet; sie überschreiben sich gegenseitig.
- Play-Uploads sind manuell und ausschließlich Owner.

## 7. Theme-Uploads

Themes sind ausführbare Inhalte (ZIP/MTZ) und werden wie Code behandelt:

- Upload nur per PR, niemals direkter Push auf `main`.
- `themes/manifest.json` und `app-themes/manifest.json` müssen gültig sein
  (`scripts/validate-themes.py`), inklusive SHA-256.
- Kein Auto-Merge, keine Bot-Merges für Theme-PRs.
- Rechte-Tool-Signaturen: nur echte Xiaomi-`.mra`, niemals selbst erzeugte.
- Gemeldete Rechteverletzungen führen zum sofortigen Entfernen des Themes.

## 8. Audits

| Rhythmus | Umfang | Werkzeug |
| --- | --- | --- |
| wöchentlich | Rollen, Rulesets, Repo-Settings | `access-audit.yml` + `scripts/audit-access.sh` |
| monatlich | PATs, Deploy Keys, Apps, Sessions | manuell im GitHub-UI |
| quartalsweise | vollständige Rezertifizierung aller Rollen | Issue `access` + Owner |
| bei Offboarding | Rechte, Keys, Secrets | Checkliste unten |

```bash
# nur lesen, keine Änderungen
GH_TOKEN=… ./scripts/audit-access.sh

# Soll-Stand anwenden (Owner, interaktiv bestätigt)
./scripts/setup-github.sh --apply
```

### Offboarding-Checkliste

- [ ] Repo-Zugriff entfernen (`setup-github.sh --apply` nach Policy-Update)
- [ ] PATs und SSH/GPG-Keys der Person widerrufen
- [ ] GitHub-App-Installationen prüfen
- [ ] Secrets rotieren, wenn die Person Zugriff hatte
- [ ] `CODEOWNERS` und Policy aktualisieren
- [ ] Audit-Issue schließen und Ergebnis dokumentieren

## 9. Migration in eine Organisation

Empfohlen, sobald mehr als zwei Personen mit Schreibrechten mitarbeiten:

1. Organisation `libmatti` (oder ähnlich) anlegen, 2FA-Pflicht aktivieren.
2. Teams `owners`, `maintainers`, `reviewers`, `triage` erstellen.
3. Repository übertragen; alten Namen als Redirect behalten.
4. `access-policy.json` um `teams` erweitern, `CODEOWNERS` auf `@org/team`
   umstellen.
5. Rulesets erneut importieren (Repository-IDs ändern sich).
6. Audit-Token mit Organisationsrechten neu ausstellen.
7. Alle Mitglieder erneut bestätigen lassen.

Bis dahin gilt: persönliches Konto, Rollen über Collaborator-Permissions,
`CODEOWNERS` mit Einzelpersonen.
