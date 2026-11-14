# Store listing: Freetia TV (`store` flavor)

Source of truth for the text is `fastlane/metadata/android/en-US/`. This file mirrors it and adds store-specific fields. If you change copy, change the fastlane file first and then copy it here. Store copy follows BRAND_KIT §8: never name channels, providers or playlist sources.

## Text

| Field | Play limit | Amazon limit | Value | Length |
|---|---|---|---|---|
| Title | 30 chars | brief | `Freetia TV` (fastlane `title.txt`) | 10 |
| Short description | 80 chars | 1,200 chars (Fire TV shows the first 200) | `Free, ad-free player for the M3U playlists you already have` (fastlane `short_description.txt`) | 59 |
| Full description | 4,000 chars | 4,000 chars | fastlane `full_description.txt`, reproduced below | 2454 chars |
| Release notes (v1.0.0, code 1) | 500 chars | n/a | fastlane `changelogs/1.txt` | 416 chars |
| Category | Video Players & Editors | Video / Media players (pick the closest Fire TV category the console offers) | | |
| Contact email | required | required | **OWNER: fill in** (the same address as `CONTACT_EMAIL` in `docs/privacy.html`) | |
| Website | optional | optional | `https://freetia-tv.github.io/freetia-tv/` | |
| Privacy policy URL | required | required | `https://freetia-tv.github.io/freetia-tv/privacy.html` | |

### Full description (copy of fastlane `full_description.txt`)


```
Freetia TV is a free, ad-free player for M3U and M3U8 playlists that you already have. Add your playlist link and watch with your remote, just like a regular TV. It also works with touch on phones and tablets.

Freetia TV does not include any channels. You add your own playlists.

Your playlists
• Add several playlists, put them in order and choose which one opens first
• Pick what happens at startup, and keep playlists up to date automatically
• Choose your region so the list shows what matters to you
• Favourites, your own groups and recently watched channels
• Search by name, or speak your search with voice search
• A health check marks streams that no longer work

Watching
• Full-screen player: Up/Down changes channel, number keys jump straight to a channel, Back returns
• Picks up where you left off with your last channel
• Choose the audio track and turn captions on, with your own caption style
• See the stream's resolution and get a notice when quality changes
• Buffer presets for slow or fast connections
• Sleep timer
• Keep listening in the background, and picture-in-picture on phones
• HLS, DASH and MPEG-TS streams

Programme guide
• Full guide grid when your playlist or guide link includes programme information
• Search the guide
• Reminders for programmes you choose
• Catch-up for channels that support it

Family controls
• Child Safe Mode, protected by a PIN
• A recovery code and a security question in case you forget the PIN
• Lock single channels or whole categories
• Profiles, including a child profile

Easy with a remote
• Every screen shows which buttons to press
• Start Freetia TV automatically when your TV turns on (optional, off until you turn it on)
• Large text options, a high contrast theme and screen reader support
• Settings for playback, display, startup, sleep, reminders, playlists and cache

Backup and move
• Export your settings and playlists to a file
• Protect the file with a passphrase to include playlist links
• Move to another device with a file, a QR code or a typed code
• Diagnostics screen to share with support when you choose

Private
• No account
• No ads
• No analytics or tracking
• Playlist links stay on your device, are stored encrypted and are never backed up to the cloud

Content notice
Freetia TV is a media player only. It does not provide, host or sell any content. You are responsible for making sure you have the right to watch the content in the playlists you add.
```

Every feature bullet above is built in v1.0 (see `plan/V1_BUILD_PLAN.md`). Before each submission, still check that each one works in the `store` build you upload. Google and Amazon both reject listings that describe missing features (Play policy "Misrepresentation"; TV quality criterion TV-G3).

### Keywords

Play has no keyword field. Its search uses the title and descriptions, and keyword stuffing breaks policy. Amazon takes an optional comma-separated keyword list:

```
m3u, m3u8, iptv player, playlist player, live tv player, hls player, fire tv player, android tv player, remote control
```

Don't add channel, network, provider or sports-league names, and don't use "free TV channels" or "free movies". They suggest bundled content and attract IPTV takedowns.

## Required assets (exact sizes)

Generated files are in `store/assets/` (rendered from the vector path data in `app/src/main/res/drawable/`; see "How the assets were made" below).

### Google Play

| Asset | Spec | Required | File |
|---|---|---|---|
| App icon | 512×512, 32-bit PNG, ≤ 1 MB, full bleed (Play applies the mask) | Yes | `assets/icon-512.png` |
| Feature graphic | 1024×500, PNG or JPEG, no alpha | Yes | `assets/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, 16:9 or 9:16, each side 320–3840 px | Yes (the app has a phone form factor) | **To capture** on a phone emulator |
| TV banner | 1280×720 PNG/JPEG | Yes, for the TV form factor | `assets/tv-banner-1280x720.png` |
| TV screenshots | 1–8, 1920×1080 (16:9), unaltered (TV-G4) | Yes, for the TV form factor | Drafts in `assets/screenshots/`; check against the list below before upload |
| 7" and 10" tablet screenshots | 16:9 or 9:16 | Optional (recommended) | To capture |

### Amazon Appstore (Fire TV)

| Asset | Spec | Required | File |
|---|---|---|---|
| Small icon | 114×114 PNG (transparency allowed) | Yes | `assets/icon-114.png` |
| Large icon | 512×512 PNG (transparency allowed) | Yes | `assets/icon-512.png` |
| Fire TV app icon | 1280×720 PNG, **no transparency** | Yes | `assets/tv-banner-1280x720.png` (opaque) |
| Fire TV background image | 1920×1080 JPG or 24-bit PNG, no transparency | Yes | `assets/firetv-background-1920x1080.png` |
| Fire TV screenshots | 3–10, 1920×1080 landscape, JPG or 24-bit PNG, no transparency | Yes | Drafts in `assets/screenshots/` (RGBA: re-encode as 24-bit RGB before upload) |
| Tablet screenshots | 3–10, e.g. 1920×1080 | Console may still ask for them; reuse the Fire TV shots | Reuse |
| Promotional image | 1024×500 | Optional | `assets/feature-graphic-1024x500.png` |

### TV screenshots to capture (store flavor, 1920×1080)

Capture these from the **store** build (`com.freetia.tv`; the flavors share the same application ID, so uninstall the foss build first), on the `Television_4K` AVD set to 1920×1080, with `adb exec-out screencap -p > shot.png`:

1. Empty state on first launch (shows that no content is bundled).
2. Add playlist screen with a URL being typed using the remote.
3. Channel list after adding a playlist the way a user would.
4. Channel list with a category focused.
5. Full-screen player with the info overlay.
6. Programme guide grid.
7. Settings → Safety (Child Safe Mode and PIN).

Screenshot rules: no channel logos or names of well-known commercial broadcasters in any frame (use a public, freely licensed test playlist, or crop and blur logos you don't have rights to); no device frames; no added text that claims features the app doesn't have.

## How the assets were made

`store/assets/render.js` builds every PNG with resvg (Node) from the exact path data in `ic_launcher_foreground.xml`, `ic_launcher_background.xml` and `ic_brand_mark.xml`, the colour tokens in BRAND_KIT §3, and Roboto Bold for the wordmark (letter-spacing −0.5%). To regenerate:

```powershell
cd <a scratch folder>
npm init -y; npm install @resvg/resvg-js@2.6.2 @expo-google-fonts/roboto@0.2.3
Copy-Item D:\FreetiaTV\store\assets\render.js .
node render.js D:\FreetiaTV\store\assets D:\FreetiaTV\app\src\main\res\drawable-nodpi\tv_banner_wordmark.png
```

The feature graphic tagline "Your playlists on the big screen" is new copy. Have `brand-ux-reviewer` check it, along with all the generated assets (C8), before upload.
