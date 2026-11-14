package at.libmatti.mtztool

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

enum class UpdateChannel {
    STABLE,
    PRERELEASE;

    companion object {
        fun fromStorage(value: String?): UpdateChannel =
            entries.firstOrNull { it.name == value } ?: STABLE
    }
}

data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long = 0L,
    val sha256: String? = null,
)

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val prerelease: Boolean,
    val title: String,
    val changelog: String,
    val publishedAt: String,
    val apk: UpdateAsset,
    val splits: List<UpdateAsset>,
    val htmlUrl: String,
    val channel: UpdateChannel,
)

/**
 * GitHub Releases based update checker.
 *
 * Stable builds only look at normal releases. The pre-release channel (enabled
 * in developer options) also accepts GitHub pre-releases and nightly builds.
 * A custom manifest URL can be configured for testing without publishing a
 * release.
 */
object UpdateChecker {

    private const val USER_AGENT = "MTZTool-Updater/2"
    private const val API_VERSION = "2022-11-28"

    suspend fun check(context: Context): Result<UpdateRelease?> = withContext(Dispatchers.IO) {
        try {
            val channel = AppSettings.getUpdateChannel(context)
            val releases: List<UpdateRelease> =
                if (AppSettings.getUpdateManifestUrl(context).isNotBlank()) {
                    listOfNotNull(
                        parseCustomManifest(AppSettings.getUpdateManifestUrl(context), channel)
                    )
                } else {
                    fetchGithubReleases(context, channel)
                }

            AppSettings.setLastUpdateCheck(context, System.currentTimeMillis())

            val currentCode = currentVersionCode(context)
            val currentName = currentVersionName(context)
            val newer = releases
                .filter { release -> release.channel == UpdateChannel.PRERELEASE || !release.prerelease }
                .filter { release ->
                    if (release.versionCode > 0) release.versionCode > currentCode
                    else compareVersions(release.versionName, currentName) > 0
                }
                .maxWithOrNull(
                    compareBy<UpdateRelease> { if (it.versionCode > 0) it.versionCode else 0 }
                        .thenBy { it.publishedAt }
                )

            if (newer == null && AppSettings.isVerboseLoggingEnabled(context)) {
                AppLog.info("Update-Prüfung: Keine neuere Version gefunden.")
            }
            Result.success(newer)
        } catch (e: Exception) {
            AppLog.warn("Update-Prüfung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    fun currentVersionCode(context: Context): Int {
        val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    fun currentVersionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0.0" }

    suspend fun download(
        context: Context,
        release: UpdateRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "MTZTool-${release.versionName}.apk")
            val temp = File(directory, target.name + ".part")
            temp.delete()
            target.delete()

            val connection = openConnection(release.apk.url)
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apk.size
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            val expected = release.apk.sha256
            if (!expected.isNullOrBlank()) {
                val actual = sha256(temp)
                if (!actual.equals(expected, ignoreCase = true)) {
                    temp.delete()
                    throw IOException(
                        "SHA-256 stimmt nicht überein. Erwartet: $expected, erhalten: $actual"
                    )
                }
            }

            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Download konnte nicht abgeschlossen werden.")
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun asJson(context: Context, release: UpdateRelease): String = JSONObject().apply {
        put("versionCode", release.versionCode)
        put("versionName", release.versionName)
        put("tag", release.tag)
        put("prerelease", release.prerelease)
        put("title", release.title)
        put("changelog", release.changelog)
        put("publishedAt", release.publishedAt)
        put("htmlUrl", release.htmlUrl)
        put("apkUrl", release.apk.url)
        put("apkSize", release.apk.size)
        put("sha256", release.apk.sha256)
        put("channel", release.channel.name)
        put("repo", AppSettings.getGithubRepo(context))
    }.toString(2)

    // ── GitHub API ────────────────────────────────────────────

    private fun fetchGithubReleases(context: Context, channel: UpdateChannel): List<UpdateRelease> {
        val repo = AppSettings.getGithubRepo(context)
        val url = "https://api.github.com/repos/$repo/releases?per_page=30"
        val json = JSONArray(fetchText(url))
        val result = mutableListOf<UpdateRelease>()

        for (index in 0 until json.length()) {
            val release = json.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false)) continue
            val prerelease = release.optBoolean("prerelease", false)
            if (channel == UpdateChannel.STABLE && prerelease) continue

            val parsed = parseGithubRelease(release, channel) ?: continue
            result += parsed
        }
        return result
    }

    private fun parseGithubRelease(release: JSONObject, channel: UpdateChannel): UpdateRelease? {
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val assetList = (0 until assets.length()).mapNotNull { index ->
            val asset = assets.optJSONObject(index) ?: return@mapNotNull null
            UpdateAsset(
                name = asset.optString("name"),
                url = asset.optString("browser_download_url"),
                size = asset.optLong("size", 0L),
            )
        }

        val manifest = assetList.firstOrNull { it.name == "update.json" }
        if (manifest != null) {
            runCatching {
                val parsed = parseManifestObject(
                    JSONObject(fetchText(manifest.url)),
                    channel,
                    fallbackHtml = release.optString("html_url"),
                    fallbackTag = release.optString("tag_name"),
                    fallbackPrerelease = release.optBoolean("prerelease", false),
                    fallbackPublishedAt = release.optString("published_at"),
                    fallbackChangelog = release.optString("body"),
                )
                if (parsed != null) return parsed
            }
        }

        val apk = assetList.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) &&
                (it.name.contains("universal", ignoreCase = true) ||
                    it.name.contains("release", ignoreCase = true))
        } ?: assetList.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null

        val tag = release.optString("tag_name").ifBlank { release.optString("name") }
        val versionName = tag.removePrefix("v").ifBlank { "0.0.0" }
        return UpdateRelease(
            versionCode = versionCodeFromVersionName(versionName),
            versionName = versionName,
            tag = tag,
            prerelease = release.optBoolean("prerelease", false),
            title = release.optString("name").ifBlank { "MTZ Tool $tag" },
            changelog = release.optString("body"),
            publishedAt = release.optString("published_at"),
            apk = apk,
            splits = assetList.filter { it.name.endsWith(".apk", true) && it != apk },
            htmlUrl = release.optString("html_url"),
            channel = if (release.optBoolean("prerelease", false)) {
                UpdateChannel.PRERELEASE
            } else {
                UpdateChannel.STABLE
            },
        )
    }

    // ── Custom manifest ───────────────────────────────────────

    private fun parseCustomManifest(url: String, channel: UpdateChannel): UpdateRelease? {
        val json = JSONObject(fetchText(url))
        val releases = json.optJSONArray("releases")
        if (releases != null) {
            return (0 until releases.length())
                .mapNotNull { parseManifestObject(releases.optJSONObject(it) ?: return@mapNotNull null, channel, url) }
                .maxWithOrNull(
                    compareBy<UpdateRelease> { if (it.versionCode > 0) it.versionCode else 0 }
                        .thenBy { it.publishedAt }
                )
        }
        return parseManifestObject(json, channel, url)
    }

    private fun parseManifestObject(
        json: JSONObject,
        channel: UpdateChannel,
        baseUrl: String? = null,
        fallbackHtml: String = "",
        fallbackTag: String = "",
        fallbackPrerelease: Boolean = false,
        fallbackPublishedAt: String = "",
        fallbackChangelog: String = "",
    ): UpdateRelease? {
        val prerelease = if (json.has("prerelease")) json.getBoolean("prerelease") else fallbackPrerelease
        if (channel == UpdateChannel.STABLE && prerelease) return null

        val versionName = json.optString("versionName")
            .ifBlank { json.optString("tag").removePrefix("v") }
            .ifBlank { fallbackTag.removePrefix("v") }
            .ifBlank { return null }
        val versionCode = json.optInt("versionCode", versionCodeFromVersionName(versionName))

        val apkObject = json.optJSONObject("apk")
        val apkUrl = json.optString("apkUrl")
            .ifBlank { apkObject?.optString("url").orEmpty() }
        if (apkUrl.isBlank()) return null
        val resolvedApkUrl = resolveUrl(baseUrl, apkUrl)
        val apkSize = json.optLong("apkSize", apkObject?.optLong("size", 0L) ?: 0L)
        val sha = json.optString("sha256").ifBlank { apkObject?.optString("sha256").orEmpty() }

        val splits = mutableListOf<UpdateAsset>()
        json.optJSONArray("splits")?.let { array ->
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val splitUrl = obj.optString("url")
                if (splitUrl.isNotBlank()) {
                    splits += UpdateAsset(
                        name = obj.optString("name"),
                        url = resolveUrl(baseUrl, splitUrl),
                        size = obj.optLong("size", 0L),
                        sha256 = obj.optString("sha256").ifBlank { null },
                    )
                }
            }
        }

        return UpdateRelease(
            versionCode = versionCode,
            versionName = versionName,
            tag = json.optString("tag").ifBlank { fallbackTag },
            prerelease = prerelease,
            title = json.optString("title").ifBlank { "MTZ Tool $versionName" },
            changelog = json.optString("changelog").ifBlank { fallbackChangelog },
            publishedAt = json.optString("publishedAt").ifBlank { fallbackPublishedAt },
            apk = UpdateAsset(
                name = apkObject?.optString("name").orEmpty().ifBlank { "MTZTool-$versionName.apk" },
                url = resolvedApkUrl,
                size = apkSize,
                sha256 = sha.ifBlank { null },
            ),
            splits = splits,
            htmlUrl = json.optString("htmlUrl").ifBlank { fallbackHtml },
            channel = if (prerelease) UpdateChannel.PRERELEASE else UpdateChannel.STABLE,
        )
    }

    // ── HTTP / helpers ────────────────────────────────────────

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION)
        return connection
    }

    private fun fetchText(url: String): String {
        val connection = openConnection(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $status: ${error.take(300)}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveUrl(baseUrl: String?, value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (baseUrl == null) return value
        return runCatching {
            URL(URL(baseUrl), value).toString()
        }.getOrDefault(value)
    }

    private fun versionCodeFromVersionName(name: String): Int {
        val cleaned = name.substringBefore('-').removePrefix("v")
        val parts = cleaned.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 0
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 1_000_000 + minor * 1_000 + patch
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.removePrefix("v").split('.', '-')
        val right = b.removePrefix("v").split('.', '-')
        for (index in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrNull(index)?.toIntOrNull() ?: 0
            val r = right.getOrNull(index)?.toIntOrNull() ?: 0
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
