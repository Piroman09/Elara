import React from 'react';
import { useMusicStore } from '../../store/useMusicStore';
import { formatTime } from '../../utils/formatters';
import { 
  Play, 
  Pause, 
  SkipBack, 
  SkipForward, 
  Shuffle, 
  Repeat, 
  Repeat1, 
  Heart, 
  Volume2, 
  VolumeX, 
  Volume1, 
  Mic2, 
  ListMusic, 
  Sliders, 
  Maximize2,
  Moon
} from 'lucide-react';

export const PlayerBar: React.FC = () => {
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const currentTime = useMusicStore(state => state.currentTime);
  const duration = useMusicStore(state => state.duration);
  const volume = useMusicStore(state => state.volume);
  const isMuted = useMusicStore(state => state.isMuted);
  const isShuffle = useMusicStore(state => state.isShuffle);
  const repeatMode = useMusicStore(state => state.repeatMode);
  const likedTrackIds = useMusicStore(state => state.likedTrackIds);
  const queue = useMusicStore(state => state.queue);
  const queueIndex = useMusicStore(state => state.queueIndex);
  const isLyricsOpen = useMusicStore(state => state.isLyricsOpen);
  const isQueueOpen = useMusicStore(state => state.isQueueOpen);
  const sleepTimerRemaining = useMusicStore(state => state.sleepTimerRemaining);

  const togglePlay = useMusicStore(state => state.togglePlay);
  const nextTrack = useMusicStore(state => state.nextTrack);
  const prevTrack = useMusicStore(state => state.prevTrack);
  const seek = useMusicStore(state => state.seek);
  const setVolume = useMusicStore(state => state.setVolume);
  const toggleMute = useMusicStore(state => state.toggleMute);
  const toggleShuffle = useMusicStore(state => state.toggleShuffle);
  const toggleRepeat = useMusicStore(state => state.toggleRepeat);
  const toggleLikeTrack = useMusicStore(state => state.toggleLikeTrack);
  const setIsLyricsOpen = useMusicStore(state => state.setIsLyricsOpen);
  const setIsQueueOpen = useMusicStore(state => state.setIsQueueOpen);
  const setIsEqualizerOpen = useMusicStore(state => state.setIsEqualizerOpen);
  const setIsFullscreen = useMusicStore(state => state.setIsFullscreen);
  const setActiveView = useMusicStore(state => state.setActiveView);

  const isLiked = currentTrack ? likedTrackIds.includes(currentTrack.id) : false;
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  const handleSeekChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const time = parseFloat(e.target.value);
    seek(time);
  };

  const getVolumeIcon = () => {
    if (isMuted || volume === 0) return VolumeX;
    if (volume < 0.5) return Volume1;
    return Volume2;
  };
  const VolumeIconComponent = getVolumeIcon();

  return (
    <footer className="h-24 bg-[#0c0c10]/95 backdrop-blur-2xl border-t border-white/[0.08] px-6 flex items-center justify-between gap-6 z-30 select-none shadow-[0_-10px_30px_rgba(0,0,0,0.5)]">
      {/* Left Column: Track Info */}
      <div className="flex items-center gap-4 min-w-[240px] max-w-[30%]">
        {currentTrack ? (
          <>
            <div 
              onClick={() => setIsFullscreen(true)}
              className="relative w-14 h-14 rounded-xl overflow-hidden shadow-xl flex-shrink-0 cursor-pointer group border border-white/10"
            >
              <img 
                src={currentTrack.albumArt} 
                alt={currentTrack.title} 
                className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
              />
              <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                <Maximize2 className="w-5 h-5 text-white" />
              </div>
            </div>

            <div className="flex flex-col min-w-0">
              <span 
                onClick={() => setIsFullscreen(true)}
                className="text-sm font-bold text-white hover:text-spotify-green hover:underline cursor-pointer truncate transition-colors"
              >
                {currentTrack.title}
              </span>
              <span 
                onClick={() => currentTrack.artistId && setActiveView('artist', { artistId: currentTrack.artistId })}
                className="text-xs text-zinc-400 hover:text-zinc-200 hover:underline cursor-pointer truncate transition-colors"
              >
                {currentTrack.artist}
              </span>
            </div>

            <button
              onClick={() => toggleLikeTrack(currentTrack.id)}
              className={`p-2 rounded-full hover:scale-110 transition-all ${
                isLiked ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'
              }`}
            >
              <Heart className={`w-5 h-5 ${isLiked ? 'fill-spotify-green stroke-spotify-green' : ''}`} />
            </button>
          </>
        ) : (
          <div className="text-xs text-zinc-500 italic">No track selected</div>
        )}
      </div>

      {/* Center Column: Playback Controls & Waveform Seekbar */}
      <div className="flex flex-col items-center gap-1.5 flex-1 max-w-2xl">
        {/* Buttons */}
        <div className="flex items-center gap-5">
          {/* Shuffle */}
          <button
            onClick={toggleShuffle}
            title={isShuffle ? 'Disable Shuffle' : 'Enable Shuffle'}
            className={`p-2 rounded-full relative transition-all ${
              isShuffle ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Shuffle className="w-4 h-4" />
            {isShuffle && <span className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-spotify-green" />}
          </button>

          {/* Previous Track */}
          <button
            onClick={prevTrack}
            title="Previous"
            className="p-2 text-zinc-300 hover:text-white hover:scale-110 active:scale-95 transition-all"
          >
            <SkipBack className="w-5 h-5 fill-current" />
          </button>

          {/* Play / Pause Primary Button */}
          <button
            onClick={togglePlay}
            title={isPlaying ? 'Pause' : 'Play'}
            className="w-10 h-10 rounded-full bg-white hover:bg-zinc-100 flex items-center justify-center shadow-lg shadow-white/10 hover:scale-105 active:scale-95 transition-all"
          >
            {isPlaying ? (
              <Pause className="w-5 h-5 fill-black text-black" />
            ) : (
              <Play className="w-5 h-5 fill-black text-black ml-0.5" />
            )}
          </button>

          {/* Next Track */}
          <button
            onClick={nextTrack}
            title="Next"
            className="p-2 text-zinc-300 hover:text-white hover:scale-110 active:scale-95 transition-all"
          >
            <SkipForward className="w-5 h-5 fill-current" />
          </button>

          {/* Repeat */}
          <button
            onClick={toggleRepeat}
            title={`Repeat: ${repeatMode}`}
            className={`p-2 rounded-full relative transition-all ${
              repeatMode !== 'off' ? 'text-spotify-green' : 'text-zinc-400 hover:text-white'
            }`}
          >
            {repeatMode === 'one' ? <Repeat1 className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
            {repeatMode !== 'off' && <span className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-spotify-green" />}
          </button>
        </div>

        {/* Progress Bar & Timers */}
        <div className="w-full flex items-center gap-3 text-xs font-mono text-zinc-400">
          <span className="w-10 text-right">{formatTime(currentTime)}</span>
          
          <div className="relative flex-1 flex items-center group">
            {/* Custom Track Background */}
            <div className="absolute inset-x-0 h-1 rounded-full bg-zinc-800 pointer-events-none overflow-hidden">
              <div 
                className="h-full bg-white group-hover:bg-spotify-green transition-colors rounded-full" 
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <input
              type="range"
              min={0}
              max={duration || 100}
              step={0.1}
              value={currentTime}
              onChange={handleSeekChange}
              className="w-full h-3 opacity-0 cursor-pointer relative z-10"
            />
          </div>

          <span className="w-10 text-left">{formatTime(duration)}</span>
        </div>
      </div>

      {/* Right Column: Auxiliary Controls & Volume */}
      <div className="flex items-center justify-end gap-3 min-w-[240px] max-w-[30%]">
        {/* Sleep Timer Indicator if active */}
        {sleepTimerRemaining !== null && (
          <button
            onClick={() => setIsEqualizerOpen(true)}
            className="flex items-center gap-1 px-2 py-1 rounded-full bg-indigo-500/20 text-indigo-300 text-xs font-mono border border-indigo-500/30"
          >
            <Moon className="w-3.5 h-3.5" />
            <span>{Math.ceil(sleepTimerRemaining / 60)}m</span>
          </button>
        )}

        {/* Lyrics Button */}
        <button
          onClick={() => setIsLyricsOpen(!isLyricsOpen)}
          title="Karaoke Lyrics"
          className={`p-2 rounded-full transition-all ${
            isLyricsOpen ? 'text-spotify-green bg-white/10' : 'text-zinc-400 hover:text-white'
          }`}
        >
          <Mic2 className="w-4 h-4" />
        </button>

        {/* Queue Button */}
        <button
          onClick={() => setIsQueueOpen(!isQueueOpen)}
          title="Play Queue"
          className={`p-2 rounded-full relative transition-all ${
            isQueueOpen ? 'text-spotify-green bg-white/10' : 'text-zinc-400 hover:text-white'
          }`}
        >
          <ListMusic className="w-4 h-4" />
          {(queueIndex >= 0 ? queue.length - queueIndex - 1 : queue.length) > 0 && (
            <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-spotify-green" />
          )}
        </button>

        {/* Equalizer & Audio Settings */}
        <button
          onClick={() => setIsEqualizerOpen(true)}
          title="Audio Equalizer & Effects"
          className="p-2 rounded-full text-zinc-400 hover:text-white transition-all"
        >
          <Sliders className="w-4 h-4" />
        </button>

        {/* Volume Controls */}
        <div className="flex items-center gap-2 group w-32">
          <button
            onClick={toggleMute}
            className="text-zinc-400 hover:text-white p-1 transition-colors"
          >
            <VolumeIconComponent className="w-4 h-4" />
          </button>
          
          <div className="relative flex-1 flex items-center">
            <div className="absolute inset-x-0 h-1 rounded-full bg-zinc-800 pointer-events-none overflow-hidden">
              <div 
                className="h-full bg-white group-hover:bg-spotify-green transition-colors rounded-full" 
                style={{ width: `${(isMuted ? 0 : volume) * 100}%` }}
              />
            </div>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={isMuted ? 0 : volume}
              onChange={(e) => setVolume(parseFloat(e.target.value))}
              className="w-full h-3 opacity-0 cursor-pointer relative z-10"
            />
          </div>
        </div>

        {/* Fullscreen Mode */}
        <button
          onClick={() => setIsFullscreen(true)}
          title="Aesthetic Fullscreen"
          className="p-2 rounded-full text-zinc-400 hover:text-white hover:scale-105 transition-all"
        >
          <Maximize2 className="w-4 h-4" />
        </button>
      </div>
    </footer>
  );
};
