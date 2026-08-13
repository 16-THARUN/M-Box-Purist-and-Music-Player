package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HexagonalAudioGridVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, color: Color) {
    val binCount = 64
    var animatedData by remember { mutableStateOf(FloatArray(binCount)) }
    var targetData by remember { mutableStateOf(FloatArray(binCount)) }

    LaunchedEffect(Unit) {
        // Coroutine 1: Capture Target Data
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(binCount)
                for (i in 0 until binCount) {
                    newTarget[i] = if (i < fft.size) fft[i] else 0f
                }
                targetData = newTarget
            }
        }
        
        // Coroutine 2: 60FPS Hardware-Synced Glide
        launch {
            while (true) {
                withFrameNanos { _ ->
                    val newArr = FloatArray(binCount)
                    for (i in 0 until binCount) {
                        newArr[i] = animatedData[i] + ((targetData[i] - animatedData[i]) * 0.15f)
                    }
                    animatedData = newArr
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (animatedData.isEmpty()) return@Canvas

        val hexRadius = 40f
        val hexWidth = sqrt(3f) * hexRadius
        val hexHeight = 2f * hexRadius
        
        val cols = (size.width / hexWidth).toInt() + 2
        val rows = (size.height / (hexHeight * 0.75f)).toInt() + 2

        val startX = (size.width - (cols * hexWidth)) / 2f
        val startY = (size.height - (rows * hexHeight * 0.75f)) / 2f

        var dataIndex = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val intensity = if (dataIndex < binCount) animatedData[dataIndex] else 0f
                dataIndex = (dataIndex + 1) % binCount

                val xOffset = if (row % 2 == 1) hexWidth / 2f else 0f
                val cx = startX + col * hexWidth + xOffset
                val cy = startY + row * hexHeight * 0.75f

                val path = Path()
                for (i in 0 until 6) {
                    val angle = PI / 3f * i - PI / 6f
                    val px = cx + hexRadius * cos(angle).toFloat()
                    val py = cy + hexRadius * sin(angle).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()

                val alpha = (intensity / 100f).coerceIn(0.05f, 0.8f)

                drawPath(path = path, color = color.copy(alpha = alpha * 0.3f))
                drawPath(
                    path = path, 
                    color = color.copy(alpha = alpha), 
                    style = Stroke(width = 2f + (intensity / 20f)),
                )
            }
        }
    }
}
