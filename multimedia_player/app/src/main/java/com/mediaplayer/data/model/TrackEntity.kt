package com.mediaplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val folderName: String,
    val lyrics: String? = null,           // Pre-loaded lyrics content (if available)
    val hasSyncedLyrics: Boolean = false  // Quick flag for UI badges
)
