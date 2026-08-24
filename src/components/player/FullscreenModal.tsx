import React, { useEffect } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AudioVisualizer } from './AudioVisualizer';
import { formatTime } from '../../utils/formatters';
import { 
  Minimize2, 
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
  Sparkles,
  Disc3
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const FullscreenModal: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTime = useMusicStore(state => state.currentTime);
  const duration = useMusicStore(state => state.duration);
  const isShuffle = useMusicStore(state => state.isShuffle);
  const repeatMode = useMusicStore(state => state.repeatMode);
  const volume = useMusicStore(state => state.volume);
  const isMuted = useMusicStore(state => state.isMuted);
  const isFullscreen = useMusicStore(state => state.isFullscreen);
  const likedTrackIds = useMusicStore(state => state.likedTrackIds);

  const togglePlay = useMusicStore(state => state.togglePlay);
  const nextTrack = useMusicStore(state => state.nextTrack);
  const prevTrack = useMusicStore(state => state.prevTrack);
  const seek = useMusicStore(state => state.seek);
  const setVolume = useMusicStore(state => state.setVolume);
  const toggleMute = useMusicStore(state => state.toggleMute);
  const toggleShuffle = useMusicStore(state => state.toggleShuffle);
  const toggleRepeat = useMusicStore(state => state.toggleRepeat);
  const toggleLikeTrack = useMusicStore(state => state.toggleLikeTrack);
  const setIsFullscreen = useMusicStore(state => state.setIsFullscreen);

  const isLiked = currentTrack ? likedTrackIds.includes(currentTrack.id) : false;
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;
  const lyrics = currentTrack?.lyrics || [];

  // Listen for Escape key to exit fullscreen
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isFullscreen) {
        setIsFullscreen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFullscreen, setIsFullscreen]);

  if (!isFullscreen || !currentTrack) return null;

  // Active lyric
  let activeLyric = lyrics.find((l, i) => {
    const next = lyrics[i + 1];
    return currentTime >= l.time && (!next || currentTime < next.time);
  });

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.98 }}
        transition={{ duration: 0.4 }}
        className="fixed inset-0 z-50 bg-[#07070a] text-white flex flex-col justify-between p-8 sm:p-12 overflow-hidden select-none"
      >
        {/* Dynamic Glow Mesh */}
        <div 
          className="absolute -top-40 -left-40 w-[900px] h-[900px] rounded-full filter blur-[180px] opacity-35 pointer-events-none"
          style={{ backgroundColor: currentTrack.accentColor || '#8b5cf6' }}
        />
        <div 
          className="absolute -bottom-40 -right-40 w-[800px] h-[800px] rounded-full filter blur-[180px] opacity-25 pointer-events-none bg-cyan-600"
        />

        {/* Top Header */}
        <div className="flex items-center justify-between z-10">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
              <Sparkles className="w-4 h-4 animate-pulse" />
            </div>
            <div>
              <h2 className="font-extrabold text-sm tracking-wide text-zinc-300 uppercase">Immersive Sound Experience</h2>
              <p className="text-xs text-zinc-500">{currentTrack.album}</p>
            </div>
          </div>

          <button
            onClick={() => setIsFullscreen(false)}
            className="p-3 rounded-full bg-white/10 hover:bg-white/20 text-zinc-300 hover:text-white transition-all backdrop-blur-xl"
            title="Exit Fullscreen (Esc)"
          >
            <Minimize2 className="w-5 h-5" />
          </button>
        </div>

        {/* Center Stage: Artwork + Synced Lyrics + Visualizer */}
        <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-12 items-center max-w-6xl mx-auto w-full py-8 z-10">
          {/* Left: Oversized Glowing Artwork & Vinyl Animation */}
          <div className="flex flex-col items-center lg:items-start text-center lg:text-left space-y-6">
            <div className="relative group">
              <div 
                className="absolute inset-0 rounded-3xl filter blur-2xl opacity-60 transition-opacity duration-1000"
                style={{ backgroundColor: currentTrack.accentColor || '#8b5cf6' }}
              />
              <div className="relative w-72 h-72 sm:w-96 sm:h-96 rounded-3xl overflow-hidden shadow-2xl border border-white/20">
                <img 
                  src={currentTrack.albumArt} 
                  alt={currentTrack.title} 
                  className={`w-full h-full object-cover ${isPlaying ? 'scale-105' : 'scale-100'} transition-transform duration-700`}
                />
              </div>
            </div>

            <div className="w-full">
              <div className="flex items-center justify-between gap-4">
                <div className="min-w-0">
                  <h1 className="text-2xl sm:text-4xl font-black tracking-tight text-white truncate glow-text">
                    {currentTrack.title}
                  </h1>
                  <p className="text-base sm:text-lg text-zinc-400 font-medium mt-1">
                    {currentTrack.artist}
                  </p>
                </div>

                <button
                  onClick={() => toggleLikeTrack(currentTrack.id)}
                  className={`p-3 rounded-full hover:scale-110 transition-all ${
                    isLiked ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'
                  }`}
                >
                  <Heart className={`w-7 h-7 ${isLiked ? 'fill-spotify-green stroke-spotify-green' : ''}`} />
                </button>
              </div>

              {/* Visualizer Spectrum below Artwork */}
              <div className="mt-4 pt-3 border-t border-white/10">
                <AudioVisualizer height={70} />
              </div>
            </div>
          </div>

          {/* Right: Live Synchronized Lyrics Highlight */}
          <div className="hidden lg:flex flex-col justify-center h-full max-h-[460px] overflow-y-auto px-6 space-y-6 glass-panel rounded-3xl p-8 border border-white/10 shadow-2xl">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-400">
              <Disc3 className="w-4 h-4 animate-spin-slow" />
              Live Synced Lyrics
            </div>

            {lyrics.length > 0 ? (
              <div className="space-y-6">
                {lyrics.map((line) => {
                  const isActive = activeLyric?.id === line.id;
                  return (
                    <p
                      key={line.id}
                      onClick={() => seek(line.time)}
                      className={`cursor-pointer transition-all duration-300 font-extrabold ${
                        isActive
                          ? 'text-2xl text-white glow-text scale-105 opacity-100'
                          : 'text-lg text-zinc-600 hover:text-zinc-300 opacity-60'
                      }`}
                    >
                      {line.text}
                    </p>
                  );
                })}
              </div>
            ) : (
              <div className="text-zinc-500 italic text-center py-12">
                Instrumental resonance • Enjoy the soundscape
              </div>
            )}
          </div>
        </div>

        {/* Bottom Floating Playbar */}
        <div className="max-w-4xl mx-auto w-full z-10 space-y-4 glass-panel p-5 rounded-2xl border border-white/10 shadow-2xl">
          {/* Progress Slider */}
          <div className="flex items-center gap-4 text-xs font-mono text-zinc-400">
            <span>{formatTime(currentTime)}</span>
            <div className="relative flex-1 flex items-center group">
              <div className="absolute inset-x-0 h-1.5 rounded-full bg-zinc-800 pointer-events-none overflow-hidden">
                <div 
                  className="h-full bg-spotify-green transition-all rounded-full" 
                  style={{ width: `${progressPercent}%` }}
                />
              </div>
              <input
                type="range"
                min={0}
                max={duration || 100}
                step={0.1}
                value={currentTime}
                onChange={(e) => seek(parseFloat(e.target.value))}
                className="w-full h-4 opacity-0 cursor-pointer relative z-10"
              />
            </div>
            <span>{formatTime(duration)}</span>
          </div>

          {/* Controls */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <button
                onClick={toggleShuffle}
                className={`p-2 rounded-full ${isShuffle ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'}`}
              >
                <Shuffle className="w-4 h-4" />
              </button>
              <button
                onClick={toggleRepeat}
                className={`p-2 rounded-full ${repeatMode !== 'off' ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'}`}
              >
                {repeatMode === 'one' ? <Repeat1 className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
              </button>
            </div>

            <div className="flex items-center gap-6">
              <button onClick={prevTrack} className="p-2 text-zinc-300 hover:text-white hover:scale-110 transition-transform">
                <SkipBack className="w-6 h-6 fill-current" />
              </button>
              <button
                onClick={togglePlay}
                className="w-12 h-12 rounded-full bg-spotify-green text-black flex items-center justify-center shadow-lg shadow-spotify-green/40 hover:scale-110 active:scale-95 transition-transform"
              >
                {isPlaying ? <Pause className="w-6 h-6 fill-black" /> : <Play className="w-6 h-6 fill-black ml-1" />}
              </button>
              <button onClick={nextTrack} className="p-2 text-zinc-300 hover:text-white hover:scale-110 transition-transform">
                <SkipForward className="w-6 h-6 fill-current" />
              </button>
            </div>

            <div className="flex items-center gap-2 w-32">
              <button onClick={toggleMute} className="text-zinc-400 hover:text-white">
                {isMuted || volume === 0 ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
              </button>
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={isMuted ? 0 : volume}
                onChange={(e) => setVolume(parseFloat(e.target.value))}
                className="w-full accent-spotify-green"
              />
            </div>
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  );
};
