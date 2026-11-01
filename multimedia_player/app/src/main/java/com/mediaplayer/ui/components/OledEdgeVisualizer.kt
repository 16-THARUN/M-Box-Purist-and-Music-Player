package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay

@Composable
fun OledEdgeVisualizer(
    fftData: FloatArray,
    modifier: Modifier = Modifier,
    color: Color,
    deviceState: DeviceState,
    centerOffset: Offset? = null
) {
    var bassIntensity by remember { mutableFloatStateOf(0f) }
    var targetBass by remember { mutableFloatStateOf(0f) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            lastUpdateTime = System.currentTimeMillis()
            targetBass = fftData[0].coerceIn(0f, 1f)
        } else {
            targetBass = 0f
        }
    }

    val frameDelay = when (deviceState) {
        DeviceState.OPTIMAL -> 0L
        DeviceState.LOW_BATTERY -> 32L
        DeviceState.POWER_SAVER -> 64L
    }

    LaunchedEffect(deviceState) {
        while (true) {
            withFrameNanos {
                if (System.currentTimeMillis() - lastUpdateTime > 150L) {
                    targetBass = 0f
                }
                bassIntensity += (targetBass - bassIntensity) * 0.25f
            }
            if (frameDelay > 0) delay(frameDelay)
        }
    }

    val drawColor = remember(color) { getLightenedAccentColor(color) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cornerRadiusPx = 28.dp.toPx()
        val phoneCorner = CornerRadius(cornerRadiusPx, cornerRadiusPx)

        val alpha = (0.20f + bassIntensity * 0.70f).coerceIn(0.20f, 0.90f)
        val outerGlowWidth = 32.dp.toPx()
        val coreGlowWidth = 12.dp.toPx()

        drawRoundRect(
            color = drawColor.copy(alpha = alpha * 0.35f),
            topLeft = Offset(outerGlowWidth / 2f, outerGlowWidth / 2f),
            size = Size(size.width - outerGlowWidth, size.height - outerGlowWidth),
            cornerRadius = phoneCorner,
            style = Stroke(width = outerGlowWidth)
        )

        drawRoundRect(
            color = drawColor.copy(alpha = alpha),
            topLeft = Offset(coreGlowWidth / 2f, coreGlowWidth / 2f),
            size = Size(size.width - coreGlowWidth, size.height - coreGlowWidth),
            cornerRadius = phoneCorner,
            style = Stroke(width = coreGlowWidth)
        )
    }
}
