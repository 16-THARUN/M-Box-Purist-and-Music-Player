import os
import shutil
import glob
import csv
import re
import time
import spotipy
from spotipy.oauth2 import SpotifyClientCredentials
import yt_dlp
from processor import AudioUpscaler
from mutagen.flac import FLAC, Picture

try:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.common.by import By
    _SELENIUM_AVAILABLE = True
except ImportError:
    _SELENIUM_AVAILABLE = False

# --- CREDENTIALS & CONFIGURATION ---
CLIENT_ID = "dbe07f7ba6424a25976c752e23db72d9"
CLIENT_SECRET = "f0edab2a62e64568b1cee3d3594d5cd8"

TRUSTED_LABELS = [
    "Universal Music India", "Universal Music Group", "UMG", "Sony Music India", 
    "T-Series", "Zee Music", "WaterTower Music", "SonySoundtracksVEVO", "- Topic",
    "The Score Vault"
]

BLOCKED_TAGS = re.compile(
    r'(?i)\b(remix|8d\s*audio|bass\s*boost|slowed|reverb|nightcore|'
    r'sped[\s_]up|karaoke|live\s+version|acoustic|extended\s*mix|mashup|cover|fan\s*made)\b'
)

# 👇 Define the folder where FFmpeg lives
FFMPEG_DIR = r"C:\Users\HP\Documents\video_converter"

class KivySafeLogger:
    def __init__(self, log_func):
        self.log_func = log_func
    def debug(self, msg): pass
    def info(self, msg): pass
    def warning(self, msg): 
        if "javascript" not in msg.lower() and "js runtime" not in msg.lower():
            self.log_func(f"⚠️ {msg}")
    def error(self, msg): 
        if "javascript" in msg.lower() or "js runtime" in msg.lower():
            pass 
        elif "ffmpeg" in msg.lower():
            self.log_func("🚨 WARNING: FFmpeg is missing. Attempting direct audio processing...")
        else:
            self.log_func(f"🚨 {msg}")

# ===========================================================================
# 1. THE UNIVERSAL DOWNLOADER ENGINE
# ===========================================================================
class MboxDownloader:
    def __init__(self, download_path="downloads/raw", progress_callback=None):
        self.download_path = download_path
        self.progress_callback = progress_callback
        os.makedirs(self.download_path, exist_ok=True)
        
        self.sp = spotipy.Spotify(auth_manager=SpotifyClientCredentials(
            client_id=CLIENT_ID, client_secret=CLIENT_SECRET))

    def _log(self, msg):
        if self.progress_callback: self.progress_callback(msg)
        else: print(msg)

    def fetch_and_download(self, target_input, mode="1"):
        self._log(f"\n🌐 Analyzing Target...")
        target_input = target_input.strip().strip('"').strip("'")
        queue = []

        try:
            if target_input.endswith(".csv") and os.path.exists(target_input):
                self._log("📄 Detected: Local CSV. Upgrading metadata...")
                queue = self._scrape_csv(target_input)
                
            elif "spotify.com" in target_input or "spotify.com" in target_input:
                self._log("📡 Detected: Spotify. Utilizing native API...")
                queue = self._scrape_spotify(target_input)

            elif "youtube.com" in target_input or "youtu.be" in target_input:
                self._log("📺 Detected: YouTube. Extracting data...")
                queue = self._scrape_youtube(target_input)

            elif "amazon" in target_input or "apple" in target_input:
                self._log("🛒 Detected: Walled Garden. Deploying stealth web-scraper...")
                queue = self._scrape_walled_garden(target_input)
                
            else:
                self._log(f"🔎 Detected Search Query: {target_input}")
                queue = [{'title': target_input, 'artist': '', 'isrc': 'N/A', 'direct_url': None}]

            if queue:
                self._log(f"✅ Target isolated ({len(queue)} items). Engaging download pipeline...")
                self._batch_download(queue)
            else:
                self._log("❌ No items added to queue.")
                
        except Exception as e:
            self._log(f"❌ Ingestion Error: {e}")

    # =========================================================================
    # PLATFORM SPECIFIC EXTRACTORS
    # =========================================================================
    def _scrape_csv(self, csv_path):
        queue = []
        with open(csv_path, mode='r', encoding='utf-8-sig') as infile:
            reader = csv.DictReader(infile)
            reader.fieldnames = [h.strip().lower() for h in reader.fieldnames]
            for row in reader:
                title, artist = row.get('track name') or row.get('title'), row.get('artist name') or row.get('artist')
                isrc = row.get('isrc')
                if title and artist:
                    if not isrc or isrc == 'N/A':
                        res = self.sp.search(q=f"track:{title} artist:{artist}", type='track', limit=1)
                        isrc = res['tracks']['items'][0]['external_ids'].get('isrc') if res['tracks']['items'] else 'N/A'
                    queue.append({'title': title, 'artist': artist, 'isrc': isrc, 'direct_url': None})
        return queue

    def _scrape_spotify(self, url):
        playlist_id = url.split('/')[-1].split('?')[0]
        results = self.sp.playlist_tracks(playlist_id)
        queue = []
        for item in results['items']:
            if not item or not item.get('track'): continue
            t = item['track']
            title = t.get('name', 'Unknown Title')
            artist = t['artists'][0]['name'] if t.get('artists') else "Unknown Artist"
            isrc = t.get('external_ids', {}).get('isrc', 'N/A')
            queue.append({'title': title, 'artist': artist, 'isrc': isrc, 'direct_url': None})
        return queue

    def _scrape_youtube(self, url):
        queue = []
        ydl_opts = {
            'extract_flat': True, 
            'quiet': True, 
            'no_warnings': True,
            'ffmpeg_location': FFMPEG_DIR, # 👈 Added folder path here!
            'logger': KivySafeLogger(self._log)
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            entries = info.get('entries', [info])
            for entry in entries:
                if not entry: continue
                artist = entry.get('uploader') or entry.get('channel', 'Unknown Artist')
                direct_url = entry.get('url') or entry.get('webpage_url') or url 
                queue.append({'title': entry.get('title'), 'artist': artist, 'isrc': 'N/A', 'direct_url': direct_url})
        return queue

    def _scrape_walled_garden(self, url):
        if not _SELENIUM_AVAILABLE: return []
        queue = []
        options = Options()
        options.add_argument('--headless=new')
        options.add_argument('--log-level=3')
        driver = webdriver.Chrome(options=options)
        try:
            driver.get(url)
            time.sleep(5)
            lines = [line.strip() for line in driver.find_element(By.TAG_NAME, 'body').text.split('\n') if line.strip()]
            for i in range(0, len(lines)-1, 2):
                title, artist = lines[i], lines[i+1]
                if len(title) > 2 and len(artist) > 2:
                    res = self.sp.search(q=f"track:{title} artist:{artist}", type='track', limit=1)
                    isrc = res['tracks']['items'][0]['external_ids'].get('isrc') if res['tracks']['items'] else 'N/A'
                    queue.append({'title': title, 'artist': artist, 'isrc': isrc, 'direct_url': None})
        finally:
            driver.quit()
        return queue

    def _batch_download(self, queue):
        for index, track in enumerate(queue, 1):
            isrc = track.get('isrc')
            title = track.get('title')
            artist = track.get('artist')
            direct_url = track.get('direct_url')

            if direct_url:
                query = direct_url
                self._log(f" [{index}/{len(queue)}] Targeting Direct Link: {title}")
                # 👇 FIX: Skip Gatekeeper for Direct Links!
                self._trigger_ytdlp(query, apply_gatekeeper=False) 
            elif isrc and isrc != 'N/A':
                query = f"ytsearch5:isrc:{isrc}"
                self._log(f" [{index}/{len(queue)}] Targeting ISRC: {isrc}")
                self._trigger_ytdlp(query, apply_gatekeeper=True)
            else:
                query = f"ytsearch5:\"{title}\" \"{artist}\" full audio lyric"
                self._log(f" [{index}/{len(queue)}] Targeting Fallback: {title}")
                self._trigger_ytdlp(query, apply_gatekeeper=True)

    def _trigger_ytdlp(self, query, apply_gatekeeper=True):
        ydl_opts = {
            'format': 'bestaudio/best',
            'outtmpl': os.path.join(self.download_path, '%(title)s.%(ext)s'),
            'writethumbnail': True,
            'ffmpeg_location': FFMPEG_DIR, # 👈 Added folder path here!
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'wav',
                'preferredquality': '0' 
            }],
            'quiet': False, 
            'no_warnings': False, 
            'ignoreerrors': False, 
            'noprogress': True,
            'logger': KivySafeLogger(self._log)
        }

        # Only run the gatekeeper if we are searching (not for direct links)
        if apply_gatekeeper:
            ydl_opts['match_filter'] = self._label_gatekeeper
        
        self._log("⏳ Passing command to yt-dlp...")
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([query])
        except Exception as e:
            self._log(f"🚨 YT-DLP CRASHED: {e}")

    def _label_gatekeeper(self, info):
        title, desc, uploader = info.get('title', ''), info.get('description', '').lower(), info.get('uploader', '')
        
        if BLOCKED_TAGS.search(title): 
            self._log(f"🛡️ GATEKEEPER: Blocked '{title}' (Fan modification detected).")
            return "Rejected: Fan modification detected."
            
        video_signals = r'(?i)\b(official\s+video|video\s+song|music\s+video|full\s+video|4k\s+video|movie\s+scene|clip)\b'
        audio_signals = r'(?i)\b(lyric|lyrical|full\s+audio|audio\s+song|audio\s+track|art\s+track)\b'
        if re.search(video_signals, title) and not re.search(audio_signals, title):
            self._log(f"🛡️ GATEKEEPER: Blocked '{title}' (Cinematic video - SFX risk).")
            return "Rejected: Cinematic video version (SFX risk)."
            
        if "provided to youtube" in desc or "music metadata provided by" in desc: 
            return None
            
        if any(label.lower() in uploader.lower() for label in TRUSTED_LABELS): 
            return None
            
        self._log(f"🛡️ GATEKEEPER: Blocked '{uploader}' (Not in Trusted Labels list!).")
        return "Rejected: Missing official label signature."

# ===========================================================================
# 2. METADATA INJECTION
# ===========================================================================
def inject_metadata(flac_path: str, original_name: str, raw_folder: str, extra_tags: dict | None = None):
    try:
        audio = FLAC(flac_path)
        if " - " in original_name:
            parts = original_name.split(" - ", maxsplit=1)
            title, artist = parts[0].strip(), parts[1].strip()
        else:
            title = original_name.strip()
            artist = extra_tags.get("artist", "MBOX2 Crystal Master") if extra_tags else "MBOX2 Crystal Master"

        audio["title"]  = [title]
        audio["artist"] = [artist]
        audio["album"]  = [extra_tags.get("album", "M-BOX2 Purist Collection") if extra_tags else "M-BOX2 Purist Collection"]
        audio["genre"]  = [extra_tags.get("genre", "Cinematic BGM") if extra_tags else "Cinematic BGM"]

        search_pattern = glob.escape(os.path.join(raw_folder, original_name)) + ".*"
        images = [f for f in glob.glob(search_pattern) if f.rsplit('.', 1)[-1].lower() in ('jpg', 'jpeg', 'png', 'webp')]
        
        if images:
            image_path = images[0]
            ext = image_path.rsplit('.', 1)[-1].lower()
            with open(image_path, "rb") as fh:
                pic = Picture()
                pic.data = fh.read()
                pic.type = 3
                pic.mime = {'webp': 'image/webp', 'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'png': 'image/png'}.get(ext, 'image/png')
            audio.add_picture(pic)
            
        audio.save()
    except Exception as e:
        print(f"⚠️  Metadata error on {original_name!r}: {e}")

# ===========================================================================
# 3. MAIN PIPELINE ORCHESTRATOR
# ===========================================================================
def run_mbox_pipeline(target_input: str, mode: str = "1", progress_callback=None, extra_tags: dict | None = None, stop_checker=None):
    raw_folder = r"C:\Users\HP\mbox2\downloads\raw"
    enhanced_folder = r"C:\Users\HP\mbox2\downloads\enhanced"

    def log(msg: str):
        if progress_callback: progress_callback(msg)
        else: print(msg)

    dl = MboxDownloader(download_path=raw_folder, progress_callback=log)
    proc = AudioUpscaler(output_path=enhanced_folder, progress_callback=log)

    log("\n" + "💎" * 25)
    log("🎬 MBOX2 PURIST MASTERING — STARTING")
    log("💎" * 25)

    if os.path.exists(raw_folder): shutil.rmtree(raw_folder, ignore_errors=True)
    os.makedirs(raw_folder, exist_ok=True)
    os.makedirs(enhanced_folder, exist_ok=True)

    log("\n📥 STEP 1: FETCHING PURIST SOURCES…")
    dl.fetch_and_download(target_input, mode=mode)

    log("\n💎 STEP 2: CRYSTAL MASTERING…")
    
    valid_ext = ('.wav', '.webm', '.m4a', '.opus', '.mp3', '.ogg', '.aac')
    files_to_process = sorted(f for f in os.listdir(raw_folder) if f.lower().endswith(valid_ext))

    if not files_to_process:
        log("❌ No raw audio files found in the download folder to master.")
        return

    total = len(files_to_process)
    for i, file in enumerate(files_to_process, 1):
        if stop_checker and stop_checker():
            log("\n⚠️ PIPELINE ABORTED BY USER. Stopping safely...")
            break

        input_path = os.path.join(raw_folder, file)
        original_name = os.path.splitext(file)[0]
        log(f"\n[{i}/{total}] Mastering: {file}")

        try:
            final_flac = proc.process_to_1411(input_path)
            if final_flac and os.path.exists(final_flac):
                inject_metadata(final_flac, original_name, raw_folder, extra_tags)

            os.remove(input_path)
            for leftover in glob.glob(glob.escape(os.path.join(raw_folder, original_name)) + ".*"):
                try: os.remove(leftover)
                except: pass

            if progress_callback: progress_callback(f"__PROGRESS__{int(i / total * 100)}")

        except Exception as e:
            log(f"⚠️  Mastering error on {file!r}: {e}")

    log("\n" + "=" * 50)
    log("🎯 MBOX2 SESSION COMPLETE")
    log(f"📂 Location: {os.path.abspath(enhanced_folder)}")
    log("=" * 50 + "\n")