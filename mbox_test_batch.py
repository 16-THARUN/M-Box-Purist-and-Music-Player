import os
import glob
import shutil
from downloader import MboxDownloader
from processor import AudioUpscaler
from mutagen.flac import FLAC, Picture # ✅ NEW: For Car Dashboard Metadata

def inject_metadata(flac_path, title, artist):
    """Bakes the Artist and Title into the 1411kbps file."""
    try:
        audio = FLAC(flac_path)
        audio["title"] = title
        audio["artist"] = artist
        audio["album"] = "MBOX2 AI Masters"
        audio.save()
    except Exception as e:
        print(f"⚠️ Metadata injection failed for {title}: {e}")

def run_sample_batch(playlist_url):
    raw_path = "downloads/raw"
    enhanced_path = "downloads/enhanced"
    
    # 1. Initialize
    downloader = MboxDownloader(download_path=raw_path)
    upscaler = AudioUpscaler(output_path=enhanced_path)
    
    print("="*50)
    print(" 🚀 MBOX2 BATCH: 5-SONG MASTERING")
    print("="*50)

    # Fresh Start
    if os.path.exists(raw_path): shutil.rmtree(raw_path)
    os.makedirs(raw_path)

    # 2. Download (Ensure downloader.py has 'playlist_items': '1-5')
    print("\n📥 Step 1: Downloading Raw WAVs...")
    dl_results = downloader.fetch_and_download(playlist_url)

    # 3. Process and Sync
    raw_files = glob.glob(f"{raw_path}/*.wav")
    
    if not raw_files:
        print("❌ No files found. Check your YouTube link.")
        return

    print(f"\n🧠 Step 2: AI Enhancing {len(raw_files)} tracks...")
    
    for wav_file in raw_files:
        try:
            # Get track name for metadata (clean filename)
            track_name = os.path.basename(wav_file).replace(".wav", "")
            
            # Upscale to 1411kbps
            final_flac = upscaler.process_to_1411(wav_file)
            
            # ✅ NEW: Inject Artist/Title for Android Auto
            # We assume filename is "Title [ID]" or "Artist - Title"
            inject_metadata(final_flac, track_name, "MBOX2 AI")
            
            # 4. Cleanup
            os.remove(wav_file)
            print(f"🗑️ Cleaned raw source for: {track_name}")
            
        except Exception as e:
            print(f"⚠️ Error: {e}")

    print("\n" + "="*50)
    print(" ✅ BATCH COMPLETE!")
    print(f" 📂 Check '{enhanced_path}' for your 1411kbps masters.")
    print("="*50)

if __name__ == "__main__":
    TEST_URL = "https://music.youtube.com/playlist?list=LRYRgEi1Txc-tgjgtZRZPwQgE-VtpsjmMj-PZ"
    run_sample_batch(TEST_URL)