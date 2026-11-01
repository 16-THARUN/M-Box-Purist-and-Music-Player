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
import kotlin.random.Random

@Composable
fun VolumetricParticleVisualizer(modifier: Modifier = Modifier, centerOffset: Offset? = null) {
    val particles = remember {
        List(150) {
            ParticleState(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                zOffset = Random.nextFloat(),
                color = listOf(
                    Color(0xFFFF1744), // Red
                    Color(0xFF00E676), // Green
                    Color(0xFF2979FF), // Blue
                    Color(0xFFFFFFFF)  // White
                ).random()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Flythrough")
    val globalZTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ZTime"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = 0.99f }
    ) {
        val w = size.width
        val h = size.height
        val maxRadius = w * 0.04f
        val center = centerOffset ?: Offset(w / 2f, h / 2f)

        particles.forEach { particle ->
            val currentZ = (particle.zOffset + globalZTime) % 1.0f
            val scale = currentZ * currentZ
            
            val centerX = center.x
            val centerY = center.y
            val drawX = centerX + ((particle.x * w - centerX) * (1f + scale * 2f))
            val drawY = centerY + ((particle.y * h - centerY) * (1f + scale * 2f))

            val drawRadius = maxRadius * scale
            val alpha = if (currentZ < 0.2f) currentZ * 5f else if (currentZ > 0.8f) (1f - currentZ) * 5f else 1f

            if (drawX in -w * 0.5f..w * 1.5f && drawY in -h * 0.5f..h * 1.5f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(particle.color.copy(alpha = alpha), Color.Transparent),
                        center = Offset(drawX, drawY),
                        radius = drawRadius.coerceAtLeast(1f)
                    ),
                    center = Offset(drawX, drawY),
                    radius = drawRadius.coerceAtLeast(1f),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

data class ParticleState(
    val x: Float,
    val y: Float,
    val zOffset: Float,
    val color: Color
)
