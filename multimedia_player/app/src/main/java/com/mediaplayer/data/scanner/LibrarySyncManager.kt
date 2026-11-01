package com.mediaplayer.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mediaplayer.data.db.LibraryTrackDao
import com.mediaplayer.data.model.TrackEntity
import com.mediaplayer.data.network.lyrics.LrcLibApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
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
                    if (lyrics.isNullOrBlank()) {
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

    suspend fun syncLibraryFromUris(folderUris: Set<String>) = withContext(Dispatchers.IO) {
        val scannedTracks = mutableListOf<TrackEntity>()
        val retriever = MediaMetadataRetriever()

        folderUris.forEach { uriString ->
            val treeUri = Uri.parse(uriString)
            val audioFiles = scanFolderForAudio(context, treeUri)
            
            audioFiles.forEach { docFile ->
                try {
                    retriever.setDataSource(context, docFile.uri)
                    
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                        ?: docFile.name?.substringBeforeLast(".") ?: "Unknown"
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                    scannedTracks.add(
                        TrackEntity(
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = docFile.uri.toString(),
                            folderName = docFile.parentFile?.name ?: "Unknown",
                            hasSyncedLyrics = false
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
        fetchMissingLyricsFromServer()
    }

    private fun scanFolderForAudio(context: Context, treeUri: Uri): List<DocumentFile> {
        val audioFiles = mutableListOf<DocumentFile>()
        val rootFolder = DocumentFile.fromTreeUri(context, treeUri)
        
        fun recursiveScan(folder: DocumentFile) {
            folder.listFiles().forEach { file ->
                if (file.isDirectory) {
                    recursiveScan(file)
                } else {
                    val mimeType = file.type ?: ""
                    val name = file.name ?: ""
                    if (mimeType.startsWith("audio/") || name.endsWith(".flac", true) || 
                        name.endsWith(".mp3", true) || name.endsWith(".m4a", true) || 
                        name.endsWith(".wav", true) || name.endsWith(".opus", true)) {
                        audioFiles.add(file)
                    }
                }
            }
        }
        
        rootFolder?.let { recursiveScan(it) }
        return audioFiles
    }

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

    private suspend fun fetchMissingLyricsFromServer() = withContext(Dispatchers.IO) {
        val missingTracks = trackDao.getTracksMissingLyrics()

        for (track in missingTracks) {
            try {
                val cleanTitle = track.title.replace(Regex("\\(?\\b(From|OST|Original Motion Picture).*?\\)?", RegexOption.IGNORE_CASE), "").trim()
                val primaryArtist = track.artist.split(Regex("[,&]|\\bfeat\\b|\\bft\\b", RegexOption.IGNORE_CASE))[0].trim()
                val durationSec = (track.durationMs / 1000).toInt()

                var fetchedLyrics: String? = null

                try {
                    val response = lrcLibApi.getLyrics(cleanTitle, primaryArtist, track.album, durationSec)
                    if (response.isSuccessful) {
                        val data = response.body()
                        fetchedLyrics = data?.syncedLyrics ?: data?.plainLyrics
                    }
                } catch (e: Exception) { }

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
                    val isSynced = fetchedLyrics.contains(Regex("\\[\\d{2}:\\d{2}"))
                    trackDao.updateLyrics(track.id, fetchedLyrics, isSynced)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
