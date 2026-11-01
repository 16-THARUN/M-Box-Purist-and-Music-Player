package com.mediaplayer.viewmodel

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.prefs.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PlayerPreferences
) : ViewModel() {

    data class SettingsUiState(
        val appTheme:         AppTheme          = AppTheme.SYSTEM,
        val gapless:          Boolean           = true,
        val crossfadeDuration:Int               = 0,
        val replayGainOn:     Boolean           = false,
        val replayGainMode:   String            = "track",
        val notifStyle:       NotificationStyle = NotificationStyle.EXPANDED,
        val lockscreenArt:    Boolean           = true,
        val showTrackNumber:  Boolean           = false,
        val scanExcludeShort: Boolean           = true,
        val scanMinDuration:  Int               = 30,
        val activeVisualizationMode: String     = "circular_rings",
        val eqEnabled:        Boolean           = true,
        val activePresetLabel:String            = "KZ Castor",
        val skipSilence:      Boolean           = false,
        val playbackSpeed:    Float             = 1.0f,
        val pitchLock:        Boolean           = true,
        val reverbPreset:     String            = "Off",
        val outputPrecision:  String            = "AudioTrack · CPH2649 · 48.0 kHz · 16-bit PCM",
        val preferUsbDac:     Boolean           = false,
        val monoAudio:        Boolean           = false,
        val isUsbDacConnected:Boolean           = false,
        val usbDacName:       String            = "",
        val usbDacSampleRate: String            = "48.0 kHz",
        val seekBarStyle:     String            = "pill",
        val openNowPlayingOnSongStart: Boolean   = false
    )

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    fun registerUsbDacListener(context: Context) {
        if (audioDeviceCallback != null) return
        try {
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val am = audioManager ?: return

            fun queryUsbAudioDevices() {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val usbDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                }

                if (usbDevice != null) {
                    val rawName = usbDevice.productName?.toString()?.ifBlank { "USB Audio DAC" } ?: "USB Audio DAC"
                    val dacName = if (rawName.startsWith("AudioTrack", ignoreCase = true)) rawName else "AudioTrack $rawName"
                    val rates = usbDevice.sampleRates
                    val sampleRateHz = rates.firstOrNull { it == 44100 || it == 48000 || it == 96000 || it == 192000 }
                        ?: rates.firstOrNull() ?: 44100
                    val sampleRateStr = String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000.0f)
                    val precisionStr = "$dacName · $sampleRateStr · 32-bit Float"

                    Log.d("UsbDacDebug", "USB DAC Connected: name=$dacName, sampleRate=$sampleRateStr")

                    _state.update {
                        it.copy(
                            isUsbDacConnected = true,
                            usbDacName = dacName,
                            usbDacSampleRate = sampleRateStr,
                            outputPrecision = precisionStr
                        )
                    }
                } else {
                    Log.d("UsbDacDebug", "USB DAC Disconnected")
                    _state.update {
                        it.copy(
                            isUsbDacConnected = false,
                            usbDacName = "",
                            usbDacSampleRate = "48.0 kHz",
                            outputPrecision = "AudioTrack · CPH2649 · 48.0 kHz · 16-bit PCM"
                        )
                    }
                }
            }

            queryUsbAudioDevices()

            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    queryUsbAudioDevices()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    queryUsbAudioDevices()
                }
            }

            am.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e("UsbDacDebug", "Error registering AudioDeviceCallback: ${e.message}", e)
        }
    }

    override fun onCleared() {
        try {
            audioDeviceCallback?.let {
                audioManager?.unregisterAudioDeviceCallback(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            combine(
                prefs.appTheme,
                prefs.gapless,
                prefs.crossfadeDuration,
                prefs.replayGainOn,
                prefs.replayGainMode,
                prefs.visualizerMode,
                prefs.seekBarStyle,
                prefs.openNowPlayingOnSongStart
            ) { args: Array<Any> ->
                _state.update { it.copy(
                    appTheme          = args[0] as AppTheme,
                    gapless           = args[1] as Boolean,
                    crossfadeDuration = args[2] as Int,
                    replayGainOn      = args[3] as Boolean,
                    replayGainMode    = args[4] as String,
                    activeVisualizationMode = args[5] as String,
                    seekBarStyle      = args[6] as String,
                    openNowPlayingOnSongStart = args[7] as Boolean
                ) }
            }.collect()
        }
        viewModelScope.launch {
            combine(
                prefs.notifStyle,
                prefs.lockscreenArt,
                prefs.showTrackNumber,
                prefs.scanExcludeShort,
                prefs.scanMinDuration
            ) { args: Array<Any> ->
                _state.update { it.copy(
                    notifStyle       = args[0] as NotificationStyle,
                    lockscreenArt    = args[1] as Boolean,
                    showTrackNumber  = args[2] as Boolean,
                    scanExcludeShort = args[3] as Boolean,
                    scanMinDuration  = args[4] as Int
                ) }
            }.collect()
        }
        viewModelScope.launch {
            combine(prefs.eqEnabled, prefs.eqPreset, prefs.eqBands) { enabled, presetName, bands ->
                val isDefaultOrFlat = presetName.isBlank() || 
                    presetName.equals("Flat", ignoreCase = true)

                val isCustom = presetName.equals("Custom", ignoreCase = true) || (isDefaultOrFlat && bands.any { it != 0 })

                val label = if (!enabled) {
                    "Off"
                } else if (isCustom) {
                    "Custom"
                } else if (!isDefaultOrFlat) {
                    presetName
                } else {
                    "KZ Castor"
                }

                _state.update { it.copy(eqEnabled = enabled, activePresetLabel = label) }
            }.collect()
        }

        viewModelScope.launch {
            combine(
                prefs.skipSilence,
                prefs.playbackSpeed,
                prefs.pitchLock,
                prefs.reverbPreset,
                prefs.outputPrecision,
                prefs.preferUsbDac,
                prefs.monoAudio
            ) { args: Array<Any> ->
                _state.update { it.copy(
                    skipSilence     = args[0] as Boolean,
                    playbackSpeed   = args[1] as Float,
                    pitchLock       = args[2] as Boolean,
                    reverbPreset    = args[3] as String,
                    outputPrecision = args[4] as String,
                    preferUsbDac    = args[5] as Boolean,
                    monoAudio       = args[6] as Boolean
                ) }
            }.collect()
        }
    }

    fun setAppTheme(t: AppTheme)            = viewModelScope.launch { prefs.setAppTheme(t) }
    fun setGapless(on: Boolean)             = viewModelScope.launch { prefs.setGapless(on) }
    fun setCrossfade(s: Int)                = viewModelScope.launch { prefs.setCrossfade(s) }
    fun setReplayGainOn(on: Boolean)        = viewModelScope.launch { prefs.setReplayGainOn(on) }
    fun setReplayGainMode(mode: String)     = viewModelScope.launch { prefs.setReplayGainMode(mode) }
    fun setNotifStyle(s: NotificationStyle) = viewModelScope.launch { prefs.setNotifStyle(s) }
    fun setLockscreenArt(on: Boolean)       = viewModelScope.launch { prefs.setLockscreenArt(on) }
    fun setShowTrackNumber(on: Boolean)     = viewModelScope.launch { prefs.setShowTrackNumber(on) }
    fun setScanExcludeShort(on: Boolean)    = viewModelScope.launch { prefs.setScanExcludeShort(on) }
    fun setScanMinDuration(s: Int)          = viewModelScope.launch { prefs.setScanMinDuration(s) }
    fun setActiveVisualizationMode(mode: String) = viewModelScope.launch { prefs.setVisualizerMode(mode) }

    fun setSkipSilence(on: Boolean) = viewModelScope.launch { prefs.setSkipSilence(on) }
    fun setPlaybackSpeed(s: Float) = viewModelScope.launch { prefs.setPlaybackSpeed(s) }
    fun setPitchLock(on: Boolean) = viewModelScope.launch { prefs.setPitchLock(on) }
    fun setReverbPreset(preset: String) = viewModelScope.launch { prefs.setReverbPreset(preset) }
    fun setOutputPrecision(p: String) = viewModelScope.launch { prefs.setOutputPrecision(p) }
    fun setPreferUsbDac(on: Boolean) = viewModelScope.launch { prefs.setPreferUsbDac(on) }
    fun setMonoAudio(on: Boolean) = viewModelScope.launch { prefs.setMonoAudio(on) }
    fun setSeekBarStyle(style: String) = viewModelScope.launch { prefs.setSeekBarStyle(style) }
    fun setOpenNowPlayingOnStart(on: Boolean) = viewModelScope.launch { prefs.setOpenNowPlayingOnStart(on) }
}
