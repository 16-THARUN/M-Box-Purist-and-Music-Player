package com.mediaplayer.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.mediaplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileRelocationManager {

    suspend fun moveTracks(
        context: Context,
        tracks: List<Track>,
        destFolderUriOrPath: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var movedCount = 0
            val isDestTreeUri = destFolderUriOrPath.startsWith("content://")

            for (track in tracks) {
                val sourceUri = Uri.parse(track.uri)

                if (isDestTreeUri) {
                    val treeUri = Uri.parse(destFolderUriOrPath)
                    val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                    if (targetDir != null && targetDir.canWrite()) {
                        val sourceStream = context.contentResolver.openInputStream(sourceUri)
                        val fileName = track.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + "." + track.mimeType.substringAfterLast("/")
                        val newFile = targetDir.createFile(track.mimeType.ifBlank { "audio/mpeg" }, fileName)

                        if (sourceStream != null && newFile != null) {
                            val outStream = context.contentResolver.openOutputStream(newFile.uri)
                            if (outStream != null) {
                                sourceStream.copyTo(outStream)
                                outStream.flush()
                                outStream.close()
                                sourceStream.close()

                                try {
                                    context.contentResolver.delete(sourceUri, null, null)
                                } catch (e: Exception) {
                                    Log.w("FileRelocationManager", "Source delete failed: ${e.message}")
                                }

                                MediaScannerConnection.scanFile(context, arrayOf(sourceUri.path ?: "", newFile.uri.toString()), null, null)
                                movedCount++
                            }
                        }
                    }
                } else {
                    val destDir = File(destFolderUriOrPath)
                    if (!destDir.exists()) destDir.mkdirs()

                    val sourceFile = if (track.uri.startsWith("file://")) File(Uri.parse(track.uri).path ?: "") else File(track.uri)
                    if (sourceFile.exists()) {
                        val destFile = File(destDir, sourceFile.name)
                        if (sourceFile.renameTo(destFile)) {
                            MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath, destFile.absolutePath), null, null)
                            movedCount++
                        }
                    }
                }
            }

            if (movedCount > 0) {
                Result.success(movedCount)
            } else {
                Result.failure(Exception("No files could be moved"))
            }
        } catch (e: Exception) {
            Log.e("FileRelocationManager", "Error moving tracks: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun copyTracks(
        context: Context,
        tracks: List<Track>,
        destFolderUriOrPath: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var copiedCount = 0
            val isDestTreeUri = destFolderUriOrPath.startsWith("content://")

            for (track in tracks) {
                val sourceUri = Uri.parse(track.uri)

                if (isDestTreeUri) {
                    val treeUri = Uri.parse(destFolderUriOrPath)
                    val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                    if (targetDir != null && targetDir.canWrite()) {
                        val sourceStream = context.contentResolver.openInputStream(sourceUri)
                        val fileName = track.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + "." + track.mimeType.substringAfterLast("/")
                        val newFile = targetDir.createFile(track.mimeType.ifBlank { "audio/mpeg" }, fileName)

                        if (sourceStream != null && newFile != null) {
                            val outStream = context.contentResolver.openOutputStream(newFile.uri)
                            if (outStream != null) {
                                sourceStream.copyTo(outStream)
                                outStream.flush()
                                outStream.close()
                                sourceStream.close()

                                MediaScannerConnection.scanFile(context, arrayOf(newFile.uri.toString()), null, null)
                                copiedCount++
                            }
                        }
                    }
                } else {
                    val destDir = File(destFolderUriOrPath)
                    if (!destDir.exists()) destDir.mkdirs()

                    val sourceFile = if (track.uri.startsWith("file://")) File(Uri.parse(track.uri).path ?: "") else File(track.uri)
                    if (sourceFile.exists()) {
                        val destFile = File(destDir, sourceFile.name)
                        sourceFile.copyTo(destFile, overwrite = true)
                        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                        copiedCount++
                    }
                }
            }

            if (copiedCount > 0) {
                Result.success(copiedCount)
            } else {
                Result.failure(Exception("No files could be copied"))
            }
        } catch (e: Exception) {
            Log.e("FileRelocationManager", "Error copying tracks: ${e.message}", e)
            Result.failure(e)
        }
    }
}
