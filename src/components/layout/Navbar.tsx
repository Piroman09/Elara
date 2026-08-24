import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { AURA_THEMES } from '../../data/mockTracks';
import { Search, Palette, ChevronDown, Check, Radio, User as UserIcon, LogOut, ShieldCheck } from 'lucide-react';

export const Navbar: React.FC = () => {
  const activeView = useMusicStore(state => state.activeView);
  const setActiveView = useMusicStore(state => state.setActiveView);
  const searchQuery = useMusicStore(state => state.searchQuery);
  const setSearchQuery = useMusicStore(state => state.setSearchQuery);
  const currentTheme = useMusicStore(state => state.currentTheme);
  const setTheme = useMusicStore(state => state.setTheme);
  const isApiOnline = useMusicStore(state => state.isApiOnline);

  const user = useMusicStore(state => state.user);
  const setIsAuthModalOpen = useMusicStore(state => state.setIsAuthModalOpen);
  const logoutUser = useMusicStore(state => state.logoutUser);

  const [themeDropdownOpen, setThemeDropdownOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);

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
          <span>{isApiOnline ? 'ELARA Cloud' : 'Offline Mode'}</span>
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
                    className={`w-full px-3.5 py-2 text-left text-xs font-medium flex items-center justify-between transition-colors ${
                      currentTheme === theme.id 
                        ? 'bg-white/15 text-white font-bold' 
                        : 'text-zinc-300 hover:bg-white/10'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <div 
                        className="w-2.5 h-2.5 rounded-full" 
                        style={{ backgroundColor: theme.accent }}
                      />
                      <span>{theme.name}</span>
                    </div>
                    {currentTheme === theme.id && (
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                    )}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>

        {/* User Auth Section */}
        {user ? (
          <div className="relative">
            <button
              onClick={() => setUserDropdownOpen(!userDropdownOpen)}
              className="flex items-center gap-2 p-1.5 sm:px-3 sm:py-1.5 rounded-2xl glass-panel hover:bg-white/10 border border-white/10 text-xs font-bold text-white transition-all shadow-md"
            >
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.name} className="w-6 h-6 rounded-full object-cover border border-emerald-400/40" />
              ) : (
                <div className="w-6 h-6 rounded-full bg-emerald-500/20 text-emerald-300 flex items-center justify-center font-bold text-[10px]">
                  {user.name.charAt(0)}
                </div>
              )}
              <span className="hidden sm:inline max-w-[100px] truncate">{user.name}</span>
              <ChevronDown className="w-3 h-3 text-zinc-400 hidden sm:inline" />
            </button>

            {userDropdownOpen && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setUserDropdownOpen(false)} />
                <div className="absolute right-0 top-full mt-2 w-56 py-2 glass-panel-elevated rounded-2xl z-50 shadow-2xl border border-zinc-700/60 font-sans">
                  <div className="px-4 py-2.5 border-b border-white/[0.08]">
                    <p className="text-xs font-extrabold text-white truncate">{user.name}</p>
                    <p className="text-[10px] text-zinc-400 truncate">{user.email}</p>
                    <div className="mt-1 flex items-center gap-1 text-[9px] font-mono text-emerald-400">
                      <ShieldCheck className="w-3 h-3" />
                      <span>Signed in via {user.provider.toUpperCase()}</span>
                    </div>
                  </div>

                  <button
                    onClick={() => {
                      logoutUser();
                      setUserDropdownOpen(false);
                    }}
                    className="w-full px-4 py-2 text-left text-xs text-rose-300 hover:bg-rose-500/10 flex items-center gap-2.5 transition-colors mt-1"
                  >
                    <LogOut className="w-3.5 h-3.5 text-rose-400" />
                    <span>Sign Out</span>
                  </button>
                </div>
              </>
            )}
          </div>
        ) : (
          <button
            onClick={() => setIsAuthModalOpen(true)}
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-2xl bg-gradient-to-r from-emerald-400 to-cyan-400 hover:from-emerald-300 hover:to-cyan-300 text-black font-extrabold text-xs shadow-lg shadow-emerald-400/20 hover:scale-105 active:scale-95 transition-all"
          >
            <UserIcon className="w-3.5 h-3.5 fill-black" />
            <span>Sign In</span>
          </button>
        )}
      </div>
    </header>
  );
};
