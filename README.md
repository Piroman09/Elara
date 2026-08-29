# 🎵 ELARA

> **Your music. Your mood. Your world.**

**ELARA** is a modern Android music streaming application built to provide a clean, smooth, and aesthetic music listening experience.

Discover music, search for your favorite tracks, create playlists, and enjoy your music — all from one beautiful Android app.

---

## ✨ Features

* 🎧 **Music Streaming** — Stream your favorite music directly from the app.
* 🔍 **Search** — Find songs, artists, albums, and playlists quickly.
* ❤️ **Favorites** — Save your favorite songs for easy access.
* 🎵 **Playlists** — Create and manage personalized playlists.
* 🕘 **Recently Played** — Keep track of your recently played music.
* 📱 **Android Native Experience** — Designed specifically for Android devices.
* 🎨 **Clean & Aesthetic UI** — Minimal, modern, and user-friendly interface.
* ⚡ **Smooth Performance** — Built with performance and responsiveness in mind.

---

## 🛠️ Tech Stack

| Technology         | Purpose                         |
| ------------------ | ------------------------------- |
| **Kotlin / Java**  | Android application development |
| **Android Studio** | Development environment         |
| **Gradle**         | Build system                    |
| **Supabase**       | Backend & database              |
| **Git & GitHub**   | Version control                 |

> The technology stack may evolve as ELARA continues to develop.

---

## 📱 Screens

ELARA is designed around a simple and intuitive music experience.

Planned/available screens include:

* 🏠 Home
* 🔎 Search
* 🎵 Music Player
* ❤️ Favorites
* 📚 Playlists
* 👤 Profile
* 🎤 Artist pages
* 💿 Album pages

---

## 🚀 Getting Started

### Prerequisites

Make sure you have:

* Android Studio installed
* Android SDK installed
* JDK configured
* Git installed
* An Android device or emulator

### Clone the Repository

```bash
git clone YOUR_REPOSITORY_URL
cd ELARA
```

### Open in Android Studio

1. Open **Android Studio**.
2. Select **Open**.
3. Choose the ELARA project folder.
4. Allow Gradle to sync.
5. Connect an Android device or start an emulator.
6. Press **Run ▶**.

---

## 🔐 Supabase Configuration

ELARA uses **Supabase** for backend functionality.

If the project requires Supabase credentials, configure them using your project's environment/configuration system.

**Never commit private API keys, service-role keys, passwords, or other secrets to GitHub.**

---

## 🏗️ Build the APK

To create a debug APK:

### Windows

```bash
gradlew.bat assembleDebug
```

The generated APK can typically be found under:

```text
app/build/outputs/apk/debug/
```

For a release build:

```bash
gradlew.bat assembleRelease
```

> Release builds may require signing configuration.

---

## 📂 Project Structure

```text
ELARA/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/
│   │
│   ├── build.gradle
│   └── proguard-rules.pro
│
├── gradle/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
└── README.md
```

---

## 🗺️ Roadmap

* [ ] User authentication
* [ ] Music library
* [ ] Search improvements
* [ ] Personalized recommendations
* [ ] Artist profiles
* [ ] Album pages
* [ ] Lyrics
* [ ] Queue management
* [ ] Recently played
* [ ] Download/offline listening
* [ ] Background playback
* [ ] Lock-screen controls
* [ ] Notifications & media controls
* [ ] Android Auto support
* [ ] Improved animations
* [ ] More customization options

---

## 🤝 Contributing

Want to contribute to ELARA?

1. Fork or clone the repository.
2. Create a new branch.
3. Make your changes.
4. Test the application.
5. Commit your changes.
6. Push your branch.
7. Open a Pull Request.

```bash
git checkout -b feature/my-feature
git add .
git commit -m "Add my feature"
git push origin feature/my-feature
```

---

## ⚠️ Disclaimer

ELARA is a software project created for learning and development purposes.

Make sure that any music, artwork, metadata, or other media used by the application is properly licensed or otherwise authorized for use.

---

## 📄 License

This project is currently intended for development and educational purposes.

A formal open-source license can be added when the project is ready for public distribution.

---

<div align="center">

# 🎶 ELARA

### Listen. Discover. Feel the music.

**Built with ❤️ for music lovers.**
