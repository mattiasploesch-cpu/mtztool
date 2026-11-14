package at.libmatti.mtztool.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import at.libmatti.mtztool.AppSettings

/**
 * Physical/usage class of the device the app is currently running on.
 *
 * The detection is intentionally conservative: it only uses information the
 * platform exposes without extra permissions. Developer options can override
 * the result to preview a layout on another form factor.
 */
enum class DeviceClass(val label: String) {
    AUTO("Automatisch"),
    PHONE("Smartphone"),
    TABLET("Tablet"),
    FOLDABLE("Faltbar"),
    TV("TV"),
    DESKTOP("Desktop"),
    CAR("Auto"),
    WATCH("Uhr");

    companion object {
        fun fromStorage(value: String?): DeviceClass =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** Compose window size class equivalent (Material 3 breakpoints). */
enum class WindowTier(val label: String) {
    COMPACT("Kompakt"),
    MEDIUM("Mittel"),
    EXPANDED("Breit"),
}

data class DeviceProfile(
    val effectiveClass: DeviceClass,
    val detectedClass: DeviceClass,
    val tier: WindowTier,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val portrait: Boolean,
    val adaptiveLayout: Boolean,
) {
    val isCompact: Boolean get() = tier == WindowTier.COMPACT
    val isExpanded: Boolean get() = tier == WindowTier.EXPANDED

    /** Number of tiles/columns that are comfortable for this window. */
    val columns: Int
        get() = when {
            !adaptiveLayout -> 2
            tier == WindowTier.EXPANDED -> 4
            tier == WindowTier.MEDIUM -> 3
            else -> 2
        }

    /** Maximum content width so text lines stay readable on tablets/TV. */
    val contentMaxWidthDp: Int
        get() = when {
            !adaptiveLayout -> Int.MAX_VALUE
            effectiveClass == DeviceClass.TV || effectiveClass == DeviceClass.DESKTOP -> 1180
            tier == WindowTier.EXPANDED -> 1000
            tier == WindowTier.MEDIUM -> 820
            else -> Int.MAX_VALUE
        }

    val horizontalPaddingDp: Int
        get() = when {
            !adaptiveLayout -> 18
            tier == WindowTier.EXPANDED -> 32
            tier == WindowTier.MEDIUM -> 24
            else -> 16
        }

    val description: String
        get() = "${effectiveClass.label} · ${tier.label} · ${widthDp}×${heightDp} dp"
}

object DeviceProfiles {

    fun detect(context: Context): DeviceProfile =
        detect(context, context.resources.configuration)

    fun detect(context: Context, configuration: Configuration): DeviceProfile {
        val detected = detectClass(context, configuration)
        val override = AppSettings.getDeviceClassOverride(context)
        val effective = if (override == DeviceClass.AUTO) detected else override
        val adaptive = AppSettings.isAdaptiveLayoutEnabled(context)

        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        val detectedTier = when {
            widthDp < 600 -> WindowTier.COMPACT
            widthDp < 840 -> WindowTier.MEDIUM
            else -> WindowTier.EXPANDED
        }
        // A manual override simulates the usual layout of that form factor so
        // the preview is actually useful on the device that runs the app.
        val tier = if (override == DeviceClass.AUTO) {
            detectedTier
        } else {
            when (effective) {
                DeviceClass.PHONE, DeviceClass.WATCH -> WindowTier.COMPACT
                DeviceClass.TABLET, DeviceClass.FOLDABLE, DeviceClass.CAR -> WindowTier.MEDIUM
                DeviceClass.TV, DeviceClass.DESKTOP -> WindowTier.EXPANDED
                DeviceClass.AUTO -> detectedTier
            }
        }

        return DeviceProfile(
            effectiveClass = effective,
            detectedClass = detected,
            tier = tier,
            widthDp = widthDp,
            heightDp = heightDp,
            smallestWidthDp = configuration.smallestScreenWidthDp,
            portrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
            adaptiveLayout = adaptive,
        )
    }

    fun detectClass(context: Context, configuration: Configuration): DeviceClass {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        when (uiMode?.currentModeType) {
            Configuration.UI_MODE_TYPE_TELEVISION -> return DeviceClass.TV
            Configuration.UI_MODE_TYPE_CAR -> return DeviceClass.CAR
            Configuration.UI_MODE_TYPE_WATCH -> return DeviceClass.WATCH
            Configuration.UI_MODE_TYPE_DESK -> return DeviceClass.DESKTOP
        }

        val packageManager = context.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PC)) {
            return DeviceClass.DESKTOP
        }
        // Hinge sensors are only exposed by foldables. Keep the feature name
        // as a literal so the check also works on API levels below 30.
        if (packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
            packageManager.hasSystemFeature("android.hardware.sensor.hinge")
        ) {
            return DeviceClass.FOLDABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            configuration.smallestScreenWidthDp >= 600
        ) {
            return DeviceClass.TABLET
        }
        return DeviceClass.PHONE
    }
}
