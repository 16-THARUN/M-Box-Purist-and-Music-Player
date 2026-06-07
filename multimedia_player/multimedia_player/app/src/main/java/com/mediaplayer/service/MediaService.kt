package com.mediaplayer.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.annotation.OptIn
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.MainActivity
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.Track
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
    @Inject lateinit var trackDao:  TrackDao

    private lateinit var player:  ExoPlayer
    private lateinit var session: MediaLibrarySession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @UnstableApi
    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
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

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid != C.AUDIO_SESSION_ID_UNSET) eqManager.init(sid)
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!(player.playWhenReady) || (player.mediaItemCount == 0)) stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        eqManager.release()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaLibrarySession callback — drives the Android Auto browseable tree
    // ─────────────────────────────────────────────────────────────────────────
    @OptIn(UnstableApi::class)
    inner class LibraryCallback : MediaLibrarySession.Callback {

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
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setRecordingYear(year)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }

    companion object {
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
