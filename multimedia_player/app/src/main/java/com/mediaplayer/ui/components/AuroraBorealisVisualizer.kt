package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

@Composable
fun AuroraBorealisVisualizer(
    fftFlow: StateFlow<FloatArray>,
    modifier: Modifier = Modifier,
    color: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null
) {
    var bassLevel by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    var targetBass by remember { mutableFloatStateOf(0f) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val frameDelay = when (deviceState) {
        DeviceState.OPTIMAL -> 0L
        DeviceState.LOW_BATTERY -> 32L
        DeviceState.POWER_SAVER -> 64L
    }

    LaunchedEffect(fftFlow) {
        fftFlow.collect { fft ->
            if (fft.isNotEmpty()) {
                lastUpdateTime = System.currentTimeMillis()
                targetBass = fft[0].coerceIn(0f, 1f)
            } else {
                targetBass = 0f
            }
        }
    }

    LaunchedEffect(deviceState) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (System.currentTimeMillis() - lastUpdateTime > 150L) {
                    targetBass = 0f
                }
                bassLevel += (targetBass - bassLevel) * 0.25f

                if (lastNanos > 0L) {
                    val dtSec = (now - lastNanos) / 1_000_000_000f
                    time += dtSec * 1.5f
                }
                lastNanos = now
            }
            if (frameDelay > 0) delay(frameDelay)
        }
    }

    val drawColor = remember(color) { getLightenedAccentColor(color) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)

        for (i in 0..2) {
            val offsetX = sin(time + (i * 2f)) * (size.width * 0.30f)
            val offsetY = sin((time * 0.8f) + (i * 3f)) * (size.height * 0.20f)

            val baseRadius = size.minDimension * 0.55f
            val pulseRadius = baseRadius + (bassLevel * 80f)
            val orbAlpha = (0.40f + (bassLevel * 0.50f)).coerceIn(0.40f, 0.90f)

            val drawPos = Offset(center.x + offsetX, center.y + offsetY)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        drawColor.copy(alpha = orbAlpha),
                        drawColor.copy(alpha = orbAlpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = drawPos,
                    radius = pulseRadius,
                ),
                radius = pulseRadius,
                center = drawPos
            )
        }
    }
}
