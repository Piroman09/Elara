import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { TrackRow } from '../common/TrackRow';
import { Heart, Play, Pause, Clock, Sparkles, ShieldCheck } from 'lucide-react';
import { formatTotalDuration } from '../../utils/formatters';

export const LikedSongsView: React.FC = () => {
  const likedTracks = useMusicStore(state => state.likedTracks);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);

  const isCurrentPlayingLiked = isPlaying && likedTracks.some(t => t.id === currentTrack?.id);
  const totalDuration = likedTracks.reduce((acc, t) => acc + (t.duration || 180), 0);

  const handlePlayLiked = () => {
    if (likedTracks.length === 0) return;
    if (isCurrentPlayingLiked) {
      togglePlay();
    } else {
      playTrack(likedTracks[0], likedTracks, 'Liked Songs');
    }
  };

  return (
    <div className="pb-36 select-none">
      {/* Header Banner */}
      <div className="relative p-6 sm:p-8 md:p-12 overflow-hidden bg-gradient-to-b from-purple-900/60 via-indigo-950/40 to-transparent border-b border-white/[0.06]">
        <div className="absolute top-0 left-10 w-96 h-96 rounded-full bg-purple-500/20 filter blur-[100px] pointer-events-none" />

        <div className="relative z-10 flex flex-col md:flex-row items-start md:items-end gap-6">
          <div className="w-36 h-36 sm:w-44 sm:h-44 rounded-3xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-2xl shadow-purple-500/30 flex-shrink-0 border border-white/20">
            <Heart className="w-20 h-20 text-white fill-white drop-shadow-lg" />
          </div>

          <div className="flex flex-col space-y-2">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-mono uppercase tracking-widest text-purple-300 font-bold">
                Permanent Collection
              </span>
              <span className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-mono border border-emerald-500/30">
                <ShieldCheck className="w-3 h-3" />
                Protected
              </span>
            </div>
            <h1 className="text-3xl sm:text-5xl md:text-6xl font-black text-white tracking-tight drop-shadow-md">
              Liked Songs
            </h1>
            <p className="text-xs sm:text-sm text-zinc-300 flex items-center gap-2 pt-1 font-medium">
              <span className="font-bold text-white">Permanent Favorites</span>
              <span>•</span>
              <span className="text-emerald-300 font-mono font-bold">{likedTracks.length} tracks</span>
              {likedTracks.length > 0 && (
                <>
                  <span>•</span>
                  <span className="text-zinc-400 font-mono">{formatTotalDuration(totalDuration)}</span>
                </>
              )}
            </p>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="p-6 md:p-8 max-w-7xl mx-auto space-y-6">
        {likedTracks.length > 0 && (
          <div className="flex items-center gap-4">
            <button
              onClick={handlePlayLiked}
              className="w-14 h-14 rounded-full bg-emerald-400 hover:bg-emerald-300 text-black flex items-center justify-center shadow-xl shadow-emerald-400/30 hover:scale-105 active:scale-95 transition-all"
            >
              {isCurrentPlayingLiked ? (
                <Pause className="w-6 h-6 fill-black" />
              ) : (
                <Play className="w-6 h-6 fill-black ml-1" />
              )}
            </button>
          </div>
        )}

        {likedTracks.length > 0 ? (
          <div className="space-y-1">
            <div className="grid grid-cols-[36px_1fr_auto] md:grid-cols-[36px_4fr_3fr_80px_40px] gap-4 px-4 py-2 text-zinc-400 text-xs font-mono border-b border-white/[0.06] mb-2 uppercase tracking-wider">
              <span>#</span>
              <span>Title</span>
              <span className="hidden md:block">Album / Source</span>
              <span className="hidden md:flex justify-end items-center">
                <Clock className="w-3.5 h-3.5" />
              </span>
              <span></span>
            </div>

            {likedTracks.map((track, index) => (
              <TrackRow
                key={`${track.id}-${index}`}
                track={track}
                index={index}
                contextPlaylistTracks={likedTracks}
              />
            ))}
          </div>
        ) : (
          <div className="py-20 text-center glass-panel rounded-3xl border border-white/5 space-y-3">
            <Sparkles className="w-12 h-12 text-zinc-600 mx-auto" />
            <h3 className="text-lg font-bold text-white">Your permanent favorites will appear here</h3>
            <p className="text-xs text-zinc-400 max-w-sm mx-auto">
              Click the heart icon on any YouTube Music track to save it permanently.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
