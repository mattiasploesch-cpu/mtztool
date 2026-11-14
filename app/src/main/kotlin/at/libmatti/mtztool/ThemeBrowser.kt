package at.libmatti.mtztool

import android.content.Context
import at.libmatti.mtztool.github.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Browses themes that are not on the device yet.
 *
 * Sources:
 *  - `themes/` folder of the configured GitHub repository (one tap, no setup)
 *  - `themes/manifest.json` for richer metadata and external download links
 *  - any direct theme URL
 *
 * The official Xiaomi Theme Store has no public browser API, so it is opened
 * in the in-app browser and single themes can be imported by URL.
 */
object ThemeBrowser {

    private const val MAX_BYTES = 320L * 1024L * 1024L

    data class RemoteTheme(
        val name: String,
        val downloadUrl: String,
        val size: Long,
        val source: String,
        val sha256: String = "",
    ) {
        val sizeLabel: String
            get() = when {
                size <= 0 -> "unbekannte Größe"
                size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", size / 1024.0)
                else -> String.format(java.util.Locale.US, "%.1f MB", size / 1024.0 / 1024.0)
            }
    }

    fun officialStoreUrl(): String = "https://zhuti.xiaomi.com/"

    fun manifestUrl(context: Context): String {
        val repo = AppSettings.getGithubRepo(context)
        return "https://raw.githubusercontent.com/$repo/main/themes/manifest.json"
    }

    suspend fun githubThemes(context: Context): Result<List<RemoteTheme>> =
        githubFolder(context, "themes", "GitHub")

    /** App-specific themes (themes for individual apps) live in app-themes/. */
    suspend fun appThemes(context: Context): Result<List<RemoteTheme>> =
        githubFolder(context, "app-themes", "App-Themes")

    private suspend fun githubFolder(
        context: Context,
        folder: String,
        label: String,
    ): Result<List<RemoteTheme>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = GitHubApi.directory(context, folder).getOrThrow()
            entries
                .filter {
                    it.name.endsWith(".mtz", true) ||
                        it.name.endsWith(".apk", true) ||
                        it.name.endsWith(".zip", true)
                }
                .map { entry ->
                    RemoteTheme(
                        name = entry.name,
                        downloadUrl = entry.downloadUrl,
                        size = entry.size,
                        source = label,
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    suspend fun manifestThemes(context: Context): Result<List<RemoteTheme>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = fetchText(manifestUrl(context))
                val json = JSONObject(text)
                val array = json.optJSONArray("themes") ?: JSONArray()
                (0 until array.length()).mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    val url = entry.optString("downloadUrl")
                    if (url.isBlank()) return@mapNotNull null
                    RemoteTheme(
                        name = entry.optString("name", "theme.mtz"),
                        downloadUrl = url,
                        size = entry.optLong("size"),
                        source = entry.optString("source", "Theme-Store"),
                        sha256 = entry.optString("sha256"),
                    )
                }
            }
        }

    suspend fun download(context: Context, theme: RemoteTheme): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetchBytes(theme.downloadUrl)
                if (bytes.isEmpty()) throw IOException("Die heruntergeladene Datei ist leer.")
                if (theme.sha256.isNotBlank()) {
                    val actual = ThemeLibrary.sha256(bytes)
                    if (!actual.equals(theme.sha256, ignoreCase = true)) {
                        throw IOException(
                            "SHA-256 stimmt nicht überein. Erwartet ${theme.sha256}, erhalten $actual"
                        )
                    }
                }
                bytes
            }
        }

    suspend fun downloadToLibrary(
        context: Context,
        theme: RemoteTheme,
    ): Result<ThemeLibrary.Item> = withContext(Dispatchers.IO) {
        download(context, theme).mapCatching { bytes ->
            ThemeLibrary.import(context, theme.name, bytes, theme.source)
        }
    }

    suspend fun importUrl(context: Context, url: String): Result<ThemeLibrary.Item> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "theme.mtz" }
                val bytes = fetchBytes(url)
                if (bytes.isEmpty()) throw IOException("Die URL lieferte keine Daten.")
                ThemeLibrary.import(context, name, bytes, "URL-Import")
            }
        }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MTZTool-Android/2.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} für $url")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "MTZTool-Android/2.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} für $url")
            }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("Theme ist zu groß (max. 320 MB).")
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
