# Security and privacy standards

Version 1.0 (14 Nov 2026). These follow OWASP MASVS v2 and Google Play / Amazon Appstore policy.

## 1. Data we hold
| Data | Where | Protection |
|---|---|---|
| PIN, recovery code, security answer | `EncryptionManager` | Salted SHA-256 hash only. Plaintext is never stored or logged |
| Playlist URLs (may contain tokens) | Encrypted SharedPreferences (planned; currently in Room, which is a known gap) | AES-256 through AndroidX Security |
| Settings | DataStore | Not sensitive |
| Channels, EPG, favourites | Room | Not sensitive; never exported with credentials |

We collect **no personal data**: no analytics, no ads, no account and no crash upload. Any future diagnostics must be opt-in and show the user exactly what is sent.

## 2. Rules
| ID | Rule |
|---|---|
| S1 Secrets | No API keys, tokens, passwords or keystores in the repo. `.gitignore` covers `*.jks`, `*.keystore` and `local.properties`. Signing uses CI secrets only |
| S2 Logging | Never log stream or playlist URLs, query strings, PINs or HTTP bodies. Use `Log.d` only behind `BuildConfig.DEBUG`. Release builds strip debug logs through R8 |
| S3 Network | HTTPS by default. Cleartext is allowed only because user-supplied IPTV streams often use plain HTTP. This is documented in `network_security_config.xml` and must not spread to any app-owned endpoint. No custom TrustManager, and hostname verification stays on |
| S4 Storage | `android:allowBackup="false"`. Sensitive values go only in encrypted preferences. No sensitive data on external storage |
| S5 Components | Every `exported="true"` component needs a reason in the manifest. Exported allowlist (kept in sync with `scripts/quality-gate.ps1` and `.sh`): `.ui.MainActivity`, `.playback.PlaybackService` (a MediaSessionService must be exported for Media3 and the system controllers; `onConnect` rejects untrusted controllers) and `.receiver.BootReceiver` (system broadcast; auto-start is opt-in through `AUTO_START_ON_TV`, default off). Receivers check the intent action |
| S6 Permissions | Request the minimum, at the moment of use, with an explanation. Remove unused permissions before release (`FOREGROUND_SERVICE_MEDIA_PLAYBACK` is for background play and needs the Play declaration; `SCHEDULE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` stay only while the code degrades without them, see store/PLAY_SUBMISSION.md) |
| S7 Input | Parsers treat M3U and XMLTV as hostile: cap line length, cap the number of attributes, ignore `file://` and `content://` streams, and never execute anything |
| S8 Exports | Settings export leaves out the PIN hash unless the user opts in. Never include tokens in QR codes without a warning |
| S9 WebViews | None. If ever added: JavaScript off, no file access, no `addJavascriptInterface` |
| S10 Dependencies | Weekly Dependabot / OSV scan. Any high-severity CVE blocks a release |
| S11 Release build | `isMinifyEnabled = true`, `isDebuggable = false`, signed with the release key, `SHA256SUMS.txt` published |
| S12 Child safety | Child Safe Mode and channel locks can't be bypassed through search, channel up/down, number entry or deep links |

## 3. Store policy guardrails
- The `store` flavor bundles no stream sources or links. It's a player for user-supplied playlists only.
- The privacy policy URL is live (GitHub Pages `privacy.html`). The Data Safety form says nothing is collected or shared.
- The content rating questionnaire is answered honestly for user-generated content.

## 4. Checklist used by `code-security-reviewer`
- [ ] S1–S12 hold for the changed code
- [ ] No new `exported="true"`, permission or cleartext exception without a reason
- [ ] No URL, credential or PIN reaches a log, crash message, toast or export
- [ ] New parsing code bounds its input sizes
