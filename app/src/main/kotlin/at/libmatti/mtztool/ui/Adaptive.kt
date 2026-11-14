package at.libmatti.mtztool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import at.libmatti.mtztool.AppSettings
import at.libmatti.mtztool.device.DeviceProfile
import at.libmatti.mtztool.device.DeviceProfiles

/**
 * Current form factor and window size, recomputed when the configuration
 * changes (rotation, split screen, unfolded hinge) or the developer override
 * is edited.
 */
@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val revision = AppSettings.themeRevision
    return remember(
        context,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation,
        configuration.uiMode,
        revision,
    ) {
        DeviceProfiles.detect(context, configuration)
    }
}

/** Centers scrolling content on wide windows without stretching text lines. */
@Composable
fun AdaptiveContent(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val maxWidth = if (profile.contentMaxWidthDp == Int.MAX_VALUE) {
        Modifier
    } else {
        Modifier.widthIn(max = profile.contentMaxWidthDp.dp)
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = maxWidth.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Lays out [items] in rows whose width follows the current window tier.
 * Used for the dashboard action tiles and the tool shortcuts.
 */
@Composable
fun <T> AdaptiveGrid(
    items: List<T>,
    columns: Int,
    horizontalSpacing: Int = 12,
    verticalSpacing: Int = 12,
    item: @Composable (T) -> Unit,
) {
    val safeColumns = columns.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp)) {
        items.chunked(safeColumns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalSpacing.dp)) {
                rowItems.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) { item(entry) }
                }
                repeat(safeColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Two-pane layout for expanded windows (tablet, foldable unfolded, desktop,
 * TV). Falls back to a single scrolling column on phones.
 */
@Composable
fun AdaptiveTwoPane(
    profile: DeviceProfile,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    if (profile.isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                primary()
            }
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                secondary()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            primary()
            secondary()
        }
    }
}
