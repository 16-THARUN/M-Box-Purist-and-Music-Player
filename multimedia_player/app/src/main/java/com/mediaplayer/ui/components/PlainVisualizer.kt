package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun PlainVisualizer(fftFlow: StateFlow<FloatArray>, modifier: Modifier = Modifier, barColor: Color) {
    val pointCount = 64
    var animatedData by remember { mutableStateOf(FloatArray(pointCount)) }
    var targetData by remember { mutableStateOf(FloatArray(pointCount)) }

    LaunchedEffect(Unit) {
        launch {
            fftFlow.collect { fft ->
                val newTarget = FloatArray(pointCount)
                if (fft.isNotEmpty()) {
                    val step = maxOf(1, fft.size / pointCount)
                    for (i in 0 until pointCount) {
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
                    val newArr = FloatArray(pointCount)
                    for (i in 0 until pointCount) {
                        newArr[i] = animatedData[i] + ((targetData[i] - animatedData[i]) * 0.3f)
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
        
        // 🔥 THE FIX: Anchor dead center to cut right across the vinyl
        val centerY = h / 2f 
        val stepX = w / (pointCount - 1)
        
        val path = Path()
        
        // Start the path at the left edge
        path.moveTo(0f, centerY - (animatedData[0] * 3.5f))
        
        // 🔥 THE FIX: Draw a smooth, continuous analog curve instead of jagged lines
        for (i in 1 until pointCount) {
            val prevX = (i - 1) * stepX
            val prevY = centerY - (animatedData[i - 1] * 3.5f)
            val curX = i * stepX
            val curY = centerY - (animatedData[i] * 3.5f)
            
            // Calculate midpoint for smooth Bezier control anchors
            val midX = (prevX + curX) / 2f
            val midY = (prevY + curY) / 2f
            
            path.quadraticTo(prevX, prevY, midX, midY)
        }
        // Connect to the final point on the right edge
        path.lineTo(w, centerY - (animatedData.last() * 3.5f))
        
        // ==========================================
        // 🔥 CRT PHOSPHOR GLOW EFFECT
        // ==========================================
        
        // 1. Draw the Outer Glow (Thick, transparent stroke)
        drawPath(
            path = path,
            color = barColor.copy(alpha = 0.35f),
            style = Stroke(width = 16f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        
        // 2. Draw the Bright Phosphor Core (Thin, solid stroke)
        drawPath(
            path = path,
            color = barColor,
            style = Stroke(width = 4f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
    }
}
