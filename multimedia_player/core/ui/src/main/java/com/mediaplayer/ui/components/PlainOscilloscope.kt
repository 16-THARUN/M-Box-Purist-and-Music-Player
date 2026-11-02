package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PlainOscilloscope(
    animatedData: List<Float>,
    waveColor: Color,
    modifier: Modifier = Modifier,
    heightMultiplier: Float = 0.4f,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val path = remember { Path() }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dataSize = animatedData.size
            if (dataSize < 3) return@Canvas

            val maxDim = size.minDimension
            // Clamp maximum ring radius so it never exceeds 0.42f of space
            val maxRingRadiusPx = maxDim * 0.42f

            val centerX = centerOffset?.x ?: (size.width / 2f)
            val centerY = centerOffset?.y ?: (size.height / 2f)

            val baseRadius = if (vinylRadiusOverride > 0f) {
                vinylRadiusOverride.coerceAtMost(maxRingRadiusPx * 0.85f)
            } else {
                maxRingRadiusPx * 0.75f
            }

            // Max pulse delta clamped to 8% of base radius (never unbounded or touching edges)
            val maxPulseDelta = (baseRadius * 0.08f * (heightMultiplier / 0.4f)).coerceAtMost((maxRingRadiusPx - baseRadius).coerceAtLeast(0f))

            // 1. Moving-average smoothing across adjacent amplitude samples
            val smoothedAmplitudes = FloatArray(dataSize) { i ->
                val prev = animatedData[(i - 1 + dataSize) % dataSize]
                val curr = animatedData[i]
                val next = animatedData[(i + 1) % dataSize]
                ((prev * 0.25f) + (curr * 0.50f) + (next * 0.25f)).coerceIn(0f, 1f)
            }

            // Calculate closed ring points in polar coordinates
            val points = Array(dataSize) { i ->
                val angle = (i.toFloat() / dataSize) * (2f * Math.PI.toFloat())
                val radius = baseRadius + (smoothedAmplitudes[i] * maxPulseDelta)
                Offset(
                    x = centerX + radius * cos(angle),
                    y = centerY + radius * sin(angle)
                )
            }

            // Construct smooth closed Catmull-Rom cubic Bezier spline
            path.reset()
            val n = points.size
            path.moveTo(points[0].x, points[0].y)

            for (i in 0 until n) {
                val p0 = points[(i - 1 + n) % n]
                val p1 = points[i]
                val p2 = points[(i + 1) % n]
                val p3 = points[(i + 2) % n]

                // Tension factor for smooth curve
                val alpha = 0.25f
                val c1X = p1.x + (p2.x - p0.x) * alpha
                val c1Y = p1.y + (p2.y - p0.y) * alpha
                val c2X = p2.x - (p3.x - p1.x) * alpha
                val c2Y = p2.y - (p3.y - p1.y) * alpha

                path.cubicTo(c1X, c1Y, c2X, c2Y, p2.x, p2.y)
            }

            drawPath(
                path = path,
                color = waveColor,
                style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }
}
