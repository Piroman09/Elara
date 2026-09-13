import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { AudioOutputDevice } from '../../types/music';
import { 
  Palette, 
  Headphones, 
  Radio, 
  Sparkles, 
  Volume2, 
  VolumeX, 
  Sliders, 
  X, 
  Check, 
  Disc, 
  Speaker
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const PixelQuickSettings: React.FC = () => {
  const isQuickSettingsOpen = useMusicStore(state => state.isQuickSettingsOpen);
  const setIsQuickSettingsOpen = useMusicStore(state => state.setIsQuickSettingsOpen);
  const currentTheme = useMusicStore(state => state.currentTheme);
  const setTheme = useMusicStore(state => state.setTheme);
  const activeOutputDevice = useMusicStore(state => state.activeOutputDevice);
  const setActiveOutputDevice = useMusicStore(state => state.setActiveOutputDevice);
  const volume = useMusicStore(state => state.volume);
  const setVolume = useMusicStore(state => state.setVolume);
  const isMuted = useMusicStore(state => state.isMuted);
  const toggleMute = useMusicStore(state => state.toggleMute);
  const bassBoostEnabled = useMusicStore(state => state.bassBoostEnabled);
  const toggleBassBoost = useMusicStore(state => state.toggleBassBoost);
  const visualizerMode = useMusicStore(state => state.visualizerMode);
  const setVisualizerMode = useMusicStore(state => state.setVisualizerMode);
  const setIsEqualizerOpen = useMusicStore(state => state.setIsEqualizerOpen);

  const [deviceMenuOpen, setDeviceMenuOpen] = useState(false);
  const [themePickerOpen, setThemePickerOpen] = useState(false);

  const activeTheme = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];

  const devices: { id: AudioOutputDevice; label: string; icon: any }[] = [
    { id: 'pixel-buds', label: 'Pixel Buds Pro', icon: Headphones },
    { id: 'phone-speaker', label: 'Device Speaker', icon: Speaker },
    { id: 'chromecast', label: 'Chromecast Audio', icon: Radio },
    { id: 'bluetooth', label: 'Bluetooth Device', icon: Headphones },
  ];

  if (!isQuickSettingsOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex flex-col items-center select-none">
        {/* Backdrop overlay */}
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={() => setIsQuickSettingsOpen(false)}
          className="absolute inset-0 bg-black/60 backdrop-blur-md"
        />

        {/* Quick Settings Panel Sheet */}
        <motion.div
          initial={{ y: -80, opacity: 0, scale: 0.96 }}
          animate={{ y: 0, opacity: 1, scale: 1 }}
          exit={{ y: -80, opacity: 0, scale: 0.96 }}
          transition={{ type: 'spring', damping: 28, stiffness: 300 }}
          className="relative mt-2 sm:mt-6 w-[94%] max-w-2xl bg-pixel-container/95 border border-white/[0.12] rounded-3.5xl p-5 sm:p-6 shadow-[0_25px_60px_rgba(0,0,0,0.8)] backdrop-blur-2xl z-10"
        >
          {/* Header */}
          <div className="flex items-center justify-between pb-4 border-b border-white/[0.08]">
            <div className="flex items-center gap-2">
              <div 
                className="w-3 h-3 rounded-full shadow-sm"
                style={{ backgroundColor: activeTheme.accent }}
              />
              <h2 className="text-base font-bold text-white font-sans">
                Pixel Quick Tiles
              </h2>
              <span className="text-xs px-2 py-0.5 rounded-full bg-white/10 text-zinc-300 font-mono">
                Material 3
              </span>
            </div>

            <button
              onClick={() => setIsQuickSettingsOpen(false)}
              className="p-1.5 rounded-full bg-white/10 hover:bg-white/20 text-zinc-300 hover:text-white transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Quick Settings Grid Tiles (2 x 3 or 3 x 2) */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 my-5">
            {/* Tile 1: Monet Palette */}
            <button
              onClick={() => setThemePickerOpen(!themePickerOpen)}
              className={`p-3.5 rounded-3xl flex items-center gap-3 transition-all text-left border ${
                themePickerOpen 
                  ? 'bg-white/20 border-white/30 text-white' 
                  : 'bg-white/[0.06] hover:bg-white/[0.1] border-white/5 text-zinc-200'
              }`}
            >
              <div 
                className="w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 shadow-sm"
                style={{ backgroundColor: `${activeTheme.accent}25`, color: activeTheme.accent }}
              >
                <Palette className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Monet Theme</div>
                <div className="text-[11px] text-zinc-400 truncate flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full inline-block" style={{ backgroundColor: activeTheme.accent }} />
                  <span>{activeTheme.name.split(' ')[0]}</span>
                </div>
              </div>
            </button>

            {/* Tile 2: Audio Output Switcher */}
            <button
              onClick={() => setDeviceMenuOpen(!deviceMenuOpen)}
              className={`p-3.5 rounded-3xl flex items-center gap-3 transition-all text-left border ${
                deviceMenuOpen 
                  ? 'bg-white/20 border-white/30 text-white' 
                  : 'bg-white/[0.06] hover:bg-white/[0.1] border-white/5 text-zinc-200'
              }`}
            >
              <div 
                className="w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 bg-emerald-500/20 text-emerald-400 shadow-sm"
                style={{ backgroundColor: `${activeTheme.accent}25`, color: activeTheme.accent }}
              >
                <Headphones className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Audio Device</div>
                <div className="text-[11px] text-zinc-400 truncate">
                  {devices.find(d => d.id === activeOutputDevice)?.label || 'Pixel Buds'}
                </div>
              </div>
            </button>

            {/* Tile 3: Bass Boost */}
            <button
              onClick={toggleBassBoost}
              className={`p-3.5 rounded-3xl flex items-center gap-3 transition-all text-left border ${
                bassBoostEnabled 
                  ? 'bg-emerald-400/20 border-emerald-400/40 text-white' 
                  : 'bg-white/[0.06] hover:bg-white/[0.1] border-white/5 text-zinc-200'
              }`}
              style={bassBoostEnabled ? { backgroundColor: `${activeTheme.accent}25`, borderColor: `${activeTheme.accent}50` } : {}}
            >
              <div 
                className={`w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 transition-colors ${
                  bassBoostEnabled ? 'bg-white/20 text-white font-bold' : 'bg-white/10 text-zinc-400'
                }`}
              >
                <Sliders className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Bass Boost</div>
                <div className="text-[11px] text-zinc-400">
                  {bassBoostEnabled ? 'Dynamic On' : 'Standard Off'}
                </div>
              </div>
            </button>

            {/* Tile 4: Visualizer Mode */}
            <button
              onClick={() => {
                const modes = ['halo', 'bars', 'wave', 'particles', 'vinyl', 'off'];
                const nextIdx = (modes.indexOf(visualizerMode) + 1) % modes.length;
                setVisualizerMode(modes[nextIdx] as any);
              }}
              className="p-3.5 rounded-3xl flex items-center gap-3 bg-white/[0.06] hover:bg-white/[0.1] border border-white/5 transition-all text-left text-zinc-200"
            >
              <div className="w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 bg-white/10 text-cyan-400">
                <Sparkles className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Vibe Aura</div>
                <div className="text-[11px] text-zinc-400 capitalize">
                  {visualizerMode}
                </div>
              </div>
            </button>

            {/* Tile 5: Equalizer Suite */}
            <button
              onClick={() => {
                setIsQuickSettingsOpen(false);
                setIsEqualizerOpen(true);
              }}
              className="p-3.5 rounded-3xl flex items-center gap-3 bg-white/[0.06] hover:bg-white/[0.1] border border-white/5 transition-all text-left text-zinc-200"
            >
              <div className="w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 bg-white/10 text-violet-400">
                <Disc className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Equalizer</div>
                <div className="text-[11px] text-zinc-400">
                  Studio DSP
                </div>
              </div>
            </button>

            {/* Tile 6: Offline Mode */}
            <div className="p-3.5 rounded-3xl flex items-center gap-3 bg-white/[0.06] border border-white/5 text-zinc-200">
              <div className="w-9 h-9 rounded-2xl flex items-center justify-center flex-shrink-0 bg-white/10 text-emerald-400">
                <Radio className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold truncate text-white">Cloud Stream</div>
                <div className="text-[11px] text-emerald-400 font-mono">
                  Online
                </div>
              </div>
            </div>
          </div>

          {/* Expanded Device Switcher Popdown */}
          {deviceMenuOpen && (
            <motion.div 
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="mb-4 p-3 rounded-2.5xl bg-white/[0.04] border border-white/10 space-y-1"
            >
              <div className="text-[11px] uppercase tracking-wider text-zinc-400 font-bold px-2 py-1">
                Select Audio Output
              </div>
              {devices.map(device => {
                const Icon = device.icon;
                const isCurrent = activeOutputDevice === device.id;
                return (
                  <button
                    key={device.id}
                    onClick={() => {
                      setActiveOutputDevice(device.id);
                      setDeviceMenuOpen(false);
                    }}
                    className={`w-full px-3 py-2 rounded-xl flex items-center justify-between text-xs font-medium transition-colors ${
                      isCurrent ? 'bg-white/15 text-white font-bold' : 'text-zinc-300 hover:bg-white/5'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <Icon className="w-4 h-4 text-zinc-400" />
                      <span>{device.label}</span>
                    </div>
                    {isCurrent && <Check className="w-4 h-4 text-emerald-400" />}
                  </button>
                );
              })}
            </motion.div>
          )}

          {/* Expanded Monet Palette Picker */}
          {themePickerOpen && (
            <motion.div 
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="mb-4 p-3 rounded-2.5xl bg-white/[0.04] border border-white/10 space-y-1"
            >
              <div className="text-[11px] uppercase tracking-wider text-zinc-400 font-bold px-2 py-1">
                Monet Color Extraction
              </div>
              <div className="grid grid-cols-2 gap-1.5">
                {AURA_THEMES.map(theme => (
                  <button
                    key={theme.id}
                    onClick={() => {
                      setTheme(theme.id);
                      setThemePickerOpen(false);
                    }}
                    className={`px-3 py-2 rounded-xl flex items-center justify-between text-xs transition-colors ${
                      currentTheme === theme.id ? 'bg-white/15 text-white font-bold' : 'text-zinc-300 hover:bg-white/5'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <div className="w-3 h-3 rounded-full flex-shrink-0" style={{ backgroundColor: theme.accent }} />
                      <span className="truncate">{theme.name}</span>
                    </div>
                    {currentTheme === theme.id && <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />}
                  </button>
                ))}
              </div>
            </motion.div>
          )}

          {/* Pixel Volume Pill Slider */}
          <div className="mt-4 p-3.5 rounded-3xl bg-white/[0.04] border border-white/[0.06] flex items-center gap-3">
            <button
              onClick={toggleMute}
              className="p-1.5 rounded-full hover:bg-white/10 text-zinc-300 hover:text-white transition-colors"
            >
              {isMuted || volume === 0 ? <VolumeX className="w-5 h-5 text-red-400" /> : <Volume2 className="w-5 h-5 text-zinc-300" />}
            </button>
            <div className="relative flex-1 flex items-center">
              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={isMuted ? 0 : volume}
                onChange={(e) => setVolume(parseFloat(e.target.value))}
                className="w-full h-2 rounded-full cursor-pointer accent-emerald-400"
                style={{ accentColor: activeTheme.accent }}
              />
            </div>
            <span className="text-xs font-mono text-zinc-400 w-9 text-right font-medium">
              {Math.round((isMuted ? 0 : volume) * 100)}%
            </span>
          </div>

          {/* Bottom Pull Handle */}
          <div className="flex justify-center pt-4">
            <button 
              onClick={() => setIsQuickSettingsOpen(false)}
              className="w-12 h-1.5 rounded-full bg-white/25 hover:bg-white/40 transition-colors" 
              title="Close Tiles"
            />
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
