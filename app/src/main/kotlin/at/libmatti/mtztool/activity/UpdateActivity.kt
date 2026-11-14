package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdateActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    UpdateScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<UpdateRelease?>(null) }
    var checkedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var installing by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }

    val currentCode = remember { UpdateChecker.currentVersionCode(context) }
    val currentName = remember { UpdateChecker.currentVersionName(context) }
    val channel = AppSettings.getUpdateChannel(context)

    fun check() {
        if (checking) return
        checking = true
        error = null
        installMessage = null
        scope.launch {
            val result = UpdateChecker.check(context)
            checking = false
            checkedOnce = true
            result.fold(
                onSuccess = {
                    release = it
                    downloadedFile = null
                },
                onFailure = {
                    error = it.message ?: it.javaClass.simpleName
                },
            )
        }
    }

    fun download(target: UpdateRelease) {
        if (downloading) return
        downloading = true
        downloaded = 0L
        total = target.apk.size
        error = null
        scope.launch {
            val result = UpdateChecker.download(context, target) { got, size ->
                mainHandler.post {
                    downloaded = got
                    total = size
                }
            }
            downloading = false
            result.fold(
                onSuccess = {
                    downloadedFile = it
                    AppLog.success("Update heruntergeladen: ${it.name}")
                },
                onFailure = {
                    error = "Download fehlgeschlagen: ${it.message}"
                    AppLog.error(error!!)
                },
            )
        }
    }

    fun install(file: File) {
        if (installing) return
        installing = true
        installMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                UpdateInstaller.install(context, file) { line -> AppLog.info(line) }
            }
            installing = false
            result.fold(
                onSuccess = {
                    installMessage = it.message
                    AppLog.success(it.message)
                },
                onFailure = {
                    installMessage = "Installation fehlgeschlagen: ${it.message}"
                    AppLog.error(installMessage!!)
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.updates_title), fontWeight = FontWeight.Bold)
                        Text(
                            text = if (channel == UpdateChannel.PRERELEASE) {
                                stringResource(R.string.updates_prerelease)
                            } else {
                                stringResource(R.string.updates_stable)
                            },
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
                actions = {
                    IconButton(onClick = ::check, enabled = !checking) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.updates_check))
                        }
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.updates_installed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "v$currentName · Code $currentCode",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "${AppSettings.getGithubRepo(context)} · ${channel.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    val last = AppSettings.getLastUpdateCheck(context)
                    if (last > 0) {
                        Text(
                            "Letzte Prüfung: ${
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                    .format(Date(last))
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Button(
                onClick = ::check,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (checking) stringResource(R.string.updates_checking)
                    else stringResource(R.string.updates_check)
                )
            }

            if (channel == UpdateChannel.STABLE) {
                Text(
                    text = stringResource(R.string.updates_stable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.updates_prerelease_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val target = release
            if (target != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NewReleases, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Update ${target.versionName}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "${if (target.prerelease) "Pre-release" else "Stable"} · ${target.tag}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (target.publishedAt.isNotBlank()) {
                            Text(
                                "Veröffentlicht: ${target.publishedAt.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "${target.apk.name} · ${formatBytes(target.apk.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (target.changelog.isNotBlank()) {
                            Text(
                                target.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 14,
                            )
                        }

                        if (downloading) {
                            LinearProgressIndicator(
                                progress = {
                                    if (total > 0) (downloaded.toFloat() / total) else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${formatBytes(downloaded)} / ${formatBytes(total)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        val file = downloadedFile
                        if (file == null) {
                            Button(
                                onClick = { download(target) },
                                enabled = !downloading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (downloading) stringResource(R.string.updates_downloading)
                                    else stringResource(R.string.updates_download)
                                )
                            }
                        } else {
                            Button(
                                onClick = { install(file) },
                                enabled = !installing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (installing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (installing) stringResource(R.string.updates_installing)
                                    else stringResource(R.string.updates_install)
                                )
                            }
                            Text(
                                text = if (UpdateInstaller.canInstallInternally(context)) {
                                    stringResource(R.string.updates_internal_install)
                                } else {
                                    stringResource(R.string.updates_fallback_install)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        installMessage?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        if (target.htmlUrl.isNotBlank()) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target.htmlUrl))
                                    )
                                }
                            }) {
                                Text(stringResource(R.string.updates_open_github))
                            }
                        }
                    }
                }
            } else if (checkedOnce && !checking && error == null) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.updates_up_to_date))
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(context, DeveloperActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.updates_open_dev))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
