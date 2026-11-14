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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ThemePatchActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThemePatchScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class PatchState(
    val analysis: MtzPatcher.Analysis? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val sourceName: String? = null,
    val sourceBytes: ByteArray? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(PatchState()) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var designer by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0") }
    val removed = remember { mutableStateListOf<String>() }
    var wallpaper by remember { mutableStateOf<ByteArray?>(null) }
    var wallpaperExt by remember { mutableStateOf("jpg") }
    var lockWallpaper by remember { mutableStateOf<ByteArray?>(null) }
    var lockWallpaperExt by remember { mutableStateOf("jpg") }
    var message by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf("theme-patched.mtz") }

    fun loadAnalysis(bytes: ByteArray, name: String) {
        state = PatchState(loading = true, sourceName = name, sourceBytes = bytes)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MtzPatcher.analyze(bytes, name) }
            }
            result.fold(
                onSuccess = { analysis ->
                    state = PatchState(
                        analysis = analysis,
                        sourceName = name,
                        sourceBytes = bytes,
                    )
                    title = analysis.metadata.title
                    author = analysis.metadata.author
                    designer = analysis.metadata.designer
                    description = analysis.metadata.description
                    version = analysis.metadata.version
                    removed.clear()
                    exportName = MtzPatcher.suggestedFileName(name, analysis.metadata.title)
                    message = null
                },
                onFailure = {
                    state = PatchState(error = it.message, sourceName = name)
                },
            )
        }
    }

    fun loadSession() {
        val theme = ThemeSession.current
        if (theme == null) {
            message = "Im Workspace ist kein Theme geladen."
            return
        }
        loadAnalysis(theme.bytes, theme.name)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val name = ThemeApplier.getFileName(context, uri) ?: "theme.mtz"
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: error("Datei konnte nicht gelesen werden.")
                    bytes to name
                }
            }
            loaded.fold(
                onSuccess = { (bytes, name) ->
                    loadAnalysis(bytes, name)
                },
                onFailure = {
                    state = PatchState(error = it.message)
                },
            )
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    wallpaper = bytes
                    wallpaperExt = MtzPatcher.imageExtension(bytes)
                }
            }
        }
    }

    val lockWallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    lockWallpaper = bytes
                    lockWallpaperExt = MtzPatcher.imageExtension(bytes)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sourceBytes = state.sourceBytes
        val analysis = state.analysis
        if (sourceBytes == null || analysis == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = "Baue gepatchte MTZ …"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val patch = MtzPatcher.Patch(
                        metadata = MtzPatcher.Metadata(
                            title = title,
                            author = author,
                            designer = designer,
                            description = description,
                            version = version,
                            uiVersion = analysis.metadata.uiVersion,
                            adapterVersion = analysis.metadata.adapterVersion,
                        ),
                        removeEntries = removed.toSet(),
                        wallpaper = wallpaper,
                        wallpaperExtension = wallpaperExt,
                        lockWallpaper = lockWallpaper,
                        lockWallpaperExtension = lockWallpaperExt,
                    )
                    val built = MtzPatcher.patch(sourceBytes, patch)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(built.bytes)
                    } ?: error("Zieldatei konnte nicht geöffnet werden.")
                    built to patch
                }
            }
            result.fold(
                onSuccess = { (built, _) ->
                    message = buildString {
                        append("MTZ gespeichert (${formatBytes(built.bytes.size.toLong())}).")
                        if (built.removedEntries > 0) append(" ${built.removedEntries} Einträge entfernt.")
                        if (built.wallpaperReplaced) append(" Wallpaper ersetzt.")
                        if (built.lockWallpaperReplaced) append(" Lockscreen-Wallpaper ersetzt.")
                    }
                    AppLog.success(message!!)
                    ThemeSession.openBytes(
                        context,
                        exportName,
                        built.bytes,
                        sourceUri = state.sourceName,
                    )
                },
                onFailure = {
                    message = "Patchen fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}"
                    AppLog.error(message!!)
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.patcher_title), fontWeight = FontWeight.Bold)
                        Text(
                            text = "MTZ bearbeiten und neu packen",
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.patcher_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.patcher_open))
                        }
                        OutlinedButton(
                            onClick = ::loadSession,
                            enabled = ThemeSession.current != null,
                        ) {
                            Text(stringResource(R.string.common_use_workspace))
                        }
                    }
                    state.sourceName?.let {
                        Text(
                            "Quelle: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.patcher_analyzing))
                }
            }

            state.error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            val analysis = state.analysis
            if (analysis != null) {
                Text(
                    stringResource(R.string.patcher_metadata),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.patcher_field_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.patcher_author)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = version,
                        onValueChange = { version = it },
                        label = { Text(stringResource(R.string.patcher_version)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = designer,
                    onValueChange = { designer = it },
                    label = { Text(stringResource(R.string.patcher_designer)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.patcher_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Text(
                    stringResource(R.string.patcher_content),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.patcher_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (analysis.components.isEmpty()) {
                    Text(stringResource(R.string.patcher_no_components))
                } else {
                    ComponentToggleList(
                        components = analysis.components,
                        removed = removed,
                        onToggle = { entry ->
                            if (entry in removed) removed.remove(entry) else removed.add(entry)
                        },
                    )
                }

                Text(
                    stringResource(R.string.patcher_wallpaper),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                WallpaperPickerRow(
                    label = stringResource(R.string.patcher_system_wallpaper),
                    active = wallpaper != null,
                    onPick = { wallpaperPicker.launch(arrayOf("image/*")) },
                    onClear = { wallpaper = null },
                )
                WallpaperPickerRow(
                    label = stringResource(R.string.patcher_lock_wallpaper),
                    active = lockWallpaper != null,
                    onPick = { lockWallpaperPicker.launch(arrayOf("image/*")) },
                    onClear = { lockWallpaper = null },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = exportName,
                        onValueChange = { exportName = it },
                        label = { Text(stringResource(R.string.patcher_export_name)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = { exportLauncher.launch(exportName) },
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.patcher_export))
                    }
                }

                analysis.entries.take(12).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            entry.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                        Text(
                            if (entry.directory) "dir" else formatBytes(entry.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                if (analysis.entries.size > 12) {
                    Text(
                        "… ${analysis.entries.size - 12} weitere Einträge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            message?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComponentToggleList(
    components: List<MtzPatcher.Component>,
    removed: List<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        components.forEach { component ->
            val isRemoved = component.entry in removed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(component.entry) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Checkbox(
                    checked = !isRemoved,
                    onCheckedChange = { onToggle(component.entry) },
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ThemeComponents.displayName(component.code),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isRemoved) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        component.entry,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (isRemoved) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperPickerRow(
    label: String,
    active: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Image,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (active) "$label · ersetzt" else label,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
        OutlinedButton(onClick = onPick) {
            Text(if (active) "Ändern" else "Wählen")
        }
    }
}
