package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private data class RippleRing(
    var currentRadiusPx: Float,
    val maxRadiusPx: Float,
    val speedPx: Float,
    var alpha: Float
)

@Composable
fun AcousticRipplesVisualizer(
    fftData: FloatArray,
    accentColor: Color,
    deviceState: DeviceState,
    vinylScale: Float,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var lastBeatTime by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current.density
    val activeRings = remember { mutableStateListOf<RippleRing>() }

    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            val bass = fftData[0]
            val now = System.currentTimeMillis()
            if (bass > 0.65f && now - lastBeatTime > 300L) {
                lastBeatTime = now
                if (activeRings.size >= 6) {
                    activeRings.removeAt(0)
                }
                activeRings.add(
                    RippleRing(
                        currentRadiusPx = 0f,
                        maxRadiusPx = 180f * density,
                        speedPx = 220f * density,
                        alpha = 1.0f
                    )
                )
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

                val dtSec = dtMs / 1000f
                val iterator = activeRings.iterator()
                while (iterator.hasNext()) {
                    val ring = iterator.next()
                    ring.currentRadiusPx += ring.speedPx * dtSec
                    ring.alpha = (1.0f - (ring.currentRadiusPx / ring.maxRadiusPx)).coerceIn(0f, 1f)
                    if (ring.currentRadiusPx >= ring.maxRadiusPx || ring.alpha <= 0f) {
                        iterator.remove()
                    }
                }
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

        for (ring in activeRings) {
            val drawRadius = R + ring.currentRadiusPx
            drawCircle(
                color = drawColor.copy(alpha = ring.alpha * 0.85f),
                radius = drawRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
