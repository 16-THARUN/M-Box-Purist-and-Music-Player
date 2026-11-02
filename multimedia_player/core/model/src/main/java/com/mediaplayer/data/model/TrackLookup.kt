package com.mediaplayer.data.model

interface TrackLookup {
    suspend fun trackById(id: Long): Track?
}
