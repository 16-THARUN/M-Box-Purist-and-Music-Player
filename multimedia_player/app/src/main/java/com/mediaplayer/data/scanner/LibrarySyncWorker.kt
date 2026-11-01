package com.mediaplayer.data.scanner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import com.mediaplayer.data.repository.FolderPreferencesRepository
import com.mediaplayer.data.prefs.PlayerPreferences

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: LibrarySyncManager,
    private val folderRepo: FolderPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentFolders = folderRepo.selectedFoldersFlow.first()

            if (currentFolders.isNotEmpty()) {
                syncManager.syncLibraryFromUris(currentFolders)
            } else {
                // Fallback to legacy scan if no folders selected
                val musicDirectory = File("/storage/emulated/0/Music") 
                if (musicDirectory.exists()) {
                    syncManager.fullLibraryScan(musicDirectory)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
