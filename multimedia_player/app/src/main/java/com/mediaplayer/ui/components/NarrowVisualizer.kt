package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NarrowVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    barColor: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val barCount = 120
    val animatedData = remember { FloatArray(barCount) }
    val targetData = remember { FloatArray(barCount) }
    val autoGainSmoother = remember { AutoGainBandSmoother(barCount, 12f, 110f) }

    var frameTick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { rawFft ->
            if (rawFft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                val len = rawFft.size
                for (i in 0 until barCount) {
                    val bandIdx = mapSymmetricBandIndex(i, barCount, len)
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
                    for (i in 0 until barCount) targetData[i] = 0f
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

        val startR = R + 4.dp.toPx()
        val maxAllowedRadius = min(R + 53.dp.toPx(), min(center.x, center.y) - 12.dp.toPx())
        val maxLen = (maxAllowedRadius - startR).coerceAtLeast(10.dp.toPx())
        val slotAngle = 360f / barCount
        val barWidthPx = 1.2.dp.toPx()
        val cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)

        for (i in 0 until barCount) {
            val angleDeg = i * slotAngle
            val energy = animatedData[i].coerceIn(0f, 1f)
            val barLen = (2.dp.toPx() + energy * maxLen)

            val color = Color(
                red = barColor.red + (lightTint.red - barColor.red) * energy,
                green = barColor.green + (lightTint.green - barColor.green) * energy,
                blue = barColor.blue + (lightTint.blue - barColor.blue) * energy,
                alpha = 0.55f + energy * 0.45f
            )

            withTransform({
                rotate(degrees = angleDeg, pivot = center)
            }) {
                val rectLeft = center.x - (barWidthPx / 2f)
                val rectTop = center.y - startR - barLen
                val rectSize = Size(barWidthPx, barLen)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(rectLeft, rectTop),
                    size = rectSize,
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}
