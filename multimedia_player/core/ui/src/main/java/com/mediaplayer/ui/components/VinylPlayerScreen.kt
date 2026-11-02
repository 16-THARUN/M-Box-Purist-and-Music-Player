package com.mediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VinylPlayerScreen(
    albumArt: Painter,
    isPlaying: Boolean,
    fftFlow: StateFlow<FloatArray>,
    deviceState: DeviceState
) {
    val fftData by fftFlow.collectAsState()
    val avgAmplitude = remember(fftData) {
        if (fftData.isEmpty()) 0f else fftData.map { kotlin.math.abs(it) }.average().toFloat().coerceIn(0f, 1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        // Disc diameter is 80% of the smallest dimension; radius is 40%
        val discRadiusDp = minOf(maxWidth, maxHeight) * 0.4f
        val radiusPx = with(LocalDensity.current) { discRadiusDp.toPx() }

        PlainVisualizer(
            fftFlow = fftFlow,
            barColor = Color(0xFFE55A39), // Orange accent from your screenshot
            deviceState = deviceState,
            vinylRadiusOverride = radiusPx,
            modifier = Modifier.fillMaxSize()
        )

        VinylDisc(
            albumArt = albumArt,
            isPlaying = isPlaying,
            amplitude = avgAmplitude,
            modifier = Modifier.fillMaxSize()
        )
    }
}
