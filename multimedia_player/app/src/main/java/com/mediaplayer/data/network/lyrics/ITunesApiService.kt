package com.mediaplayer.data.network.lyrics

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApiService {
    @GET("search?media=music&entity=song&limit=1")
    suspend fun searchTrack(
        @Query("term") query: String
    ): Response<ITunesSearchResponse>
}

data class ITunesSearchResponse(val results: List<ITunesTrack>)
data class ITunesTrack(val trackName: String, val artistName: String, val trackId: Long)
