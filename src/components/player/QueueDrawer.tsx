import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { formatTime } from '../../utils/formatters';
import { Track } from '../../types/music';
import { Play, Trash2, Sparkles } from 'lucide-react';

export const QueueDrawer: React.FC = () => {
  const queue = useMusicStore(state => state.queue);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const galaxyTracks = useMusicStore(state => state.galaxyTracks);
  const isQueueOpen = useMusicStore(state => state.isQueueOpen);
  const setIsQueueOpen = useMusicStore(state => state.setIsQueueOpen);
  const removeFromQueue = useMusicStore(state => state.removeFromQueue);
  const playTrack = useMusicStore(state => state.playTrack);
  const clearQueue = useMusicStore(state => state.clearQueue);

  // Advanced similarity scoring algorithm
  const getSimilarTracks = (current: Track, candidates: Track[], currentQueue: Track[]): Track[] => {
    if (!current) return [];

    const queueIds = new Set(currentQueue.map(t => t.id));
    const excludedIds = new Set([current.id, ...queueIds]);

    // Base tags from current track
    const baseTags = new Set((current.tags || [current.genre.toLowerCase()]).map(t => t.toLowerCase()));
    const baseArtistId = current.artistId;
    const baseEnergy = current.energy || 50;
    const baseGenre = current.genre.toLowerCase();

    interface ScoredTrack {
      track: Track;
      score: number;
      details: string[];
    }

    const scored: ScoredTrack[] = candidates
      .filter(t => !excludedIds.has(t.id))
      .map(track => {
        let score = 0;
        const details: string[] = [];

        // 1. Artist Match (weight: 50)
        if (track.artistId === baseArtistId) {
          score += 50;
          details.push('Same artist');
        }

        // 2. Tag Overlap using Jaccard Similarity (weight: 30)
        const trackTags = new Set((track.tags || [track.genre.toLowerCase()]).map(t => t.toLowerCase()));
        const intersection = [...baseTags].filter(t => trackTags.has(t)).length;
        const union = new Set([...baseTags, ...trackTags]).size;
        if (union > 0) {
          const jaccard = intersection / union;
          score += jaccard * 30;
          if (intersection > 0) {
            details.push(`${intersection} shared ${intersection === 1 ? 'tag' : 'tags'}`);
          }
        }

        // 3. Genre Match (weight: 20)
        if (track.genre.toLowerCase() === baseGenre) {
          score += 20;
          details.push('Same genre');
        } else if (track.genre.toLowerCase().includes(baseGenre) || baseGenre.includes(track.genre.toLowerCase())) {
          score += 10;
          details.push('Related genre');
        }

        // 4. Energy Proximity (weight: 15) - prefer tracks with similar energy
        const trackEnergy = track.energy || 50;
        const energyDiff = Math.abs(trackEnergy - baseEnergy);
        const energyScore = Math.max(0, 15 - (energyDiff * 0.15));
        score += energyScore;
        if (energyDiff <= 10) {
          details.push('Similar energy');
        }

        // 5. Popularity Bonus (weight: 5) - slightly prefer popular tracks
        const popularityScore = Math.min(5, (track.plays || 0) / 100000);
        score += popularityScore;

        return { track, score, details };
      });

    // Sort by score descending and take top 5
    return scored
      .sort((a, b) => b.score - a.score)
      .slice(0, 5)
      .map(x => x.track);
  };

  if (!isQueueOpen) return null;

  const similarTracks = currentTrack ? getSimilarTracks(currentTrack, galaxyTracks, queue) : [];

  return (
    <aside className="fixed inset-y-0 right-0 w-80 bg-[#0c0c10]/95 backdrop-blur-2xl border-l border-white/[0.06] flex flex-col h-full z-30 select-none shadow-2xl animate-in slide-in-from-right duration-300">
      <div className="p-5 flex items-center justify-between border-b border-white/[0.06]">
        <h2 className="text-sm font-bold text-white">Up Next</h2>
        <button onClick={() => setIsQueueOpen(false)} className="p-1 rounded-full text-zinc-400 hover:text-white hover:bg-white/10 transition-all">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 12L6 6V18Z" />
          </svg>
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {/* Current Queue */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-zinc-400">
              Queue ({queue.length})
            </h3>
            {queue.length > 0 && (
              <button
                onClick={() => clearQueue()}
                className="text-[11px] text-zinc-400 hover:text-rose-400 transition-colors"
              >
                Clear
              </button>
            )}
          </div>
          {queue.length === 0 ? (
            <p className="text-center text-zinc-500 text-xs py-4">Queue is empty</p>
          ) : (
            <div className="space-y-2">
              {queue.map((track, idx) => (
                <div key={`${track.id}-${idx}`} className="flex items-center justify-between p-2 rounded-lg hover:bg-white/5 transition-all cursor-pointer group">
                  <div className="flex items-center gap-3 flex-1 min-w-0" onClick={() => playTrack(track, queue.slice(idx + 1))}>
                    <img src={track.albumArt} alt={track.title} className="w-10 h-10 rounded object-cover flex-shrink-0" />
                    <div className="flex flex-col min-w-0">
                      <span className="text-sm font-medium text-white truncate">{track.title}</span>
                      <span className="text-xs text-zinc-400 truncate">{track.artist}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 ml-2">
                    <span className="text-xs text-zinc-500 hidden sm:block">{formatTime(track.duration)}</span>
                    <button
                      onClick={(e) => { e.stopPropagation(); removeFromQueue(idx); }}
                      className="p-1 text-zinc-500 hover:text-rose-400 opacity-0 group-hover:opacity-100 transition-all"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Similar Songs Section */}
        {currentTrack && (
          <div className="pt-4 border-t border-white/10">
            <div className="flex items-center gap-2 mb-3">
              <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-zinc-400">
                Similar Songs
              </h3>
            </div>
            {similarTracks.length === 0 ? (
              <p className="text-xs text-zinc-500 py-2">No similar tracks found</p>
            ) : (
              <div className="space-y-2">
                {similarTracks.map((track) => (
                  <div
                    key={track.id}
                    className="flex items-center justify-between p-2 rounded-lg hover:bg-white/5 transition-all cursor-pointer group"
                    onClick={() => playTrack(track, [...queue, track])}
                  >
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                      <img src={track.albumArt} alt={track.title} className="w-10 h-10 rounded object-cover flex-shrink-0" />
                      <div className="flex flex-col min-w-0">
                        <span className="text-sm font-medium text-white truncate">{track.title}</span>
                        <span className="text-xs text-zinc-400 truncate">{track.artist}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 ml-2">
                      <span className="text-xs text-zinc-500 hidden sm:block">{formatTime(track.duration)}</span>
                      <button className="p-1 text-zinc-500 hover:text-emerald-400 opacity-0 group-hover:opacity-100 transition-all">
                        <Play className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </aside>
  );
};
