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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.launch

class ThemeCompareActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThemeCompareScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ThemeCompareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var firstFile by remember { mutableStateOf<String?>(null) }
    var secondFile by remember { mutableStateOf<String?>(null) }
    var firstLoading by remember { mutableStateOf(false) }
    var secondLoading by remember { mutableStateOf(false) }
    var firstError by remember { mutableStateOf<String?>(null) }
    var secondError by remember { mutableStateOf<String?>(null) }
    var firstTheme by remember { mutableStateOf<ThemeSourceReader.Candidate?>(null) }
    var secondTheme by remember { mutableStateOf<ThemeSourceReader.Candidate?>(null) }
    var firstCount by remember { mutableStateOf(0) }
    var secondCount by remember { mutableStateOf(0) }

    fun loadTheme(uri: Uri?, isFirst: Boolean) {
        if (uri == null) return
        val name = pickedFileName(context, uri)

        if (isFirst) {
            firstFile = name
            firstLoading = true
            firstError = null
            firstTheme = null
            firstCount = 0
        } else {
            secondFile = name
            secondLoading = true
            secondError = null
            secondTheme = null
            secondCount = 0
        }

        scope.launch {
            val parsed = parseThemeUri(
                context,
                uri,
                name,
                if (isFirst) "tool-compare-a" else "tool-compare-b"
            )
            parsed.fold(
                onSuccess = { result ->
                    val candidate = result.candidates.firstOrNull()
                    if (isFirst) {
                        firstTheme = candidate
                        firstCount = result.candidates.size
                        firstLoading = false
                    } else {
                        secondTheme = candidate
                        secondCount = result.candidates.size
                        secondLoading = false
                    }
                },
                onFailure = {
                    if (isFirst) {
                        firstError = it.message ?: it.javaClass.simpleName
                        firstLoading = false
                    } else {
                        secondError = it.message ?: it.javaClass.simpleName
                        secondLoading = false
                    }
                }
            )
        }
    }

    val firstPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { loadTheme(it, true) }

    val secondPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { loadTheme(it, false) }

    fun loadWorkspaceAsFirst() {
        val theme = ThemeSession.current ?: return
        firstFile = theme.name
        firstLoading = true
        firstError = null
        firstTheme = null
        firstCount = 0
        scope.launch {
            parseThemeBytes(context, theme.bytes, theme.displayTitle, "tool-compare-a").fold(
                onSuccess = { result ->
                    firstTheme = result.candidates.firstOrNull()
                    firstCount = result.candidates.size
                    firstLoading = false
                },
                onFailure = {
                    firstError = it.message ?: it.javaClass.simpleName
                    firstLoading = false
                }
            )
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_compare),
        onBack = onBack
    ) {
        ToolWorkspaceCard(
            onLoad = ::loadWorkspaceAsFirst,
            enabled = !firstLoading && !secondLoading,
        )
        ToolFilePickerCard(
            title = stringResource(R.string.tool_first_theme),
            fileName = firstFile,
            buttonText = stringResource(R.string.tool_pick_first_theme),
            onPick = { firstPicker.launch(arrayOf("*/*")) },
            enabled = !firstLoading && !secondLoading
        )
        ToolFilePickerCard(
            title = stringResource(R.string.tool_second_theme),
            fileName = secondFile,
            buttonText = stringResource(R.string.tool_pick_second_theme),
            onPick = { secondPicker.launch(arrayOf("*/*")) },
            enabled = !firstLoading && !secondLoading
        )

        if (firstLoading || secondLoading) {
            ToolLoadingCard(stringResource(R.string.tool_loading))
        }

        firstError?.let { ToolMessageCard(it, isError = true) }
        secondError?.let { ToolMessageCard(it, isError = true) }

        val first = firstTheme
        val second = secondTheme

        if (first != null && second != null) {
            ThemeSummaryCard(stringResource(R.string.tool_first_theme), first)
            ThemeSummaryCard(stringResource(R.string.tool_second_theme), second)
            if (firstCount > 1 || secondCount > 1) {
                ToolMessageCard(stringResource(R.string.compare_multiple_candidates))
            }
            ComparisonCard(first, second)
        } else if (!firstLoading && !secondLoading &&
            firstError == null && secondError == null &&
            (firstFile != null || secondFile != null)
        ) {
            ToolMessageCard(stringResource(R.string.compare_waiting))
        } else if (firstFile == null && secondFile == null) {
            ToolMessageCard(stringResource(R.string.tool_compare_desc))
        }
    }
}

@Composable
private fun ThemeSummaryCard(label: String, candidate: ThemeSourceReader.Candidate) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ToolInfoRow(stringResource(R.string.theme_author), candidate.author.ifBlank { "–" })
            ToolInfoRow(stringResource(R.string.theme_version), candidate.version)
            ToolInfoRow(stringResource(R.string.theme_platform), candidate.platform.toString())
            ToolInfoRow(
                stringResource(R.string.theme_component_count),
                candidate.components.size.toString()
            )
            ToolInfoRow(stringResource(R.string.theme_size), formatBytes(candidate.size))
        }
    }
}

@Composable
private fun ComparisonCard(
    first: ThemeSourceReader.Candidate,
    second: ThemeSourceReader.Candidate
) {
    val firstMap = first.components.associateBy { it.code }
    val secondMap = second.components.associateBy { it.code }

    val onlyFirst = firstMap.keys.filterNot { it in secondMap }.sorted()
    val onlySecond = secondMap.keys.filterNot { it in firstMap }.sorted()
    val common = firstMap.keys.filter { it in secondMap }
    val changed = common.filter { firstMap[it]?.sha1 != secondMap[it]?.sha1 }.sorted()
    val identical = common.filter { firstMap[it]?.sha1 == secondMap[it]?.sha1 }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.compare_result),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (onlyFirst.isEmpty() && onlySecond.isEmpty() && changed.isEmpty()) {
                Text(
                    text = stringResource(R.string.compare_no_differences),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (onlyFirst.isNotEmpty()) {
                HorizontalDivider()
                CompareSection(
                    title = stringResource(R.string.compare_only_first),
                    lines = onlyFirst.map { componentLine(it) }
                )
            }

            if (onlySecond.isNotEmpty()) {
                HorizontalDivider()
                CompareSection(
                    title = stringResource(R.string.compare_only_second),
                    lines = onlySecond.map { componentLine(it) }
                )
            }

            if (changed.isNotEmpty()) {
                HorizontalDivider()
                CompareSection(
                    title = stringResource(R.string.compare_changed),
                    lines = changed.map { code ->
                        val left = firstMap.getValue(code)
                        val right = secondMap.getValue(code)
                        "${componentLine(code)} · ${
                            formatBytes(left.data.size.toLong())
                        } → ${formatBytes(right.data.size.toLong())}"
                    }
                )
            }

            Text(
                text = stringResource(R.string.compare_identical_count, identical.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun componentLine(code: String): String =
    "${ThemeComponents.displayName(code)} ($code)"

@Composable
private fun CompareSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        lines.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
