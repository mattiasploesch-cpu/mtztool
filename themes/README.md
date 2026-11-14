# Themes

Dieser Ordner ist die Quelle für den Theme-Browser in der App.

- Jede `.mtz` in diesem Ordner erscheint automatisch unter
  **Theme-Bibliothek → Entdecken → GitHub /themes**.
- `manifest.json` kann zusätzliche Metadaten und externe Links liefern.
- Theme-Uploads aus der App landen als Draft-PR in diesem Ordner.

## manifest.json

```json
{
  "schema": 1,
  "themes": [
    {
      "name": "Mein Theme.mtz",
      "downloadUrl": "https://example.com/mein-theme.mtz",
      "size": 1234567,
      "sha256": "…64 hex…",
      "source": "Community"
    }
  ]
}
```

`sha256` ist optional, wird aber beim Download geprüft, wenn vorhanden.

## Regeln

- Keine Themes mit fremden, nicht lizenzierten Inhalten hochladen.
- Keine `.mra`-Dateien einbetten, die nicht von Xiaomi signiert sind.
- Große Dateien vermeiden; der Download ist auf 320 MB begrenzt.
