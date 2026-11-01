package com.mediaplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mediaplayer.data.model.Playlist
import com.mediaplayer.data.model.PlaylistTrack
import com.mediaplayer.data.model.Track

@Database(
    entities  = [Track::class, Playlist::class, PlaylistTrack::class],
    version   = 2,
    exportSchema  = true
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
}