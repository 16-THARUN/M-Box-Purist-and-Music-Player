package com.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaplayer.data.prefs.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        // EQ summary (read-only in Settings — editing is done in EQ screen)
        val eqEnabled:        Boolean           = true,
        val activePresetLabel:String            = EqPreset.FLAT.label
    )

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.appTheme,
                prefs.gapless,
                prefs.crossfadeDuration,
                prefs.replayGainOn,
                prefs.replayGainMode
            ) { theme, gapless, crossfade, rgOn, rgMode ->
                _state.update { it.copy(
                    appTheme          = theme,
                    gapless           = gapless,
                    crossfadeDuration = crossfade,
                    replayGainOn      = rgOn,
                    replayGainMode    = rgMode
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
            ) { notif, lockArt, trackNum, excludeShort, minDur ->
                _state.update { it.copy(
                    notifStyle       = notif,
                    lockscreenArt    = lockArt,
                    showTrackNumber  = trackNum,
                    scanExcludeShort = excludeShort,
                    scanMinDuration  = minDur
                ) }
            }.collect()
        }
        viewModelScope.launch {
            combine(prefs.eqEnabled, prefs.eqPreset) { enabled, presetName ->
                val label = runCatching { EqPreset.valueOf(presetName).label }
                    .getOrDefault(EqPreset.FLAT.label)
                _state.update { it.copy(eqEnabled = enabled, activePresetLabel = label) }
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
}
