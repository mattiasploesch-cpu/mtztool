package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class IconPackActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    IconPackScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceName by remember { mutableStateOf(ThemeSession.current?.name) }
    var sourceBytes by remember { mutableStateOf(ThemeSession.current?.bytes) }
    var building by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    var built by remember { mutableStateOf<IconPackBuilder.BuildResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
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
                    sourceBytes = bytes
                    sourceName = name
                    built = null
                    installed = false
                },
                onFailure = { message = it.message },
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val file = built?.file
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("Zieldatei konnte nicht geöffnet werden.")
                }
            }
            message = result.fold(
                onSuccess = { "Icon-Pack APK gespeichert." },
                onFailure = { "Speichern fehlgeschlagen: ${it.message}" },
            )
        }
    }

    fun build() {
        val bytes = sourceBytes ?: return
        if (building) return
        building = true
        message = null
        installed = false
        scope.launch {
            AppLog.info("Starte Icon-Pack-Erstellung …")
            val result = withContext(Dispatchers.IO) {
                IconPackBuilder.buildFromBytes(
                    context,
                    bytes,
                    sourceName ?: "theme.mtz",
                ) { line -> AppLog.add(line) }
            }
            building = false
            result.fold(
                onSuccess = {
                    built = it
                    message = "${it.iconCount} Symbole gepackt (${it.skippedCount} übersprungen)."
                    AppLog.success(message!!)
                },
                onFailure = {
                    message = "Icon-Pack fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}"
                    AppLog.error(message!!)
                },
            )
        }
    }

    fun install() {
        val file = built?.file ?: return
        val provider = AppSettings.getPrivilegeProvider(context)
        if (provider != PrivilegeProvider.ROOT &&
            ShizukuHelper.getStatus() != ShizukuHelper.Status.READY
        ) {
            installMessage = "Für die Installation wird Root oder Shizuku benötigt."
            return
        }
        installMessage = "Installiere Icon-Pack …"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShizukuHelper.installGeneratedApk(file, IconPackBuilder.ICON_PACK_PACKAGE)
            }
            result.fold(
                onSuccess = {
                    installed = true
                    installMessage = it
                    AppLog.success(it)
                },
                onFailure = {
                    installMessage = "Installation fehlgeschlagen: ${it.message}"
                    AppLog.error(installMessage!!)
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.icon_pack_title), fontWeight = FontWeight.Bold)
                        Text(
                            "Launcher-Icon-Pack aus dem Theme erzeugen",
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
                        text = stringResource(R.string.icon_pack_desc),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.patcher_open))
                        }
                        OutlinedButton(
                            onClick = {
                                sourceBytes = ThemeSession.current?.bytes
                                sourceName = ThemeSession.current?.name
                                built = null
                            },
                            enabled = ThemeSession.current != null,
                        ) {
                            Text(stringResource(R.string.common_use_workspace))
                        }
                    }
                    sourceName?.let {
                        Text(
                            text = "Quelle: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Button(
                onClick = ::build,
                enabled = sourceBytes != null && !building,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (building) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Apps, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (building) "Wird gebaut …" else stringResource(R.string.icon_pack_build))
            }

            built?.let { result ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Icon-Pack bereit", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${result.iconCount} Symbole · ${formatBytes(result.file.length())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            result.file.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::install, enabled = !installed) {
                                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (installed) "Installiert" else stringResource(R.string.icon_pack_install))
                            }
                            OutlinedButton(onClick = { exportLauncher.launch(result.file.name) }) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.tool_save))
                            }
                        }
                        installMessage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            message?.let {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                "Protokoll",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            LogConsole(height = 200)
            Spacer(Modifier.height(16.dp))
        }
    }
}
