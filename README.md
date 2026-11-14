# Freetia TV

Freetia TV — free, open-source IPTV player for your own M3U playlists, on phones, tablets, Android TV and Fire TV. Builds locally with `gradlew.bat`; see PROJECT_STATUS.md.

## Features

Everything in the v1.0 spec is built:
- **Playlists**: several M3U/M3U8 links with priority, startup rules and auto-update; region picker; the `store` build has no built-in channels or providers
- **Browse**: categories, favourites, custom groups, recents, search and voice search, health check for dead streams
- **Player**: Up/Down and number keys, audio tracks, captions with style, resolution and auto-quality toasts, buffer presets, HLS, DASH and MPEG-TS
- **Programme guide**: full EPG grid, guide search, reminders, catch-up
- **Family controls**: Child Safe Mode with PIN, recovery code and security question, channel locks, profiles
- **Everywhere**: sleep timer, background play, phone picture-in-picture, auto-start on TV (opt-in, off by default), cache policy
- **Settings**: all pages, export/import (file, passphrase-encrypted, QR or typed code), diagnostics, About screen, accessibility
- **Private by design**: no account, no ads, no analytics; links are encrypted on the device and excluded from backup

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose Material 3 (mobile) + Compose for TV (Android TV/Fire TV)
- **Database**: Room with 9 DAOs
- **Playback**: Media3/ExoPlayer with adaptive bitrate
- **Parsing**: Custom streaming parsers (M3U, XMLTV)
- **Background**: WorkManager (refresh, updates) + AlarmManager (reminders, sleep timer)
- **Storage**: DataStore for settings, encrypted SharedPreferences for secrets
- **Min SDK**: Android 8.0 (API 26), target SDK 36

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
- PIN, recovery code and security answer: salted hashes
- Playlist and guide URLs: encrypted on the device, excluded from backup and transfer
- Cache: full clear on exit (configurable)
- No telemetry without opt-in
- Privacy-first: no accounts, no tracking

## Known Limitations (v1.0)
- No DRM (Widevine) support; shows a clear unsupported message
- Local-only; no cloud sync or accounts. Moving to another device uses export/import
- Picture-in-picture is for phones; TV PiP only if the OS supports it
- Not yet tried on real Fire TV or Android TV hardware

## Roadmap

v1.0 is the full spec; see `plan/V1_BUILD_PLAN.md` and PROJECT_STATUS.md. Future ideas are not scheduled.

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
