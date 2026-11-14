# Governance

Dieses Dokument beschreibt, **wer** im Projekt welche Entscheidungen treffen
darf und **wie** jemand eine Rolle bekommt oder verliert. Die technische
Durchsetzung steht in [ACCESS_CONTROL.md](ACCESS_CONTROL.md), die
Zusammenarbeit in [CONTRIBUTING.md](CONTRIBUTING.md).

## Rollenhierarchie

Die Rollen sind kumulativ: Eine höhere Stufe darf alles, was die niedrigere
darf. Die Zahl ist die Stufe, nicht die Anzahl der Personen.

| Stufe | Rolle | GitHub-Berechtigung | Darf | Darf nicht |
| --- | --- | --- | --- | --- |
| 5 | **Owner** | `admin` | Alles: Rulesets, Secrets, Releases, Rollen vergeben, Repository löschen/übertragen | – |
| 4 | **Maintainer** | `maintain` | `main` mergen, Releases auslösen, Milestones/Projekte pflegen, Triage-Regeln ändern, Rollen bis Stufe 3 vorschlagen | Secrets lesen, Rulesets ändern, Stufe 4+ vergeben, Repository-Einstellungen ändern |
| 3 | **Reviewer** | `read` + CODEOWNERS-Review | PRs fachlich reviewen, Änderungen anfordern, Issues schließen/lablen (als Reviewer) | Mergen, pushen, Releases, Secrets |
| 2 | **Triage** | `triage` | Issues/PRs labeln, zuweisen, schließen, Milestones setzen, Duplikate verwalten | Code reviewen oder mergen, Code pushen |
| 1 | **Contributor** | kein Repo-Zugriff (Fork) | Fork + PR, Issues, Diskussionen, Code reviewen (ohne Gewicht) | Direkt pushen, mergen, labeln |
| 0 | **Community** | kein Zugriff | Issues, Diskussionen, Sicherheitsmeldung | Alles andere |

### Wer ist aktuell wer?

Die verbindliche, maschinenlesbare Liste ist
[`access-policy.json`](access-policy.json). Sie ist die einzige Quelle für
Zugriffe; dieses Dokument beschreibt nur die Regeln. Weicht der tatsächliche
Stand ab, gewinnt die Policy und der Audit schlägt fehl.

- **Owner:** `@mattiasploesch-cpu`
- **Maintainer:** derzeit keine
- **Reviewer:** derzeit keine
- **Triage:** derzeit keine

Neue Reviewer werden erst in die Policy eingetragen und dann in
[`CODEOWNERS`](CODEOWNERS) ergänzt. Umgekehrt gilt: Wer in `CODEOWNERS`
steht, muss in der Policy stehen.

## Beförderung

| Ziel | Voraussetzung | Entscheidung |
| --- | --- | --- |
| Triage (2) | 3 hilfreiche Triage-Aktionen oder 1 Monat Mitarbeit | 1 Maintainer |
| Reviewer (3) | 3 substanzielle, gemergte PRs; kennt Build und Testpfad | 1 Maintainer + Owner |
| Maintainer (4) | 6 Monate Reviewer, 10 gemergte PRs, vertraut mit Release- und Signaturprozess | Owner + alle aktiven Maintainer |
| Owner (5) | Vertrauensstellung, 2FA + Hardware-Key, Zugriff auf Keystore-Backup | aktueller Owner, schriftlich im Issue |

Beförderungen laufen über ein
[Zugriffs-Issue](ISSUE_TEMPLATE/access_request.yml) und werden **immer** im
Repository dokumentiert (Issue + Policy-Commit). Es gibt keine mündlichen
oder privaten Zusagen.

## Abstieg, Inaktivität, Offboarding

- **Inaktivität:** 90 Tage ohne Review/Triage/Commit → `emeritus`-Notiz im
  Issue, Rechte bleiben vorerst. Nach 180 Tagen werden Schreibrechte auf
  `read` reduziert; ein späterer Wiedereinstieg ist ohne Wartezeit möglich.
- **Vertrauensbruch:** Owner kann Rechte sofort entziehen (Security-Incident,
  Datenabfluss, Umgehung der Rulesets). Begründung im internen Security-Issue.
- **Offboarding:** innerhalb von 24 Stunden nach Austritt: Repo-Zugriff
  entfernen, PATs/SSH-Keys widerrufen, Secret-Rotation, Audit-Eintrag.
- **Emeritus:** verdiente Beitragende ohne aktive Rechte; werden im
  `CONTRIBUTORS`-Abschnitt der README genannt, nicht in `CODEOWNERS`.

## Entscheidungen

1. **Lazy Consensus:** Ein Vorschlag im Issue gilt nach 72 Stunden als
   angenommen, wenn kein Maintainer widerspricht.
2. **Konsens der Maintainer:** Bei Widerspruch entscheiden die Maintainer mit
   einfacher Mehrheit; bei Patt entscheidet der Owner.
3. **Owner-Veto:** nur bei Sicherheit, Recht, Signaturschlüsseln oder
   Repository-Existenz. Das Veto wird begründet und ist im Issue sichtbar.
4. **Technische Fakten schlagen Meinungen:** Build-, Test- und
   Sicherheitsergebnisse sind argumentierbar, Stilvorlieben nicht.
5. **Alles schriftlich:** Entscheidungen, Rollen und Ausnahmen stehen in
   Issues/PRs, nicht in Chats.

## Releases und Signatur

- Releases dürfen nur von Maintainern (Stufe 4) ausgelöst werden.
- Der Release-Workflow nutzt das GitHub-Environment `release`; der Owner muss
  den Lauf freigeben. Ohne Freigabe wird nicht signiert.
- Der Release-Keystore liegt **niemals** im Repository. Secrets sind an das
  Environment gebunden und nur für den Release-Job sichtbar.
- Nightly-Builds sind unsignierte bzw. debug-signierte Vorabversionen, werden
  als Pre-Release veröffentlicht und sind **kein** Supportstand.
- Play-Store-Uploads macht ausschließlich der Owner.

## Sicherheit

- Sicherheitsmeldungen laufen über
  [Security Advisories](../.github/SECURITY.md), nicht über öffentliche Issues.
- Der Owner ist Security-Lead. Reviewer mit Sicherheitsfokus werden im
  Advisory als „Security Response Team“ genannt.
- Ein Advisory wird erst veröffentlicht, wenn ein Fix oder eine Mitigation
  bereitsteht; Ausnahme: aktiv ausgenutzte Lücken.
- Bei Verdacht auf kompromittierte Zugänge gilt sofort: Rechte entziehen,
  Secrets rotieren, Audit-Log sichern, danach analysieren.

## Änderungen an diesem Dokument

Änderungen an `GOVERNANCE.md`, `ACCESS_CONTROL.md`, `access-policy.json`,
`CODEOWNERS` oder den Rulesets benötigen:

1. einen PR mit dem Label `access`,
2. Review durch den Owner,
3. einen dokumentierten Audit-Lauf (`scripts/audit-access.sh`) im PR.
