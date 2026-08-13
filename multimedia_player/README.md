# M-Box Player v1.0.7

A fully **offline** Android music player inspired by Poweramp, built with Kotlin, Jetpack Compose, and Media3 ExoPlayer. No internet permission required — everything runs locally on the device.

---

## Features

| Feature | Implementation |
|---|---|
| Local library scan | `MediaStore` + `SAF` |
| Deep tag reading | `JAudioTagger` (ID3v2, FLAC, OGG, M4A, AAC) |
| Background playback | `MediaSessionService` + wake lock |
| Audio focus | Automatic via `ExoPlayer` |
| Headphone unplug pause | `setHandleAudioBecomingNoisy(true)` |
| 10-band equalizer | `android.media.audiofx.Equalizer` |
| EQ presets | Flat, Bass Boost, Treble, Vocal, Rock, Classical, Hip-Hop, Jazz, Pop, Electronic |
| Bass Boost | `android.media.audiofx.BassBoost` |
| Virtualizer (surround) | `android.media.audiofx.Virtualizer` |
| Playlists | Room DB |
| Persistent preferences | Jetpack DataStore |
| Album art | `MediaStore` embedded art + Glide cache |
| Gapless playback | ExoPlayer built-in |
| Notification controls | `MediaSession` → system media controls |
| Material You (dynamic color) | Material 3 + Android 12+ monet |

---

## Project structure

```
app/src/main/java/com/mediaplayer/
├── MediaPlayerApp.kt          # Hilt application
├── MainActivity.kt            # Nav host + bottom bar
├── data/
│   ├── model/
│   │   ├── Track.kt           # Room entity
│   │   └── Playlist.kt        # Playlist + PlaylistTrack entities
│   ├── db/
│   │   ├── MediaDatabase.kt   # Room database
│   │   ├── TrackDao.kt        # Tracks CRUD + queries
│   │   └── PlaylistDao.kt     # Playlists CRUD
│   ├── scanner/
│   │   └── MediaScanner.kt    # MediaStore + JAudioTagger scan
│   └── prefs/
│       └── PlayerPreferences.kt  # DataStore + EqPreset enum
├── service/
│   ├── MediaService.kt        # ExoPlayer MediaSessionService
│   └── EqualizerManager.kt    # AudioEffect (EQ, Bass, Virtualizer)
├── viewmodel/
│   ├── PlayerViewModel.kt     # Playback state + EQ controls
│   └── LibraryViewModel.kt    # Library scan + browse
├── ui/
│   ├── player/
│   │   └── PlayerScreen.kt    # Now-playing full-screen UI
│   ├── library/
│   │   └── LibraryScreen.kt   # Tracks / Albums / Artists / Playlists
│   ├── equalizer/
│   │   └── EqualizerScreen.kt # 10-band EQ + effects
│   └── theme/
│       └── Theme.kt           # Material3 dynamic color
└── di/
    └── AppModule.kt           # Hilt providers
```

---

## Opening in VS Code (Android development)

VS Code works well for browsing and editing the Kotlin source. To get full build + run support you need the Android SDK installed separately.

### 1. Prerequisites

- **Java 17** — [Adoptium JDK 17](https://adoptium.net/)
- **Android SDK** — easiest via [Android Studio](https://developer.android.com/studio) (install once for the SDK, then use VS Code day-to-day)
- **Android SDK command-line tools** — in Android Studio → SDK Manager → SDK Tools → tick "Android SDK Command-line Tools"

### 2. VS Code extensions to install

Search for these in the Extensions sidebar (`Ctrl+Shift+X`):

| Extension | Publisher | Purpose |
|---|---|---|
| **Kotlin** | fwcd | Kotlin syntax, highlighting |
| **Extension Pack for Java** | Microsoft | Java language support |
| **Android iOS Emulator** | DiemasMichiels | Launch emulator from VS Code |
| **Gradle for Java** | Microsoft | Run Gradle tasks in sidebar |
| **XML** | Red Hat | AndroidManifest / layout editing |

### 3. Set up Android SDK path

Create `.vscode/settings.json` in the project root (already included):

```json
{
  "java.home": "/path/to/jdk-17",
  "android.sdk.root": "/path/to/Android/Sdk"
}
```

Find your SDK path:
- **Windows**: `C:\Users\YourName\AppData\Local\Android\Sdk`
- **macOS**: `~/Library/Android/sdk`
- **Linux**: `~/Android/Sdk`

### 4. Build from VS Code terminal

```bash
# Navigate to project root
cd advanced-media-player

# Make gradlew executable (macOS / Linux)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Install on connected device / emulator
./gradlew installDebug

# Run all checks
./gradlew check
```

On **Windows** use `gradlew.bat` instead of `./gradlew`.

### 5. Gradle tasks sidebar

After opening the project, the Gradle extension shows tasks in the sidebar under the elephant icon. Useful tasks:
- `app > build > assembleDebug`
- `app > install > installDebug`
- `app > verification > lint`

---

## Opening in Android Studio (full IDE)

1. **File → Open** → select the `advanced-media-player` folder
2. Wait for Gradle sync to complete
3. Connect a physical device (USB debugging on) or start an AVD
4. Click **Run** ▶

---

## First run

1. Grant **Read media / audio** permission when prompted
2. Tap the **Refresh** icon in the Library to scan your device
3. Tap any track to start playing
4. Use the bottom navigation to switch between Library and Now Playing
5. Tap **Equalizer** on the player screen to open the EQ

---

## Supported formats

All formats supported by ExoPlayer on the device's Android version:
`MP3` · `FLAC` · `AAC / M4A` · `OGG Vorbis` · `WAV` · `OPUS` · `WMA` (via MediaCodec)

---

## Minimum requirements

- Android 8.0 (API 26) or higher
- No internet permission required
