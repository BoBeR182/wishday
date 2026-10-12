package io.github.wishday.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.wishday.R

/**
 * A color theme: the header gradient plus Material color seeds. Chart colors are picked
 * per mode and were checked for contrast and color-blind separation against the card surface.
 */
@Immutable
data class Palette(
    val id: String,
    @StringRes val name: Int,
    val gradient: List<Color>,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val chartBarLight: Color,
    val chartNowLight: Color,
    val chartBarDark: Color,
    val chartNowDark: Color,
) {
    val brush: Brush get() = Brush.linearGradient(gradient)
}

object Palettes {
    val SUNSET = Palette(
        "sunset", R.string.theme_sunset,
        gradient = listOf(Color(0xFFFF7A59), Color(0xFFFF6F9C), Color(0xFF7B2CBF)),
        primaryLight = Color(0xFF7B2CBF), primaryDark = Color(0xFFDDB8FF),
        secondaryLight = Color(0xFFC2185B), secondaryDark = Color(0xFFFFB1C6),
        chartBarLight = Color(0xFF7B2CBF), chartNowLight = Color(0xFFE8603F),
        chartBarDark = Color(0xFF9A5FD6), chartNowDark = Color(0xFFE0684A),
    )
    val OCEAN = Palette(
        "ocean", R.string.theme_ocean,
        gradient = listOf(Color(0xFF2EC4B6), Color(0xFF3A86FF), Color(0xFF4338CA)),
        primaryLight = Color(0xFF1D4ED8), primaryDark = Color(0xFFADC6FF),
        secondaryLight = Color(0xFF0F766E), secondaryDark = Color(0xFF7FD8CC),
        chartBarLight = Color(0xFF1D4ED8), chartNowLight = Color(0xFF0D9488),
        chartBarDark = Color(0xFF5B8DEF), chartNowDark = Color(0xFF14A898),
    )
    val FOREST = Palette(
        "forest", R.string.theme_forest,
        gradient = listOf(Color(0xFF84CC16), Color(0xFF10B981), Color(0xFF0F766E)),
        primaryLight = Color(0xFF047857), primaryDark = Color(0xFF7DDCB0),
        secondaryLight = Color(0xFF4D7C0F), secondaryDark = Color(0xFFB9E38A),
        chartBarLight = Color(0xFF047857), chartNowLight = Color(0xFF7C3AED),
        chartBarDark = Color(0xFF15965F), chartNowDark = Color(0xFF8B7CF0),
    )
    val BERRY = Palette(
        "berry", R.string.theme_berry,
        gradient = listOf(Color(0xFFEC4899), Color(0xFFC026D3), Color(0xFF6D28D9)),
        primaryLight = Color(0xFFA21CAF), primaryDark = Color(0xFFF4AFFF),
        secondaryLight = Color(0xFFBE185D), secondaryDark = Color(0xFFFFB0CB),
        chartBarLight = Color(0xFFA21CAF), chartNowLight = Color(0xFFE11D48),
        chartBarDark = Color(0xFFC04BD0), chartNowDark = Color(0xFFF0506E),
    )
    val MIDNIGHT = Palette(
        "midnight", R.string.theme_midnight,
        gradient = listOf(Color(0xFF22D3EE), Color(0xFF8B5CF6), Color(0xFFD946EF)),
        primaryLight = Color(0xFF6D28D9), primaryDark = Color(0xFFC9B3FF),
        secondaryLight = Color(0xFF0E7490), secondaryDark = Color(0xFF7DE3F4),
        chartBarLight = Color(0xFF6D28D9), chartNowLight = Color(0xFF0891B2),
        chartBarDark = Color(0xFF8B5CF6), chartNowDark = Color(0xFF0EA5C6),
    )

    val ALL = listOf(SUNSET, OCEAN, FOREST, BERRY, MIDNIGHT)

    /** Special id: colors come from the wallpaper (Android 12+). */
    const val WALLPAPER = "wallpaper"

    fun byId(id: String?): Palette = ALL.firstOrNull { it.id == id } ?: SUNSET

    /** A palette built from the wallpaper's Material You colors. */
    fun fromScheme(scheme: ColorScheme, dark: Boolean) = Palette(
        WALLPAPER, R.string.theme_wallpaper,
        gradient = if (dark) {
            listOf(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer)
        } else {
            listOf(scheme.tertiary, scheme.primary, lerp(scheme.primary, Color.Black, 0.25f))
        },
        primaryLight = scheme.primary, primaryDark = scheme.primary,
        secondaryLight = scheme.secondary, secondaryDark = scheme.secondary,
        chartBarLight = scheme.primary, chartNowLight = scheme.tertiary,
        chartBarDark = scheme.primary, chartNowDark = scheme.tertiary,
    )
}

/** The active theme, for gradients and charts that sit outside Material's color roles. */
val LocalPalette = staticCompositionLocalOf { Palettes.SUNSET }

/** Material color scheme for [p]: tinted surfaces, containers mixed from the seed colors. */
fun schemeFor(p: Palette, dark: Boolean): ColorScheme {
    val tint = p.primaryLight
    return if (!dark) {
        val bg = lerp(Color.White, tint, 0.03f)
        lightColorScheme(
            primary = p.primaryLight, onPrimary = Color.White,
            primaryContainer = lerp(p.primaryLight, Color.White, 0.84f),
            onPrimaryContainer = lerp(p.primaryLight, Color.Black, 0.6f),
            secondary = p.secondaryLight, onSecondary = Color.White,
            secondaryContainer = lerp(p.secondaryLight, Color.White, 0.84f),
            onSecondaryContainer = lerp(p.secondaryLight, Color.Black, 0.6f),
            tertiary = p.chartNowLight, onTertiary = Color.White,
            tertiaryContainer = lerp(p.chartNowLight, Color.White, 0.84f),
            onTertiaryContainer = lerp(p.chartNowLight, Color.Black, 0.6f),
            background = bg, surface = bg,
            onBackground = Color(0xFF1C1A20), onSurface = Color(0xFF1C1A20),
            surfaceVariant = lerp(Color.White, tint, 0.10f), onSurfaceVariant = lerp(Color(0xFF55505C), tint, 0.15f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color.White, tint, 0.05f),
            surfaceContainer = lerp(Color.White, tint, 0.08f),
            surfaceContainerHigh = lerp(Color.White, tint, 0.11f),
            surfaceContainerHighest = lerp(Color.White, tint, 0.14f),
            outline = lerp(Color(0xFF8A8590), tint, 0.15f), outlineVariant = lerp(Color.White, tint, 0.18f),
        )
    } else {
        val base = Color(0xFF131116)
        val bg = lerp(base, tint, 0.05f)
        darkColorScheme(
            primary = p.primaryDark, onPrimary = lerp(p.primaryLight, Color.Black, 0.55f),
            primaryContainer = lerp(p.primaryLight, Color.Black, 0.45f),
            onPrimaryContainer = lerp(p.primaryDark, Color.White, 0.6f),
            secondary = p.secondaryDark, onSecondary = lerp(p.secondaryLight, Color.Black, 0.55f),
            secondaryContainer = lerp(p.secondaryLight, Color.Black, 0.45f),
            onSecondaryContainer = lerp(p.secondaryDark, Color.White, 0.6f),
            tertiary = p.chartNowDark, onTertiary = Color.Black,
            tertiaryContainer = lerp(p.chartNowDark, Color.Black, 0.5f),
            onTertiaryContainer = lerp(p.chartNowDark, Color.White, 0.6f),
            background = bg, surface = bg,
            onBackground = Color(0xFFEAE6EE), onSurface = Color(0xFFEAE6EE),
            surfaceVariant = lerp(base, tint, 0.22f), onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainerLowest = lerp(Color.Black, tint, 0.03f),
            surfaceContainerLow = lerp(base, tint, 0.09f),
            surfaceContainer = lerp(base, tint, 0.12f),
            surfaceContainerHigh = lerp(base, tint, 0.16f),
            surfaceContainerHighest = lerp(base, tint, 0.20f),
            outline = Color(0xFF948E9C), outlineVariant = lerp(base, tint, 0.28f),
        )
    }
}
