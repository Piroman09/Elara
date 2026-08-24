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
  Orbit 
} from 'lucide-react';
import { AudioVisualizer } from '../player/AudioVisualizer';
import { AURA_THEMES } from '../../data/mockTracks';

export const Sidebar: React.FC = () => {
  const activeView = useMusicStore(state => state.activeView);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const selectedPlaylistId = useMusicStore(state => state.selectedPlaylistId);
  const playlists = useMusicStore(state => state.playlists);
  const likedTracks = useMusicStore(state => state.likedTracks);
  const setIsCreatePlaylistOpen = useMusicStore(state => state.setIsCreatePlaylistOpen);
  const deletePlaylist = useMusicStore(state => state.deletePlaylist);
  const visualizerMode = useMusicStore(state => state.visualizerMode);
  const currentTheme = useMusicStore(state => state.currentTheme);

  const themeConfig = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];

  return (
    <aside className="w-56 sm:w-60 bg-[#09090b]/85 backdrop-blur-2xl border-r border-white/[0.06] flex flex-col h-full select-none z-20">
      {/* Brand Header */}
      <div 
        onClick={() => setActiveView('galaxy')}
        className="p-5 sm:p-6 flex items-center gap-3 cursor-pointer group"
      >
        <div 
          className="w-9 h-9 rounded-2xl flex items-center justify-center shadow-lg transition-transform group-hover:scale-105"
          style={{
            background: `linear-gradient(135deg, ${themeConfig.accent}, ${themeConfig.secondary})`,
            boxShadow: `0 8px 20px -4px ${themeConfig.accent}50`
          }}
        >
          <Orbit className="w-5 h-5 text-black animate-spin-slow" />
        </div>
        <div className="flex flex-col">
          <span className="font-black text-base tracking-tight text-white group-hover:text-emerald-300 transition-colors flex items-center gap-1.5">
            AuraSonic
            <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-white/10 text-emerald-300 font-mono">LIVE</span>
          </span>
          <span className="text-[10px] text-zinc-400 font-medium">Liquid Music Stream</span>
        </div>
      </div>

      {/* Main Navigation Links */}
      <div className="px-3 space-y-1">
        <button
          onClick={() => setActiveView('galaxy')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'galaxy'
              ? 'bg-white/15 text-white shadow-lg border border-white/10'
              : 'text-zinc-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <Compass className={`w-4 h-4 ${activeView === 'galaxy' ? 'text-emerald-400' : 'text-zinc-400'}`} />
          <span>Vibe Galaxy</span>
        </button>

        <button
          onClick={() => setActiveView('search')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'search'
              ? 'bg-white/15 text-white shadow-lg border border-white/10'
              : 'text-zinc-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <Search className={`w-4 h-4 ${activeView === 'search' ? 'text-emerald-400' : 'text-zinc-400'}`} />
          <span>Search & Explore</span>
        </button>

        <button
          onClick={() => setActiveView('liked')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'liked'
              ? 'bg-white/15 text-white shadow-lg border border-white/10'
              : 'text-zinc-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <div className="w-5 h-5 rounded-lg bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-md">
            <Heart className="w-3 h-3 text-white fill-white" />
          </div>
          <span>Liked Songs ({likedTracks.length})</span>
        </button>

        <button
          onClick={() => setActiveView('library')}
          className={`w-full flex items-center gap-3.5 px-3.5 py-2.5 rounded-2xl text-xs font-bold transition-all ${
            activeView === 'library'
              ? 'bg-white/15 text-white shadow-lg border border-white/10'
              : 'text-zinc-400 hover:text-white hover:bg-white/5'
          }`}
        >
          <Library className={`w-4 h-4 ${activeView === 'library' ? 'text-emerald-400' : 'text-zinc-400'}`} />
          <span>Your Library</span>
        </button>
      </div>

      {/* Section Divider */}
      <div className="px-6 py-3">
        <div className="h-[1px] bg-white/[0.08]" />
      </div>

      {/* Playlists & Collections */}
      <div className="px-3 flex items-center justify-between mb-1.5">
        <span className="px-3 text-[10px] font-bold uppercase tracking-widest text-zinc-500">
          Playlists
        </span>
        <button
          onClick={() => setIsCreatePlaylistOpen(true)}
          className="p-1 rounded-lg hover:bg-white/10 text-zinc-400 hover:text-white transition-colors"
          title="Create Playlist"
        >
          <Plus className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Scrollable Playlist List */}
      <div className="flex-1 overflow-y-auto px-3 py-1 space-y-0.5 no-scrollbar">
        {playlists.map((playlist) => {
          const isSelected = activeView === 'playlist' && selectedPlaylistId === playlist.id;
          return (
            <div
              key={playlist.id}
              onClick={() => setActiveView('playlist', { playlistId: playlist.id })}
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

              {playlist.isCustom && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    deletePlaylist(playlist.id);
                  }}
                  className="opacity-0 group-hover:opacity-100 p-1 hover:text-rose-400 transition-opacity"
                >
                  <Trash2 className="w-3 h-3" />
                </button>
              )}
            </div>
          );
        })}
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
