package com.mediaplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.annotation.OptIn
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.util.EventLogger
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.MainActivity
import com.mediaplayer.R
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.Track
import com.mediaplayer.util.DolbyCapabilityDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// MediaService
//
// Upgraded from MediaSessionService → MediaLibraryService so Android Auto /
// Android Automotive can browse the library via the media-library API.
//
// Browseable tree layout:
//   ROOT
//   ├── node_songs       → flat list of all songs
//   ├── node_albums      → album nodes (each browseable)
//   ├── node_artists     → artist nodes (each browseable)
//   ├── node_genres      → genre nodes (each browseable)
//   └── node_folders     → folder nodes (each browseable)
//
// AndroidManifest.xml changes required (see bottom of file for the diff).
// ─────────────────────────────────────────────────────────────────────────────
@AndroidEntryPoint
class MediaService : MediaLibraryService() {

    @Inject lateinit var eqManager: EqualizerManager
    @Inject lateinit var dspEngine: NativeDspEngine
    @Inject lateinit var trackDao:  TrackDao
    @Inject lateinit var vinylEffectManager: VinylEffectManager
    @Inject lateinit var visualizerManager: com.mediaplayer.util.VisualizerManager

    private lateinit var player:  ExoPlayer
    private lateinit var session: MediaLibrarySession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val biquadEqProcessor = BiquadEqAudioProcessor()

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        // Step 0: Query and Log Dolby Capabilities
        DolbyCapabilityDetector.queryCapabilities(this)

        // 1. Setup the Hardware-Aware AudioSink
        val audioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(biquadEqProcessor, visualizerManager.pcmAnalysisProcessor))
            .build()

        // 2. Force MediaCodec over FFmpeg for Bitstream Passthrough
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                // Put the hardware bitstream renderer at Index 0
                out.add(
                    MediaCodecAudioRenderer(
                        context,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink
                    )
                )
                // Allow software fallback (FFmpeg) only after hardware fails
                super.buildAudioRenderers(
                    context,
                    EXTENSION_RENDERER_MODE_OFF,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }

        // 3. The Atmos Track Selector: Unlock the channel ceiling & Enable Offload
        val audioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .setIsGaplessSupportRequired(false) // GAPLESS usually breaks Atmos bitstream
            .build()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxAudioChannelCount(Int.MAX_VALUE)
                    .setAudioOffloadPreferences(audioOffloadPreferences)
                    .build()
            )
        }

        val stableAudioSessionId = androidx.media3.common.util.Util.generateAudioSessionIdV21(this)

        // 4. Build the Player
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.audioSessionId = stableAudioSessionId
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)

        // Attach Equalizer once for stable service lifetime audio session
        eqManager.attach(stableAudioSessionId)

        player.addAnalyticsListener(EventLogger())

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                Log.i("AudioPipeline", "Live Audio Decoder Initialized: $decoderName")
                currentDecoderName = decoderName
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception
            ) {
                Log.e("AudioPipeline", "AudioSink Error: ${audioSinkError.message}", audioSinkError)
                currentAudioError = "AudioSink Error: ${audioSinkError.message}"
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                Log.e("AudioPipeline", "Player Error: ${error.errorCodeName} - ${error.message}", error)
                currentAudioError = "Player Error: ${error.message}"
            }
        })

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    eqManager.attach(audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentDecoderName = ""
                currentAudioError = null
                val title = mediaItem?.mediaMetadata?.title ?: "Unknown Title"
                val artist = mediaItem?.mediaMetadata?.artist ?: "Unknown Artist"
                Log.d("AudioPipeline", "Track transition: title=$title, artist=$artist")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid != C.AUDIO_SESSION_ID_UNSET) {
                        dspEngine.init(sid)
                        visualizerManager.start(sid)
                    }
                }
                
                // Trigger vinyl scratch effect when buffering during a seek
                // Ignore the very first BUFFERING state during initial load
                if (state == Player.STATE_BUFFERING && player.mediaItemCount > 0 && player.currentPosition > 0) {
                    vinylEffectManager.playScratch()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    vinylEffectManager.playScratch()
                }
            }
        })

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activityIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification_small)
        }
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!(player.playWhenReady) || (player.mediaItemCount == 0)) stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        eqManager.release()
        dspEngine.release()
        visualizerManager.stop()
        vinylEffectManager.release()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaLibrarySession callback — drives the Android Auto browseable tree
    // ─────────────────────────────────────────────────────────────────────────
    @OptIn(UnstableApi::class)
    inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availablePlayerCommands = connectionResult.availablePlayerCommands
                .buildUpon()
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .build()

            val cmdList = mutableListOf<String>()
            for (i in 0 until availablePlayerCommands.size()) {
                val cmd = availablePlayerCommands.get(i)
                val name = when (cmd) {
                    Player.COMMAND_PLAY_PAUSE -> "PLAY_PAUSE($cmd)"
                    Player.COMMAND_PREPARE -> "PREPARE($cmd)"
                    Player.COMMAND_STOP -> "STOP($cmd)"
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> "SEEK_TO_DEFAULT_POSITION($cmd)"
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> "SEEK_IN_CURRENT_MEDIA_ITEM($cmd)"
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM -> "SEEK_TO_MEDIA_ITEM($cmd)"
                    Player.COMMAND_SEEK_TO_PREVIOUS -> "SEEK_TO_PREVIOUS($cmd)"
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> "SEEK_TO_PREVIOUS_MEDIA_ITEM($cmd)"
                    Player.COMMAND_SEEK_TO_NEXT -> "SEEK_TO_NEXT($cmd)"
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> "SEEK_TO_NEXT_MEDIA_ITEM($cmd)"
                    else -> "CMD_$cmd"
                }
                cmdList.add(name)
            }
            Log.i("MBoxCast", "Available commands: ${cmdList.joinToString(", ")}")

            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands,
                availablePlayerCommands
            )
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            val callerPkg = controller.packageName
            when (playerCommand) {
                Player.COMMAND_PLAY_PAUSE -> Log.d("CastDebug", "onPlay/onPause called, calling package = $callerPkg")
                Player.COMMAND_SEEK_TO_NEXT -> Log.d("CastDebug", "onSkipToNext called, calling package = $callerPkg")
                Player.COMMAND_SEEK_TO_PREVIOUS -> Log.d("CastDebug", "onSkipToPrevious called, calling package = $callerPkg")
                else -> Log.d("CastDebug", "PlayerCommand $playerCommand called, calling package = $callerPkg")
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        // Root node: Android Auto calls this first
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = buildBrowsableItem(
                mediaId     = NODE_ROOT,
                title       = getString(com.mediaplayer.R.string.app_name),
                browsable   = true,
                playable    = false
            )
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        // Called when Auto wants a specific item by ID
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            serviceScope.future {
                val item = when {
                    mediaId == NODE_ROOT    -> buildBrowsableItem(NODE_ROOT, getString(com.mediaplayer.R.string.app_name))
                    mediaId == NODE_SONGS   -> buildBrowsableItem(NODE_SONGS, "Songs")
                    mediaId == NODE_ALBUMS  -> buildBrowsableItem(NODE_ALBUMS, "Albums")
                    mediaId == NODE_ARTISTS -> buildBrowsableItem(NODE_ARTISTS, "Artists")
                    mediaId == NODE_GENRES  -> buildBrowsableItem(NODE_GENRES, "Genres")
                    mediaId == NODE_FOLDERS -> buildBrowsableItem(NODE_FOLDERS, "Folders")
                    else -> {
                        val id = mediaId.toLongOrNull()
                        val track = id?.let { trackDao.getById(it) }
                        track?.toMediaItem()
                    }
                }
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

        // Called when Auto needs the children of a node
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future {
                val items = when {
                    parentId == NODE_ROOT       -> rootChildren()
                    parentId == NODE_SONGS      -> allSongItems()
                    parentId == NODE_ALBUMS     -> albumItems()
                    parentId == NODE_ARTISTS    -> artistItems()
                    parentId == NODE_GENRES     -> genreItems()
                    parentId == NODE_FOLDERS    -> folderItems()
                    parentId.startsWith(PFX_ALBUM)  -> albumTrackItems(parentId.removePrefix(PFX_ALBUM))
                    parentId.startsWith(PFX_ARTIST) -> artistTrackItems(parentId.removePrefix(PFX_ARTIST))
                    parentId.startsWith(PFX_GENRE)  -> genreTrackItems(parentId.removePrefix(PFX_GENRE))
                    parentId.startsWith(PFX_FOLDER) -> folderTrackItems(parentId.removePrefix(PFX_FOLDER))
                    else -> emptyList()
                }
                LibraryResult.ofItemList(items, params)
            }

        // Called when Auto wants to play a specific item
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            serviceScope.future {
                // Resolve each item's URI from the database if not already set
                mediaItems.map { item ->
                    if (item.localConfiguration?.uri != null) {
                        item
                    } else {
                        val id = item.mediaId.toLongOrNull()
                        val track = id?.let { trackDao.getById(it) }
                        track?.toMediaItem() ?: item
                    }
                }.toMutableList()
            }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future {
                val tracks = trackDao.searchOnce(query)
                val items = tracks.map { it.toMediaItem() }
                LibraryResult.ofItemList(items, params)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tree builders
    // ─────────────────────────────────────────────────────────────────────────

    private fun rootChildren() = listOf(
        buildBrowsableItem(NODE_SONGS,   "Songs",   browsable = true),
        buildBrowsableItem(NODE_ALBUMS,  "Albums",  browsable = true),
        buildBrowsableItem(NODE_ARTISTS, "Artists", browsable = true),
        buildBrowsableItem(NODE_GENRES,  "Genres",  browsable = true),
        buildBrowsableItem(NODE_FOLDERS, "Folders", browsable = true)
    )

    private suspend fun allSongItems(): List<MediaItem> =
        trackDao.getAllTracksOnce().map { it.toMediaItem() }

    private suspend fun albumItems(): List<MediaItem> =
        trackDao.getAlbumsOnce().map { row ->
            buildBrowsableItem(
                mediaId   = "$PFX_ALBUM${row.album}",
                title     = row.album,
                subtitle  = row.artist,
                browsable = true
            )
        }

    private suspend fun artistItems(): List<MediaItem> =
        trackDao.getArtistsOnce().map { row ->
            buildBrowsableItem(
                mediaId   = "$PFX_ARTIST${row.artist}",
                title     = row.artist,
                subtitle  = "${row.trackCount} tracks",
                browsable = true
            )
        }

    private suspend fun genreItems(): List<MediaItem> =
        trackDao.getGenresOnce().map { row ->
            buildBrowsableItem(
                mediaId   = "$PFX_GENRE${row.genre}",
                title     = row.genre,
                subtitle  = "${row.trackCount} tracks",
                browsable = true
            )
        }

    private suspend fun folderItems(): List<MediaItem> =
        trackDao.getFoldersOnce().map { row ->
            buildBrowsableItem(
                mediaId   = "$PFX_FOLDER${row.folderPath}",
                title     = row.folderName,
                subtitle  = "${row.trackCount} tracks",
                browsable = true
            )
        }

    private suspend fun albumTrackItems(album: String)   = trackDao.getByAlbumOnce(album).map { it.toMediaItem() }
    private suspend fun artistTrackItems(artist: String) = trackDao.getByArtistOnce(artist).map { it.toMediaItem() }
    private suspend fun genreTrackItems(genre: String)   = trackDao.getByGenreOnce(genre).map { it.toMediaItem() }
    private suspend fun folderTrackItems(path: String)   = trackDao.getByFolderOnce(path).map { it.toMediaItem() }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildBrowsableItem(
        mediaId:  String,
        title:    String,
        subtitle: String = "",
        browsable:Boolean = true,
        playable: Boolean = false
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(browsable)
            .setIsPlayable(playable)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(meta)
            .build()
    }

    private fun Track.toMediaItem(): MediaItem {
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setRecordingYear(year)
            .setIsBrowsable(false)
            .setIsPlayable(true)

        // 🔥 THE FIX: Downscale artwork to prevent Android Auto Binder IPC crashes
        if (albumArtUri != null) {
            try {
                val bitmap = BitmapFactory.decodeFile(albumArtUri.removePrefix("file://"))
                if (bitmap != null) {
                    val scaled = getScaledArt(bitmap)
                    val bytes = scaled.toBytes()
                    metaBuilder.setArtworkData(
                        bytes,
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    private fun getScaledArt(bitmap: Bitmap): Bitmap {
        val maxSize = 512 // Safe size for Android Auto widgets
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val width = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
        val height = if (ratio > 1) (maxSize / ratio).toInt() else maxSize
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun Bitmap.toBytes(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    companion object {
        var currentDecoderName: String = "MediaCodec C2 Audio Decoder"
        var currentAudioError: String? = null

        private const val NODE_ROOT    = "root"
        private const val NODE_SONGS   = "node_songs"
        private const val NODE_ALBUMS  = "node_albums"
        private const val NODE_ARTISTS = "node_artists"
        private const val NODE_GENRES  = "node_genres"
        private const val NODE_FOLDERS = "node_folders"

        private const val PFX_ALBUM  = "album:"
        private const val PFX_ARTIST = "artist:"
        private const val PFX_GENRE  = "genre:"
        private const val PFX_FOLDER = "folder:"
    }
}
