import os
import re
import random
from typing import Optional, List
from fastapi import FastAPI, Query, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from ytmusicapi import YTMusic
import yt_dlp
import requests

app = FastAPI(title="ELARA YTMusic API Engine")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ytm = YTMusic()

YTDL_OPTS = {
    'format': 'bestaudio/best',
    'quiet': True,
    'no_warnings': True,
    'extract_flat': False,
    'noplaylist': True,
}

def parse_duration_to_seconds(dur_str: str) -> int:
    if not dur_str:
        return 180
    parts = str(dur_str).split(':')
    try:
        if len(parts) == 3:
            return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
        elif len(parts) == 2:
            return int(parts[0]) * 60 + int(parts[1])
        elif len(parts) == 1:
            return int(parts[0])
    except Exception:
        pass
    return 180

def get_best_thumbnail(thumbnails: list) -> str:
    if not thumbnails:
        return 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80'
    sorted_thumbs = sorted(thumbnails, key=lambda x: x.get('width', 0), reverse=True)
    return sorted_thumbs[0].get('url', '')

ACCENT_PALETTE = ['#00ff87', '#60efff', '#ec4899', '#8b5cf6', '#06b6d4', '#f59e0b', '#f43f5e', '#a855f7']

@app.get("/api/health")
def health_check():
    return {"status": "ok", "service": "ELARA YTMusic Engine"}

@app.get("/api/search")
def search_music(
    q: str = Query(..., description="Search query"),
    filter_type: Optional[str] = Query(None, alias="filter")
):
    try:
        # Default to 'songs' filter for best results with full metadata
        filter_param = 'songs'
        if filter_type in ['songs', 'artists', 'albums', 'playlists', 'community_playlists']:
            filter_param = filter_type

        results = ytm.search(q, filter=filter_param, limit=25)
        normalized = []

        for idx, item in enumerate(results):
            result_type = item.get('resultType', filter_type or 'song')
            
            # Only include playable song/video results
            if result_type in ['song', 'video']:
                video_id = item.get('videoId')
                if not video_id:
                    continue

                artists = item.get('artists', [])
                artist_name = ", ".join([a.get('name', '') for a in artists if a.get('name')]) or 'Unknown Artist'
                artist_id = artists[0].get('id') if artists else None

                album_info = item.get('album') or {}
                album_name = album_info.get('name', 'Single') if isinstance(album_info, dict) else 'Single'

                duration_str = item.get('duration', '3:00')
                duration_sec = item.get('duration_seconds') or parse_duration_to_seconds(duration_str)

                accent = ACCENT_PALETTE[idx % len(ACCENT_PALETTE)]

                normalized.append({
                    "id": f"yt-{video_id}",
                    "videoId": video_id,
                    "title": item.get('title', 'Unknown Title'),
                    "artist": artist_name,
                    "artistId": artist_id,
                    "album": album_name,
                    "albumArt": get_best_thumbnail(item.get('thumbnails', [])),
                    "audioUrl": f"http://localhost:8000/api/stream/{video_id}",
                    "duration": duration_sec,
                    "genre": "Global Stream",
                    "mood": "ELARA Stream",
                    "accentColor": accent,
                    "vibeCoords": {
                        "x": 15 + ((idx * 27) % 70),
                        "y": 20 + ((idx * 33) % 60)
                    },
                    "energy": 50 + ((idx * 17) % 48),
                    "plays": item.get('views') or 1000000
                })

        return {"query": q, "results": normalized}
    except Exception as e:
        print(f"Search error: {e}")
        return {"query": q, "results": [], "error": str(e)}

TRENDING_SEEDS = [
    "Top Hits 2026",
    "Billboard Hot 100 Hits",
    "Global Viral Music",
    "Top Trending Pop Hits",
    "Global Electronic Dance",
    "Viral Hip Hop Hits",
    "Trending Melodic Chill"
]

@app.get("/api/charts")
def get_charts(randomize: bool = Query(True)):
    try:
        candidate_songs = []

        # 1. Fetch official global & US charts
        try:
            charts = ytm.get_charts(country="US")
            songs = charts.get('videos', {}).get('items', []) or charts.get('songs', {}).get('items', [])
            candidate_songs.extend(songs)
        except Exception:
            pass

        # 2. Fetch from a random trending query seed for variety
        try:
            random_seed = random.choice(TRENDING_SEEDS)
            search_items = ytm.search(random_seed, filter="songs", limit=20)
            candidate_songs.extend(search_items)
        except Exception:
            pass

        # 3. Deduplicate by videoId
        seen_ids = set()
        unique_songs = []
        for s in candidate_songs:
            vid = s.get('videoId')
            if vid and vid not in seen_ids:
                seen_ids.add(vid)
                unique_songs.append(s)

        # 4. Shuffle for dynamic celestial orbit
        if randomize:
            random.shuffle(unique_songs)

        normalized_songs = []
        for idx, item in enumerate(unique_songs[:28]):
            video_id = item.get('videoId')
            if not video_id:
                continue

            artists = item.get('artists', [])
            artist_name = ", ".join([a.get('name', '') for a in artists if a.get('name')]) or 'Trending Artist'
            accent = ACCENT_PALETTE[idx % len(ACCENT_PALETTE)]

            # Generate aesthetic non-overlapping galaxy coordinates across 4 quadrants
            quadrant = idx % 4
            if quadrant == 0:  # Top Left
                cx = random.uniform(10, 44)
                cy = random.uniform(12, 44)
            elif quadrant == 1:  # Top Right
                cx = random.uniform(54, 88)
                cy = random.uniform(12, 44)
            elif quadrant == 2:  # Bottom Left
                cx = random.uniform(10, 44)
                cy = random.uniform(54, 86)
            else:  # Bottom Right
                cx = random.uniform(54, 88)
                cy = random.uniform(54, 86)

            normalized_songs.append({
                "id": f"yt-{video_id}",
                "videoId": video_id,
                "title": item.get('title', 'Trending Hit'),
                "artist": artist_name,
                "album": "ELARA Cosmic Hits",
                "albumArt": get_best_thumbnail(item.get('thumbnails', [])),
                "audioUrl": f"http://localhost:8000/api/stream/{video_id}",
                "duration": parse_duration_to_seconds(item.get('duration', '3:30')),
                "genre": "Global Hit",
                "mood": "Celestial Vibe",
                "accentColor": accent,
                "vibeCoords": {
                    "x": round(cx, 1),
                    "y": round(cy, 1)
                },
                "energy": random.randint(55, 98),
                "plays": random.randint(1500000, 9500000)
            })

        return {"trending": normalized_songs}
    except Exception as e:
        print(f"Charts error: {e}")
        return {"trending": []}

@app.get("/api/explore/playlists")
def get_explore_playlists():
    """
    Returns curated YTMusic playlists for Explore & Search.
    """
    try:
        results = ytm.search("Popular Music Hits Playlist", filter="community_playlists", limit=12)
        playlists = []
        for idx, item in enumerate(results):
            browse_id = item.get('browseId')
            if not browse_id:
                continue
            playlists.append({
                "id": browse_id,
                "name": item.get('title', 'Featured Playlist'),
                "description": f"Curated by {item.get('author', 'ELARA')} • {item.get('itemCount', '')} songs",
                "coverArt": get_best_thumbnail(item.get('thumbnails', [])),
                "tracks": [],
                "color": ACCENT_PALETTE[idx % len(ACCENT_PALETTE)],
                "createdBy": item.get('author', 'ELARA Curated')
            })
        return {"playlists": playlists}
    except Exception as e:
        print(f"Explore playlists error: {e}")
        return {"playlists": []}

@app.get("/api/playlist/{playlist_id}")
def get_playlist_tracks(playlist_id: str):
    """
    Fetches full track list for a playlist.
    """
    try:
        data = ytm.get_playlist(playlist_id, limit=50)
        tracks_data = data.get('tracks', [])
        
        normalized_tracks = []
        for idx, item in enumerate(tracks_data):
            video_id = item.get('videoId')
            if not video_id:
                continue

            artists = item.get('artists', [])
            artist_name = ", ".join([a.get('name', '') for a in artists if a.get('name')]) or 'Various Artists'
            album_name = item.get('album', {}).get('name', 'Playlist Track') if isinstance(item.get('album'), dict) else 'Playlist Track'
            accent = ACCENT_PALETTE[idx % len(ACCENT_PALETTE)]

            normalized_tracks.append({
                "id": f"yt-{video_id}",
                "videoId": video_id,
                "title": item.get('title', 'Unknown Title'),
                "artist": artist_name,
                "album": album_name,
                "albumArt": get_best_thumbnail(item.get('thumbnails', [])),
                "audioUrl": f"http://localhost:8000/api/stream/{video_id}",
                "duration": parse_duration_to_seconds(item.get('duration', '3:30')),
                "genre": "Curated Playlist",
                "accentColor": accent,
                "addedAt": "Today"
            })

        return {
            "id": playlist_id,
            "name": data.get('title', 'Featured Playlist'),
            "description": data.get('description', 'Curated Playlist Collection'),
            "coverArt": get_best_thumbnail(data.get('thumbnails', [])),
            "tracks": normalized_tracks,
            "createdBy": data.get('author', {}).get('name', 'ELARA Curated') if isinstance(data.get('author'), dict) else 'ELARA Curated'
        }
    except Exception as e:
        print(f"Get playlist error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/stream/{video_id}")
def stream_audio(video_id: str, request: Request):
    try:
        url = f"https://www.youtube.com/watch?v={video_id}"
        with yt_dlp.YoutubeDL(YTDL_OPTS) as ydl:
            info = ydl.extract_info(url, download=False)
            
            audio_url = None
            if 'url' in info:
                audio_url = info['url']
            elif 'formats' in info:
                audio_formats = [f for f in info['formats'] if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                if not audio_formats:
                    audio_formats = [f for f in info['formats'] if f.get('acodec') != 'none']
                if audio_formats:
                    audio_url = audio_formats[-1].get('url')

            if not audio_url:
                raise HTTPException(status_code=404, detail="Audio stream format not found")

            headers = {}
            if "range" in request.headers:
                headers["Range"] = request.headers["range"]

            upstream_res = requests.get(audio_url, headers=headers, stream=True, timeout=10)
            
            response_headers = {
                "Accept-Ranges": "bytes",
                "Content-Type": upstream_res.headers.get("Content-Type", "audio/mp4"),
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
                "Access-Control-Allow-Headers": "*"
            }
            if "Content-Range" in upstream_res.headers:
                response_headers["Content-Range"] = upstream_res.headers["Content-Range"]
            if "Content-Length" in upstream_res.headers:
                response_headers["Content-Length"] = upstream_res.headers["Content-Length"]

            return StreamingResponse(
                upstream_res.iter_content(chunk_size=64 * 1024),
                status_code=upstream_res.status_code,
                headers=response_headers
            )
    except Exception as e:
        print(f"Streaming error for {video_id}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/lyrics/{video_id}")
def get_track_lyrics(video_id: str):
    try:
        watch_playlist = ytm.get_watch_playlist(videoId=video_id)
        lyrics_browse_id = watch_playlist.get('lyrics')
        if not lyrics_browse_id:
            return {"lyrics": [], "text": None}

        lyrics_data = ytm.get_lyrics(lyrics_browse_id)
        lyrics_text = lyrics_data.get('lyrics', '')
        
        lines = []
        for idx, line in enumerate(lyrics_text.split('\n')):
            if line.strip():
                lines.append({
                    "id": str(idx),
                    "time": idx * 5.5,
                    "text": line.strip()
                })

        return {"lyrics": lines, "text": lyrics_text}
    except Exception as e:
        print(f"Lyrics error: {e}")
        return {"lyrics": [], "text": None}

@app.get("/api/download/{video_id}")
def download_track(video_id: str):
    """
    Returns a downloadable MP4 stream for a given YouTube video_id.
    Uses yt-dlp to extract the best mp4 format with video+audio merged.
    """
    try:
        url = f"https://www.youtube.com/watch?v={video_id}"
        dl_opts = {
            'format': 'best[ext=mp4]/bestvideo[ext=mp4]+bestaudio[ext=m4a]/best',
            'quiet': True,
            'no_warnings': True,
            'noplaylist': True,
        }
        with yt_dlp.YoutubeDL(dl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            download_url = None
            title = info.get('title', video_id)

            if 'url' in info:
                download_url = info['url']
            elif 'formats' in info:
                # Prefer mp4 formats with both video and audio
                mp4_formats = [
                    f for f in info['formats']
                    if f.get('ext') == 'mp4'
                    and f.get('acodec') != 'none'
                    and f.get('vcodec') != 'none'
                ]
                if mp4_formats:
                    # Pick the highest quality mp4
                    mp4_formats.sort(key=lambda f: f.get('height', 0) or 0, reverse=True)
                    download_url = mp4_formats[0].get('url')
                else:
                    # Fallback: any format with both streams
                    combined = [
                        f for f in info['formats']
                        if f.get('acodec') != 'none' and f.get('vcodec') != 'none'
                    ]
                    if combined:
                        download_url = combined[-1].get('url')
                    elif info['formats']:
                        download_url = info['formats'][-1].get('url')

            if not download_url:
                raise HTTPException(status_code=404, detail="No downloadable MP4 format found")

            # Sanitize filename
            safe_title = re.sub(r'[^\w\s\-]', '', title).strip()[:80]
            filename = f"{safe_title}.mp4"

            upstream_res = requests.get(download_url, stream=True, timeout=15)

            response_headers = {
                "Content-Type": "video/mp4",
                "Content-Disposition": f'attachment; filename="{filename}"',
                "Access-Control-Allow-Origin": "*",
            }
            if "Content-Length" in upstream_res.headers:
                response_headers["Content-Length"] = upstream_res.headers["Content-Length"]

            return StreamingResponse(
                upstream_res.iter_content(chunk_size=128 * 1024),
                status_code=200,
                headers=response_headers,
                media_type="video/mp4"
            )
    except HTTPException:
        raise
    except Exception as e:
        print(f"Download error for {video_id}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
