import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { X, Plus, Music, Image as ImageIcon } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const PRESET_COVERS = [
  'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80'
];

export const CreatePlaylistModal: React.FC = () => {
  const isCreatePlaylistOpen = useMusicStore(state => state.isCreatePlaylistOpen);
  const setIsCreatePlaylistOpen = useMusicStore(state => state.setIsCreatePlaylistOpen);
  const createPlaylist = useMusicStore(state => state.createPlaylist);
  const setActiveView = useMusicStore(state => state.setActiveView);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selectedCover, setSelectedCover] = useState(PRESET_COVERS[0]);

  if (!isCreatePlaylistOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const newPl = createPlaylist(name, description, selectedCover);
    setName('');
    setDescription('');
    setActiveView('playlist', { playlistId: newPl.id });
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={() => setIsCreatePlaylistOpen(false)}
          className="absolute inset-0 bg-black/75 backdrop-blur-md"
        />

        {/* Modal Content */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          className="relative w-full max-w-lg glass-panel-elevated rounded-3xl p-6 sm:p-8 z-10 border border-white/10 shadow-2xl text-zinc-100"
        >
          {/* Header */}
          <div className="flex items-center justify-between pb-4 border-b border-white/10">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-purple-500 to-pink-500 flex items-center justify-center text-white shadow-lg">
                <Music className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-extrabold text-lg tracking-tight text-white">Create Aesthetic Playlist</h3>
                <p className="text-xs text-zinc-400">Assemble your favorite mood and sonic vibes</p>
              </div>
            </div>

            <button
              onClick={() => setIsCreatePlaylistOpen(false)}
              className="p-2 rounded-full hover:bg-white/10 text-zinc-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="py-5 space-y-4">
            {/* Name Input */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1.5 block">
                Playlist Name *
              </label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Midnight Cyberpunk Reverie"
                className="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 focus:border-spotify-green focus:outline-none text-sm text-white placeholder-zinc-500 transition-colors"
              />
            </div>

            {/* Description */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1.5 block">
                Description (Optional)
              </label>
              <textarea
                rows={2}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe the vibes, moods, or tempo..."
                className="w-full px-4 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-spotify-green focus:outline-none text-sm text-white placeholder-zinc-500 transition-colors resize-none"
              />
            </div>

            {/* Cover Art Presets */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-2 block flex items-center gap-1.5">
                <ImageIcon className="w-3.5 h-3.5" />
                Select Cover Aesthetic
              </label>
              <div className="grid grid-cols-6 gap-2">
                {PRESET_COVERS.map((cover, i) => (
                  <div
                    key={i}
                    onClick={() => setSelectedCover(cover)}
                    className={`relative aspect-square rounded-xl overflow-hidden cursor-pointer border-2 transition-all ${
                      selectedCover === cover 
                        ? 'border-spotify-green scale-105 shadow-md shadow-spotify-green/30 ring-2 ring-spotify-green/50' 
                        : 'border-transparent opacity-60 hover:opacity-100 hover:scale-105'
                    }`}
                  >
                    <img src={cover} alt={`Preset ${i}`} className="w-full h-full object-cover" />
                  </div>
                ))}
              </div>
            </div>

            {/* Submit Actions */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/10">
              <button
                type="button"
                onClick={() => setIsCreatePlaylistOpen(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!name.trim()}
                className="px-5 py-2 rounded-xl bg-spotify-green hover:bg-spotify-hover text-black font-bold text-xs shadow-lg shadow-spotify-green/30 transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
              >
                <Plus className="w-4 h-4" />
                Create Playlist
              </button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
