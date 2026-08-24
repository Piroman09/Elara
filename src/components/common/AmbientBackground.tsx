import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { motion, AnimatePresence } from 'framer-motion';

export const AmbientBackground: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTheme = useMusicStore(state => state.currentTheme);

  const theme = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];
  const primaryColor = currentTrack?.accentColor || theme.accent;
  const secondaryColor = theme.secondary;

  return (
    <div className="fixed inset-0 pointer-events-none overflow-hidden z-0 transition-colors duration-1000">
      {/* Primary Dynamic Theme Orb */}
      <AnimatePresence>
        <motion.div
          key={`${currentTheme}-${currentTrack?.id || 'aura'}`}
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ 
            opacity: isPlaying ? 0.35 : 0.22, 
            scale: isPlaying ? [1, 1.08, 0.98, 1] : 1,
            transition: { 
              opacity: { duration: 1.2 },
              scale: { duration: 6, repeat: Infinity, ease: 'easeInOut' }
            }
          }}
          exit={{ opacity: 0, scale: 0.9 }}
          className="absolute -top-32 -left-32 w-[650px] h-[650px] rounded-full filter blur-[140px]"
          style={{ backgroundColor: primaryColor }}
        />
      </AnimatePresence>

      {/* Secondary Dynamic Color Orb */}
      <motion.div
        animate={{
          x: isPlaying ? [0, 50, -40, 0] : 0,
          y: isPlaying ? [0, -40, 30, 0] : 0,
          opacity: isPlaying ? 0.28 : 0.15
        }}
        transition={{ duration: 14, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute top-1/3 -right-24 w-[600px] h-[600px] rounded-full filter blur-[150px]"
        style={{ backgroundColor: secondaryColor }}
      />

      {/* Deep Atmosphere Base Orb */}
      <motion.div
        animate={{
          scale: isPlaying ? [1, 1.2, 0.95, 1] : 1,
          opacity: isPlaying ? 0.22 : 0.12
        }}
        transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
        className="absolute -bottom-48 left-1/4 w-[750px] h-[750px] rounded-full filter blur-[160px] bg-indigo-900/30"
      />

      {/* Futuristic subtle star / matrix grid texture */}
      <div 
        className="absolute inset-0 opacity-[0.03] bg-[radial-gradient(#fff_1px,transparent_1px)] [background-size:28px_28px]"
      />
    </div>
  );
};
