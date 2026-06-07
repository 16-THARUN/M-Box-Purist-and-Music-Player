package com.mediaplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String?,
    val duration: Long,       // milliseconds
    val bitrate: Int,
    val sampleRate: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val genre: String,
    val composer: String,
    val comment: String,
    val fileSize: Long,
    val dateAdded: Long,
    val mimeType: String,
    val folderName: String,
    val folderPath: String
)

data class AlbumRow(
    val album: String,
    val artist: String,
    val albumArtUri: String?
)

data class ArtistRow(
    val artist: String,
    val trackCount: Int
)

data class GenreRow(
    val genre: String,
    val trackCount: Int
)

data class FolderRow(
    val folderName: String,
    val folderPath: String,
    val trackCount: Int
)
