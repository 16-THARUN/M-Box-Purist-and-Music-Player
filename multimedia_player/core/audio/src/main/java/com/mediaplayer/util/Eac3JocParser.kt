package com.mediaplayer.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.mediaplayer.data.model.Track
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class DolbyInfo(
    val isDolby: Boolean,
    val isJoc: Boolean,
    val codecName: String,
    val atmosObjects: Int? = null,
    val channels: Int = 6,
    val sampleRate: Int = 48000,
    val bitrateKbps: Int = 768
)

object Eac3JocParser {

    private const val TAG = "MBoxDolby"
    private val dolbyCache = ConcurrentHashMap<String, DolbyInfo>()

    fun parseTrack(context: Context, track: Track): DolbyInfo {
        val cacheKey = "${track.id}_${track.fileSize}_${track.dateAdded}"
        val cached = dolbyCache[cacheKey]
        if (cached != null) {
            return cached
        }

        val mimeLower = track.mimeType.lowercase()
        val uriLower = track.uri.lowercase()

        if (!mimeLower.contains("eac3") && !mimeLower.contains("ac3") && !mimeLower.contains("ec3") &&
            !uriLower.contains("eac3") && !uriLower.contains("ac3") && !uriLower.contains("ec3")
        ) {
            val nonDolby = DolbyInfo(isDolby = false, isJoc = false, codecName = "None")
            dolbyCache[cacheKey] = nonDolby
            return nonDolby
        }

        Log.i("MBoxScan", "Probing Dolby format for track '${track.title}' (ID: ${track.id})")

        val inputUri = Uri.parse(track.uri)
        var isDolby = false
        var isJoc = false
        var codecName = "None"
        var atmosObjects: Int? = null
        var channels = 6
        var sampleRate = if (track.sampleRate > 0) track.sampleRate else 48000
        val bitrate = if (track.bitrate > 0 && track.bitrate < 5000000) track.bitrate / 1000 else 768

        try {
            val extractor = MediaExtractor()
            if (track.uri.startsWith("content://")) {
                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                if (pfd != null) {
                    extractor.setDataSource(pfd.fileDescriptor)
                    pfd.close()
                } else {
                    extractor.setDataSource(context, inputUri, null)
                }
            } else {
                val filePath = if (track.uri.startsWith("file://")) inputUri.path ?: track.uri else track.uri
                val file = File(filePath)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                } else {
                    extractor.setDataSource(context, inputUri, null)
                }
            }

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }

                    if (mime.contains("eac3-joc", ignoreCase = true)) {
                        isDolby = true
                        isJoc = true
                        codecName = "E-AC3 JOC"
                        atmosObjects = 16
                        break
                    } else if (mime.contains("eac3", ignoreCase = true) || mime.contains("ec-3", ignoreCase = true) || mime.contains("ec3", ignoreCase = true)) {
                        isDolby = true
                        codecName = "E-AC3"
                        break
                    } else if (mime.contains("ac3", ignoreCase = true)) {
                        isDolby = true
                        codecName = "AC-3"
                        break
                    }
                }
            }
            try { extractor.release() } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor probe error: ${e.message}")
        }

        if (isDolby && codecName == "E-AC3") {
            val mp4JocInfo = parseMp4Dec3Box(context, track)
            if (mp4JocInfo != null && mp4JocInfo.isJoc) {
                isJoc = true
                codecName = "E-AC3 JOC"
                atmosObjects = mp4JocInfo.atmosObjects
            }
        }

        if (!isDolby) {
            val path = track.uri.lowercase()
            if (mimeLower.contains("eac3") || mimeLower.contains("ac3") || path.contains("eac3")) {
                isDolby = true
                val mp4JocInfo = parseMp4Dec3Box(context, track)
                if (mp4JocInfo != null && mp4JocInfo.isJoc) {
                    isJoc = true
                    codecName = "E-AC3 JOC"
                    atmosObjects = mp4JocInfo.atmosObjects
                } else {
                    codecName = "E-AC3"
                }
            }
        }

        val info = DolbyInfo(
            isDolby = isDolby,
            isJoc = isJoc,
            codecName = if (isDolby) codecName else "None",
            atmosObjects = if (isJoc) (atmosObjects ?: 16) else null,
            channels = channels,
            sampleRate = sampleRate,
            bitrateKbps = bitrate
        )

        dolbyCache[cacheKey] = info
        Log.i(TAG, "Dolby Probe Result: $info for track '${track.title}' (${track.uri})")
        return info
    }

    private fun parseMp4Dec3Box(context: Context, track: Track): DolbyInfo? {
        try {
            val inputStream: InputStream? = if (track.uri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(track.uri))
            } else {
                val path = if (track.uri.startsWith("file://")) Uri.parse(track.uri).path ?: track.uri else track.uri
                val file = File(path)
                if (file.exists()) FileInputStream(file) else null
            }

            if (inputStream == null) return null

            inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val read = stream.read(buffer, 0, buffer.size)
                if (read < 16) return null

                for (i in 0 until (read - 12)) {
                    if (buffer[i] == 0x64.toByte() &&
                        buffer[i + 1] == 0x65.toByte() &&
                        buffer[i + 2] == 0x63.toByte() &&
                        buffer[i + 3] == 0x33.toByte()
                    ) {
                        val boxOffset = i + 4
                        val b2 = buffer[boxOffset + 2].toInt() and 0xFF
                        val b3 = buffer[boxOffset + 3].toInt() and 0xFF

                        val flagTypeA = (b2 and 0x01)
                        val complexityIndex = b3 and 0xFF

                        if (flagTypeA != 0) {
                            val objects = if (complexityIndex in 1..32) complexityIndex else 16
                            Log.i(TAG, "Parsed MP4 dec3 box: JOC Atmos extension = true, objects = $objects")
                            return DolbyInfo(
                                isDolby = true,
                                isJoc = true,
                                codecName = "E-AC3 JOC",
                                atmosObjects = objects
                            )
                        } else {
                            Log.i(TAG, "Parsed MP4 dec3 box: JOC Atmos extension = false")
                            return DolbyInfo(
                                isDolby = true,
                                isJoc = false,
                                codecName = "E-AC3",
                                atmosObjects = null
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing MP4 dec3 box: ${e.message}")
        }
        return null
    }
}
