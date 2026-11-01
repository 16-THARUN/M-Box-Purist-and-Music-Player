package com.mediaplayer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: TrackDao
) {
    sealed class ScanState {
        object Idle : ScanState()
        data class Scanning(val current: Int, val total: Int) : ScanState()
        data class Done(val count: Int) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    suspend fun scan(onProgress: (ScanState) -> Unit = {}) = withContext(Dispatchers.IO) {
        onProgress(ScanState.Scanning(0, 0))

        val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        )

        val found = mutableListOf<Track>()

        ctx.contentResolver.query(
            queryUri, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} = 1",
            null,
            "${MediaStore.Audio.Media.ARTIST} ASC"
        )?.use { cursor ->

            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val bitrateCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
            val trackCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            val total = cursor.count
            var index = 0

            while (cursor.moveToNext()) {
                index++
                if (index % 50 == 0) onProgress(ScanState.Scanning(index, total))

                val id       = cursor.getLong(idCol)
                val filePath = cursor.getString(dataCol) ?: continue
                val albumId  = cursor.getLong(albumIdCol)

                val albumArtUri = ContentUris.withAppendedId(albumArtBase, albumId).toString()
                val trackUri    = ContentUris.withAppendedId(queryUri, id).toString()

                val file = File(filePath)
                val folderName = file.parentFile?.name ?: "Unknown"
                val folderPath = file.parentFile?.absolutePath ?: ""

                // Deep tag read with JAudioTagger for extra fields
                val deepTags = readDeepTags(filePath)

                val rawTrack = cursor.getInt(trackCol)
                val trackNumber = rawTrack % 1000      // e.g. 1002 = disc 1, track 2
                val discNumber  = rawTrack / 1000

                found.add(
                    Track(
                        id          = id,
                        uri         = trackUri,
                        title       = deepTags?.getFirst(FieldKey.TITLE)
                                          ?.takeIf { it.isNotBlank() }
                                          ?: cursor.getString(titleCol) ?: "Unknown",
                        artist      = deepTags?.getFirst(FieldKey.ARTIST)
                                          ?.takeIf { it.isNotBlank() }
                                          ?: cursor.getString(artistCol) ?: "Unknown",
                        album       = deepTags?.getFirst(FieldKey.ALBUM)
                                          ?.takeIf { it.isNotBlank() }
                                          ?: cursor.getString(albumCol) ?: "Unknown",
                        albumArtUri = albumArtUri,
                        duration    = cursor.getLong(durationCol),
                        bitrate     = cursor.getInt(bitrateCol),
                        sampleRate  = deepTags?.let {
                                          runCatching {
                                              AudioFileIO.read(File(filePath)).audioHeader.sampleRateAsNumber
                                          }.getOrDefault(44100)
                                      } ?: 44100,
                        trackNumber = trackNumber,
                        discNumber  = discNumber,
                        year        = deepTags?.getFirst(FieldKey.YEAR)
                                          ?.toIntOrNull()
                                          ?: cursor.getInt(yearCol),
                        genre       = deepTags?.getFirst(FieldKey.GENRE) ?: "",
                        composer    = deepTags?.getFirst(FieldKey.COMPOSER) ?: "",
                        comment     = deepTags?.getFirst(FieldKey.COMMENT) ?: "",
                        fileSize    = cursor.getLong(sizeCol),
                        dateAdded   = cursor.getLong(dateCol),
                        mimeType    = cursor.getString(mimeCol) ?: "",
                        folderName  = folderName,
                        folderPath  = folderPath
                    )
                )
            }
        }

        // Persist to Room
        dao.upsertAll(found)
        dao.pruneDeleted(found.map { it.uri })

        onProgress(ScanState.Done(found.size))
        found.size
    }

    private fun readDeepTags(path: String) = runCatching {
        AudioFileIO.read(File(path)).tag
    }.getOrNull()
}
