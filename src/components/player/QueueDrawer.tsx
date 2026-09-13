import React, { useMemo, useRef, useState } from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { formatTime, formatTotalDuration } from '../../utils/formatters';
import { Track } from '../../types/music';
import {
  Play,
  Trash2,
  Sparkles,
  ListMusic,
  X,
  GripVertical,
  ArrowUpToLine,
  Shuffle,
  Plus,
  Clock
} from 'lucide-react';

/**
 * Advanced similarity scoring — suggests tracks that fit the current one.
 * Excludes whatever is already in the queue so suggestions stay useful.
 */
function getSimilarTracks(current: Track, candidates: Track[], queuedIds: Set<string>): Track[] {
  const excludedIds = new Set([current.id, ...queuedIds]);

  const baseTags = new Set((current.tags || [current.genre.toLowerCase()]).map(t => t.toLowerCase()));
  const baseArtistId = current.artistId;
  const baseEnergy = current.energy || 50;
  const baseGenre = current.genre.toLowerCase();

  return candidates
    .filter(t => !excludedIds.has(t.id))
    .map(track => {
      let score = 0;

      // 1. Same artist (weight: 50)
      if (track.artistId === baseArtistId) score += 50;

      // 2. Tag overlap via Jaccard similarity (weight: 30)
      const trackTags = new Set((track.tags || [track.genre.toLowerCase()]).map(t => t.toLowerCase()));
      const intersection = [...baseTags].filter(t => trackTags.has(t)).length;
      const union = new Set([...baseTags, ...trackTags]).size;
      if (union > 0) score += (intersection / union) * 30;

      // 3. Genre match (weight: 20)
      if (track.genre.toLowerCase() === baseGenre) score += 20;
      else if (track.genre.toLowerCase().includes(baseGenre) || baseGenre.includes(track.genre.toLowerCase())) score += 10;

      // 4. Energy proximity (weight: 15)
      score += Math.max(0, 15 - Math.abs((track.energy || 50) - baseEnergy) * 0.15);

      // 5. Popularity nudge (weight: 5)
      score += Math.min(5, (track.plays || 0) / 100000);

      return { track, score };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
    .map(x => x.track);
}

export const QueueDrawer: React.FC = () => {
  const queue = useMusicStore(state => state.queue);
  const queueIndex = useMusicStore(state => state.queueIndex);
  const queueSource = useMusicStore(state => state.queueSource);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const galaxyTracks = useMusicStore(state => state.galaxyTracks);
  const isQueueOpen = useMusicStore(state => state.isQueueOpen);
  const isShuffle = useMusicStore(state => state.isShuffle);

  const setIsQueueOpen = useMusicStore(state => state.setIsQueueOpen);
  const jumpToQueueIndex = useMusicStore(state => state.jumpToQueueIndex);
  const moveInQueue = useMusicStore(state => state.moveInQueue);
  const removeFromQueue = useMusicStore(state => state.removeFromQueue);
  const clearQueue = useMusicStore(state => state.clearQueue);
  const toggleShuffle = useMusicStore(state => state.toggleShuffle);
  const playNext = useMusicStore(state => state.playNext);
  const addToQueue = useMusicStore(state => state.addToQueue);

  // Drag-and-drop reordering state
  const dragArmedRef = useRef(false);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [overIndex, setOverIndex] = useState<number | null>(null);

  const nowPlaying = queueIndex >= 0 ? queue[queueIndex] : currentTrack;
  const upcomingStart = queueIndex >= 0 ? queueIndex + 1 : 0;
  const upcoming = useMemo(() => queue.slice(upcomingStart), [queue, upcomingStart]);
  const upcomingDuration = useMemo(
    () => upcoming.reduce((acc, t) => acc + (t.duration || 0), 0),
    [upcoming]
  );

  const queuedIds = useMemo(() => new Set(queue.map(t => t.id)), [queue]);
  const similarTracks = useMemo(
    () => (nowPlaying ? getSimilarTracks(nowPlaying, galaxyTracks, queuedIds) : []),
    [nowPlaying, galaxyTracks, queuedIds]
  );

  if (!isQueueOpen) return null;

  const handleDragStart = (e: React.DragEvent, index: number) => {
    // Only drags that started on the grip handle are allowed, so normal
    // clicking and text selection keep working.
    if (!dragArmedRef.current) {
      e.preventDefault();
      return;
    }
    setDragIndex(index);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(index));
  };

  const handleDrop = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    if (dragIndex !== null && dragIndex !== index) {
      moveInQueue(dragIndex, index);
    }
    dragArmedRef.current = false;
    setDragIndex(null);
    setOverIndex(null);
  };

  const handleDragEnd = () => {
    dragArmedRef.current = false;
    setDragIndex(null);
    setOverIndex(null);
  };

  const resetDrag = () => {
    dragArmedRef.current = false;
    setDragIndex(null);
    setOverIndex(null);
  };

  return (
    <aside className="fixed inset-y-0 right-0 w-80 bg-[#0c0c10]/95 backdrop-blur-2xl border-l border-white/[0.06] flex flex-col h-full z-30 select-none shadow-2xl animate-in slide-in-from-right duration-300">
      {/* Header */}
      <div className="p-5 flex items-center justify-between border-b border-white/[0.06]">
        <div className="flex items-center gap-2 min-w-0">
          <ListMusic className="w-4 h-4 text-spotify-green flex-shrink-0" />
          <div className="min-w-0">
            <h2 className="text-sm font-bold text-white leading-tight">Queue</h2>
            {queueSource && (
              <p className="text-[10px] text-zinc-500 truncate">Playing from {queueSource}</p>
            )}
          </div>
        </div>
        <button
          onClick={() => setIsQueueOpen(false)}
          title="Close queue"
          className="p-1 rounded-full text-zinc-400 hover:text-white hover:bg-white/10 transition-all flex-shrink-0"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-5">
        {/* Now Playing */}
        {nowPlaying && (
          <div>
            <h3 className="text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-2">
              Now Playing
            </h3>
            <div className="flex items-center gap-3 p-2.5 rounded-xl bg-white/[0.07] border border-white/10">
              <div className="relative w-11 h-11 rounded-lg overflow-hidden flex-shrink-0">
                <img
                  src={nowPlaying.albumArt}
                  alt={nowPlaying.title}
                  className="w-full h-full object-cover"
                />
                {isPlaying && (
                  <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
                    <span className="flex items-end gap-[2px] h-3.5">
                      <span className="w-[2px] bg-spotify-green rounded-full animate-soundwave" />
                      <span className="w-[2px] bg-spotify-green rounded-full animate-soundwave" style={{ animationDelay: '150ms' }} />
                      <span className="w-[2px] bg-spotify-green rounded-full animate-soundwave" style={{ animationDelay: '300ms' }} />
                    </span>
                  </div>
                )}
              </div>
              <div className="flex flex-col min-w-0 flex-1">
                <span className="text-sm font-semibold text-white truncate">{nowPlaying.title}</span>
                <span className="text-xs text-zinc-400 truncate">{nowPlaying.artist}</span>
              </div>
              <span className="text-[11px] font-mono text-zinc-500 flex-shrink-0">
                {formatTime(nowPlaying.duration)}
              </span>
            </div>
          </div>
        )}

        {/* Up Next */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-[11px] font-bold uppercase tracking-wider text-zinc-500">
              Next Up
            </h3>
            <div className="flex items-center gap-1">
              <button
                onClick={toggleShuffle}
                title={isShuffle ? 'Shuffle on' : 'Shuffle off'}
                className={`p-1 rounded-md transition-colors ${
                  isShuffle ? 'text-spotify-green bg-white/10' : 'text-zinc-500 hover:text-white hover:bg-white/10'
                }`}
              >
                <Shuffle className="w-3.5 h-3.5" />
              </button>
              {upcoming.length > 0 && (
                <button
                  onClick={clearQueue}
                  className="text-[11px] px-1.5 py-1 rounded-md text-zinc-400 hover:text-rose-400 hover:bg-white/5 transition-colors"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {upcoming.length > 0 && (
            <div className="flex items-center gap-1.5 mb-2 text-[10px] text-zinc-500 font-mono">
              <Clock className="w-3 h-3" />
              <span>
                {upcoming.length} {upcoming.length === 1 ? 'song' : 'songs'} · {formatTotalDuration(upcomingDuration)}
              </span>
              {isShuffle && <span className="text-spotify-green">· shuffled</span>}
            </div>
          )}

          {upcoming.length === 0 ? (
            <div className="py-6 text-center border border-dashed border-white/10 rounded-xl">
              <p className="text-xs text-zinc-500">Nothing up next</p>
              <p className="text-[10px] text-zinc-600 mt-1">
                Add songs from a playlist or search
              </p>
            </div>
          ) : (
            <div className="space-y-1">
              {upcoming.map((track, i) => {
                const absIndex = upcomingStart + i;
                const isDragging = dragIndex === absIndex;
                const isDropTarget = overIndex === absIndex && dragIndex !== null && dragIndex !== absIndex;

                return (
                  <div
                    key={`${track.id}-${absIndex}`}
                    draggable
                    onDragStart={e => handleDragStart(e, absIndex)}
                    onDragOver={e => {
                      e.preventDefault();
                      e.dataTransfer.dropEffect = 'move';
                      if (overIndex !== absIndex) setOverIndex(absIndex);
                    }}
                    onDrop={e => handleDrop(e, absIndex)}
                    onDragEnd={handleDragEnd}
                    onMouseUp={resetDrag}
                    className={`group flex items-center gap-2 p-1.5 rounded-lg transition-all ${
                      isDragging ? 'opacity-40' : 'hover:bg-white/5'
                    } ${isDropTarget ? 'ring-1 ring-spotify-green/70 bg-white/[0.06]' : ''}`}
                  >
                    {/* Drag handle */}
                    <button
                      onMouseDown={() => {
                        dragArmedRef.current = true;
                      }}
                      onMouseUp={resetDrag}
                      title="Drag to reorder"
                      className="p-0.5 text-zinc-600 hover:text-zinc-300 cursor-grab active:cursor-grabbing flex-shrink-0 touch-none"
                    >
                      <GripVertical className="w-3.5 h-3.5" />
                    </button>

                    {/* Click to jump to this track (non-destructive) */}
                    <button
                      onClick={() => jumpToQueueIndex(absIndex)}
                      className="flex items-center gap-2.5 flex-1 min-w-0 text-left"
                    >
                      <img
                        src={track.albumArt}
                        alt={track.title}
                        draggable={false}
                        className="w-9 h-9 rounded object-cover flex-shrink-0"
                      />
                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-medium text-white truncate">{track.title}</span>
                        <span className="text-[11px] text-zinc-500 truncate">{track.artist}</span>
                      </div>
                    </button>

                    <div className="flex items-center gap-0.5 flex-shrink-0">
                      <span className="text-[10px] font-mono text-zinc-500 group-hover:hidden">
                        {formatTime(track.duration)}
                      </span>
                      <button
                        onClick={() => moveInQueue(absIndex, upcomingStart)}
                        title="Move to top"
                        className="hidden group-hover:block p-1 text-zinc-500 hover:text-spotify-green transition-colors"
                      >
                        <ArrowUpToLine className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => removeFromQueue(absIndex)}
                        title="Remove from queue"
                        className="hidden group-hover:block p-1 text-zinc-500 hover:text-rose-400 transition-colors"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Similar Songs */}
        {nowPlaying && (
          <div className="pt-4 border-t border-white/10">
            <div className="flex items-center gap-2 mb-2">
              <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
              <h3 className="text-[11px] font-bold uppercase tracking-wider text-zinc-500">
                Similar Songs
              </h3>
            </div>

            {similarTracks.length === 0 ? (
              <p className="text-xs text-zinc-500 py-2">No similar tracks found</p>
            ) : (
              <div className="space-y-1">
                {similarTracks.map(track => (
                  <div
                    key={track.id}
                    className="group flex items-center gap-2.5 p-1.5 rounded-lg hover:bg-white/5 transition-all"
                  >
                    <img
                      src={track.albumArt}
                      alt={track.title}
                      className="w-9 h-9 rounded object-cover flex-shrink-0"
                    />
                    <div className="flex flex-col min-w-0 flex-1">
                      <span className="text-xs font-medium text-white truncate">{track.title}</span>
                      <span className="text-[11px] text-zinc-500 truncate">{track.artist}</span>
                    </div>
                    <div className="flex items-center gap-0.5 flex-shrink-0">
                      <button
                        onClick={() => playNext(track)}
                        title="Play next"
                        className="p-1 text-zinc-500 hover:text-spotify-green transition-colors"
                      >
                        <Play className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => addToQueue(track)}
                        title="Add to queue"
                        className="p-1 text-zinc-500 hover:text-white transition-colors"
                      >
                        <Plus className="w-3.5 h-3.5" />
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
