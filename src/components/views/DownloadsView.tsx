import React, { useRef } from 'react';
import { useMusicStore, DownloadedTrack } from '../../store/useMusicStore';
import { 
  Download, 
  Clock, 
  Trash2, 
  CheckCircle2, 
  XCircle, 
  Loader2, 
  HardDrive, 
  Play, 
  Pause, 
  Upload, 
  WifiOff, 
  Film, 
  Music 
} from 'lucide-react';
import { formatTotalDuration, formatTime } from '../../utils/formatters';

export const DownloadsView: React.FC = () => {
  const downloadedTracks = useMusicStore(state => state.downloadedTracks);
  const currentTrack = useMusicStore(state => state.currentTrack);
  const isPlaying = useMusicStore(state => state.isPlaying);
  const playTrack = useMusicStore(state => state.playTrack);
  const togglePlay = useMusicStore(state => state.togglePlay);
  const removeDownload = useMusicStore(state => state.removeDownload);
  const clearDownloads = useMusicStore(state => state.clearDownloads);
  const downloadTrack = useMusicStore(state => state.downloadTrack);
  const importDownloadedMP4 = useMusicStore(state => state.importDownloadedMP4);
  const activePlaybackSource = useMusicStore(state => state.activePlaybackSource);
  const isOfflineOnly = useMusicStore(state => state.isOfflineOnly);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const completedCount = downloadedTracks.filter(d => d.status === 'completed').length;
  const tracks = downloadedTracks.map(d => d.track);
  const totalDuration = tracks.reduce((acc, t) => acc + (t.duration || 180), 0);

  const handleTrackClick = (track: typeof tracks[0]) => {
    if (currentTrack?.id === track.id) {
      togglePlay();
    } else {
      playTrack(track, tracks, 'Downloads');
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      Array.from(files).forEach(file => {
        importDownloadedMP4(file);
      });
    }
  };

  const getStatusIcon = (status: DownloadedTrack['status']) => {
    switch (status) {
      case 'downloading':
        return <Loader2 className="w-3.5 h-3.5 text-cyan-400 animate-spin" />;
      case 'completed':
        return <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />;
      case 'failed':
        return <XCircle className="w-3.5 h-3.5 text-rose-400" />;
    }
  };

  const getStatusLabel = (status: DownloadedTrack['status']) => {
    switch (status) {
      case 'downloading': return 'Downloading MP4...';
      case 'completed': return 'Offline Ready';
      case 'failed': return 'Failed';
    }
  };

  return (
    <div className="pb-36 select-none">
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept="video/mp4,audio/mp4,video/*,audio/*"
        multiple
        className="hidden"
      />

      {/* Header Banner */}
      <div className="relative p-6 sm:p-8 md:p-12 overflow-hidden bg-gradient-to-b from-teal-900/50 via-cyan-950/30 to-transparent border-b border-white/[0.06]">
        <div className="absolute top-0 left-10 w-96 h-96 rounded-full bg-cyan-500/15 filter blur-[100px] pointer-events-none" />

        <div className="relative z-10 flex flex-col md:flex-row items-start md:items-end gap-6 justify-between">
          <div className="flex flex-col md:flex-row items-start md:items-end gap-6">
            <div className="w-36 h-36 sm:w-44 sm:h-44 rounded-3xl bg-gradient-to-br from-teal-500 via-cyan-500 to-blue-500 flex items-center justify-center shadow-2xl shadow-cyan-500/30 flex-shrink-0 border border-white/20">
              <Film className="w-20 h-20 text-white drop-shadow-lg" />
            </div>

            <div className="flex flex-col space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-mono uppercase tracking-widest text-cyan-300 font-bold flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-cyan-500/10 border border-cyan-500/20">
                  <HardDrive className="w-3 h-3" />
                  Local MP4 Engine
                </span>
                {isOfflineOnly && (
                  <span className="text-[11px] font-mono uppercase tracking-widest text-amber-300 font-bold flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-amber-500/10 border border-amber-500/20">
                    <WifiOff className="w-3 h-3" />
                    Offline Mode Active
                  </span>
                )}
              </div>

              <h1 className="text-3xl sm:text-5xl md:text-6xl font-black text-white tracking-tight drop-shadow-md">
                Downloads & Offline
              </h1>
              
              <p className="text-xs sm:text-sm text-zinc-300 flex items-center gap-2 pt-1 font-medium flex-wrap">
                <span className="font-bold text-white">{completedCount} offline MP4 songs</span>
                <span>•</span>
                <span className="text-cyan-300 font-mono font-bold">{downloadedTracks.length} total</span>
                {downloadedTracks.length > 0 && (
                  <>
                    <span>•</span>
                    <span className="text-zinc-400 font-mono">{formatTotalDuration(totalDuration)}</span>
                  </>
                )}
                {activePlaybackSource === 'offline-mp4' && (
                  <span className="ml-2 px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-300 text-[10px] font-mono font-bold animate-pulse border border-emerald-500/30">
                    ● Playing from Local MP4
                  </span>
                )}
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-3 self-stretch md:self-end pt-4 md:pt-0">
            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex-1 md:flex-initial px-4 py-2.5 rounded-2xl bg-cyan-500/20 hover:bg-cyan-500/30 text-cyan-200 text-xs font-bold transition-all border border-cyan-500/30 flex items-center justify-center gap-2 shadow-lg shadow-cyan-900/20"
            >
              <Upload className="w-3.5 h-3.5" />
              Import MP4 File
            </button>
            {downloadedTracks.length > 0 && (
              <button
                onClick={() => {
                  const first = downloadedTracks[0]?.track;
                  if (first) playTrack(first, tracks, 'Downloads');
                }}
                className="px-6 py-2.5 rounded-2xl bg-gradient-to-r from-teal-400 to-cyan-500 text-black font-extrabold text-xs shadow-lg shadow-cyan-500/30 hover:scale-105 active:scale-95 transition-all flex items-center gap-2"
              >
                <Play className="w-4 h-4 fill-black" />
                Play All Offline
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="p-6 md:p-8 max-w-7xl mx-auto space-y-6">
        {downloadedTracks.length > 0 && (
          <div className="flex items-center justify-between">
            <p className="text-xs text-zinc-400 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
              Songs in this section are stored in local storage and play offline seamlessly as MP4 files even when disconnected from internet.
            </p>
            <button
              onClick={clearDownloads}
              className="px-3 py-1.5 rounded-xl bg-white/5 hover:bg-rose-500/20 text-xs font-bold text-zinc-400 hover:text-rose-300 transition-colors border border-white/5 flex items-center gap-1.5"
            >
              <Trash2 className="w-3 h-3" />
              Clear History
            </button>
          </div>
        )}

        {downloadedTracks.length > 0 ? (
          <div className="space-y-1">
            {/* Column Header */}
            <div className="grid grid-cols-[36px_4fr_2fr_120px_100px] gap-4 px-4 py-2 text-zinc-400 text-xs font-mono border-b border-white/[0.06] mb-2 uppercase tracking-wider">
              <span>#</span>
              <span>Title & Artist</span>
              <span>Offline Source</span>
              <span className="flex items-center gap-1">
                <Clock className="w-3.5 h-3.5" />
                Time / Date
              </span>
              <span className="text-right">Action</span>
            </div>

            {downloadedTracks.map((dl, index) => {
              const isThisTrackActive = currentTrack?.id === dl.track.id;
              const isThisTrackPlaying = isThisTrackActive && isPlaying;

              return (
                <div
                  key={`${dl.track.id}-${index}`}
                  onClick={() => handleTrackClick(dl.track)}
                  className={`group grid grid-cols-[36px_4fr_2fr_120px_100px] items-center gap-4 px-4 py-3 rounded-2xl transition-all cursor-pointer ${
                    isThisTrackActive 
                      ? 'bg-cyan-500/15 border border-cyan-500/30 text-white shadow-lg shadow-cyan-950/40' 
                      : 'hover:bg-white/5 border border-transparent text-zinc-300'
                  }`}
                >
                  {/* Index / Play Hover */}
                  <div className="flex items-center justify-center">
                    <span className="text-xs font-mono text-zinc-500 group-hover:hidden block">
                      {isThisTrackPlaying ? (
                        <div className="flex items-end gap-[2px] h-3 w-3">
                          <span className="w-0.5 bg-cyan-400 animate-pulse h-full" />
                          <span className="w-0.5 bg-cyan-400 animate-pulse h-2/3" />
                          <span className="w-0.5 bg-cyan-400 animate-pulse h-full" />
                        </div>
                      ) : (
                        index + 1
                      )}
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleTrackClick(dl.track);
                      }}
                      className="hidden group-hover:flex items-center justify-center w-6 h-6 rounded-full bg-cyan-400 text-black hover:scale-110 transition-transform shadow-md"
                    >
                      {isThisTrackPlaying ? (
                        <Pause className="w-3 h-3 fill-black" />
                      ) : (
                        <Play className="w-3 h-3 fill-black ml-0.5" />
                      )}
                    </button>
                  </div>

                  {/* Track Info */}
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div className="relative w-10 h-10 rounded-xl overflow-hidden flex-shrink-0 shadow-md bg-zinc-900 border border-white/10">
                      <img src={dl.track.albumArt} alt={dl.track.title} className="w-full h-full object-cover" loading="lazy" />
                      {isThisTrackPlaying && (
                        <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                          <Music className="w-4 h-4 text-cyan-300 animate-bounce" />
                        </div>
                      )}
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className={`text-xs sm:text-sm font-bold truncate ${
                        isThisTrackActive ? 'text-cyan-300' : 'text-zinc-100'
                      }`}>
                        {dl.track.title}
                      </span>
                      <span className="text-[11px] text-zinc-400 truncate flex items-center gap-1.5">
                        <span>{dl.track.artist}</span>
                        {isThisTrackActive && activePlaybackSource === 'offline-mp4' && (
                          <span className="text-[9px] px-1.5 py-0.2 bg-emerald-500/20 text-emerald-300 rounded font-mono">
                            OFFLINE MP4
                          </span>
                        )}
                      </span>
                    </div>
                  </div>

                  {/* Status */}
                  <div className="flex items-center gap-2">
                    {getStatusIcon(dl.status)}
                    <span className={`text-xs font-semibold ${
                      dl.status === 'completed' ? 'text-emerald-400' :
                      dl.status === 'failed' ? 'text-rose-400' :
                      'text-cyan-400'
                    }`}>
                      {getStatusLabel(dl.status)}
                    </span>
                  </div>

                  {/* Duration & Date */}
                  <div className="flex flex-col text-[11px] font-mono text-zinc-400">
                    <span>{formatTime(dl.track.duration || 180)}</span>
                    <span className="text-[9px] text-zinc-500 truncate">{dl.downloadedAt}</span>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center justify-end gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    {dl.status === 'failed' && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          downloadTrack(dl.track);
                        }}
                        className="p-1.5 rounded-full hover:bg-cyan-500/20 text-zinc-400 hover:text-cyan-400 transition-colors"
                        title="Retry download"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </button>
                    )}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        removeDownload(dl.track.id);
                      }}
                      className="p-1.5 rounded-full hover:bg-rose-500/20 text-zinc-400 hover:text-rose-400 transition-colors"
                      title="Remove from history"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="py-20 text-center glass-panel rounded-3xl border border-white/5 space-y-4 max-w-xl mx-auto p-8">
            <div className="w-16 h-16 rounded-2xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center mx-auto text-cyan-400">
              <Download className="w-8 h-8" />
            </div>
            <div className="space-y-1.5">
              <h3 className="text-lg font-bold text-white">No offline songs yet</h3>
              <p className="text-xs text-zinc-400 max-w-md mx-auto leading-relaxed">
                Click the <strong className="text-cyan-300 font-semibold">"Download MP4"</strong> option on any song in Vibe Galaxy, Search & Explore, or Playlists. The song will be saved locally so you can play it even when there's no internet connection!
              </p>
            </div>
            <button
              onClick={() => fileInputRef.current?.click()}
              className="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-xs font-bold text-white transition-all border border-white/10 inline-flex items-center gap-2"
            >
              <Upload className="w-3.5 h-3.5" />
              Import Local MP4 Files
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
