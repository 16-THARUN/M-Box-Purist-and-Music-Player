package com.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediaplayer.ui.components.MiniPlayer
import com.mediaplayer.ui.equalizer.EqualizerScreen
import com.mediaplayer.ui.library.AlbumDetailsScreen
import com.mediaplayer.ui.library.FolderDetailsScreen
import com.mediaplayer.ui.library.LibraryScreen
import com.mediaplayer.ui.player.PlayerScreen
import com.mediaplayer.ui.settings.SettingsScreen
import com.mediaplayer.ui.theme.MediaPlayerTheme
import com.mediaplayer.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.NavType
import androidx.navigation.navArgument

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled in LibraryViewModel */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perm = if (android.os.Build.VERSION.SDK_INT >= 33)
            android.Manifest.permission.READ_MEDIA_AUDIO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        permissionLauncher.launch(perm)

        setContent {
            MediaPlayerTheme {
                MainNavigation()
            }
        }
    }
}

object Routes {
    const val LIBRARY       = "library"
    const val PLAYER        = "player"
    const val EQ            = "equalizer"
    const val SETTINGS      = "settings"
    const val ALBUM_DETAILS = "album_details/{albumName}"
    const val FOLDER_DETAILS = "folder_details/{folderPath}"

    fun albumDetails(name: String) = "album_details/$name"
    fun folderDetails(path: String) = "folder_details/${java.net.URLEncoder.encode(path, "UTF-8")}"
}

/** Routes where the bottom navigation bar is visible. */
private val bottomBarRoutes = setOf(Routes.LIBRARY, Routes.PLAYER)

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route

    // Single PlayerViewModel hoisted here — shared between MiniPlayer + PlayerScreen
    val playerVm: PlayerViewModel = hiltViewModel()
    val playerState by playerVm.state.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                // ── Mini-player: visible on every screen except the full player ──
                if (currentRoute != Routes.PLAYER) {
                    MiniPlayer(
                        title            = playerState.currentTrack?.title  ?: "Song title",
                        artist           = playerState.currentTrack?.artist ?: "",
                        albumArtUri      = playerState.currentTrack?.albumArtUri,
                        isPlaying        = playerState.isPlaying,
                        onPlayPauseClick = playerVm::togglePlayPause,
                        onNextClick      = playerVm::skipNext,
                        onExpandClick    = {
                            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                        }
                    )
                }

                // ── Bottom nav bar ────────────────────────────────────────────
                if (currentRoute in bottomBarRoutes) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Routes.LIBRARY,
                            onClick  = {
                                navController.navigate(Routes.LIBRARY) {
                                    popUpTo(Routes.LIBRARY) { inclusive = true }
                                }
                            },
                            icon  = { Icon(Icons.Rounded.LibraryMusic, null) },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.PLAYER,
                            onClick  = {
                                navController.navigate(Routes.PLAYER) {
                                    popUpTo(Routes.PLAYER) { inclusive = true }
                                }
                            },
                            icon  = { Icon(Icons.Rounded.PlayCircle, null) },
                            label = { Text("Now playing") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SETTINGS,
                            onClick  = { navController.navigate(Routes.SETTINGS) },
                            icon  = { Icon(Icons.Rounded.Settings, null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        }
    ) {padding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.LIBRARY,
            modifier         = Modifier.padding(padding)
        ) {
            // ── 1. Library Route ──
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    playerVm = playerVm,
                    onAlbumClick = { name -> navController.navigate(Routes.albumDetails(name)) },
                    onFolderClick = { path -> navController.navigate(Routes.folderDetails(path)) }
                )
            }

            composable(
                route = Routes.FOLDER_DETAILS,
                arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderPath = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("folderPath") ?: "", "UTF-8")
                FolderDetailsScreen(
                    folderPath = folderPath,
                    libVm = hiltViewModel(),
                    playerVm = playerVm,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── 2. Album Details Route ──
            composable(
                route = Routes.ALBUM_DETAILS,
                arguments = listOf(navArgument("albumName") { type = NavType.StringType })
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                AlbumDetailsScreen(
                    albumName = albumName,
                    libVm = hiltViewModel(),
                    playerVm = playerVm,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── 3. Player Route ──
            composable(Routes.PLAYER) {
                PlayerScreen(
                    vm       = playerVm,
                    onOpenEq = { navController.navigate(Routes.EQ) },
                    onBack   = { navController.popBackStack() }
                )
            }

            // ── 4. Equalizer Route (FIXED!) ──
            composable(Routes.EQ) {
                EqualizerScreen(
                    onBack = { navController.popBackStack() },
                    vm = playerVm // 👈 Now correctly sharing the same audio engine!
                )
            }

            // ── 5. Settings Route ──
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack    = { navController.popBackStack() },
                    onOpenEq  = { navController.navigate(Routes.EQ) }
                )
            }
        }
    }
}