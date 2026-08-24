export interface LyricLine {
  id: string;
  time: number; // in seconds
  text: string;
  translation?: string;
}

export interface Track {
  id: string;
  title: string;
  artist: string;
  artistId?: string;
  album: string;
  albumArt: string;
  audioUrl: string;
  duration: number; // in seconds
  genre: string;
  mood?: string;
  vibeCoords?: { x: number; y: number }; // For Vibe Galaxy map
  energy?: number; // 0-100
  lyrics?: LyricLine[];
  accentColor?: string;
  isLiked?: boolean;
  plays?: number;
  addedAt?: string;
}

export interface Artist {
  id: string;
  name: string;
  image: string;
  headerImage: string;
  monthlyListeners: number;
  bio: string;
  genres: string[];
  topTracks: Track[];
  albums: Album[];
}

export interface Album {
  id: string;
  title: string;
  artist: string;
  coverArt: string;
  releaseYear: number;
  tracks: Track[];
}

export interface Playlist {
  id: string;
  name: string;
  description: string;
  coverArt: string;
  tracks: Track[];
  isCustom?: boolean;
  color?: string;
  createdBy?: string;
}

export type VisualizerMode = 'halo' | 'bars' | 'wave' | 'particles' | 'vinyl' | 'off';

export type AuraTheme = 
  | 'cyber-neon' 
  | 'tokyo-sunset' 
  | 'nordic-aurora' 
  | 'obsidian-glass' 
  | 'solar-flare';

export interface ThemeConfig {
  id: AuraTheme;
  name: string;
  accent: string;
  secondary: string;
  bgGlow: string;
  badge: string;
}

export interface EqualizerPreset {
  id: string;
  name: string;
  gains: number[];
}

export type RoomReverb = 'studio' | 'cathedral' | 'cyber-bunker' | 'vinyl-lounge';

export interface SpatialAudioState {
  is8DEnabled: boolean;
  orbitalSpeed: number;
  roomReverb: RoomReverb;
  spatialDepth: number;
}

export interface AmbientSoundsState {
  rain: number;
  cafe: number;
  fire: number;
  spaceDrone: number;
  vinylHiss: number;
  masterVolume: number;
}

export type ViewType = 
  | 'galaxy'
  | 'home' 
  | 'search' 
  | 'library' 
  | 'playlist' 
  | 'artist' 
  | 'liked' 
  | 'focus';

export interface GenreCategory {
  id: string;
  name: string;
  color: string;
  gradient: string;
  image: string;
}
