package com.mediaplayer.util

import android.media.audiofx.Visualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot

@Singleton
class VisualizerManager @Inject constructor() {
    val pcmAnalysisProcessor = PcmAnalysisAudioProcessor()
    private val smoother = VisualizerSmoother(32, 0.65f, 0.18f)
    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1

    private val _fftFlow = MutableStateFlow(FloatArray(0))
    val fftFlow: StateFlow<FloatArray> = _fftFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(audioSessionId: Int) {
        stop()
        currentSessionId = audioSessionId

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {}

                    private val rawBuffer = FloatArray(32)

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft != null && fft.isNotEmpty()) {
                            val n = fft.size
                            val binCount = minOf(32, n / 2)

                            for (i in 0 until binCount) {
                                val real = fft[i * 2].toFloat()
                                val imaginary = fft[i * 2 + 1].toFloat()
                                val magnitude = (hypot(real, imaginary) / 128f).coerceIn(0f, 1f)

                                rawBuffer[i] = if (magnitude.isNaN() || magnitude.isInfinite()) 0f else magnitude
                            }

                            val smooth = smoother.process(rawBuffer)
                            _fftFlow.value = smooth
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)

                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setRemoteBands(bands: ByteArray) {
        val floatBands = FloatArray(bands.size)
        for (i in bands.indices) {
            floatBands[i] = (bands[i].toInt() and 0xFF) / 255f
        }
        val smooth = smoother.process(floatBands)
        _fftFlow.value = smooth
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        smoother.reset()
        _fftFlow.value = FloatArray(0)
    }
}
