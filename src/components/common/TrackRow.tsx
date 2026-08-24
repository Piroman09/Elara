import React, { useState } from 'react';
import { Track } from '../../types/music';
import { useMusicStore } from '../../store/useMusicStore';
import { formatTime } from '../../utils/formatters';
import { Play, Pause, Heart, MoreHorizontal, Plus, ListPlus, Radio, Trash2, Download } from 'lucide-react';

interface TrackRowProps {
  track: Track;
  index: number;
  showAlbum?: boolean;
  showDateAdded?: boolean;
  contextPlaylistTracks?: Track[];
  currentPlaylistId?: string;
  canRemoveFromPlaylist?: boolean;
}

export const TrackRow: React.FC<TrackRowProps> = ({
  track,
  index,
  showAlbum = true,
  showDateAdded = true,
  contextPlaylistTracks,
  currentPlaylistId,
  canRemoveFromPlaylist = false
}) => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const likedTrackIds = useMusicStore(state => state.likedTrackIds);
  const toggleLikeTrack = useMusicStore(state => state.toggleLikeTrack);
  const addToQueue = useMusicStore(state => state.addToQueue);
  const playlists = useMusicStore(state => state.playlists);
  const addTrackToPlaylist = useMusicStore(state => state.addTrackToPlaylist);
  const removeTrackFromPlaylist = useMusicStore(state => state.removeTrackFromPlaylist);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const downloadTrack = useMusicStore(state => state.downloadTrack);

  const [menuOpen, setMenuOpen] = useState(false);
  const isCurrentTrack = currentTrack?.id === track.id;
  const isLiked = likedTrackIds.includes(track.id);

  const handlePlayClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isCurrentTrack) {
      togglePlay();
    } else {
      playTrack(track, contextPlaylistTracks);
    }
  };

  const handleRemoveFromPlaylist = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (currentPlaylistId) {
      removeTrackFromPlaylist(currentPlaylistId, track.id);
    }
  };

  return (
    <div 
      onClick={() => isCurrentTrack ? togglePlay() : playTrack(track, contextPlaylistTracks)}
      className={`group grid grid-cols-[16px_4fr_3fr_minmax(100px,2fr)_minmax(80px,1fr)] items-center gap-4 px-4 py-2.5 rounded-2xl transition-all duration-150 cursor-pointer ${
        isCurrentTrack 
          ? 'bg-white/10 text-white shadow-lg backdrop-blur-md border border-white/10' 
          : 'hover:bg-white/5 text-zinc-400 hover:text-zinc-200'
      }`}
    >
      {/* Track Index / Play status */}
      <div className="flex items-center justify-center w-5">
        {isCurrentTrack && isPlaying ? (
          <div className="flex items-end gap-[2px] h-3.5">
            <span className="w-[3px] bg-emerald-400 animate-soundwave rounded-full h-full" style={{ animationDelay: '0ms' }} />
            <span className="w-[3px] bg-emerald-400 animate-soundwave rounded-full h-full" style={{ animationDelay: '200ms' }} />
            <span className="w-[3px] bg-emerald-400 animate-soundwave rounded-full h-full" style={{ animationDelay: '400ms' }} />
          </div>
        ) : (
          <>
            <span className="group-hover:hidden text-xs font-mono font-medium text-zinc-500">
              {index + 1}
            </span>
            <button
              onClick={handlePlayClick}
              className="hidden group-hover:flex items-center justify-center text-white hover:scale-110 transition-transform"
            >
              {isCurrentTrack && isPlaying ? (
                <Pause className="w-4 h-4 fill-white" />
              ) : (
                <Play className="w-4 h-4 fill-white ml-0.5" />
              )}
            </button>
          </>
        )}
      </div>

      {/* Title & Artist & Album Thumbnail */}
      <div className="flex items-center gap-3.5 min-w-0 pr-2">
        <div className="relative w-10 h-10 rounded-xl overflow-hidden flex-shrink-0 shadow-md bg-zinc-900">
          <img 
            src={track.albumArt} 
            alt={track.title} 
            loading="lazy"
            className="w-full h-full object-cover"
          />
        </div>
        <div className="flex flex-col min-w-0">
          <span className={`text-xs sm:text-sm font-bold truncate ${isCurrentTrack ? 'text-emerald-400' : 'text-zinc-100'}`}>
            {track.title}
          </span>
          <span 
            onClick={(e) => {
              e.stopPropagation();
              if (track.artistId) {
                setActiveView('artist', { artistId: track.artistId });
              }
            }}
            className="text-[11px] text-zinc-400 hover:text-zinc-200 truncate transition-colors"
          >
            {track.artist}
          </span>
        </div>
      </div>

      {/* Album */}
      {showAlbum && (
        <div className="text-xs text-zinc-400 truncate hover:text-zinc-200 transition-colors">
          {track.album}
        </div>
      )}

      {/* Date Added */}
      {showDateAdded && (
        <div className="text-xs text-zinc-500 font-mono">
          {track.addedAt || 'Today'}
        </div>
      )}

      {/* Actions: Like, Remove, Duration, Menu */}
      <div className="flex items-center justify-end gap-2 font-mono text-xs">
        {/* Remove from playlist quick button */}
        {canRemoveFromPlaylist && currentPlaylistId && (
          <button
            onClick={handleRemoveFromPlaylist}
            className="p-1.5 rounded-full hover:bg-rose-500/20 text-zinc-500 hover:text-rose-400 opacity-0 group-hover:opacity-100 transition-all"
            title="Remove from playlist"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}

        {/* Permanent Like Button */}
        <button
          onClick={(e) => {
            e.stopPropagation();
            toggleLikeTrack(track);
          }}
          className={`p-1.5 rounded-full hover:scale-110 active:scale-90 transition-all ${
            isLiked 
              ? 'text-emerald-400 opacity-100' 
              : 'text-zinc-500 hover:text-white opacity-0 group-hover:opacity-100'
          }`}
          title={isLiked ? "Saved in Liked Songs" : "Add to Liked Songs"}
        >
          <Heart className={`w-4 h-4 ${isLiked ? 'fill-emerald-400 stroke-emerald-400' : ''}`} />
        </button>

        <span className="text-zinc-400 w-10 text-right">
          {formatTime(track.duration)}
        </span>

        {/* Dropdown Menu */}
        <div className="relative">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setMenuOpen(!menuOpen);
            }}
            className="p-1 rounded-full text-zinc-400 hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <MoreHorizontal className="w-4 h-4" />
          </button>

          {menuOpen && (
            <>
              <div 
                className="fixed inset-0 z-40" 
                onClick={(e) => {
                  e.stopPropagation();
                  setMenuOpen(false);
                }} 
              />
              <div className="absolute right-0 bottom-full mb-2 w-56 py-2 glass-panel-elevated rounded-2xl z-50 shadow-2xl border border-zinc-700/60 font-sans">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    addToQueue(track);
                    setMenuOpen(false);
                  }}
                  className="w-full px-4 py-2 text-left text-xs text-zinc-200 hover:bg-white/10 flex items-center gap-2.5 transition-colors"
                >
                  <ListPlus className="w-3.5 h-3.5 text-zinc-400" />
                  Add to queue
                </button>

                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    downloadTrack(track);
                    setMenuOpen(false);
                  }}
                  className="w-full px-4 py-2 text-left text-xs text-cyan-300 hover:bg-cyan-500/10 flex items-center gap-2.5 transition-colors"
                >
                  <Download className="w-3.5 h-3.5 text-cyan-400" />
                  Download MP4
                </button>

                {/* Remove from this playlist item */}
                {canRemoveFromPlaylist && currentPlaylistId && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleRemoveFromPlaylist(e);
                      setMenuOpen(false);
                    }}
                    className="w-full px-4 py-2 text-left text-xs text-rose-400 hover:bg-rose-500/10 flex items-center gap-2.5 transition-colors border-t border-zinc-800"
                  >
                    <Trash2 className="w-3.5 h-3.5 text-rose-400" />
                    Remove from this playlist
                  </button>
                )}

                {playlists.length > 0 && (
                  <div className="border-t border-zinc-800 my-1 py-1">
                    <div className="px-4 py-1 text-[10px] uppercase font-bold tracking-wider text-zinc-500">
                      Add to Playlist
                    </div>
                    {playlists.map(pl => (
                      <button
                        key={pl.id}
                        onClick={(e) => {
                          e.stopPropagation();
                          addTrackToPlaylist(pl.id, track);
                          setMenuOpen(false);
                        }}
                        className="w-full px-4 py-1.5 text-left text-xs text-zinc-300 hover:bg-white/10 flex items-center gap-2 truncate transition-colors"
                      >
                        <Plus className="w-3.5 h-3.5 text-zinc-400" />
                        <span className="truncate">{pl.name}</span>
                      </button>
                    ))}
                  </div>
                )}

                {track.artistId && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setActiveView('artist', { artistId: track.artistId });
                      setMenuOpen(false);
                    }}
                    className="w-full px-4 py-2 text-left text-xs text-zinc-200 hover:bg-white/10 flex items-center gap-2.5 transition-colors border-t border-zinc-800"
                  >
                    <Radio className="w-3.5 h-3.5 text-zinc-400" />
                    Go to Artist
                  </button>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
