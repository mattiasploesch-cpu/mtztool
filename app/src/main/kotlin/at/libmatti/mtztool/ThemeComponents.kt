package at.libmatti.mtztool

/**
 * Mapping between the classic .mtz layout and the HyperOS .data library layout,
 * plus the list of resource codes that do not require a DRM rights file.
 */
object ThemeComponents {

    /** .mtz top level entry -> HyperOS resourceCode. */
    val MTZ_MAP: Map<String, String> = linkedMapOf(
        "icons" to "icons",
        "lockscreen" to "lockstyle",                 // .mtz/lockscreen == .data/lockstyle
        "com.android.systemui" to "statusbar",
        "com.miui.home" to "launcher",
        "com.android.contacts" to "contact",
        "com.android.mms" to "mms",
        "com.android.settings" to "com.android.settings",
        "com.miui.fliphome" to "com.miui.fliphome",
        "com.miui.securitycenter" to "com.miui.securitycenter",
        "framework-res" to "framework",
        "framework-miui-res" to "framework-miui-res",
        "miui.systemui.plugin" to "miui.systemui.plugin",
        "miwallpaper" to "miwallpaper",
        "aod" to "aod",
        "clock_2x4" to "clock_2x4",
    )

    /**
     * Resource codes that are exempt from the DRM rights check
     * (miui.app.constants.ThemeResourceConstants.kB).
     */
    val EXEMPT: Set<String> = setOf(
        "audioeffect",
        "wallpaper",
        "lockscreen",
        "ringtone",
        "alarm",
        "notification",
        "bootaudio",
    )

    fun isExempt(code: String): Boolean = code in EXEMPT

    /** German label for the warning dialog. */
    fun displayName(code: String): String = when (code) {
        "wallpaper" -> "Hintergrundbild"
        "lockscreen" -> "Sperrbildschirm"
        "lockstyle" -> "Sperrbildschirm-Stil"
        "icons" -> "Symbole"
        "statusbar" -> "Statusleiste"
        "launcher" -> "Startbildschirm"
        "contact" -> "Kontakte"
        "mms" -> "Nachrichten"
        "com.android.settings" -> "Einstellungen"
        "com.miui.securitycenter" -> "Sicherheit"
        "fonts" -> "Schriftart"
        "framework" -> "System-Framework"
        "framework-miui-res" -> "MIUI-Framework"
        "aod" -> "Always-On-Display"
        "clock_2x4" -> "Uhr-Widget"
        "miui.systemui.plugin" -> "System-UI-Plugin"
        "miwallpaper" -> "MIUI-Hintergrund"
        else -> code
    }
}
