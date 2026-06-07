package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

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
    title: String,
    artist: String,
    albumArtUri: String?,       // Pass track.albumArtUri from PlayerViewModel
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art — falls back to a dark placeholder when URI is null
            GlideImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title + animated bars, then artist underneath
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        color = if (isPlaying)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface,
                        // Auto-scrolls when text overflows
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NowPlayingBars(isPlaying = isPlaying)
                }

                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Controls
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onNextClick) {
                Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next")
            }
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
