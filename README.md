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




