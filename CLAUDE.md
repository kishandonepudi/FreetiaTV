# Freetia TV Project Documentation

## Mandatory rules for every agent and developer (read first)
1. **Before any UX/UI, icon, colour, text or copy change:** read `standards/BRAND_KIT.md` and follow it exactly. Run the `brand-ux-reviewer` agent on the change.
2. **Before any code change:** follow `standards/ENGINEERING_STANDARDS.md` (including the memory budget M1–M9 for low-RAM TVs) and `standards/SECURITY_STANDARDS.md`.
3. **Before handing back any change:** run `powershell -ExecutionPolicy Bypass -File scripts/quality-gate.ps1 -Fast` and report the result.
4. **Before building any release APK/AAB, opening a PR or tagging:** run `/release-gate`. It runs the full script plus the `brand-ux-reviewer`, `code-security-reviewer` and `performance-reviewer` agents in parallel. The `.claude/hooks/apk-gate.js` hook blocks release builds until a fresh passing checkpoint exists.
5. **For UI changes, also do a D-pad pass on the TV emulator** (`Television_4K` AVD in `D:\Android\avd`) and check screenshots before saying the change is done.
6. **Every Freetia app must show the brand logo** (`ic_brand_mark`, launcher and banner assets) in the placements listed in BRAND_KIT §2.2.

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
- `ui/theme/Type.kt` - Typography (TV scale for 960x540dp: 16sp body, 12sp minimum; text sizes set per screen)

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
- EPG: 7 days Ã— 5,000 channels with incremental updates
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
- v1.0 scope is the full spec and every feature is built; see plan/V1_BUILD_PLAN.md for the per-feature API notes
- Builds locally with `gradlew.bat` (see PROJECT_STATUS.md); keep the repo at an ASCII-only path. Flavors: `foss` (default iptv-org India playlist) and `store` (no playlist) â€” never add a default URL to `store`
- Device detection is runtime-based; single APK supports all targets
- Cache policy is strict: full clear on exit (except settings/playlists) to comply with store guidelines
- Reminders use AlarmManager + WorkManager for reliability
- Test D-pad navigation thoroughly on real TV hardware before each release
