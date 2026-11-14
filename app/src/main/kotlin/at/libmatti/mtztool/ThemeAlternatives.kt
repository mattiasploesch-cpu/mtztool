package at.libmatti.mtztool

/**
 * Practical alternatives for components that cannot be imported without a
 * Xiaomi signed .mra. Shown as suggestions in the warning/failure dialogs.
 *
 * The Play Store links are plain https URLs, so no package visibility
 * declaration is required.
 */
object ThemeAlternatives {

    data class Suggestion(
        val name: String,
        val description: String,
        val url: String,
    )

    data class Group(
        val code: String,
        val label: String,
        val note: String? = null,
        val suggestions: List<Suggestion> = emptyList(),
    )

    private fun play(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    fun forCode(code: String): Group = when (code) {
        "icons" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Einzelne Icons gehen ohne Rechte nicht ins System-Theme. " +
                "Du kannst im Hauptfenster aus diesem Theme aber ein installierbares " +
                "Icon-Pack (APK) erstellen und es z. B. mit Smart Launcher nutzen. " +
                "Hinweis: Mit einem alternativen Launcher funktionieren die MIUI-Gesten nicht mehr.",
            suggestions = listOf(
                Suggestion(
                    "Shortcut Maker (empfohlen, mit Gesten)",
                    "Einzelne App-Verknüpfungen mit eigenem Icon anlegen. Bleibt beim " +
                        "MIUI-Launcher, daher funktionieren die Gesten weiter.",
                    play("com.riteshsahu.ShortcutMaker"),
                ),
                Suggestion(
                    "Icon Pack Studio",
                    "Eigenes Icon-Paket aus Vorlagen oder Bildern bauen und im Launcher anwenden.",
                    play("ginlemon.iconpackstudio"),
                ),
                Suggestion(
                    "Smart Launcher",
                    "Alternativer Startbildschirm mit Icon-Pack-Unterstützung. " +
                        "Hinweis: Die MIUI-Gesten funktionieren damit nicht mehr.",
                    play("ginlemon.flowerfree"),
                ),
            ),
        )

        "statusbar" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Eine echte Statusleisten-Änderung ist ohne Root nicht möglich. " +
                "Einzelne Symbole lassen sich in den Systemeinstellungen ausblenden.",
            suggestions = listOf(
                Suggestion(
                    "SystemUI Tuner",
                    "Blendet Statusleisten-Symbole aus (benötigt einmalig eine ADB-Freigabe).",
                    play("com.zacharee1.systemuituner"),
                ),
            ),
        )

        "fonts" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "HyperOS bringt eigene Schriftarten mit: Einstellungen → Anzeige → Schriftart.",
            suggestions = listOf(
                Suggestion(
                    "zFont 3",
                    "Installiert Schriftarten über den Theme-Mechanismus von MIUI/HyperOS.",
                    play("com.htetznaing.zfont2"),
                ),
                Suggestion(
                    "iFont",
                    "Weitere Schriftarten mit Theme-Installation für Xiaomi-Geräte.",
                    play("com.kapp.ifont"),
                ),
            ),
        )

        "lockstyle" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Den Sperrbildschirm-Stil selbst kann nur der Theme Manager ersetzen. " +
                "Uhr-Stil und Hintergrund gibt es in den Systemeinstellungen unter „Sperrbildschirm“.",
            suggestions = listOf(
                Suggestion(
                    "KLCK Kustom Lock Screen",
                    "Baut einen eigenen Sperrbildschirm als Overlay – unabhängig vom System-Theme.",
                    play("org.kustom.lockscreen"),
                ),
            ),
        )

        "launcher" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Der Startbildschirm des Themes wird nicht übernommen. " +
                "Ein alternativer Launcher bringt Raster, Icons und Effekte mit. " +
                "Hinweis: Die MIUI-Gesten funktionieren mit einem alternativen Launcher nicht mehr.",
            suggestions = listOf(
                Suggestion(
                    "Smart Launcher",
                    "Anpassbarer Startbildschirm inklusive Icon-Paketen. " +
                        "Hinweis: Die MIUI-Gesten funktionieren damit nicht mehr.",
                    play("ginlemon.flowerfree"),
                ),
            ),
        )

        "clock_2x4" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Das Uhr-Widget des Themes bleibt geschützt. Eigene Widgets lassen sich frei bauen.",
            suggestions = listOf(
                Suggestion(
                    "KWGT Kustom Widget Maker",
                    "Erstellt eigene Uhr- und Info-Widgets in beliebigem Design.",
                    play("org.kustom.widget"),
                ),
            ),
        )

        "aod" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Always-On-Display lässt sich in den Systemeinstellungen anpassen " +
                "(Einstellungen → Sperrbildschirm → Always-On-Display).",
        )

        "bootanimation" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Eine eigene Boot-Animation ist ohne Root (Magisk/KernelSU) nicht möglich.",
        )

        "contact" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Das Theming der System-Kontakte ist ohne Rechte nicht möglich. " +
                "Als Ersatz-App funktioniert Google Kontakte unabhängig vom Theme.",
            suggestions = listOf(
                Suggestion(
                    "Google Kontakte",
                    "Eigenständige Kontakte-App, unabhängig vom Xiaomi-Theme.",
                    play("com.google.android.contacts"),
                ),
            ),
        )

        "mms" -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Das Theming der Nachrichten-App ist ohne Rechte nicht möglich. " +
                "Als Ersatz-App funktioniert Google Messages unabhängig vom Theme.",
            suggestions = listOf(
                Suggestion(
                    "Google Messages",
                    "Eigenständige SMS-/RCS-App, unabhängig vom Xiaomi-Theme.",
                    play("com.google.android.apps.messaging"),
                ),
            ),
        )

        "com.android.settings",
        "com.miui.securitycenter",
        "framework",
        "framework-miui-res",
        "miui.systemui.plugin",
        "miwallpaper",
        -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Tief im System verankert – dafür gibt es ohne Root keine sichere App-Alternative.",
        )

        else -> Group(
            code = code,
            label = ThemeComponents.displayName(code),
            note = "Für diesen Bestandteil gibt es ohne signierte Xiaomi-Rechte keine Alternative.",
        )
    }

    fun forCodes(codes: List<String>): List<Group> = codes.distinct().map(::forCode)
}
