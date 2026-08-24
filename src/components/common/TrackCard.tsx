import React from 'react';
import { Play, Pause } from 'lucide-react';
import { motion } from 'framer-motion';

interface TrackCardProps {
  title: string;
  subtitle?: string;
  image: string;
  isPlaying?: boolean;
  isCurrent?: boolean;
  isCircular?: boolean;
  onPlayClick?: () => void;
  onClick?: () => void;
  badge?: string;
}

export const TrackCard: React.FC<TrackCardProps> = ({
  title,
  subtitle,
  image,
  isPlaying = false,
  isCurrent = false,
  isCircular = false,
  onPlayClick,
  onClick,
  badge
}) => {
  return (
    <motion.div
      whileHover={{ y: -4 }}
      transition={{ duration: 0.2 }}
      onClick={onClick}
      className={`group relative p-3.5 rounded-2xl glass-panel hover:bg-white/[0.08] transition-all duration-300 cursor-pointer flex flex-col ${
        isCurrent ? 'border-spotify-green/40 shadow-[0_0_20px_rgba(30,215,96,0.15)]' : 'border-white/5'
      }`}
    >
      {/* Image Container */}
      <div className={`relative w-full aspect-square overflow-hidden mb-3.5 shadow-lg bg-zinc-900 ${
        isCircular ? 'rounded-full' : 'rounded-xl'
      }`}>
        <img 
          src={image} 
          alt={title}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
        />

        {badge && (
          <div className="absolute top-2 left-2 px-2 py-0.5 rounded-full bg-black/60 backdrop-blur-md text-[10px] font-bold uppercase tracking-wider text-emerald-400 border border-emerald-500/30">
            {badge}
          </div>
        )}

        {/* Hover Play Button */}
        {onPlayClick && (
          <div 
            onClick={(e) => {
              e.stopPropagation();
              onPlayClick();
            }}
            className={`absolute bottom-3 right-3 w-11 h-11 rounded-full bg-spotify-green flex items-center justify-center shadow-2xl transition-all duration-300 transform group-hover:translate-y-0 group-hover:opacity-100 hover:scale-110 active:scale-95 ${
              isCurrent && isPlaying 
                ? 'opacity-100 translate-y-0 shadow-[0_0_25px_rgba(30,215,96,0.8)]' 
                : 'opacity-0 translate-y-2 group-hover:opacity-100 group-hover:translate-y-0 shadow-[0_0_20px_rgba(0,0,0,0.6)]'
            }`}
          >
            {isCurrent && isPlaying ? (
              <Pause className="w-5 h-5 fill-black text-black" />
            ) : (
              <Play className="w-5 h-5 fill-black text-black ml-0.5" />
            )}
          </div>
        )}
      </div>

      {/* Card Info */}
      <div className="flex flex-col min-w-0">
        <h4 className={`text-sm font-bold truncate transition-colors ${
          isCurrent ? 'text-spotify-green' : 'text-zinc-100 group-hover:text-white'
        }`}>
          {title}
        </h4>
        {subtitle && (
          <p className="text-xs text-zinc-400 line-clamp-2 mt-1 leading-relaxed">
            {subtitle}
          </p>
        )}
      </div>
    </motion.div>
  );
};
