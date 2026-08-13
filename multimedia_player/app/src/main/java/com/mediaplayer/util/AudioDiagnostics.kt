package com.mediaplayer.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Returns the system hardware sample rate (e.g., 48000)
    fun getHardwareSampleRate(): Int {
        val rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return rate?.toInt() ?: 48000
    }

    // Returns the system hardware buffer size
    fun getHardwareBufferSize(): Int {
        val size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return size?.toInt() ?: 256
    }

    // Determines the bit depth conversion
    fun getOutputBitDepth(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "32-bit Float"
        } else {
            "16-bit PCM"
        }
    }
    
    fun getOutputApi(): String {
        return "AudioTrack (Java)" // Default for Media3/ExoPlayer unless using custom extensions
    }
}
