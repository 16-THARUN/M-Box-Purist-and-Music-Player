package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlainVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    barColor: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val pointCount = 256
    val animatedData = remember { FloatArray(pointCount) }
    val targetData = remember { FloatArray(pointCount) }
    val autoGainSmoother = remember { AutoGainBandSmoother(pointCount, 12f, 110f) }

    var frameTick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { rawFft ->
            if (rawFft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                val len = rawFft.size
                for (i in 0 until pointCount) {
                    val bandIdx = mapSymmetricBandIndex(i, pointCount, len)
                    targetData[i] = rawFft[bandIdx]
                }
            }
        }
    }

    val frameDelay = when (deviceState) {
        DeviceState.OPTIMAL -> 0L
        DeviceState.LOW_BATTERY -> 32L
        DeviceState.POWER_SAVER -> 64L
    }

    LaunchedEffect(deviceState) {
        while (true) {
            withFrameNanos { now ->
                val dtMs = if (lastNanos > 0L) ((now - lastNanos) / 1_000_000f).coerceIn(1f, 50f) else 16f
                lastNanos = now

                if (System.currentTimeMillis() - lastUpdateTime > 120L) {
                    for (i in 0 until pointCount) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)
                frameTick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val lightTint = remember(barColor) { getLightenedAccentColor(barColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _tick = frameTick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val baseR = R + 18.dp.toPx()
        val maxAllowedRadius = min(R + 59.dp.toPx(), min(center.x, center.y) - 12.dp.toPx())
        val maxDisplacement = (maxAllowedRadius - baseR).coerceAtLeast(10.dp.toPx())

        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = baseR,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        val path = Path()
        val slotAngleRad = (2 * Math.PI / pointCount).toFloat()

        for (i in 0 until pointCount) {
            val angle = (i * slotAngleRad) - (Math.PI / 2.0).toFloat()
            val energy = animatedData[i].coerceIn(0f, 1f)
            val currentR = baseR + (energy * maxDisplacement)

            val x = center.x + currentR * cos(angle.toDouble()).toFloat()
            val y = center.y + currentR * sin(angle.toDouble()).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        drawPath(
            path = path,
            color = barColor.copy(alpha = 0.35f),
            style = Stroke(width = 4.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawPath(
            path = path,
            color = lightTint,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
