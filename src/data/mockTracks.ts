import { Track, Artist, Playlist, GenreCategory, EqualizerPreset, ThemeConfig } from '../types/music';

export const AURA_THEMES: ThemeConfig[] = [
  {
    id: 'cyber-neon',
    name: 'Cyber Neon ⚡',
    accent: '#00ff87',
    secondary: '#60efff',
    bgGlow: 'rgba(0, 255, 135, 0.25)',
    badge: 'Futuristic'
  },
  {
    id: 'tokyo-sunset',
    name: 'Tokyo Sunset 🌆',
    accent: '#ec4899',
    secondary: '#f43f5e',
    bgGlow: 'rgba(236, 72, 153, 0.25)',
    badge: 'Warm Lo-Fi'
  },
  {
    id: 'nordic-aurora',
    name: 'Nordic Aurora 🌌',
    accent: '#06b6d4',
    secondary: '#8b5cf6',
    bgGlow: 'rgba(6, 182, 212, 0.25)',
    badge: 'Ethereal'
  },
  {
    id: 'obsidian-glass',
    name: 'Obsidian Glass 💎',
    accent: '#a855f7',
    secondary: '#3b82f6',
    bgGlow: 'rgba(168, 85, 247, 0.25)',
    badge: 'Minimal Luxury'
  },
  {
    id: 'solar-flare',
    name: 'Solar Flare ☀️',
    accent: '#f59e0b',
    secondary: '#ef4444',
    bgGlow: 'rgba(245, 158, 11, 0.25)',
    badge: 'High Energy'
  }
];

export const GENRES: GenreCategory[] = [
  {
    id: 'synthwave',
    name: 'Retrowave & Synth',
    color: '#ec4899',
    gradient: 'from-pink-600 to-purple-800',
    image: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80'
  },
  {
    id: 'lofi',
    name: 'Lo-Fi Chill Beats',
    color: '#8b5cf6',
    gradient: 'from-violet-600 to-indigo-900',
    image: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=600&auto=format&fit=crop&q=80'
  },
  {
    id: 'cyberpunk',
    name: 'Cyberpunk Darksynth',
    color: '#06b6d4',
    gradient: 'from-cyan-500 to-blue-900',
    image: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80'
  },
  {
    id: 'ambient',
    name: 'Deep Ambient & Space',
    color: '#10b981',
    gradient: 'from-emerald-600 to-teal-950',
    image: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600&auto=format&fit=crop&q=80'
  },
  {
    id: 'electronic',
    name: 'Future Bass & EDM',
    color: '#f59e0b',
    gradient: 'from-amber-500 to-orange-800',
    image: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80'
  },
  {
    id: 'indie',
    name: 'Indie & Dream Pop',
    color: '#f43f5e',
    gradient: 'from-rose-500 to-red-900',
    image: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80'
  }
];

export const MOCK_TRACKS: Track[] = [
  {
    id: 'track-1',
    title: 'Midnight City Lights',
    artist: 'Neon Mirage',
    artistId: 'artist-1',
    album: 'Neon Horizon 2088',
    albumArt: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=synthwave-80s-110045.mp3',
    duration: 168,
    genre: 'Synthwave',
    mood: 'Velocity Night',
    vibeCoords: { x: 28, y: 35 },
    energy: 82,
    accentColor: '#ec4899',
    plays: 1420500,
    addedAt: '2026-08-10',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Synth wave intro resonates through the night) ♪' },
      { id: '2', time: 12, text: 'Neon reflections on the wet asphalt' },
      { id: '3', time: 18, text: 'Speeding through the skyline with nobody at fault' },
      { id: '4', time: 24, text: 'Chasing the purple horizon line' },
      { id: '5', time: 31, text: 'Leaving the memories of yesterday behind' },
      { id: '6', time: 39, text: 'Every streetlight shines like a supernova' },
      { id: '7', time: 47, text: 'Until the midnight overdrive is finally over' },
      { id: '8', time: 56, text: 'We move through digital rain' },
      { id: '9', time: 64, text: 'Feeling the frequency, erasing the pain' },
      { id: '10', time: 76, text: '♪ (Retro synth solo & arpeggios) ♪' },
      { id: '11', time: 98, text: 'Cruising past the glowing tower heights' },
      { id: '12', time: 106, text: 'Lost forever in the midnight city lights' },
      { id: '13', time: 118, text: 'Midnight city lights... endlessly glowing' },
      { id: '14', time: 130, text: 'Where the time stops and the bass keeps flowing' },
      { id: '15', time: 146, text: '♪ (Outro fade with analog warmth) ♪' }
    ]
  },
  {
    id: 'track-2',
    title: 'Rainy Cafe in Shibuya',
    artist: 'Komorebi Sound',
    artistId: 'artist-2',
    album: 'Tokyo After Dark EP',
    albumArt: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2022/01/18/audio_d0a13f69d2.mp3?filename=lofi-study-112191.mp3',
    duration: 147,
    genre: 'Lo-Fi Chill',
    mood: 'Zen Flow',
    vibeCoords: { x: 70, y: 65 },
    energy: 45,
    accentColor: '#8b5cf6',
    plays: 890400,
    addedAt: '2026-08-12',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Warm vinyl crackle & soft rain against glass) ♪' },
      { id: '2', time: 10, text: 'Steam rises gently from the porcelain cup' },
      { id: '3', time: 19, text: 'Watching busy umbrellas never look up' },
      { id: '4', time: 29, text: 'Pages turning slowly in the corner booth' },
      { id: '5', time: 38, text: 'Seeking a moment of quiet and truth' },
      { id: '6', time: 48, text: '♪ (Mellow Rhodes piano & chill drum bounce) ♪' },
      { id: '7', time: 68, text: 'Distant chatter fades into the storm' },
      { id: '8', time: 78, text: 'Here in the shelter, peaceful and warm' },
      { id: '9', time: 89, text: 'Take a deep breath and let thoughts drift away' },
      { id: '10', time: 101, text: 'Saving tomorrow for another day' }
    ]
  },
  {
    id: 'track-3',
    title: 'Cybernetic Dreams',
    artist: 'Vortex 99',
    artistId: 'artist-3',
    album: 'Neural Overdrive',
    albumArt: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2022/03/15/audio_c8c8a73467.mp3?filename=electronic-future-beats-117997.mp3',
    duration: 154,
    genre: 'Cyberpunk',
    mood: 'Neural Rush',
    vibeCoords: { x: 42, y: 78 },
    energy: 94,
    accentColor: '#06b6d4',
    plays: 2150000,
    addedAt: '2026-08-15',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Pulsing bass sequence initializing) ♪' },
      { id: '2', time: 14, text: 'Data streams rushing through the spine' },
      { id: '3', time: 22, text: 'Crossing every synthetic border line' },
      { id: '4', time: 30, text: 'Electric pulse, higher frequency state' },
      { id: '5', time: 38, text: 'Rewrite the code, accelerate the fate' },
      { id: '6', time: 46, text: '♪ (Heavy Cyber drop & glitch synths) ♪' }
    ]
  },
  {
    id: 'track-4',
    title: 'Astral Echoes',
    artist: 'Celestial Bloom',
    artistId: 'artist-4',
    album: 'Infinite Void',
    albumArt: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2021/08/04/audio_0625c1539c.mp3?filename=ambient-piano-amp-strings-10711.mp3',
    duration: 182,
    genre: 'Deep Ambient',
    mood: 'Ethereal Cosmic',
    vibeCoords: { x: 80, y: 25 },
    energy: 30,
    accentColor: '#10b981',
    plays: 640000,
    addedAt: '2026-08-18',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Gentle atmospheric pads and cosmic resonance) ♪' },
      { id: '2', time: 20, text: 'Drifting beyond the edge of gravity' },
      { id: '3', time: 34, text: 'Into the silence of eternity' }
    ]
  },
  {
    id: 'track-5',
    title: 'Sunset Boulevard',
    artist: 'Palms & Wave',
    artistId: 'artist-5',
    album: 'Golden Hour Memories',
    albumArt: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2022/10/14/audio_9939f792cb.mp3?filename=summer-chill-124458.mp3',
    duration: 135,
    genre: 'Indie Pop',
    mood: 'Golden Euphoria',
    vibeCoords: { x: 55, y: 40 },
    energy: 72,
    accentColor: '#f43f5e',
    plays: 1120000,
    addedAt: '2026-08-20',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Upbeat guitar groove and bassline) ♪' },
      { id: '2', time: 11, text: 'Golden rays shining in your rearview mirror' }
    ]
  },
  {
    id: 'track-6',
    title: 'Hyperdrive Festival',
    artist: 'Nova Strike',
    artistId: 'artist-6',
    album: 'Electric Supernova',
    albumArt: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80',
    audioUrl: 'https://cdn.pixabay.com/download/audio/2022/01/26/audio_d0c6ff1101.mp3?filename=electronic-rock-king-around-here-15045.mp3',
    duration: 160,
    genre: 'Electronic',
    mood: 'Maximum Adrenaline',
    vibeCoords: { x: 20, y: 80 },
    energy: 98,
    accentColor: '#f59e0b',
    plays: 3450000,
    addedAt: '2026-08-22',
    lyrics: [
      { id: '1', time: 0, text: '♪ (Countdown pulse & riser builds up) ♪' },
      { id: '2', time: 15, text: 'Are you ready for the ultimate drop?' }
    ]
  }
];

export const MOCK_ARTISTS: Artist[] = [
  {
    id: 'artist-1',
    name: 'Neon Mirage',
    image: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80',
    headerImage: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1600&auto=format&fit=crop&q=80',
    monthlyListeners: 1845200,
    bio: 'Pioneering the retro-futuristic synthwave sound with analog synthesizers, cinematic drums, and neon-lit dystopian vibes.',
    genres: ['Synthwave', 'Retrowave', 'Electronic'],
    topTracks: [MOCK_TRACKS[0], MOCK_TRACKS[2]],
    albums: [
      {
        id: 'album-1',
        title: 'Neon Horizon 2088',
        artist: 'Neon Mirage',
        coverArt: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80',
        releaseYear: 2024,
        tracks: [MOCK_TRACKS[0]]
      }
    ]
  },
  {
    id: 'artist-2',
    name: 'Komorebi Sound',
    image: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=600&auto=format&fit=crop&q=80',
    headerImage: 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1600&auto=format&fit=crop&q=80',
    monthlyListeners: 2450000,
    bio: 'Tokyo-based music producer blending organic rain samples, cozy Rhodes keyboards, and vinyl nostalgia for studying and relaxation.',
    genres: ['Lo-Fi Chill', 'Jazzhop', 'Ambient'],
    topTracks: [MOCK_TRACKS[1], MOCK_TRACKS[3]],
    albums: [
      {
        id: 'album-2',
        title: 'Tokyo After Dark EP',
        artist: 'Komorebi Sound',
        coverArt: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&auto=format&fit=crop&q=80',
        releaseYear: 2025,
        tracks: [MOCK_TRACKS[1]]
      }
    ]
  },
  {
    id: 'artist-3',
    name: 'Vortex 99',
    image: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80',
    headerImage: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1600&auto=format&fit=crop&q=80',
    monthlyListeners: 3120000,
    bio: 'High-octane electronic and darksynth mastermind producing soundtracks for futuristic cyber-metropolises and late night drives.',
    genres: ['Cyberpunk', 'Darksynth', 'Industrial'],
    topTracks: [MOCK_TRACKS[2], MOCK_TRACKS[5]],
    albums: [
      {
        id: 'album-3',
        title: 'Neural Overdrive',
        artist: 'Vortex 99',
        coverArt: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80',
        releaseYear: 2026,
        tracks: [MOCK_TRACKS[2]]
      }
    ]
  }
];

export const MOCK_PLAYLISTS: Playlist[] = [
  {
    id: 'playlist-aesthetic-mix',
    name: 'Midnight Velocity 🌌',
    description: 'Neon synthwave, deep cyberpunk, and hypnotic basslines for midnight cruising.',
    coverArt: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80',
    color: '#8b5cf6',
    createdBy: 'AuraSonic Flow',
    tracks: [MOCK_TRACKS[0], MOCK_TRACKS[2], MOCK_TRACKS[5]]
  },
  {
    id: 'playlist-lofi-focus',
    name: 'Tokyo Lo-Fi & Code ☕',
    description: 'Peaceful beats and soothing rain harmonies to help you concentrate and unwind.',
    coverArt: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&auto=format&fit=crop&q=80',
    color: '#06b6d4',
    createdBy: 'AuraSonic Flow',
    tracks: [MOCK_TRACKS[1], MOCK_TRACKS[3]]
  },
  {
    id: 'playlist-sunset-vibes',
    name: 'Sunset Euphoria 🌅',
    description: 'Warm indie grooves and melodic vibes to light up your evening.',
    coverArt: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80',
    color: '#f43f5e',
    createdBy: 'AuraSonic Flow',
    tracks: [MOCK_TRACKS[4], MOCK_TRACKS[0], MOCK_TRACKS[1]]
  },
  {
    id: 'playlist-electric-workout',
    name: 'Hyperdrive Overclock ⚡',
    description: 'High BPM electronic bangers to elevate your adrenaline.',
    coverArt: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80',
    color: '#f59e0b',
    createdBy: 'AuraSonic Flow',
    tracks: [MOCK_TRACKS[5], MOCK_TRACKS[2]]
  }
];

export const EQ_PRESETS: EqualizerPreset[] = [
  { id: 'flat', name: 'Flat (Studio Neutral)', gains: [0, 0, 0, 0, 0] },
  { id: 'bass-boost', name: 'Sub-Bass Engine 🔊', gains: [7, 5, 0, -1, -2] },
  { id: 'vocal-boost', name: 'Vocal Presence 🎙️', gains: [-2, 1, 6, 4, 1] },
  { id: 'electronic', name: 'Cyber Synth 🎛️', gains: [6, 3, -2, 4, 5] },
  { id: 'lofi-warmth', name: 'Analog Tape Warmth 📼', gains: [4, 2, -1, -4, -6] },
  { id: 'acoustic', name: 'Acoustic Chamber 🎻', gains: [2, 1, 2, 5, 4] }
];
