package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun ReferenceLightLeaks(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "BokehAnimation")

    // 1. The Flicker/Pulse Animation
    // Simulates the unstable light source intensity
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // 2. The Slow Camera Drift
    // Simulates the subtle movement of the lens
    val drift by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Drift"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Deep black background from the video
            .graphicsLayer { alpha = 0.99f } // Required for Screen blending
    ) {
        val w = size.width
        val h = size.height

        // --- ELEMENT 1: The Large Left-Edge Light ---
        // This clips the left edge and has an intense white/yellow core
        val leftLeakRadius = (w * 0.45f) * pulse
        val leftLeakCenter = Offset(
            x = -w * 0.05f, 
            y = (h * 0.5f) + (drift * h * 0.08f)
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9E6),                   // Hot white/yellow core
                    Color(0xFFFF8C00).copy(alpha = 0.8f),// Intense orange corona
                    Color(0xFF8B0000).copy(alpha = 0.3f),// Dark red falloff
                    Color.Transparent
                ),
                center = leftLeakCenter,
                radius = leftLeakRadius
            ),
            center = leftLeakCenter,
            radius = leftLeakRadius,
            blendMode = BlendMode.Screen
        )

        // --- ELEMENT 2: The Smaller Center-Left Bokeh ---
        // This is softer, purely orange, and pulses out-of-phase with the main light
        val inversePulse = 2f - pulse // Pulses down when the main light pulses up
        val smallBokehRadius = (w * 0.15f) * inversePulse
        val smallBokehCenter = Offset(
            x = (w * 0.35f) + (drift * w * 0.03f), 
            y = (h * 0.55f) - (drift * h * 0.05f)
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9800).copy(alpha = 0.8f), // Solid orange core
                    Color(0xFFE65100).copy(alpha = 0.4f), // Darker orange fade
                    Color.Transparent
                ),
                center = smallBokehCenter,
                radius = smallBokehRadius
            ),
            center = smallBokehCenter,
            radius = smallBokehRadius,
            blendMode = BlendMode.Screen
        )
    }
}
