package com.mediaplayer.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.viewmodel.LibraryViewModel
import com.mediaplayer.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun AlbumDetailsScreen(
    albumName: String,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onBack: () -> Unit
) {
    val tracks by libVm.getTracksForAlbum(albumName).collectAsState(initial = emptyList())
    val albumInfo = tracks.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (tracks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { playerVm.playTracks(tracks) },
                    icon = { Icon(Icons.Rounded.PlayArrow, null) },
                    text = { Text("Play All") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Album Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                GlideImage(
                    model = albumInfo?.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = albumInfo?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Track List
            TrackList(
                tracks = tracks,
                onTrackClick = { clickedTrack ->
                    playerVm.playTracks(tracks, tracks.indexOf(clickedTrack))
                }
            )
        }
    }
}
