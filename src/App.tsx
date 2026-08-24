import React, { useEffect } from 'react';
import { useMusicStore } from './store/useMusicStore';
import { AmbientBackground } from './components/common/AmbientBackground';
import { Sidebar } from './components/layout/Sidebar';
import { Navbar } from './components/layout/Navbar';
import { FloatingIslandPlayer } from './components/player/FloatingIslandPlayer';
import { RightSidebar } from './components/layout/RightSidebar';

// Views
import { VibeGalaxyView } from './components/views/VibeGalaxyView';
import { HomeView } from './components/views/HomeView';
import { SearchView } from './components/views/SearchView';
import { PlaylistView } from './components/views/PlaylistView';
import { ArtistView } from './components/views/ArtistView';
import { LikedSongsView } from './components/views/LikedSongsView';
import { LibraryView } from './components/views/LibraryView';
import { DownloadsView } from './components/views/DownloadsView';

// Modals & Overlays
import { LyricsView } from './components/player/LyricsView';
import { EqualizerModal } from './components/player/EqualizerModal';
import { FullscreenModal } from './components/player/FullscreenModal';
import { CreatePlaylistModal } from './components/modals/CreatePlaylistModal';
import { UploadTrackModal } from './components/modals/UploadTrackModal';
import { AuthModal } from './components/modals/AuthModal';

export const App: React.FC = () => {
  const init = useMusicStore(state => state.init);
  const activeView = useMusicStore(state => state.activeView);

  useEffect(() => {
    init();
  }, [init]);

  const renderActiveView = () => {
    switch (activeView) {
      case 'galaxy':
        return <VibeGalaxyView />;
      case 'home':
        return <HomeView />;
      case 'search':
        return <SearchView />;
      case 'playlist':
        return <PlaylistView />;
      case 'artist':
        return <ArtistView />;
      case 'liked':
        return <LikedSongsView />;
      case 'downloads':
        return <DownloadsView />;
      case 'library':
        return <LibraryView />;
      default:
        return <VibeGalaxyView />;
    }
  };

  return (
    <div className="relative h-screen w-screen bg-[#070709] text-zinc-100 flex flex-col overflow-hidden font-sans select-none">
      {/* Dynamic Liquid Aura Background */}
      <AmbientBackground />

      {/* Main Workspace Frame */}
      <div className="flex-1 flex overflow-hidden relative z-10">
        {/* Left Floating Sidebar */}
        <Sidebar />

        {/* Central Scrollable Canvas */}
        <main className="flex-1 flex flex-col overflow-hidden bg-transparent relative">
          <Navbar />
          <div className="flex-1 overflow-y-auto relative no-scrollbar pb-24">
            {renderActiveView()}
          </div>
        </main>

        {/* Right Drawer (Up Next / Queue) */}
        <RightSidebar />
      </div>

      {/* Floating Dynamic Island Player */}
      <FloatingIslandPlayer />

      {/* Media & Tool Modals */}
      <LyricsView />
      <EqualizerModal />
      <FullscreenModal />
      <CreatePlaylistModal />
      <UploadTrackModal />
      <AuthModal />
    </div>
  );
};

export default App;
