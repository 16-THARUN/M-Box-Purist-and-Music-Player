package com.mediaplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeDspEngine @Inject constructor() {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun init(sessionId: Int) {
        equalizer = Equalizer(0, sessionId).apply { enabled = true }
        bassBoost = BassBoost(0, sessionId)
        virtualizer = Virtualizer(0, sessionId)
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun applyManualEqualizer(gains: FloatArray) {
        virtualizer?.enabled = false
        bassBoost?.enabled = false

        equalizer?.let { eq ->
            eq.enabled = true
            val numBands = eq.numberOfBands

            for (i in 0 until numBands.toInt()) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000f
                val targetFrequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
                val closestIndex = targetFrequencies.indices.minByOrNull { 
                    kotlin.math.abs(targetFrequencies[it] - centerFreq) 
                } ?: 0
                
                val targetGainDb = gains[closestIndex]
                val millibels = (targetGainDb * 100).toInt().toShort()
                eq.setBandLevel(i.toShort(), millibels)
            }
        }
    }
}
