package at.libmatti.mtztool.github

import android.content.Context
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.ThemeBrowser
import at.libmatti.mtztool.ThemeComposer
import at.libmatti.mtztool.ThemeLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Upload flow for themes and app themes.
 *
 * A pull request contains only files below ``themes/uploads/<slug>/`` or
 * ``app-themes/uploads/<slug>/``. The workflow validates the diff, and after
 * the owner's approval publishes the canonical JSON (and the theme file) into
 * the real folder.
 */
object ThemeUpload {

    suspend fun existingThemeNames(context: Context): Result<Set<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val names = GitHubApi.directory(context, "themes")
                    .getOrThrow()
                    .filter { it.type == "file" && it.name.endsWith(".mtz", true) }
                    .map { it.name.lowercase(Locale.US) }
                    .toMutableSet()
                ThemeBrowser.manifestThemes(context).getOrNull()?.forEach {
                    names += it.name.lowercase(Locale.US)
                }
                names
            }
        }

    suspend fun submitTheme(
        context: Context,
        item: ThemeLibrary.Item,
        title: String,
        description: String,
        preview: ByteArray?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(GitHubApi.isSignedIn(context)) { "Bitte zuerst mit GitHub anmelden." }
            require(title.trim().length in 3..80) { "Der Titel braucht 3–80 Zeichen." }
            val bytes = ThemeLibrary.read(context, item)
                ?: throw IllegalStateException("Das Theme konnte nicht gelesen werden.")
            val existing = existingThemeNames(context).getOrThrow()
            if (item.name.lowercase(Locale.US) in existing) {
                throw IllegalStateException("Dieses Theme existiert bereits im Store.")
            }
            val slug = slug(title)
            val branch = "theme-upload/$slug-${System.currentTimeMillis()}"
            val dir = "themes/uploads/$slug"
            val previewName = preview?.let { previewFileName(it) }
            val meta = JSONObject().apply {
                put("schema", 1)
                put("kind", "theme")
                put("slug", slug)
                put("title", title.trim())
                put("description", description.trim().take(2000))
                put("author", AppSettings.getGithubLogin(context))
                put("fileName", "theme.mtz")
                put("sha256", ThemeLibrary.sha256(bytes))
                put("size", bytes.size)
                put("preview", previewName ?: JSONObject.NULL)
                put("submittedAt", now())
            }
            GitHubApi.uploadFile(
                context, "$dir/theme.mtz", bytes, branch, "theme: upload $title",
            ).getOrThrow()
            if (preview != null && previewName != null) {
                GitHubApi.uploadFile(
                    context, "$dir/$previewName", preview, branch, "theme: preview $title",
                ).getOrThrow()
            }
            GitHubApi.uploadFile(
                context, "$dir/meta.json", meta.toString(2).toByteArray(), branch,
                "theme: metadata $title",
            ).getOrThrow()
            val pr = GitHubApi.createPullRequest(
                context = context,
                title = "theme: ${title.trim()}",
                head = branch,
                base = "main",
                body = buildString {
                    appendLine("## Theme upload")
                    appendLine()
                    appendLine("| Feld | Wert |")
                    appendLine("| --- | --- |")
                    appendLine("| Titel | ${title.trim()} |")
                    appendLine("| Autor | @${AppSettings.getGithubLogin(context)} |")
                    appendLine("| Größe | ${bytes.size} Bytes |")
                    appendLine("| SHA-256 | `${ThemeLibrary.sha256(bytes)}` |")
                    appendLine("| Preview | ${previewName ?: "–"} |")
                    appendLine()
                    appendLine("Der Workflow prüft ausschließlich Dateien unter `$dir/`.")
                    appendLine("Nach dem Approval werden `themes/$slug.mtz` und")
                    appendLine("`themes/$slug.json` von der Action angelegt.")
                },
                draft = false,
            ).getOrThrow()
            AppLog.success("Theme-Upload-PR #${pr.number} erstellt.")
            pr.htmlUrl
        }
    }

    suspend fun submitAppTheme(
        context: Context,
        name: String,
        description: String,
        colors: Map<String, String>,
        preview: ByteArray,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(GitHubApi.isSignedIn(context)) { "Bitte zuerst mit GitHub anmelden." }
            require(name.trim().length in 3..60) { "Der Name braucht 3–60 Zeichen." }
            require(colors.isNotEmpty()) { "Mindestens eine Farbe festlegen." }
            val slug = slug(name)
            val branch = "app-theme-upload/$slug-${System.currentTimeMillis()}"
            val dir = "app-themes/uploads/$slug"
            val json = JSONObject().apply {
                put("schema", 1)
                put("kind", "app-theme")
                put("slug", slug)
                put("name", name.trim())
                put("description", description.trim().take(1000))
                put("author", AppSettings.getGithubLogin(context))
                put("colors", JSONObject(colors.toMap()))
                put("preview", "preview.png")
                put("submittedAt", now())
            }
            GitHubApi.uploadFile(
                context, "$dir/theme.json", json.toString(2).toByteArray(), branch,
                "app-theme: upload $name",
            ).getOrThrow()
            GitHubApi.uploadFile(
                context, "$dir/preview.png", preview, branch, "app-theme: preview $name",
            ).getOrThrow()
            val pr = GitHubApi.createPullRequest(
                context = context,
                title = "app-theme: ${name.trim()}",
                head = branch,
                base = "main",
                body = buildString {
                    appendLine("## App-Theme")
                    appendLine()
                    appendLine("- Name: **${name.trim()}**")
                    appendLine("- Autor: @${AppSettings.getGithubLogin(context)}")
                    appendLine("- Farben: ${colors.size}")
                    appendLine("- Preview: `preview.png`")
                    appendLine()
                    appendLine("Nur Dateien unter `$dir/` sind erlaubt. Nach dem Approval")
                    appendLine("erzeugt die Action `app-themes/$slug.json`.")
                },
                draft = false,
            ).getOrThrow()
            AppLog.success("App-Theme-Upload-PR #${pr.number} erstellt.")
            pr.htmlUrl
        }
    }

    fun slug(value: String): String =
        ThemeComposer.sanitizeFileName(value)
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "theme" }
            .take(60)

    private fun previewFileName(bytes: ByteArray): String =
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            "preview.jpg"
        } else {
            "preview.png"
        }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
}
