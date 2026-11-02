package com.mediaplayer.util

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.mediaplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object Eac3Transcoder {

    private const val TAG = "MBoxDolby"
    private const val MAX_CACHE_SIZE_BYTES = 2000L * 1024L * 1024L // 2 GB

    fun getDolbyInfo(context: Context, track: Track): DolbyInfo {
        return Eac3JocParser.parseTrack(context, track)
    }

    fun isDolbyTrack(context: Context, track: Track): Boolean {
        return getDolbyInfo(context, track).isDolby
    }

    fun hasNativeDolbyDecoder(isJoc: Boolean): Boolean {
        try {
            val mime = if (isJoc) "audio/eac3-joc" else "audio/eac3"
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(mime, ignoreCase = true) || type.equals("audio/eac3", ignoreCase = true)) {
                        Log.i(TAG, "Found native device Dolby decoder: ${info.name} for $mime")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MediaCodecList for Dolby decoder: ${e.message}")
        }
        return false
    }

    suspend fun getPlayableUri(context: Context, track: Track, onProgress: (String) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        val dolbyInfo = getDolbyInfo(context, track)
        if (!dolbyInfo.isDolby) {
            return@withContext track.uri
        }

        if (hasNativeDolbyDecoder(dolbyInfo.isJoc)) {
            Log.i(TAG, "Using Native Device Dolby Decoder for track '${track.title}' (isJoc=${dolbyInfo.isJoc}, objects=${dolbyInfo.atmosObjects})")
            return@withContext track.uri
        }

        Log.i(TAG, "Native device Dolby decoder not available or disabled. Using FFmpeg path for '${track.title}'")

        val cacheDir = File(context.cacheDir, "decoded_dolby_cache").apply { if (!exists()) mkdirs() }
        val inputUri = Uri.parse(track.uri)

        val ffmpegInputPath = try {
            if (track.uri.startsWith("content://")) {
                FFmpegKitConfig.getSafParameterForRead(context, inputUri)
            } else if (track.uri.startsWith("file://")) {
                inputUri.path ?: track.uri
            } else {
                track.uri
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving SAF parameter for ${track.uri}: ${t.message}", t)
            track.uri
        }

        var sizeBytes = track.fileSize
        val dateModified = track.dateAdded

        if (sizeBytes <= 0L && track.uri.startsWith("content://")) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                sizeBytes = pfd?.statSize ?: 0L
                pfd?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error reading statSize for URI $inputUri: ${e.message}")
            }
        }

        val fileKey = "${track.id}_${sizeBytes}_$dateModified"
        val outputFile = File(cacheDir, "dolby_flac_v3_$fileKey.flac")

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.i(TAG, "MBoxDolby Cache HIT: Path=${outputFile.absolutePath}, Size=${outputFile.length()} bytes")
            return@withContext "file://${outputFile.absolutePath}"
        }

        withContext(Dispatchers.Main) { onProgress("Preparing audio…") }

        val primaryCmd = "-y -drc_scale 0 -downmix stereo -i \"$ffmpegInputPath\" -map 0:a:0 -vn -sample_fmt s32 -c:a flac \"${outputFile.absolutePath}\""
        val fallbackCmd = "-y -drc_scale 0 -i \"$ffmpegInputPath\" -map 0:a:0 -vn -af \"pan=stereo|FL=FL+0.707*FC+0.707*SL|FR=FR+0.707*FC+0.707*SR\" -sample_fmt s32 -c:a flac \"${outputFile.absolutePath}\""

        Log.i(TAG, "FFmpeg START primaryCmd=$primaryCmd")

        return@withContext try {
            val startTime = System.currentTimeMillis()
            var session = FFmpegKit.execute(primaryCmd)
            var durationMs = System.currentTimeMillis() - startTime

            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.w(TAG, "FFmpeg -downmix failed (code=${session.returnCode}). Trying fallback pan filter...")
                val startTime2 = System.currentTimeMillis()
                session = FFmpegKit.execute(fallbackCmd)
                durationMs = System.currentTimeMillis() - startTime2
            }

            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "MBoxDolby FFmpeg DONE returnCode=${session.returnCode} timeMs=$durationMs outSize=${outputFile.length()} bytes")
                evictCacheIfNeeded(cacheDir)
                "file://${outputFile.absolutePath}"
            } else {
                val logs = runCatching { session.allLogsAsString }.getOrNull() ?: "No logs"
                val recentLogs = if (logs.length > 2000) logs.takeLast(2000) else logs
                Log.e(TAG, "FFmpegKit Transcode FAILED: returnCode=${session.returnCode}, timeMs=$durationMs\nLogs:\n$recentLogs")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't decode this Dolby track", Toast.LENGTH_LONG).show()
                }
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "FFmpegKit Throwable Error: ${t.javaClass.name}: ${t.message}", t)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Couldn't decode this Dolby track", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    private fun evictCacheIfNeeded(cacheDir: File) {
        try {
            val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var totalSize = files.sumOf { it.length() }
            for (file in files) {
                if (totalSize > MAX_CACHE_SIZE_BYTES) {
                    val size = file.length()
                    if (file.delete()) {
                        totalSize -= size
                        Log.i(TAG, "Evicted old Dolby FLAC cache file: ${file.name}")
                    }
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evicting LRU cache: ${e.message}")
        }
    }

    fun getCacheDiagnostics(context: Context, track: Track): String {
        return try {
            val cacheDir = File(context.cacheDir, "decoded_dolby_cache")
            val inputUri = Uri.parse(track.uri)
            var sizeBytes = track.fileSize
            val dateModified = track.dateAdded

            if (sizeBytes <= 0L && track.uri.startsWith("content://")) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                    sizeBytes = pfd?.statSize ?: 0L
                    pfd?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading statSize for URI $inputUri: ${e.message}")
                }
            }

            val fileKey = "${track.id}_${sizeBytes}_$dateModified"
            val outputFile = File(cacheDir, "dolby_flac_v3_$fileKey.flac")

            if (outputFile.exists() && outputFile.length() > 0) {
                "Cache HIT: Path=${outputFile.absolutePath}, Size=${outputFile.length()} bytes"
            } else {
                "Cache MISS: Path=${outputFile.absolutePath}, Exists=${outputFile.exists()}"
            }
        } catch (e: Exception) {
            "Cache Error: ${e.message}"
        }
    }

    fun purgeCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "decoded_dolby_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.i(TAG, "Cleared decoded Dolby FLAC cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging cache: ${e.message}")
        }
    }
}
