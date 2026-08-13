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
fun VolumetricParticleVisualizer(modifier: Modifier = Modifier) {
    // Generate a static list of random particle starting states
    val particles = remember {
        List(150) {
            ParticleState(
                x = Random.nextFloat(), // 0.0 to 1.0 (Percentage of screen width)
                y = Random.nextFloat(), // 0.0 to 1.0 (Percentage of screen height)
                zOffset = Random.nextFloat(), // Used to stagger their movement
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

        particles.forEach { particle ->
            // Calculate effective Z depth (0.0 is far away, 1.0 is hitting the camera)
            val currentZ = (particle.zOffset + globalZTime) % 1.0f
            
            // The closer it gets, the larger and faster it appears to move outwards from the center
            val scale = currentZ * currentZ // Exponential scale for 3D feel
            
            // Push particles outward from the center as they get "closer"
            val centerX = w / 2f
            val centerY = h / 2f
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

// Data class to hold particle properties
data class ParticleState(
    val x: Float,
    val y: Float,
    val zOffset: Float,
    val color: Color
)
