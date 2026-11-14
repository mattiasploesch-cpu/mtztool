package at.libmatti.mtztool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import at.libmatti.mtztool.activity.ApplyThemeActivity
import at.libmatti.mtztool.activity.BugReportActivity
import at.libmatti.mtztool.activity.GitHubActivity
import at.libmatti.mtztool.activity.IssueEditorActivity
import at.libmatti.mtztool.activity.ThemeBrowserActivity
import at.libmatti.mtztool.activity.ThemeRightsActivity
import at.libmatti.mtztool.activity.ThemeStudioActivity
import at.libmatti.mtztool.activity.CreditsActivity
import at.libmatti.mtztool.activity.DeveloperActivity
import at.libmatti.mtztool.activity.IconPackActivity
import at.libmatti.mtztool.activity.LocalizedActivity
import at.libmatti.mtztool.activity.MraCheckerActivity
import at.libmatti.mtztool.activity.SettingsActivity
import at.libmatti.mtztool.activity.SetupWizardActivity
import at.libmatti.mtztool.activity.ThemeCompareActivity
import at.libmatti.mtztool.activity.ThemeExtractorActivity
import at.libmatti.mtztool.activity.ThemeInspectorActivity
import at.libmatti.mtztool.activity.ThemePatchActivity
import at.libmatti.mtztool.activity.ThemePreviewActivity
import at.libmatti.mtztool.activity.ToolsActivity
import at.libmatti.mtztool.activity.UpdateActivity
import at.libmatti.mtztool.device.DeviceProfile
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : LocalizedActivity() {

    private var awaitingSetup = false
    private var updateChecking = false
    private var availableUpdate by mutableStateOf<UpdateRelease?>(null)
    private var pendingWarning by mutableStateOf<LibraryImport?>(null)
    private var pendingError by mutableStateOf<String?>(null)
    private var pendingErrorCodes by mutableStateOf<List<String>>(emptyList())
    private var pendingCrash by mutableStateOf<CrashReporter.Report?>(null)

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            importTheme(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.syncFrom(this)
        CrashReporter.install(this)
        ShizukuHelper.selectedProvider = AppSettings.getPrivilegeProvider(this)

        if (!AppSettings.isSetupCompleted(this)) {
            awaitingSetup = true
            setContent {
                MTZToolTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            startActivity(Intent(this, SetupWizardActivity::class.java))
            return
        }

        handleIncomingIntent(intent)
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        onPickFile = { filePicker.launch(arrayOf("*/*")) },
                        onApply = ::requestApply,
                        onImportLibrary = ::importToLibrary,
                        onPatch = { startActivity(Intent(this, ThemePatchActivity::class.java)) },
                        onIconPack = { startActivity(Intent(this, IconPackActivity::class.java)) },
                        onOpenTool = { startActivity(Intent(this, it)) },
                        onOpenTools = { startActivity(Intent(this, ToolsActivity::class.java)) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onOpenCredits = { startActivity(Intent(this, CreditsActivity::class.java)) },
                        onOpenDeveloper = { startActivity(Intent(this, DeveloperActivity::class.java)) },
                        onOpenBugReport = { startActivity(Intent(this, BugReportActivity::class.java)) },
                        onOpenUpdates = { startActivity(Intent(this, UpdateActivity::class.java)) },
                        onOpenStudio = { startActivity(Intent(this, ThemeStudioActivity::class.java)) },
                        onOpenLibrary = { startActivity(Intent(this, ThemeBrowserActivity::class.java)) },
                        onOpenRights = { startActivity(Intent(this, ThemeRightsActivity::class.java)) },
                        onOpenGitHub = { startActivity(Intent(this, GitHubActivity::class.java)) },
                        onNewIssue = {
                            startActivity(
                                Intent(this, IssueEditorActivity::class.java)
                                    .putExtra(IssueEditorActivity.EXTRA_KIND, "BUG")
                            )
                        },
                        crashReport = pendingCrash,
                        onDismissCrash = {
                            CrashReporter.clear(this)
                            pendingCrash = null
                        },
                        onCreateCrashIssue = { report ->
                            CrashReporter.clear(this)
                            pendingCrash = null
                            startActivity(
                                Intent(this, IssueEditorActivity::class.java)
                                    .putExtra(IssueEditorActivity.EXTRA_KIND, "CRASH")
                                    .putExtra(
                                        IssueEditorActivity.EXTRA_TITLE,
                                        "Absturz: " + report.exception.take(60),
                                    )
                                    .putExtra(IssueEditorActivity.EXTRA_CRASH, report.stacktrace)
                            )
                        },
                        onCheckUpdates = { maybeCheckUpdates(manual = true) },
                        updateChecking = updateChecking,
                        availableUpdate = availableUpdate,
                        onDismissUpdate = {
                            availableUpdate?.let {
                                AppSettings.setSkippedUpdateVersionCode(this, it.versionCode)
                            }
                            availableUpdate = null
                        },
                        onApplyBundled = ::applyBundled,
                        pendingWarning = pendingWarning,
                        onDismissWarning = { pendingWarning = null },
                        pendingError = pendingError,
                        pendingErrorCodes = pendingErrorCodes,
                        onDismissError = {
                            pendingError = null
                            pendingErrorCodes = emptyList()
                        },
                    )
                }
            }
        }
        maybeReportCrash()
    }

    /** Creates an issue automatically for the last crash when signed in. */
    private fun maybeReportCrash() {
        val report = CrashReporter.pending(this) ?: return
        pendingCrash = report
        if (!AppSettings.isCrashAutoReportEnabled(this)) return
        if (!GitHubApi.isSignedIn(this)) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitHubApi.createIssue(
                    context = this@MainActivity,
                    title = "[Crash] " + report.exception.take(80),
                    body = CrashReporter.issueBody(this@MainActivity, report),
                    labels = listOf("bug", "crash"),
                )
            }
            result.fold(
                onSuccess = { issue ->
                    AppLog.success("Crash-Issue automatisch erstellt: #${issue.number}")
                    CrashReporter.clear(this@MainActivity)
                    pendingCrash = null
                },
                onFailure = {
                    AppLog.warn("Automatisches Crash-Issue fehlgeschlagen: ${it.message}")
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        AppLog.syncFrom(this)
        ShizukuHelper.selectedProvider = AppSettings.getPrivilegeProvider(this)
        if (awaitingSetup && AppSettings.isSetupCompleted(this)) {
            awaitingSetup = false
            recreate()
            return
        }
        if (!awaitingSetup) maybeCheckUpdates(manual = false)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        importTheme(uri)
    }

    private fun importTheme(uri: Uri) {
        lifecycleScope.launch {
            ThemeSession.open(this@MainActivity, uri) { line ->
                runOnUiThread { AppLog.info(line) }
            }
        }
    }

    private fun requestApply() {
        val theme = ThemeSession.current
        if (theme == null) {
            AppLog.warn("Kein Theme im Workspace. Bitte zuerst eine .mtz öffnen.")
            return
        }
        val provider = AppSettings.getPrivilegeProvider(this)
        if (provider == PrivilegeProvider.ROOT) {
            AppLog.info("Starte direkte Theme-Anwendung über Root …")
            startActivity(
                Intent(this, ApplyThemeActivity::class.java).apply {
                    putExtra(ApplyThemeActivity.EXTRA_USE_SESSION, true)
                }
            )
            return
        }

        if (ShizukuHelper.getStatus() != ShizukuHelper.Status.READY) {
            AppLog.warn("Shizuku ist nicht bereit. Bitte Berechtigung erteilen oder Shizuku starten.")
            if (ShizukuHelper.getStatus() == ShizukuHelper.Status.NO_PERMISSION) {
                ShizukuHelper.requestPermission()
            }
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        lifecycleScope.launch {
            AppLog.info("Übergebe „${theme.displayTitle}“ an den Theme Manager (Shizuku) …")
            val result = withContext(Dispatchers.IO) {
                ShizukuThemeApplier.apply(this@MainActivity, theme) { line ->
                    runOnUiThread { AppLog.info(line) }
                }
            }
            result.fold(
                onSuccess = {
                    AppLog.success(it.message)
                    Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show()
                },
                onFailure = {
                    val message = it.message ?: it.javaClass.simpleName
                    AppLog.error("Theme-Anwendung fehlgeschlagen: $message")
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun importToLibrary() {
        val theme = ThemeSession.current
        if (theme == null) {
            AppLog.warn("Kein Theme im Workspace. Bitte zuerst eine .mtz öffnen.")
            return
        }
        val provider = AppSettings.getPrivilegeProvider(this)
        if (provider != PrivilegeProvider.ROOT &&
            ShizukuHelper.getStatus() != ShizukuHelper.Status.READY
        ) {
            AppLog.warn("Für den Bibliotheks-Import wird Root oder Shizuku benötigt.")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        lifecycleScope.launch {
            AppLog.info("Importiere „${theme.displayTitle}“ in die Theme-Bibliothek …")
            val result = withContext(Dispatchers.IO) {
                ShizukuHelper.importMtzBytesToLibrary(
                    this@MainActivity,
                    theme.bytes,
                    theme.displayTitle,
                )
            }
            result.fold(
                onSuccess = { imported ->
                    AppLog.success(imported.message)
                    if (!imported.fullTheme) {
                        AppLog.warn(imported.reason)
                        if (AppSettings.isStrictRightsEnabled(this@MainActivity)) {
                            pendingError = "Strikte Rechteprüfung aktiv: ${imported.reason}"
                            pendingErrorCodes = imported.droppedCodes
                        } else {
                            pendingWarning = imported
                        }
                    }
                },
                onFailure = { error ->
                    val base = error.message ?: error.javaClass.simpleName
                    AppLog.error("Import fehlgeschlagen: $base")
                    pendingErrorCodes = (error as? ImportFailure)?.missingCodes.orEmpty()
                    pendingError = ThemeLibraryWriter.withImportHints(base, theme.name)
                }
            )
        }
    }

    private fun applyBundled() {
        AppLog.info("Wende mitgeliefertes Theme über Root an …")
        startActivity(
            Intent(this, ApplyThemeActivity::class.java).apply {
                putExtra(ApplyThemeActivity.EXTRA_USE_BUNDLED, true)
            }
        )
    }

    private fun maybeCheckUpdates(manual: Boolean) {
        if (!manual && !AppSettings.isAutoUpdateCheckEnabled(this)) return
        if (updateChecking) return
        val elapsed = System.currentTimeMillis() - AppSettings.getLastUpdateCheck(this)
        val intervalMillis = AppSettings.getUpdateIntervalHours(this) * 60L * 60L * 1000L
        if (!manual && elapsed < intervalMillis) return

        updateChecking = true
        lifecycleScope.launch {
            val result = UpdateChecker.check(this@MainActivity)
            updateChecking = false
            result.fold(
                onSuccess = { release ->
                    if (release == null) {
                        if (manual) {
                            AppLog.info("Update-Prüfung: Die App ist aktuell.")
                            Toast.makeText(
                                this@MainActivity,
                                "Die App ist aktuell.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        availableUpdate = null
                    } else if (release.versionCode !=
                        AppSettings.getSkippedUpdateVersionCode(this@MainActivity) ||
                        manual
                    ) {
                        availableUpdate = release
                        AppLog.info(
                            "Update verfügbar: ${release.versionName} " +
                                "(${release.channel.name.lowercase()})."
                        )
                    }
                },
                onFailure = {
                    if (manual) {
                        val message = it.message ?: it.javaClass.simpleName
                        AppLog.error("Update-Prüfung fehlgeschlagen: $message")
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

// ── Dashboard ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onPickFile: () -> Unit,
    onApply: () -> Unit,
    onImportLibrary: () -> Unit,
    onPatch: () -> Unit,
    onIconPack: () -> Unit,
    onOpenTool: (Class<*>) -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenBugReport: () -> Unit,
    onOpenUpdates: () -> Unit,
    onCheckUpdates: () -> Unit,
    updateChecking: Boolean,
    availableUpdate: UpdateRelease?,
    onDismissUpdate: () -> Unit,
    onApplyBundled: () -> Unit,
    pendingWarning: LibraryImport?,
    onDismissWarning: () -> Unit,
    pendingError: String?,
    pendingErrorCodes: List<String>,
    onDismissError: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenRights: () -> Unit,
    onOpenGitHub: () -> Unit,
    onNewIssue: () -> Unit,
    crashReport: CrashReporter.Report?,
    onDismissCrash: () -> Unit,
    onCreateCrashIssue: (CrashReporter.Report) -> Unit,
) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val developerMode = AppSettings.isDeveloperModeEnabled(context)
    val provider = AppSettings.getPrivilegeProvider(context)
    val theme = ThemeSession.current
    val summary = ThemeSession.summary
    val importing = ThemeSession.importing

    var shizukuStatus by remember { mutableStateOf(ShizukuHelper.getStatus()) }
    var confirmApply by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val permission = Shizuku.OnRequestPermissionResultListener { _, result ->
            shizukuStatus = ShizukuHelper.getStatus()
            if (result == PackageManager.PERMISSION_GRANTED) {
                AppLog.success("Shizuku-Berechtigung erteilt.")
            } else {
                AppLog.warn("Shizuku-Berechtigung verweigert.")
            }
        }
        val received = Shizuku.OnBinderReceivedListener {
            shizukuStatus = ShizukuHelper.getStatus()
            AppLog.success("Shizuku verbunden.")
        }
        val dead = Shizuku.OnBinderDeadListener {
            shizukuStatus = ShizukuHelper.getStatus()
            AppLog.warn("Shizuku-Verbindung verloren.")
        }
        ShizukuHelper.addPermissionListener(permission)
        ShizukuHelper.addBinderListener(received)
        ShizukuHelper.addBinderDeadListener(dead)
        onDispose {
            ShizukuHelper.removePermissionListener(permission)
            ShizukuHelper.removeBinderListener(received)
            ShizukuHelper.removeBinderDeadListener(dead)
        }
    }

    val shellReady = provider == PrivilegeProvider.ROOT ||
        shizukuStatus == ShizukuHelper.Status.READY

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                DrawerHeader(provider)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Start") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text(stringResource(R.string.tools_name)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenTools()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Store, contentDescription = null) },
                    label = { Text("Theme Store") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenLibrary()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    label = { Text("GitHub & Issues") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenGitHub()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                    label = { Text("Updates") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenUpdates()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings_name)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    label = { Text("Bug melden") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenBugReport()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PeopleAlt, contentDescription = null) },
                    label = { Text(stringResource(R.string.credits_name)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenCredits()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                if (developerMode) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        label = { Text(stringResource(R.string.developer_mode)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenDeveloper()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    if (AppSettings.isBundledApplyVisible(context)) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.RocketLaunch, contentDescription = null) },
                            label = { Text("Bundled Theme anwenden") },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onApplyBundled()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "MTZ Tool ${versionName(context)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(28.dp),
                )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Theme workspace & patcher",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        if (updateChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(onClick = onCheckUpdates) {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    contentDescription = "Nach Updates suchen",
                                    tint = if (availableUpdate != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_name))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (profile.contentMaxWidthDp == Int.MAX_VALUE) Modifier
                        else Modifier.widthIn(max = profile.contentMaxWidthDp.dp)
                    ),
                contentPadding = PaddingValues(
                    start = profile.horizontalPaddingDp.dp,
                    end = profile.horizontalPaddingDp.dp,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HeroCard(theme, summary, importing, onPickFile) }

                if (availableUpdate != null) {
                    item {
                        UpdateBanner(
                            release = availableUpdate,
                            onOpen = onOpenUpdates,
                            onDismiss = onDismissUpdate,
                        )
                    }
                }

                item {
                    Text(
                        text = "Aktionen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Palette,
                            title = "Theme anwenden",
                            subtitle = if (provider == PrivilegeProvider.ROOT) {
                                "Direkt über Root"
                            } else {
                                "Über Shizuku / Theme Manager"
                            },
                            enabled = theme != null && shellReady && !importing,
                            onClick = { confirmApply = true },
                        )
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Store,
                            title = "In Theme-App",
                            subtitle = "In die Offline-Bibliothek importieren",
                            enabled = theme != null && shellReady && !importing,
                            onClick = onImportLibrary,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Edit,
                            title = "MTZ patchen",
                            subtitle = "Inhalte ändern und neu exportieren",
                            enabled = !importing,
                            onClick = onPatch,
                        )
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Apps,
                            title = "Icon-Pack",
                            subtitle = "APK aus dem Theme erzeugen",
                            enabled = theme != null && shellReady && !importing,
                            onClick = onIconPack,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Palette,
                            title = "Theme Studio",
                            subtitle = "Gerätetheme pullen, eigene Themes bauen",
                            enabled = !importing,
                            onClick = onOpenStudio,
                        )
                        ActionTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Store,
                            title = "Theme-Bibliothek",
                            subtitle = "Themes dauerhaft speichern & entdecken",
                            enabled = true,
                            onClick = onOpenLibrary,
                        )
                    }
                }

                item {
                    GitHubCard(onOpen = onOpenGitHub, onNewIssue = onNewIssue)
                }

                if (!shellReady) {
                    item {
                        PrivilegeWarningCard(
                            provider = provider,
                            status = shizukuStatus,
                            onAllow = {
                                if (shizukuStatus == ShizukuHelper.Status.NO_PERMISSION) {
                                    ShizukuHelper.requestPermission()
                                } else {
                                    onOpenSettings()
                                }
                            },
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenTools, modifier = Modifier.weight(1f)) {
                            Text("Alle Tools") 
                        }
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                            Text("Einstellungen")
                        }
                    }
                }
            }
            }
        }
    }

    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = onDismissCrash,
            icon = { Icon(Icons.Default.Error, contentDescription = null) },
            title = { Text("Letzter Absturz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report.summary())
                    Text(
                        "Du kannst den Absturz als Issue mit Stacktrace und Log melden.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onCreateCrashIssue(report) }) {
                    Text("Issue erstellen")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCrash) { Text("Verwerfen") }
            },
        )
    }

    if (confirmApply && theme != null) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            icon = { Icon(Icons.Default.Palette, contentDescription = null) },
            title = { Text("Theme anwenden?") },
            text = {
                Text(
                    if (provider == PrivilegeProvider.ROOT) {
                        "„${theme.displayTitle}“ wird direkt nach /data/system/theme " +
                            "geschrieben und anschließend SystemUI/Launcher neu gestartet."
                    } else {
                        "„${theme.displayTitle}“ wird über Shizuku an den Xiaomi Theme " +
                            "Manager übergeben. Die Anwendung wird dort abgeschlossen."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmApply = false
                    onApply()
                }) { Text("Anwenden") }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) { Text("Abbrechen") }
            },
        )
    }

    pendingWarning?.let { warning ->
        var showAlternatives by remember(warning) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissWarning,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(if (showAlternatives) "Alternativen" else "Theme nur teilweise importiert") },
            text = {
                Column(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showAlternatives) {
                        AlternativesList(warning.droppedCodes, context)
                    } else {
                        Text(warning.reason)
                        if (warning.appliedCodes.isNotEmpty()) {
                            Text("Importiert: " + warning.appliedCodes.joinToString(", ") {
                                ThemeComponents.displayName(it)
                            })
                        }
                        if (warning.droppedCodes.isNotEmpty()) {
                            Text("Nicht enthalten: " + warning.droppedCodes.joinToString(", ") {
                                ThemeComponents.displayName(it)
                            })
                            TextButton(onClick = { showAlternatives = true }) {
                                Text("Alternativen anzeigen")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissWarning) { Text("Verstanden") }
            },
        )
    }

    pendingError?.let { error ->
        var showAlternatives by remember(error) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissError,
            icon = { Icon(Icons.Default.Error, contentDescription = null) },
            title = { Text(if (showAlternatives) "Alternativen" else "Import fehlgeschlagen") },
            text = {
                Column(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showAlternatives) {
                        AlternativesList(pendingErrorCodes, context)
                    } else {
                        Text(error)
                        if (pendingErrorCodes.isNotEmpty()) {
                            TextButton(onClick = { showAlternatives = true }) {
                                Text("Alternativen anzeigen")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("OK") }
            },
        )
    }
}

// ── Dashboard components ──────────────────────────────────────────

@Composable
private fun DrawerHeader(provider: PrivilegeProvider) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(provider.titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HeroCard(
    theme: ThemeSession.Theme?,
    summary: ThemeSession.Summary?,
    importing: Boolean,
    onPickFile: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    )
                )
            )
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssistChip(
                onClick = onPickFile,
                label = {
                    Text(
                        text = if (importing) "Import läuft …" else "Theme workspace",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingIcon = {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
            )
            Text(
                text = summary?.title ?: theme?.displayTitle ?: "Ein Theme. Alle Werkzeuge.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (theme == null) {
                    "Öffne eine .mtz oder .bak einmal – alle Tools greifen danach " +
                        "auf dieselbe In-Memory-Version zu."
                } else {
                    "${theme.name} · ${formatBytes(theme.size)}" +
                        (summary?.let { " · ${it.componentCount} Komponenten" } ?: "")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Button(
                onClick = onPickFile,
                enabled = !importing,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (theme == null) "MTZ öffnen" else "Anderes Theme öffnen")
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    theme: ThemeSession.Theme?,
    summary: ThemeSession.Summary?,
    importing: Boolean,
    onPickFile: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Workspace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (theme != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Workspace leeren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (importing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Theme wird in den Arbeitsspeicher geladen …")
                }
            } else if (theme == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Noch kein Theme geladen",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Öffne eine Datei, um loszulegen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("MTZ / BAK auswählen")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary?.title ?: theme.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${theme.name} · ${formatBytes(theme.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoPill("${summary?.componentCount ?: "–"} Komponenten")
                    InfoPill("${summary?.previewCount ?: "–"} Vorschauen")
                    InfoPill(
                        if (summary == null) "Analyse …"
                        else if (summary.fullTheme) "Volltheme" else "Teilweise",
                        accent = summary?.fullTheme == true,
                    )
                }

                if (summary != null) {
                    Text(
                        text = buildString {
                            if (summary.author.isNotBlank()) append("von ${summary.author} · ")
                            append("v${summary.version}")
                            if (summary.candidateCount > 1) append(" · ${summary.candidateCount} Themes")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!summary.fullTheme && summary.rightsReason.isNotBlank()) {
                        Text(
                            text = summary.rightsReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                Text(
                    text = "SHA-1 ${theme.sha1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                        Text("Ersetzen")
                    }
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text("Entfernen")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (accent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ToolShortcutCard(
    profile: DeviceProfile,
    onOpenTool: (Class<*>) -> Unit,
    onOpenTools: () -> Unit,
) {
    data class Shortcut(
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val onClick: () -> Unit,
    )

    val shortcuts = listOf(
        Shortcut(
            Icons.Default.Search,
            stringResource(R.string.tool_inspector),
            stringResource(R.string.tool_inspector_desc),
        ) { onOpenTool(ThemeInspectorActivity::class.java) },
        Shortcut(
            Icons.Default.Edit,
            stringResource(R.string.patcher_title),
            stringResource(R.string.patcher_intro),
        ) { onOpenTool(ThemePatchActivity::class.java) },
        Shortcut(
            Icons.Default.FileDownload,
            stringResource(R.string.tool_extractor),
            stringResource(R.string.tool_extractor_desc),
        ) { onOpenTool(ThemeExtractorActivity::class.java) },
        Shortcut(
            Icons.Default.CompareArrows,
            stringResource(R.string.tool_compare),
            stringResource(R.string.tool_compare_desc),
        ) { onOpenTool(ThemeCompareActivity::class.java) },
        Shortcut(
            Icons.Default.VerifiedUser,
            stringResource(R.string.tool_mra_checker),
            stringResource(R.string.tool_mra_checker_desc),
        ) { onOpenTool(MraCheckerActivity::class.java) },
        Shortcut(
            Icons.Default.PhotoLibrary,
            stringResource(R.string.tool_previews),
            stringResource(R.string.tool_previews_desc),
        ) { onOpenTool(ThemePreviewActivity::class.java) },
    )

    // Phones get a short list; tablets and desktop windows show every tool.
    val visible = if (profile.isExpanded) shortcuts else shortcuts.take(3)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            visible.forEachIndexed { index, shortcut ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
                }
                ToolRow(
                    shortcut.icon,
                    shortcut.title,
                    shortcut.subtitle,
                    onClick = shortcut.onClick,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
            ToolRow(
                Icons.Default.Build,
                stringResource(R.string.tools_name),
                stringResource(R.string.tools_desc),
                onClick = onOpenTools,
            )
        }
    }
}

@Composable
private fun GitHubCard(onOpen: () -> Unit, onNewIssue: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "GitHub & Community",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                "Issues in der App erstellen und verfolgen, Pull Requests prüfen, " +
                    "Actions ansehen und Themes hochladen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("GitHub öffnen")
                }
                OutlinedButton(onClick = onNewIssue, modifier = Modifier.weight(1f)) {
                    Text("Neues Issue")
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivilegeWarningCard(
    provider: PrivilegeProvider,
    status: ShizukuHelper.Status,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Privilegierter Zugriff benötigt",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = when (status) {
                    ShizukuHelper.Status.NO_PERMISSION ->
                        "Shizuku läuft, aber MTZ Tool hat noch keine Berechtigung."
                    ShizukuHelper.Status.NOT_RUNNING ->
                        "Shizuku ist installiert, aber nicht gestartet."
                    ShizukuHelper.Status.NOT_INSTALLED ->
                        "Shizuku ist nicht installiert. Root oder Shizuku werden benötigt."
                    ShizukuHelper.Status.READY ->
                        "${stringResource(provider.titleRes)} ist ausgewählt."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == ShizukuHelper.Status.NO_PERMISSION) {
                    Button(onClick = onAllow) { Text("Berechtigung erteilen") }
                }
                OutlinedButton(onClick = onOpenSettings) { Text("Einstellungen") }
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    release: UpdateRelease,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update ${release.versionName} verfügbar",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (release.prerelease) "Pre-release · ${release.tag}"
                    else "Stable · ${release.tag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            TextButton(onClick = onOpen) { Text("Ansehen") }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Später",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AlternativesList(codes: List<String>, context: Context) {
    fun openUrl(url: String) {
        val packageId = url.substringAfter("id=", "")
        if (url.startsWith("https://play.google.com/store/apps/details?id=") &&
            packageId.isNotBlank()
        ) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageId"))
                        .setPackage("com.android.vending")
                )
                return
            } catch (_: Exception) {
                // Fall back to the browser.
            }
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                Toast.makeText(context, "Link konnte nicht geöffnet werden.", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Für diese Bestandteile gibt es ohne Xiaomi-Rechte Alternativen:",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeAlternatives.forCodes(codes).forEach { group ->
                item {
                    Text(group.label, fontWeight = FontWeight.Bold)
                    group.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    group.suggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openUrl(suggestion.url) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                        ) {
                            Text(
                                "${suggestion.name} ↗",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(suggestion.description, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun versionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "2.0.0"
