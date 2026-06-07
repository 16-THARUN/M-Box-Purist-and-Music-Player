package com.mediaplayer.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.prefs.EqPreset
import com.mediaplayer.data.prefs.PlayerPreferences
import com.mediaplayer.service.EqualizerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val browserFuture: ListenableFuture<MediaBrowser>,
    private val eqManager: EqualizerManager,
    private val prefs: PlayerPreferences
) : ViewModel() {

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
        val loudnessGainMb: Int = 0
    )

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var browser: MediaBrowser? = null

    init {
        viewModelScope.launch {
            browser = browserFuture.await()
            browser?.addListener(playerListener)
            updateCurrentTrack(browser?.currentMediaItem)
            collectPreferences()
            startPositionUpdates()
        }
    }

    // ── Playback controls ─────────────────────────────────────

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
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
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        browser?.run { if (isPlaying) pause() else play() }
    }

    fun skipNext()     { browser?.seekToNextMediaItem() }
    fun skipPrevious() {
        browser?.run {
            if (currentPosition > 3000L) seekTo(0L) else seekToPreviousMediaItem()
        }
    }

    fun seekTo(ms: Long) { browser?.seekTo(ms) }

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

    // ── Equalizer ─────────────────────────────────────────────

    fun setEqEnabled(enabled: Boolean) {
        eqManager.setEnabled(enabled)
        _state.update { it.copy(eqEnabled = enabled) }
        viewModelScope.launch { prefs.setEqEnabled(enabled) }
    }

    fun setEqBand(band: Int, levelMb: Int) {
        eqManager.setBandLevel(band, levelMb)
        val newBands = _state.value.eqBands.toMutableList().also { it[band] = levelMb }
        _state.update { it.copy(eqBands = newBands, activePreset = EqPreset.FLAT) }
        viewModelScope.launch { prefs.setEqBands(newBands) }
    }

    fun applyEqPreset(preset: EqPreset) {
        eqManager.applyBands(preset.bandLevels)
        _state.update { it.copy(eqBands = preset.bandLevels, activePreset = preset) }
        viewModelScope.launch { prefs.applyPreset(preset) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        eqManager.setBassBoostEnabled(enabled)
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

    // ── Internals ─────────────────────────────────────────────

    private fun collectPreferences() = viewModelScope.launch {
        combine(
            prefs.eqBands,
            prefs.eqEnabled,
            prefs.shuffleOn,
            prefs.repeatMode,
            prefs.gapless
        ) { bands, eqOn, shuffle, repeat, gapless ->
            _state.update {
                it.copy(
                    eqBands    = bands,
                    eqEnabled  = eqOn,
                    shuffleOn  = shuffle,
                    repeatMode = repeat,
                    gaplessOn  = gapless
                )
            }
            eqManager.applyBands(bands)
            eqManager.setEnabled(eqOn)
        }.collect()
    }

    private fun startPositionUpdates() = viewModelScope.launch {
        while (true) {
            browser?.let { b ->
                _state.update {
                    it.copy(
                        positionMs = b.currentPosition,
                        durationMs = b.duration.coerceAtLeast(0L)
                    )
                }
            }
            delay(500L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentTrack(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleOn = shuffleModeEnabled) }
        }
    }

    private fun updateCurrentTrack(mediaItem: MediaItem?) {
        val metadata = mediaItem?.mediaMetadata
        if (metadata == null) {
            _state.update { it.copy(currentTrack = null) }
            return
        }

        // Create a minimal Track object from metadata for UI display
        val track = Track(
            id          = mediaItem.mediaId.toLongOrNull() ?: 0L,
            uri         = mediaItem.localConfiguration?.uri?.toString() ?: "",
            title       = metadata.title?.toString() ?: "",
            artist      = metadata.artist?.toString() ?: "Unknown Artist",
            album       = metadata.albumTitle?.toString() ?: "Unknown Album",
            albumArtUri = metadata.artworkUri?.toString(),
            duration    = 0L, // Will be updated by startPositionUpdates
            bitrate     = 0,
            sampleRate  = 0,
            trackNumber = metadata.trackNumber ?: 0,
            discNumber  = metadata.discNumber ?: 0,
            year        = metadata.recordingYear ?: 0,
            genre       = metadata.genre?.toString() ?: "",
            composer    = metadata.composer?.toString() ?: "",
            comment     = "",
            fileSize    = 0L,
            dateAdded   = 0L,
            mimeType    = "",
            folderName  = "",
            folderPath  = ""
        )
        _state.update { it.copy(currentTrack = track) }
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
