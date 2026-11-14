# Freetia TV Project Documentation

## Project Overview
Freetia TV is a free, ad-free IPTV player for Android phones, tablets, Android TV, and Fire TV. It plays channels from user-supplied M3U/M3U8 playlist URLs with support for EPG, reminders, child safety, and full D-pad navigation.

## Architecture

### Core Structure
- **Database Layer**: Room database with 9 DAOs for playlists, channels, favorites, groups, locks, reminders, and EPG
- **Data Layer**: Repositories for abstracted data access, encrypted storage for sensitive data
- **Business Logic**: Parsers (M3U, XMLTV), cache management, device detection
- **UI Layer**: Compose for Material 3 (mobile) and TV (Android TV/Fire TV)
- **Services**: PlaybackService for background audio, WorkManager for playlist refresh and EPG updates
- **Security**: Encrypted SharedPreferences for PIN, recovery codes, playlist URLs with tokens

### Key Files

#### Database & Models
- `data/database/FreetiaDatabase.kt` - Room database configuration
- `data/model/Entities.kt` - All 10 entity classes with relationships
- `data/database/*Dao.kt` - 9 DAOs with full query sets (created by agent)

#### Parsers & Data Access
- `data/parser/M3uParser.kt` - Streaming M3U parser, memory-efficient
- `data/parser/XmltvParser.kt` - StAX-based XMLTV EPG parser
- `data/repository/PlaylistRepository.kt` - Repository pattern (stub, expandable)

#### Core Utilities
- `core/security/EncryptionManager.kt` - PIN/recovery code hashing, URL encryption
- `core/cache/CacheManager.kt` - Logo cache (50MB LRU), EPG/temp buffers, cache policy
- `core/util/DeviceDetector.kt` - TV vs mobile runtime detection
- `core/input/RemoteKeyHandler.kt` - D-pad event mapping and remote events
- `core/timer/SleepTimerManager.kt` - Sleep timer with countdown, add-time support

#### Services & Background Work
- `playback/PlaybackService.kt` - MediaSession for background playback
- `receiver/BootReceiver.kt` - Auto-start on TV (boot completion)
- `receiver/ReminderReceiver.kt` - Alarm-based reminder notifications
- `work/AutoStartWorker.kt` - WorkManager task for boot auto-start
- `work/ReminderWorker.kt` - WorkManager task for reminders

#### UI & Styling
- `ui/MainActivity.kt` - Main activity entry point
- `ui/theme/Theme.kt` - Material 3 dark/light theme with high-contrast variant
- `ui/theme/Type.kt` - Typography (large body text for TV, 24sp minimum)

#### Preferences & Configuration
- `data/preferences/PreferencesKeys.kt` - DataStore keys for all settings (40+ keys)
- `FreetiaApp.kt` - Application class with lazy singletons for database, cache, encryption

#### Resources
- `res/values/strings.xml` - All UI strings (100+ localization ready)
- `res/values/colors.xml` - Material color palette with high-contrast support
- `res/values/themes.xml` - Light, dark, and high-contrast themes

## Build Configuration
- **Gradle**: Android 8.0 (API 26) to Android 14 (API 34)
- **Kotlin**: 1.9.20, Compose 1.5.8
- **Key Dependencies**:
  - Compose + TV Compose
  - Media3/ExoPlayer
  - Room + Moshi
  - OkHttp
  - AndroidX Work, DataStore, Security Crypto
  - AndroidX TV Provider (for reminders/channels)

## Development Workflow

### Adding a New Feature
1. **Define entities** in `data/model/Entities.kt` if database changes are needed
2. **Extend DAO** in `data/database/*Dao.kt` with required queries
3. **Create parser/logic** in `data/parser/` or `core/` subdirectories
4. **Build repository** in `data/repository/` to expose data access
5. **Create UI** in `ui/` with Compose for target device (TV/mobile)
6. **Add preferences** if user settings are required in `PreferencesKeys.kt`
7. **Test** on target device types

### Key Conventions
- **No comments** on trivial code; comment only the WHY (constraints, workarounds, non-obvious invariants)
- **Suspend functions** for all I/O (parsers, database, cache)
- **Flow<T>** for reactive database queries
- **No premature abstractions**; duplication is OK until a pattern is clear
- **No unnecessary validation** inside the app; trust internal contracts

### Performance Priorities
- Cold start: < 2 seconds (Fire TV Stick Lite, cache clear enabled)
- Channel change: < 2 seconds
- 10,000+ channels: virtualised, paged lists
- EPG: 7 days × 5,000 channels with incremental updates
- Memory: parse M3U/XMLTV in chunks, never fully in memory

## Security & Privacy
- PIN stored as salted SHA-256 hash
- Recovery code and security answer also hashed
- Playlist URLs + credentials in encrypted SharedPreferences
- No personal data collection, no analytics (opt-in only in future)
- No built-in channel providers; user-supplied sources only

## Testing & Deployment
- **Matrix**: Fire TV Stick 4K, Fire TV Stick Lite, Android TV box, Samsung TV, Sony TV, 2 phone sizes
- **Compliance**: Google Play Store and Amazon Fire TV app requirements (check on release)
- **Crash-free sessions**: Target > 99.5%
- **Remote-only UX**: > 95% usability test success for seniors/children

## Future Phases
- **v1.1**: Full EPG grid, reminders with auto-wake, background play, auto-start on TV, profiles
- **v2.0**: Catch-up/timeshift, phone PiP, diagnostics, custom color buttons, multi-device sync

## Quick Start
1. Open the project in Android Studio
2. Sync Gradle
3. Run on target device or emulator
4. The app initializes with a loading screen and empty playlist state
5. Add a playlist via the UI or settings

## Notes for Future Development
- Code is uncompiled until CI runs; see PROJECT_STATUS.md. Flavors: `foss` (default iptv-org India playlist) and `store` (no playlist) — never add a default URL to `store`
- Device detection is runtime-based; single APK supports all targets
- Cache policy is strict: full clear on exit (except settings/playlists) to comply with store guidelines
- Reminders use AlarmManager + WorkManager for reliability
- Test D-pad navigation thoroughly on real TV hardware before each release
