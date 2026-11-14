package at.libmatti.mtztool.activity

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import at.libmatti.mtztool.R
import at.libmatti.mtztool.UpdateChecker
import at.libmatti.mtztool.UpdateRelease
import at.libmatti.mtztool.ui.theme.MTZToolTheme

class UpdatePromptActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    UpdatePromptScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class ChangelogSection(val title: String, val items: List<String>)

private fun parseChangelog(markdown: String): Pair<List<ChangelogSection>, List<String>> {
    val sections = mutableListOf<ChangelogSection>()
    val authors = linkedSetOf<String>()
    var title: String? = null
    var items = mutableListOf<String>()
    fun flush() {
        val current = title
        if (current != null && items.isNotEmpty()) {
            sections += ChangelogSection(current, items.toList())
        }
        items = mutableListOf()
    }
    markdown.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("## ")) {
            flush()
            title = line.removePrefix("## ").trim()
        } else if (line.startsWith("- ") && title != null) {
            items += line.removePrefix("- ").trim()
        }
        Regex("@([A-Za-z0-9-]+)").findAll(line).forEach { authors += it.groupValues[1] }
    }
    flush()
    return sections.filterNot {
        it.title.equals("Contributors", true) || it.title.startsWith("Full Changelog")
    } to authors.toList()
}

private fun sectionIcon(title: String) = when {
    title.contains("Security", true) -> Icons.Default.Security
    title.contains("Fix", true) -> Icons.Default.BugReport
    else -> Icons.Default.NewReleases
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatePromptScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var release by remember { mutableStateOf<UpdateRelease?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        UpdateChecker.check(context).fold(
            onSuccess = { release = it },
            onFailure = { error = it.message ?: it.javaClass.simpleName },
        )
        loading = false
    }

    val target = release
    val (sections, authors) = remember(target?.changelog) {
        parseChangelog(target?.changelog.orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.update_prompt_title), fontWeight = FontWeight.Bold)
                        if (target != null) {
                            Text(
                                "v${target.versionName} · ${target.tag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                target == null -> Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Text(
                        error ?: stringResource(R.string.updates_up_to_date),
                        modifier = Modifier.padding(18.dp),
                    )
                }

                else -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Update ${target.versionName}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (target.prerelease) stringResource(R.string.updates_prerelease)
                                else stringResource(R.string.updates_stable),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (target.patch != null) {
                                Text(
                                    stringResource(
                                        R.string.update_prompt_patch_hint,
                                        formatBytes(target.patch.size),
                                        formatBytes(target.apk.size),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    if (sections.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Text(
                                target.changelog.ifBlank { stringResource(R.string.update_prompt_no_changelog) },
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        sections.forEach { section ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(sectionIcon(section.title), contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text(
                                            section.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    section.items.forEach { item ->
                                        Text("• $item", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    if (authors.isNotEmpty()) {
                        Text(
                            stringResource(R.string.update_prompt_authors),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            authors.joinToString(" · ") { "@$it" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (target.patch != null) {
                        Button(
                            onClick = { PatcherLauncher.start(context, target.patch, target.versionName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.update_prompt_patch))
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, UpdateActivity::class.java))
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.update_prompt_full))
                        }
                    } else {
                        Button(
                            onClick = {
                                context.startActivity(Intent(context, UpdateActivity::class.java))
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.update_prompt_open_updates))
                        }
                    }

                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.update_prompt_later))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "–"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> String.format(java.util.Locale.US, "%.1f MiB", bytes / 1024.0 / 1024.0)
}
