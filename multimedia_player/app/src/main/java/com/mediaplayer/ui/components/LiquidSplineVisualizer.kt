package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
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
fun LiquidSplineVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    color: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val points = 64
    val animatedData = remember { FloatArray(points) }
    val targetData = remember { FloatArray(points) }
    val autoGainSmoother = remember { AutoGainBandSmoother(points, 12f, 110f) }

    var tick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { fft ->
            if (fft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                val len = fft.size
                for (i in 0 until points) {
                    val bandIdx = mapSymmetricBandIndex(i, points, len)
                    targetData[i] = fft[bandIdx]
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

                if (System.currentTimeMillis() - lastUpdateTime > 150L) {
                    for (i in 0 until points) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)
                tick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val drawColor = remember(color) { getLightenedAccentColor(color) }
    val blobPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _t = tick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val baseR = R + 8.dp.toPx()
        val maxSplineDisplacement = 40.dp.toPx()

        blobPath.reset()
        val angleStep = (2.0 * Math.PI) / points

        for (i in 0 until points) {
            val angle = i * angleStep
            val energy = animatedData[i].coerceIn(0f, 1f)
            val currentR = baseR + energy * maxSplineDisplacement

            val x = center.x + (currentR * cos(angle)).toFloat()
            val y = center.y + (currentR * sin(angle)).toFloat()

            if (i == 0) blobPath.moveTo(x, y) else blobPath.lineTo(x, y)
        }
        blobPath.close()

        drawPath(
            path = blobPath,
            color = drawColor.copy(alpha = 0.25f),
            style = Fill
        )
        drawPath(
            path = blobPath,
            color = drawColor.copy(alpha = 0.80f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
