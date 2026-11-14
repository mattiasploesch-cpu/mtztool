# Architektur

## Schichten

```
app/src/main/kotlin/at/libmatti/mtztool/
  MainActivity.kt          Dashboard, Drawer, Crash-Einstieg
  AppSettings.kt           Persistente Einstellungen (SharedPreferences)
  AppLog.kt                Gemeinsame Log-Konsole
  CrashReporter.kt         Uncaught-Exception-Handler + Issue-Bericht
  ThemeSession.kt          In-Memory-Workspace
  ThemeLibrary.kt          Dauerhafte lokale Theme-Bibliothek
  ThemePuller.kt           /data/system/theme → .mtz
  ThemeComposer.kt         Beliebige Dateien → .mtz
  ThemeBrowser.kt          GitHub/Manifest/URL-Quellen
  ThemeRightsTool.kt       .mra prüfen und anhängen
  ThemeSourceReader.kt     .mtz/.bak/.data einlesen
  MtzPatcher.kt            .mtz neu schreiben
  RootThemeApplier.kt      Root-Pfad
  ShizukuHelper.kt         Shizuku/Root-Shell

  device/DeviceProfile.kt  Formfaktor + Fensterklasse
  github/GitHubApi.kt      REST-Client
  github/GitHubAuth.kt     Device-Flow + Google PKCE
  github/IssueTemplates.kt Pflichtfelder und Body-Aufbau
  ui/Adaptive.kt           Adaptive Layout-Helfer
  ui/SettingsComponents.kt Einheitliche Einstellungszeilen
  activity/…               Je Bildschirm eine Activity (Compose)

.github/                   CI/CD, Templates, Labels, Ownership
scripts/build-release.sh   APK + AAB + SHA256SUMS + update.json
themes/                    Verteilte Themes und Manifest
```

## Datenflüsse

### Theme öffnen
`Uri → ThemeSession.open → Analyse (ThemeSourceReader + ThemeRights) → UI`

### Theme anwenden
`ThemeSession → (Root) RootThemeApplier | (Shizuku) ShizukuThemeApplier`

### Theme pullen
`ShizukuHelper.readFileBytes(/data/system/theme/…) → ThemeComposer.buildArchive → .mtz`

### Theme bauen
`Picker → ThemeComposer.describe (Zielpfad-Vorschlag) → build → Speichern/Workspace`

### Theme-Bibliothek
`Import/Download → filesDir/theme-library/<id> + index.json → list/read/delete`

### Issue
`IssueTemplates.validate → buildBody → GitHubApi.createIssue`
Ohne Token: GitHub-Formular im In-App-Browser.

### Theme-Upload
`Datei → GitHubApi.uploadFile(themes/<name>, branch) → createPullRequest(draft)`

## Geräteerkennung

`UiModeManager` (TV, Auto, Uhr, Desk) → `FEATURE_PC` → Hinge-Sensoren
(Faltbar) → `smallestScreenWidthDp ≥ 600` (Tablet) → Smartphone.

Fensterklassen nach Material-3-Breakpoints: `< 600` kompakt, `< 840` mittel,
darüber breit. Die Einstellung „Gerät simulieren“ überschreibt nur die
Darstellung, nie die echte Erkennung.

## Build-Typen

| Typ | Minify | Shrinking | Self-Update | Verwendung |
| --- | --- | --- | --- | --- |
| `debug` | nein | nein | ja | Entwicklung |
| `release` | ja | ja | ja | GitHub-Releases |
| `playstore` | ja | ja | nein | Play-AAB, gestrippte Symbole |

`PLAY_BUILD` schaltet Update-Prüfung und Installer zur Laufzeit ab.

## Grenzen

- `.mra` kann nicht signiert werden; nur Xiaomi-signierte Dateien sind gültig.
- Der offizielle Xiaomi-Theme-Store bietet keine öffentliche API; die App
  öffnet ihn im In-App-Browser und importiert per URL.
- Google blockiert OAuth in WebViews teilweise; die App zeigt das an und
  bietet einen Browser-Fallback.
