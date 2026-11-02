package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavySlider(
    value: Float, // 0f to 1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    waveAmplitude: Float = 6f,
    waveLength: Float = 40f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavySliderTransition")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val activeWidth = width * value

        // Draw Inactive (Straight) line
        drawLine(
            color = inactiveColor,
            start = Offset(activeWidth, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx()
        )

        // Draw Active (Wavy) line
        if (activeWidth > 0) {
            val path = Path()
            path.moveTo(0f, centerY)
            
            var x = 0f
            val step = 2f
            while (x <= activeWidth) {
                val relativeX = x / waveLength
                val y = centerY + sin(relativeX * 2 * PI.toFloat() - phaseShift) * waveAmplitude
                path.lineTo(x, y)
                x += step
            }

            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Draw Thumb
        drawCircle(
            color = activeColor,
            radius = 6.dp.toPx(),
            center = Offset(activeWidth, centerY)
        )
    }
}
