import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES, MOCK_PLAYLISTS } from '../../data/mockTracks';
import { Zap, Flame, Play, Pause, Radio, RefreshCw, Shuffle, Sparkles } from 'lucide-react';
import { motion } from 'framer-motion';

export const VibeGalaxyView: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const galaxyTracks = useMusicStore(state => state.galaxyTracks);
  const explorePlaylists = useMusicStore(state => state.explorePlaylists);
  const openOnlinePlaylist = useMusicStore(state => state.openOnlinePlaylist);
  const currentTheme = useMusicStore(state => state.currentTheme);
  const isApiOnline = useMusicStore(state => state.isApiOnline);
  const refreshGalaxyTracks = useMusicStore(state => state.refreshGalaxyTracks);
  const isRefreshingGalaxy = useMusicStore(state => state.isRefreshingGalaxy);

  const activeThemeConfig = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];
  const displayPlaylists = explorePlaylists.length > 0 ? explorePlaylists.slice(0, 8) : MOCK_PLAYLISTS;

  const handlePlayRandomStar = () => {
    if (galaxyTracks.length === 0) return;
    const randomIdx = Math.floor(Math.random() * galaxyTracks.length);
    playTrack(galaxyTracks[randomIdx], galaxyTracks);
  };

  return (
    <div className="p-4 sm:p-6 md:p-8 space-y-8 max-w-7xl mx-auto pb-36 select-none">
      {/* Vibe Galaxy Header */}
      <div className="relative rounded-3xl p-6 sm:p-8 overflow-hidden glass-panel border border-white/10 shadow-2xl">
        <div 
          className="absolute -top-24 -right-24 w-80 h-80 rounded-full filter blur-[100px] opacity-25 pointer-events-none transition-all duration-700"
          style={{ backgroundColor: activeThemeConfig.accent }}
        />

        <div className="relative z-10 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="space-y-1.5 max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-xs font-bold text-emerald-300 border border-emerald-400/20">
              <Radio className={`w-3.5 h-3.5 ${isApiOnline ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
              <span>{isApiOnline ? 'Live Cosmic Soundstream' : 'Curated Vibe Galaxy'}</span>
            </div>
            <h1 className="text-2xl sm:text-4xl font-black text-white tracking-tight flex items-center gap-2.5">
              <span>Interactive Vibe Map</span>
              <Sparkles className="w-6 h-6 text-amber-300" />
            </h1>
            <p className="text-xs sm:text-sm text-zinc-300 leading-relaxed">
              Real-time trending global hit nodes dynamically mapped across celestial sonic coordinates. Click any star to stream instantly.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 self-stretch md:self-auto">
            <button
              onClick={refreshGalaxyTracks}
              disabled={isRefreshingGalaxy}
              className="flex-1 md:flex-initial px-4 py-2.5 rounded-2xl bg-white/10 hover:bg-white/20 border border-white/10 text-xs font-bold text-cyan-200 hover:text-white transition-all shadow-md flex items-center justify-center gap-2 disabled:opacity-50"
              title="Rescan and randomize celestial orbit nodes"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshingGalaxy ? 'animate-spin text-cyan-400' : ''}`} />
              <span>{isRefreshingGalaxy ? 'Rescanning Orbit...' : 'Shuffle Orbit'}</span>
            </button>

            <button
              onClick={handlePlayRandomStar}
              className="flex-1 md:flex-initial px-5 py-2.5 rounded-2xl bg-gradient-to-r from-emerald-400 to-cyan-400 text-black font-extrabold text-xs shadow-lg shadow-emerald-400/30 hover:scale-105 active:scale-95 transition-all flex items-center justify-center gap-2"
            >
              <Shuffle className="w-3.5 h-3.5 fill-black" />
              <span>Play Random Hit</span>
            </button>
          </div>
        </div>
      </div>

      {/* Interactive Constellation Map Canvas */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-black text-white tracking-tight flex items-center gap-2">
            <Zap className="w-4 h-4 text-amber-400" />
            Live Celestial Orbit
          </h2>
          <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
            <span className="w-2 h-2 rounded-full bg-cyan-400 animate-ping" />
            <span>{Math.min(galaxyTracks.length, 24)} Trending Star Nodes</span>
          </div>
        </div>

        {/* 2D Coordinate Galaxy Stage */}
        <div className="relative h-[380px] sm:h-[460px] w-full rounded-3xl bg-[#08080c]/95 border border-white/10 overflow-hidden shadow-2xl p-4 flex items-center justify-center will-change-transform">
          {/* Subtle Grid Backdrop */}
          <div className="absolute inset-0 bg-[radial-gradient(#ffffff_1px,transparent_1px)] [background-size:28px_28px] opacity-10 pointer-events-none" />

          {/* Coordinate Crosshairs */}
          <div className="absolute inset-x-0 top-1/2 h-[1px] bg-white/[0.04] pointer-events-none" />
          <div className="absolute inset-y-0 left-1/2 w-[1px] bg-white/[0.04] pointer-events-none" />

          {/* Quadrant Labels */}
          <span className="absolute top-3 left-4 text-[9px] font-mono uppercase tracking-widest text-cyan-500/80 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
            High Velocity • Trending Pop
          </span>
          <span className="absolute top-3 right-4 text-[9px] font-mono uppercase tracking-widest text-purple-400/80 bg-purple-500/10 px-2 py-0.5 rounded border border-purple-500/20">
            Ethereal • Electronic / Dance
          </span>
          <span className="absolute bottom-3 left-4 text-[9px] font-mono uppercase tracking-widest text-amber-400/80 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20">
            Viral • Hip Hop & R&B
          </span>
          <span className="absolute bottom-3 right-4 text-[9px] font-mono uppercase tracking-widest text-emerald-400/80 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
            Deep Ambient • Chill Lo-Fi
          </span>

          {/* Render Floating Celestial Track Stars */}
          {galaxyTracks.slice(0, 24).map((track, idx) => {
            const isCurrent = currentTrack?.id === track.id;
            const coords = track.vibeCoords || { 
              x: 12 + ((idx * 23) % 76), 
              y: 15 + ((idx * 31) % 68) 
            };

            return (
              <motion.div
                key={`${track.id}-${idx}`}
                whileHover={{ scale: 1.25, zIndex: 40 }}
                transition={{ type: 'spring', damping: 20, stiffness: 300 }}
                onClick={() => {
                  if (isCurrent) {
                    togglePlay();
                  } else {
                    playTrack(track, galaxyTracks);
                  }
                }}
                className="absolute cursor-pointer transform -translate-x-1/2 -translate-y-1/2 group will-change-transform"
                style={{
                  left: `${coords.x}%`,
                  top: `${coords.y}%`
                }}
              >
                {/* Pulsating Glowing Aura Ring */}
                <div 
                  className={`absolute -inset-2.5 rounded-2xl transition-all duration-500 filter blur-md ${
                    isCurrent && isPlaying 
                      ? 'scale-150 animate-pulse opacity-90' 
                      : 'opacity-40 scale-100 group-hover:scale-125 group-hover:opacity-75'
                  }`}
                  style={{ backgroundColor: track.accentColor || activeThemeConfig.accent }}
                />

                {/* Star Core Button */}
                <div 
                  className={`relative w-11 h-11 sm:w-12 sm:h-12 rounded-2xl overflow-hidden border-2 shadow-2xl flex items-center justify-center transition-all bg-zinc-950 ${
                    isCurrent 
                      ? 'border-white ring-4 ring-emerald-400/50 shadow-[0_0_25px_rgba(0,255,135,0.8)] scale-110' 
                      : 'border-white/20 group-hover:border-white/80'
                  }`}
                >
                  <img 
                    src={track.albumArt} 
                    alt={track.title} 
                    loading="lazy" 
                    className="w-full h-full object-cover" 
                  />

                  {/* Play / Pause Overlay */}
                  <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                    {isCurrent && isPlaying ? (
                      <Pause className="w-4 h-4 fill-white text-white" />
                    ) : (
                      <Play className="w-4 h-4 fill-white text-white ml-0.5" />
                    )}
                  </div>
                </div>

                {/* Interactive Star Title Pill */}
                <div className="absolute top-full mt-2 left-1/2 -translate-x-1/2 whitespace-nowrap pointer-events-none opacity-0 group-hover:opacity-100 transition-all duration-200 z-50 transform group-hover:translate-y-0 translate-y-1">
                  <div className="px-3 py-1.5 rounded-xl glass-panel-elevated border border-white/20 shadow-2xl text-center backdrop-blur-xl">
                    <p className="text-[11px] font-extrabold text-white max-w-[170px] truncate">{track.title}</p>
                    <p className="text-[9px] text-zinc-400 max-w-[170px] truncate">{track.artist}</p>
                    <div className="flex items-center justify-center gap-1.5 mt-0.5">
                      <span className="text-[8px] px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-300 font-mono">
                        TOP HIT
                      </span>
                    </div>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </section>

      {/* Featured Playlists */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-black text-white tracking-tight flex items-center gap-2">
              <Flame className="w-4 h-4 text-pink-500" />
              Featured Sonic Playlists
            </h2>
            <p className="text-xs text-zinc-400">Curated global soundscape collections</p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {displayPlaylists.map((playlist, idx) => (
            <motion.div
              key={`${playlist.id}-${idx}`}
              whileHover={{ y: -4 }}
              transition={{ type: 'spring', damping: 20, stiffness: 300 }}
              onClick={() => openOnlinePlaylist(playlist)}
              className="group relative p-3.5 rounded-3xl glass-panel hover:bg-white/[0.08] border border-white/10 transition-all duration-200 cursor-pointer overflow-hidden shadow-xl"
            >
              <div className="relative aspect-video rounded-2xl overflow-hidden mb-3 shadow-lg bg-zinc-900">
                <img 
                  src={playlist.coverArt} 
                  alt={playlist.name} 
                  loading="lazy" 
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" 
                />
                
                <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/60 backdrop-blur-md text-[9px] font-mono font-bold text-emerald-300 border border-emerald-400/30">
                  {playlist.createdBy || 'ELARA Curated'}
                </div>

                <div className="absolute bottom-2.5 right-2.5 w-9 h-9 rounded-full bg-emerald-400 flex items-center justify-center shadow-2xl opacity-0 group-hover:opacity-100 transition-opacity">
                  <Play className="w-4 h-4 fill-black text-black ml-0.5" />
                </div>
              </div>

              <h3 className="font-extrabold text-xs sm:text-sm text-white group-hover:text-emerald-300 transition-colors truncate">
                {playlist.name}
              </h3>
              <p className="text-[11px] text-zinc-400 line-clamp-1 mt-0.5">
                {playlist.description}
              </p>
            </motion.div>
          ))}
        </div>
      </section>
    </div>
  );
};
