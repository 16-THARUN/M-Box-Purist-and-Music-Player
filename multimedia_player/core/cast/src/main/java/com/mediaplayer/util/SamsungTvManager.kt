package com.mediaplayer.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

data class DiscoveredSamsungTv(
    val ip: String,
    val name: String,
    val modelName: String,
    val wifiMac: String,
    val powerState: String = "on"
)

object SamsungTvManager {

    private const val TAG = "MBoxCast"
    private const val APP_ID = "MBoxTVApp1.MBox"

    private fun getTrustManager(targetHost: String): X509TrustManager {
        return object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
    }

    private fun getTrustedSslContext(targetHost: String): SSLContext {
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, arrayOf<TrustManager>(getTrustManager(targetHost)), SecureRandom())
        return sc
    }

    private fun trustHostSsl(targetHost: String) {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(getTrustedSslContext(targetHost).socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { hostname, _ ->
                hostname == targetHost || hostname == "samsung.remote.control"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting host SSL trust: ${e.message}")
        }
    }

    suspend fun discoverTvs(context: Context): List<DiscoveredSamsungTv> = withContext(Dispatchers.IO) {
        val tvs = mutableListOf<DiscoveredSamsungTv>()
        val phoneIp = MBoxCastServer.getLocalIpAddress() ?: ""
        if (phoneIp.isBlank() || phoneIp == "127.0.0.1") return@withContext emptyList()

        val prefix = phoneIp.substringBeforeLast(".")

        Log.i(TAG, "SSDP M-SEARCH started...")

        val ssdpIps = mutableSetOf<String>()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifiManager.createMulticastLock("MBoxSSDP")
            lock.acquire()

            val socket = DatagramSocket()
            socket.soTimeout = 1500

            val searchMsg = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"

            val sendData = searchMsg.toByteArray()
            val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("239.255.255.250"), 1900)
            socket.send(packet)

            val recvBuf = ByteArray(2048)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1500) {
                try {
                    val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                    socket.receive(recvPacket)
                    val ip = recvPacket.address.hostAddress
                    if (!ip.isNullOrBlank()) {
                        ssdpIps.add(ip)
                        Log.i(TAG, "SSDP reply from $ip")
                    }
                } catch (e: Exception) {
                    break
                }
            }
            socket.close()
            if (lock.isHeld) lock.release()
        } catch (e: Exception) {
            Log.w(TAG, "SSDP scan error: ${e.message}")
        }

        for (ip in ssdpIps) {
            val tv = queryTvInfo(ip)
            if (tv != null) tvs.add(tv)
        }

        if (tvs.isEmpty()) {
            for (i in 1..254) {
                val testIp = "$prefix.$i"
                if (testIp == phoneIp) continue
                val tv = queryTvInfo(testIp)
                if (tv != null) tvs.add(tv)
            }
        }

        val routeNames = tvs.joinToString { it.name }
        Log.i(TAG, "descriptor published: ${tvs.size} routes ($routeNames)")

        tvs
    }

    suspend fun queryTvInfo(ip: String): DiscoveredSamsungTv? = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:8001/api/v2/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1200
            conn.readTimeout = 1200
            val code = conn.responseCode
            if (code == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val deviceObj = json.optJSONObject("device")
                val name = deviceObj?.optString("name")?.ifBlank { "Samsung TV" } ?: "Samsung TV"
                val model = deviceObj?.optString("modelName") ?: ""
                val mac = deviceObj?.optString("wifiMac") ?: ""
                val powerState = deviceObj?.optString("PowerState", "on") ?: "on"

                Log.i(TAG, "GET http://$ip:8001/api/v2/ -> HTTP $code, name='$name', PowerState='$powerState'")

                return@withContext DiscoveredSamsungTv(
                    ip = ip,
                    name = name,
                    modelName = model,
                    wifiMac = mac,
                    powerState = powerState
                )
            } else {
                Log.w(TAG, "GET http://$ip:8001/api/v2/ -> HTTP $code")
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET http://$ip:8001/api/v2/ failed: ${e.message}")
        }
        null
    }

    suspend fun sendWakeOnLan(macAddress: String) = withContext(Dispatchers.IO) {
        if (macAddress.isBlank()) return@withContext
        try {
            val macBytes = macAddress.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
            if (macBytes.size != 6) return@withContext

            val bytes = ByteArray(6 + 16 * 6)
            for (i in 0..5) bytes[i] = 0xFF.toByte()
            for (i in 6 until bytes.size) bytes[i] = macBytes[i % 6]

            val address = InetAddress.getByName("255.255.255.255")
            val socket = DatagramSocket()
            socket.broadcast = true
            val packet = DatagramPacket(bytes, bytes.size, address, 9)

            repeat(3) {
                socket.send(packet)
                delay(100L)
            }
            socket.close()
            Log.i(TAG, "Sent WoL magic packet to MAC: $macAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WoL packet", e)
        }
    }

    fun sendRemoteKey(keyName: String) {
        try {
            val keyMsg = JSONObject().apply {
                put("method", "ms.remote.control")
                put("params", JSONObject().apply {
                    put("Cmd", "Click")
                    put("DataOfCmd", keyName)
                    put("Option", "false")
                    put("TypeOfRemote", "SendRemoteKey")
                })
            }
            Log.i(TAG, "Path A Remote Key Sent: $keyName")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending remote key $keyName: ${e.message}")
        }
    }

    suspend fun connectAndLaunchApp(
        context: Context,
        tvIp: String,
        phoneIp: String,
        savedToken: String,
        onTokenSaved: (String) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        trustHostSsl(tvIp)
        val appNameBase64 = Base64.encodeToString("M-Box".toByteArray(), Base64.NO_WRAP)
        val tokenParam = if (savedToken.isNotBlank()) "&token=$savedToken" else ""
        val wsUrl = "wss://$tvIp:8002/api/v2/channels/samsung.remote.control?name=$appNameBase64$tokenParam"

        Log.i(TAG, "8002 connecting to $wsUrl")
        onStatusUpdate("Connecting to TV…")

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .sslSocketFactory(getTrustedSslContext(tvIp).socketFactory, getTrustManager(tvIp))
                .hostnameVerifier { hostname, _ -> hostname == tvIp || hostname == "samsung.remote.control" }
                .build()

            val request = Request.Builder().url(wsUrl).build()
            val launchSuccess = CompletableDeferred<Boolean>()

            val webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket 8002 connected to TV")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.i(TAG, "TV 8002 Message: $text")
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")

                        if (event == "ms.channel.connect") {
                            val data = json.optJSONObject("data")
                            val token = data?.optString("token") ?: ""
                            if (token.isNotBlank()) {
                                Log.i(TAG, "ms.channel.connect (token saved: ${token.take(8)}...)")
                                onTokenSaved(token)
                            } else {
                                Log.i(TAG, "ms.channel.connect (no new token)")
                            }

                            val launchMsg = JSONObject().apply {
                                put("method", "ms.channel.emit")
                                put("params", JSONObject().apply {
                                    put("event", "ed.apps.launch")
                                    put("to", "host")
                                    put("data", JSONObject().apply {
                                        put("appId", APP_ID)
                                        put("action_type", "DEEP_LINK")
                                        put("metaTag", "senderIp=$phoneIp&port=8787")
                                    })
                                })
                            }
                            webSocket.send(launchMsg.toString())
                            Log.i(TAG, "launch sent over WebSocket 8002")
                            launchSuccess.complete(true)
                        } else if (event == "ms.channel.unauthorized") {
                            Log.e(TAG, "ms.channel.unauthorized -> user denied on TV")
                            onStatusUpdate("Allow M-Box in TV Device Connection Manager settings")
                            launchSuccess.complete(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing TV 8002 message: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 8002 failed: ${t.message}")
                    launchSuccess.complete(false)
                }
            })

            val success = withTimeoutOrNull(8000L) {
                launchSuccess.await()
            } ?: false

            if (success) {
                return@withContext true
            } else {
                Log.w(TAG, "WebSocket launch unsuccessful or timed out. Falling back to HTTP POST launch")
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebSocket 8002 exception: ${e.message}")
        }

        return@withContext launchAppOnTv(context, tvIp, phoneIp)
    }

    suspend fun launchAppOnTv(context: Context, tvIp: String, phoneIp: String): Boolean = withContext(Dispatchers.IO) {
        trustHostSsl(tvIp)
        val payload = JSONObject().apply {
            put("metaTag", "senderIp=$phoneIp&port=8787")
        }

        try {
            val launchUrl = URL("http://$tvIp:8001/api/v2/applications/$APP_ID")
            val conn = launchUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            conn.outputStream.write(payload.toString().toByteArray())
            val code = conn.responseCode
            Log.i(TAG, "HTTP 8001 Launch POST $launchUrl -> $code")
            if (code == 200 || code == 201) return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "HTTP 8001 Launch failed (${e.message}), trying HTTPS 8002...")
        }

        try {
            val launchUrl = URL("https://$tvIp:8002/api/v2/applications/$APP_ID")
            val conn = launchUrl.openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            conn.outputStream.write(payload.toString().toByteArray())
            val code = conn.responseCode
            Log.i(TAG, "HTTPS 8002 Launch POST $launchUrl -> $code")
            if (code == 200 || code == 201) return@withContext true
        } catch (e: Exception) {
            Log.w(TAG, "HTTPS 8002 Launch failed: ${e.message}")
        }

        false
    }
}
