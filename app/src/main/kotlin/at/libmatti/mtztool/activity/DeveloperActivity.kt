package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.DevInfoRow
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.SetButtonRow
import at.libmatti.mtztool.ui.SetDropdownRow
import at.libmatti.mtztool.ui.SetSliderRow
import at.libmatti.mtztool.ui.SetSwitchRow
import at.libmatti.mtztool.ui.SettingsSection
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import at.libmatti.mtztool.ui.theme.ThemeFamily
import at.libmatti.mtztool.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeveloperActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DeveloperScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var developerMode by remember { mutableStateOf(AppSettings.isDeveloperModeEnabled(context)) }
    var provider by remember { mutableStateOf(AppSettings.getPrivilegeProvider(context)) }
    var family by remember { mutableStateOf(AppSettings.getThemeFamily(context)) }
    var mode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
    var language by remember { mutableStateOf(AppSettings.getLanguage(context)) }
    var dynamicColor by remember { mutableStateOf(AppSettings.isDynamicColorEnabled(context)) }
    var channel by remember { mutableStateOf(AppSettings.getStoredUpdateChannel(context)) }
    var autoUpdate by remember { mutableStateOf(AppSettings.isAutoUpdateCheckEnabled(context)) }
    var interval by remember { mutableStateOf(AppSettings.getUpdateIntervalHours(context)) }
    var shellInstall by remember { mutableStateOf(AppSettings.isShellInstallEnabled(context)) }
    var allowDowngrade by remember { mutableStateOf(AppSettings.isDowngradeAllowed(context)) }
    var debugChannel by remember { mutableStateOf(AppSettings.isDebugChannelVisible(context)) }
    var repo by remember { mutableStateOf(AppSettings.getGithubRepo(context)) }
    var manifestUrl by remember { mutableStateOf(AppSettings.getUpdateManifestUrl(context)) }
    var shellUser by remember { mutableStateOf(AppSettings.getShellUser(context)) }
    var rootTimeout by remember { mutableStateOf(AppSettings.getRootTimeoutSeconds(context)) }
    var confirmApply by remember { mutableStateOf(AppSettings.isConfirmApplyEnabled(context)) }
    var keepScreenOn by remember { mutableStateOf(AppSettings.isKeepScreenOnEnabled(context)) }
    var strictRights by remember { mutableStateOf(AppSettings.isStrictRightsEnabled(context)) }
    var experimental by remember { mutableStateOf(AppSettings.isExperimentalPatcherEnabled(context)) }
    var rawCodes by remember { mutableStateOf(AppSettings.areRawCodesVisible(context)) }
    var bundledVisible by remember { mutableStateOf(AppSettings.isBundledApplyVisible(context)) }
    var autoscroll by remember { mutableStateOf(AppSettings.isLogAutoscrollEnabled(context)) }
    var verbose by remember { mutableStateOf(AppSettings.isVerboseLoggingEnabled(context)) }
    var maxLog by remember { mutableStateOf(AppSettings.getMaxLogEntries(context)) }

    var editDialog by remember { mutableStateOf<EditField?>(null) }
    var editValue by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val versionName = UpdateChecker.currentVersionName(context)
    val versionCode = UpdateChecker.currentVersionCode(context)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(AppSettings.exportJson(context))
                    } ?: error("Zieldatei konnte nicht geöffnet werden")
                }
            }
            result.fold(
                onSuccess = { toast(context.getString(R.string.export_success)) },
                onFailure = { toast("Export fehlgeschlagen: ${it.message}") },
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: error("Quelldatei konnte nicht geöffnet werden")
                    AppSettings.importJson(context, text).getOrThrow()
                }
            }
            result.fold(
                onSuccess = {
                    toast(context.getString(R.string.import_success))
                    (context as? Activity)?.recreate()
                },
                onFailure = { toast("Import fehlgeschlagen: ${it.message}") },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.developer_mode), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.developer_advanced_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection(
                title = stringResource(R.string.developer_mode),
                subtitle = stringResource(R.string.developer_advanced_subtitle),
                icon = Icons.Default.Science,
            ) {
                SetSwitchRow(
                    title = stringResource(R.string.developer_mode),
                    subtitle = stringResource(R.string.developer_mode_active),
                    checked = developerMode,
                ) {
                    developerMode = it
                    AppSettings.setDeveloperModeEnabled(context, it)
                }
                Text(
                    stringResource(R.string.developer_everything_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(
                title = stringResource(R.string.developer_diagnostics),
                subtitle = stringResource(R.string.developer_diagnostics_desc),
                icon = Icons.Default.Memory,
            ) {
                DevInfoRow(stringResource(R.string.developer_version), "$versionName ($versionCode)")
                DevInfoRow(stringResource(R.string.developer_package), context.packageName)
                DevInfoRow(
                    stringResource(R.string.developer_android),
                    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                )
                DevInfoRow(
                    stringResource(R.string.developer_device),
                    "${Build.MANUFACTURER} ${Build.MODEL}",
                )
                DevInfoRow(stringResource(R.string.developer_provider), provider.name)
                DevInfoRow(
                    stringResource(R.string.developer_shizuku),
                    ShizukuHelper.getStatus().name,
                )
                DevInfoRow(
                    stringResource(R.string.developer_root),
                    if (ShizukuHelper.isRootAvailable()) "available" else "unknown/unavailable",
                )
                DevInfoRow(stringResource(R.string.developer_theme_family), family.name)
                DevInfoRow(stringResource(R.string.developer_theme_mode), mode.name)
                DevInfoRow(stringResource(R.string.developer_language), language.ifBlank { "system" })
                DevInfoRow(
                    stringResource(R.string.developer_workspace),
                    ThemeSession.current?.let {
                        "${it.name} · ${formatBytes(it.size)}"
                    } ?: "empty",
                )
                val lastCheck = AppSettings.getLastUpdateCheck(context)
                DevInfoRow(
                    stringResource(R.string.developer_last_check),
                    if (lastCheck > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(lastCheck))
                    } else {
                        "never"
                    },
                )
                DevInfoRow(
                    stringResource(R.string.developer_skipped_version),
                    AppSettings.getSkippedUpdateVersionCode(context).takeIf { it > 0 }?.toString()
                        ?: "none",
                )
                val diagnostics = remember(developerMode, repo) {
                    buildString {
                        appendLine("MTZ Tool $versionName ($versionCode)")
                        appendLine("package=${context.packageName}")
                        appendLine("android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
                        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("build=${Build.DISPLAY}")
                        appendLine("provider=${AppSettings.getPrivilegeProvider(context).name}")
                        appendLine("shizuku=${ShizukuHelper.getStatus().name}")
                        appendLine("root=${ShizukuHelper.isRootAvailable()}")
                        appendLine("theme=${AppSettings.getThemeFamily(context).name}/${AppSettings.getThemeMode(context).name}")
                        appendLine("language=${AppSettings.getLanguage(context).ifBlank { "system" }}")
                        appendLine("channel=${AppSettings.getUpdateChannel(context).name}")
                        appendLine("repo=$repo")
                        appendLine("workspace=${ThemeSession.current?.name ?: "empty"}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MTZ Tool diagnostics", diagnostics))
                            toast("Diagnose kopiert")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Diagnose kopieren")
                    }
                    OutlinedButton(
                        onClick = { exportLauncher.launch("mtz-tool-settings.json") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.export_settings))
                    }
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_settings))
                }
            }

            SettingsSection(
                title = stringResource(R.string.developer_update_settings),
                subtitle = stringResource(R.string.developer_update_settings_desc),
                icon = Icons.Default.SystemUpdate,
            ) {
                SetDropdownRow(
                    title = stringResource(R.string.settings_update_channel),
                    selectedText = channel.name.lowercase(),
                    options = UpdateChannel.entries,
                    optionText = { it.name.lowercase() },
                    onSelected = {
                        channel = it
                        AppSettings.setUpdateChannel(context, it)
                        AppLog.info("Update-Kanal: ${it.name}")
                    },
                )
                SetSwitchRow(
                    title = stringResource(R.string.settings_auto_check),
                    subtitle = stringResource(R.string.settings_auto_check_desc),
                    checked = autoUpdate,
                ) {
                    autoUpdate = it
                    AppSettings.setAutoUpdateCheckEnabled(context, it)
                }
                SetSliderRow(
                    title = stringResource(R.string.settings_update_interval),
                    valueText = "$interval h",
                    value = interval,
                    range = 1..720,
                    onValueChange = {
                        interval = it
                        AppSettings.setUpdateIntervalHours(context, it)
                    },
                )
                SetSwitchRow(
                    title = stringResource(R.string.developer_shell_install),
                    subtitle = stringResource(R.string.developer_shell_install_desc),
                    checked = shellInstall,
                ) {
                    shellInstall = it
                    AppSettings.setShellInstallEnabled(context, it)
                }
                SetSwitchRow(
                    title = stringResource(R.string.developer_allow_downgrade),
                    subtitle = stringResource(R.string.developer_allow_downgrade_desc),
                    checked = allowDowngrade,
                ) {
                    allowDowngrade = it
                    AppSettings.setDowngradeAllowed(context, it)
                }
                SetSwitchRow(
                    title = stringResource(R.string.developer_debug_channel),
                    subtitle = stringResource(R.string.developer_debug_channel_desc),
                    checked = debugChannel,
                ) {
                    debugChannel = it
                    AppSettings.setDebugChannelVisible(context, it)
                }
                SetButtonRow(
                    title = stringResource(R.string.developer_repo),
                    subtitle = repo,
                    buttonText = stringResource(R.string.developer_edit),
                    onClick = {
                        editDialog = EditField.REPO
                        editValue = repo
                    },
                )
                SetButtonRow(
                    title = stringResource(R.string.developer_manifest),
                    subtitle = manifestUrl.ifBlank { "GitHub Releases API" },
                    buttonText = stringResource(R.string.developer_edit),
                    onClick = {
                        editDialog = EditField.MANIFEST
                        editValue = manifestUrl
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            AppSettings.setSkippedUpdateVersionCode(context, 0)
                            AppSettings.setLastUpdateCheck(context, 0)
                            toast("Update-Status zurückgesetzt")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.developer_reset_update))
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, UpdateActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.updates_check))
                    }
                }
            }

            SettingsSection(
                title = stringResource(R.string.developer_appearance),
                icon = Icons.Default.Palette,
            ) {
                SetDropdownRow(
                    title = stringResource(R.string.settings_theme_family),
                    selectedText = family.label,
                    options = ThemeFamily.entries,
                    optionText = { it.label },
                    onSelected = {
                        family = it
                        AppSettings.setThemeFamily(context, it)
                    },
                )
                SetDropdownRow(
                    title = stringResource(R.string.settings_theme_mode),
                    selectedText = mode.name.lowercase(),
                    options = ThemeMode.entries,
                    optionText = { it.name.lowercase() },
                    onSelected = {
                        mode = it
                        AppSettings.setThemeMode(context, it)
                    },
                )
                SetSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    checked = dynamicColor,
                ) {
                    dynamicColor = it
                    AppSettings.setDynamicColorEnabled(context, it)
                }
                SetDropdownRow(
                    title = stringResource(R.string.language),
                    selectedText = language.ifBlank { "system" },
                    options = AppSettings.supportedLanguages,
                    optionText = { it.ifBlank { "system" } },
                    onSelected = {
                        language = it
                        AppSettings.setLanguage(context, it)
                        (context as? Activity)?.recreate()
                    },
                )
            }

            SettingsSection(
                title = stringResource(R.string.settings_behavior),
                icon = Icons.Default.Settings,
            ) {
                SetSwitchRow(stringResource(R.string.settings_confirm_apply), checked = confirmApply) {
                    confirmApply = it
                    AppSettings.setConfirmApplyEnabled(context, it)
                }
                SetSwitchRow(stringResource(R.string.settings_keep_screen_on), checked = keepScreenOn) {
                    keepScreenOn = it
                    AppSettings.setKeepScreenOnEnabled(context, it)
                }
                SetSwitchRow(stringResource(R.string.settings_strict_rights), checked = strictRights) {
                    strictRights = it
                    AppSettings.setStrictRightsEnabled(context, it)
                }
                SetSwitchRow(stringResource(R.string.settings_experimental_patcher), checked = experimental) {
                    experimental = it
                    AppSettings.setExperimentalPatcherEnabled(context, it)
                }
                SetSwitchRow(stringResource(R.string.settings_raw_codes), checked = rawCodes) {
                    rawCodes = it
                    AppSettings.setRawCodesVisible(context, it)
                }
                SetSwitchRow(stringResource(R.string.settings_bundled_visible), checked = bundledVisible) {
                    bundledVisible = it
                    AppSettings.setBundledApplyVisible(context, it)
                }
                SetSwitchRow(
                    stringResource(R.string.settings_log_autoscroll),
                    checked = autoscroll,
                ) {
                    autoscroll = it
                    AppSettings.setLogAutoscrollEnabled(context, it)
                    AppLog.autoScroll = it
                }
                SetSwitchRow(stringResource(R.string.settings_log_verbose), checked = verbose) {
                    verbose = it
                    AppSettings.setVerboseLoggingEnabled(context, it)
                    AppLog.verbose = it
                }
                SetSliderRow(
                    title = stringResource(R.string.settings_max_log_entries),
                    valueText = maxLog.toString(),
                    value = maxLog,
                    range = 50..2000,
                    onValueChange = {
                        maxLog = it
                        AppSettings.setMaxLogEntries(context, it)
                        AppLog.maxEntries = it
                    },
                    step = 50,
                )
            }

            SettingsSection(
                title = stringResource(R.string.developer_access),
                icon = Icons.Default.Security,
            ) {
                SetDropdownRow(
                    title = stringResource(R.string.privilege_method),
                    selectedText = stringResource(provider.titleRes),
                    options = PrivilegeProvider.entries,
                    optionText = { stringResource(it.titleRes) },
                    onSelected = {
                        provider = it
                        AppSettings.setPrivilegeProvider(context, it)
                        ShizukuHelper.selectedProvider = it
                    },
                )
                SetSliderRow(
                    title = stringResource(R.string.developer_root_timeout),
                    valueText = "$rootTimeout s",
                    value = rootTimeout,
                    range = 3..120,
                    onValueChange = {
                        rootTimeout = it
                        AppSettings.setRootTimeoutSeconds(context, it)
                    },
                )
                SetButtonRow(
                    title = stringResource(R.string.developer_shell_user),
                    subtitle = shellUser,
                    buttonText = stringResource(R.string.developer_edit),
                    onClick = {
                        editDialog = EditField.SHELL_USER
                        editValue = shellUser
                    },
                )
                Text(
                    stringResource(R.string.developer_access_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(
                title = stringResource(R.string.developer_workspace_tools),
                icon = Icons.Default.Terminal,
            ) {
                DevInfoRow(
                    stringResource(R.string.developer_workspace),
                    ThemeSession.current?.let { "${it.name} · ${formatBytes(it.size)}" } ?: "empty",
                )
                OutlinedButton(
                    onClick = {
                        val log = AppLog.asText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MTZ Tool log", log))
                        toast("Log kopiert (${AppLog.entries.size} Einträge)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Log kopieren")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { AppLog.clear() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.developer_clear_logs))
                    }
                    OutlinedButton(
                        onClick = { ThemeSession.clear() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.developer_clear_workspace))
                    }
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, BugReportActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bug_report_title))
                }
            }

            LogConsole(height = 240)

            SettingsSection(
                title = stringResource(R.string.developer_danger_zone),
                icon = Icons.Default.Refresh,
            ) {
                Text(
                    stringResource(R.string.developer_danger_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { confirmReset = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_reset_button))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    editDialog?.let { field ->
        AlertDialog(
            onDismissRequest = { editDialog = null },
            title = {
                Text(
                    when (field) {
                        EditField.REPO -> stringResource(R.string.developer_repo)
                        EditField.MANIFEST -> stringResource(R.string.developer_manifest)
                        EditField.SHELL_USER -> stringResource(R.string.developer_shell_user)
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = {
                        Text(
                            when (field) {
                                EditField.REPO -> "owner/repository"
                                EditField.MANIFEST -> "https://…"
                                EditField.SHELL_USER -> "shell / root"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (field) {
                        EditField.REPO -> {
                            AppSettings.setGithubRepo(context, editValue)
                            repo = AppSettings.getGithubRepo(context)
                        }
                        EditField.MANIFEST -> {
                            AppSettings.setUpdateManifestUrl(context, editValue)
                            manifestUrl = AppSettings.getUpdateManifestUrl(context)
                        }
                        EditField.SHELL_USER -> {
                            AppSettings.setShellUser(context, editValue)
                            shellUser = AppSettings.getShellUser(context)
                        }
                    }
                    editDialog = null
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    AppSettings.resetAll(context)
                    toast(context.getString(R.string.settings_reset_done))
                    (context as? Activity)?.finish()
                }) {
                    Text(stringResource(R.string.settings_reset_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private enum class EditField { REPO, MANIFEST, SHELL_USER }
