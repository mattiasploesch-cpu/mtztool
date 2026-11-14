package at.libmatti.mtztool

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Applies an .mtz theme directly into /data/system/theme through root.
 *
 * This is the app's own replacement for the Theme Manager's internal
 * screenshot/apply task:
 * no Theme Manager activity is started, no Theme Store UI is opened. The
 * component files are written with the same destination paths, ownership and
 * modes the Theme Manager uses. The actual theme change is picked up by
 * restarting SystemUI and the launcher and by sending the MIUI theme-changed
 * broadcast.
 *
 * The writes are executed as UID 6101 (`system_theme`) inside the
 * `u:r:theme:s0` SELinux domain. That is the only process context besides the
 * Theme Manager itself which has write access to /data/system/theme.
 */
object RootThemeApplier {

    private const val TAG = "RootThemeApplier"

    const val THEME_DIR = "/data/system/theme"
    private const val THEME_UID = 6101
    private const val THEME_DOMAIN = "u:r:theme:s0"

    private const val MODE_FILE = 0b111101101 // 0755
    private const val MODE_DIR = 0b111111101 // 0775
    private const val MODE_RUNTIME_DIR = 0b111000000 // 0700
    private const val MODE_RUNTIME_FILE = 0b110000000 // 0600

    private const val CACHE_DIR = "root-theme-apply"

    // ── Public API ────────────────────────────────────────────

    /** Where the theme that should be applied comes from. */
    sealed class Source {
        data class LocalFile(val file: File, val displayName: String) : Source()
        data class ContentUri(val uri: Uri, val displayName: String) : Source()
        data class Asset(val path: String) : Source()
    }

    data class Outcome(
        val title: String,
        val appliedComponents: List<String>,
        val appliedFiles: List<String>,
        val skippedEntries: List<String>,
        val notes: List<String>,
    ) {
        fun summary(): String = buildString {
            append("Theme „").append(title).append("“ angewendet")
            if (appliedComponents.isNotEmpty()) {
                append("\nKomponenten: ")
                append(appliedComponents.joinToString(", ") { ThemeComponents.displayName(it) })
            }
            if (appliedFiles.isNotEmpty()) {
                append("\nDateien: ").append(appliedFiles.size)
            }
            if (notes.isNotEmpty()) {
                append("\n").append(notes.joinToString("\n"))
            }
        }
    }

    /**
     * True when the device grants us root and the theme SELinux domain can be
     * entered. This check is cached for the lifetime of the process.
     */
    fun isAvailable(): Boolean = try {
        ensureThemeDomain() || runRoot("id -u").stdout.trim() == "0"
    } catch (e: Exception) {
        Log.w(TAG, "Root check failed", e)
        false
    }

    /** Applies a theme selected by the user (content:// or file://). */
    fun applyUri(
        context: Context,
        uri: Uri,
        displayName: String,
        progress: (String) -> Unit = {},
    ): Outcome {
        val cache = prepareCache(context)
        try {
            val file = File(cache, "source.mtz")
            progress("Kopiere Theme-Datei in den Cache …")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Die Theme-Datei konnte nicht gelesen werden.")
            return applyLocalFile(context, file, displayName, progress)
        } finally {
            cache.deleteRecursively()
        }
    }

    /** Applies a theme that is already available as a local file. */
    fun applyFile(
        context: Context,
        file: File,
        displayName: String = file.name,
        progress: (String) -> Unit = {},
    ): Outcome {
        if (!file.isFile || file.length() == 0L) {
            throw IOException("Die Theme-Datei existiert nicht oder ist leer.")
        }
        return applyLocalFile(context, file, displayName, progress)
    }

    /**
     * Applies the theme bundled with the app.
     *
     * Accepted locations, in this order:
     *   assets/theme.mtz
     *   assets/themes/theme.mtz
     *   assets/themes/<first *.mtz>
     */
    fun applyBundled(context: Context, progress: (String) -> Unit = {}): Outcome {
        val asset = findBundledAsset(context)
            ?: throw IOException(
                "Kein mitgeliefertes Theme gefunden. Lege eine .mtz unter " +
                    "app/src/main/assets/themes/ ab."
            )
        val cache = prepareCache(context)
        try {
            val file = File(cache, asset.substringAfterLast('/'))
            progress("Entpacke mitgeliefertes Theme ($asset) …")
            context.assets.open(asset).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            return applyLocalFile(context, file, file.name, progress)
        } finally {
            cache.deleteRecursively()
        }
    }

    /** Returns the asset path of the bundled theme, or null if none exists. */
    fun findBundledAsset(context: Context): String? {
        val direct = listOf("theme.mtz", "themes/theme.mtz")
        for (candidate in direct) {
            try {
                context.assets.open(candidate).close()
                return candidate
            } catch (_: IOException) {
                // try next
            }
        }
        val themes = try {
            context.assets.list("themes").orEmpty()
        } catch (_: IOException) {
            emptyArray()
        }
        return themes
            .filter { it.endsWith(".mtz", ignoreCase = true) || it.endsWith(".zip", ignoreCase = true) }
            .sorted()
            .firstOrNull()
            ?.let { "themes/$it" }
    }

    // ── Apply pipeline ───────────────────────────────────────

    private fun applyLocalFile(
        context: Context,
        file: File,
        displayName: String,
        progress: (String) -> Unit,
    ): Outcome {
        progress("Prüfe Theme-Archiv …")
        ZipFile(file).use { zip ->
            val plan = buildPlan(zip, displayName)
            progress("Theme: ${plan.title} (${plan.writes.size} Dateien)")

            val appliedFiles = copyPlan(plan, zip, progress)
            fixPermissions(plan)

            plan.notes += writeRuntimeData(plan)

            applyWallpapers(context, plan, progress)

            progress("Theme-Wechsel bekannt machen …")
            triggerThemeRefresh(plan.notes)

            return Outcome(
                title = plan.title,
                appliedComponents = plan.appliedComponents.distinct(),
                appliedFiles = appliedFiles,
                skippedEntries = plan.skipped,
                notes = plan.notes,
            )
        }
    }

    // ── Plan ─────────────────────────────────────────────────

    private data class Write(
        val relativePath: String,
        val zipEntry: String? = null,
        val bytes: ByteArray? = null,
        val mode: Int = MODE_FILE,
    )

    private data class Plan(
        val title: String,
        val writes: LinkedHashMap<String, Write>,
        val appliedComponents: MutableList<String>,
        val skipped: MutableList<String>,
        val notes: MutableList<String>,
        val homeWallpaper: ByteArray?,
        val lockWallpaper: ByteArray?,
        val themeHash: String,
    )

    private fun buildPlan(zip: ZipFile, fallbackTitle: String): Plan {
        val entries = LinkedHashMap<String, ZipEntry>()
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (!entry.isDirectory) {
                entries[normalizeEntryName(entry.name)] = entry
            }
        }
        val names = entries.keys.toList()
        val writes = LinkedHashMap<String, Write>()
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        fun add(relativePath: String, zipEntry: String? = null, bytes: ByteArray? = null) {
            writes[relativePath] = Write(relativePath, zipEntry, bytes)
        }

        fun addTopLevel(mtzEntry: String, code: String) {
            val exact = entries[mtzEntry]
            if (exact != null) {
                add(mtzEntry, zipEntry = mtzEntry)
                applied += code
                return
            }
            val prefix = "$mtzEntry/"
            val children = names.filter { it.startsWith(prefix) && it.length > prefix.length }
            if (children.isNotEmpty()) {
                add(mtzEntry, bytes = buildNestedZip(zip, prefix, children))
                applied += code
            }
        }

        // 1) Known components in the classic .mtz layout.
        for ((entry, code) in ThemeComponents.MTZ_MAP) {
            addTopLevel(entry, code)
        }

        // 2) Additional component archives (clock widgets, photo frames, ...).
        val topLevelNames = (
            names.filter { !it.contains('/') } +
                names.filter { it.contains('/') }.map { it.substringBefore('/') }
            ).distinct()
        val extraComponents = topLevelNames.filter { name ->
            name.startsWith("clock_") ||
                name.startsWith("photoframe_") ||
                name.startsWith("photo_frame_") ||
                name == "alarmscreen" ||
                name == "dynamic_wallpaper" ||
                name == "dynamicvideowallpaper" ||
                name.startsWith("com.") ||
                name == "framework-res" ||
                name == "framework-miui-res" ||
                name == "miui.systemui.plugin"
        }
        for (name in extraComponents) {
            if (writes.containsKey(name) || name == "description.xml") continue
            addTopLevel(name, name)
        }

        // Theme Manager also installs the 2x4 clock as the 3x4 clock when the
        // theme only ships clock_2x4 (see ApplyThemeTask).
        writes["clock_2x4"]?.let { clock ->
            if (!writes.containsKey("clock_3x4")) {
                writes["clock_3x4"] = clock.copy(relativePath = "clock_3x4")
                if ("clock_3x4" !in applied) applied += "clock_3x4"
            }
        }

        // 3) Wallpapers have fixed destination names in /data/system/theme.
        var homeWallpaper: ByteArray? = null
        var lockWallpaper: ByteArray? = null
        val homeEntry = findWallpaperEntry(names, "wallpaper/default_wallpaper")
        val lockEntry = findWallpaperEntry(names, "wallpaper/default_lock_wallpaper")
        if (homeEntry != null) {
            add("wallpaper", zipEntry = homeEntry)
            homeWallpaper = readEntry(zip, homeEntry)
            applied += "wallpaper"
        }
        if (lockEntry != null) {
            add("lock_wallpaper", zipEntry = lockEntry)
            lockWallpaper = readEntry(zip, lockEntry)
        }

        // 4) Fonts. Theme Manager reads fonts/Roboto-Regular.ttf and
        //    fonts/DroidSansFallback.ttf, so normalise the common names.
        var regularFontAssigned = false
        val fontEntries = names.filter { it.startsWith("fonts/") && it.length > 6 }
            .sortedBy { if (it.contains("fallback", ignoreCase = true)) 1 else 0 }
        for (name in fontEntries) {
            val lower = name.lowercase()
            if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue
            val base = name.substringAfterLast('/')
            val destination = when {
                base.contains("fallback", ignoreCase = true) -> "fonts/DroidSansFallback.ttf"
                !regularFontAssigned -> {
                    regularFontAssigned = true
                    "fonts/Roboto-Regular.ttf"
                }
                else -> "fonts/$base"
            }
            add(destination, zipEntry = name)
            if ("fonts" !in applied) applied += "fonts"
        }

        // 5) Boot animation/audio, ringtones and rights files keep their paths.
        for (name in names) {
            when {
                name.startsWith("boots/") -> {
                    val destination = when {
                        name.startsWith("boots/bootanimation") -> "boots/bootanimation.zip"
                        name.startsWith("boots/bootaudio") -> "boots/bootaudio.mp3"
                        else -> name
                    }
                    add(destination, zipEntry = name)
                }
                name.startsWith("ringtones/") -> {
                    val base = name.substringAfterLast('/')
                    val destination = when {
                        base.startsWith("ringtone") -> "ringtones/ringtone.mp3"
                        base.startsWith("notification") -> "ringtones/notification.mp3"
                        base.startsWith("alarm") -> "ringtones/alarm.mp3"
                        else -> name
                    }
                    add(destination, zipEntry = name)
                    val code = destination.substringAfterLast('/').substringBeforeLast('.')
                    if (code !in applied) applied += code
                }
                name.startsWith("rights/") -> add(name, zipEntry = name)
            }
        }

        // 6) description.xml belongs into the applied theme directory.
        if (entries.containsKey("description.xml")) {
            add("description.xml", zipEntry = "description.xml")
        }

        // 7) Everything that we cannot map safely is reported, not copied.
        for (name in names) {
            val topLevel = name.substringBefore('/')
            if (name == "description.xml") continue
            if (writes.containsKey(topLevel) || writes.containsKey(name)) continue
            if (topLevel == "preview" || topLevel == "wallpaper") continue
            if (topLevel == "fonts" || topLevel == "boots" || topLevel == "ringtones" || topLevel == "rights") continue
            if (topLevel == ".runtime" || topLevel == "rights") continue
            if (writes.keys.any { it == topLevel }) continue
            if (writes.keys.any { it.startsWith("$topLevel/") }) continue
            skipped += name
        }

        if (writes.isEmpty()) {
            throw IOException("In der .mtz wurden keine anwendbaren Theme-Bestandteile gefunden.")
        }

        val title = parseTitle(zip) ?: fallbackTitle.substringBeforeLast('.').ifBlank { "Custom Theme" }
        val hash = ThemeSourceReader.sha1(
            writes.entries.joinToString("|") { (path, write) ->
                val size = write.bytes?.size
                    ?: write.zipEntry?.let { zip.getEntry(it)?.size }
                    ?: 0L
                "$path:$size"
            }.toByteArray()
        )

        return Plan(
            title = title,
            writes = writes,
            appliedComponents = applied,
            skipped = skipped,
            notes = mutableListOf(),
            homeWallpaper = homeWallpaper,
            lockWallpaper = lockWallpaper,
            themeHash = hash,
        )
    }

    private fun normalizeEntryName(name: String): String {
        var result = name.replace('\\', '/')
        while (result.startsWith("./")) result = result.substring(2)
        return result.trimStart('/')
    }

    private fun findWallpaperEntry(names: List<String>, base: String): String? {
        val candidates = listOf("$base.jpg", "$base.jpeg", "$base.png", "$base.webp")
        candidates.firstOrNull { it in names }?.let { return it }
        return names.firstOrNull { it.startsWith("$base.") }
    }

    private fun buildNestedZip(zip: ZipFile, prefix: String, names: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zos ->
            for (name in names) {
                val entry = zip.getEntry(name) ?: continue
                if (entry.isDirectory) continue
                val relative = name.removePrefix(prefix)
                if (relative.isBlank() || relative.startsWith("/") || relative.contains("../")) continue
                zos.putNextEntry(ZipEntry(relative))
                zip.getInputStream(entry).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun readEntry(zip: ZipFile, name: String): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        return try {
            zip.getInputStream(entry).use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read $name", e)
            null
        }
    }

    private fun parseTitle(zip: ZipFile): String? {
        val entry = zip.getEntry("description.xml") ?: return null
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val document = factory.newDocumentBuilder()
                .parse(zip.getInputStream(entry))
            val localized = document.getElementsByTagName("titles").item(0) as? Element
            if (localized != null) {
                val titles = localized.getElementsByTagName("title")
                for (i in 0 until titles.length) {
                    val value = titles.item(i).textContent?.trim().orEmpty()
                    if (value.isNotEmpty()) return value
                }
            }
            val titles = document.getElementsByTagName("title")
            if (titles.length > 0) {
                titles.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse description.xml", e)
            null
        }
    }

    // ── Writing ──────────────────────────────────────────────

    private fun copyPlan(plan: Plan, zip: ZipFile, progress: (String) -> Unit): List<String> {
        val directories = plan.writes.keys
            .mapNotNull { it.substringBeforeLast('/', "").takeIf { dir -> dir.isNotEmpty() } }
            .toSortedSet()

        val mkdirScript = buildString {
            if (directories.isNotEmpty()) {
                val paths = directories.map { "$THEME_DIR/$it" }
                append("mkdir -p ").append(paths.joinToString(" ") { q(it) })
                append(" && chmod ").append(octal(MODE_DIR)).append(' ')
                append(paths.joinToString(" ") { q(it) })
                append(" && ")
            }
            append("mkdir -p ").append(q("$THEME_DIR/.runtime"))
            append(" && chmod ").append(octal(MODE_RUNTIME_DIR)).append(' ')
            append(q("$THEME_DIR/.runtime"))
        }
        val mkdir = runThemeCommand(mkdirScript)
        if (mkdir.exitCode != 0) {
            throw IOException(
                "Theme-Verzeichnis konnte nicht vorbereitet werden: " +
                    mkdir.errorText()
            )
        }

        val applied = mutableListOf<String>()
        for (write in plan.writes.values) {
            progress("Schreibe ${write.relativePath} …")
            val destination = "$THEME_DIR/${write.relativePath}"
            val script = "cat > ${q(destination)} && chmod ${octal(write.mode)} ${q(destination)}"
            val input = when {
                write.bytes != null -> ByteArrayInputStream(write.bytes)
                write.zipEntry != null -> zip.getInputStream(zip.getEntry(write.zipEntry)!!)
                else -> throw IOException("Leerer Eintrag: ${write.relativePath}")
            }
            val result = runThemeCommand(script, input)
            if (result.exitCode != 0) {
                throw IOException(
                    "Schreiben von ${write.relativePath} fehlgeschlagen: ${result.errorText()}"
                )
            }
            applied += write.relativePath
        }
        return applied
    }

    private fun fixPermissions(plan: Plan) {
        val files = plan.writes.keys.map { "$THEME_DIR/$it" }
        val directories = plan.writes.keys
            .mapNotNull { it.substringBeforeLast('/', "").takeIf { dir -> dir.isNotEmpty() } }
            .toSortedSet()
            .map { "$THEME_DIR/$it" }

        val script = buildString {
            if (files.isNotEmpty()) {
                append("chmod ").append(octal(MODE_FILE)).append(' ')
                append(files.joinToString(" ") { q(it) })
                append("; ")
            }
            if (directories.isNotEmpty()) {
                append("chmod ").append(octal(MODE_DIR)).append(' ')
                append(directories.joinToString(" ") { q(it) })
                append("; ")
            }
            append("chmod ").append(octal(MODE_RUNTIME_DIR)).append(' ')
            append(q("$THEME_DIR/.runtime"))
        }
        val chmod = runThemeCommand(script)
        if (chmod.exitCode != 0) {
            Log.w(TAG, "chmod failed: ${chmod.errorText()}")
        }

        if (!useThemeDomainCached) {
            val owner = runRoot("chown -R $THEME_UID:$THEME_UID ${q(THEME_DIR)}")
            if (owner.exitCode != 0) {
                Log.w(TAG, "chown failed: ${owner.errorText()}")
            }
        }
        // Re-apply the file_contexts labels. Ignored when root cannot set them;
        // files created by the theme domain already inherit the correct label.
        runRoot("restorecon -R ${q(THEME_DIR)} >/dev/null 2>&1 || true")
    }

    // ── Runtime metadata ─────────────────────────────────────

    private fun writeRuntimeData(plan: Plan): List<String> {
        val path = "$THEME_DIR/.runtime/runtime_data.properties"
        val existingResult = runThemeCommand("cat ${q(path)} 2>/dev/null || true")
        val properties = LinkedHashMap<String, String>()
        existingResult.stdout.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val index = line.indexOf('=')
                if (index > 0) {
                    properties[line.substring(0, index).trim()] = line.substring(index + 1).trim()
                }
            }

        val now = System.currentTimeMillis().toString()
        val codes = plan.appliedComponents.distinct() + "theme"
        for (code in codes) {
            val metaPath = "/system/media/theme/.data/meta/$code/default.mrm"
            properties["theme_runtime_meta_path-$code"] = metaPath
            properties["theme_runtime_rights_path-$code"] = "NULL_PLACE_HOLDER"
            properties["theme_runtime_local_id-$code"] = plan.themeHash.take(16)
            properties["theme_runtime_online_id-$code"] = "0"
            properties["theme_runtime_name-$code"] = plan.title
            properties["theme_runtime_hash-$code"] = plan.themeHash
            properties["theme_runtime_update_time-$code"] = now
        }

        val content = buildString {
            append("# Written by MTZ Tool\n")
            properties.forEach { (key, value) ->
                append(key.replace("\\", "\\\\").replace("=", "\\="))
                append('=')
                append(value.replace("\\", "\\\\"))
                append('\n')
            }
        }
        val result = runThemeCommand(
            "cat > ${q(path)} && chmod ${octal(MODE_RUNTIME_FILE)} ${q(path)}",
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
        )
        return if (result.exitCode == 0) {
            listOf("Theme-Metadaten aktualisiert")
        } else {
            listOf("Hinweis: Theme-Metadaten konnten nicht geschrieben werden (${result.errorText()})")
        }
    }

    // ── Wallpaper ────────────────────────────────────────────

    private fun applyWallpapers(context: Context, plan: Plan, progress: (String) -> Unit) {
        val home = plan.homeWallpaper
        val lock = plan.lockWallpaper
        if (home == null && lock == null) return

        progress("Setze Hintergrundbild …")
        val rootAvailable = prepareRoot()
        if (home != null && !setSingleWallpaper(context, home, "home", plan, rootAvailable)) {
            plan.notes += "Hinweis: Startbildschirm-Hintergrundbild konnte nicht gesetzt werden."
        }
        if (lock != null && !setSingleWallpaper(context, lock, "lock", plan, rootAvailable)) {
            plan.notes += "Hinweis: Sperrbildschirm-Hintergrundbild konnte nicht gesetzt werden."
        }
    }

    private fun setSingleWallpaper(
        context: Context,
        bytes: ByteArray,
        which: String,
        plan: Plan,
        rootAvailable: Boolean,
    ): Boolean {
        val label = if (which == "lock") "Sperrbildschirm" else "Startbildschirm"

        // Xiaomi's wallpaper service may expose a root shell command. Only try
        // it when the help output actually advertises it; AOSP does not.
        if (rootAvailable && wallpaperShellSupportsSetImage()) {
            val remote = "/data/local/tmp/mtz-wallpaper-$which-" +
                System.currentTimeMillis() + imageExtension(bytes)
            try {
                if (pushTempFile(bytes, remote) && setWallpaperViaRoot(remote, which)) {
                    plan.notes += "$label-Hintergrundbild gesetzt"
                    return true
                }
            } finally {
                runRoot("rm -f ${q(remote)}")
            }
        }

        // Standard API fallback. Works without shell command support and does
        // not open any Themes or wallpaper UI.
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
            val flag = if (which == "lock") WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flag)
            plan.notes += "$label-Hintergrundbild gesetzt (App-API)"
            true
        } catch (e: Exception) {
            Log.w(TAG, "Wallpaper apply failed for $which", e)
            false
        }
    }

    private fun wallpaperShellSupportsSetImage(): Boolean {
        if (wallpaperSetImageChecked) return wallpaperSetImageSupported
        synchronized(this) {
            if (wallpaperSetImageChecked) return wallpaperSetImageSupported
            val help = runRoot("cmd wallpaper 2>&1")
            val text = help.stdout + "\n" + help.stderr
            wallpaperSetImageSupported = text.contains("set-image")
            wallpaperSetImageChecked = true
            Log.i(TAG, "cmd wallpaper set-image supported: $wallpaperSetImageSupported")
            return wallpaperSetImageSupported
        }
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> ".jpg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> ".png"
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() -> ".webp"
        else -> ".img"
    }

    private fun pushTempFile(bytes: ByteArray, remote: String): Boolean {
        val result = runRoot(
            "cat > ${q(remote)} && chmod 0644 ${q(remote)}",
            ByteArrayInputStream(bytes),
        )
        return result.exitCode == 0
    }

    private fun setWallpaperViaRoot(remote: String, which: String): Boolean {
        fun succeeded(result: CommandResult): Boolean {
            if (result.exitCode != 0) return false
            val output = result.stdout + "\n" + result.stderr
            return !output.contains("Error", ignoreCase = true) &&
                !output.contains("Exception", ignoreCase = true) &&
                !output.contains("Unknown command", ignoreCase = true)
        }

        val result = runRoot("cmd wallpaper set-image --which $which ${q(remote)}")
        if (succeeded(result)) return true
        // Some builds only understand the older syntax without --which. It
        // sets both home and lock screen, which is acceptable as a fallback.
        val legacy = runRoot("cmd wallpaper set-image ${q(remote)}")
        return succeeded(legacy)
    }

    // ── Theme refresh ────────────────────────────────────────

    private fun triggerThemeRefresh(notes: MutableList<String>) {
        if (!prepareRoot()) {
            notes += "Hinweis: Root nicht verfügbar, Theme-Wechsel konnte nicht signalisiert werden."
            return
        }
        val script = """
            am broadcast -a miui.intent.action.ACTION_THEME_CHANGED >/dev/null 2>&1
            sleep 1
            pkill -f 'com[.]android[.]systemui' >/dev/null 2>&1 || killall com.android.systemui >/dev/null 2>&1
            pkill -f 'com[.]miui[.]home' >/dev/null 2>&1 || killall com.miui.home >/dev/null 2>&1
            sleep 2
            am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1
            true
        """.trimIndent()
        val result = runRoot(script)
        if (result.exitCode == 0) {
            notes += "SystemUI und Launcher neu gestartet"
        } else {
            notes += "Hinweis: Neustart von SystemUI/Launcher fehlgeschlagen (${result.errorText()})"
        }
    }

    // ── Root helpers ─────────────────────────────────────────

    private data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        fun errorText(): String = when {
            stderr.isNotBlank() -> stderr
            stdout.isNotBlank() -> stdout
            else -> "Exit-Code $exitCode"
        }
    }

    @Volatile
    private var themeDomainChecked = false

    @Volatile
    private var useThemeDomainCached = false

    @Volatile
    private var wallpaperSetImageChecked = false

    @Volatile
    private var wallpaperSetImageSupported = false

    @Volatile
    private var rootChecked = false

    @Volatile
    private var rootAvailableCached = false

    private fun ensureThemeDomain(): Boolean {
        if (themeDomainChecked) return useThemeDomainCached
        synchronized(this) {
            if (themeDomainChecked) return useThemeDomainCached
            val result = runCommand(
                arrayOf("su", "-Z", THEME_DOMAIN, THEME_UID.toString(), "-c", "id -u"),
                null,
                90,
            )
            useThemeDomainCached = result.exitCode == 0 &&
                result.stdout.lineSequence().lastOrNull()?.trim() == THEME_UID.toString()
            themeDomainChecked = true
            Log.i(TAG, "Theme domain available: $useThemeDomainCached (${result.errorText()})")
            return useThemeDomainCached
        }
    }

    private fun prepareRoot(): Boolean {
        if (rootChecked) return rootAvailableCached
        synchronized(this) {
            if (rootChecked) return rootAvailableCached
            val result = runRoot("id -u")
            rootAvailableCached = result.exitCode == 0 &&
                result.stdout.lineSequence().lastOrNull()?.trim() == "0"
            rootChecked = true
            Log.i(TAG, "Root available: $rootAvailableCached (${result.errorText()})")
            return rootAvailableCached
        }
    }

    private fun runThemeCommand(script: String, input: InputStream? = null): CommandResult {
        return if (ensureThemeDomain()) {
            runCommand(
                arrayOf("su", "-Z", THEME_DOMAIN, THEME_UID.toString(), "-c", script),
                input,
                120,
            )
        } else {
            runRoot(script, input)
        }
    }

    private fun runRoot(script: String, input: InputStream? = null): CommandResult =
        runCommand(arrayOf("su", "-c", script), input, 120)

    private fun runCommand(
        command: Array<String>,
        input: InputStream?,
        timeoutSeconds: Long,
    ): CommandResult {
        val process = try {
            ProcessBuilder(*command).start()
        } catch (e: Exception) {
            return CommandResult(-1, "", e.message ?: "su konnte nicht gestartet werden")
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = Thread {
            try {
                process.inputStream.bufferedReader().use { stdout.append(it.readText()) }
            } catch (_: Exception) {
            }
        }
        val stderrThread = Thread {
            try {
                process.errorStream.bufferedReader().use { stderr.append(it.readText()) }
            } catch (_: Exception) {
            }
        }
        stdoutThread.start()
        stderrThread.start()

        try {
            if (input != null) {
                process.outputStream.use { output ->
                    input.use { it.copyTo(output) }
                }
            } else {
                process.outputStream.close()
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(-1, stdout.toString().trim(), "Zeitüberschreitung (${timeoutSeconds}s)")
            }
            stdoutThread.join(2000)
            stderrThread.join(2000)
            return CommandResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim())
        } catch (e: Exception) {
            process.destroyForcibly()
            return CommandResult(-1, stdout.toString().trim(), e.message ?: "Unbekannter Fehler")
        }
    }

    private fun prepareCache(context: Context): File =
        File(context.cacheDir, CACHE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }

    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun octal(mode: Int): String = "0" + Integer.toOctalString(mode)
}
