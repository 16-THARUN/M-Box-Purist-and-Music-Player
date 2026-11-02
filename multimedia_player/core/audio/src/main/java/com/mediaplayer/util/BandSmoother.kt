package com.mediaplayer.util

import kotlin.math.exp
import kotlin.math.min

class BandSmoother(
    private val bandCount: Int = 32,
    private val attackMs: Float = 25f,
    private val releaseMs: Float = 150f
) {
    private val values = FloatArray(bandCount)

    fun process(target: FloatArray, dtMs: Float, out: FloatArray) {
        val a = 1f - exp(-dtMs / attackMs)
        val r = 1f - exp(-dtMs / releaseMs)
        val n = min(bandCount, min(target.size, out.size))
        for (i in 0 until n) {
            val t = target[i].coerceIn(0f, 1f)
            val v = values[i]
            values[i] = v + (t - v) * (if (t > v) a else r)
            out[i] = values[i]
        }
    }

    fun reset() = values.fill(0f)
}
