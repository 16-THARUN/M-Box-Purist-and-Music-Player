package com.mediaplayer.ui.library

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.*
import com.mediaplayer.data.prefs.ListType
import com.mediaplayer.data.repository.FolderPreferencesRepository
import com.mediaplayer.data.scanner.LibrarySyncWorker
import com.mediaplayer.ui.components.*
import com.mediaplayer.util.FileRelocationManager
import com.mediaplayer.viewmodel.LibraryViewModel
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.toNowPlayingState
import com.mediaplayer.data.model.NowPlayingState
import com.mediaplayer.data.model.CastConnectionState
import com.mediaplayer.viewmodel.SortOrder
import kotlinx.coroutines.launch

private val TABS = listOf("Tracks", "Albums", "Artists", "Playlists", "Folders", "Hierarchy", "Album Artists", "Genres", "Years", "Composers")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onAlbumClick: (String) -> Unit = {},
    onFolderClick: (String) -> Unit = {},
    onTrackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) libVm.refreshLibrary()
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
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
    val playerState by playerVm.state.collectAsState()

    val folderRepo = remember { FolderPreferencesRepository(context) }
    val savedFolders by folderRepo.selectedFoldersFlow.collectAsState(initial = emptySet())

    val genres by libVm.genres.collectAsState()
    val years by libVm.years.collectAsState()
    val composers by libVm.composers.collectAsState()
    val albumArtists by libVm.albumArtists.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedGroupType by remember { mutableStateOf<String?>(null) }
    var selectedGroupValue by remember { mutableStateOf<String?>(null) }

    val tabStates = remember { Array(10) { LazyListState() } }

    BackHandler(enabled = selectedGroupValue != null) {
        selectedGroupType = null
        selectedGroupValue = null
    }

    val groupTracksFlow = remember(selectedGroupType, selectedGroupValue) {
        when (selectedGroupType) {
            "artist" -> selectedGroupValue?.let { libVm.getTracksForArtist(it) }
            "genre" -> selectedGroupValue?.let { libVm.getTracksForGenre(it) }
            "year" -> selectedGroupValue?.toIntOrNull()?.let { libVm.getTracksForYear(it) }
            "composer" -> selectedGroupValue?.let { libVm.getTracksForComposer(it) }
            else -> null
        }
    }
    val groupTracksState = groupTracksFlow?.collectAsState(initial = emptyList())
    val groupTracks = groupTracksState?.value ?: emptyList()

    var showListOptionsSheet by remember { mutableStateOf(false) }
    var showFolderPickerSheet by remember { mutableStateOf(false) }
    var showFolderSelection by remember { mutableStateOf(false) }
    var folderPickerActionTitle by remember { mutableStateOf("Move here") }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedSortOption by remember { mutableStateOf(SortOption.TITLE) }
    var isAscendingOrder by remember { mutableStateOf(true) }

    val pullToRefreshState = rememberPullToRefreshState()
    val accentColor = MaterialTheme.colorScheme.primary

    if (selectedGroupValue != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuggestionChip(
                                onClick = {
                                    selectedGroupType = null
                                    selectedGroupValue = null
                                },
                                label = { Text("↑ Library") },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(selectedGroupValue!!, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                )
            }
        ) { padding ->
            TrackList(
                tracks = groupTracks,
                playerState = playerState,
                accentColor = accentColor,
                onTrackClick = { clickedTrack ->
                    playerVm.playTracks(groupTracks, groupTracks.indexOf(clickedTrack), selectedGroupType?.capitalize() ?: "Library", selectedGroupValue ?: "")
                    onTrackClick()
                },
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = 120.dp
                ),
                header = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = selectedGroupValue!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${groupTracks.size} tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                    }
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            var showPlayOnSheet by remember { mutableStateOf(false) }

            LibraryScreenHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = libVm::updateSearch,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                onOpenListOptions = { showListOptionsSheet = true },
                onOpenSelectFolders = { showFolderSelection = true },
                onRescanLibrary = { libVm.refreshLibrary() },
                playerState = playerState,
                onShowCastSheet = { showPlayOnSheet = true }
            )

            if (showPlayOnSheet) {
                PlayOnDeviceBottomSheet(
                    playerState = playerState?.toNowPlayingState() ?: NowPlayingState(),
                    onSelectOutputDevice = playerVm::selectOutputDevice,
                    onOpenOutputSettings = { },
                    onSelectCastRoute = { },
                    castRoutes = rememberCastRoutes(),
                    onDismiss = { showPlayOnSheet = false }
                )
            }

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
                            playerState = playerState,
                            accentColor = accentColor,
                            onTrackClick = { track ->
                                playerVm.playTracks(searchResults, searchResults.indexOf(track), "Search", searchQuery)
                                onTrackClick()
                            }
                        )
                    } else {
                        when (selectedTab) {
                            0 -> TrackList(
                                tracks = tracks,
                                listState = tabStates[0],
                                playerState = playerState,
                                accentColor = accentColor,
                                isSelectionMode = isMultiSelectMode,
                                selectedTrackIds = selectedTrackIds,
                                onTrackSelectionToggled = { t ->
                                    selectedTrackIds = if (selectedTrackIds.contains(t.id)) selectedTrackIds - t.id else selectedTrackIds + t.id
                                },
                                onTrackClick = { track ->
                                    if (isMultiSelectMode) {
                                        selectedTrackIds = if (selectedTrackIds.contains(track.id)) selectedTrackIds - track.id else selectedTrackIds + track.id
                                    } else {
                                        playerVm.playTracks(tracks, tracks.indexOf(track), "All Songs", "All Songs")
                                        onTrackClick()
                                    }
                                }
                            )
                            1 -> AlbumGrid(albums = albums, listState = tabStates[1], playerState = playerState, accentColor = accentColor, onAlbumClick = onAlbumClick)
                            2 -> ArtistList(artists = artists, listState = tabStates[2], playerState = playerState, accentColor = accentColor, onGroupClick = { type, valStr -> selectedGroupType = type; selectedGroupValue = valStr })
                            3 -> PlaylistList(playlists = playlists, listState = tabStates[3], playerState = playerState, accentColor = accentColor, libVm = libVm, playerVm = playerVm)
                            4 -> FolderList(folders = folders, listState = tabStates[4], playerState = playerState, accentColor = accentColor, libVm = libVm, playerVm = playerVm, onFolderClick = onFolderClick)
                            5 -> FolderHierarchyView(folders = folders, playerState = playerState, accentColor = accentColor, onFolderClick = onFolderClick)
                            6 -> AlbumArtistList(albumArtists = albumArtists, listState = tabStates[6], playerState = playerState, accentColor = accentColor, onGroupClick = { type, valStr -> selectedGroupType = type; selectedGroupValue = valStr })
                            7 -> GenreList(genres = genres, listState = tabStates[7], playerState = playerState, accentColor = accentColor, onGroupClick = { type, valStr -> selectedGroupType = type; selectedGroupValue = valStr })
                            8 -> YearList(years = years, listState = tabStates[8], playerState = playerState, accentColor = accentColor, onGroupClick = { type, valStr -> selectedGroupType = type; selectedGroupValue = valStr })
                            9 -> ComposerList(composers = composers, listState = tabStates[9], playerState = playerState, accentColor = accentColor, onGroupClick = { type, valStr -> selectedGroupType = type; selectedGroupValue = valStr })
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

        if (showListOptionsSheet) {
            val currentListType = when (selectedTab) {
                0 -> ListType.ALL_SONGS
                1 -> ListType.ALBUMS
                2 -> ListType.ARTISTS
                3 -> ListType.PLAYLISTS
                4 -> ListType.FOLDERS
                5 -> ListType.FOLDERS_HIERARCHY
                6 -> ListType.ALBUM_ARTISTS
                7 -> ListType.GENRES
                8 -> ListType.YEARS
                9 -> ListType.COMPOSERS
                else -> ListType.ALL_SONGS
            }

            ListOptionsBottomSheet(
                listType = currentListType,
                currentSort = selectedSortOption,
                isReverse = !isAscendingOrder,
                onSortSelected = { option ->
                    selectedSortOption = option
                    libVm.updateSortOrder(when (option) {
                        SortOption.TRACK_NUMBER -> SortOrder.TRACK_NUMBER
                        SortOption.TITLE -> SortOrder.TITLE
                        SortOption.FILENAME -> SortOrder.FILENAME
                        SortOption.YEAR -> SortOrder.YEAR
                        else -> SortOrder.DATE_ADDED
                    })
                },
                onReverseToggled = { isAscendingOrder = !it },
                onOpenSettings = { },
                onDismiss = { showListOptionsSheet = false }
            )
        }
    }
}

@Composable
fun LibraryScreenHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onOpenListOptions: () -> Unit,
    onOpenSelectFolders: () -> Unit,
    onRescanLibrary: () -> Unit,
    playerState: PlayerViewModel.PlayerUiState? = null,
    onShowCastSheet: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search tracks, artists...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            val isDeviceConnected = playerState?.activeOutputDevice == "m-box TV" || playerState?.castState == CastConnectionState.CONNECTED
            val isDeviceConnecting = playerState?.castState == CastConnectionState.CONNECTING
            val accentColor = MaterialTheme.colorScheme.primary

            IconButton(onClick = onShowCastSheet) {
                Icon(
                    imageVector = if (isDeviceConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    contentDescription = "Play on Device",
                    tint = if (isDeviceConnected) accentColor else if (isDeviceConnecting) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onOpenListOptions) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp,
            divider = {}
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    isSelectionMode: Boolean = false,
    selectedTrackIds: Set<Long> = emptySet(),
    onTrackSelectionToggled: ((Track) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    playerState: PlayerViewModel.PlayerUiState? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
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
                val isSelected = selectedTrackIds.contains(track.id)
                val isCurrentTrack = playerState?.currentTrack?.id == track.id
                val isPlaying = playerState?.isPlaying == true

                SharedTrackRow(
                    track = track,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    onClick = { onTrackClick(it) },
                    isSelected = isSelected,
                    onSelectionToggled = onTrackSelectionToggled
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        FastScrollbar(
            listState = listState,
            getSectionText = { index ->
                val trackIndex = if (header != null) index - 1 else index
                val track = tracks.getOrNull(trackIndex)
                track?.title?.trimStart()?.take(1)?.uppercase()
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun AlbumGrid(
    albums: List<AlbumRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onAlbumClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(albums) { album ->
                val isGroupPlaying = playerState.currentTrack?.album == album.album
                SharedGroupRow(
                    title = album.album,
                    subtitle = album.artist,
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.Album,
                    onClick = { onAlbumClick(album.album) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        FastScrollbar(
            listState = listState,
            getSectionText = { index ->
                albums.getOrNull(index)?.album?.trimStart()?.take(1)?.uppercase()
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ArtistList(
    artists: List<ArtistRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onGroupClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(artists) { item ->
                val isGroupPlaying = playerState.currentTrack?.artist == item.artist
                SharedGroupRow(
                    title = item.artist,
                    subtitle = "${item.trackCount} tracks",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.Person,
                    onClick = { onGroupClick("artist", item.artist) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        FastScrollbar(
            listState = listState,
            getSectionText = { index ->
                artists.getOrNull(index)?.artist?.trimStart()?.take(1)?.uppercase()
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun AlbumArtistList(
    albumArtists: List<AlbumArtistRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onGroupClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(albumArtists) { item ->
                val isGroupPlaying = playerState.currentTrack?.artist == item.albumArtist
                SharedGroupRow(
                    title = item.albumArtist,
                    subtitle = "${item.trackCount} tracks",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.Person,
                    onClick = { onGroupClick("artist", item.albumArtist) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        FastScrollbar(
            listState = listState,
            getSectionText = { index ->
                albumArtists.getOrNull(index)?.albumArtist?.trimStart()?.take(1)?.uppercase()
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(playlists) { playlist ->
                val isGroupPlaying = playerState.playingFromName == playlist.name
                SharedGroupRow(
                    title = playlist.name,
                    subtitle = "Playlist",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.QueueMusic,
                    onClick = { }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun FolderList(
    folders: List<FolderRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    libVm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onFolderClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(folders) { folder ->
                val isGroupPlaying = playerState.currentTrack?.uri?.contains(folder.folderPath, ignoreCase = true) == true
                SharedGroupRow(
                    title = folder.folderName,
                    subtitle = "${folder.trackCount} tracks · ${folder.folderPath}",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.Folder,
                    onClick = { onFolderClick(folder.folderPath) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun FolderHierarchyView(
    folders: List<FolderRow>,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onFolderClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(folders) { folder ->
                val isGroupPlaying = playerState.currentTrack?.uri?.contains(folder.folderPath, ignoreCase = true) == true
                SharedGroupRow(
                    title = folder.folderName,
                    subtitle = "${folder.trackCount} items · ${folder.folderPath}",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.FolderSpecial,
                    onClick = { onFolderClick(folder.folderPath) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun GenreList(
    genres: List<GenreRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onGroupClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(genres) { genre ->
                val isGroupPlaying = playerState.currentTrack?.genre == genre.genre
                SharedGroupRow(
                    title = genre.genre,
                    subtitle = "${genre.trackCount} tracks",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.Category,
                    onClick = { onGroupClick("genre", genre.genre) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun YearList(
    years: List<YearRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onGroupClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(years) { year ->
                val isGroupPlaying = playerState.currentTrack?.year == year.year
                SharedGroupRow(
                    title = year.year.toString(),
                    subtitle = "${year.trackCount} tracks",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.CalendarToday,
                    onClick = { onGroupClick("year", year.year.toString()) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ComposerList(
    composers: List<ComposerRow>,
    listState: LazyListState,
    playerState: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onGroupClick: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(composers) { composer ->
                val isGroupPlaying = playerState.currentTrack?.composer == composer.composer
                SharedGroupRow(
                    title = composer.composer,
                    subtitle = "${composer.trackCount} tracks",
                    isGroupPlaying = isGroupPlaying,
                    isPlaying = playerState.isPlaying,
                    accentColor = accentColor,
                    icon = Icons.Rounded.MusicNote,
                    onClick = { onGroupClick("composer", composer.composer) }
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}
