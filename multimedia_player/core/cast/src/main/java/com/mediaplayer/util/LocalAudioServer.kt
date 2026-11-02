package com.mediaplayer.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.util.Collections

class LocalAudioServer(
    private val context: Context,
    private val listeningPort: Int = 8888
) : NanoHTTPD(listeningPort) {

    private var activeUri: Uri? = null
    private var activeFile: File? = null
    private var explicitMimeType: String? = null
    private var activeArtworkUri: Uri? = null

    init {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i("LocalAudioServer", "HTTP server started on port $listeningPort")
        } catch (e: Exception) {
            Log.e("LocalAudioServer", "Failed to start HTTP server: ${e.message}", e)
        }
    }

    fun setAudioSource(file: File, mimeType: String? = null) {
        activeFile = file
        activeUri = null
        explicitMimeType = mimeType
    }

    fun setAudioSource(uri: Uri, mimeType: String? = null) {
        if (uri.scheme == "file") {
            activeFile = File(uri.path ?: "")
            activeUri = null
        } else {
            activeUri = uri
            activeFile = null
        }
        explicitMimeType = mimeType
    }

    fun setArtworkSource(uri: Uri?) {
        activeArtworkUri = uri
    }

    override fun serve(session: IHTTPSession?): Response {
        if (session?.method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            res.addHeader("Access-Control-Allow-Origin", "*")
            res.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            res.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
            return res
        }

        val response = handleServe(session)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
        return response
    }

    private fun handleServe(session: IHTTPSession?): Response {
        val uriPath = session?.uri ?: ""
        if (uriPath.contains("artwork")) {
            val artUri = activeArtworkUri
            if (artUri != null) {
                if (artUri.scheme == "file") {
                    val artFile = File(artUri.path ?: "")
                    if (artFile.exists()) {
                        val mimeType = if (artFile.name.endsWith(".png", true)) "image/png" else "image/jpeg"
                        Log.i("LocalAudioServer", "Serving local artwork File: ${artFile.absolutePath} (${artFile.length()} bytes)")
                        val res = newFixedLengthResponse(Response.Status.OK, mimeType, FileInputStream(artFile), artFile.length())
                        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                        res.addHeader("Pragma", "no-cache")
                        res.addHeader("Expires", "0")
                        return res
                    }
                }
                try {
                    val inputStream = context.contentResolver.openInputStream(artUri)
                    val pfd = context.contentResolver.openFileDescriptor(artUri, "r")
                    val size = pfd?.statSize ?: -1L
                    pfd?.close()

                    if (inputStream != null) {
                        val mimeType = context.contentResolver.getType(artUri) ?: "image/jpeg"
                        Log.i("LocalAudioServer", "Serving content artwork Uri: $artUri ($size bytes)")
                        val res = if (size > 0) {
                            newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, size)
                        } else {
                            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
                        }
                        res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                        res.addHeader("Pragma", "no-cache")
                        res.addHeader("Expires", "0")
                        return res
                    }
                } catch (e: Exception) {
                    Log.e("LocalAudioServer", "Content artwork Uri failed ($artUri): ${e.message}, trying embedded picture fallback")
                }
            }

            val trackUri = activeUri
            val trackFile = activeFile
            try {
                val retriever = MediaMetadataRetriever()
                if (trackFile != null && trackFile.exists()) {
                    retriever.setDataSource(trackFile.absolutePath)
                } else if (trackUri != null) {
                    retriever.setDataSource(context, trackUri)
                }
                val picBytes = retriever.embeddedPicture
                retriever.release()

                if (picBytes != null) {
                    Log.i("LocalAudioServer", "Serving embedded picture fallback (${picBytes.size} bytes)")
                    val res = newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(picBytes), picBytes.size.toLong())
                    res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                    res.addHeader("Pragma", "no-cache")
                    res.addHeader("Expires", "0")
                    return res
                }
            } catch (e: Exception) {
                Log.e("LocalAudioServer", "Error serving embedded picture fallback: ${e.message}")
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Artwork not found")
        }

        val file = activeFile
        if (file != null && file.exists()) {
            val mimeType = explicitMimeType ?: getMimeType(file.name)
            return newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                FileInputStream(file),
                file.length()
            )
        }

        val uri = activeUri
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val size = pfd?.statSize ?: -1L
                pfd?.close()

                if (inputStream != null) {
                    val mimeType = explicitMimeType
                        ?: context.contentResolver.getType(uri)
                        ?: "audio/mpeg"
                    return if (size > 0) {
                        newFixedLengthResponse(
                            Response.Status.OK,
                            mimeType,
                            inputStream,
                            size
                        )
                    } else {
                        newChunkedResponse(Response.Status.OK, mimeType, inputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalAudioServer", "Error serving content Uri $uri: ${e.message}")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
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
                                Log.i("LocalAudioServer", "Found interface $name IPv4: $sAddr")
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
                        if (sAddr.indexOf(':') < 0 && !sAddr.startsWith("10.0.2.")) {
                            Log.i("LocalAudioServer", "Fallback interface ${intf.name} IPv4: $sAddr")
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalAudioServer", "Error finding IP address: ${e.message}")
        }
        return null
    }

    fun getStreamUrl(file: File, mimeType: String? = null): String? {
        val ip = getLocalIpAddress() ?: return null
        setAudioSource(file, mimeType)
        val url = "http://$ip:$listeningPort/audio.${file.extension}"
        Log.i("LocalAudioServer", "Serving $file over local HTTP: $url")
        return url
    }

    fun getStreamUrl(uri: Uri, mimeType: String? = null): String? {
        val ip = getLocalIpAddress() ?: return null
        setAudioSource(uri, mimeType)
        val url = "http://$ip:$listeningPort/audio_stream"
        Log.i("LocalAudioServer", "Serving content Uri $uri over local HTTP: $url")
        return url
    }

    fun getArtworkUrl(artUriStr: String?, trackKey: String = System.currentTimeMillis().toString()): String? {
        if (artUriStr.isNullOrBlank()) return null
        val uri = Uri.parse(artUriStr)
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return artUriStr
        }
        val ip = getLocalIpAddress() ?: return null
        setArtworkSource(uri)
        val sanitizedKey = trackKey.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val url = "http://$ip:$listeningPort/artwork_$sanitizedKey.jpg"
        Log.i("LocalAudioServer", "Serving artwork $uri over unique local HTTP URL: $url")
        return url
    }
}
