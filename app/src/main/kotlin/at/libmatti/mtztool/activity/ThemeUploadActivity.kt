package at.libmatti.mtztool.activity

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import at.libmatti.mtztool.ThemeLibrary
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.github.ThemeUpload
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

class ThemeUploadActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ThemeUploadScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeUploadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var library by remember { mutableStateOf<List<ThemeLibrary.Item>>(emptyList()) }
    var existing by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selected by remember { mutableStateOf<ThemeLibrary.Item?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ByteArray?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prUrl by remember { mutableStateOf<String?>(null) }

    val previewPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > 3 * 1024 * 1024) break
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                error = "Preview konnte nicht gelesen werden."
            } else if (bytes.size > 3 * 1024 * 1024) {
                error = "Preview ist größer als 3 MiB."
            } else {
                preview = bytes
                error = null
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            library = ThemeLibrary.list(context)
            existing = ThemeUpload.existingThemeNames(context).getOrDefault(emptySet())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Theme hochladen", fontWeight = FontWeight.Bold)
                        Text(
                            "Schritt ${step + 1} von 3 · ${deviceWorkflowLabel(step)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1f) / 3f },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step == 0) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Text(
                                "Wähle ein Theme aus deiner Bibliothek. Es muss bereits " +
                                    "importiert sein und darf noch nicht im Store existieren.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (library.isEmpty()) {
                        item {
                            Text(
                                "Deine Bibliothek ist leer. Importiere zuerst ein Theme " +
                                    "im Theme-Browser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(library) { item ->
                        val duplicate = item.name.lowercase(Locale.US) in existing
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !duplicate) {
                                    selected = item
                                    title = item.name.substringBeforeLast('.').take(80)
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected?.id == item.id) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${item.bytesLabel} · ${item.sha256.take(16)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (duplicate) {
                                        Text(
                                            "Bereits im Store – nicht erneut hochladbar.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                if (selected?.id == item.id) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                if (step == 1) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(80) },
                            label = { Text("Theme-Titel *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(2000) },
                            label = { Text("Beschreibung (optional)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Preview-Bild (optional)", fontWeight = FontWeight.Bold)
                                Text(
                                    preview?.let { "Ausgewählt: ${it.size} Bytes" }
                                        ?: "Kein Preview ausgewählt.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { previewPicker.launch("image/*") }) {
                                        Icon(Icons.Default.Image, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text("Bild wählen")
                                    }
                                    if (preview != null) {
                                        TextButton(onClick = { preview = null }) {
                                            Text("Entfernen")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (step == 2) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(
                                    selected?.name.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "SHA-256 ${selected?.sha256?.take(24)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "Der PR wird automatisch benannt. Nach dem Approval " +
                                        "erstellt die Action die endgültigen Dateien.",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (!GitHubApi.isSignedIn(context)) {
                        item {
                            Text(
                                "Bitte zuerst mit GitHub anmelden.",
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    android.content.Intent(
                                        context,
                                        GitHubAuthActivity::class.java,
                                    )
                                )
                            }) { Text("Anmelden") }
                        }
                    } else {
                        item {
                            Button(
                                onClick = {
                                    val item = selected ?: return@Button
                                    if (uploading) return@Button
                                    uploading = true
                                    error = null
                                    scope.launch {
                                        val result = ThemeUpload.submitTheme(
                                            context, item, title, description, preview,
                                        )
                                        uploading = false
                                        result.fold(
                                            onSuccess = { prUrl = it },
                                            onFailure = { error = it.message },
                                        )
                                    }
                                },
                                enabled = !uploading && title.length >= 3,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Upload, contentDescription = null)
                                }
                                Spacer(Modifier.size(8.dp))
                                Text(if (uploading) "Lade hoch …" else "Upload")
                            }
                        }
                    }
                    error?.let {
                        item {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    prUrl?.let { url ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("PR erstellt", fontWeight = FontWeight.Bold)
                                    Text(url, style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = {
                                        context.startActivity(
                                            android.content.Intent(
                                                context,
                                                InAppBrowserActivity::class.java,
                                            ).putExtra(InAppBrowserActivity.EXTRA_URL, url)
                                        )
                                    }) { Text("In der App öffnen") }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    OutlinedButton(onClick = { step -= 1 }) { Text("Zurück") }
                }
                Spacer(Modifier.weight(1f))
                if (step < 2) {
                    Button(
                        onClick = {
                            if (step == 0 && selected == null) {
                                error = "Bitte ein Theme auswählen."
                            } else if (step == 1 && title.length < 3) {
                                error = "Bitte einen Titel vergeben."
                            } else {
                                error = null
                                step += 1
                            }
                        },
                    ) { Text("Weiter") }
                }
            }
            LogConsole(height = 150)
        }
    }
}

private fun deviceWorkflowLabel(step: Int) = when (step) {
    0 -> "Theme auswählen"
    1 -> "Titel & Preview"
    else -> "Upload"
}
