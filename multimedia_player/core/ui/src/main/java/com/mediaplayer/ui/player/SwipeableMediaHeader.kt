package com.mediaplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.Track

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SwipeableMediaHeader(
    playlist: List<Track>,           // Your full list of songs
    currentIndex: Int,               // The player's current playing index
    onTrackSelected: (Int) -> Unit,  // Callback to tell ViewModel to play new index
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = if (currentIndex >= 0) currentIndex else 0,
        pageCount = { playlist.size }
    )

    // BRIDGE 1: ExoPlayer changed the track (e.g. Song ended, or user clicked 'Next' button)
    // Action: Force the Pager to slide over to match ExoPlayer
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < playlist.size && pagerState.currentPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // BRIDGE 2: The User swiped the card with their finger
    // Action: Tell ExoPlayer to jump to the newly landed page index
    LaunchedEffect(pagerState.currentPage) {
        if (currentIndex >= 0 && pagerState.currentPage != currentIndex) {
            onTrackSelected(pagerState.currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        pageSpacing = 24.dp, // Creates the visual gap between Track A and Track B
    ) { page ->
        val track = playlist.getOrNull(page)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. The Swiping Album Art Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .shadow(18.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp)
            ) {
                GlideImage(
                    model = track?.albumArtUri,
                    contentDescription = "Album Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. The Swiping Metadata
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track?.title ?: "Select a Track",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
