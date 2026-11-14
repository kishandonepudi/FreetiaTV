# Releasing Freetia TV

## Builds

| Flavor | Where it goes | Default playlist |
|---|---|---|
| `foss` | GitHub Releases, sideload (phones, Android TV, Fire TV via Downloader) | Adds the public iptv-org India list on first run |
| `store` | Google Play, Amazon Appstore | None |

Only ever upload the `store` build to an app store. Store IPTV policies reject or remove apps that bundle or point to stream sources, even public ones; the `foss` build does exactly that.

## One-time setup

### 1. Create a release keystore

```bash
keytool -genkeypair -v -keystore freetia-release.jks -alias freetia \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` file and its passwords somewhere safe and offline. If you lose them you cannot ship updates to the same app listing. Never commit it (`.gitignore` excludes `*.jks`).

### 2. Add GitHub secrets

Encode the keystore:

```bash
base64 -w0 freetia-release.jks > keystore.b64        # Linux / Git Bash
# PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("freetia-release.jks")) > keystore.b64
```

In the repo, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `freetia` (or the alias you chose) |
| `KEY_PASSWORD` | Key password |

Delete `keystore.b64` afterwards.

### 3. Enable GitHub Pages

Go to **Settings → Pages → Source: GitHub Actions**. The `Pages` workflow publishes `docs/` on every push to `main` that changes it. Your privacy policy URL is then `https://freetia-tv.github.io/freetia-tv/privacy.html`.

Replace the placeholders `freetia-tv/freetia-tv` and `CONTACT_EMAIL` in `docs/*.html` first.

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag and push:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

4. The `Release` workflow builds the signed `foss` APK and the `store` AAB and APK, writes `SHA256SUMS.txt`, and publishes a GitHub Release.

## Google Play Console checklist

Full step-by-step guides with form answers: `store/PLAY_SUBMISSION.md`, `store/AMAZON_SUBMISSION.md` and `store/LISTING.md`. Store graphics are in `store/assets/`.


- [ ] Upload `FreetiaTV-vX.Y.Z-store.aab` only. Use Play App Signing.
- [ ] Privacy policy URL: the Pages `privacy.html`.
- [ ] Data safety form: no data collected, no data shared.
- [ ] Content rating (IARC) questionnaire. The app shows user-supplied content, so answer honestly about unrestricted user content.
- [ ] Ads: "No ads".
- [ ] Store listing from `fastlane/metadata/android/en-US/`. Do not name channels, providers or playlist sources.
- [ ] Android TV: add the TV form factor, upload a 1280x720 TV banner and TV screenshots. Requirements already met in the manifest: `LEANBACK_LAUNCHER` activity, `android:banner` (320x180 in-app), `android.software.leanback` and `android.hardware.touchscreen` set to `required="false"`. Expect a manual TV quality review (D-pad only, no touch-only UI).
- [ ] Re-check Google Play policy on IPTV and media players before each submission. It changes.

## Amazon Appstore checklist

- [ ] Upload `FreetiaTV-vX.Y.Z-store.apk` (Amazon takes APKs).
- [ ] Device support: select the Fire TV devices you have tested (Fire TV Stick Lite, Stick 4K, and others), plus Fire tablets if wanted.
- [ ] Privacy policy URL: the Pages `privacy.html`.
- [ ] Content rating questionnaire. Mature content: none bundled.
- [ ] Icons 512x512 and 114x114, TV banner 1280x720, at least 3 Fire TV screenshots at 1920x1080.
- [ ] Re-check Amazon's content policy on streaming and IPTV apps before submission.

## Sideload (foss) users

Point them at the Pages home page. It has Fire TV (Downloader), Android TV and phone install steps and links to the latest release.
