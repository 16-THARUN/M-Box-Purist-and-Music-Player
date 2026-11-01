package com.mediaplayer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun prepareMediaForCast(context: Context, originalFile: File): Uri = withContext(Dispatchers.IO) {
    if (!originalFile.exists() || originalFile.extension.lowercase() != "wav") {
        return@withContext Uri.fromFile(originalFile)
    }

    val mp3OutputFile = File(context.cacheDir, "cast_optimized_audio.mp3")
    if (mp3OutputFile.exists()) mp3OutputFile.delete()

    return@withContext try {
        val command = "-i \"${originalFile.absolutePath}\" -codec:a libmp3lame -b:a 320k \"${mp3OutputFile.absolutePath}\""
        val session = FFmpegKit.execute(command)

        if (ReturnCode.isSuccess(session.returnCode) && mp3OutputFile.exists()) {
            Uri.fromFile(mp3OutputFile)
        } else {
            Uri.fromFile(originalFile)
        }
    } catch (t: Throwable) {
        Log.e("MBoxAudio", "FFmpegKit Cast prepare Throwable error: ${t.javaClass.name}: ${t.message}", t)
        Uri.fromFile(originalFile)
    }
}
