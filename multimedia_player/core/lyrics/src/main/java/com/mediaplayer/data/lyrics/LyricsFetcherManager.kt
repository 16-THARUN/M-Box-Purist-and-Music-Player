package com.mediaplayer.data.lyrics

import com.mediaplayer.data.repository.LyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsFetcherManager @Inject constructor(
    private val lyricsRepository: LyricsRepository
) {
    fun getSourcesMap(filePath: String = ""): Map<String, LyricsSource> {
        return mapOf(
            "embedded_tag" to EmbeddedTagSource(lyricsRepository, filePath),
            "lyrics_plus" to LyricsPlusSource(),
            "pax_senix" to PaxSenixSource(),
            "better_lyrics" to BetterLyricsSource(),
            "simp_music" to SimpMusicSource(),
            "kugou" to KuGouSource(),
            "lrclib" to LrcLibSource(lyricsRepository),
            "musixmatch" to MusixmatchSource(),
            "genius" to GeniusSource()
        )
    }

    suspend fun fetchMultiSourceLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        filePath: String = "",
        enabledSourceIds: List<String> = listOf("embedded_tag", "lyrics_plus", "pax_senix", "better_lyrics", "simp_music", "kugou", "lrclib", "musixmatch", "genius"),
        prioritizeSyllable: Boolean = false
    ): LyricsResult {
        val map = getSourcesMap(filePath)
        var bestLineSyncedResult: LyricsResult.LineSynced? = null

        for (sourceId in enabledSourceIds) {
            val source = map[sourceId] ?: continue
            val result = source.fetchLyrics(title, artist, album, durationMs)

            when (result) {
                is LyricsResult.SyllableSynced -> {
                    return result
                }
                is LyricsResult.LineSynced -> {
                    if (!prioritizeSyllable) {
                        return result
                    } else if (bestLineSyncedResult == null) {
                        bestLineSyncedResult = result
                    }
                }
                is LyricsResult.PlainText -> {
                    if (!prioritizeSyllable && bestLineSyncedResult == null) {
                        return result
                    }
                }
                is LyricsResult.NotFound -> {
                    // Continue to next source
                }
            }
        }

        return bestLineSyncedResult ?: LyricsResult.NotFound
    }
}
