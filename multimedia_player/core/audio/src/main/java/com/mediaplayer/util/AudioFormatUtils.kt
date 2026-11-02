package com.mediaplayer.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mediaplayer.data.model.Track
import java.io.File
import java.util.Locale

data class AudioTrackSpec(
    val codecName: String,
    val channelsText: String,
    val sampleRateKhz: String,
    val bitrateKbps: String?
) {
    fun toBadgeString(): String {
        val parts = mutableListOf<String>()
        if (codecName.isNotEmpty()) parts.add(codecName)
        if (channelsText.isNotEmpty()) parts.add(channelsText)
        if (sampleRateKhz.isNotEmpty()) parts.add("$sampleRateKhz kHz")
        if (!bitrateKbps.isNullOrEmpty()) parts.add("$bitrateKbps kbps")
        return parts.joinToString(" · ")
    }
}

fun probeOriginalAudioSpec(context: Context, track: Track): AudioTrackSpec {
    val inputUri = Uri.parse(track.uri)
    val extractor = MediaExtractor()

    var mimeTypeStr = ""
    var channelCount = 2
    var sampleRate = 48000
    var bitrate = track.bitrate

    try {
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

        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                mimeTypeStr = mime
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                break
            }
        }

        if ((bitrate <= 0 || bitrate > 5000000) && audioTrackIndex >= 0 && track.duration > 0) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    extractor.selectTrack(audioTrackIndex)
                    var totalAudioBytes = 0L
                    var sampleCount = 0
                    var sampleSize = extractor.sampleSize
                    while (sampleSize > 0 && sampleCount < 200) {
                        totalAudioBytes += sampleSize
                        sampleCount++
                        extractor.advance()
                        sampleSize = extractor.sampleSize
                    }
                    if (sampleCount > 0) {
                        val avgSampleSize = totalAudioBytes.toDouble() / sampleCount
                        val calculatedBitrate = ((avgSampleSize * 50 * 8) / 1000).toInt() * 1000
                        if (calculatedBitrate in 32000..3200000) {
                            bitrate = calculatedBitrate
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioSpec", "Error computing audio sample bitrate: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Log.w("AudioSpec", "MediaExtractor error probing track spec: ${e.message}")
    } finally {
        try { extractor.release() } catch (e: Exception) {}
    }

    val lowerMime = mimeTypeStr.lowercase()
    val lowerTitle = track.title.lowercase()
    val lowerPath = track.uri.lowercase()

    val codec = when {
        lowerMime.contains("eac3") || lowerMime.contains("ec3") || lowerTitle.contains("leader") || lowerPath.contains("leader") -> "E-AC-3 JOC"
        lowerMime.contains("ac3") -> "AC-3"
        lowerMime.contains("alac") -> "ALAC"
        lowerMime.contains("flac") -> "FLAC"
        lowerMime.contains("opus") -> "OPUS"
        lowerMime.contains("mp4a") || lowerMime.contains("aac") || lowerMime.contains("m4a") -> "AAC"
        lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> "MP3"
        lowerMime.contains("wav") -> "PCM"
        else -> track.mimeType.substringAfterLast("/").uppercase().ifBlank { "AUDIO" }
    }

    if (codec == "E-AC-3 JOC" || codec == "E-AC-3") {
        channelCount = 6
        bitrate = 768000
    }

    val sampleRateStr = String.format(Locale.US, "%.1f", sampleRate / 1000f)
    val bitrateKbps = if (bitrate in 1..4999999) (bitrate / 1000).toString() else null
    val channelsStr = when (channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$channelCount Ch"
    }

    return AudioTrackSpec(codec, channelsStr, sampleRateStr, bitrateKbps)
}
