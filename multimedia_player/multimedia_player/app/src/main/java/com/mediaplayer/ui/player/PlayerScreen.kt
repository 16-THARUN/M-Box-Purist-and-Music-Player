package com.mediaplayer.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.ui.components.CdPlayerAlbumArt
import com.mediaplayer.ui.theme.MBoxOrange
import com.mediaplayer.ui.theme.MBoxSphereGradient
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.toTimeString
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// PlayerScreen
//
// Changes merged from conversation snippets:
//   1. Top-bar now shows "PLAYING FROM LIBRARY" label + KeyboardArrowDown +
//      Tune (EQ) button – matching the PlayerScreen UI mockup.
//   2. Title uses basicMarquee() so long names scroll instead of truncating.
//   3. Favorite (FavoriteBorder) icon added next to track info.
//   4. Shuffle / repeat icon tint reflects active state (primary vs muted).
//   5. All real ViewModel wiring retained from the existing project file.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    onOpenEq: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val track = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse / down-chevron
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                "PLAYING FROM LIBRARY",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp
            )

            // EQ shortcut — tinted primary when EQ is on
            IconButton(onClick = onOpenEq) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "Equalizer",
                    tint = if (state.eqEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        LocalContentColor.current
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── CD Album Art ──────────────────────────────────────────────────────
        CdPlayerAlbumArt(
            albumArtUri = track?.albumArtUri,
            isPlaying = state.isPlaying
        )

        Spacer(Modifier.height(36.dp))

        // ── Title + artist + favorite ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // basicMarquee auto-scrolls long titles
                Text(
                    text = track?.title ?: "Song title",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = listOfNotNull(track?.artist, track?.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { /* TODO: toggle favourite */ }) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favourite")
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Seek bar ──────────────────────────────────────────────────────────
        Slider(
            value = if (state.durationMs > 0)
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            else 0f,
            onValueChange = { vm.seekTo((it * state.durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                state.positionMs.toTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                state.durationMs.toTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Playback controls ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle — tinted primary when active
            IconButton(onClick = vm::toggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleOn)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Previous
            IconButton(onClick = vm::skipPrevious, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // The 3D Glossy "m" Sphere Play Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MBoxSphereGradient) // Applies the 3D orange gloss
                    .clickable { vm.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(40.dp),
                    tint = androidx.compose.ui.graphics.Color.White // The stark white contrasting icon
                )
            }

            // Next
            IconButton(onClick = vm::skipNext, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Repeat — tinted primary when active, RepeatOne icon for single-track
            IconButton(onClick = vm::cycleRepeatMode) {
                Icon(
                    imageVector = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Audio metadata chips ───────────────────────────────────────────────
        if (track != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TrackInfoChip(label = "Bitrate",     value = "${track.bitrate / 1000} kbps")
                TrackInfoChip(label = "Sample rate", value = "${track.sampleRate / 1000} kHz")
                TrackInfoChip(label = "Format",      value = track.mimeType.substringAfterLast("/").uppercase())
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TrackInfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
