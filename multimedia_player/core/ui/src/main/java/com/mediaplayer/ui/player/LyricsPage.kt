package com.mediaplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaplayer.ui.components.LyricsContent
import com.mediaplayer.data.model.NowPlayingState
import com.mediaplayer.data.model.toTimeString

enum class LyricsViewMode {
    SYNCED,
    STATIC
}

@Composable
fun CustomDarkSegmentedSwitch(
    isSyncedSelected: Boolean,
    hasTimedLyrics: Boolean,
    accentColor: Color,
    height: Dp = 36.dp,
    onSelectMode: (LyricsViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .width(220.dp)
            .height(height)
            .clip(RoundedCornerShape(height / 2f))
            .background(Color.Black.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(height / 2f))
            .padding(2.dp)
    ) {
        // SYNCED TAB
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape((height - 4.dp) / 2f))
                .then(
                    if (isSyncedSelected) {
                        Modifier
                            .background(accentColor.copy(alpha = 0.25f))
                            .border(1.dp, accentColor.copy(alpha = 0.60f), RoundedCornerShape((height - 4.dp) / 2f))
                    } else Modifier
                )
                .clickable(enabled = hasTimedLyrics) { onSelectMode(LyricsViewMode.SYNCED) },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSyncedSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Synced",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSyncedSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = if (isSyncedSelected) Color.White else Color.White.copy(alpha = 0.60f)
                )
            }
        }

        // STATIC TAB
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape((height - 4.dp) / 2f))
                .then(
                    if (!isSyncedSelected) {
                        Modifier
                            .background(accentColor.copy(alpha = 0.25f))
                            .border(1.dp, accentColor.copy(alpha = 0.60f), RoundedCornerShape((height - 4.dp) / 2f))
                    } else Modifier
                )
                .clickable { onSelectMode(LyricsViewMode.STATIC) },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isSyncedSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Static",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (!isSyncedSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = if (!isSyncedSelected) Color.White else Color.White.copy(alpha = 0.60f)
                )
            }
        }
    }
}

@Composable
fun LyricsPage(
    state: NowPlayingState,
    bgColor: Color,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val lyrics = state.currentLyrics
    val hasTimedLyrics = remember(lyrics) { lyrics.any { it.startTimeMs > 0 } }
    var viewMode by remember(hasTimedLyrics) {
        mutableStateOf(if (hasTimedLyrics) LyricsViewMode.SYNCED else LyricsViewMode.STATIC)
    }
    var showEditDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor.copy(alpha = 0.45f),
                        Color.Black
                    )
                )
            )
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val availableHeight = maxHeight
            val isCompactScreen = availableHeight < 600.dp || isLandscape

            val headerHeight = if (isCompactScreen) 44.dp else 52.dp
            val switchHeight = if (isCompactScreen) 32.dp else 36.dp
            val verticalSpacer = if (isCompactScreen) 4.dp else 8.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. HEADER ROW (wrap content)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.30f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.currentTrack?.title ?: "Unknown Track",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.currentTrack?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.30f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Lyrics",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(verticalSpacer))

                // 2. SYNCD / STATIC SWITCH (wrap content, dark style)
                CustomDarkSegmentedSwitch(
                    isSyncedSelected = viewMode == LyricsViewMode.SYNCED,
                    hasTimedLyrics = hasTimedLyrics,
                    accentColor = accentColor,
                    height = switchHeight,
                    onSelectMode = { viewMode = it }
                )

                if (!hasTimedLyrics) {
                    Text(
                        text = "Timed lyrics not available",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(verticalSpacer))

                // 3. LYRICS LIST CONTAINER (weight(1f) takes ALL remaining space)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isLandscape) Modifier.widthIn(max = 600.dp)
                            else Modifier.fillMaxWidth()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LyricsContent(
                        lyrics = lyrics,
                        positionMs = state.positionMs,
                        isSyncedMode = viewMode == LyricsViewMode.SYNCED,
                        onSeekTo = onSeekTo,
                        activeLineColor = Color.White,
                        inactiveLineColor = Color.White.copy(alpha = 0.65f),
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(verticalSpacer))

                // 4. PROGRESS SECTION & TRANSPORT ROW (wrap content)
                Column(
                    modifier = Modifier
                        .then(
                            if (isLandscape) Modifier.widthIn(max = 600.dp)
                            else Modifier.fillMaxWidth()
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PremiumSeekSection(
                        state = state,
                        accentColor = accentColor,
                        timerTextColor = Color.White.copy(alpha = 0.70f),
                        onSeekTo = onSeekTo,
                        onShowAudioInfo = { }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Transport row: shuffle, previous, play/pause, next, repeat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (state.shuffleOn) accentColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = onTogglePlayPause,
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onCycleRepeatMode,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = when (state.repeatMode) {
                                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                    androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                    else -> Icons.Rounded.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) accentColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        val initialLrc = remember(lyrics) {
            lyrics.joinToString("\n") { line ->
                val m = line.startTimeMs / 1000 / 60
                val s = (line.startTimeMs / 1000) % 60
                val ms = (line.startTimeMs % 1000) / 10
                if (line.startTimeMs > 0) "[%02d:%02d.%02d]%s".format(m, s, ms, line.text)
                else line.text
            }
        }
        EditLyricsDialog(
            initialText = initialLrc,
            onDismiss = { showEditDialog = false },
            onSave = {
                onSaveLyrics(it)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun PremiumSeekSection(
    state: NowPlayingState,
    accentColor: Color,
    timerTextColor: Color = Color.White.copy(alpha = 0.70f),
    onSeekTo: (Long) -> Unit,
    onShowAudioInfo: () -> Unit = {}
) {
    if (state.seekBarStyle == "wavy") {
        com.mediaplayer.ui.components.WavyLineSeekBar(
            currentPositionMs = state.positionMs,
            totalDurationMs = state.durationMs,
            isPlaying = state.isPlaying,
            accentColor = accentColor,
            onSeek = onSeekTo
        )
    } else {
        com.mediaplayer.ui.components.PillSeekBar(
            currentPositionMs = state.positionMs,
            totalDurationMs = state.durationMs,
            accentColor = accentColor,
            onSeek = onSeekTo
        )
    }
}
