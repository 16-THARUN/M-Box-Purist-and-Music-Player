package com.mediaplayer.ui.visualizer

enum class CircularVisualizerType(val title: String, val subtitle: String, val isWaveform: Boolean) {
    BOTTOM_GLASS_BARS("Bottom Glass Bars", "Minimalist Spectrum", false),
    CLASSIC_SYMMETRICAL("Classic Symmetrical", "Legacy Spectrum", false),
    SPECTRUM_DOTS("Spectrum Dots", "Legacy Particles", false),
    NARROW_BARS("Narrow Bars", "Legacy Spectrum", false),
    PLAIN_OSCILLOSCOPE("Plain Oscilloscope", "Legacy Waveform", true)
}

class RadialAudioSmoother(val bandCount: Int = 48) {
    private val smoothed = FloatArray(bandCount)
    private val peakDots = FloatArray(bandCount)

    fun processFft(rawFft: FloatArray): Pair<FloatArray, FloatArray> {
        val count = minOf(bandCount, rawFft.size)
        for (i in 0 until count) {
            val target = rawFft[i].coerceIn(0f, 1f)
            smoothed[i] = if (target > smoothed[i]) {
                smoothed[i] + (target - smoothed[i]) * 0.75f
            } else {
                smoothed[i] - (smoothed[i] - target) * 0.12f
            }

            if (smoothed[i] >= peakDots[i]) {
                peakDots[i] = smoothed[i]
            } else {
                peakDots[i] = (peakDots[i] - 0.015f).coerceAtLeast(smoothed[i])
            }
        }
        return Pair(smoothed.copyOf(), peakDots.copyOf())
    }

    fun processWaveform(rawWaveform: ByteArray, sampleCount: Int = 120): FloatArray {
        val result = FloatArray(sampleCount)
        if (rawWaveform.isEmpty()) return result
        val step = (rawWaveform.size / sampleCount).coerceAtLeast(1)
        for (i in 0 until sampleCount) {
            val idx = (i * step).coerceIn(0, rawWaveform.size - 1)
            result[i] = (rawWaveform[idx].toInt() / 128f).coerceIn(-1f, 1f)
        }
        return result
    }
}
