package com.mediaplayer.data.repository

import com.mediaplayer.data.network.lyrics.LrcLibApiService
import com.mediaplayer.data.network.lyrics.LrcLibResponse
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val apiService: LrcLibApiService
) {
    /**
     * Attempts to read lyrics from a local .lrc file or embedded metadata.
     */
    fun getLocalOrEmbeddedLyrics(filePath: String): String? {
        try {
            // Step A: Check for adjacent .lrc file in the same folder
            val dotIndex = filePath.lastIndexOf('.')
            if (dotIndex != -1) {
                val lrcFilePath = filePath.substring(0, dotIndex) + ".lrc"
                val lrcFile = File(lrcFilePath)
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val text = lrcFile.readText().trim()
                    if (text.isNotEmpty()) return text
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Saves lyrics to a local .lrc file next to the audio file.
     */
    fun saveLocalLrcFile(filePath: String, lyricsContent: String): Boolean {
        return try {
            val dotIndex = filePath.lastIndexOf('.')
            if (dotIndex == -1) return false
            val lrcFilePath = filePath.substring(0, dotIndex) + ".lrc"
            val lrcFile = File(lrcFilePath)
            lrcFile.writeText(lyricsContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fetches lyrics from LRCLIB API safely using Retrofit.
     * Wrapped in Response<T> to handle 404s without throwing exceptions.
     */
    suspend fun fetchLyricsStrict(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): LrcLibResponse? {
        return try {
            val durationSec = (durationMs / 1000).toInt()
            val response = apiService.getLyrics(title, artist, album, durationSec)
            
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Performs a fuzzy search for lyrics when strict matching fails.
     */
    suspend fun searchLyricsFuzzy(query: String): LrcLibResponse? {
        return try {
            val response = apiService.searchLyrics(query = query)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                // Return the first match as the best candidate
                response.body()?.firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
