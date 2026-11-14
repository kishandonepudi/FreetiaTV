# Freetia TV status

v1.0 scope is the full spec, and all of it is built (phases 1 to 3; see plan/V1_BUILD_PLAN.md for the per-feature API notes). Final emulator QA is in progress. Owner tasks remain before release.

Earlier milestone: first local build passed on 14 Nov 2026 (JDK 17, Android SDK, Gradle wrapper 8.10.2) with `foss` and `store` debug APKs, unit tests and lint (0 errors), smoke-tested on the Android TV 16 emulator (4K). Not yet tried on real Fire TV or Android TV hardware.
The project must live at an ASCII-only path (for example `D:FreetiaTV`): Android build tools refuse non-ASCII paths on Windows.

## Build flavors
| Flavor | Where it ships | Default playlist |
|---|---|---|
| `foss` | GitHub Releases, sideload (Fire TV via Downloader), Android TV | iptv-org India (`https://iptv-org.github.io/iptv/countries/in.m3u`), added once on first run |
| `store` | Google Play, Amazon Appstore | none (store IPTV policies reject apps that bundle stream sources) |

## Built
Playlists (priority, startup rules, auto-update), regions, favourites, groups, recents, search and voice search, health check, audio tracks, captions with style, resolution and auto-quality toasts, buffer presets, Child Safe Mode with PIN, recovery code and security question, channel locks, full EPG grid, guide search, reminders, catch-up, sleep timer, background play, phone PiP, auto-start on TV (opt-in), cache policy, all settings, export/import (file, passphrase-encrypted, QR or typed code), diagnostics, profiles, About screen, accessibility.

## In progress
- Final emulator QA walkthrough, screenshots and the review fix loop.

## Owner tasks remaining
- Replace `CONTACT_EMAIL` and the repo link in `docs/*.html`; replace `OWNER`/`REPO` in the store URLs and in `PRIVACY_URL` / `CONTENT_NOTICE_URL` in `ui/settings/AboutScreen.kt`; enable GitHub Pages.
- Provide a legal reviewer test playlist URL for the store submission notes.
- Create the upload keystore, run the release gate, trademark search.
- Complete the Play declarations (media playback, exact alarm, full-screen intent) and the Data safety form; see store/PLAY_SUBMISSION.md and store/AMAZON_SUBMISSION.md.
- Test on real Fire TV and Android TV hardware.
