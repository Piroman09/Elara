import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { 
  Compass, 
  Search, 
  Library, 
  Plus, 
  Heart, 
  Music, 
  Trash2, 
  Sparkles,
  Download,
  User as UserIcon,
  LogOut
} from 'lucide-react';
import { AudioVisualizer } from '../player/AudioVisualizer';

export const Sidebar: React.FC = () => {
  const activeView = useMusicStore(state => state.activeView);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const selectedPlaylistId = useMusicStore(state => state.selectedPlaylistId);
  const playlists = useMusicStore(state => state.playlists);
  const likedTracks = useMusicStore(state => state.likedTracks);
  const downloadedTracks = useMusicStore(state => state.downloadedTracks);
  const setIsCreatePlaylistOpen = useMusicStore(state => state.setIsCreatePlaylistOpen);
  const deletePlaylist = useMusicStore(state => state.deletePlaylist);
  const visualizerMode = useMusicStore(state => state.visualizerMode);

  const user = useMusicStore(state => state.user);
  const setIsAuthModalOpen = useMusicStore(state => state.setIsAuthModalOpen);
  const logoutUser = useMusicStore(state => state.logoutUser);

  return (
    <aside className="w-56 sm:w-60 bg-[#09090b]/85 backdrop-blur-2xl border-r border-white/[0.06] flex flex-col h-full select-none z-20">
      {/* Brand Header */}
      <div 
        onClick={() => setActiveView('galaxy')}
        className="p-5 sm:p-6 flex items-center gap-3 cursor-pointer group"
      >
        <div className="relative w-9 h-9 rounded-2xl overflow-hidden shadow-lg transition-transform group-hover:scale-105 border border-white/20 flex-shrink-0 bg-black">
          <img src="/elara-logo.png" alt="ELARA" className="w-full h-full object-cover" />
        </div>
        <div className="flex flex-col min-w-0">
          <span className="font-black text-base tracking-widest text-white group-hover:text-cyan-300 transition-colors flex items-center gap-1.5 uppercase">
            ELARA
            <span className="text-[8px] px-1.5 py-0.2 rounded-full bg-cyan-500/20 text-cyan-300 font-mono border border-cyan-500/30">AI</span>
          </span>
          <span className="text-[10px] text-zinc-400 font-medium tracking-wide">Sonic Streaming</span>
        </div>
      </div>

      {/* Main Navigation Links */}
      <div className="px-3 space-y-1">
        <button
          onClick={() => setActiveView('galaxy')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'galaxy' 
              ? 'bg-gradient-to-r from-emerald-500/20 to-cyan-500/10 text-emerald-300 border border-emerald-400/30 shadow-lg shadow-emerald-500/10' 
              : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
          }`}
        >
          <Compass className={`w-4 h-4 ${activeView === 'galaxy' ? 'text-emerald-400' : ''}`} />
          <span>Vibe Galaxy</span>
        </button>

        <button
          onClick={() => setActiveView('search')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'search' 
              ? 'bg-gradient-to-r from-emerald-500/20 to-cyan-500/10 text-emerald-300 border border-emerald-400/30 shadow-lg shadow-emerald-500/10' 
              : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
          }`}
        >
          <Search className={`w-4 h-4 ${activeView === 'search' ? 'text-emerald-400' : ''}`} />
          <span>Search & Explore</span>
        </button>

        <button
          onClick={() => setActiveView('library')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'library' 
              ? 'bg-gradient-to-r from-emerald-500/20 to-cyan-500/10 text-emerald-300 border border-emerald-400/30 shadow-lg shadow-emerald-500/10' 
              : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
          }`}
        >
          <Library className={`w-4 h-4 ${activeView === 'library' ? 'text-emerald-400' : ''}`} />
          <span>Your Library</span>
        </button>

        <button
          onClick={() => setActiveView('downloads')}
          className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'downloads' 
              ? 'bg-gradient-to-r from-cyan-500/20 to-indigo-500/10 text-cyan-300 border border-cyan-400/30 shadow-lg shadow-cyan-500/10' 
              : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
          }`}
        >
          <div className="flex items-center gap-3.5">
            <Download className={`w-4 h-4 ${activeView === 'downloads' ? 'text-cyan-400' : ''}`} />
            <span>Offline Downloads</span>
          </div>
          {downloadedTracks.length > 0 && (
            <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/30">
              {downloadedTracks.length}
            </span>
          )}
        </button>
      </div>

      {/* Liked Songs Quick Action */}
      <div className="px-3 pt-4">
        <button
          onClick={() => setActiveView('liked')}
          className={`w-full flex items-center justify-between p-3 rounded-2xl transition-all duration-200 ${
            activeView === 'liked'
              ? 'bg-gradient-to-r from-purple-500/30 to-pink-500/20 text-white border border-purple-400/40 shadow-xl'
              : 'glass-panel hover:bg-white/10 text-zinc-300 border border-white/5'
          }`}
        >
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-indigo-500 to-pink-500 flex items-center justify-center shadow-md">
              <Heart className="w-4 h-4 text-white fill-white" />
            </div>
            <div className="flex flex-col text-left">
              <span className="text-xs font-extrabold text-white">Liked Songs</span>
              <span className="text-[10px] text-purple-300 font-mono">{likedTracks.length} songs</span>
            </div>
          </div>
        </button>
      </div>

      {/* User Playlists Section Header */}
      <div className="px-5 pt-6 pb-2 flex items-center justify-between">
        <span className="text-[10px] font-mono uppercase tracking-wider text-zinc-400 font-bold">
          Playlists
        </span>
        <button
          onClick={() => setIsCreatePlaylistOpen(true)}
          className="p-1 rounded-full text-zinc-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Create Playlist"
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>

      {/* User Playlists Scrollable List */}
      <div className="flex-1 overflow-y-auto px-3 space-y-0.5 custom-scrollbar">
        {playlists.map(playlist => {
          const isSelected = activeView === 'playlist' && selectedPlaylistId === playlist.id;
          return (
            <div
              key={playlist.id}
              onClick={() => setActiveView('playlist', { playlistId: playlist.id, playlistData: playlist })}
              className={`group flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium cursor-pointer transition-all ${
                isSelected
                  ? 'bg-white/15 text-emerald-300 font-bold border border-white/10'
                  : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
              }`}
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <Music className={`w-3.5 h-3.5 flex-shrink-0 ${isSelected ? 'text-emerald-400' : 'text-zinc-500'}`} />
                <span className="truncate">{playlist.name}</span>
              </div>

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  deletePlaylist(playlist.id);
                }}
                className="opacity-0 group-hover:opacity-100 p-1 text-zinc-500 hover:text-rose-400 transition-all rounded hover:bg-rose-500/10"
                title={`Delete ${playlist.name}`}
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          );
        })}
      </div>

      {/* User Auth Card at Bottom of Sidebar */}
      <div className="p-3 border-t border-white/[0.06] bg-black/40">
        {user ? (
          <div className="flex items-center justify-between p-2 rounded-2xl glass-panel border border-white/10">
            <div className="flex items-center gap-2.5 min-w-0">
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.name} className="w-8 h-8 rounded-full object-cover border border-emerald-400/40" />
              ) : (
                <div className="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-300 flex items-center justify-center font-bold text-xs">
                  {user.name.charAt(0)}
                </div>
              )}
              <div className="flex flex-col min-w-0">
                <span className="text-xs font-extrabold text-white truncate">{user.name}</span>
                <span className="text-[9px] text-emerald-400 font-mono truncate">Connected</span>
              </div>
            </div>
            <button
              onClick={logoutUser}
              className="p-1.5 rounded-xl text-zinc-400 hover:text-rose-400 hover:bg-rose-500/10 transition-all"
              title="Sign Out"
            >
              <LogOut className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <button
            onClick={() => setIsAuthModalOpen(true)}
            className="w-full py-2.5 px-3 rounded-2xl bg-white/10 hover:bg-white/15 border border-white/10 text-xs font-extrabold text-white transition-all flex items-center justify-center gap-2 shadow-md"
          >
            <UserIcon className="w-3.5 h-3.5 text-emerald-400" />
            <span>Sign In / Register</span>
          </button>
        )}
      </div>

      {/* Mini Visualizer at Bottom of Sidebar */}
      {visualizerMode !== 'off' && (
        <div className="p-3 border-t border-white/[0.06] bg-black/30">
          <div className="flex items-center justify-between mb-1 px-1">
            <span className="text-[9px] font-mono uppercase tracking-widest text-zinc-500 flex items-center gap-1.5">
              <Sparkles className="w-2.5 h-2.5 text-emerald-400" />
              Frequency Halo
            </span>
            <span className="text-[9px] font-mono text-zinc-400 uppercase">{visualizerMode}</span>
          </div>
          <div className="rounded-2xl overflow-hidden glass-panel border border-white/5 p-1">
            <AudioVisualizer height={44} />
          </div>
        </div>
      )}
    </aside>
  );
};
