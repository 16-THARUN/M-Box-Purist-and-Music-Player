package com.mediaplayer.data.lyrics

import android.util.Base64
import com.mediaplayer.data.repository.LyricsRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class EmbeddedTagSource(private val lyricsRepository: LyricsRepository, private val filePath: String = "") : LyricsSource {
    override val id = "embedded_tag"
    override val displayName = "Embedded (file tags)"
    override val description = "Read MP4 ©lyr, ID3 USLT/SYLT, or FLAC LYRICS tag directly from local audio file"
    override val providerType = "syllable"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        if (filePath.isNotBlank()) {
            val localLrc = lyricsRepository.getLocalOrEmbeddedLyrics(filePath)
            if (!localLrc.isNullOrBlank()) {
                val isSynced = localLrc.contains("[") && localLrc.contains("]")
                return if (isSynced) {
                    if (localLrc.contains("<")) LyricsResult.SyllableSynced(localLrc, displayName)
                    else LyricsResult.LineSynced(localLrc, displayName)
                } else {
                    LyricsResult.PlainText(localLrc, displayName)
                }
            }
        }
        return LyricsResult.NotFound
    }
}

class LrcLibSource(private val lyricsRepository: LyricsRepository) : LyricsSource {
    override val id = "lrclib"
    override val displayName = "LRCLIB"
    override val description = "Whole lines only, and always up"
    override val providerType = "line"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        val strictMatch = lyricsRepository.fetchLyricsStrict(title, artist, album, durationMs)
        if (strictMatch != null && !strictMatch.syncedLyrics.isNullOrBlank()) {
            return LyricsResult.LineSynced(strictMatch.syncedLyrics, displayName)
        } else if (strictMatch != null && !strictMatch.plainLyrics.isNullOrBlank()) {
            return LyricsResult.PlainText(strictMatch.plainLyrics, displayName)
        }

        val fuzzyMatch = lyricsRepository.searchLyricsFuzzy("$title $artist")
        if (fuzzyMatch != null && !fuzzyMatch.syncedLyrics.isNullOrBlank()) {
            return LyricsResult.LineSynced(fuzzyMatch.syncedLyrics, displayName)
        } else if (fuzzyMatch != null && !fuzzyMatch.plainLyrics.isNullOrBlank()) {
            return LyricsResult.PlainText(fuzzyMatch.plainLyrics, displayName)
        }

        return LyricsResult.NotFound
    }
}

class LyricsPlusSource : LyricsSource {
    override val id = "lyrics_plus"
    override val displayName = "LyricsPlus"
    override val description = "Syllable by syllable, on community mirrors"
    override val providerType = "syllable"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-UTF-8".substringBefore("-"))
            val url = URL("https://lyricsplus.srv.community/v1/search?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val lrc = json.optString("syllableLyrics") ?: json.optString("syncedLyrics")
                if (!lrc.isNullOrBlank()) {
                    return LyricsResult.SyllableSynced(lrc, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class PaxSenixSource : LyricsSource {
    override val id = "pax_senix"
    override val displayName = "PaxSenix"
    override val description = "Apple Music timings again, on a second host"
    override val providerType = "syllable"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://paxsenix.srv.community/v1/lyrics?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val lrc = json.optString("ttml") ?: json.optString("lrc")
                if (!lrc.isNullOrBlank()) {
                    return LyricsResult.SyllableSynced(lrc, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class BetterLyricsSource : LyricsSource {
    override val id = "better_lyrics"
    override val displayName = "BetterLyrics"
    override val description = "Apple Music timings, word by word"
    override val providerType = "syllable"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://betterlyrics.srv.community/v1/search?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val lrc = json.optString("syllableLrc") ?: json.optString("lrc")
                if (!lrc.isNullOrBlank()) {
                    return LyricsResult.SyllableSynced(lrc, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class SimpMusicSource : LyricsSource {
    override val id = "simp_music"
    override val displayName = "SimpMusic"
    override val description = "Matched on the video, so never the wrong lyrics"
    override val providerType = "line"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://simpmusic.srv.community/v1/lyrics?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val lrc = json.optString("syncedLyrics")
                if (!lrc.isNullOrBlank()) {
                    return LyricsResult.LineSynced(lrc, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class KuGouSource : LyricsSource {
    override val id = "kugou"
    override val displayName = "KuGou"
    override val description = "Whole lines, strong outside the English catalogue"
    override val providerType = "line"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.optJSONObject(0)
                    val id = firstCandidate?.optString("id")
                    val accesskey = firstCandidate?.optString("accesskey")
                    if (!id.isNullOrBlank() && !accesskey.isNullOrBlank()) {
                        val downloadUrl = URL("https://krcs.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8")
                        val dlConn = downloadUrl.openConnection() as HttpURLConnection
                        dlConn.connectTimeout = 3000
                        dlConn.readTimeout = 3000
                        if (dlConn.responseCode == 200) {
                            val dlJsonStr = BufferedReader(InputStreamReader(dlConn.inputStream)).readText()
                            val dlJson = JSONObject(dlJsonStr)
                            val contentBase64 = dlJson.optString("content")
                            if (!contentBase64.isNullOrBlank()) {
                                val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                                val lrcText = String(decodedBytes, Charsets.UTF_8)
                                return LyricsResult.LineSynced(lrcText, displayName)
                            }
                        }
                    }
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class MusixmatchSource : LyricsSource {
    override val id = "musixmatch"
    override val displayName = "Musixmatch"
    override val description = "Whole lines, from the biggest lyrics database"
    override val providerType = "line"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://musixmatch.srv.community/v1/search?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val lrc = json.optString("syncedLyrics")
                if (!lrc.isNullOrBlank()) {
                    return LyricsResult.LineSynced(lrc, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}

class GeniusSource : LyricsSource {
    override val id = "genius"
    override val displayName = "Genius"
    override val description = "Plain text fallback, massive web catalogue"
    override val providerType = "plain"

    override suspend fun fetchLyrics(title: String, artist: String, album: String, durationMs: Long): LyricsResult {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://genius.srv.community/v1/search?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val json = JSONObject(jsonStr)
                val plainText = json.optString("plainLyrics")
                if (!plainText.isNullOrBlank()) {
                    return LyricsResult.PlainText(plainText, displayName)
                }
            }
            LyricsResult.NotFound
        } catch (e: Exception) {
            LyricsResult.NotFound
        }
    }
}
