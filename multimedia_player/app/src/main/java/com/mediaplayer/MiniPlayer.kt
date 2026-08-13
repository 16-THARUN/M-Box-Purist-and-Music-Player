package com.mediaplayer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.mediaplayer.data.model.Track

// ─────────────────────────────────────────────────────────────────────────────
// NowPlayingBars
//   Three animated bars that bounce when isPlaying=true, shrink when paused.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NowPlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "now_playing_bars")

    val bar1Height by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier
            .height(18.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val barColor = MaterialTheme.colorScheme.primary
        val h1 = if (isPlaying) bar1Height else 0.2f
        val h2 = if (isPlaying) bar2Height else 0.2f
        val h3 = if (isPlaying) bar3Height else 0.2f

        Box(Modifier.width(4.dp).fillMaxHeight(h1).clip(RoundedCornerShape(2.dp)).background(barColor))
        Box(Modifier.width(4.dp).fillMaxHeight(h2).clip(RoundedCornerShape(2.dp)).background(barColor))
        Box(Modifier.width(4.dp).fillMaxHeight(h3).clip(RoundedCornerShape(2.dp)).background(barColor))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniPlayer
//   Persistent mini-player card shown at the bottom of all screens except the
//   full PlayerScreen.  Tap anywhere to expand; controls play/pause and next.
//
//   Wire up real state from PlayerViewModel at the call-site in MainActivity /
//   MainNavigation (see Scaffold snippet below).
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onClick: () -> Unit
) {
    if (track == null) return

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragEnd = {
                        val xThreshold = 100f
                        val yThreshold = 50f

                        if (dragOffsetX > xThreshold) {
                            onPreviousClick()
                        } else if (dragOffsetX < -xThreshold) {
                            onNextClick()
                        } else if (dragOffsetY < -yThreshold) {
                            onClick()
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY += dragAmount.y
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                GlideImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title + animated bars, then artist underneath
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            color = if (isPlaying)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        NowPlayingBars(isPlaying = isPlaying)
                    }

                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play/Pause Action
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Toggle Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val progressRatio = if (totalDurationMs > 0) {
                (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFFE65100),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Square
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scaffold integration — place this in MainNavigation() inside MainActivity.kt
// replacing your existing Scaffold { bottomBar = … } block.
//
//   val playerState by playerVm.state.collectAsState()    // hoist PlayerViewModel
//
//   Scaffold(
//       bottomBar = {
//           Column {
//               if (currentRoute != Routes.PLAYER) {
//                   MiniPlayer(
//                       title        = playerState.currentTrack?.title  ?: "Nothing playing",
//                       artist       = playerState.currentTrack?.artist ?: "",
//                       albumArtUri  = playerState.currentTrack?.albumArtUri,
//                       isPlaying    = playerState.isPlaying,
//                       onPlayPauseClick = playerVm::togglePlayPause,
//                       onNextClick      = playerVm::skipNext,
//                       onExpandClick    = {
//                           navController.navigate(Routes.PLAYER) { launchSingleTop = true }
//                       }
//                   )
//               }
//               if (currentRoute in bottomBarRoutes) {
//                   NavigationBar { /* … existing tabs … */ }
//               }
//           }
//       }
//   ) { … }
// ─────────────────────────────────────────────────────────────────────────────
