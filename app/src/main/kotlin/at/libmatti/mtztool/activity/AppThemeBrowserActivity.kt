package at.libmatti.mtztool.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AppThemeBrowserActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppThemeBrowserScreen(onBack = { finish() })
                }
            }
        }
    }
}

private data class AppThemeEntry(
    val slug: String,
    val name: String,
    val author: String,
    val description: String,
    val colors: Map<String, String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppThemeBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var themes by remember { mutableStateOf<List<AppThemeEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val entries = GitHubApi.directory(context, "app-themes").getOrThrow()
                entries.filter { it.type == "file" && it.name.endsWith(".json") && it.name != "schema.json" }
                    .mapNotNull { entry ->
                        val text = GitHubApi.readFile(context, entry.path)
                            .getOrNull()?.bytes?.toString(Charsets.UTF_8) ?: return@mapNotNull null
                        val json = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
                        val colors = json.optJSONObject("colors") ?: return@mapNotNull null
                        val map = buildMap {
                            colors.keys().forEach { key -> put(key, colors.optString(key)) }
                        }
                        AppThemeEntry(
                            slug = json.optString("slug", entry.name.removeSuffix(".json")),
                            name = json.optString("name", entry.name),
                            author = json.optString("author"),
                            description = json.optString("description"),
                            colors = map,
                        )
                    }
            }.fold(
                onSuccess = { themes = it },
                onFailure = { error = it.message },
            )
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App-Theme-Browser", fontWeight = FontWeight.Bold)
                        Text(
                            "Farbwelten für die App · eigener Browser",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, AppThemeStudioActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Neues App-Theme erstellen")
                }
            }
            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            }
            error?.let {
                item { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (!loading && themes.isEmpty() && error == null) {
                item {
                    Text(
                        "Noch keine App-Themes veröffentlicht.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(themes) { theme ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(theme.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "@${theme.author.ifBlank { "unbekannt" }} · ${theme.slug}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (theme.description.isNotBlank()) {
                            Text(theme.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            theme.colors.values.take(8).forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(parseHex(hex), CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseHex(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF415F91))
