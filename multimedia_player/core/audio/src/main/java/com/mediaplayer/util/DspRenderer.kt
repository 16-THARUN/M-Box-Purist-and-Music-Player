package com.mediaplayer.util

import android.content.Context
import androidx.media3.session.MediaBrowser
import com.mediaplayer.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DspRenderer {
    suspend fun renderAndPlayDspMode(
        context: Context,
        browser: MediaBrowser,
        track: Track,
        mode: String,
        irFilePath: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext true
    }
}
