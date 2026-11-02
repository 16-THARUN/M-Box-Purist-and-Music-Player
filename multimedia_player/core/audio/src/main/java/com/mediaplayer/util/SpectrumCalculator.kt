package com.mediaplayer.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.mediaplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object SpectrumCalculator {

    private const val TAG = "MBoxCast"
    private const val FPS = 20
    private const val BANDS = 32

    private val spectrumMemoryCache = ConcurrentHashMap<String, String>()
    private val calculationLocks = ConcurrentHashMap<String, Mutex>()

    fun getCachedSpectrumJson(trackId: String): String? {
        return spectrumMemoryCache[trackId]
    }

    suspend fun getOrComputeSpectrumJson(context: Context, track: Track): String? = withContext(Dispatchers.IO.limitedParallelism(1)) {
        val cacheKey = "${track.id}_${track.fileSize}_${track.dateAdded}"
        val cached = spectrumMemoryCache[track.id.toString()]
        if (cached != null) {
            return@withContext cached
        }

        val cacheDir = File(context.cacheDir, "spectrum_cache").apply { if (!exists()) mkdirs() }
        val diskCacheFile = File(cacheDir, "spec_$cacheKey.json")

        if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
            try {
                val jsonStr = diskCacheFile.readText()
                spectrumMemoryCache[track.id.toString()] = jsonStr
                Log.i(TAG, "Spectrum cache HIT (disk) for track ID ${track.id}")
                return@withContext jsonStr
            } catch (e: Exception) {
                Log.w(TAG, "Error reading disk spectrum cache: ${e.message}")
            }
        }

        val mutex = calculationLocks.getOrPut(track.id.toString()) { Mutex() }

        mutex.withLock {
            val reCheck = spectrumMemoryCache[track.id.toString()]
            if (reCheck != null) return@withContext reCheck

            val startTimeNanos = System.nanoTime()
            Log.i(TAG, "Starting spectrum computation for track '${track.title}' (ID: ${track.id})")

            val pcmFile = File(cacheDir, "temp_pcm_${track.id}.raw")
            try {
                val isDolby = Eac3Transcoder.isDolbyTrack(context, track)
                val inputPath = if (isDolby) {
                    val flacUri = Eac3Transcoder.getPlayableUri(context, track)
                    if (flacUri != null && flacUri.startsWith("file://")) {
                        Uri.parse(flacUri).path ?: track.uri
                    } else {
                        getFFmpegInputPath(context, track)
                    }
                } else {
                    getFFmpegInputPath(context, track)
                }

                val cmd = "-y -i \"$inputPath\" -map 0:a:0 -ac 1 -ar 16000 -f s16le \"${pcmFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)

                if (!ReturnCode.isSuccess(session.returnCode) || !pcmFile.exists()) {
                    Log.e(TAG, "FFmpeg PCM decode failed for spectrum calculation: code=${session.returnCode}")
                    return@withContext null
                }

                val pcmBytes = pcmFile.readBytes()
                pcmFile.delete()

                val sampleRate = 16000
                val samplesPerFrame = sampleRate / FPS
                val totalFrames = pcmBytes.size / 2 / samplesPerFrame

                if (totalFrames <= 0) return@withContext null

                val pcmShorts = ShortArray(pcmBytes.size / 2)
                for (i in pcmShorts.indices) {
                    val b1 = pcmBytes[i * 2].toInt() and 0xFF
                    val b2 = pcmBytes[i * 2 + 1].toInt() and 0xFF
                    pcmShorts[i] = ((b2 shl 8) or b1).toShort()
                }

                val spectrumData = ByteArray(totalFrames * BANDS)

                for (f in 0 until totalFrames) {
                    val frameStart = f * samplesPerFrame
                    val frameEnd = minOf(frameStart + samplesPerFrame, pcmShorts.size)

                    for (b in 0 until BANDS) {
                        val bandStep = maxOf(1, (samplesPerFrame / 2) / BANDS)
                        var sum = 0.0
                        val bStart = frameStart + (b * bandStep)
                        val bEnd = minOf(bStart + bandStep, frameEnd)

                        for (s in bStart until bEnd) {
                            sum += abs(pcmShorts[s].toDouble())
                        }
                        val avgAmp = if (bEnd > bStart) sum / (bEnd - bStart) else 0.0
                        val val255 = ((avgAmp / 32768.0) * 255.0 * 2.5).coerceIn(0.0, 255.0).toInt().toByte()
                        spectrumData[f * BANDS + b] = val255
                    }
                }

                val base64Data = Base64.encodeToString(spectrumData, Base64.NO_WRAP)
                val json = JSONObject().apply {
                    put("fps", FPS)
                    put("bands", BANDS)
                    put("data", base64Data)
                }.toString()

                diskCacheFile.writeText(json)
                spectrumMemoryCache[track.id.toString()] = json

                val elapsedMs = (System.nanoTime() - startTimeNanos) / 1_000_000L
                Log.i(TAG, "Spectrum computation DONE for '${track.title}': $totalFrames frames ($elapsedMs ms)")
                return@withContext json

            } catch (e: Exception) {
                Log.e(TAG, "Error computing spectrum for track ${track.id}: ${e.message}", e)
            } finally {
                if (pcmFile.exists()) pcmFile.delete()
            }
            null
        }
    }

    private fun getFFmpegInputPath(context: Context, track: Track): String {
        val uri = Uri.parse(track.uri)
        return try {
            if (track.uri.startsWith("content://")) {
                FFmpegKitConfig.getSafParameterForRead(context, uri)
            } else if (track.uri.startsWith("file://")) {
                uri.path ?: track.uri
            } else {
                track.uri
            }
        } catch (e: Exception) {
            track.uri
        }
    }
}
