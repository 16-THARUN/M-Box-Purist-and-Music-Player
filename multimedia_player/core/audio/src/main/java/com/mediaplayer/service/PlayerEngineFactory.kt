package com.mediaplayer.service

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.mediaplayer.util.DolbyCapabilityDetector
import com.mediaplayer.util.VisualizerManager

data class PlayerEngineResult(
    val player: ExoPlayer,
    val biquadEqProcessor: BiquadEqAudioProcessor,
    val stableAudioSessionId: Int
)

@OptIn(UnstableApi::class)
object PlayerEngineFactory {

    fun createExoPlayer(
        context: Context,
        eqManager: EqualizerManager,
        visualizerManager: VisualizerManager,
        onDecoderInitialized: (String) -> Unit = {},
        onAudioError: (String) -> Unit = {}
    ): PlayerEngineResult {
        // Step 0: Query and Log Dolby Capabilities
        DolbyCapabilityDetector.queryCapabilities(context)

        val biquadEqProcessor = BiquadEqAudioProcessor()

        // 1. Setup the Hardware-Aware AudioSink
        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(biquadEqProcessor, visualizerManager.pcmAnalysisProcessor))
            .build()

        // 2. Force MediaCodec over FFmpeg for Bitstream Passthrough
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                ctx: Context,
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
                        ctx,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink
                    )
                )
                // Allow software fallback (FFmpeg) only after hardware fails
                super.buildAudioRenderers(
                    ctx,
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

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxAudioChannelCount(Int.MAX_VALUE)
                    .setAudioOffloadPreferences(audioOffloadPreferences)
                    .build()
            )
        }

        val stableAudioSessionId = Util.generateAudioSessionIdV21(context)

        // 4. Build the Player
        val player = ExoPlayer.Builder(context, renderersFactory)
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
                onDecoderInitialized(decoderName)
            }

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception
            ) {
                Log.e("AudioPipeline", "AudioSink Error: ${audioSinkError.message}", audioSinkError)
                onAudioError("AudioSink Error: ${audioSinkError.message}")
            }

            override fun onPlayerError(
                eventTime: AnalyticsListener.EventTime,
                error: PlaybackException
            ) {
                Log.e("AudioPipeline", "Player Error: ${error.errorCodeName} - ${error.message}", error)
                onAudioError("Player Error: ${error.message}")
            }
        })

        return PlayerEngineResult(player, biquadEqProcessor, stableAudioSessionId)
    }
}
