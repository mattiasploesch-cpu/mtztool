package at.libmatti.mtztool

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions and keeps the last report so the app can turn
 * it into a GitHub issue on the next start. The handler always delegates to
 * the previous handler, so normal crash reporting by the system is preserved.
 */
object CrashReporter {

    private const val PREFS = "mtz_tool_crash"
    private const val KEY_PENDING = "pending_report"

    data class Report(
        val timestamp: Long,
        val thread: String,
        val exception: String,
        val stacktrace: String,
        val versionName: String,
        val versionCode: Int,
        val android: String,
        val device: String,
        val log: String,
    ) {
        fun toJson(): String = JSONObject().apply {
            put("timestamp", timestamp)
            put("thread", thread)
            put("exception", exception)
            put("stacktrace", stacktrace)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("android", android)
            put("device", device)
            put("log", log)
        }.toString()

        fun summary(): String =
            "Absturz am ${formatTimestamp(timestamp)} in „$thread“: $exception"

        companion object {
            fun fromJson(text: String): Report? = runCatching {
                val json = JSONObject(text)
                Report(
                    timestamp = json.optLong("timestamp"),
                    thread = json.optString("thread"),
                    exception = json.optString("exception"),
                    stacktrace = json.optString("stacktrace"),
                    versionName = json.optString("versionName"),
                    versionCode = json.optInt("versionCode"),
                    android = json.optString("android"),
                    device = json.optString("device"),
                    log = json.optString("log"),
                )
            }.getOrNull()

            private fun formatTimestamp(timestamp: Long): String =
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(timestamp))
        }
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(app, previous))
    }

    fun pending(context: Context): Report? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING, null) ?: return null
        return Report.fromJson(raw)
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .commit()
    }

    fun issueBody(context: Context, report: Report): String = buildString {
        appendLine("### Crash")
        appendLine()
        appendLine("Die App wurde unerwartet beendet. Der Bericht wurde automatisch erfasst.")
        appendLine()
        appendLine("### Absturz")
        appendLine("- Zeitpunkt: ${report.summary()}")
        appendLine("- Version: ${report.versionName} (${report.versionCode})")
        appendLine("- Gerät: ${report.device}")
        appendLine("- Android: ${report.android}")
        appendLine()
        appendLine("### Stacktrace")
        appendLine("```")
        appendLine(report.stacktrace.take(12_000))
        appendLine("```")
        appendLine()
        appendLine("### Log")
        appendLine("```")
        appendLine(report.log.ifBlank { "(keine Einträge)" }.takeLast(8_000))
        appendLine("```")
    }

    private class CrashHandler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { record(thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }

        private fun record(thread: Thread, error: Throwable) {
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            val report = Report(
                timestamp = System.currentTimeMillis(),
                thread = thread.name,
                exception = "${error.javaClass.name}: ${error.message.orEmpty()}",
                stacktrace = writer.toString(),
                versionName = UpdateChecker.currentVersionName(context),
                versionCode = UpdateChecker.currentVersionCode(context),
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                log = AppLog.asText(),
            )
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING, report.toJson())
                .commit()
        }
    }
}
