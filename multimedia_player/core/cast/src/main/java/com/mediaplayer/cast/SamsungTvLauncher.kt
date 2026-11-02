package com.mediaplayer.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object SamsungTvLauncher {
    private const val TAG = "MBoxCast"
    const val APP_ID = "MBoxTVApp1.MBox"
    private const val CLIENT_NAME = "M-Box"
    private const val PREFS = "mbox_tv"

    interface Listener {
        fun onStatus(text: String) {}
        fun onLaunched(tvName: String) {}
        fun onFailed(reason: String) {}
    }

    data class TvInfo(val ip: String, val name: String, val model: String, val mac: String?, val powerState: String?)

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    private var remoteSocket: WebSocket? = null
    private var remoteIp: String? = null

    fun info(ip: String): TvInfo? {
        try {
            http.newCall(Request.Builder().url("http://$ip:8001/api/v2/").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                Log.i(TAG, "/api/v2 $ip -> HTTP ${r.code}")
                if (!r.isSuccessful) return null
                val d = JSONObject(body).optJSONObject("device") ?: return null
                val tv = TvInfo(ip, d.optString("name", "Samsung TV").removePrefix("[TV] "), d.optString("modelName"),
                    d.optString("wifiMac").ifBlank { null }, d.optString("PowerState").ifBlank { null })
                Log.i(TAG, "TV found: ${tv.name} (${tv.model}) ip=$ip mac=${tv.mac} power=${tv.powerState}")
                return tv
            }
        } catch (e: Exception) {
            Log.i(TAG, "/api/v2 $ip failed: ${e.javaClass.simpleName}: ${e.message}")
            return null
        }
    }

    fun wake(mac: String) {
        try {
            val m = mac.split(':', '-').map { it.toInt(16).toByte() }
            if (m.size != 6) return
            val pkt = ByteArray(6) { 0xFF.toByte() } + List(16) { m }.flatten().toByteArray()
            DatagramSocket().use { s ->
                s.broadcast = true
                repeat(3) { s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName("255.255.255.255"), 9)) }
            }
            Log.i(TAG, "Wake-on-LAN sent to $mac")
        } catch (e: Exception) { Log.i(TAG, "Wake-on-LAN failed: ${e.message}") }
    }

    fun connectAndLaunch(context: Context, ip: String, phoneIp: String, port: Int, listener: Listener, launchApp: Boolean = true) {
        Log.i(TAG, if (launchApp) "tap: open M-Box on $ip (phone $phoneIp:$port)" else "8002: opening the remote-control socket for the volume keys")
        listener.onStatus("Connecting…")

        var tv = info(ip)
        if (launchApp && (tv == null || tv.powerState?.equals("on", true) == false)) {
            val mac = tv?.mac ?: savedMac(context, ip)
            if (mac != null) {
                listener.onStatus("Waking the TV…"); wake(mac)
                for (i in 0 until 15) { Thread.sleep(1000); tv = info(ip); if (tv != null && tv.powerState?.equals("standby", true) != true) break }
            }
        }
        val found: TvInfo = tv ?: run { listener.onFailed("TV not reachable at $ip. Is it on and on the same Wi-Fi?"); return }
        found.mac?.let { saveMac(context, ip, it) }

        val token = savedToken(context, ip)
        val name64 = Base64.encodeToString(CLIENT_NAME.toByteArray(), Base64.NO_WRAP)
        val url = "wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$name64" + (token?.let { "&token=$it" } ?: "")
        Log.i(TAG, "8002: connecting (${if (token == null) "first time — TV will ask Allow/Deny" else "saved token"})")
        if (token == null) listener.onStatus("Accept on your TV")

        val client = tvClient(ip)
        val launched = java.util.concurrent.atomic.AtomicBoolean(false)
        client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { Log.i(TAG, "8002: socket open, waiting for the TV's answer") }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try { JSONObject(text) } catch (e: Exception) { return }
                when (msg.optString("event")) {
                    "ms.channel.connect" -> {
                        val newToken = msg.optJSONObject("data")?.optString("token").orEmpty()
                        if (newToken.isNotBlank()) { saveToken(context, ip, newToken); Log.i(TAG, "8002: ms.channel.connect — allowed, token saved") }
                        else Log.i(TAG, "8002: ms.channel.connect — allowed")
                        remoteSocket = webSocket; remoteIp = ip
                        if (launchApp && launched.compareAndSet(false, true)) sendLaunch(webSocket, ip, phoneIp, port, found.name, listener)
                    }
                    "ms.channel.unauthorized" -> {
                        Log.i(TAG, "8002: ms.channel.unauthorized — the TV said no")
                        clearToken(context, ip)
                        listener.onFailed("The TV said no. Allow M-Box in the TV's Device Connection Manager settings, then try again.")
                        webSocket.close(1000, null)
                    }
                    "ms.channel.timeOut" -> { Log.i(TAG, "8002: Allow/Deny timed out on the TV"); listener.onFailed("No answer on the TV (Allow/Deny). Try again.") }
                    "ed.apps.launch" -> { Log.i(TAG, "launch reply: ${text.take(300)}") }
                    "ed.installedApp.get" -> {
                        val apps = msg.optJSONObject("data")?.optJSONArray("data")
                        var mine: JSONObject? = null
                        if (apps != null) for (i in 0 until apps.length()) { val a = apps.optJSONObject(i); if (a?.optString("appId") == APP_ID) mine = a }
                        Log.i(TAG, "TV app list: ${apps?.length() ?: 0} apps; $APP_ID " + (mine?.let { "FOUND (name=${it.optString("name")}, app_type=${it.optInt("app_type")})" } ?: "NOT in the list"))
                    }
                    else -> { Log.i(TAG, "8002 message: ${text.take(200)}") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.i(TAG, "8002: failed: ${t.javaClass.simpleName}: ${t.message} (HTTP ${response?.code})")
                if (remoteSocket === webSocket) remoteSocket = null
                if (launchApp && launched.compareAndSet(false, true)) {
                    if (restLaunch(ip)) listener.onLaunched(found.name)
                    else listener.onFailed("Couldn't open M-Box on the TV (${t.message}).")
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "8002: closed $code $reason"); if (remoteSocket === ws) remoteSocket = null
            }
        })
    }

    private fun emitLaunch(ws: WebSocket, actionType: String, phoneIp: String, port: Int): Boolean {
        val data = JSONObject().put("appId", APP_ID).put("action_type", actionType).put("metaTag", "senderIp=$phoneIp&port=$port")
        val msg = JSONObject().put("method", "ms.channel.emit")
            .put("params", JSONObject().put("event", "ed.apps.launch").put("to", "host").put("data", data))
        return ws.send(msg.toString()).also { Log.i(TAG, "launch sent (ed.apps.launch $actionType $APP_ID, senderIp=$phoneIp port=$port) ok=$it") }
    }

    @Volatile private var tvReadyAt = 0L
    @Volatile private var launchStartedAt = 0L
    fun noteTvReady() {
        tvReadyAt = System.currentTimeMillis()
        if (launchStartedAt > 0) { Log.i(TAG, "launch: M-Box connected ${(tvReadyAt - launchStartedAt) / 100 / 10.0} s after the launch"); launchStartedAt = 0 }
    }

    private fun sendLaunch(ws: WebSocket, ip: String, phoneIp: String, port: Int, tvName: String, listener: Listener) {
        val t0 = System.currentTimeMillis(); launchStartedAt = t0; tvReadyAt = 0L
        listener.onStatus("Opening M-Box on $tvName…")
        val before = appStatus(ip)
        if (before.startsWith("running")) emitLaunch(ws, "DEEP_LINK", phoneIp, port)
        else restLaunch(ip)
        listener.onLaunched(tvName)
        Thread {
            var lastDeepLink = if (before.startsWith("running")) System.currentTimeMillis() else 0L
            var restAgain = false; var deepLinks = 0; var restFront = false
            while (System.currentTimeMillis() - t0 < 25_000) {
                Thread.sleep(500)
                if (tvReadyAt >= t0) return@Thread
                val st = appStatus(ip)
                val now = System.currentTimeMillis()
                if (st.startsWith("running") && now - lastDeepLink > 2000) {
                    lastDeepLink = now; deepLinks++
                    emitLaunch(ws, "DEEP_LINK", phoneIp, port)
                    if (deepLinks == 2 && !restFront) { restFront = true; Log.i(TAG, "launch: M-Box running but not connecting — REST launch to bring it to the front"); restLaunch(ip) }
                } else if (!st.startsWith("running") && !restAgain && now - t0 > 6000) {
                    restAgain = true; Log.i(TAG, "launch: M-Box still not running after 6 s ($st) — REST launch again"); restLaunch(ip)
                }
            }
            if (tvReadyAt < t0) {
                launchStartedAt = 0
                Log.i(TAG, "launch: M-Box didn't connect within 25 s (status: ${appStatus(ip)})")
                listener.onFailed("M-Box didn't connect. Check that the TV and phone are on the same Wi-Fi.")
            }
        }.start()
    }

    @Volatile private var remoteTriedAt = 0L
    fun ensureRemote(context: Context, ip: String) {
        if (remoteSocket != null && remoteIp == ip) return
        val now = System.currentTimeMillis(); if (now - remoteTriedAt < 10_000) return; remoteTriedAt = now
        if (savedToken(context, ip) == null) { Log.i(TAG, "volume keys: no saved TV token yet (tap the TV once in Play on)"); return }
        Thread { connectAndLaunch(context, ip, "", 0, object : Listener {}, launchApp = false) }.start()
    }

    @Volatile private var lastStatus = ""
    fun appStatus(ip: String): String {
        return try {
            http.newCall(Request.Builder().url("http://$ip:8001/api/v2/applications/$APP_ID").build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                val st = if (r.code == 404) "unknown app (404)" else if (!r.isSuccessful) "HTTP ${r.code}" else {
                    val j = JSONObject(body)
                    val running = j.optBoolean("running", false); val visible = j.optBoolean("visible", false)
                    if (running && visible) "running+visible" else if (running) "running (not visible)" else "installed, not running"
                }
                if (st != lastStatus) { lastStatus = st; Log.i(TAG, "app status: $st ${body.take(160)}") }
                st
            }
        } catch (e: Exception) { Log.i(TAG, "app status failed: ${e.message}"); "error" }
    }

    fun restLaunch(ip: String): Boolean {
        return try {
            http.newCall(Request.Builder().url("http://$ip:8001/api/v2/applications/$APP_ID")
                .post(ByteArray(0).toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                Log.i(TAG, "REST launch $APP_ID -> HTTP ${r.code}"); r.isSuccessful
            }
        } catch (e: Exception) { Log.i(TAG, "REST launch failed: ${e.message}"); false }
    }

    fun hasRemote(): Boolean = remoteSocket != null
    fun sendKey(key: String): Boolean {
        val ws = remoteSocket ?: run { Log.i(TAG, "key $key: remote-control socket not open"); return false }
        val msg = JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
            .put("Cmd", "Click").put("DataOfCmd", key).put("Option", "false").put("TypeOfRemote", "SendRemoteKey"))
        return ws.send(msg.toString()).also { Log.i(TAG, "key $key sent=$it") }
    }

    private fun tvClient(ip: String): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { host, _ -> host == ip }
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS).build()
    }

    fun phoneWifiIp(context: Context): String? {
        try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address }?.let { return it.hostAddress }
        } catch (_: Exception) {}
        @Suppress("DEPRECATION")
        val ip = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        return if (ip == 0) null else "${ip and 255}.${ip shr 8 and 255}.${ip shr 16 and 255}.${ip shr 24 and 255}"
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun savedToken(c: Context, ip: String) = prefs(c).getString("token_$ip", null)
    private fun saveToken(c: Context, ip: String, t: String) = prefs(c).edit().putString("token_$ip", t).apply()
    private fun clearToken(c: Context, ip: String) = prefs(c).edit().remove("token_$ip").apply()
    private fun savedMac(c: Context, ip: String) = prefs(c).getString("mac_$ip", null)
    private fun saveMac(c: Context, ip: String, m: String) = prefs(c).edit().putString("mac_$ip", m).apply()
}
