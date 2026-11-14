package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
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
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.AdaptiveGrid
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeStudioActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThemeStudioScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class PendingSave(val fileName: String, val bytes: ByteArray)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableIntStateOf(0) }
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }

    // Pull state
    var pulling by remember { mutableStateOf(false) }
    var pulled by remember { mutableStateOf<ThemePuller.PulledTheme?>(null) }
    var pullLog by remember { mutableStateOf("") }

    // Compose state
    var title by remember { mutableStateOf("Mein eigenes Theme") }
    var author by remember { mutableStateOf(AppSettings.getGithubLogin(context)) }
    var designer by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    val sources = remember { mutableStateListOf<ThemeComposer.Source>() }
    val builtEntries = remember { mutableStateListOf<Pair<String, Int>>() }
    var building by remember { mutableStateOf(false) }
    var buildLog by remember { mutableStateOf("") }
    var builtBytes by remember { mutableStateOf<ByteArray?>(null) }
    var builtName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ThemeComposer.Source?>(null) }
    var editValue by remember { mutableStateOf("") }

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
                    context.contentResolver.openOutputStream(uri)?.use { it.write(pending.bytes) }
                        ?: error("Zieldatei konnte nicht geöffnet werden")
                }
            }
            result.fold(
                onSuccess = {
                    AppLog.success("Theme gespeichert: ${pending.fileName}")
                    message("Gespeichert: ${pending.fileName}")
                },
                onFailure = { message("Speichern fehlgeschlagen: ${it.message}") },
            )
            pendingSave = null
        }
    }

    fun save(fileName: String, bytes: ByteArray) {
        pendingSave = PendingSave(fileName, bytes)
        saver.launch(fileName)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                val source = ThemeComposer.describe(context, uri)
                if (!isImportableAsset(source.displayName)) {
                    AppLog.warn(
                        "${source.displayName} übersprungen: importierbar sind nur " +
                            "Schriftarten, Wallpaper und Lockscreen-Bilder."
                    )
                    return@forEach
                }
                sources.add(source)
            }.onFailure {
                AppLog.warn("Datei konnte nicht gelesen werden: ${it.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Theme Studio", fontWeight = FontWeight.Bold)
                        Text(
                            "Vom Gerät pullen · eigene Themes bauen",
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
                    text = { Text("Vom Gerät") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Eigenes Theme") },
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (tab == 0) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Aktuelles Theme auslesen",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        "MTZ Tool liest das gerade angewendete Theme aus " +
                                            "dem Systemverzeichnis und packt es als portable .mtz. " +
                                            "Dafür werden Root oder Shizuku benötigt.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        "Zugriff: " + if (ThemePuller.isAvailable()) {
                                            "bereit"
                                        } else {
                                            "nicht bereit – bitte in den Einstellungen einrichten"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (pulling) return@Button
                                        pulling = true
                                        pullLog = ""
                                        pulled = null
                                        scope.launch {
                                            val result = ThemePuller.pullAsync { line ->
                                                pullLog = (pullLog + "\n" + line).trim()
                                            }
                                            pulling = false
                                            result.fold(
                                                onSuccess = { theme ->
                                                    pulled = theme
                                                    pullLog = (
                                                        pullLog + "\nFertig: ${theme.fileName} " +
                                                            "(${theme.entryCount} Dateien)"
                                                        ).trim()
                                                    AppLog.success(
                                                        "Gerätetheme ausgelesen (${theme.entryCount} Dateien)."
                                                    )
                                                },
                                                onFailure = {
                                                    pullLog = (pullLog + "\nFehler: ${it.message}").trim()
                                                    AppLog.error(
                                                        "Theme-Pull fehlgeschlagen: ${it.message}"
                                                    )
                                                    message(it.message ?: "Pull fehlgeschlagen")
                                                },
                                            )
                                        }
                                    },
                                    enabled = !pulling,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (pulling) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (pulling) "Lese Theme …" else "Theme pullen")
                                }
                                OutlinedButton(
                                    onClick = { pullLog = "" },
                                    enabled = !pulling && pullLog.isNotEmpty(),
                                ) {
                                    Text("Log leeren")
                                }
                            }
                        }
                        if (pullLog.isNotBlank()) {
                            item {
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    Text(
                                        text = pullLog,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        pulled?.let { theme ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            theme.fileName,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "${theme.entryCount} Dateien · ${theme.size} Bytes" +
                                                if (theme.skippedFiles > 0) {
                                                    " · ${theme.skippedFiles} übersprungen"
                                                } else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { save(theme.fileName, theme.bytes) },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Speichern")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        ThemeSession.openBytes(
                                                            context,
                                                            theme.fileName,
                                                            theme.bytes,
                                                        ).onSuccess {
                                                            message("Im Workspace geladen")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("In Workspace")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Card(shape = RoundedCornerShape(24.dp)) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "Theme-Metadaten",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    OutlinedTextField(
                                        value = title,
                                        onValueChange = { title = it },
                                        label = { Text("Titel *") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = author,
                                        onValueChange = { author = it },
                                        label = { Text("Autor") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = designer,
                                        onValueChange = { designer = it },
                                        label = { Text("Designer") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = version,
                                        onValueChange = { version = it },
                                        label = { Text("Version") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { description = it },
                                        label = { Text("Beschreibung") },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Dateien",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                "Nur Schriftarten, Wallpaper und Lockscreen-Bilder " +
                                                    "importieren – der Zielpfad im MTZ ist editierbar.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { picker.launch(arrayOf("*/*")) },
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Hinzufügen")
                                        }
                                    }

                                    if (sources.isEmpty()) {
                                        Text(
                                            "Noch keine Dateien ausgewählt.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        sources.forEachIndexed { index, source ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        source.displayName,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                    )
                                                    Text(
                                                        source.targetPath,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        editing = source
                                                        editValue = source.targetPath
                                                    },
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Pfad bearbeiten")
                                                }
                                                IconButton(
                                                    onClick = { sources.removeAt(index) },
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (building) return@Button
                                        building = true
                                        builtBytes = null
                                        builtEntries.clear()
                                        buildLog = "Starte Build …"
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    ThemeComposer.build(
                                                        context = context,
                                                        metadata = ThemeComposer.Metadata(
                                                            title = title,
                                                            author = author,
                                                            designer = designer,
                                                            description = description,
                                                            version = version,
                                                        ),
                                                        sources = sources.toList(),
                                                        onProgress = { line ->
                                                            buildLog = (buildLog + "\n" + line).trim()
                                                        },
                                                    )
                                                }
                                            }
                                            building = false
                                            result.fold(
                                                onSuccess = { bytes ->
                                                    builtBytes = bytes
                                                    builtName = ThemeComposer.sanitizeFileName(title)
                                                        .ifBlank { "theme" } + ".mtz"
                                                    builtEntries.addAll(
                                                        sources.map {
                                                            it.targetPath to 0
                                                        }
                                                    )
                                                    buildLog = (
                                                        buildLog +
                                                            "\nFertig: ${bytes.size} Bytes"
                                                        ).trim()
                                                    AppLog.success(
                                                        "Theme gebaut (${bytes.size} Bytes)."
                                                    )
                                                },
                                                onFailure = {
                                                    buildLog = (buildLog + "\nFehler: ${it.message}").trim()
                                                    AppLog.error("Theme-Build fehlgeschlagen: ${it.message}")
                                                    message(it.message ?: "Build fehlgeschlagen")
                                                },
                                            )
                                        }
                                    },
                                    enabled = !building && sources.isNotEmpty() && title.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (building) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (building) "Baue Theme …" else "Theme bauen")
                                }
                                OutlinedButton(
                                    onClick = { picker.launch(arrayOf("*/*")) },
                                    enabled = !building,
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Dateien")
                                }
                            }
                        }
                        if (buildLog.isNotBlank()) {
                            item {
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    Text(
                                        text = buildLog,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        builtBytes?.let { bytes ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            builtName,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(
                                            "${bytes.size} Bytes · ${sources.size} Dateien",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { save(builtName, bytes) },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Speichern")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        ThemeSession.openBytes(
                                                            context,
                                                            builtName,
                                                            bytes,
                                                        ).onSuccess {
                                                            message("Im Workspace geladen")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text("In Workspace")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    editing?.let { source ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Zielpfad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pfad im MTZ, z. B. fonts/MeineSchrift.ttf oder " +
                            "wallpaper/default_wallpaper.jpg.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text("Pfad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val index = sources.indexOf(source)
                    if (index >= 0) {
                        sources[index] = source.copy(targetPath = editValue.trim())
                    }
                    editing = null
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Abbrechen") }
            },
        )
    }
}

private fun isImportableAsset(displayName: String): Boolean {
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return extension in setOf("ttf", "otf", "ttc", "jpg", "jpeg", "png", "webp")
}

@Composable
private fun UnusedStudioIcon() {
    // Keeps the icon import set intentional for future studio actions.
    Icon(Icons.Default.Download, contentDescription = null)
    Icon(Icons.Default.Palette, contentDescription = null)
    Icon(Icons.Default.Edit, contentDescription = null)
}
