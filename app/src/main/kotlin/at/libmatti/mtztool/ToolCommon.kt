package at.libmatti.mtztool

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Shared building blocks for the small MTZ tool activities.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun ToolFilePickerCard(
    title: String,
    fileName: String?,
    buttonText: String,
    onPick: () -> Unit,
    enabled: Boolean = true
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = fileName ?: stringResource(R.string.tool_no_file_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun ToolMessageCard(text: String, isError: Boolean = false) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun ToolLoadingCard(text: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ToolInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun <T> ToolDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedText,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionText(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

// ── Theme parsing ─────────────────────────────────────────────────

suspend fun parseThemeUri(
    context: Context,
    uri: Uri,
    fallbackName: String,
    cacheDirName: String
): Result<ThemeSourceReader.ReadResult> = withContext(Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, cacheDirName)
        directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) {
            error("Cache directory could not be created")
        }
        ThemeSourceReader.read(context, uri, directory, fallbackName)
    }
}

/** Parses the shared in-memory workspace theme without asking for a file again. */
suspend fun parseThemeBytes(
    context: Context,
    bytes: ByteArray,
    fallbackName: String,
    cacheDirName: String
): Result<ThemeSourceReader.ReadResult> = withContext(Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, cacheDirName)
        directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) {
            error("Cache directory could not be created")
        }
        val source = File(directory, "workspace-theme.mtz")
        source.writeBytes(bytes)
        ThemeReaderBridge.read(source, directory, fallbackName)
    }
}

/**
 * Small indirection so the tools keep working if the reader entry point is
 * renamed. It always parses from a real file, which is what the backup parser
 * expects.
 */
private object ThemeReaderBridge {
    fun read(file: File, directory: File, fallbackName: String) =
        ThemeSourceReader.read(file, directory, fallbackName)
}

/** Card shown at the top of every tool when a theme is loaded in the workspace. */
@Composable
fun ToolWorkspaceCard(
    onLoad: () -> Unit,
    enabled: Boolean = true,
) {
    val theme = ThemeSession.current
    if (theme == null) return
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${theme.name} · ${formatBytes(theme.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onLoad, enabled = enabled) {
                Text(stringResource(R.string.common_use_workspace))
            }
        }
    }
}

fun pickedFileName(context: Context, uri: Uri): String =
    ThemeApplier.getFileName(context, uri)
        ?.takeIf { it.isNotBlank() }
        ?: "theme"

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    var value = bytes.toDouble() / 1024.0
    val units = listOf("KB", "MB", "GB")
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

// ── Component helpers ─────────────────────────────────────────────

internal fun ByteArray.startsWith(vararg expected: Int): Boolean {
    if (size < expected.size) return false
    for (index in expected.indices) {
        if ((this[index].toInt() and 0xFF) != expected[index]) return false
    }
    return true
}

fun componentExtension(component: ThemeSourceReader.Component): String {
    val data = component.data
    if (data.startsWith(0x50, 0x4B)) return "zip"
    if (data.startsWith(0xFF, 0xD8)) return "jpg"
    if (data.startsWith(0x89, 0x50, 0x4E, 0x47)) return "png"
    if (data.size >= 12 &&
        data.startsWith(0x52, 0x49, 0x46, 0x46) &&
        String(data, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
    ) {
        return "webp"
    }
    if (data.startsWith(0x4F, 0x54, 0x54, 0x4F)) return "otf"
    if (data.startsWith(0x00, 0x01, 0x00, 0x00)) return "ttf"
    if (data.startsWith(0x74, 0x72, 0x75, 0x65)) return "ttf"
    if (data.startsWith(0x3C)) return "xml"
    return "bin"
}

fun componentMime(component: ThemeSourceReader.Component): String =
    when (componentExtension(component)) {
        "zip" -> "application/zip"
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "xml" -> "text/xml"
        else -> "application/octet-stream"
    }

fun imageExtension(data: ByteArray): String =
    when {
        data.startsWith(0xFF, 0xD8) -> "jpg"
        data.startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
        data.size >= 12 &&
            data.startsWith(0x52, 0x49, 0x46, 0x46) &&
            String(data, 8, 4, StandardCharsets.US_ASCII) == "WEBP" -> "webp"
        else -> "png"
    }

fun sanitizeFileName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .take(80)
        .ifBlank { "theme" }

fun suggestedComponentFileName(
    title: String,
    component: ThemeSourceReader.Component
): String {
    val base = sanitizeFileName(title.ifBlank { "theme" })
    val code = sanitizeFileName(component.code.ifBlank { "component" })
    return "$base-$code.${componentExtension(component)}"
}

fun suggestedPreviewFileName(
    title: String,
    previewName: String,
    data: ByteArray
): String {
    val base = sanitizeFileName(title.ifBlank { "theme" })
    var name = sanitizeFileName(previewName.substringAfterLast('/'))
    if (!name.contains('.')) {
        name = "$name.${imageExtension(data)}"
    }
    return "$base-$name"
}
