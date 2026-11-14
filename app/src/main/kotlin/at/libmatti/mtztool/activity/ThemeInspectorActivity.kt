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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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

private data class InspectedTheme(
    val candidate: ThemeSourceReader.Candidate,
    val assessment: ThemeRights.Assessment
)

class ThemeInspectorActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThemeInspectorScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ThemeInspectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var themes by remember { mutableStateOf<List<InspectedTheme>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = pickedFileName(context, uri)
        fileName = name
        loading = true
        error = null
        themes = emptyList()

        scope.launch {
            val parsed = parseThemeUri(context, uri, name, "tool-inspector")
            parsed.fold(
                onSuccess = { result ->
                    val assessed = withContext(Dispatchers.IO) {
                        result.candidates.map { candidate ->
                            InspectedTheme(candidate, ThemeRights.assess(candidate))
                        }
                    }
                    themes = assessed
                    loading = false
                },
                onFailure = {
                    error = it.message ?: it.javaClass.simpleName
                    loading = false
                }
            )
        }
    }

    fun loadWorkspace() {
        val theme = ThemeSession.current ?: return
        fileName = theme.name
        loading = true
        error = null
        themes = emptyList()
        scope.launch {
            parseThemeBytes(context, theme.bytes, theme.displayTitle, "tool-inspector").fold(
                onSuccess = { result ->
                    themes = withContext(Dispatchers.IO) {
                        result.candidates.map {
                            InspectedTheme(it, ThemeRights.assess(it))
                        }
                    }
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
        title = stringResource(R.string.tool_inspector),
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

        error?.let {
            ToolMessageCard(it, isError = true)
        }

        if (!loading && error == null && fileName == null) {
            ToolMessageCard(stringResource(R.string.tool_inspector_desc))
        }

        themes.forEach { inspected ->
            InspectorThemeCard(inspected)
        }
    }
}

@Composable
private fun InspectorThemeCard(inspected: InspectedTheme) {
    val candidate = inspected.candidate
    val assessment = inspected.assessment
    val missingHashes = assessment.missing.map { it.sha1 }.toSet()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            if (candidate.kind == ThemeSourceReader.Kind.MTZ) {
                                R.string.theme_kind_mtz
                            } else {
                                R.string.theme_kind_backup
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatBytes(candidate.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider()

            ToolInfoRow(
                stringResource(R.string.theme_author),
                candidate.author.ifBlank { "–" }
            )
            ToolInfoRow(
                stringResource(R.string.theme_version),
                candidate.version
            )
            ToolInfoRow(
                stringResource(R.string.theme_platform),
                candidate.platform.toString()
            )
            ToolInfoRow(
                stringResource(R.string.theme_component_count),
                candidate.components.size.toString()
            )
            ToolInfoRow(
                stringResource(R.string.theme_preview_count),
                candidate.previews.size.toString()
            )
            ToolInfoRow(
                stringResource(R.string.theme_valid_mras),
                "${assessment.validMras.size} / ${candidate.mras.size}"
            )

            if (candidate.description.isNotBlank()) {
                Text(
                    text = candidate.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.theme_rights),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    if (assessment.fullTheme) R.string.theme_full else R.string.theme_partial
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (assessment.fullTheme) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
            if (!assessment.fullTheme && assessment.reason.isNotBlank()) {
                Text(
                    text = assessment.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.theme_hash),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = candidate.themeHash,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            if (candidate.components.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.theme_components),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                candidate.components.forEach { component ->
                    val statusRes = when {
                        ThemeComponents.isExempt(component.code) -> R.string.component_exempt
                        component.sha1 in missingHashes -> R.string.component_uncovered
                        else -> R.string.component_covered
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ThemeComponents.displayName(component.code),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${component.code} · ${
                                    formatBytes(component.data.size.toLong())
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = stringResource(statusRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (statusRes) {
                                R.string.component_covered -> MaterialTheme.colorScheme.primary
                                R.string.component_uncovered -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
            }
        }
    }
}
