package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.collections.iterator

/**
 * Reads a third party theme from a .mtz (ZIP), a MIUI .bak backup (header +
 * tar, optionally zlib compressed) or a ZIP that contains one of those.
 *
 * The result is a list of theme candidates in a normalized representation.
 * Genuine .bak files usually contain a complete .data library tree including
 * the original .mra rights files; those candidates can be imported 1:1.
 */
object ThemeSourceReader {

    private const val TAG = "ThemeSourceReader"

    data class Component(
        val code: String,
        val localId: String,
        val data: ByteArray,
    ) {
        val sha1: String by lazy { sha1(data) }
    }

    data class Preview(val name: String, val data: ByteArray)

    data class MraFile(val name: String, val data: ByteArray)

    enum class Kind { MTZ, BACKUP }

    data class Candidate(
        val localId: String,
        val assemblyId: String,
        val title: String,
        val author: String,
        val description: String,
        val version: String,
        val adapterVersion: String,
        val platform: Int,
        val themeHash: String,
        val size: Long,
        val components: List<Component>,
        val componentMeta: Map<String, ByteArray>,
        val previews: List<Preview>,
        val mras: List<MraFile>,
        val themeMrc: ByteArray?,
        val themeMrm: ByteArray?,
        val kind: Kind,
    )

    data class ReadResult(
        val candidates: List<Candidate>,
        val notes: List<String> = emptyList(),
    )

    // ── Public entry points ──────────────────────────────────

    fun read(context: Context, uri: Uri, cacheDir: File, fallbackTitle: String): ReadResult {
        val source = File(cacheDir, "source.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Kann Quelldatei nicht lesen")
        return read(source, cacheDir, cleanTitle(fallbackTitle), 0)
    }

    fun read(file: File, cacheDir: File, fallbackTitle: String, depth: Int = 0): ReadResult {
        if (MiuiBackup.looksLikeBackup(file)) {
            val tar = MiuiBackup.open(file)
            return try {
                readLibrary(tar, fallbackTitle)
            } finally {
                tar.close()
            }
        }

        val header = ByteArray(2)
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count <= 0) break
                read += count
            }
        }
        if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            return readZip(file, cacheDir, fallbackTitle, depth)
        }

        throw IllegalArgumentException(
            "Unbekanntes Dateiformat. Unterstützt werden .mtz/.zip und MIUI-.bak."
        )
    }

    // ── ZIP / .mtz ───────────────────────────────────────────

    private fun readZip(file: File, cacheDir: File, fallbackTitle: String, depth: Int): ReadResult {
        ZipArchive(file).use { archive ->
            val names = archive.names

            if (names.any { it.contains(".data/meta/theme/") }) {
                return readLibrary(archive, fallbackTitle)
            }

            val nested = names.filter {
                (it.endsWith(".bak", ignoreCase = true) || it.endsWith(".mtz", ignoreCase = true)) &&
                    !it.startsWith("preview/")
            }
            if (nested.isNotEmpty() && depth < 3) {
                val candidates = mutableListOf<Candidate>()
                val notes = mutableListOf<String>()
                nested.forEachIndexed { index, name ->
                    val extension = name.substringAfterLast('.', "bin")
                    val temp = File(cacheDir, "nested-$depth-$index.$extension")
                    val bytes = archive.read(name) ?: return@forEachIndexed
                    temp.writeBytes(bytes)
                    val result = read(temp, cacheDir, fallbackTitle, depth + 1)
                    candidates += result.candidates
                    notes += result.notes
                    temp.delete()
                }
                if (candidates.isNotEmpty()) {
                    notes.add(0, "${nested.size} Archiv(e) im ZIP gefunden")
                    return ReadResult(candidates, notes)
                }
            }

            return readMtz(archive, file, fallbackTitle)
        }
    }

    // ── HyperOS .data library tree ───────────────────────────

    private fun readLibrary(archive: ArchiveReader, fallbackTitle: String): ReadResult {
        val names = archive.names
        val marker = ".data/"

        fun relative(name: String): String = name.substringAfter(marker, "")

        val themeMetaNames = names.filter {
            relative(it).startsWith("meta/theme/") && it.endsWith(".mrm")
        }
        if (themeMetaNames.isEmpty()) {
            throw IllegalArgumentException(
                "Das Backup enthält keine Theme-Metadaten (meta/theme)."
            )
        }

        val mraNames = names.filter {
            relative(it).contains("rights/") && it.endsWith(".mra", ignoreCase = true)
        }
        val mras = mraNames.mapNotNull { name ->
            archive.read(name)?.let { MraFile(name.substringAfterLast('/'), it) }
        }

        val candidates = mutableListOf<Candidate>()
        for (metaName in themeMetaNames) {
            val metaBytes = archive.read(metaName) ?: continue
            val json = try {
                JSONObject(String(metaBytes, StandardCharsets.UTF_8))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot parse $metaName", e)
                continue
            }

            val localId = json.optString("localId")
                .ifBlank { metaName.substringAfterLast('/').removeSuffix(".mrm") }

            val subArray = json.optJSONArray("subResources") ?: JSONArray()
            val components = mutableListOf<Component>()
            val componentMeta = mutableMapOf<String, ByteArray>()

            for (i in 0 until subArray.length()) {
                val sub = subArray.optJSONObject(i) ?: continue
                val code = sub.optString("resourceCode")
                val subId = sub.optString("localId")
                if (code.isBlank() || subId.isBlank() || code == "theme") continue
                val content = archive.read(archiveName(names, "content/$code/$subId.mrc")) ?: continue
                components += Component(code, subId, content)
                archive.read(archiveName(names, "meta/$code/$subId.mrm"))?.let {
                    componentMeta[code] = it
                }
            }

            if (components.isEmpty()) {
                continue
            }

            val previewPrefix = "preview/theme/$localId/"
            val previews = names
                .filter { relative(it).startsWith(previewPrefix) && isImage(it) }
                .mapNotNull { name ->
                    archive.read(name)?.let { Preview(name.substringAfterLast('/'), it) }
                }
                .distinctBy { it.name }

            val themeMrc = archive.read(archiveName(names, "content/theme/$localId.mrc"))
            val themeHash = json.optString("hash").ifBlank {
                sha1(components.map { it.sha1 }.sorted().joinToString("").toByteArray())
            }
            val title = localized(json, "titles").ifBlank { fallbackTitle }
            val author = localized(json, "authors").ifBlank { localized(json, "designers") }
            val description = localized(json, "descriptions")

            candidates += Candidate(
                localId = localId,
                assemblyId = json.optString("assemblyId").ifBlank { localId },
                title = title,
                author = author,
                description = description,
                version = json.optString("version").ifBlank { "1.0" },
                adapterVersion = json.optString("miuiAdapterVersion").ifBlank { "3.2" },
                platform = json.optInt("platform", 17),
                themeHash = themeHash,
                size = json.optLong("size", components.sumOf { it.data.size.toLong() }),
                components = components,
                componentMeta = componentMeta,
                previews = previews,
                mras = mras,
                themeMrc = themeMrc,
                themeMrm = metaBytes,
                kind = Kind.BACKUP,
            )
        }

        if (candidates.isEmpty()) {
            throw IllegalArgumentException("Im Backup wurde kein vollständiges Theme gefunden.")
        }

        val notes = mutableListOf<String>()
        if (candidates.size > 1) {
            notes += "Backup enthält ${candidates.size} Themen"
        }
        return ReadResult(candidates, notes)
    }

    /** Finds the real archive name for a .data relative path (prefix independent). */
    private fun archiveName(names: List<String>, relativePath: String): String {
        return names.firstOrNull { it.endsWith("/$relativePath") || it.endsWith(relativePath) }
            ?: relativePath
    }

    // ── Classic .mtz ─────────────────────────────────────────

    private fun readMtz(archive: ArchiveReader, sourceFile: File, fallbackTitle: String): ReadResult {
        val names = archive.names
        val description = parseDescription(archive.read("description.xml"))

        val title = description["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = description["author"] ?: description["designer"] ?: ""
        val descr = description["description"] ?: "Importiert mit MTZ Tool"
        val version = description["version"]?.takeIf { it.isNotBlank() } ?: "1.0"
        val platform = (description["uiVersion"]?.trim()?.toIntOrNull() ?: 17).coerceAtLeast(17)
        val adapter = description["miuiAdapterVersion"]?.takeIf { it.isNotBlank() } ?: "3.3"

        val components = mutableListOf<Component>()

        fun add(code: String, data: ByteArray?) {
            if (data == null || data.isEmpty()) return
            if (components.any { it.code == code }) return
            components += Component(code, UUID.randomUUID().toString(), data)
        }

        // 1) Store format: top level nested ZIPs
        for ((entry, code) in ThemeComponents.MTZ_MAP) {
            if (entry !in names) continue
            val data = archive.read(entry) ?: continue
            if (data.size >= 2 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte()) {
                add(code, data)
            }
        }

        // 2) Folder format: entries below "<entry>/"
        for ((entry, code) in ThemeComponents.MTZ_MAP) {
            if (components.any { it.code == code }) continue
            val prefix = "$entry/"
            val folderEntries = names.filter { it.startsWith(prefix) && !it.endsWith("/") }
            if (folderEntries.isNotEmpty()) {
                add(code, buildNestedZip(archive, prefix, folderEntries))
            }
        }

        // 3) Wallpaper images
        findWallpaper(names, "wallpaper/default_wallpaper")?.let { add("wallpaper", archive.read(it)) }
        findWallpaper(names, "wallpaper/default_lock_wallpaper")
            ?.let { add("lockscreen", archive.read(it)) }

        // 4) Fonts (raw TTF/OTF, own sub resource)
        val fontEntries = names.filter { it.startsWith("fonts/") && !it.endsWith("/") }
        if (fontEntries.isNotEmpty() && components.none { it.code == "fonts" }) {
            val font = fontEntries.firstOrNull {
                val lower = it.lowercase()
                lower.endsWith(".ttf") || lower.endsWith(".otf")
            }
            add(
                "fonts",
                if (font != null) archive.read(font)
                else buildNestedZip(archive, "fonts/", fontEntries)
            )
        }

        if (components.isEmpty()) {
            throw IllegalArgumentException("In der .mtz wurden keine Theme-Bestandteile gefunden.")
        }

        val previews = names
            .filter { it.startsWith("preview/") && isImage(it) }
            .mapNotNull { name ->
                archive.read(name)?.let { Preview(name.substringAfterLast('/'), it) }
            }
            .distinctBy { it.name }

        val mras = names
            .filter { it.endsWith(".mra", ignoreCase = true) }
            .mapNotNull { name ->
                archive.read(name)?.let { MraFile(name.substringAfterLast('/'), it) }
            }

        val sortedHashes = components.map { it.sha1 }.sorted().joinToString("")
        val themeHash = sha1(sortedHashes.toByteArray())

        return ReadResult(
            listOf(
                Candidate(
                    localId = UUID.randomUUID().toString(),
                    assemblyId = UUID.randomUUID().toString(),
                    title = title,
                    author = author,
                    description = descr,
                    version = version,
                    adapterVersion = adapter,
                    platform = platform,
                    themeHash = themeHash,
                    size = components.sumOf { it.data.size.toLong() },
                    components = components,
                    componentMeta = emptyMap(),
                    previews = previews,
                    mras = mras,
                    themeMrc = sourceFile.readBytes(),
                    themeMrm = null,
                    kind = Kind.MTZ,
                )
            )
        )
    }

    // ── Helpers ──────────────────────────────────────────────

    fun sha1(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(data)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 0x0F])
            }
        }
    }

    private fun localized(json: JSONObject, key: String): String {
        val objectValue = json.optJSONObject(key) ?: return ""
        return objectValue.optString("fallback")
            .ifBlank { objectValue.optString("en_US") }
            .ifBlank {
                val keys = objectValue.keys()
                var result = ""
                while (keys.hasNext() && result.isBlank()) {
                    result = objectValue.optString(keys.next())
                }
                result
            }
    }

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private fun findWallpaper(names: List<String>, base: String): String? {
        val extensions = listOf("jpg", "jpeg", "png", "webp", "")
        for (extension in extensions) {
            val name = if (extension.isEmpty()) base else "$base.$extension"
            if (name in names) return name
        }
        return names.firstOrNull { it.startsWith("$base.") }
    }

    private fun parseDescription(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            val keys = listOf(
                "version", "uiVersion", "author", "designer", "title",
                "description", "miuiAdapterVersion"
            )
            buildMap {
                for (key in keys) {
                    val nodes = document.getElementsByTagName(key)
                    if (nodes.length > 0) {
                        val value = nodes.item(0).textContent?.trim().orEmpty()
                        if (value.isNotEmpty()) put(key, value)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse description.xml", e)
            emptyMap()
        }
    }

    private fun cleanTitle(name: String): String {
        return name
            .substringBeforeLast('.', name)
            .ifBlank { "Custom Theme" }
    }

    @Suppress("unused")
    private fun Node.textOrEmpty(): String = textContent?.trim().orEmpty()
}
