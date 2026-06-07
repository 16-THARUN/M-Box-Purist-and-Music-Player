package com.mediaplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var audioSessionId: Int = 0
        private set

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

    fun setBassBoostEnabled(enabled: Boolean) {
        bassBoost?.enabled = enabled
    }

    /** strength: 0..1000 */
    fun setBassBoostStrength(strength: Int) {
        bassBoost?.setStrength(strength.toShort())
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
