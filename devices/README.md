# `/devices` – Geräteregistrierung

Jede Installation kann sich einmalig mit einem Pull Request anmelden. Der
Workflow `.github/workflows/device-registration.yml` akzeptiert **ausschließlich**:

- genau **eine** Datei,
- Status **added** (keine Änderung, keine Löschung),
- Pfad `devices/<deviceId>.json`,
- maximal 24 KiB,
- gültiges Schema (`devices/schema.json`), keine Tokens/Secrets,
- `deviceId` = Dateiname und beginnt mit dem GitHub-Login.

Alles andere wird abgelehnt. Der Validator läuft aus dem Basis-Branch und liest
die PR-Datei über die API; PR-Code wird nie ausgecheckt. Ein Patent-Token
(`DEVICE_MERGE_TOKEN`) kann den geprüften PR automatisch mergen. Ohne dieses
Secret bleibt der PR offen und der Owner merged manuell.

## Dateiname

`devices/<login>-<hash>.json`, z. B. `devices/mattiasploesch-cpu-a1b2c3d4e5.json`.
Der Hash wird auf dem Gerät aus `ANDROID_ID`, Modell und Fingerprint gebildet
und ist kein Geheimnis.

## Cloud-Backup

Veränderliche Daten (Einstellungen, Log-Auszug, letzter Sync) liegen **nicht**
auf `main`, sondern auf dem separaten Branch `device-cloud` unter
`devices/<deviceId>/state.json` und `latest.log`. So bleibt die Registrierung
unveränderlich und die Auto-Merge-Regel kann sich nur auf neue Dateien
beziehen.

## Datenschutz

- Keine Tokens, Passwörter, Keystore-Daten oder Dateiinhalte aus Themes.
- Logs werden nur mit ausdrücklicher Freigabe hochgeladen.
- Der Branch ist öffentlich, weil das Repository öffentlich ist. Gerätenamen
  und Einstellungen sind damit sichtbar; die Freigabe ist opt-in.
