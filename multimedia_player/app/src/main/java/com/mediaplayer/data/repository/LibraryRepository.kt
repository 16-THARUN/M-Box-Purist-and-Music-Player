package com.mediaplayer.data.repository

import android.media.MediaMetadataRetriever
import com.mediaplayer.data.db.LibraryTrackDao
import com.mediaplayer.data.model.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val trackDao: LibraryTrackDao
) {

    // The ViewModel will observe this Flow and pass it to Compose
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()

    // Run this on a background thread when the user clicks "Sync Library"
    suspend fun syncLibraryFromDirectory(rootDirectory: File) {
        withContext(Dispatchers.IO) {
            val trackList = mutableListOf<TrackEntity>()
            val retriever = MediaMetadataRetriever()

            // Recursively walk through the directory looking for audio files
            rootDirectory.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in listOf("flac", "mp3", "m4a", "wav")) {
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                        trackList.add(
                            TrackEntity(
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                filePath = file.absolutePath,
                                folderName = file.parentFile?.name ?: "Unknown"
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            // Clear old cache and insert fresh data
            trackDao.clearLibrary()
            trackDao.insertAll(trackList)
            retriever.release()
        }
    }
}
