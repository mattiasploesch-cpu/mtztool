package at.libmatti.mtztool

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val message: String,
    val level: LogLevel,
)

/**
 * In-memory log shared by every screen and every tool. Logs survive activity
 * changes and are capped by the developer-configurable entry limit.
 */
object AppLog {

    private val lock = Any()
    private var nextId = 1L

    val entries = mutableStateListOf<LogEntry>()

    /** Mirrors [AppSettings.isLogAutoscrollEnabled], kept as observable state. */
    var autoScroll by mutableStateOf(true)

    var verbose by mutableStateOf(false)

    var maxEntries by mutableStateOf(300)

    fun syncFrom(context: Context) {
        autoScroll = AppSettings.isLogAutoscrollEnabled(context)
        verbose = AppSettings.isVerboseLoggingEnabled(context)
        maxEntries = AppSettings.getMaxLogEntries(context)
    }

    fun add(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = synchronized(lock) {
            LogEntry(
                id = nextId++,
                timestamp = System.currentTimeMillis(),
                message = message,
                level = level,
            ).also {
                entries.add(it)
                while (entries.size > maxEntries.coerceAtLeast(50)) {
                    entries.removeAt(0)
                }
            }
        }
        if (level == LogLevel.ERROR) {
            android.util.Log.e("MTZTool", entry.message)
        } else if (verbose) {
            android.util.Log.i("MTZTool", entry.message)
        }
    }

    fun info(message: String) = add(message, LogLevel.INFO)
    fun success(message: String) = add(message, LogLevel.SUCCESS)
    fun warn(message: String) = add(message, LogLevel.WARNING)
    fun error(message: String) = add(message, LogLevel.ERROR)

    fun clear() = synchronized(lock) { entries.clear() }

    fun asText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return entries.joinToString("\n") { entry ->
            "${format.format(Date(entry.timestamp))} [${entry.level}] ${entry.message}"
        }
    }
}
