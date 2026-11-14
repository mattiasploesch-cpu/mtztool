package at.libmatti.mtztool

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** Import error that still knows which theme components were rejected. */
class ImportFailure(
    override val message: String,
    val missingCodes: List<String> = emptyList(),
) : Exception(message)

/** Result of a library import, used by the UI to show the warning dialog. */
data class LibraryImport(
    val title: String,
    val fullTheme: Boolean,
    val appliedCodes: List<String>,
    val droppedCodes: List<String>,
    val reason: String,
    val message: String,
)

/**
 * Writes a normalized theme candidate into the Theme Manager's offline
 * catalog (.data). A full theme is only written when the rights check passed;
 * otherwise only the rights exempt components are written and the protected
 * ones are replaced by empty placeholders so the detail page stays intact.
 */
object ThemeLibraryWriter {

    private const val TAG = "ThemeLibraryWriter"

    /** Root of the Theme Manager's offline catalog. */
    const val THEME_DATA_DIR =
        "/sdcard/Android/data/com.android.thememanager/files/MIUI/theme/.data"

    private const val PERMISSION_HINT =
        "Tipp: Falls der Zugriff weiterhin verweigert wird, lösche die App-Daten der " +
            "Themes-App (Einstellungen → Apps → Alle Apps → Themes → Speicher → Daten löschen) " +
            "und versuche den Import erneut."

    private const val BACKUP_HINT =
        "Tipp: Eine .bak-Sicherung kannst du auch über die Systemeinstellungen " +
            "wiederherstellen: Einstellungen → Über das Gerät → Sichern und Wiederherstellen."

    /** Adds user-facing hints for permission problems and .bak backups to a failure text. */
    fun withImportHints(message: String, fileName: String?): String {
        val lower = message.lowercase()
        val permissionDenied = listOf(
            "permission denied",
            "keine schreibrechte",
            "zugriff verweigert",
            "nicht erreichbar",
            "mkdir",
        ).any { lower.contains(it) }
        val isBackup = fileName?.endsWith(".bak", ignoreCase = true) == true

        return buildList {
            add(message)
            if (permissionDenied) add(PERMISSION_HINT)
            if (isBackup) add(BACKUP_HINT)
        }.joinToString("\n\n")
    }

    fun write(
        candidate: ThemeSourceReader.Candidate,
        assessment: ThemeRights.Assessment,
    ): Result<LibraryImport> {
        val full = assessment.fullTheme
        val kept = if (full) {
            candidate.components
        } else {
            candidate.components.filter { ThemeComponents.isExempt(it.code) }
        }

        if (!full && kept.isEmpty()) {
            val codes = candidate.components.map { it.code }.distinct()
            val names = codes
                .map { ThemeComponents.displayName(it) }
                .joinToString(", ")
            return Result.failure(
                ImportFailure(
                    "Dieses Theme enthält nur geschützte Bestandteile ($names). " +
                        "Ohne gültige Xiaomi-Rechte-Datei (.mra) kann es nicht importiert werden.",
                    missingCodes = codes,
                )
            )
        }

        // Empty placeholders keep the detail page for icons/statusbar intact.
        val placeholders = if (full) {
            emptyList()
        } else {
            candidate.components
                .map { it.code }
                .filter { it == "icons" || it == "statusbar" }
                .distinct()
                .map {
                    ThemeSourceReader.Component(
                        code = it,
                        localId = "${candidate.localId}-empty-$it",
                        data = ByteArray(0),
                    )
                }
        }

        val directories = buildSet {
            add("$THEME_DATA_DIR/content/theme")
            add("$THEME_DATA_DIR/meta/theme")
            add("$THEME_DATA_DIR/preview/theme/${candidate.localId}")
            kept.forEach {
                add("$THEME_DATA_DIR/content/${it.code}")
                add("$THEME_DATA_DIR/meta/${it.code}")
            }
            placeholders.forEach {
                add("$THEME_DATA_DIR/content/${it.code}")
                add("$THEME_DATA_DIR/meta/${it.code}")
            }
            if (full) add("$THEME_DATA_DIR/rights/theme")
        }

        // Existing folders (e.g. the MIUI directory) are used as-is; only create what is missing.
        // MIUI creates them as 0700 system_theme, so an unprivileged Shizuku shell may have no
        // access at all. In that case fail with a clear message instead of trying to gain root.
        val problem = prepareTarget(directories)
        if (problem != null) {
            return Result.failure(
                Exception(
                    "Zielordner $THEME_DATA_DIR ist nicht erreichbar oder konnte nicht " +
                        "erstellt werden: $problem – bitte Shizuku-Zugriff prüfen."
                )
            )
        }

        val failures = mutableListOf<String>()

        fun put(path: String, bytes: ByteArray) {
            val result = ShizukuHelper.copyStreamToPath(ByteArrayInputStream(bytes), path)
            if (result.isFailure) {
                failures += path
                Log.e(TAG, "Write failed: $path", result.exceptionOrNull())
            }
        }

        // Theme container (genuine library themes use an empty .mrc)
        put(
            "$THEME_DATA_DIR/content/theme/${candidate.localId}.mrc",
            candidate.themeMrc ?: ByteArray(0),
        )

        // Real components
        for (component in kept) {
            val contentPath =
                "$THEME_DATA_DIR/content/${component.code}/${component.localId}.mrc"
            put(contentPath, component.data)

            val meta = candidate.componentMeta[component.code]
                ?: buildComponentMrm(
                    candidate = candidate,
                    code = component.code,
                    localId = component.localId,
                    hash = component.sha1,
                    size = component.data.size.toLong(),
                    previewNames = previewsFor(candidate, component.code),
                )
            put("$THEME_DATA_DIR/meta/${component.code}/${component.localId}.mrm", meta)
        }

        // Placeholders for dropped protected components
        for (placeholder in placeholders) {
            put(
                "$THEME_DATA_DIR/content/${placeholder.code}/${placeholder.localId}.mrc",
                ByteArray(0),
            )
            put(
                "$THEME_DATA_DIR/meta/${placeholder.code}/${placeholder.localId}.mrm",
                buildComponentMrm(
                    candidate = candidate,
                    code = placeholder.code,
                    localId = placeholder.localId,
                    hash = ThemeSourceReader.sha1(ByteArray(0)),
                    size = 0,
                    previewNames = previewsFor(candidate, placeholder.code),
                ),
            )
        }

        // Previews
        val previews = candidate.previews
            .map { sanitize(it.name) to it.data }
            .distinctBy { it.first }
        for ((name, data) in previews) {
            put("$THEME_DATA_DIR/preview/theme/${candidate.localId}/$name", data)
        }
        val previewNames = previews.map { it.first }

        // Rights are only written for a full import
        if (full) {
            writeRights(candidate, assessment, ::put)
        }

        if (failures.isNotEmpty()) {
            return Result.failure(
                Exception("Schreiben fehlgeschlagen (${failures.size} Datei(en)): ${failures.first()}")
            )
        }

        val themeHash = if (full) {
            assessment.validMras.firstOrNull()?.info?.themeHash ?: candidate.themeHash
        } else {
            candidate.themeHash
        }

        val themeMrm = if (full && candidate.themeMrm != null) {
            candidate.themeMrm
        } else {
            buildThemeMrm(candidate, kept, placeholders, themeHash, previewNames)
        }
        put("$THEME_DATA_DIR/meta/theme/${candidate.localId}.mrm", themeMrm)

        if (failures.isNotEmpty()) {
            return Result.failure(
                Exception("Schreiben der Metadaten fehlgeschlagen: ${failures.first()}")
            )
        }

        val appliedCodes = kept.map { it.code }.distinct()
            .sortedBy { ThemeComponents.displayName(it) }
        val droppedCodes = if (full) {
            emptyList()
        } else {
            candidate.components
                .filterNot { ThemeComponents.isExempt(it.code) }
                .map { it.code }
                .distinct()
                .sortedBy { ThemeComponents.displayName(it) }
        }

        val message = if (full) {
            "Theme vollständig importiert (${candidate.title}, ${appliedCodes.size} Bestandteile)"
        } else {
            "Theme teilweise importiert – nur rechtefreie Bestandteile (${candidate.title})"
        }

        return Result.success(
            LibraryImport(
                title = candidate.title,
                fullTheme = full,
                appliedCodes = appliedCodes,
                droppedCodes = droppedCodes,
                reason = assessment.reason,
                message = message,
            )
        )
    }

    // ── Metadata ─────────────────────────────────────────────

    private fun buildThemeMrm(
        candidate: ThemeSourceReader.Candidate,
        kept: List<ThemeSourceReader.Component>,
        placeholders: List<ThemeSourceReader.Component>,
        themeHash: String,
        previewNames: List<String>,
    ): ByteArray {
        val resources = kept + placeholders
        val totalSize = kept.sumOf { it.data.size.toLong() }

        val root = JSONObject()
        root.put("localId", candidate.localId)
        root.put("onlineId", JSONObject.NULL)
        root.put("assemblyId", candidate.assemblyId)
        root.put("productId", candidate.assemblyId)
        root.put("hash", themeHash)
        root.put("platform", candidate.platform)
        root.put("size", totalSize)
        root.put("updatedTime", 0)
        root.put("version", candidate.version)
        root.put("authors", localized(candidate.author))
        root.put("designers", localized(candidate.author))
        root.put("titles", localized(candidate.title))
        root.put("descriptions", localized(candidate.description))
        root.put("builtInThumbnails", JSONObject().put("fallback", JSONArray(previewNames)))
        root.put("builtInPreviews", JSONObject().put("fallback", JSONArray(previewNames)))
        root.put("thumbnails", JSONArray())
        root.put("previews", JSONArray())
        root.put("parentResources", JSONArray())

        val subResources = JSONArray()
        for (component in resources) {
            subResources.put(
                JSONObject()
                    .put("localId", component.localId)
                    .put("resourceCode", component.code)
                    .put("extraMeta", JSONObject())
                    .put("metaPath", JSONObject.NULL)
                    .put("contentPath", JSONObject.NULL)
            )
        }
        root.put("subResources", subResources)

        root.put("extraMeta", JSONObject())
        root.put("metaPath", JSONObject.NULL)
        root.put("contentPath", JSONObject.NULL)
        root.put("rightsPath", JSONObject.NULL)
        root.put("screenRatio", "18:9")
        root.put("supportHomeSearchBar", false)
        root.put("packageVersion", JSONObject.NULL)
        root.put("packageName", JSONObject.NULL)
        root.put("officialIcons", false)
        root.put("subResourceType", JSONObject.NULL)
        root.put("isRearScreenEditable", false)
        root.put("isThirdParties", false)
        root.put("supportAon", false)
        root.put("wallpaperStyle", 0)
        root.put("isSingleResource", false)
        root.put("iconsCount", JSONObject.NULL)
        root.put("fontWeight", JSONObject.NULL)
        root.put("price", -1)
        root.put("isBackUpVersion", false)
        root.put("themeType", 0)
        root.put("miuiAdapterVersion", candidate.adapterVersion)

        return root.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun buildComponentMrm(
        candidate: ThemeSourceReader.Candidate,
        code: String,
        localId: String,
        hash: String,
        size: Long,
        previewNames: List<String>,
    ): ByteArray {
        val root = JSONObject()
        root.put("localId", localId)
        root.put("onlineId", JSONObject.NULL)
        root.put("assemblyId", candidate.assemblyId)
        root.put("productId", candidate.assemblyId)
        root.put("hash", hash)
        root.put("platform", if (code == "fonts") 8 else candidate.platform)
        root.put("size", size)
        root.put("updatedTime", 0)
        root.put("version", if (code == "fonts") "1.00" else candidate.version)
        root.put("authors", localized(candidate.author))
        root.put("designers", localized(candidate.author))
        root.put("titles", localized(candidate.title))
        root.put("descriptions", localized(candidate.description))
        root.put("builtInThumbnails", JSONObject().put("fallback", JSONArray(previewNames)))
        root.put("builtInPreviews", JSONObject().put("fallback", JSONArray(previewNames)))
        root.put("thumbnails", JSONArray())
        root.put("previews", JSONArray())
        root.put(
            "parentResources",
            JSONArray().put(
                JSONObject()
                    .put("localId", candidate.localId)
                    .put("resourceCode", "theme")
                    .put("extraMeta", JSONObject())
                    .put("metaPath", JSONObject.NULL)
                    .put("contentPath", JSONObject.NULL)
            )
        )
        root.put("subResources", JSONArray())
        root.put("extraMeta", JSONObject())
        root.put("metaPath", JSONObject.NULL)
        root.put("contentPath", JSONObject.NULL)
        root.put("rightsPath", JSONObject.NULL)
        root.put("screenRatio", JSONObject.NULL)
        root.put("supportHomeSearchBar", false)
        root.put("packageVersion", JSONObject.NULL)
        root.put("packageName", JSONObject.NULL)
        root.put("officialIcons", false)
        root.put("iconsCount", JSONObject.NULL)
        root.put("fontWeight", JSONObject.NULL)
        root.put("price", -1)
        root.put("isBackUpVersion", false)
        root.put("themeType", 0)
        root.put("miuiAdapterVersion", candidate.adapterVersion)

        return root.toString().toByteArray(StandardCharsets.UTF_8)
    }

    // ── Rights ───────────────────────────────────────────────

    private fun writeRights(
        candidate: ThemeSourceReader.Candidate,
        assessment: ThemeRights.Assessment,
        put: (String, ByteArray) -> Unit,
    ) {
        val written = mutableSetOf<String>()

        fun writeAlias(name: String, bytes: ByteArray) {
            val safe = sanitize(name)
            if (safe.isBlank() || !written.add(safe)) return
            put("$THEME_DATA_DIR/rights/theme/$safe", bytes)
        }

        assessment.validMras.forEachIndexed { index, mra ->
            val names = mutableSetOf<String>()
            names += "${candidate.themeHash}-$index.mra"
            mra.info.themeHash?.let { names += "$it.mra" }
            if (index == 0) {
                names += "${candidate.assemblyId}.mra"
                names += "${candidate.localId}.mra"
            }
            names += mra.file.name
            for (name in names) {
                writeAlias(name, mra.file.data)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun previewsFor(
        candidate: ThemeSourceReader.Candidate,
        code: String,
    ): List<String> {
        return candidate.previews
            .map { sanitize(it.name) }
            .filter { it.contains("_${code}_") || it.startsWith("${code}_") }
            .distinct()
    }

    private fun localized(text: String): JSONObject {
        return JSONObject().put("fallback", text.ifBlank { "Unbekannt" })
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
    }

    /**
     * Creates missing directories and verifies that the catalog is writable.
     * Returns null when everything is ready, otherwise a short German detail.
     */
    private fun prepareTarget(directories: Set<String>): String? {
        val missing = directories.filterNot { ShizukuHelper.directoryExists(it) }
        if (missing.isNotEmpty()) {
            val mkdir = ShizukuHelper.executeShellCommand(
                "mkdir -p " + missing.joinToString(" ") { quote(it) }
            )
            if (mkdir.exitCode != 0) {
                return mkdir.stderr
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .joinToString("; ")
                    .ifEmpty { "mkdir exit ${mkdir.exitCode}" }
            }
        }
        val probe = "$THEME_DATA_DIR/.mtztool-probe"
        val write = ShizukuHelper.executeShellCommand(": > ${quote(probe)} && rm -f ${quote(probe)}")
        if (write.exitCode != 0) {
            return "keine Schreibrechte in $THEME_DATA_DIR"
        }
        return null
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
