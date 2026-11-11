# ✨ ELARA

> **Your music, in orbit.** A cosmic-themed music streaming platform with a spatial "Vibe Galaxy", ambient mixing, a full web player, and a native Android app — powered by YouTube Music.

> [!NOTE]
> **ELARA is purely vibe-coded** 🎛️ — built end-to-end on vibes, prompts, and AI flow. No hand-written specs, no design docs, just code that *felt* right.

<p align="center">
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white" alt="React 18"/>
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white" alt="FastAPI"/>
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Compose"/>
  <img src="https://img.shields.io/badge/Tailwind-3-38BDF8?logo=tailwindcss&logoColor=white" alt="Tailwind CSS"/>
  <img src="https://img.shields.io/badge/Vibe--Coded-100%25-purple" alt="Vibe-coded"/>
</p>

---

## 🌌 What is ELARA?

ELARA turns music discovery into a visual, spatial experience. Instead of a flat list of songs, trending tracks float as **stars in an interactive galaxy** — each with its own vibe coordinates, energy level, and accent color. Pick a star, and the music starts.

The repository contains **three parts**:

| Part | Path | Stack |
|------|------|-------|
| 🖥️ Web App | `src/` | React 18, TypeScript, Vite, Tailwind CSS, Zustand, Framer Motion |
| ⚙️ API Engine | `server/` | Python, FastAPI, ytmusicapi, yt-dlp |
| 📱 Android App | `app/` | Kotlin, Jetpack Compose, Material 3 |

---

## 🚀 Features

### 🌠 Vibe Galaxy
- Trending songs rendered as **stars across a celestial map**, positioned in non-overlapping quadrants
- Each track carries generated **vibe coordinates, energy, and accent color**
- One tap on any star launches playback instantly

### 🎧 Full-Featured Player
- **Floating Island player** — a compact, animated mini-player that's always with you
- **Fullscreen player** with immersive visuals
- **Live audio visualizer** reacting to the music
- **Queue drawer** to see and reorder what's coming next
- **Synced lyrics view** straight from YouTube Music

### 🎚️ Sound Tuning
- **Built-in Equalizer** modal
- **Spatial Audio** mode for a wider soundstage
- **Ambient Mixer** — layer ambient sounds over your music
- **Focus Mode** for distraction-free listening sessions

### 🔍 Discovery
- **Search** songs, artists, albums, and playlists (YouTube Music)
- **Charts & Trending** — global charts blended with curated trending seeds
- **Explore playlists** — curated community playlists with full track lists
- **Artist pages** with top tracks

### 📚 Your Library
- **Liked Songs**, custom playlist creation, and a full library view
- **Offline downloads** with an offline storage service
- **Track uploads** — add your own audio files
- **Listening stats & Wrapped** on Android — your month in music

### 📱 Native Android App
- Built with **Kotlin + Jetpack Compose** (`com.piroman.elara`)
- Home, Search, Library, Downloads, Playlists, and Player screens
- **Spotify playlist import** and configurable audio sources
- **Live spectrum bars**, floating & fullscreen players, queue drawer
- **Listening stats, Wrapped screen, playback diagnostics, and backup**

---

## 🏗️ Architecture

```
┌──────────────┐      ┌───────────────┐      ┌─────────────┐
│   Web App    │─────▶│  FastAPI API  │─────▶│ YouTube     │
│  React+Vite  │      │  (port 8000)  │      │ Music Engine│
└──────────────┘      └───────────────┘      └─────────────┘
                              │
                              ▼
                      ┌───────────────┐
                      │    yt-dlp     │
                      │ audio streams │
                      └───────────────┘
```

### API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | Service health check |
| `GET /api/search?q=&filter=` | Search songs/artists/albums/playlists |
| `GET /api/charts` | Trending tracks with galaxy coordinates |
| `GET /api/explore/playlists` | Curated playlists for Explore |
| `GET /api/playlist/{id}` | Full track list for a playlist |
| `GET /api/stream/{video_id}` | Streaming audio proxy (supports Range requests) |
| `GET /api/lyrics/{video_id}` | Synced lyrics for a track |
| `GET /api/download/{video_id}` | Downloadable MP4 stream |

---

## 🛠️ Getting Started

### Prerequisites
- **Node.js** 18+ and npm
- **Python** 3.10+ with `pip`

### 1. Start the API Engine

```bash
pip install fastapi uvicorn ytmusicapi yt-dlp requests
npm run server
```

The API engine runs at `http://localhost:8000`.

### 2. Start the Web App

```bash
npm install
npm run dev
```

Open `http://localhost:5173` and explore the galaxy. 🌌

### 3. Android App (optional)

Open the project root in **Android Studio** and run the `app` module on a device or emulator.

---

## 🧰 Tech Stack

- **Frontend:** React 18, TypeScript, Vite 6, Tailwind CSS 3, Zustand, Framer Motion, Lucide icons
- **Backend:** FastAPI, Uvicorn, ytmusicapi, yt-dlp
- **Android:** Kotlin, Jetpack Compose, Material 3

---

## 📄 License & Attribution

See [ATTRIBUTION.md](ATTRIBUTION.md) for third-party attributions.

---

<p align="center">⭐ Star this repo if ELARA made your listening out of this world.</p>
