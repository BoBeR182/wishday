package io.github.wishday.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Plum,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1DBFF),
    onPrimaryContainer = Color(0xFF2C0051),
    secondary = Color(0xFFC2185B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Color(0xFFB4421F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFFF8FA),
    onBackground = Color(0xFF1F1A21),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF1F1A21),
    surfaceVariant = Color(0xFFF2E6EE),
    onSurfaceVariant = Color(0xFF5A4F5E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F5),
    surfaceContainer = Color(0xFFFBEAF1),
    surfaceContainerHigh = Color(0xFFF6E4EC),
    surfaceContainerHighest = Color(0xFFF0DEE7),
    outline = Color(0xFF8C7F90),
    outlineVariant = Color(0xFFE2D3DE),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFDDB8FF),
    onPrimary = Color(0xFF47007E),
    primaryContainer = Color(0xFF5E1599),
    onPrimaryContainer = Color(0xFFF1DBFF),
    secondary = Color(0xFFFFB1C6),
    onSecondary = Color(0xFF650032),
    secondaryContainer = Color(0xFF8E0048),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFFFB59F),
    onTertiary = Color(0xFF5E1600),
    tertiaryContainer = Color(0xFF8A2A0B),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF16111B),
    onBackground = Color(0xFFEBDFEA),
    surface = Color(0xFF16111B),
    onSurface = Color(0xFFEBDFEA),
    surfaceVariant = Color(0xFF4A4250),
    onSurfaceVariant = Color(0xFFCDC1D2),
    surfaceContainerLowest = Color(0xFF100C14),
    surfaceContainerLow = Color(0xFF1E1724),
    surfaceContainer = Color(0xFF231B29),
    surfaceContainerHigh = Color(0xFF2D2534),
    surfaceContainerHighest = Color(0xFF38303F),
    outline = Color(0xFF968B9B),
    outlineVariant = Color(0xFF4A4250),
)

@Composable
fun WishdayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
