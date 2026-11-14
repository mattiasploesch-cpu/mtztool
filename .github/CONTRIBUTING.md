# Contributing

Danke, dass du MTZ Tool verbessern möchtest.

## Rollen

Die Hierarchie (Community → Contributor → Triage → Reviewer → Maintainer →
Owner) steht in [GOVERNANCE.md](GOVERNANCE.md). Für Mitarbeit brauchst du keine
Rolle: Fork und Pull Request genügen. Wer regelmäßig hilft, kann über ein
[Zugriffs-Issue](ISSUE_TEMPLATE/access_request.yml) Triage- oder
Reviewer-Rechte beantragen.

## Ablauf

1. Issue öffnen oder bestehendes Issue auswählen (bei Security:
   [SECURITY.md](SECURITY.md), nicht öffentlich).
2. Branch von `main` erstellen: `feat/…`, `fix/…`, `docs/…`, `ci/…`.
3. Änderung implementieren und lokal prüfen.
4. Pull Request mit dem Template öffnen. Der Titel muss konventionell sein
   (`feat: …`, `fix: …`) und mit einem Großbuchstaben beginnen.
5. Review abwarten. Merges sind Squash-only; der Branch wird automatisch
   gelöscht.

## Build und Prüfung

```bash
./gradlew :app:assembleDebug        # Debug, schnellster Weg
./gradlew :app:assembleRelease      # Release-APK
./gradlew :app:lintDebug            # Lint (Pflicht-Check in CI)
./scripts/build-release.sh          # APK + Play-AAB + SHA256SUMS
./scripts/build-nightly.sh          # Nightly-APK mit Datums-Suffix
python3 scripts/validate-themes.py  # Theme-Manifeste
```

Der Play-Store-Build ist ein eigener Build-Typ (`playstore`) und wird von
normalen Debug- und Release-Builds **nicht** mitgebaut. Bitte baue ihn nur,
wenn dein PR ihn betrifft.

## Review-Regeln

- Jeder PR braucht mindestens ein Approval und Owner-Review für die Bereiche
  aus [CODEOWNERS](CODEOWNERS).
- Der letzte Push muss vom Reviewer freigegeben sein; neue Commits verwerfen
  alte Approvals.
- Konversationen müssen aufgelöst sein.
- Keine Bypässe, kein Selbst-Merge ohne Review.
- Große PRs (`size/xl`) bitte aufteilen.

## Code-Stil

- Kotlin, Jetpack Compose, Material 3.
- Neue UI muss auf Smartphone **und** Tablet funktionieren
  (`rememberDeviceProfile()` verwenden).
- Keine neue Abhängigkeit ohne Begründung im PR.
- Deutsche UI-Texte sind willkommen; bestehende Übersetzungen nicht brechen.
- Keine Tokens, Keystores oder `local.properties` committen.
- Themen-Uploads nur über PR; Manifeste und SHA-256 müssen gültig sein.

## Nightly

Nightly-Builds sind Vorabversionen ohne Support. Sie werden täglich als
Pre-Release `nightly` veröffentlicht, nutzen denselben `versionCode` wie das
Stable-Release und ersetzen ihre Assets bei jedem Lauf. Details:
[WORKFLOWS.md](WORKFLOWS.md).

## Entscheidungen

Lazy Consensus: Ein Vorschlag im Issue gilt nach 72 Stunden ohne Widerspruch
als angenommen. Bei Konflikt entscheiden die Maintainer, bei Patt der Owner.
Alles wird im Issue/PR dokumentiert.

## Sicherheit

Bitte lesen: [SECURITY.md](SECURITY.md). Sicherheitslücken nicht öffentlich
melden, keine fremden Tokens oder Geräte-IDs in Logs veröffentlichen.
