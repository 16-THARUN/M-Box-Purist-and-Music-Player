package com.mediaplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.mbox.core.cast.R

class TvCastKeepAliveService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tvName = intent?.getStringExtra(EXTRA_TV) ?: "TV"
        val title = intent?.getStringExtra(EXTRA_TITLE)
        val n = buildNotification(tvName, title)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "keep-alive: couldn't start in the foreground (${e.message})")
            stopSelf(); return START_NOT_STICKY
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "mbox:cast").apply { setReferenceCounted(false); acquire() }
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mbox:cast").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        Log.i(TAG, "keep-alive: on (casting to $tvName) — phone server stays up in the background")
        return START_STICKY
    }

    override fun onDestroy() {
        try { wifiLock?.release() } catch (_: Exception) {}
        try { wakeLock?.release() } catch (_: Exception) {}
        wifiLock = null; wakeLock = null
        Log.i(TAG, "keep-alive: off")
        super.onDestroy()
    }

    private fun buildNotification(tvName: String, title: String?): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playing on TV", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while M-Box plays on your TV"; setShowBadge(false)
            })
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
        val open = PendingIntent.getActivity(this, 0, launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("Playing on $tvName")
            .setContentText(title ?: "M-Box")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "MBoxCast"
        private const val CHANNEL = "mbox_tv_cast"
        private const val NOTIF_ID = 4711
        private const val EXTRA_TV = "tv"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, tvName: String, title: String? = null) {
            try {
                val i = Intent(context, TvCastKeepAliveService::class.java).putExtra(EXTRA_TV, tvName).putExtra(EXTRA_TITLE, title)
                context.startForegroundService(i)
            } catch (e: Exception) {
                Log.e(TAG, "keep-alive: couldn't start (${e.message}) — open M-Box once while casting")
            }
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, TvCastKeepAliveService::class.java)) } catch (_: Exception) {}
        }
    }
}
