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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MraCheckResult(
    val fileName: String,
    val size: Long,
    val rights: MraVerifier.Rights?,
    val error: String?
)

class MraCheckerActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MraCheckerScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun MraCheckerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MraCheckResult>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        loading = true
        results = emptyList()
        fileNames = uris.map { pickedFileName(context, it) }

        scope.launch {
            val checked = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = pickedFileName(context, uri)
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes()
                        } ?: error("Could not read file")
                        MraCheckResult(
                            fileName = name,
                            size = bytes.size.toLong(),
                            rights = MraVerifier.verify(bytes),
                            error = null
                        )
                    }.getOrElse {
                        MraCheckResult(
                            fileName = name,
                            size = 0L,
                            rights = null,
                            error = it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
            results = checked
            loading = false
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_mra_checker),
        onBack = onBack
    ) {
        ToolFilePickerCard(
            title = stringResource(R.string.tool_mra_files),
            fileName = if (fileNames.isEmpty()) null else fileNames.joinToString(", "),
            buttonText = stringResource(R.string.tool_pick_mra),
            onPick = { picker.launch(arrayOf("*/*")) },
            enabled = !loading
        )

        if (loading) {
            ToolLoadingCard(stringResource(R.string.tool_loading))
        }

        if (!loading && results.isEmpty() && fileNames.isEmpty()) {
            ToolMessageCard(stringResource(R.string.mra_none_selected))
        }

        if (results.isNotEmpty()) {
            val valid = results.count { it.rights?.signatureValid == true }
            ToolMessageCard(
                stringResource(R.string.mra_summary, valid, results.size)
            )
            results.forEach { MraResultCard(it) }
        }

        ToolMessageCard(stringResource(R.string.mra_hint))
    }
}

@Composable
private fun MraResultCard(result: MraCheckResult) {
    val valid = result.rights?.signatureValid == true
    val color = if (valid) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (valid) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = color
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            if (valid) R.string.mra_signature_valid else R.string.mra_signature_invalid
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }

            if (result.error == null && result.rights != null) {
                HorizontalDivider()
                ToolInfoRow(
                    stringResource(R.string.theme_size),
                    formatBytes(result.size)
                )
                ToolInfoRow(
                    stringResource(R.string.mra_asset_count),
                    result.rights.assets.size.toString()
                )
                result.rights.themeHash?.let { hash ->
                    Text(
                        text = stringResource(R.string.mra_theme_hash),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hash,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                result.rights.error?.let { message ->
                    Text(
                        text = "${stringResource(R.string.mra_error)}: $message",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = result.error ?: stringResource(R.string.mra_read_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
