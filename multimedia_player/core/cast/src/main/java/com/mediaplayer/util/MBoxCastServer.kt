package com.mediaplayer.util

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.mediaplayer.data.model.Track
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class FlacVirtualHeader(
    val headerBytes: ByteArray,
    val audioOffset: Long,
    val originalFileLength: Long
) {
    val totalVirtualLength: Long = 42L + (originalFileLength - audioOffset)
}

class MBoxCastServer(
    private val context: Context,
    private val listeningPort: Int = 8787,
    private val onTvEventReceived: ((String, JSONObject) -> Unit)? = null
) : NanoWSD(listeningPort) {

    private var activeAudioUri: Uri? = null
    private var activeAudioFile: File? = null
    private var activeAudioMime: String? = null
    private var activeArtworkUri: Uri? = null

    private val trackFileMap = ConcurrentHashMap<String, File>()
    private val trackUriMap = ConcurrentHashMap<String, Uri>()
    private val flacHeaderCache = ConcurrentHashMap<String, FlacVirtualHeader>()

    private val connectedSockets = mutableListOf<WebSocket>()

    /** IP of the TV whose M-Box app is (or last was) connected — used to open M-Box on it again. */
    @Volatile var lastTvIp: String? = null
    /** Last time the TV's M-Box app sent anything (it sends STATE every second while it runs). */
    @Volatile var lastTvMessageAt: Long = 0L
    /**
     * True only while the TV's M-Box app is connected AND alive. On Samsung TVs, leaving an app
     * (Home / another app) usually only suspends it: the socket stays open but nothing arrives.
     * Then we must send the "open M-Box" command again (it brings the app back to the front).
     */
    fun isTvConnected(): Boolean {
        val open = synchronized(connectedSockets) { connectedSockets.isNotEmpty() }
        val alive = System.currentTimeMillis() - lastTvMessageAt < 4000
        if (open && !alive) Log.i("MBoxCast", "TV socket open but silent for ${System.currentTimeMillis() - lastTvMessageAt} ms — M-Box is in the background or closed")
        return open && alive
    }
    private val wsExecutor = Executors.newSingleThreadExecutor()

    init {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i("MBoxCastServer", "Unified HTTP & WebSocket Server started on port $listeningPort")
            runStartupSelfTest()
        } catch (e: Exception) {
            Log.e("MBoxCastServer", "Failed to start server on port $listeningPort: ${e.message}", e)
        }
    }

    private fun runStartupSelfTest() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("http://127.0.0.1:$listeningPort/mbox/hello")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                val code = conn.responseCode
                Log.i("MBoxCastServer", "Self-test OK (HTTP $code from /mbox/hello)")
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("MBoxCastServer", "Self-test error: ${e.message}")
            }
        }
    }

    fun setAudioTrackSource(trackId: String, file: File?, uri: Uri?, mimeType: String?) {
        activeAudioFile = file
        activeAudioUri = uri
        activeAudioMime = mimeType
        if (file != null && file.exists()) {
            trackFileMap[trackId] = file
        }
        if (uri != null) {
            trackUriMap[trackId] = uri
        }
    }

    private val trackArtUriMap = ConcurrentHashMap<String, Uri>()
    private val artworkMemoryCache = ConcurrentHashMap<String, ByteArray>()

    fun setArtworkSource(trackId: String, artUri: Uri?) {
        if (artUri != null) {
            val sanitizedId = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            trackArtUriMap[sanitizedId] = artUri
        }
    }

    fun broadcastMessage(jsonMessage: String) {
        wsExecutor.execute {
            synchronized(connectedSockets) {
                val iterator = connectedSockets.iterator()
                while (iterator.hasNext()) {
                    val ws = iterator.next()
                    try {
                        ws.send(jsonMessage)
                        val json = runCatching { JSONObject(jsonMessage) }.getOrNull()
                        val action = json?.optString("action") ?: "MESSAGE"
                        val reason = json?.optString("reason") ?: "user action"
                        val url = json?.optString("url") ?: ""
                        val pos = json?.optDouble("position", -1.0)
                        val posStr = if (pos != null && pos >= 0) " position=$pos" else ""
                        val urlStr = if (url.isNotBlank()) " url=$url" else ""
                        Log.i("MBoxCastServer", "Sent to TV: $action (reason: $reason)$urlStr$posStr")
                    } catch (e: Exception) {
                        Log.e("MBoxCastServer", "Error sending WS message", e)
                        iterator.remove()
                    }
                }
            }
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return MBoxWebSocket(handshake)
    }

    private inner class MBoxWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            synchronized(connectedSockets) {
                connectedSockets.add(this)
            }
            Log.i("MBoxCastServer", "WebSocket connected from ${handshakeRequest.remoteIpAddress}")
            handshakeRequest.remoteIpAddress?.let { ip ->
                lastTvIp = ip
                context.getSharedPreferences("mbox_tv", Context.MODE_PRIVATE).edit().putString("last_tv_ip", ip).apply()
            }
            val tvReadyEvent = JSONObject().apply {
                put("status", "connected")
            }
            onTvEventReceived?.invoke("TV_READY", tvReadyEvent)
        }

        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            synchronized(connectedSockets) {
                connectedSockets.remove(this)
            }
            Log.i("MBoxCastServer", "WebSocket closed: $reason")
            if (!isTvConnected()) onTvEventReceived?.invoke("TV_DISCONNECTED", JSONObject())
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                lastTvMessageAt = System.currentTimeMillis()
                val text = message.textPayload
                Log.i("MBoxCastServer", "WebSocket message received: $text")
                val json = JSONObject(text)
                val type = json.optString("type")
                if (type == "event") {
                    val event = json.optString("event")
                    val payload = json.optJSONObject("payload") ?: JSONObject()
                    onTvEventReceived?.invoke(event, payload)
                }
            } catch (e: Exception) {
                Log.e("MBoxCastServer", "Error parsing incoming WS message: ${e.message}")
            }
        }

        override fun onPong(pong: WebSocketFrame) {}

        override fun onException(exception: IOException) {
            Log.e("MBoxCastServer", "WebSocket exception: ${exception.message}")
        }
    }

    override fun serve(session: IHTTPSession?): Response {
        val fullUri = session?.uri ?: ""
        val path = fullUri.substringBefore("?").trimEnd('/')
        val queryStr = session?.queryParameterString ?: "(none)"
        val rangeHeader = session?.headers?.get("range") ?: "none"
        val method = session?.method?.name ?: "GET"

        if (session?.method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS, POST")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            Log.i("MBoxCastServer", "HTTP OPTIONS $path query:$queryStr Range:$rangeHeader -> 200 (OPTIONS Pre-flight)")
            return res
        }

        if (path == "/mbox/hello") {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val helloObj = JSONObject().apply {
                put("app", "m-box")
                put("version", 1)
                put("name", deviceModel)
                put("wsPath", "/cast-bridge")
                put("features", JSONArray().put("tvsafe"))
            }
            val res = newFixedLengthResponse(Response.Status.OK, "application/json", helloObj.toString())
            res.addHeader("Access-Control-Allow-Origin", "*")
            Log.i("MBoxCastServer", "HTTP GET /mbox/hello -> 200 OK (Hello JSON with tvsafe feature)")
            return res
        }

        if (path == "/cast-bridge") {
            val res = super.serve(session)
            res.addHeader("Access-Control-Allow-Origin", "*")
            Log.i("MBoxCastServer", "HTTP GET /cast-bridge -> 101 Switching Protocols (WebSocket Handshake)")
            return res
        }

        if (path.endsWith("/spectrum")) {
            return handleSpectrumEndpoint(session, path)
        }

        if (path.endsWith("/lyrics")) {
            return handleLyricsEndpoint(session, path)
        }

        if (path.endsWith("/prepare")) {
            return handlePrepareEndpoint(session, path, method, queryStr, rangeHeader)
        }

        if (path.startsWith("/art/") || path.contains("artwork")) {
            return handleArtworkEndpoint(session, path, method, queryStr, rangeHeader)
        }

        if (path.startsWith("/audio/") || path == "/audio" || path.contains("audio_stream")) {
            return handleAudioEndpoint(session, path, method, queryStr, rangeHeader)
        }

        val fallbackRes = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Endpoint not found: $path")
        fallbackRes.addHeader("Access-Control-Allow-Origin", "*")
        Log.w("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 404 Not Found (Endpoint not found)")
        return fallbackRes
    }

    private val trackMap = ConcurrentHashMap<String, Track>()
    private val trackLyricsMap = ConcurrentHashMap<String, String>()

    fun registerTrack(track: Track) {
        val sanitizedId = track.id.toString().replace(Regex("[^a-zA-Z0-9_]"), "_")
        trackMap[sanitizedId] = track
    }

    fun setTrackLyrics(trackId: String, lrcText: String) {
        val sanitizedId = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        if (lrcText.isNotBlank()) {
            trackLyricsMap[sanitizedId] = lrcText
        }
    }

    private fun handleLyricsEndpoint(session: IHTTPSession?, path: String): Response {
        val id = path.substringAfter("/audio/").substringBefore("/lyrics").trim()
        val text = trackLyricsMap[id] ?: ""
        val json = JSONObject().apply {
            put("trackId", id)
            put("text", text)
        }.toString()
        val res = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Cache-Control", "no-cache")
        Log.i("MBoxCast", "HTTP GET $path -> 200 OK (Served lyrics for ID $id, length ${text.length})")
        return res
    }

    private fun handleSpectrumEndpoint(session: IHTTPSession?, path: String): Response {
        val id = path.substringAfter("/audio/").substringBefore("/spectrum").trim()
        val cachedJson = SpectrumCalculator.getCachedSpectrumJson(id)
        if (cachedJson != null) {
            val res = newFixedLengthResponse(Response.Status.OK, "application/json", cachedJson)
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Cache-Control", "no-cache")
            Log.i("MBoxCast", "HTTP GET $path -> 200 OK (Served cached spectrum JSON for ID $id)")
            return res
        }

        val resolvedFile = resolveTrackFile(id)
        val track = trackMap[id] ?: if (resolvedFile != null) Track(
            id = id.toLongOrNull() ?: 0L,
            title = id,
            artist = "",
            album = "",
            duration = 0L,
            uri = resolvedFile.absolutePath,
            albumArtUri = null,
            mimeType = getMimeType(resolvedFile.name),
            sampleRate = 48000,
            bitrate = 320000,
            dateAdded = resolvedFile.lastModified(),
            fileSize = resolvedFile.length(),
            folderName = resolvedFile.parentFile?.name ?: "",
            folderPath = resolvedFile.parentFile?.absolutePath ?: "",
            trackNumber = 0,
            discNumber = 0,
            year = 0,
            genre = "",
            composer = "",
            comment = ""
        ) else null

        if (track != null) {
            val json = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    SpectrumCalculator.getOrComputeSpectrumJson(context, track)
                }
            }
            if (json != null) {
                val res = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                res.addHeader("Access-Control-Allow-Origin", "*")
                res.addHeader("Cache-Control", "no-cache")
                Log.i("MBoxCast", "HTTP GET $path -> 200 OK (Computed spectrum JSON for ID $id)")
                return res
            }
        }

        val res = newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Spectrum not ready\"}")
        res.addHeader("Access-Control-Allow-Origin", "*")
        Log.w("MBoxCast", "HTTP GET $path -> 404 Not Found (Spectrum not ready for ID $id)")
        return res
    }

    private fun handlePrepareEndpoint(session: IHTTPSession?, path: String, method: String, queryStr: String, rangeHeader: String): Response {
        val rateParam = session?.parms?.get("rate")?.toIntOrNull() ?: 48000
        val chParam = session?.parms?.get("ch")?.toIntOrNull() ?: 2
        val bitsParam = session?.parms?.get("bits")?.toIntOrNull() ?: 24

        val id = path.substringAfter("/audio/").substringBefore("/prepare")
        val resolvedFile = resolveTrackFile(id)

        if (resolvedFile == null || !resolvedFile.exists()) {
            val errBody = "Could not resolve audio file for track ID: $id"
            val res = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, errBody)
            res.addHeader("Access-Control-Allow-Origin", "*")
            Log.e("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 404 Not Found ($errBody)")
            return res
        }

        val cacheDir = File(context.cacheDir, "tv_prepare_cache").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "prepared_${id}_${rateParam}_${chParam}.flac")

        if (!cacheFile.exists() || cacheFile.length() <= 0) {
            val inputPath = resolvedFile.absolutePath
            val cmd = "-y -i \"$inputPath\" -map 0:a:0 -map_metadata -1 -af aresample=$rateParam:resampler=soxr:precision=28 -ac $chParam -sample_fmt s32 -bits_per_raw_sample $bitsParam -c:a flac \"${cacheFile.absolutePath}\""
            Log.i("MBoxCastServer", "Executing /prepare command: $cmd")
            val ffmpegSession = FFmpegKit.execute(cmd)
            if (!ReturnCode.isSuccess(ffmpegSession.returnCode)) {
                val res = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Transcode failed for ID $id")
                res.addHeader("Access-Control-Allow-Origin", "*")
                Log.e("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 500 Internal Error (FFmpegKit prepare failed)")
                return res
            }
        }

        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val resUrl = "http://$ip:$listeningPort/audio/$id?tv=1&rate=$rateParam&bits=$bitsParam&ch=$chParam"

        val resObj = JSONObject().apply {
            put("url", resUrl)
            put("sampleRate", rateParam)
            put("bitDepth", 24)
            put("channels", chParam)
        }

        val res = newFixedLengthResponse(Response.Status.OK, "application/json", resObj.toString())
        res.addHeader("Access-Control-Allow-Origin", "*")
        Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 200 OK (Prepared $resUrl)")
        return res
    }

    private fun handleAudioEndpoint(session: IHTTPSession?, path: String, method: String, queryStr: String, rangeHeader: String): Response {
        val id = path.substringAfter("/audio/").ifBlank { "default" }
        val isTv = session?.parms?.get("tv") == "1"
        val rateParam = session?.parms?.get("rate")?.toIntOrNull()
        val chParam = session?.parms?.get("ch")?.toIntOrNull()

        var targetFile: File? = null
        if (isTv && rateParam != null && chParam != null) {
            val cacheDir = File(context.cacheDir, "tv_prepare_cache")
            val preparedCacheFile = File(cacheDir, "prepared_${id}_${rateParam}_${chParam}.flac")
            if (preparedCacheFile.exists() && preparedCacheFile.length() > 0) {
                targetFile = preparedCacheFile
            }
        }

        if (targetFile == null) {
            targetFile = resolveTrackFile(id)
        }

        if (targetFile == null || !targetFile.exists()) {
            val errReason = "Track ID '$id' not found in database or disk"
            val res = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, errReason)
            res.addHeader("Access-Control-Allow-Origin", "*")
            Log.e("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 404 Not Found ($errReason)")
            return res
        }

        val fileLength = targetFile.length()
        val isFlac = targetFile.name.endsWith(".flac", true)

        if (isTv && isFlac) {
            val virtualHeader = flacHeaderCache.getOrPut(id) {
                parseFlacHeader(targetFile)
            }
            if (virtualHeader != null) {
                return serveVirtualFlac(session, virtualHeader, targetFile, method, path, queryStr, rangeHeader)
            }
        }

        val mimeType = if (isFlac) "audio/flac" else (activeAudioMime ?: getMimeType(targetFile.name))

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeStr = rangeHeader.removePrefix("bytes=").trim()
            val parts = rangeStr.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            var end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toLongOrNull() ?: (fileLength - 1) else (fileLength - 1)

            if (end >= fileLength) {
                end = fileLength - 1
            }
            val contentLength = (end - start + 1).coerceAtLeast(0L)

            val fis = FileInputStream(targetFile)
            fis.skip(start)

            val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, contentLength)
            res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 206 Partial Content (bytes $start-$end/$fileLength)")
            return res
        } else {
            val res = newFixedLengthResponse(Response.Status.OK, mimeType, FileInputStream(targetFile), fileLength)
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> 200 OK ($fileLength bytes)")
            return res
        }
    }

    private fun parseFlacHeader(file: File): FlacVirtualHeader? {
        try {
            val raf = RandomAccessFile(file, "r")
            val fileLength = file.length()
            if (fileLength < 42) {
                raf.close()
                return null
            }

            // 1. Check ID3 tag
            var offset = 0L
            val id3Header = ByteArray(10)
            raf.readFully(id3Header)

            if (id3Header[0] == 'I'.toByte() && id3Header[1] == 'D'.toByte() && id3Header[2] == '3'.toByte()) {
                val size = ((id3Header[6].toInt() and 0x7F) shl 21) or
                           ((id3Header[7].toInt() and 0x7F) shl 14) or
                           ((id3Header[8].toInt() and 0x7F) shl 7) or
                           (id3Header[9].toInt() and 0x7F)
                val hasFooter = (id3Header[5].toInt() and 0x10) != 0
                offset = 10L + size + (if (hasFooter) 10L else 0L)
                raf.seek(offset)
            } else {
                raf.seek(0)
                offset = 0L
            }

            // 2. Check "fLaC" marker
            val flacMarker = ByteArray(4)
            raf.readFully(flacMarker)
            if (flacMarker[0] != 'f'.toByte() || flacMarker[1] != 'L'.toByte() ||
                flacMarker[2] != 'a'.toByte() || flacMarker[3] != 'C'.toByte()) {
                raf.close()
                return null
            }

            var currPos = offset + 4L
            var streamInfoBytes: ByteArray? = null
            var lastBlock = false

            // 3. Walk all metadata blocks until lastBlock is passed
            while (!lastBlock && currPos + 4L <= fileLength) {
                raf.seek(currPos)
                val blockHeader = ByteArray(4)
                raf.readFully(blockHeader)

                val headerByte = blockHeader[0].toInt() and 0xFF
                lastBlock = (headerByte and 0x80) != 0
                val blockType = headerByte and 0x7F
                val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                             ((blockHeader[2].toInt() and 0xFF) shl 8) or
                             (blockHeader[3].toInt() and 0xFF)

                if (blockType == 0 && length == 34) {
                    streamInfoBytes = ByteArray(34)
                    raf.readFully(streamInfoBytes)
                }

                currPos += 4L + length.toLong()
            }

            val audioOffset = currPos

            // 4. Verify FLAC sync word at audioOffset (0xFF 0xF8 or 0xFF 0xF9)
            raf.seek(audioOffset)
            val syncBytes = ByteArray(2)
            val syncRead = raf.read(syncBytes)
            raf.close()

            val byte0 = if (syncRead >= 1) syncBytes[0].toInt() and 0xFF else 0
            val byte1 = if (syncRead >= 2) syncBytes[1].toInt() and 0xFF else 0

            Log.i(
                "MBoxCastServer",
                "FLAC parse result for ${file.name}: audioOffset=$audioOffset, syncBytes=0x${String.format("%02X %02X", byte0, byte1)}"
            )

            if (streamInfoBytes == null) {
                Log.e("MBoxCastServer", "STREAMINFO block not found in ${file.name}")
                return null
            }

            // 5. Construct 42-byte header ("fLaC" + 0x80 0x00 0x00 0x22 + 34 STREAMINFO bytes)
            val header42 = ByteArray(42)
            header42[0] = 'f'.toByte(); header42[1] = 'L'.toByte(); header42[2] = 'a'.toByte(); header42[3] = 'C'.toByte()
            header42[4] = 0x80.toByte(); header42[5] = 0x00.toByte(); header42[6] = 0x00.toByte(); header42[7] = 0x22.toByte()
            System.arraycopy(streamInfoBytes, 0, header42, 8, 34)

            return FlacVirtualHeader(header42, audioOffset, fileLength)
        } catch (e: Exception) {
            Log.e("MBoxCastServer", "Error parsing FLAC header for ${file.name}: ${e.message}", e)
            return null
        }
    }

    private fun serveVirtualFlac(session: IHTTPSession?, vh: FlacVirtualHeader, file: File, method: String, path: String, queryStr: String, rangeHeader: String): Response {
        val totalLength = vh.totalVirtualLength
        var start = 0L
        var end = totalLength - 1

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeStr = rangeHeader.removePrefix("bytes=").trim()
            val parts = rangeStr.split("-")
            start = parts[0].toLongOrNull() ?: 0L
            end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toLongOrNull() ?: (totalLength - 1) else (totalLength - 1)
            if (end >= totalLength) end = totalLength - 1
        }

        val contentLength = (end - start + 1).coerceAtLeast(0L)
        val virtualStream = object : InputStream() {
            private var currentVirtualPos = start

            override fun read(): Int {
                if (currentVirtualPos > end) return -1
                val b: Int = if (currentVirtualPos < 42L) {
                    vh.headerBytes[currentVirtualPos.toInt()].toInt() and 0xFF
                } else {
                    val filePos = vh.audioOffset + (currentVirtualPos - 42L)
                    val fis = FileInputStream(file)
                    fis.skip(filePos)
                    val readByte = fis.read()
                    fis.close()
                    readByte
                }
                currentVirtualPos++
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (currentVirtualPos > end) return -1
                val remainingInVirtual = (end - currentVirtualPos + 1).coerceAtLeast(0L)
                val toRead = minOf(len.toLong(), remainingInVirtual).toInt()
                if (toRead <= 0) return -1

                var bytesRead = 0
                if (currentVirtualPos < 42L) {
                    val headerAvailable = (42L - currentVirtualPos).toInt()
                    val headerChunk = minOf(toRead, headerAvailable)
                    System.arraycopy(vh.headerBytes, currentVirtualPos.toInt(), b, off, headerChunk)
                    currentVirtualPos += headerChunk
                    bytesRead += headerChunk
                }

                if (bytesRead < toRead && currentVirtualPos >= 42L) {
                    val filePos = vh.audioOffset + (currentVirtualPos - 42L)
                    val fileChunk = toRead - bytesRead
                    val fis = FileInputStream(file)
                    fis.skip(filePos)
                    val actualRead = fis.read(b, off + bytesRead, fileChunk)
                    fis.close()
                    if (actualRead > 0) {
                        currentVirtualPos += actualRead
                        bytesRead += actualRead
                    }
                }

                return if (bytesRead > 0) bytesRead else -1
            }
        }

        val status = if (rangeHeader != "none") Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val res = newFixedLengthResponse(status, "audio/flac", virtualStream, contentLength)
        if (status == Response.Status.PARTIAL_CONTENT) {
            res.addHeader("Content-Range", "bytes $start-$end/$totalLength")
        }
        res.addHeader("Accept-Ranges", "bytes")
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
        Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr Range:$rangeHeader -> ${status.requestStatus} (Virtual FLAC bytes $start-$end/$totalLength)")
        return res
    }

    private fun resolveTrackFile(id: String): File? {
        val cached = trackFileMap[id]
        if (cached != null && cached.exists()) return cached

        val active = activeAudioFile
        if (active != null && active.exists()) return active

        try {
            val longId = id.toLongOrNull()
            if (longId != null) {
                val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(
                    queryUri, projection,
                    "${MediaStore.Audio.Media._ID} = ?",
                    arrayOf(id), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val path = cursor.getString(0)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists()) {
                                trackFileMap[id] = f
                                return f
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MBoxCastServer", "MediaStore query error for ID $id: ${e.message}")
        }

        return null
    }

    private fun resolveMediaStoreArtUri(trackId: String): Uri? {
        val numericId = trackId.toLongOrNull() ?: return null
        try {
            val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(numericId.toString())

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                    if (albumId > 0) {
                        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                        return ContentUris.withAppendedId(sArtworkUri, albumId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MBoxCastServer", "Error querying albumId for trackId $trackId: ${e.message}")
        }
        return null
    }

    private fun handleArtworkEndpoint(session: IHTTPSession?, path: String, method: String, queryStr: String, rangeHeader: String): Response {
        val id = path.substringAfter("/art/").substringBefore("?").trim()
        val resolvedFile = resolveTrackFile(id)
        val resolvedUri = trackUriMap[id]

        val cacheKey = "${id}_${resolvedFile?.lastModified() ?: 0L}"
        val cachedBytes = artworkMemoryCache[cacheKey]

        if (cachedBytes != null) {
            val res = newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(cachedBytes), cachedBytes.size.toLong())
            res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr -> 200 OK (Cached artwork bytes for ID $id)")
            return res
        }

        var artBytes: ByteArray? = null

        // 1. Try embedded picture of that specific audio file first
        try {
            val retriever = MediaMetadataRetriever()
            if (resolvedFile != null && resolvedFile.exists()) {
                retriever.setDataSource(resolvedFile.absolutePath)
            } else if (resolvedUri != null) {
                retriever.setDataSource(context, resolvedUri)
            }
            artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes != null) {
                Log.i("MBoxCastServer", "Extracted embedded picture for track ID $id (${artBytes.size} bytes)")
            }
        } catch (e: Exception) {
            Log.w("MBoxCastServer", "Embedded picture extraction error for ID $id: ${e.message}")
        }

        // 2. Try album art URI for that song's albumId
        if (artBytes == null) {
            val artUri = trackArtUriMap[id] ?: resolveMediaStoreArtUri(id)
            if (artUri != null) {
                try {
                    if (artUri.scheme == "file") {
                        val artFile = File(artUri.path ?: "")
                        if (artFile.exists()) {
                            artBytes = artFile.readBytes()
                        }
                    } else {
                        context.contentResolver.openInputStream(artUri)?.use { stream ->
                            artBytes = stream.readBytes()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MBoxCastServer", "Artwork fetch error for ID $id from URI $artUri: ${e.message}")
                }
            }
        }

        // Serve art if found
        if (artBytes != null) {
            if (artworkMemoryCache.size > 100) artworkMemoryCache.clear()
            artworkMemoryCache[cacheKey] = artBytes

            val res = newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(artBytes), artBytes.size.toLong())
            res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            Log.i("MBoxCastServer", "HTTP $method $path query:$queryStr -> 200 OK (Served artwork for ID $id, size ${artBytes.size} bytes)")
            return res
        }

        // 3. Fallback: 404
        val res = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Artwork not found for ID $id")
        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
        Log.w("MBoxCastServer", "HTTP $method $path query:$queryStr -> 404 Not Found (Artwork not found for ID $id)")
        return res
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            else -> "audio/*"
        }
    }

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback || intf.isPointToPoint) continue
                    val name = intf.name.lowercase()
                    if (name.contains("wlan") || name.contains("eth") || name.contains("ap")) {
                        val addrs = Collections.list(intf.inetAddresses)
                        for (addr in addrs) {
                            if (!addr.isLoopbackAddress) {
                                val sAddr = addr.hostAddress ?: continue
                                if (sAddr.indexOf(':') < 0) {
                                    return sAddr
                                }
                            }
                        }
                    }
                }

                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress ?: continue
                            if (sAddr.indexOf(':') < 0) {
                                return sAddr
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MBoxCastServer", "Error finding IP address: ${e.message}")
            }
            return null
        }
    }

    fun getStreamUrl(track: Track): String? {
        registerTrack(track)
        val file = if (track.uri.startsWith("file://") || track.uri.startsWith("/")) {
            File(if (track.uri.startsWith("file://")) Uri.parse(track.uri).path ?: track.uri else track.uri)
        } else null
        val uri = Uri.parse(track.uri)
        return getStreamUrl(track.id.toString(), file, uri, track.mimeType)
    }

    fun getStreamUrl(trackId: String, file: File?, uri: Uri?, mimeType: String? = null): String? {
        val ip = getLocalIpAddress() ?: return null
        setAudioTrackSource(trackId, file, uri, mimeType)
        val sanitizedId = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "http://$ip:$listeningPort/audio/$sanitizedId"
    }

    fun getArtworkUrl(trackId: String, artUri: Uri?): String? {
        val sanitizedId = trackId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        if (artUri != null) {
            val scheme = artUri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return artUri.toString()
            setArtworkSource(sanitizedId, artUri)
        }
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$listeningPort/art/$sanitizedId"
    }
}
