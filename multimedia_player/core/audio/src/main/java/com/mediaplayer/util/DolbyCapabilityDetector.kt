package com.mediaplayer.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import android.util.Log

data class DolbyCapabilities(
    val eac3JocCodecName: String?,
    val eac3CodecName: String?,
    val ac3CodecName: String?,
    val isSpatializerAvailable: Boolean,
    val isSpatializerEnabled: Boolean,
    val immersiveLevel: String
)

object DolbyCapabilityDetector {

    fun queryCapabilities(context: Context): DolbyCapabilities {
        var eac3JocName: String? = null
        var eac3Name: String? = null
        var ac3Name: String? = null

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                val types = info.supportedTypes
                for (type in types) {
                    when {
                        type.equals("audio/eac3-joc", ignoreCase = true) -> eac3JocName = info.name
                        type.equals("audio/eac3", ignoreCase = true) -> eac3Name = info.name
                        type.equals("audio/ac3", ignoreCase = true) -> ac3Name = info.name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MBoxAudio", "Error querying MediaCodecList: ${e.message}")
        }

        var isSpatialAvailable = false
        var isSpatialEnabled = false
        var levelStr = "NONE"

        if (Build.VERSION.SDK_INT >= 32) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val spatializer = audioManager?.spatializer
                if (spatializer != null) {
                    isSpatialAvailable = spatializer.isAvailable
                    isSpatialEnabled = spatializer.isEnabled
                    levelStr = spatializer.immersiveAudioLevel.toString()
                }
            } catch (e: Exception) {
                Log.w("MBoxAudio", "Error querying Spatializer API: ${e.message}")
            }
        }

        val capabilities = DolbyCapabilities(
            eac3JocCodecName = eac3JocName,
            eac3CodecName = eac3Name,
            ac3CodecName = ac3Name,
            isSpatializerAvailable = isSpatialAvailable,
            isSpatializerEnabled = isSpatialEnabled,
            immersiveLevel = levelStr
        )

        Log.i(
            "MBoxAudio",
            """
            ================ STEP 0 DOLBY CAPABILITY REPORT ================
            eac3-joc Decoder: ${eac3JocName ?: "NONE"}
            eac3 Decoder:     ${eac3Name ?: "NONE"}
            ac3 Decoder:      ${ac3Name ?: "NONE"}
            Spatializer Available: $isSpatialAvailable
            Spatializer Enabled:   $isSpatialEnabled
            Immersive Level:       $levelStr
            ================================================================
            """.trimIndent()
        )

        return capabilities
    }
}
