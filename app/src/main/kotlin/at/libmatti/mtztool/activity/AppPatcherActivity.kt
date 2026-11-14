package at.libmatti.mtztool.activity

import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ApkPatchEngine
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.R
import at.libmatti.mtztool.UpdateChecker
import at.libmatti.mtztool.UpdateInstaller
import at.libmatti.mtztool.UpdatePatch
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppPatcherActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_FROM_NAME = "from_name"
        const val EXTRA_PATCH_URL = "patch_url"
        const val EXTRA_PATCH_SHA = "patch_sha"
        const val EXTRA_PATCH_SIZE = "patch_size"
        const val EXTRA_FROM_CODE = "from_code"
        const val EXTRA_TO_CODE = "to_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (AppSettings.isKeepScreenOnEnabled(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val patches = intent.getStringExtra(PatcherLauncher.EXTRA_PATCHES_JSON)
            ?.let(::parsePatches)
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                UpdatePatch(
                    fromVersionCode = intent.getIntExtra(EXTRA_FROM_CODE, 0),
                    toVersionCode = intent.getIntExtra(EXTRA_TO_CODE, 0),
                    fromVersionName = intent.getStringExtra(EXTRA_FROM_NAME).orEmpty(),
                    toVersionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty(),
                    url = intent.getStringExtra(EXTRA_PATCH_URL).orEmpty(),
                    size = intent.getLongExtra(EXTRA_PATCH_SIZE, 0L),
                    sha256 = intent.getStringExtra(EXTRA_PATCH_SHA),
                ),
            )
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppPatcherScreen(patches = patches, onBack = { finish() })
                }
            }
        }
    }
}

private fun parsePatches(json: String): List<UpdatePatch> = runCatching {
    val array = org.json.JSONArray(json)
    (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index) ?: return@mapNotNull null
        val url = obj.optString("url")
        if (url.isBlank()) null else UpdatePatch(
            fromVersionCode = obj.optInt("fromVersionCode"),
            toVersionCode = obj.optInt("toVersionCode"),
            fromVersionName = obj.optString("fromVersionName"),
            toVersionName = obj.optString("toVersionName"),
            url = url,
            size = obj.optLong("size", 0L),
            sha256 = obj.optString("sha256").takeIf { it.isNotBlank() },
        )
    }
}.getOrDefault(emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPatcherScreen(patches: List<UpdatePatch>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(context.getString(R.string.patch_preparing)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        if (running || patches.isEmpty() || patches.any { it.url.isBlank() }) {
            if (patches.isEmpty() || patches.any { it.url.isBlank() }) {
                error = "Kein Patch-Link übergeben."
            }
            return
        }
        running = true
        finished = false
        error = null
        progress = 0f
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var working = File(context.applicationInfo.sourceDir)
                    patches.forEachIndexed { index, patch ->
                        phase = "Lade Patch ${index + 1}/${patches.size} …"
                        AppLog.info("Lade Patch ${patch.fromVersionName} → ${patch.toVersionName} …")
                        val patchFile = UpdateChecker.downloadPatch(context, patch) { done, total ->
                            if (total > 0) {
                                val step = 0.85f / patches.size
                                progress = index * step + (done.toFloat() / total) * step * 0.4f
                            }
                        }.getOrThrow()
                        AppLog.success("Patch geladen (${formatSize(patchFile.length())}).")

                        phase = "Wende Patch ${index + 1}/${patches.size} an …"
                        AppLog.info("Wende Delta auf die installierte APK an …")
                        val output = File(
                            context.cacheDir,
                            "updates/MTZTool-${patch.toVersionName}-${index}.apk",
                        )
                        val applied = ApkPatchEngine.apply(working, patchFile, output) {
                                written, total ->
                            if (total > 0) {
                                val step = 0.85f / patches.size
                                progress = index * step + step * 0.4f +
                                    (written.toFloat() / total) * step * 0.6f
                            }
                        }
                        working = applied.file
                        progress = ((index + 1).toFloat() / patches.size) * 0.85f
                        AppLog.success("Patch ${index + 1} geprüft.")
                    }

                    phase = context.getString(R.string.patch_installing)
                    AppLog.info("Installiere gepatchtes APK …")
                    progress = 0.9f
                    UpdateInstaller.install(context, working) { line -> AppLog.info(line) }
                        .getOrThrow()
                    progress = 1f
                    finished = true
                    phase = context.getString(R.string.patch_done)
                    AppLog.success("Patch-Update abgeschlossen. Die App startet neu.")
                    working
                }
            }
            running = false
            result.exceptionOrNull()?.let {
                error = it.message ?: it.javaClass.simpleName
                phase = context.getString(R.string.patch_failed)
                AppLog.error("Patch-Update fehlgeschlagen: $error")
            }
        }
    }

    LaunchedEffect(Unit) { start() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.patch_title), fontWeight = FontWeight.Bold)
                        Text(
                            "${patches.first().fromVersionName.ifBlank { "?" }} → " +
                                patches.last().toVersionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (error == null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                error != null -> Icons.Default.Error
                                finished -> Icons.Default.CheckCircle
                                else -> Icons.Default.SystemUpdate
                            },
                            contentDescription = null,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            phase,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(progress * 100).toInt()} % · ${patches.size} Patch(es) · " +
                            formatSize(patches.sumOf { it.size }),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        stringResource(R.string.patch_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = ::start, enabled = !running) {
                            Text(stringResource(R.string.patch_retry))
                        }
                    }
                }
            }

            if (!running && finished) {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.patch_close))
                }
            }

            LogConsole(height = 320)
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "–"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> String.format(java.util.Locale.US, "%.1f MiB", bytes / 1024.0 / 1024.0)
}
