package com.mediaplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.AlbumRow
import com.mediaplayer.data.model.Track
import com.mediaplayer.viewmodel.LibraryViewModel
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.SortOrder
import kotlinx.coroutines.launch

private val TABS = listOf("Tracks", "Albums", "Artists", "Playlists", "Folders")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playerVm: PlayerViewModel = hiltViewModel(),
    libVm: LibraryViewModel = hiltViewModel(),
    onAlbumClick: (String) -> Unit = {},
    onFolderClick: (String) -> Unit = {}
) {
    val tracks by libVm.allTracks.collectAsState()
    val albums by libVm.albums.collectAsState()
    val artists by libVm.artists.collectAsState()
    val playlists by libVm.playlists.collectAsState()
    val scanState by libVm.scanState.collectAsState()
    val searchQuery by libVm.searchQuery.collectAsState()
    val searchResults by libVm.searchResults.collectAsState()
    val sortOrder by libVm.sortOrder.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.Sort, "Sort order")
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        onClick = {
                                            libVm.updateSortOrder(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOrder == order) {
                                                Icon(Icons.Rounded.Check, null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = libVm::scanLibrary) {
                            Icon(Icons.Rounded.Refresh, "Scan library")
                        }
                    }
                )

                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = libVm::updateSearch,
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search tracks, artists, albums…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { libVm.updateSearch("") }) {
                                Icon(Icons.Rounded.Clear, "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {}

                // Scan progress
                val s = scanState
                if (s is com.mediaplayer.data.scanner.MediaScanner.ScanState.Scanning) {
                    LinearProgressIndicator(
                        progress = if (s.total > 0) s.current.toFloat() / s.total else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Tabs
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    TABS.forEachIndexed { i, label ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (searchQuery.isNotBlank()) {
                // Search results override tab content
                TrackList(
                    tracks = searchResults,
                    onTrackClick = { track ->
                        playerVm.playTracks(searchResults, searchResults.indexOf(track))
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> TrackList(
                        tracks = tracks,
                        onTrackClick = { track ->
                            playerVm.playTracks(tracks, tracks.indexOf(track))
                        }
                    )
                    1 -> AlbumGrid(albums = albums, libVm = libVm, playerVm = playerVm, onAlbumClick = onAlbumClick)
                    2 -> ArtistList(artists = artists.map { it.artist }, libVm = libVm, playerVm = playerVm)
                    3 -> PlaylistList(playlists = playlists, libVm = libVm, playerVm = playerVm)
                    4 -> {
                        val folders by libVm.folders.collectAsState()
                        FolderList(folders = folders, libVm = libVm, playerVm = playerVm, onFolderClick = onFolderClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TrackList(tracks: List<Track>, onTrackClick: (Track) -> Unit) {
    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            ListItem(
                headlineContent = {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        "${track.artist} · ${track.album}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    GlideImage(
                        model = track.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp)
                    )
                },
                trailingContent = {
                    Text(
                        track.duration.toTimeDisplay(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onTrackClick(track) }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumGrid(
    albums: List<AlbumRow>,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onAlbumClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyColumn {
        items(albums) { album ->
            ListItem(
                headlineContent = { Text(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(album.artist, style = MaterialTheme.typography.bodySmall) },
                leadingContent = {
                    GlideImage(
                        model = album.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp)
                    )
                },
                modifier = Modifier.clickable {
                    onAlbumClick(album.album)
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<String>,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel
) {
    val scope = rememberCoroutineScope()
    LazyColumn {
        items(artists) { artist ->
            ListItem(
                headlineContent = { Text(artist) },
                leadingContent = {
                    Icon(Icons.Rounded.Person, null, modifier = Modifier.size(40.dp))
                },
                modifier = Modifier.clickable {
                    scope.launch {
                        libVm.getTracksForArtist(artist).collect { tracks ->
                            if (tracks.isNotEmpty()) playerVm.playTracks(tracks)
                        }
                    }
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<com.mediaplayer.data.model.Playlist>,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        libVm.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn {
        item {
            ListItem(
                headlineContent = { Text("New playlist") },
                leadingContent = {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable { showCreateDialog = true }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = { Text(playlist.name) },
                leadingContent = {
                    Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(40.dp))
                },
                modifier = Modifier.clickable {
                    // TODO: Navigate to Playlist Detail screen
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun FolderList(
    folders: List<com.mediaplayer.data.model.FolderRow>,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onFolderClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyColumn {
        items(folders) { folder ->
            ListItem(
                headlineContent = { Text(folder.folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { 
                    Text("${folder.trackCount} tracks · ${folder.folderPath}", 
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                leadingContent = {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    onFolderClick(folder.folderPath)
                }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

private fun Long.toTimeDisplay(): String {
    val m = this / 1000 / 60
    val s = this / 1000 % 60
    return "%d:%02d".format(m, s)
}