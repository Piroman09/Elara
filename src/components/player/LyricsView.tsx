import React, { useEffect, useRef } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { X, Mic2, Sparkles } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const LyricsView: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const currentTime = useMusicStore(state => state.currentTime);
  const seek = useMusicStore(state => state.seek);
  const isLyricsOpen = useMusicStore(state => state.isLyricsOpen);
  const setIsLyricsOpen = useMusicStore(state => state.setIsLyricsOpen);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const activeLineRef = useRef<HTMLDivElement | null>(null);

  const lyrics = currentTrack?.lyrics || [];

  // Determine current active lyric index
  let activeIndex = -1;
  for (let i = 0; i < lyrics.length; i++) {
    if (currentTime >= lyrics[i].time) {
      activeIndex = i;
    } else {
      break;
    }
  }

  // Smooth auto-scroll to keep active line centered
  useEffect(() => {
    if (activeLineRef.current && containerRef.current) {
      const container = containerRef.current;
      const element = activeLineRef.current;
      const elementOffsetTop = element.offsetTop;
      const containerHalfHeight = container.clientHeight / 2;

      container.scrollTo({
        top: elementOffsetTop - containerHalfHeight + element.clientHeight / 2,
        behavior: 'smooth'
      });
    }
  }, [activeIndex]);

  if (!isLyricsOpen) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: 40 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 40 }}
        transition={{ duration: 0.3 }}
        className="fixed inset-0 z-40 bg-[#09090b]/90 backdrop-blur-3xl flex flex-col pt-16 pb-28 px-6 sm:px-12 md:px-24 select-none overflow-hidden"
      >
        {/* Background ambient lighting */}
        <div 
          className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] rounded-full filter blur-[160px] opacity-25 pointer-events-none"
          style={{ backgroundColor: currentTrack?.accentColor || '#8b5cf6' }}
        />

        {/* Top Control Bar */}
        <div className="flex items-center justify-between py-4 border-b border-white/10 z-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl overflow-hidden shadow-lg border border-white/10">
              <img src={currentTrack?.albumArt} alt={currentTrack?.title} className="w-full h-full object-cover" />
            </div>
            <div>
              <h3 className="font-bold text-base text-white">{currentTrack?.title}</h3>
              <p className="text-xs text-zinc-400">{currentTrack?.artist}</p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <div className="hidden sm:flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-xs font-semibold text-emerald-300">
              <Sparkles className="w-3.5 h-3.5" />
              Synced Karaoke
            </div>
            <button
              onClick={() => setIsLyricsOpen(false)}
              className="p-2 rounded-full bg-white/5 hover:bg-white/10 text-zinc-400 hover:text-white transition-all"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Scrolling Lyrics Container */}
        <div 
          ref={containerRef}
          className="flex-1 overflow-y-auto py-24 space-y-8 text-center scroll-smooth z-10 no-scrollbar"
        >
          {lyrics.length === 0 ? (
            <div className="py-24 flex flex-col items-center justify-center text-zinc-500">
              <Mic2 className="w-12 h-12 mb-4 opacity-40 animate-pulse" />
              <p className="text-lg font-bold text-zinc-300">Lyrics unavailable for this track</p>
              <p className="text-sm text-zinc-500 mt-1">Enjoy the rhythm and instrumental vibe</p>
            </div>
          ) : (
            lyrics.map((line, idx) => {
              const isActive = idx === activeIndex;
              const isPast = idx < activeIndex;

              return (
                <motion.div
                  key={line.id}
                  ref={isActive ? activeLineRef : null}
                  onClick={() => seek(line.time)}
                  whileHover={{ scale: 1.03 }}
                  className={`transition-all duration-300 cursor-pointer font-extrabold tracking-tight px-4 py-2 rounded-2xl ${
                    isActive
                      ? 'text-2xl sm:text-4xl md:text-5xl text-white glow-text scale-105 opacity-100 bg-white/5'
                      : isPast
                      ? 'text-lg sm:text-2xl md:text-3xl text-zinc-500 opacity-60 hover:opacity-90'
                      : 'text-lg sm:text-2xl md:text-3xl text-zinc-600 opacity-40 hover:opacity-80'
                  }`}
                >
                  <p>{line.text}</p>
                </motion.div>
              );
            })
          )}
        </div>
      </motion.div>
    </AnimatePresence>
  );
};
