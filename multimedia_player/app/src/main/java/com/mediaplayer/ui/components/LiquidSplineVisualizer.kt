package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun LiquidSplineVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, color: Color) {
    val points = 16
    var animatedData by remember { mutableStateOf(FloatArray(points)) }
    var targetData by remember { mutableStateOf(FloatArray(points)) }

    LaunchedEffect(Unit) {
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(points)
                // 🔥 THE FIX: Only run the math if the array actually has data
                if (fft.isNotEmpty()) {
                    val step = Math.max(1, fft.size / points)
                    for (i in 0 until points) {
                        val dataIndex = (i * step).coerceIn(0, fft.lastIndex)
                        newTarget[i] = fft[dataIndex]
                    }
                }
                targetData = newTarget
            }
        }
        launch {
            while (true) {
                withFrameNanos { _ ->
                    val newArr = FloatArray(points)
                    for (i in 0 until points) {
                            newArr[i] = animatedData[i] + ((targetData[i] - animatedData[i]) * 0.15f)
                    }
                    animatedData = newArr
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (animatedData.isEmpty()) return@Canvas
        
        val w = size.width
        val h = size.height
        val centerY = h * 0.75f // Anchor to bottom quadrant
        val stepX = w / (points - 1)
        
        val path1 = Path().apply { moveTo(0f, h) }
        val path2 = Path().apply { moveTo(0f, h) }
        
        for (i in 0 until points) {
            val splashHeight = animatedData[i] * 4f // Original wave height multiplier
            val targetX = i * stepX
            val targetY1 = centerY - splashHeight
            val targetY2 = centerY + (splashHeight * 0.5f) - (h * 0.05f)
            
            if (i == 0) {
                path1.lineTo(targetX, targetY1)
                path2.lineTo(targetX, targetY2)
            } else {
                val prevX = (i - 1) * stepX
                path1.quadraticTo(prevX + (stepX / 2), targetY1, targetX, targetY1)
                path2.quadraticTo(prevX + (stepX / 2), targetY2, targetX, targetY2)
            }
        }
        
        path1.apply { lineTo(w, h); close() }
        path2.apply { lineTo(w, h); close() }
        
        drawPath(path1, color = color.copy(alpha = 0.6f), style = Fill, blendMode = BlendMode.Screen)
        drawPath(path2, color = color.copy(alpha = 0.3f), style = Fill, blendMode = BlendMode.Screen)
    }
}
