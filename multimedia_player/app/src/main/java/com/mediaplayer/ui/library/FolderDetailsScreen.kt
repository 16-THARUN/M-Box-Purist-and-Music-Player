package com.mediaplayer.ui.library

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaplayer.data.prefs.ListType
import com.mediaplayer.ui.components.HeroHeader
import com.mediaplayer.ui.components.ListOptionsBottomSheet
import com.mediaplayer.ui.components.SortOption
import com.mediaplayer.viewmodel.LibraryViewModel
import com.mediaplayer.viewmodel.PlayerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailsScreen(
    folderPath: String,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
    onTrackClick: () -> Unit
) {
    val context = LocalContext.current
    val tracks by libVm.getTracksForFolder(folderPath).collectAsState(initial = emptyList())
    val playerState by playerVm.state.collectAsState()
    val folderName = folderPath.substringAfterLast("/").ifBlank { "Folder" }

    val listState = rememberLazyListState()
    val isScrolledPastHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250 }
    }

    val isFolderPlaying = playerState.playingFromName == folderName || playerState.playingFromPath == folderPath
    val isPlayingCurrentFolder = isFolderPlaying && playerState.isPlaying

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showListOptionsSheet by remember { mutableStateOf(false) }
    var selectedSortOption by remember { mutableStateOf(SortOption.TITLE) }
    var isAscendingOrder by remember { mutableStateOf(true) }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create subfolder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        val newDir = File(folderPath, newFolderName)
                        if (!newDir.exists()) {
                            newDir.mkdirs()
                        }
                        Toast.makeText(context, "Created folder: ${newDir.name}", Toast.LENGTH_SHORT).show()
                        libVm.refreshLibrary()
                        newFolderName = ""
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SuggestionChip(
                            onClick = onBack,
                            label = { Text("↑ Library") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = folderName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Pinned Play/Pause FAB when header is scrolled away
                    if (isScrolledPastHeader) {
                        FloatingActionButton(
                            onClick = {
                                if (isFolderPlaying) {
                                    playerVm.togglePlayPause()
                                } else if (tracks.isNotEmpty()) {
                                    playerVm.playTracks(tracks, 0, "Folder", folderName, folderPath)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingCurrentFolder) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlayingCurrentFolder) "Pause" else "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, "Create Subfolder")
                    }
                    IconButton(onClick = { showListOptionsSheet = true }) {
                        Icon(Icons.Rounded.MoreVert, "List Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isScrolledPastHeader) MaterialTheme.colorScheme.surface else Color.Transparent
                )
            )
        }
    ) { padding ->
        TrackList(
            tracks = tracks,
            listState = listState,
            playerState = playerState,
            accentColor = MaterialTheme.colorScheme.primary,
            onTrackClick = { clickedTrack ->
                playerVm.playTracks(tracks, tracks.indexOf(clickedTrack), "Folder", folderName, folderPath)
                onTrackClick()
            },
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 120.dp
            ),
            header = {
                val totalDurationMs = tracks.sumOf { it.duration }
                val firstArt = tracks.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri

                HeroHeader(
                    title = folderName,
                    subtitle = folderPath,
                    trackCount = tracks.size,
                    totalDurationMs = totalDurationMs,
                    artworkUri = firstArt,
                    onShuffleClick = { playerVm.playTracks(tracks.shuffled(), 0, "Folder", folderName, folderPath) },
                    onPlayClick = {
                        if (isFolderPlaying) {
                            playerVm.togglePlayPause()
                        } else if (tracks.isNotEmpty()) {
                            playerVm.playTracks(tracks, 0, "Folder", folderName, folderPath)
                        }
                    },
                    onSearchClick = { },
                    onSelectClick = { },
                    onOverflowClick = { showListOptionsSheet = true }
                )
            }
        )
    }

    if (showListOptionsSheet) {
        ListOptionsBottomSheet(
            listType = ListType.FOLDER_SONGS,
            currentSort = selectedSortOption,
            isReverse = !isAscendingOrder,
            onSortSelected = { option ->
                selectedSortOption = option
            },
            onReverseToggled = { isAscendingOrder = !it },
            onOpenSettings = { },
            onDismiss = { showListOptionsSheet = false }
        )
    }
}
