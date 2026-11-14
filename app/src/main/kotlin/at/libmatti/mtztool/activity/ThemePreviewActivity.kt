package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PreviewExportPayload(
    val name: String,
    val bytes: ByteArray
)

class ThemePreviewActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThemePreviewScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ThemePreviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var candidates by remember {
        mutableStateOf<List<ThemeSourceReader.Candidate>>(emptyList())
    }
    var candidateIndex by remember { mutableStateOf(0) }
    var pendingExport by remember { mutableStateOf<PreviewExportPayload?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
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

        scope.launch {
            parseThemeUri(context, uri, name, "tool-previews").fold(
                onSuccess = { result ->
                    candidates = result.candidates
                    candidateIndex = 0
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

    fun loadWorkspace() {
        val theme = ThemeSession.current ?: return
        fileName = theme.name
        loading = true
        error = null
        candidates = emptyList()
        candidateIndex = 0
        scope.launch {
            parseThemeBytes(context, theme.bytes, theme.displayTitle, "tool-previews").fold(
                onSuccess = { result ->
                    candidates = result.candidates
                    candidateIndex = 0
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
        title = stringResource(R.string.tool_previews),
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
            ToolMessageCard(stringResource(R.string.tool_previews_desc))
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
                        text = "${candidate.previews.size} ${
                            stringResource(R.string.theme_preview_count)
                        }",
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
                    onSelected = { candidateIndex = it }
                )
            }

            if (candidate.previews.isEmpty()) {
                ToolMessageCard(stringResource(R.string.previews_none))
            } else {
                candidate.previews.forEach { preview ->
                    PreviewCard(
                        preview = preview,
                        onSave = {
                            val name = suggestedPreviewFileName(
                                candidate.title,
                                preview.name,
                                preview.data
                            )
                            pendingExport = PreviewExportPayload(name, preview.data)
                            saveLauncher.launch(name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: ThemeSourceReader.Preview,
    onSave: () -> Unit
) {
    val bitmap = remember(preview) {
        runCatching {
            BitmapFactory.decodeByteArray(preview.data, 0, preview.data.size)
                ?.asImageBitmap()
        }.getOrNull()
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = preview.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = stringResource(R.string.previews_decode_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()

            Text(
                text = preview.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatBytes(preview.data.size.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tool_save))
            }
        }
    }
}
