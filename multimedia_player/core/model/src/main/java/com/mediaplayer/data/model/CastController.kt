package com.mediaplayer.data.model

interface CastController {
    fun sendSetLyricsToTv(enabled: Boolean)
    fun sendSetDisplayModeToTv(mode: String)
}
