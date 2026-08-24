import { create } from 'zustand';
import { 
  Track, 
  Playlist, 
  VisualizerMode, 
  ViewType, 
  AuraTheme 
} from '../types/music';
import { MOCK_TRACKS, MOCK_PLAYLISTS, EQ_PRESETS } from '../data/mockTracks';
import { audioEngine } from '../services/audioEngine';
import { YTMusicService } from '../services/ytmusicService';
import confetti from 'canvas-confetti';

const STORAGE_KEY_LIKED_TRACKS = 'aurasonic_liked_tracks_v2';
const STORAGE_KEY_PLAYLISTS = 'aurasonic_custom_playlists_v2';
const STORAGE_KEY_THEME = 'aurasonic_theme_v2';

function getStoredLikedTracks(): Track[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_LIKED_TRACKS);
    return saved ? JSON.parse(saved) : [MOCK_TRACKS[0], MOCK_TRACKS[2]];
  } catch {
    return [MOCK_TRACKS[0], MOCK_TRACKS[2]];
  }
}

function getStoredPlaylists(): Playlist[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_PLAYLISTS);
    return saved ? JSON.parse(saved) : MOCK_PLAYLISTS;
  } catch {
    return MOCK_PLAYLISTS;
  }
}

function getStoredTheme(): AuraTheme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_THEME) as AuraTheme;
    return saved || 'cyber-neon';
  } catch {
    return 'cyber-neon';
  }
}

interface MusicStoreState {
  currentTrack: Track | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  isMuted: boolean;
  isShuffle: boolean;
  repeatMode: 'off' | 'all' | 'one';
  queue: Track[];
  history: Track[];
  recentTracks: Track[];
  galaxyTracks: Track[];
  explorePlaylists: Playlist[];
  activeView: ViewType;
  selectedPlaylistId: string | null;
  selectedPlaylistData: Playlist | null;
  selectedArtistId: string | null;
  selectedGenreId: string | null;
  searchQuery: string;
  searchResults: Track[];
  isSearchingLive: boolean;
  isApiOnline: boolean;
  playlists: Playlist[];
  likedTracks: Track[];
  likedTrackIds: string[];
  visualizerMode: VisualizerMode;
  currentTheme: AuraTheme;

  // Modals & Drawers
  isFullscreen: boolean;
  isLyricsOpen: boolean;
  isQueueOpen: boolean;
  isEqualizerOpen: boolean;
  isCreatePlaylistOpen: boolean;
  isUploadTrackOpen: boolean;

  // EQ & Sleep
  selectedEQPresetId: string;
  customEQGains: number[];
  bassBoostEnabled: boolean;
  sleepTimerRemaining: number | null;
  sleepTimerTotalMinutes: number | null;

  // Actions
  init: () => void;
  playTrack: (track: Track, newQueue?: Track[]) => void;
  togglePlay: () => void;
  pauseTrack: () => void;
  resumeTrack: () => void;
  nextTrack: () => void;
  prevTrack: () => void;
  seek: (seconds: number) => void;
  setVolume: (volume: number) => void;
  toggleMute: () => void;
  toggleShuffle: () => void;
  toggleRepeat: () => void;
  toggleLikeTrack: (track: Track | string) => void;
  addToQueue: (track: Track) => void;
  removeFromQueue: (index: number) => void;
  clearQueue: () => void;
  setActiveView: (view: ViewType, params?: { playlistId?: string; playlistData?: Playlist; artistId?: string; genreId?: string }) => void;
  setSearchQuery: (query: string) => void;
  executeLiveSearch: (query: string) => Promise<void>;
  openOnlinePlaylist: (playlist: Playlist) => Promise<void>;
  setTheme: (theme: AuraTheme) => void;

  // Modals
  setIsFullscreen: (open: boolean) => void;
  setIsLyricsOpen: (open: boolean) => void;
  setIsQueueOpen: (open: boolean) => void;
  setIsEqualizerOpen: (open: boolean) => void;
  setIsCreatePlaylistOpen: (open: boolean) => void;
  setIsUploadTrackOpen: (open: boolean) => void;

  // Playlist Management
  createPlaylist: (name: string, description?: string, coverArt?: string) => Playlist;
  addTrackToPlaylist: (playlistId: string, track: Track) => void;
  removeTrackFromPlaylist: (playlistId: string, trackId: string) => void;
  deletePlaylist: (playlistId: string) => void;

  setVisualizerMode: (mode: VisualizerMode) => void;
  setEQPreset: (presetId: string) => void;
  setCustomEQGain: (index: number, gain: number) => void;
  toggleBassBoost: () => void;
  setSleepTimer: (minutes: number | null) => void;
  importLocalTrack: (track: Track) => void;
}

let sleepTimerInterval: any = null;
let searchDebounceTimeout: any = null;

const initialLiked = getStoredLikedTracks();

export const useMusicStore = create<MusicStoreState>((set, get) => ({
  currentTrack: MOCK_TRACKS[0],
  isPlaying: false,
  currentTime: 0,
  duration: MOCK_TRACKS[0].duration,
  volume: 0.8,
  isMuted: false,
  isShuffle: false,
  repeatMode: 'all',
  queue: [MOCK_TRACKS[1], MOCK_TRACKS[2], MOCK_TRACKS[3], MOCK_TRACKS[4], MOCK_TRACKS[5]],
  history: [],
  recentTracks: [MOCK_TRACKS[0], MOCK_TRACKS[1], MOCK_TRACKS[2]],
  galaxyTracks: MOCK_TRACKS,
  explorePlaylists: [],
  activeView: 'galaxy',
  selectedPlaylistId: null,
  selectedPlaylistData: null,
  selectedArtistId: null,
  selectedGenreId: null,
  searchQuery: '',
  searchResults: [],
  isSearchingLive: false,
  isApiOnline: false,
  playlists: getStoredPlaylists(),
  likedTracks: initialLiked,
  likedTrackIds: initialLiked.map(t => t.id),
  visualizerMode: 'halo',
  currentTheme: getStoredTheme(),

  isFullscreen: false,
  isLyricsOpen: false,
  isQueueOpen: false,
  isEqualizerOpen: false,
  isCreatePlaylistOpen: false,
  isUploadTrackOpen: false,

  selectedEQPresetId: 'flat',
  customEQGains: [0, 0, 0, 0, 0],
  bassBoostEnabled: false,
  sleepTimerRemaining: null,
  sleepTimerTotalMinutes: null,

  init: () => {
    audioEngine.setCallbacks({
      onTimeUpdate: (currentTime, duration) => {
        set({ currentTime, duration: duration || get().duration });
      },
      onPlay: () => {
        set({ isPlaying: true });
      },
      onPause: () => {
        set({ isPlaying: false });
      },
      onEnded: () => {
        const { repeatMode, currentTrack } = get();
        if (repeatMode === 'one' && currentTrack) {
          audioEngine.seek(0);
          audioEngine.play();
        } else {
          get().nextTrack();
        }
      }
    });

    audioEngine.setVolume(get().volume);

    // Check YTMusic API health and populate trending galaxy tracks and explore playlists
    YTMusicService.checkHealth().then(isOnline => {
      set({ isApiOnline: isOnline });
      if (isOnline) {
        // 1. Fetch trending tracks for Vibe Galaxy
        YTMusicService.getTrendingTracks().then(trending => {
          if (trending && trending.length > 0) {
            // Assign coordinate map points for Vibe Galaxy
            const mappedTrending = trending.map((t, idx) => ({
              ...t,
              vibeCoords: {
                x: 10 + ((idx * 21) % 78),
                y: 12 + ((idx * 27) % 74)
              }
            }));
            set({ galaxyTracks: [...mappedTrending, ...MOCK_TRACKS] });
          }
        });

        // 2. Fetch curated explore playlists
        YTMusicService.getExplorePlaylists().then(playlists => {
          if (playlists && playlists.length > 0) {
            set({ explorePlaylists: playlists });
          }
        });
      }
    });
  },

  setTheme: (theme: AuraTheme) => {
    try {
      localStorage.setItem(STORAGE_KEY_THEME, theme);
    } catch {}
    set({ currentTheme: theme });
  },

  playTrack: (track: Track, newQueue?: Track[]) => {
    const { currentTrack, history, recentTracks } = get();
    
    let updatedHistory = history;
    if (currentTrack && currentTrack.id !== track.id) {
      updatedHistory = [currentTrack, ...history.slice(0, 19)];
    }

    const filteredRecent = recentTracks.filter(t => t.id !== track.id);
    const updatedRecent = [track, ...filteredRecent].slice(0, 10);

    let updatedQueue = get().queue;
    if (newQueue) {
      updatedQueue = newQueue.filter(t => t.id !== track.id);
    }

    set({
      currentTrack: track,
      isPlaying: true,
      currentTime: 0,
      duration: track.duration,
      history: updatedHistory,
      recentTracks: updatedRecent,
      queue: updatedQueue
    });

    audioEngine.loadTrack(track.audioUrl, true);

    // Auto-fetch lyrics for YouTube Music tracks if not yet loaded
    if (track.id.startsWith('yt-') && (!track.lyrics || track.lyrics.length === 0)) {
      YTMusicService.getLyrics(track.id).then(lyrics => {
        if (lyrics && lyrics.length > 0) {
          set(state => {
            if (state.currentTrack?.id === track.id) {
              return { currentTrack: { ...state.currentTrack, lyrics } };
            }
            return state;
          });
        }
      });
    }
  },

  togglePlay: () => {
    const { isPlaying, currentTrack } = get();
    if (!currentTrack) {
      if (MOCK_TRACKS.length > 0) {
        get().playTrack(MOCK_TRACKS[0]);
      }
      return;
    }

    if (isPlaying) {
      audioEngine.pause();
      set({ isPlaying: false });
    } else {
      audioEngine.play();
      set({ isPlaying: true });
    }
  },

  pauseTrack: () => {
    audioEngine.pause();
    set({ isPlaying: false });
  },

  resumeTrack: () => {
    audioEngine.play();
    set({ isPlaying: true });
  },

  nextTrack: () => {
    const { queue, isShuffle, repeatMode, currentTrack, galaxyTracks } = get();

    if (queue.length === 0) {
      if (repeatMode === 'all' && currentTrack) {
        const pool = galaxyTracks.filter(t => t.id !== currentTrack.id);
        const next = pool[0];
        if (next) {
          get().playTrack(next, pool.slice(1));
        }
      } else {
        set({ isPlaying: false });
      }
      return;
    }

    let nextTrack: Track;
    let nextQueue: Track[];

    if (isShuffle) {
      const randomIndex = Math.floor(Math.random() * queue.length);
      nextTrack = queue[randomIndex];
      nextQueue = queue.filter((_, idx) => idx !== randomIndex);
    } else {
      nextTrack = queue[0];
      nextQueue = queue.slice(1);
    }

    get().playTrack(nextTrack, nextQueue);
  },

  prevTrack: () => {
    const { currentTime, history, currentTrack } = get();
    
    if (currentTime > 3) {
      audioEngine.seek(0);
      set({ currentTime: 0 });
      return;
    }

    if (history.length > 0) {
      const prevTrack = history[0];
      const nextHistory = history.slice(1);
      const nextQueue = currentTrack ? [currentTrack, ...get().queue] : get().queue;

      set({
        currentTrack: prevTrack,
        history: nextHistory,
        queue: nextQueue,
        currentTime: 0,
        isPlaying: true
      });
      audioEngine.loadTrack(prevTrack.audioUrl, true);
    } else {
      audioEngine.seek(0);
      set({ currentTime: 0 });
    }
  },

  seek: (seconds: number) => {
    audioEngine.seek(seconds);
    set({ currentTime: seconds });
  },

  setVolume: (volume: number) => {
    const clamped = Math.max(0, Math.min(1, volume));
    audioEngine.setVolume(clamped);
    set({ volume: clamped, isMuted: clamped === 0 });
  },

  toggleMute: () => {
    const { isMuted, volume } = get();
    if (isMuted) {
      const restored = volume > 0 ? volume : 0.7;
      audioEngine.setVolume(restored);
      set({ isMuted: false, volume: restored });
    } else {
      audioEngine.setVolume(0);
      set({ isMuted: true });
    }
  },

  toggleShuffle: () => {
    set(state => ({ isShuffle: !state.isShuffle }));
  },

  toggleRepeat: () => {
    set(state => {
      const modes: ('off' | 'all' | 'one')[] = ['off', 'all', 'one'];
      const nextIndex = (modes.indexOf(state.repeatMode) + 1) % modes.length;
      return { repeatMode: modes[nextIndex] };
    });
  },

  toggleLikeTrack: (target: Track | string) => {
    const { likedTracks, currentTrack, galaxyTracks, searchResults } = get();
    
    // Resolve target track object
    let trackToToggle: Track | null = null;
    let trackId: string;

    if (typeof target === 'string') {
      trackId = target;
      if (currentTrack?.id === trackId) trackToToggle = currentTrack;
      else trackToToggle = likedTracks.find(t => t.id === trackId) 
        || galaxyTracks.find(t => t.id === trackId) 
        || searchResults.find(t => t.id === trackId) 
        || MOCK_TRACKS.find(t => t.id === trackId) || null;
    } else {
      trackToToggle = target;
      trackId = target.id;
    }

    const isAlreadyLiked = likedTracks.some(t => t.id === trackId);
    let updatedLiked: Track[];

    if (isAlreadyLiked) {
      updatedLiked = likedTracks.filter(t => t.id !== trackId);
    } else {
      if (trackToToggle) {
        updatedLiked = [{ ...trackToToggle, addedAt: 'Today' }, ...likedTracks];
      } else {
        updatedLiked = likedTracks;
      }
      try {
        confetti({
          particleCount: 30,
          spread: 60,
          origin: { y: 0.85, x: 0.15 },
          colors: ['#00ff87', '#60efff', '#ec4899', '#8b5cf6'],
          disableForReducedMotion: true
        });
      } catch {}
    }

    try {
      localStorage.setItem(STORAGE_KEY_LIKED_TRACKS, JSON.stringify(updatedLiked));
    } catch {}

    set({
      likedTracks: updatedLiked,
      likedTrackIds: updatedLiked.map(t => t.id)
    });
  },

  addToQueue: (track: Track) => {
    set(state => ({
      queue: [...state.queue, track]
    }));
  },

  removeFromQueue: (index: number) => {
    set(state => ({
      queue: state.queue.filter((_, idx) => idx !== index)
    }));
  },

  clearQueue: () => {
    set({ queue: [] });
  },

  setActiveView: (view: ViewType, params) => {
    set({
      activeView: view,
      selectedPlaylistId: params?.playlistId || null,
      selectedPlaylistData: params?.playlistData || null,
      selectedArtistId: params?.artistId || null,
      selectedGenreId: params?.genreId || null
    });
  },

  setSearchQuery: (query: string) => {
    set({ searchQuery: query });
    if (searchDebounceTimeout) clearTimeout(searchDebounceTimeout);

    if (query.trim()) {
      searchDebounceTimeout = setTimeout(() => {
        get().executeLiveSearch(query);
      }, 300);
    } else {
      set({ searchResults: [], isSearchingLive: false });
    }
  },

  executeLiveSearch: async (query: string) => {
    set({ isSearchingLive: true });
    try {
      const liveResults = await YTMusicService.searchTracks(query);
      set({ searchResults: liveResults, isSearchingLive: false });
    } catch {
      set({ isSearchingLive: false });
    }
  },

  openOnlinePlaylist: async (playlist: Playlist) => {
    set({
      activeView: 'playlist',
      selectedPlaylistId: playlist.id,
      selectedPlaylistData: playlist
    });

    // If tracks are not yet populated, fetch them live from YTMusic
    if (!playlist.tracks || playlist.tracks.length === 0) {
      const full = await YTMusicService.getPlaylistDetails(playlist.id);
      if (full) {
        set({ selectedPlaylistData: full });
      }
    }
  },

  createPlaylist: (name: string, description = '', coverArt?: string) => {
    const newPlaylist: Playlist = {
      id: `custom-playlist-${Date.now()}`,
      name: name.trim() || 'My Aura Playlist',
      description: description.trim() || 'Curated with AuraSonic',
      coverArt: coverArt || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
      tracks: [],
      isCustom: true,
      color: '#8b5cf6',
      createdBy: 'You'
    };

    const updated = [newPlaylist, ...get().playlists];
    try {
      localStorage.setItem(STORAGE_KEY_PLAYLISTS, JSON.stringify(updated));
    } catch {}

    set({ playlists: updated, isCreatePlaylistOpen: false });
    return newPlaylist;
  },

  addTrackToPlaylist: (playlistId: string, track: Track) => {
    const { playlists } = get();
    const updated = playlists.map(pl => {
      if (pl.id === playlistId) {
        if (pl.tracks.some(t => t.id === track.id)) return pl;
        return {
          ...pl,
          tracks: [...pl.tracks, { ...track, addedAt: 'Today' }]
        };
      }
      return pl;
    });

    try {
      localStorage.setItem(STORAGE_KEY_PLAYLISTS, JSON.stringify(updated));
    } catch {}

    set({ playlists: updated });
  },

  removeTrackFromPlaylist: (playlistId: string, trackId: string) => {
    const { playlists, selectedPlaylistData } = get();
    const updated = playlists.map(pl => {
      if (pl.id === playlistId) {
        return {
          ...pl,
          tracks: pl.tracks.filter(t => t.id !== trackId)
        };
      }
      return pl;
    });

    try {
      localStorage.setItem(STORAGE_KEY_PLAYLISTS, JSON.stringify(updated));
    } catch {}

    let updatedSelected = selectedPlaylistData;
    if (selectedPlaylistData && selectedPlaylistData.id === playlistId) {
      updatedSelected = {
        ...selectedPlaylistData,
        tracks: selectedPlaylistData.tracks.filter(t => t.id !== trackId)
      };
    }

    set({ 
      playlists: updated,
      selectedPlaylistData: updatedSelected
    });
  },

  deletePlaylist: (playlistId: string) => {
    const { playlists, activeView, selectedPlaylistId, selectedPlaylistData } = get();
    const updated = playlists.filter(p => p.id !== playlistId);

    try {
      localStorage.setItem(STORAGE_KEY_PLAYLISTS, JSON.stringify(updated));
    } catch {}

    const isCurrentDeleted = selectedPlaylistId === playlistId || selectedPlaylistData?.id === playlistId;

    set({
      playlists: updated,
      activeView: isCurrentDeleted ? 'library' : activeView,
      selectedPlaylistId: isCurrentDeleted ? null : selectedPlaylistId,
      selectedPlaylistData: isCurrentDeleted ? null : selectedPlaylistData
    });
  },

  setVisualizerMode: (mode: VisualizerMode) => {
    set({ visualizerMode: mode });
  },

  setIsFullscreen: (open: boolean) => {
    set({ isFullscreen: open });
  },

  setIsLyricsOpen: (open: boolean) => {
    set({ isLyricsOpen: open });
  },

  setIsQueueOpen: (open: boolean) => {
    set({ isQueueOpen: open });
  },

  setIsEqualizerOpen: (open: boolean) => {
    set({ isEqualizerOpen: open });
  },

  setIsCreatePlaylistOpen: (open: boolean) => {
    set({ isCreatePlaylistOpen: open });
  },

  setIsUploadTrackOpen: (open: boolean) => {
    set({ isUploadTrackOpen: open });
  },

  setEQPreset: (presetId: string) => {
    const preset = EQ_PRESETS.find(p => p.id === presetId);
    if (preset) {
      let gains = [...preset.gains];
      if (get().bassBoostEnabled) {
        gains[0] = Math.min(12, gains[0] + 6);
        gains[1] = Math.min(12, gains[1] + 3);
      }
      audioEngine.setEqualizerGains(gains);
      set({ selectedEQPresetId: presetId, customEQGains: preset.gains });
    }
  },

  setCustomEQGain: (index: number, gain: number) => {
    const newGains = [...get().customEQGains];
    newGains[index] = gain;
    
    let appliedGains = [...newGains];
    if (get().bassBoostEnabled) {
      appliedGains[0] = Math.min(12, appliedGains[0] + 6);
      appliedGains[1] = Math.min(12, appliedGains[1] + 3);
    }

    audioEngine.setEqualizerGains(appliedGains);
    set({ customEQGains: newGains, selectedEQPresetId: 'custom' });
  },

  toggleBassBoost: () => {
    const nextBoost = !get().bassBoostEnabled;
    const { customEQGains } = get();
    
    let appliedGains = [...customEQGains];
    if (nextBoost) {
      appliedGains[0] = Math.min(12, appliedGains[0] + 6);
      appliedGains[1] = Math.min(12, appliedGains[1] + 3);
    }
    audioEngine.setEqualizerGains(appliedGains);
    set({ bassBoostEnabled: nextBoost });
  },

  setSleepTimer: (minutes: number | null) => {
    if (sleepTimerInterval) {
      clearInterval(sleepTimerInterval);
      sleepTimerInterval = null;
    }

    if (minutes === null) {
      set({ sleepTimerRemaining: null, sleepTimerTotalMinutes: null });
      return;
    }

    let remainingSeconds = minutes * 60;
    set({ sleepTimerRemaining: remainingSeconds, sleepTimerTotalMinutes: minutes });

    sleepTimerInterval = setInterval(() => {
      remainingSeconds -= 1;
      if (remainingSeconds <= 0) {
        clearInterval(sleepTimerInterval);
        sleepTimerInterval = null;
        audioEngine.pause();
        set({ isPlaying: false, sleepTimerRemaining: null, sleepTimerTotalMinutes: null });
      } else {
        set({ sleepTimerRemaining: remainingSeconds });
      }
    }, 1000);
  },

  importLocalTrack: (track: Track) => {
    MOCK_TRACKS.unshift(track);
    get().playTrack(track);
    set({ isUploadTrackOpen: false });
  }
}));
