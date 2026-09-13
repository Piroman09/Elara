import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { getGreeting } from '../../utils/formatters';
import { MOCK_TRACKS, MOCK_PLAYLISTS, MOCK_ARTISTS } from '../../data/mockTracks';
import { TrackCard } from '../common/TrackCard';
import { Play, Pause, Sparkles, Flame, Radio, Disc } from 'lucide-react';
import { motion } from 'framer-motion';
import { Track } from '../../types/music';

interface QuickItem {
  id: string;
  title: string;
  image: string;
  action: () => void;
  tracks?: Track[];
  isLiked?: boolean;
}

export const HomeView: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const playlists = useMusicStore(state => state.playlists);

  const greeting = getGreeting();

  // Quick jump cards (first 6 items)
  const quickItems: QuickItem[] = [
    { 
      id: 'quick-liked', 
      title: 'Liked Songs', 
      image: 'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=300&auto=format&fit=crop&q=80',
      action: () => setActiveView('liked'),
      isLiked: true
    },
    ...playlists.slice(0, 5).map(p => ({
      id: p.id,
      title: p.name,
      image: p.coverArt,
      action: () => setActiveView('playlist', { playlistId: p.id }),
      tracks: p.tracks
    }))
  ];

  return (
    <div className="p-6 md:p-8 space-y-10 max-w-7xl mx-auto pb-16">
      {/* Hero Greeting Section */}
      <div>
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-emerald-400 mb-1">
          <Sparkles className="w-3.5 h-3.5" />
          Aesthetic Audio Stream
        </div>
        <h1 className="text-3xl md:text-4xl font-black text-white tracking-tight">
          {greeting}
        </h1>
      </div>

      {/* Spotify-style 6-Card Quick Access Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
        {quickItems.map((item) => {
          const isItemPlaying = isPlaying && !!item.tracks?.some(t => t.id === currentTrack?.id);
          return (
            <motion.div
              key={item.id}
              whileHover={{ scale: 1.02 }}
              onClick={item.action}
              className="group relative flex items-center gap-4 p-2.5 pr-4 rounded-xl bg-white/[0.05] hover:bg-white/[0.12] transition-all duration-300 cursor-pointer overflow-hidden border border-white/5 shadow-md"
            >
              <div className="relative w-14 h-14 rounded-lg overflow-hidden flex-shrink-0 shadow-lg bg-zinc-800">
                <img src={item.image} alt={item.title} className="w-full h-full object-cover" />
              </div>

              <span className="font-bold text-sm text-zinc-100 group-hover:text-white truncate flex-1">
                {item.title}
              </span>

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  if (item.tracks && item.tracks.length > 0) {
                    playTrack(item.tracks[0], item.tracks, item.title);
                  } else {
                    item.action();
                  }
                }}
                className={`w-10 h-10 rounded-full bg-spotify-green flex items-center justify-center shadow-xl transition-all duration-300 transform ${
                  isItemPlaying
                    ? 'opacity-100 scale-100 shadow-[0_0_20px_rgba(30,215,96,0.6)]'
                    : 'opacity-0 translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 hover:scale-110 active:scale-95'
                }`}
              >
                {isItemPlaying ? (
                  <Pause className="w-4 h-4 fill-black text-black" />
                ) : (
                  <Play className="w-4 h-4 fill-black text-black ml-0.5" />
                )}
              </button>
            </motion.div>
          );
        })}
      </div>

      {/* Featured Aesthetic Playlists */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-extrabold text-white tracking-tight flex items-center gap-2">
              <Flame className="w-5 h-5 text-pink-500" />
              Featured Aesthetic Mixes
            </h2>
            <p className="text-xs text-zinc-400 mt-0.5">Carefully tuned frequencies for your flow state</p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-4 gap-4">
          {MOCK_PLAYLISTS.map((playlist) => (
            <TrackCard
              key={playlist.id}
              title={playlist.name}
              subtitle={playlist.description}
              image={playlist.coverArt}
              badge="Curated"
              onClick={() => setActiveView('playlist', { playlistId: playlist.id })}
              onPlayClick={() => {
                if (playlist.tracks.length > 0) {
                  playTrack(playlist.tracks[0], playlist.tracks, playlist.name);
                }
              }}
            />
          ))}
        </div>
      </section>

      {/* Top Chill & Lo-Fi Beats */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-extrabold text-white tracking-tight flex items-center gap-2">
              <Disc className="w-5 h-5 text-cyan-400" />
              Trending Audio Tracks
            </h2>
            <p className="text-xs text-zinc-400 mt-0.5">Stream the most played sonic experiences</p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-4">
          {MOCK_TRACKS.map((track) => (
            <TrackCard
              key={track.id}
              title={track.title}
              subtitle={track.artist}
              image={track.albumArt}
              isCurrent={currentTrack?.id === track.id}
              isPlaying={isPlaying}
              onClick={() => playTrack(track, MOCK_TRACKS, 'Home')}
              onPlayClick={() => {
                if (currentTrack?.id === track.id) {
                  togglePlay();
                } else {
                  playTrack(track, MOCK_TRACKS, 'Home');
                }
              }}
            />
          ))}
        </div>
      </section>

      {/* Featured Artists */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-extrabold text-white tracking-tight flex items-center gap-2">
              <Radio className="w-5 h-5 text-emerald-400" />
              Visionary Artists
            </h2>
            <p className="text-xs text-zinc-400 mt-0.5">Producers pushing the boundary of modern sound</p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-3 gap-4">
          {MOCK_ARTISTS.map((artist) => (
            <TrackCard
              key={artist.id}
              title={artist.name}
              subtitle={`${artist.monthlyListeners.toLocaleString()} monthly listeners`}
              image={artist.image}
              isCircular={true}
              badge="Verified"
              onClick={() => setActiveView('artist', { artistId: artist.id })}
              onPlayClick={() => {
                if (artist.topTracks.length > 0) {
                  playTrack(artist.topTracks[0], artist.topTracks, artist.name);
                }
              }}
            />
          ))}
        </div>
      </section>
    </div>
  );
};
