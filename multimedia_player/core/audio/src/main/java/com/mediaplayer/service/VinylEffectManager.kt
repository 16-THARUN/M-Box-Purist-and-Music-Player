package com.mediaplayer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.mbox.core.audio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VinylEffectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val TAG = "VinylEffectManager"

    fun playScratch() {
        try {
            if (mediaPlayer?.isPlaying == true) return

            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                
                val afd = context.resources.openRawResourceFd(R.raw.vinyl_scratch) ?: return
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                
                prepare()
                setOnCompletionListener { 
                    it.release() 
                    if (mediaPlayer == it) mediaPlayer = null
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing scratch sound", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
