package com.mediaplayer.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.content.Context
import android.util.Log

data class PairedAudioDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
)

object BluetoothHelper {

    @SuppressLint("MissingPermission")
    fun getPairedAudioDevices(context: Context): List<PairedAudioDevice> {
        val devices = mutableListOf<PairedAudioDevice>()
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()

            val bonded = adapter.bondedDevices ?: return emptyList()
            for (dev in bonded) {
                val c = dev.bluetoothClass
                val devName = dev.name ?: "Unknown Device"

                Log.d(
                    "BtDebug",
                    "name=$devName " +
                    "majorClass=${c?.majorDeviceClass} " +
                    "deviceClass=${c?.deviceClass} " +
                    "hasAudio=${c?.hasService(BluetoothClass.Service.AUDIO)} " +
                    "hasRender=${c?.hasService(BluetoothClass.Service.RENDER)}"
                )

                val majorClass = c?.majorDeviceClass ?: 0
                val isComputer = majorClass == BluetoothClass.Device.Major.COMPUTER
                val isPeripheral = majorClass == BluetoothClass.Device.Major.PERIPHERAL
                val isPhone = majorClass == BluetoothClass.Device.Major.PHONE

                val hasAudioService = c?.hasService(BluetoothClass.Service.AUDIO) == true
                val hasRenderService = c?.hasService(BluetoothClass.Service.RENDER) == true
                val isAudioVideoMajor = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO

                val isAudio = (hasAudioService || hasRenderService || isAudioVideoMajor) &&
                    (!isComputer) &&
                    (!isPeripheral) &&
                    (!isPhone)

                if (isAudio) {
                    devices.add(PairedAudioDevice(devName, dev.address))
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothHelper", "Error getting paired devices: ${e.message}")
        }
        return devices
    }
}
