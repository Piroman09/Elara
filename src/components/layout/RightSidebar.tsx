import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { X, Play, Trash2, Mic2, ListMusic, Sparkles } from 'lucide-react';
import { MOCK_ARTISTS } from '../../data/mockTracks';
import { formatTime } from '../../utils/formatters';

export const RightSidebar: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const queue = useMusicStore(state => state.queue);
  const isQueueOpen = useMusicStore(state => state.isQueueOpen);
  const setIsQueueOpen = useMusicStore(state => state.setIsQueueOpen);
  const removeFromQueue = useMusicStore(state => state.removeFromQueue);
  const clearQueue = useMusicStore(state => state.clearQueue);
  const playTrack = useMusicStore(state => state.playTrack);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const setIsLyricsOpen = useMusicStore(state => state.setIsLyricsOpen);

  if (!isQueueOpen) return null;

  const currentArtist = currentTrack?.artistId 
    ? MOCK_ARTISTS.find(a => a.id === currentTrack.artistId)
    : null;

  return (
    <aside className="w-80 bg-[#0c0c10]/95 backdrop-blur-2xl border-l border-white/[0.06] flex flex-col h-full z-20 select-none shadow-2xl animate-in slide-in-from-right duration-300">
      {/* Header */}
      <div className="p-5 flex items-center justify-between border-b border-white/[0.06]">
        <div className="flex items-center gap-2">
          <ListMusic className="w-5 h-5 text-spotify-green" />
          <span className="font-bold text-sm text-white">Up Next & Info</span>
        </div>
        <button
          onClick={() => setIsQueueOpen(false)}
          className="p-1 rounded-full text-zinc-400 hover:text-white hover:bg-white/10 transition-all"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Now Playing Mini Card */}
        {currentTrack && (
          <div className="space-y-3">
            <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-400">
              Now Playing
            </span>
            <div className="p-3.5 rounded-2xl glass-panel border border-white/10 flex flex-col gap-3">
              <div className="relative aspect-video rounded-xl overflow-hidden shadow-lg">
                <img 
                  src={currentTrack.albumArt} 
                  alt={currentTrack.title} 
                  className="w-full h-full object-cover"
                />
                <div className="absolute top-2 right-2 px-2 py-0.5 rounded-full bg-black/60 backdrop-blur-md text-[10px] font-semibold text-emerald-400 border border-emerald-500/20">
                  {currentTrack.genre}
                </div>
              </div>

              <div>
                <h4 className="font-bold text-sm text-white truncate">{currentTrack.title}</h4>
                <p className="text-xs text-zinc-400 truncate mt-0.5">{currentTrack.artist}</p>
              </div>

              <div className="flex items-center gap-2 pt-1 border-t border-white/5">
                <button
                  onClick={() => setIsLyricsOpen(true)}
                  className="flex-1 py-1.5 px-3 rounded-lg bg-white/5 hover:bg-white/10 text-xs font-semibold text-zinc-300 hover:text-white flex items-center justify-center gap-1.5 transition-all"
                >
                  <Mic2 className="w-3.5 h-3.5 text-spotify-green" />
                  View Lyrics
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Up Next Queue */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-400">
              Queue ({queue.length})
            </span>
            {queue.length > 0 && (
              <button
                onClick={clearQueue}
                className="text-[11px] text-zinc-400 hover:text-rose-400 transition-colors"
              >
                Clear all
              </button>
            )}
          </div>

          {queue.length === 0 ? (
            <div className="py-8 text-center glass-panel rounded-xl border border-dashed border-zinc-800">
              <Sparkles className="w-6 h-6 text-zinc-600 mx-auto mb-2" />
              <p className="text-xs text-zinc-500">Queue is empty</p>
              <p className="text-[10px] text-zinc-600 mt-1">Add songs from the playlist or search</p>
            </div>
          ) : (
            <div className="space-y-1.5">
              {queue.map((track, idx) => (
                <div
                  key={`${track.id}-${idx}`}
                  className="group flex items-center justify-between p-2 rounded-xl hover:bg-white/5 transition-all cursor-pointer"
                >
                  <div 
                    onClick={() => playTrack(track, queue.slice(idx + 1))}
                    className="flex items-center gap-3 min-w-0 flex-1"
                  >
                    <div className="relative w-9 h-9 rounded-lg overflow-hidden flex-shrink-0 bg-zinc-800 shadow">
                      <img src={track.albumArt} alt={track.title} className="w-full h-full object-cover" />
                      <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                        <Play className="w-3.5 h-3.5 fill-white text-white ml-0.5" />
                      </div>
                    </div>

                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-semibold text-zinc-200 truncate group-hover:text-white">
                        {track.title}
                      </span>
                      <span className="text-[11px] text-zinc-500 truncate">
                        {track.artist}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-mono text-zinc-500">
                      {formatTime(track.duration)}
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        removeFromQueue(idx);
                      }}
                      className="p-1 text-zinc-500 hover:text-rose-400 opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Artist Card Preview */}
        {currentArtist && (
          <div className="space-y-3 pt-2">
            <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-400">
              About the Artist
            </span>
            <div 
              onClick={() => setActiveView('artist', { artistId: currentArtist.id })}
              className="group p-4 rounded-2xl glass-panel hover:bg-white/[0.08] border border-white/10 cursor-pointer transition-all flex flex-col gap-3"
            >
              <div className="flex items-center gap-3">
                <img 
                  src={currentArtist.image} 
                  alt={currentArtist.name}
                  className="w-12 h-12 rounded-full object-cover shadow-md" 
                />
                <div className="flex flex-col min-w-0">
                  <span className="text-sm font-bold text-white group-hover:text-spotify-green transition-colors">
                    {currentArtist.name}
                  </span>
                  <span className="text-xs text-zinc-400">
                    {currentArtist.monthlyListeners.toLocaleString()} monthly listeners
                  </span>
                </div>
              </div>
              <p className="text-xs text-zinc-400 line-clamp-3 leading-relaxed">
                {currentArtist.bio}
              </p>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};
