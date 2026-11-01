# m-Box Shared Core Audit Report

**Project**: m-Box Player  
**Application ID**: `com.mediaplayer`  
**Version**: `1.2.0` (Version Code `12`)  
**Audit Date**: October 2026  

---

## 1. Project Facts

### Module Structure
- **Root Project**: `MBoxPlayer` (`settings.gradle.kts`)
- **Modules**: Single module `:app` (`C:/Users/HP/mbox2/multimedia_player/app`)

### Build Toolchain & Versions
- **Android Gradle Plugin (AGP)**: `9.2.1`
- **Kotlin**: `2.3.21` (Compose Compiler plugin `2.3.21`, KSP `2.3.2`)
- **Gradle Version**: `9.3.1` (Wrapper)
- **Hilt**: `2.59.2`

### Application Configuration (`app/build.gradle.kts`)
- **`applicationId`**: `com.mediaplayer`
- **`compileSdk`**: `35`
- **`minSdk`**: `26` (Android 8.0 Oreo)
- **`targetSdk`**: `35` (Android 15)
- **`versionCode`**: `12`
- **`versionName`**: `1.2.0`

### Build Types & Flavors
- **Build Types**:
  - `debug`: Unminified, debuggable, StrictMode thread policy enabled.
  - `release`: Minified (`isMinifyEnabled = true`), ProGuard rules (`proguard-rules.pro`, `proguard-android-optimize.txt`), NDK ABI filter `arm64-v8a`.
- **Product Flavors**: None configured.

### Release Signing Config
- No explicit `signingConfigs` block defined in `app/build.gradle.kts`. Uses default debug keystore for debug builds, and environment/properties for release signing.

### Dependency Injection (DI)
- **Framework**: Dagger Hilt `2.59.2`
- **Application**: `MediaPlayerApp` annotated with `@HiltAndroidApp`
- **ViewModels**: `@HiltViewModel` (`PlayerViewModel`, `LibraryViewModel`, `SettingsViewModel`, `EqualizerViewModel`)
- **Background Services**: `MediaService` (`@AndroidEntryPoint` `MediaLibraryService`) injects `TrackDao`, `EqualizerManager`, `NativeDspEngine`, `VinylEffectManager`, `VisualizerManager`. `PlayerViewModel` acquires `MediaBrowser` asynchronously via `ListenableFuture<MediaBrowser>`.

---

## 2. Classification of Source Files

Total Kotlin files: **131**

### A. `core-audio` (18 files)
1. `com.mediaplayer.service.BiquadEqAudioProcessor` (142 lines) — Custom Biquad EQ AudioProcessor for Media3.
2. `com.mediaplayer.service.BiquadFilter` (119 lines) — Biquad filter DSP mathematics.
3. `com.mediaplayer.service.EqualizerManager` (233 lines) — Android AudioFX `Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer` manager.
4. `com.mediaplayer.service.MediaService` (595 lines) — Media3 `ExoPlayer` / `MediaLibraryService` background service.
5. `com.mediaplayer.service.NativeDspEngine` (58 lines) — Native NDK C++ DSP wrapper interface.
6. `com.mediaplayer.service.VinylEffectManager` (58 lines) — Vinyl needle scratch audio effect player.
7. `com.mediaplayer.util.AudioDiagnostics` (68 lines) — Audio output hardware sample rate, buffer size, latency inspector.
8. `com.mediaplayer.util.AudioFormatUtils` (141 lines) — Audio format parser & specification string formatter.
9. `com.mediaplayer.util.AutoEqParser` (55 lines) — AutoEQ frequency response curve text file parser.
10. `com.mediaplayer.util.BluetoothHelper` (62 lines) — Bluetooth A2DP / SCO audio routing helper.
11. `com.mediaplayer.util.DeviceStateMonitor` (65 lines) — CPU load, thermal state, and memory pressure monitor.
12. `com.mediaplayer.util.DolbyCapabilityDetector` (86 lines) — Device hardware Dolby Atmos / E-AC-3 capability detector.
13. `com.mediaplayer.util.DolbySilenceWatchdog` (36 lines) — Silence detection watchdog for hardware decoder stalls.
14. `com.mediaplayer.util.DspRenderer` (20 lines) — Off-line DSP mode renderer helper.
15. `com.mediaplayer.util.Eac3JocParser` (208 lines) — Media3 track format & MP4 `dec3` box parser for E-AC-3 JOC (Atmos).
16. `com.mediaplayer.util.Eac3Transcoder` (206 lines) — FFmpegKit-backed E-AC-3 / Dolby Atmos FLAC transcode manager & LRU cache.
17. `com.mediaplayer.util.PcmAnalysisAudioProcessor` (225 lines) — PCM AudioProcessor for real-time FFT spectrum extraction.
18. `com.mediaplayer.util.PresetImporter` (45 lines) — Equalizer preset JSON/text importer.

### B. `core-ui` (58 files)
1. `com.mediaplayer.ui.common.CastButton` (34 lines) — Cast action button composable.
2. `com.mediaplayer.ui.common.FloatingPlayerSwitch` (39 lines) — Floating mini-player toggle composable.
3. `com.mediaplayer.ui.components.AcousticRipplesVisualizer` (110 lines) — Acoustic Ripples canvas visualizer.
4. `com.mediaplayer.ui.components.AnalogGlitchVisualizer` (87 lines) — Analog Glitch visualizer.
5. `com.mediaplayer.ui.components.AnamorphicFlareVisualizer` (81 lines) — Anamorphic Flare visualizer.
6. `com.mediaplayer.ui.components.AnimatedEqualizerIcon` (87 lines) — Animated EQ bars icon.
7. `com.mediaplayer.ui.components.AuroraBorealisVisualizer` (95 lines) — Aurora Borealis visualizer.
8. `com.mediaplayer.ui.components.BluetoothDeviceBottomSheet` (154 lines) — Bluetooth device selector sheet.
9. `com.mediaplayer.ui.components.BottomGlassBarsVisualizer` (136 lines) — Bottom Glass Bars visualizer.
10. `com.mediaplayer.ui.components.CdPlayerAlbumArt` (100 lines) — CD Player rotating album art composable.
11. `com.mediaplayer.ui.components.FastScrollbar` (239 lines) — Fast-scroll thumb and popup indicator.
12. `com.mediaplayer.ui.components.HeroHeader` (128 lines) — Hero artwork header composable.
13. `com.mediaplayer.ui.components.HexagonalAudioGridVisualizer` (114 lines) — Hexagonal Audio Grid visualizer.
14. `com.mediaplayer.ui.components.LiquidSplineVisualizer` (115 lines) — Liquid Spline visualizer.
15. `com.mediaplayer.ui.components.ListOptionsBottomSheet` (235 lines) — List sorting & display options sheet.
16. `com.mediaplayer.ui.components.LoopingVideoVisualizer` (108 lines) — Looping video background visualizer.
17. `com.mediaplayer.ui.components.LyricsContent` (259 lines) — Synced lyrics scrollable view composable.
18. `com.mediaplayer.ui.components.NarrowVisualizer` (118 lines) — Narrow frequency bars visualizer.
19. `com.mediaplayer.ui.components.NowPlayingArtwork` (446 lines) — Spinning vinyl, cover art, and turntable gesture handlers.
20. `com.mediaplayer.ui.components.OledEdgeVisualizer` (82 lines) — OLED edge lighting visualizer.
21. `com.mediaplayer.ui.components.OrbitingStardustVisualizer` (185 lines) — Orbiting Stardust visualizer.
22. `com.mediaplayer.ui.components.PillSeekBar` (193 lines) — Pill-style seek bar composable.
23. `com.mediaplayer.ui.components.PlainOscilloscope` (97 lines) — Oscilloscope waveform visualizer.
24. `com.mediaplayer.ui.components.PlainVisualizer` (128 lines) — Plain frequency bars visualizer.
25. `com.mediaplayer.ui.components.PresetListItem` (77 lines) — Equalizer preset item row composable.
26. `com.mediaplayer.ui.components.ProceduralBackgroundVisualizers` (106 lines) — Procedural background visualizers.
27. `com.mediaplayer.ui.components.ReactiveVideoVisualizer` (228 lines) — Reactive video background visualizer.
28. `com.mediaplayer.ui.components.ReferenceLightLeaks` (99 lines) — Reference light leaks overlay visualizer.
29. `com.mediaplayer.ui.components.SharedGroupRow` (101 lines) — Group row item composable.
30. `com.mediaplayer.ui.components.SharedTrackRow` (193 lines) — Track row item composable.
31. `com.mediaplayer.ui.components.SpectrumDotsVisualizer` (127 lines) — Spectrum dots particle visualizer.
32. `com.mediaplayer.ui.components.SpectrumVisualizer` (139 lines) — Spectrum bars visualizer.
33. `com.mediaplayer.ui.components.VinylDisc` (110 lines) — Spinning vinyl disc layout composable.
34. `com.mediaplayer.ui.components.VinylGrooveBleedVisualizer` (116 lines) — Vinyl groove bleed visualizer.
35. `com.mediaplayer.ui.components.VinylPlayerScreen` (56 lines) — Vinyl player layout wrapper.
36. `com.mediaplayer.ui.components.VinylStage` (194 lines) — Vinyl stage container and ring visualizer layer.
37. `com.mediaplayer.ui.components.VisualizationModeSheet` (105 lines) — Visualizer mode selector bottom sheet.
38. `com.mediaplayer.ui.components.VisualizerRingHelper` (63 lines) — Ring visualizer math & boundary helper.
39. `com.mediaplayer.ui.components.VolumetricParticleVisualizer` (90 lines) — Volumetric particle swarm visualizer.
40. `com.mediaplayer.ui.components.WavyLineSeekBar` (231 lines) — Wavy animated seek bar composable.
41. `com.mediaplayer.ui.components.WavySlider` (92 lines) — Wavy slider composable.
42. `com.mediaplayer.ui.components.WordHighlightedText` (72 lines) — Word-by-word highlighted text composable.
43. `com.mediaplayer.ui.equalizer.DynamicEqualizerScreen` (598 lines) — Dynamic Equalizer UI screen with orbital controls.
44. `com.mediaplayer.ui.equalizer.EqualizerScreen` (248 lines) — Equalizer sliders & controls UI.
45. `com.mediaplayer.ui.equalizer.TriangularOrbitView` (147 lines) — Orbital 3D-style EQ view composable.
46. `com.mediaplayer.ui.player.CircularSpectrumVisualizer` (122 lines) — Circular spectrum ring visualizer.
47. `com.mediaplayer.ui.player.CompactMiniPlayer` (93 lines) — Floating mini-player bar composable.
48. `com.mediaplayer.ui.player.LyricsEditDialog` (48 lines) — Lyrics editing dialog composable.
49. `com.mediaplayer.ui.player.LyricsPage` (412 lines) — Full-screen Lyrics page composable.
50. `com.mediaplayer.ui.player.NowPlayingQueueScreen` (141 lines) — Now Playing queue list screen composable.
51. `com.mediaplayer.ui.player.NowPlayingScreen` (1265 lines) — Main Now Playing screen layout.
52. `com.mediaplayer.ui.player.PlayerScreen` (63 lines) — Player screen wrapper composable.
53. `com.mediaplayer.ui.player.SwipeableMediaHeader` (104 lines) — Swipeable header composable.
54. `com.mediaplayer.ui.player.SwipeableTrackTitle` (122 lines) — Swipeable track title composable.
55. `com.mediaplayer.ui.theme.Color` (23 lines) — App color palette definitions.
56. `com.mediaplayer.ui.theme.Theme` (58 lines) — Material3 theme configuration.
57. `com.mediaplayer.ui.visualizer.CircularVisualizerCanvas` (212 lines) — Circular visualizer canvas renderer.
58. `com.mediaplayer.ui.visualizer.CircularVisualizerEngine` (45 lines) — Circular visualizer math engine.

### C. `core-cast` (13 files)
1. `com.mediaplayer.cast.SamsungTvLauncher` (307 lines) — Samsung TV SmartView API / WebSocket app launcher.
2. `com.mediaplayer.cast.TvRemoteVolume` (153 lines) — TV remote volume provider and state manager.
3. `com.mediaplayer.service.CastOptionsProvider` (20 lines) — Google Cast OptionsProvider implementation.
4. `com.mediaplayer.service.TvCastKeepAliveService` (109 lines) — Foreground keep-alive service for TV casting.
5. `com.mediaplayer.ui.components.CastDebugOverlay` (114 lines) — Cast debug overlay dialog composable.
6. `com.mediaplayer.ui.components.PlayOnDeviceBottomSheet` (446 lines) — "Play on" device selection sheet composable.
7. `com.mediaplayer.ui.components.TvVolumeOverlay` (137 lines) — Floating Samsung TV volume overlay.
8. `com.mediaplayer.util.CastMediaHelper` (34 lines) — Cast MediaItem builder.
9. `com.mediaplayer.util.LocalAudioServer` (263 lines) — NanoHTTPD HTTP streaming server for local files.
10. `com.mediaplayer.util.MBoxCastServer` (861 lines) — Unified NanoHTTPD HTTP & WebSocket `/cast-bridge` server for M-Box TV.
11. `com.mediaplayer.util.SamsungTvManager` (354 lines) — Samsung TV SSDP discovery, WoL, and REST API client.
12. `com.mediaplayer.util.SpectrumCalculator` (160 lines) — Precomputed 32-band FFT spectrum calculator for TV.
13. `com.mediaplayer.util.WifiDiscoveryManager` (60 lines) — Wi-Fi network and TV discovery manager.

### D. `core-model` (24 files)
1. `com.mediaplayer.data.lyrics.LyricsFetcherManager` (66 lines) — Lyrics fetcher manager.
2. `com.mediaplayer.data.lyrics.LyricsSource` (23 lines) — Lyrics source interface.
3. `com.mediaplayer.data.lyrics.LyricsSourceProviders` (280 lines) — LRCLIB and iTunes lyrics fetchers.
4. `com.mediaplayer.data.model.AudioPreset` (13 lines) — Audio preset data model.
5. `com.mediaplayer.data.model.AudioSignalPath` (24 lines) — Audio signal path specification model.
6. `com.mediaplayer.data.model.EqStateModels` (71 lines) — Equalizer state data models.
7. `com.mediaplayer.data.model.EqualizerModels` (36 lines) — Equalizer presets and band models.
8. `com.mediaplayer.data.model.LyricLine` (34 lines) — Lyric line and LRC parser models.
9. `com.mediaplayer.data.model.Playlist` (39 lines) — Playlist data model.
10. `com.mediaplayer.data.model.Track` (67 lines) — Track domain model.
11. `com.mediaplayer.data.model.TrackEntity` (18 lines) — Room Track entity model.
12. `com.mediaplayer.data.model.VisualizationPreset` (42 lines) — Visualization mode preset model.
13. `com.mediaplayer.data.network.RetrofitClient` (47 lines) — Retrofit REST client setup.
14. `com.mediaplayer.data.network.lyrics.ITunesApiService` (16 lines) — iTunes Search API interface.
15. `com.mediaplayer.data.network.lyrics.LrcLibApiService` (48 lines) — LRCLIB API interface.
16. `com.mediaplayer.data.network.lyrics.LrcLibResponse` (18 lines) — LRCLIB response DTO model.
17. `com.mediaplayer.data.prefs.PlayerPreferences` (249 lines) — DataStore player preferences repository.
18. `com.mediaplayer.data.repository.FolderPreferencesRepository` (45 lines) — Folder preferences repository.
19. `com.mediaplayer.data.repository.LyricsRepository` (109 lines) — Lyrics repository.
20. `com.mediaplayer.util.BandSmoother` (27 lines) — Frequency band smoothing math utility.
21. `com.mediaplayer.util.ColorUtils` (19 lines) — Color conversion and palette utility.
22. `com.mediaplayer.util.FileRelocationManager` (138 lines) — File relocation & URI resolver utility.
23. `com.mediaplayer.util.VisualizerManager` (84 lines) — Visualizer state & FFT band manager.
24. `com.mediaplayer.util.VisualizerSmoother` (29 lines) — Visualizer frame smoothing math utility.

### E. `player-only` (34 files)
1. `com.mediaplayer.MainActivity` (514 lines) — Main Activity, navigation host, intent handler, key dispatcher.
2. `com.mediaplayer.MediaPlayerApp` (52 lines) — Application class, Hilt entry point, WorkManager setup.
3. `com.mediaplayer.MiniPlayer` (250 lines) — Local mini-player bar composable.
4. `com.mediaplayer.data.db.AppDatabase` (11 lines) — Room database definition.
5. `com.mediaplayer.data.db.LibraryTrackDao` (28 lines) — Room DAO for library tracks.
6. `com.mediaplayer.data.db.MediaDatabase` (17 lines) — Room MediaDatabase definition.
7. `com.mediaplayer.data.db.PlaylistDao` (41 lines) — Room DAO for playlists.
8. `com.mediaplayer.data.db.TrackDao` (136 lines) — Room DAO for track queries.
9. `com.mediaplayer.data.repository.LibraryRepository` (60 lines) — Local library repository.
10. `com.mediaplayer.data.scanner.LibrarySyncManager` (188 lines) — MediaStore & filesystem scanner manager.
11. `com.mediaplayer.data.scanner.LibrarySyncWorker` (44 lines) — WorkManager background sync worker.
12. `com.mediaplayer.data.scanner.MediaScanner` (151 lines) — MediaStore scanner utility.
13. `com.mediaplayer.di.AppModule` (58 lines) — Hilt DI module for App/Room DB.
14. `com.mediaplayer.di.NetworkModule` (51 lines) — Hilt DI module for Retrofit network.
15. `com.mediaplayer.tv.TvLibraryScreen` (119 lines) — Android TV Leanback library screen.
16. `com.mediaplayer.tv.TvMainActivity` (105 lines) — Android TV Leanback launcher activity.
17. `com.mediaplayer.tv.TvNowPlayingScreen` (121 lines) — Android TV Leanback now playing screen.
18. `com.mediaplayer.ui.components.FolderPickerBottomSheet` (144 lines) — Folder picker bottom sheet.
19. `com.mediaplayer.ui.components.LibraryFoldersChooserDialog` (315 lines) — Library folder configuration dialog.
20. `com.mediaplayer.ui.library.AlbumDetailsScreen` (95 lines) — Album details screen composable.
21. `com.mediaplayer.ui.library.FolderDetailsScreen` (197 lines) — Folder details screen composable.
22. `com.mediaplayer.ui.library.LibraryHomeCategoryList` (107 lines) — Library categories list composable.
23. `com.mediaplayer.ui.library.LibraryScreen` (791 lines) — Main local library screen composable.
24. `com.mediaplayer.ui.settings.LyricsSourcesScreen` (183 lines) — Lyrics sources configuration screen.
25. `com.mediaplayer.ui.settings.SettingsScreen` (783 lines) — Main app settings screen composable.
26. `com.mediaplayer.ui.settings.VinylPlayerPreview` (218 lines) — Settings preview composable.
27. `com.mediaplayer.util.UpdateManager` (66 lines) — In-app update manager.
28. `com.mediaplayer.viewmodel.EqualizerViewModel` (60 lines) — ViewModel for Equalizer UI.
29. `com.mediaplayer.viewmodel.LibraryViewModel` (214 lines) — ViewModel for Local Library UI.
30. `com.mediaplayer.viewmodel.PlayerViewModel` (2857 lines) — ViewModel for Player, Queue, Casting & State management.
31. `com.mediaplayer.viewmodel.SettingsViewModel` (242 lines) — ViewModel for Settings UI.
32. `com.mediaplayer.viewmodel.ui.theme.Color` (11 lines) — Duplicate theme color file.
33. `com.mediaplayer.viewmodel.ui.theme.Theme` (58 lines) — Duplicate theme file.
34. `com.mediaplayer.viewmodel.ui.theme.Type` (34 lines) — Duplicate theme typography file.

### F. `unclear` (None)
- 0 files. All files are categorized into exactly one group.

---

## 3. What Blocks a Move

### Direct Dependencies on `player-only` (E)
1. **`MediaService.kt` (Group A)**:
   - Injects `TrackDao` (Group E). Requires a `TrackRepository` interface in `core-model` (D).
2. **`NowPlayingScreen.kt` (Group B)** & **`VinylStage.kt` (Group B)** & **`NowPlayingArtwork.kt` (Group B)**:
   - Import `PlayerViewModel` (Group E) and `PlayerViewModel.PlayerUiState` (Group E) directly. Requires a `PlayerState` domain model and `PlayerController` interface in `core-model` / `core-ui`.
3. **`PlayOnDeviceBottomSheet.kt` (Group C)**:
   - Imports `PlayerViewModel` (Group E) directly. Requires a `CastController` interface in `core-model` / `core-cast`.

### Context, Singleton & Static State Usage
- `Eac3Transcoder` (A), `SpectrumCalculator` (C), `MBoxCastServer` (C), `SamsungTvManager` (C) require explicit `Context` parameters.
- `EqualizerManager` (A) and `VisualizerManager` (D) use `@Singleton` Hilt annotations.

### Resources Required
- `service/MediaService.kt`: `R.drawable.ic_notification_small`, `R.string.app_name`.
- `service/TvCastKeepAliveService.kt`: `R.drawable.ic_notification_small`.
- `ui/components/ReactiveVideoVisualizer.kt`: Raw video MP4 resources (`R.raw.glitch_burn`, `R.raw.vis_lens_flare_landscape`, etc.).

---

## 4. Libraries and Licences

| Library | Version | License | Source / Resolution Status |
| :--- | :--- | :--- | :--- |
| **AndroidX Media3** | `1.4.1` | Apache 2.0 | Maven Central |
| **Compose BOM** | `2024.10.01` | Apache 2.0 | Maven Central |
| **Room / Hilt / DataStore** | `2.8.4` / `2.59.2` | Apache 2.0 | Maven Central |
| **Kotlin Coroutines / Serialization** | `1.9.0` / `1.7.3` | Apache 2.0 | Maven Central |
| **FFmpegKit Maintained** | `8.1.7` | LGPL 2.1 / GPL 3.0 | `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7` (Maven Central). **Resolves cleanly on new builds.** |
| **smart-exception-java** | `0.2.1` | Apache 2.0 | Maven Central |
| **NanoHTTPD & WebSocket** | `2.3.1` | BSD 3-Clause | Maven Central |
| **OkHttp** | `4.12.0` | Apache 2.0 | Maven Central |
| **JAudioTagger** | `2.3.15` | LGPL 3.0 | JitPack (`https://jitpack.io`) |
| **Glide** | `4.16.0` | BSD / MIT / Apache 2.0 | Maven Central |
| **Retrofit & Gson** | `2.11.0` | Apache 2.0 | Maven Central |
| **Google Cast Framework** | `21.4.0` | Proprietary / Android SDK | Google Maven |

### Source Code Ownership & Licensing Audit
- **Audit Result**: All Kotlin source files under `app/src/main/java/com/mediaplayer/` are custom original project files. No GPL or third-party header licenses exist in source files. Safe for GPL-3 public core release.

---

## 5. Proposal

### Proposed Module Architecture
```
:core:model   (Domain models, interfaces: Track, LyricLine, PlayerState, CastState, Preferences)
:core:audio   (ExoPlayer setup, Eac3Transcoder, BiquadEQ, AudioProcessors, EqualizerManager, DolbyInfo)
:core:ui      (VinylStage, NowPlayingArtwork, 17 Visualizers, LyricsContent, WavySeekBar, Theme)
:core:cast    (MBoxCastServer, LocalAudioServer, SamsungTvManager, SamsungTvLauncher, SpectrumCalculator)
:app          (MBoxPlayer App: Local MediaStore Scanner, Room DB, Library Screens, ViewModels)
```

### Migration Order
1. **Step 1: Extracted `:core:model` (Low Risk)**:
   - Move `Track`, `LyricLine`, `AudioSignalPath`, `PlayerPreferences` to `:core:model`.
   - Define interfaces `TrackRepository`, `PlayerController`, `CastController`.
2. **Step 2: Extracted `:core:audio` (Medium Risk)**:
   - Move `BiquadEqAudioProcessor`, `EqualizerManager`, `Eac3Transcoder`, `Eac3JocParser` to `:core:audio`.
3. **Step 3: Extracted `:core:ui` (Low Risk)**:
   - Move `VinylStage`, `NowPlayingArtwork`, 17 canvas visualizers, `LyricsContent` to `:core:ui`.
4. **Step 4: Extracted `:core:cast` (Medium Risk)**:
   - Move `MBoxCastServer`, `LocalAudioServer`, `SamsungTvManager`, `SamsungTvLauncher`, `SpectrumCalculator` to `:core:cast`.

### What Should NOT Be Shared
- Local Room Database entities/DAOs (`TrackDao`, `LibraryTrackDao`, `AppDatabase`).
- Local filesystem scanner & WorkManager worker (`MediaScanner`, `LibrarySyncWorker`).
- App-specific ViewModels (`LibraryViewModel`, `SettingsViewModel`).
