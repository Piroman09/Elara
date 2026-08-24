import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { EQ_PRESETS } from '../../data/mockTracks';
import { X, Sliders, Zap, Moon, Check } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const EqualizerModal: React.FC = () => {
  const isEqualizerOpen = useMusicStore(state => state.isEqualizerOpen);
  const setIsEqualizerOpen = useMusicStore(state => state.setIsEqualizerOpen);
  const selectedEQPresetId = useMusicStore(state => state.selectedEQPresetId);
  const customEQGains = useMusicStore(state => state.customEQGains);
  const bassBoostEnabled = useMusicStore(state => state.bassBoostEnabled);
  const sleepTimerTotalMinutes = useMusicStore(state => state.sleepTimerTotalMinutes);
  const sleepTimerRemaining = useMusicStore(state => state.sleepTimerRemaining);

  const setEQPreset = useMusicStore(state => state.setEQPreset);
  const setCustomEQGain = useMusicStore(state => state.setCustomEQGain);
  const toggleBassBoost = useMusicStore(state => state.toggleBassBoost);
  const setSleepTimer = useMusicStore(state => state.setSleepTimer);

  if (!isEqualizerOpen) return null;

  const bandLabels = [
    { freq: '60 Hz', name: 'Sub-Bass' },
    { freq: '230 Hz', name: 'Bass' },
    { freq: '910 Hz', name: 'Mid' },
    { freq: '3.6 kHz', name: 'Presence' },
    { freq: '14 kHz', name: 'Air / High' }
  ];

  const sleepOptions = [
    { label: 'Off', minutes: null },
    { label: '15 min', minutes: 15 },
    { label: '30 min', minutes: 30 },
    { label: '45 min', minutes: 45 },
    { label: '60 min', minutes: 60 }
  ];

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={() => setIsEqualizerOpen(false)}
          className="absolute inset-0 bg-black/75 backdrop-blur-md"
        />

        {/* Modal Window */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          className="relative w-full max-w-xl glass-panel-elevated rounded-3xl p-6 sm:p-8 z-10 border border-white/10 shadow-2xl text-zinc-100 overflow-hidden"
        >
          {/* Ambient Glow in Modal */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-emerald-500/15 rounded-full filter blur-3xl pointer-events-none" />
          <div className="absolute bottom-0 left-0 w-64 h-64 bg-indigo-500/15 rounded-full filter blur-3xl pointer-events-none" />

          {/* Header */}
          <div className="flex items-center justify-between pb-4 border-b border-white/10 relative z-10">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-black shadow-lg">
                <Sliders className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-extrabold text-lg tracking-tight text-white">Audio Equalizer & FX</h3>
                <p className="text-xs text-zinc-400">Fine-tune acoustic balance & studio depth</p>
              </div>
            </div>

            <button
              onClick={() => setIsEqualizerOpen(false)}
              className="p-2 rounded-full hover:bg-white/10 text-zinc-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="py-6 space-y-6 relative z-10">
            {/* Presets Chips */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-2.5 block">
                Sound Profiles & Presets
              </label>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {EQ_PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    onClick={() => setEQPreset(preset.id)}
                    className={`px-3 py-2 rounded-xl text-xs font-semibold flex items-center justify-between border transition-all ${
                      selectedEQPresetId === preset.id
                        ? 'bg-spotify-green text-black border-spotify-green font-bold shadow-md shadow-spotify-green/20'
                        : 'bg-white/5 hover:bg-white/10 text-zinc-300 border-white/5'
                    }`}
                  >
                    <span className="truncate">{preset.name}</span>
                    {selectedEQPresetId === preset.id && <Check className="w-3.5 h-3.5 ml-1" />}
                  </button>
                ))}
              </div>
            </div>

            {/* 5-Band Sliders */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <label className="text-xs font-bold uppercase tracking-wider text-zinc-400">
                  Frequency Response (dB)
                </label>
                <span className="text-[11px] font-mono text-emerald-400">
                  {selectedEQPresetId === 'custom' ? 'Custom EQ Active' : 'Preset Tuned'}
                </span>
              </div>

              <div className="grid grid-cols-5 gap-3 p-4 rounded-2xl bg-black/40 border border-white/5">
                {bandLabels.map((band, idx) => {
                  const gainValue = customEQGains[idx] || 0;
                  return (
                    <div key={band.freq} className="flex flex-col items-center gap-3">
                      <span className="text-[10px] font-mono text-zinc-400">
                        {gainValue > 0 ? `+${gainValue}` : gainValue} dB
                      </span>

                      {/* Vertical Slider Wrapper */}
                      <div className="h-32 flex items-center justify-center relative py-2">
                        <input
                          type="range"
                          min={-12}
                          max={12}
                          step={1}
                          value={gainValue}
                          onChange={(e) => setCustomEQGain(idx, parseFloat(e.target.value))}
                          className="w-24 -rotate-90 origin-center accent-emerald-400"
                        />
                      </div>

                      <div className="text-center">
                        <div className="text-xs font-bold text-zinc-200">{band.freq}</div>
                        <div className="text-[10px] text-zinc-500 truncate">{band.name}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Enhancements Row: Mega Bass & Sleep Timer */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              {/* Mega Bass Toggle */}
              <div 
                onClick={toggleBassBoost}
                className={`p-4 rounded-2xl border cursor-pointer transition-all flex items-center justify-between ${
                  bassBoostEnabled 
                    ? 'bg-emerald-500/10 border-emerald-500/40 shadow-lg shadow-emerald-500/10' 
                    : 'bg-white/5 border-white/5 hover:bg-white/10'
                }`}
              >
                <div className="flex items-center gap-3">
                  <div className={`p-2.5 rounded-xl ${bassBoostEnabled ? 'bg-emerald-400 text-black' : 'bg-white/10 text-zinc-400'}`}>
                    <Zap className="w-4 h-4" />
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-white">Mega Bass Boost</h4>
                    <p className="text-[11px] text-zinc-400">Dynamic low-end punch</p>
                  </div>
                </div>

                <div className={`w-11 h-6 rounded-full p-1 transition-colors ${bassBoostEnabled ? 'bg-spotify-green' : 'bg-zinc-700'}`}>
                  <div className={`w-4 h-4 rounded-full bg-white transition-transform ${bassBoostEnabled ? 'translate-x-5' : 'translate-x-0'}`} />
                </div>
              </div>

              {/* Sleep Timer Section */}
              <div className="p-4 rounded-2xl bg-white/5 border border-white/5 flex flex-col justify-between">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2 text-xs font-bold text-white">
                    <Moon className="w-4 h-4 text-indigo-400" />
                    Sleep Timer
                  </div>
                  {sleepTimerRemaining !== null && (
                    <span className="text-[10px] font-mono text-indigo-300 animate-pulse">
                      {Math.floor(sleepTimerRemaining / 60)}m {sleepTimerRemaining % 60}s left
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar">
                  {sleepOptions.map((opt) => (
                    <button
                      key={opt.label}
                      onClick={() => setSleepTimer(opt.minutes)}
                      className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all ${
                        sleepTimerTotalMinutes === opt.minutes
                          ? 'bg-indigo-500 text-white font-bold'
                          : 'bg-white/5 hover:bg-white/10 text-zinc-400 hover:text-white'
                      }`}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
