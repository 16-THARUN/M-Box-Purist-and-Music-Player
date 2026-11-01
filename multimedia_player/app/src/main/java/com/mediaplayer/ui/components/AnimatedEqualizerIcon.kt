package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedEqualizerIcon(
    isPlaying: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.size(16.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqualizerAnimation")

    val bar1Fraction by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar1"
        )
    } else {
        remember { mutableFloatStateOf(0.40f) }
    }

    val bar2Fraction by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar2"
        )
    } else {
        remember { mutableFloatStateOf(0.75f) }
    }

    val bar3Fraction by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Bar3"
        )
    } else {
        remember { mutableFloatStateOf(0.50f) }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width * 0.22f
        val gap = width * 0.12f

        val fractions = listOf(bar1Fraction, bar2Fraction, bar3Fraction)

        for (i in 0..2) {
            val barHeight = (height * fractions[i]).coerceAtLeast(height * 0.15f)
            val x = i * (barWidth + gap) + gap / 2f
            val y = height - barHeight

            drawRoundRect(
                color = tint,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
