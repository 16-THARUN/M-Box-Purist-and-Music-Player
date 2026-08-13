package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.hypot

@Composable
fun OledEdgeVisualizer(
    fftData: ByteArray, 
    modifier: Modifier = Modifier, 
    color: Color
) {
    var intensity by remember { mutableFloatStateOf(0f) }
    var extremeBass by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "EdgeRotation")
    val gradientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GradientRotationAngle"
    )

    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            var sum = 0f
            for (i in 2 until fftData.size step 2) {
                sum += hypot(fftData[i].toFloat(), fftData[i+1].toFloat())
            }
            val rawAvg = sum / (fftData.size / 2f)
            val rawSub = hypot(fftData[2].toFloat(), fftData[3].toFloat()) // First bin after DC

            intensity += (rawAvg - intensity) * 0.3f
            extremeBass += (rawSub - extremeBass) * 0.5f 
        } else {
            intensity += (0f - intensity) * 0.1f
            extremeBass += (0f - extremeBass) * 0.1f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (intensity <= 0.1f) return@Canvas

        val baseStroke = 4f + (intensity * 0.15f).coerceIn(0f, 12f)
        val pulseGlow = baseStroke + (extremeBass * 0.35f).coerceIn(0f, 50f)
        val phoneCornerRadius = CornerRadius(64f, 64f) 
        
        val secondaryColor = Color(
            red = (color.red + 0.3f).coerceIn(0f, 1f),
            green = (color.green + 0.3f).coerceIn(0f, 1f),
            blue = 1f, 
            alpha = color.alpha
        )

        val sweepGradient = Brush.sweepGradient(
            colors = listOf(color, secondaryColor, color, secondaryColor, color),
            center = Offset(size.width / 2f, size.height / 2f)
        )

        withTransform({
            rotate(gradientRotation, Offset(size.width / 2f, size.height / 2f))
        }) {
            drawRoundRect(
                brush = sweepGradient,
                alpha = (intensity / 180f).coerceIn(0f, 0.2f),
                size = size,
                cornerRadius = phoneCornerRadius,
                style = Stroke(width = pulseGlow * 3f)
            )

            drawRoundRect(
                brush = sweepGradient,
                alpha = (intensity / 120f).coerceIn(0f, 0.5f),
                size = size,
                cornerRadius = phoneCornerRadius,
                style = Stroke(width = pulseGlow * 1.5f)
            )

            drawRoundRect(
                brush = sweepGradient,
                alpha = (intensity / 60f).coerceIn(0f, 1f),
                size = size,
                cornerRadius = phoneCornerRadius,
                style = Stroke(width = baseStroke)
            )
        }

        if (extremeBass > 50f) {
            val flareRadius = (extremeBass * 1.5f).coerceIn(0f, 150f)
            val flareAlpha = ((extremeBass - 50f) / 100f).coerceIn(0f, 0.4f)
            val flareColor = Color.White.copy(alpha = flareAlpha)

            val corners = listOf(
                Offset(0f, 0f), 
                Offset(size.width, 0f), 
                Offset(0f, size.height), 
                Offset(size.width, size.height) 
            )

            corners.forEach { corner ->
                drawCircle(
                    color = flareColor,
                    radius = flareRadius,
                    center = corner
                )
            }
        }
    }
}
