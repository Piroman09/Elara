import { create } from 'zustand';
import { 
  Track, 
  Playlist, 
  VisualizerMode, 
  ViewType, 
  AuraTheme 
} from '../types/music';
import { MOCK_TRACKS, EQ_PRESETS } from '../data/mockTracks';
import { audioEngine } from '../services/audioEngine';
import { YTMusicService } from '../services/ytmusicService';
import { offlineStorage } from '../services/offlineStorageService';
import { AppUser, AuthService, supabase } from '../services/supabaseService';
import confetti from 'canvas-confetti';

const STORAGE_KEY_USER = 'elara_user_session_v1';

const STORAGE_KEY_LIKED_TRACKS = 'aurasonic_liked_tracks_v2';
const STORAGE_KEY_PLAYLISTS = 'aurasonic_custom_playlists_v2';
const STORAGE_KEY_THEME = 'aurasonic_theme_v2';
const STORAGE_KEY_DOWNLOADS = 'aurasonic_downloads_v2';
const STORAGE_KEY_QUEUE = 'aurasonic_queue_v1';
const STORAGE_KEY_PREFS = 'aurasonic_playback_prefs_v1';

function getStoredUser(): AppUser | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_USER);
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

function getPlaylistKey(user: AppUser | null): string {
  return user ? `elara_user_playlists_${user.id}` : STORAGE_KEY_PLAYLISTS;
}

function getLikedKey(user: AppUser | null): string {
  return user ? `elara_user_liked_${user.id}` : STORAGE_KEY_LIKED_TRACKS;
}

function getStoredLikedTracks(user: AppUser | null = getStoredUser()): Track[] {
  try {
    const key = getLikedKey(user);
    const saved = localStorage.getItem(key);
    return saved ? JSON.parse(saved) : [];
  } catch {
    return [];
  }
}

function getStoredPlaylists(user: AppUser | null = getStoredUser()): Playlist[] {
  try {
    const key = getPlaylistKey(user);
    const saved = localStorage.getItem(key);
    return saved ? JSON.parse(saved) : [];
  } catch {
    return [];
  }
}

function saveUserPlaylists(playlists: Playlist[], user: AppUser | null) {
  try {
    const key = getPlaylistKey(user);
    localStorage.setItem(key, JSON.stringify(playlists));
  } catch {}
}

function saveUserLikedTracks(likedTracks: Track[], user: AppUser | null) {
  try {
    const key = getLikedKey(user);
    localStorage.setItem(key, JSON.stringify(likedTracks));
  } catch {}
}

function getStoredTheme(): AuraTheme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_THEME) as AuraTheme;
    return saved || 'cyber-neon';
  } catch {
    return 'cyber-neon';
  }
}

export interface DownloadedTrack {
  track: Track;
  downloadedAt: string;
  status: 'downloading' | 'completed' | 'failed';
}

function getStoredDownloads(): DownloadedTrack[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY_DOWNLOADS);
    return saved ? JSON.parse(saved) : [];
  } catch {
    return [];
  }
}

/* ------------------------------------------------------------------ *
 * Queue helpers
 *
 * The queue is the full ordered playback list and `queueIndex` points at
 * the track that is currently playing. Tracks before the cursor are
 * "already played" (reachable via Previous), tracks after it are upcoming.
 * ------------------------------------------------------------------ */

type RepeatMode = 'off' | 'all' | 'one';

/** Blob URLs (imported local files) are dead after a reload, so never persist them. */
function isPersistableTrack(track: Track): boolean {
  return !track.isLocal && !track.audioUrl?.startsWith('blob:');
}

function getStoredQueue(): { queue: Track[]; queueIndex: number } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_QUEUE);
    if (!raw) return { queue: [], queueIndex: -1 };
    const parsed = JSON.parse(raw);
    const queue: Track[] = Array.isArray(parsed?.queue)
      ? parsed.queue.filter(isPersistableTrack)
      : [];
    let queueIndex = typeof parsed?.queueIndex === 'number' ? parsed.queueIndex : -1;
    if (queueIndex >= queue.length) queueIndex = queue.length - 1;
    return { queue, queueIndex };
  } catch {
    return { queue: [], queueIndex: -1 };
  }
}

function persistQueue(queue: Track[], queueIndex: number) {
  try {
    localStorage.setItem(
      STORAGE_KEY_QUEUE,
      JSON.stringify({ queue: queue.filter(isPersistableTrack), queueIndex })
    );
  } catch {}
}

interface PlaybackPrefs {
  volume: number;
  isShuffle: boolean;
  repeatMode: RepeatMode;
}

function getStoredPrefs(): PlaybackPrefs {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_PREFS);
    if (raw) {
      const parsed = JSON.parse(raw);
      const modes: RepeatMode[] = ['off', 'all', 'one'];
      return {
        volume: typeof parsed?.volume === 'number' ? Math.max(0, Math.min(1, parsed.volume)) : 0.8,
        isShuffle: !!parsed?.isShuffle,
        repeatMode: modes.includes(parsed?.repeatMode) ? parsed.repeatMode : 'all'
      };
    }
  } catch {}
  return { volume: 0.8, isShuffle: false, repeatMode: 'all' };
}

function persistPrefs(volume: number, isShuffle: boolean, repeatMode: RepeatMode) {
  try {
    localStorage.setItem(STORAGE_KEY_PREFS, JSON.stringify({ volume, isShuffle, repeatMode }));
  } catch {}
}

/** Fisher-Yates shuffle of every index except `startIndex`, which is pinned first. */
function createShuffleOrder(length: number, startIndex: number): number[] {
  const rest: number[] = [];
  for (let i = 0; i < length; i++) {
    if (i !== startIndex) rest.push(i);
  }
  for (let i = rest.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  return startIndex >= 0 && startIndex < length ? [startIndex, ...rest] : rest;
}

/** Rebuild the shuffle order around a (possibly new) current index. */
function buildShuffleState(queueLength: number, queueIndex: number, isShuffle: boolean) {
  if (!isShuffle || queueIndex < 0 || queueLength === 0) {
    return { shuffleOrder: [] as number[], shufflePos: 0 };
  }
  return { shuffleOrder: createShuffleOrder(queueLength, queueIndex), shufflePos: 0 };
}

/**
 * Drop a freshly appended index into the *unplayed* tail of the shuffle order
 * so that adding a track doesn't reset the user's shuffle progress.
 */
function insertIntoShuffleTail(
  shuffleOrder: number[],
  shufflePos: number,
  newIndex: number
): number[] {
  const order = [...shuffleOrder];
  const minPos = Math.min(shufflePos + 1, order.length);
  const insertAt = minPos + Math.floor(Math.random() * Math.max(1, order.length - minPos + 1));
  order.splice(Math.min(insertAt, order.length), 0, newIndex);
  return order;
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
  queueIndex: number;
  shuffleOrder: number[];
  shufflePos: number;
  queueSource: string | null;
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
  downloadedTracks: DownloadedTrack[];
  activePlaybackSource: 'network' | 'offline-mp4' | 'local';
  isOfflineOnly: boolean;
  isRefreshingGalaxy: boolean;

  // Auth User
  user: AppUser | null;
  isAuthModalOpen: boolean;

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
  playTrack: (track: Track, newQueue?: Track[], source?: string) => void;
  jumpToQueueIndex: (index: number) => void;
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
  playNext: (track: Track) => void;
  moveInQueue: (from: number, to: number) => void;
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
  setIsAuthModalOpen: (open: boolean) => void;

  // Auth Actions
  setUser: (user: AppUser | null) => void;
  logoutUser: () => Promise<void>;

  // Playlist Management
  createPlaylist: (name: string, description?: string, coverArt?: string) => Playlist;
  addTrackToPlaylist: (playlistId: string, track: Track) => void;
  removeTrackFromPlaylist: (playlistId: string, trackId: string) => void;
  clearPlaylistTracks: (playlistId: string) => void;
  deletePlaylist: (playlistId: string) => void;

  setVisualizerMode: (mode: VisualizerMode) => void;
  setEQPreset: (presetId: string) => void;
  setCustomEQGain: (index: number, gain: number) => void;
  toggleBassBoost: () => void;
  setSleepTimer: (minutes: number | null) => void;
  importLocalTrack: (track: Track) => void;

  // Downloads & Offline Playback
  downloadTrack: (track: Track) => Promise<void>;
  removeDownload: (trackId: string) => Promise<void>;
  clearDownloads: () => Promise<void>;
  importDownloadedMP4: (file: File) => Promise<void>;

  // Celestial Orbit / Vibe Galaxy
  refreshGalaxyTracks: () => Promise<void>;
}

type StoreSet = (
  partial: Partial<MusicStoreState> | ((state: MusicStoreState) => Partial<MusicStoreState>)
) => void;
type StoreGet = () => MusicStoreState;

/**
 * Single entry point for "a track is now the current track".
 * Handles history, recents, offline-MP4 resolution, audio loading and lyric
 * fetching so every queue transition behaves identically.
 */
async function startPlayback(
  set: StoreSet,
  get: StoreGet,
  track: Track,
  patch: Partial<MusicStoreState>
) {
  const { currentTrack, history, recentTracks } = get();

  const updatedHistory =
    currentTrack && currentTrack.id !== track.id
      ? [currentTrack, ...history.filter(t => t.id !== currentTrack.id)].slice(0, 50)
      : history;

  const updatedRecent = [track, ...recentTracks.filter(t => t.id !== track.id)].slice(0, 10);

  // Prefer a cached offline MP4 when one exists
  const offlineUrl = await offlineStorage.getMP4PlaybackUrl(track.id);
  const playbackSource: 'network' | 'offline-mp4' | 'local' = offlineUrl
    ? 'offline-mp4'
    : track.isLocal
      ? 'local'
      : 'network';

  set({
    currentTrack: { ...track, offlineAvailable: !!offlineUrl },
    isPlaying: true,
    currentTime: 0,
    duration: track.duration,
    history: updatedHistory,
    recentTracks: updatedRecent,
    activePlaybackSource: playbackSource,
    ...patch
  });

  audioEngine.loadTrack(offlineUrl || track.audioUrl, true);

  // Auto-fetch lyrics for YouTube Music tracks if not yet loaded
  if (track.id.startsWith('yt-') && (!track.lyrics || track.lyrics.length === 0) && navigator.onLine) {
    YTMusicService.getLyrics(track.id).then(lyrics => {
      if (lyrics && lyrics.length > 0) {
        set(state =>
          state.currentTrack?.id === track.id
            ? { currentTrack: { ...state.currentTrack, lyrics } }
            : {}
        );
      }
    });
  }
}

let sleepTimerInterval: any = null;
let searchDebounceTimeout: any = null;

const initialLiked = getStoredLikedTracks();
const initialPrefs = getStoredPrefs();

const DEFAULT_QUEUE: Track[] = [
  MOCK_TRACKS[0],
  MOCK_TRACKS[1],
  MOCK_TRACKS[2],
  MOCK_TRACKS[3],
  MOCK_TRACKS[4],
  MOCK_TRACKS[5]
].filter(Boolean);

const storedQueueState = getStoredQueue();
const initialQueue = storedQueueState.queue.length > 0 ? storedQueueState.queue : DEFAULT_QUEUE;
const initialQueueIndex = storedQueueState.queue.length > 0 ? storedQueueState.queueIndex : 0;
const initialShuffleState = buildShuffleState(
  initialQueue.length,
  initialQueueIndex,
  initialPrefs.isShuffle
);

export const useMusicStore = create<MusicStoreState>((set, get) => ({
  currentTrack: initialQueue[initialQueueIndex] || MOCK_TRACKS[0],
  isPlaying: false,
  currentTime: 0,
  duration: (initialQueue[initialQueueIndex] || MOCK_TRACKS[0]).duration,
  volume: initialPrefs.volume,
  isMuted: initialPrefs.volume === 0,
  isShuffle: initialPrefs.isShuffle,
  repeatMode: initialPrefs.repeatMode,
  queue: initialQueue,
  queueIndex: initialQueueIndex,
  ...initialShuffleState,
  queueSource: null,
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
  downloadedTracks: getStoredDownloads(),
  activePlaybackSource: 'network',
  isOfflineOnly: typeof navigator !== 'undefined' ? !navigator.onLine : false,
  isRefreshingGalaxy: false,
  user: getStoredUser(),
  isAuthModalOpen: false,

  isFullscreen: false,
  isLyricsOpen: false,
  isQueueOpen: false,
  isEqualizerOpen: false,
  isCreatePlaylistOpen: false,
  isUploadTrackOpen: false,

  setUser: (user: AppUser | null) => {
    try {
      if (user) {
        localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(user));
      } else {
        localStorage.removeItem(STORAGE_KEY_USER);
      }
    } catch {}

    // Load user's saved playlists & liked songs upon login
    const userPlaylists = getStoredPlaylists(user);
    const userLiked = getStoredLikedTracks(user);

    set({ 
      user,
      playlists: userPlaylists,
      likedTracks: userLiked,
      likedTrackIds: userLiked.map(t => t.id)
    });
  },

  setIsAuthModalOpen: (open: boolean) => set({ isAuthModalOpen: open }),

  logoutUser: async () => {
    await AuthService.signOut();
    try {
      localStorage.removeItem(STORAGE_KEY_USER);
    } catch {}

    // Reset everything back to default empty state (zero playlists & zero liked tracks)
    set({ 
      user: null,
      playlists: [],
      likedTracks: [],
      likedTrackIds: [],
      selectedPlaylistId: null,
      selectedPlaylistData: null,
      activeView: 'galaxy'
    });
  },

  selectedEQPresetId: 'flat',
  customEQGains: [0, 0, 0, 0, 0],
  bassBoostEnabled: false,
  sleepTimerRemaining: null,
  sleepTimerTotalMinutes: null,

  init: () => {
    // Sync Supabase Auth session if configured
    if (supabase) {
      supabase.auth.getSession().then(({ data: { session } }) => {
        if (session?.user) {
          const formatted = AuthService.formatSupabaseUser(session.user);
          get().setUser(formatted);
        }
      });

      supabase.auth.onAuthStateChange((_event, session) => {
        if (session?.user) {
          const formatted = AuthService.formatSupabaseUser(session.user);
          get().setUser(formatted);
        } else if (_event === 'SIGNED_OUT') {
          get().setUser(null);
        }
      });
    }

    // Online / Offline network status listeners
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        set({ isOfflineOnly: false });
        YTMusicService.checkHealth().then(isOnline => set({ isApiOnline: isOnline }));
      });
      window.addEventListener('offline', () => {
        set({ isOfflineOnly: true, isApiOnline: false });
      });
    }

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
      },
      onError: async (_err) => {
        const { currentTrack, activePlaybackSource } = get();
        // If online stream fails and we have an offline MP4 cached, fallback immediately!
        if (currentTrack && activePlaybackSource === 'network') {
          const offlineUrl = await offlineStorage.getMP4PlaybackUrl(currentTrack.id);
          if (offlineUrl) {
            console.log('Network stream note, playing offline MP4:', currentTrack.title, _err);
            set({ activePlaybackSource: 'offline-mp4' });
            audioEngine.loadTrack(offlineUrl, true);
          }
        }
      }
    });

    audioEngine.setVolume(get().volume);

    // Sync IndexedDB offline downloads with state
    offlineStorage.getAllDownloadedRecords().then(records => {
      if (records && records.length > 0) {
        const dbDownloads: DownloadedTrack[] = records.map(r => ({
          track: { ...r.track, offlineAvailable: true },
          downloadedAt: r.downloadedAt,
          status: 'completed'
        }));
        
        const existing = get().downloadedTracks;
        const mergedMap = new Map<string, DownloadedTrack>();
        
        for (const dl of existing) {
          mergedMap.set(dl.track.id, dl);
        }
        for (const dl of dbDownloads) {
          mergedMap.set(dl.track.id, dl);
        }

        const merged = Array.from(mergedMap.values());
        set({ downloadedTracks: merged });
        try {
          localStorage.setItem(STORAGE_KEY_DOWNLOADS, JSON.stringify(merged));
        } catch {}
      }
    });

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

  playTrack: async (track: Track, newQueue?: Track[], source?: string) => {
    const { queue, isShuffle, queueSource } = get();

    let nextQueue: Track[];
    let nextIndex: number;
    let nextSource: string | null;

    if (newQueue && newQueue.length > 0) {
      // A new playback context was supplied — adopt it wholesale. Tracks that
      // came before the clicked one stay reachable through Previous.
      nextQueue = newQueue;
      nextIndex = nextQueue.findIndex(t => t.id === track.id);
      if (nextIndex === -1) {
        nextQueue = [track, ...newQueue];
        nextIndex = 0;
      }
      nextSource = source ?? null;
    } else {
      // No context: jump to the track if it is already queued, otherwise append
      // it so it still has a defined place in the running order.
      const existing = queue.findIndex(t => t.id === track.id);
      if (existing >= 0) {
        nextQueue = queue;
        nextIndex = existing;
      } else {
        nextQueue = [...queue, track];
        nextIndex = nextQueue.length - 1;
      }
      nextSource = queueSource;
    }

    await startPlayback(set, get, track, {
      queue: nextQueue,
      queueIndex: nextIndex,
      queueSource: nextSource,
      ...buildShuffleState(nextQueue.length, nextIndex, isShuffle)
    });
  },

  jumpToQueueIndex: (index: number) => {
    const { queue, queueIndex, isShuffle, shuffleOrder, isPlaying } = get();
    const track = queue[index];
    if (!track) return;

    if (index === queueIndex) {
      if (!isPlaying) {
        audioEngine.play();
        set({ isPlaying: true });
      }
      return;
    }

    let shufflePatch: Partial<MusicStoreState> = {};
    if (isShuffle && shuffleOrder.length > 0) {
      const pos = shuffleOrder.indexOf(index);
      shufflePatch = pos >= 0
        ? { shufflePos: pos }
        : buildShuffleState(queue.length, index, true);
    }

    void startPlayback(set, get, track, { queueIndex: index, ...shufflePatch });
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
    const {
      queue,
      queueIndex,
      isShuffle,
      shuffleOrder,
      shufflePos,
      repeatMode,
      currentTrack,
      galaxyTracks
    } = get();

    // Nothing queued — keep the "endless radio" behaviour while repeating.
    if (queue.length === 0) {
      if (repeatMode !== 'off' && currentTrack) {
        const pool = (galaxyTracks.length > 0 ? galaxyTracks : MOCK_TRACKS).filter(
          t => t.id !== currentTrack.id
        );
        if (pool.length > 0) get().playTrack(pool[0], pool, 'Auto radio');
      } else {
        set({ isPlaying: false });
      }
      return;
    }

    let target = -1;

    if (isShuffle && shuffleOrder.length > 0) {
      if (shufflePos + 1 < shuffleOrder.length) {
        target = shuffleOrder[shufflePos + 1];
      } else if (repeatMode === 'all') {
        // Reshuffle for the next lap, avoiding an immediate repeat of the last track.
        const order = createShuffleOrder(queue.length, queueIndex);
        if (order.length > 1) {
          set({ shuffleOrder: order, shufflePos: 1 });
          target = order[1];
        }
      }
    } else if (queueIndex >= 0 && queueIndex + 1 < queue.length) {
      target = queueIndex + 1;
    } else if (queueIndex < 0) {
      target = 0;
    } else if (repeatMode === 'all') {
      target = 0;
    }

    if (target >= 0 && queue[target]) {
      get().jumpToQueueIndex(target);
      return;
    }

    set({ isPlaying: false });
  },

  prevTrack: () => {
    const {
      currentTime,
      queue,
      queueIndex,
      history,
      isShuffle,
      shuffleOrder,
      shufflePos
    } = get();

    // Restart the track first, like every other player does.
    if (currentTime > 3) {
      audioEngine.seek(0);
      set({ currentTime: 0 });
      return;
    }

    if (isShuffle && shufflePos > 0) {
      const target = shuffleOrder[shufflePos - 1];
      if (queue[target]) {
        get().jumpToQueueIndex(target);
        return;
      }
    }

    if (queueIndex > 0 && queue[queueIndex - 1]) {
      get().jumpToQueueIndex(queueIndex - 1);
      return;
    }

    // Fall back to history for tracks that were played outside the queue.
    const previous = history[0];
    if (previous) {
      void startPlayback(set, get, previous, {
        queueIndex: -1,
        history: history.slice(1),
        shuffleOrder: [],
        shufflePos: 0
      });
      return;
    }

    audioEngine.seek(0);
    set({ currentTime: 0 });
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
    set(state => {
      const isShuffle = !state.isShuffle;
      return {
        isShuffle,
        ...buildShuffleState(state.queue.length, state.queueIndex, isShuffle)
      };
    });
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

    saveUserLikedTracks(updatedLiked, get().user);

    set({
      likedTracks: updatedLiked,
      likedTrackIds: updatedLiked.map(t => t.id)
    });
  },

  addToQueue: (track: Track) => {
    set(state => {
      const queue = [...state.queue, track];
      const shuffleOrder =
        state.isShuffle && state.shuffleOrder.length > 0
          ? insertIntoShuffleTail(state.shuffleOrder, state.shufflePos, queue.length - 1)
          : state.shuffleOrder;
      return { queue, shuffleOrder };
    });
  },

  playNext: (track: Track) => {
    set(state => {
      const insertAt = state.queueIndex >= 0 ? state.queueIndex + 1 : state.queue.length;
      const queue = [...state.queue.slice(0, insertAt), track, ...state.queue.slice(insertAt)];

      let shuffleOrder = state.shuffleOrder;
      let shufflePos = state.shufflePos;
      if (state.isShuffle && shuffleOrder.length > 0) {
        // Shift every index at or past the insertion point, then slot the new
        // track in directly after the current shuffle position.
        shuffleOrder = shuffleOrder.map(i => (i >= insertAt ? i + 1 : i));
        shuffleOrder.splice(state.shufflePos + 1, 0, insertAt);
      }

      return { queue, shuffleOrder, shufflePos };
    });
  },

  moveInQueue: (from: number, to: number) => {
    set(state => {
      const { queue, queueIndex } = state;
      if (from === to) return {};
      if (from < 0 || to < 0 || from >= queue.length || to >= queue.length) return {};

      const nextQueue = [...queue];
      const [moved] = nextQueue.splice(from, 1);
      nextQueue.splice(to, 0, moved);

      // Keep the cursor pointing at the same physical track.
      let nextIndex = queueIndex;
      if (queueIndex >= 0) {
        if (queueIndex === from) nextIndex = to;
        else if (from < queueIndex && to >= queueIndex) nextIndex = queueIndex - 1;
        else if (from > queueIndex && to <= queueIndex) nextIndex = queueIndex + 1;
      }

      let shufflePatch: Partial<MusicStoreState> = {};
      if (state.isShuffle && state.shuffleOrder.length > 0) {
        const order = state.shuffleOrder.map(i => {
          if (i === from) return to;
          if (from < to) return i > from && i <= to ? i - 1 : i;
          return i >= to && i < from ? i + 1 : i;
        });
        const pos = order.indexOf(nextIndex);
        shufflePatch = { shuffleOrder: order, shufflePos: pos >= 0 ? pos : 0 };
      }

      return { queue: nextQueue, queueIndex: nextIndex, ...shufflePatch };
    });
  },

  removeFromQueue: (index: number) => {
    set(state => {
      const { queue, queueIndex } = state;
      if (index < 0 || index >= queue.length) return {};

      const nextQueue = queue.filter((_, i) => i !== index);

      // Removing the current track keeps it playing but detaches it from the queue.
      let nextIndex = queueIndex;
      if (index < queueIndex) nextIndex = queueIndex - 1;
      else if (index === queueIndex) nextIndex = -1;

      if (nextIndex < 0) {
        return { queue: nextQueue, queueIndex: -1, shuffleOrder: [], shufflePos: 0 };
      }

      let shufflePatch: Partial<MusicStoreState> = {};
      if (state.isShuffle && state.shuffleOrder.length > 0) {
        const order = state.shuffleOrder
          .filter(i => i !== index)
          .map(i => (i > index ? i - 1 : i));
        const pos = order.indexOf(nextIndex);
        shufflePatch = { shuffleOrder: order, shufflePos: pos >= 0 ? pos : 0 };
      }

      return { queue: nextQueue, queueIndex: nextIndex, ...shufflePatch };
    });
  },

  clearQueue: () => {
    const { queue, queueIndex } = get();
    // "Clear queue" drops what is coming up but never interrupts playback.
    if (queueIndex >= 0 && queue[queueIndex]) {
      set({ queue: [queue[queueIndex]], queueIndex: 0, shuffleOrder: [], shufflePos: 0 });
    } else {
      set({ queue: [], queueIndex: -1, shuffleOrder: [], shufflePos: 0 });
    }
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
    saveUserPlaylists(updated, get().user);

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

    saveUserPlaylists(updated, get().user);

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

    saveUserPlaylists(updated, get().user);

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

  clearPlaylistTracks: (playlistId: string) => {
    const { playlists, selectedPlaylistData } = get();
    const updated = playlists.map(pl => {
      if (pl.id === playlistId) {
        return {
          ...pl,
          tracks: []
        };
      }
      return pl;
    });

    saveUserPlaylists(updated, get().user);

    let updatedSelected = selectedPlaylistData;
    if (selectedPlaylistData && selectedPlaylistData.id === playlistId) {
      updatedSelected = {
        ...selectedPlaylistData,
        tracks: []
      };
    }

    set({
      playlists: updated,
      selectedPlaylistData: updatedSelected
    });
  },

  deletePlaylist: (playlistId: string) => {
    const { playlists, explorePlaylists, activeView, selectedPlaylistId, selectedPlaylistData } = get();
    const updated = playlists.filter(p => p.id !== playlistId);
    const updatedExplore = explorePlaylists.filter(p => p.id !== playlistId);

    saveUserPlaylists(updated, get().user);

    const isCurrentDeleted = selectedPlaylistId === playlistId || selectedPlaylistData?.id === playlistId;

    set({
      playlists: updated,
      explorePlaylists: updatedExplore,
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
  },

  downloadTrack: async (track: Track) => {
    const { downloadedTracks } = get();

    const existingIdx = downloadedTracks.findIndex(d => d.track.id === track.id);
    let updated: DownloadedTrack[];
    
    if (existingIdx >= 0) {
      updated = [...downloadedTracks];
      updated[existingIdx] = { ...updated[existingIdx], status: 'downloading' };
    } else {
      const newDownload: DownloadedTrack = {
        track: { ...track, offlineAvailable: true },
        downloadedAt: new Date().toLocaleString(),
        status: 'downloading'
      };
      updated = [newDownload, ...downloadedTracks];
    }
    set({ downloadedTracks: updated });

    // Download MP4 blob & save to IndexedDB
    const success = await YTMusicService.downloadTrack(track);
    const current = get().downloadedTracks.map(d =>
      d.track.id === track.id
        ? { 
            ...d, 
            track: { ...d.track, offlineAvailable: success }, 
            status: (success ? 'completed' : 'failed') as DownloadedTrack['status'] 
          }
        : d
    );
    try {
      localStorage.setItem(STORAGE_KEY_DOWNLOADS, JSON.stringify(current));
    } catch {}
    set({ downloadedTracks: current });
  },

  removeDownload: async (trackId: string) => {
    await offlineStorage.deleteMP4(trackId);
    const updated = get().downloadedTracks.filter(d => d.track.id !== trackId);
    try {
      localStorage.setItem(STORAGE_KEY_DOWNLOADS, JSON.stringify(updated));
    } catch {}
    set({ downloadedTracks: updated });
  },

  clearDownloads: async () => {
    await offlineStorage.clearAll();
    try {
      localStorage.setItem(STORAGE_KEY_DOWNLOADS, JSON.stringify([]));
    } catch {}
    set({ downloadedTracks: [] });
  },

  importDownloadedMP4: async (file: File) => {
    const title = file.name.replace(/\.[^/.]+$/, "");
    const trackId = `local-mp4-${Date.now()}`;
    const blobUrl = URL.createObjectURL(file);

    const track: Track = {
      id: trackId,
      title,
      artist: 'Downloaded MP4',
      album: 'Offline Video & Audio',
      duration: 210,
      albumArt: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=60',
      audioUrl: blobUrl,
      isLocal: true,
      offlineAvailable: true,
      genre: 'Local MP4',
      vibeCoords: { x: 50, y: 50 }
    };

    await offlineStorage.saveMP4(trackId, track, file);

    const newDownload: DownloadedTrack = {
      track,
      downloadedAt: new Date().toLocaleString(),
      status: 'completed'
    };

    const updated = [newDownload, ...get().downloadedTracks];
    set({ downloadedTracks: updated });
    try {
      localStorage.setItem(STORAGE_KEY_DOWNLOADS, JSON.stringify(updated));
    } catch {}

    get().playTrack(track);
  },

  refreshGalaxyTracks: async () => {
    set({ isRefreshingGalaxy: true });
    try {
      const freshTrending = await YTMusicService.getTrendingTracks();
      if (freshTrending && freshTrending.length > 0) {
        set({ 
          galaxyTracks: [...freshTrending, ...MOCK_TRACKS],
          isRefreshingGalaxy: false 
        });
      } else {
        set({ isRefreshingGalaxy: false });
      }
    } catch {
      set({ isRefreshingGalaxy: false });
    }
  }
}));

/* ------------------------------------------------------------------ *
 * Persistence — the queue and playback preferences survive a reload.
 * ------------------------------------------------------------------ */
useMusicStore.subscribe((state, prevState) => {
  if (state.queue !== prevState.queue || state.queueIndex !== prevState.queueIndex) {
    persistQueue(state.queue, state.queueIndex);
  }
  if (
    state.volume !== prevState.volume ||
    state.isShuffle !== prevState.isShuffle ||
    state.repeatMode !== prevState.repeatMode
  ) {
    persistPrefs(state.volume, state.isShuffle, state.repeatMode);
  }
});
