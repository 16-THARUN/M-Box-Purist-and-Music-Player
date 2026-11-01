package com.mediaplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

val MBoxOrange = Color(0xFFF25C05)
val MBoxDeepOrange = Color(0xFFD93600)
val MBoxTeal = Color(0xFF38B29E)
val MBoxDarkGray = Color(0xFF121212)
val MBoxLightGray = Color(0xFFF5F5F5)

// Create the custom gradient from the 3D cube
val MBoxCubeGradient = Brush.linearGradient(
    colors = listOf(MBoxOrange, MBoxTeal)
)

// Create the 3D sphere effect for the Play Button
val MBoxSphereGradient = Brush.radialGradient(
    colors = listOf(Color(0xFFFF8A3D), MBoxDeepOrange),
    center = Offset(0.3f, 0.3f) // Off-center for a "glossy highlight"
)
