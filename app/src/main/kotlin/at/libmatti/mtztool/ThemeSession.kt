package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * The single in-memory theme workspace.
 *
 * The main screen imports an .mtz/.bak exactly once and keeps the bytes in RAM.
 * Every tool can then reuse the same theme without asking for the file again.
 */
object ThemeSession {

    private const val MAX_BYTES = 512L * 1024L * 1024L
    private const val INSPECT_LIMIT = 96L * 1024L * 1024L

    data class Theme(
        val name: String,
        val bytes: ByteArray,
        val sourceUri: String?,
        val openedAt: Long = System.currentTimeMillis(),
        val sha1: String = ThemeSourceReader.sha1(bytes),
    ) {
        val size: Long get() = bytes.size.toLong()
        val displayTitle: String get() = name.substringBeforeLast('.').ifBlank { name }
    }

    data class Summary(
        val title: String,
        val author: String,
        val version: String,
        val componentCount: Int,
        val previewCount: Int,
        val fullTheme: Boolean,
        val rightsReason: String,
        val candidateCount: Int,
    )

    var current by mutableStateOf<Theme?>(null)
        private set

    var summary by mutableStateOf<Summary?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    fun set(theme: Theme) {
        current = theme
        summary = null
        lastError = null
    }

    fun clear() {
        current = null
        summary = null
        lastError = null
    }

    /**
     * Reads a theme from a content/file URI into memory. The whole import is
     * done on the IO dispatcher; [onProgress] is called with user-facing lines.
     */
    suspend fun open(
        context: Context,
        uri: Uri,
        displayName: String? = null,
        onProgress: (String) -> Unit = {},
    ): Result<Theme> = withContext(Dispatchers.IO) {
        importing = true
        lastError = null
        try {
            val name = (displayName ?: ThemeApplier.getFileName(context, uri) ?: "theme.mtz")
                .substringAfterLast('/')

            onProgress("Lese $name in den Arbeitsspeicher …")
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                readLimited(input)
            } ?: throw IOException("Die Theme-Datei konnte nicht gelesen werden.")

            if (bytes.isEmpty()) throw IOException("Die Theme-Datei ist leer.")

            val theme = Theme(
                name = name,
                bytes = bytes,
                sourceUri = uri.toString(),
            )
            set(theme)
            onProgress("Theme geladen: ${formatBytes(theme.size)}")

            if (theme.size <= INSPECT_LIMIT) {
                onProgress("Analysiere Theme-Inhalte …")
                inspect(context, theme)
            } else {
                onProgress("Große Datei – Analyse wird übersprungen.")
            }

            AppLog.success("Theme „${theme.displayTitle}“ geladen (${formatBytes(theme.size)}).")
            Result.success(theme)
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            AppLog.error("Theme konnte nicht geladen werden: $lastError")
            Result.failure(e)
        } finally {
            importing = false
        }
    }

    /** Loads a theme from raw bytes (used by the patcher and bug reports). */
    suspend fun openBytes(
        context: Context,
        name: String,
        bytes: ByteArray,
        sourceUri: String? = null,
        inspectTheme: Boolean = true,
    ): Result<Theme> = withContext(Dispatchers.IO) {
        importing = true
        lastError = null
        try {
            if (bytes.isEmpty()) throw IOException("Die Theme-Datei ist leer.")
            val theme = Theme(name = name, bytes = bytes, sourceUri = sourceUri)
            set(theme)
            if (inspectTheme && theme.size <= INSPECT_LIMIT) {
                inspect(context, theme)
            }
            AppLog.success("„${theme.displayTitle}“ im Workspace aktualisiert.")
            Result.success(theme)
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            AppLog.error("Workspace-Update fehlgeschlagen: $lastError")
            Result.failure(e)
        } finally {
            importing = false
        }
    }

    /** Parses the current theme and stores a compact summary for the UI. */
    suspend fun inspect(context: Context, theme: Theme? = current) =
        withContext(Dispatchers.IO) {
            val target = theme ?: return@withContext
            if (target.size > INSPECT_LIMIT) return@withContext
            val directory = File(context.cacheDir, "theme-session-inspect")
            try {
                directory.deleteRecursively()
                directory.mkdirs()
                val file = File(directory, "source.mtz")
                file.writeBytes(target.bytes)
                val parsed = ThemeSourceReader.read(
                    file,
                    directory,
                    target.name.substringBeforeLast('.').ifBlank { "Theme" },
                )
                val best = parsed.candidates
                    .map { it to ThemeRights.assess(it) }
                    .maxWithOrNull(
                        compareBy<Pair<ThemeSourceReader.Candidate, ThemeRights.Assessment>> {
                            if (it.second.fullTheme) 1 else 0
                        }.thenBy { it.first.components.size }
                    )
                if (best != null) {
                    val (candidate, assessment) = best
                    summary = Summary(
                        title = candidate.title,
                        author = candidate.author,
                        version = candidate.version,
                        componentCount = candidate.components.size,
                        previewCount = candidate.previews.size,
                        fullTheme = assessment.fullTheme,
                        rightsReason = assessment.reason,
                        candidateCount = parsed.candidates.size,
                    )
                    if (assessment.fullTheme) {
                        AppLog.info("Rechteprüfung: vollständiges Theme (${candidate.components.size} Komponenten).")
                    } else {
                        AppLog.warn("Rechteprüfung: ${assessment.reason}")
                    }
                }
            } catch (e: Exception) {
                AppLog.warn("Theme-Analyse fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_BYTES) {
                throw IOException("Die Theme-Datei ist zu groß (max. 512 MB).")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble() / 1024.0
        val units = listOf("KB", "MB", "GB")
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }
}
