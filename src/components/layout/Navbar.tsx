import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { Search, PlusCircle, Palette, ChevronDown, Check, Radio } from 'lucide-react';

export const Navbar: React.FC = () => {
  const activeView = useMusicStore(state => state.activeView);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const searchQuery = useMusicStore(state => state.searchQuery);
  const setSearchQuery = useMusicStore(state => state.setSearchQuery);
  const currentTheme = useMusicStore(state => state.currentTheme);
  const setTheme = useMusicStore(state => state.setTheme);
  const setIsUploadTrackOpen = useMusicStore(state => state.setIsUploadTrackOpen);
  const isApiOnline = useMusicStore(state => state.isApiOnline);

  const [themeDropdownOpen, setThemeDropdownOpen] = useState(false);

  const activeTheme = AURA_THEMES.find(t => t.id === currentTheme) || AURA_THEMES[0];

  return (
    <header className="h-16 px-6 flex items-center justify-between gap-4 sticky top-0 z-30 bg-[#09090b]/80 backdrop-blur-xl border-b border-white/[0.06]">
      {/* Search Input */}
      <div className="relative flex-1 max-w-md group">
        <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-400 group-focus-within:text-emerald-400 transition-colors" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => {
            setSearchQuery(e.target.value);
            if (activeView !== 'search') {
              setActiveView('search');
            }
          }}
          placeholder="Search any song, artist, album, playlist..."
          className="w-full pl-10 pr-4 py-2 rounded-2xl bg-white/[0.05] hover:bg-white/[0.08] focus:bg-white/[0.12] border border-white/10 focus:border-emerald-400/50 text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none transition-all"
        />
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-2.5">
        {/* Live Status Indicator */}
        <div className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/5 border border-white/10 text-[11px] font-mono text-zinc-300">
          <Radio className={`w-3 h-3 ${isApiOnline ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
          <span>{isApiOnline ? 'YTMusic Live' : 'Offline Mode'}</span>
        </div>

        {/* Theme Switcher Dropdown */}
        <div className="relative">
          <button
            onClick={() => setThemeDropdownOpen(!themeDropdownOpen)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-2xl glass-panel hover:bg-white/10 border border-white/10 text-xs font-bold text-zinc-200 transition-all shadow-sm"
          >
            <div 
              className="w-2.5 h-2.5 rounded-full shadow-[0_0_8px_currentColor]" 
              style={{ backgroundColor: activeTheme.accent, color: activeTheme.accent }}
            />
            <span className="hidden sm:inline">{activeTheme.name.split(' ')[0]}</span>
            <ChevronDown className="w-3 h-3 text-zinc-400" />
          </button>

          {themeDropdownOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setThemeDropdownOpen(false)} />
              <div className="absolute right-0 top-full mt-2 w-48 py-2 glass-panel-elevated rounded-2xl z-50 shadow-2xl border border-zinc-700/60 font-sans">
                <div className="px-3 py-1 text-[10px] uppercase font-bold tracking-wider text-zinc-500 flex items-center gap-1.5">
                  <Palette className="w-3 h-3" />
                  Select Aura Theme
                </div>
                {AURA_THEMES.map((theme) => (
                  <button
                    key={theme.id}
                    onClick={() => {
                      setTheme(theme.id);
                      setThemeDropdownOpen(false);
                    }}
                    className={`w-full px-3 py-2 text-left text-xs font-semibold hover:bg-white/10 flex items-center justify-between transition-colors ${
                      currentTheme === theme.id ? 'text-white bg-white/5' : 'text-zinc-400'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: theme.accent }} />
                      <span>{theme.name}</span>
                    </div>
                    {currentTheme === theme.id && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        {/* Upload Audio File */}
        <button
          onClick={() => setIsUploadTrackOpen(true)}
          className="p-2 rounded-2xl glass-panel hover:bg-white/10 border border-white/10 text-zinc-300 hover:text-white transition-all shadow-sm"
          title="Import Custom Audio"
        >
          <PlusCircle className="w-4 h-4 text-emerald-400" />
        </button>
      </div>
    </header>
  );
};
