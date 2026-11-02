package com.mediaplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaplayer.data.model.Track

@Composable
fun SwipeableTrackTitle(
    playlist: List<Track>,
    currentIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onToggleLyrics: () -> Unit,
    primaryTextColor: Color = Color.White,
    secondaryTextColor: Color = Color.White.copy(alpha = 0.8f),
    iconTintColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    // 🔥 THE FIX: Bulletproof bounds checking prevents the silent crash!
    val safeSize = playlist.size.coerceAtLeast(1)
    val safeIndex = currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
    
    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { safeSize }
    )

    // 🛑 THE MOUNTING MUZZLE
    // Traps the phantom "Page 0" layout emission during screen flips
    var hasMounted by remember { mutableStateOf(false) }

    // Bridge 1: External trigger changed the song (e.g., Track finished, or user hit Next button)
    LaunchedEffect(safeIndex) {
        if (safeIndex >= 0 && safeIndex < playlist.size && pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    // Bridge 2: User manually swiped the text -> Tell the ViewModel to jump tracks
    LaunchedEffect(pagerState.currentPage) {
        // 1. If this is the exact nano-second the sideways screen woke up, swallow the event and abort!
        if (!hasMounted) {
            hasMounted = true
            return@LaunchedEffect
        }

        // 2. Safe to listen to real human thumbs now
        if (safeIndex >= 0 && pagerState.currentPage != safeIndex && playlist.isNotEmpty()) {
            onTrackSelected(pagerState.currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        pageSpacing = 48.dp, // Wide gap so the titles don't blend together while passing
        verticalAlignment = Alignment.CenterVertically
    ) { page ->
        val track = playlist.getOrNull(page)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Expand the touch "hitbox" vertically so the user's thumb easily catches it
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Select a track",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 🔥 LYRICS BUTTON (White 12% background circle, white 90% icon)
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier
                    .zIndex(10f)
                    .background(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    tint = iconTintColor,
                    contentDescription = "Toggle Lyrics"
                )
            }
        }
    }
}
