package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExportPayload(
    val name: String,
    val mime: String,
    val bytes: ByteArray
)

class ThemeExtractorActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThemeExtractorScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ThemeExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidates by remember {
        mutableStateOf<List<ThemeSourceReader.Candidate>>(emptyList())
    }
    var candidateIndex by remember { mutableStateOf(0) }
    var componentIndex by remember { mutableStateOf(0) }
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(payload.bytes)
                        } ?: error("Could not open output file")
                    }
                }
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            context,
                            R.string.tool_export_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.tool_export_failed,
                                it.message ?: it.javaClass.simpleName
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
        pendingExport = null
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = pickedFileName(context, uri)
        fileName = name
        loading = true
        error = null
        candidates = emptyList()
        candidateIndex = 0
        componentIndex = 0

        scope.launch {
            parseThemeUri(context, uri, name, "tool-extractor").fold(
                onSuccess = { result ->
                    candidates = result.candidates
                    candidateIndex = 0
                    componentIndex = 0
                    loading = false
                },
                onFailure = {
                    error = it.message ?: it.javaClass.simpleName
                    loading = false
                }
            )
        }
    }

    val candidate = candidates.getOrNull(candidateIndex)
    val component = candidate?.components?.getOrNull(componentIndex)

    fun componentLabel(value: ThemeSourceReader.Component): String =
        "${ThemeComponents.displayName(value.code)} · ${formatBytes(value.data.size.toLong())}"

    fun loadWorkspace() {
        val theme = ThemeSession.current ?: return
        fileName = theme.name
        loading = true
        error = null
        candidates = emptyList()
        candidateIndex = 0
        componentIndex = 0
        scope.launch {
            parseThemeBytes(context, theme.bytes, theme.displayTitle, "tool-extractor").fold(
                onSuccess = { result ->
                    candidates = result.candidates
                    candidateIndex = 0
                    componentIndex = 0
                    loading = false
                },
                onFailure = {
                    error = it.message ?: it.javaClass.simpleName
                    loading = false
                }
            )
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_extractor),
        onBack = onBack
    ) {
        ToolWorkspaceCard(onLoad = ::loadWorkspace, enabled = !loading)
        ToolFilePickerCard(
            title = stringResource(R.string.tool_theme_file),
            fileName = fileName,
            buttonText = stringResource(
                if (fileName == null) R.string.tool_pick_theme else R.string.tool_pick_another_theme
            ),
            onPick = { picker.launch(arrayOf("*/*")) },
            enabled = !loading
        )

        if (loading) {
            ToolLoadingCard(stringResource(R.string.tool_loading))
        }

        error?.let { ToolMessageCard(it, isError = true) }

        if (!loading && error == null && fileName == null) {
            ToolMessageCard(stringResource(R.string.tool_extractor_desc))
        }

        if (candidate != null) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = candidate.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${candidate.author.ifBlank { "–" }} · v${candidate.version} · ${
                            candidate.components.size
                        } ${stringResource(R.string.theme_components).lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (candidates.size > 1) {
                ToolDropdown(
                    label = stringResource(R.string.extractor_choose_theme),
                    selectedText = candidate.title,
                    options = candidates.indices.toList(),
                    optionText = { candidates[it].title },
                    onSelected = {
                        candidateIndex = it
                        componentIndex = 0
                    }
                )
            }

            if (candidate.components.isEmpty()) {
                ToolMessageCard(stringResource(R.string.extractor_no_components))
            } else {
                ToolDropdown(
                    label = stringResource(R.string.extractor_component),
                    selectedText = component?.let { componentLabel(it) }
                        ?: stringResource(R.string.tool_no_file_selected),
                    options = candidate.components,
                    optionText = { componentLabel(it) },
                    onSelected = {
                        componentIndex = candidate.components.indexOf(it)
                    }
                )
            }

            if (component != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ThemeComponents.displayName(component.code),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        ToolInfoRow(stringResource(R.string.extractor_code), component.code)
                        ToolInfoRow(
                            stringResource(R.string.theme_size),
                            formatBytes(component.data.size.toLong())
                        )
                        ToolInfoRow(
                            stringResource(R.string.theme_rights),
                            stringResource(
                                if (ThemeComponents.isExempt(component.code)) {
                                    R.string.component_exempt
                                } else {
                                    R.string.component_protected
                                }
                            )
                        )
                        Text(
                            text = stringResource(R.string.component_sha1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = component.sha1,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        OutlinedButton(
                            onClick = {
                                val payload = ExportPayload(
                                    name = suggestedComponentFileName(
                                        candidate.title,
                                        component
                                    ),
                                    mime = componentMime(component),
                                    bytes = component.data
                                )
                                pendingExport = payload
                                saveLauncher.launch(payload.name)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.tool_export))
                        }
                    }
                }
            }
        }
    }
}
