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
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
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
import at.libmatti.mtztool.device.DeviceClass
import at.libmatti.mtztool.device.DeviceProfiles
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.DevInfoRow
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.SetActionRow
import at.libmatti.mtztool.ui.SetButtonRow
import at.libmatti.mtztool.ui.SetDropdownRow
import at.libmatti.mtztool.ui.SetSliderRow
import at.libmatti.mtztool.ui.SetSwitchRow
import at.libmatti.mtztool.ui.SettingsSection
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DevRoute(val title: String) {
    HOME("Entwickleroptionen"),
    DIAGNOSTICS("Diagnose"),
    UPDATES("Update-System"),
    PRIVILEGE("Privilegien-Engine"),
    GITHUB("GitHub-API"),
    DEVICE("Gerät & Layout"),
    WORKSPACE("Workspace & Logs"),
    DANGER("Danger Zone"),
}

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
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf(DevRoute.HOME) }
    var developerMode by remember { mutableStateOf(AppSettings.isDeveloperModeEnabled(context)) }
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var rootOutput by remember { mutableStateOf("") }

    val versionName = UpdateChecker.currentVersionName(context)
    val versionCode = UpdateChecker.currentVersionCode(context)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun diagnosticsText(): String = buildString {
        appendLine("MTZ Tool $versionName ($versionCode)")
        appendLine("package=${context.packageName}")
        appendLine("android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("formFactor=${profile.description}")
        appendLine("build=${Build.DISPLAY}")
        appendLine("provider=${AppSettings.getPrivilegeProvider(context).name}")
        appendLine("shizuku=${ShizukuHelper.getStatus().name}")
        appendLine("root=${ShizukuHelper.isRootAvailable()}")
        appendLine("theme=${AppSettings.getThemeFamily(context).name}/${AppSettings.getThemeMode(context).name}")
        appendLine("language=${AppSettings.getLanguage(context).ifBlank { "system" }}")
        appendLine("channel=${AppSettings.getUpdateChannel(context).name}")
        appendLine("repo=${AppSettings.getGithubRepo(context)}")
        appendLine("github=${AppSettings.getGithubLogin(context).ifBlank { "signed out" }}")
        appendLine("workspace=${ThemeSession.current?.name ?: "empty"}")
        appendLine("library=${ThemeLibrary.list(context).size} themes")
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

    BackHandler {
        if (route == DevRoute.HOME) onBack() else route = DevRoute.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(route.title, fontWeight = FontWeight.Bold)
                        Text(
                            "MTZ Tool $versionName ($versionCode)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (route == DevRoute.HOME) onBack() else route = DevRoute.HOME
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveContent(profile = profile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = profile.horizontalPaddingDp.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (route) {
                    DevRoute.HOME -> {
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
                            title = "Bereiche",
                            subtitle = "Alles ist in eigene Unterseiten aufgeteilt.",
                            icon = Icons.Default.Build,
                        ) {
                            SetActionRow(
                                Icons.Default.Memory,
                                "Diagnose",
                                "Zustand, Export, Kopieren",
                                onClick = { route = DevRoute.DIAGNOSTICS },
                            )
                            SetActionRow(
                                Icons.Default.SystemUpdate,
                                "Update-System",
                                "Kanal, Installer, Repo",
                                onClick = { route = DevRoute.UPDATES },
                            )
                            SetActionRow(
                                Icons.Default.Security,
                                "Privilegien-Engine",
                                "Root-Timeout, Shell-User",
                                onClick = { route = DevRoute.PRIVILEGE },
                            )
                            SetActionRow(
                                Icons.Default.Cloud,
                                "GitHub-API",
                                "Token, Client-IDs, Repo",
                                onClick = { route = DevRoute.GITHUB },
                            )
                            SetActionRow(
                                Icons.Default.Devices,
                                "Gerät & Layout",
                                "Simulation und Messwerte",
                                onClick = { route = DevRoute.DEVICE },
                            )
                            SetActionRow(
                                Icons.Default.Terminal,
                                "Workspace & Logs",
                                "Theme-Session, Log-Konsole",
                                onClick = { route = DevRoute.WORKSPACE },
                            )
                            SetActionRow(
                                Icons.Default.Science,
                                "Danger Zone",
                                "Alle Einstellungen löschen",
                                onClick = { route = DevRoute.DANGER },
                            )
                        }
                    }

                    DevRoute.DIAGNOSTICS -> {
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
                            DevInfoRow("Formfaktor", profile.description)
                            DevInfoRow("Erkannt", profile.detectedClass.label)
                            DevInfoRow("Layout", profile.tier.label)
                            DevInfoRow(
                                stringResource(R.string.developer_provider),
                                AppSettings.getPrivilegeProvider(context).name,
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_shizuku),
                                ShizukuHelper.getStatus().name,
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_root),
                                if (ShizukuHelper.isRootAvailable()) "available" else "unknown/unavailable",
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_theme_family),
                                AppSettings.getThemeFamily(context).name,
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_theme_mode),
                                AppSettings.getThemeMode(context).name,
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_language),
                                AppSettings.getLanguage(context).ifBlank { "system" },
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_workspace),
                                ThemeSession.current?.let { "${it.name} · ${formatBytes(it.size)}" }
                                    ?: "empty",
                            )
                            DevInfoRow("Bibliothek", "${ThemeLibrary.list(context).size} Themes")
                            val lastCheck = AppSettings.getLastUpdateCheck(context)
                            DevInfoRow(
                                stringResource(R.string.developer_last_check),
                                if (lastCheck > 0) {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(lastCheck))
                                } else "never",
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_skipped_version),
                                AppSettings.getSkippedUpdateVersionCode(context)
                                    .takeIf { it > 0 }?.toString() ?: "none",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(
                                            Context.CLIPBOARD_SERVICE
                                        ) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "MTZ Tool diagnostics",
                                                diagnosticsText(),
                                            )
                                        )
                                        toast("Diagnose kopiert")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Kopieren") }
                                OutlinedButton(
                                    onClick = { exportLauncher.launch("mtz-tool-settings.json") },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.export_settings)) }
                            }
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf("application/json", "text/*", "*/*")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.import_settings)) }
                        }
                    }

                    DevRoute.UPDATES -> {
                        SettingsSection(
                            title = stringResource(R.string.developer_update_settings),
                            subtitle = stringResource(R.string.developer_update_settings_desc),
                            icon = Icons.Default.SystemUpdate,
                        ) {
                            var channel by remember {
                                mutableStateOf(AppSettings.getStoredUpdateChannel(context))
                            }
                            var autoUpdate by remember {
                                mutableStateOf(AppSettings.isAutoUpdateCheckEnabled(context))
                            }
                            var interval by remember {
                                mutableStateOf(AppSettings.getUpdateIntervalHours(context))
                            }
                            var shellInstall by remember {
                                mutableStateOf(AppSettings.isShellInstallEnabled(context))
                            }
                            var allowDowngrade by remember {
                                mutableStateOf(AppSettings.isDowngradeAllowed(context))
                            }
                            var debugChannel by remember {
                                mutableStateOf(AppSettings.isDebugChannelVisible(context))
                            }
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
                                subtitle = AppSettings.getGithubRepo(context),
                                buttonText = stringResource(R.string.developer_edit),
                                onClick = {
                                    editTarget = "repo"
                                    editValue = AppSettings.getGithubRepo(context)
                                },
                            )
                            SetButtonRow(
                                title = stringResource(R.string.developer_manifest),
                                subtitle = AppSettings.getUpdateManifestUrl(context)
                                    .ifBlank { "standard GitHub Releases" },
                                buttonText = stringResource(R.string.developer_edit),
                                onClick = {
                                    editTarget = "manifest"
                                    editValue = AppSettings.getUpdateManifestUrl(context)
                                },
                            )
                            SetButtonRow(
                                title = stringResource(R.string.developer_reset_update),
                                subtitle = "Setzt letzten Check und übersprungene Version zurück.",
                                buttonText = "Zurücksetzen",
                                onClick = {
                                    AppSettings.setLastUpdateCheck(context, 0)
                                    AppSettings.setSkippedUpdateVersionCode(context, 0)
                                    toast("Update-Status zurückgesetzt")
                                },
                            )
                        }
                    }

                    DevRoute.PRIVILEGE -> {
                        SettingsSection(
                            title = stringResource(R.string.developer_access),
                            subtitle = stringResource(R.string.developer_access_note),
                            icon = Icons.Default.Security,
                        ) {
                            var rootTimeout by remember {
                                mutableStateOf(AppSettings.getRootTimeoutSeconds(context))
                            }
                            var shellUser by remember {
                                mutableStateOf(AppSettings.getShellUser(context))
                            }
                            DevInfoRow(
                                stringResource(R.string.developer_provider),
                                AppSettings.getPrivilegeProvider(context).name,
                            )
                            DevInfoRow(
                                stringResource(R.string.developer_shizuku),
                                ShizukuHelper.getStatus().name,
                            )
                            SetSliderRow(
                                title = stringResource(R.string.developer_root_timeout),
                                valueText = "$rootTimeout s",
                                value = rootTimeout,
                                range = 3..60,
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
                                    editTarget = "shell"
                                    editValue = shellUser
                                },
                            )
                            OutlinedButton(
                                onClick = {
                                    if (!ShizukuHelper.isReady()) {
                                        toast("Root oder Shizuku ist nicht bereit.")
                                        return@OutlinedButton
                                    }
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            ShizukuHelper.executeShellCommand("id")
                                        }
                                        rootOutput = result.stdout.ifBlank { result.stderr }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Shell testen (id)")
                            }
                            if (rootOutput.isNotBlank()) {
                                Text(
                                    rootOutput,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    DevRoute.GITHUB -> {
                        SettingsSection(
                            title = "GitHub-API",
                            subtitle = "Token, Client-IDs und Repo für die In-App-Integration.",
                            icon = Icons.Default.Cloud,
                        ) {
                            DevInfoRow(
                                "Token",
                                if (AppSettings.getGithubToken(context).isBlank()) "nicht gesetzt"
                                else "gesetzt (${AppSettings.getGithubToken(context).take(6)}…)",
                            )
                            DevInfoRow("Login", AppSettings.getGithubLogin(context).ifBlank { "–" })
                            DevInfoRow("Repository", AppSettings.getGithubRepo(context))
                            DevInfoRow(
                                "Google-Konto",
                                AppSettings.getGoogleAccount(context).ifBlank { "–" },
                            )
                            SetButtonRow(
                                title = "GitHub-Client-ID",
                                subtitle = AppSettings.getGithubClientId(context).ifBlank { "nicht gesetzt" },
                                buttonText = stringResource(R.string.developer_edit),
                                onClick = {
                                    editTarget = "githubClient"
                                    editValue = AppSettings.getGithubClientId(context)
                                },
                            )
                            SetButtonRow(
                                title = "Google-Client-ID",
                                subtitle = AppSettings.getGoogleClientId(context).ifBlank { "nicht gesetzt" },
                                buttonText = stringResource(R.string.developer_edit),
                                onClick = {
                                    editTarget = "googleClient"
                                    editValue = AppSettings.getGoogleClientId(context)
                                },
                            )
                            SetButtonRow(
                                title = stringResource(R.string.developer_repo),
                                subtitle = AppSettings.getGithubRepo(context),
                                buttonText = stringResource(R.string.developer_edit),
                                onClick = {
                                    editTarget = "repo"
                                    editValue = AppSettings.getGithubRepo(context)
                                },
                            )
                            var crashAuto by remember {
                                mutableStateOf(AppSettings.isCrashAutoReportEnabled(context))
                            }
                            SetSwitchRow(
                                title = "Crash automatisch als Issue",
                                subtitle = "Nur mit gültigem Token.",
                                checked = crashAuto,
                            ) {
                                crashAuto = it
                                AppSettings.setCrashAutoReportEnabled(context, it)
                            }
                            OutlinedButton(
                                onClick = {
                                    AppSettings.setGithubToken(context, "")
                                    AppSettings.setGithubLogin(context, "")
                                    toast("Token entfernt")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Token entfernen") }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, GitHubActivity::class.java)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("GitHub-Hub öffnen") }
                        }
                    }

                    DevRoute.DEVICE -> {
                        SettingsSection(
                            title = stringResource(R.string.developer_device),
                            subtitle = "Erkennung prüfen und Layout simulieren.",
                            icon = Icons.Default.Devices,
                        ) {
                            var override by remember {
                                mutableStateOf(AppSettings.getDeviceClassOverride(context))
                            }
                            var adaptive by remember {
                                mutableStateOf(AppSettings.isAdaptiveLayoutEnabled(context))
                            }
                            DevInfoRow("Erkannt", profile.detectedClass.label)
                            DevInfoRow("Aktiv", profile.effectiveClass.label)
                            DevInfoRow("Fenster", "${profile.widthDp} × ${profile.heightDp} dp")
                            DevInfoRow("Kleinste Breite", "${profile.smallestWidthDp} dp")
                            DevInfoRow("Spalten", profile.columns.toString())
                            DevInfoRow("Max. Breite", "${profile.contentMaxWidthDp} dp")
                            SetDropdownRow(
                                title = "Simulation",
                                selectedText = override.label,
                                options = DeviceClass.entries,
                                optionText = { it.label },
                                onSelected = {
                                    override = it
                                    AppSettings.setDeviceClassOverride(context, it)
                                },
                            )
                            SetSwitchRow(
                                title = "Adaptive Oberfläche",
                                checked = adaptive,
                            ) {
                                adaptive = it
                                AppSettings.setAdaptiveLayoutEnabled(context, it)
                            }
                            DevInfoRow(
                                "Discovery",
                                "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.DEVICE}",
                            )
                        }
                    }

                    DevRoute.WORKSPACE -> {
                        SettingsSection(
                            title = stringResource(R.string.developer_workspace_tools),
                            subtitle = "Gemeinsame In-Memory-Session und Log.",
                            icon = Icons.Default.Terminal,
                        ) {
                            DevInfoRow(
                                "Workspace",
                                ThemeSession.current?.let { "${it.name} · ${formatBytes(it.size)}" }
                                    ?: "leer",
                            )
                            DevInfoRow("Bibliothek", "${ThemeLibrary.list(context).size} Themes")
                            DevInfoRow("Log-Einträge", AppLog.entries.size.toString())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        ThemeSession.clear()
                                        toast("Workspace geleert")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.developer_clear_workspace)) }
                                OutlinedButton(
                                    onClick = {
                                        AppLog.clear()
                                        toast("Log geleert")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.developer_clear_logs)) }
                            }
                        }
                        LogConsole(height = 320)
                    }

                    DevRoute.DANGER -> {
                        SettingsSection(
                            title = stringResource(R.string.developer_danger_zone),
                            subtitle = stringResource(R.string.developer_danger_desc),
                            icon = Icons.Default.Science,
                        ) {
                            OutlinedButton(
                                onClick = { confirmReset = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_reset_button))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Wert bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (target) {
                            "repo" -> "Repository als owner/name."
                            "manifest" -> "URL zu einer update.json (leer = GitHub Releases)."
                            "shell" -> "Shell-Benutzer für erweiterte Integrationen."
                            "githubClient" -> "GitHub-OAuth-App Client-ID (öffentlich)."
                            "googleClient" -> "Google-OAuth Client-ID (öffentlich)."
                            else -> "Neuer Wert"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text("Wert") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        "repo" -> AppSettings.setGithubRepo(context, editValue)
                        "manifest" -> AppSettings.setUpdateManifestUrl(context, editValue)
                        "shell" -> AppSettings.setShellUser(context, editValue)
                        "githubClient" -> AppSettings.setGithubClientId(context, editValue)
                        "googleClient" -> AppSettings.setGoogleClientId(context, editValue)
                    }
                    editTarget = null
                    toast("Gespeichert")
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Abbrechen") }
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
                    context.startActivity(
                        Intent(context, SetupWizardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    (context as? Activity)?.finish()
                }) { Text(stringResource(R.string.settings_reset_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
