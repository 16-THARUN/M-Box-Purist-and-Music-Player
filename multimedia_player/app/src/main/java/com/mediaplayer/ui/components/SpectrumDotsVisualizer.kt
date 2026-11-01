package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SpectrumDotsVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    dotColor: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val anglesCount = 32
    val rowsCount = 5
    val animatedData = remember { FloatArray(anglesCount) }
    val targetData = remember { FloatArray(anglesCount) }
    val autoGainSmoother = remember { AutoGainBandSmoother(anglesCount, 12f, 110f) }

    var frameTick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { rawFft ->
            if (rawFft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                val len = rawFft.size
                for (i in 0 until anglesCount) {
                    val bandIdx = mapSymmetricBandIndex(i, anglesCount, len)
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
                    for (i in 0 until anglesCount) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)
                frameTick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val lightTint = remember(dotColor) { getLightenedAccentColor(dotColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _tick = frameTick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val startR = R + 10.dp.toPx()
        val rowSpacing = 9.dp.toPx()
        val slotAngle = 360f / anglesCount
        val unlitRadius = 1.6.dp.toPx()
        val litMaxRadius = 3.2.dp.toPx()

        for (a in 0 until anglesCount) {
            val angleDeg = a * slotAngle
            val energy = animatedData[a].coerceIn(0f, 1f)
            val litRowsExact = energy * rowsCount

            withTransform({
                rotate(degrees = angleDeg, pivot = center)
            }) {
                for (r in 0 until rowsCount) {
                    val rowR = startR + (r * rowSpacing)
                    val dotCenter = Offset(center.x, center.y - rowR)

                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = unlitRadius,
                        center = dotCenter
                    )

                    val rowFill = (litRowsExact - r).coerceIn(0f, 1f)
                    if (rowFill > 0f) {
                        val rowRatio = r.toFloat() / (rowsCount - 1)
                        val currentColor = Color(
                            red = dotColor.red + (lightTint.red - dotColor.red) * rowRatio,
                            green = dotColor.green + (lightTint.green - dotColor.green) * rowRatio,
                            blue = dotColor.blue + (lightTint.blue - dotColor.blue) * rowRatio,
                            alpha = (0.35f + rowFill * 0.65f)
                        )
                        val currentRadius = unlitRadius + (litMaxRadius - unlitRadius) * rowFill

                        drawCircle(
                            color = currentColor,
                            radius = currentRadius,
                            center = dotCenter
                        )
                    }
                }
            }
        }
    }
}
