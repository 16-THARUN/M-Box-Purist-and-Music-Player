package com.mediaplayer.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaplayer.data.db.PlaylistDao
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.*
import com.mediaplayer.data.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortOrder(val label: String) {
    DATE_ADDED("Date Added"),
    TRACK_NUMBER("Track Number"),
    TITLE("Title"),
    FILENAME("Filename"),
    YEAR("Year")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scanner: MediaScanner,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    // ── Search & Sort ─────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _scanningPath = MutableStateFlow<String?>(null)
    val scanningPath: StateFlow<String?> = _scanningPath.asStateFlow()

    // ── Scan state ────────────────────────────────────────────────────────────
    private val _scanState = MutableStateFlow<MediaScanner.ScanState>(MediaScanner.ScanState.Idle)
    val scanState: StateFlow<MediaScanner.ScanState> = _scanState.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            scanLibrary()
        }
    }

    init {
        scanLibrary()
        ctx.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    override fun onCleared() {
        super.onCleared()
        ctx.contentResolver.unregisterContentObserver(observer)
    }

    // ── Library data ──────────────────────────────────────────────────────────
    val allTracks: StateFlow<List<Track>> = combine(trackDao.getAllTracks(), _sortOrder) { tracks, sort ->
        sortTracks(tracks, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<AlbumRow>> = trackDao.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<ArtistRow>> = trackDao.getArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<GenreRow>> = trackDao.getGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val years: StateFlow<List<YearRow>> = trackDao.getYears()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val composers: StateFlow<List<ComposerRow>> = trackDao.getComposers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumArtists: StateFlow<List<AlbumArtistRow>> = trackDao.getAlbumArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Folders: list of on-device directories that contain music ─────────────
    val folders: StateFlow<List<FolderRow>> = trackDao.getFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Track>> = combine(_searchQuery, _sortOrder) { q, sort -> q to sort }
        .debounce(300)
        .flatMapLatest { (q, sort) -> 
            if (q.isBlank()) emptyFlow() 
            else trackDao.search(q).map { sortTracks(it, sort) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions ───────────────────────────────────────────────────────────────

    fun updateSearch(q: String) { _searchQuery.value = q }
    fun updateSortOrder(order: SortOrder) { _sortOrder.value = order }

    private fun sortTracks(tracks: List<Track>, sort: SortOrder): List<Track> {
        return when (sort) {
            SortOrder.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
            SortOrder.TRACK_NUMBER -> tracks.sortedWith(compareBy<Track> { it.discNumber }.thenBy { it.trackNumber })
            SortOrder.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortOrder.FILENAME -> tracks.sortedBy { it.uri.substringAfterLast("/").lowercase() }
            SortOrder.YEAR -> tracks.sortedByDescending { it.year }
        }
    }

    fun scanLibrary() = viewModelScope.launch {
        performScan()
    }

    private suspend fun performScan() {
        if (!hasAudioPermission()) {
            _scanState.value = MediaScanner.ScanState.Error("Storage permission not granted")
            return
        }
        scanner.scan { state -> _scanState.value = state }
    }

    fun refreshLibrary() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            // Safely move the heavy lifting to the background thread
            withContext(Dispatchers.IO) {
                try {
                    _isRefreshing.value = true
                    
                    // Simulating folder discovery for the UI
                    val directories = listOf(
                        "/storage/emulated/0/Music",
                        "/storage/emulated/0/Downloads",
                        "/storage/emulated/0/Audio",
                        "/storage/emulated/0/Podcasts"
                    )

                    for (dir in directories) {
                        _scanningPath.value = "Discovering $dir..."
                        delay(500)
                    }

                    _scanningPath.value = "Scanning MediaStore..."
                    performScan()
                    delay(500)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _scanningPath.value = null
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun getTracksForAlbum(album: String): Flow<List<Track>> = trackDao.getByAlbum(album).map { sortTracks(it, _sortOrder.value) }
    fun getTracksForArtist(artist: String): Flow<List<Track>> = trackDao.getByArtist(artist).map { sortTracks(it, _sortOrder.value) }
    fun getTracksForGenre(genre: String): Flow<List<Track>>   = trackDao.getByGenre(genre).map { sortTracks(it, _sortOrder.value) }
    fun getTracksForYear(year: Int): Flow<List<Track>>        = trackDao.getByYear(year).map { sortTracks(it, _sortOrder.value) }
    fun getTracksForComposer(composer: String): Flow<List<Track>> = trackDao.getByComposer(composer).map { sortTracks(it, _sortOrder.value) }

    /** All tracks inside a specific on-device folder. */
    fun getTracksForFolder(folderPath: String): Flow<List<Track>> = trackDao.getByFolder(folderPath).map { sortTracks(it, _sortOrder.value) }

    fun createPlaylist(name: String) = viewModelScope.launch {
        playlistDao.insertPlaylist(Playlist(name = name))
    }

    fun deletePlaylist(playlist: Playlist) = viewModelScope.launch {
        playlistDao.deletePlaylist(playlist)
    }

    fun addToPlaylist(playlistId: Long, trackUri: String) = viewModelScope.launch {
        val pos = playlistDao.getTrackCount(playlistId)
        playlistDao.addTrackToPlaylist(
            PlaylistTrack(playlistId = playlistId, trackUri = trackUri, position = pos)
        )
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }
}
