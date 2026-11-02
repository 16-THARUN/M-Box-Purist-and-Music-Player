package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

@Composable
fun AnamorphicLensFlareCanvasVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    centerOffset: Offset? = null,
    accentColor: Color = Color(0xFF00E5FF)
) {
    val drawColor = remember(accentColor) { getLightenedAccentColor(accentColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, drawColor.copy(alpha = 0.60f), Color.Transparent),
                startX = 0f,
                endX = size.width
            ),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 24.dp.toPx(),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Screen
        )

        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White, Color.Transparent),
                startX = size.width * 0.10f,
                endX = size.width * 0.90f
            ),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Screen
        )

        val ghostFractions = listOf(0.20f, 0.40f, 0.65f, 0.82f)
        val ghostRadii = listOf(12.dp.toPx(), 22.dp.toPx(), 16.dp.toPx(), 28.dp.toPx())

        for (i in ghostFractions.indices) {
            val gx = size.width * ghostFractions[i]
            val gr = ghostRadii[i]
            drawCircle(
                color = drawColor.copy(alpha = 0.35f),
                radius = gr,
                center = Offset(gx, center.y),
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
fun ParticleGridCanvasVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    centerOffset: Offset? = null,
    accentColor: Color = Color(0xFF00E5FF)
) {
    val drawColor = remember(accentColor) { getLightenedAccentColor(accentColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val gridSpacing = 22.dp.toPx()
        val vinylExclusionRadius = 140.dp.toPx()

        val cols = (size.width / gridSpacing).toInt() + 1
        val rows = (size.height / gridSpacing).toInt() + 1

        val startX = (size.width - (cols - 1) * gridSpacing) / 2f
        val startY = (size.height - (rows - 1) * gridSpacing) / 2f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val px = startX + c * gridSpacing
                val py = startY + r * gridSpacing

                val dx = px - center.x
                val dy = py - center.y
                val distFromCenter = kotlin.math.hypot(dx, dy)

                if (distFromCenter > vinylExclusionRadius && py > 100.dp.toPx() && py < size.height - 250.dp.toPx()) {
                    drawCircle(
                        color = drawColor.copy(alpha = 0.55f),
                        radius = 2.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
