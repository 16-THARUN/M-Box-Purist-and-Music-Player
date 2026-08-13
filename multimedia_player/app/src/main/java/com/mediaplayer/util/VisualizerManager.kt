package com.mediaplayer.util

import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

// 🔥 THE FIX: Wrap FFT data to force Compose redraws
data class FftState(val data: ByteArray? = null) {
    override fun equals(other: Any?): Boolean = false 
    override fun hashCode(): Int = data?.contentHashCode() ?: 0
}

@Singleton
class VisualizerManager @Inject constructor() {
    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1
    private val _fftData = MutableStateFlow(FftState())
    val fftData: StateFlow<FftState> = _fftData.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(audioSessionId: Int) {
        if (visualizer != null && currentSessionId == audioSessionId) {
            return
        }
        stop() 
        currentSessionId = audioSessionId
        
        try {
            // 🔥 Explicitly lock to the player's session ID
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] 
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft != null) {
                            // 🔥 Clone the array and wrap it in the FftState data class
                            _fftData.value = FftState(fft.copyOf())
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                
                enabled = true 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        _fftData.value = FftState()
    }
}
