package com.mediaplayer.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.AlbumRow
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.scanner.MediaScanner
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
    onFolderClick: (String) -> Unit = {},
    onTrackClick: () -> Unit = {}
) {
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
            if (isGranted) {
                libVm.scanLibrary()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(audioPermission)
        } else {
            libVm.scanLibrary()
        }
    }

    if (!hasAudioPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Music player requires storage access.", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To show your local music library, we need permission to read audio files from your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionLauncher.launch(audioPermission) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    val tracks by libVm.allTracks.collectAsState()
    val albums by libVm.albums.collectAsState()
    val artists by libVm.artists.collectAsState()
    val playlists by libVm.playlists.collectAsState()
    val folders by libVm.folders.collectAsState()
    val searchQuery by libVm.searchQuery.collectAsState()
    val searchResults by libVm.searchResults.collectAsState()
    val isRefreshing by libVm.isRefreshing.collectAsStateWithLifecycle()
    val scanningPath by libVm.scanningPath.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedFolder != null) {
        selectedFolder = null
    }

    if (selectedFolder != null) {
        FolderDetailsScreen(
            folderPath = selectedFolder!!,
            libVm = libVm,
            playerVm = playerVm,
            onBack = { selectedFolder = null },
            onTrackClick = onTrackClick
        )
    } else {
        val pullToRefreshState = rememberPullToRefreshState()

        Column(modifier = Modifier.fillMaxSize()) {
            LibraryScreenHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = libVm::updateSearch,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pullToRefresh(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        threshold = 48.dp,
                        onRefresh = { libVm.refreshLibrary() }
                    )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (searchQuery.isNotBlank()) {
                        TrackList(
                            tracks = searchResults,
                            onTrackClick = { track ->
                                playerVm.playTracks(searchResults, searchResults.indexOf(track))
                                onTrackClick()
                            }
                        )
                    } else {
                        when (selectedTab) {
                            0 -> TrackList(
                                tracks = tracks,
                                onTrackClick = { track ->
                                    playerVm.playTracks(tracks, tracks.indexOf(track))
                                    onTrackClick()
                                }
                            )
                            1 -> AlbumGrid(albums = albums, libVm = libVm, playerVm = playerVm, onAlbumClick = onAlbumClick)
                            2 -> ArtistList(artists = artists.map { it.artist }, libVm = libVm, playerVm = playerVm)
                            3 -> PlaylistList(playlists = playlists, libVm = libVm, playerVm = playerVm)
                            4 -> {
                                FolderList(
                                    folders = folders,
                                    libVm = libVm,
                                    playerVm = playerVm,
                                    onFolderClick = { selectedFolder = it }
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isRefreshing && scanningPath != null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = scanningPath ?: "",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                    threshold = 48.dp
                )
            }
        }
    }
}

@Composable
fun LibraryScreenHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(end = 16.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search tracks, artists, albums...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search tracks, artists, albums...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            divider = {}, 
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TrackList(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            if (header != null) {
                item { header() }
            }

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

        if (tracks.size > 5) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight(0.8f)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                    .pointerInput(tracks) {
                        detectVerticalDragGestures { change, dragAmount ->
                            val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                            val totalItems = listState.layoutInfo.totalItemsCount
                            val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                            scope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                            change.consume()
                        }
                    }
            ) {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val totalItems = tracks.size
                val thumbFraction = if (totalItems > 0) firstVisibleIndex.toFloat() / totalItems else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.1f)
                        .graphicsLayer {
                            translationY = thumbFraction * size.height
                        }
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
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
                            if (tracks.isNotEmpty()) {
                                playerVm.playTracks(tracks)
                                playerVm.expandPlayer()
                            }
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
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No music folders found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
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
}

private fun Long.toTimeDisplay(): String {
    val m = this / 1000 / 60
    val s = this / 1000 % 60
    return "%d:%02d".format(m, s)
}
