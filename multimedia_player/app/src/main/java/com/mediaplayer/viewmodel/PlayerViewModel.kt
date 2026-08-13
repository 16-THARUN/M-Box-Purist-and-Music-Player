package com.mediaplayer.viewmodel

import android.net.Uri
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.util.Log
import android.media.audiofx.Equalizer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.R
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.AudioSignalPath
import com.mediaplayer.data.model.AudioPreset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mediaplayer.data.model.LyricLine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.model.LyricsParser
import com.mediaplayer.data.network.RetrofitClient
import com.mediaplayer.data.network.lyrics.LrcLibResponse
import com.mediaplayer.data.repository.LibraryRepository
import com.mediaplayer.data.repository.LyricsRepository
import com.mediaplayer.data.prefs.EqPreset
import com.mediaplayer.data.prefs.PlayerPreferences
import com.mediaplayer.service.EqualizerManager
import com.mediaplayer.service.VinylEffectManager
import com.mediaplayer.util.AudioDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val browserFuture: ListenableFuture<MediaBrowser>,
    private val eqManager: EqualizerManager,
    private val libraryRepository: LibraryRepository,
    private val prefs: PlayerPreferences,
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository,
    private val vinylEffectManager: VinylEffectManager,
    private val audioDiagnostics: AudioDiagnostics,
    private val visualizerManager: com.mediaplayer.util.VisualizerManager,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val VISUALIZER_MODE_KEY = "visualizer_mode_id"
    val activeVisualizerMode: StateFlow<String> = savedStateHandle.getStateFlow(VISUALIZER_MODE_KEY, "circular_rings")

    private val _eqBands = MutableStateFlow(FloatArray(10) { 0f })
    val eqBands = _eqBands.asStateFlow()

    val eqPresetsLabels = listOf("KZ Castor")

    data class PlayerUiState(
        val currentTrack: Track? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val shuffleOn: Boolean = false,
        val eqEnabled: Boolean = true,
        val eqBands: List<Int> = List(10) { 0 },
        val activePreset: EqPreset = EqPreset.FLAT,
        val bassBoostOn: Boolean = false,
        val bassBoostStrength: Int = 500,
        val virtualizerOn: Boolean = false,
        val virtualizerStrength: Int = 500,
        val gaplessOn: Boolean = true,
        val loudnessOn: Boolean = false,
        val loudnessGainMb: Int = 0,
        val showAudioInfo: Boolean = false,
        val signalPath: AudioSignalPath? = null,
        val vinylRotation: Float = 0f,
        val currentPlaylist: List<Track> = emptyList(),
        val currentTrackIndex: Int = -1,
        val currentLyrics: List<LyricLine> = emptyList(),
        val rawLyricsText: String = "",
        val isVisualizerEnabled: Boolean = true,
        val activeVisualizerMode: String = "circular_rings",
        val customPresets: List<AudioPreset> = emptyList(),
        val selectedEcho: String = "Flat",
        val selectedDolby: String = "Dolby Focused",
        val selectedDirac: String = ""
    )

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _isPlayerModalOpen = MutableStateFlow(false)
    val isPlayerModalOpen = _isPlayerModalOpen.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _rawLyricsText = MutableStateFlow("")
    val rawLyricsText = _rawLyricsText.asStateFlow()

    // 🔥 THE NEW REPOSITORY FLOW
    val libraryTracks = libraryRepository.allTracks

    private val _currentSyncedLyrics = MutableStateFlow<String?>(null)
    val currentSyncedLyrics = _currentSyncedLyrics.asStateFlow()

    val fftData: StateFlow<com.mediaplayer.util.FftState> = visualizerManager.fftData

    fun expandPlayer() { _isPlayerModalOpen.value = true }
    fun collapsePlayer() { _isPlayerModalOpen.value = false }

    private var browser: MediaBrowser? = null
    private var nativeEqualizer: Equalizer? = null 

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            viewModelScope.launch { updateCurrentTrack(mediaItem) }
            browser?.let { b ->
                _state.update { it.copy(currentTrackIndex = b.currentMediaItemIndex) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                // 🔥 THE FIX: Decay visualizer to 0 when paused
                visualizerManager.stop() // Or just emit empty if we want to keep it running
            } else {
                val sessionId = eqManager.audioSessionId
                if (sessionId != 0 && _state.value.isVisualizerEnabled) {
                    visualizerManager.start(sessionId)
                }
            }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleOn = shuffleModeEnabled) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("ExoPlayerDebug", "🔥 Fatal Playback Error: ${error.message}", error)
            Log.e("ExoPlayerDebug", "Error Code: ${error.errorCodeName} (${error.errorCode})")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN_STATE"
            }
            Log.d("ExoPlayerDebug", "🔄 Player State Transition: $stateName")
        }
    }

    init {
        viewModelScope.launch {
            browser = browserFuture.await()
            browser?.addListener(playerListener)
            
            // Core Task 3: Immediately sync UI state with ExoPlayer's current item
            browser?.currentMediaItem?.let { item ->
                updateCurrentTrack(item)
            }
            
            collectPreferences()
            startPositionUpdates()
        }

        viewModelScope.launch {
            eqManager.audioSessionIdFlow.collect { sessionId ->
                if (sessionId != 0) {
                    nativeEqualizer = eqManager.getEqualizer()
                    applyToNativeEqualizer(_eqBands.value)
                }
            }
        }
    }

    // ── Playback controls ─────────────────────────────────────

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        _state.update { it.copy(currentPlaylist = tracks, currentTrackIndex = startIndex) }
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
                        .setTrackNumber(track.trackNumber)
                        .setDiscNumber(track.discNumber)
                        .setRecordingYear(track.year)
                        .build()
                ).build()
        }
        browser?.run {
            Log.d("ExoPlayerDebug", "▶️ Playing ${items.size} tracks starting at $startIndex")
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun playExternalUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                // Extract metadata from the external file
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "External Audio File"
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/mpeg"
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                
                // 🔥 THE FIX: Extract the embedded picture data
                val pictureData = retriever.embeddedPicture
                val albumArtUriString = extractAlbumArtToCache(context, pictureData)

                retriever.release()

                // 🔥 THE FIX: Create a temporary Track object for the UI
                val externalTrack = Track(
                    id = -1L, // Use a negative ID to denote a temporary/external file
                    uri = uri.toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtUri = albumArtUriString, // 🔥 Assign the cached artwork URI here
                    duration = durationMs,
                    bitrate = bitrate,
                    sampleRate = 48000, // Fallback sample rate
                    trackNumber = 0,
                    discNumber = 0,
                    year = 0,
                    genre = "External",
                    composer = "",
                    comment = "",
                    fileSize = 0L,
                    dateAdded = System.currentTimeMillis(),
                    mimeType = mimeType,
                    folderName = "External",
                    folderPath = "External"
                )

                withContext(Dispatchers.Main) {
                    // 1. Update the UI state so the mini-player becomes visible
                    _state.update { it.copy(
                        currentTrack = externalTrack,
                        currentTrackIndex = 0,
                        currentPlaylist = listOf(externalTrack),
                        isPlaying = true
                    ) }

                    // 2. Build the Media3 Item with attached metadata
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        // 🔥 Pass the artwork byte array to Media3 for the system notification/lock screen
                        .setArtworkData(pictureData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId("external_file")
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    // 3. Command MediaBrowser to play
                    browser?.run {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        browser?.run { if (isPlaying) pause() else play() }
    }

    fun toggleVisualizer() {
        viewModelScope.launch {
            val next = !_state.value.isVisualizerEnabled
            prefs.setVisualizerEnabled(next)
            
            if (next) {
                val sessionId = eqManager.audioSessionId
                if (sessionId != 0) visualizerManager.start(sessionId)
            } else {
                visualizerManager.stop()
            }
        }
    }

    fun setVisualizerMode(mode: String) {
        savedStateHandle[VISUALIZER_MODE_KEY] = mode
        viewModelScope.launch {
            prefs.setVisualizerMode(mode)
        }
    }

    fun saveCurrentConfigurationAsPreset(name: String) {
        viewModelScope.launch {
            val currentBands = mutableMapOf<Int, Int>()
            val count = eqManager.getBandCount()
            for (i in 0 until count) {
                currentBands[i] = eqManager.getBandLevel(i)
            }
            
            val newPreset = AudioPreset(name = name, bandLevels = currentBands)
            val updatedPresets = _state.value.customPresets + newPreset
            
            val json = Gson().toJson(updatedPresets)
            prefs.setCustomPresets(json)
        }
    }

    fun applyAudioPreset(preset: AudioPreset) {
        viewModelScope.launch {
            val levels = List(eqManager.getBandCount()) { i ->
                preset.bandLevels[i] ?: 0
            }
            prefs.setEqBands(levels)
            applyToNativeEqualizer(levels.map { it / 100f }.toFloatArray())
            
            // Sync with legacy state
            _state.update { it.copy(eqBands = levels) }
        }
    }

    // 🔥 THE EXPORT LOGIC
    fun exportPresetToUri(preset: AudioPreset, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = Json.encodeToString(preset)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 🔥 THE IMPORT LOGIC
    fun importPresetFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                }
                
                if (jsonString != null) {
                    val importedPreset = Json.decodeFromString<AudioPreset>(jsonString)
                    
                    val updatedPresets = _state.value.customPresets + importedPreset
                    val json = Gson().toJson(updatedPresets)
                    prefs.setCustomPresets(json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setShowAudioInfo(show: Boolean) {
        _state.update { it.copy(showAudioInfo = show) }
    }

    fun updateVinylRotation(rotation: Float) {
        _state.update { it.copy(vinylRotation = rotation) }
    }

    fun skipNext()     { browser?.seekToNextMediaItem() }
    fun skipPrevious() {
        browser?.run {
            if (currentPosition > 3000L) seekTo(0L) else seekToPreviousMediaItem()
        }
    }

    fun skipToQueueItem(index: Int) {
        browser?.seekToDefaultPosition(index)
    }

    fun syncLibrary(rootPath: String) {
        viewModelScope.launch {
            libraryRepository.syncLibraryFromDirectory(File(rootPath))
        }
    }

    private var lastScratchTime = 0L

    fun seekTo(ms: Long) {
        browser?.seekTo(ms)
        vinylEffectManager.playScratch()
    }

    fun toggleShuffle() {
        val next = !(browser?.shuffleModeEnabled ?: false)
        browser?.shuffleModeEnabled = next
        viewModelScope.launch { prefs.setShuffleOn(next) }
        _state.update { it.copy(shuffleOn = next) }
    }

    fun cycleRepeatMode() {
        val next = ((browser?.repeatMode ?: 0) + 1) % 3
        browser?.repeatMode = next
        viewModelScope.launch { prefs.setRepeatMode(next) }
        _state.update { it.copy(repeatMode = next) }
    }

    fun fetchLyricsForTrack(title: String, artist: String, album: String, durationMs: Long, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. TIER 0: Check Local Storage First (.lrc and Embedded)
            val localLyrics = lyricsRepository.getLocalOrEmbeddedLyrics(filePath)
            if (!localLyrics.isNullOrBlank()) {
                _currentSyncedLyrics.value = localLyrics
                applyLyricsToUi(localLyrics)
                return@launch
            }

            // 2. Network Fallbacks (LRCLIB, iTunes)
            val cleanTitle = sanitizeTitle(title)
            val primaryArtist = getPrimaryArtist(artist)
            val fuzzyQuery = "$cleanTitle $primaryArtist"

            try {
                var bestMatchLrc: String?

                // TIER 1: Strict Exact Match
                val lrcData = lyricsRepository.fetchLyricsStrict(cleanTitle, primaryArtist, album, durationMs)
                bestMatchLrc = lrcData?.syncedLyrics

                if (bestMatchLrc.isNullOrBlank()) {
                    // TIER 2: Search with Clean Title + Primary Artist
                    val searchMatch = lyricsRepository.searchLyricsFuzzy(fuzzyQuery)
                    bestMatchLrc = searchMatch?.syncedLyrics
                }

                if (bestMatchLrc.isNullOrBlank()) {
                    // TIER 3: Desperation Search (Title Only)
                    val searchMatch = lyricsRepository.searchLyricsFuzzy(cleanTitle)
                    bestMatchLrc = searchMatch?.syncedLyrics
                }
                
                if (bestMatchLrc.isNullOrBlank()) {
                    // TIER 4: iTunes API Fallback
                    val itunesResponse = RetrofitClient.iTunesApiService.searchTrack(fuzzyQuery)
                    if (itunesResponse.isSuccessful && !itunesResponse.body()?.results.isNullOrEmpty()) {
                        val itunesTrack = itunesResponse.body()!!.results.first()
                        bestMatchLrc = "[00:00.00] No lyrics found on primary servers.\n[00:02.00] Verified on iTunes: ${itunesTrack.trackName} by ${itunesTrack.artistName}\n[00:05.00] Tap edit to add your own."
                    }
                }

                // Push to UI
                _currentSyncedLyrics.value = bestMatchLrc
                if (bestMatchLrc != null) {
                    applyLyricsToUi(bestMatchLrc)
                } else {
                    setLyricsNotFound()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                val errorLrc = "[00:00.00] Unable to reach lyrics servers.\n[00:05.00] Check your connection or tap edit."
                applyLyricsToUi(errorLrc)
            }
        }
    }

    fun saveManualLyrics(trackPath: String, lyricsText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = lyricsRepository.saveLocalLrcFile(trackPath, lyricsText)
            if (success) {
                _currentSyncedLyrics.value = lyricsText
                applyLyricsToUi(lyricsText)
            }
        }
    }

    // ── Equalizer ─────────────────────────────────────────────

    fun setEqEnabled(enabled: Boolean) {
        eqManager.setEnabled(enabled)
        _state.update { it.copy(eqEnabled = enabled) }
        viewModelScope.launch { prefs.setEqEnabled(enabled) }
    }

    fun setEqBand(band: Int, levelMb: Int) {
        updateBandLevel(band, levelMb / 100f)
    }

    fun applyEqPreset(preset: EqPreset) {
        eqManager.applyBands(preset.bandLevels)
        _state.update { it.copy(eqBands = preset.bandLevels, activePreset = preset) }
        _eqBands.value = preset.bandLevels.map { it / 100f }.toFloatArray()
        viewModelScope.launch { prefs.applyPreset(preset) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        eqManager.setBassBoostEnabled(enabled, viewModelScope)
        _state.update { it.copy(bassBoostOn = enabled) }
    }

    fun setBassBoostStrength(strength: Int) {
        eqManager.setBassBoostStrength(strength)
        _state.update { it.copy(bassBoostStrength = strength) }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        eqManager.setVirtualizerEnabled(enabled)
        _state.update { it.copy(virtualizerOn = enabled) }
    }

    fun setVirtualizerStrength(strength: Int) {
        eqManager.setVirtualizerStrength(strength)
        _state.update { it.copy(virtualizerStrength = strength) }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        eqManager.setLoudnessEnhancerEnabled(enabled)
        _state.update { it.copy(loudnessOn = enabled) }
    }

    fun setLoudnessGain(gainMb: Int) {
        eqManager.setLoudnessGain(gainMb)
        _state.update { it.copy(loudnessGainMb = gainMb) }
    }

    fun setEchoSignature(signature: String) {
        _state.update { it.copy(selectedEcho = signature) }
        applyDspProfile(signature)
    }

    fun setDolbyProfile(profile: String) {
        val next = if (_state.value.selectedDolby == profile) "" else profile
        _state.update { it.copy(selectedDolby = next) }
        applyDspProfile(next)
    }

    fun setDiracProfile(profile: String) {
        val next = if (_state.value.selectedDirac == profile) "" else profile
        _state.update { it.copy(selectedDirac = next) }
        applyDspProfile(next)
    }

    fun applyDspProfile(profileName: String) {
        val newBands = when (profileName) {
            "Flat" -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            "KZ Castor" -> floatArrayOf(4.5f, 5.7f, 5.3f, 4.8f, 5.6f, 2.3f, 1.1f, 1.0f, -0.8f, -3.6f)
            "Dolby Open" -> floatArrayOf(2f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 2f)
            "Signature" -> floatArrayOf(2f, 4f, 2f, 0f, 1f, 2f, 1f, 0f, 2f, 3f)
            "Bass Boost" -> floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)
            "Acoustic" -> floatArrayOf(2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f)
            "Rock" -> floatArrayOf(4f, 3f, -1f, -2f, 0f, 2f, 3f, 3f, 3f, 4f)
            "Pop" -> floatArrayOf(-1f, 0f, 2f, 3f, 3f, 2f, 1f, 1f, 0f, -1f)
            "Electronic" -> floatArrayOf(4f, 3f, 0f, -2f, -1f, 2f, 3f, 3f, 4f, 4f)
            "Jazz" -> floatArrayOf(2f, 1f, 0f, 2f, 3f, 3f, 2f, 1f, 0f, 0f)
            "Voice" -> floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f)
            "Pure Clarity" -> floatArrayOf(-2f, -1f, 0f, 1f, 3f, 5f, 4f, 2f, 1f, 0f)
            "Soft Bass" -> floatArrayOf(3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            else -> _eqBands.value
        }
        _eqBands.value = newBands
        
        // Push to Media3/Android Engine
        applyToNativeEqualizer(newBands)
        
        // Sync with legacy state for consistency
        val intBands = newBands.map { (it * 100).toInt() }
        _state.update { it.copy(eqBands = intBands) }
        viewModelScope.launch { prefs.setEqBands(intBands) }
    }

    private fun applyToNativeEqualizer(bandsDb: FloatArray) {
        nativeEqualizer?.let { eq ->
            val numHardwareBands = eq.numberOfBands
            // Safely loop through the available hardware bands
            for (i in 0 until minOf(numHardwareBands.toInt(), bandsDb.size)) {
                // Android OS requires Millibels (1 dB = 100 mB)
                val levelMillibels = (bandsDb[i] * 100).toInt().toShort()
                try {
                    eq.setBandLevel(i.toShort(), levelMillibels)
                } catch (e: Exception) {
                    // Failsafe in case a band goes out of hardware range
                    e.printStackTrace()
                }
            }
        }
    }

    // Handle manual slider dragging from the UI
    fun updateBandLevel(bandIndex: Int, levelDb: Float) {
        val currentBands = _eqBands.value.copyOf()
        currentBands[bandIndex] = levelDb
        _eqBands.value = currentBands
        applyToNativeEqualizer(currentBands)
        
        // Sync with legacy state
        val levelMb = (levelDb * 100).toInt()
        val newIntBands = _state.value.eqBands.toMutableList().also { it[bandIndex] = levelMb }
        _state.update { it.copy(eqBands = newIntBands, activePreset = EqPreset.FLAT) }
        viewModelScope.launch { prefs.setEqBands(newIntBands) }
    }

    // ── Internals ─────────────────────────────────────────────

    private fun collectPreferences() = viewModelScope.launch {
        combine(
            prefs.eqBands,
            prefs.eqEnabled,
            prefs.shuffleOn,
            prefs.repeatMode,
            prefs.gapless,
            prefs.visualizerEnabled,
            prefs.customPresetsJson,
            eqManager.audioSessionIdFlow,
            prefs.visualizerMode
        ) { args: Array<Any> ->
            val bands = args[0] as List<Int>
            val eqOn = args[1] as Boolean
            val shuffle = args[2] as Boolean
            val repeat = args[3] as Int
            val gapless = args[4] as Boolean
            val visEnabled = args[5] as Boolean
            val customPresetsJson = args[6] as String
            val sessionId = args[7] as Int
            val visMode = args[8] as String

            val customPresets = try {
                val type = object : TypeToken<List<AudioPreset>>() {}.type
                Gson().fromJson<List<AudioPreset>>(customPresetsJson, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList<AudioPreset>()
            }

            _state.update {
                it.copy(
                    eqBands    = bands,
                    eqEnabled  = eqOn,
                    shuffleOn  = shuffle,
                    repeatMode = repeat,
                    gaplessOn  = gapless,
                    isVisualizerEnabled = visEnabled,
                    activeVisualizerMode = visMode,
                    customPresets = customPresets
                )
            }
            savedStateHandle[VISUALIZER_MODE_KEY] = visMode
            applyToNativeEqualizer(bands.map { it / 100f }.toFloatArray())
            eqManager.setEnabled(eqOn)
            
            if (visEnabled && sessionId != 0) {
                visualizerManager.start(sessionId)
            } else if (!visEnabled) {
                visualizerManager.stop()
            }
        }.collect()
    }

    private fun startPositionUpdates() = viewModelScope.launch {
        while (this.isActive) {
            try {
                browser?.let { b ->
                    val format = b.currentTracks.groups
                        .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                        .firstNotNullOfOrNull { it.getTrackFormat(0) }

                    val track = _state.value.currentTrack
                    
                    val signalPath = if (format != null && track != null) {
                        AudioSignalPath(
                            format = track.mimeType.substringAfterLast("/").uppercase(),
                            sourceInfo = "${track.bitrate / 1000} kbps | ${track.sampleRate / 1000} kHz",
                            decoder = format.sampleMimeType ?: "Standard Decoder",
                            hardwareInfo = "${audioDiagnostics.getHardwareSampleRate() / 1000} kHz | ${audioDiagnostics.getHardwareBufferSize()} frames",
                            outputApi = audioDiagnostics.getOutputApi(),
                            latency = 0,
                            bitDepth = audioDiagnostics.getOutputBitDepth()
                        )
                    } else null

                    _state.update {
                        it.copy(
                            positionMs = b.currentPosition,
                            durationMs = b.duration.coerceAtLeast(0L),
                            signalPath = signalPath,
                            isPlaying = b.isPlaying // 🔥 Ensure isPlaying is always synced with hardware
                        )
                    }
                }
            } catch (e: Exception) {
                // Failsafe in case ExoPlayer is briefly inaccessible
            }
            delay(500L) // 🔥 Poll twice a second for a hyper-responsive UI
        }
    }

    private fun extractAlbumArtToCache(context: Context, pictureData: ByteArray?): String? {
        if (pictureData == null) return null
        return try {
            // Overwrite the same temp file to prevent cache bloat
            val cacheFile = File(context.cacheDir, "temp_external_album_art.jpg")
            FileOutputStream(cacheFile).use { it.write(pictureData) }
            Uri.fromFile(cacheFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun updateCurrentTrack(mediaItem: MediaItem?) {
        val mediaIdString = mediaItem?.mediaId
        val mediaId = mediaIdString?.toLongOrNull()

        // 🔥 THE FIX: If this is an external file, don't let the DB lookup reset the state.
        // The playExternalUri method already populated the 'externalTrack' into the state.
        if (mediaIdString == "external_file") {
            return
        }

        if (mediaId == null) {
            _state.update { it.copy(currentTrack = null, currentLyrics = emptyList(), rawLyricsText = "") }
            _currentLyrics.value = emptyList()
            _rawLyricsText.value = ""
            return
        }

        val track = trackDao.getById(mediaId)
        _state.update { it.copy(currentTrack = track) }
        
        if (track != null) {
            fetchLyricsForTrack(track.title, track.artist, track.album, track.duration, track.uri)
        } else {
            _state.update { it.copy(currentLyrics = emptyList(), rawLyricsText = "") }
            _currentLyrics.value = emptyList()
            _rawLyricsText.value = ""
        }
    }

    private fun applyLyricsToUi(rawLrcString: String) {
        _rawLyricsText.value = rawLrcString
        val parsed = LyricsParser.parseLrc(rawLrcString)
        _currentLyrics.value = parsed
        _state.update { it.copy(currentLyrics = parsed, rawLyricsText = rawLrcString) }
    }

    private fun setLyricsNotFound() {
        val fallbackText = "[00:00.00] No lyrics found.\n[00:05.00] Tap edit to add your own."
        applyLyricsToUi(fallbackText)
    }

    fun updateLyricsManually(trackId: Long, newLrcText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            saveLyricsToLocalCache(trackId.toString(), newLrcText)
            trackDao.updateLyrics(trackId, newLrcText) // Keep Room in sync too
            applyLyricsToUi(newLrcText)
        }
    }

    // ── File I/O Helpers ─────────────────────────────────────

    private fun getLyricsFile(trackId: String): File {
        val lyricsDir = File(context.filesDir, "lyrics")
        if (!lyricsDir.exists()) lyricsDir.mkdirs()
        val safeName = trackId.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return File(lyricsDir, "$safeName.lrc")
    }

    private fun saveLyricsToLocalCache(trackId: String, lrcText: String) {
        val file = getLyricsFile(trackId)
        file.writeText(lrcText)
    }

    // 1. Cleans the title but leaves important tags like (Lofi), (Live), or (Remix)
    private fun sanitizeTitle(title: String): String {
        return title
            // Remove common junk like (From "Movie") or (Original Motion Picture Soundtrack)
            .replace(Regex("\\(?\\b(From|OST|Original Motion Picture).*?\\)?", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    // 2. Extracts only the first artist to prevent confusing the search engine
    private fun getPrimaryArtist(artistStr: String): String {
        return artistStr
            // Split the string at the first comma, ampersand, or "feat" and take the first chunk
            .split(Regex("[,&]|\\bfeat\\b|\\bft\\b", RegexOption.IGNORE_CASE))[0]
            .trim()
    }

    override fun onCleared() {
        browser?.removeListener(playerListener)
        MediaBrowser.releaseFuture(browserFuture)
        super.onCleared()
    }
}

// Extension: Long ms -> "m:ss"
fun Long.toTimeString(): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(this)
    val s = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return "%d:%02d".format(m, s)
}
