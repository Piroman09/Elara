import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { GENRES, MOCK_TRACKS } from '../../data/mockTracks';
import { TrackRow } from '../common/TrackRow';
import { Search as SearchIcon, X, Sparkles, Music, Loader2, Radio, ListMusic, Play } from 'lucide-react';
import { motion } from 'framer-motion';

export const SearchView: React.FC = () => {
  const searchQuery = useMusicStore(state => state.searchQuery);
  const setSearchQuery = useMusicStore(state => state.setSearchQuery);
  const searchResults = useMusicStore(state => state.searchResults);
  const isSearchingLive = useMusicStore(state => state.isSearchingLive);
  const isApiOnline = useMusicStore(state => state.isApiOnline);
  const explorePlaylists = useMusicStore(state => state.explorePlaylists);
  const openOnlinePlaylist = useMusicStore(state => state.openOnlinePlaylist);

  const cleanQuery = searchQuery.trim().toLowerCase();

  const localTracks = cleanQuery
    ? MOCK_TRACKS.filter(t => 
        t.title.toLowerCase().includes(cleanQuery) ||
        t.artist.toLowerCase().includes(cleanQuery) ||
        t.genre.toLowerCase().includes(cleanQuery)
      )
    : [];

  const displayTracks = searchResults.length > 0 ? searchResults : localTracks;
  const isSearching = cleanQuery.length > 0;

  return (
    <div className="p-4 sm:p-6 md:p-8 space-y-8 max-w-7xl mx-auto pb-32 select-none">
      {/* Search Header Bar */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight flex items-center gap-2.5">
            <SearchIcon className="w-7 h-7 text-emerald-400" />
            Search & Explore
          </h1>

          <div className="flex items-center gap-2">
            <span className={`w-2 h-2 rounded-full ${isApiOnline ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400'}`} />
            <span className="text-[11px] font-mono text-zinc-400">
              {isApiOnline ? 'YouTube Music Online' : 'Local Archive'}
            </span>
          </div>
        </div>

        <div className="relative max-w-3xl">
          <SearchIcon className="w-5 h-5 absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" />
          <input
            type="text"
            autoFocus
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search any song, artist, album, or playlist worldwide..."
            className="w-full pl-12 pr-12 py-3.5 rounded-2xl bg-white/[0.08] focus:bg-white/[0.12] border border-white/10 focus:border-emerald-400/60 text-sm sm:text-base text-white placeholder-zinc-500 focus:outline-none transition-all shadow-xl"
          />

          <div className="absolute right-4 top-1/2 -translate-y-1/2 flex items-center gap-2">
            {isSearchingLive && (
              <Loader2 className="w-4 h-4 text-emerald-400 animate-spin" />
            )}
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="p-1 text-zinc-400 hover:text-white transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Results View */}
      {isSearching ? (
        <div className="space-y-8">
          {displayTracks.length === 0 && !isSearchingLive ? (
            <div className="py-20 text-center glass-panel rounded-3xl border border-zinc-800">
              <Sparkles className="w-10 h-10 text-zinc-600 mx-auto mb-3" />
              <h3 className="text-lg font-bold text-white">No results found for "{searchQuery}"</h3>
              <p className="text-xs text-zinc-500 mt-1">Try another artist or song query</p>
            </div>
          ) : (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-extrabold text-white flex items-center gap-2">
                  <Music className="w-4 h-4 text-emerald-400" />
                  Live Songs ({displayTracks.length})
                </h3>
                {isSearchingLive && (
                  <span className="text-xs font-mono text-emerald-400 flex items-center gap-1.5">
                    <Radio className="w-3.5 h-3.5 animate-pulse" />
                    Querying YouTube Music...
                  </span>
                )}
              </div>

              <div className="space-y-1 glass-panel rounded-2xl p-2 border border-white/10 shadow-2xl">
                {displayTracks.map((track, index) => (
                  <TrackRow 
                    key={`${track.id}-${index}`} 
                    track={track} 
                    index={index} 
                    contextPlaylistTracks={displayTracks} 
                  />
                ))}
              </div>
            </section>
          )}
        </div>
      ) : (
        <div className="space-y-8">
          {/* YouTube Music Explore Playlists */}
          {explorePlaylists.length > 0 && (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-extrabold text-white tracking-tight flex items-center gap-2">
                    <ListMusic className="w-5 h-5 text-emerald-400" />
                    Trending YouTube Music Playlists
                  </h2>
                  <p className="text-xs text-zinc-400">Popular community and curated collections</p>
                </div>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                {explorePlaylists.map((playlist, idx) => (
                  <motion.div
                    key={`${playlist.id}-${idx}`}
                    whileHover={{ y: -4 }}
                    transition={{ type: 'spring', damping: 20, stiffness: 300 }}
                    onClick={() => openOnlinePlaylist(playlist)}
                    className="group relative p-3 rounded-2xl glass-panel hover:bg-white/[0.08] border border-white/10 transition-all duration-200 cursor-pointer overflow-hidden shadow-lg"
                  >
                    <div className="relative aspect-video rounded-xl overflow-hidden mb-2.5 shadow-md bg-zinc-900">
                      <img 
                        src={playlist.coverArt} 
                        alt={playlist.name} 
                        loading="lazy" 
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" 
                      />
                      <div className="absolute bottom-2 right-2 w-8 h-8 rounded-full bg-emerald-400 flex items-center justify-center shadow-xl opacity-0 group-hover:opacity-100 transition-opacity">
                        <Play className="w-3.5 h-3.5 fill-black text-black ml-0.5" />
                      </div>
                    </div>

                    <h3 className="font-extrabold text-xs text-white group-hover:text-emerald-300 transition-colors truncate">
                      {playlist.name}
                    </h3>
                    <p className="text-[10px] text-zinc-400 truncate mt-0.5">
                      {playlist.description}
                    </p>
                  </motion.div>
                ))}
              </div>
            </section>
          )}

          {/* Browse All Genres Section */}
          <section className="space-y-4">
            <div>
              <h2 className="text-xl font-extrabold text-white tracking-tight">
                Explore by Mood & Genre
              </h2>
              <p className="text-xs text-zinc-400 mt-0.5">Filter by sonic mood and atmosphere</p>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-3 gap-4">
              {GENRES.map((genre) => (
                <motion.div
                  key={genre.id}
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  transition={{ type: 'spring', damping: 20, stiffness: 300 }}
                  onClick={() => {
                    setSearchQuery(genre.name.split(' ')[0]);
                  }}
                  className={`relative aspect-[16/10] rounded-3xl p-5 overflow-hidden cursor-pointer shadow-xl bg-gradient-to-br ${genre.gradient} border border-white/10 group`}
                >
                  <h3 className="font-black text-base sm:text-lg text-white max-w-[70%] z-10 relative drop-shadow-md">
                    {genre.name}
                  </h3>

                  <div className="absolute -bottom-2 -right-4 w-24 h-24 sm:w-28 sm:h-28 rounded-2xl overflow-hidden shadow-2xl rotate-[18deg] group-hover:rotate-[12deg] group-hover:scale-105 transition-all duration-300">
                    <img src={genre.image} alt={genre.name} loading="lazy" className="w-full h-full object-cover" />
                  </div>
                </motion.div>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
};
