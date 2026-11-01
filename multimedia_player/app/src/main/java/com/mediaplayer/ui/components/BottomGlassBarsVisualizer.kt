package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BottomGlassBars(
    fftFlow: StateFlow<FloatArray>,
    accentColor: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f,
    modifier: Modifier = Modifier
) {
    val barCount = 28
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

    val lightTint = remember(accentColor) { getLightenedAccentColor(accentColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _tick = frameTick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val startR = R + 6.dp.toPx()
        val maxAllowedRadius = min(R + 52.dp.toPx(), min(center.x, center.y) - 12.dp.toPx())
        val maxLen = (maxAllowedRadius - startR).coerceAtLeast(10.dp.toPx())
        val slotAngle = 360f / barCount
        val rawWidth = (2 * Math.PI.toFloat() * startR / barCount) * 0.62f
        val barWidthPx = rawWidth.coerceAtMost(10.dp.toPx())
        val pillRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)

        for (i in 0 until barCount) {
            val angleDeg = i * slotAngle
            val energy = animatedData[i].coerceIn(0f, 1f)
            val barLen = (4.dp.toPx() + energy * (maxLen - 4.dp.toPx()))

            withTransform({
                rotate(degrees = angleDeg, pivot = center)
            }) {
                val rectLeft = center.x - (barWidthPx / 2f)
                val rectTop = center.y - startR - barLen
                val rectSize = Size(barWidthPx, barLen)

                val brush = Brush.verticalGradient(
                    colors = listOf(lightTint.copy(alpha = 0.85f), accentColor.copy(alpha = 0.45f)),
                    startY = rectTop,
                    endY = rectTop + barLen
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(rectLeft, rectTop),
                    size = rectSize,
                    cornerRadius = pillRadius
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = rectSize,
                    cornerRadius = pillRadius,
                    style = Stroke(width = 1.dp.toPx())
                )

                val highlightWidth = 1.5.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = Offset(rectLeft + 2.dp.toPx(), rectTop + 2.dp.toPx()),
                    size = Size(highlightWidth, (barLen - 4.dp.toPx()).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(highlightWidth / 2f, highlightWidth / 2f)
                )
            }
        }
    }
}
