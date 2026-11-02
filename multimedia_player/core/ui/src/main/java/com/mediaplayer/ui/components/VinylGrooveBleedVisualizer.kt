package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
fun VinylGrooveBleedVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    color: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val points = 96
    val animatedData = remember { FloatArray(points) }
    val targetData = remember { FloatArray(points) }
    val autoGainSmoother = remember { AutoGainBandSmoother(points, 12f, 110f) }

    var tick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    val transition = rememberInfiniteTransition(label = "BleedRotation")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "rot"
    )

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
    val starPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _t = tick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val baseR = R + 6.dp.toPx()
        val maxSpike = 70.dp.toPx()
        val rotRad = Math.toRadians(rotationAngle.toDouble())

        starPath.reset()
        for (i in 0 until points) {
            val baseAngle = i * (2.0 * Math.PI / points)
            val angle = baseAngle + rotRad
            val energy = animatedData[i].coerceIn(0f, 1f)
            val currentR = baseR + (energy * maxSpike)

            val x = center.x + (currentR * cos(angle)).toFloat()
            val y = center.y + (currentR * sin(angle)).toFloat()

            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()

        drawPath(
            path = starPath,
            color = drawColor.copy(alpha = 0.85f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
