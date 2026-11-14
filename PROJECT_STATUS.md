# Freetia TV status

Nothing here has been compiled yet. This machine has no JDK or Android SDK, so the first
GitHub Actions run (`.github/workflows/ci.yml`) is the first real build. Expect a round of fixes.

## Build flavors
| Flavor | Where it ships | Default playlist |
|---|---|---|
| `foss` | GitHub Releases, sideload (Fire TV via Downloader), Android TV | iptv-org India (`https://iptv-org.github.io/iptv/countries/in.m3u`), added once on first run |
| `store` | Google Play, Amazon Appstore | none (store IPTV policies reject apps that bundle stream sources) |

## Working end to end (once it builds)
- First run (foss): seeds the India playlist → `PlaylistRefreshWorker` downloads and stream-parses it into Room.
- Startup refresh: only playlists with "Update when app starts"; skipped if updated < 10 min ago; cached channels stay on failure.
- Refresh keeps channel IDs stable (matched by stream URL), so favourites and locks survive.
- Channel list (D-pad, logos with a 50 MB Coil disk cache) → full-screen player.
- Player: ↑↓ channel, 0–9 jump (2 s timeout), OK info banner, 3 auto-retries then a clear message, resume last channel.
- Back from list: toast, then double-Back within 2 s does a full exit with cache clear.
- Child Safe Mode setting filters adult channels from the list and channel up/down.

## Not built yet
- Settings, add/edit playlist screens, playlist switcher, "All" merged view, search, favourites UI.
- PIN / channel lock UI (hashing is in `EncryptionManager`), EPG import (parser exists), reminders, sleep-timer UI.
- Audio/caption/resolution menus, quality-change toasts, background play wiring (`PlaybackService` exists, UI uses an in-activity player).
- Playlist URLs are in Room, not encrypted storage (spec §5).
- Channel list loads the whole playlist into memory; needs Paging before 10,000+ channel playlists.
