package com.mediaplayer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

fun mapSymmetricBandIndex(i: Int, n: Int, bandCount: Int): Int {
    val m = min(i, n - i) / (n / 2.0f)
    val b = min(m, 1.0f - m) * 2.0f
    return (b * (bandCount - 1)).toInt().coerceIn(0, bandCount - 1)
}

fun getLightenedAccentColor(accentColor: Color): Color {
    val maxRGB = max(accentColor.red, max(accentColor.green, accentColor.blue))
    val minRGB = min(accentColor.red, min(accentColor.green, accentColor.blue))
    val lightness = (maxRGB + minRGB) / 2f
    return if (lightness < 0.55f) {
        Color.White.copy(alpha = 0.5f).compositeOver(accentColor)
    } else {
        accentColor
    }
}

fun getLightTint(accentColor: Color): Color {
    return Color.White.copy(alpha = 0.6f).compositeOver(accentColor)
}

class AutoGainBandSmoother(
    private val bandCount: Int,
    private val attackMs: Float = 12f,
    private val releaseMs: Float = 110f,
    private val peakDecayMs: Float = 3000f
) {
    private val smoothedValues = FloatArray(bandCount)
    private val peaks = FloatArray(bandCount) { 0.1f }

    fun process(rawInput: FloatArray, dtMs: Float, out: FloatArray) {
        val a = 1f - exp(-dtMs / attackMs)
        val r = 1f - exp(-dtMs / releaseMs)
        val peakDecay = exp(-dtMs / peakDecayMs)
        val n = min(bandCount, min(rawInput.size, out.size))

        for (i in 0 until n) {
            val raw = rawInput[i]
            val floored = if (raw < 0.003f) 0f else raw

            peaks[i] = max(peaks[i] * peakDecay, max(floored, 0.05f))
            val target = (floored / peaks[i]).coerceIn(0f, 1f)

            val v = smoothedValues[i]
            smoothedValues[i] = v + (target - v) * (if (target > v) a else r)
            out[i] = smoothedValues[i]
        }
    }

    fun reset() {
        smoothedValues.fill(0f)
        peaks.fill(0.1f)
    }
}
