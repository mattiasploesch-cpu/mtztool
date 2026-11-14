package at.libmatti.mtztool.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.github.GitHubApi
import kotlinx.coroutines.launch

/**
 * Full issue view. Owners additionally get label, assignee, state, comment
 * and workflow controls; every signed-in user can read their own issue.
 */
@Composable
fun IssueDetailDialog(
    issue: GitHubApi.Issue,
    isOwner: Boolean,
    login: String,
    repoLabels: List<GitHubApi.Label>,
    assignableUsers: List<String>,
    workflows: List<GitHubApi.Workflow>,
    onDismiss: () -> Unit,
    onUpdated: (GitHubApi.Issue) -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var workflowRef by remember { mutableStateOf("main") }
    var workflowInputs by remember { mutableStateOf("") }

    fun applyIssueUpdate(
        labels: List<String>? = null,
        assignees: List<String>? = null,
        state: String? = null,
    ) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            GitHubApi.updateIssue(
                context = context,
                number = issue.number,
                labels = labels,
                assignees = assignees,
                state = state,
            ).fold(
                onSuccess = {
                    onUpdated(it)
                    message = "Issue aktualisiert."
                },
                onFailure = { message = it.message },
            )
            busy = false
        }
    }

    fun sendComment() {
        if (busy || comment.isBlank()) return
        busy = true
        message = null
        scope.launch {
            GitHubApi.comment(
                context = context,
                repo = AppSettings.getGithubRepo(context),
                number = issue.number,
                body = comment.trim(),
            ).fold(
                onSuccess = {
                    comment = ""
                    message = "Kommentar gesendet."
                },
                onFailure = { message = it.message },
            )
            busy = false
        }
    }

    fun runWorkflow(workflow: GitHubApi.Workflow) {
        if (busy) return
        val inputs = workflowInputs.split(',')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                part.substring(0, separator).trim() to part.substring(separator + 1).trim()
            }
            .filter { it.first.isNotBlank() }
            .toMap()
        busy = true
        message = null
        scope.launch {
            GitHubApi.dispatchWorkflow(
                context = context,
                workflowId = workflow.id,
                ref = workflowRef.ifBlank { "main" },
                inputs = inputs,
            ).fold(
                onSuccess = { message = "„${workflow.name}“ gestartet." },
                onFailure = { message = it.message },
            )
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("#${issue.number} ${issue.title}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${issue.state} · @${issue.author} · ${issue.comments} Kommentare",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (issue.labels.isNotEmpty()) {
                    Text(
                        "Labels: ${issue.labels.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (issue.assignees.isNotEmpty()) {
                    Text(
                        "Zugewiesen: ${issue.assignees.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(issue.body.ifBlank { "Keine Beschreibung." })

                if (isOwner) {
                    Text("Labels", fontWeight = FontWeight.Bold)
                    if (repoLabels.isEmpty()) {
                        Text(
                            "Keine Labels geladen.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    repoLabels.take(40).forEach { label ->
                        FilterChip(
                            selected = label.name in issue.labels,
                            onClick = {
                                val next = if (label.name in issue.labels) {
                                    issue.labels - label.name
                                } else {
                                    issue.labels + label.name
                                }
                                applyIssueUpdate(labels = next)
                            },
                            enabled = !busy,
                            label = { Text(label.name) },
                        )
                    }

                    Text("Personen zuweisen", fontWeight = FontWeight.Bold)
                    Text(
                        "Eingeloggt als @$login",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (assignableUsers.isEmpty()) {
                        Text(
                            "Keine zuweisbaren Personen geladen.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    assignableUsers.take(30).forEach { user ->
                        FilterChip(
                            selected = user in issue.assignees,
                            onClick = {
                                val next = if (user in issue.assignees) {
                                    issue.assignees - user
                                } else {
                                    issue.assignees + user
                                }
                                applyIssueUpdate(assignees = next)
                            },
                            enabled = !busy,
                            label = { Text(user) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                applyIssueUpdate(
                                    state = if (issue.state == "open") "closed" else "open",
                                )
                            },
                            enabled = !busy,
                        ) {
                            Text(if (issue.state == "open") "Schließen" else "Wieder öffnen")
                        }
                        TextButton(onClick = onOpenInBrowser) {
                            Text("Im Browser")
                        }
                    }

                    Text("Kommentar", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("Antwort schreiben") },
                    )
                    Button(
                        onClick = { sendComment() },
                        enabled = !busy && comment.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Kommentar senden")
                    }

                    Text("Actions", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = workflowRef,
                        onValueChange = { workflowRef = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Branch / Ref") },
                    )
                    OutlinedTextField(
                        value = workflowInputs,
                        onValueChange = { workflowInputs = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Inputs: key=value, key2=value2") },
                    )
                    if (workflows.isEmpty()) {
                        Text(
                            "Keine Workflows geladen.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    workflows.take(30).forEach { workflow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    workflow.name.ifBlank { workflow.path },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                                Text(
                                    workflow.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            TextButton(
                                onClick = { runWorkflow(workflow) },
                                enabled = !busy,
                            ) {
                                Text("Start")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Im Browser öffnen")
                    }
                }

                message?.let {
                    Text(
                        it,
                        color = if (it.contains("fehl", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (busy) {
                    Text(
                        "Bitte warten …",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}
