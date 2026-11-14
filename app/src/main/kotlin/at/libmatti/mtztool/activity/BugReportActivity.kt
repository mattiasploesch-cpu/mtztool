package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import java.net.URLEncoder

class BugReportActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BugReportScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BugReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var isFeature by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var includeLogs by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(false) }

    fun diagnostics(): String = buildString {
        appendLine("### Environment")
        appendLine("- App: ${UpdateChecker.currentVersionName(context)} (${UpdateChecker.currentVersionCode(context)})")
        appendLine("- Package: ${context.packageName}")
        appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- Build: ${Build.DISPLAY}")
        appendLine("- Provider: ${AppSettings.getPrivilegeProvider(context).name}")
        appendLine("- Shizuku: ${ShizukuHelper.getStatus().name}")
        appendLine("- Theme family: ${AppSettings.getThemeFamily(context).name}")
        appendLine("- Theme mode: ${AppSettings.getThemeMode(context).name}")
        appendLine("- Update channel: ${AppSettings.getUpdateChannel(context).name}")
        appendLine("- Workspace: ${ThemeSession.current?.let { "${it.name} (${it.size} bytes)" } ?: "empty"}")
        if (includeSettings) {
            appendLine()
            appendLine("### Settings")
            appendLine("```json")
            appendLine(AppSettings.exportJson(context))
            appendLine("```")
        }
        if (includeLogs) {
            appendLine()
            appendLine("### Log")
            appendLine("```")
            appendLine(AppLog.asText().ifBlank { "(no entries)" })
            appendLine("```")
        }
    }

    fun issueBody(): String = buildString {
        appendLine(if (isFeature) "### Feature request" else "### Bug description")
        appendLine(description.ifBlank { "_No description provided._" })
        if (steps.isNotBlank()) {
            appendLine()
            appendLine("### Steps to reproduce")
            appendLine(steps)
        }
        if (expected.isNotBlank()) {
            appendLine()
            appendLine("### Expected behaviour")
            appendLine(expected)
        }
        if (includeDiagnostics) {
            appendLine()
            append(diagnostics())
        }
    }

    fun issueUrl(): String {
        val repo = AppSettings.getGithubRepo(context)
        val label = if (isFeature) "enhancement" else "bug"
        val header = if (isFeature) "[Feature] " else "[Bug] "
        return "https://github.com/$repo/issues/new" +
            "?labels=${enc(label)}" +
            "&title=${enc(header + title.ifBlank { "Report" })}" +
            "&body=${enc(issueBody())}"
    }

    fun copyAll() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MTZ Tool bug report", issueBody()))
        Toast.makeText(context, R.string.bug_report_copied, Toast.LENGTH_SHORT).show()
    }

    fun openIssue() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl())))
        }.onFailure {
            copyAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.bug_report_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.bug_report_subtitle),
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
                Row(modifier = Modifier.padding(18.dp)) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.bug_report_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "GitHub · ${AppSettings.getGithubRepo(context)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isFeature,
                    onClick = { isFeature = false },
                    label = { Text(stringResource(R.string.bug_report_category_bug)) },
                )
                FilterChip(
                    selected = isFeature,
                    onClick = { isFeature = true },
                    label = { Text(stringResource(R.string.bug_report_category_feature)) },
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.bug_report_short_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.bug_report_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = steps,
                onValueChange = { steps = it },
                label = { Text(stringResource(R.string.bug_report_steps)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text(stringResource(R.string.bug_report_expected)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ToggleRow(
                        stringResource(R.string.bug_report_include_device),
                        includeDiagnostics,
                    ) { includeDiagnostics = it }
                    ToggleRow(
                        stringResource(R.string.bug_report_include_logs),
                        includeLogs,
                    ) { includeLogs = it }
                    ToggleRow(
                        stringResource(R.string.bug_report_include_settings),
                        includeSettings,
                    ) { includeSettings = it }
                }
            }

            if (includeDiagnostics) {
                Text(
                    stringResource(R.string.bug_report_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text(
                        text = diagnostics(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 20,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = ::openIssue,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bug_report_open))
                }
                OutlinedButton(
                    onClick = ::copyAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bug_report_copy))
                }
            }
            Text(
                text = stringResource(R.string.bug_report_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun enc(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
