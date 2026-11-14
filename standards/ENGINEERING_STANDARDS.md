# Engineering standards

Version 1.0 (14 Nov 2026). Applies to Kotlin, Gradle, resources and tests in every Freetia Android app.

## 1. Architecture
- **Layers:** `data` (Room, parsers, repositories, sync) → `core` (security, cache, memory, input, timers) → `ui` (Compose screens) → `playback` / `work` / `receiver`. UI never touches OkHttp or SQL directly.
- **One APK for all devices.** TV versus phone is decided at runtime with `DeviceDetector` and width breakpoints (720 dp). Never by flavor.
- **Flavors:**
  - `foss` may seed the default playlist.
  - `store` must keep every `DEFAULT_PLAYLIST_*` field empty. The gate checks this.
- **Singletons** live on `FreetiaApp`, created lazily. No DI framework until there's a real need.
- **No premature abstraction.** Duplicate twice and abstract on the third use.

## 2. Kotlin and Compose
- Follow the Kotlin official style (`kotlin.code.style=official`). Lines may be up to 120 characters.
- All I/O is `suspend` and runs on `Dispatchers.IO`. Database reads that drive UI return `Flow<T>`.
- No `GlobalScope` and no `runBlocking` in app code. Use `appScope`, `lifecycleScope` or `viewModelScope`.
- Compose:
  - State is hoisted. A screen takes data and lambdas.
  - Lazy lists use stable `key`s.
  - `remember` anything costly.
  - Never create a player, decoder or bitmap inside a recomposing lambda without `remember` plus `DisposableEffect` to release it.
- **Comments explain why, never what.** Every public class has one line saying its purpose.
- No `!!` outside tests. Use `?:`, `requireNotNull` with a message, or redesign.
- Catch exceptions only where you can recover or report. Never swallow `CancellationException`.

## 3. Memory budget (low-RAM TVs)
The target floor is the **Fire TV Stick Lite: 1 GB RAM, an app heap of about 192 MB, and a weak GPU**. Everything must stay smooth there.

| Rule | How |
|---|---|
| M1 One source of truth | `core/util/MemoryGuard`. `isLowRamDevice` (OS flag, or less than 1.3 GB total) and `underPressure` (from `onTrimMemory` / `onLowMemory`, two-minute cooldown) |
| M2 Optional features are gated | Live preview, animated backgrounds, extra decoders and prefetching must check `MemoryGuard` and degrade to a static fallback |
| M3 One full decoder at a time | Full-screen playback and the preview never run together. The preview is disposed when the list screen leaves |
| M4 Images at display size | Coil loads at the composable's size. Memory cache is 10% of heap (5% plus RGB_565 on low-RAM). Disk cache is 50 MB LRU |
| M5 Trim on pressure | `onTrimMemory(RUNNING_LOW+)` clears Coil's memory cache and raises `underPressure` |
| M6 Stream, don't load | M3U and XMLTV are parsed as streams. Never read a whole playlist or guide into a `String`. Room inserts go in batches of 500 or fewer |
| M7 Lists are virtualised | `LazyColumn` with keys. Playlists over 5,000 channels must move to Room Paging before release |
| M8 Background work yields | Workers check `underPressure` and stop early. EPG refresh never runs while video is playing on a low-RAM device |
| M9 No leaks | Players are released in `onDispose`. No `Context` or `View` is held in singletons or companions. LeakCanary runs in debug builds once it's added |

## 4. Performance targets (checked on the Fire TV Stick Lite before release)
| Metric | Target |
|---|---|
| Cold start to first frame | < 2 s, with startup cache clearing on |
| Channel change to first video frame | < 2 s on a typical connection |
| List scroll | No dropped frames when holding ↓ through 1,000 rows |
| Java heap after 30 min of browsing | < 120 MB, with no steady growth |
| Release APK | < 15 MB |

## 5. Testing
- **Unit tests** (`app/src/test`) for every parser, the syncer, encryption and pure logic. A new parser branch needs a new test.
- **Database tests** (instrumented) for every new DAO query.
- **Manual D-pad pass** on the TV emulator for every UI change: reach every control without touch, and check Back from every screen.
- Tests are named `whatHappens_whenCondition`. They use no network: use fixtures in `src/test/resources`.

## 6. Dependencies
- Add one only when it removes real complexity. Record why in the PR.
- Pin exact versions and use nothing older than one stable release behind. No dynamic `+` versions.
- No analytics, ads, crash-reporting or tracking SDKs, ever.
- Licences must be Apache-2.0-compatible.

## 7. Git and PRs
- Branch names: `feat/…`, `fix/…`, `chore/…`. Commits are imperative and say why.
- The PR description lists the checkpoints touched (see RELEASE_GATE.md) and pastes the gate result.
- Every PR also needs a short reviewer summary: what changed, the risk, and how it was tested.

## 8. Checklist used by `code-security-reviewer` and `performance-reviewer`
- [ ] E1 Layering respected; no I/O on the main thread
- [ ] E2 Coroutines scoped; no `GlobalScope`, `runBlocking` or `!!`
- [ ] E3 Players, receivers, flows and listeners are released
- [ ] E4 Memory rules M1–M9 hold for the change
- [ ] E5 Tests added or updated; the gate's unit tests pass
- [ ] E6 No new lint errors; warnings in touched files don't increase
- [ ] E7 Store flavor has no default playlist; there's still one APK for all devices
- [ ] E8 Dependencies follow §6
