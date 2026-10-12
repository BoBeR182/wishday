package io.github.wishday.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Light/dark choice: follow the system, or force one. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

@Composable
fun WishdayTheme(
    themeId: String = Palettes.SUNSET.id,
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val wallpaper = themeId == Palettes.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val (scheme, palette) = remember(themeId, dark, wallpaper) {
        if (wallpaper) {
            val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            s to Palettes.fromScheme(s, dark)
        } else {
            val p = Palettes.byId(themeId)
            schemeFor(p, dark) to p
        }
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}

/** Whether the app is currently dark, which can differ from the system when forced in Settings. */
val LocalIsDark = androidx.compose.runtime.staticCompositionLocalOf { false }
