package com.mediaplayer.data.model

interface PlayerController {
    fun togglePlayPause()
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun seekLive(targetMs: Long, source: String = "ui")
    fun seekFinal(targetMs: Long, resume: Boolean = true, source: String = "ui")
    fun setVisualizerMode(modeId: String)
    fun pauseForDjHold()
    fun resumeAfterDjHold()
    fun skipToQueueItem(index: Int)
    fun updateLyricsManually(trackId: Long, text: String)
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, sourceType: String = "All Songs", sourceName: String = "All Songs", sourcePath: String = "")
    fun expandPlayer()
    fun collapsePlayer()
}
