package com.mediaplayer

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediaplayer.ui.common.FloatingPlayerSwitch
import com.mediaplayer.ui.equalizer.EqualizerScreen
import com.mediaplayer.ui.library.AlbumDetailsScreen
import com.mediaplayer.ui.library.FolderDetailsScreen
import com.mediaplayer.ui.library.LibraryScreen
import com.mediaplayer.ui.player.NowPlayingQueueScreen
import com.mediaplayer.ui.player.NowPlayingScreen
import com.mediaplayer.ui.settings.SettingsScreen
import com.mediaplayer.ui.theme.MediaPlayerTheme
import com.mediaplayer.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mediaplayer.data.scanner.LibrarySyncWorker
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled in LibraryViewModel and via effects */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // 🔥 THE FIX: Tell Android to let the app draw under the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Handle intent if the app is launching fresh
        handleAudioIntent(intent)

        // 1. Trigger the background scan exactly once per app session
        val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "AutoLibrarySync",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )

        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.add(android.Manifest.permission.RECORD_AUDIO)

        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            MediaPlayerTheme {
                MainNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's current intent
        handleAudioIntent(intent)
    }

    private fun handleAudioIntent(currentIntent: Intent?) {
        val action = currentIntent?.action
        val type = currentIntent?.type
        val uri: Uri? = currentIntent?.data

        if (Intent.ACTION_VIEW == action && uri != null) {
            if (type?.startsWith("audio/") == true || type == "application/ogg" || type == "application/x-flac" || type == "audio/ogg" || type == "audio/flac") {
                // 🔥 THE FIX: Pass applicationContext to read the file's metadata safely
                playerViewModel.playExternalUri(applicationContext, uri)
            }
        }
    }
}

object Routes {
    const val LIBRARY       = "library"
    const val PLAYER        = "player"
    const val EQ            = "equalizer"
    const val QUEUE         = "queue"
    const val SETTINGS      = "settings"
    const val ALBUM_DETAILS = "album_details/{albumName}"
    const val FOLDER_DETAILS = "folder_details/{folderPath}"

    fun albumDetails(name: String) = "album_details/$name"
    fun folderDetails(path: String) = "folder_details/${java.net.URLEncoder.encode(path, "UTF-8")}"
}

/** Routes where the bottom navigation bar is visible. */
private val bottomBarRoutes = setOf(Routes.LIBRARY, Routes.SETTINGS)

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // I should check if I should change it. User didn't ask to change orientation logic, just the architecture.

    val activity = LocalContext.current as ComponentActivity
    val playerVm: PlayerViewModel = hiltViewModel(activity)
    val playerState by playerVm.state.collectAsState()

    val currentTrack = playerState.currentTrack

    Box(modifier = Modifier.fillMaxSize()) {
        // Child A: The Base App
        Scaffold(
            bottomBar = {
                // Only show this entire block if we aren't on the full-screen player
                if (currentRoute != Routes.PLAYER) {
                    Column {
                        // 1. THE MINI PLAYER (Sits on top)
                        AnimatedVisibility(
                            visible = currentTrack != null,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            MiniPlayer(
                                track = currentTrack,
                                isPlaying = playerState.isPlaying,
                                currentPositionMs = playerState.positionMs,
                                totalDurationMs = playerState.durationMs,
                                onPlayPauseClick = playerVm::togglePlayPause,
                                onNextClick = playerVm::skipNext,
                                onPreviousClick = playerVm::skipPrevious,
                                onClick = { navController.navigate(Routes.PLAYER) }
                            )
                        }

                        // 2. THE BOTTOM NAVIGATION BAR (Sits on the very bottom)
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && currentRoute in bottomBarRoutes) {
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
                                    selected = currentRoute == Routes.SETTINGS,
                                    onClick  = { navController.navigate(Routes.SETTINGS) },
                                    icon  = { Icon(Icons.Rounded.Settings, null) },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // 🔥 THE FIX: NavHost fills the entire screen to allow immersive backgrounds.
            // We apply bottom padding only to screens that share space with the NavigationBar/MiniPlayer.
            val bottomPadding = innerPadding.calculateBottomPadding()

            Box(modifier = Modifier.fillMaxSize()) {
                // Child A (Inner): The NavHost
                NavHost(
                    navController    = navController,
                    startDestination = Routes.LIBRARY,
                    modifier         = Modifier.fillMaxSize()
                ) {
                    composable(Routes.LIBRARY) {
                        Box(Modifier.padding(bottom = bottomPadding)) {
                            LibraryScreen(
                                playerVm = playerVm,
                                onAlbumClick = { name -> navController.navigate(Routes.albumDetails(name)) },
                                onFolderClick = { path -> navController.navigate(Routes.folderDetails(path)) },
                                onTrackClick = { navController.navigate(Routes.PLAYER) }
                            )
                        }
                    }

                    composable(Routes.PLAYER) {
                        NowPlayingScreen(
                            onBack = { navController.popBackStack() },
                            onOpenEq = { navController.navigate(Routes.EQ) },
                            onOpenQueue = { navController.navigate(Routes.QUEUE) }
                        )
                    }

                    composable(
                        route = Routes.FOLDER_DETAILS,
                        arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val folderPath = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("folderPath") ?: "", "UTF-8")
                        Box(Modifier.padding(bottom = bottomPadding)) {
                            FolderDetailsScreen(
                                folderPath = folderPath,
                                libVm = hiltViewModel(),
                                playerVm = playerVm,
                                onBack = { navController.popBackStack() },
                                onTrackClick = { navController.navigate(Routes.PLAYER) }
                            )
                        }
                    }

                    composable(
                        route = Routes.ALBUM_DETAILS,
                        arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                        Box(Modifier.padding(bottom = bottomPadding)) {
                            AlbumDetailsScreen(
                                albumName = albumName,
                                libVm = hiltViewModel(),
                                playerVm = playerVm,
                                onBack = { navController.popBackStack() },
                                onTrackClick = { navController.navigate(Routes.PLAYER) }
                            )
                        }
                    }

                    composable(Routes.EQ) {
                        EqualizerScreen(
                            onBack = { navController.popBackStack() },
                            vm = playerVm
                        )
                    }

                    composable(Routes.QUEUE) {
                        NowPlayingQueueScreen(
                            vm = playerVm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        Box(Modifier.padding(bottom = bottomPadding)) {
                            SettingsScreen(
                                onBack    = { navController.popBackStack() },
                                onOpenEq  = { navController.navigate(Routes.EQ) },
                                onOpenVisualizerSettings = {
                                    navController.navigate(Routes.EQ) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Removed BackHandler for modal as we now use NavHost
    }
}
