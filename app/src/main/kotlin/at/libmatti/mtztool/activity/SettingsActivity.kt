package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import at.libmatti.mtztool.ui.SetActionRow
import at.libmatti.mtztool.ui.SetButtonRow
import at.libmatti.mtztool.ui.SetDropdownRow
import at.libmatti.mtztool.ui.SetSliderRow
import at.libmatti.mtztool.ui.SetSwitchRow
import at.libmatti.mtztool.ui.SettingsSection
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import at.libmatti.mtztool.ui.theme.ThemeFamily
import at.libmatti.mtztool.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DEVELOPER_MODE_TAPS = 5

private enum class SettingsRoute(val title: String) {
    HOME("Einstellungen"),
    APPEARANCE("Darstellung"),
    DEVICE("Gerät & Layout"),
    ACCESS("Privilegierter Zugriff"),
    BEHAVIOR("Verhalten"),
    LOGGING("Protokoll"),
    UPDATES("Updates"),
    ACCOUNTS("GitHub & Konten"),
    BACKUP("Sicherung"),
    ABOUT("Über die App"),
}

class SettingsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ShizukuHelper.selectedProvider = AppSettings.getPrivilegeProvider(this)
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var route by remember { mutableStateOf(SettingsRoute.HOME) }
    var developerMode by remember { mutableStateOf(AppSettings.isDeveloperModeEnabled(context)) }
    var versionTaps by remember { mutableStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val versionName = remember { UpdateChecker.currentVersionName(context) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
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
                onSuccess = { message(context.getString(R.string.export_success)) },
                onFailure = { message(context.getString(R.string.export_failed, it.message ?: "?")) },
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
                    Toast.makeText(context, R.string.import_success, Toast.LENGTH_SHORT).show()
                    (context as? Activity)?.recreate()
                },
                onFailure = { message(context.getString(R.string.import_failed, it.message ?: "?")) },
            )
        }
    }

    BackHandler {
        if (route == SettingsRoute.HOME) onBack() else route = SettingsRoute.HOME
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(route.title, fontWeight = FontWeight.Bold)
                        if (route == SettingsRoute.HOME) {
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (route == SettingsRoute.HOME) onBack() else route = SettingsRoute.HOME
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
                    SettingsRoute.HOME -> SettingsHome(
                        developerMode = developerMode,
                        versionName = versionName,
                        onOpen = { route = it },
                        onReset = { confirmReset = true },
                    )

                    SettingsRoute.APPEARANCE -> AppearanceSettings()
                    SettingsRoute.DEVICE -> DeviceSettings(profile)
                    SettingsRoute.ACCESS -> AccessSettings(::message)
                    SettingsRoute.BEHAVIOR -> BehaviorSettings()
                    SettingsRoute.LOGGING -> LoggingSettings()
                    SettingsRoute.UPDATES -> UpdateSettings(developerMode)
                    SettingsRoute.ACCOUNTS -> AccountSettings(::message)
                    SettingsRoute.BACKUP -> BackupSettings(
                        onExport = { exportLauncher.launch("mtz-tool-settings.json") },
                        onImport = {
                            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        onReset = { confirmReset = true },
                    )
                    SettingsRoute.ABOUT -> AboutSettings(
                        versionName = versionName,
                        developerMode = developerMode,
                        onVersionTap = {
                            if (developerMode) {
                                message(context.getString(R.string.developer_mode_already_active))
                                return@AboutSettings
                            }
                            versionTaps += 1
                            val remaining = DEVELOPER_MODE_TAPS - versionTaps
                            if (remaining <= 0) {
                                versionTaps = 0
                                AppSettings.setDeveloperModeEnabled(context, true)
                                developerMode = true
                                message(context.getString(R.string.developer_mode_activated))
                            } else {
                                message(
                                    context.getString(
                                        R.string.developer_mode_taps_remaining,
                                        remaining,
                                    )
                                )
                            }
                        },
                        onDisableDeveloper = {
                            AppSettings.setDeveloperModeEnabled(context, false)
                            developerMode = false
                            message(context.getString(R.string.developer_mode_disabled))
                        },
                        onOpenDeveloper = {
                            context.startActivity(Intent(context, DeveloperActivity::class.java))
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
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
                    Toast.makeText(context, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
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

// ── Hub ───────────────────────────────────────────────────────────

@Composable
private fun SettingsHome(
    developerMode: Boolean,
    versionName: String,
    onOpen: (SettingsRoute) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    SettingsSection(
        title = "Bereiche",
        subtitle = "Jede Gruppe hat eine eigene Seite.",
        icon = Icons.Default.Settings,
    ) {
        HubRow(Icons.Default.Palette, "Darstellung", "Farbfamilie, Modus, dynamische Farben") {
            onOpen(SettingsRoute.APPEARANCE)
        }
        HubRow(Icons.Default.Devices, "Gerät & Layout", "Erkennung und adaptive Oberfläche") {
            onOpen(SettingsRoute.DEVICE)
        }
        HubRow(Icons.Default.Security, "Privilegierter Zugriff", "Shizuku, Root, ADB, Dhizuku") {
            onOpen(SettingsRoute.ACCESS)
        }
        HubRow(Icons.Default.Tune, "Verhalten", "Bestätigungen, Rechte, Patcher") {
            onOpen(SettingsRoute.BEHAVIOR)
        }
        HubRow(Icons.Default.Settings, "Protokoll", "Log-Anzeige und Detailtiefe") {
            onOpen(SettingsRoute.LOGGING)
        }
        HubRow(Icons.Default.SystemUpdate, "Updates", "Kanal, Intervall, Installation") {
            onOpen(SettingsRoute.UPDATES)
        }
        HubRow(Icons.Default.Cloud, "GitHub & Konten", "Anmeldung, Client-IDs, Issue-Tracker") {
            onOpen(SettingsRoute.ACCOUNTS)
        }
        HubRow(Icons.Default.Backup, "Sicherung", "Export, Import und Zurücksetzen", onReset)
        HubRow(Icons.Default.Info, "Über die App", "$versionName · Entwicklermodus") {
            onOpen(SettingsRoute.ABOUT)
        }
    }

    SettingsSection(
        title = "Hinweis",
        subtitle = "Alle Änderungen gelten sofort und werden gespeichert.",
        icon = Icons.Default.Info,
    ) {
        Text(
            "Die Bereiche sind bewusst getrennt: Darstellung und Verhalten für den " +
                "Alltag, Zugriff und GitHub für Integrationen, Entwickleroptionen für " +
                "Diagnose und Update-System.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (developerMode) {
            Text(
                "Entwicklermodus ist aktiv.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SetActionRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick)
}

// ── Appearance ────────────────────────────────────────────────────

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    var family by remember { mutableStateOf(AppSettings.getThemeFamily(context)) }
    var mode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
    var dynamicColor by remember { mutableStateOf(AppSettings.isDynamicColorEnabled(context)) }

    SettingsSection(
        title = stringResource(R.string.settings_appearance),
        subtitle = stringResource(R.string.settings_appearance_desc),
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
            selectedText = modeLabel(mode),
            options = ThemeMode.entries,
            optionText = { modeLabel(it) },
            onSelected = {
                mode = it
                AppSettings.setThemeMode(context, it)
            },
        )
        SetSwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_desc),
            checked = dynamicColor,
            onCheckedChange = {
                dynamicColor = it
                AppSettings.setDynamicColorEnabled(context, it)
            },
        )
    }

    SettingsSection(
        title = "Sprache",
        icon = Icons.Default.Translate,
    ) {
        var language by remember { mutableStateOf(AppSettings.getLanguage(context)) }
        SetDropdownRow(
            title = stringResource(R.string.language),
            subtitle = stringResource(R.string.language_desc),
            selectedText = languageLabel(language),
            options = AppSettings.supportedLanguages,
            optionText = { languageLabel(it) },
            onSelected = {
                language = it
                AppSettings.setLanguage(context, it)
                (context as? Activity)?.recreate()
            },
        )
    }

    SettingsSection(title = "Design & Themes", icon = Icons.Default.Palette) {
        SetActionRow(
            icon = Icons.Default.Palette,
            title = "Theme-Browser",
            subtitle = "Themes entdecken, importieren und hochladen",
            onClick = {
                context.startActivity(Intent(context, ThemeBrowserActivity::class.java))
            },
        )
        SetActionRow(
            icon = Icons.Default.Palette,
            title = "App-Theme-Browser",
            subtitle = "Eigene App-Farbwelten ansehen und erstellen",
            onClick = {
                context.startActivity(Intent(context, AppThemeBrowserActivity::class.java))
            },
        )
        SetActionRow(
            icon = Icons.Default.Palette,
            title = "App-Theme Studio",
            subtitle = "Alle Farben inklusive Uhr per Picker ändern und einreichen",
            onClick = {
                context.startActivity(Intent(context, AppThemeStudioActivity::class.java))
            },
        )
        SetActionRow(
            icon = Icons.Default.Palette,
            title = "MTZ Theme Editor",
            subtitle = "Metadaten, Komponenten, Schriftart, Wallpaper und Lockscreen bearbeiten",
            onClick = {
                context.startActivity(Intent(context, ThemePatchActivity::class.java))
            },
        )
        SetActionRow(
            icon = Icons.Default.Palette,
            title = "Eigenes MTZ bauen",
            subtitle = "Nur Schriftart, Wallpaper und Lockscreen-Bild importieren",
            onClick = {
                context.startActivity(Intent(context, ThemeStudioActivity::class.java))
            },
        )
    }

    SettingsSection(title = "Assistent", icon = Icons.Default.VerifiedUser) {
        SetButtonRow(
            title = stringResource(R.string.settings_rerun_wizard),
            subtitle = stringResource(R.string.settings_rerun_wizard_desc),
            buttonText = stringResource(R.string.settings_open),
            onClick = {
                context.startActivity(Intent(context, SetupWizardActivity::class.java))
            },
        )
    }
}

// ── Device ────────────────────────────────────────────────────────

@Composable
private fun DeviceSettings(profile: at.libmatti.mtztool.device.DeviceProfile) {
    val context = LocalContext.current
    var override by remember { mutableStateOf(AppSettings.getDeviceClassOverride(context)) }
    var adaptive by remember { mutableStateOf(AppSettings.isAdaptiveLayoutEnabled(context)) }

    SettingsSection(
        title = "Erkennung",
        subtitle = "Formfaktor und Fenstergröße werden automatisch erkannt.",
        icon = Icons.Default.Devices,
    ) {
        DevInfoRow("Erkanntes Gerät", profile.detectedClass.label)
        DevInfoRow("Aktives Layout", profile.tier.label)
        DevInfoRow("Fenster", "${profile.widthDp} × ${profile.heightDp} dp")
        DevInfoRow("Kleinste Breite", "${profile.smallestWidthDp} dp")
        DevInfoRow("Ausrichtung", if (profile.portrait) "Hochformat" else "Querformat")
        DevInfoRow("Spalten", profile.columns.toString())
    }

    SettingsSection(
        title = "Layout",
        subtitle = "Tablet, Faltbar, TV und Desktop erhalten eigene Layouts.",
        icon = Icons.Default.Palette,
    ) {
        SetSwitchRow(
            title = "Adaptive Oberfläche",
            subtitle = "Lesbare Breiten, mehr Spalten und zweispaltige Ansichten auf großen Displays.",
            checked = adaptive,
            onCheckedChange = {
                adaptive = it
                AppSettings.setAdaptiveLayoutEnabled(context, it)
            },
        )
        SetDropdownRow(
            title = "Gerät simulieren",
            subtitle = "Nur zur Vorschau – überschreibt die Erkennung.",
            selectedText = override.label,
            options = DeviceClass.entries,
            optionText = { it.label },
            onSelected = {
                override = it
                AppSettings.setDeviceClassOverride(context, it)
            },
        )
        Text(
            "Automatisch folgt der echten Erkennung. Smartphone/Tablet/TV/Desktop " +
                "erzwingen das typische Layout dieses Formfaktors.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Access ────────────────────────────────────────────────────────

@Composable
private fun AccessSettings(message: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(AppSettings.getPrivilegeProvider(context)) }
    var shizukuStatus by remember { mutableStateOf(ShizukuHelper.getStatus()) }
    var rootAvailable by remember { mutableStateOf(ShizukuHelper.isRootAvailable()) }
    var rootMessage by remember { mutableStateOf<String?>(null) }
    var checkingRoot by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val permission = Shizuku.OnRequestPermissionResultListener { _, result ->
            shizukuStatus = ShizukuHelper.getStatus()
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                AppLog.success("Shizuku-Berechtigung erteilt.")
            }
        }
        val binder = Shizuku.OnBinderReceivedListener { shizukuStatus = ShizukuHelper.getStatus() }
        val dead = Shizuku.OnBinderDeadListener { shizukuStatus = ShizukuHelper.getStatus() }
        ShizukuHelper.addPermissionListener(permission)
        ShizukuHelper.addBinderListener(binder)
        ShizukuHelper.addBinderDeadListener(dead)
        onDispose {
            ShizukuHelper.removePermissionListener(permission)
            ShizukuHelper.removeBinderListener(binder)
            ShizukuHelper.removeBinderDeadListener(dead)
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_access),
        subtitle = stringResource(R.string.settings_access_desc),
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
                shizukuStatus = ShizukuHelper.getStatus()
                rootAvailable = ShizukuHelper.isRootAvailable()
            },
        )
        Text(
            stringResource(provider.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (provider) {
            PrivilegeProvider.SHIZUKU -> {
                DevInfoRow(stringResource(R.string.developer_shizuku), shizukuStatusText(shizukuStatus))
                if (shizukuStatus == ShizukuHelper.Status.NO_PERMISSION) {
                    OutlinedButton(
                        onClick = { ShizukuHelper.requestPermission() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.allow)) }
                }
                if (shizukuStatus == ShizukuHelper.Status.NOT_RUNNING ||
                    shizukuStatus == ShizukuHelper.Status.NOT_INSTALLED
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setPackage("moe.shizuku.privileged.api")
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.open_shizuku)) }
                }
                OutlinedButton(
                    onClick = { shizukuStatus = ShizukuHelper.getStatus() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refresh))
                }
            }

            PrivilegeProvider.ROOT -> {
                DevInfoRow(
                    stringResource(R.string.developer_root),
                    stringResource(
                        if (rootAvailable) R.string.root_available else R.string.root_not_tested
                    ),
                )
                rootMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (checkingRoot) return@OutlinedButton
                        checkingRoot = true
                        scope.launch {
                            val result = checkRootAccess(context)
                            checkingRoot = false
                            rootAvailable = result.available
                            rootMessage = result.message
                            ShizukuHelper.setRootAvailable(result.available)
                            AppLog.add(
                                "Root-Test: ${if (result.available) "ok" else "fehlgeschlagen"} – ${result.message}",
                            )
                        }
                    },
                    enabled = !checkingRoot,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (checkingRoot) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (checkingRoot) stringResource(R.string.checking)
                        else stringResource(R.string.test_root)
                    )
                }
            }

            PrivilegeProvider.ADB -> {
                Text(
                    stringResource(R.string.adb_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.open_developer_options)) }
            }

            PrivilegeProvider.DHIZUKU -> {
                Text(
                    stringResource(R.string.dhizuku_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage("com.rosan.dhizuku")
                        if (launch != null) {
                            runCatching { context.startActivity(launch) }
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            "https://play.google.com/store/apps/details?id=com.rosan.dhizuku"
                                        ),
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.open_dhizuku)) }
            }
        }
    }

    SettingsSection(title = "Hinweis", icon = Icons.Default.Info) {
        Text(
            "Der Zugriff wird nur für das Schreiben und Auslesen von Theme-Dateien " +
                "sowie für Shell-Aktionen genutzt. Ohne Zugriff funktionieren " +
                "Bibliothek, Studio und GitHub weiterhin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Behaviour / Logging / Updates ─────────────────────────────────

@Composable
private fun BehaviorSettings() {
    val context = LocalContext.current
    var confirmApply by remember { mutableStateOf(AppSettings.isConfirmApplyEnabled(context)) }
    var keepScreenOn by remember { mutableStateOf(AppSettings.isKeepScreenOnEnabled(context)) }
    var strictRights by remember { mutableStateOf(AppSettings.isStrictRightsEnabled(context)) }
    var experimental by remember { mutableStateOf(AppSettings.isExperimentalPatcherEnabled(context)) }
    var rawCodes by remember { mutableStateOf(AppSettings.areRawCodesVisible(context)) }
    var bundledVisible by remember { mutableStateOf(AppSettings.isBundledApplyVisible(context)) }

    SettingsSection(
        title = stringResource(R.string.settings_behavior),
        subtitle = stringResource(R.string.settings_behavior_desc),
        icon = Icons.Default.Tune,
    ) {
        SetSwitchRow(
            stringResource(R.string.settings_confirm_apply),
            stringResource(R.string.settings_confirm_apply_desc),
            confirmApply,
        ) {
            confirmApply = it
            AppSettings.setConfirmApplyEnabled(context, it)
        }
        SetSwitchRow(
            stringResource(R.string.settings_keep_screen_on),
            stringResource(R.string.settings_keep_screen_on_desc),
            keepScreenOn,
        ) {
            keepScreenOn = it
            AppSettings.setKeepScreenOnEnabled(context, it)
        }
        SetSwitchRow(
            stringResource(R.string.settings_strict_rights),
            stringResource(R.string.settings_strict_rights_desc),
            strictRights,
        ) {
            strictRights = it
            AppSettings.setStrictRightsEnabled(context, it)
        }
        SetSwitchRow(
            stringResource(R.string.settings_experimental_patcher),
            stringResource(R.string.settings_experimental_patcher_desc),
            experimental,
        ) {
            experimental = it
            AppSettings.setExperimentalPatcherEnabled(context, it)
        }
        SetSwitchRow(
            stringResource(R.string.settings_raw_codes),
            stringResource(R.string.settings_raw_codes_desc),
            rawCodes,
        ) {
            rawCodes = it
            AppSettings.setRawCodesVisible(context, it)
        }
        SetSwitchRow(
            stringResource(R.string.settings_bundled_visible),
            stringResource(R.string.settings_bundled_visible_desc),
            bundledVisible,
        ) {
            bundledVisible = it
            AppSettings.setBundledApplyVisible(context, it)
        }
    }
}

@Composable
private fun LoggingSettings() {
    val context = LocalContext.current
    var autoscroll by remember { mutableStateOf(AppSettings.isLogAutoscrollEnabled(context)) }
    var verbose by remember { mutableStateOf(AppSettings.isVerboseLoggingEnabled(context)) }
    var maxLog by remember { mutableStateOf(AppSettings.getMaxLogEntries(context)) }

    SettingsSection(
        title = stringResource(R.string.settings_logging),
        subtitle = stringResource(R.string.settings_logging_desc),
        icon = Icons.Default.Settings,
    ) {
        SetSwitchRow(
            stringResource(R.string.settings_log_autoscroll),
            stringResource(R.string.settings_log_autoscroll_desc),
            autoscroll,
        ) {
            autoscroll = it
            AppSettings.setLogAutoscrollEnabled(context, it)
            AppLog.autoScroll = it
        }
        SetSwitchRow(
            stringResource(R.string.settings_log_verbose),
            stringResource(R.string.settings_log_verbose_desc),
            verbose,
        ) {
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
}

@Composable
private fun UpdateSettings(developerMode: Boolean) {
    val context = LocalContext.current
    var autoUpdate by remember { mutableStateOf(AppSettings.isAutoUpdateCheckEnabled(context)) }
    var channel by remember { mutableStateOf(AppSettings.getStoredUpdateChannel(context)) }
    var interval by remember { mutableStateOf(AppSettings.getUpdateIntervalHours(context)) }
    val versionName = remember { UpdateChecker.currentVersionName(context) }

    SettingsSection(
        title = stringResource(R.string.updates_title),
        subtitle = stringResource(R.string.settings_updates_desc),
        icon = Icons.Default.SystemUpdate,
    ) {
        SetSwitchRow(
            stringResource(R.string.settings_auto_check),
            stringResource(R.string.settings_auto_check_desc),
            autoUpdate,
        ) {
            autoUpdate = it
            AppSettings.setAutoUpdateCheckEnabled(context, it)
        }
        DevInfoRow(
            "Installationsart",
            if (AppSettings.isSelfUpdateSupported()) "In-App (GitHub)" else "Google Play",
        )
        if (developerMode) {
            SetDropdownRow(
                title = stringResource(R.string.settings_update_channel),
                selectedText = channel.name.lowercase(),
                options = UpdateChannel.entries,
                optionText = { it.name.lowercase() },
                onSelected = {
                    channel = it
                    AppSettings.setUpdateChannel(context, it)
                },
            )
            SetSliderRow(
                title = stringResource(R.string.settings_update_interval),
                valueText = "$interval h",
                value = interval,
                range = 1..168,
                onValueChange = {
                    interval = it
                    AppSettings.setUpdateIntervalHours(context, it)
                },
            )
        }
        SetButtonRow(
            title = stringResource(R.string.settings_open_updates),
            subtitle = stringResource(R.string.updates_installed) + " " + versionName,
            buttonText = stringResource(R.string.settings_open),
            onClick = { context.startActivity(Intent(context, UpdateActivity::class.java)) },
        )
    }
}

// ── Accounts ──────────────────────────────────────────────────────

@Composable
private fun AccountSettings(message: (String) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(GitHubApi.isSignedIn(context)) }
    var login by remember { mutableStateOf(AppSettings.getGithubLogin(context)) }
    var googleAccount by remember { mutableStateOf(AppSettings.getGoogleAccount(context)) }
    var crashAuto by remember { mutableStateOf(AppSettings.isCrashAutoReportEnabled(context)) }

    SettingsSection(
        title = "GitHub",
        subtitle = "Issues, PRs, Actions und Theme-Uploads.",
        icon = Icons.Default.Cloud,
    ) {
        DevInfoRow("Status", if (signedIn) "angemeldet als $login" else "nicht angemeldet")
        DevInfoRow("Repository", AppSettings.getGithubRepo(context))
        if (signedIn) {
            OutlinedButton(
                onClick = {
                    AppSettings.setGithubToken(context, "")
                    AppSettings.setGithubLogin(context, "")
                    signedIn = false
                    login = ""
                    message("GitHub abgemeldet")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abmelden") }
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(context, GitHubAuthActivity::class.java)
                        .putExtra(GitHubAuthActivity.EXTRA_MODE, GitHubAuthActivity.MODE_GITHUB)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (signedIn) "Erneut anmelden" else "Mit GitHub anmelden") }
        SetButtonRow(
            title = "GitHub-OAuth-Client-ID",
            subtitle = AppSettings.getGithubClientId(context).ifBlank { "nicht gesetzt" },
            buttonText = "Bearbeiten",
            onClick = {
                editing = "github"
                editValue = AppSettings.getGithubClientId(context)
            },
        )
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, GitHubActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("GitHub-Hub öffnen") }
    }

    SettingsSection(
        title = "Google",
        subtitle = "Konto-Verknüpfung für die Anmeldung in der App.",
        icon = Icons.Default.VerifiedUser,
    ) {
        DevInfoRow("Konto", googleAccount.ifBlank { "nicht verknüpft" })
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(context, GitHubAuthActivity::class.java)
                        .putExtra(GitHubAuthActivity.EXTRA_MODE, GitHubAuthActivity.MODE_GOOGLE)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (googleAccount.isBlank()) "Mit Google anmelden" else "Anderes Konto") }
        SetButtonRow(
            title = "Google-OAuth-Client-ID",
            subtitle = AppSettings.getGoogleClientId(context).ifBlank { "nicht gesetzt" },
            buttonText = "Bearbeiten",
            onClick = {
                editing = "google"
                editValue = AppSettings.getGoogleClientId(context)
            },
        )
        Text(
            "Google verlangt eine registrierte Client-ID mit Redirect-URI " +
                "http://localhost. Ohne ID bleibt die Google-Anmeldung deaktiviert.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SettingsSection(
        title = "Abstürze",
        icon = Icons.Default.BugReport,
    ) {
        SetSwitchRow(
            title = "Crash automatisch als Issue melden",
            subtitle = "Nur wenn GitHub angemeldet ist. Sonst wird der Bericht gespeichert und angeboten.",
            checked = crashAuto,
            onCheckedChange = {
                crashAuto = it
                AppSettings.setCrashAutoReportEnabled(context, it)
            },
        )
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(context, IssueEditorActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Issue-Tracker öffnen") }
    }

    editing?.let { which ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == "github") "GitHub-Client-ID" else "Google-Client-ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Öffentliche Client-ID der OAuth-App. Kein Secret eintragen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text("Client-ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (which == "github") {
                        AppSettings.setGithubClientId(context, editValue)
                    } else {
                        AppSettings.setGoogleClientId(context, editValue)
                    }
                    editing = null
                    message("Client-ID gespeichert")
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Abbrechen") }
            },
        )
    }
}

// ── Backup / About ────────────────────────────────────────────────

@Composable
private fun BackupSettings(onExport: () -> Unit, onImport: () -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(
        title = stringResource(R.string.settings_backup),
        subtitle = stringResource(R.string.settings_backup_desc),
        icon = Icons.Default.Backup,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_settings))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.import_settings))
            }
        }
        SetButtonRow(
            title = stringResource(R.string.settings_bug_report),
            subtitle = stringResource(R.string.bug_report_subtitle),
            buttonText = stringResource(R.string.settings_open),
            onClick = { context.startActivity(Intent(context, BugReportActivity::class.java)) },
        )
        SetButtonRow(
            title = stringResource(R.string.settings_reset),
            subtitle = stringResource(R.string.settings_reset_desc),
            buttonText = stringResource(R.string.settings_reset_button),
            onClick = onReset,
        )
    }
}

@Composable
private fun AboutSettings(
    versionName: String,
    developerMode: Boolean,
    onVersionTap: () -> Unit,
    onDisableDeveloper: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val context = LocalContext.current
    SettingsSection(title = stringResource(R.string.settings_about), icon = Icons.Default.Info) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.version), fontWeight = FontWeight.Medium)
                Text(
                    "$versionName · Code ${UpdateChecker.currentVersionCode(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onVersionTap) {
                Text(stringResource(R.string.version_tap_short))
            }
        }
        DevInfoRow(
            label = stringResource(R.string.developer_android),
            value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        DevInfoRow(
            label = stringResource(R.string.developer_device),
            value = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        DevInfoRow(
            label = "Formfaktor",
            value = DeviceProfiles.detect(context).description,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, CreditsActivity::class.java)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.credits_name)) }
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, BugReportActivity::class.java)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.bug_report_title))
            }
        }
        if (developerMode) {
            OutlinedButton(onClick = onOpenDeveloper, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.open_developer_tools))
            }
            SetButtonRow(
                title = stringResource(R.string.developer_mode_disable),
                buttonText = stringResource(R.string.common_disable),
                onClick = onDisableDeveloper,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────

@Composable
private fun modeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.setup_mode_system
        ThemeMode.LIGHT -> R.string.setup_mode_light
        ThemeMode.DARK -> R.string.setup_mode_dark
        ThemeMode.AMOLED -> R.string.setup_mode_amoled
    }
)

@Composable
private fun languageLabel(tag: String): String =
    if (tag.isBlank()) {
        stringResource(R.string.language_system)
    } else {
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayName(locale).replaceFirstChar { it.uppercase() } + " ($tag)"
    }

private fun shizukuStatusText(status: ShizukuHelper.Status): String = when (status) {
    ShizukuHelper.Status.READY -> "Bereit"
    ShizukuHelper.Status.NO_PERMISSION -> "Berechtigung fehlt"
    ShizukuHelper.Status.NOT_RUNNING -> "Nicht gestartet"
    ShizukuHelper.Status.NOT_INSTALLED -> "Nicht installiert"
}

private data class RootCheckResult(val available: Boolean, val message: String)

private suspend fun checkRootAccess(context: Context): RootCheckResult =
    withContext(Dispatchers.IO) {
        try {
            val timeout = AppSettings.getRootTimeoutSeconds(context).toLong()
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeout, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return@withContext RootCheckResult(false, context.getString(R.string.root_timeout))
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            RootCheckResult(
                available = process.exitValue() == 0 && output.contains("uid=0"),
                message = output.ifBlank { context.getString(R.string.root_no_output) },
            )
        } catch (e: Exception) {
            RootCheckResult(false, e.message ?: context.getString(R.string.root_su_missing))
        }
    }
