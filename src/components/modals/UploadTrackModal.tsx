import React, { useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { X, Upload, Music, Sparkles } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { Track } from '../../types/music';

const COVER_OPTIONS = [
  'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800&auto=format&fit=crop&q=80',
  'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80'
];

export const UploadTrackModal: React.FC = () => {
  const isUploadTrackOpen = useMusicStore(state => state.isUploadTrackOpen);
  const setIsUploadTrackOpen = useMusicStore(state => state.setIsUploadTrackOpen);
  const importLocalTrack = useMusicStore(state => state.importLocalTrack);

  const [title, setTitle] = useState('');
  const [artist, setArtist] = useState('');
  const [genre, setGenre] = useState('Electronic');
  const [accentColor, setAccentColor] = useState('#1ed760');
  const [audioUrl, setAudioUrl] = useState('');
  const [albumArt, setAlbumArt] = useState(COVER_OPTIONS[0]);
  const [fileName, setFileName] = useState('');

  if (!isUploadTrackOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setFileName(file.name);
      // Auto-populate title from filename
      const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
      setTitle(cleanName);
      // Create local object URL for instant zero-server streaming
      const localUrl = URL.createObjectURL(file);
      setAudioUrl(localUrl);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !audioUrl) return;

    const newTrack: Track = {
      id: `imported-${Date.now()}`,
      title: title.trim(),
      artist: artist.trim() || 'Local Artist',
      album: 'Local Library Collection',
      albumArt: albumArt,
      audioUrl: audioUrl,
      duration: 180,
      genre: genre,
      mood: 'Custom Vibe',
      accentColor: accentColor,
      plays: 1,
      addedAt: new Date().toISOString().split('T')[0],
      lyrics: [
        { id: '1', time: 0, text: `♪ ${title} by ${artist || 'Local Artist'} ♪` },
        { id: '2', time: 10, text: 'Custom local track playing in high definition' }
      ]
    };

    importLocalTrack(newTrack);
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={() => setIsUploadTrackOpen(false)}
          className="absolute inset-0 bg-black/75 backdrop-blur-md"
        />

        {/* Modal Window */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          className="relative w-full max-w-lg glass-panel-elevated rounded-3xl p-6 sm:p-8 z-10 border border-white/10 shadow-2xl text-zinc-100"
        >
          {/* Header */}
          <div className="flex items-center justify-between pb-4 border-b border-white/10">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-emerald-400 flex items-center justify-center text-black shadow-lg">
                <Upload className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-extrabold text-lg tracking-tight text-white">Import Custom Audio</h3>
                <p className="text-xs text-zinc-400">Play local files with real-time visualizers</p>
              </div>
            </div>

            <button
              onClick={() => setIsUploadTrackOpen(false)}
              className="p-2 rounded-full hover:bg-white/10 text-zinc-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="py-5 space-y-4">
            {/* File Dropzone */}
            <div className="relative">
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1.5 block">
                Choose Audio File (.mp3, .wav, .ogg, .m4a)
              </label>
              <label className="flex flex-col items-center justify-center border-2 border-dashed border-zinc-700 hover:border-spotify-green/60 rounded-2xl p-5 cursor-pointer bg-white/[0.02] hover:bg-white/[0.05] transition-all">
                <Music className="w-8 h-8 text-spotify-green mb-2" />
                <span className="text-xs font-semibold text-zinc-200">
                  {fileName ? fileName : 'Click to browse audio file from your device'}
                </span>
                <span className="text-[10px] text-zinc-500 mt-1">Processed instantly in your browser</span>
                <input
                  type="file"
                  accept="audio/*"
                  onChange={handleFileUpload}
                  className="hidden"
                />
              </label>
            </div>

            {/* URL Alternative */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1 block">
                Or Direct Audio Stream URL
              </label>
              <input
                type="url"
                value={audioUrl.startsWith('blob:') ? '' : audioUrl}
                onChange={(e) => setAudioUrl(e.target.value)}
                placeholder="https://example.com/stream.mp3"
                className="w-full px-4 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-spotify-green focus:outline-none text-xs text-white placeholder-zinc-500 transition-colors"
              />
            </div>

            {/* Title & Artist */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1 block">
                  Track Title *
                </label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Song Title"
                  className="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-spotify-green focus:outline-none text-xs text-white placeholder-zinc-500 transition-colors"
                />
              </div>

              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1 block">
                  Artist Name
                </label>
                <input
                  type="text"
                  value={artist}
                  onChange={(e) => setArtist(e.target.value)}
                  placeholder="Artist / Producer"
                  className="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/10 focus:border-spotify-green focus:outline-none text-xs text-white placeholder-zinc-500 transition-colors"
                />
              </div>
            </div>

            {/* Cover Selector */}
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1.5 block">
                Artwork Aesthetic
              </label>
              <div className="grid grid-cols-5 gap-2">
                {COVER_OPTIONS.map((c, i) => (
                  <div
                    key={i}
                    onClick={() => setAlbumArt(c)}
                    className={`aspect-square rounded-xl overflow-hidden cursor-pointer border-2 transition-all ${
                      albumArt === c ? 'border-spotify-green scale-105 shadow-md ring-2 ring-spotify-green/40' : 'border-transparent opacity-60 hover:opacity-100'
                    }`}
                  >
                    <img src={c} alt="Cover option" className="w-full h-full object-cover" />
                  </div>
                ))}
              </div>
            </div>

            {/* Genre & Glow Color */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1 block">
                  Genre
                </label>
                <select
                  value={genre}
                  onChange={(e) => setGenre(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-zinc-900 border border-white/10 focus:border-spotify-green focus:outline-none text-xs text-zinc-200"
                >
                  <option value="Synthwave">Synthwave</option>
                  <option value="Lo-Fi Chill">Lo-Fi Chill</option>
                  <option value="Cyberpunk">Cyberpunk</option>
                  <option value="Electronic">Electronic</option>
                  <option value="Ambient">Ambient</option>
                  <option value="Indie">Indie</option>
                </select>
              </div>

              <div>
                <label className="text-xs font-bold uppercase tracking-wider text-zinc-400 mb-1 block">
                  Ambient Glow Hue
                </label>
                <div className="flex items-center gap-2">
                  {['#1ed760', '#ec4899', '#8b5cf6', '#06b6d4', '#f59e0b', '#f43f5e'].map(c => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setAccentColor(c)}
                      className={`w-6 h-6 rounded-full border-2 transition-all ${
                        accentColor === c ? 'border-white scale-110 shadow-md' : 'border-transparent opacity-60'
                      }`}
                      style={{ backgroundColor: c }}
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Submit */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/10">
              <button
                type="button"
                onClick={() => setIsUploadTrackOpen(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!title.trim() || !audioUrl}
                className="px-5 py-2 rounded-xl bg-spotify-green hover:bg-spotify-hover text-black font-bold text-xs shadow-lg shadow-spotify-green/30 transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
              >
                <Sparkles className="w-4 h-4" />
                Play Track Now
              </button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
