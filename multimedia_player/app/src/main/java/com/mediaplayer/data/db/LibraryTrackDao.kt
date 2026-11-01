package com.mediaplayer.data.db

import androidx.room.*
import com.mediaplayer.data.model.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryTrackDao {
    // Emits a new list to the UI anytime the database changes!
    @Query("SELECT * FROM library_tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM library_tracks WHERE folderName = :folder ORDER BY title ASC")
    fun getTracksByFolder(folder: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM library_tracks WHERE lyrics IS NULL OR lyrics = ''")
    suspend fun getTracksMissingLyrics(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("UPDATE library_tracks SET lyrics = :lyrics, hasSyncedLyrics = :isSynced WHERE id = :trackId")
    suspend fun updateLyrics(trackId: Int, lyrics: String?, isSynced: Boolean)

    @Query("DELETE FROM library_tracks")
    suspend fun clearLibrary()
}
