package com.mediaplayer.data.network.lyrics

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit Interface for interacting with the LRCLIB API.
 */
interface LrcLibApiService {

    /**
     * Fetches exact lyrics using track metadata.
     * Wrapped in a Response<T> to safely handle HTTP 404s without throwing exceptions.
     *
     * @param trackName The name of the track.
     * @param artistName The name of the artist.
     * @param albumName The name of the album.
     * @param duration The duration of the track in seconds.
     * @return A Response containing LrcLibResponse if successful, or an error body.
     */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") duration: Int
    ): Response<LrcLibResponse>

    /**
     * Search for lyrics using flexible query parameters.
     * At least one of 'q' or 'track_name' should be provided.
     *
     * @param query General search query (can include title, artist, or lyrics fragment).
     * @param trackName The name of the track.
     * @param artistName The name of the artist (optional filter).
     * @param albumName The name of the album (optional filter).
     * @return A Response containing a List of LrcLibResponse objects.
     */
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null
    ): Response<List<LrcLibResponse>>
}
