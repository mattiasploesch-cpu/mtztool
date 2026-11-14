# Security Policy

## Unterstützte Versionen

Nur die jeweils aktuelle Release-Version erhält Sicherheitsupdates.

## Melden

Bitte melde Sicherheitsprobleme über
[GitHub Security Advisories](https://github.com/mattiasploesch-cpu/mtztool/security/advisories/new)
und nicht als öffentliches Issue.

## Umgang mit Zugangsdaten

- GitHub-Tokens werden ausschließlich in den privaten App-Einstellungen
  gespeichert und nur an `api.github.com` gesendet.
- OAuth-Client-IDs sind öffentliche Kennungen, keine Secrets.
- Der Release-Keystore und `keystore.properties` gehören **nicht** ins
  Repository. In CI werden sie aus Secrets wiederhergestellt.
- Theme-Dateien werden lokal verarbeitet; Uploads passieren nur nach
  ausdrücklicher Aktion des Nutzers.
