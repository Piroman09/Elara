import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { Music, Play, Pause } from 'lucide-react';
import { motion } from 'framer-motion';

export const PixelAtAGlance: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const setIsFullscreen = useMusicStore(state => state.setIsFullscreen);
  const currentTheme = useMusicStore(state => state.currentTheme);

  const activeTheme = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];

  // Format today's date in Pixel style: e.g., "Friday, Sep 4"
  const now = new Date();
  const dateOptions: Intl.DateTimeFormatOptions = { weekday: 'long', month: 'short', day: 'numeric' };
  const formattedDate = now.toLocaleDateString('en-US', dateOptions);

  return (
    <div className="w-full py-2 px-1 select-none">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        {/* Left: Pixel Date */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-white font-sans">
              {formattedDate}
            </span>
          </div>
        </div>

        {/* Right: At a Glance Live "Now Playing" Chip */}
        {currentTrack && (
          <motion.div 
            initial={{ opacity: 0, y: -5 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-white/[0.06] hover:bg-white/[0.1] border border-white/10 transition-all shadow-sm max-w-full sm:max-w-md"
          >
            {/* Pulsing visualizer bars */}
            <div 
              onClick={() => setIsFullscreen(true)}
              className="flex items-center gap-1 cursor-pointer"
            >
              {isPlaying ? (
                <div className="flex gap-0.5 items-end h-3.5 w-3.5 flex-shrink-0">
                  <span className="w-1 bg-emerald-400 rounded-full animate-soundwave h-2" style={{ backgroundColor: activeTheme.accent }} />
                  <span className="w-1 bg-emerald-400 rounded-full animate-soundwave h-3.5" style={{ backgroundColor: activeTheme.accent, animationDelay: '0.2s' }} />
                  <span className="w-1 bg-emerald-400 rounded-full animate-soundwave h-1.5" style={{ backgroundColor: activeTheme.accent, animationDelay: '0.4s' }} />
                </div>
              ) : (
                <Music className="w-3.5 h-3.5 text-zinc-400 flex-shrink-0" />
              )}
            </div>

            {/* Track snippet */}
            <div 
              onClick={() => setIsFullscreen(true)}
              className="flex-1 min-w-0 cursor-pointer"
            >
              <div className="text-xs font-bold text-white truncate hover:underline flex items-center gap-1.5">
                <span>{currentTrack.title}</span>
                <span className="text-zinc-500 font-normal">•</span>
                <span className="text-zinc-400 text-[11px] font-medium truncate">{currentTrack.artist}</span>
              </div>
            </div>

            {/* Mini Play / Pause Toggle Button */}
            <button
              onClick={togglePlay}
              className="p-1 rounded-full bg-white/10 hover:bg-white/20 text-white transition-all flex-shrink-0"
              title={isPlaying ? "Pause" : "Play"}
            >
              {isPlaying ? <Pause className="w-3 h-3 fill-current" /> : <Play className="w-3 h-3 fill-current ml-0.5" />}
            </button>
          </motion.div>
        )}
      </div>
    </div>
  );
};
