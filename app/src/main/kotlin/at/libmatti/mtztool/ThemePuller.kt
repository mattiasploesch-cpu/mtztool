package at.libmatti.mtztool

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads the theme that is currently applied on the device and repackages it as
 * a portable `.mtz`.
 *
 * The MIUI/HyperOS Theme Manager materialises the active theme in
 * `/data/system/theme`. That directory is only readable with root or Shizuku,
 * so the pull uses the same privilege layer as applying a theme.
 */
object ThemePuller {

    const val THEME_DIR = RootThemeApplier.THEME_DIR

    private const val MAX_FILES = 4000
    private const val MAX_FILE_BYTES = 48L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

    data class PulledTheme(
        val fileName: String,
        val bytes: ByteArray,
        val entryCount: Int,
        val skippedFiles: Int,
        val notes: List<String>,
    ) {
        val size: Long get() = bytes.size.toLong()
    }

    fun isAvailable(): Boolean =
        ShizukuHelper.isReady() || RootThemeApplier.isAvailable()

    /**
     * Blocking read; call from [Dispatchers.IO]. [onProgress] receives
     * user-facing status lines.
     */
    fun pull(onProgress: (String) -> Unit = {}): Result<PulledTheme> = runCatching {
        if (!isAvailable()) {
            error(
                "Für das Auslesen des Gerätethemes werden Root oder Shizuku benötigt. " +
                    "Richte den Zugriff in den Einstellungen ein."
            )
        }

        onProgress("Lese Verzeichnis $THEME_DIR …")
        val listing = ShizukuHelper.executeShellCommand(
            "find '$THEME_DIR' -type f 2>/dev/null | head -n $MAX_FILES"
        )
        val paths = listing.stdout.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (paths.isEmpty()) {
            error(
                "Unter $THEME_DIR wurden keine Theme-Dateien gefunden. " +
                    "Ist ein Theme angewendet? (${listing.stderr.ifBlank { "keine Ausgabe" }})"
            )
        }

        val entries = mutableListOf<Pair<String, ByteArray>>()
        val notes = mutableListOf<String>()
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        var skipped = 0
        var total = 0L

        paths.forEachIndexed { index, path ->
            if (index % 25 == 0) {
                onProgress("Übertrage Dateien … ${index + 1}/${paths.size}")
            }
            val relative = path.removePrefix("$THEME_DIR/").trimStart('/')
            val target = targetFor(relative)
            if (target == null) {
                skipped++
                return@forEachIndexed
            }
            if (total > MAX_TOTAL_BYTES) return@forEachIndexed

            val bytes = ShizukuHelper.readFileBytes(path, MAX_FILE_BYTES).getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                skipped++
                return@forEachIndexed
            }
            total += bytes.size
            entries += target to bytes
        }

        if (entries.isEmpty()) {
            error(
                "Es konnten keine bekannten Theme-Komponenten gelesen werden. " +
                    "Das Layout des Theme-Managers wird möglicherweise nicht unterstützt."
            )
        }

        notes += "Quelle: $THEME_DIR"
        notes += "${entries.size} Dateien ausgelesen"
        if (skipped > 0) notes += "$skipped Dateien übersprungen"
        if (paths.size >= MAX_FILES) notes += "Dateiliste wurde bei $MAX_FILES Einträgen begrenzt"

        val metadata = ThemeComposer.Metadata(
            title = "Gerätetheme $date",
            author = "MTZ Tool (Pull)",
            description = "Vom Gerät ausgelesenes Theme ($date).",
            version = "1.0",
        )

        onProgress("Baue .mtz-Archiv …")
        val archive = ThemeComposer.buildArchive(metadata, entries)
        val fileName = "device-theme-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".mtz"
        PulledTheme(
            fileName = fileName,
            bytes = archive,
            entryCount = entries.size,
            skippedFiles = skipped,
            notes = notes,
        )
    }

    /**
     * Maps a path inside `/data/system/theme` onto the classic MTZ layout.
     * Unknown top level entries are skipped on purpose: configuration files
     * and cached images are not part of a portable theme.
     */
    private fun targetFor(relative: String): String? {
        if (relative.isBlank()) return null
        val top = relative.substringBefore('/')
        val known = ThemeComponents.MTZ_MAP.keys + ThemeComponents.MTZ_MAP.values
        return when {
            top == "wallpaper" -> relative
            top == "fonts" -> relative
            top == "lockscreen" -> relative
            top == "icons" -> relative
            top in known -> relative
            else -> null
        }
    }

    suspend fun pullAsync(onProgress: (String) -> Unit = {}): Result<PulledTheme> =
        withContext(Dispatchers.IO) { pull(onProgress) }

    /** Writes the pulled archive to a user selected document. */
    fun save(
        context: android.content.Context,
        uri: android.net.Uri,
        pulled: PulledTheme,
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(pulled.bytes) }
            ?: error("Zieldatei konnte nicht geöffnet werden.")
    }

    /** Convenience for debug logging of the archive size. */
    fun describe(pulled: PulledTheme): String {
        val output = ByteArrayOutputStream()
        output.write(pulled.bytes)
        return "${pulled.fileName} · ${pulled.entryCount} Dateien · ${pulled.size} Bytes"
    }
}
