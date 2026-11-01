package com.mediaplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mediaplayer.data.model.TrackEntity

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryTrackDao(): LibraryTrackDao
}
