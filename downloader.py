import spotipy
from spotipy.oauth2 import SpotifyClientCredentials
import yt_dlp
import csv
import os
import re
import time

# Optional: Required for Amazon/Apple Music direct link scraping
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

# 🏛️ HIGH-TRUST AUTHORITIES
TRUSTED_LABELS = [
    "Universal Music India", "Universal Music Group", "UMG", "Sony Music India", 
    "T-Series", "Zee Music", "WaterTower Music", "SonySoundtracksVEVO", "- Topic"
]

# 🛡️ THE PURIST SHIELD (Hardened against standard modifications)
BLOCKED_TAGS = re.compile(
    r'(?i)\b(remix|8d\s*audio|bass\s*boost|slowed|reverb|nightcore|'
    r'sped[\s_]up|karaoke|live\s+version|acoustic|extended\s*mix|mashup|cover|fan\s*made)\b'
)

class UniversalFlacPipeline:
    def __init__(self, download_path="downloads/flac_masters"):
        self.download_path = download_path
        os.makedirs(self.download_path, exist_ok=True)
        
        # Spotify Auth for Metadata Extraction & Upgrading
        self.sp = spotipy.Spotify(auth_manager=SpotifyClientCredentials(
            client_id=CLIENT_ID, client_secret=CLIENT_SECRET))

    # =========================================================================
    # STAGE 1: THE INGESTION ROUTER
    # =========================================================================
    def ingest_target(self, target_input):
        """THE ROUTER: Automatically detects the platform and generates the CSV ledger."""
        print(f"\n🌐 INGESTION NODE: Analyzing Target...")
        target_input = target_input.strip().strip('"').strip("'")
        
        queue = []
        csv_filename = ""

        try:
            # 1. LOCAL CSV (Fallback / Manual)
            if target_input.endswith(".csv") and os.path.exists(target_input):
                print("📄 Detected: Local CSV. Upgrading metadata...")
                queue, csv_filename = self._scrape_csv(target_input)
                
            # 2. SPOTIFY PLAYLIST (The Gold Standard)
            elif "spotify.com" in target_input:
                print("📡 Detected: Spotify. Utilizing native API...")
                queue, csv_filename = self._scrape_spotify(target_input)

            # 3. YOUTUBE / YOUTUBE MUSIC PLAYLIST
            elif "youtube.com" in target_input or "youtu.be" in target_input:
                print("📺 Detected: YouTube. Utilizing yt-dlp flat-extraction...")
                queue, csv_filename = self._scrape_youtube(target_input)

            # 4. AMAZON / APPLE MUSIC (The Stealth Scraper)
            elif "amazon" in target_input or "apple" in target_input:
                print("🛒 Detected: Walled Garden. Deploying stealth web-scraper...")
                queue, csv_filename = self._scrape_walled_garden(target_input)
                
            else:
                # 5. SINGLE SONG FALLBACK
                print("🔎 Detected: Direct Search Query.")
                queue = [{'title': target_input, 'artist': '', 'isrc': 'N/A'}]

            if queue:
                if csv_filename:
                    print(f"✅ Ledger created: {csv_filename}. Engaging FLAC pipeline...")
                self._batch_download(queue)
                
        except Exception as e:
            print(f"❌ Ingestion Error: {e}")

    # =========================================================================
    # STAGE 2: PLATFORM SPECIFIC EXTRACTORS
    # =========================================================================
    def _scrape_csv(self, csv_path):
        """Reads Amazon/Custom CSV, upgrades with Spotify ISRCs, generates standardized ledger."""
        queue = []
        csv_filename = f".catalog_upgraded_{os.path.basename(csv_path)}"
        
        with open(csv_path, mode='r', encoding='utf-8-sig') as infile:
            reader = csv.DictReader(infile)
            reader.fieldnames = [h.strip().lower() for h in reader.fieldnames]
            
            with open(csv_filename, mode='w', encoding='utf-8-sig', newline='') as outfile:
                writer = csv.writer(outfile)
                writer.writerow(["Title", "Artist", "ISRC"])
                
                for row in reader:
                    title = row.get('track name') or row.get('title')
                    artist = row.get('artist name') or row.get('artist')
                    isrc = row.get('isrc') # If it already has one
                    
                    if title and artist:
                        # Upgrade missing ISRCs via Spotify Search
                        if not isrc or isrc == 'N/A':
                            res = self.sp.search(q=f"track:{title} artist:{artist}", type='track', limit=1)
                            isrc = res['tracks']['items'][0]['external_ids'].get('isrc') if res['tracks']['items'] else 'N/A'
                            print(f"  🔍 Upgraded: {title} -> ISRC: {isrc}")
                        
                        writer.writerow([title, artist, isrc])
                        queue.append({'title': title, 'artist': artist, 'isrc': isrc})
                        
        return queue, csv_filename

    def _scrape_spotify(self, url):
        playlist_id = url.split('/')[-1].split('?')[0]
        results = self.sp.playlist_tracks(playlist_id)
        
        csv_filename = f".catalog_spotify_{playlist_id[:8]}.csv"
        queue = []
        
        with open(csv_filename, mode='w', encoding='utf-8-sig', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(["Title", "Artist", "ISRC"])
            
            for item in results['items']:
                if not item or not item.get('track'): continue
                t = item['track']
                title = t.get('name', 'Unknown Title')
                artist = t['artists'][0]['name'] if t.get('artists') else "Unknown Artist"
                isrc = t.get('external_ids', {}).get('isrc', 'N/A')
                
                writer.writerow([title, artist, isrc])
                queue.append({'title': title, 'artist': artist, 'isrc': isrc})
        
        return queue, csv_filename

    def _scrape_youtube(self, url):
        ydl_opts = {'extract_flat': True, 'quiet': True}
        queue = []
        csv_filename = ".catalog_youtube_playlist.csv"
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            entries = info.get('entries', [info]) # Handle single videos gracefully
            
            with open(csv_filename, mode='w', encoding='utf-8-sig', newline='') as f:
                writer = csv.writer(f)
                writer.writerow(["Title", "Uploader", "ISRC"])
                
                for entry in entries:
                    if not entry: continue
                    title, uploader = entry.get('title'), entry.get('uploader')
                    writer.writerow([title, uploader, "N/A"])
                    queue.append({'title': title, 'artist': uploader, 'isrc': 'N/A'})
                    
        return queue, csv_filename

    def _scrape_walled_garden(self, url):
        if not _SELENIUM_AVAILABLE:
            print("❌ Selenium not installed. Run: pip install selenium webdriver-manager")
            return [], ""

        queue = []
        csv_filename = ".catalog_walled_garden.csv"
        
        options = Options()
        options.add_argument('--headless=new')
        options.add_argument('--log-level=3')
        driver = webdriver.Chrome(options=options)
        
        try:
            driver.get(url)
            time.sleep(5) # Wait for DOM
            page_text = driver.find_element(By.TAG_NAME, 'body').text
            lines = [line.strip() for line in page_text.split('\n') if line.strip()]
            
            with open(csv_filename, mode='w', encoding='utf-8-sig', newline='') as f:
                writer = csv.writer(f)
                writer.writerow(["Title", "Artist", "ISRC"])
                
                # Heuristic pairing (Title -> Artist)
                for i in range(0, len(lines)-1, 2):
                    title, artist = lines[i], lines[i+1]
                    if len(title) > 2 and len(artist) > 2:
                        res = self.sp.search(q=f"track:{title} artist:{artist}", type='track', limit=1)
                        isrc = res['tracks']['items'][0]['external_ids'].get('isrc') if res['tracks']['items'] else 'N/A'
                        
                        writer.writerow([title, artist, isrc])
                        queue.append({'title': title, 'artist': artist, 'isrc': isrc})
        finally:
            driver.quit()

        return queue, csv_filename

    # =========================================================================
    # STAGE 3: THE DOWNLOAD & MASTERING ENGINE
    # =========================================================================
    def _batch_download(self, queue):
        """Executes the precision-targeted FLAC conversion."""
        for index, track in enumerate(queue, 1):
            isrc, title, artist = track.get('isrc'), track.get('title'), track.get('artist')
            
            # If we have an exact ISRC, we force that database lookup
            if isrc and isrc != 'N/A':
                query = f"ytsearch5:isrc:{isrc}"
                print(f" [{index}/{len(queue)}] Targeting ISRC: {isrc} ({title})")
            else:
                # Fallback to smart search ensuring we get Audio/Lyric versions
                query = f"ytsearch5:\"{title}\" \"{artist}\" full audio lyric"
                print(f" [{index}/{len(queue)}] Targeting Fallback: {title}")
                
            self._trigger_ytdlp(query)

    def _trigger_ytdlp(self, query):
        ydl_opts = {
            'format': 'bestaudio/best',
            'match_filter': self._label_gatekeeper, 
            'outtmpl': os.path.join(self.download_path, '%(title)s.%(ext)s'),
            'writethumbnail': True,
            'postprocessors': [
                {
                    'key': 'FFmpegExtractAudio',
                    'preferredcodec': 'flac',
                    'preferredquality': '0' 
                },
                {'key': 'FFmpegMetadata'}, 
            ],
            'quiet': True, 
            'no_warnings': True, 
            'ignoreerrors': True
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([query])

    def _label_gatekeeper(self, info):
        """The Split-Filter Decision Matrix for Pristine Audio."""
        title = info.get('title', '')
        desc = info.get('description', '').lower()
        uploader = info.get('uploader', '')

        # 1. PURIST SHIELD: Reject user-modified audio
        if BLOCKED_TAGS.search(title):
            return "Rejected: Fan modification detected."

        # 2. CINEMATIC STRIP: Reject explicit videos unless marked as lyric/audio
        video_signals = r'(?i)\b(official\s+video|video\s+song|music\s+video|full\s+video|4k\s+video|movie\s+scene|clip)\b'
        audio_signals = r'(?i)\b(lyric|lyrical|full\s+audio|audio\s+song|audio\s+track|art\s+track)\b'
        if re.search(video_signals, title) and not re.search(audio_signals, title):
            return "Rejected: Cinematic video version (SFX risk)."

        # 3. LABEL PASS: Direct DDEX Delivery Verification
        if "provided to youtube" in desc or "music metadata provided by" in desc:
            return None

        # 4. TRUSTED CHANNEL PASS
        if any(label.lower() in uploader.lower() for label in TRUSTED_LABELS):
            return None
            
        return "Rejected: Missing official label signature."

# --- TEST EXECUTION ---
if __name__ == "__main__":
    engine = UniversalFlacPipeline()
    test_link = input("\n🌐 Paste a Spotify, YouTube, Amazon link, or CSV path: ")
    engine.ingest_target(test_link)