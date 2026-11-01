package com.mediaplayer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Create the DataStore instance at the top level
val Context.dataStore by preferencesDataStore(name = "mbox_settings")

@Singleton
class FolderPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val FOLDER_URIS_KEY = stringSetPreferencesKey("selected_folder_uris")

    // Read the URIs as a continuous Flow
    val selectedFoldersFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[FOLDER_URIS_KEY] ?: emptySet()
        }

    // Save a new URI to the list
    suspend fun addFolderUri(uriString: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[FOLDER_URIS_KEY] ?: emptySet()
            preferences[FOLDER_URIS_KEY] = currentSet + uriString
        }
    }

    // Remove a URI from the list
    suspend fun removeFolderUri(uriString: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[FOLDER_URIS_KEY] ?: emptySet()
            preferences[FOLDER_URIS_KEY] = currentSet - uriString
        }
    }
}
