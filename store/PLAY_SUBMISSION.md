# Google Play Console submission checklist (Freetia TV, `store` flavor)

Last checked against Google's published requirements on 14 Nov 2026. Re-check the linked policy pages before every submission, because they change.

Upload only the `store` build. The `foss` build adds a default playlist on first run, and Play's IP policy treats bundled or pointed-to stream sources as facilitating infringement.

## 0. Before you open the console (owner only)

- [ ] Google Play developer account: one-time US$25 fee, identity verification, and (for a new personal account) the closed-testing requirement. At the time of writing, new personal accounts must run a closed test with at least 12 testers for 14 continuous days before production access. Check the current rule in the console.
- [ ] Trademark search for "Freetia" and the mark (BRAND_KIT §2.3), recorded in `standards/TRADEMARK_LOG.md`.
- [ ] Fill in the placeholders in `docs/*.html` (`CONTACT_EMAIL`, `freetia-tv/freetia-tv`), enable GitHub Pages, and check that `https://freetia-tv.github.io/freetia-tv/privacy.html` and `content-notice.html` load.
- [ ] Owner placeholders still to replace: `CONTACT_EMAIL` and the GitHub repo link in `docs/*.html`; `OWNER`/`REPO` in the store URLs and in `PRIVACY_URL` and `CONTENT_NOTICE_URL` in `app/src/main/kotlin/com/freetia/tv/ui/settings/AboutScreen.kt` (the About screen opens these, so they must match the live Pages URLs); the `<OWNER: test URL>` reviewer playlist; the upload keystore; the trademark search.
- [ ] Create the upload keystore (RELEASING.md §1). Keep it offline and never commit it.
- [ ] Run `/release-gate` (standards/RELEASE_GATE.md). The `apk-gate` hook blocks release builds until a passing checkpoint exists.
- [ ] Build: `gradlew bundleStoreRelease` (or tag a release so CI builds `FreetiaTV-vX.Y.Z-store.aab`).

## 1. Create the app

- App name: `Freetia TV`. Default language: English (United States) – en-US.
- App or game: **App**. Free or paid: **Free**. (Free can never be changed to paid later.)
- Accept the Developer Program Policies and US export laws declarations.

## 2. Play App Signing and upload

- [ ] Use **Play App Signing** (required for AABs). Let Google generate and hold the app signing key. Your keystore becomes the **upload key**.
- [ ] Upload `app/build/outputs/bundle/storeRelease/app-store-release.aab`. Play only accepts signed bundles. If you built without a signing config, the bundle is unsigned and Play rejects it, so sign it with your upload key (CI does this through the `FREETIA_KEYSTORE_*` env vars).
- [ ] Keep the upload key certificate SHA-1/SHA-256 shown in **Setup → App signing** with your records. If the upload key is lost, request an upload key reset there.
- Build facts the console will check (already met): `targetSdk 36` (required for new apps and updates from 31 Aug 2026), `minSdk 26`, `versionCode 1`, `versionName 1.0.0`, R8 minify and resource shrinking on release, 64-bit and 16 KB page-size compliant (the app ships no native code of its own; if a dependency adds `.so` files, check the "16 KB" warning in the bundle explorer).

## 3. App content (Policy → App content)

### Privacy policy
`https://freetia-tv.github.io/freetia-tv/privacy.html`.

### Ads
**No, my app does not contain ads.**

### App access
Choose **All or some functionality is restricted** and add these instructions. The app needs no login, but it needs a playlist to show anything, and reviewers must know how to get past the empty state:

> Freetia TV is a player for M3U/M3U8 playlists that the user supplies. It ships with no channels and needs no account or login.
> To test: launch the app → press OK on **Add playlist** → type this public test playlist link: `<OWNER: test URL>` → choose **Add** → pick a channel. Press OK to play, Up/Down to change channel, Back to return. The app works fully with a D-pad remote.
> Programme guide: if the test playlist includes a guide link, open the TV guide with the yellow colour button (default) to see the full grid; otherwise add the guide link under Settings → Playlists, TV guide and refresh.
> Parental controls: open **Settings → Safety → Child Safe Mode** and turn it on. The app asks you to set a PIN (4 to 6 digits), then shows a recovery code and a security question. Channel locks are on the same page.

Owner: put a stable, legally clean test URL in this note. A playlist you host yourself on GitHub Pages that points to public-domain or self-owned streams is the safest choice. Don't give reviewers a playlist of commercial channels, and don't put a URL into the app.

### Content rating (IARC questionnaire)
- Email: the developer contact email.
- Category: **Entertainment** (streaming media). If the console's wording changes, choose the category for streaming video apps, not "Game".
- Violence, fear, sexuality, language, controlled substances, gambling, crude humour in content **provided by the app**: **No** to all. The store build bundles no content.
- Does the app let users interact or exchange content with other users (chat, sharing)? **No.**
- Does the app share the user's current physical location with other users? **No.**
- Does the app allow users to purchase digital goods? **No.**
- Does the app give access to **unrestricted content from the internet** (the user can load any stream URL)? Answer **Yes**. This is the honest answer for an IPTV player (RELEASING.md, SECURITY_STANDARDS §3). The rating may come back higher than "Everyone" and may carry an interactive-element notice. That is expected; don't answer No to get a lower rating.
- Does the app contain a web browser or search engine? **No.** (The app has a search box for channels and programmes in the user's own playlists; it does not search the web.)
- Parental controls: mention in any free-text field that the app has **PIN-protected parental controls** (Child Safe Mode hides adult channels, and channel and category locks need the PIN). The rating still reflects the unrestricted-content answer above.

### Target audience and content
- Target age groups: **18 and over** only.
  - Reason: the app plays arbitrary user-supplied streams, so it can't meet the Families policy if it targets children.
- "Could your store listing unintentionally appeal to children?" **No.** Keep the listing, icon and screenshots free of cartoon characters and kids' channels.
- Not in "Designed for Families" / Teacher Approved.

### News app
**No.**

### Data safety
Derived from the code on 14 Nov 2026 (`app/src/main`, dependencies in `app/build.gradle.kts`):
- No backend, account, analytics, crash reporting or ads SDK. Dependencies are AndroidX, Media3/ExoPlayer, OkHttp, Coil and Room. None of them sends telemetry.
- Network requests go only to addresses the user enters or that their playlists contain: playlist URLs, stream URLs and channel-logo URLs (`PlaylistSyncer`, `PlaylistRefreshWorker`, Media3, Coil). The developer never receives this data. It goes directly, at the user's request, to servers the user chose (the same model as a browser).
- The only outgoing share is user-initiated: "Send report" in Diagnostics and export files use the system share or file picker, and the report holds no links or personal data. There is no server to receive anything.
- PIN, recovery code and security answer are stored as salted hashes, and playlist and guide links are encrypted on the device.
- Playlists, channels and the last-watched channel stay on the device. `allowBackup="false"` and the data extraction rules exclude everything, so nothing goes to Google backup or device transfer.

Form answers:
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| (Shown only if Yes) Is all user data encrypted in transit? | n/a. Note for later: you could **not** answer Yes, because cleartext HTTP is allowed for user-supplied streams (`network_security_config.xml`) |
| Do you provide a way for users to request that their data be deleted? | n/a (nothing collected). Local data is deleted by clearing the app's data or uninstalling |
| Independent security review | No |

Google requires the form even when nothing is collected. Re-do it if you ever add crash reporting, analytics, a sync backend or any SDK.

### Permission declarations
- **Foreground service, media playback** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`): `PlaybackService` keeps audio playing, with a media notification (play, pause, stop), when the user leaves the app and a playlist's "Allow background play" is on. In **App content → Foreground service permissions**, declare **Media playback** with a short video: play a channel, press Home, show the notification, then tap it to return. Video is switched off in the background; only audio continues. The service is exported only because Media3 binds through its intent-filter; it rejects any controller that is not this app or the system.
- **Exact alarm** (`SCHEDULE_EXACT_ALARM`): justification: the user sets a reminder for a programme and expects it at the chosen minute. It is optional; without it reminders use `setAndAllowWhileIdle` and can be a few minutes late.
- **Full-screen intent** (`USE_FULL_SCREEN_INTENT`): justification: a user-set programme reminder can wake the screen on a TV. It is optional; without it the reminder is an ordinary notification with a Watch action.
- Also declared, no console form: `POST_NOTIFICATIONS` (reminders, background play), `RECEIVE_BOOT_COMPLETED` (auto-start on TV, off by default), `WAKE_LOCK` (audio with the screen off).

### Other declarations
- Government app: No. Financial features: No. Health: No. Account deletion: n/a (no accounts).
- Advertising ID: **No** (the app doesn't use it; no ads or analytics SDK).
- **Exact alarms (`SCHEDULE_EXACT_ALARM`) and full-screen intent (`USE_FULL_SCREEN_INTENT`): declared for TV programme reminders, and the owner must complete both Play Console declarations** (App content → Exact alarms; App content → Full-screen intent). Play limits both to alarm, calendar and calling apps, so expect scrutiny and answer honestly that these are user-set programme reminders. The code degrades without them: without exact-alarm access `ReminderScheduler` uses `setAndAllowWhileIdle` (the reminder can fire a few minutes late and the reminders screen says so), and without full-screen access (`canUseFullScreenIntent()` on Android 14+) the reminder is an ordinary notification whose Watch action opens the channel. **If Play declines either declaration, delete that `<uses-permission>` line from `AndroidManifest.xml`; nothing else changes.**
- Photo-video permissions: not declared.

## 4. Store listing (Grow → Store presence → Main store listing)

Use `store/LISTING.md` (copy from `fastlane/metadata/android/en-US/`).
- [ ] Title, short description and full description.
- [ ] App icon `store/assets/icon-512.png`, feature graphic `store/assets/feature-graphic-1024x500.png`.
- [ ] Phone screenshots (2–8). **Still to capture** (shot list in `LISTING.md`).
- [ ] Category: **Video Players & Editors**. Tags: media player, video player.
- [ ] Contact email (owner) and website (Pages URL).

### IPTV / copyright positioning
Play's Intellectual Property policy bans apps that "encourage or induce infringement" or facilitate "unauthorized streaming", and listing images that promote copyrighted content. It doesn't ban general-purpose players. Freetia TV stays on the right side if you keep to all of these:
- The `store` build ships **no playlist, no channel list and no stream URLs** (enforced by release-gate C4: `DEFAULT_PLAYLIST_URL` is empty in `store`).
- Listing text says "does not include any channels" and has the content notice paragraph (already in the full description).
- No channel names, broadcaster logos, sports leagues or movie art in the title, description, icon, feature graphic or screenshots. Screenshots use a public-domain or self-owned test playlist.
- Never link from the listing or the app to playlist sites, "free TV" lists or the `foss` build. Keep the GitHub Pages home page (which links the foss APK) off the Play listing's website field if you want to be extra safe; linking only `content-notice.html` / `privacy.html` is safer.
- Content notice page: `https://freetia-tv.github.io/freetia-tv/content-notice.html` explains that the app is a player, users are responsible for their sources, and where takedown requests go. It's worth showing from the app's About screen too.
- If you receive a DMCA complaint, answer it through the console with this positioning. Player apps (for example Perfect Player) have been pulled on complaints, so keep these records.

## 5. Android TV form factor

Manifest requirements (TV app quality, Tier 3) already met in the `store` build:
| Criterion | Status |
|---|---|
| TV-ML `ACTION_MAIN` + `LEANBACK_LAUNCHER` | Yes (`MainActivity`) |
| TV-LB 320×180 banner + launcher icon | Yes (`@drawable/tv_banner`, `@mipmap/ic_launcher`) |
| TV-BN banner contains the app name | Yes (wordmark layer `tv_banner_wordmark.png` added 14 Nov 2026) |
| TV-MT `android.hardware.touchscreen` not required | Yes, plus `android.software.leanback` `required="false"` so phones still get the app |
| TV-LO landscape, no portrait lock | Yes (no `screenOrientation` in the manifest or code) |
| TV-PS minSdk ≤ 31 | Yes (26) |
| TV-G1 App Bundle | Yes (`bundleStoreRelease`) |
| TV-G6 64-bit + 16 KB pages | Yes (no first-party native code) |
| TV-DP / TV-DM / TV-DB D-pad, no Menu key, Back exits | Needs the manual D-pad pass (C12) on the final build |
| TV-G4 unaltered TV screenshot | **Still to capture** |

Steps:
- [ ] **Test and release → Advanced settings → Form factors → + Add form factor → Android TV.** Opt in and accept the TV terms.
- [ ] Upload the TV banner `store/assets/tv-banner-1280x720.png` and at least one 1920×1080 TV screenshot (Store presence → store listing → Android TV section).
- [ ] Release to a track that includes TV. The first TV release gets a manual TV quality review (usually days). Failures are emailed to the developer account address. The phone release isn't blocked as long as the TV artifact is the one at fault.
- [ ] Android TV has its own target API level floor (API 34). The app's API 36 meets both the phone and TV floors.

## 6. Testing tracks and rollout

1. **Internal testing**: upload the AAB, add yourself, and install from Play on a phone and an Android TV device. Check that the TV listing appears on the TV Play Store.
2. **Closed testing**: the 12-tester / 14-day requirement for new personal accounts (if it applies).
3. **Production**: countries (exclude any where you can't respond to legal notices); staged rollout of 20% → 100%.
4. Watch Android vitals (crash rate target < 0.5%, ANR) and the policy inbox.

## Sources
- Target API levels: https://support.google.com/googleplay/android-developer/answer/11926878 and https://developer.android.com/google/play/requirements/target-sdk
- TV app quality: https://developer.android.com/docs/quality-guidelines/tv-app-quality
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Intellectual property policy: https://support.google.com/googleplay/android-developer/answer/9888072
- AGP and API level compatibility: https://developer.android.com/build/releases/about-agp
