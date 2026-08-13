package com.mediaplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun CircularSpectrumVisualizer(fftData: ByteArray, accentColor: Color, vinylScale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (fftData.size < 4) return@Canvas
        
        val center = Offset(size.width / 2, size.height / 2)
        // 🔥 THE FIX: Escape the shadow
        val vinylRadius = ((size.width * vinylScale) / 2f) + 30f 
        val maxLineLength = size.width * 0.25f
        
        val bars = 64
        val step = (fftData.size / 2) / bars
        
        for (i in 0 until bars) {
            val dataIndex = (i * step * 2 + 2).coerceIn(0, fftData.size - 2)
            val real = fftData[dataIndex].toFloat()
            val imaginary = fftData[dataIndex + 1].toFloat()
            
            // 🔥 BOOST MULTIPLIER (x4)
            val magnitude = (hypot(real, imaginary) * 4f).coerceAtMost(200f)
            val lineLength = (magnitude / 128f) * maxLineLength
            
            val angle = (i.toFloat() / bars) * 2.0 * Math.PI
            
            val startX = center.x + (vinylRadius * cos(angle)).toFloat()
            val startY = center.y + (vinylRadius * sin(angle)).toFloat()
            
            val endX = center.x + ((vinylRadius + lineLength) * cos(angle)).toFloat()
            val endY = center.y + ((vinylRadius + lineLength) * sin(angle)).toFloat()
            
            drawLine(
                color = accentColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
    }
}
