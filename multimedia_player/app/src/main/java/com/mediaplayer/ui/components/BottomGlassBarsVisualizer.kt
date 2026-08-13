package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun BottomGlassBars(smoothedData: FloatArray, accentColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baselineY = h * 0.95f 
        
        val barCount = 32
        val spacing = w / barCount
        val barWidth = spacing * 0.7f

        for (i in 0 until barCount) {
            val magnitude = smoothedData[i % smoothedData.size]
            val barHeight = (magnitude * 4f).coerceAtMost(h * 0.4f)
            
            // Draw the "Glass" Bar
            drawLine(
                color = accentColor.copy(alpha = 0.4f),
                start = Offset(i * spacing + (spacing / 2), baselineY),
                end = Offset(i * spacing + (spacing / 2), baselineY - barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            
            // Draw a bright solid core inside the glass bar
            drawLine(
                color = accentColor,
                start = Offset(i * spacing + (spacing / 2), baselineY),
                end = Offset(i * spacing + (spacing / 2), baselineY - (barHeight * 0.5f)),
                strokeWidth = barWidth * 0.3f,
                cap = StrokeCap.Round
            )
        }
    }
}
