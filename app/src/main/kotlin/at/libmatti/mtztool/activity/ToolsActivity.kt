package at.libmatti.mtztool.activity

import at.libmatti.mtztool.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.ui.theme.MTZToolTheme

private data class ToolEntry(
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    val activity: Class<*>
)

private val tools = listOf(
    ToolEntry(
        R.string.tool_inspector,
        R.string.tool_inspector_desc,
        Icons.Default.Search,
        ThemeInspectorActivity::class.java
    ),
    ToolEntry(
        R.string.tool_compare,
        R.string.tool_compare_desc,
        Icons.Default.CompareArrows,
        ThemeCompareActivity::class.java
    ),
    ToolEntry(
        R.string.tool_mra_checker,
        R.string.tool_mra_checker_desc,
        Icons.Default.VerifiedUser,
        MraCheckerActivity::class.java
    ),
    ToolEntry(
        R.string.tool_extractor,
        R.string.tool_extractor_desc,
        Icons.Default.FileDownload,
        ThemeExtractorActivity::class.java
    ),
    ToolEntry(
        R.string.tool_previews,
        R.string.tool_previews_desc,
        Icons.Default.PhotoLibrary,
        ThemePreviewActivity::class.java
    ),
    ToolEntry(
        R.string.patcher_title,
        R.string.patcher_intro,
        Icons.Default.Edit,
        ThemePatchActivity::class.java
    ),
    ToolEntry(
        R.string.icon_pack_title,
        R.string.icon_pack_desc,
        Icons.Default.Apps,
        IconPackActivity::class.java
    ),
    ToolEntry(
        R.string.tool_studio,
        R.string.tool_studio_desc,
        Icons.Default.Palette,
        ThemeStudioActivity::class.java
    ),
    ToolEntry(
        R.string.tool_store,
        R.string.tool_store_desc,
        Icons.Default.Store,
        ThemeBrowserActivity::class.java
    ),
    ToolEntry(
        R.string.tool_rights,
        R.string.tool_rights_desc,
        Icons.Default.VerifiedUser,
        ThemeRightsActivity::class.java
    )
)

class ToolsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MTZToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ToolsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ToolScaffold(
        title = stringResource(R.string.tools_name),
        onBack = onBack
    ) {
        Text(
            text = stringResource(R.string.tools_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        tools.forEach { tool ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, tool.activity))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(tool.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(tool.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
