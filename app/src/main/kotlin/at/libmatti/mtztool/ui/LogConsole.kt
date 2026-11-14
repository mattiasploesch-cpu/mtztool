package at.libmatti.mtztool.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared log console. Scrolls to the newest entry automatically and lets the
 * user pause auto-scroll without leaving the screen.
 */
@Composable
fun LogConsole(
    modifier: Modifier = Modifier,
    height: Int = 260,
    showHeader: Boolean = true,
) {
    val context = LocalContext.current
    val entries = AppLog.entries
    val state = rememberLazyListState()

    LaunchedEffect(entries.size, AppLog.autoScroll) {
        if (AppLog.autoScroll && entries.isNotEmpty()) {
            state.animateScrollToItem(entries.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Log",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = AppLog.autoScroll,
                        onCheckedChange = {
                            AppLog.autoScroll = it
                            AppSettings.setLogAutoscrollEnabled(context, it)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("MTZ Tool log", AppLog.asText())
                        )
                        Toast.makeText(context, "Log kopiert", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Log kopieren",
                        )
                    }
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Log leeren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "${entries.size} Einträge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
                )
            }

            if (entries.isEmpty()) {
                Text(
                    text = "Noch keine Einträge. Aktionen erscheinen hier live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val color = when (entry.level) {
                            LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
                            LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = timeFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
