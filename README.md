# MoodSync 🎵🐾

An Android app that combines mood-aware music recommendations, an AI mascot buddy, and social music sharing — powered by Spotify, Firebase, and OpenAI (ChatGPT).

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup & Configuration](#setup--configuration)
- [Running the App](#running-the-app)
- [Architecture Overview](#architecture-overview)
- [Screen Map](#screen-map)

---

## Features

- 🐾 **AI Mascot Buddy** — An animated companion that detects your mood and recommends music
- 🎵 **Mood-aware Music Recommendations** — Powered by Spotify search + OpenAI ChatGPT
- 💬 **Emotion Chat** — Chat with the AI to express feelings and get song suggestions
- 👥 **Social Sharing** — Share songs with friends via QR code
- 📊 **Mood History** — Track your emotional patterns over time
- 🎨 **Dynamic Theming** — App colours shift based on your current mood
- 🛍️ **Pet Shop** — Customise your mascot with accessories

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Auth & Database | Firebase Auth + Firestore |
| Music API | Spotify Android SDK + Web API |
| AI Chat | OpenAI ChatGPT API (GPT-4) |
| Architecture | MVVM (ViewModel + StateFlow) |
| Networking | Retrofit + OkHttp |

---

## Project Structure

```
com.example.fypdraft/
│
├── core/
│   └── config/
│       └── AppConfig.kt          # API keys, base URLs, system prompts
│
├── data/
│   ├── api/
│   │   ├── ChatGPTApiService.kt  # Retrofit interface for OpenAI
│   │   └── SpotifyApiService.kt  # Retrofit interface for Spotify Web API
│   └── repository/
│       ├── AuthRepository.kt          # Firebase authentication
│       ├── ChatGPTRepository.kt       # ChatGPT conversation + song parsing
│       ├── FavoritesRepository.kt     # Liked/saved tracks (Firestore)
│       ├── MoodCheckInRepository.kt   # Manual mood check-in storage
│       ├── MoodHistoryRepository.kt   # Mood log retrieval
│       ├── SearchHistoryRepository.kt # Recent searches
│       ├── SocialRepository.kt        # Friend connections, QR share
│       ├── SpotifyMusicRepository.kt  # Spotify search + playlist fetch (singleton)
│       ├── SpotifyRepository.kt       # Spotify OAuth token management (singleton)
│       └── UserPreferencesRepository.kt
│
├── model/
│   ├── AIResponse.kt            # ChatGPT response models
│   ├── AudioEmotionTagger.kt    # Audio feature → emotion mapping
│   ├── AudioPreprocessor.kt
│   ├── ChatModels.kt
│   ├── ExplanationGenerator.kt  # Why-this-song explanation
│   ├── GptMoodTagger.kt         # GPT-based mood classification
│   ├── IntentClassifier.kt
│   ├── MascotMood.kt            # Enum: mascot emotional states
│   ├── MetadataEmotionTagger.kt
│   ├── MoodAwareRecommender.kt  # Core mood → music recommendation logic
│   ├── MoodPipeline.kt          # Orchestrates mood detection pipeline
│   ├── MusicModels.kt           # Track, Playlist, Album data classes
│   ├── MusicRecommendationEngine.kt
│   ├── PassiveMoodDetector.kt
│   ├── PetAIBrain.kt            # Mascot AI decision engine
│   ├── PetBehaviourEngine.kt
│   ├── PetModels.kt             # PetState, PetType, accessories
│   ├── PetPersonalityEngine.kt
│   ├── RLRecommendationEngine.kt # Reinforcement-learning-style recommendations
│   ├── SpotifyAudioFeatures.kt
│   └── UserTasteProfile.kt
│
├── ui/
│   └── theme/
│       ├── Color.kt
│       ├── MoodThemeEngine.kt   # Maps mood → colour palette
│       ├── Theme.kt
│       └── Type.kt
│
├── view/                        # All Composable screens
│   ├── AudioVisualizerView.kt
│   ├── BuddyHubCard.kt
│   ├── ConnectMusicScreen.kt
│   ├── EmotionChatScreen.kt
│   ├── FlappyMiniGame.kt
│   ├── FriendsScreen.kt
│   ├── HomeScreen.kt
│   ├── LayeredAvatarSystem.kt   # Modular mascot avatar (stacked PNGs)
│   ├── LibraryScreen.kt
│   ├── LoginScreen.kt
│   ├── MascotWidget.kt          # Floating interactive mascot
│   ├── MoodHistoryScreen.kt
│   ├── MusicPlayerScreen.kt
│   ├── NicknameScreen.kt
│   ├── PetShopScreen.kt
│   ├── QRScannerScreen.kt
│   ├── ResetPWScreen.kt
│   ├── SearchScreen.kt
│   ├── SettingsScreen.kt
│   ├── SignUpScreen.kt
│   ├── SmartFloatingPet.kt      # Draggable floating pet overlay
│   ├── SpotifyConnectionScreen.kt
│   ├── ThemeSelectionScreen.kt
│   └── WelcomeScreen.kt
│
├── viewmodel/
│   ├── AuthViewModel.kt
│   ├── ChatGPTViewModel.kt
│   ├── MoodCheckInViewModel.kt
│   ├── MoodHistoryViewModel.kt
│   ├── MusicPlayerViewModel.kt  # Spotify playback + MediaPlayer bridge
│   └── SpotifyViewModel.kt
│
├── MainActivity.kt              # Entry point, back-stack nav, screen routing
├── MoodSyncApplication.kt       # Application class
└── NetworkModule.kt             # Retrofit/OkHttp factory
```

---

## Prerequisites

- Android Studio **Hedgehog (2023.1.1)** or later
- Android SDK **API 26+** (minSdk 26)
- A **Spotify Premium** account (required for playback)
- A **Spotify Developer** app registered at [developer.spotify.com](https://developer.spotify.com)
- An **OpenAI API key** with GPT-4 access
- A **Firebase** project with Auth (Email/Password) and Firestore enabled

---

## Setup & Configuration

### 1. Clone the repository

```bash
git clone https://github.com/<your-org>/moodsync.git
cd moodsync
```

### 2. Firebase

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable **Email/Password** authentication
3. Enable **Cloud Firestore**
4. Download `google-services.json` and place it in `app/`

### 3. API Keys

Create or edit `local.properties` in the project root (never commit this file):

```properties
OPENAI_API_KEY=sk-...your-key...
```

The Spotify Client ID is already embedded in `AppConfig.kt` and `SpotifyRepository.kt`:

```
CLIENT_ID = "667c083092c747f1bef171ed8aacb46e"
REDIRECT_URI = "fypdraft://callback"
```

If you use your own Spotify app, update these two constants and register `fypdraft://callback` as a redirect URI in your Spotify Developer Dashboard.

### 4. Spotify SDK

The Spotify Android Auth SDK (`.aar`) must be present. If it is missing, download it from [Spotify for Developers](https://developer.spotify.com/documentation/android) and add it under `app/libs/`.

---
## Running the App

### Option 1 — Run directly from Android Studio
1. Open the project in Android Studio
2. Let Gradle sync complete
3. Connect a physical device or emulator (API 26+)
4. Click **Run ▶**

### Option 2 — Debug APK
1. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`
3. Install with `adb install app/build/outputs/apk/debug/app-debug.apk`

### Option 3 — Release APK
1. Go to **Build → Generate Signed Bundle / APK → APK**
2. Select or create a keystore, choose **release** build variant
3. Find the APK at `app/build/outputs/apk/release/app-release.apk`

> **Note:** Spotify audio playback only works on a physical device with the Spotify app installed.
> Register your debug SHA-1 in Firebase Console under *Project Settings → Your Apps* to keep Auth working on installed APKs. Run `./gradlew signingReport` to get it.
---

## Architecture Overview

```
UI (Compose Screens)
        │
        ▼
ViewModels (StateFlow)
        │
        ▼
Repositories (data access)
   ├── Firebase (Auth, Firestore)
   ├── SpotifyRepository (OAuth token)
   ├── SpotifyMusicRepository (search, playlists)
   └── ChatGPTRepository (OpenAI chat + song parsing)
        │
        ▼
External APIs
   ├── Spotify Web API
   ├── Spotify Android SDK (playback)
   └── OpenAI API
```

## Screen Map

```
Welcome
  ├── Login ──────────────────────┐───────────┐
  │     └── Reset Password        │           │
  └── Sign Up ────────────────────│           │
                                              ▼
                                           Home (main tab)
                                            ├── Emotion Chat
                                            ├── Mood History
                                            ├── Settings
                                            └── Spotify Connection

Bottom Navigation:
  [Home] [Search] [Friends] [Library]
                     
                     
                     
```
