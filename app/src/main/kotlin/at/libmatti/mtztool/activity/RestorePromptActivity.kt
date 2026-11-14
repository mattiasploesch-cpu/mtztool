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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.MainActivity
import at.libmatti.mtztool.R
import at.libmatti.mtztool.github.DeviceCloud
import at.libmatti.mtztool.github.DeviceRecord
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.launch

class RestorePromptActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RestoreScreen(
                        onNewSetup = { startWizard() },
                        onRestored = { startMain() },
                    )
                }
            }
        }
    }

    private fun startWizard() {
        startActivity(Intent(this, SetupWizardActivity::class.java))
        finish()
    }

    private fun startMain() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}

@Composable
private fun RestoreScreen(onNewSetup: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<DeviceRecord>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }

    fun continueToWizard() {
        if (advanced) return
        advanced = true
        onNewSetup()
    }

    LaunchedEffect(Unit) {
        if (!DeviceCloud.isSignedIn(context) || !AppSettings.isDeviceSyncEnabled(context)) {
            continueToWizard()
            return@LaunchedEffect
        }
        val result = DeviceCloud.otherDevices(context)
        devices = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        loading = false
        if (devices.isEmpty() && error == null) continueToWizard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Devices, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.restore_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            stringResource(R.string.restore_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }

        devices.forEach { device ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(device.label.ifBlank { device.model }, fontWeight = FontWeight.Bold)
                    Text(
                        "${device.manufacturer} ${device.model} · Android ${device.android} · v${device.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            restoring = device.deviceId
                            scope.launch {
                                val result = DeviceCloud.restore(context, device.deviceId)
                                restoring = null
                                result.fold(
                                    onSuccess = { onRestored() },
                                    onFailure = { error = it.message },
                                )
                            }
                        },
                        enabled = restoring == null,
                    ) {
                        if (restoring == device.deviceId) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.restore_apply))
                    }
                }
            }
        }

        error?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    stringResource(R.string.restore_failed, it),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (!loading && devices.isNotEmpty()) {
            OutlinedButton(onClick = { continueToWizard() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore_new))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
