package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Theme library and browser: installed themes, remote GitHub themes and the
 * theme upload flow that prepares a pull request.
 */
class ThemeBrowserActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThemeBrowserScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableIntStateOf(0) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    val library = remember { mutableStateListOf<ThemeLibrary.Item>() }
    var pendingSave by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    var sourceIndex by remember { mutableIntStateOf(0) }
    var remote by remember { mutableStateOf<List<ThemeBrowser.RemoteTheme>>(emptyList()) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var remoteRefresh by remember { mutableIntStateOf(0) }
    var downloadName by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var uploadFolder by remember { mutableStateOf("themes") }

    var uploadFile by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadBusy by remember { mutableStateOf(false) }
    var uploadPrUrl by remember { mutableStateOf<String?>(null) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadProgress by remember { mutableStateOf("") }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val pending = pendingSave
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pending.second) }
                        ?: error("Zieldatei konnte nicht geöffnet werden")
                }
            }
            result.fold(
                onSuccess = { message("Gespeichert: ${pending.first}") },
                onFailure = { message("Fehler: ${it.message}") },
            )
            pendingSave = null
        }
    }

    val themePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ThemeLibrary.importUri(context, uri, source = "Datei-Upload")
            }
            result.fold(
                onSuccess = {
                    libraryRevision += 1
                    message("„${it.name}“ importiert")
                },
                onFailure = { message("Import fehlgeschlagen: ${it.message}") },
            )
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                message("Datei konnte nicht gelesen werden")
                return@launch
            }
            val name = ThemeApplier.getFileName(context, uri) ?: "theme.mtz"
            uploadFile = name to bytes
            uploadTitle = "Theme: " + name.substringBeforeLast('.')
        }
    }

    LaunchedEffect(libraryRevision) {
        val items = withContext(Dispatchers.IO) { ThemeLibrary.list(context) }
        library.clear()
        library.addAll(items)
    }

    LaunchedEffect(sourceIndex, remoteRefresh) {
        remoteLoading = true
        remoteError = null
        val result = when (sourceIndex) {
            0 -> ThemeBrowser.githubThemes(context)
            1 -> ThemeBrowser.appThemes(context)
            else -> ThemeBrowser.manifestThemes(context)
        }
        remoteLoading = false
        result.fold(
            onSuccess = { remote = it },
            onFailure = {
                remote = emptyList()
                remoteError = it.message ?: "Quelle nicht erreichbar"
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Theme-Bibliothek", fontWeight = FontWeight.Bold)
                        Text(
                            "Immer da · GitHub · Upload",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Bibliothek") },
                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Entdecken") },
                    icon = { Icon(Icons.Default.Public, contentDescription = null) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("Upload") },
                    icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                )
            }

            AdaptiveContent(profile = profile) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = profile.horizontalPaddingDp.dp,
                        end = profile.horizontalPaddingDp.dp,
                        top = 14.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (tab == 0) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${library.size} Themes · " +
                                            ThemeLibrary.totalSize(context).let {
                                                if (it < 1024 * 1024) "${it / 1024} KB"
                                                else String.format(
                                                    java.util.Locale.US,
                                                    "%.1f MB",
                                                    it / 1024.0 / 1024.0,
                                                )
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { themePicker.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Importieren")
                                }
                            }
                        }
                        if (library.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            "Deine Bibliothek ist leer",
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Importierte Themes bleiben dauerhaft in der App " +
                                                "gespeichert, bis du sie löschst.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        items(library.size) { index ->
                            val item = library[index]
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${item.bytesLabel} · ${item.source} · SHA-256 ${item.sha256.take(16)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                scope.launch {
                                                    val bytes = withContext(Dispatchers.IO) {
                                                        ThemeLibrary.read(context, item)
                                                    }
                                                    if (bytes == null) {
                                                        message("Theme konnte nicht gelesen werden")
                                                    } else {
                                                        ThemeSession.openBytes(
                                                            context,
                                                            item.name,
                                                            bytes,
                                                        ).onSuccess {
                                                            message("Im Workspace geladen")
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Laden")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val bytes = withContext(Dispatchers.IO) {
                                                        ThemeLibrary.read(context, item)
                                                    }
                                                    if (bytes != null) {
                                                        pendingSave = item.name to bytes
                                                        saver.launch(item.name)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null)
                                        }
                                        IconButton(onClick = {
                                            ThemeLibrary.delete(context, item)
                                            libraryRevision += 1
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (tab == 1) {
                        item {
                            Card(shape = RoundedCornerShape(22.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "Quelle",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { sourceIndex = 0 },
                                            enabled = sourceIndex != 0,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Themes") }
                                        OutlinedButton(
                                            onClick = { sourceIndex = 1 },
                                            enabled = sourceIndex != 1,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("App-Themes") }
                                        OutlinedButton(
                                            onClick = { sourceIndex = 2 },
                                            enabled = sourceIndex != 2,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Manifest") }
                                    }
                                    Text(
                                        when (sourceIndex) {
                                            0 -> "Themes aus dem Ordner themes/ des Repos " +
                                                AppSettings.getGithubRepo(context)
                                            1 -> "App-Themes aus dem Ordner app-themes/ " +
                                                "(pro-App-Designs, Icons, Schriften)"
                                            else -> "themes/manifest.json mit Name, URL und SHA-256"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { remoteRefresh += 1 },
                                            enabled = !remoteLoading,
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Aktualisieren")
                                        }
                                        Text(
                                            "Nativer Store · kein Web-UI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                        if (remoteLoading) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Lade Theme-Liste …")
                                }
                            }
                        }
                        remoteError?.let { error ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text(
                                            "Tipp: Lege im Repo einen Ordner themes/ mit " +
                                                ".mtz-Dateien an oder nutze den URL-Import.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text("Suchen") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val visibleRemote = remote.filter {
                            search.isBlank() || it.name.contains(search, ignoreCase = true)
                        }
                        items(visibleRemote.size) { index ->
                            val theme = visibleRemote[index]
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(theme.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${theme.sizeLabel} · ${theme.source}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (downloadName == theme.name) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        IconButton(onClick = {
                                            downloadName = theme.name
                                            scope.launch {
                                                ThemeBrowser.downloadToLibrary(context, theme).fold(
                                                    onSuccess = {
                                                        libraryRevision += 1
                                                        message("„${it.name}“ heruntergeladen")
                                                    },
                                                    onFailure = {
                                                        message("Download fehlgeschlagen: ${it.message}")
                                                    },
                                                )
                                                downloadName = null
                                            }
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Herunterladen")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Theme per URL importieren",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        label = { Text("Direkter .mtz-Link") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (urlInput.isBlank()) return@OutlinedButton
                                            scope.launch {
                                                ThemeBrowser.importUrl(context, urlInput.trim()).fold(
                                                    onSuccess = {
                                                        libraryRevision += 1
                                                        urlInput = ""
                                                        message("„${it.name}“ importiert")
                                                    },
                                                    onFailure = {
                                                        message("Import fehlgeschlagen: ${it.message}")
                                                    },
                                                )
                                            }
                                        },
                                        enabled = urlInput.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Importieren")
                                    }
                                }
                            }
                        }
                    }

                    if (tab == 2) {
                        item {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        "Theme-Upload als Pull Request",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        "Der Upload committet das Theme in themes/ auf einen " +
                                            "neuen Branch und erstellt einen Draft-PR. Du " +
                                            "prüfst und mergst ihn auf GitHub.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("Datei auswählen", fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { uploadFolder = "themes" },
                                            enabled = uploadFolder != "themes",
                                            modifier = Modifier.weight(1f),
                                        ) { Text("themes/") }
                                        OutlinedButton(
                                            onClick = { uploadFolder = "app-themes" },
                                            enabled = uploadFolder != "app-themes",
                                            modifier = Modifier.weight(1f),
                                        ) { Text("app-themes/") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { uploadPicker.launch(arrayOf("*/*")) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(uploadFile?.first ?: "Theme wählen")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val theme = ThemeSession.current
                                                if (theme == null) {
                                                    message("Kein Theme im Workspace")
                                                } else {
                                                    uploadFile = theme.name to theme.bytes
                                                    uploadTitle = "Theme: " + theme.displayTitle
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Workspace")
                                        }
                                    }
                                    OutlinedTextField(
                                        value = uploadTitle,
                                        onValueChange = { uploadTitle = it },
                                        label = { Text("PR-Titel") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "Ziel: $uploadFolder/${uploadFile?.first ?: "…"} in " +
                                            AppSettings.getGithubRepo(context),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (!GitHubApi.isSignedIn(context)) {
                                        Text(
                                            "Bitte zuerst mit GitHub anmelden.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        OutlinedButton(onClick = {
                                            context.startActivity(
                                                android.content.Intent(context, GitHubAuthActivity::class.java)
                                                    .putExtra(
                                                        GitHubAuthActivity.EXTRA_MODE,
                                                        GitHubAuthActivity.MODE_GITHUB,
                                                    )
                                            )
                                        }) { Text("Mit GitHub anmelden") }
                                    } else {
                                        Button(
                                            onClick = {
                                                val file = uploadFile ?: return@Button
                                                if (uploadBusy) return@Button
                                                uploadBusy = true
                                                uploadError = null
                                                uploadPrUrl = null
                                                scope.launch {
                                                    val branch = "theme-upload/" +
                                                        System.currentTimeMillis()
                                                    val path = "$uploadFolder/" +
                                                        ThemeComposer.sanitizeFileName(file.first)
                                                            .let {
                                                                if (it.endsWith(".mtz", true)) it
                                                                else "$it.mtz"
                                                            }
                                                    uploadProgress = "Committe ${file.first} …"
                                                    val upload = GitHubApi.uploadFile(
                                                        context = context,
                                                        path = path,
                                                        bytes = file.second,
                                                        branch = branch,
                                                        message = "Add theme ${file.first}",
                                                    )
                                                    upload.fold(
                                                        onSuccess = {
                                                            uploadProgress = "Erstelle Pull Request …"
                                                            GitHubApi.createPullRequest(
                                                                context = context,
                                                                title = uploadTitle.ifBlank {
                                                                    "Theme: ${file.first}"
                                                                },
                                                                head = branch,
                                                                base = "main",
                                                                body = buildString {
                                                                    appendLine("## Theme-Upload")
                                                                    appendLine()
                                                                    appendLine(
                                                                        "- Datei: `$path`"
                                                                    )
                                                                    appendLine(
                                                                        "- SHA-256: `" +
                                                                            ThemeLibrary.sha256(file.second) +
                                                                            "`"
                                                                    )
                                                                    appendLine(
                                                                        "- Größe: ${file.second.size} Bytes"
                                                                    )
                                                                    appendLine()
                                                                    appendLine(
                                                                        "Dieser PR wurde mit MTZ Tool erstellt " +
                                                                            "und muss vor dem Merge geprüft werden."
                                                                    )
                                                                },
                                                                draft = true,
                                                            ).fold(
                                                                onSuccess = { pr ->
                                                                    uploadPrUrl = pr.htmlUrl
                                                                    AppLog.success(
                                                                        "Theme-Upload-PR erstellt: #${pr.number}"
                                                                    )
                                                                },
                                                                onFailure = {
                                                                    uploadError =
                                                                        "PR fehlgeschlagen: ${it.message}"
                                                                },
                                                            )
                                                        },
                                                        onFailure = {
                                                            uploadError =
                                                                "Upload fehlgeschlagen: ${it.message}"
                                                        },
                                                    )
                                                    uploadBusy = false
                                                    uploadProgress = ""
                                                }
                                            },
                                            enabled = !uploadBusy && uploadFile != null,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            if (uploadBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(
                                                if (uploadBusy) uploadProgress
                                                else "Theme-Upload-PR erstellen"
                                            )
                                        }
                                    }
                                    uploadError?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    uploadPrUrl?.let { url ->
                                        Text(
                                            "Draft-PR erstellt",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                context.startActivity(
                                                    android.content.Intent(
                                                        context,
                                                        InAppBrowserActivity::class.java,
                                                    )
                                                        .putExtra(InAppBrowserActivity.EXTRA_URL, url)
                                                        .putExtra(
                                                            InAppBrowserActivity.EXTRA_TITLE,
                                                            "Theme-Upload PR",
                                                        )
                                                )
                                            }) { Text("PR in der App öffnen") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
