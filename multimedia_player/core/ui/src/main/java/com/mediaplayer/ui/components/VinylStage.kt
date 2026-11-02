package com.mediaplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaplayer.ui.player.CircularSpectrumVisualizer
import com.mediaplayer.util.DeviceState
import com.mediaplayer.data.model.NowPlayingState
import com.mediaplayer.data.model.PlayerController
import kotlinx.coroutines.flow.StateFlow

val ringVisualizerModes = setOf(
    "circular_rings",
    "orbiting_stardust",
    "acoustic_ripples",
    "groove_bleed",
    "liquid_spline",
    "glass_bars",
    "bottom_glass_bars",
    "symmetrical_waves", "1", "2",
    "particle_swarms", "5", "8",
    "digital_bars", "6",
    "oscilloscope", "plain_bars", "7"
)

@Composable
fun VinylStage(
    activeVisualizerMode: String,
    fftFlow: StateFlow<FloatArray>,
    accentColor: Color,
    deviceState: DeviceState,
    isPlaying: Boolean,
    imageUri: String?,
    playerState: NowPlayingState,
    onSeekTo: (Long) -> Unit,
    onPauseForDjHold: () -> Unit = {},
    onResumeAfterDjHold: () -> Unit = {},
    showLyrics: Boolean,
    vinylDiameterPx: Float,
    stageSizePx: Float,
    onStagePositioned: (Offset, Float) -> Unit,
    playerVm: PlayerController? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val vinylDiameterDp = with(density) { vinylDiameterPx.toDp() }
    val vinylRadiusPx = vinylDiameterPx / 2f
    var displayMode by remember { mutableStateOf(ArtworkDisplayMode.VINYL) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val stageCenterInRoot = coords.positionInRoot() + Offset(coords.size.width / 2f, coords.size.height / 2f)
                onStagePositioned(stageCenterInRoot, vinylRadiusPx)
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Ring visualizer canvas (sibling in SAME Box as vinyl disc)
        if (!showLyrics && displayMode == ArtworkDisplayMode.VINYL && activeVisualizerMode in ringVisualizerModes) {
            RingVisualizerLayer(
                activeVisualizerMode = activeVisualizerMode,
                fftFlow = fftFlow,
                accentColor = accentColor,
                deviceState = deviceState,
                vinylRadiusPx = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Vinyl disc & label (Artwork)
        NowPlayingArtwork(
            imageUri = imageUri,
            isPlaying = isPlaying,
            playerState = playerState,
            onSeekTo = onSeekTo,
            onPauseForDjHold = onPauseForDjHold,
            onResumeAfterDjHold = onResumeAfterDjHold,
            displayMode = displayMode,
            onDisplayModeChange = { displayMode = it },
            playerVm = playerVm,
            modifier = Modifier.size(vinylDiameterDp)
        )
    }
}

@Composable
fun RingVisualizerLayer(
    activeVisualizerMode: String,
    fftFlow: StateFlow<FloatArray>,
    accentColor: Color,
    deviceState: DeviceState,
    vinylRadiusPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val magnitudes by fftFlow.collectAsStateWithLifecycle()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (activeVisualizerMode) {
            "circular_rings" -> CircularSpectrumVisualizer(
                fftData = magnitudes,
                accentColor = accentColor,
                deviceState = deviceState,
                vinylScale = 0.85f,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "orbiting_stardust" -> OrbitingStardustVisualizer(
                fftData = magnitudes,
                accentColor = accentColor,
                deviceState = deviceState,
                vinylScale = 0.85f,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "acoustic_ripples" -> AcousticRipplesVisualizer(
                fftData = magnitudes,
                accentColor = accentColor,
                deviceState = deviceState,
                vinylScale = 0.85f,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "groove_bleed" -> VinylGrooveBleedVisualizer(
                fftFlow = fftFlow,
                color = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "liquid_spline" -> LiquidSplineVisualizer(
                fftFlow = fftFlow,
                color = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "hex_grid" -> HexagonalAudioGridVisualizer(
                fftFlow = fftFlow,
                color = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "glass_bars", "bottom_glass_bars" -> BottomGlassBars(
                fftFlow = fftFlow,
                accentColor = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "symmetrical_waves", "1", "2" -> SymmetricalVisualizer(
                fftFlow = fftFlow,
                barColor = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "particle_swarms", "5", "8" -> SpectrumDotsVisualizer(
                fftFlow = fftFlow,
                dotColor = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "digital_bars", "6" -> NarrowVisualizer(
                fftFlow = fftFlow,
                barColor = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
            "oscilloscope", "plain_bars", "7" -> PlainVisualizer(
                fftFlow = fftFlow,
                barColor = accentColor,
                deviceState = deviceState,
                vinylRadiusOverride = vinylRadiusPx,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
