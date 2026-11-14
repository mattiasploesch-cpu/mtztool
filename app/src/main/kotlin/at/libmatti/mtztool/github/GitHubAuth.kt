package at.libmatti.mtztool.github

import android.content.Context
import at.libmatti.mtztool.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * In-app authentication helpers.
 *
 * GitHub uses the OAuth device flow: the app shows a short code, the user
 * signs in inside the embedded WebView and the app polls for the token. No
 * client secret and no external browser are required, so the flow can run
 * entirely inside the app.
 *
 * Google uses the OAuth 2.0 authorization-code flow with PKCE, also rendered
 * in the embedded WebView. Google requires a registered OAuth client ID for
 * the `http://localhost` redirect URI; the ID can be configured in the
 * developer options.
 */
object GitHubAuth {

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int,
    )

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val SCOPES = "repo read:user notifications workflow"

    suspend fun startDeviceFlow(context: Context): Result<DeviceCode> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clientId = AppSettings.getGithubClientId(context)
                if (clientId.isBlank()) {
                    throw IOException(
                        "Keine GitHub-Client-ID hinterlegt. Trage sie unter " +
                            "Entwickleroptionen → GitHub API ein."
                    )
                }
                val response = postForm(
                    DEVICE_CODE_URL,
                    mapOf(
                        "client_id" to clientId,
                        "scope" to SCOPES,
                    ),
                )
                val json = JSONObject(response)
                val error = json.optString("error")
                if (error.isNotBlank()) {
                    throw IOException(json.optString("error_description", error))
                }
                DeviceCode(
                    deviceCode = json.getString("device_code"),
                    userCode = json.getString("user_code"),
                    verificationUri = json.optString(
                        "verification_uri",
                        "https://github.com/login/device",
                    ),
                    expiresIn = json.optInt("expires_in", 900),
                    interval = json.optInt("interval", 5).coerceAtLeast(5),
                )
            }
        }

    /**
     * Polls GitHub until the user confirms the device code. [onAttempt] is
     * called with a short status line so the UI can show progress.
     */
    suspend fun pollForToken(
        context: Context,
        deviceCode: DeviceCode,
        onAttempt: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = AppSettings.getGithubClientId(context)
            val deadline = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
            var interval = deviceCode.interval

            while (System.currentTimeMillis() < deadline) {
                delay(interval * 1000L)
                onAttempt("Warte auf Bestätigung …")
                val response = postForm(
                    TOKEN_URL,
                    mapOf(
                        "client_id" to clientId,
                        "device_code" to deviceCode.deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    ),
                )
                val json = JSONObject(response)
                val token = json.optString("access_token")
                if (token.isNotBlank()) return@runCatching token

                when (json.optString("error")) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "expired_token" -> throw IOException("Der Code ist abgelaufen.")
                    "access_denied" -> throw IOException("Die Anmeldung wurde abgelehnt.")
                    else -> throw IOException(
                        json.optString("error_description", "Unbekannter OAuth-Fehler")
                    )
                }
            }
            throw IOException("Zeitüberschreitung – bitte Code neu anfordern.")
        }
    }

    internal fun postForm(url: String, fields: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MTZTool-Android/2.0")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val body = fields.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("OAuth HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }
}

object GoogleAuth {

    const val REDIRECT_URI = "http://localhost"

    data class Pkce(val verifier: String, val challenge: String)

    fun createPkce(): Pkce {
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = base64Url(random)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pkce(verifier, base64Url(digest))
    }

    fun authorizationUrl(clientId: String, pkce: Pkce, state: String): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to "openid email profile",
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "prompt" to "select_account",
        )
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
            params.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
    }

    suspend fun exchangeCode(
        clientId: String,
        code: String,
        verifier: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fields = mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to REDIRECT_URI,
            )
            val response = GitHubAuth.postForm(
                "https://oauth2.googleapis.com/token",
                fields,
            )
            val idToken = JSONObject(response).optString("id_token")
            if (idToken.isBlank()) throw IOException("Google hat kein ID-Token geliefert.")
            idToken
        }
    }

    /** Reads email/name/sub out of the ID token payload (display only). */
    fun decodeIdToken(idToken: String): Map<String, String> {
        val payload = idToken.split('.').getOrNull(1) ?: return emptyMap()
        val decoded = runCatching {
            Base64.getUrlDecoder().decode(pad(payload)).toString(Charsets.UTF_8)
        }.getOrNull() ?: return emptyMap()
        val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.optString("email").takeIf { it.isNotBlank() }?.let { put("email", it) }
            json.optString("name").takeIf { it.isNotBlank() }?.let { put("name", it) }
            json.optString("sub").takeIf { it.isNotBlank() }?.let { put("sub", it) }
        }
    }

    private fun pad(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
