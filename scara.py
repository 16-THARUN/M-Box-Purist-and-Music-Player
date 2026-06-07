import time
from ytmusicapi import YTMusic

class MboxMatcher:
    def __init__(self):
        # Requires your oauth.json from 'ytmusicapi setup'
        self.ytm = YTMusic("client_secret.json")

    def find_master_on_ytm(self, spotify_track):
        title = spotify_track['title']
        artist = spotify_track['artist']
        isrc = spotify_track.get('isrc')

        print(f"🔍 Matching: {title} by {artist} (ISRC: {isrc})")

        # 1. Search by ISRC (The "Silver Bullet" for 1411kbps)
        # In 2026, searching an ISRC directly in YTM returns the official song.
        search_results = self.ytm.search(isrc, filter="songs") if isrc else []
        
        if not search_results:
            # 2. Fallback: Search Title + Artist + "Official"
            query = f"{title} {artist} official"
            search_results = self.ytm.search(query, filter="songs")

        for result in search_results:
            # CRITICAL: Verify this is a 'Song' (High Res) and not a 'Video' (Low Res)
            if result['resultType'] == 'song':
                
                # Check 1: Artist Matching (Case insensitive)
                res_artist = result['artists'][0]['name'].lower()
                if artist.lower() in res_artist or res_artist in artist.lower():
                    
                    # Check 2: The "Topic" Channel Filter
                    # Official songs are linked to 'Artist - Topic' or have specific 'album' info
                    if result.get('album') is not None:
                        print(f"✅ MATCH FOUND: {result['videoId']} (Album: {result['album']['name']})")
                        return {
                            "videoId": result['videoId'],
                            "title": result['title'],
                            "is_official": True
                        }

        print(f"⚠️ No perfect master found for {title}. Skipping to avoid junk audio.")
        return None

# --- Main Logic ---
if __name__ == "__main__":
    matcher = MboxMatcher()
    
    # Example track from your Spotify Importer
    spotify_data = {
        "title": "Blinding Lights",
        "artist": "The Weeknd",
        "isrc": "USUM71922442"
    }
    
    match = matcher.find_master_on_ytm(spotify_data)
    
    if match:
        # Now you can pass this videoId to your yt-dlp downloader 
        # to get the raw FLAC for the AI Upscaler.
        print(f"🚀 Ready for 1411kbps Conversion: {match['videoId']}")