package at.libmatti.mtztool

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import at.libmatti.mtztool.ui.theme.ThemeFamily
import at.libmatti.mtztool.ui.theme.ThemeMode
import org.json.JSONObject
import java.util.Locale

/**
 * Central persistent settings store.
 *
 * Everything the app can change at runtime is stored here so the developer
 * options can expose the complete configuration surface and the backup
 * feature can export/import all of it.
 */
object AppSettings {

    private const val PREFS_NAME = "mtz_tool_settings"

    const val LANGUAGE_SYSTEM = ""

    /** Repository used for the update checker and bug tracker. */
    val DEFAULT_GITHUB_REPO: String = BuildConfig.GITHUB_REPO

    val supportedLanguages = listOf(
        LANGUAGE_SYSTEM,
        "en",
        "de",
        "fr",
        "es",
        "it",
        "pt",
        "ru",
        "tr",
        "zh-CN",
        "ja",
    )

    private const val EXPORT_SCHEMA = 3

    /**
     * Bumped whenever a visual setting changes. Compose themes read this value
     * so a change in the setup wizard or settings screen is applied instantly.
     */
    var themeRevision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun bumpTheme() {
        themeRevision += 1
    }

    // ── Generic helpers ───────────────────────────────────────

    private fun getBool(context: Context, key: String, default: Boolean) =
        prefs(context).getBoolean(key, default)

    private fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    private fun getInt(context: Context, key: String, default: Int) =
        prefs(context).getInt(key, default)

    private fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    private fun getString(context: Context, key: String, default: String) =
        prefs(context).getString(key, default) ?: default

    private fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    private fun getLong(context: Context, key: String, default: Long) =
        prefs(context).getLong(key, default)

    private fun setLong(context: Context, key: String, value: Long) {
        prefs(context).edit().putLong(key, value).apply()
    }

    // ── Setup ─────────────────────────────────────────────────

    fun isSetupCompleted(context: Context) =
        getBool(context, "setup_completed", false)

    fun setSetupCompleted(context: Context, completed: Boolean) {
        setBool(context, "setup_completed", completed)
    }

    // ── Privilege provider ────────────────────────────────────

    fun getPrivilegeProvider(context: Context): PrivilegeProvider =
        PrivilegeProvider.fromStorage(getString(context, "privilege_provider", "shizuku"))

    fun setPrivilegeProvider(context: Context, provider: PrivilegeProvider) {
        setString(context, "privilege_provider", provider.name)
    }

    // ── Developer mode ────────────────────────────────────────

    fun isDeveloperModeEnabled(context: Context) =
        getBool(context, "developer_mode", false)

    fun setDeveloperModeEnabled(context: Context, enabled: Boolean) {
        setBool(context, "developer_mode", enabled)
    }

    // ── Language ──────────────────────────────────────────────

    fun getLanguage(context: Context): String {
        val stored = getString(context, "language", LANGUAGE_SYSTEM)
        return if (stored in supportedLanguages) stored else LANGUAGE_SYSTEM
    }

    fun setLanguage(context: Context, languageTag: String) {
        val normalized = if (languageTag in supportedLanguages) languageTag else LANGUAGE_SYSTEM
        setString(context, "language", normalized)
    }

    fun localizedContext(base: Context): Context {
        val languageTag = getLanguage(base)
        val locale = if (languageTag.isEmpty()) {
            base.resources.configuration.locales[0]
        } else {
            Locale.forLanguageTag(languageTag)
        }
        Locale.setDefault(locale)

        if (languageTag.isEmpty()) return base

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    // ── Theme ─────────────────────────────────────────────────

    fun getThemeFamily(context: Context): ThemeFamily =
        ThemeFamily.fromStorage(getString(context, "theme_family", ThemeFamily.MATERIAL.name))

    fun setThemeFamily(context: Context, family: ThemeFamily) {
        setString(context, "theme_family", family.name)
        bumpTheme()
    }

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromStorage(getString(context, "theme_mode", ThemeMode.SYSTEM.name))

    fun setThemeMode(context: Context, mode: ThemeMode) {
        setString(context, "theme_mode", mode.name)
        bumpTheme()
    }

    fun isDynamicColorEnabled(context: Context) =
        getBool(context, "dynamic_color", false)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        setBool(context, "dynamic_color", enabled)
        bumpTheme()
    }

    // ── Logging ───────────────────────────────────────────────

    fun isLogAutoscrollEnabled(context: Context) =
        getBool(context, "log_autoscroll", true)

    fun setLogAutoscrollEnabled(context: Context, enabled: Boolean) {
        setBool(context, "log_autoscroll", enabled)
    }

    fun isVerboseLoggingEnabled(context: Context) =
        getBool(context, "log_verbose", false)

    fun setVerboseLoggingEnabled(context: Context, enabled: Boolean) {
        setBool(context, "log_verbose", enabled)
    }

    fun getMaxLogEntries(context: Context): Int =
        getInt(context, "log_max_entries", 300).coerceIn(50, 5000)

    fun setMaxLogEntries(context: Context, value: Int) {
        setInt(context, "log_max_entries", value.coerceIn(50, 5000))
    }

    // ── Behaviour ─────────────────────────────────────────────

    fun isConfirmApplyEnabled(context: Context) =
        getBool(context, "confirm_apply", true)

    fun setConfirmApplyEnabled(context: Context, enabled: Boolean) {
        setBool(context, "confirm_apply", enabled)
    }

    fun isKeepScreenOnEnabled(context: Context) =
        getBool(context, "keep_screen_on", true)

    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        setBool(context, "keep_screen_on", enabled)
    }

    // ── Feature flags ─────────────────────────────────────────

    fun isBundledApplyVisible(context: Context) =
        getBool(context, "show_bundled_apply", false)

    fun setBundledApplyVisible(context: Context, enabled: Boolean) {
        setBool(context, "show_bundled_apply", enabled)
    }

    fun areRawCodesVisible(context: Context) =
        getBool(context, "show_raw_codes", true)

    fun setRawCodesVisible(context: Context, enabled: Boolean) {
        setBool(context, "show_raw_codes", enabled)
    }

    fun isStrictRightsEnabled(context: Context) =
        getBool(context, "strict_rights", false)

    fun setStrictRightsEnabled(context: Context, enabled: Boolean) {
        setBool(context, "strict_rights", enabled)
    }

    fun isExperimentalPatcherEnabled(context: Context) =
        getBool(context, "experimental_patcher", true)

    fun setExperimentalPatcherEnabled(context: Context, enabled: Boolean) {
        setBool(context, "experimental_patcher", enabled)
    }

    fun isDebugChannelVisible(context: Context) =
        getBool(context, "debug_channel_visible", false)

    fun setDebugChannelVisible(context: Context, enabled: Boolean) {
        setBool(context, "debug_channel_visible", enabled)
    }

    // ── Root / shell engine ───────────────────────────────────

    fun getRootTimeoutSeconds(context: Context): Int =
        getInt(context, "root_timeout", 10).coerceIn(3, 120)

    fun setRootTimeoutSeconds(context: Context, seconds: Int) {
        setInt(context, "root_timeout", seconds.coerceIn(3, 120))
    }

    fun getShellUser(context: Context): String =
        getString(context, "shell_user", "shell").ifBlank { "shell" }

    fun setShellUser(context: Context, value: String) {
        setString(context, "shell_user", value.trim().ifBlank { "shell" })
    }

    // ── Updates ───────────────────────────────────────────────

    fun isAutoUpdateCheckEnabled(context: Context) =
        getBool(context, "auto_check_updates", true)

    fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
        setBool(context, "auto_check_updates", enabled)
    }

    /** Stores stable/prerelease. Only honoured while developer mode is on. */
    fun getStoredUpdateChannel(context: Context): UpdateChannel =
        UpdateChannel.fromStorage(getString(context, "update_channel", UpdateChannel.STABLE.name))

    fun getUpdateChannel(context: Context): UpdateChannel =
        if (isDeveloperModeEnabled(context)) getStoredUpdateChannel(context)
        else UpdateChannel.STABLE

    fun setUpdateChannel(context: Context, channel: UpdateChannel) {
        setString(context, "update_channel", channel.name)
    }

    fun getUpdateIntervalHours(context: Context): Int =
        getInt(context, "update_interval_hours", 24).coerceIn(1, 24 * 30)

    fun setUpdateIntervalHours(context: Context, hours: Int) {
        setInt(context, "update_interval_hours", hours.coerceIn(1, 24 * 30))
    }

    fun getGithubRepo(context: Context): String =
        getString(context, "github_repo", DEFAULT_GITHUB_REPO)
            .trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')
            .ifBlank { DEFAULT_GITHUB_REPO }

    fun setGithubRepo(context: Context, repo: String) {
        setString(context, "github_repo", repo.trim())
    }

    fun getUpdateManifestUrl(context: Context): String =
        getString(context, "update_manifest_url", "").trim()

    fun setUpdateManifestUrl(context: Context, url: String) {
        setString(context, "update_manifest_url", url.trim())
    }

    fun isShellInstallEnabled(context: Context) =
        getBool(context, "shell_install", true)

    fun setShellInstallEnabled(context: Context, enabled: Boolean) {
        setBool(context, "shell_install", enabled)
    }

    fun isDowngradeAllowed(context: Context) =
        getBool(context, "allow_downgrade", false)

    fun setDowngradeAllowed(context: Context, enabled: Boolean) {
        setBool(context, "allow_downgrade", enabled)
    }

    fun getLastUpdateCheck(context: Context) =
        getLong(context, "last_update_check", 0L)

    fun setLastUpdateCheck(context: Context, timestamp: Long) {
        setLong(context, "last_update_check", timestamp)
    }

    fun getSkippedUpdateVersionCode(context: Context) =
        getInt(context, "skipped_update_version_code", 0)

    fun setSkippedUpdateVersionCode(context: Context, value: Int) {
        setInt(context, "skipped_update_version_code", value)
    }

    // ── Reset ─────────────────────────────────────────────────

    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
        bumpTheme()
    }

    // ── Export / import ────────────────────────────────────────

    fun exportJson(context: Context): String {
        val json = JSONObject()
        json.put("app", context.packageName)
        json.put("schema", EXPORT_SCHEMA)
        json.put("setupCompleted", isSetupCompleted(context))
        json.put("privilegeProvider", getPrivilegeProvider(context).name)
        json.put("developerMode", isDeveloperModeEnabled(context))
        json.put("language", getLanguage(context))
        json.put("themeFamily", getThemeFamily(context).name)
        json.put("themeMode", getThemeMode(context).name)
        json.put("dynamicColor", isDynamicColorEnabled(context))
        json.put("logAutoscroll", isLogAutoscrollEnabled(context))
        json.put("logVerbose", isVerboseLoggingEnabled(context))
        json.put("maxLogEntries", getMaxLogEntries(context))
        json.put("confirmApply", isConfirmApplyEnabled(context))
        json.put("keepScreenOn", isKeepScreenOnEnabled(context))
        json.put("showBundledApply", isBundledApplyVisible(context))
        json.put("showRawCodes", areRawCodesVisible(context))
        json.put("strictRights", isStrictRightsEnabled(context))
        json.put("experimentalPatcher", isExperimentalPatcherEnabled(context))
        json.put("debugChannelVisible", isDebugChannelVisible(context))
        json.put("rootTimeout", getRootTimeoutSeconds(context))
        json.put("shellUser", getShellUser(context))
        json.put("autoCheckUpdates", isAutoUpdateCheckEnabled(context))
        json.put("updateChannel", getStoredUpdateChannel(context).name)
        json.put("updateIntervalHours", getUpdateIntervalHours(context))
        json.put("githubRepo", getGithubRepo(context))
        json.put("updateManifestUrl", getUpdateManifestUrl(context))
        json.put("shellInstall", isShellInstallEnabled(context))
        json.put("allowDowngrade", isDowngradeAllowed(context))
        json.put("lastUpdateCheck", getLastUpdateCheck(context))
        json.put("skippedUpdateVersionCode", getSkippedUpdateVersionCode(context))
        return json.toString(2)
    }

    fun importJson(context: Context, jsonText: String): Result<Unit> =
        runCatching {
            val json = JSONObject(jsonText)
            val schema = json.optInt("schema", EXPORT_SCHEMA)
            require(schema <= EXPORT_SCHEMA) { "Unsupported settings schema: $schema" }

            json.optStringOrNull("privilegeProvider")?.let { raw ->
                val provider = PrivilegeProvider.entries.firstOrNull {
                    it.name.equals(raw, ignoreCase = true)
                } ?: throw IllegalArgumentException("Unknown privilege provider: $raw")
                setPrivilegeProvider(context, provider)
            }

            json.optBooleanOrNull("setupCompleted")?.let { setSetupCompleted(context, it) }
            json.optBooleanOrNull("developerMode")?.let { setDeveloperModeEnabled(context, it) }
            json.optStringOrNull("language")?.let { setLanguage(context, it) }
            json.optStringOrNull("themeFamily")?.let {
                setThemeFamily(context, ThemeFamily.fromStorage(it))
            }
            json.optStringOrNull("themeMode")?.let {
                setThemeMode(context, ThemeMode.fromStorage(it))
            }
            json.optBooleanOrNull("dynamicColor")?.let { setDynamicColorEnabled(context, it) }
            json.optBooleanOrNull("logAutoscroll")?.let { setLogAutoscrollEnabled(context, it) }
            json.optBooleanOrNull("logVerbose")?.let { setVerboseLoggingEnabled(context, it) }
            if (json.has("maxLogEntries")) setMaxLogEntries(context, json.getInt("maxLogEntries"))
            json.optBooleanOrNull("confirmApply")?.let { setConfirmApplyEnabled(context, it) }
            json.optBooleanOrNull("keepScreenOn")?.let { setKeepScreenOnEnabled(context, it) }
            json.optBooleanOrNull("showBundledApply")?.let { setBundledApplyVisible(context, it) }
            json.optBooleanOrNull("showRawCodes")?.let { setRawCodesVisible(context, it) }
            json.optBooleanOrNull("strictRights")?.let { setStrictRightsEnabled(context, it) }
            json.optBooleanOrNull("experimentalPatcher")?.let {
                setExperimentalPatcherEnabled(context, it)
            }
            json.optBooleanOrNull("debugChannelVisible")?.let { setDebugChannelVisible(context, it) }
            if (json.has("rootTimeout")) setRootTimeoutSeconds(context, json.getInt("rootTimeout"))
            json.optStringOrNull("shellUser")?.let { setShellUser(context, it) }
            json.optBooleanOrNull("autoCheckUpdates")?.let { setAutoUpdateCheckEnabled(context, it) }
            json.optStringOrNull("updateChannel")?.let {
                setUpdateChannel(context, UpdateChannel.fromStorage(it))
            }
            if (json.has("updateIntervalHours")) {
                setUpdateIntervalHours(context, json.getInt("updateIntervalHours"))
            }
            json.optStringOrNull("githubRepo")?.let { setGithubRepo(context, it) }
            json.optStringOrNull("updateManifestUrl")?.let { setUpdateManifestUrl(context, it) }
            json.optBooleanOrNull("shellInstall")?.let { setShellInstallEnabled(context, it) }
            json.optBooleanOrNull("allowDowngrade")?.let { setDowngradeAllowed(context, it) }
            if (json.has("lastUpdateCheck")) setLastUpdateCheck(context, json.getLong("lastUpdateCheck"))
            if (json.has("skippedUpdateVersionCode")) {
                setSkippedUpdateVersionCode(context, json.getInt("skippedUpdateVersionCode"))
            }
        }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) getBoolean(key) else null
}
