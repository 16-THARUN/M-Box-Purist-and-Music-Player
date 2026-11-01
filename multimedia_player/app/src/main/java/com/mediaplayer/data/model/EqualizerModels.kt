package com.mediaplayer.data.model

data class SoundSignature(
    val name: String,
    val mids: Float,   // -6f to +6f
    val treble: Float,
    val bass: Float,
    val subtitle: String? = null
)

object EqualizerRepository {
    val echoSignatures = listOf(
        SoundSignature("Flat", mids = 0f, treble = 0f, bass = 0f),
        SoundSignature("Signature", mids = 3f, treble = -1f, bass = 5f),
        SoundSignature("Acoustic", mids = 1f, treble = 3f, bass = 3f),
        SoundSignature("Bass Boost", mids = -2f, treble = -1f, bass = 6f),
        SoundSignature("Pure Clarity", mids = 2f, treble = 5f, bass = -1f),
        SoundSignature("Soft Bass", mids = 0f, treble = 1f, bass = 3f),
        SoundSignature("Electronic", mids = 2f, treble = 4f, bass = 5f),
        SoundSignature("Rock", mids = 1f, treble = 3f, bass = 4f),
        SoundSignature("Pop", mids = 2f, treble = 2f, bass = 2f),
        SoundSignature("Jazz", mids = 0f, treble = 2f, bass = 3f),
        SoundSignature("Voice", mids = 7f, treble = 0f, bass = -3f)
    )

    val headsetProfiles = listOf(
        SoundSignature("KZ Castor Bass", mids = 1f, treble = -2f, bass = 6f, subtitle = "1-1-0-0"),
        SoundSignature("KZ Castor Harman", mids = 2f, treble = 3f, bass = 2f, subtitle = "0-0-1-1"),
        SoundSignature("Tangzu Wan'er", mids = 3f, treble = 2f, bass = 1f),
        SoundSignature("Moondrop Chu II", mids = 1f, treble = 2f, bass = 0f)
    )

    val dolbyModes = listOf("Dolby Open", "Dolby Rich", "Dolby Focused")
    val diracModes = listOf("Dirac Music", "Dirac Movie", "Dirac Game")
}
