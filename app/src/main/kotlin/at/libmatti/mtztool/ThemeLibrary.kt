package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Persistent theme library.
 *
 * Imported themes are copied into the app's private files directory, so they
 * are available after a restart without the original document permission and
 * without the workspace being loaded. Themes stay until the user deletes them.
 */
object ThemeLibrary {

    private const val DIR = "theme-library"
    private const val INDEX = "index.json"
    private const val MAX_BYTES = 512L * 1024L * 1024L

    private val lock = Any()

    data class Item(
        val id: String,
        val name: String,
        val size: Long,
        val addedAt: Long,
        val source: String,
        val sha256: String,
    ) {
        val bytesLabel: String
            get() = when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", size / 1024.0)
                else -> String.format(java.util.Locale.US, "%.1f MB", size / 1024.0 / 1024.0)
            }
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun indexFile(context: Context) = File(directory(context), INDEX)

    fun list(context: Context): List<Item> = synchronized(lock) {
        readIndex(context).sortedByDescending { it.addedAt }
    }

    fun import(
        context: Context,
        name: String,
        bytes: ByteArray,
        source: String = "lokal",
    ): Item {
        require(bytes.isNotEmpty()) { "Die Theme-Datei ist leer." }
        require(bytes.size <= MAX_BYTES) { "Die Theme-Datei ist zu groß (max. 512 MB)." }

        val hash = sha256(bytes)
        val safeName = name.substringAfterLast('/').ifBlank { "theme.mtz" }
        val id = hash.take(16) + "-" + System.currentTimeMillis().toString(36)
        val item = Item(
            id = id,
            name = if (safeName.endsWith(".mtz", true)) safeName else "$safeName.mtz",
            size = bytes.size.toLong(),
            addedAt = System.currentTimeMillis(),
            source = source,
            sha256 = hash,
        )
        synchronized(lock) {
            File(directory(context), id).writeBytes(bytes)
            val items = readIndex(context).toMutableList()
            items.removeAll { it.sha256 == hash }
            items.add(item)
            writeIndex(context, items)
        }
        AppLog.success("„${item.name}“ in die Bibliothek importiert.")
        return item
    }

    fun importUri(
        context: Context,
        uri: Uri,
        displayName: String? = null,
        source: String = "Import",
    ): Result<Item> = runCatching {
        val name = displayName
            ?: ThemeApplier.getFileName(context, uri)
            ?: uri.lastPathSegment
            ?: "theme.mtz"
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(128 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_BYTES) throw IOException("Die Theme-Datei ist zu groß (max. 512 MB).")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException("Die Theme-Datei konnte nicht gelesen werden.")
        import(context, name, bytes, source)
    }

    fun read(context: Context, item: Item): ByteArray? =
        runCatching { File(directory(context), item.id).readBytes() }.getOrNull()

    fun delete(context: Context, item: Item): Boolean = synchronized(lock) {
        File(directory(context), item.id).delete()
        val items = readIndex(context).filterNot { it.id == item.id }
        writeIndex(context, items)
        AppLog.info("„${item.name}“ aus der Bibliothek gelöscht.")
        true
    }

    fun totalSize(context: Context): Long = list(context).sumOf { it.size }

    fun contains(context: Context, sha256: String): Boolean =
        list(context).any { it.sha256 == sha256 }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    private fun readIndex(context: Context): List<Item> {
        val file = indexFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { json ->
                    val id = json.optString("id")
                    if (id.isBlank()) return@let null
                    Item(
                        id = id,
                        name = json.optString("name", "theme.mtz"),
                        size = json.optLong("size"),
                        addedAt = json.optLong("addedAt"),
                        source = json.optString("source", "lokal"),
                        sha256 = json.optString("sha256"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("size", item.size)
                    put("addedAt", item.addedAt)
                    put("source", item.source)
                    put("sha256", item.sha256)
                }
            )
        }
        indexFile(context).writeText(array.toString(2))
    }
}
