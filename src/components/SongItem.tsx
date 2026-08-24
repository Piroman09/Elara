import React from 'react';
import { Download } from 'lucide-react';

interface Song {
  id: string;
  title: string;
  artist: string;
  cover: string;
}

interface SongItemProps {
  song: Song;
}

const SongItem: React.FC<SongItemProps> = ({ song }) => {
  const handleDownload = () => {
    // Assuming the backend provides a download endpoint
    window.open(`/api/download/${song.id}`, '_blank');
  };

  return (
    <div className="flex items-center justify-between p-4 bg-surface-card rounded-lg hover:bg-surface-card-hover transition-colors">
      <div className="flex items-center space-x-4">
        <img src={song.cover} alt={song.title} className="w-12 h-12 rounded" />
        <div>
          <h3 className="font-semibold">{song.title}</h3>
          <p className="text-sm text-gray-400">{song.artist}</p>
        </div>
      </div>
      <button
        onClick={handleDownload}
        className="p-2 rounded-full hover:bg-spotify-hover transition-colors"
      >
        <Download size={20} />
      </button>
    </div>
  );
};

export default SongItem;
