package at.libmatti.mtztool.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Login
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.libmatti.mtztool.AppLog
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.github.GitHubAuth
import at.libmatti.mtztool.github.GoogleAuth
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * In-app sign-in for GitHub (device flow) and Google (PKCE).
 *
 * No external browser is opened: the verification/login page and the Google
 * consent screen are rendered in an embedded WebView.
 */
class GitHubAuthActivity : LocalizedActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_GITHUB = "github"
        const val MODE_GOOGLE = "google"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_GITHUB
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuthScreen(mode = mode, onDone = { finish() })
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScreen(mode: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isGoogle = mode == GitHubAuthActivity.MODE_GOOGLE
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Bereit") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<GitHubAuth.DeviceCode?>(null) }
    var pageUrl by remember { mutableStateOf<String?>(null) }
    var pkce by remember { mutableStateOf<GoogleAuth.Pkce?>(null) }
    var googleConsentUrl by remember { mutableStateOf<String?>(null) }
    var googleBusy by remember { mutableStateOf(false) }

    fun finishWithSuccess() {
        busy = false
        googleBusy = false
        success = true
        status = "Anmeldung erfolgreich"
        (context as? Activity)?.setResult(Activity.RESULT_OK)
    }

    fun startGitHub() {
        if (busy) return
        busy = true
        error = null
        status = "Fordere Gerätecode an …"
        scope.launch {
            GitHubAuth.startDeviceFlow(context).fold(
                onSuccess = { code ->
                    deviceCode = code
                    pageUrl = code.verificationUri
                    status = "Code im Fenster eingeben und bestätigen"
                    // Poll in a separate coroutine so the WebView stays usable.
                    scope.launch {
                        GitHubAuth.pollForToken(context, code) { line -> status = line }.fold(
                            onSuccess = { token ->
                                AppSettings.setGithubToken(context, token)
                                GitHubApi.user(context).fold(
                                    onSuccess = { user ->
                                        AppSettings.setGithubLogin(context, user.login)
                                        AppLog.success("Mit GitHub angemeldet: ${user.login}")
                                    },
                                    onFailure = {
                                        AppLog.warn("Profil konnte nicht geladen werden: ${it.message}")
                                    },
                                )
                                finishWithSuccess()
                            },
                            onFailure = {
                                error = it.message ?: "Anmeldung fehlgeschlagen"
                                busy = false
                            },
                        )
                    }
                },
                onFailure = {
                    error = it.message ?: "Gerätecode fehlgeschlagen"
                    busy = false
                },
            )
        }
    }

    fun startGoogle() {
        if (googleBusy) return
        val clientId = AppSettings.getGoogleClientId(context)
        if (clientId.isBlank()) {
            error = "Keine Google-Client-ID hinterlegt. Trage sie unter " +
                "Einstellungen → GitHub & Konten ein."
            return
        }
        googleBusy = true
        error = null
        val created = GoogleAuth.createPkce()
        pkce = created
        googleConsentUrl = GoogleAuth.authorizationUrl(
            clientId = clientId,
            pkce = created,
            state = UUID.randomUUID().toString(),
        )
        status = "Google-Anmeldung läuft im eingebetteten Fenster"
    }

    fun handleGoogleRedirect(url: String) {
        val code = runCatching { Uri.parse(url).getQueryParameter("code") }.getOrNull()
        val returnedState = runCatching { Uri.parse(url).getQueryParameter("state") }.getOrNull()
        if (returnedState.isNullOrBlank()) {
            error = "Google hat die Anfrage abgelehnt."
            googleBusy = false
            return
        }
        if (code.isNullOrBlank()) {
            error = "Google hat keinen Autorisierungscode geliefert."
            googleBusy = false
            return
        }
        val verifier = pkce?.verifier ?: return
        val clientId = AppSettings.getGoogleClientId(context)
        googleBusy = true
        scope.launch {
            GoogleAuth.exchangeCode(clientId, code, verifier).fold(
                onSuccess = { idToken ->
                    val claims = GoogleAuth.decodeIdToken(idToken)
                    val email = claims["email"].orEmpty()
                    AppSettings.setGoogleAccount(context, email)
                    AppLog.success("Google-Konto verknüpft: ${email.ifBlank { "unbekannt" }}")
                    finishWithSuccess()
                },
                onFailure = {
                    error = it.message ?: "Google-Token-Austausch fehlgeschlagen"
                    googleBusy = false
                },
            )
        }
    }

    // Automatic sign-in: the OAuth client ID is baked into the build unless
    // the developer options store a different one. Opening the sign-in screen
    // starts the device flow immediately, with no manual ID entry.
    LaunchedEffect(Unit) {
        if (!isGoogle && mode == GitHubAuthActivity.MODE_GITHUB &&
            !GitHubApi.isSignedIn(context) &&
            AppSettings.getGithubClientId(context).isNotBlank()
        ) {
            startGitHub()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isGoogle) "Google-Anmeldung" else "GitHub-Anmeldung",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Anmeldung direkt in der App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (isGoogle) {
                            "Google-Konto verknüpfen"
                        } else {
                            "GitHub in der App anmelden"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        if (isGoogle) {
                            "Die Anmeldung wird im eingebetteten Fenster angezeigt. Google " +
                                "verlangt für WebViews eine registrierte Client-ID mit " +
                                "Redirect-URI http://localhost."
                        } else {
                            "Der Gerätecode wird unten angezeigt. Melde dich im eingebetteten " +
                                "Fenster an, damit MTZ Tool Issues, PRs und Themes für dich " +
                                "verwalten kann."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text("Status: $status", style = MaterialTheme.typography.bodyMedium)

            if (error != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        error.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (deviceCode != null && !success) {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Dein Code", style = MaterialTheme.typography.labelMedium)
                        Text(
                            deviceCode?.userCode.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(
                                    android.content.ClipboardManager::class.java
                                )
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "GitHub Code",
                                        deviceCode?.userCode.orEmpty(),
                                    )
                                )
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Kopieren")
                            }
                            OutlinedButton(onClick = { pageUrl = deviceCode?.verificationUri }) {
                                Text("Seite neu laden")
                            }
                        }
                    }
                }
            }

            if (!isGoogle) {
                if (!busy && !success && deviceCode == null) {
                    Button(
                        onClick = ::startGitHub,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("GitHub-Anmeldung starten")
                    }
                }
                if (busy && !success) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(status)
                    }
                }
            } else {
                if (!googleBusy && !success) {
                    Button(onClick = ::startGoogle, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Google-Anmeldung starten")
                    }
                }
                if (googleBusy && !success) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(status)
                    }
                }
            }

            val targetUrl = if (isGoogle) googleConsentUrl else pageUrl
            if (targetUrl != null && !success) {
                val webView = remember(targetUrl) {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString +
                            " MTZTool/2.0"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                url: String?,
                            ): Boolean {
                                if (isGoogle && url != null &&
                                    url.startsWith(GoogleAuth.REDIRECT_URI)
                                ) {
                                    handleGoogleRedirect(url)
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(targetUrl)
                    }
                }
                Card(shape = RoundedCornerShape(18.dp)) {
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                    )
                }
                Text(
                    "Tipp: Falls Google die Anmeldung im WebView blockiert, nutze den " +
                        "Browser-Fallback unten und anschließend den Gerätecode erneut.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (success) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "Angemeldet als " + (
                                AppSettings.getGithubLogin(context).ifBlank {
                                    AppSettings.getGoogleAccount(context)
                                }
                                ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Fertig")
                }
            }

            if (!success) {
                TextButton(onClick = {
                    val fallback = if (isGoogle) {
                        googleConsentUrl
                    } else {
                        deviceCode?.verificationUri
                    } ?: return@TextButton
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(fallback))
                        )
                    }
                }) {
                    Text("Browser-Fallback öffnen")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
