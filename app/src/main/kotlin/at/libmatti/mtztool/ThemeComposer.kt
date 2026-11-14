package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a classic `.mtz` from arbitrary user files.
 *
 * Everything a theme can contain is accepted: fonts, wallpapers, XML files,
 * audio, icons and even complete component folders. The target path inside the
 * archive is guessed from the file type and name, but every entry can be
 * edited in the theme studio before the archive is written.
 */
object ThemeComposer {

    private const val MAX_FILE_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 320L * 1024L * 1024L

    data class Metadata(
        val title: String,
        val author: String,
        val designer: String = "",
        val description: String = "",
        val version: String = "1.0",
        val uiVersion: String = "17",
    )

    data class Source(
        val uri: String,
        val displayName: String,
        val targetPath: String,
    )

    /** Reads name and size for a picked document and suggests a target path. */
    fun describe(context: Context, uri: Uri): Source {
        val name = queryName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "datei"
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return Source(
            uri = uri.toString(),
            displayName = name,
            targetPath = suggestTarget(name, mime),
        )
    }

    fun suggestTarget(displayName: String, mimeType: String?): String {
        val name = sanitizeFileName(displayName)
        val extension = name.substringAfterLast('.', "").lowercase()
        val lower = name.lowercase()
        return when {
            extension in setOf("ttf", "otf", "ttc") -> "fonts/$name"
            extension in setOf("jpg", "jpeg", "png", "webp") -> when {
                lower.contains("lock") -> "wallpaper/default_lock_wallpaper.$extension"
                lower.contains("wallpaper") || lower.contains("hintergrund") ->
                    "wallpaper/default_wallpaper.$extension"
                else -> "wallpaper/$name"
            }
            extension in setOf("mp3", "ogg", "wav", "m4a", "flac") -> when {
                lower.contains("ring") -> "ringtone/$name"
                lower.contains("alarm") -> "alarm/$name"
                lower.contains("notif") -> "notification/$name"
                lower.contains("boot") -> "bootaudio/$name"
                else -> "bootaudio/$name"
            }
            extension == "xml" -> "values/$name"
            extension == "zip" || extension == "mtz" -> name
            extension == "apk" -> "icons/$name"
            mimeType?.startsWith("font/") == true -> "fonts/$name"
            mimeType?.startsWith("image/") == true -> "wallpaper/$name"
            else -> "misc/$name"
        }
    }

    /**
     * Writes the archive. Entries keep the order they are given in; duplicate
     * target paths are renamed instead of overwriting each other.
     */
    fun buildArchive(
        metadata: Metadata,
        entries: List<Pair<String, ByteArray>>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val used = mutableSetOf<String>()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry("description.xml").apply { time = System.currentTimeMillis() })
            zip.write(descriptionXml(metadata))
            zip.closeEntry()

            entries.forEach { (rawPath, bytes) ->
                var path = rawPath.trim().trimStart('/')
                if (path.isBlank()) return@forEach
                if (!used.add(path)) {
                    val dot = path.lastIndexOf('.')
                    val base = if (dot > 0) path.substring(0, dot) else path
                    val extension = if (dot > 0) path.substring(dot) else ""
                    var counter = 2
                    while (!used.add("$base-$counter$extension")) counter++
                    path = "$base-$counter$extension"
                }
                zip.putNextEntry(ZipEntry(path).apply { time = System.currentTimeMillis() })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Reads all selected documents and returns a ready-to-write `.mtz`. */
    fun build(
        context: Context,
        metadata: Metadata,
        sources: List<Source>,
        onProgress: (String) -> Unit = {},
    ): ByteArray {
        require(metadata.title.isNotBlank()) { "Der Theme-Titel darf nicht leer sein." }
        require(sources.isNotEmpty()) { "Füge mindestens eine Datei hinzu." }

        var total = 0L
        val entries = sources.mapIndexed { index, source ->
            val uri = Uri.parse(source.uri)
            onProgress("Lese ${source.displayName} (${index + 1}/${sources.size}) …")
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(128 * 1024)
                var fileBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    fileBytes += read
                    if (fileBytes > MAX_FILE_BYTES) {
                        throw IOException("${source.displayName} ist zu groß (max. 64 MB).")
                    }
                    total += read
                    if (total > MAX_TOTAL_BYTES) {
                        throw IOException("Das Theme ist zu groß (max. 320 MB).")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw IOException("${source.displayName} konnte nicht gelesen werden.")

            if (bytes.isEmpty()) throw IOException("${source.displayName} ist leer.")
            source.targetPath.ifBlank { source.displayName } to bytes
        }

        onProgress("Schreibe ${entries.size} Dateien in das MTZ-Archiv …")
        return buildArchive(metadata, entries)
    }

    private fun descriptionXml(metadata: Metadata): ByteArray {
        val xml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<theme>")
            appendLine("  <version>${escapeXml(metadata.version)}</version>")
            appendLine("  <uiVersion>${escapeXml(metadata.uiVersion)}</uiVersion>")
            appendLine("  <author>${escapeXml(metadata.author)}</author>")
            if (metadata.designer.isNotBlank()) {
                appendLine("  <designer>${escapeXml(metadata.designer)}</designer>")
            }
            appendLine("  <title>${escapeXml(metadata.title)}</title>")
            appendLine("  <description>${escapeXml(metadata.description)}</description>")
            appendLine("  <miuiAdapterVersion>3.3</miuiAdapterVersion>")
            appendLine("</theme>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    fun sanitizeFileName(value: String): String =
        value.substringAfterLast('/')
            .replace(Regex("[\\\\:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "datei" }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
