package com.mediaplayer.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.isActive

// 1. Define our playback states
enum class PlaybackState {
    PAUSED, PLAYING, FAST_FORWARD, REWIND
}

@Composable
fun MusicPlayerScreen(
    playbackState: PlaybackState,
    albumArtPainter: Painter,
    codecInfo: String = "96.0 kHz • 3311 kbps • FLAC",
    onCodecTap: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 2. Adaptive Layout based on orientation
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Vinyl
            VinylAlbumCover(
                playbackState = playbackState,
                albumArtPainter = albumArtPainter,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f) // Keeps it perfectly round
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Right Side: Controls & Info
            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Placeholder for your title, progress bar, and play/pause controls
                Text("Now Playing", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                // The Tappable Codec Badge
                AudioCodecBadge(info = codecInfo, onClick = onCodecTap)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Top: Vinyl
            VinylAlbumCover(
                playbackState = playbackState,
                albumArtPainter = albumArtPainter,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
            )

            // Bottom: Controls & Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Now Playing", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                AudioCodecBadge(info = codecInfo, onClick = onCodecTap)
            }
        }
    }
}

@Composable
fun VinylAlbumCover(
    playbackState: PlaybackState,
    modifier: Modifier = Modifier.Companion,
    albumArtPainter: Painter
) {
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playbackState) {
        if (playbackState != PlaybackState.PAUSED) {
            var lastFrameTime = withFrameNanos { it }

            while (true) {
                withFrameNanos { frameTime ->
                    val deltaSeconds = (frameTime - lastFrameTime) / 1_000_000_000f

                    // Determine speed and direction based on state
                    val baseDegreesPerSecond = 90f
                    val speedMultiplier = when (playbackState) {
                        PlaybackState.PLAYING -> 1f
                        PlaybackState.FAST_FORWARD -> 4f // Spins 4x faster clockwise
                        PlaybackState.REWIND -> -4f      // Spins 4x faster counter-clockwise
                        PlaybackState.PAUSED -> 0f
                    }

                    rotation =
                        (rotation + (baseDegreesPerSecond * speedMultiplier * deltaSeconds)) % 360f
                    lastFrameTime = frameTime
                }
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .clip(CircleShape)
            .background(Color.Black)
            .drawBehind {
                val grooveColor = Color.DarkGray.copy(alpha = 0.5f)
                drawCircle(
                    color = grooveColor,
                    radius = size.minDimension * 0.45f,
                    style = Stroke(1.dp.toPx())
                )
                drawCircle(
                    color = grooveColor,
                    radius = size.minDimension * 0.40f,
                    style = Stroke(1.dp.toPx())
                )
                drawCircle(
                    color = grooveColor,
                    radius = size.minDimension * 0.35f,
                    style = Stroke(1.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = albumArtPainter,
            contentDescription = "Album Art",
            modifier = Modifier
                .fillMaxSize(0.65f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        )
    }
}

// 3. Tappable Codec Badge Component
@Composable
fun AudioCodecBadge(
    info: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Text(
            text = info,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
