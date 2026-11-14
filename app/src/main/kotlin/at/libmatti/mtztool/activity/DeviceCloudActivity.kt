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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.R
import at.libmatti.mtztool.github.DeviceCloud
import at.libmatti.mtztool.github.DeviceRecord
import at.libmatti.mtztool.github.RemoteConfig
import at.libmatti.mtztool.ui.LogConsole
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceCloudActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DeviceCloudScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCloudScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(DeviceCloud.isSignedIn(context)) }
    var syncEnabled by remember { mutableStateOf(AppSettings.isDeviceSyncEnabled(context)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }
    var remote by remember { mutableStateOf(RemoteConfig.cached(context)) }
    val lastSync = AppSettings.getLastDeviceSync(context)

    fun reload() {
        scope.launch {
            signedIn = DeviceCloud.isSignedIn(context)
            if (!signedIn) return@launch
            DeviceCloud.otherDevices(context).onSuccess { devices = it }
        }
    }

    LaunchedEffect(Unit) {
        reload()
        RemoteConfig.refresh(context).onSuccess { remote = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.cloud_title), fontWeight = FontWeight.Bold)
                        Text(
                            DeviceCloud.slug(context),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (signedIn) stringResource(R.string.cloud_desc)
                            else stringResource(R.string.cloud_signed_out),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "${DeviceCloud.label()} · ${AppSettings.getGithubLogin(context)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.cloud_last_sync,
                            if (lastSync > 0) {
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                    .format(Date(lastSync))
                            } else "–",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (!signedIn) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, GitHubAuthActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cloud_open_github)) }
            } else {
                Button(
                    onClick = {
                        busy = "register"
                        scope.launch {
                            DeviceCloud.ensureRegistration(context).fold(
                                onSuccess = { message = it },
                                onFailure = { message = it.message },
                            )
                            busy = null
                            reload()
                        }
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "register") {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Devices, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.cloud_register))
                }

                Button(
                    onClick = {
                        busy = "upload"
                        scope.launch {
                            DeviceCloud.upload(context).fold(
                                onSuccess = { message = "Backup-PR angelegt: $it" },
                                onFailure = { message = it.message },
                            )
                            busy = null
                        }
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "upload") {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.cloud_backup))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.cloud_auto_sync), fontWeight = FontWeight.Bold)
                        Text(
                            "Alle ${remote.syncIntervalHours} h, nur mit GitHub-Anmeldung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = {
                            syncEnabled = it
                            AppSettings.setDeviceSyncEnabled(context, it)
                            if (it) {
                                scope.launch { DeviceCloud.upload(context) }
                            }
                        },
                    )
                }

                if (devices.isNotEmpty()) {
                    Text(stringResource(R.string.cloud_devices), fontWeight = FontWeight.Bold)
                    devices.forEach { device ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.label, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${device.model} · v${device.appVersion}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        busy = device.deviceId
                                        scope.launch {
                                            DeviceCloud.restore(context, device.deviceId).fold(
                                                onSuccess = { message = it },
                                                onFailure = { message = it.message },
                                            )
                                            busy = null
                                        }
                                    },
                                    enabled = busy == null,
                                ) { Text(stringResource(R.string.cloud_restore)) }
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.cloud_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            message?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Remote-Steuerung", fontWeight = FontWeight.Bold)
                    Text(
                        "Kanal ${remote.channel} · Patches ${if (remote.patchUpdates) "an" else "aus"} · " +
                            "Mindest-Code ${remote.minVersionCode}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (remote.announcement.isNotBlank()) {
                        Text(remote.announcement, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            LogConsole(height = 220)
            Spacer(Modifier.height(16.dp))
            AppLog.syncFrom(context)
        }
    }
}
