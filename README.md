# 🎧 M-BOX Purist & Music Player

![M-BOX Logo](https://img.shields.io/badge/M--BOX-Purist-F25C05?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/python-3670A0?style=for-the-badge&logo=python&logoColor=ffdd54)

**M-BOX Purist** is a premium, audiophile-grade local media player built with modern Android development standards. Designed for high-fidelity audio enthusiasts, it features a stunning 3D-inspired UI, an advanced 10-band Equalizer, and seamlessly integrated Python-powered multimedia processing scripts.

---

## ✨ Key Features

### 📱 Android Application (Jetpack Compose)
* **Audiophile UX/UI:** Deep dark mode with a metallic orange (`#F25C05`) and teal aesthetic.
* **Media3 & ExoPlayer:** Rock-solid audio playback engine supporting high-resolution local files.
* **The "Purist" Player Screen:** Features a responsive, spinning Vinyl/CD album art component that reacts to playback state.
* **Advanced Audio Control:** Built-in 10-band Equalizer with vertical "analog-style" faders, Bass Boost, and Virtualizer dials.
* **Local Media Scanner:** Fast, efficient indexing of local folders, albums, and artists using Room Database.
* **Technical Metadata:** Real-time display of audio format, bitrate, and sample rate.

### 🐍 Python Multimedia Suite
* **`mbox_gui.py` & `mbox_master.py`:** Desktop-side management tools.
* **`downloader.py`:** Efficient media fetching and asset management.
* **`processor.py`:** Batch processing for multimedia files.

---

## 🛠️ Tech Stack

**Android (Frontend & Audio):**
* [Kotlin](https://kotlinlang.org/)
* [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI)
* [Media3 / ExoPlayer](https://developer.android.com/media/media3) (Playback)
* [Dagger-Hilt](https://dagger.dev/hilt/) (Dependency Injection)
* [Room](https://developer.android.com/training/data-storage/room) (Local Database)

**Scripts (Backend Processing):**
* Python 3.x

---

## 🚀 Getting Started

### Running the Android App
1. Open the project in **Android Studio**.
2. Sync the project with Gradle files.
3. Connect an Android device or start an Emulator (API 26+ recommended).
4. Click **Run** (`Shift + F10`).

### Running the Python Scripts
1. Navigate to the root directory.
2. Ensure you have Python installed.
3. Install dependencies (if applicable):
```bash
   pip install -r requirements.txt




# 🚀 v1.0.5: Premium UI Overhaul & Interactive Vinyl Scrubbing

This major update transforms the player into a premium, audiophile-grade experience. We've introduced highly requested tactile interactions, deep audio pipeline data, and gorgeous dynamic theming.

### ✨ New Features & Enhancements

* **🎛️ Physics-Based DJ Scrubbing:** The `VinylAlbumCover` is now fully interactive. Drag the record to seek tracks forward or backward. Release it, and physics-based inertia will smoothly glide the record back to its standard playback speed.
* **🎨 Dynamic Material You Theming:** The player now seamlessly extracts colors from the currently playing Album Art using the Android `Palette` API, dynamically coloring the playback controls, seekbar, and UI accents for a cohesive look.
* **📊 Audiophile "Audio Info" Dashboard:** Tapping the new Codec Badge (e.g., FLAC, OPUS) opens a highly detailed bottom sheet displaying the complete audio processing pipeline—including Decoder, Resampler, DSP, and final hardware Output Device stats.
* **📱 Adaptive Responsive Layout:** The Now Playing screen has been completely rewritten to provide a "perfect fit" experience across both Portrait (Column) and Landscape (Row) orientations without stretching or clipping the album art.
* **⏪ Modernized Controls:** Upgraded the playback controls row to feature a clean, high-contrast, modern circular Play/Pause button that perfectly highlights the dynamic track color.

### 🐛 Under the Hood
* Improved Compose layout weights to handle ultra-wide screens.
* Implemented safe fallback colors for the Palette API to prevent UI blackout on low-contrast album art.
* Replaced redundant image loaders to optimize app size (Exclusively using `GlideImage`).




