package io.github.wishday.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand colors, shared with the launcher icon (tools/artwork.py).
val Coral = Color(0xFFFF7A59)
val Plum = Color(0xFF7B2CBF)
val Rose = Color(0xFFFF6F9C)

/** The coral-to-plum gradient behind the icon and the home screen header. */
val BrandGradient = Brush.linearGradient(listOf(Coral, Rose, Plum))

/** Avatar gradients; each person gets one based on their name so it stays stable. */
val AvatarGradients = listOf(
    listOf(Color(0xFFFF9A76), Color(0xFFFF5E8A)),
    listOf(Color(0xFFB388FF), Color(0xFF7B2CBF)),
    listOf(Color(0xFF4CC9F0), Color(0xFF4361EE)),
    listOf(Color(0xFFFFD166), Color(0xFFFF7A59)),
    listOf(Color(0xFF80ED99), Color(0xFF14B8A6)),
)
