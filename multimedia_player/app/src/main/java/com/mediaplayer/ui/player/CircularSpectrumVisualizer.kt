package com.mediaplayer.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.mediaplayer.ui.components.AutoGainBandSmoother
import com.mediaplayer.ui.components.getLightenedAccentColor
import com.mediaplayer.ui.components.mapSymmetricBandIndex
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CircularSpectrumVisualizer(
    fftData: FloatArray,
    accentColor: Color,
    deviceState: DeviceState,
    vinylScale: Float,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f,
    modifier: Modifier = Modifier
) {
    val rays = 72
    val animatedData = remember { FloatArray(rays) }
    val targetData = remember { FloatArray(rays) }
    val autoGainSmoother = remember { AutoGainBandSmoother(rays, 12f, 110f) }

    var tick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    val transition = rememberInfiniteTransition(label = "VinylRotate")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot"
    )

    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            lastUpdateTime = System.currentTimeMillis()
            val len = fftData.size
            for (i in 0 until rays) {
                val bandIdx = mapSymmetricBandIndex(i, rays, len)
                targetData[i] = fftData[bandIdx]
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
                    for (i in 0 until rays) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)
                tick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val drawColor = remember(accentColor) { getLightenedAccentColor(accentColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _t = tick
        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val startR = R + 4.dp.toPx()
        val distToEdge = min(min(center.x, center.y), min(size.width - center.x, size.height - center.y))
        val maxAllowedRadius = min(R + 110.dp.toPx(), distToEdge - 12.dp.toPx())
        val maxRayLen = (maxAllowedRadius - startR).coerceAtLeast(10.dp.toPx())

        val strokeWidthPx = 2f.dp.toPx()
        val rotRad = Math.toRadians(rotationAngle.toDouble())

        for (i in 0 until rays) {
            val baseAngle = (i.toFloat() / rays) * 2.0 * Math.PI
            val angle = baseAngle + rotRad

            val energy = animatedData[i].coerceIn(0f, 1f)
            val rayLen = 4.dp.toPx() + energy * (maxRayLen - 4.dp.toPx())

            val startX = center.x + (startR * cos(angle)).toFloat()
            val startY = center.y + (startR * sin(angle)).toFloat()

            val endX = center.x + ((startR + rayLen) * cos(angle)).toFloat()
            val endY = center.y + ((startR + rayLen) * sin(angle)).toFloat()

            val alpha = 0.40f + energy * 0.60f

            drawLine(
                color = drawColor.copy(alpha = alpha),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}
