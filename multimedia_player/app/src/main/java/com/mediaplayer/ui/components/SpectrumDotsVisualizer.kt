package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun SpectrumDotsVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, dotColor: Color) {
    val columns = 24
    var animatedData by remember { mutableStateOf(FloatArray(columns)) }
    var targetData by remember { mutableStateOf(FloatArray(columns)) }

    LaunchedEffect(Unit) {
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(columns)
                // 🔥 THE FIX: Safety check
                if (fft.isNotEmpty()) {
                    val step = Math.max(1, fft.size / columns)
                    for (i in 0 until columns) {
                        val dataIndex = (i * step).coerceIn(0, fft.lastIndex)
                        newTarget[i] = fft[dataIndex]
                    }
                }
                targetData = newTarget
            }
        }
        launch {
            while (true) {
                withFrameNanos {
                    val newArr = FloatArray(columns)
                    for (i in 0 until columns) newArr[i] = animatedData[i] + ((targetData[i] - animatedData[i]) * 0.2f)
                    animatedData = newArr
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baselineY = h * 0.8f
        val colSpacing = w / columns
        val dotRadius = colSpacing * 0.25f
        val dotSpacing = dotRadius * 3.5f
        
        for (i in 0 until columns) {
            val intensity = animatedData[i] * 2.5f
            val dotCount = (intensity / dotSpacing).toInt().coerceIn(1, 15)
            val x = i * colSpacing + (colSpacing / 2f)
            
            for (j in 0 until dotCount) {
                val dotAlpha = (1f - (j.toFloat() / 15f)).coerceIn(0.1f, 1f)
                val y = baselineY - (j * dotSpacing)
                
                drawCircle(
                    color = dotColor.copy(alpha = dotAlpha),
                    radius = dotRadius,
                    center = Offset(x, y),
                )
            }
        }
    }
}
