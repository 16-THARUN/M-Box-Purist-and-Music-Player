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
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HexagonalAudioGridVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    color: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f
) {
    val binCount = 32
    val animatedData = remember { FloatArray(binCount) }
    val targetData = remember { FloatArray(binCount) }
    val autoGainSmoother = remember { AutoGainBandSmoother(binCount, 12f, 110f) }

    var tick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { fft ->
            if (fft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                val len = fft.size
                for (i in 0 until binCount) {
                    targetData[i] = fft[i.coerceIn(0, len - 1)]
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
                    for (i in 0 until binCount) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)
                tick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val drawColor = remember(color) { getLightenedAccentColor(color) }
    val hexPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _t = tick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val hexRadius = 12.dp.toPx()
        val ringCount = 3
        val hexPerRing = 12

        for (ring in 0 until ringCount) {
            val ringRadius = R + 10.dp.toPx() + (ring * 22.dp.toPx())
            for (i in 0 until hexPerRing) {
                val angle = (i.toFloat() / hexPerRing) * 2f * PI
                val cx = (center.x + ringRadius * cos(angle)).toFloat()
                val cy = (center.y + ringRadius * sin(angle)).toFloat()

                val bandIdx = (ring * hexPerRing + i) % binCount
                val energy = animatedData[bandIdx].coerceIn(0f, 1f)

                hexPath.reset()
                for (k in 0 until 6) {
                    val hexAngle = PI / 3f * k - PI / 6f
                    val currentHexR = hexRadius * (1f + energy * 0.20f)
                    val px = (cx + currentHexR * cos(hexAngle)).toFloat()
                    val py = (cy + currentHexR * sin(hexAngle)).toFloat()
                    if (k == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                }
                hexPath.close()

                val alpha = (0.15f + energy * 0.75f).coerceIn(0.15f, 0.90f)

                drawPath(path = hexPath, color = drawColor.copy(alpha = alpha * 0.35f), style = Fill)
                drawPath(path = hexPath, color = drawColor.copy(alpha = alpha), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
