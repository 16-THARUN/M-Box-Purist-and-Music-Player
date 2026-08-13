package com.mediaplayer.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaplayer.viewmodel.LibraryViewModel
import com.mediaplayer.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailsScreen(
    folderPath: String,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
    onTrackClick: () -> Unit
) {
    val tracks by libVm.getTracksForFolder(folderPath).collectAsState(initial = emptyList())
    val folderName = folderPath.substringAfterLast("/")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        TrackList(
            tracks = tracks,
            onTrackClick = { clickedTrack ->
                playerVm.playTracks(tracks, tracks.indexOf(clickedTrack))
                onTrackClick()
            },
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 120.dp
            ),
            header = {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                }
            }
        )
    }
}
