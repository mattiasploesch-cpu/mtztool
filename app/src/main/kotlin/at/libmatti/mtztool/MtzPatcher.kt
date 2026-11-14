package at.libmatti.mtztool

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and rewrites classic .mtz archives (ZIP) without touching the
 * Theme Manager. Supports metadata editing, removing components and replacing
 * the system/lock screen wallpaper.
 */
object MtzPatcher {

    data class Entry(val name: String, val size: Long, val directory: Boolean)

    data class Metadata(
        val title: String,
        val author: String,
        val designer: String,
        val description: String,
        val version: String,
        val uiVersion: String = "17",
        val adapterVersion: String = "3.3",
    )

    data class Component(
        val entry: String,
        val code: String,
        val present: Boolean,
    )

    data class Analysis(
        val entries: List<Entry>,
        val metadata: Metadata,
        val components: List<Component>,
        val hasDescription: Boolean,
    )

    data class Patch(
        val metadata: Metadata,
        val removeEntries: Set<String> = emptySet(),
        val wallpaper: ByteArray? = null,
        val wallpaperExtension: String? = null,
        val lockWallpaper: ByteArray? = null,
        val lockWallpaperExtension: String? = null,
    )

    data class BuildResult(
        val bytes: ByteArray,
        val removedEntries: Int,
        val wallpaperReplaced: Boolean,
        val lockWallpaperReplaced: Boolean,
        val descriptionWritten: Boolean,
    )

    fun analyze(bytes: ByteArray, fallbackTitle: String = ""): Analysis {
        require(isZip(bytes)) {
            "Der MTZ-Patcher unterstützt nur klassische ZIP-basierte .mtz-Dateien."
        }
        val entries = readEntries(bytes)
        val names = entries.map { it.name }
        require(names.none { it.contains(".data/") }) {
            "MIUI-Backups (.data-Bibliothek) können noch nicht gepatcht werden. " +
                "Bitte eine klassische .mtz verwenden."
        }

        val metadata = parseMetadata(
            title = fallbackTitle.substringBeforeLast('.').ifBlank { fallbackTitle },
            author = "",
            version = "1.0",
            originalXml = readText(bytes, "description.xml"),
        )

        val components = buildList {
            for ((entry, code) in ThemeComponents.MTZ_MAP) {
                val present = hasEntryOrFolder(names, entry) ||
                    (code == "lockstyle" && names.any { isLockWallpaper(it) }) ||
                    (code == "wallpaper" && names.any { isSystemWallpaper(it) })
                if (present) {
                    add(
                        Component(
                            entry = if (code == "wallpaper") "wallpaper" else entry,
                            code = code,
                            present = true,
                        )
                    )
                }
            }
            if (hasEntryOrFolder(names, "fonts")) {
                add(Component(entry = "fonts", code = "fonts", present = true))
            }
        }.distinctBy { it.entry }

        return Analysis(
            entries = entries,
            metadata = metadata,
            components = components,
            hasDescription = "description.xml" in names,
        )
    }

    fun patch(bytes: ByteArray, patch: Patch): BuildResult {
        require(isZip(bytes)) { "Keine gültige .mtz (ZIP) ausgewählt." }
        val names = readEntries(bytes).map { it.name }

        var removed = 0
        var wallpaperReplaced = false
        var lockReplaced = false
        var descriptionWritten = false

        val output = ByteArrayOutputStream(bytes.size + 4096)
        ZipOutputStream(output).use { zip ->
            zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)

            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (shouldRemove(name, patch.removeEntries)) {
                        removed++
                        input.closeEntry()
                        entry = input.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        zip.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
                        zip.closeEntry()
                        input.closeEntry()
                        entry = input.nextEntry
                        continue
                    }

                    val data = input.readBytes()

                    when {
                        name == "description.xml" -> {
                            zip.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
                            zip.write(buildDescriptionXml(patch.metadata))
                            zip.closeEntry()
                            descriptionWritten = true
                        }

                        isSystemWallpaper(name) && patch.wallpaper != null &&
                            "wallpaper" !in patch.removeEntries -> {
                            val target = name.takeIf { it.startsWith("wallpaper/default_wallpaper") }
                                ?: "wallpaper/default_wallpaper.${patch.wallpaperExtension ?: "jpg"}"
                            zip.putNextEntry(ZipEntry(target).apply { time = System.currentTimeMillis() })
                            zip.write(patch.wallpaper)
                            zip.closeEntry()
                            wallpaperReplaced = true
                        }

                        isLockWallpaper(name) && patch.lockWallpaper != null &&
                            "lockscreen" !in patch.removeEntries -> {
                            zip.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
                            zip.write(patch.lockWallpaper)
                            zip.closeEntry()
                            lockReplaced = true
                        }

                        else -> {
                            zip.putNextEntry(ZipEntry(name).apply { time = System.currentTimeMillis() })
                            zip.write(data)
                            zip.closeEntry()
                        }
                    }

                    input.closeEntry()
                    entry = input.nextEntry
                }
            }

            // description.xml is always (re)written so metadata edits work even
            // when the original archive had no description at all.
            if (!descriptionWritten) {
                zip.putNextEntry(ZipEntry("description.xml").apply { time = System.currentTimeMillis() })
                zip.write(buildDescriptionXml(patch.metadata))
                zip.closeEntry()
                descriptionWritten = true
            }

            if (patch.wallpaper != null && !wallpaperReplaced &&
                "wallpaper" !in patch.removeEntries
            ) {
                zip.putNextEntry(
                    ZipEntry("wallpaper/default_wallpaper.${patch.wallpaperExtension ?: "jpg"}")
                        .apply { time = System.currentTimeMillis() }
                )
                zip.write(patch.wallpaper)
                zip.closeEntry()
                wallpaperReplaced = true
            }

            if (patch.lockWallpaper != null && !lockReplaced &&
                "lockscreen" !in patch.removeEntries
            ) {
                zip.putNextEntry(
                    ZipEntry("wallpaper/default_lock_wallpaper.${patch.lockWallpaperExtension ?: "jpg"}")
                        .apply { time = System.currentTimeMillis() }
                )
                zip.write(patch.lockWallpaper)
                zip.closeEntry()
                lockReplaced = true
            }

        }

        return BuildResult(
            bytes = output.toByteArray(),
            removedEntries = removed,
            wallpaperReplaced = wallpaperReplaced,
            lockWallpaperReplaced = lockReplaced,
            descriptionWritten = descriptionWritten,
        )
    }

    fun suggestedFileName(original: String, title: String): String {
        val base = sanitizeFileName(title.ifBlank { original.substringBeforeLast('.') })
        return "$base-patched.mtz"
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun isZip(bytes: ByteArray) =
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun readEntries(bytes: ByteArray): List<Entry> {
        val result = mutableListOf<Entry>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result += Entry(entry.name, entry.size, entry.isDirectory)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun readText(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == name) {
                    return String(zip.readBytes(), StandardCharsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun hasEntryOrFolder(names: List<String>, entry: String) =
        names.any { it == entry || it.startsWith("$entry/") }

    private fun isSystemWallpaper(name: String) =
        name.startsWith("wallpaper/default_wallpaper") && !name.contains("lock")

    private fun isLockWallpaper(name: String) =
        name.startsWith("wallpaper/default_lock_wallpaper") ||
            name.startsWith("wallpaper/lock_wallpaper")

    private fun shouldRemove(name: String, removeEntries: Set<String>): Boolean {
        for (entry in removeEntries) {
            when (entry) {
                "wallpaper" -> if (isSystemWallpaper(name)) return true
                "lockscreen" -> if (name == "lockscreen" || name.startsWith("lockscreen/") ||
                    isLockWallpaper(name)
                ) return true
                "fonts" -> if (name == "fonts" || name.startsWith("fonts/")) return true
                else -> if (name == entry || name.startsWith("$entry/")) return true
            }
        }
        return false
    }

    private fun parseMetadata(
        title: String,
        author: String,
        version: String,
        originalXml: String?,
    ): Metadata {
        val values = mutableMapOf<String, String>()
        if (originalXml != null) {
            for (tag in listOf(
                "title", "author", "designer", "description", "version",
                "uiVersion", "miuiAdapterVersion",
            )) {
                val regex = Regex("<$tag\\b[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
                regex.find(originalXml)?.groupValues?.getOrNull(1)?.let {
                    values[tag] = unescapeXml(it.trim())
                }
            }
        }
        return Metadata(
            title = values["title"].orEmpty().ifBlank { title },
            author = values["author"].orEmpty().ifBlank { author },
            designer = values["designer"].orEmpty(),
            description = values["description"].orEmpty(),
            version = values["version"].orEmpty().ifBlank { version },
            uiVersion = values["uiVersion"].orEmpty().ifBlank { "17" },
            adapterVersion = values["miuiAdapterVersion"].orEmpty().ifBlank { "3.3" },
        )
    }

    private fun buildDescriptionXml(metadata: Metadata): ByteArray {
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
            appendLine("  <miuiAdapterVersion>${escapeXml(metadata.adapterVersion)}</miuiAdapterVersion>")
            appendLine("</theme>")
        }
        return xml.toByteArray(StandardCharsets.UTF_8)
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() -> "webp"
        else -> "jpg"
    }
}
