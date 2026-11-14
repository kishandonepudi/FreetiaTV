# Amazon Appstore submission checklist (Freetia TV, `store` flavor, Fire TV)

Last checked against Amazon's developer docs on 14 Nov 2026. Re-check before each submission.

Upload only the `store` build. Never upload `foss`, because it adds a default playlist on first run.

## 0. Before you start (owner only)

- [ ] Amazon Developer account at https://developer.amazon.com. Amazon has historically charged no registration fee. I couldn't confirm the current fee from an official page, so check it when you register. Registration needs a business or legal name, address and tax interview, plus payout details even for free apps. Check the console for current requirements.
- [ ] Privacy policy and content-notice pages live on GitHub Pages, with the placeholders filled in. Owner placeholders: `CONTACT_EMAIL` and the GitHub repo link in `docs/*.html`; `OWNER`/`REPO` in the store URLs and in `PRIVACY_URL` and `CONTENT_NOTICE_URL` in `app/src/main/kotlin/com/freetia/tv/ui/settings/AboutScreen.kt`; the `<OWNER: test URL>` reviewer playlist.
- [ ] Trademark search done (BRAND_KIT §2.3).
- [ ] `/release-gate` passed; release keystore created (RELEASING.md). Amazon re-signs apps with its own key by default, but you still upload a build signed with **your** key and must keep signing every update with the same key.

## 1. APK or AAB?

Amazon accepts both. "App bundles are optional. You can continue submitting apps using the existing APK format." Amazon turns an AAB into device APKs with its own bundletool (1.11).

**Recommendation: upload the signed `store` APK** (`FreetiaTV-vX.Y.Z-store.apk`, built by the Release workflow with `assembleStoreRelease`), as RELEASING.md already says:
- What you test on a Fire TV Stick is byte-for-byte what Amazon ships.
- The App Submission API does not support AABs, if you ever automate uploads.
- The app has no dynamic feature or asset packs, so an AAB gains nothing on Amazon. (Amazon supports install-time delivery only anyway.)

If you upload an AAB instead, run `bundletool validate --bundle=app-store-release.aab` first, as Amazon recommends.

Notes:
- The **Amazon Appstore for Android phones/tablets (non-Fire) is discontinued**. Target Fire TV (and optionally Fire tablets) only.
- **Vega OS** Fire TV devices (Amazon names the Fire TV Stick 4K Select; newer models may follow) don't run Android APKs; they need a separate VPKG app. Freetia TV targets Fire OS devices only. Deselect Vega OS devices if the console lists them.

## 2. Compatibility facts (already met in the manifest/build)

| Amazon requirement | Freetia TV `store` |
|---|---|
| `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`: Amazon only classifies an app as Fire TV compatible when this is present | Yes |
| `android.software.leanback` not required (Fire TV doesn't need it; `required="false"` keeps phones and Fire tablets) | Yes, `required="false"` |
| No other required hardware features (camera, telephony, GPS, etc.) | `wifi` and `telephony` are `required="false"`; nothing else declared |
| `LEANBACK_LAUNCHER` intent filter + `android:banner` so the app shows in the Fire TV launcher row | Yes; the banner now includes the app name |
| Minimum targetSdkVersion per Fire OS: Fire OS 6 → 25, Fire OS 7 → 28, Fire OS 8 → 30, Fire OS 14 → 34, Fire OS 16 → 36 | targetSdk **36** meets every listed Fire OS version |
| minSdkVersion low enough for the devices you target: Fire OS 6 = API 25, Fire OS 7 = API 28, Fire OS 8 = API 30 | minSdk **26**. This **excludes Fire OS 6 devices** (API 25; older Fire TV models still on Fire OS 6; see Amazon's Fire TV device specifications for the exact list). All Fire OS 7, 8 and later devices are fine. To cover Fire OS 6 you'd have to drop minSdk to 25. It's a product decision; leave it unless you need those devices. |
| No Google Play Services dependency (Fire OS has none) | Yes. Nothing uses GMS |
| D-pad and media keys (Fire TV remote: Back, Home, Menu, Play/Pause, Rewind/FF) | Supported in code; verify on hardware |

## 3. Developer Console steps

1. **Apps & Services → My Apps → Add New App → Android**.
2. **General information**: title `Freetia TV`, app SKU (any unique string you choose, e.g. `freetia-tv-store`), category **Video / Media players** (closest Fire TV category offered), customer support email (owner), website and privacy policy URLs.
3. **Availability & pricing**: Free. Countries: all, or the ones where you can respond to legal notices. Release date: as soon as possible after approval.
4. **Upload your app file**: the signed `store` APK.
   - Amazon reads the manifest and lists compatible devices.
   - **Device support**: select the Fire TV devices you have tested (Fire TV Stick Lite, Fire TV Stick 4K / 4K Max, Fire TV Cube, Fire TV Edition smart TVs, all Fire OS 7+). Deselect devices you haven't tested.
   - Fire tablets are optional. They're Fire OS (API 28/30) and the app runs there with touch.
   - Deselect any Vega OS devices.
   - Language support: English.
   - **Export compliance**: confirm (the app has no non-standard encryption; it uses Android's built-in AES/TLS only).
   - **Uses Amazon DRM?** **No**. Amazon DRM is for paid apps; it isn't needed.
5. **Appstore details**:
   - Short description, long description and keywords from `LISTING.md`. Fire TV shows only the first 200 characters of the short description.
   - Product feature bullets (3–5): take them from the full description's section headings.
6. **Images & multimedia** (all PNG files are 24-bit RGB, no alpha):
   | Asset | Size | File |
   |---|---|---|
   | Small icon | 114×114 | `store/assets/icon-114.png` |
   | Large icon | 512×512 | `store/assets/icon-512.png` |
   | Fire TV app icon (banner, no transparency) | 1280×720 | `store/assets/tv-banner-1280x720.png` |
   | Fire TV background image (no transparency) | 1920×1080 | `store/assets/firetv-background-1920x1080.png` |
   | Fire TV screenshots (3–10) | 1920×1080 | **Still to capture** (shot list in `LISTING.md`) |
   | Tablet screenshots (3–10, if the console asks) | 1920×1080 | Reuse the Fire TV shots |
   | Promotional image (optional) | 1024×500 | `store/assets/feature-graphic-1024x500.png` |
   | Video (optional) | — | none |
7. **Content rating** (Amazon's own questionnaire, not IARC):
   - Violence, sex, nudity, profanity, drugs, alcohol, tobacco, gambling, mature themes in **content provided by the app**: **None** (the `store` build bundles no content).
   - "Does the app allow access to unrestricted web content / user-provided content?" **Yes**. Users can play any stream URL. Answer honestly, and expect a higher rating or a "Guidance Suggested" type label.
   - Parental controls: the app has PIN-protected parental controls (Child Safe Mode, channel and category locks); say so in any free-text field.
   - Data: no data is collected or shared. The only share is the user-initiated diagnostics report, with no links or personal data, and the app has no server.
   - Collects personal information? **No**. Advertising? **No**. Location-based? **No**. In-app purchases? **No**. Account creation? **No**.
   - Intended for children / "Is this app directed at children under 13?" **No**. COPPA and Amazon Kids requirements would apply otherwise. The app plays arbitrary streams, so it isn't child-directed.
8. **Testing instructions for Amazon's reviewers**: same text as the Play "App access" note in `PLAY_SUBMISSION.md`. No login needed: launch → **Add playlist** → type the test link with the remote (on-screen keyboard) → **Add** → pick a channel. Also tell reviewers: open the TV guide with the yellow colour button, and go to **Settings → Safety → Child Safe Mode** (turn it on and set a PIN) to see the parental controls.
9. Submit. Amazon tests on real Fire TV hardware, usually within a few days. Common Fire TV rejection reasons:
   - The app doesn't work with the remote alone.
   - Back doesn't exit from the home screen.
   - Text is cut off by overscan.
   - Playback has no on-screen controls.
   - The app crashes when there's no network.

### Permissions to explain if Amazon asks
- Foreground service, media playback: background audio with a notification.
- Exact alarm: user-set programme reminders on time (optional).
- Full-screen intent: a reminder can wake the screen (optional).
- Notifications: reminders and background play. Boot completed: auto-start on TV, off by default.

## 4. Content policy positioning (IPTV)

Amazon's content policy rejects apps that facilitate piracy or link to unlicensed content. Freetia TV `store`:
- ships no playlist, channel list or stream URL (release-gate C4);
- says in the description that it contains no channels and that users are responsible for their sources;
- links to the content notice page (`https://freetia-tv.github.io/freetia-tv/content-notice.html`) from the listing website field;
- uses no channel or broadcaster names or logos in its listing or screenshots.

If Amazon asks for proof of content rights, answer that the app is a general-purpose M3U player with no bundled sources (the same position as VLC, Kodi or other playlist players in the Appstore), and point to the content notice.

## 5. After approval

- Live App Testing (optional) lets testers install the build from the Appstore before production.
- Report significant changes through the Developer Console when you update (Amazon added this requirement on 17 Jul 2026).
- Updates: same package `com.freetia.tv`, higher `versionCode`, signed with the same key.

## Sources
- Device filtering, Fire OS targetSdk minimums, touchscreen rule: https://developer.amazon.com/docs/app-submission/device-filtering-and-compatibility.html
- Supported manifest filters: https://developer.amazon.com/docs/app-submission/supported-filters-on-the-amazon-appstore.html
- App bundles on Amazon: https://developer.amazon.com/docs/app-submission/app-bundles.html
- Appstore details and image specs: https://developer.amazon.com/docs/app-submission/appstore-details.html
- Release notes (mobile Appstore discontinued, Vega VPKG, significant-change reporting): https://developer.amazon.com/docs/app-submission/release-notes.html
