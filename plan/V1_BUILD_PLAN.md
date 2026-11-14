# Freetia TV v1.0 build plan (shared brief for all agents)

Owner decision (2026-11-14): **every feature in `Freetia-TV-Spec.txt` ships in v1.0.** Nothing is deferred. Read the spec section for your task only; you don't need the whole spec.

## Token rules (mandatory)
- Read only the files you need, using offset/limit or grep for big files. Don't read whole standards docs; the digest below is enough.
- Don't paste code into your report. The report is **15 lines max**: files changed, build result, open issues.
- Build with `& D:\FreetiaTV\gradlew.bat -p D:\FreetiaTV <tasks> --console=plain -q 2>&1 | Out-File $env:TEMP\<you>.txt`, then read only the tail or the errors. Never dump the full log.
- Don't use the emulator unless your task says so. Only one agent at a time uses it.

## Rules digest (from standards/)
- **Brand:**
  - All colours come from `LocalBrand.current` (see `ui/theme/BrandTokens.kt`). Never use a raw hex in UI.
  - Type scale: TV body 16sp, titles 20–22sp, hints 12–13sp, line height 1.3em.
  - Focus states:
    - Rows and chips: focused = white pill or `brand.focusGradient` with a 2dp white border, scale 1.03, 150 ms tween.
    - Text fields: white fill when focused and not editing.
    - Body text contrast of at least 4.5:1.
  - Layout: TV-safe insets 48dp horizontal and 24dp vertical. Below 720dp width use 16dp.
  - Every screen ends with a `HintBar(...)`; the public composable is in `ui/ChannelListScreen.kt`.
  - No screen is more than 2 levels deep from Home.
  - Copy is plain and says what to do next. Never name channel providers.
- **Engineering:**
  - Suspend functions for I/O, Flow for DB, no `!!`, no GlobalScope or runBlocking. Rethrow `CancellationException`.
  - Stream M3U and XMLTV; never hold a whole file in memory.
  - Use `app.memoryGuard`: `isLowRamDevice` gates optional animations and previews, and workers check `shouldDeferBackgroundWork()`.
  - Room queries that can return big results need LIMIT or paging.
- **Security:**
  - Never log URLs, PINs, tokens or programme names. Logs only behind `BuildConfig.DEBUG`.
  - Untrusted input (playlists, XMLTV, imports) must be bounded and scheme-checked (http/https).
  - Hash PINs, recovery codes and security answers with `EncryptionManager.hash/verify`. Playlist URLs go in encrypted storage.
  - No new exported components without updating the S5 allowlist in both gate scripts and SECURITY_STANDARDS.md.
- **Store:**
  - The `store` flavor never gets a default playlist URL.
  - No accounts, server or analytics. "Sync" means local export/import by file or QR.
  - DRM streams show a clear "unsupported stream" message.
- **Tests:** every new pure-logic branch gets a unit test in `app/src/test`. DAO and DB logic goes in `app/src/androidTest`.

## Architecture contracts (created in Wave 1, then used by everyone)
- `ui/nav/Routes.kt` lists every screen route. `MainActivity` hosts a simple state-based navigator (no extra nav library) with a back stack.
  - **Feature agents add screens only in their own files** and register them with a single line in `ui/nav/Routes.kt` / `ui/nav/NavHost.kt`.
- `data/settings/SettingsRepository.kt` provides typed `Flow`s and setters for every DataStore key. Feature agents use it and don't touch DataStore directly.
- `ui/components/` holds the brand kit:
  - `SettingsScaffold`, `SettingToggleRow`, `SettingChoiceRow`, `SettingStepperRow`, `SettingActionRow`
  - `BrandDialog`, `NumberPad` (TV-sized), `BrandToast` (non-blocking), `FocusPill`
- Room schema: the DB was never released, so Wave 1 finalises the schema at **version 1**, with no migrations. During Wave 1 the debug build uses `fallbackToDestructiveMigration`.
  - **Only Wave 1 edits `Entities.kt` and `FreetiaDatabase.kt`.** Later agents who need a column report it instead of adding it.
- `data/secure/PlaylistUrlStore.kt` stores playlist source URLs encrypted (EncryptionManager). `PlaylistEntity.sourceUrl` holds only a reference key.

## Execution order (owner, 2026-11-14)
Agents run **one at a time**: foundation → player → safety → library → epg → system → settings → qa. Each agent finishes its scope and leaves the build green (compile, unit tests, lint at 0 errors) before the next one starts. Full testing happens once at the end: the gate, emulator QA and the three reviewers, with a fix loop until all pass. The owner won't intervene until everything is done.

## Waves and file ownership
| Wave | Agent | Owns (creates or edits) |
|---|---|---|
| 1 | foundation | `ui/nav/*`, `ui/components/*`, `data/settings/*`, `data/secure/*`, `Entities.kt`, `FreetiaDatabase.kt`, all DAOs (new queries), `MainActivity.kt`, `PreferencesKeys.kt` |
| 2 | player | `ui/player/*` (split `PlayerScreen.kt` into this package), `playback/*` except `PlaybackService` |
| 2 | safety | `ui/safety/*`, `core/safety/*` (PIN, child-safe filter, locks, session unlock) |
| 2 | library | `ui/library/*` (playlist manager, search, favourites, groups, recents, region picker), `ChannelListScreen.kt`, `data/sync/*`, `data/repository/*`, `work/PlaylistRefreshWorker.kt` |
| 2 | epg | `ui/epg/*`, `data/epg/*`, `data/parser/XmltvParser.kt`, `work/Epg*`, `work/ReminderWorker.kt`, `receiver/ReminderReceiver.kt` |
| 3 | system | `playback/PlaybackService.kt`, `core/timer/*`, `core/cache/*`, `receiver/BootReceiver.kt`, `work/AutoStartWorker.kt`, `ui/system/*`, AndroidManifest (service, PiP, boot) |
| 3 | settings | `ui/settings/*` (every §3.14 page and wiring into the other features' APIs), `data/backup/*` (export/import, QR), `ui/diagnostics/*`, profiles |
| 4 | qa | tests for the §8 acceptance checks, an emulator walkthrough, screenshots, store copy |

## Known issues assigned to waves
- **foundation:**
  - Back stack: leaving the player takes two Backs, because the first only hides the banner. Back from the phone form goes to the player instead of the list. The category resets to All after returning from the form.
  - `PillButton` shows grey instead of white when focused (the M3 Surface focus overlay), and this affects every pill.
  - The channel list still uses 48dp side padding on phones.
- **player:** the banner has no initials fallback when there's no logo, and the muted meta line has weak contrast over bright video, so the scrim needs to be stronger.

If you have to change a file you don't own, keep the edit to a few lines with Edit (never Write) and mention it in your report.

## Wave 1 API (foundation, done)
**Routes** (`ui/nav/Routes.kt`, sealed `Route`, Serializable): `Home`, `Player(channelId)`, `AddPlaylist`, `Playlists`, `PlaylistEdit(playlistId)`, `Search`, `Favourites`, `Groups`, `GroupDetail(groupId)`, `Recents`, `RegionPicker`, `Epg`, `Reminders`, `Settings`, `SettingsPage(SettingsSection)`, `Safety`, `PinRecovery`, `Locks`, `Profiles`, `Backup`, `Diagnostics`, `AutoStartSetup`. `SettingsSection` = PLAYBACK, STARTUP, NAVIGATION, DISPLAY, SAFETY, SLEEP_REMINDERS, PLAYLISTS_EPG, CACHE, ADVANCED.
- **Placeholders** already exist in each owner's file, e.g. `ui/library/SearchScreen.kt` → `fun SearchScreen(nav: Navigator)`; `ui/epg/EpgScreen.kt`, `ui/settings/SettingsScreen.kt` + `SettingsPageScreen(section, nav)`, `ui/safety/SafetyScreen.kt`, `ui/system/AutoStartSetupScreen.kt`, `ui/diagnostics/DiagnosticsScreen.kt`. Replace the body and keep the signature, or change the one `Route.X ->` line in `NavHost.kt`'s `Screen()`.
- **New screen:** add `data object X : Route` and one `Route.X -> XScreen(nav)` line (the `when` is exhaustive).
- **Navigator** (`LocalNavigator.current` or the `nav` param): `push(route)` (a route already on the stack pops back to it), `pop(): Boolean`, `replace(route)`, `play(channelId)` (single player), `openTopLevel(route)` (pops to Home/Player first, used by colour buttons), `popToHome()`, `popTo<T>()`. Back is global: the host pops; Home = double-Back exit with a toast. Use `BackHandler` for screen-local Back (dialog/panel). For raw Back such as long-press, consume both KeyDown and KeyUp of `Key.Back` in `onKeyEvent`. Colour buttons (PROG_RED/GREEN/YELLOW/BLUE) read `settings.colorButtons` → `ColorAction.route()`.
- **AppSession** (`LocalAppSession.current`): `playlists`, `activePlaylist`, `channels` (StateFlows; Child Safe and hide-dead applied), `selectPlaylist(id)`, `channelsFor(playlistId)`, `addPlaylist(name, url)`, `lastWatchedChannelId`, `toast`, `exitApp()`.
- **Settings:** `app.settings.<pref>.flow` / `.get()` / `.set(v)` / `.reset()`; `settings.all` lists every pref; `resetPreferences()` keeps PIN/recovery hashes. Enums: `ThemeMode`, `BufferMode`, `AspectRatioMode`, `LockBehavior`, `ColorAction`, `ColorButton`; `colorAction(button)`. New keys: RESUME_LAST_CHANNEL, ACTIVE_PROFILE_ID, SUBTITLE_USE_SYSTEM_STYLE, CHANNEL_CHANGE_OSD_DELAY_MS, REMINDER_AUTO_TUNE, EPG_SOURCE_URL, EPG_REFRESH_INTERVAL_HOURS, EPG_DAYS. `sessionUnlockMinutes` 0 = until exit.
- **Playlist URLs:** `PlaylistEntity.sourceUrl` is a `playlist_url:<uuid>` ref. Use `playlistRepository.add(entity, url)`, `sourceUrl(playlist)`, `updateSourceUrl(playlist, url)` and `delete(playlist)` (this also removes the secret), or `app.playlistUrlStore`. Never log the URL.
- **Schema v1** (only Wave 1 edits it):
  - New tables `profiles` (id 1 "Me" is seeded and can't be deleted; `isChild`, `safetyLevel`) and `category_locks` (scope `group`/`region`, value).
  - Favourites, recents and groups use `profileId` (default 1).
  - Reminders have `wakeScreen`, `autoTune` and `stopTime`, are unique per (channelId, startTime), and have no FK to programmes.
  - Channel adds `catchup`, `catchupSource`, `isDead`.
  - Playlist adds `userEpgUrl`, `lastChannelId` and `epgLastUpdate*`.
  - Paging: `ChannelDao.pageByPlaylist/pageAll/pageByCategory/pageByRegion/pageSearch`, `FavouriteDao.pageFavouriteChannels`, `GroupDao.pageGroupChannels` (paging-compose 3.3.5 is on the classpath).
  - Locks: `LockedChannelDao.isChannelLocked(id)` and `observeLockedIds(playlistId)`. Recents: `RecentChannelDao.record(channelId, profileId)` trims to 20. EPG: `EpgDao.getGridWindow(tvgIds, from, to)`.
- **Components** (`ui/components/`):
  - `SettingsScaffold(title, subtitle?, hints, autoFocus) { item { … } }` and `SettingSectionHeader(text)`
  - `SettingToggleRow(title, checked, onCheckedChange, subtitle?, enabled)`
  - `SettingChoiceRow(title, options, selected, onSelect, label)`: ←→ cycles, OK opens a dialog
  - `SettingStepperRow(title, value, onValueChange, range, step, format)`
  - `SettingActionRow(title, onClick, subtitle?, value?, destructive, showChevron)`
  - `BrandDialog(title, onDismiss, message?, confirmLabel, onConfirm?, dismissLabel?, destructive, initialFocus?) { content }` and `BrandChoiceDialog(...)`
  - `NumberPad(onDigit, onBackspace, onDone, doneLabel, doneEnabled)` plus `PinDots(length, filled, error)`
  - `LocalBrandToast.current.show(text, isError)`, hosted by the nav host
  - `FocusPill(text, onClick, primary, enabled, icon)` and `FocusSurface(onClick, shape, unfocusedColor) { focused -> }`
  - `rememberScreenInsets()`: 48/24dp on TV, 16dp below 720dp
  - `HintBar` is still `ui.HintBar`

## Player API (player agent, done)
- `ui/player/ChannelStepper.kt`: `nextChannel(channels, currentId, delta)` is the only place Up/Down is decided (wraps); safety adds adult/locked skipping there. `NumberEntry.append/resolve` holds number entry.
- Entry points: OK opens `ChannelOverlay` (inside the player), Menu key or its "Player options" pill opens `PlayerMenu`; sleep timer row pushes `Route.SettingsPage(SLEEP_REMINDERS)` (system agent: change that one line in `PlayerScreen.kt` if a timer route is added).
- Per-channel audio/CC use `ChannelDao.updatePreferredAudio/updateCaptionsEnabled`; buffer preset via `playback/PlayerFactory.kt` `bufferValues(mode, min, max)`, read once per player (next tune/open).
- Long-press Back to previous channel uses `PlayerHistory` (setting `backReturnsToPrevious`); KeyDown and KeyUp are both consumed only when it is on.
- Edited outside ownership: one import line in `ui/nav/NavHost.kt` (`ui.player.PlayerScreen`).

## Safety API (safety agent, done)
- `app.safety` (`core/safety/SafetyManager`): `childSafeMode`, `lockedIds(playlistId)`, `categoryLocks()`, `isLocked/needsPinToTune`, `setChannelLocked/setCategoryLocked/setMarkedAdult/setChildSafeMode`, `session` (in-memory `SessionUnlock`), `pin` (`PinManager`: hashes now in EncryptionManager secrets, not DataStore; `exportSecrets/importSecrets` for backup "include PIN" only; the DataStore `pinHash/recovery*` prefs are legacy, migrated and cleared on start).
- `core/safety/ChannelFilter`: `isAdultText` (sync detection), `isAdult`, `visible(list, childSafe, hideDead)` (AppSession lists; **library search must use it or the DAO `hideAdult` flag**), `skipWhenStepping`. `nextChannel(..., skip)` gets `ui.safety.rememberStepSkip(playlistId)` in the player.
- Tuning gate: `ui.safety.LockGate` wraps PlayerScreen in NavHost's PlayerRoute, so every tune (list, search, favourites, number entry, reminders) asks the PIN for locked channels. Padlocks: wrap a list in `LockedIdsProvider(playlistId)` and check `LocalLockedChannelIds.current` / draw `Padlock(focused)` (done for ChannelListScreen rows).
- Menus: `val actions = rememberSafetyActions()` then `toggleChannelLock(ch)`, `toggleMarkAdult(ch)`, `setCategoryLocked(LockScope.GROUP|REGION, value, on)`, `setChildSafeMode(on)`, `withPin(title) { }` (general PIN gate), `ensurePin { }`; it hosts its own PIN dialogs. Dialogs: `PinEntryDialog`, `PinSetupDialog`, `RecoveryCodeDialog`, `SecurityQuestionDialog`.
- Settings agent: route `SettingsPage(SAFETY)` to `SafetyScreen(nav)`; player/library menus still need "Lock channel"/"Mark as adult" rows calling `SafetyActions` (not added by safety).

## Library API (library agent, done)
- Startup, failure fallback, sync toasts, first-run region picker and periodic refresh live in `ui/library/LibraryHost` (hosted once in `NavHost`; `HomeRoute` no longer resumes). Pure rules: `data/repository/StartupRules`, `RegionRules` (`RegionFilter` is stored in `settings.selectedRegion`: "" = never asked, "*" = all, "IN" or "IN/hindi"), `data/sync/SyncRules` (`SyncPolicy`, `ChannelDiff`). `LibraryStartup.channelFor(app, playlist)` = resume or first channel (both resume toggles).
- `PlaylistRepository`: `addNew(name, url)` (first playlist = default and auto-update on, later ones off), `move(id, ±1)`, `update`, `rename`, `setDefault/clearDefault`, `userEpgUrl(p)` / `effectiveEpgUrl(p)` / `setUserEpgUrl` (the guide URL is an encrypted `playlist_url:` ref in `userEpgUrl`: the epg agent must resolve it with these, never read the column raw), `updateSourceUrl` is now suspend. `LibraryRepository(db, settings)`: favourites, groups, recents, `search`, paged flows (profile = `settings.activeProfileId`).
- `SyncEvents` (`flow`, `failedIds`) carries sync results to BrandToasts; `PlaylistSyncer` emits them, workers no longer use Android toasts. `HealthChecker.get(app).run(playlistId)` (HEAD then GET, 300 per run, throttled, sets `isDead`), `HealthCheckWorker`, `PlaylistRefreshWorker.ensurePeriodic` (only playlists with auto-update on and an interval).
- UI: `ChannelContextMenu(channel, onDismiss)` and `ChannelActionRows` (favourite, group, lock, adult), `PlayerLibraryRows(channel, onClose)` (via the new `extraRows` slot in `PlayerMenu`), `PlaylistSwitcherDialog`, `PagedBrowser` (more than 5,000 channels or the merged All view), `AllLockedIdsProvider`, `NameDialog`; `ui.FormField` is now internal for reuse. Settings can link to `Route.Playlists` and `Route.RegionPicker` as they are.
- Not done by library: a `SELECTED_LANGUAGE` key (language rides inside `selectedRegion`), and a manifest `<queries>` entry for `RecognizerIntent` (the Voice search button always shows and toasts when no recognizer exists).

## EPG API (epg agent, done)
- Import: `work/EpgRefreshWorker` (`start(app)` from FreetiaApp follows `epgRefreshIntervalHours` and runs after `SyncEvent.Updated`; `enqueue(ctx, playlistId, force)`) uses `data/epg/EpgImporter` (url = `effectiveEpgUrl` else `settings.epgSourceUrl`; gzip by magic bytes; `EpgStreams` caps 200 MB download / 800 MB XML; `XmltvLimits` field caps; window = 24 h back to `epgDays` ahead; programmes stored once per XMLTV id on the first matched channel, matched by tvg-id then name).
- Read: `EpgRepository` (`guideIds`, `window`, `search` with LIKE-escape, region/child-safe in SQL via `EpgDao.searchGuide`); I added `getEpgRefs/getEpgChannelsForPlaylist/searchGuide/hasUpcoming` to `EpgDao` (no schema change). UI: `EpgScreen` (virtual-focus grid, `EpgSearchPanel` inside it, no new route), `RemindersScreen`, shared `ProgrammeDialog` + `rememberReminderUi()` for other screens.
- Reminders: `data/epg/Reminders.kt` has `ReminderRepository.set/cancel`, `ReminderScheduler.schedule/cancel/rescheduleAll(context)` (BootReceiver calls it), `ReminderPermissions` (notifications, exact alarms API 31+, full-screen API 34+), `ReminderNotifier`, `ReminderLaunch` (MainActivity `channel_id` extra tunes once). Lead time, wake and auto-tune come from `settings.reminderLeadMinutes/reminderWakeScreen/reminderAutoTune`.
- Catch-up: `CatchupUrl.build/isAvailable`, `CatchupRequest.set/take` (PlayerScreen takes it on the next tune of that channel; one-line edit). Settings agent: link `Route.Reminders` from the Sleep and reminders page and surface the EPG URL/refresh interval/EPG days there; "Refresh now" = `EpgRefreshWorker.enqueue(ctx, force = true)`.
- Edits outside ownership: BootReceiver (1 line), FreetiaApp (1 line), MainActivity (intent handling), PlayerScreen (1 line), AndroidManifest (SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT).

## System API (system agent, done)
- `core/system/AppExit.exit(app)` is the only full exit (stop timer, `PlaybackHost.shutdown`, exit cache clear from the synchronous `CacheManager.policy` mirror, finish, kill); `AppSession.exitApp` and the sleep timer call it. `PlaybackHost` (playback/) owns the single player: the player screen registers/reclaims it, and Home hands it to `PlaybackService` only when the playlist's `allowBackgroundPlay` is on (video off, audio on).
- `app.cacheManager.clearNow(CacheSelection = ALL, imageLoader = app.imageLoader)` is Settings' "Clear now" (returns bytes freed; `sizeBytes()`); `policy` mirrors `clearCacheOnExit/OnStartup/logoCacheSizeMb` (a new logo cap applies from the next launch); `CacheSelection` has logos, epg, temp, player, probe, crashLogs.
- `app.sleepTimer` (`SleepTimerManager`): `start(minutes)`, `addTime(15|30)`, `stop()`, `continueWatching()`, `timerState` (Inactive, Active, FinalWarning, Expired); `Route.SleepTimer` is the page and `SystemHost` (hosted in NavHost) draws the 60 s warning and the dim. Settings can link `Route.SleepTimer` from the Sleep page.
- Auto-start setup: `Route.AutoStartSetup`; `SystemHost` pushes it once when `autoStartOnTv` is on, setup not shown and `AutoStartAdvisor` says launch is likely blocked. Settings should push it too when the toggle turns on. `AutoStartStatus.get(ctx)` gives the last boot result.
- Edited outside ownership (a few lines each): `FreetiaApp` (sleepTimer, startup clear, cache mirror), `MainActivity` (AppExit, PiP hooks), `PlayerScreen` (host/PiP/sleep route/media title), `PlayerFactory` (wake mode), `Routes.kt`/`NavHost.kt` (SleepTimer, SystemHost, sleepWarningKeys), `strings.xml`.

## Settings API (settings agent, done)
- Pages: `ui/settings/SettingsPages.kt` (one composable per `SettingsSection`, dispatched in `SettingsPageScreen`; Safety = `SafetyScreen`). Shared helpers in `SettingsSupport.kt` (`rememberSettingsCtx`, `Pref.collect()`, `describe` for TalkBack, `TextPromptDialog`, `launchPinned`). Pure rules in `SettingsRules.kt` (`ColorButtonRules`, `ProfileRules`, `ProfileSwitchRules`) and `ui/theme/TextScale.kt` (`TextSize`, `effectiveFontScale`, applied through `LocalDensity` in `FreetiaTheme(textSizePercent)`). Theme = Default or High contrast (the `highContrast` pref); no light theme.
- Backup (`data/backup/`): `BackupCodec` (JSON, schema 1, validates every field), `BackupFiles.write/open` (plain file has no links or secrets; a passphrase gives AES-256-GCM + PBKDF2, 120k, and only then links, guide link and an optional PIN hash), `SettingRules` (every exportable pref and its allowed values; a test fails when a new pref is not listed), `BackupService` (`build/apply`), `SyncCode` (deflate + base32 `FTV1...` text and zxing QR matrix). Import is capped at 2 MB, http/https only, merge or replace playlists; favourites, groups and locks are matched to channels already on the device by tvg-id then name.
- Global guide link: `data/backup/EpgSourceUrl.get/set(app)` keeps `epgSourceUrl` as an encrypted `playlist_url:` ref; `EpgImporter` resolves it (one-line edit). Anything that reads that pref must use this helper, never the raw value.
- Profiles: `ui/settings/ProfileEnforcer.start(app)` (from `FreetiaApp`) keeps the active profile valid and Child Safe Mode on for child profiles; switching away from a child profile (or lowering safety) needs the PIN once one exists. Favourites, groups and recents already read `settings.activeProfileId` through `LibraryRepository`.
- Diagnostics: `DiagnosticsSnapshot.capture(player)` is called when `PlayerScreen` goes away so the page can show the last numbers; live numbers come from `PlaybackHost.playerForService()`. "Send report" is a plain-text share intent. Edits outside ownership: `PlayerScreen` (capture call, root semantics), `ChannelOverlay`, `EpgScreen`, `ChannelListScreen` (a11y and min heights), `BrandDialog` (paneTitle), `Theme.kt`, `MainActivity` (text size), `FreetiaApp`, `EpgImporter`, DAOs (3 read queries: `getGroupChannels`, `getLockedChannels`/`getCategoryLocks`, `getByName`).
