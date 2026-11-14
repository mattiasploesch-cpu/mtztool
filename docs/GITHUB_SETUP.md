# GitHub-Einrichtung

## OAuth-App für die In-App-Anmeldung

Die App nutzt den **Device Flow**. Dafür wird nur eine Client-ID benötigt,
kein Client-Secret.

1. GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**
   (oder „New GitHub App“ mit Device Flow aktiviert).
2. Name: `MTZ Tool`, Homepage: Repository-URL.
3. Device Flow aktivieren.
4. Client-ID kopieren.
5. In der App unter **Einstellungen → GitHub & Konten → GitHub-OAuth-Client-ID**
   eintragen oder beim Build setzen:
   ```bash
   ./gradlew :app:assembleDebug -Pmtz.githubClientId=Iv1.…
   ```

Benötigte Scopes: `repo`, `read:user`, `notifications`, `workflow`.

## Google

1. Google Cloud Console → APIs & Services → Credentials → OAuth-Client-ID
   (Typ „Webanwendung“).
2. Redirect-URI: `http://localhost`.
3. Client-ID in der App oder per `-Pmtz.googleClientId=…` setzen.

Hinweis: Google blockiert OAuth in eingebetteten WebViews teilweise
(`disallowed_useragent`). Die App zeigt das an und bietet einen
Browser-Fallback. Google dient nur der Konto-Verknüpfung; GitHub-Funktionen
benötigen die GitHub-Anmeldung.

## Actions

Die Workflows benötigen keine zusätzlichen Secrets für Builds. Für signierte
Releases:

| Secret | Wert |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 keystore/release.jks` |
| `KEYSTORE_PASSWORD` | Store-Passwort |
| `KEY_ALIAS` | Key-Alias |
| `KEY_PASSWORD` | Key-Passwort |

## Theme-Ordner

- `themes/*.mtz` erscheint im In-App-Browser.
- `themes/manifest.json` kann externe Downloads mit SHA-256 beschreiben.
- Uploads aus der App erzeugen `theme-upload/<timestamp>` und einen Draft-PR.
