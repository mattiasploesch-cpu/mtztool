package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.github.IssueField
import at.libmatti.mtztool.github.IssueKind
import at.libmatti.mtztool.github.IssueTemplates
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** In-app issue creation with type-specific mandatory fields. */
class IssueEditorActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CRASH = "crash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = runCatching {
            IssueKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: IssueKind.BUG.name)
        }.getOrDefault(IssueKind.BUG)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val crash = intent.getStringExtra(EXTRA_CRASH)
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    IssueEditorScreen(
                        initialKind = kind,
                        initialTitle = title,
                        crashReport = crash,
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueEditorScreen(
    initialKind: IssueKind,
    initialTitle: String,
    crashReport: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var kind by remember { mutableStateOf(initialKind) }
    var title by remember { mutableStateOf(initialTitle) }
    val values = remember { mutableStateMapOf<IssueField, String>() }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var includeLogs by remember { mutableStateOf(true) }
    var includeSettings by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<GitHubApi.Issue?>(null) }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

    val visibleFields = IssueTemplates.fields(kind).let { fields ->
        if (filter.isBlank()) fields else fields.filter {
            it.label.contains(filter, true) || it.hint.contains(filter, true)
        }
    }

    fun submit() {
        val validation = IssueTemplates.validate(kind, title, values)
        problems = validation
        if (validation.isNotEmpty()) return
        if (!GitHubApi.isSignedIn(context)) {
            submitError = "Bitte zuerst mit GitHub anmelden."
            return
        }
        submitting = true
        submitError = null
        scope.launch {
            val body = IssueTemplates.buildBody(
                context = context,
                kind = kind,
                values = values,
                includeDiagnostics = includeDiagnostics,
                includeLogs = includeLogs,
                includeSettings = includeSettings,
                crashReport = crashReport,
            )
            GitHubApi.createIssue(
                context = context,
                title = IssueTemplates.buildTitle(kind, title),
                body = body,
                labels = kind.labels,
            ).fold(
                onSuccess = {
                    created = it
                    AppLog.success("Issue #${it.number} erstellt.")
                },
                onFailure = {
                    submitError = it.message ?: "Issue konnte nicht erstellt werden"
                },
            )
            submitting = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Issue erstellen", fontWeight = FontWeight.Bold)
                        Text(
                            AppSettings.getGithubRepo(context),
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
                    top = 12.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == IssueKind.BUG,
                            onClick = { kind = IssueKind.BUG },
                            label = { Text("Bug") },
                            leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        )
                        FilterChip(
                            selected = kind == IssueKind.FEATURE,
                            onClick = { kind = IssueKind.FEATURE },
                            label = { Text("Feature") },
                            leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                        )
                        FilterChip(
                            selected = kind == IssueKind.CRASH,
                            onClick = { kind = IssueKind.CRASH },
                            label = { Text("Crash") },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        )
                    }
                }

                item {
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
                            Text(
                                when (kind) {
                                    IssueKind.BUG -> "Bug Report"
                                    IssueKind.FEATURE -> "Feature Request"
                                    IssueKind.CRASH -> "Crash Report"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when (kind) {
                                    IssueKind.BUG ->
                                        "Reproduktionsschritte, erwartetes und tatsächliches " +
                                            "Verhalten sind Pflicht – so lässt sich der Fehler finden."
                                    IssueKind.FEATURE ->
                                        "Beschreibe Problem, Vorschlag und Anwendungsfall. " +
                                            "Feature-Requests brauchen andere Angaben als Bugs."
                                    IssueKind.CRASH ->
                                        "Der Stacktrace wird automatisch angehängt. Ergänze, " +
                                            "was du vor dem Absturz gemacht hast."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel *") },
                        supportingText = {
                            Text(
                                "Wird als \"${IssueTemplates.buildTitle(kind, title.ifBlank { "…" })}\" gesendet"
                            )
                        },
                        singleLine = true,
                        isError = title.isNotBlank() && title.trim().length < 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Felder filtern (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                items(visibleFields.size) { index ->
                    val field = visibleFields[index]
                    OutlinedTextField(
                        value = values[field].orEmpty(),
                        onValueChange = { values[field] = it },
                        label = { Text(field.label + if (field.minLength > 0) " *" else "") },
                        placeholder = { Text(field.hint) },
                        minLines = if (field == IssueField.STEPS) 4 else 3,
                        supportingText = {
                            Text(
                                "Mindestens ${field.minLength} Zeichen · " +
                                    "${values[field].orEmpty().trim().length} vorhanden",
                            )
                        },
                        isError = values[field].orEmpty().isNotBlank() &&
                            values[field].orEmpty().trim().length < field.minLength,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (crashReport != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                crashReport.take(2500),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ToggleRow("Geräte- und App-Diagnose anhängen", includeDiagnostics) {
                                includeDiagnostics = it
                            }
                            ToggleRow("Log anhängen", includeLogs) { includeLogs = it }
                            ToggleRow("Einstellungen (JSON) anhängen", includeSettings) {
                                includeSettings = it
                            }
                        }
                    }
                }

                if (problems.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Bitte ergänzen:",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                problems.forEach {
                                    Text(
                                        "• $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                submitError?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = ::submit,
                            enabled = !submitting && created == null,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (submitting) "Sende …" else "Issue erstellen")
                        }
                        OutlinedButton(
                            onClick = {
                                val repo = AppSettings.getGithubRepo(context)
                                val body = IssueTemplates.buildBody(
                                    context = context,
                                    kind = kind,
                                    values = values,
                                    includeDiagnostics = includeDiagnostics,
                                    includeLogs = includeLogs,
                                    includeSettings = includeSettings,
                                    crashReport = crashReport,
                                )
                                val url = "https://github.com/$repo/issues/new" +
                                    "?labels=${enc(kind.labels.joinToString(","))}" +
                                    "&title=${enc(IssueTemplates.buildTitle(kind, title))}" +
                                    "&body=${enc(body)}"
                                context.startActivity(
                                    android.content.Intent(context, InAppBrowserActivity::class.java)
                                        .putExtra(InAppBrowserActivity.EXTRA_URL, url)
                                        .putExtra(
                                            InAppBrowserActivity.EXTRA_TITLE,
                                            "Neues Issue",
                                        )
                                )
                            },
                            enabled = title.isNotBlank(),
                        ) {
                            Text("Formular")
                        }
                    }
                }

                if (!GitHubApi.isSignedIn(context)) {
                    item {
                        Text(
                            "Ohne Anmeldung kannst du das Issue über das GitHub-Formular im " +
                                "In-App-Browser erstellen.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                created?.let { issue ->
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Issue #${issue.number} erstellt",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    issue.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                TextButton(onClick = {
                                    context.startActivity(
                                        android.content.Intent(
                                            context,
                                            InAppBrowserActivity::class.java,
                                        )
                                            .putExtra(
                                                InAppBrowserActivity.EXTRA_URL,
                                                issue.htmlUrl,
                                            )
                                            .putExtra(
                                                InAppBrowserActivity.EXTRA_TITLE,
                                                "Issue #${issue.number}",
                                            )
                                    )
                                }) { Text("In der App öffnen") }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
