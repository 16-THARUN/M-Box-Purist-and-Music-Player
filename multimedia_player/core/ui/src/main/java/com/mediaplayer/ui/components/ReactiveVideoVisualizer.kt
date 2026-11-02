package com.mediaplayer.ui.components

import android.content.res.Configuration
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mbox.core.ui.R
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.flow.StateFlow

enum class ReactiveVideoStyle {
    LENS_FLARE,
    PARTICLE_GRID
}

@OptIn(UnstableApi::class)
@Composable
fun ReactiveVideoVisualizer(
    style: ReactiveVideoStyle,
    fftFlow: StateFlow<FloatArray>,
    isPlaying: Boolean,
    deviceState: DeviceState,
    centerOffset: Offset? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    @RawRes val targetResId = remember(style, isLandscape) {
        when (style) {
            ReactiveVideoStyle.LENS_FLARE -> {
                if (isLandscape) R.raw.vis_lens_flare_landscape
                else R.raw.vis_lens_flare_portrait
            }
            ReactiveVideoStyle.PARTICLE_GRID -> {
                if (isLandscape) R.raw.vis_particle_grid_landscape
                else R.raw.vis_particle_grid_portrait
            }
        }
    }

    var isFirstFrameRendered by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }

    LaunchedEffect(targetResId) {
        val lastPos = if (exoPlayer.playbackState != Player.STATE_IDLE) exoPlayer.currentPosition else 0L
        val uri = RawResourceDataSource.buildRawResourceUri(targetResId)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        if (lastPos > 0) {
            exoPlayer.seekTo(lastPos)
        }
        if (isPlaying) {
            exoPlayer.play()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    val magnitudes by fftFlow.collectAsStateWithLifecycle()
    val autoGainSmoother = remember { AutoGainBandSmoother(32, 12f, 110f) }
    val animatedBands = remember { FloatArray(32) }

    var targetScale by remember { mutableFloatStateOf(1.00f) }
    var targetOpacity by remember { mutableFloatStateOf(0.90f) }

    val isReduceMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
            ) == 0f ||
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(magnitudes, style, isReduceMotion) {
        if (isReduceMotion) {
            targetScale = 1.00f
            targetOpacity = 0.85f
            return@LaunchedEffect
        }

        if (magnitudes.isNotEmpty()) {
            val len = magnitudes.size
            val raw32 = FloatArray(32) { i ->
                magnitudes[i.coerceIn(0, len - 1)]
            }
            autoGainSmoother.process(raw32, 16f, animatedBands)

            when (style) {
                ReactiveVideoStyle.LENS_FLARE -> {
                    var highSum = 0f
                    for (i in 16..31) highSum += animatedBands[i]
                    val highEnergy = (highSum / 16f).coerceIn(0f, 1f)

                    targetScale = 1.00f + (highEnergy * 0.03f)
                    targetOpacity = 0.80f + (highEnergy * 0.20f)
                }
                ReactiveVideoStyle.PARTICLE_GRID -> {
                    var bassSum = 0f
                    for (i in 0..4) bassSum += animatedBands[i]
                    val bassEnergy = (bassSum / 5f).coerceIn(0f, 1f)

                    var totalSum = 0f
                    for (i in 0..31) totalSum += animatedBands[i]
                    val overallEnergy = (totalSum / 32f).coerceIn(0f, 1f)

                    targetScale = 1.00f + (bassEnergy * 0.05f)
                    targetOpacity = 0.75f + (overallEnergy * 0.25f)
                }
            }
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = if (style == ReactiveVideoStyle.LENS_FLARE) 250 else 300),
        label = "VideoScale"
    )

    val animatedOpacity by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) targetOpacity else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "VideoOpacity"
    )

    var layoutWidth by remember { mutableFloatStateOf(1f) }
    var layoutHeight by remember { mutableFloatStateOf(1f) }

    val pivotX = (centerOffset?.x ?: (layoutWidth / 2f)).coerceIn(0f, layoutWidth)
    val pivotY = (centerOffset?.y ?: (layoutHeight / 2f)).coerceIn(0f, layoutHeight)
    val originX = if (layoutWidth > 0f) pivotX / layoutWidth else 0.5f
    val originY = if (layoutHeight > 0f) pivotY / layoutHeight else 0.5f

    AndroidView(
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(ctx, R.style.Theme_MediaPlayer)
            PlayerView(themedContext).apply {
                player = exoPlayer
                useController = false
                setShutterBackgroundColor(Color.TRANSPARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                layoutWidth = coords.size.width.toFloat()
                layoutHeight = coords.size.height.toFloat()
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(originX, originY)
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedOpacity
            }
    )
}
