package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun OrbitingStardustVisualizer(fftData: ByteArray, accentColor: Color, vinylScale: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "rot"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (fftData.size < 4) return@Canvas
        
        val center = Offset(size.width / 2, size.height / 2)
        val vinylRadius = ((size.width * vinylScale) / 2f) + 20f
        val particleCount = 30
        
        for (i in 0 until particleCount) {
            val real = fftData[(i * 2) % fftData.size].toFloat()
            val imaginary = fftData[(i * 2 + 1) % fftData.size].toFloat()
            val magnitude = hypot(real, imaginary)
            
            // Original gentle expansion
            val orbitRadius = vinylRadius + 10f + (magnitude * 1.5f) 
            val angle = Math.toRadians(rotation.toDouble() + (i * (360.0 / particleCount)))
            
            drawCircle(
                color = accentColor.copy(alpha = (magnitude / 128f).coerceIn(0.2f, 1f)),
                radius = 4f + (magnitude / 20f),
                center = Offset(
                    x = center.x + (orbitRadius * cos(angle)).toFloat(),
                    y = center.y + (orbitRadius * sin(angle)).toFloat()
                )
            )
        }
    }
}
