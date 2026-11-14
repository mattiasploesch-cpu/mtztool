# Contributing

Danke, dass du MTZ Tool verbessern möchtest.

## Ablauf

1. Issue öffnen oder bestehendes Issue auswählen.
2. Branch von `main` erstellen: `feat/…`, `fix/…`, `docs/…`.
3. Änderung implementieren und lokal prüfen.
4. Pull Request mit dem Template öffnen.
5. Review abwarten – der Maintainer merged.

## Build

```bash
./gradlew :app:assembleDebug        # Debug, schnellster Weg
./gradlew :app:assembleRelease      # Release-APK
./scripts/build-release.sh          # APK + Play-AAB + SHA256SUMS
```

Der Play-Store-Build ist ein eigener Build-Typ (`playstore`) und wird von
normalen Debug- und Release-Builds **nicht** mitgebaut.

## Code-Stil

- Kotlin, Jetpack Compose, Material 3.
- Neue UI muss auf Smartphone **und** Tablet funktionieren
  (`rememberDeviceProfile()` verwenden).
- Keine neue Abhängigkeit ohne Begründung im PR.
- Deutsche UI-Texte sind willkommen; bestehende Übersetzungen nicht brechen.
- Keine Tokens, Keystores oder `local.properties` committen.

## Issues

Bug Reports und Feature Requests haben unterschiedliche Pflichtfelder.
Bitte fülle sie vollständig aus; der In-App-Tracker erzwingt das ebenfalls.

## Sicherheit

Bitte lesen: [SECURITY.md](SECURITY.md). Sicherheitslücken nicht öffentlich
melden.
