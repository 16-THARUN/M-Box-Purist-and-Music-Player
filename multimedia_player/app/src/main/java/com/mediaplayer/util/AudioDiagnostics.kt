package com.mediaplayer.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getHardwareSampleRate(): Int {
        val rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return rate?.toInt() ?: 48000
    }

    fun getHardwareBufferSize(): Int {
        val size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return size?.toInt() ?: 256
    }

    fun getOutputBitDepth(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "32 bit -> 24 bit"
        } else {
            "32 bit -> 16 bit"
        }
    }

    fun getOutputApi(): String {
        return "AudioTrack PCM Output"
    }

    fun getActiveOutputDeviceName(): String {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val isScreenRecorderActive = devices.any { it.type == 25 }

        val physicalDevice = devices.firstOrNull {
            it.type != 25 &&
            it.type != AudioDeviceInfo.TYPE_TELEPHONY &&
            (it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
             it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
             it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
             it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
             it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        } ?: devices.firstOrNull {
            it.type != 25 &&
            it.type != AudioDeviceInfo.TYPE_TELEPHONY
        }

        val rawName = physicalDevice?.productName?.toString() ?: ""
        val name = when (physicalDevice?.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB: ${if (rawName.isNotBlank() && !rawName.startsWith("CPH", ignoreCase = true)) rawName else "Audio DAC"}"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth: ${if (rawName.isNotBlank() && !rawName.startsWith("CPH", ignoreCase = true)) rawName else "Audio Device"}"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
            else -> "Built-in speaker"
        }

        return if (isScreenRecorderActive) "$name (captured by screen recorder)" else name
    }
}
