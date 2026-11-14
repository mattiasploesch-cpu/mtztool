package at.libmatti.mtztool.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.R
import at.libmatti.mtztool.RootThemeApplier
import at.libmatti.mtztool.ThemeApplier
import at.libmatti.mtztool.ThemeSession
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ApplyState {
    data object Running : ApplyState
    data class Success(val outcome: RootThemeApplier.Outcome) : ApplyState
    data class Failure(val message: String) : ApplyState
}

/**
 * Applies an MTZ theme without starting the Xiaomi Theme Manager.
 *
 * Supported input, in this order:
 *  - [EXTRA_THEME_PATH]: absolute path of a local .mtz
 *  - [EXTRA_THEME_URI] or the intent data: content:// or file:// URI
 *  - [EXTRA_USE_BUNDLED] or no input at all: the theme bundled under
 *    assets/theme.mtz or any .mtz in assets/themes/
 *
 * All file writes happen through [RootThemeApplier] in the theme UID/domain.
 */
class ApplyThemeActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_THEME_URI = "at.libmatti.mtztool.extra.THEME_URI"
        const val EXTRA_THEME_PATH = "at.libmatti.mtztool.extra.THEME_PATH"
        const val EXTRA_USE_BUNDLED = "at.libmatti.mtztool.extra.USE_BUNDLED"
        const val EXTRA_USE_SESSION = "at.libmatti.mtztool.extra.USE_SESSION"
        const val EXTRA_RESULT_MESSAGE = "at.libmatti.mtztool.extra.RESULT_MESSAGE"
    }

    private val state = mutableStateOf<ApplyState>(ApplyState.Running)
    private val logs = mutableStateListOf<String>()
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppSettings.isKeepScreenOnEnabled(this)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ApplyThemeScreen(
                        state = state.value,
                        logs = logs,
                        onClose = { finish() },
                        onRetry = {
                            started = false
                            startApply()
                        },
                    )
                }
            }
        }
        startApply()
    }

    private fun startApply() {
        if (started) return
        started = true
        state.value = ApplyState.Running
        logs.clear()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(applyResolvedSource())
                } catch (e: Exception) {
                    Result.failure<RootThemeApplier.Outcome>(e)
                }
            }

            result.onSuccess { outcome ->
                state.value = ApplyState.Success(outcome)
                AppLog.success(outcome.summary())
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_RESULT_MESSAGE, outcome.summary()),
                )
            }
            result.onFailure { error ->
                started = false
                val message = error.message ?: "Unbekannter Fehler"
                state.value = ApplyState.Failure(message)
                AppLog.error("Theme-Anwendung fehlgeschlagen: $message")
                setResult(
                    Activity.RESULT_CANCELED,
                    Intent().putExtra(EXTRA_RESULT_MESSAGE, message),
                )
            }
        }
    }

    private fun applyResolvedSource(): RootThemeApplier.Outcome {
        val explicitPath = intent.getStringExtra(EXTRA_THEME_PATH)
        if (!explicitPath.isNullOrBlank()) {
            val file = File(explicitPath)
            return RootThemeApplier.applyFile(this, file, file.name, ::progress)
        }

        val uriString = intent.getStringExtra(EXTRA_THEME_URI) ?: intent.dataString
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path
                    ?: throw IllegalArgumentException("file://-URI ohne Pfad")
                val file = File(path)
                return RootThemeApplier.applyFile(this, file, file.name, ::progress)
            }
            val displayName = ThemeApplier.getFileName(this, uri) ?: "Theme"
            return RootThemeApplier.applyUri(this, uri, displayName, ::progress)
        }

        if (intent.getBooleanExtra(EXTRA_USE_SESSION, false)) {
            val theme = ThemeSession.current
                ?: throw IllegalStateException("Im Workspace ist kein Theme geladen.")
            val directory = File(cacheDir, "session-theme-apply")
            try {
                directory.deleteRecursively()
                directory.mkdirs()
                val file = File(directory, "session-theme.mtz")
                file.writeBytes(theme.bytes)
                return RootThemeApplier.applyFile(this, file, theme.name, ::progress)
            } finally {
                directory.deleteRecursively()
            }
        }

        // No explicit source: apply the theme shipped with the app.
        return RootThemeApplier.applyBundled(this, ::progress)
    }

    private fun progress(line: String) {
        runOnUiThread {
            logs.add(line)
            AppLog.info(line)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyThemeScreen(
    state: ApplyState,
    logs: List<String>,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apply_theme_title)) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                ApplyState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.apply_theme_running),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                is ApplyState.Success -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.apply_theme_success),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(state.outcome.title, style = MaterialTheme.typography.titleLarge)
                    if (state.outcome.appliedComponents.isNotEmpty()) {
                        Text(
                            state.outcome.appliedComponents.joinToString(", ") {
                                at.libmatti.mtztool.ThemeComponents.displayName(it)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    state.outcome.notes.forEach { note ->
                        Text(note, style = MaterialTheme.typography.bodySmall)
                    }
                }

                is ApplyState.Failure -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.apply_theme_failure),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.apply_theme_log),
                    fontWeight = FontWeight.Bold,
                )
                val logState = rememberLazyListState()
                LaunchedEffect(logs.size) {
                    if (AppLog.autoScroll && logs.isNotEmpty()) {
                        logState.animateScrollToItem(logs.lastIndex)
                    }
                }
                LazyColumn(
                    state = logState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logs.size) { index ->
                        Text(
                            logs[index],
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state is ApplyState.Failure) {
                    Button(onClick = onRetry) {
                        Icon(Icons.Default.Palette, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.apply_theme_retry))
                    }
                }
                if (state !is ApplyState.Running) {
                    OutlinedButton(onClick = onClose) {
                        Text(stringResource(R.string.apply_theme_close))
                    }
                }
            }
        }
    }
}
