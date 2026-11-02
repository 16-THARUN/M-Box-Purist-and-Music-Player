package com.mediaplayer.viewmodel

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.widget.Toast
import android.media.MediaMetadataRetriever
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.util.Log
import com.mediaplayer.util.Eac3Transcoder
import com.mediaplayer.util.MBoxCastServer
import com.mediaplayer.util.SamsungTvManager
import com.mediaplayer.util.DiscoveredSamsungTv
import org.json.JSONObject
import android.media.audiofx.Equalizer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.session.MediaBrowser
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.R
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.AudioSignalPath
import com.mediaplayer.data.model.AudioPreset
import com.mediaplayer.data.model.EqPreset as NewEqPreset
import com.mediaplayer.data.model.HeadsetProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mediaplayer.data.model.LyricLine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.model.LyricsParser
import com.mediaplayer.data.model.PlayerController
import com.mediaplayer.data.model.CastController
import com.mediaplayer.data.model.CastConnectionState
import com.mediaplayer.data.model.NowPlayingState
import com.mediaplayer.data.network.RetrofitClient
import com.mediaplayer.data.network.lyrics.LrcLibResponse
import com.mediaplayer.data.repository.LibraryRepository
import com.mediaplayer.data.repository.LyricsRepository
import com.mediaplayer.data.prefs.EqPreset
import com.mediaplayer.data.prefs.PlayerPreferences
import com.mediaplayer.service.EqualizerManager
import com.mediaplayer.service.NativeDspEngine
import com.mediaplayer.service.VinylEffectManager
import com.mediaplayer.util.AudioDiagnostics
import com.mediaplayer.util.DeviceState
import com.mediaplayer.util.DeviceStateMonitor
import com.mediaplayer.util.DspRenderer
import com.bumptech.glide.Glide
import com.mediaplayer.service.MediaService
import com.mediaplayer.util.LocalAudioServer
import com.mediaplayer.util.prepareMediaForCast
import com.mediaplayer.util.probeOriginalAudioSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

data class CastState(
    val isCasting: Boolean = false,
    val deviceName: String = "",
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shownPositionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val queueId: String = "",
    val queueIndex: Int = -1,
    val queueLength: Int = 0,
    val repeatMode: String = "off",
    val isShuffle: Boolean = false,
    val tvVolume: Int = 50,
    val isMuted: Boolean = false,
    val volumeSupported: Boolean = true,
    val lyricsOn: Boolean = false,
    val displayMode: String = "vinyl",
    val visualizerId: String = "circular-spectrum",
    val rttMs: Long = 0L,
    val latencyMs: Long = 0L,
    val lastSeq: Long = 0L,
    val driftMs: Long = 0L,
    val recentEvents: List<String> = emptyList(),
    val recentCommands: List<String> = emptyList()
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val browserFuture: ListenableFuture<MediaBrowser>,
    private val eqManager: EqualizerManager,
    val dspEngine: NativeDspEngine,
    private val libraryRepository: LibraryRepository,
    private val prefs: PlayerPreferences,
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository,
    private val vinylEffectManager: VinylEffectManager,
    private val audioDiagnostics: AudioDiagnostics,
    private val visualizerManager: com.mediaplayer.util.VisualizerManager,
    private val deviceStateMonitor: DeviceStateMonitor,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel(), PlayerController, CastController {

    private val VISUALIZER_MODE_KEY = "visualizer_mode_id"
    val activeVisualizerMode: StateFlow<String> = savedStateHandle.getStateFlow(VISUALIZER_MODE_KEY, "circular_rings")

    private val _eqBands = MutableStateFlow(FloatArray(10) { 0f })

    data class PlayerUiState(
        val currentTrack: Track? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val castState: CastConnectionState = CastConnectionState.IDLE,
        val castDeviceName: String = "",
        val castErrorMessage: String = "",
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
        val seekBarStyle: String = "pill",
        val openNowPlayingOnSongStart: Boolean = false,
        val playingFromType: String = "All Songs",
        val playingFromName: String = "All Songs",
        val playingFromPath: String = "",
        val customPresets: List<AudioPreset> = emptyList(),
        val selectedEcho: String = "Flat",
        val selectedDolby: String = "Dolby Focused",
        val selectedDirac: String = "",
        val mids: Int = 0,
        val treble: Int = 0,
        val bass: Int = 0,
        val activeHeadset: HeadsetProfile? = null,
        val deviceState: DeviceState = DeviceState.OPTIMAL,
        val isRendering: Boolean = false,
        val isCasting: Boolean = false,
        val isMBoxTvConnected: Boolean = false,
        val activeOutputDevice: String = "This phone",
        val tvDeviceName: String = "m-box TV",
        val isTvReconnecting: Boolean = false,
        val currentAudioSpec: String = "Detecting..."
    )

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val castState = MutableStateFlow(CastState())

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

    val fftFlow: StateFlow<FloatArray> = visualizerManager.fftFlow
    val deviceState = deviceStateMonitor.stateFlow

    override fun expandPlayer() { _isPlayerModalOpen.value = true }
    override fun collapsePlayer() { _isPlayerModalOpen.value = false }

    private var browser: MediaBrowser? = null
    private var nativeEqualizer: Equalizer? = null 

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            viewModelScope.launch { updateCurrentTrack(mediaItem) }
            browser?.let { b ->
                _state.update { it.copy(currentTrackIndex = b.currentMediaItemIndex) }
                
                // 🔥 THE FIX: Rebuild Visualizer instance completely on track transition
                if (b.isPlaying && _state.value.isVisualizerEnabled) {
                    val sessionId = eqManager.audioSessionId
                    if (sessionId != 0) visualizerManager.start(sessionId)
                }
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
        initCastPlayer()

        viewModelScope.launch {
            browser = browserFuture.await()
            browser?.addListener(playerListener)
            
            // Immediately sync UI state with ExoPlayer's current state & queue on connect
            resyncWithController()
            
            collectPreferences()
            startPositionUpdates()
            startDeviceStateCollection()
            startPredictiveCaching()
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

    fun resyncWithController() {
        val b = browser ?: return

        if (_state.value.activeOutputDevice == "m-box TV" || _state.value.isCasting) {
            Log.i("MBoxResync", "resync: casting active -> using TV state for Now Playing")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = b.mediaItemCount
                val currentIndex = b.currentMediaItemIndex
                val mediaItem = b.currentMediaItem

                val mediaIdString = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString() ?: ""
                val mediaId = mediaIdString.toLongOrNull()

                val allTracks: List<Track> = _state.value.currentPlaylist.ifEmpty { trackDao.getAllTracks().first() }
                val currentTrack = if (mediaId != null) {
                    allTracks.find { it.id == mediaId }
                } else {
                    allTracks.find { it.uri == mediaIdString }
                }

                val queueList = mutableListOf<Track>()
                for (i in 0 until count) {
                    val item = b.getMediaItemAt(i)
                    val itemMediaId = item.mediaId.toLongOrNull()
                    val track = if (itemMediaId != null) {
                        allTracks.find { it.id == itemMediaId }
                    } else {
                        val itemUri = item.requestMetadata.mediaUri?.toString() ?: ""
                        allTracks.find { it.uri == itemUri }
                    }
                    if (track != null) {
                        queueList.add(track)
                    }
                }

                val isPlaying = b.isPlaying
                val positionMs = b.currentPosition.coerceAtLeast(0L)
                val durationMs = b.duration.coerceAtLeast(0L)
                val repeatMode = b.repeatMode
                val shuffleOn = b.shuffleModeEnabled

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            currentTrack = currentTrack ?: it.currentTrack,
                            currentPlaylist = if (queueList.isNotEmpty()) queueList else it.currentPlaylist,
                            currentTrackIndex = if (currentIndex >= 0) currentIndex else it.currentTrackIndex,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = if (durationMs > 0) durationMs else (currentTrack?.duration ?: it.durationMs),
                            repeatMode = repeatMode,
                            shuffleOn = shuffleOn
                        )
                    }
                    if (currentTrack != null) {
                        updateAudioSpecForTrack(currentTrack)
                    }
                }

                Log.i("MBoxResync", "resync: '${currentTrack?.title}' playing=$isPlaying pos=${positionMs / 1000}s queue=${queueList.size} index=$currentIndex")
            } catch (e: Exception) {
                Log.w("MBoxResync", "Error in resyncWithController: ${e.message}")
            }
        }
    }

    private var castPlayer: CastPlayer? = null
    private var localAudioServer: LocalAudioServer? = null
    private var castTimeoutJob: Job? = null

    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    fun setCastVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _castVolume.value = clamped
        try {
            castPlayer?.volume = clamped
            CastContext.getSharedInstance(context.applicationContext).sessionManager.currentCastSession?.remoteMediaClient?.setStreamVolume(clamped.toDouble())
            Log.d("CastDebug", "Set Cast volume to $clamped")
        } catch (e: Exception) {
            Log.e("CastDebug", "Error setting Cast volume: ${e.message}")
        }
    }

    private var mBoxCastServer: MBoxCastServer? = null

    fun initMBoxCastServer() {
        if (mBoxCastServer == null) {
            mBoxCastServer = MBoxCastServer(
                context = context.applicationContext,
                listeningPort = 8787,
                onTvEventReceived = { event, payload ->
                    handleMBoxTvEvent(event, payload)
                }
            )
        }
        com.mediaplayer.cast.TvRemoteVolume.apply {
            init(context.applicationContext)
            onStep = { up -> sendVolumeStepToTv(up) }
            onMute = { on -> sendSetMuteToTv(on) }
            onPlayPause = { togglePlayPause() }
            onNext = { sendNextToTv() }
            onPrev = { sendPrevToTv() }
            onTarget = { v -> moveTvVolumeTo(v) }
        }
    }

    private var errorRetryCount = 0
    private var lastErrorTrackId: Long = -1L
    private var remoteVisJob: Job? = null

    private fun startRemoteVisualizer() {
        stopRemoteVisualizer()
        val track = _state.value.currentTrack ?: return
        val uri = Uri.parse(track.uri)
        
        remoteVisJob = viewModelScope.launch(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                if (track.uri.startsWith("content://")) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        extractor.setDataSource(pfd.fileDescriptor)
                        pfd.close()
                    } else {
                        extractor.setDataSource(context, uri, null)
                    }
                } else {
                    val filePath = if (track.uri.startsWith("file://")) uri.path ?: track.uri else track.uri
                    val file = File(filePath)
                    if (file.exists()) extractor.setDataSource(file.absolutePath)
                    else extractor.setDataSource(context, uri, null)
                }

                var audioTrackIdx = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioTrackIdx = i
                        break
                    }
                }
                if (audioTrackIdx < 0) return@launch
                extractor.selectTrack(audioTrackIdx)
                val format = extractor.getTrackFormat(audioTrackIdx)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@launch
                
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                var isEOS = false
                var lastFrameTimeMs = System.currentTimeMillis()
                
                while (isActive && _state.value.activeOutputDevice == "m-box TV") {
                    if (!_state.value.isPlaying) {
                        delay(50)
                        continue
                    }
                    
                    val tvPosMs = _state.value.positionMs
                    val extPosMs = extractor.sampleTime / 1000
                    if (kotlin.math.abs(tvPosMs - extPosMs) > 300) {
                        extractor.seekTo(tvPosMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        codec.flush()
                    }

                    if (!isEOS) {
                        val inIdx = codec.dequeueInputBuffer(10000)
                        if (inIdx >= 0) {
                            val buffer = codec.getInputBuffer(inIdx)
                            val sampleSize = extractor.readSampleData(buffer!!, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10000)
                    if (outIdx >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIdx)
                        if (outBuffer != null && info.size > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastFrameTimeMs >= 50) {
                                lastFrameTimeMs = now
                                
                                val pcm16 = ShortArray(info.size / 2)
                                outBuffer.asShortBuffer().get(pcm16)
                                
                                val wave = ByteArray(64)
                                val bands = ByteArray(64)
                                
                                val step = maxOf(1, pcm16.size / 64)
                                for (i in 0 until 64) {
                                    val idx = (i * step).coerceAtMost(pcm16.size - 1)
                                    val sample = pcm16[idx]
                                    wave[i] = ((sample / 32768f) * 127 + 128).toInt().coerceIn(0, 255).toByte()
                                    var maxAmp = 0f
                                    for (j in 0 until step) {
                                        val sIdx = (idx + j).coerceAtMost(pcm16.size - 1)
                                        val amp = kotlin.math.abs(pcm16[sIdx] / 32768f)
                                        if (amp > maxAmp) maxAmp = amp
                                    }
                                    bands[i] = (maxAmp * 255).toInt().coerceIn(0, 255).toByte()
                                }
                                
                                val msg = JSONObject().apply {
                                    put("action", "VIS_FRAME")
                                    val bArray = org.json.JSONArray()
                                    val wArray = JSONArray()
                                    for (i in 0 until 64) {
                                        bArray.put(bands[i].toInt() and 0xFF)
                                        wArray.put(wave[i].toInt() and 0xFF)
                                    }
                                    put("bands", bArray)
                                    put("wave", wArray)
                                    put("t", (extractor.sampleTime / 1_000_000.0))
                                }
                                
                                mBoxCastServer?.broadcastMessage(msg.toString())
                                visualizerManager.setRemoteBands(bands)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                    delay(10)
                }
            } catch (e: Exception) {
                Log.e("MBoxTv", "Error in remote visualizer", e)
            } finally {
                try { codec?.stop() } catch(e: Exception){}
                try { codec?.release() } catch(e: Exception){}
                try { extractor.release() } catch(e: Exception){}
            }
        }
    }

    private fun stopRemoteVisualizer() {
        remoteVisJob?.cancel()
        remoteVisJob = null
    }

    private var isTvPlaybackConfirmed = false
    private var applyingRemoteState = false
    private var tvQueueIds: List<Long>? = null
    private var songPickRetryJob: Job? = null
    private var pendingPickedTrackId: String? = null
    private val recentEventsList = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val recentCommandsList = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val rttList = java.util.Collections.synchronizedList(mutableListOf<Long>())
    private var pingSeq = 0L
    private var lastAppliedSeq = -1L

    private fun handleMBoxTvEvent(event: String, payload: JSONObject) {
        applyingRemoteState = true
        try {
            val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            synchronized(recentEventsList) {
                recentEventsList.add("[$nowStr] $event: ${payload.toString().take(60)}")
                if (recentEventsList.size > 20) recentEventsList.removeAt(0)
            }

            Log.d("MBoxTv", "Event: $event, Payload: $payload")
            when (event) {
                "TV_READY" -> {
                    Log.i("MBoxCast", "TV_READY ${payload.optString("deviceName", "(socket open)")}")
                    val devName = payload.optString("deviceName", payload.optString("name", "m-box TV"))
                    _state.update { it.copy(isMBoxTvConnected = true, tvDeviceName = devName, activeOutputDevice = "m-box TV") }
                    if (devName != "m-box TV") com.mediaplayer.cast.TvRemoteVolume.setTvName(devName)
                    tvReadyDeferred?.complete(true)
                    com.mediaplayer.cast.SamsungTvLauncher.noteTvReady()

                    // The server reports the socket opening AND the TV's own TV_READY: send the queue once.
                    val nowMs = System.currentTimeMillis()
                    val playlist = _state.value.currentPlaylist
                    val wasPending = pendingTvCast
                    if (nowMs - lastTvReadyQueueAt <= 3000) {
                        Log.i("MBoxCast", "TV_READY: queue already sent ${nowMs - lastTvReadyQueueAt} ms ago")
                    } else if (playlist.isNotEmpty()) {
                        lastTvReadyQueueAt = nowMs; pendingTvCast = false
                        val curIndex = _state.value.currentTrackIndex.coerceIn(0, playlist.size - 1)
                        Log.i("MBoxCast", "TV_READY -> sending the queue (${playlist.size} songs, index $curIndex, ${_state.value.positionMs / 1000}s${if (wasPending) ", after the tap" else ""})")
                        sendLoadQueueToTv(playlist, curIndex, _state.value.positionMs, "TV_READY handover")
                    } else {
                        val t = _state.value.currentTrack
                        if (t != null) {
                            lastTvReadyQueueAt = nowMs; pendingTvCast = false
                            Log.i("MBoxCast", "TV_READY -> no queue, sending the current song")
                            sendPlayTrackToTv(t, _state.value.positionMs, "TV_READY handover")
                        } else Log.i("MBoxCast", "TV_READY: nothing to send (no queue, no song)")
                    }

                    // Volume: phone buttons -> TV; TV remote keys need the remote-control socket.
                    com.mediaplayer.cast.TvRemoteVolume.setActive(true, "casting to the TV")
                    (mBoxCastServer?.lastTvIp)?.let { com.mediaplayer.cast.SamsungTvLauncher.ensureRemote(context, it) }
                    mBoxCastServer?.broadcastMessage(JSONObject().put("action", "GET_VOLUME").toString())
                }
            "TV_DISCONNECTED" -> {
                Log.i("MBoxCast", "TV app disconnected")
                _state.update { it.copy(isMBoxTvConnected = false) }
                // Still casting (the TV reconnects by itself): keep the keep-alive service and the volume
                // buttons on the TV. Only "This phone" / another output turns them off.
                if (_state.value.activeOutputDevice != "m-box TV") com.mediaplayer.cast.TvRemoteVolume.setActive(false, "TV app disconnected")
            }
            "VOLUME" -> {
                val lvl = payload.optInt("level", castState.value.tvVolume)
                val m = payload.optBoolean("muted", false)
                val from = payload.optString("from", "")
                val applied = if (payload.has("applied") && !payload.isNull("applied")) payload.optBoolean("applied") else null
                castState.update { it.copy(tvVolume = lvl, isMuted = m, volumeSupported = payload.optBoolean("supported", true)) }
                com.mediaplayer.cast.TvRemoteVolume.update(lvl, m)
                Log.i("MBoxCast", "TV volume $lvl${if (m) " (muted)" else ""}" + (if (from.isNotBlank()) " from $from" else "") + (applied?.let { ", applied=$it" } ?: ""))
                if (from == "phone" && applied == false && !appVolumeIgnored) {
                    appVolumeIgnored = true
                    Log.i("MBoxCast", "TV ignored the app's volume change (soundbar?) — using the TV remote keys from now on")
                }
            }
            "COMMAND_FLOOD" -> {
                Log.e("MBoxTv", "COMMAND_FLOOD detected from TV! Loop safeguard triggered.")
            }
            "BACKEND_FALLBACK" -> {
                Log.i("MBoxTv", "TV Backend Fallback event received: $payload")
            }
            "VISUALIZER_CHANGED" -> {
                val name = payload.optString("name", "")
                if (name.isNotBlank()) {
                    _state.update { it.copy(activeVisualizerMode = name) }
                }
            }
            "VIS_SOURCE" -> {
                val source = payload.optString("source", "tv")
                if (source == "phone") {
                    startRemoteVisualizer()
                } else {
                    stopRemoteVisualizer()
                }
            }
            "VIS_FRAME" -> {
                val bandsArray = payload.optJSONArray("bands")
                if (bandsArray != null && bandsArray.length() >= 64) {
                    val bands = ByteArray(64)
                    for (i in 0 until 64) {
                        bands[i] = bandsArray.optInt(i, 0).toByte()
                    }
                    visualizerManager.setRemoteBands(bands)
                }
            }
            "PLAYBACK_STARTED" -> {
                isTvPlaybackConfirmed = true
                val isPlaying = payload.optBoolean("isPlaying", true)
                val currentTime = (payload.optDouble("currentTime", 0.0) * 1000).toLong()
                val duration = (payload.optDouble("duration", 0.0) * 1000).toLong()

                if (_state.value.activeOutputDevice == "m-box TV") {
                    _state.update {
                        it.copy(
                            isPlaying = isPlaying,
                            positionMs = currentTime,
                            durationMs = if (duration > 0) duration else it.durationMs
                        )
                    }
                }
            }
            "TRACK_CHANGED" -> {
                val queueIndex = payload.optInt("index", -1)
                val trackId = payload.optString("id", "")
                val isPlaying = payload.optBoolean("isPlaying", true)
                val durationSec = payload.optDouble("durationSec", 0.0)

                val playlist = _state.value.currentPlaylist
                val track = playlist.firstOrNull { it.id.toString() == trackId }
                    ?: if (queueIndex >= 0) playlist.getOrNull(queueIndex) else null

                if (track != null) {
                    _state.update {
                        it.copy(
                            currentTrack = track,
                            currentTrackIndex = if (queueIndex >= 0) queueIndex else it.currentTrackIndex,
                            isPlaying = isPlaying,
                            positionMs = 0L,
                            durationMs = if (durationSec > 0) (durationSec * 1000).toLong() else track.duration
                        )
                    }
                    updateAudioSpecForTrack(track)
                }
            }
            "QUEUE_ENDED" -> {
                if (_state.value.activeOutputDevice == "m-box TV") {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
            "PAUSE_STATE" -> {
                val isPlaying = payload.optBoolean("isPlaying", false)
                val currentTime = (payload.optDouble("currentTime", 0.0) * 1000).toLong()
                if (_state.value.activeOutputDevice == "m-box TV") {
                    _state.update {
                        it.copy(
                            isPlaying = isPlaying,
                            positionMs = if (currentTime > 0) currentTime else it.positionMs
                        )
                    }
                }
            }
            "SEEK_RESULT" -> {
                val currentTime = (payload.optDouble("currentTime", 0.0) * 1000).toLong()
                val duration = (payload.optDouble("duration", 0.0) * 1000).toLong()
                if (_state.value.activeOutputDevice == "m-box TV") {
                    _state.update {
                        it.copy(
                            positionMs = currentTime,
                            durationMs = if (duration > 0) duration else it.durationMs
                        )
                    }
                }
            }
            "STATE" -> {
                val isPlaying = payload.optBoolean("isPlaying", true)
                val currentTime = (payload.optDouble("currentTime", 0.0) * 1000).toLong()
                val duration = (payload.optDouble("duration", 0.0) * 1000).toLong()
                val queueIndex = payload.optInt("queueIndex", -1)
                val trackId = payload.optString("trackId", "")
                if (payload.has("volume") && !payload.isNull("volume") && payload.opt("volume") is Number) {
                    val lvl = payload.optInt("volume"); val m = payload.optBoolean("muted", false)
                    if (lvl != castState.value.tvVolume || m != castState.value.isMuted) {
                        castState.update { it.copy(tvVolume = lvl, isMuted = m) }
                        com.mediaplayer.cast.TvRemoteVolume.update(lvl, m)
                    }
                }

                if (_state.value.activeOutputDevice == "m-box TV") {
                    val playlist = _state.value.currentPlaylist
                    val track = playlist.firstOrNull { it.id.toString() == trackId }
                        ?: if (queueIndex >= 0) playlist.getOrNull(queueIndex) else null

                    _state.update {
                        it.copy(
                            currentTrack = track ?: it.currentTrack,
                            currentTrackIndex = if (queueIndex >= 0) queueIndex else it.currentTrackIndex,
                            isPlaying = if (isTvPlaybackConfirmed) isPlaying else true,
                            positionMs = currentTime,
                            durationMs = if (duration > 0) duration else it.durationMs
                        )
                    }
                }
            }
            "NEED_TRACK", "STREAM_ERROR" -> {
                val reason = payload.optString("reason", "")
                Log.e("MBoxTv", "TV Event $event received. Reason: $reason")
                isTvPlaybackConfirmed = false
                val currentTrack = _state.value.currentTrack
                if (currentTrack != null && _state.value.activeOutputDevice == "m-box TV") {
                    if (event == "STREAM_ERROR") {
                        if (currentTrack.id != lastErrorTrackId) {
                            lastErrorTrackId = currentTrack.id
                            errorRetryCount = 0
                        }
                        if (errorRetryCount >= 1 || (!reason.startsWith("SAMPLE_RATE_ABOVE_48K", true) && !reason.startsWith("STALLED", true))) {
                            Log.e("MBoxTv", "Fatal TV Stream Error: $reason")
                            viewModelScope.launch(Dispatchers.Main) {
                                Toast.makeText(context, "TV couldn't play this track", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        errorRetryCount++
                        val force48k = reason.startsWith("SAMPLE_RATE_ABOVE_48K", ignoreCase = true)
                        sendPlayTrackToTv(currentTrack, _state.value.positionMs, "TV stream error retry", forceDownsampleTo48k = force48k)
                    } else {
                        sendPlayTrackToTv(currentTrack, _state.value.positionMs, "TV request")
                    }
                }
            }
        }
    } finally {
        applyingRemoteState = false
    }
}

    fun sendPlayTrackToTv(
        track: Track,
        startPositionMs: Long = 0L,
        reason: String = "track change",
        forceDownsampleTo48k: Boolean = false
    ) {
        val server = mBoxCastServer ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val originalUri = Uri.parse(track.uri)

            var probedMime = ""
            var probedChannels = 2
            var probedSampleRate = 48000
            var probedBitrate = -1

            val extractor = MediaExtractor()
            try {
                if (track.uri.startsWith("content://")) {
                    val pfd = context.contentResolver.openFileDescriptor(originalUri, "r")
                    if (pfd != null) {
                        extractor.setDataSource(pfd.fileDescriptor)
                        pfd.close()
                    } else {
                        extractor.setDataSource(context, originalUri, null)
                    }
                } else {
                    val filePath = if (track.uri.startsWith("file://")) originalUri.path ?: track.uri else track.uri
                    val file = File(filePath)
                    if (file.exists()) extractor.setDataSource(file.absolutePath)
                    else extractor.setDataSource(context, originalUri, null)
                }

                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        probedMime = mime
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) probedChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) probedSampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) probedBitrate = fmt.getInteger(MediaFormat.KEY_BIT_RATE)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w("MBoxTv", "MediaExtractor format probe error: ${e.message}")
            } finally {
                try { extractor.release() } catch (e: Exception) {}
            }

            val codecName = when {
                probedMime.contains("eac3-joc", true) -> "E-AC-3 JOC"
                probedMime.contains("eac3", true) || probedMime.contains("ec3", true) -> "E-AC-3"
                probedMime.contains("ac3", true) -> "AC-3"
                probedMime.contains("alac", true) || track.mimeType.contains("alac", true) -> "ALAC"
                probedMime.contains("flac", true) || track.mimeType.contains("flac", true) -> "FLAC"
                probedMime.contains("opus", true) || track.mimeType.contains("opus", true) -> "OPUS"
                probedMime.contains("mp4a", true) || probedMime.contains("aac", true) -> "AAC"
                else -> track.mimeType.substringAfterLast("/").uppercase().ifBlank { "MP3" }
            }

            if (codecName == "E-AC-3 JOC") {
                probedChannels = 6
                if (probedBitrate <= 0) probedBitrate = 768000
            }

            val origSampleRate = if (probedSampleRate > 0) probedSampleRate else if (track.sampleRate > 0) track.sampleRate else 48000
            val isHiRes = origSampleRate > 48000 || forceDownsampleTo48k

            val targetSampleRate = if (isHiRes) {
                if (origSampleRate % 44100 == 0) 44100 else 48000
            } else {
                origSampleRate
            }

            if (isHiRes) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Preparing for TV…", Toast.LENGTH_SHORT).show()
                }
            }

            val streamUrl = if (isHiRes) {
                val cacheDir = File(context.cacheDir, "tv_resampled_cache").apply { if (!exists()) mkdirs() }
                val resampledFile = File(cacheDir, "tv_48k_${track.id}_$targetSampleRate.flac")

                if (!resampledFile.exists() || resampledFile.length() <= 0) {
                    val inputPath = if (track.uri.startsWith("content://")) {
                        FFmpegKitConfig.getSafParameterForRead(context, originalUri)
                    } else if (track.uri.startsWith("file://")) {
                        originalUri.path ?: track.uri
                    } else {
                        track.uri
                    }
                    val cmd = "-y -i \"$inputPath\" -ar $targetSampleRate -c:a flac \"${resampledFile.absolutePath}\""
                    Log.i("MBoxTv", "Resampling Hi-Res audio for TV: $cmd")
                    runCatching { FFmpegKit.execute(cmd) }
                }
                server.getStreamUrl(track.id.toString(), resampledFile, null, "audio/flac")
            } else {
                if (track.uri.startsWith("file://") || track.uri.startsWith("/")) {
                    val file = File(if (track.uri.startsWith("file://")) originalUri.path ?: track.uri else track.uri)
                    server.getStreamUrl(track.id.toString(), file, null, track.mimeType)
                } else {
                    server.getStreamUrl(track.id.toString(), null, originalUri, track.mimeType)
                }
            } ?: ""

            val rawArtUri = track.albumArtUri
            val artworkUrl = server.getArtworkUrl(track.id.toString(), rawArtUri?.let { Uri.parse(it) }) ?: ""

            val isLossless = codecName == "FLAC" || codecName == "ALAC" || codecName == "PCM" || isHiRes

            val playMsg = JSONObject().apply {
                put("action", "PLAY_TRACK")
                put("id", track.id.toString())
                put("url", streamUrl)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("artworkUrl", artworkUrl)
                put("mimeType", if (isHiRes) "audio/flac" else if (probedMime.isNotBlank()) probedMime else track.mimeType.ifBlank { "audio/mpeg" })
                put("codec", if (isHiRes) "FLAC" else codecName)
                if (isLossless) {
                    put("bitDepth", 24)
                }
                put("sampleRate", targetSampleRate)
                if (isHiRes) {
                    put("sourceSampleRate", origSampleRate)
                }
                put("channels", probedChannels)
                if (probedBitrate > 0) {
                    put("bitrate", probedBitrate)
                }
                put("durationSec", (track.duration / 1000).toInt())
                put("position", (startPositionMs / 1000.0))
                put("reason", reason)
            }

            isTvPlaybackConfirmed = false
            Log.i("MBoxCastServer", "Sent to TV: PLAY_TRACK (reason: $reason) url=$streamUrl sampleRate=$targetSampleRate position=${startPositionMs / 1000.0}")
            server.broadcastMessage(playMsg.toString())
        }
    }

    fun sendLoadQueueToTv(
        tracks: List<Track>,
        selectedIndex: Int = 0,
        positionMs: Long = 0L,
        reason: String = "user action",
        paused: Boolean? = null          // null = keep the phone's play/pause state; a song pick always plays
    ) {
        val server = mBoxCastServer ?: return

        viewModelScope.launch(Dispatchers.IO.limitedParallelism(2)) {
            try {
                val startTimeNanos = System.nanoTime()
                val totalCount = tracks.size
                val windowStart = (selectedIndex - 250).coerceAtLeast(0)
                val windowEnd = (selectedIndex + 250).coerceAtMost(totalCount)
                val windowTracks = tracks.subList(windowStart, windowEnd)
                val adjustedIndex = selectedIndex - windowStart

                val itemsArray = JSONArray()
                for (t in windowTracks) {
                    val streamUrl = server.getStreamUrl(t.id.toString(), null, Uri.parse(t.uri), t.mimeType) ?: ""
                    val artUrl = server.getArtworkUrl(t.id.toString(), t.albumArtUri?.let { Uri.parse(it) }) ?: ""

                    val dolbyInfo = Eac3Transcoder.getDolbyInfo(context, t)
                    val fmt = if (dolbyInfo.isJoc) "E-AC3 JOC" else if (dolbyInfo.isDolby) "E-AC3" else t.mimeType.substringAfterLast("/").uppercase()

                    val itemObj = JSONObject().apply {
                        put("id", t.id.toString())
                        put("url", streamUrl)
                        put("title", com.mediaplayer.ui.components.cleanTrackTitle(t.title, t.uri))
                        put("artist", t.artist)
                        put("album", t.album)
                        put("durationSec", t.duration / 1000)
                        put("artworkUrl", artUrl)
                        put("mimeType", t.mimeType)
                        put("codec", fmt)
                        if (dolbyInfo.isJoc) {
                            put("atmosObjects", dolbyInfo.atmosObjects ?: 16)
                        }
                        put("bitDepth", if (t.sampleRate > 48000 || t.bitrate > 1500000) 24 else 16)
                        put("sampleRate", if (t.sampleRate > 0) t.sampleRate else 48000)
                        put("channels", if (dolbyInfo.isDolby) 6 else 2)
                    }
                    itemsArray.put(itemObj)
                }

                val repeatStr = when (_state.value.repeatMode) {
                    1 -> "one"
                    2 -> "all"
                    else -> "off"
                }

                val queueMsg = JSONObject().apply {
                    put("action", "LOAD_QUEUE")
                    put("queueId", _state.value.playingFromPath.ifBlank { _state.value.playingFromName })
                    put("items", itemsArray)
                    put("index", adjustedIndex)
                    put("position", positionMs / 1000.0)
                    put("repeat", repeatStr)
                    put("shuffle", _state.value.shuffleOn)
                    put("paused", paused ?: !_state.value.isPlaying)
                    put("reason", reason)
                }

                val elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000L
                Log.i("MBoxCast", "queue built (${itemsArray.length()} items, ${elapsedMs}ms) -> LOAD_QUEUE sent")
                server.broadcastMessage(queueMsg.toString())
                tvQueueIds = windowTracks.map { it.id }
            } catch (t: Throwable) {
                Log.e("MBoxCast", "Error in sendLoadQueueToTv: ${t.message}", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't play on TV — playing on this phone", Toast.LENGTH_SHORT).show()
                    selectOutputDevice("This phone")
                }
            }
        }
    }

    fun sendNextToTv() = sendTvControl("NEXT", "user next")
    fun sendPrevToTv() = sendTvControl("PREV", "user prev")
    fun sendSkipToTv(index: Int) = sendTvControl("SKIP_TO", "user skip", seekPositionMs = index.toLong())
    fun sendSetRepeatToTv(mode: String) {
        val server = mBoxCastServer ?: return
        val msg = JSONObject().apply {
            put("action", "SET_REPEAT")
            put("mode", mode)
        }
        server.broadcastMessage(msg.toString())
    }
    fun sendSetShuffleToTv(on: Boolean) {
        val server = mBoxCastServer ?: return
        val msg = JSONObject().apply {
            put("action", "SET_SHUFFLE")
            put("on", on)
        }
        server.broadcastMessage(msg.toString())
    }

    // ---------- TV volume ----------
    // Path A: the TV remote keys (KEY_VOLUP/DOWN/MUTE over the 8002 remote-control socket) — exactly like the
    //         TV remote, so the TV shows its own volume bar and a soundbar (Q-Symphony / eARC) follows.
    // Path B: M-Box's own volume (SET_VOLUME / VOLUME_UP …) — exact levels for the slider, but a soundbar may
    //         ignore it (the TV then answers applied=false and we switch the slider to path A).
    @Volatile private var appVolumeIgnored = false   // logged only; the keys are used whenever the socket is open

    private fun ensureTvKeys() {
        if (!com.mediaplayer.cast.SamsungTvLauncher.hasRemote()) mBoxCastServer?.lastTvIp?.let { com.mediaplayer.cast.SamsungTvLauncher.ensureRemote(context, it) }
    }

    /**
     * Volume buttons: one step, always as the TV remote's own key (KEY_VOLUP/DOWN over the 8002
     * remote-control socket), so the TV shows its Q-Symphony bar and the soundbar follows.
     * M-Box's own volume (SET_VOLUME / VOLUME_UP) is no longer used: with the soundbar it changed a
     * different level and the volume jumped (videos 2026-10-22).
     */
    fun sendVolumeStepToTv(isUp: Boolean) {
        ensureTvKeys()
        if (com.mediaplayer.cast.SamsungTvLauncher.sendKey(if (isUp) "KEY_VOLUP" else "KEY_VOLDOWN")) {
            Log.i("MBoxCast", "volume ${if (isUp) "up" else "down"} -> TV remote key")
        } else {
            Log.i("MBoxCast", "volume ${if (isUp) "up" else "down"}: TV remote-control socket not open yet — reconnecting, press again")
        }
    }

    /**
     * The phone's TV volume slider was dragged: reach [target] with the TV remote keys, one press
     * at a time (70 ms apart), re-reading the TV's level after each press — so it can never jump.
     * A new drag position replaces the old target.
     */
    @Volatile private var tvVolumeTarget = -1
    private var tvVolumeJob: Job? = null
    fun moveTvVolumeTo(target: Int) {
        tvVolumeTarget = target
        if (tvVolumeJob?.isActive == true) return
        ensureTvKeys()
        tvVolumeJob = viewModelScope.launch(Dispatchers.IO) {
            var presses = 0
            while (presses < 100) {
                val diff = tvVolumeTarget - com.mediaplayer.cast.TvRemoteVolume.level
                if (diff == 0) break
                if (!com.mediaplayer.cast.SamsungTvLauncher.sendKey(if (diff > 0) "KEY_VOLUP" else "KEY_VOLDOWN")) break
                presses++
                delay(70)
            }
            Log.i("MBoxCast", "TV volume slider -> $tvVolumeTarget: $presses key presses, TV now ${com.mediaplayer.cast.TvRemoteVolume.level}")
        }
    }

    fun sendSetMuteToTv(on: Boolean) {
        if (applyingRemoteState) return
        ensureTvKeys()
        if (on != castState.value.isMuted) com.mediaplayer.cast.SamsungTvLauncher.sendKey("KEY_MUTE")
    }

    override fun sendSetLyricsToTv(on: Boolean) {
        if (applyingRemoteState) return
        val server = mBoxCastServer ?: return
        val msg = JSONObject().apply {
            put("action", "SET_LYRICS")
            put("on", on)
        }
        server.broadcastMessage(msg.toString())
    }

    override fun sendSetDisplayModeToTv(mode: String) {
        if (applyingRemoteState) return
        val server = mBoxCastServer ?: return
        val msg = JSONObject().apply {
            put("action", "SET_DISPLAY")
            put("mode", mode)
        }
        server.broadcastMessage(msg.toString())
    }

    fun sendTvControl(action: String, reason: String = "user tap", seekPositionMs: Long? = null) {
        if (applyingRemoteState) return // Rule R2: NEVER send command in response to remote state update!
        val server = mBoxCastServer ?: return

        val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(recentCommandsList) {
            recentCommandsList.add("[$nowStr] $action (reason: $reason)")
            if (recentCommandsList.size > 20) recentCommandsList.removeAt(0)
        }

        if (action == "RESUME" && !isTvPlaybackConfirmed) {
            val currentTrack = _state.value.currentTrack
            if (currentTrack != null) {
                sendPlayTrackToTv(currentTrack, _state.value.positionMs, reason)
                return
            }
        }
        val msg = JSONObject().apply {
            put("action", action)
            put("reason", reason)
            if (seekPositionMs != null) {
                put("position", seekPositionMs / 1000.0)
            }
        }
        Log.i("MBoxCastServer", "Sent to TV: $action (reason: $reason)" + if (seekPositionMs != null) " position=${seekPositionMs / 1000.0}" else "")
        server.broadcastMessage(msg.toString())
    }

    private var handoffDeferred: kotlinx.coroutines.CompletableDeferred<JSONObject>? = null
    private var lastTvReadyQueueAt = 0L
    private var lastAudioDiagKey = ""
    @Volatile private var pendingTvCast = false

    fun selectOutputDevice(deviceName: String) {
        if (deviceName != "m-box TV") com.mediaplayer.cast.TvRemoteVolume.setActive(false, "output: $deviceName")
        if (deviceName == "m-box TV" && mBoxCastServer?.isTvConnected() != true) {
            // M-Box is not open on the TV: open it (the TV asks Allow/Deny the first time).
            // The queue is sent when the TV app connects back (TV_READY).
            val tvIp = mBoxCastServer?.lastTvIp
                ?: context.getSharedPreferences("mbox_tv", Context.MODE_PRIVATE).getString("last_tv_ip", null)
                ?: "192.168.1.3"
            Log.i("MBoxCast", "tap: m-box TV — M-Box not connected, opening it on $tvIp")
            _state.update { it.copy(activeOutputDevice = "m-box TV") }
            browser?.pause()
            pendingTvCast = true; lastTvReadyQueueAt = 0L
            connectAndLaunchTv(tvIp, "", "")
            return
        }
        if (deviceName == "m-box TV") {
            Log.i("MBoxCast", "tap: m-box TV — M-Box already connected, sending the queue")
            _state.update { it.copy(activeOutputDevice = "m-box TV") }
            browser?.pause()
            if (_state.value.currentPlaylist.isNotEmpty()) {
                sendLoadQueueToTv(_state.value.currentPlaylist, _state.value.currentTrackIndex, _state.value.positionMs, "selectOutputDevice")
            } else {
                val currentTrack = _state.value.currentTrack
                if (currentTrack != null) {
                    sendPlayTrackToTv(currentTrack, _state.value.positionMs)
                }
            }
        } else if (deviceName == "This phone") {
            pendingTvCast = false
            if (_state.value.activeOutputDevice == "m-box TV") {
                viewModelScope.launch(Dispatchers.IO) {
                    performHandoffBackToPhone()
                }
            } else {
                _state.update { it.copy(activeOutputDevice = "This phone") }
                browser?.let { b ->
                    b.seekTo(_state.value.positionMs)
                    b.prepare()
                    b.play()
                }
            }
        }
    }

    private var lastHandoffToPhoneMs = 0L

    private fun buildMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(metadata)
            .build()
    }

    suspend fun performHandoffBackToPhone() {
        val handoffStartNanos = System.nanoTime()
        lastHandoffToPhoneMs = System.currentTimeMillis()

        val deferred = kotlinx.coroutines.CompletableDeferred<JSONObject>()
        handoffDeferred = deferred

        val msg = JSONObject().apply {
            put("action", "HANDOFF")
            put("reason", "user output switch to phone")
        }
        mBoxCastServer?.broadcastMessage(msg.toString())

        val handoffState = kotlinx.coroutines.withTimeoutOrNull(1500L) {
            deferred.await()
        }
        handoffDeferred = null

        val tvTrackId = handoffState?.optString("trackId")?.ifBlank { null }
            ?: handoffState?.optString("id")?.ifBlank { null }
            ?: castState.value.trackId

        val tvPosMs = if (handoffState != null && handoffState.has("position")) {
            (handoffState.optDouble("position", 0.0) * 1000).toLong().coerceAtLeast(0L)
        } else {
            (castState.value.shownPositionSec * 1000).toLong().coerceAtLeast(0L)
        }

        val tvPlaying = handoffState?.optBoolean("isPlaying") ?: castState.value.isPlaying
        val tvQueueIndex = handoffState?.optInt("queueIndex") ?: castState.value.queueIndex

        val queueIdsList = mutableListOf<Long>()
        if (handoffState != null && handoffState.has("queueIds")) {
            val arr = handoffState.optJSONArray("queueIds")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val itemVal = arr.opt(i)
                    val idLong = when (itemVal) {
                        is Number -> itemVal.toLong()
                        is String -> itemVal.toLongOrNull()
                        else -> null
                    }
                    if (idLong != null) queueIdsList.add(idLong)
                }
            }
        }

        val allTracks = trackDao.getAllTracks().first()

        val targetPlaylist = if (queueIdsList.isNotEmpty()) {
            queueIdsList.mapNotNull { qId -> allTracks.find { t -> t.id == qId } }
        } else {
            _state.value.currentPlaylist.ifEmpty { allTracks }
        }

        val targetTrack = targetPlaylist.firstOrNull { it.id.toString() == tvTrackId }
            ?: if (tvQueueIndex in targetPlaylist.indices) targetPlaylist[tvQueueIndex] else _state.value.currentTrack

        val targetIndex = if (targetTrack != null && targetPlaylist.contains(targetTrack)) {
            targetPlaylist.indexOf(targetTrack)
        } else {
            tvQueueIndex.coerceIn(0, (targetPlaylist.size - 1).coerceAtLeast(0))
        }

        if (handoffState != null) {
            Log.i("MBoxCast", "leave cast: HANDOFF_STATE '${targetTrack?.title}' @${tvPosMs / 1000}s playing=$tvPlaying queue=${targetPlaylist.size} index=$targetIndex")
        } else {
            Log.i("MBoxCast", "leave cast: using last STATE '${targetTrack?.title}' @${tvPosMs / 1000}s playing=$tvPlaying queue=${targetPlaylist.size} index=$targetIndex")
        }

        val mediaItems = targetPlaylist.map { t -> buildMediaItem(t) }

        withContext(Dispatchers.Main) {
            com.mediaplayer.cast.TvRemoteVolume.setActive(false, "left cast: handover to phone")

            _state.update {
                it.copy(
                    activeOutputDevice = "This phone",
                    isCasting = false,
                    currentPlaylist = targetPlaylist,
                    currentTrackIndex = if (targetIndex in targetPlaylist.indices) targetIndex else 0,
                    currentTrack = targetTrack,
                    positionMs = tvPosMs,
                    isPlaying = tvPlaying
                )
            }

            browser?.let { b ->
                if (mediaItems.isNotEmpty()) {
                    b.setMediaItems(mediaItems, targetIndex.coerceIn(0, mediaItems.size - 1), tvPosMs)
                    b.repeatMode = _state.value.repeatMode
                    b.shuffleModeEnabled = _state.value.shuffleOn
                    b.prepare()
                    if (tvPlaying) {
                        b.play()
                    } else {
                        b.pause()
                    }
                }
            }

            if (targetTrack != null) {
                updateAudioSpecForTrack(targetTrack)
            }

            val elapsedMs = (System.nanoTime() - handoffStartNanos) / 1_000_000L
            Log.i("MBoxCast", "local load: '${targetTrack?.title}' @${tvPosMs / 1000}s -> prepared/playing (took ${elapsedMs}ms)")
        }
    }

    private var tvReadyDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    fun connectAndLaunchTv(tvIpInput: String, tvNameInput: String, tvMacInput: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneIp = com.mediaplayer.cast.SamsungTvLauncher.phoneWifiIp(context)
            val tvIp = if (tvIpInput.isNotBlank()) tvIpInput else "192.168.1.3"

            if (phoneIp == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Phone is not on Wi-Fi", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            com.mediaplayer.cast.SamsungTvLauncher.connectAndLaunch(
                context = context,
                ip = tvIp,
                phoneIp = phoneIp,
                port = 8787,
                listener = object : com.mediaplayer.cast.SamsungTvLauncher.Listener {
                    override fun onStatus(text: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onLaunched(tvName: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _state.update { it.copy(tvDeviceName = tvName, activeOutputDevice = "m-box TV") }
                            Toast.makeText(context, "Connecting…", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailed(reason: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun initCastPlayer() {
        Log.d("CastDebug", "initCastPlayer() called")
        initMBoxCastServer()
        try {
            if (localAudioServer == null) {
                localAudioServer = LocalAudioServer(context.applicationContext)
            }
            val castExecutor = Executors.newSingleThreadExecutor()
            CastContext.getSharedInstance(context.applicationContext, castExecutor)
                .addOnSuccessListener { castContext ->
                    Log.d("CastDebug", "CastContext.getSharedInstance success")
                    val cp = CastPlayer(castContext)
                    castPlayer = cp

                    cp.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("CastDebug", "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
                            castTimeoutJob?.cancel()
                            _state.update {
                                it.copy(
                                    castState = CastConnectionState.ERROR,
                                    castErrorMessage = "Couldn't connect"
                                )
                            }
                            viewModelScope.launch(Dispatchers.Main) {
                                val currentTrackTitle = _state.value.currentTrack?.title ?: "track"
                                Toast.makeText(
                                    context.applicationContext,
                                    "Couldn't load '$currentTrackTitle' on Cast device",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            Log.d("CastDebug", "onPlaybackStateChanged: $state, duration after state change = ${cp.duration}")
                            if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                                castTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        castState = CastConnectionState.CONNECTED,
                                        isCasting = true
                                    )
                                }
                            } else if (state == Player.STATE_IDLE && cp.playerError != null) {
                                castTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        castState = CastConnectionState.ERROR,
                                        castErrorMessage = "Couldn't connect"
                                    )
                                }
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            Log.d("CastDebug", "onMediaItemTransition: ${mediaItem?.localConfiguration?.uri}")
                            if (mediaItem != null) {
                                castTimeoutJob?.cancel()
                                _state.update {
                                    it.copy(
                                        castState = CastConnectionState.CONNECTED,
                                        isCasting = true
                                    )
                                }
                            }
                        }
                    })

                    cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable() {
                            Log.d("CastDebug", "onCastSessionAvailable fired")
                            try {
                                val deviceName = try {
                                    castContext.sessionManager.currentCastSession?.castDevice?.friendlyName ?: "Cast Device"
                                } catch (e: Exception) {
                                    "Cast Device"
                                }
                                Log.d("CastDebug", "Device selected: $deviceName")

                                _state.update {
                                    it.copy(
                                        castState = CastConnectionState.CONNECTING,
                                        castDeviceName = deviceName,
                                        castErrorMessage = ""
                                    )
                                }

                                castTimeoutJob?.cancel()
                                castTimeoutJob = viewModelScope.launch {
                                    delay(10_000)
                                    if (_state.value.castState == CastConnectionState.CONNECTING) {
                                        Log.w("CastDebug", "Cast connection timed out after 10s")
                                        _state.update {
                                            it.copy(
                                                castState = CastConnectionState.ERROR,
                                                castErrorMessage = "Couldn't connect"
                                            )
                                        }
                                    }
                                }

                                val position = browser?.currentPosition ?: 0L
                                Log.d("CastDebug", "Local position = $position")

                                val currentTrack = _state.value.currentTrack
                                    ?: browser?.currentMediaItem?.let { item ->
                                        val uri = item.localConfiguration?.uri?.toString() ?: ""
                                        val title = item.mediaMetadata.title?.toString() ?: "Now Playing"
                                        val artist = item.mediaMetadata.artist?.toString() ?: "M-Box Player"
                                        val album = item.mediaMetadata.albumTitle?.toString() ?: "Music"
                                        val artworkUri = item.mediaMetadata.artworkUri?.toString()
                                        Track(
                                            id = -1L,
                                            uri = uri,
                                            title = title,
                                            artist = artist,
                                            album = album,
                                            albumArtUri = artworkUri,
                                            duration = browser?.duration ?: 0L,
                                            bitrate = 0,
                                            sampleRate = 44100,
                                            trackNumber = 0,
                                            discNumber = 0,
                                            year = 0,
                                            genre = "",
                                            composer = "",
                                            comment = "",
                                            fileSize = 0L,
                                            dateAdded = System.currentTimeMillis(),
                                            mimeType = "audio/mpeg",
                                            folderName = "",
                                            folderPath = ""
                                        )
                                    }

                                val currentTrackUriStr = currentTrack?.uri ?: ""
                                Log.d("CastDebug", "onCastSessionAvailable READ currentTrackUriStr: '$currentTrackUriStr' from ${Thread.currentThread().name}")
                                Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")

                                if (currentTrackUriStr.isBlank()) {
                                    Log.d("CastDebug", "onCastSessionAvailable: No track loaded yet -> setting ready state")
                                    _state.update {
                                        it.copy(
                                            castState = CastConnectionState.CONNECTED,
                                            castDeviceName = deviceName,
                                            castErrorMessage = "",
                                            isCasting = true
                                        )
                                    }
                                    return
                                }

                                browser?.pause()
                                Log.d("CastDebug", "browser.pause() executed. Local ExoPlayer isPlaying=${browser?.isPlaying}, playWhenReady=${browser?.playWhenReady}, playbackState=${browser?.playbackState}")

                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val originalUri = if (currentTrackUriStr.isNotBlank()) Uri.parse(currentTrackUriStr) else null
                                        Log.d("CastDebug", "Original Track URI = $originalUri, Scheme = ${originalUri?.scheme}")

                                        val trackMimeType = currentTrack?.mimeType?.takeIf { it.isNotBlank() }
                                            ?: (if (originalUri != null) context.contentResolver.getType(originalUri) else null)
                                            ?: "audio/mpeg"

                                        val httpStreamUrl = if (originalUri != null) {
                                            if (originalUri.scheme == "file") {
                                                val file = File(originalUri.path ?: "")
                                                val preparedUri = prepareMediaForCast(context.applicationContext, file)
                                                val targetFile = if (preparedUri.scheme == "file") File(preparedUri.path ?: "") else file
                                                val fileMime = if (targetFile.extension == "mp3") "audio/mpeg" else trackMimeType
                                                localAudioServer?.getStreamUrl(targetFile, fileMime)
                                            } else {
                                                localAudioServer?.getStreamUrl(originalUri, trackMimeType)
                                            }
                                        } else null

                                        Log.d("CastDebug", "Local HTTP Stream URL = $httpStreamUrl, MIME = $trackMimeType")
                                        val finalCastUri = if (httpStreamUrl != null) Uri.parse(httpStreamUrl) else originalUri
                                        Log.d("CastDebug", "Final Cast URI = $finalCastUri, Scheme = ${finalCastUri?.scheme}")

                                        val rawArtUri = currentTrack?.albumArtUri
                                        val trackKey = "${currentTrack?.id ?: System.currentTimeMillis()}_${rawArtUri.hashCode()}"
                                        val httpArtworkUrl = localAudioServer?.getArtworkUrl(rawArtUri, trackKey)
                                        Log.d("CastDebug", "Artwork URL for cast = $httpArtworkUrl")

                                        withContext(Dispatchers.Main) {
                                            if (finalCastUri != null) {
                                                val metadata = MediaMetadata.Builder()
                                                    .setTitle(currentTrack?.title ?: "Unknown Title")
                                                    .setArtist(currentTrack?.artist ?: "Unknown Artist")
                                                    .setAlbumTitle(currentTrack?.album ?: "Unknown Album")
                                                    .setArtworkUri(httpArtworkUrl?.let { Uri.parse(it) })
                                                    .build()

                                                val mediaItem = MediaItem.Builder()
                                                    .setUri(finalCastUri)
                                                    .setMimeType(trackMimeType)
                                                    .setMediaMetadata(metadata)
                                                    .build()

                                                Log.d("CastDebug", "About to set MediaItem, uri=${mediaItem.localConfiguration?.uri}, scheme=${mediaItem.localConfiguration?.uri?.scheme}, mimeType=$trackMimeType")
                                                cp.setMediaItem(mediaItem)
                                            } else {
                                                browser?.currentMediaItem?.let { item ->
                                                    Log.d("CastDebug", "Setting fallback browser currentMediaItem: ${item.localConfiguration?.uri}")
                                                    cp.setMediaItem(item)
                                                }
                                            }

                                            Log.d("CastDebug", "Duration at load start = ${cp.duration}")
                                            cp.seekTo(position)
                                            cp.prepare()
                                            cp.play()
                                            Log.d("CastDebug", "Cast media load calls completed without throwing. Duration after prepare = ${cp.duration}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CastDebug", "EXCEPTION inside onCastSessionAvailable coroutine", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("CastDebug", "EXCEPTION inside onCastSessionAvailable outer", e)
                            }
                        }

                        override fun onCastSessionUnavailable() {
                            Log.d("CastDebug", "Stop casting tapped / onCastSessionUnavailable fired")
                            Log.d("CastDebug", "Before disconnect - isCasting=${_state.value.isCasting}, currentTrackUriStr=${_state.value.currentTrack?.uri}")
                            try {
                                val session = castContext.sessionManager.currentCastSession
                                Log.d("CastDebug", "Current cast session on unavailable: isConnected=${session?.isConnected}, isConnecting=${session?.isConnecting}, state=${castContext.castState}")
                            } catch (e: Exception) {
                                Log.e("CastDebug", "Error logging session state on unavailable: ${e.message}")
                            }

                            try {
                                val position = cp.currentPosition
                                Log.d("CastDebug", "Cast player position on unavailable = $position")
                                cp.stop()
                                browser?.let { b ->
                                    b.seekTo(position)
                                    b.prepare()
                                    b.play()
                                }
                            } catch (e: Exception) {
                                Log.e("CastDebug", "EXCEPTION inside onCastSessionUnavailable", e)
                            }

                            _state.update {
                                it.copy(
                                    isCasting = false,
                                    castState = CastConnectionState.IDLE,
                                    castDeviceName = "",
                                    castErrorMessage = ""
                                )
                            }

                            Log.d("CastDebug", "After disconnect - isCasting=${_state.value.isCasting}, currentTrackUriStr=${_state.value.currentTrack?.uri}, isPlaying=${browser?.isPlaying}")
                        }
                    })
                }
                .addOnFailureListener { exception ->
                    Log.e("CastDebug", "CastContext init failed: ${exception.message}", exception)
                }
        } catch (e: Exception) {
            Log.e("CastDebug", "CastContext init skipped: ${e.message}")
        }
    }

    // ── Playback controls ─────────────────────────────────────

    override fun playTracks(
        tracks: List<Track>,
        startIndex: Int,
        sourceType: String,
        sourceName: String,
        sourcePath: String
    ) {
        val activeTrack = tracks.getOrNull(startIndex)
        Log.d("CastDebug", "currentTrackUriStr SET to: '${activeTrack?.uri}' from ${Thread.currentThread().name}")
        Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")
        _state.update {
            it.copy(
                currentPlaylist = tracks,
                currentTrackIndex = startIndex,
                currentTrack = activeTrack,
                playingFromType = sourceType,
                playingFromName = sourceName,
                playingFromPath = sourcePath
            )
        }
        updateAudioSpecForTrack(activeTrack)

        if (_state.value.activeOutputDevice == "m-box TV" && activeTrack != null) {
            Log.i("MBoxCast", "local player paused (not forwarded)")
            browser?.pause()

            val wanted = tracks.map { it.id }
            val sameQueue = tvQueueIds != null && tracks.size <= 500 && tvQueueIds == wanted
            val currentTvQueueIndex = castState.value.queueIndex

            songPickRetryJob?.cancel()
            pendingPickedTrackId = activeTrack.id.toString()

            if (sameQueue && startIndex == currentTvQueueIndex) {
                Log.i("MBoxCast", "song pick: already playing on the TV (${activeTrack.title})")
                if (!castState.value.isPlaying) {
                    sendTvControl("RESUME", "user play pick")
                }
            } else if (sameQueue) {
                Log.i("MBoxCast", "song pick: same queue on the TV -> SKIP_TO $startIndex (${activeTrack.title})")
                sendSkipToTv(startIndex)
            } else {
                Log.i("MBoxCast", "song pick: new queue (${tracks.size} songs) -> LOAD_QUEUE at $startIndex (${activeTrack.title})")
                sendLoadQueueToTv(tracks, startIndex, 0L, "user song pick", paused = false)
            }

            songPickRetryJob = viewModelScope.launch(Dispatchers.IO) {
                delay(3000L)
                if (pendingPickedTrackId == activeTrack.id.toString() &&
                    castState.value.trackId != activeTrack.id.toString()
                ) {
                    Log.i("MBoxCast", "song pick: TV didn't switch -> resending LOAD_QUEUE")
                    sendLoadQueueToTv(tracks, startIndex, 0L, "song pick retry", paused = false)
                }
            }

            _state.update {
                it.copy(
                    currentPlaylist = tracks,
                    currentTrackIndex = startIndex,
                    currentTrack = activeTrack,
                    playingFromType = sourceType,
                    playingFromName = sourceName,
                    playingFromPath = sourcePath
                )
            }
            updateAudioSpecForTrack(activeTrack)
            return
        }

        if (_state.value.isCasting && castPlayer != null && activeTrack != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val originalUri = Uri.parse(activeTrack.uri)
                val trackMimeType = activeTrack.mimeType.takeIf { it.isNotBlank() }
                    ?: (context.contentResolver.getType(originalUri))
                    ?: "audio/mpeg"

                val httpStreamUrl = if (originalUri.scheme == "file") {
                    val file = File(originalUri.path ?: "")
                    val preparedUri = prepareMediaForCast(context.applicationContext, file)
                    val targetFile = if (preparedUri.scheme == "file") File(preparedUri.path ?: "") else file
                    val fileMime = if (targetFile.extension == "mp3") "audio/mpeg" else trackMimeType
                    localAudioServer?.getStreamUrl(targetFile, fileMime)
                } else {
                    localAudioServer?.getStreamUrl(originalUri, trackMimeType)
                }

                val finalCastUri = if (httpStreamUrl != null) Uri.parse(httpStreamUrl) else originalUri

                val rawArtUri = activeTrack.albumArtUri
                val trackKey = "${activeTrack.id}_${rawArtUri.hashCode()}"
                val httpArtworkUrl = localAudioServer?.getArtworkUrl(rawArtUri, trackKey)
                Log.d("CastDebug", "playTracks Artwork URI for cast = '$httpArtworkUrl', raw = '$rawArtUri'")

                withContext(Dispatchers.Main) {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(activeTrack.title)
                        .setArtist(activeTrack.artist)
                        .setAlbumTitle(activeTrack.album)
                        .setArtworkUri(httpArtworkUrl?.let { Uri.parse(it) })
                        .build()

                    val castMediaItem = MediaItem.Builder()
                        .setUri(finalCastUri)
                        .setMimeType(trackMimeType)
                        .setMediaMetadata(metadata)
                        .build()

                    Log.d("CastDebug", "Mid-session track change to CastPlayer: uri=${castMediaItem.localConfiguration?.uri}, title=${activeTrack.title}")
                    castPlayer?.setMediaItem(castMediaItem)
                    castPlayer?.prepare()
                    castPlayer?.play()
                }
            }
        }
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

        if (!_state.value.isCasting) {
            viewModelScope.launch(Dispatchers.IO) {
                val playableUriStr = if (activeTrack != null) {
                    Eac3Transcoder.getPlayableUri(context, activeTrack) { msg ->
                        viewModelScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else null

                withContext(Dispatchers.Main) {
                    browser?.run {
                        Log.d("ExoPlayerDebug", "▶️ Playing ${items.size} tracks starting at $startIndex locally")
                        if (!playableUriStr.isNullOrBlank() && activeTrack != null) {
                            val updatedItems = items.toMutableList()
                            updatedItems[startIndex] = MediaItem.Builder()
                                .setUri(Uri.parse(playableUriStr))
                                .setMediaId(activeTrack.id.toString())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(activeTrack.title)
                                        .setArtist(activeTrack.artist)
                                        .setAlbumTitle(activeTrack.album)
                                        .setArtworkUri(activeTrack.albumArtUri?.let { Uri.parse(it) })
                                        .build()
                                ).build()
                            setMediaItems(updatedItems, startIndex, C.TIME_UNSET)
                        } else {
                            setMediaItems(items, startIndex, C.TIME_UNSET)
                        }
                        prepare()
                        play()
                    }
                }
            }
        } else {
            browser?.pause()
            Log.d("CastDebug", "Local browser kept PAUSED during cast playTracks. Local isPlaying=${browser?.isPlaying}")
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

    override fun togglePlayPause() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            if (_state.value.isPlaying) {
                sendTvControl("PAUSE")
            } else {
                if (isTvPlaybackConfirmed) {
                    sendTvControl("RESUME")
                } else {
                    val currentTrack = _state.value.currentTrack
                    if (currentTrack != null) {
                        sendPlayTrackToTv(currentTrack, _state.value.positionMs)
                    }
                }
            }
            return
        }

        if (_state.value.isCasting && castPlayer != null) {
            castPlayer?.run { if (isPlaying) pause() else play() }
            browser?.pause()
        } else {
            browser?.run { if (isPlaying) pause() else play() }
        }
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

    override fun setVisualizerMode(mode: String) {
        savedStateHandle[VISUALIZER_MODE_KEY] = mode
        _state.update { it.copy(activeVisualizerMode = mode) }
        viewModelScope.launch {
            prefs.setVisualizerMode(mode)
        }
        if (_state.value.activeOutputDevice == "m-box TV") {
            val msg = JSONObject().apply {
                put("action", "SET_VISUALIZER")
                put("name", mode)
                put("reason", "user selection")
            }
            mBoxCastServer?.broadcastMessage(msg.toString())
        }
    }

    fun setSeekBarStyle(style: String) {
        _state.update { it.copy(seekBarStyle = style) }
        viewModelScope.launch {
            prefs.setSeekBarStyle(style)
        }
    }

    fun setOpenNowPlayingOnStart(on: Boolean) {
        _state.update { it.copy(openNowPlayingOnSongStart = on) }
        viewModelScope.launch {
            prefs.setOpenNowPlayingOnStart(on)
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

    fun skipNext() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendNextToTv()
        } else {
            browser?.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendPrevToTv()
        } else {
            browser?.run {
                if (currentPosition > 3000L) seekTo(0L) else seekToPreviousMediaItem()
            }
        }
    }

    override fun skipToQueueItem(index: Int) {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendSkipToTv(index)
        } else {
            browser?.seekToDefaultPosition(index)
        }
    }

    fun syncLibrary(rootPath: String) {
        viewModelScope.launch {
            libraryRepository.syncLibraryFromDirectory(File(rootPath))
        }
    }

    private var lastTvSeekAt = 0L

    override fun seekTo(positionMs: Long) {
        seekTo(positionMs, "ui")
    }

    fun seekTo(ms: Long, source: String = "ui") {
        val target = ms.coerceAtLeast(0L)
        if (com.mediaplayer.BuildConfig.DEBUG) {
            Log.i("MBoxSeek", "SEEK: target=${target}ms, source=$source")
        }
        _state.update { it.copy(positionMs = target) }
        browser?.seekTo(target)

        if (_state.value.activeOutputDevice == "m-box TV") {
            sendTvControl("SEEK", "user seek", target)
        }
    }

    override fun seekLive(targetMs: Long, source: String) {
        val target = targetMs.coerceAtLeast(0L)
        if (com.mediaplayer.BuildConfig.DEBUG) {
            Log.i("MBoxSeek", "SEEK LIVE: target=${target}ms, source=$source")
        }
        _state.update { it.copy(positionMs = target) }
        browser?.seekTo(target)

        if (_state.value.activeOutputDevice == "m-box TV") {
            val now = System.currentTimeMillis()
            if (now - lastTvSeekAt >= 300) {
                lastTvSeekAt = now
                sendTvControl("SEEK", "user live scratch", target)
            }
        }
    }

    override fun seekFinal(targetMs: Long, resume: Boolean, source: String) {
        val target = targetMs.coerceAtLeast(0L)
        if (com.mediaplayer.BuildConfig.DEBUG) {
            Log.i("MBoxSeek", "SEEK FINAL: target=${target}ms, resume=$resume, source=$source")
        }
        _state.update { it.copy(positionMs = target) }
        browser?.seekTo(target)

        if (resume && !(browser?.isPlaying ?: false)) {
            browser?.play()
            _state.update { it.copy(isPlaying = true) }
        }

        if (_state.value.activeOutputDevice == "m-box TV") {
            sendTvControl("SEEK", "user final scratch", target)
        }
    }

    override fun pauseForDjHold() {
        if (_state.value.isPlaying) {
            browser?.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    override fun resumeAfterDjHold() {
        if (!_state.value.isPlaying) {
            browser?.play()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    override fun play() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendTvControl("RESUME", "user play")
        } else if (_state.value.isCasting && castPlayer != null) {
            castPlayer?.play()
        } else {
            browser?.play()
        }
    }

    override fun pause() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendTvControl("PAUSE", "user pause")
        } else if (_state.value.isCasting && castPlayer != null) {
            castPlayer?.pause()
        } else {
            browser?.pause()
        }
    }

    override fun next() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendNextToTv()
        } else {
            browser?.seekToNextMediaItem()
        }
    }

    override fun previous() {
        if (_state.value.activeOutputDevice == "m-box TV") {
            sendPrevToTv()
        } else {
            browser?.run {
                if (currentPosition > 3000L) seekTo(0L) else seekToPreviousMediaItem()
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val duration = if (_state.value.durationMs > 0L) _state.value.durationMs else Long.MAX_VALUE
        val targetPos = (_state.value.positionMs + deltaMs).coerceIn(0L, duration)
        seekTo(targetPos)
    }

    fun toggleShuffle() {
        val next = !(_state.value.shuffleOn)
        browser?.shuffleModeEnabled = next
        viewModelScope.launch { prefs.setShuffleOn(next) }
        _state.update { it.copy(shuffleOn = next) }

        if (_state.value.activeOutputDevice == "m-box TV") {
            val currentTracks = _state.value.currentPlaylist.toMutableList()
            if (next) {
                currentTracks.shuffle()
            }
            sendLoadQueueToTv(currentTracks, 0, _state.value.positionMs, "toggleShuffle")
        }
    }

    fun cycleRepeatMode() {
        val next = ((_state.value.repeatMode) + 1) % 3
        browser?.repeatMode = next
        viewModelScope.launch { prefs.setRepeatMode(next) }
        _state.update { it.copy(repeatMode = next) }

        if (_state.value.activeOutputDevice == "m-box TV") {
            val repeatStr = when (next) {
                1 -> "one"
                2 -> "all"
                else -> "off"
            }
            sendSetRepeatToTv(repeatStr)
        }
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

    fun setEqPresetName(name: String) {
        viewModelScope.launch { prefs.applyPresetName(name) }
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

    fun updateOrbitalEq(mids: Int, treble: Int, bass: Int) {
        eqManager.updateOrbitalEq(mids, treble, bass)
        _state.update { it.copy(mids = mids, treble = treble, bass = bass, activeHeadset = null) }
    }

    fun setEchoPresetName(name: String) {
        _state.update { it.copy(selectedEcho = name) }
    }

    fun applyEchoPreset(preset: NewEqPreset) {
        eqManager.applyBands(preset.bands10.map { (it * 100).toInt() })
        _state.update { 
            it.copy(
                mids = preset.mids, 
                treble = preset.treble, 
                bass = preset.bass, 
                selectedEcho = preset.name,
                activeHeadset = null,
                eqBands = preset.bands10.map { (it * 100).toInt() }
            ) 
        }
        _eqBands.value = preset.bands10
    }

    fun applyHeadsetProfile(profile: HeadsetProfile) {
        eqManager.applyHeadsetProfile(profile)
        _state.update { 
            it.copy(
                activeHeadset = profile, 
                selectedEcho = "Headset: ${profile.name}",
                eqBands = profile.bands10.map { (it * 100).toInt() }
            ) 
        }
        _eqBands.value = profile.bands10
    }

    fun setDolbyMode(mode: String) {
        _state.update { it.copy(selectedDolby = mode) }
        applyDspEffect(mode)
    }

    fun setDiracMode(mode: String) {
        _state.update { it.copy(selectedDirac = mode) }
        applyDspEffect(mode)
    }

    private fun applyDspEffect(mode: String) {
        val track = _state.value.currentTrack ?: return
        val browser = browser ?: return

        viewModelScope.launch {
            _state.update { it.copy(isRendering = true) }
            
            DspRenderer.renderAndPlayDspMode(
                context = context,
                browser = browser,
                track = track,
                mode = mode
            )
            
            _state.update { it.copy(isRendering = false) }
        }
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
            prefs.visualizerMode,
            prefs.seekBarStyle,
            prefs.openNowPlayingOnSongStart
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
            val seekBarStyle = args[9] as String
            val openOnStart = args[10] as Boolean

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
                    seekBarStyle = seekBarStyle,
                    openNowPlayingOnSongStart = openOnStart,
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

    private fun startDeviceStateCollection() = viewModelScope.launch {
        deviceStateMonitor.stateFlow.collect { state ->
            _state.update { it.copy(deviceState = state) }
        }
    }

    private var nextTrackPreloadedId = -1L

    private fun startPredictiveCaching() = viewModelScope.launch {
        _state.map { it.positionMs to it.durationMs }.distinctUntilChanged().collect { (pos, dur) ->
            if (dur > 0 && pos > dur * 0.85f) {
                val currentIndex = _state.value.currentTrackIndex
                val playlist = _state.value.currentPlaylist
                if (currentIndex >= 0 && currentIndex < playlist.size - 1) {
                    val nextTrack = playlist[currentIndex + 1]
                    if (nextTrackPreloadedId != nextTrack.id) {
                        preloadNextTrack(nextTrack)
                        nextTrackPreloadedId = nextTrack.id
                    }
                }
            } else if (dur > 0 && pos < dur * 0.1f) {
                // Reset flag when track starts/restarts
                nextTrackPreloadedId = -1L
            }
        }
    }

    private fun preloadNextTrack(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("SmartEngine", "🚀 Preloading next track: ${track.title}")
            // Preload album art into Glide's memory cache
            if (track.albumArtUri != null) {
                Glide.with(context)
                    .load(Uri.parse(track.albumArtUri))
                    .preload()
            }
            // Additional caching like pre-fetching lyrics or metadata could go here
        }
    }

    private fun startPositionUpdates() = viewModelScope.launch {
        var lastLogTime = 0L
        while (this.isActive) {
            try {
                val isCast = _state.value.isCasting && castPlayer != null
                if (isCast) {
                    val cp = castPlayer!!
                    val castPosition = cp.currentPosition.coerceAtLeast(0L)
                    val castDuration = cp.duration.coerceAtLeast(0L)
                    val isCastPlaying = cp.isPlaying

                    _state.update {
                        it.copy(
                            positionMs = castPosition,
                            durationMs = castDuration,
                            isPlaying = isCastPlaying
                        )
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 2000L) {
                        lastLogTime = now
                        Log.d("CastDebug", "UI position=$castPosition, CastPlayer.currentPosition=$castPosition, drift=0")
                    }
                } else {
                    browser?.let { b ->
                        val format = b.currentTracks.groups
                            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                            .firstNotNullOfOrNull { it.getTrackFormat(0) }

                        val track = _state.value.currentTrack

                        val signalPath = if (track != null) {
                            val dolbyInfo = Eac3Transcoder.getDolbyInfo(context, track)
                            val hasNativeDolby = Eac3Transcoder.hasNativeDolbyDecoder(dolbyInfo.isJoc)

                            val codecName = when {
                                dolbyInfo.isJoc -> "E-AC3 JOC"
                                dolbyInfo.isDolby -> "E-AC3"
                                track.mimeType.contains("alac", true) -> "ALAC"
                                track.mimeType.contains("flac", true) -> "FLAC"
                                track.mimeType.contains("mpeg", true) -> "MP3"
                                else -> track.mimeType.substringAfterLast("/").uppercase()
                            }

                            val actualBitrate = if (dolbyInfo.isDolby) dolbyInfo.bitrateKbps else if (track.bitrate > 0 && track.bitrate < 5000000) track.bitrate / 1000 else if (format != null && format.bitrate > 0) format.bitrate / 1000 else 236
                            val actualSampleRate = if (dolbyInfo.isDolby) dolbyInfo.sampleRate / 1000.0 else if (track.sampleRate > 0) track.sampleRate / 1000.0 else 48.0

                            val decoderName = if (dolbyInfo.isDolby) {
                                if (hasNativeDolby) "device Dolby (c2.dolby.eac3-joc.decoder)"
                                else "FFmpeg — -drc_scale 0 -> stereo (Atmos height data not decoded)"
                            } else {
                                MediaService.currentDecoderName.ifBlank {
                                    format?.sampleMimeType ?: "MediaCodec C2 Audio Decoder"
                                }
                            }

                            val channelText = if (dolbyInfo.isJoc) {
                                "Dolby Atmos: Yes (${dolbyInfo.atmosObjects ?: 16} objects)"
                            } else if (dolbyInfo.isDolby) {
                                "Dolby Atmos: No (${dolbyInfo.channels} ch)"
                            } else {
                                "Stereo 2.0"
                            }
                            val rateStr = "%.1f".format(actualSampleRate)

                            val activePresetName = _state.value.activePreset.name.ifBlank { "KZ Castor" }

                            AudioSignalPath(
                                format = codecName,
                                sourceInfo = "$rateStr kHz · $actualBitrate kbps",
                                decoder = decoderName,
                                hardwareInfo = "${audioDiagnostics.getHardwareSampleRate() / 1000.0} kHz | ${audioDiagnostics.getHardwareBufferSize()} frames",
                                outputApi = audioDiagnostics.getOutputApi(),
                                latency = 45,
                                bitDepth = audioDiagnostics.getOutputBitDepth(),
                                channels = channelText,
                                dspBitDepth = "32-bit Float",
                                eqMode = activePresetName,
                                preamp = "0.0 dB",
                                deviceType = audioDiagnostics.getActiveOutputDeviceName()
                            )
                        } else null



                        // Only when something changed (was logged every 0.5 s, 2026-10-25 log: ~250 identical
                        // blocks in 2 minutes, each also re-checking the Dolby cache on disk).
                        val diagKey = "${track?.uri}|${format?.sampleMimeType}|${format?.sampleRate}|${format?.channelCount}|${b.volume}|${_state.value.eqEnabled}"
                        if (com.mediaplayer.BuildConfig.DEBUG && track != null && diagKey != lastAudioDiagKey) {
                            lastAudioDiagKey = diagKey
                            Log.i(
                                "MBoxAudio",
                                """
                                =================== MBoxAudio DIAGNOSTIC LOG ===================
                                File Path: ${track.uri}
                                Selected Format: mime=${format?.sampleMimeType}, channels=${format?.channelCount}, rate=${format?.sampleRate}, bitrate=${format?.bitrate}
                                Player Volume: ${b.volume}
                                Output Device: ${audioDiagnostics.getActiveOutputDeviceName()}
                                Settings State: EQ Enabled=${_state.value.eqEnabled}, Float32=true
                                FFmpeg Cache Info: ${Eac3Transcoder.getCacheDiagnostics(context, track)}
                                ================================================================
                                """.trimIndent()
                            )
                        }

                        if (_state.value.activeOutputDevice == "m-box TV" || _state.value.isCasting) {
                            _state.update {
                                it.copy(signalPath = signalPath)
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    positionMs = b.currentPosition.coerceAtLeast(0L),
                                    durationMs = b.duration.coerceAtLeast(0L),
                                    signalPath = signalPath,
                                    isPlaying = b.isPlaying
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Failsafe in case player is briefly inaccessible
            }
            delay(500L)
        }
    }

    private fun updateAudioSpecForTrack(track: Track?) {
        _state.update { it.copy(currentAudioSpec = "") }
        if (track == null) return

        val targetTrackId = track.id
        viewModelScope.launch(Dispatchers.IO) {
            val spec = probeOriginalAudioSpec(context.applicationContext, track)
            if (_state.value.currentTrack?.id == targetTrackId) {
                val badgeString = spec.toBadgeString()
                _state.update { it.copy(currentAudioSpec = badgeString) }
            }
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

        // If this is an external file, don't let the DB lookup reset the state.
        if (mediaIdString == "external_file") {
            Log.d("CastDebug", "currentTrackUriStr SET to: '${_state.value.currentTrack?.uri}' from ${Thread.currentThread().name}")
            Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")
            return
        }

        if (mediaId == null) {
            val uriFromMediaItem = mediaItem?.localConfiguration?.uri?.toString()
            val matchedTrack = _state.value.currentPlaylist.find { it.uri == uriFromMediaItem || it.id.toString() == mediaIdString }
            if (matchedTrack != null) {
                Log.d("CastDebug", "currentTrackUriStr SET to: '${matchedTrack.uri}' from ${Thread.currentThread().name}")
                Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")
                _state.update { it.copy(currentTrack = matchedTrack) }
                fetchLyricsForTrack(matchedTrack.title, matchedTrack.artist, matchedTrack.album, matchedTrack.duration, matchedTrack.uri)
                return
            }

            Log.d("CastDebug", "currentTrackUriStr SET to: 'NULL' from ${Thread.currentThread().name}")
            Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")
            _state.update { it.copy(currentTrack = null, currentLyrics = emptyList(), rawLyricsText = "") }
            _currentLyrics.value = emptyList()
            _rawLyricsText.value = ""
            return
        }

        val track = trackDao.getById(mediaId) ?: _state.value.currentPlaylist.find { it.id == mediaId }
        Log.d("CastDebug", "currentTrackUriStr SET to: '${track?.uri}' from ${Thread.currentThread().name}")
        Log.d("CastDebug", "currentTrackUriStr instance = ${System.identityHashCode(this)}")
        _state.update { it.copy(currentTrack = track) }
        
        if (track != null) {
            if (!applyingRemoteState && _state.value.activeOutputDevice != "m-box TV" && _state.value.isCasting && castPlayer != null) {
                Log.d("CastDebug", "Track changed while cast active: isCastActive=${_state.value.isCasting}, newUri=${track.uri}")
                loadTrackOnCastPlayer(track)
            }
            fetchLyricsForTrack(track.title, track.artist, track.album, track.duration, track.uri)
        } else {
            _state.update { it.copy(currentLyrics = emptyList(), rawLyricsText = "") }
            _currentLyrics.value = emptyList()
            _rawLyricsText.value = ""
        }
    }

    private var currentCastLoadJob: Job? = null

    private fun loadTrackOnCastPlayer(track: Track) {
        if (!_state.value.isCasting || castPlayer == null) return

        currentCastLoadJob?.cancel()

        val trackId = track.id
        val startTime = System.currentTimeMillis()
        Log.d("CastDebug", "LOAD START trackId=$trackId title='${track.title}' at $startTime")

        _state.update { it.copy(positionMs = 0L) }

        currentCastLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalUri = Uri.parse(track.uri)
                val trackMimeType = track.mimeType.takeIf { it.isNotBlank() }
                    ?: (context.contentResolver.getType(originalUri))
                    ?: "audio/mpeg"

                val httpStreamUrl = if (originalUri.scheme == "file") {
                    val file = File(originalUri.path ?: "")
                    val preparedUri = prepareMediaForCast(context.applicationContext, file)
                    val targetFile = if (preparedUri.scheme == "file") File(preparedUri.path ?: "") else file
                    val fileMime = if (targetFile.extension == "mp3") "audio/mpeg" else trackMimeType
                    localAudioServer?.getStreamUrl(targetFile, fileMime)
                } else {
                    localAudioServer?.getStreamUrl(originalUri, trackMimeType)
                }

                val finalCastUri = if (httpStreamUrl != null) Uri.parse(httpStreamUrl) else originalUri

                val rawArtUri = track.albumArtUri
                val trackKey = "${track.id}_${rawArtUri.hashCode()}"
                val httpArtworkUrl = localAudioServer?.getArtworkUrl(rawArtUri, trackKey)
                Log.d("CastDebug", "Artwork URL for cast = $httpArtworkUrl")

                ensureActive()

                withContext(Dispatchers.Main) {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(httpArtworkUrl?.let { Uri.parse(it) })
                        .build()

                    val castMediaItem = MediaItem.Builder()
                        .setUri(finalCastUri)
                        .setMimeType(trackMimeType)
                        .setMediaMetadata(metadata)
                        .build()

                    Log.d("CastDebug", "Sending atomic MediaItem to CastPlayer: uri=${castMediaItem.localConfiguration?.uri}, mimeType=$trackMimeType, title=${track.title}, artworkUri=${metadata.artworkUri}")
                    castPlayer?.setMediaItem(castMediaItem)
                    castPlayer?.prepare()
                    castPlayer?.play()

                    val completeTime = System.currentTimeMillis()
                    Log.d("CastDebug", "LOAD COMPLETE trackId=$trackId title='${track.title}' at $completeTime (duration=${completeTime - startTime}ms)")
                }
            } catch (e: CancellationException) {
                Log.d("CastDebug", "LOAD CANCELLED trackId=$trackId title='${track.title}' (superseded by newer skip)")
            } catch (e: Exception) {
                Log.e("CastDebug", "EXCEPTION in loadTrackOnCastPlayer for trackId=$trackId", e)
            }
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

    override fun updateLyricsManually(trackId: Long, newLrcText: String) {
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

fun PlayerViewModel.PlayerUiState.toNowPlayingState(): NowPlayingState = NowPlayingState(
    currentTrack = currentTrack,
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    castState = castState,
    castDeviceName = castDeviceName,
    castErrorMessage = castErrorMessage,
    repeatMode = repeatMode,
    shuffleOn = shuffleOn,
    eqEnabled = eqEnabled,
    isVisualizerEnabled = isVisualizerEnabled,
    activeVisualizerMode = activeVisualizerMode,
    seekBarStyle = seekBarStyle,
    vinylRotation = vinylRotation,
    currentPlaylist = currentPlaylist,
    currentTrackIndex = currentTrackIndex,
    currentLyrics = currentLyrics,
    rawLyricsText = rawLyricsText,
    playingFromType = playingFromType,
    playingFromName = playingFromName,
    activeOutputDevice = activeOutputDevice,
    tvDeviceName = tvDeviceName,
    isMBoxTvConnected = isMBoxTvConnected,
    isTvReconnecting = isTvReconnecting,
    currentAudioSpec = currentAudioSpec,
    signalPath = signalPath
)
