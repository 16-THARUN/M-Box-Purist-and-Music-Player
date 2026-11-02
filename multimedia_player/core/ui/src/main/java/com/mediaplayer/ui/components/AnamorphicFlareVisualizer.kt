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
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun AnamorphicFlareVisualizer(
    modifier: Modifier = Modifier,
    centerOffset: Offset? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FlareTransition")
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
            .graphicsLayer { alpha = 0.99f }
    ) {
        val w = size.width
        val h = size.height
        val center = centerOffset ?: Offset(w / 2f, h / 2f)

        // Light origin aligned relative to center
        val lightOrigin = Offset(center.x + (w * 0.4f), center.y - (h * 0.4f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE0F7FA), Color(0xFF00BCD4).copy(alpha = 0.3f), Color.Transparent),
                center = lightOrigin,
                radius = (w * 0.5f) * pulse
            ),
            center = lightOrigin,
            radius = (w * 0.5f) * pulse,
            blendMode = BlendMode.Screen
        )

        // Streak centered relative to the passed center
        val streakCenter = Offset(center.x - (w * 0.1f), center.y - (h * 0.1f))
        rotate(degrees = -15f, pivot = streakCenter) {
            val rainbowBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00FF00).copy(alpha = 0.4f),
                    Color(0xFFFFFF00).copy(alpha = 0.4f),
                    Color(0xFFFF0000).copy(alpha = 0.3f),
                    Color(0xFF0000FF).copy(alpha = 0.2f),
                    Color.Transparent
                ),
                start = Offset(0f, streakCenter.y),
                end = Offset(w, streakCenter.y)
            )

            drawOval(
                brush = rainbowBrush,
                topLeft = Offset(-w * 0.5f, streakCenter.y - (h * 0.15f * pulse)),
                size = androidx.compose.ui.geometry.Size(w * 2f, h * 0.3f * pulse),
                blendMode = BlendMode.Screen
            )
        }
    }
}
