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
fun ReferenceLightLeaks(
    modifier: Modifier = Modifier,
    centerOffset: Offset? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BokehAnimation")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

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
            .background(Color.Black)
            .graphicsLayer { alpha = 0.99f }
    ) {
        val w = size.width
        val h = size.height
        val center = centerOffset ?: Offset(w / 2f, h / 2f)

        // Element 1: Dynamic relative to the center
        val leftLeakRadius = (w * 0.45f) * pulse
        val leftLeakCenter = Offset(
            x = center.x - (w * 0.55f), 
            y = center.y + (drift * h * 0.08f)
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF9E6),
                    Color(0xFFFF8C00).copy(alpha = 0.8f),
                    Color(0xFF8B0000).copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = leftLeakCenter,
                radius = leftLeakRadius
            ),
            center = leftLeakCenter,
            radius = leftLeakRadius,
            blendMode = BlendMode.Screen
        )

        // Element 2: Dynamic relative to the center
        val inversePulse = 2f - pulse
        val smallBokehRadius = (w * 0.15f) * inversePulse
        val smallBokehCenter = Offset(
            x = center.x - (w * 0.15f) + (drift * w * 0.03f), 
            y = center.y + (h * 0.05f) - (drift * h * 0.05f)
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9800).copy(alpha = 0.8f),
                    Color(0xFFE65100).copy(alpha = 0.4f),
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
