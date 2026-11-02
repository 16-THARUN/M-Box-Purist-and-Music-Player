package com.mediaplayer.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.pow

@OptIn(UnstableApi::class)
class BiquadEqAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val TUNABLE_GRAPHIC_Q = 4.322f
    }

    private var isEnabled = true
    private var preventClipping = true
    private var preampDb = 0f
    private var bands: List<BiquadBandSpec> = emptyList()

    private val filters = mutableListOf<BiquadFilter>()

    private var currentLinearGain = 1.0f
    private var targetLinearGain = 1.0f

    @Synchronized
    fun configureFilterCascade(
        enabled: Boolean,
        preampGainDb: Float,
        preventClippingEnabled: Boolean,
        bandSpecs: List<BiquadBandSpec>
    ) {
        this.isEnabled = enabled
        this.preampDb = preampGainDb
        this.preventClipping = preventClippingEnabled
        this.bands = bandSpecs

        val maxPositiveGain = bandSpecs.filter { it.type == BiquadFilterType.PEAKING_EQ || it.type == BiquadFilterType.LOW_SHELF || it.type == BiquadFilterType.HIGH_SHELF }
            .maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f

        val safetyAttenuationDb = if (preventClippingEnabled) maxPositiveGain else 0f
        val effectivePreampDb = preampGainDb - safetyAttenuationDb

        targetLinearGain = 10.0f.pow(effectivePreampDb / 20.0f)

        if (inputAudioFormat.sampleRate > 0) {
            updateFilterCoefficients()
        }
    }

    private fun updateFilterCoefficients() {
        val sampleRate = inputAudioFormat.sampleRate
        if (sampleRate <= 0) return

        while (filters.size < bands.size) {
            filters.add(BiquadFilter())
        }

        for (i in bands.indices) {
            filters[i].configure(bands[i], sampleRate)
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        updateFilterCoefficients()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isEnabled || bands.isEmpty()) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val buffer = replaceOutputBuffer(remaining)
        val channelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        val encoding = inputAudioFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT) {
            val sampleCount = remaining / 2
            val frameCount = sampleCount / channelCount

            for (f in 0 until frameCount) {
                currentLinearGain += (targetLinearGain - currentLinearGain) * 0.01f

                for (c in 0 until channelCount) {
                    val rawShort = inputBuffer.short
                    var sampleFloat = (rawShort / 32768.0f) * currentLinearGain

                    if (sampleFloat.isNaN() || sampleFloat.isInfinite()) {
                        sampleFloat = 0f
                    }

                    for (i in bands.indices) {
                        sampleFloat = filters[i].processChannel(sampleFloat, c)
                    }

                    val clampedShort = (sampleFloat.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                    buffer.putShort(clampedShort)
                }
            }
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            val sampleCount = remaining / 4
            val frameCount = sampleCount / channelCount

            for (f in 0 until frameCount) {
                currentLinearGain += (targetLinearGain - currentLinearGain) * 0.01f

                for (c in 0 until channelCount) {
                    var sampleFloat = inputBuffer.float * currentLinearGain

                    if (sampleFloat.isNaN() || sampleFloat.isInfinite()) {
                        sampleFloat = 0f
                    }

                    for (i in bands.indices) {
                        sampleFloat = filters[i].processChannel(sampleFloat, c)
                    }

                    buffer.putFloat(sampleFloat.coerceIn(-1.0f, 1.0f))
                }
            }
        }

        buffer.flip()
    }

    override fun onReset() {
        for (f in filters) {
            f.resetState()
        }
    }
}
