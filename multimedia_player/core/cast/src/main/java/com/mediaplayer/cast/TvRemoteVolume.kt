package com.mediaplayer.cast

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TvRemoteVolume {
    private const val TAG = "MBoxCast"

    @Volatile var active = false; private set
    @Volatile var level = 50; private set
    @Volatile var muted = false; private set

    @Volatile var onStep: ((up: Boolean) -> Unit)? = null
    @Volatile var onMute: ((on: Boolean) -> Unit)? = null
    @Volatile var onPlayPause: (() -> Unit)? = null
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrev: (() -> Unit)? = null
    @Volatile var onTarget: ((level: Int) -> Unit)? = null

    data class Ui(val active: Boolean = false, val level: Int = 50, val muted: Boolean = false,
                  val tvName: String = "TV", val poke: Long = 0L)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui
    private fun pushUi(poke: Boolean = false) {
        val u = _ui.value
        _ui.value = u.copy(active = active, level = level, muted = muted, poke = if (poke) System.currentTimeMillis() else u.poke)
    }
    fun setTvName(name: String) {
        val n = name.ifBlank { "TV" }
        val changed = n != _ui.value.tvName
        _ui.value = _ui.value.copy(tvName = n)
        if (changed && active) appContext?.let { ctx -> com.mediaplayer.service.TvCastKeepAliveService.start(ctx, n) }
    }
    fun poke() = pushUi(poke = true)
    fun target(levelWanted: Int) { onTarget?.invoke(levelWanted.coerceIn(0, 100)) }

    private var appContext: Context? = null
    private var session: MediaSession? = null
    private var provider: VolumeProvider? = null
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(context: Context) { appContext = context.applicationContext }
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() = main.post { listeners.forEach { try { it() } catch (_: Exception) {} } }

    fun setActive(on: Boolean, why: String) {
        main.post {
            if (active == on) return@post
            active = on
            closeSession()
            pushUi()
            appContext?.let { ctx ->
                if (on) com.mediaplayer.service.TvCastKeepAliveService.start(ctx, _ui.value.tvName)
                else com.mediaplayer.service.TvCastKeepAliveService.stop(ctx)
            }
            Log.i(TAG, "volume buttons -> ${if (on) "TV (while M-Box is open on the phone)" else "phone"} ($why)")
            notifyListeners()
        }
    }

    private fun openSession() {
        val ctx = appContext ?: run { Log.e(TAG, "TV volume: no context (init not called)"); return }
        try {
            val p = object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, level) {
                override fun onAdjustVolume(direction: Int) {
                    when {
                        direction > 0 -> step(true, "phone button")
                        direction < 0 -> step(false, "phone button")
                        else -> {}
                    }
                }
            }
            val s = MediaSession(ctx, "M-Box TV")
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { onPlayPause?.invoke() }
                override fun onPause() { onPlayPause?.invoke() }
                override fun onSkipToNext() { onNext?.invoke() }
                override fun onSkipToPrevious() { onPrev?.invoke() }
            }, main)
            s.setPlaybackToRemote(p)
            s.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build())
            s.isActive = true
            session = s; provider = p
            Log.i(TAG, "TV volume session active (remote, buttons only, level $level)")
        } catch (e: Exception) {
            Log.e(TAG, "TV volume session failed: ${e.message}")
        }
    }

    private fun closeSession() {
        try { session?.isActive = false; session?.release() } catch (_: Exception) {}
        session = null; provider = null
    }

    fun update(newLevel: Int, newMuted: Boolean) {
        val l = newLevel.coerceIn(0, 100)
        if (l == level && newMuted == muted) return
        level = l; muted = newMuted
        pushUi()
        main.post { provider?.currentVolume = if (muted) 0 else level }
        notifyListeners()
    }

    @Volatile private var lastStepAt = 0L
    fun step(up: Boolean, from: String) {
        val now = System.currentTimeMillis()
        if (now - lastStepAt < 80) return
        lastStepAt = now
        pushUi(poke = true)
        Log.i(TAG, "volume ${if (up) "up" else "down"} ($from)")
        onStep?.invoke(up)
    }
}
