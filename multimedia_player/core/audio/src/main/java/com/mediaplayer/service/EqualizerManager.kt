package com.mediaplayer.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mediaplayer.data.model.HeadsetProfile
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

    private val _eqAvailableFlow = MutableStateFlow(true)
    val eqAvailableFlow: StateFlow<Boolean> = _eqAvailableFlow.asStateFlow()

    @Volatile private var currentAttachedSessionId: Int = -1
    @Volatile private var isAttachingRetry: Boolean = false

    var audioSessionId: Int 
        get() = _audioSessionId.value
        private set(value) { _audioSessionId.value = value }

    fun getEqualizer(): Equalizer? = equalizer

    fun attach(sessionId: Int) {
        if (sessionId <= 0) {
            Log.i("MBoxEq", "skipped (no session or uninitialized: $sessionId)")
            return
        }
        if (sessionId == currentAttachedSessionId && equalizer != null) {
            Log.i("MBoxEq", "already attached to session $sessionId")
            return
        }

        release()
        audioSessionId = sessionId
        currentAttachedSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            runCatching { bassBoost = BassBoost(0, sessionId).apply { enabled = false } }
            runCatching { virtualizer = Virtualizer(0, sessionId).apply { enabled = false } }
            runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = false } }

            _eqAvailableFlow.value = true
            Log.i("MBoxEq", "attach($sessionId) successful")
        } catch (t: Throwable) {
            Log.w("MBoxEq", "Equalizer unavailable: ${t.message}, session=$sessionId")
            _eqAvailableFlow.value = false

            if (!isAttachingRetry) {
                isAttachingRetry = true
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        release()
                        equalizer = Equalizer(0, sessionId).apply { enabled = true }
                        runCatching { bassBoost = BassBoost(0, sessionId).apply { enabled = false } }
                        runCatching { virtualizer = Virtualizer(0, sessionId).apply { enabled = false } }
                        runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = false } }

                        _eqAvailableFlow.value = true
                        Log.i("MBoxEq", "attach($sessionId) retry successful")
                    } catch (retryThrowable: Throwable) {
                        Log.w("MBoxEq", "Equalizer attach retry failed: ${retryThrowable.message}, session=$sessionId")
                        _eqAvailableFlow.value = false
                    } finally {
                        isAttachingRetry = false
                    }
                }, 1000L)
            }
        }
    }

    fun init(sessionId: Int) {
        attach(sessionId)
    }

    fun release() {
        Log.i("MBoxEq", "released session $currentAttachedSessionId")
        currentAttachedSessionId = -1
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
    }

    fun getBandCount(): Int = runCatching { equalizer?.numberOfBands?.toInt() }.getOrNull() ?: 0

    fun getBandLevelRange(): Pair<Int, Int> {
        val range = runCatching { equalizer?.bandLevelRange }.getOrNull() ?: shortArrayOf(-1500, 1500)
        return range[0].toInt() to range[1].toInt()
    }

    fun getBandCenterFreq(band: Int): Int {
        return runCatching { (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000 }.getOrDefault(0)
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), levelMb.toShort()) }
    }

    fun getBandLevel(band: Int): Int {
        return runCatching { equalizer?.getBandLevel(band.toShort())?.toInt() }.getOrNull() ?: 0
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

    fun applyHeadsetProfile(profile: HeadsetProfile) {
        val eq = equalizer ?: return
        runCatching {
            profile.bands10.forEachIndexed { index, gainDb ->
                val bandIndex = index.toShort()
                if (bandIndex < eq.numberOfBands) {
                    val gainMb = (gainDb * 100).toInt().coerceIn(
                        eq.bandLevelRange[0].toInt(),
                        eq.bandLevelRange[1].toInt()
                    ).toShort()
                    eq.setBandLevel(bandIndex, gainMb)
                }
            }
        }
    }

    fun updateOrbitalEq(mids: Int, treble: Int, bass: Int) {
        val eq = equalizer ?: return
        runCatching {
            val bassLevelMb = (bass * 100).coerceIn(-1500, 1500)
            val midsLevelMb = (mids * 100).coerceIn(-1500, 1500)
            val trebleLevelMb = (treble * 100).coerceIn(-1500, 1500)

            for (i in 0..2) if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), bassLevelMb.toShort())
            for (i in 3..6) if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), midsLevelMb.toShort())
            for (i in 7..9) if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), trebleLevelMb.toShort())
        }
    }

    fun setBassBoostEnabled(enabled: Boolean, scope: CoroutineScope) {
        val bb = bassBoost ?: return
        
        fadeJob?.cancel()
        fadeJob = scope.launch(Dispatchers.IO) {
            runCatching {
                if (enabled) {
                    bb.enabled = true
                    bb.setStrength(savedBassStrength)
                } else {
                    val currentStrength = bb.roundedStrength.toInt()
                    if (currentStrength > 0) {
                        val steps = 10
                        val stepSize = currentStrength / steps
                        for (i in steps downTo 1) {
                            bb.setStrength((stepSize * i).toShort())
                            delay(10)
                        }
                    }
                    bb.setStrength(0)
                    delay(20)
                    bb.enabled = false
                }
            }
        }
    }

    fun setBassBoostStrength(strength: Int) {
        savedBassStrength = strength.toShort()
        runCatching {
            if (bassBoost?.enabled == true) {
                bassBoost?.setStrength(savedBassStrength)
            }
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        runCatching { virtualizer?.enabled = enabled }
    }

    fun setVirtualizerStrength(strength: Int) {
        runCatching { virtualizer?.setStrength(strength.toShort()) }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        runCatching { loudnessEnhancer?.enabled = enabled }
    }

    fun setLoudnessGain(gainMb: Int) {
        runCatching { loudnessEnhancer?.setTargetGain(gainMb) }
    }
}
