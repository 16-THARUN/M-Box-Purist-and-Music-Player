package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun AuroraBorealisVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, color: Color) {
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    // 🔥 THE FIX: Start at 0 and only add small deltas to prevent Float precision death
    var time by remember { mutableFloatStateOf(0f) } 

    LaunchedEffect(Unit) {
        // Coroutine 1: Process Audio
        launch {
            fftFlow.collect { fft ->
                if (fft.isNotEmpty()) {
                    val rawAvg = fft.average().toFloat().coerceIn(0f, 100f)
                    volumeLevel += (rawAvg - volumeLevel) * 0.2f
                } else {
                    volumeLevel += (0f - volumeLevel) * 0.1f
                }
            }
        }
        
        // Coroutine 2: Hardware V-Sync Animation Loop
        launch {
            var lastFrameNanos = -1L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastFrameNanos == -1L) {
                        lastFrameNanos = frameNanos
                    } else {
                        // Calculate time passed in seconds since the last frame
                        var deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                        lastFrameNanos = frameNanos
                        
                        // Cap delta to prevent massive jumps if the thread sleeps
                        if (deltaSeconds > 0.1f) deltaSeconds = 0.016f 
                        
                        time += deltaSeconds * 1.5f // 1.5x rotation speed
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Draw 3 massive floating orbs
        for (i in 0..2) {
            // Movement parameters
            val offsetX = sin(time + (i * 2f)) * (size.width * 0.4f)
            val offsetY = sin((time * 0.8f) + (i * 3f)) * (size.height * 0.3f)
            
            // 🔥 THE FIX: Base radius tied to screen size, making it much larger
            val baseRadius = size.minDimension * 0.65f 
            val pulseRadius = baseRadius + (volumeLevel * 12f)
            
            // 🔥 THE FIX: Increased base opacity so it pops on dark backgrounds
            val orbAlpha = (0.4f + (volumeLevel / 150f)).coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = orbAlpha), // Bright core
                        color.copy(alpha = orbAlpha * 0.3f), // Soft mid-fade
                        Color.Transparent, // Edge fade
                    ),
                    center = Offset(centerX + offsetX, centerY + offsetY),
                    radius = pulseRadius,
                ),
                radius = pulseRadius,
                center = Offset(centerX + offsetX, centerY + offsetY)
            )
        }
    }
}
