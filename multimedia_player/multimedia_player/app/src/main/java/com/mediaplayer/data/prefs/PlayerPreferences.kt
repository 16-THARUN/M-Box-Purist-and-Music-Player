package com.mediaplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "player_prefs")

// 10 bands: 32 64 125 250 500 1k 2k 4k 8k 16k Hz — levels in millibels (-1500..+1500)
enum class EqPreset(val label: String, val bandLevels: List<Int>) {
    FLAT        ("Flat",         listOf( 0,    0,    0,    0,    0,    0,    0,    0,    0,    0)),
    BASS_BOOST  ("Bass boost",   listOf( 700,  600,  400,  200,  0,    0,    0,    0,    0,    0)),
    TREBLE_BOOST("Treble boost", listOf( 0,    0,    0,    0,    0,    200,  400,  500,  600,  700)),
    VOCAL       ("Vocal",        listOf(-200, -100,   0,   200,  400,  400,  200,   0,  -100, -200)),
    ROCK        ("Rock",         listOf( 400,  300, -100, -200,   0,   200,  300,  300,  300,  400)),
    CLASSICAL   ("Classical",    listOf( 400,  300,  200,  100,   0,    0,    0,    0,   200,  300)),
    HIP_HOP     ("Hip-hop",      listOf( 500,  400,  100,  300, -100, -100,  100,  200,  300,  300)),
    JAZZ        ("Jazz",         listOf( 200,  100,   0,   200,  300,  300,  200,  100,   0,    0)),
    POP         ("Pop",          listOf(-100,   0,   200,  300,  300,  200,  100,  100,   0,   -100)),
    ELECTRONIC  ("Electronic",   listOf( 400,  300,   0,  -200, -100,  200,  300,  300,  400,  400)),
}

enum class AppTheme(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    BLACK("Black (AMOLED)")
}

enum class NotificationStyle(val label: String) {
    COMPACT("Compact (3 actions)"),
    EXPANDED("Expanded (5 actions)")
}

@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val ds = ctx.dataStore

    companion object {
        // ── Playback ─────────────────────────────────────────────────────────
        val REPEAT_MODE     = intPreferencesKey("repeat_mode")
        val SHUFFLE_ON      = booleanPreferencesKey("shuffle_on")
        val GAPLESS         = booleanPreferencesKey("gapless")
        val VOLUME_LEVEL    = floatPreferencesKey("volume_level")
        val LAST_TRACK_URI  = stringPreferencesKey("last_track_uri")
        val LAST_POSITION   = longPreferencesKey("last_position")

        // ── Equalizer ────────────────────────────────────────────────────────
        val EQ_ENABLED      = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET       = stringPreferencesKey("eq_preset")
        val EQ_BANDS        = stringPreferencesKey("eq_bands")       // CSV of 10 ints
        val BASS_BOOST_ON   = booleanPreferencesKey("bass_boost_on")
        val BASS_BOOST_STR  = intPreferencesKey("bass_boost_strength")
        val VIRT_ON         = booleanPreferencesKey("virtualizer_on")
        val VIRT_STR        = intPreferencesKey("virtualizer_strength")

        // ── Settings ─────────────────────────────────────────────────────────
        val APP_THEME           = stringPreferencesKey("app_theme")          // AppTheme.name
        val CROSSFADE_DURATION  = intPreferencesKey("crossfade_duration_s")  // 0 = off, 1-12 s
        val REPLAY_GAIN_ON      = booleanPreferencesKey("replay_gain_on")
        val REPLAY_GAIN_MODE    = stringPreferencesKey("replay_gain_mode")   // "track" | "album"
        val LOCKSCREEN_ART      = booleanPreferencesKey("lockscreen_art")
        val NOTIF_STYLE         = stringPreferencesKey("notification_style") // NotificationStyle.name
        val SHOW_TRACK_NUMBER   = booleanPreferencesKey("show_track_number")
        val SCAN_EXCLUDE_SHORT  = booleanPreferencesKey("scan_exclude_short") // skip <30 s files
        val SCAN_MIN_DURATION   = intPreferencesKey("scan_min_duration_s")   // default 30
    }

    // ── Playback flows ────────────────────────────────────────────────────────
    val repeatMode:  Flow<Int>     = ds.data.map { it[REPEAT_MODE]  ?: 0 }
    val shuffleOn:   Flow<Boolean> = ds.data.map { it[SHUFFLE_ON]   ?: false }
    val gapless:     Flow<Boolean> = ds.data.map { it[GAPLESS]      ?: true }
    val volumeLevel: Flow<Float>   = ds.data.map { it[VOLUME_LEVEL] ?: 1f }

    // ── EQ flows ──────────────────────────────────────────────────────────────
    val eqEnabled:        Flow<Boolean> = ds.data.map { it[EQ_ENABLED]     ?: true }
    val eqPreset:         Flow<String>  = ds.data.map { it[EQ_PRESET]      ?: EqPreset.FLAT.name }
    val bassBoostOn:      Flow<Boolean> = ds.data.map { it[BASS_BOOST_ON]  ?: false }
    val bassBoostStrength:Flow<Int>     = ds.data.map { it[BASS_BOOST_STR] ?: 500 }
    val virtualizerOn:    Flow<Boolean> = ds.data.map { it[VIRT_ON]        ?: false }
    val virtualizerStrength: Flow<Int>  = ds.data.map { it[VIRT_STR]       ?: 500 }

    val eqBands: Flow<List<Int>> = ds.data.map { prefs ->
        prefs[EQ_BANDS]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 10 }
            ?: EqPreset.FLAT.bandLevels
    }

    // ── Settings flows ────────────────────────────────────────────────────────
    val appTheme:         Flow<AppTheme>          = ds.data.map {
        runCatching { AppTheme.valueOf(it[APP_THEME] ?: "") }.getOrDefault(AppTheme.SYSTEM)
    }
    val crossfadeDuration:Flow<Int>               = ds.data.map { it[CROSSFADE_DURATION]  ?: 0 }
    val replayGainOn:     Flow<Boolean>            = ds.data.map { it[REPLAY_GAIN_ON]      ?: false }
    val replayGainMode:   Flow<String>             = ds.data.map { it[REPLAY_GAIN_MODE]    ?: "track" }
    val lockscreenArt:    Flow<Boolean>            = ds.data.map { it[LOCKSCREEN_ART]      ?: true }
    val notifStyle:       Flow<NotificationStyle>  = ds.data.map {
        runCatching { NotificationStyle.valueOf(it[NOTIF_STYLE] ?: "") }.getOrDefault(NotificationStyle.EXPANDED)
    }
    val showTrackNumber:  Flow<Boolean>            = ds.data.map { it[SHOW_TRACK_NUMBER]   ?: false }
    val scanExcludeShort: Flow<Boolean>            = ds.data.map { it[SCAN_EXCLUDE_SHORT]  ?: true }
    val scanMinDuration:  Flow<Int>                = ds.data.map { it[SCAN_MIN_DURATION]   ?: 30 }

    // ── Writes ────────────────────────────────────────────────────────────────
    suspend fun setRepeatMode(mode: Int)           = ds.edit { it[REPEAT_MODE]       = mode }
    suspend fun setShuffleOn(on: Boolean)          = ds.edit { it[SHUFFLE_ON]        = on }
    suspend fun setGapless(on: Boolean)            = ds.edit { it[GAPLESS]           = on }
    suspend fun setVolumeLevel(v: Float)           = ds.edit { it[VOLUME_LEVEL]      = v }
    suspend fun saveLastTrack(uri: String, ms: Long) = ds.edit {
        it[LAST_TRACK_URI] = uri; it[LAST_POSITION] = ms
    }

    suspend fun setEqEnabled(enabled: Boolean)     = ds.edit { it[EQ_ENABLED]        = enabled }
    suspend fun setBassBoostOn(on: Boolean)        = ds.edit { it[BASS_BOOST_ON]     = on }
    suspend fun setBassBoostStrength(s: Int)       = ds.edit { it[BASS_BOOST_STR]    = s }
    suspend fun setVirtualizerOn(on: Boolean)      = ds.edit { it[VIRT_ON]           = on }
    suspend fun setVirtualizerStrength(s: Int)     = ds.edit { it[VIRT_STR]          = s }

    suspend fun setEqBands(bands: List<Int>)       = ds.edit { it[EQ_BANDS] = bands.joinToString(",") }
    suspend fun applyPreset(preset: EqPreset)      = ds.edit {
        it[EQ_PRESET] = preset.name
        it[EQ_BANDS]  = preset.bandLevels.joinToString(",")
    }

    suspend fun setAppTheme(t: AppTheme)           = ds.edit { it[APP_THEME]         = t.name }
    suspend fun setCrossfade(seconds: Int)         = ds.edit { it[CROSSFADE_DURATION]= seconds }
    suspend fun setReplayGainOn(on: Boolean)       = ds.edit { it[REPLAY_GAIN_ON]    = on }
    suspend fun setReplayGainMode(mode: String)    = ds.edit { it[REPLAY_GAIN_MODE]  = mode }
    suspend fun setLockscreenArt(on: Boolean)      = ds.edit { it[LOCKSCREEN_ART]    = on }
    suspend fun setNotifStyle(s: NotificationStyle)= ds.edit { it[NOTIF_STYLE]       = s.name }
    suspend fun setShowTrackNumber(on: Boolean)    = ds.edit { it[SHOW_TRACK_NUMBER] = on }
    suspend fun setScanExcludeShort(on: Boolean)   = ds.edit { it[SCAN_EXCLUDE_SHORT]= on }
    suspend fun setScanMinDuration(s: Int)         = ds.edit { it[SCAN_MIN_DURATION] = s }
}
