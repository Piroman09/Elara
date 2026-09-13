import React, { useState, useEffect } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { 
  Wifi, 
  Battery, 
  Sliders, 
  Headphones
} from 'lucide-react';

export const PixelStatusBar: React.FC = () => {
  const [timeStr, setTimeStr] = useState('');
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTheme = useMusicStore(state => state.currentTheme);
  const activeOutputDevice = useMusicStore(state => state.activeOutputDevice);
  const toggleQuickSettings = useMusicStore(state => state.toggleQuickSettings);
  const isQuickSettingsOpen = useMusicStore(state => state.isQuickSettingsOpen);

  const activeTheme = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      const hours = now.getHours().toString().padStart(2, '0');
      const minutes = now.getMinutes().toString().padStart(2, '0');
      setTimeStr(`${hours}:${minutes}`);
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  const getDeviceLabel = () => {
    switch (activeOutputDevice) {
      case 'pixel-buds': return 'Pixel Buds Pro';
      case 'chromecast': return 'Chromecast';
      case 'bluetooth': return 'Bluetooth Audio';
      default: return 'Phone Speaker';
    }
  };

  return (
    <div className="h-9 px-4 sm:px-6 w-full bg-pixel-bg/90 backdrop-blur-md border-b border-white/[0.04] flex items-center justify-between text-[11px] font-sans font-medium text-zinc-300 select-none z-40 relative">
      {/* Left: Pixel Clock & Now Playing notification badge */}
      <div className="flex items-center gap-3">
        <span className="font-bold tracking-tight text-white font-mono text-xs">
          {timeStr || '12:00'}
        </span>

        {/* Dynamic Android Notification Chip */}
        {currentTrack && (
          <div className="hidden sm:flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-white/[0.06] border border-white/[0.06] text-[10px] text-zinc-300 transition-all">
            <div 
              className="w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: activeTheme.accent }}
            />
            <span className="truncate max-w-[120px]">{currentTrack.title}</span>
            {isPlaying && (
              <span className="flex gap-0.5 items-end h-2.5">
                <span className="w-0.5 bg-emerald-400 rounded-full animate-pulse h-1.5" />
                <span className="w-0.5 bg-emerald-400 rounded-full animate-pulse h-2.5" />
                <span className="w-0.5 bg-emerald-400 rounded-full animate-pulse h-1" />
              </span>
            )}
          </div>
        )}
      </div>

      {/* Center: PixelOS Branding Pill */}
      <div className="hidden md:flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-white/[0.04] border border-white/[0.06] text-[10px] text-zinc-400">
        <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ backgroundColor: activeTheme.accent }} />
        <span className="font-semibold text-zinc-200">PixelOS</span>
        <span>•</span>
        <span>Material You</span>
      </div>

      {/* Right: Connectivity, Output Device, Battery Pill & Quick Settings Trigger */}
      <div className="flex items-center gap-2 sm:gap-3 text-zinc-400">
        {/* Device output badge */}
        <div className="hidden lg:flex items-center gap-1 text-[10px] text-zinc-300 bg-white/[0.05] px-2 py-0.5 rounded-full border border-white/[0.05]">
          <Headphones className="w-3 h-3 text-zinc-400" />
          <span>{getDeviceLabel()}</span>
        </div>

        {/* 5G / VoLTE Icon */}
        <div className="flex items-center gap-0.5 font-mono text-[9px] font-bold text-zinc-300">
          <span>5G</span>
        </div>

        {/* Wi-Fi Icon */}
        <Wifi className="w-3.5 h-3.5 text-zinc-300" />

        {/* Pixel Battery Pill with Percentage */}
        <div className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-white/[0.06] border border-white/[0.08] text-[10px] text-zinc-200 font-mono">
          <Battery className="w-3.5 h-3.5 text-emerald-400 fill-emerald-400/30" />
          <span className="font-bold">98%</span>
        </div>

        {/* Quick Settings Pull-down Pill Button */}
        <button
          onClick={toggleQuickSettings}
          className={`flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-bold transition-all border ${
            isQuickSettingsOpen 
              ? 'bg-emerald-400 text-black border-emerald-400 shadow-sm' 
              : 'bg-white/[0.06] hover:bg-white/[0.12] text-zinc-200 border-white/10'
          }`}
          style={isQuickSettingsOpen ? { backgroundColor: activeTheme.accent, borderColor: activeTheme.accent } : {}}
          title="Pixel Quick Settings"
        >
          <Sliders className="w-3 h-3" />
          <span className="hidden sm:inline">Tiles</span>
        </button>
      </div>
    </div>
  );
};
