# Freetia TV

Freetia TV — free, open-source IPTV player for your own M3U playlists, on phones, tablets, Android TV and Fire TV. Not yet compiled; see PROJECT_STATUS.md.

## Features

### Core Playback
- 🎬 **HLS, DASH, MPEG-TS** streams via Media3/ExoPlayer
- 🔄 **Automatic quality switching** with user control (1080p, 720p, 480p, auto)
- 🌍 **Multi-format EPG** (XMLTV) with now/next, search, and catchup/timeshift
- 📺 **Live TV** experience: channel up/down, instant switching, numbered entry

### Playlists & Organization
- 📋 **Unlimited playlists** with priority-based auto-switching
- 🔄 **Auto-update on startup** (per playlist) with stale-while-revalidate
- 🌐 **Region and language filters** from M3U metadata
- ⭐ **Favorites** + custom groups (e.g., Kids, News, Sports)
- 📊 **Health check** to flag dead streams

### Navigation & Remotes
- 🎮 **Full D-pad control**: Up/Down = channels, Left/Right = menu, OK = select
- 🔢 **Numeric entry**: press 0-9 to jump to channel by number
- 🎨 **Color buttons**: Customizable red, green, yellow, blue shortcuts
- ⏮️ **Double-Back exit**: one Back shows hint, two within 2s exits fully
- 📱 **Touch & Compose**: Compose Material 3 for phones, TV Compose for sets

### Safety & Parental Controls
- 🔐 **Child Safe Mode** with PIN (salted SHA-256 hash)
- 🔑 **Recovery code** + security question for forgotten PIN
- 🚫 **Adult channel detection** from metadata + manual marking
- 🔒 **Channel locks** per channel or by group/region
- ⏰ **Session unlock**: PIN stays valid for configurable period (default 30 min)

### Playback & Streaming
- 📺 **Buffer presets**: Low Latency (2s), Balanced (10s), Stable (30s), Custom
- 🎵 **Multi-audio tracks** with language selection and per-channel memory
- 📝 **Captions**: CEA-608/708 + sidecar subtitles, customizable size/color
- 💤 **Sleep timer**: 10–240 minutes, quick +15/+30 min buttons, 60s warning

### Background & Auto-Start
- 🔊 **Background audio** with notification controls (per playlist)
- 📱 **Picture-in-picture** (phone only)
- 🌅 **Auto-start on TV** with "Boot Completed" receiver (off by default)
- 🔔 **Reminders**: set for future programmes, wake screen + tune to channel

### Cache & Performance
- ⚡ **Cold start < 2s** (Fire TV Stick Lite, cache clear on)
- 🖼️ **Logo cache**: 50 MB LRU, auto-eviction
- 📦 **Streaming parsers**: M3U & XMLTV parsed in chunks, never fully in memory
- 🧹 **Smart cache policy**: full clear on exit (logo, EPG, temp), but keep settings & playlists

### Privacy & Compliance
- 🔒 **No accounts, no analytics** (opt-in only in future)
- 🔐 **Encrypted storage** for PIN, recovery code, playlist URLs with credentials
- 📱 **User-supplied sources only**: no built-in channels or providers
- ✅ **Google Play & Amazon compliance**: focused store listing, content responsibility note

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose Material 3 (mobile) + Compose for TV (Android TV/Fire TV)
- **Database**: Room with 9 DAOs
- **Playback**: Media3/ExoPlayer with adaptive bitrate
- **Parsing**: Custom streaming parsers (M3U, XMLTV)
- **Background**: WorkManager (refresh, updates) + AlarmManager (reminders, sleep timer)
- **Storage**: DataStore for settings, encrypted SharedPreferences for secrets
- **Min SDK**: Android 8.0 (API 26), targeting Android 14 (API 34)

## Project Structure

```
app/
├── build.gradle.kts                    # Dependencies, build config
├── src/main/
│   ├── AndroidManifest.xml             # Permissions, receivers, services
│   ├── kotlin/com/freetia/tv/
│   │   ├── FreetiaApp.kt                # Application class, singletons
│   │   ├── ui/
│   │   │   ├── MainActivity.kt
│   │   │   └── theme/
│   │   ├── data/
│   │   │   ├── database/               # Room, DAOs (9 files)
│   │   │   ├── model/                  # Entities (10 classes)
│   │   │   ├── parser/                 # M3U, XMLTV streamers
│   │   │   ├── preferences/            # DataStore keys
│   │   │   └── repository/             # Playlists (expandable)
│   │   ├── core/
│   │   │   ├── security/               # EncryptionManager
│   │   │   ├── cache/                  # CacheManager
│   │   │   ├── util/                   # DeviceDetector
│   │   │   ├── input/                  # RemoteKeyHandler
│   │   │   └── timer/                  # SleepTimerManager
│   │   ├── playback/
│   │   │   └── PlaybackService.kt      # MediaSession + foreground
│   │   ├── receiver/                   # BootReceiver, ReminderReceiver
│   │   └── work/                       # WorkManager tasks
│   └── res/
│       ├── values/strings.xml          # 100+ strings
│       ├── values/colors.xml           # Material palette
│       └── values/themes.xml           # Light, dark, high-contrast
```

## Getting Started

### Requirements
- Android Studio 2022.1+
- Gradle 8.0+
- JDK 17
- Android SDK 34 (Android 14), with API 26 (Android 8.0) available

### Setup
1. Clone or download the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Connect a device or launch an emulator
5. Run the app

### First Run
- App opens with loading screen
- No playlists configured → settings screen prompts to add one
- Enter M3U/M3U8 URL and refresh to load channels
- Select region, set defaults, enjoy

### Adding a Playlist
1. Settings → Playlists → Add Playlist
2. Paste URL, give it a name, set priority
3. Choose refresh interval, startup behavior
4. Save and refresh (or wait for auto-update)

### Testing Playlists
Use free, legal M3U sources for testing:
- Many public IPTV lists are available online
- Ensure you have rights to stream content
- Test with 10, 100, and 1,000+ channel playlists

## Development

### Adding Features
See `CLAUDE.md` for architectural guidelines, coding conventions, and performance targets.

### Running Tests
Android instrumentation tests:
```bash
./gradlew connectedAndroidTest
```

### Building for Release
```bash
./gradlew assembleRelease
```
Review `app/proguard-rules.pro` before signing for Play Store or Fire TV.

### Device Testing Matrix
- Fire TV Stick 4K (reference, best support)
- Fire TV Stick Lite (performance target)
- Android TV box (generic, e.g., MiBox)
- Samsung/Sony TV (native Android TV)
- Pixel phone (reference mobile)
- Tablet (9-10 inch)

## Performance Targets
- **Cold start**: < 2 seconds
- **Channel change**: < 2 seconds
- **10,000+ channels**: no UI lag
- **Crash-free sessions**: > 99.5%

## Security & Privacy
- PIN: salted SHA-256 hash
- Playlist URLs with tokens: encrypted SharedPreferences
- Cache: full clear on exit (configurable)
- No telemetry without opt-in
- Privacy-first: no accounts, no tracking

## Known Limitations (v1.0)
- No DRM (Widevine) support; show clear unsupported message
- Local-only; no cloud sync or accounts (v2.0+)
- Picture-in-picture phone only; TV PiP if OS supports
- Catch-up/timeshift (v1.1+)
- Multi-device sync (v2.0+)

## Roadmap

### v1.0 (MVP - Current)
- ✅ Remote navigation, playlists, channels
- ✅ Audio tracks, captions, resolution
- ✅ Child Safe Mode, PIN, locks
- ✅ EPG (now/next), sleep timer, cache policy
- ✅ Settings, multi-device support

### v1.1
- Full EPG grid with search
- Reminders with auto-wake
- Background play + auto-start on TV
- Settings backup/import
- Voice search (Fire TV)

### v2.0
- Catch-up / timeshift
- Phone PiP + background audio
- Diagnostics dashboard
- Custom color-button mapping
- Multi-device sync

## Compliance
- **Google Play Store**: Focuses on "play your own playlists," no promoted providers
- **Amazon Fire TV**: Same principles, tested on Fire TV Stick 4K/Lite
- **Content**: User responsibility; clear disclaimer in app

## Support & Contribution
- **Bug reports**: Include device, playlist source, steps to reproduce
- **Feature requests**: Align with roadmap phases
- **Code contributions**: Follow conventions in `CLAUDE.md`

## License
Apache-2.0 — see LICENSE.

## Credits
Built with Kotlin, Jetpack Compose, Media3, and Android best practices.

---

**Made for users who want a simple, remote-friendly, ad-free IPTV player for the whole family.**
