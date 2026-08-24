import { Track, LyricLine, Playlist } from '../types/music';

import { offlineStorage } from './offlineStorageService';

const API_BASE = 'http://localhost:8000/api';

export class YTMusicService {
  public static async searchTracks(query: string, filter = 'songs'): Promise<Track[]> {
    if (!query.trim()) return [];

    try {
      const res = await fetch(`${API_BASE}/search?q=${encodeURIComponent(query)}&filter=${filter}`);
      if (!res.ok) throw new Error(`Search failed: ${res.statusText}`);
      const data = await res.json();
      return data.results || [];
    } catch (err) {
      console.warn('YTMusic API search note:', err);
      return [];
    }
  }

  public static async getTrendingTracks(): Promise<Track[]> {
    try {
      const res = await fetch(`${API_BASE}/charts`);
      if (!res.ok) throw new Error(`Charts failed: ${res.statusText}`);
      const data = await res.json();
      return data.trending || [];
    } catch (err) {
      console.warn('YTMusic API trending note:', err);
      return [];
    }
  }

  public static async getExplorePlaylists(): Promise<Playlist[]> {
    try {
      const res = await fetch(`${API_BASE}/explore/playlists`);
      if (!res.ok) throw new Error(`Explore playlists failed: ${res.statusText}`);
      const data = await res.json();
      return data.playlists || [];
    } catch (err) {
      console.warn('YTMusic API explore playlists note:', err);
      return [];
    }
  }

  public static async getPlaylistDetails(playlistId: string): Promise<Playlist | null> {
    try {
      const res = await fetch(`${API_BASE}/playlist/${playlistId}`);
      if (!res.ok) throw new Error(`Playlist details failed: ${res.statusText}`);
      return await res.json();
    } catch (err) {
      console.warn('YTMusic API playlist details note:', err);
      return null;
    }
  }

  public static async getLyrics(videoId: string): Promise<LyricLine[]> {
    try {
      const cleanId = videoId.replace('yt-', '');
      const res = await fetch(`${API_BASE}/lyrics/${cleanId}`);
      if (!res.ok) return [];
      const data = await res.json();
      return data.lyrics || [];
    } catch (err) {
      console.warn('YTMusic API lyrics note:', err);
      return [];
    }
  }

  public static async downloadTrack(track: Track): Promise<boolean> {
    try {
      const cleanId = track.id.replace('yt-', '');
      const downloadUrl = `${API_BASE}/download/${cleanId}`;
      
      // Fetch binary blob
      const res = await fetch(downloadUrl);
      if (!res.ok) throw new Error(`Download failed with status ${res.status}`);
      
      const blob = await res.blob();
      
      // Store in IndexedDB for offline background MP4 playback
      await offlineStorage.saveMP4(track.id, track, blob);
      
      // Trigger file download in browser
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = `${track.title.replace(/[/\\?%*:|"<>]/g, '')}.mp4`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      
      setTimeout(() => URL.revokeObjectURL(blobUrl), 10000);
      return true;
    } catch (err) {
      console.warn('YTMusic download error:', err);
      return false;
    }
  }

  public static async checkHealth(): Promise<boolean> {
    try {
      const res = await fetch(`${API_BASE}/health`, { signal: AbortSignal.timeout(2000) });
      return res.ok;
    } catch {
      return false;
    }
  }
}
