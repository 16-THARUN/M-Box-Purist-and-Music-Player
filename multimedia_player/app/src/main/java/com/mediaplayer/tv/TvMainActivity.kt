package com.mediaplayer.tv

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.mediaplayer.service.MediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

sealed class TvScreen {
    object Library : TvScreen()
    object NowPlaying : TvScreen()
}

class TvMainActivity : ComponentActivity() {

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private var playerState = mutableStateOf<Player?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, MediaService::class.java)
        )
        browserFuture = MediaBrowser.Builder(applicationContext, sessionToken).buildAsync()
        browserFuture?.addListener({
            try {
                val browser = browserFuture?.get()
                playerState.value = browser
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        val libraryRows = listOf(
            TvMediaRow(
                title = "Popular Albums",
                items = listOf(
                    TvMediaItem("content://media/external/audio/media/1", "Hurry Up, We're Dreaming", "M83"),
                    TvMediaItem("content://media/external/audio/media/2", "Random Access Memories", "Daft Punk"),
                    TvMediaItem("content://media/external/audio/media/3", "After Hours", "The Weeknd")
                )
            ),
            TvMediaRow(
                title = "Top Artists",
                items = listOf(
                    TvMediaItem("content://media/external/audio/media/4", "M83", "Electronic"),
                    TvMediaItem("content://media/external/audio/media/5", "Daft Punk", "Dance/Electronic"),
                    TvMediaItem("content://media/external/audio/media/6", "Anirudh Ravichander", "Soundtrack")
                )
            ),
            TvMediaRow(
                title = "Featured Playlists",
                items = listOf(
                    TvMediaItem("content://media/external/audio/media/7", "Chill Electronic", "24 Songs"),
                    TvMediaItem("content://media/external/audio/media/8", "High Bitrate FLAC", "18 Songs")
                )
            )
        )

        setContent {
            var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Library) }
            val player = playerState.value

            BackHandler(enabled = currentScreen is TvScreen.NowPlaying) {
                currentScreen = TvScreen.Library
            }

            when (currentScreen) {
                is TvScreen.Library -> TvLibraryScreen(
                    rows = libraryRows,
                    onItemSelected = { item ->
                        player?.let { p ->
                            p.setMediaItem(MediaItem.fromUri(item.id))
                            p.prepare()
                            p.play()
                        }
                        currentScreen = TvScreen.NowPlaying
                    }
                )
                is TvScreen.NowPlaying -> TvNowPlayingScreen(
                    player = player,
                    trackTitle = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Now Playing",
                    artistName = player?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "M-Box Player",
                    bitrateInfo = "48.0 kHz • 237 kbps • High-Res Audio"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        browserFuture?.let { MediaBrowser.releaseFuture(it) }
    }
}
