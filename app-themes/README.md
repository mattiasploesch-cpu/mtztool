# App-Themes

Hier liegen Themes für einzelne Apps (pro-App-Designs, Icons, Schriften,
Sperrbildschirm-Stile). Der In-App-Store liest diesen Ordner über den Tab
**App-Themes** und bietet Download, Import und Verwaltung an.

- Erlaubt sind `.mtz`, `.zip` (Theme-Archiv) und `.apk` (Icon-/App-Theme-Pack).
- Uploads aus der App landen wahlweise hier oder in `themes/`.
- `manifest.json` kann externe Quellen mit SHA-256 beschreiben.

## Aufbau

```
app-themes/
  manifest.json
  <app-package>/theme.mtz
  <app-package>/preview.png
```

Der Ordner wird nicht in die App eingebettet, sondern vom konfigurierten
GitHub-Repository geladen. Die App bleibt dadurch klein und Themes können
unabhängig aktualisiert werden.
