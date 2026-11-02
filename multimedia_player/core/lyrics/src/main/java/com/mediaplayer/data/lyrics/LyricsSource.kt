package com.mediaplayer.data.lyrics

sealed class LyricsResult {
    data class SyllableSynced(val rawLrc: String, val sourceName: String) : LyricsResult()
    data class LineSynced(val rawLrc: String, val sourceName: String) : LyricsResult()
    data class PlainText(val text: String, val sourceName: String) : LyricsResult()
    object NotFound : LyricsResult()
}

interface LyricsSource {
    val id: String
    val displayName: String
    val description: String
    val providerType: String // "syllable" | "line" | "plain"

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): LyricsResult
}
