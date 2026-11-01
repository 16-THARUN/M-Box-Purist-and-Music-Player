package com.mediaplayer

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import javax.inject.Inject

@HiltAndroidApp
class MediaPlayerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "mbox_last_crash.txt")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val crashReport = """
                    M-Box Crash Report
                    Time: ${Date()}
                    App Version: 1.2.0
                    Thread: ${thread.name}
                    Exception: ${throwable.javaClass.name}: ${throwable.message}
                    
                    Stack Trace:
                    $sw
                """.trimIndent()
                crashFile.writeText(crashReport)
                Log.e("MBoxCrash", "Uncaught exception saved to mbox_last_crash.txt", throwable)
            } catch (e: Exception) {
                Log.e("MBoxCrash", "Error writing crash report: ${e.message}")
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
