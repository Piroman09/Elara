import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { formatTime } from '../../utils/formatters';
import { 
  Play, 
  Pause, 
  SkipBack, 
  SkipForward, 
  Shuffle, 
  Repeat, 
  Repeat1, 
  Heart, 
  Volume2, 
  VolumeX, 
  Volume1, 
  Mic2, 
  Sliders, 
  Maximize2, 
  Sparkles, 
  Waves, 
  BarChart2, 
  Disc, 
  Disc3 
} from 'lucide-react';
import { motion } from 'framer-motion';
import { VisualizerMode } from '../../types/music';

export const FloatingIslandPlayer: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTime = useMusicStore(state => state.currentTime);
  const duration = useMusicStore(state => state.duration);
  const volume = useMusicStore(state => state.volume);
  const isMuted = useMusicStore(state => state.isMuted);
  const isShuffle = useMusicStore(state => state.isShuffle);
  const repeatMode = useMusicStore(state => state.repeatMode);
  const likedTrackIds = useMusicStore(state => state.likedTrackIds);
  const isLyricsOpen = useMusicStore(state => state.isLyricsOpen);
  const visualizerMode = useMusicStore(state => state.visualizerMode);
  const activePlaybackSource = useMusicStore(state => state.activePlaybackSource);

  const togglePlay = useMusicStore(state => state.togglePlay);
  const nextTrack = useMusicStore(state => state.nextTrack);
  const prevTrack = useMusicStore(state => state.prevTrack);
  const seek = useMusicStore(state => state.seek);
  const setVolume = useMusicStore(state => state.setVolume);
  const toggleMute = useMusicStore(state => state.toggleMute);
  const toggleShuffle = useMusicStore(state => state.toggleShuffle);
  const toggleRepeat = useMusicStore(state => state.toggleRepeat);
  const toggleLikeTrack = useMusicStore(state => state.toggleLikeTrack);
  const setIsLyricsOpen = useMusicStore(state => state.setIsLyricsOpen);
  const setIsEqualizerOpen = useMusicStore(state => state.setIsEqualizerOpen);
  const setIsFullscreen = useMusicStore(state => state.setIsFullscreen);
  const setVisualizerMode = useMusicStore(state => state.setVisualizerMode);
  const setActiveView = useMusicStore(state => state.setActiveView);

  const isLiked = currentTrack ? likedTrackIds.includes(currentTrack.id) : false;
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;
  const accentColor = currentTrack?.accentColor || '#00ff87';

  const visualizerCycle: VisualizerMode[] = ['halo', 'bars', 'wave', 'particles', 'vinyl'];
  const nextVisualizer = () => {
    const nextIdx = (visualizerCycle.indexOf(visualizerMode) + 1) % visualizerCycle.length;
    setVisualizerMode(visualizerCycle[nextIdx]);
  };

  const getVolumeIcon = () => {
    if (isMuted || volume === 0) return VolumeX;
    if (volume < 0.5) return Volume1;
    return Volume2;
  };
  const VolumeIcon = getVolumeIcon();

  return (
    <div className="fixed bottom-4 inset-x-0 z-40 px-3 sm:px-8 flex justify-center pointer-events-none select-none">
      {/* Floating Island Container */}
      <motion.div
        layout
        initial={{ y: 50, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ type: 'spring', damping: 25, stiffness: 220 }}
        className="w-full max-w-5xl pointer-events-auto p-3 sm:px-6 rounded-3xl glass-panel-elevated border border-white/15 shadow-[0_15px_45px_rgba(0,0,0,0.8)] backdrop-blur-2xl flex flex-col md:flex-row items-center justify-between gap-3 relative overflow-hidden"
        style={{
          boxShadow: `0 12px 40px -10px ${accentColor}30, 0 0 0 1px rgba(255,255,255,0.12)`
        }}
      >
        {/* Subtle Bottom Accent Glow */}
        <div 
          className="absolute inset-x-0 bottom-0 h-[2px] transition-all duration-700"
          style={{
            background: `linear-gradient(to right, transparent, ${accentColor}, transparent)`
          }}
        />

        {/* Left Section: Spinning Tactile Vinyl & Track Info */}
        <div className="flex items-center gap-3 min-w-0 md:w-[32%]">
          {currentTrack ? (
            <>
              {/* Interactive Spinning Vinyl Disc */}
              <div 
                onClick={() => setIsFullscreen(true)}
                className="relative w-11 h-11 sm:w-12 sm:h-12 rounded-full overflow-hidden flex-shrink-0 cursor-pointer group shadow-xl border border-white/20 bg-zinc-950 flex items-center justify-center will-change-transform"
              >
                <img 
                  src={currentTrack.albumArt} 
                  alt={currentTrack.title} 
                  loading="lazy"
                  className={`w-full h-full object-cover rounded-full ${
                    isPlaying ? 'animate-spin-slow' : ''
                  } group-hover:scale-105 transition-transform`}
                />
                <div className="absolute w-3.5 h-3.5 rounded-full bg-[#09090b] border border-white/40 ring-1 ring-black" />
                
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity rounded-full">
                  <Maximize2 className="w-4 h-4 text-white" />
                </div>
              </div>

              <div className="flex flex-col min-w-0">
                <span 
                  onClick={() => setIsFullscreen(true)}
                  className="text-xs sm:text-sm font-black text-white hover:text-emerald-400 cursor-pointer truncate transition-colors drop-shadow-sm flex items-center gap-1.5"
                >
                  {currentTrack.title}
                  {isPlaying && (
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse flex-shrink-0" />
                  )}
                </span>
                <span 
                  onClick={() => currentTrack.artistId && setActiveView('artist', { artistId: currentTrack.artistId })}
                  className="text-[11px] text-zinc-400 hover:text-zinc-200 cursor-pointer truncate transition-colors flex items-center gap-1.5"
                >
                  <span className="truncate">{currentTrack.artist}</span>
                  {activePlaybackSource === 'offline-mp4' && (
                    <span className="px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 text-[9px] font-mono font-bold flex-shrink-0 border border-emerald-500/30">
                      MP4
                    </span>
                  )}
                </span>
              </div>

              <button
                onClick={() => toggleLikeTrack(currentTrack)}
                className={`p-1.5 rounded-full hover:scale-110 active:scale-90 transition-all ${
                  isLiked ? 'text-emerald-400' : 'text-zinc-500 hover:text-white'
                }`}
                title={isLiked ? "Remove from Liked" : "Add to Liked Songs"}
              >
                <Heart className={`w-4 h-4 ${isLiked ? 'fill-emerald-400 stroke-emerald-400' : ''}`} />
              </button>
            </>
          ) : (
            <div className="text-xs text-zinc-500 italic">No track loaded</div>
          )}
        </div>

        {/* Center Section: Playback Controls & Precision Scrubber */}
        <div className="flex flex-col items-center gap-1 flex-1 max-w-lg w-full">
          <div className="flex items-center gap-4">
            <button
              onClick={toggleShuffle}
              className={`p-1 rounded-full relative transition-all ${
                isShuffle ? 'text-emerald-400' : 'text-zinc-400 hover:text-white'
              }`}
              title="Shuffle"
            >
              <Shuffle className="w-3.5 h-3.5" />
            </button>

            <button
              onClick={prevTrack}
              className="p-1 text-zinc-300 hover:text-white hover:scale-110 active:scale-95 transition-all"
            >
              <SkipBack className="w-4 h-4 fill-current" />
            </button>

            {/* Glowing Main Play/Pause Pill */}
            <button
              onClick={togglePlay}
              className="px-5 py-2 rounded-full bg-white hover:bg-zinc-100 text-black flex items-center justify-center shadow-lg shadow-white/20 hover:scale-105 active:scale-95 transition-all font-bold"
            >
              {isPlaying ? (
                <Pause className="w-4 h-4 fill-black" />
              ) : (
                <Play className="w-4 h-4 fill-black ml-0.5" />
              )}
            </button>

            <button
              onClick={nextTrack}
              className="p-1 text-zinc-300 hover:text-white hover:scale-110 active:scale-95 transition-all"
            >
              <SkipForward className="w-4 h-4 fill-current" />
            </button>

            <button
              onClick={toggleRepeat}
              className={`p-1 rounded-full relative transition-all ${
                repeatMode !== 'off' ? 'text-emerald-400' : 'text-zinc-400 hover:text-white'
              }`}
              title={`Repeat: ${repeatMode}`}
            >
              {repeatMode === 'one' ? <Repeat1 className="w-3.5 h-3.5" /> : <Repeat className="w-3.5 h-3.5" />}
            </button>
          </div>

          {/* Micro Progress Bar */}
          <div className="w-full flex items-center gap-2.5 text-[10px] font-mono text-zinc-400">
            <span className="w-8 text-right">{formatTime(currentTime)}</span>

            <div className="relative flex-1 flex items-center group">
              <div className="absolute inset-x-0 h-1 rounded-full bg-zinc-800 pointer-events-none overflow-hidden">
                <div 
                  className="h-full rounded-full transition-all duration-100" 
                  style={{ 
                    width: `${progressPercent}%`,
                    background: `linear-gradient(to right, #00ff87, ${accentColor})`
                  }}
                />
              </div>
              <input
                type="range"
                min={0}
                max={duration || 100}
                step={0.1}
                value={currentTime}
                onChange={(e) => seek(parseFloat(e.target.value))}
                className="w-full h-3 opacity-0 cursor-pointer relative z-10"
              />
            </div>

            <span className="w-8 text-left">{formatTime(duration)}</span>
          </div>
        </div>

        {/* Right Section: Visualizer, Lyrics, Studio EQ & Volume */}
        <div className="flex items-center justify-end gap-2.5 min-w-0 md:w-[32%]">
          {/* Visualizer Mode Cycle Button */}
          <button
            onClick={nextVisualizer}
            className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-zinc-400 hover:text-white border border-white/5 transition-all"
            title={`Visualizer: ${visualizerMode}`}
          >
            {visualizerMode === 'halo' && <Sparkles className="w-3.5 h-3.5 text-emerald-400" />}
            {visualizerMode === 'bars' && <BarChart2 className="w-3.5 h-3.5 text-emerald-400" />}
            {visualizerMode === 'wave' && <Waves className="w-3.5 h-3.5 text-emerald-400" />}
            {visualizerMode === 'particles' && <Sparkles className="w-3.5 h-3.5 text-cyan-400" />}
            {visualizerMode === 'vinyl' && <Disc3 className="w-3.5 h-3.5 text-emerald-400" />}
            {visualizerMode === 'off' && <Disc className="w-3.5 h-3.5 text-zinc-500" />}
          </button>

          {/* Lyrics Overlay Toggle */}
          <button
            onClick={() => setIsLyricsOpen(!isLyricsOpen)}
            className={`p-2 rounded-full transition-all ${
              isLyricsOpen ? 'bg-white/20 text-emerald-400' : 'text-zinc-400 hover:text-white'
            }`}
            title="Live Karaoke Lyrics"
          >
            <Mic2 className="w-3.5 h-3.5" />
          </button>

          {/* Studio EQ */}
          <button
            onClick={() => setIsEqualizerOpen(true)}
            className="p-2 rounded-full text-zinc-400 hover:text-white transition-all"
            title="Equalizer & Bass Boost"
          >
            <Sliders className="w-3.5 h-3.5" />
          </button>

          {/* Volume Slider */}
          <div className="hidden sm:flex items-center gap-1.5 w-24">
            <button onClick={toggleMute} className="text-zinc-400 hover:text-white p-1">
              <VolumeIcon className="w-3.5 h-3.5" />
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={isMuted ? 0 : volume}
              onChange={(e) => setVolume(parseFloat(e.target.value))}
              className="w-full accent-emerald-400 h-1"
            />
          </div>
        </div>
      </motion.div>
    </div>
  );
};
