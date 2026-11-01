package com.mediaplayer.service

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadFilterType {
    LOW_SHELF,  // Type 0
    HIGH_SHELF, // Type 1
    PEAKING_EQ  // Type 2
}

data class BiquadBandSpec(
    val type: BiquadFilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float
)

class BiquadFilter {

    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Max 8 channels supported independently (5.1 surround / 7.1 surround / stereo)
    private val x1 = FloatArray(8)
    private val x2 = FloatArray(8)
    private val y1 = FloatArray(8)
    private val y2 = FloatArray(8)

    fun resetState() {
        x1.fill(0f)
        x2.fill(0f)
        y1.fill(0f)
        y2.fill(0f)
    }

    fun configure(spec: BiquadBandSpec, sampleRateHz: Int) {
        val fs = sampleRateHz.toFloat().coerceAtLeast(8000f)
        val f0 = spec.frequencyHz.coerceIn(10f, fs * 0.49f)
        val gainDb = spec.gainDb
        val q = spec.q.coerceAtLeast(0.1f)

        if (abs(gainDb) < 0.01f) {
            b0 = 1.0f; b1 = 0.0f; b2 = 0.0f; a1 = 0.0f; a2 = 0.0f
            return
        }

        val w0 = (2.0 * Math.PI * f0 / fs).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val A = 10.0f.pow(gainDb / 40.0f)
        val alpha = sinW0 / (2.0f * q)

        var rawB0 = 1f
        var rawB1 = 0f
        var rawB2 = 0f
        var rawA0 = 1f
        var rawA1 = 0f
        var rawA2 = 0f

        when (spec.type) {
            BiquadFilterType.PEAKING_EQ -> {
                rawB0 = 1.0f + alpha * A
                rawB1 = -2.0f * cosW0
                rawB2 = 1.0f - alpha * A
                rawA0 = 1.0f + alpha / A
                rawA1 = -2.0f * cosW0
                rawA2 = 1.0f - alpha / A
            }
            BiquadFilterType.LOW_SHELF -> {
                val sqrtA = sqrt(A)
                rawB0 = A * ((A + 1f) - (A - 1f) * cosW0 + 2f * sqrtA * alpha)
                rawB1 = 2f * A * ((A - 1f) - (A + 1f) * cosW0)
                rawB2 = A * ((A + 1f) - (A - 1f) * cosW0 - 2f * sqrtA * alpha)
                rawA0 = (A + 1f) + (A - 1f) * cosW0 + 2f * sqrtA * alpha
                rawA1 = -2f * ((A - 1f) + (A + 1f) * cosW0)
                rawA2 = (A + 1f) + (A - 1f) * cosW0 - 2f * sqrtA * alpha
            }
            BiquadFilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(A)
                rawB0 = A * ((A + 1f) + (A - 1f) * cosW0 + 2f * sqrtA * alpha)
                rawB1 = -2f * A * ((A - 1f) + (A + 1f) * cosW0)
                rawB2 = A * ((A + 1f) + (A - 1f) * cosW0 - 2f * sqrtA * alpha)
                rawA0 = (A + 1f) - (A - 1f) * cosW0 + 2f * sqrtA * alpha
                rawA1 = 2f * ((A - 1f) + (A + 1f) * cosW0)
                rawA2 = (A + 1f) - (A - 1f) * cosW0 - 2f * sqrtA * alpha
            }
        }

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
    }

    fun processChannel(x: Float, channelIndex: Int): Float {
        if (x.isNaN() || x.isInfinite()) return 0f

        val c = channelIndex.coerceIn(0, 7)
        val y = b0 * x + b1 * x1[c] + b2 * x2[c] - a1 * y1[c] - a2 * y2[c]

        val safeY = if (y.isNaN() || y.isInfinite()) 0f else y.coerceIn(-10f, 10f)

        x2[c] = x1[c]
        x1[c] = x
        y2[c] = y1[c]
        y1[c] = safeY

        return safeY
    }
}
