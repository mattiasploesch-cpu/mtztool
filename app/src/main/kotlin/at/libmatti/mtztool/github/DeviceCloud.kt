package at.libmatti.mtztool.github

import android.content.Context
import android.os.Build
import android.provider.Settings
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.BuildConfig
import at.libmatti.mtztool.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceRecord(
    val deviceId: String,
    val owner: String,
    val label: String,
    val model: String,
    val manufacturer: String,
    val android: String,
    val appVersion: String,
    val versionCode: Int,
    val registeredAt: String,
)

data class CloudState(
    val deviceId: String,
    val label: String,
    val updatedAt: String,
    val settingsJson: String,
    val logs: String,
)

/**
 * Device identity, registration PR, cloud backup on the ``device-cloud``
 * branch and the remotely controlled app configuration.
 */
object DeviceCloud {

    const val CLOUD_BRANCH = "device-cloud"
    private const val SYNC_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun deviceHash(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        val raw = "$androidId|${Build.MANUFACTURER}|${Build.MODEL}|${Build.FINGERPRINT}"
        return UpdateChecker.sha256(raw.toByteArray()).take(12)
    }

    fun slug(context: Context): String {
        val login = AppSettings.getGithubLogin(context).lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "device" }
        return "$login-${deviceHash(context)}"
    }

    fun label(): String =
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()

    fun isSignedIn(context: Context) = GitHubApi.isSignedIn(context)

    fun registrationJson(context: Context): String = JSONObject().apply {
        put("schema", 1)
        put("deviceId", slug(context))
        put("owner", AppSettings.getGithubLogin(context))
        put("label", label().take(80))
        put("manufacturer", Build.MANUFACTURER.take(80))
        put("model", Build.MODEL.take(80))
        put("android", Build.VERSION.RELEASE.take(24))
        put("appVersion", UpdateChecker.currentVersionName(context))
        put("versionCode", UpdateChecker.currentVersionCode(context))
        put("registeredAt", now())
    }.toString(2)

    /** Opens the registration PR; returns its URL or a German status message. */
    suspend fun ensureRegistration(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isSignedIn(context)) {
                "Bitte zuerst mit GitHub anmelden (GitHub → Anmeldung)."
            }
            val slug = slug(context)
            val path = "devices/$slug.json"
            val existing = GitHubApi.readFile(context, path)
            if (existing.isSuccess) {
                return@runCatching "Bereits registriert: $path"
            }
            val branch = "device/register-$slug"
            val bytes = registrationJson(context).toByteArray(Charsets.UTF_8)
            GitHubApi.uploadFile(
                context = context,
                path = path,
                bytes = bytes,
                branch = branch,
                message = "device: register ${label()}",
            ).getOrThrow()
            val body = """
                Automatische Geräteregistrierung.

                - Gerät: ${label()}
                - Android: ${Build.VERSION.RELEASE}
                - App: ${UpdateChecker.currentVersionName(context)} (${UpdateChecker.currentVersionCode(context)})
                - Datei: `$path`

                Der Workflow prüft, dass ausschließlich diese eine neue Datei
                enthalten ist. Keine Tokens, keine Logs, keine Codeänderungen.
            """.trimIndent()
            val pr = GitHubApi.createPullRequest(
                context = context,
                title = "device: ${label()} (${deviceHash(context)})",
                head = branch,
                base = "main",
                body = body,
                draft = false,
            ).getOrThrow()
            AppLog.success("Geräteregistrierung als PR #${pr.number} angelegt.")
            pr.htmlUrl
        }
    }

    /** Other devices of the signed-in account, excluding this installation. */
    suspend fun otherDevices(context: Context): Result<List<DeviceRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val login = AppSettings.getGithubLogin(context)
                if (login.isBlank()) return@runCatching emptyList()
                val own = slug(context)
                val entries = GitHubApi.directory(context, "devices").getOrThrow()
                entries
                    .filter { it.type == "file" && it.name.endsWith(".json") && it.name != "schema.json" }
                    .mapNotNull { entry ->
                        val text = GitHubApi.readFile(context, entry.path)
                            .getOrNull()?.bytes?.toString(Charsets.UTF_8) ?: return@mapNotNull null
                        val json = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
                        val record = DeviceRecord(
                            deviceId = json.optString("deviceId"),
                            owner = json.optString("owner"),
                            label = json.optString("label"),
                            model = json.optString("model"),
                            manufacturer = json.optString("manufacturer"),
                            android = json.optString("android"),
                            appVersion = json.optString("appVersion"),
                            versionCode = json.optInt("versionCode"),
                            registeredAt = json.optString("registeredAt"),
                        )
                        if (record.deviceId == own || record.owner != login) null else record
                    }
            }
        }

    suspend fun upload(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(isSignedIn(context)) { "Nicht mit GitHub angemeldet." }
            if (!GitHubApi.branchExists(context, CLOUD_BRANCH).getOrDefault(false)) {
                GitHubApi.createBranch(context, CLOUD_BRANCH).getOrThrow()
            }
            val slug = slug(context)
            val settings = JSONObject(AppSettings.exportJson(context))
            val logs = AppLog.asText().takeLast(24_000)
            val state = JSONObject().apply {
                put("schema", 1)
                put("device", JSONObject(registrationJson(context)))
                put("settings", settings)
                put("logs", logs)
                put("updatedAt", now())
            }
            val base = "devices/$slug"
            writeFile(context, "$base/state.json", state.toString(2).toByteArray(), "backup: state $slug")
            writeFile(context, "$base/latest.log", logs.toByteArray(), "backup: logs $slug")
            AppSettings.setLastDeviceSync(context, System.currentTimeMillis())
            AppLog.success("Cloud-Backup aktualisiert: $base")
            Unit
        }
    }

    private suspend fun writeFile(
        context: Context,
        path: String,
        bytes: ByteArray,
        message: String,
    ) {
        val existing = GitHubApi.readFile(context, path, ref = CLOUD_BRANCH).getOrNull()
        GitHubApi.uploadFile(
            context = context,
            path = path,
            bytes = bytes,
            branch = CLOUD_BRANCH,
            message = message,
            sha = existing?.sha,
        ).getOrThrow()
    }

    suspend fun downloadState(context: Context, deviceId: String = slug(context)): Result<CloudState?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = GitHubApi.readFile(
                    context,
                    "devices/$deviceId/state.json",
                    ref = CLOUD_BRANCH,
                ).getOrNull()?.bytes?.toString(Charsets.UTF_8) ?: return@runCatching null
                val json = JSONObject(text)
                CloudState(
                    deviceId = deviceId,
                    label = json.optJSONObject("device")?.optString("label").orEmpty(),
                    updatedAt = json.optString("updatedAt"),
                    settingsJson = json.optJSONObject("settings")?.toString() ?: return@runCatching null,
                    logs = json.optString("logs"),
                )
            }
        }

    suspend fun restore(context: Context, deviceId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val state = downloadState(context, deviceId).getOrThrow()
                    ?: throw IOException("Für dieses Gerät gibt es noch kein Backup.")
                AppSettings.importJson(context, state.settingsJson).getOrThrow()
                AppSettings.setLastDeviceSync(context, System.currentTimeMillis())
                AppLog.success("Cloud-Backup von ${state.label.ifBlank { deviceId }} übernommen.")
                "Einstellungen von ${state.label.ifBlank { deviceId }} übernommen."
            }
        }

    /** Uploads at most once per day, in the background. */
    suspend fun syncIfStale(context: Context): Result<Unit>? {
        if (!AppSettings.isDeviceSyncEnabled(context) || !isSignedIn(context)) return null
        val last = AppSettings.getLastDeviceSync(context)
        if (last > 0 && System.currentTimeMillis() - last < SYNC_INTERVAL_MS) return null
        return upload(context)
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
}

/**
 * Remote configuration in ``config/remote.json``. Only allow-listed keys are
 * applied; unknown or malformed fields are ignored.
 */
object RemoteConfig {

    data class Config(
        val channel: String = "stable",
        val patchUpdates: Boolean = true,
        val checkIntervalHours: Int = 24,
        val minVersionCode: Int = 0,
        val message: String = "",
        val deviceSync: Boolean = true,
        val syncIntervalHours: Int = 24,
        val announcement: String = "",
        val fetchedAt: Long = 0L,
    )

    fun cached(context: Context): Config {
        val raw = AppSettings.getRemoteConfigJson(context)
        if (raw.isBlank()) return Config(fetchedAt = AppSettings.getRemoteConfigAt(context))
        return runCatching { parse(JSONObject(raw), AppSettings.getRemoteConfigAt(context)) }
            .getOrDefault(Config(fetchedAt = AppSettings.getRemoteConfigAt(context)))
    }

    suspend fun refresh(context: Context): Result<Config> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = AppSettings.getGithubRepo(context)
            val url = "https://raw.githubusercontent.com/$repo/main/config/remote.json"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "MTZTool-Android/${BuildConfig.VERSION_NAME}")
            }
            val text = try {
                if (connection.responseCode !in 200..299) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val json = JSONObject(text)
            val config = parse(json, System.currentTimeMillis())
            AppSettings.setRemoteConfigJson(context, json.toString())
            apply(context, config)
            AppLog.info(
                "Remote-Konfiguration geladen: ${config.channel}, " +
                    "Patches=${config.patchUpdates}, Sync=${config.deviceSync}."
            )
            config
        }
    }

    private fun parse(json: JSONObject, fetchedAt: Long): Config {
        val update = json.optJSONObject("update") ?: JSONObject()
        val sync = json.optJSONObject("deviceSync") ?: JSONObject()
        return Config(
            channel = update.optString("channel", "stable").takeIf {
                it == "stable" || it == "prerelease"
            } ?: "stable",
            patchUpdates = update.optBoolean("patchUpdates", true),
            checkIntervalHours = update.optInt("checkIntervalHours", 24).coerceIn(1, 720),
            minVersionCode = update.optInt("minVersionCode", 0).coerceAtLeast(0),
            message = update.optString("message").take(500),
            deviceSync = sync.optBoolean("enabled", true),
            syncIntervalHours = sync.optInt("intervalHours", 24).coerceIn(1, 720),
            announcement = json.optString("announcement").take(500),
            fetchedAt = fetchedAt,
        )
    }

    private fun apply(context: Context, config: Config) {
        AppSettings.setUpdateChannel(
            context,
            if (config.channel == "prerelease") at.libmatti.mtztool.UpdateChannel.PRERELEASE
            else at.libmatti.mtztool.UpdateChannel.STABLE,
        )
        AppSettings.setUpdateIntervalHours(context, config.checkIntervalHours)
        AppSettings.setPatchUpdatesEnabled(context, config.patchUpdates)
        AppSettings.setDeviceSyncEnabled(context, config.deviceSync)
        AppSettings.setRemoteAnnouncement(context, config.announcement)
    }
}
