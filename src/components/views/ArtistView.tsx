import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { MOCK_ARTISTS, MOCK_TRACKS } from '../../data/mockTracks';
import { TrackRow } from '../common/TrackRow';
import { TrackCard } from '../common/TrackCard';
import { Play, Pause, CheckCircle2, UserPlus, Check } from 'lucide-react';

export const ArtistView: React.FC = () => {
  const selectedArtistId = useMusicStore(state => state.selectedArtistId);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);

  const [isFollowing, setIsFollowing] = useState(false);

  const artist = MOCK_ARTISTS.find(a => a.id === selectedArtistId) || MOCK_ARTISTS[0];
  const artistTracks = MOCK_TRACKS.filter(t => t.artistId === artist.id || t.artist === artist.name);

  const isArtistPlaying = isPlaying && artistTracks.some(t => t.id === currentTrack?.id);

  const handlePlayArtist = () => {
    if (artistTracks.length === 0) return;
    if (isArtistPlaying) {
      togglePlay();
    } else {
      playTrack(artistTracks[0], artistTracks);
    }
  };

  return (
    <div className="space-y-8 pb-20 select-none">
      {/* Header with High-Res Banner */}
      <div className="relative h-72 md:h-84 overflow-hidden border-b border-white/10 flex items-end">
        {/* Banner Image */}
        <img 
          src={artist.headerImage} 
          alt={artist.name} 
          className="absolute inset-0 w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#09090b] via-[#09090b]/60 to-transparent" />

        {/* Content */}
        <div className="relative z-10 px-6 md:px-10 pb-8 flex flex-col items-start gap-2">
          <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-emerald-400">
            <CheckCircle2 className="w-4 h-4 fill-emerald-400 text-black" />
            Verified Artist
          </div>

          <h1 className="text-3xl md:text-6xl font-black text-white tracking-tight drop-shadow-lg">
            {artist.name}
          </h1>

          <p className="text-xs font-mono text-zinc-300">
            {artist.monthlyListeners.toLocaleString()} monthly listeners
          </p>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="px-6 md:px-10 flex items-center gap-4">
        <button
          onClick={handlePlayArtist}
          className="w-14 h-14 rounded-full bg-spotify-green hover:bg-spotify-hover text-black flex items-center justify-center shadow-xl shadow-spotify-green/30 hover:scale-105 active:scale-95 transition-all"
        >
          {isArtistPlaying ? (
            <Pause className="w-6 h-6 fill-black text-black" />
          ) : (
            <Play className="w-6 h-6 fill-black text-black ml-1" />
          )}
        </button>

        <button
          onClick={() => setIsFollowing(!isFollowing)}
          className={`px-4 py-2 rounded-full text-xs font-bold uppercase tracking-wider border transition-all ${
            isFollowing 
              ? 'bg-white/10 text-white border-white/20' 
              : 'bg-transparent hover:bg-white/10 text-zinc-200 border-white/30'
          }`}
        >
          {isFollowing ? (
            <span className="flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5 text-emerald-400" />
              Following
            </span>
          ) : (
            <span className="flex items-center gap-1.5">
              <UserPlus className="w-3.5 h-3.5" />
              Follow
            </span>
          )}
        </button>
      </div>

      {/* Popular Tracks Section */}
      <section className="px-6 md:px-10 space-y-4">
        <h2 className="text-xl font-black text-white tracking-tight">
          Popular
        </h2>

        <div className="space-y-1">
          {artistTracks.map((track, idx) => (
            <TrackRow
              key={track.id}
              track={track}
              index={idx}
              contextPlaylistTracks={artistTracks}
            />
          ))}
        </div>
      </section>

      {/* Discography / Albums */}
      <section className="px-6 md:px-10 space-y-4">
        <h2 className="text-xl font-black text-white tracking-tight">
          Discography
        </h2>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {artist.albums.map((album) => (
            <TrackCard
              key={album.id}
              title={album.title}
              subtitle={`${album.releaseYear} • Album`}
              image={album.coverArt}
              onPlayClick={() => {
                if (album.tracks.length > 0) {
                  playTrack(album.tracks[0], album.tracks);
                }
              }}
            />
          ))}
        </div>
      </section>

      {/* About Section */}
      <section className="px-6 md:px-10 space-y-4">
        <h2 className="text-xl font-black text-white tracking-tight">
          About
        </h2>

        <div className="relative rounded-3xl overflow-hidden glass-panel border border-white/10 p-6 md:p-8 max-w-4xl">
          <div className="space-y-4">
            <span className="text-2xl md:text-3xl font-black text-white">
              {artist.monthlyListeners.toLocaleString()} monthly listeners
            </span>
            <p className="text-sm text-zinc-300 leading-relaxed max-w-2xl">
              {artist.bio}
            </p>
            <div className="flex flex-wrap gap-2 pt-2">
              {artist.genres.map(g => (
                <span key={g} className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-xs font-semibold text-zinc-300">
                  {g}
                </span>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};
