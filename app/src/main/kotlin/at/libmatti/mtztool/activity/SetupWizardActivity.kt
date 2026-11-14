package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.github.GitHubApi
import at.libmatti.mtztool.ui.theme.AppThemes
import at.libmatti.mtztool.ui.theme.MTZToolTheme
import at.libmatti.mtztool.ui.theme.ThemeFamily
import at.libmatti.mtztool.ui.theme.ThemeMode
import java.util.Locale

class SetupWizardActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SetupWizardScreen(onFinished = {
                        AppSettings.setSetupCompleted(this, true)
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        finish()
                    })
                }
            }
        }
    }
}

private data class WizardStep(val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val steps = listOf(
    WizardStep(R.string.setup_step_theme, Icons.Default.Palette),
    WizardStep(R.string.setup_step_language, Icons.Default.Translate),
    WizardStep(R.string.setup_step_access, Icons.Default.Security),
    WizardStep(R.string.setup_step_account, Icons.Default.Cloud),
)

@Composable
private fun SetupWizardScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    var family by remember { mutableStateOf(AppSettings.getThemeFamily(context)) }
    var mode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
    var dynamicColor by remember { mutableStateOf(AppSettings.isDynamicColorEnabled(context)) }
    var language by remember { mutableStateOf(AppSettings.getLanguage(context)) }
    var provider by remember { mutableStateOf(AppSettings.getPrivilegeProvider(context)) }
    var signedIn by remember { mutableStateOf(GitHubApi.isSignedIn(context)) }
    var login by remember { mutableStateOf(AppSettings.getGithubLogin(context)) }
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        signedIn = GitHubApi.isSignedIn(context)
        login = AppSettings.getGithubLogin(context)
    }

    fun applyTheme(nextFamily: ThemeFamily = family, nextMode: ThemeMode = mode) {
        family = nextFamily
        mode = nextMode
        AppSettings.setThemeFamily(context, nextFamily)
        AppSettings.setThemeMode(context, nextMode)
    }

    fun finish() {
        AppSettings.setThemeFamily(context, family)
        AppSettings.setThemeMode(context, mode)
        AppSettings.setDynamicColorEnabled(context, dynamicColor)
        AppSettings.setLanguage(context, language)
        AppSettings.setPrivilegeProvider(context, provider)
        ShizukuHelper.selectedProvider = provider
        AppLog.info("Setup abgeschlossen: ${family.label}, ${provider.name}.")
        onFinished()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, wizardStep ->
                    StepDot(
                        index = index,
                        selected = index == step,
                        done = index < step,
                        title = stringResource(wizardStep.titleRes),
                        icon = wizardStep.icon,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (step + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> {
                    item {
                        OnboardingCard(
                            title = stringResource(R.string.setup_theme_title),
                            body = stringResource(R.string.setup_theme_body),
                        )
                    }
                    item {
                        ModeSelector(selected = mode, onSelected = { applyTheme(nextMode = it) })
                    }
                    item {
                        Text(
                            text = stringResource(R.string.setup_theme_family),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(ThemeFamily.entries) { entry ->
                        ThemeFamilyCard(
                            family = entry,
                            selected = entry == family,
                            dark = mode == ThemeMode.DARK || mode == ThemeMode.AMOLED,
                            onClick = { applyTheme(nextFamily = entry) },
                        )
                    }
                    item {
                        SetSwitch(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_desc),
                            checked = dynamicColor,
                            onCheckedChange = {
                                dynamicColor = it
                                AppSettings.setDynamicColorEnabled(context, it)
                            },
                        )
                    }
                }

                1 -> {
                    item {
                        OnboardingCard(
                            title = stringResource(R.string.setup_language_title),
                            body = stringResource(R.string.setup_language_body),
                        )
                    }
                    items(AppSettings.supportedLanguages) { tag ->
                        LanguageRow(
                            tag = tag,
                            selected = tag == language,
                            onClick = {
                                language = tag
                                AppSettings.setLanguage(context, tag)
                            },
                        )
                    }
                }

                2 -> {
                    item {
                        OnboardingCard(
                            title = stringResource(R.string.setup_access_title),
                            body = stringResource(R.string.setup_access_body),
                        )
                    }
                    items(PrivilegeProvider.entries) { entry ->
                        ProviderRow(
                            provider = entry,
                            selected = entry == provider,
                            onClick = { provider = entry },
                        )
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.setup_access_note),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                3 -> {
                    item {
                        OnboardingCard(
                            title = stringResource(R.string.setup_account_title),
                            body = stringResource(R.string.setup_account_body),
                        )
                    }
                    item {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    if (signedIn) {
                                        stringResource(R.string.setup_account_signed_in, login)
                                    } else {
                                        stringResource(R.string.setup_account_signed_out)
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = {
                                        authLauncher.launch(
                                            Intent(context, GitHubAuthActivity::class.java)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(
                                            if (signedIn) R.string.setup_account_switch
                                            else R.string.setup_account_sign_in
                                        )
                                    )
                                }
                                if (signedIn) {
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(context, RestorePromptActivity::class.java)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.setup_account_restore))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OnboardingCard(
                            title = stringResource(R.string.setup_ready_title),
                            body = stringResource(R.string.setup_ready_body),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) {
                    Text(stringResource(R.string.setup_back))
                }
            } else {
                TextButton(onClick = {
                    AppSettings.setSetupCompleted(context, true)
                    onFinished()
                }) {
                    Text(stringResource(R.string.setup_skip))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (step < steps.lastIndex) step += 1 else finish()
                },
            ) {
                Text(
                    stringResource(
                        if (step < steps.lastIndex) R.string.setup_next
                        else R.string.setup_finish
                    )
                )
                Icon(
                    if (step < steps.lastIndex) Icons.Default.ChevronRight else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    index: Int,
    selected: Boolean,
    done: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        done -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text = "$index. $title",
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun OnboardingCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ModeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.setup_mode_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf(
                ThemeMode.SYSTEM to R.string.setup_mode_system,
                ThemeMode.LIGHT to R.string.setup_mode_light,
                ThemeMode.DARK to R.string.setup_mode_dark,
                ThemeMode.AMOLED to R.string.setup_mode_amoled,
            )
            modes.forEach { (mode, label) ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun ThemeFamilyCard(
    family: ThemeFamily,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val preview = AppThemes.previewColors(family, dark)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp),
                    )
                } else Modifier
            ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(-6.dp)) {
                listOf(preview.primary, preview.secondary, preview.tertiary).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(family.label, fontWeight = FontWeight.Bold)
                Text(
                    text = if (dark) "Dark" else "Light",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(tag: String, selected: Boolean, onClick: () -> Unit) {
    val label = if (tag.isEmpty()) {
        stringResource(R.string.language_system)
    } else {
        val locale = Locale.forLanguageTag(tag)
        locale.getDisplayName(locale).replaceFirstChar { it.uppercase() } + " · $tag"
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: PrivilegeProvider,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(provider.titleRes),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(provider.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SetSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
