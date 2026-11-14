# Session 5: v1.0 full build and signed release (2026-11-14)

## Outcome
- The owner decided every spec feature ships in v1.0 (nothing deferred). Plan and API notes are in `plan/V1_BUILD_PLAN.md`.
- Built one agent at a time: foundation → player → safety → library → EPG → system → settings.
- Review fix rounds covered performance, security, settings and brand. The final delta review is fixed.
- Emulator QA on the Android TV emulator (Android 16): 33/33 instrumented tests pass. Bugs fixed:
  - priority default
  - duplicate channels on refresh
  - catch-up regex crash
  - EPG grid height
  - Back key on API 36 (`enableOnBackInvokedCallback="false"`)
  - edge-to-edge insets
- Full quality gate: 8/8 PASS. Checkpoint is at `.claude/checkpoints/release-gate.json`.
- Signed with the owner's upload key (`D:\keys\freetia-upload.jks` via `keystore.properties`, which is gitignored):
  - `app/build/outputs/bundle/storeRelease/app-store-release.aab`: Google Play
  - `app/build/outputs/apk/store/release/app-store-release.apk`: Amazon Fire TV
  - `app/build/outputs/apk/foss/release/app-foss-release.apk`: GitHub/sideload
- Privacy URLs: `https://freetia-tv.github.io/freetia-tv/` (GitHub org and repo `freetia-tv`).

## Still open (owner)
- Real-phone test was blocked by the K7 Mobile Security app on the owner's OnePlus (it takes over the screen or blocks installs).
- Contact email placeholder in `docs/privacy.html`; a test playlist URL for store reviewers.
- Create the GitHub org/repo, enable Pages, push.
- Play and Amazon developer accounts, trademark search, real Fire TV / Android TV hardware test.
- Before targetSdk 37: replace the Back opt-out with BackHandler-only handling (see the final review notes).
