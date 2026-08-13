package com.mediaplayer.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun LoopingVideoVisualizer(
    @RawRes videoResId: Int,
    isPlaying: Boolean, // 🔥 Added parameter
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize a lightweight, secondary ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = RawResourceDataSource.buildRawResourceUri(videoResId)
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE // Loop endlessly
            volume = 0f // Mute completely to avoid clashing with the main audio track
            prepare()
            // Don't auto-play immediately, let LaunchedEffect handle it
        }
    }

    // 🔥 THE FIX 3: Sync video playback with music playback
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Ensure the player is properly released to prevent memory leaks
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Hide all playback controls
                // Crop the video to fill the screen edges perfectly
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM 
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 0.99f
                // blendMode = BlendMode.Screen 
            }
    )
}
