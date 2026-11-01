package com.mediaplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mediaplayer.ui.components.SortOption
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
    KZ_CASTOR   ("KZ Castor",    listOf( 455,  575,  534,  487,  568,  235,  113,  101, -89, -367)),
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
        val VIS_ENABLED     = booleanPreferencesKey("visualizer_enabled")
        val VIS_MODE        = stringPreferencesKey("visualizer_mode")
        val CUSTOM_PRESETS  = stringPreferencesKey("custom_presets")

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
        val SKIP_SILENCE        = booleanPreferencesKey("skip_silence")
        val PLAYBACK_SPEED      = floatPreferencesKey("playback_speed")
        val PITCH_LOCK          = booleanPreferencesKey("pitch_lock")
        val REVERB_PRESET       = stringPreferencesKey("reverb_preset")
        val OUTPUT_PRECISION    = stringPreferencesKey("output_precision")
        val PREFER_USB_DAC      = booleanPreferencesKey("prefer_usb_dac")
        val MONO_AUDIO          = booleanPreferencesKey("mono_audio")
        val SEEKBAR_STYLE       = stringPreferencesKey("seekbar_style")      // "pill" | "wavy"
        val OPEN_NOW_PLAYING_ON_START = booleanPreferencesKey("open_now_playing_on_start")
        val LAST_TV_IP          = stringPreferencesKey("last_tv_ip")
        val LAST_TV_NAME        = stringPreferencesKey("last_tv_name")
        val LAST_TV_MAC         = stringPreferencesKey("last_tv_mac")
        val LAST_TV_TOKEN       = stringPreferencesKey("last_tv_token")
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
    val visualizerEnabled: Flow<Boolean> = ds.data.map { it[VIS_ENABLED]   ?: true }
    val visualizerMode:    Flow<String>  = ds.data.map { it[VIS_MODE]      ?: "1" }
    val customPresetsJson: Flow<String>  = ds.data.map { it[CUSTOM_PRESETS] ?: "[]" }

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
    val skipSilence:      Flow<Boolean>            = ds.data.map { it[SKIP_SILENCE]        ?: false }
    val playbackSpeed:    Flow<Float>              = ds.data.map { it[PLAYBACK_SPEED]      ?: 1.0f }
    val pitchLock:        Flow<Boolean>            = ds.data.map { it[PITCH_LOCK]          ?: true }
    val reverbPreset:     Flow<String>             = ds.data.map { it[REVERB_PRESET]       ?: "Off" }
    val outputPrecision:  Flow<String>             = ds.data.map { it[OUTPUT_PRECISION]    ?: "32-bit Float" }
    val preferUsbDac:     Flow<Boolean>            = ds.data.map { it[PREFER_USB_DAC]      ?: false }
    val monoAudio:        Flow<Boolean>            = ds.data.map { it[MONO_AUDIO]          ?: false }
    val seekBarStyle:     Flow<String>             = ds.data.map { it[SEEKBAR_STYLE]       ?: "pill" }
    val openNowPlayingOnSongStart: Flow<Boolean>   = ds.data.map { it[OPEN_NOW_PLAYING_ON_START] ?: false }
    val lastTvIp:         Flow<String>             = ds.data.map { it[LAST_TV_IP]          ?: "" }
    val lastTvName:       Flow<String>             = ds.data.map { it[LAST_TV_NAME]        ?: "" }
    val lastTvMac:        Flow<String>             = ds.data.map { it[LAST_TV_MAC]         ?: "" }
    val lastTvToken:      Flow<String>             = ds.data.map { it[LAST_TV_TOKEN]       ?: "" }

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
    suspend fun setVisualizerEnabled(on: Boolean)  = ds.edit { it[VIS_ENABLED]       = on }
    suspend fun setVisualizerMode(mode: String)    = ds.edit { it[VIS_MODE]          = mode }
    suspend fun setCustomPresets(json: String)     = ds.edit { it[CUSTOM_PRESETS]    = json }

    suspend fun setEqBands(bands: List<Int>)       = ds.edit { it[EQ_BANDS] = bands.joinToString(",") }
    suspend fun applyPreset(preset: EqPreset)      = ds.edit {
        it[EQ_PRESET] = preset.name
        it[EQ_BANDS]  = preset.bandLevels.joinToString(",")
    }
    suspend fun applyPresetName(name: String)      = ds.edit {
        it[EQ_PRESET] = name
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
    suspend fun setSkipSilence(on: Boolean)        = ds.edit { it[SKIP_SILENCE]     = on }
    suspend fun setPlaybackSpeed(s: Float)         = ds.edit { it[PLAYBACK_SPEED]   = s }
    suspend fun setPitchLock(on: Boolean)          = ds.edit { it[PITCH_LOCK]       = on }
    suspend fun setReverbPreset(preset: String)    = ds.edit { it[REVERB_PRESET]    = preset }
    suspend fun setOutputPrecision(p: String)      = ds.edit { it[OUTPUT_PRECISION] = p }
    suspend fun setPreferUsbDac(on: Boolean)       = ds.edit { it[PREFER_USB_DAC]   = on }
    suspend fun setMonoAudio(on: Boolean)          = ds.edit { it[MONO_AUDIO]       = on }
    suspend fun setSeekBarStyle(style: String)     = ds.edit { it[SEEKBAR_STYLE]    = style }
    suspend fun setOpenNowPlayingOnStart(on: Boolean) = ds.edit { it[OPEN_NOW_PLAYING_ON_START] = on }
    suspend fun saveLastTvInfo(ip: String, name: String, mac: String, token: String) = ds.edit {
        it[LAST_TV_IP] = ip
        it[LAST_TV_NAME] = name
        it[LAST_TV_MAC] = mac
        if (token.isNotBlank()) it[LAST_TV_TOKEN] = token
    }

    fun getListSortOrderFlow(type: ListType): Flow<SortOption> = ds.data.map { prefs ->
        val key = stringPreferencesKey("list_sort_${type.name.lowercase()}")
        val name = prefs[key] ?: SortOption.TITLE.name
        runCatching { SortOption.valueOf(name) }.getOrDefault(SortOption.TITLE)
    }

    fun getListReverseFlow(type: ListType): Flow<Boolean> = ds.data.map { prefs ->
        val key = booleanPreferencesKey("list_reverse_${type.name.lowercase()}")
        prefs[key] ?: false
    }

    suspend fun setListSortOrder(type: ListType, sortOption: SortOption) = ds.edit { prefs ->
        val key = stringPreferencesKey("list_sort_${type.name.lowercase()}")
        prefs[key] = sortOption.name
    }

    suspend fun setListReverse(type: ListType, isReverse: Boolean) = ds.edit { prefs ->
        val key = booleanPreferencesKey("list_reverse_${type.name.lowercase()}")
        prefs[key] = isReverse
    }

    suspend fun cleanupOldItemKeys() = ds.edit { prefs ->
        val keysToRemove = prefs.asMap().keys.filter { key ->
            key.name.startsWith("sort_order_folder_") || key.name.startsWith("sort_order_item_") || key.name.startsWith("category_sort_")
        }
        for (k in keysToRemove) {
            prefs.remove(k)
        }
    }
}

enum class ListType(val displayName: String, val subtitleText: String) {
    ALL_SONGS("All Songs", "Applies to all songs"),
    ALBUMS("Albums", "Applies to all albums"),
    ALBUM_SONGS("Album Songs", "Applies to all album songs"),
    ARTISTS("Artists", "Applies to all artists"),
    ARTIST_SONGS("Artist Songs", "Applies to all artist songs"),
    ALBUM_ARTISTS("Album Artists", "Applies to all album artists"),
    FOLDERS("Folders", "Applies to all folders"),
    FOLDER_SONGS("Folder Songs", "Applies to all folder songs"),
    FOLDERS_HIERARCHY("Folders Hierarchy", "Applies to folder hierarchy view"),
    GENRES("Genres", "Applies to all genres"),
    GENRE_SONGS("Genre Songs", "Applies to all genre songs"),
    YEARS("Years", "Applies to all years"),
    YEAR_SONGS("Year Songs", "Applies to all year songs"),
    COMPOSERS("Composers", "Applies to all composers"),
    COMPOSER_SONGS("Composer Songs", "Applies to all composer songs"),
    PLAYLISTS("Playlists", "Applies to all playlists")
}
