package com.mediaplayer.data.model

data class EqPreset(
    val name: String,
    val mids: Int,
    val treble: Int,
    val bass: Int,
    val bands10: FloatArray // 10 gain values in dB (-10f to +10f)
)

object EqPresetRepository {
    val presets = listOf(
        EqPreset("Flat", 0, 0, 0, floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Signature", 3, 2, 4, floatArrayOf(4f, 3f, 1f, 0f, 2f, 3f, 4f, 3f, 2f, 1f)),
        EqPreset("Acoustic", 1, 3, 3, floatArrayOf(2f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f)),
        EqPreset("Bass Boost", -1, -2, 6, floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, -1f, -2f)),
        EqPreset("Pure Clarity", 2, 5, -1, floatArrayOf(-2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 5f, 4f)),
        EqPreset("Soft Bass", 0, 1, 3, floatArrayOf(3f, 2.5f, 2f, 1f, 0f, 0f, 0f, 1f, 1f, 1f)),
        EqPreset("Electronic", 2, 4, 5, floatArrayOf(5f, 4f, 2f, 0f, -1f, 2f, 3f, 4f, 4f, 3f)),
        EqPreset("Rock", 1, 3, 4, floatArrayOf(4.5f, 3f, 1.5f, 0f, -1f, 1f, 2.5f, 3.5f, 4f, 3f)),
        EqPreset("Pop", 2, 2, 2, floatArrayOf(-1f, 1f, 3f, 3f, 2f, 0f, 1f, 2f, 2f, 1f)),
        EqPreset("Jazz", 0, 2, 3, floatArrayOf(3f, 2f, 0f, 2f, 2f, 2f, 1f, 2f, 3f, 3f)),
        EqPreset("Voice", 5, 1, -3, floatArrayOf(-3f, -2f, 0f, 3f, 5f, 4f, 2f, 1f, 0f, -2f))
    )
}

data class HeadsetProfile(
    val id: String,
    val name: String,
    val tuningTarget: String, // e.g. "Harman 2019v2 Target", "Bass Enhanced Target"
    val switchConfig: String? = null, // e.g. "DDUU (1100) Switch Mode" for KZ Castor
    val bands10: FloatArray // 32Hz, 64Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
)

object HeadsetProfileRepository {
    val audiophileProfiles = listOf(
        HeadsetProfile(
            id = "kz_castor_bass_enhanced",
            name = "KZ Castor (Bass Edition)",
            tuningTarget = "Enhanced Sub-Bass Compensation",
            switchConfig = "Switch: 1-1-0-0 (Enhanced Bass Mode)",
            bands10 = floatArrayOf(4.6f, 5.8f, 5.3f, 4.9f, 0.0f, 2.3f, 1.1f, 1.0f, -0.9f, -3.7f)
        ),
        HeadsetProfile(
            id = "kz_castor_harman",
            name = "KZ Castor (Harman Target)",
            tuningTarget = "Harman In-Ear 2019 Neutral",
            switchConfig = "Switch: 0-0-1-1 (Balanced Clarity)",
            bands10 = floatArrayOf(1.5f, 2.0f, 1.0f, 0.0f, -0.5f, 1.0f, 2.5f, 3.0f, 1.5f, -1.0f)
        ),
        HeadsetProfile(
            id = "tangzu_waner",
            name = "Tangzu Wan'er S.G",
            tuningTarget = "Vocal Clarity / Pinna Gain Balance",
            bands10 = floatArrayOf(2.0f, 1.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.8f, 2.2f, 0.0f, -2.0f)
        ),
        HeadsetProfile(
            id = "moondrop_chu2",
            name = "Moondrop Chu II",
            tuningTarget = "VDSF Target Flat Compensation",
            bands10 = floatArrayOf(1.0f, 1.0f, 0.0f, 0.0f, 0.5f, 0.0f, -1.0f, 1.5f, 2.0f, 0.5f)
        ),
        HeadsetProfile(
            id = "7hz_zero2",
            name = "7Hz x Crinacle Zero:2",
            tuningTarget = "Warm Neutral / Sub-Bass Boost",
            bands10 = floatArrayOf(3.0f, 2.5f, 1.5f, 0.0f, -0.5f, 0.0f, 1.0f, 1.5f, 0.5f, -1.5f)
        )
    )
}
