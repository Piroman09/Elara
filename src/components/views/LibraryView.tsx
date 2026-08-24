import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { TrackCard } from '../common/TrackCard';
import { MOCK_ARTISTS } from '../../data/mockTracks';
import { Plus, Upload, Library, Heart } from 'lucide-react';
import { motion } from 'framer-motion';

export const LibraryView: React.FC = () => {
  const playlists = useMusicStore(state => state.playlists);
  const likedTrackIds = useMusicStore(state => state.likedTrackIds);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const setIsCreatePlaylistOpen = useMusicStore(state => state.setIsCreatePlaylistOpen);
  const setIsUploadTrackOpen = useMusicStore(state => state.setIsUploadTrackOpen);

  const [filter, setFilter] = useState<'all' | 'playlists' | 'artists'>('all');

  return (
    <div className="p-6 md:p-8 space-y-8 max-w-7xl mx-auto pb-20 select-none">
      {/* Header & Quick Action Buttons */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl md:text-4xl font-black text-white tracking-tight flex items-center gap-3">
            <Library className="w-8 h-8 text-spotify-green" />
            Your Music Library
          </h1>
          <p className="text-xs text-zinc-400 mt-1">Playlists, collections, and custom imports</p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsUploadTrackOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-bold text-zinc-200 hover:text-white transition-all shadow-sm active:scale-95"
          >
            <Upload className="w-4 h-4 text-cyan-400" />
            Import Audio
          </button>

          <button
            onClick={() => setIsCreatePlaylistOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-spotify-green hover:bg-spotify-hover text-black text-xs font-bold shadow-lg shadow-spotify-green/30 transition-all active:scale-95"
          >
            <Plus className="w-4 h-4" />
            New Playlist
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2">
        {(['all', 'playlists', 'artists'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setFilter(tab)}
            className={`px-4 py-1.5 rounded-full text-xs font-bold capitalize transition-all ${
              filter === tab
                ? 'bg-white/20 text-white font-extrabold border border-white/20 shadow-md'
                : 'bg-white/5 hover:bg-white/10 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        {/* Liked Songs Special Card */}
        {(filter === 'all' || filter === 'playlists') && (
          <motion.div
            whileHover={{ y: -4 }}
            onClick={() => setActiveView('liked')}
            className="group relative p-5 rounded-2xl bg-gradient-to-br from-indigo-600 via-purple-700 to-pink-600 hover:brightness-110 transition-all duration-300 cursor-pointer flex flex-col justify-between shadow-xl min-h-[220px]"
          >
            <div className="flex items-center justify-between">
              <Heart className="w-8 h-8 text-white fill-white drop-shadow-md" />
              <span className="text-[10px] uppercase font-bold tracking-widest px-2 py-0.5 rounded-full bg-white/20 text-white backdrop-blur-sm">
                Favorites
              </span>
            </div>

            <div>
              <h3 className="text-lg font-black text-white drop-shadow-sm">
                Liked Songs
              </h3>
              <p className="text-xs text-white/80 font-medium mt-1">
                {likedTrackIds.length} saved tracks
              </p>
            </div>
          </motion.div>
        )}

        {/* Playlists */}
        {(filter === 'all' || filter === 'playlists') &&
          playlists.map((playlist) => (
            <TrackCard
              key={playlist.id}
              title={playlist.name}
              subtitle={`${playlist.tracks.length} songs • ${playlist.createdBy || 'SoundWave'}`}
              image={playlist.coverArt}
              badge={playlist.isCustom ? 'Custom' : undefined}
              onClick={() => setActiveView('playlist', { playlistId: playlist.id })}
            />
          ))}

        {/* Artists */}
        {(filter === 'all' || filter === 'artists') &&
          MOCK_ARTISTS.map((artist) => (
            <TrackCard
              key={artist.id}
              title={artist.name}
              subtitle="Artist"
              image={artist.image}
              isCircular={true}
              badge="Verified"
              onClick={() => setActiveView('artist', { artistId: artist.id })}
            />
          ))}
      </div>
    </div>
  );
};
