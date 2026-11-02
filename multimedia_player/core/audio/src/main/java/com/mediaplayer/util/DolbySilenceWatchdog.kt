package com.mediaplayer.util

import android.util.Log

class DolbySilenceWatchdog(
    private val onSilenceDetected: () -> Unit
) {
    private var consecutiveSilenceWindows = 0
    private var isEnabled = false

    fun startTracking() {
        isEnabled = true
        consecutiveSilenceWindows = 0
    }

    fun stopTracking() {
        isEnabled = false
        consecutiveSilenceWindows = 0
    }

    fun processAudioWindow(peakAmplitude: Float, playbackPositionMs: Long) {
        if (!isEnabled || playbackPositionMs < 3000L) return

        if (peakAmplitude < 0.0001f) {
            consecutiveSilenceWindows++
            if (consecutiveSilenceWindows >= 8) {
                Log.e("MBoxAudio", "Silence Watchdog Triggered! Digital silence detected for >2s at pos ${playbackPositionMs}ms. Executing fallback to Tier 3!")
                stopTracking()
                onSilenceDetected()
            }
        } else {
            consecutiveSilenceWindows = 0
        }
    }
}
