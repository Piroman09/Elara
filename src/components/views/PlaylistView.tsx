import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { TrackRow } from '../common/TrackRow';
import { Play, Pause, Shuffle, Clock, Trash2, AlertTriangle, Search, Eraser } from 'lucide-react';
import { formatTotalDuration } from '../../utils/formatters';

export const PlaylistView: React.FC = () => {
  const selectedPlaylistId = useMusicStore(state => state.selectedPlaylistId);
  const selectedPlaylistData = useMusicStore(state => state.selectedPlaylistData);
  const playlists = useMusicStore(state => state.playlists);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const toggleShuffle = useMusicStore(state => state.toggleShuffle);
  const deletePlaylist = useMusicStore(state => state.deletePlaylist);
  const clearPlaylistTracks = useMusicStore(state => state.clearPlaylistTracks);
  const setActiveView = useMusicStore(state => state.setActiveView);

  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [confirmClearOpen, setConfirmClearOpen] = useState(false);

  // If online playlist data was passed, prioritize it; else look up local playlists
  const playlist = selectedPlaylistData || playlists.find(p => p.id === selectedPlaylistId) || playlists[0];

  if (!playlist) {
    return (
      <div className="p-8 text-center text-zinc-400 space-y-4">
        <p>Playlist not found.</p>
        <button onClick={() => setActiveView('library')} className="px-4 py-2 bg-white/10 rounded-xl text-white font-bold text-xs">
          Back to Library
        </button>
      </div>
    );
  }

  const isPlaylistActive = isPlaying && playlist.tracks.some(t => t.id === currentTrack?.id);
  const totalDuration = playlist.tracks.reduce((acc, t) => acc + (t.duration || 180), 0);

  const handlePlayPlaylist = () => {
    if (playlist.tracks.length === 0) return;
    if (isPlaylistActive) {
      togglePlay();
    } else {
      playTrack(playlist.tracks[0], playlist.tracks);
    }
  };

  const handleShufflePlaylist = () => {
    if (playlist.tracks.length === 0) return;
    toggleShuffle();
    const randomIndex = Math.floor(Math.random() * playlist.tracks.length);
    playTrack(playlist.tracks[randomIndex], playlist.tracks);
  };

  const handleDeleteConfirm = () => {
    deletePlaylist(playlist.id);
    setConfirmDeleteOpen(false);
  };

  const handleClearConfirm = () => {
    clearPlaylistTracks(playlist.id);
    setConfirmClearOpen(false);
  };

  return (
    <div className="space-y-6 pb-36 select-none">
      {/* Hero Header */}
      <div 
        className="relative px-6 md:px-10 pt-10 pb-8 flex flex-col md:flex-row items-center md:items-end gap-6 bg-gradient-to-b from-purple-900/40 via-purple-950/20 to-transparent border-b border-white/[0.06]"
        style={{
          background: playlist.color 
            ? `linear-gradient(to bottom, ${playlist.color}40, rgba(9,9,11,0.9))` 
            : undefined
        }}
      >
        {/* Cover Art */}
        <div className="w-44 h-44 sm:w-52 sm:h-52 rounded-3xl overflow-hidden shadow-2xl flex-shrink-0 border border-white/15 bg-zinc-900">
          <img src={playlist.coverArt} alt={playlist.name} className="w-full h-full object-cover" />
        </div>

        {/* Metadata Details */}
        <div className="flex flex-col text-center md:text-left min-w-0 flex-1">
          <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-emerald-300">
            {playlist.isCustom ? 'Custom User Playlist' : 'Playlist Collection'}
          </span>
          <h1 className="text-2xl sm:text-4xl md:text-5xl font-black text-white tracking-tight mt-1 mb-2">
            {playlist.name}
          </h1>
          <p className="text-xs sm:text-sm text-zinc-400 max-w-2xl leading-relaxed">
            {playlist.description || 'Curated playlist in your collection'}
          </p>

          <div className="flex items-center justify-center md:justify-start gap-2 mt-4 text-xs font-medium text-zinc-300">
            <span className="font-bold text-white">{playlist.createdBy || 'You'}</span>
            <span>•</span>
            <span className="text-emerald-300 font-mono font-bold">{playlist.tracks.length} songs</span>
            {playlist.tracks.length > 0 && (
              <>
                <span>•</span>
                <span className="text-zinc-400 font-mono">{formatTotalDuration(totalDuration)}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Controls Bar */}
      <div className="px-6 md:px-10 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <button
            onClick={handlePlayPlaylist}
            disabled={playlist.tracks.length === 0}
            className="w-14 h-14 rounded-full bg-emerald-400 hover:bg-emerald-300 text-black flex items-center justify-center shadow-xl shadow-emerald-400/30 hover:scale-105 active:scale-95 transition-all disabled:opacity-40"
          >
            {isPlaylistActive ? (
              <Pause className="w-6 h-6 fill-black text-black" />
            ) : (
              <Play className="w-6 h-6 fill-black text-black ml-1" />
            )}
          </button>

          <button
            onClick={handleShufflePlaylist}
            disabled={playlist.tracks.length === 0}
            className="p-3 rounded-full hover:bg-white/10 text-zinc-300 hover:text-white transition-colors"
            title="Shuffle Playlist"
          >
            <Shuffle className="w-5 h-5" />
          </button>

          <button
            onClick={() => setActiveView('search')}
            className="px-4 py-2.5 rounded-2xl bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white border border-white/5 transition-all flex items-center gap-2 text-xs font-bold"
          >
            <Search className="w-4 h-4 text-cyan-400" />
            <span>Add Songs</span>
          </button>
        </div>

        {/* Playlist Management Actions */}
        <div className="flex items-center gap-2">
          {playlist.tracks.length > 0 && (
            <button
              onClick={() => setConfirmClearOpen(true)}
              className="px-3.5 py-2 rounded-2xl bg-white/5 hover:bg-amber-500/20 text-zinc-400 hover:text-amber-300 border border-white/5 transition-colors flex items-center gap-2 text-xs font-bold"
              title="Remove all songs from this playlist"
            >
              <Eraser className="w-4 h-4" />
              <span>Clear Songs</span>
            </button>
          )}

          <button
            onClick={() => setConfirmDeleteOpen(true)}
            className="px-3.5 py-2 rounded-2xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 hover:text-rose-200 border border-rose-500/20 transition-colors flex items-center gap-2 text-xs font-bold shadow-lg shadow-rose-950/20"
            title="Delete this playlist"
          >
            <Trash2 className="w-4 h-4 text-rose-400" />
            <span>Delete Playlist</span>
          </button>
        </div>
      </div>

      {/* Tracks Table */}
      <div className="px-6 md:px-10">
        {playlist.tracks.length === 0 ? (
          <div className="py-16 text-center glass-panel rounded-3xl border border-dashed border-zinc-800 space-y-4 max-w-md mx-auto p-8">
            <div className="w-12 h-12 rounded-2xl bg-zinc-800/80 flex items-center justify-center mx-auto text-zinc-400">
              <Search className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-bold text-white">This playlist is empty</h3>
              <p className="text-xs text-zinc-400">Search for tracks and add them using the "•••" menu</p>
            </div>
            <button
              onClick={() => setActiveView('search')}
              className="px-5 py-2.5 rounded-2xl bg-emerald-400 hover:bg-emerald-300 text-black font-extrabold text-xs shadow-lg shadow-emerald-400/20 transition-all inline-flex items-center gap-2"
            >
              <Search className="w-3.5 h-3.5" />
              Search & Add Songs
            </button>
          </div>
        ) : (
          <div className="space-y-1">
            <div className="grid grid-cols-[36px_1fr_auto] md:grid-cols-[36px_4fr_3fr_80px_40px] gap-4 px-4 py-2 text-zinc-400 text-xs font-mono border-b border-white/[0.06] mb-2 uppercase tracking-wider">
              <span>#</span>
              <span>Title</span>
              <span className="hidden md:block">Album / Source</span>
              <span className="hidden md:flex justify-end items-center">
                <Clock className="w-3.5 h-3.5" />
              </span>
              <span className="text-right">Actions</span>
            </div>

            {playlist.tracks.map((track, idx) => (
              <TrackRow
                key={`${track.id}-${idx}`}
                track={track}
                index={idx}
                contextPlaylistTracks={playlist.tracks}
                currentPlaylistId={playlist.id}
                canRemoveFromPlaylist={true}
              />
            ))}
          </div>
        )}
      </div>

      {/* Confirm Delete Playlist Modal */}
      {confirmDeleteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-black/75 backdrop-blur-sm"
            onClick={() => setConfirmDeleteOpen(false)}
          />
          <div className="relative w-full max-w-sm glass-panel-elevated rounded-3xl p-6 border border-white/15 shadow-2xl text-center space-y-4 z-10">
            <div className="w-12 h-12 rounded-2xl bg-rose-500/20 text-rose-400 flex items-center justify-center mx-auto border border-rose-500/30">
              <AlertTriangle className="w-6 h-6" />
            </div>

            <div>
              <h3 className="text-lg font-extrabold text-white">Delete Playlist?</h3>
              <p className="text-xs text-zinc-400 mt-1">
                Are you sure you want to delete <span className="font-bold text-white">"{playlist.name}"</span>? This will permanently remove the playlist from your library.
              </p>
            </div>

            <div className="flex items-center justify-center gap-3 pt-2">
              <button
                onClick={() => setConfirmDeleteOpen(false)}
                className="px-4 py-2 rounded-2xl bg-white/10 hover:bg-white/20 text-xs font-bold text-zinc-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteConfirm}
                className="px-5 py-2 rounded-2xl bg-rose-500 hover:bg-rose-400 text-xs font-extrabold text-white shadow-lg shadow-rose-500/30 transition-all"
              >
                Delete Playlist
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Clear All Songs Modal */}
      {confirmClearOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-black/75 backdrop-blur-sm"
            onClick={() => setConfirmClearOpen(false)}
          />
          <div className="relative w-full max-w-sm glass-panel-elevated rounded-3xl p-6 border border-white/15 shadow-2xl text-center space-y-4 z-10">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/20 text-amber-400 flex items-center justify-center mx-auto border border-amber-500/30">
              <Eraser className="w-6 h-6" />
            </div>

            <div>
              <h3 className="text-lg font-extrabold text-white">Clear All Songs?</h3>
              <p className="text-xs text-zinc-400 mt-1">
                Are you sure you want to remove all <span className="font-bold text-white">{playlist.tracks.length} songs</span> from <span className="font-bold text-white">"{playlist.name}"</span>?
              </p>
            </div>

            <div className="flex items-center justify-center gap-3 pt-2">
              <button
                onClick={() => setConfirmClearOpen(false)}
                className="px-4 py-2 rounded-2xl bg-white/10 hover:bg-white/20 text-xs font-bold text-zinc-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleClearConfirm}
                className="px-5 py-2 rounded-2xl bg-amber-500 hover:bg-amber-400 text-xs font-extrabold text-black shadow-lg shadow-amber-500/30 transition-all"
              >
                Clear All
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
