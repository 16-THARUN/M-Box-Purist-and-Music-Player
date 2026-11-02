package com.mediaplayer.ui.components

import android.graphics.Color
import android.net.Uri
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mbox.core.ui.R

@OptIn(UnstableApi::class)
@Composable
fun LoopingVideoVisualizer(
    videoUrl: String? = null,
    @RawRes videoResId: Int? = null,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isFirstFrameRendered by remember { mutableStateOf(false) }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 0.60f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "VideoFadeIn"
    )

    val exoPlayer = remember(videoUrl, videoResId) {
        ExoPlayer.Builder(context).build().apply {
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            val uri = when {
                !videoUrl.isNullOrBlank() -> Uri.parse(videoUrl)
                videoResId != null -> RawResourceDataSource.buildRawResourceUri(videoResId)
                else -> Uri.EMPTY
            }
            if (uri != Uri.EMPTY) {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
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
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

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
            .graphicsLayer {
                alpha = videoAlpha
            }
    )
}
