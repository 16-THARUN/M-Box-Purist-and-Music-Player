package com.mediaplayer.data.scanner

import android.media.MediaMetadataRetriever
import com.mediaplayer.data.db.LibraryTrackDao
import com.mediaplayer.data.model.TrackEntity
import com.mediaplayer.data.network.lyrics.LrcLibApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncManager @Inject constructor(
    private val trackDao: LibraryTrackDao,
    private val lrcLibApi: LrcLibApiService
) {

    // Step 1: Scan local files, check local/embedded lyrics, and save to Room
    suspend fun fullLibraryScan(rootFolder: File) = withContext(Dispatchers.IO) {
        val scannedTracks = mutableListOf<TrackEntity>()
        val retriever = MediaMetadataRetriever()

        rootFolder.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.lowercase() in listOf("flac", "mp3", "m4a", "wav")) {
                try {
                    retriever.setDataSource(file.absolutePath)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                    // Check for local .lrc file first
                    var lyrics: String? = getLocalLrcContent(file.absolutePath)
                    
                    // Fall back to embedded ID3 lyrics tag if no .lrc file exists
                    // Note: Standard MediaMetadataRetriever doesn't always have a constant for lyrics.
                    // We check if it can be extracted via a custom key or if the platform supports it.
                    if (lyrics.isNullOrBlank()) {
                        // METADATA_KEY_LYRICS is not standard in all Android versions, using 34 as a common value for USLT
                        lyrics = retriever.extractMetadata(34) 
                    }

                    scannedTracks.add(
                        TrackEntity(
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = file.absolutePath,
                            folderName = file.parentFile?.name ?: "Unknown",
                            lyrics = lyrics,
                            hasSyncedLyrics = lyrics?.contains(Regex("\\[\\d{2}:\\d{2}")) == true
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        retriever.release()
        trackDao.clearLibrary()
        trackDao.insertAll(scannedTracks)

        // Step 2: Trigger background server sync for missing lyrics
        fetchMissingLyricsFromServer()
    }

    // Helper: Read adjacent .lrc file from storage
    private fun getLocalLrcContent(filePath: String): String? {
        val dotIndex = filePath.lastIndexOf('.')
        if (dotIndex != -1) {
            val lrcFile = File(filePath.substring(0, dotIndex) + ".lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                return lrcFile.readText().trim().ifEmpty { null }
            }
        }
        return null
    }

    // Step 2: Background Server Fetch for missing tracks
    private suspend fun fetchMissingLyricsFromServer() = withContext(Dispatchers.IO) {
        val missingTracks = trackDao.getTracksMissingLyrics()

        for (track in missingTracks) {
            try {
                val cleanTitle = track.title.replace(Regex("\\(?\\b(From|OST|Original Motion Picture).*?\\)?", RegexOption.IGNORE_CASE), "").trim()
                val primaryArtist = track.artist.split(Regex("[,&]|\\bfeat\\b|\\bft\\b", RegexOption.IGNORE_CASE))[0].trim()
                val durationSec = (track.durationMs / 1000).toInt()

                var fetchedLyrics: String? = null

                // Try strict match
                try {
                    val response = lrcLibApi.getLyrics(cleanTitle, primaryArtist, track.album, durationSec)
                    if (response.isSuccessful) {
                        val data = response.body()
                        fetchedLyrics = data?.syncedLyrics ?: data?.plainLyrics
                    }
                } catch (e: Exception) { }

                // Fall back to search
                if (fetchedLyrics.isNullOrBlank()) {
                    try {
                        val response = lrcLibApi.searchLyrics("$cleanTitle $primaryArtist")
                        if (response.isSuccessful) {
                            val results = response.body()
                            val bestMatch = results?.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                            fetchedLyrics = bestMatch?.syncedLyrics ?: bestMatch?.plainLyrics
                        }
                    } catch (e: Exception) { }
                }

                if (!fetchedLyrics.isNullOrBlank()) {
                    // Save to disk as .lrc file
                    val dotIndex = track.filePath.lastIndexOf('.')
                    if (dotIndex != -1) {
                        val lrcFile = File(track.filePath.substring(0, dotIndex) + ".lrc")
                        lrcFile.writeText(fetchedLyrics)
                    }

                    // Update Room DB (UI updates instantly via Flow)
                    val isSynced = fetchedLyrics.contains(Regex("\\[\\d{2}:\\d{2}"))
                    trackDao.updateLyrics(track.id, fetchedLyrics, isSynced)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
