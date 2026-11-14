package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

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
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeRightsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RightsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RightsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val loaded = remember { mutableStateListOf<ThemeRightsTool.LoadedMra>() }
    var working by remember { mutableStateOf(false) }
    var coverage by remember { mutableStateOf<ThemeRightsTool.ThemeCoverage?>(null) }
    var lastAction by remember { mutableStateOf("") }
    var pendingSave by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    val mraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        scope.launch {
            val inspected = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "rechte.mra"
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() ?: ByteArray(0)
                    ThemeRightsTool.inspect(name, bytes)
                }
            }
            loaded.clear()
            loaded.addAll(inspected)
            coverage = ThemeSession.current?.let {
                ThemeRightsTool.coverageOf(it.bytes)
            } ?: coverage
        }
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
                onSuccess = { snackbar.showSnackbar("Gespeichert: ${pending.first}") },
                onFailure = { snackbar.showSnackbar("Fehler: ${it.message}") },
            )
            pendingSave = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rechte-Tool", fontWeight = FontWeight.Bold)
                        Text(
                            "MRA prüfen und Theme hinzufügen",
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
        AdaptiveContent(profile = profile) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = profile.horizontalPaddingDp.dp,
                    end = profile.horizontalPaddingDp.dp,
                    top = 14.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "Nur echte Xiaomi-Rechte werden akzeptiert",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Dieses Tool prüft .mra-Dateien offline gegen die " +
                                    "Xiaomi-Signatur und hängt gültige Dateien an das Theme. " +
                                    "Eine eigene Signatur zu erzeugen ist technisch nicht möglich " +
                                    "und wird vom Theme Manager abgelehnt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Aktuelles Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                ThemeSession.current?.let {
                                    "${it.displayTitle} · ${it.size} Bytes"
                                } ?: "Kein Theme im Workspace. Öffne zuerst ein Theme.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            coverage?.let { value ->
                                Text(
                                    value.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (value.missing.isNotEmpty()) {
                                    Text(
                                        "Fehlt: ${value.missing.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { mraPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(".mra auswählen")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val theme = ThemeSession.current ?: return@OutlinedButton
                                        coverage = ThemeRightsTool.coverageOf(theme.bytes)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Prüfen")
                                }
                            }
                        }
                    }
                }

                if (loaded.isNotEmpty()) {
                    item {
                        Text(
                            "Ausgewählte Rechte-Dateien",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(loaded.size) { index ->
                        val mra = loaded[index]
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (mra.valid) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                            ),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    mra.fileName,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    if (mra.valid) {
                                        "Signatur gültig · ${mra.assetCount} Assets · Theme-Hash " +
                                            (mra.themeHash ?: "–")
                                    } else {
                                        "Ungültig: ${mra.error ?: "unbekannter Fehler"}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                val theme = ThemeSession.current ?: return@Button
                                if (working) return@Button
                                working = true
                                lastAction = ""
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            ThemeRightsTool.attach(theme.bytes, loaded.toList()) { line ->
                                                lastAction = line
                                            }
                                        }
                                    }
                                    working = false
                                    result.fold(
                                        onSuccess = { attachResult ->
                                            coverage = attachResult.coverage
                                            lastAction = "Übernommen: ${attachResult.attached.size} · " +
                                                "Abgelehnt: ${attachResult.rejected.size}"
                                            ThemeSession.openBytes(
                                                context,
                                                theme.name,
                                                attachResult.themeBytes,
                                            )
                                            AppLog.success(
                                                "Rechte hinzugefügt: ${attachResult.attached.size}"
                                            )
                                        },
                                        onFailure = {
                                            lastAction = "Fehler: ${it.message}"
                                            AppLog.error("Rechte-Tool: ${it.message}")
                                        },
                                    )
                                }
                            },
                            enabled = !working && loaded.any { it.valid },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (working) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (working) "Übernehme Rechte …"
                                else "Gültige Rechte zum Theme hinzufügen"
                            )
                        }
                    }
                    if (lastAction.isNotBlank()) {
                        item {
                            Text(
                                lastAction,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    ThemeSession.current?.let { theme ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pendingSave = theme.name to theme.bytes
                                        saver.launch(theme.name)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Theme speichern")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Tipp: Ein Theme gilt nur dann als vollständig, wenn alle " +
                            "geschützten Bestandteile in mindestens einer gültigen .mra " +
                            "enthalten sind. Das Rechte-Tool zeigt dir genau, was fehlt.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
