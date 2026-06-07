package com.mediaplayer.data.db

import androidx.room.*
import com.mediaplayer.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    // ── Queries (Flow) ───────────────────────────────────────

    @Query("SELECT * FROM tracks ORDER BY artist, album, trackNumber")
    fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album, trackNumber")
    fun getByArtist(artist: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    fun getByAlbum(album: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY artist, title")
    fun getByGenre(genre: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE uri IN (:uris)")
    fun getByUris(uris: List<String>): Flow<List<Track>>

    @Query("""
        SELECT * FROM tracks
        WHERE title  LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album  LIKE '%' || :q || '%'
        ORDER BY title
    """)
    fun search(q: String): Flow<List<Track>>

    @Query("SELECT DISTINCT album, artist, albumArtUri FROM tracks ORDER BY album")
    fun getAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT artist, COUNT(*) AS trackCount FROM tracks GROUP BY artist ORDER BY artist")
    fun getArtists(): Flow<List<ArtistRow>>

    @Query("SELECT genre, COUNT(*) AS trackCount FROM tracks WHERE genre != '' GROUP BY genre ORDER BY genre")
    fun getGenres(): Flow<List<GenreRow>>

    @Query("""
        SELECT folderName, folderPath, COUNT(*) AS trackCount
        FROM tracks
        WHERE folderPath != ''
        GROUP BY folderPath
        ORDER BY folderName COLLATE NOCASE ASC
    """)
    fun getFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY title")
    fun getByFolder(folderPath: String): Flow<List<Track>>

    // ── One-shot Queries (Suspend) ───────────────────────────

    @Query("SELECT * FROM tracks ORDER BY artist, album, trackNumber")
    suspend fun getAllTracksOnce(): List<Track>

    @Query("SELECT DISTINCT album, artist, albumArtUri FROM tracks ORDER BY album")
    suspend fun getAlbumsOnce(): List<AlbumRow>

    @Query("SELECT artist, COUNT(*) AS trackCount FROM tracks GROUP BY artist ORDER BY artist")
    suspend fun getArtistsOnce(): List<ArtistRow>

    @Query("SELECT genre, COUNT(*) AS trackCount FROM tracks WHERE genre != '' GROUP BY genre ORDER BY genre")
    suspend fun getGenresOnce(): List<GenreRow>

    @Query("""
        SELECT folderName, folderPath, COUNT(*) AS trackCount 
        FROM tracks 
        WHERE folderPath != '' 
        GROUP BY folderPath 
        ORDER BY folderName COLLATE NOCASE
    """)
    suspend fun getFoldersOnce(): List<FolderRow>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber")
    suspend fun getByAlbumOnce(album: String): List<Track>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album, trackNumber")
    suspend fun getByArtistOnce(artist: String): List<Track>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY artist, title")
    suspend fun getByGenreOnce(genre: String): List<Track>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY title")
    suspend fun getByFolderOnce(folderPath: String): List<Track>

    @Query("""
        SELECT * FROM tracks
        WHERE title  LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album  LIKE '%' || :q || '%'
        ORDER BY title
    """)
    suspend fun searchOnce(q: String): List<Track>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): Track?

    // ── Writes ───────────────────────────────────────────────

    @Upsert
    suspend fun upsertAll(tracks: List<Track>)

    @Query("DELETE FROM tracks WHERE uri NOT IN (:activeUris)")
    suspend fun pruneDeleted(activeUris: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}
