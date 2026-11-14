package at.libmatti.mtztool.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import at.libmatti.mtztool.AppSettings

/**
 * Selectable app color families. Every family ships with a light and a dark
 * variant so the theme mode can be changed independently of the family.
 */
enum class ThemeFamily(val label: String) {
    MATERIAL("Material 3"),
    CATPPUCCIN("Catppuccin"),
    GRUVBOT("Gruvbox"),
    NORD("Nord"),
    DRACULA("Dracula"),
    TOKYO_NIGHT("Tokyo Night");

    companion object {
        fun fromStorage(value: String?): ThemeFamily =
            entries.firstOrNull { it.name == value } ?: MATERIAL
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class ThemePreviewColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val onBackground: Color,
)

object AppThemes {

    fun previewColors(family: ThemeFamily, dark: Boolean = true): ThemePreviewColors {
        val scheme = scheme(family, dark, amoled = false)
        return ThemePreviewColors(
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            background = scheme.background,
            onBackground = scheme.onBackground,
        )
    }

    fun scheme(
        family: ThemeFamily,
        dark: Boolean,
        amoled: Boolean = false,
    ) = when (family) {
        ThemeFamily.MATERIAL -> if (dark) materialDark(amoled) else materialLight()
        ThemeFamily.CATPPUCCIN -> if (dark) catppuccinDark(amoled) else catppuccinLight()
        ThemeFamily.GRUVBOT -> if (dark) gruvboxDark(amoled) else gruvboxLight()
        ThemeFamily.NORD -> if (dark) nordDark(amoled) else nordLight()
        ThemeFamily.DRACULA -> if (dark) draculaDark(amoled) else draculaLight()
        ThemeFamily.TOKYO_NIGHT -> if (dark) tokyoNightDark(amoled) else tokyoNightLight()
    }

    /** Pure black surfaces for OLED displays, keeping the family accent colors. */
    private fun applyAmoled(scheme: androidx.compose.material3.ColorScheme) =
        scheme.copy(
            background = Color.Black,
            onBackground = scheme.onBackground,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF070707),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1B1B1B),
        )

    // ── Material 3 ────────────────────────────────────────────

    private fun materialLight() = lightColorScheme(
        primary = Color(0xFF415F91),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3E),
        secondary = Color(0xFF565F71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF131C2B),
        tertiary = Color(0xFF705575),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFAD8FD),
        onTertiaryContainer = Color(0xFF28132E),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        inverseSurface = Color(0xFF2E3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        inversePrimary = Color(0xFFAAC7FF),
        surfaceDim = Color(0xFFD9D9E0),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF3F3FA),
        surfaceContainer = Color(0xFFEDEDF4),
        surfaceContainerHigh = Color(0xFFE7E8EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
    )

    private fun materialDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFFAAC7FF),
            onPrimary = Color(0xFF0A305F),
            primaryContainer = Color(0xFF284777),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFFBEC6DC),
            onSecondary = Color(0xFF283141),
            secondaryContainer = Color(0xFF3E4759),
            onSecondaryContainer = Color(0xFFDAE2F9),
            tertiary = Color(0xFFDDBCE0),
            onTertiary = Color(0xFF3F2844),
            tertiaryContainer = Color(0xFF573E5C),
            onTertiaryContainer = Color(0xFFFAD8FD),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF111318),
            onBackground = Color(0xFFE2E2E9),
            surface = Color(0xFF111318),
            onSurface = Color(0xFFE2E2E9),
            surfaceVariant = Color(0xFF44474E),
            onSurfaceVariant = Color(0xFFC4C6D0),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44474E),
            inverseSurface = Color(0xFFE2E2E9),
            inverseOnSurface = Color(0xFF2E3036),
            inversePrimary = Color(0xFF415F91),
            surfaceDim = Color(0xFF111318),
            surfaceBright = Color(0xFF37393E),
            surfaceContainerLowest = Color(0xFF0C0E13),
            surfaceContainerLow = Color(0xFF191C20),
            surfaceContainer = Color(0xFF1D2024),
            surfaceContainerHigh = Color(0xFF282A2F),
            surfaceContainerHighest = Color(0xFF33353A),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }

    // ── Catppuccin ────────────────────────────────────────────

    private fun catppuccinLight() = lightColorScheme(
        primary = Color(0xFF8839EF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9D5FF),
        onPrimaryContainer = Color(0xFF2B0A50),
        secondary = Color(0xFF1E66F5),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD6E4FF),
        onSecondaryContainer = Color(0xFF001B4D),
        tertiary = Color(0xFFEA76CB),
        onTertiary = Color(0xFF4A0A38),
        tertiaryContainer = Color(0xFFFFD8F2),
        onTertiaryContainer = Color(0xFF3B072E),
        error = Color(0xFFD20F39),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFEFF1F5),
        onBackground = Color(0xFF4C4F69),
        surface = Color(0xFFEFF1F5),
        onSurface = Color(0xFF4C4F69),
        surfaceVariant = Color(0xFFCCD0DA),
        onSurfaceVariant = Color(0xFF6C6F85),
        outline = Color(0xFF9CA0B0),
        outlineVariant = Color(0xFFBCC0CC),
        inverseSurface = Color(0xFF4C4F69),
        inverseOnSurface = Color(0xFFEFF1F5),
        inversePrimary = Color(0xFFCBA6F7),
        surfaceDim = Color(0xFFDCE0E8),
        surfaceBright = Color(0xFFEFF1F5),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFE6E9EF),
        surfaceContainer = Color(0xFFDCE0E8),
        surfaceContainerHigh = Color(0xFFD3D6E0),
        surfaceContainerHighest = Color(0xFFC9CCD6),
    )

    private fun catppuccinDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFFCBA6F7),
            onPrimary = Color(0xFF2A0A50),
            primaryContainer = Color(0xFF4A2A7A),
            onPrimaryContainer = Color(0xFFE9D5FF),
            secondary = Color(0xFF89B4FA),
            onSecondary = Color(0xFF0A2A5F),
            secondaryContainer = Color(0xFF284777),
            onSecondaryContainer = Color(0xFFD6E3FF),
            tertiary = Color(0xFFF5C2E7),
            onTertiary = Color(0xFF4A0A38),
            tertiaryContainer = Color(0xFF6E2857),
            onTertiaryContainer = Color(0xFFFFD8F2),
            error = Color(0xFFF38BA8),
            onError = Color(0xFF410002),
            errorContainer = Color(0xFF6E2030),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF1E1E2E),
            onBackground = Color(0xFFCDD6F4),
            surface = Color(0xFF1E1E2E),
            onSurface = Color(0xFFCDD6F4),
            surfaceVariant = Color(0xFF45475A),
            onSurfaceVariant = Color(0xFFA6ADC8),
            outline = Color(0xFF6C7086),
            outlineVariant = Color(0xFF45475A),
            inverseSurface = Color(0xFFCDD6F4),
            inverseOnSurface = Color(0xFF1E1E2E),
            inversePrimary = Color(0xFF8839EF),
            surfaceDim = Color(0xFF181825),
            surfaceBright = Color(0xFF313244),
            surfaceContainerLowest = Color(0xFF11111B),
            surfaceContainerLow = Color(0xFF181825),
            surfaceContainer = Color(0xFF1E1E2E),
            surfaceContainerHigh = Color(0xFF262637),
            surfaceContainerHighest = Color(0xFF313244),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }

    // ── Gruvbox ───────────────────────────────────────────────

    private fun gruvboxLight() = lightColorScheme(
        primary = Color(0xFF8F3F71),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF2D9EA),
        onPrimaryContainer = Color(0xFF3B0A2E),
        secondary = Color(0xFF076678),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCDEAF2),
        onSecondaryContainer = Color(0xFF003544),
        tertiary = Color(0xFFD65D0E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCC4),
        onTertiaryContainer = Color(0xFF3B1700),
        error = Color(0xFF9D0006),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF1C7),
        onBackground = Color(0xFF3C3836),
        surface = Color(0xFFFBF1C7),
        onSurface = Color(0xFF3C3836),
        surfaceVariant = Color(0xFFEBDBB2),
        onSurfaceVariant = Color(0xFF504945),
        outline = Color(0xFF928374),
        outlineVariant = Color(0xFFD5C4A1),
        inverseSurface = Color(0xFF3C3836),
        inverseOnSurface = Color(0xFFFBF1C7),
        inversePrimary = Color(0xFFD3869B),
        surfaceDim = Color(0xFFEBDBB2),
        surfaceBright = Color(0xFFFBF1C7),
        surfaceContainerLowest = Color(0xFFFFF9E5),
        surfaceContainerLow = Color(0xFFF5EAC4),
        surfaceContainer = Color(0xFFF0E2B6),
        surfaceContainerHigh = Color(0xFFE9D9A9),
        surfaceContainerHighest = Color(0xFFE2D09C),
    )

    private fun gruvboxDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFFD3869B),
            onPrimary = Color(0xFF3B0A2E),
            primaryContainer = Color(0xFF5A2A4A),
            onPrimaryContainer = Color(0xFFF2D9EA),
            secondary = Color(0xFF83A598),
            onSecondary = Color(0xFF003544),
            secondaryContainer = Color(0xFF1E4A44),
            onSecondaryContainer = Color(0xFFCDEAF2),
            tertiary = Color(0xFFFE8019),
            onTertiary = Color(0xFF3B1700),
            tertiaryContainer = Color(0xFF6E3A00),
            onTertiaryContainer = Color(0xFFFFDCC4),
            error = Color(0xFFFB4934),
            onError = Color(0xFF410002),
            errorContainer = Color(0xFF6E1A10),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF282828),
            onBackground = Color(0xFFEBDBB2),
            surface = Color(0xFF282828),
            onSurface = Color(0xFFEBDBB2),
            surfaceVariant = Color(0xFF504945),
            onSurfaceVariant = Color(0xFFD5C4A1),
            outline = Color(0xFF928374),
            outlineVariant = Color(0xFF504945),
            inverseSurface = Color(0xFFEBDBB2),
            inverseOnSurface = Color(0xFF282828),
            inversePrimary = Color(0xFF8F3F71),
            surfaceDim = Color(0xFF1D1D1D),
            surfaceBright = Color(0xFF3C3836),
            surfaceContainerLowest = Color(0xFF1D1D1D),
            surfaceContainerLow = Color(0xFF282828),
            surfaceContainer = Color(0xFF32302F),
            surfaceContainerHigh = Color(0xFF3C3836),
            surfaceContainerHighest = Color(0xFF504945),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }

    // ── Nord ──────────────────────────────────────────────────

    private fun nordLight() = lightColorScheme(
        primary = Color(0xFF5E81AC),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E3F3),
        onPrimaryContainer = Color(0xFF0A2748),
        secondary = Color(0xFF4C566A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE1E8),
        onSecondaryContainer = Color(0xFF1B2433),
        tertiary = Color(0xFFB48EAD),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0DCEB),
        onTertiaryContainer = Color(0xFF3B1737),
        error = Color(0xFFBF616A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFECEFF4),
        onBackground = Color(0xFF2E3440),
        surface = Color(0xFFECEFF4),
        onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFE5E9F0),
        onSurfaceVariant = Color(0xFF4C566A),
        outline = Color(0xFF7B88A1),
        outlineVariant = Color(0xFFD8DEE9),
        inverseSurface = Color(0xFF2E3440),
        inverseOnSurface = Color(0xFFECEFF4),
        inversePrimary = Color(0xFF8FBCBB),
        surfaceDim = Color(0xFFE5E9F0),
        surfaceBright = Color(0xFFF4F6FA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF2F4F8),
        surfaceContainer = Color(0xFFECEFF4),
        surfaceContainerHigh = Color(0xFFE5E9F0),
        surfaceContainerHighest = Color(0xFFD8DEE9),
    )

    private fun nordDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFF88C0D0),
            onPrimary = Color(0xFF00363F),
            primaryContainer = Color(0xFF00505F),
            onPrimaryContainer = Color(0xFFA8E8F5),
            secondary = Color(0xFF81A1C1),
            onSecondary = Color(0xFF0A2748),
            secondaryContainer = Color(0xFF294A6B),
            onSecondaryContainer = Color(0xFFD8E3F3),
            tertiary = Color(0xFFB48EAD),
            onTertiary = Color(0xFF3B1737),
            tertiaryContainer = Color(0xFF5A2A54),
            onTertiaryContainer = Color(0xFFF0DCEB),
            error = Color(0xFFBF616A),
            onError = Color(0xFF410002),
            errorContainer = Color(0xFF6E2030),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF2E3440),
            onBackground = Color(0xFFECEFF4),
            surface = Color(0xFF2E3440),
            onSurface = Color(0xFFECEFF4),
            surfaceVariant = Color(0xFF434C5E),
            onSurfaceVariant = Color(0xFFD8DEE9),
            outline = Color(0xFF7B88A1),
            outlineVariant = Color(0xFF434C5E),
            inverseSurface = Color(0xFFECEFF4),
            inverseOnSurface = Color(0xFF2E3440),
            inversePrimary = Color(0xFF5E81AC),
            surfaceDim = Color(0xFF272C36),
            surfaceBright = Color(0xFF3B4252),
            surfaceContainerLowest = Color(0xFF242933),
            surfaceContainerLow = Color(0xFF2E3440),
            surfaceContainer = Color(0xFF363C48),
            surfaceContainerHigh = Color(0xFF3B4252),
            surfaceContainerHighest = Color(0xFF434C5E),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }

    // ── Dracula / Alucard ─────────────────────────────────────

    private fun draculaLight() = lightColorScheme(
        primary = Color(0xFF644AC9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5DEFF),
        onPrimaryContainer = Color(0xFF1F0A5F),
        secondary = Color(0xFF036A96),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC4E7FF),
        onSecondaryContainer = Color(0xFF001E30),
        tertiary = Color(0xFFA3144D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E3),
        onTertiaryContainer = Color(0xFF3F0017),
        error = Color(0xFFCB3A2A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFBEB),
        onBackground = Color(0xFF1F1F1F),
        surface = Color(0xFFFFFBEB),
        onSurface = Color(0xFF1F1F1F),
        surfaceVariant = Color(0xFFCFCFDE),
        onSurfaceVariant = Color(0xFF6C664B),
        outline = Color(0xFF918C74),
        outlineVariant = Color(0xFFD8D2BC),
        inverseSurface = Color(0xFF1F1F1F),
        inverseOnSurface = Color(0xFFFFFBEB),
        inversePrimary = Color(0xFFBD93F9),
        surfaceDim = Color(0xFFF4EFDF),
        surfaceBright = Color(0xFFFFFBEB),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAF5E5),
        surfaceContainer = Color(0xFFF4EFDF),
        surfaceContainerHigh = Color(0xFFEEE9D9),
        surfaceContainerHighest = Color(0xFFE8E3D3),
    )

    private fun draculaDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFFBD93F9),
            onPrimary = Color(0xFF2A0A50),
            primaryContainer = Color(0xFF4A2A7A),
            onPrimaryContainer = Color(0xFFE9D5FF),
            secondary = Color(0xFF8BE9FD),
            onSecondary = Color(0xFF00363F),
            secondaryContainer = Color(0xFF00505F),
            onSecondaryContainer = Color(0xFFA8E8F5),
            tertiary = Color(0xFFFF79C6),
            onTertiary = Color(0xFF4A0A38),
            tertiaryContainer = Color(0xFF6E2857),
            onTertiaryContainer = Color(0xFFFFD8F2),
            error = Color(0xFFFF5555),
            onError = Color(0xFF410002),
            errorContainer = Color(0xFF6E2030),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF282A36),
            onBackground = Color(0xFFF8F8F2),
            surface = Color(0xFF282A36),
            onSurface = Color(0xFFF8F8F2),
            surfaceVariant = Color(0xFF44475A),
            onSurfaceVariant = Color(0xFFBFBFBF),
            outline = Color(0xFF6272A4),
            outlineVariant = Color(0xFF44475A),
            inverseSurface = Color(0xFFF8F8F2),
            inverseOnSurface = Color(0xFF282A36),
            inversePrimary = Color(0xFF644AC9),
            surfaceDim = Color(0xFF20222C),
            surfaceBright = Color(0xFF343746),
            surfaceContainerLowest = Color(0xFF1E1F29),
            surfaceContainerLow = Color(0xFF282A36),
            surfaceContainer = Color(0xFF2E3040),
            surfaceContainerHigh = Color(0xFF343746),
            surfaceContainerHighest = Color(0xFF44475A),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }

    // ── Tokyo Night ───────────────────────────────────────────

    private fun tokyoNightLight() = lightColorScheme(
        primary = Color(0xFF3760BF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B4D),
        secondary = Color(0xFF007197),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC4E7FF),
        onSecondaryContainer = Color(0xFF001E30),
        tertiary = Color(0xFF9854A5),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8F2),
        onTertiaryContainer = Color(0xFF3B072E),
        error = Color(0xFFC53B53),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFE1E2E7),
        onBackground = Color(0xFF343B58),
        surface = Color(0xFFE1E2E7),
        onSurface = Color(0xFF343B58),
        surfaceVariant = Color(0xFFC4C8DA),
        onSurfaceVariant = Color(0xFF6172B0),
        outline = Color(0xFF8990B3),
        outlineVariant = Color(0xFFC4C8DA),
        inverseSurface = Color(0xFF343B58),
        inverseOnSurface = Color(0xFFE1E2E7),
        inversePrimary = Color(0xFF7AA2F7),
        surfaceDim = Color(0xFFD8D9DF),
        surfaceBright = Color(0xFFF2F3F7),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFEBECF1),
        surfaceContainer = Color(0xFFE1E2E7),
        surfaceContainerHigh = Color(0xFFDBDCE3),
        surfaceContainerHighest = Color(0xFFD4D6DE),
    )

    private fun tokyoNightDark(amoled: Boolean): androidx.compose.material3.ColorScheme {
        val scheme = darkColorScheme(
            primary = Color(0xFF7AA2F7),
            onPrimary = Color(0xFF0A2A5F),
            primaryContainer = Color(0xFF284777),
            onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFF7DCFFF),
            onSecondary = Color(0xFF00363F),
            secondaryContainer = Color(0xFF00505F),
            onSecondaryContainer = Color(0xFFA8E8F5),
            tertiary = Color(0xFFBB9AF7),
            onTertiary = Color(0xFF2A0A50),
            tertiaryContainer = Color(0xFF4A2A7A),
            onTertiaryContainer = Color(0xFFE9D5FF),
            error = Color(0xFFF7768E),
            onError = Color(0xFF410002),
            errorContainer = Color(0xFF6E2030),
            onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF1A1B26),
            onBackground = Color(0xFFC0CAF5),
            surface = Color(0xFF1A1B26),
            onSurface = Color(0xFFC0CAF5),
            surfaceVariant = Color(0xFF414868),
            onSurfaceVariant = Color(0xFFA9B1D6),
            outline = Color(0xFF565F89),
            outlineVariant = Color(0xFF414868),
            inverseSurface = Color(0xFFC0CAF5),
            inverseOnSurface = Color(0xFF1A1B26),
            inversePrimary = Color(0xFF3760BF),
            surfaceDim = Color(0xFF16171F),
            surfaceBright = Color(0xFF24283B),
            surfaceContainerLowest = Color(0xFF14151C),
            surfaceContainerLow = Color(0xFF1A1B26),
            surfaceContainer = Color(0xFF1F2330),
            surfaceContainerHigh = Color(0xFF24283B),
            surfaceContainerHighest = Color(0xFF414868),
        )
        return if (amoled) applyAmoled(scheme) else scheme
    }
}

@Composable
fun MTZToolTheme(
    family: ThemeFamily? = null,
    mode: ThemeMode? = null,
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Reading the revision makes every setting change recompose the whole app.
    AppSettings.themeRevision
    val revision = AppSettings.themeRevision

    val resolvedFamily = family ?: remember(revision) { AppSettings.getThemeFamily(context) }
    val resolvedMode = mode ?: remember(revision) { AppSettings.getThemeMode(context) }
    val resolvedDynamic = dynamicColor
        ?: remember(revision) { AppSettings.isDynamicColorEnabled(context) }

    val dark = when (resolvedMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = resolvedMode == ThemeMode.AMOLED

    val scheme = remember(resolvedFamily, dark, amoled, resolvedDynamic, context) {
        if (resolvedFamily == ThemeFamily.MATERIAL &&
            resolvedDynamic &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            AppThemes.scheme(resolvedFamily, dark, amoled)
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}

/** Utility used by the setup wizard to show the palette of every family. */
fun ThemeFamily.preview(context: Context): ThemePreviewColors {
    val dark = AppSettings.getThemeMode(context).let {
        it == ThemeMode.DARK || it == ThemeMode.AMOLED
    }
    return AppThemes.previewColors(this, dark)
}
