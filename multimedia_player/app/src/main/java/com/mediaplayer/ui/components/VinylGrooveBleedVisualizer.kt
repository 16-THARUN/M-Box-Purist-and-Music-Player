package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VinylGrooveBleedVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, color: Color) {
    val numPoints = 128
    var animatedData by remember { mutableStateOf(FloatArray(numPoints)) }
    var targetData by remember { mutableStateOf(FloatArray(numPoints)) }

    LaunchedEffect(Unit) {
        // Coroutine 1: Capture Target Data
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(numPoints)
                for (i in 0 until numPoints) {
                    val fftIndex = if (i < (numPoints / 2)) i else numPoints - i - 1
                    newTarget[i] = if (fftIndex < fft.size) fft[fftIndex] else 0f
                }
                targetData = newTarget
            }
        }
        
        // Coroutine 2: 60FPS Hardware-Synced Glide
        launch {
            while (true) {
                withFrameNanos { _ ->
                    val newArr = FloatArray(numPoints)
                    for (i in 0 until numPoints) {
                        // Smooth interpolation factor (0.2f provides a natural glide)
                        newArr[i] = animatedData[i] + (targetData[i] - animatedData[i]) * 0.2f
                    }
                    animatedData = newArr
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (animatedData.isEmpty()) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (size.minDimension / 2f) * 0.85f 

        val path = Path()
        val angleStep = (2 * PI) / numPoints

        for (i in 0 until numPoints) {
            val angle = i * angleStep
            val spike = animatedData[i] * 2.5f 
            val radius = baseRadius + spike

            val x = center.x + (radius * cos(angle)).toFloat()
            val y = center.y + (radius * sin(angle)).toFloat()

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(
            path = path,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 6f),
        )
    }
}
