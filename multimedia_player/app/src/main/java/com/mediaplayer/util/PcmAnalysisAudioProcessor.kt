package com.mediaplayer.util

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

data class AnalysisFrame(
    val timestampMs: Long,
    val bands: FloatArray,
    val waveform: FloatArray
)

@OptIn(UnstableApi::class)
class PcmAnalysisAudioProcessor : BaseAudioProcessor() {

    private val ringBuffer = ConcurrentLinkedQueue<AnalysisFrame>()
    private var totalSamplesProcessed = 0L
    private var sampleRate = 44100
    private var channelCount = 2

    private val fftSize = 2048
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / fftSize))).toFloat()
    }
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val pcmAccumulator = FloatArray(fftSize)
    private var accumPos = 0

    private val bandCount = 32
    private val logBands = FloatArray(bandCount)
    private val wave256 = FloatArray(256)

    fun clearBuffer() {
        ringBuffer.clear()
        accumPos = 0
        totalSamplesProcessed = 0L
    }

    fun getFrameForTime(heardMs: Long): AnalysisFrame? {
        val list = ringBuffer.toList()
        if (list.isEmpty()) return null

        var bestIdx = 0
        var minDiff = Long.MAX_VALUE

        for (i in list.indices) {
            val diff = abs(list[i].timestampMs - heardMs)
            if (diff < minDiff) {
                minDiff = diff
                bestIdx = i
            }
        }

        return list[bestIdx]
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        clearBuffer()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val encoding = inputAudioFormat.encoding
        val bytesPerFrame = channelCount * if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val framesInInput = remaining / bytesPerFrame

        val duplicate = inputBuffer.duplicate()

        if (encoding == C.ENCODING_PCM_16BIT) {
            for (f in 0 until framesInInput) {
                var monoSample = 0f
                for (c in 0 until channelCount) {
                    val s = duplicate.short
                    monoSample += s / 32768.0f
                }
                monoSample /= channelCount

                pcmAccumulator[accumPos] = monoSample
                accumPos++
                totalSamplesProcessed++

                if (accumPos >= 512) {
                    processFftAndEmit()
                    System.arraycopy(pcmAccumulator, 512, pcmAccumulator, 0, fftSize - 512)
                    accumPos -= 512
                }
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            for (f in 0 until framesInInput) {
                var monoSample = 0f
                for (c in 0 until channelCount) {
                    val s = duplicate.float
                    monoSample += s
                }
                monoSample /= channelCount

                pcmAccumulator[accumPos] = monoSample
                accumPos++
                totalSamplesProcessed++

                if (accumPos >= 512) {
                    processFftAndEmit()
                    System.arraycopy(pcmAccumulator, 512, pcmAccumulator, 0, fftSize - 512)
                    accumPos -= 512
                }
            }
        }

        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    private fun processFftAndEmit() {
        for (i in 0 until fftSize) {
            fftReal[i] = pcmAccumulator[i] * hannWindow[i]
            fftImag[i] = 0f
        }

        fftInPlace(fftReal, fftImag)

        val numBins = fftSize / 2
        val minFreq = 35.0
        val maxFreq = 14000.0
        val sr = sampleRate.toDouble()

        for (b in 0 until bandCount) {
            val fLow = minFreq * (maxFreq / minFreq).pow(b.toDouble() / bandCount)
            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1).toDouble() / bandCount)

            val binLow = (fLow * fftSize / sr).toInt().coerceIn(1, numBins - 1)
            val binHigh = (fHigh * fftSize / sr).toInt().coerceIn(binLow + 1, numBins)

            var magSum = 0f
            var count = 0
            for (k in binLow until binHigh) {
                val mag = hypot(fftReal[k].toDouble(), fftImag[k].toDouble()).toFloat()
                magSum += mag
                count++
            }
            val avgMag = if (count > 0) magSum / count else 0f
            val db = if (avgMag > 1e-6f) 20f * log10(avgMag) else -60f
            val normVal = ((db + 60f) / 60f).coerceIn(0f, 1f)
            logBands[b] = normVal
        }

        val step = fftSize / 256
        for (w in 0 until 256) {
            wave256[w] = pcmAccumulator[w * step].coerceIn(-1f, 1f)
        }

        val presentationTimeMs = (totalSamplesProcessed * 1000L) / sampleRate
        ringBuffer.add(AnalysisFrame(presentationTimeMs, logBands.copyOf(), wave256.copyOf()))

        while (ringBuffer.size > 100) {
            ringBuffer.poll()
        }
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var wR = 1f
            var wI = 0f

            for (m in 0 until halfLen) {
                var i = m
                while (i < n) {
                    val k = i + halfLen
                    val tr = wR * real[k] - wI * imag[k]
                    val ti = wR * imag[k] + wI * real[k]

                    real[k] = real[i] - tr
                    imag[k] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti

                    i += len
                }
                val nextWR = wR * wStepR - wI * wStepI
                val nextWI = wR * wStepI + wI * wStepR
                wR = nextWR
                wI = nextWI
            }
            len = len shl 1
        }
    }

    override fun onReset() {
        clearBuffer()
    }
}
