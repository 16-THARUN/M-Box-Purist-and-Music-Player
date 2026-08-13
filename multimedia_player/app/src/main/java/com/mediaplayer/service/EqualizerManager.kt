package com.mediaplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var fadeJob: Job? = null
    private var savedBassStrength: Short = 500

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionIdFlow: StateFlow<Int> = _audioSessionId.asStateFlow()

    var audioSessionId: Int 
        get() = _audioSessionId.value
        private set(value) { _audioSessionId.value = value }

    fun getEqualizer(): Equalizer? = equalizer

    // ── Init / Release ────────────────────────────────────────

    fun init(sessionId: Int) {
        release()
        audioSessionId = sessionId

        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
        }
        bassBoost = BassBoost(0, sessionId).apply {
            enabled = false
        }
        virtualizer = Virtualizer(0, sessionId).apply {
            enabled = false
        }
        loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
            enabled = false
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    // ── Equalizer ─────────────────────────────────────────────

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    fun getBandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0

    fun getBandLevelRange(): Pair<Int, Int> {
        val range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        return range[0].toInt() to range[1].toInt()
    }

    fun getBandCenterFreq(band: Int): Int {
        return (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000   // Hz
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        equalizer?.setBandLevel(band.toShort(), levelMb.toShort())
    }

    fun getBandLevel(band: Int): Int {
        return equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
    }

    fun applyBands(levels: List<Int>) {
        levels.forEachIndexed { i, level ->
            if (i < getBandCount()) setBandLevel(i, level)
        }
    }

    fun getAllBandLevels(): List<Int> {
        val count = getBandCount()
        return List(count) { getBandLevel(it) }
    }

    // ── Bass boost ────────────────────────────────────────────

    fun setBassBoostEnabled(enabled: Boolean, scope: CoroutineScope) {
        val bb = bassBoost ?: return
        
        fadeJob?.cancel()
        fadeJob = scope.launch(Dispatchers.IO) {
            if (enabled) {
                bb.enabled = true
                bb.setStrength(savedBassStrength)
            } else {
                // 🔥 THE FIX: Prevent the DSP audio burst pop with a smooth fade-out
                val currentStrength = bb.roundedStrength.toInt()
                if (currentStrength > 0) {
                    val steps = 10
                    val stepSize = currentStrength / steps
                    for (i in steps downTo 1) {
                        bb.setStrength((stepSize * i).toShort())
                        delay(10) // 100ms total fade duration
                    }
                }
                bb.setStrength(0)
                delay(20) // Let buffers clear
                bb.enabled = false
            }
        }
    }

    /** strength: 0..1000 */
    fun setBassBoostStrength(strength: Int) {
        savedBassStrength = strength.toShort()
        if (bassBoost?.enabled == true) {
            bassBoost?.setStrength(savedBassStrength)
        }
    }

    // ── Virtualizer (surround) ───────────────────────────────

    fun setVirtualizerEnabled(enabled: Boolean) {
        virtualizer?.enabled = enabled
    }

    /** strength: 0..1000 */
    fun setVirtualizerStrength(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
    }

    // ── Loudness enhancer ─────────────────────────────────────

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        loudnessEnhancer?.enabled = enabled
    }

    /** gain in millibels */
    fun setLoudnessGain(gainMb: Int) {
        loudnessEnhancer?.setTargetGain(gainMb)
    }
}
