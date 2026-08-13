package com.mediaplayer.di

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.room.Room
import com.google.common.util.concurrent.ListenableFuture
import com.mediaplayer.data.db.AppDatabase
import com.mediaplayer.data.db.LibraryTrackDao
import com.mediaplayer.data.db.MediaDatabase
import com.mediaplayer.data.db.PlaylistDao
import com.mediaplayer.data.db.TrackDao
import com.mediaplayer.PlaybackService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MediaDatabase =
        Room.databaseBuilder(ctx, MediaDatabase::class.java, "media_player.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "library.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideLibraryTrackDao(db: AppDatabase): LibraryTrackDao = db.libraryTrackDao()

    @Provides
    fun provideTrackDao(db: MediaDatabase): TrackDao = db.trackDao()

    @Provides
    fun providePlaylistDao(db: MediaDatabase): PlaylistDao = db.playlistDao()

    @Provides
    @Singleton
    fun provideMediaBrowserFuture(
        @ApplicationContext ctx: Context
    ): ListenableFuture<MediaBrowser> {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        return MediaBrowser.Builder(ctx, token).buildAsync()
    }
}
