package com.mediaplayer.util

class VisualizerSmoother(
    private val bandCount: Int = 32,
    private val attackFactor: Float = 0.65f,
    private val decayFactor: Float = 0.18f
) {
    private val smoothedMagnitudes = FloatArray(bandCount)

    fun process(rawMagnitudes: FloatArray): FloatArray {
        val size = minOf(bandCount, rawMagnitudes.size)
        for (i in 0 until size) {
            val raw = rawMagnitudes[i].coerceIn(0f, 1f)
            val current = smoothedMagnitudes[i]

            smoothedMagnitudes[i] = if (raw > current) {
                current + (raw - current) * attackFactor
            } else {
                current - (current - raw) * decayFactor
            }
        }
        return smoothedMagnitudes.copyOf()
    }

    fun reset() {
        smoothedMagnitudes.fill(0f)
    }
}
