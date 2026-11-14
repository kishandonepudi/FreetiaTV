# Claude history for Freetia TV

Short versions of every Claude Code session on this project, so work can continue on another machine. Each `.md` file keeps the prompts and Claude's replies. Tool calls, file contents and system context are removed, and email addresses are redacted.

| File | When (UTC) | What happened |
|---|---|---|
| `2026-11-13_008372b8.md` | 13 Nov 19:57 | First planning session for the spec (then called "TuneTV") |
| `2026-11-13_68138ec1.md` | 13 Nov 19:59 | Second planning session |
| `2026-11-13_9d4e8e55.md` | 13 Nov 20:08–20:36 | Main build: project scaffold, Room, parsers, player, flavors, CI/Release/Pages workflows, docs, rename to Freetia TV |
| `2026-11-13_275f491b.md` | 13 Nov 20:39 | Review of what's left before production; this history folder; local testing setup |

The first three sessions ran when the folder was `D:\M3U-IPTV-Free`. Claude keeps the raw transcripts at `%USERPROFILE%\.claude\projects\<folder-name>\*.jsonl`. They aren't copied here because they include account IDs and local context.

## Key decisions
- **Name:** "Freetia TV" (it was "TuneTV"). Launcher label "Freetia", package `com.freetia.tv`, GitHub org `freetia-tv`. Repo `freetia-tv/freetia-tv` is assumed. Domain `freetia.tv` or `freetia.app` is undecided.
- **Two build flavors:**
  - `foss` adds the iptv-org India playlist (`https://iptv-org.github.io/iptv/countries/in.m3u`) on first run. It goes only to GitHub Releases and sideloading.
  - `store` ships with no playlist. It goes to Google Play and the Amazon Appstore.
  - Never add a default URL to `store` and never upload `foss` to a store. Store IPTV policies reject apps that bundle stream sources.
- **Hosting is GitHub:**
  - CI runs on every push to `main`.
  - Pushing a `v*` tag builds a signed release.
  - `docs/` is published on GitHub Pages, including the privacy policy the store listings need.
  - Signing comes from the `FREETIA_*` environment variables, fed by repo secrets.
- **License:** Apache-2.0 (Claude's pick; you can change it).
- **Build checks:** the first machine had no JDK or Android SDK, so GitHub Actions was the only place the code got compiled.

## Status as of 14 Nov 2026
See `PROJECT_STATUS.md` and `RELEASING.md` for the full picture. Still to do:

1. **First build.**
   - Run `git init` and push to GitHub.
   - Add the Gradle wrapper.
   - Get the first build passing. The code has never been compiled.
2. **Release blockers in the code:**
   - `BootReceiver` opens the app on every boot and ignores the `AUTO_START_ON_TV` setting.
   - The app never asks for the `POST_NOTIFICATIONS` permission at runtime.
   - `PlaybackService` is exported and not used: either connect it or remove it and its foreground-service permission.
   - After the first build, commit `app/schemas/`.
3. **Missing screens and storage:**
   - Add/edit playlist screen. Without it the `store` build can't do anything.
   - Settings, search, favourites, PIN/lock, EPG, reminders, sleep timer.
   - Paging for big playlists.
   - Encrypted storage for playlist URLs.
4. **Tests.** There are only 3 tests, all for the M3U parser. Still needed:
   - XMLTV parser
   - `PlaylistSyncer`, including whether channel IDs stay the same after a refresh
   - `EncryptionManager`
   - DAOs
   - Device and UI tests
   - Make CI fail on lint errors.
5. **Hosting.**
   - Replace `CONTACT_EMAIL` and the repo URL in `docs/`.
   - Create the keystore and add the 4 GitHub secrets.
   - Turn on Pages.
   - Make the store graphics.
6. **Docs.** `CLAUDE.md` lists old versions (Kotlin 1.9.20 / API 34). The build uses Kotlin 2.0.21 / SDK 35.

## Local testing setup (Windows)
Download links:
| Tool | Link |
|---|---|
| Android Studio | https://developer.android.com/studio |
| Emulator acceleration (Hypervisor Platform) | https://developer.android.com/studio/run/emulator-acceleration |
| JDK 17 (Temurin) | https://adoptium.net/temurin/releases/?version=17 |
| Gradle 8.10.2 | https://services.gradle.org/distributions/gradle-8.10.2-bin.zip |
| Git | https://git-scm.com/download/win |
| GitHub CLI | https://cli.github.com/ |
| Platform-Tools (adb only) | https://developer.android.com/tools/releases/platform-tools |
| scrcpy | https://github.com/Genymobile/scrcpy/releases |
| VLC | https://www.videolan.org/vlc/ |
| Developer options / USB debugging | https://developer.android.com/studio/debug/dev-options |
| Fire TV: connect ADB | https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html |
| Android TV getting started | https://developer.android.com/training/tv/start |

Install:
1. **Android Studio** (latest stable). It comes with a JDK 17+ and the SDK Manager. In SDK Manager install:
   - SDK Platform **Android 15 (API 35)**
   - Build-Tools 35.x
   - Platform-Tools (`adb`)
   - Emulator
   - Command-line Tools
2. **Emulator system images** (Device Manager):
   - Television 1080p, Android TV or Google TV, API 34, x86_64
   - Phone, API 35
   - Phone, **API 26** (the oldest version the app supports)
   - Turn on **Windows Hypervisor Platform** under Windows Features so the emulators run fast.
3. **JDK 17 (Eclipse Temurin)**, only if you build from the terminal. Set `JAVA_HOME` and `ANDROID_HOME`, or add `sdk.dir` to `local.properties`.
4. **Gradle 8.10.2**, once, to create the wrapper with `gradle wrapper --gradle-version 8.10.2`. After that use `./gradlew`.
5. **Git** (already installed). **GitHub CLI** (`gh`) is optional, for creating the repo and adding secrets.
6. Optional extras:
   - **scrcpy** mirrors a TV or phone screen to the PC.
   - **VLC** checks whether a stream URL plays at all, outside the app.

Real devices:
- **Fire TV Stick (Lite and/or 4K):**
  1. Settings → My Fire TV → Developer options → turn on ADB debugging and Apps from unknown sources. On new firmware, first tap About → device name 7 times to show Developer options.
  2. Run `adb connect <tv-ip>:5555`.
- **Android TV / Google TV:**
  1. Settings → About → tap Build 7 times.
  2. Turn on USB/network debugging.
  3. Run `adb connect <tv-ip>:5555`.
- **Phone:** turn on Developer options → USB debugging.

Build and install:
```bash
./gradlew testFossDebugUnitTest              # unit tests
./gradlew assembleFossDebug                  # debug APK with the India playlist
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
./gradlew installStoreDebug                  # store build (no playlist) on the connected device
```
- [session-05-v1-full-build.md](session-05-v1-full-build.md) — v1.0 full scope built, QA, signed release
