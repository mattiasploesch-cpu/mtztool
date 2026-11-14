# Security Policy

## Unterstützte Versionen

Nur die jeweils aktuelle stabile Release-Version erhält Sicherheitsupdates.
Nightly-Builds sind Vorabversionen ohne Support und ohne Sicherheitszusage.

## Melden

Bitte melde Sicherheitsprobleme über
[GitHub Security Advisories](https://github.com/mattiasploesch-cpu/mtztool/security/advisories/new)
und **nicht** als öffentliches Issue.

Bitte nenne:

- betroffene Version und Gerät,
- Reproduktionsschritte oder PoC,
- Auswirkung (Datenabfluss, Rechteausweitung, Codeausführung, …),
- ob die Lücke bereits öffentlich oder ausgenutzt ist.

### Reaktionszeiten

| Schwere | Erstreaktion | Ziel-Fix |
| --- | --- | --- |
| Kritisch (Codeausführung, Signatur, Secrets) | 24 h | 7 Tage |
| Hoch (Datenabfluss, Auth-Umgehung) | 48 h | 30 Tage |
| Mittel/Niedrig | 7 Tage | nächste Release |

Wir bestätigen den Eingang, halten dich auf dem Laufenden und nennen dich im
Advisory, wenn du möchtest.

## Umgang mit Zugangsdaten

- GitHub-Tokens werden ausschließlich in den privaten App-Einstellungen
  gespeichert und nur an `api.github.com` gesendet.
- OAuth-Client-IDs sind öffentliche Kennungen, keine Secrets.
- Der Release-Keystore und `keystore.properties` gehören **nicht** ins
  Repository. In CI werden sie aus Secrets im Environment `release`
  wiederhergestellt.
- Theme-Dateien werden lokal verarbeitet; Uploads passieren nur nach
  ausdrücklicher Aktion des Nutzers.
- Die App fordert keine Berechtigung zum Installieren fremder APKs im
  Play-Build an.

## Zugriff und Rollen

Rollen, Beförderungen und Offboarding stehen in
[GOVERNANCE.md](GOVERNANCE.md), die technische Durchsetzung in
[ACCESS_CONTROL.md](ACCESS_CONTROL.md).

- 2FA ist für alle Rollen ab Triage Pflicht.
- Es gibt keine geteilten Konten und keine Dauer-Tokens.
- Secrets sind auf das Environment `release` beschränkt und benötigen eine
  Owner-Freigabe.
- `main` ist durch Rulesets geschützt; Pflicht-Reviews, lineare Historie und
  fünf Status-Checks. Es gibt keine Bypass-Actors.
- Workflow-Token ist standardmäßig `read-only`.
- Rollen und Rulesets werden wöchentlich automatisch und quartalsweise manuell
  geprüft.

## Kompromittierung

Bei Verdacht auf kompromittierte Zugänge oder Schlüssel:

1. betroffene Rechte sofort entziehen,
2. Secrets und Keys rotieren,
3. Audit-Log und letzte Workflow-Läufe sichern,
4. Advisory vorbereiten, sofern Nutzer betroffen sind,
5. Ursache beheben und im Access-Issue dokumentieren.

## Theme-Inhalte

Themes sind ausführbare ZIP-Inhalte. Sie werden wie Code reviewt:

- keine direkten Pushes auf `main`,
- Manifest- und SHA-256-Prüfung in CI,
- kein Auto-Merge,
- Rechte-Tool akzeptiert nur echte Xiaomi-`.mra`, keine selbst erzeugten
  Signaturen.

Gemeldete Rechteverletzungen oder Schadsoftware führen zum sofortigen
Entfernen des Themes und, bei Wiederholung, zum Ausschluss.
