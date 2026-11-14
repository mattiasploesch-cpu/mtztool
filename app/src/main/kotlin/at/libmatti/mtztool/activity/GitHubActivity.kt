package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.AdaptiveContent
import at.libmatti.mtztool.ui.rememberDeviceProfile
import at.libmatti.mtztool.ui.theme.MTZToolTheme

class GitHubActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GitHubScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = rememberDeviceProfile()

    var signedIn by remember { mutableStateOf(GitHubApi.isSignedIn(context)) }
    var login by remember { mutableStateOf(AppSettings.getGithubLogin(context)) }
    val isOwner = GitHubApi.isOwner(context)
    var googleAccount by remember { mutableStateOf(AppSettings.getGoogleAccount(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var issueFilter by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var issues by remember { mutableStateOf<List<GitHubApi.Issue>>(emptyList()) }
    var pulls by remember { mutableStateOf<List<GitHubApi.PullRequest>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<GitHubApi.Notification>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GitHubApi.WorkflowRun>>(emptyList()) }
    var selectedIssue by remember { mutableStateOf<GitHubApi.Issue?>(null) }
    var repoLabels by remember { mutableStateOf<List<GitHubApi.Label>>(emptyList()) }
    var assignableUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var workflows by remember { mutableStateOf<List<GitHubApi.Workflow>>(emptyList()) }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            signedIn = GitHubApi.isSignedIn(context)
            login = AppSettings.getGithubLogin(context)
            googleAccount = AppSettings.getGoogleAccount(context)
            revision += 1
        }
    }

    fun open(url: String, title: String) {
        if (url.isBlank()) return
        context.startActivity(
            Intent(context, InAppBrowserActivity::class.java)
                .putExtra(InAppBrowserActivity.EXTRA_URL, url)
                .putExtra(InAppBrowserActivity.EXTRA_TITLE, title)
        )
    }

    LaunchedEffect(tab, revision, signedIn, issueFilter) {
        if (!signedIn) return@LaunchedEffect
        if (!isOwner && tab !in 0..1) {
            tab = 1
            return@LaunchedEffect
        }
        loading = true
        error = null
        when (tab) {
            1 -> {
                // Owner sees the repository queue, everyone else only their
                // own issues. Hiding the tab is not enough – the data request
                // is restricted as well.
                val result = if (isOwner && issueFilter == 0) {
                    GitHubApi.issues(context)
                } else {
                    GitHubApi.myIssues(context)
                }
                result.fold(
                    onSuccess = { issues = it },
                    onFailure = {
                        issues = emptyList()
                        error = it.message
                    },
                )
            }
            2 -> GitHubApi.pullRequests(context).fold(
                onSuccess = { pulls = it },
                onFailure = {
                    pulls = emptyList()
                    error = it.message
                },
            )
            3 -> GitHubApi.notifications(context).fold(
                onSuccess = { notifications = it },
                onFailure = {
                    notifications = emptyList()
                    error = it.message
                },
            )
            4 -> GitHubApi.workflowRuns(context).fold(
                onSuccess = { runs = it },
                onFailure = {
                    runs = emptyList()
                    error = it.message
                },
            )
        }
        loading = false
    }

    LaunchedEffect(selectedIssue?.number, isOwner) {
        val issue = selectedIssue ?: return@LaunchedEffect
        if (!isOwner) return@LaunchedEffect
        repoLabels = GitHubApi.labels(context).getOrDefault(emptyList())
        assignableUsers = GitHubApi.assignableUsers(context).getOrDefault(emptyList())
        workflows = GitHubApi.workflows(context).getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GitHub", fontWeight = FontWeight.Bold)
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
                actions = {
                    IconButton(onClick = { revision += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
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
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = tab) {
                val tabs = if (isOwner) {
                    listOf("Konto", "Issues", "PRs", "Meldungen", "Actions")
                } else {
                    listOf("Konto", "Issues")
                }
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }

            AdaptiveContent(profile = profile) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = profile.horizontalPaddingDp.dp,
                        end = profile.horizontalPaddingDp.dp,
                        top = 14.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!signedIn) {
                        item {
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
                                        "GitHub verbinden",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        "Melde dich in der App an, um Issues zu erstellen, " +
                                            "deine Issues zu sehen, Theme-Upload-PRs zu erzeugen " +
                                            "und Actions zu prüfen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Button(
                                        onClick = {
                                            authLauncher.launch(
                                                Intent(context, GitHubAuthActivity::class.java)
                                                    .putExtra(
                                                        GitHubAuthActivity.EXTRA_MODE,
                                                        GitHubAuthActivity.MODE_GITHUB,
                                                    )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Mit GitHub anmelden")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            authLauncher.launch(
                                                Intent(context, GitHubAuthActivity::class.java)
                                                    .putExtra(
                                                        GitHubAuthActivity.EXTRA_MODE,
                                                        GitHubAuthActivity.MODE_GOOGLE,
                                                    )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Mit Google anmelden")
                                    }
                                    Text(
                                        "Hinweis: Google wird nur als Konto-Verknüpfung " +
                                            "verwendet. GitHub-Funktionen brauchen die " +
                                            "GitHub-Anmeldung.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        item {
                            Card(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Ohne Anmeldung",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            open(
                                                "https://github.com/${AppSettings.getGithubRepo(context)}/issues/new",
                                                "Issue auf GitHub",
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.BugReport, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Issue im In-App-Browser")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            open(ThemeBrowser.officialStoreUrl(), "Theme Store")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Theme Store öffnen")
                                    }
                                }
                            }
                        }
                        return@LazyColumn
                    }

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
                                Text(
                                    "Angemeldet als ${login.ifBlank { "GitHub-Nutzer" }}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    AppSettings.getGithubRepo(context),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                if (googleAccount.isNotBlank()) {
                                    Text(
                                        "Google verknüpft: $googleAccount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, IssueEditorActivity::class.java)
                                            .putExtra(
                                                IssueEditorActivity.EXTRA_KIND,
                                                "BUG",
                                            )
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Neues Issue")
                            }
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, ThemeBrowserActivity::class.java)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Themes")
                            }
                        }
                    }

                    if (loading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Lade GitHub-Daten …")
                            }
                        }
                    }
                    error?.let {
                        item {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (tab == 1) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = issueFilter == 0,
                                    onClick = { issueFilter = 0 },
                                    label = { Text("Repo") },
                                )
                                FilterChip(
                                    selected = issueFilter == 1,
                                    onClick = { issueFilter = 1 },
                                    label = { Text("Meine Issues") },
                                )
                            }
                        }
                        items(issues.size) { index ->
                            val issue = issues[index]
                            GitHubListRow(
                                title = "#${issue.number} ${issue.title}",
                                subtitle = "${issue.state} · ${issue.author} · " +
                                    issue.labels.joinToString(", ") +
                                    if (issue.comments > 0) " · ${issue.comments} Kommentare" else "",
                                onClick = { selectedIssue = issue },
                            )
                        }
                        if (issues.isEmpty() && !loading) {
                            item { Text("Keine Issues gefunden.") }
                        }
                    }

                    if (tab == 2) {
                        items(pulls.size) { index ->
                            val pull = pulls[index]
                            GitHubListRow(
                                title = "#${pull.number} ${pull.title}",
                                subtitle = "${pull.state} · ${pull.author} · " +
                                    "${pull.head} → ${pull.base}" +
                                    if (pull.draft) " · Draft" else "",
                                onClick = { open(pull.htmlUrl, "PR #${pull.number}") },
                            )
                        }
                        if (pulls.isEmpty() && !loading) {
                            item { Text("Keine Pull Requests gefunden.") }
                        }
                    }

                    if (tab == 3) {
                        items(notifications.size) { index ->
                            val notification = notifications[index]
                            GitHubListRow(
                                title = notification.title.ifBlank { "Benachrichtigung" },
                                subtitle = "${notification.repo} · ${notification.reason}",
                                onClick = {
                                    open(
                                        "https://github.com/notifications",
                                        "GitHub-Benachrichtigungen",
                                    )
                                },
                            )
                        }
                        if (notifications.isEmpty() && !loading) {
                            item { Text("Keine ungelesenen Benachrichtigungen.") }
                        }
                    }

                    if (tab == 4) {
                        item {
                            Text(
                                "Die Pipelines liegen unter .github/workflows/. " +
                                    "Build, PR-Checks, CodeQL, Labels, Stale und Release " +
                                    "laufen automatisch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(runs.size) { index ->
                            val run = runs[index]
                            GitHubListRow(
                                title = run.name.ifBlank { "Workflow" },
                                subtitle = "${run.branch} · ${run.status}" +
                                    run.conclusion.takeIf { it.isNotBlank() }?.let { " · $it" }
                                        .orEmpty(),
                                onClick = { open(run.htmlUrl, "Actions") },
                            )
                        }
                        if (runs.isEmpty() && !loading) {
                            item {
                                Text(
                                    "Noch keine Workflow-Läufe gefunden. Sobald der " +
                                        "erste Push auf GitHub passiert, erscheinen sie hier.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (tab == 0) {
                        item {
                            Card(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Schnellzugriff", fontWeight = FontWeight.Bold)
                                    OutlinedButton(
                                        onClick = {
                                            open(
                                                "https://github.com/${AppSettings.getGithubRepo(context)}",
                                                "Repository",
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Repository öffnen")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            open(
                                                "https://github.com/${AppSettings.getGithubRepo(context)}/issues",
                                                "Issues",
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Alle Issues") }
                                    OutlinedButton(
                                        onClick = {
                                            open(
                                                "https://github.com/${AppSettings.getGithubRepo(context)}/pulls",
                                                "Pull Requests",
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Alle Pull Requests") }
                                }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    AppSettings.setGithubToken(context, "")
                                    AppSettings.setGithubLogin(context, "")
                                    signedIn = false
                                    revision += 1
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("GitHub abmelden")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedIssue?.let { issue ->
        IssueDetailDialog(
            issue = issue,
            isOwner = isOwner,
            login = login,
            repoLabels = repoLabels,
            assignableUsers = assignableUsers,
            workflows = workflows,
            onDismiss = { selectedIssue = null },
            onUpdated = {
                selectedIssue = it
                revision += 1
            },
            onOpenInBrowser = { open(issue.htmlUrl, "Issue #${issue.number}") },
        )
    }
}

@Composable
private fun GitHubListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
