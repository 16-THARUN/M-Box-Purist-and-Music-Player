package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun NarrowVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, barColor: Color) {
    val barCount = 48
    var animatedData by remember { mutableStateOf(FloatArray(barCount)) }
    var targetData by remember { mutableStateOf(FloatArray(barCount)) }

    LaunchedEffect(Unit) {
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(barCount)
                for (i in 0 until barCount) newTarget[i] = if (i < fft.size) fft[i] else 0f
                targetData = newTarget
            }
        }
        launch {
            while (true) {
                withFrameNanos {
                    val newArr = FloatArray(barCount)
                    for (i in 0 until barCount) newArr[i] = animatedData[i] + ((targetData[i] - animatedData[i]) * 0.25f)
                    animatedData = newArr
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baselineY = h * 0.9f
        val spacing = w / barCount
        
        for (i in 0 until barCount) {
            val barHeight = animatedData[i] * 3.5f
            val x = i * spacing + (spacing / 2f)
            
            drawLine(
                color = barColor,
                start = Offset(x, baselineY),
                end = Offset(x, baselineY - barHeight),
                strokeWidth = spacing * 0.4f, // Narrow aesthetic
                cap = StrokeCap.Round,
            )
        }
    }
}
