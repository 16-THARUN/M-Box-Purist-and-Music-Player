package com.mediaplayer

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mediaplayer.service.EqualizerManager
import com.mediaplayer.util.VisualizerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.media3.common.Player

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    
    @Inject lateinit var visualizerManager: VisualizerManager
    @Inject lateinit var eqManager: EqualizerManager

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize the player with Audio Focus handling to prevent system sound glitches
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Automatically handle audio focus (ducking/pausing for system alerts)
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val sid = player.audioSessionId
                    if (sid != C.AUDIO_SESSION_ID_UNSET) {
                        eqManager.init(sid)
                        visualizerManager.start(sid)
                    }
                }
            }
        })

        // 2. Build the MediaSession to expose playback to the OS
        mediaSession = MediaSession.Builder(this, player).build()
    }

    // 3. Allow the UI and the Android OS to connect to this session
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 4. Clean up resources to prevent memory leaks
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
