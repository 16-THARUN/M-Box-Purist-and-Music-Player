package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun AnalogGlitchVisualizer(
    modifier: Modifier = Modifier,
    centerOffset: Offset? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlitchTransition")
    
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0
                0.9f at 100
                0.3f at 150
                0.8f at 400
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "FlashAlpha"
    )

    val jitter by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Jitter"
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

        // Background dark red bleed
        drawRect(color = Color(0xFF4A0000), size = Size(w, h))

        // Left intense magenta/red band (relative to center)
        drawRect(
            color = Color(0xFFFF0044).copy(alpha = flashAlpha),
            topLeft = Offset(center.x - (w * (0.3f - jitter)), 0f),
            size = Size(w * 0.3f, h),
            blendMode = BlendMode.Screen
        )

        // Right solid red glitch band (relative to center)
        drawRect(
            color = Color(0xFFFF0000).copy(alpha = 1f - flashAlpha + 0.3f),
            topLeft = Offset(center.x + (w * (0.1f - jitter * 2)), 0f),
            size = Size(w * 0.25f, h),
            blendMode = BlendMode.Screen
        )

        if (flashAlpha > 0.85f) {
            drawRect(
                color = Color(0xFFFFD1DC).copy(alpha = 0.4f),
                size = Size(w, h),
                blendMode = BlendMode.Screen
            )
        }
    }
}
