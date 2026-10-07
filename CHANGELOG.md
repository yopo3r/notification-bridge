# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning roughly follows [SemVer](https://semver.org/).

## [0.14.1] — Links menu, restore defaults and four new languages (2026-10-06)

### Added
- **Links menu** in the top bar, next to the app name: a "⋮" button that opens a dropdown with
  the GitHub repository, "Report an issue" (the repository's Issues page) and "Get new themes"
  (the `notification-bridge-themes` repository). Links open in the browser; a device with no
  browser is handled without crashing. The button has a localized accessibility label.
- **Four new languages: Japanese, Korean, Russian and Simplified Chinese** (now 11 in total:
  es, en, fr, pt, it, nl, de, ja, ko, ru, zh). Each has a complete `values-xx/strings.xml`,
  an entry in the Settings language picker (shown in its own language: 日本語, 한국어, Русский,
  简体中文) and an entry in `locales_config.xml`, so it also appears in Android's per-app
  language settings. Russian includes all four plural forms (one, few, many, other); Japanese,
  Korean and Chinese use `other` only.
- **Restore default options** button at the bottom of Settings > Advanced. It opens a
  confirmation dialog ("Are you sure? Erased data cannot be recovered.") that also lists what
  will be reset. Confirming erases every stored option in one DataStore transaction (receiver,
  bridge switch, allowed apps, filters, batching, text length, theme and custom theme,
  dumbphone mode, auto-clear, Test sample) and returns the language to the system default.
  The first-run tutorial flag and the in-memory notification history are left alone.
  The dialog is animated (new `ui/WarningDialog`): it pops in with a fade and a slightly bouncy
  scale, the warning triangle pulses, and it fades out before the action runs. The pulse stops
  by itself when system animations are turned off.
  New `SettingsRepository.resetToDefaults()` and `MainViewModel.restoreDefaults()`.
- New strings in every locale for the links menu (`menu_more`, `menu_github_repo`,
  `menu_report_issue`, `menu_get_themes`), the restore-defaults dialog and the language names.

### Changed
- `ConfigFile.LANGUAGES` now accepts `ja`, `ko`, `ru` and `zh`, so the language is kept when
  exporting and importing a configuration file.
- README now lists 11 languages.
- The Settings language picker is now in alphabetical order: names in Latin script A-Z by their
  own spelling (Deutsch, English, Español, Français, Italiano, Nederlands, Português), followed
  by the other scripts ordered by their English name (简体中文, 日本語, 한국어, Русский).
  `ConfigFile.LANGUAGES` follows the same order.

### Tests
- `ConfigFileTest` uses `"ar"` as its example of an unsupported language (previously `"ja"`,
  which is now supported) and checks that `ja`, `ko`, `ru` and `zh` are accepted.

## [0.14.0] — Readiness checklist, precise history, config file, clearer failures (2026-10-05)

### Added
- **Home readiness checklist**: a compact panel listing notification access, listener
  connection, Bluetooth, receiver selected/paired, bridge enabled, battery-optimization status
  and the last successful transfer, with an "N to fix" / "All set" summary. A row that needs
  attention is tappable and opens the screen that fixes it (listener settings, Bluetooth
  prompt, Test, Settings, or the system battery-optimization list). Values that cannot be
  determined (e.g. no Bluetooth permission yet) show "Unknown" rather than a guess.
- New pure `readiness/ReadinessChecklist` (unit tested); strings added in all seven locales.

- **Configuration file** (Settings → Configuration file): export your setup to a plain-text
  `key: value` file and import it on another build or phone. It carries allowed apps, the four
  filters, batching (on/off and interval), maximum text length, theme (mode and any custom
  palette), language and dumbphone mode, plus the preferred receiver at your choice: **name only
  (default)**, **none**, or **name and Bluetooth address**. The address is left out by default
  because it identifies the device. Importing shows what will be replaced and asks first, applies
  everything in one transaction, rejects the whole file on any unknown, duplicate or out-of-range
  value, and never changes the bridge switch. Without an address, the receiver is selected on
  import only if exactly one paired device has that name. Not included: bridge on/off,
  onboarding, history, auto-clear, auto-reconnect, Test sample. Pure `config/ConfigFile`
  (unit tested); strings in all seven locales.

- **Clearer failures** in History: a retrying transfer reads "Attempt 2 of 3 · Receiver
  unavailable", and a failed one shows a short plain-language explanation plus one action:
  receiver not paired → *Open Bluetooth settings*; Bluetooth permission missing → *Grant
  permission*; receiver rejected the file → *Send compatibility test*; timed out or receiver
  unavailable → *Retry now*. Also handled: Bluetooth off, no receiver chosen, and an
  unclassified failure (→ Diagnostics). A banner on History opens notification-access settings
  when the listener is disconnected. Problems only you can fix stop after one attempt (no
  pointless backoff) and no longer trigger the 5-minute pause. The dumbphone-mode alert states the
  same reason. Diagnostics keeps the technical side as reason names and counts only.
  **Privacy note:** "Retry now" needs the original notification, so the last 5 failed items are
  held in memory only, for at most 10 minutes, and are discarded on retry or "Clear history".
  New pure `FailureReason`, `FailureClassifier`, `RetryBuffer` (unit tested); strings in all
  seven locales.

### Fixed
- The tutorial's Back/Next buttons are no longer covered by the on-screen system buttons on
  phones with three-button navigation (and the top row clears the status bar and cutouts): the
  screen now applies the system-bar insets, which the edge-to-edge activity does not do on its own
  outside a Scaffold.
  - Scrollbar on Home, Diagnostics and About: Home's thumb was drawn inside the scrolling content
  (so it moved with it); Diagnostics and About, which have a few very different-height items,
  got a jumpy thumb because the list height was guessed from visible items only. The thumb now
  uses the heights of every item seen so far.

### Changed
- **History statuses no longer overstate delivery.** "Sent" is now **Transferred to device**
  (the receiver's OBEX server accepted the file; it does not mean anyone saw the notification),
  and a one-line note on the History screen says so. Records now move through **Queued →
  Connecting → Transferred to device / Failed** in place, and notifications that never left the
  phone are recorded too: **Dropped by filter** (ongoing, silent, or call notifications turned
  off, with the reason), **Rate-limited**, and **Combined into batch** (one row per batch while
  its cooldown runs, then it continues as a normal transfer with its message count).
- Not recorded, on purpose: bridge disabled, app not in the allowed list, and re-posted
  duplicates. They would flood the list and keep titles from apps you never allowed. Never-sent
  rows are evicted first when the 50-entry history is full, so they cannot push out transfers.
- Diagnostics reports the last transfer as "transferred to device" instead of "success".
- State is re-read every time the app resumes, so returning from a system settings screen
  updates Home without a restart.
  - **Settings reorganised** into six collapsible sections, all collapsed at first, each with a
  one-line description: **Connection** (bridge on/off, auto-reconnect), **Forwarding** (silent,
  ongoing and duplicate filters, calls, maximum text length, allowed apps), **Privacy**
  (automatic history clearing), **Reliability** (batching and its interval, dumbphone mode),
  **Appearance** (theme, custom theme, language) and **Advanced** (test message, configuration
  file, tutorial). No setting was removed or renamed; which sections are open survives rotation
  and language changes. Strings in all seven locales (the old "Delivery and maintenance" heading
  and "Automatic clearing" sub-heading are gone).
- The scrollbar now fades in quickly and fades out slowly while sliding back into the edge,
  instead of the default spring.

## [0.13.1] — Privacy, SDK, and release maintenance (2026-10-04)

### Changed
- Diagnostics now reports transfer status without copying exception text or exact event times.
- Removed the unused in-memory diagnostic log, which could retain notification-derived file
  names. Debug builds still write troubleshooting lines to logcat; release builds do not.
- Updated the Android build to the current SDK, Gradle, Compose, and AndroidX stable releases.

### Security
- Excluded app data from Android cloud backup and device-to-device transfer, including the
  receiver address and allowed-app configuration.

### Fixed
- Corrected the short Bluetooth test sample so it stays within its documented size limit.
- Added plural-aware count strings across all seven supported locales.

## [0.13.0] — Navigation, pagination, auto-clear, and a real fix for language switching

### Added
- **Swipe navigation**: the six sections (Home/Test/History/Settings/Diagnostics/About) are now
  a `HorizontalPager`; swiping left/right changes section, kept in sync with the tab bar above
  it in both directions.
- **Responsive section bar**: on narrow screens the tab bar scrolls horizontally as before; at or
  above a 600dp width (tablets, landscape) it now fills the available width edge-to-edge instead
  of leaving a gap.
- **Vertical scrollbars**: a thin, auto-sized scrollbar (new `ui/Scrollbar.kt`, two overloads for
  `verticalScroll`/`LazyColumn`) now appears on Home, Test, Settings, History, Diagnostics and
  About whenever their content overflows the screen.
- **"Show more" pagination**: History and the Diagnostics log now render only a first page
  (15 / 30 entries respectively) with a "Show more" button to reveal older entries, instead of
  rendering a potentially long-lived list in full every time.
- **Settings → Borrado automático (auto-clear)**: history and the log can now be pruned by age
  automatically (default: after 24 hours; configurable from 1 hour to 7 days). New pure
  `RetentionPolicy` helper (unit tested) plus a periodic cleanup loop in `BridgeRuntime`.
  Reported in the diagnostics report.
- Log lines are now timestamped (`LogEntry` replaces a bare `String`), which is what makes
  age-based pruning of the log possible.

### Changed
- The "test content" sample picker (short/long/special characters/emoji) moved from the Test
  screen to Settings; the Test screen itself now just sends whatever kind is configured there.
  Persisted as `BridgeSettings.testSampleKind`.

### Fixed
- **Language switching**: selecting a different language in Settings now actually takes effect
  immediately, for real. The previous fix (an explicit `Activity.recreate()` call) was not
  enough, because `MainActivity` never extended `AppCompatActivity` in the first place and its
  native theme did not inherit from an AppCompat theme - so `AppCompatDelegate`'s own automatic
  recreate-on-locale-change hook, which `recreate()` was meant to stand in for, had nothing to
  hook into. Fixed by changing `MainActivity` to extend `AppCompatActivity` and changing both
  `values/styles.xml` and `values-night/styles.xml` to inherit from
  `Theme.AppCompat.DayNight.NoActionBar`; the manual `recreate()` call is no longer needed and
  was removed.

## [0.12.1] — Fix: build failure from an unescaped apostrophe

### Fixed
- `values-fr/strings.xml` had two strings (`settings_batching_desc`,
  `settings_batching_seconds`, added in 0.12.0) with a raw apostrophe instead of `\'`. AAPT2
  rejects this, but with a famously unhelpful error
  (`Can not extract resource from com.android.aaptcompiler.ParsedResource@...`) that doesn't
  name the file or the string. Fixed both.

### Added
- `scripts/check_string_resources.py`: scans every `values*/strings.xml` for unescaped
  apostrophes and fails with the file and string name, instead of waiting for AAPT2's cryptic
  error. Runs in CI as its own step, before the build, so a future instance of this mistake is
  caught immediately with a clear message.

## [0.12.0] — Message batching / cooldown

### Added
- **Settings → Agrupar mensajes (cooldown)**: optional batching of messages from the same
  conversation (same app + same notification title). When on, the first message starts a
  5-60 second cooldown (user-configurable via a slider); anything from the same conversation
  that arrives before it elapses is combined into a single file instead of sending one
  transfer per message. Calls are never batched. New pure `MessageBatch` helper (grouping key +
  combined-text formatting, unit tested).
- Diagnostics now reports how many messages are currently held in a batching cooldown
  (`BridgeUiState.batchedMessageCount`) and the batching setting itself.

### Design decision
- The cooldown is a **fixed window starting at the first message**, not a countdown that resets
  on every new arrival. A resetting countdown could, in principle, never fire during a very
  active conversation; a fixed window guarantees the wait is never longer than the configured
  value, at the cost of occasionally splitting a fast-moving conversation across two
  consecutive files instead of one.
- Batched messages skip the per-app rate limiter (they already have their own throttling
  mechanism); a separate hard cap (50 messages) protects memory if a batch window somehow
  receives an unusual flood.

### Verified
- Re-ran the full `BridgeRuntime` scenario harness (now 40 real end-to-end checks, up from 34)
  against the real Kotlin compiler, plus 6 new pure unit tests for `MessageBatch` — all green.

## [0.11.0] — Configurable test content and text length

### Added
- The **Test** tab can now send four sample kinds instead of one fixed message: short text,
  long text, special/accented characters, and emoji — picked with radio buttons before hitting
  "Send test file". Backed by a new pure `NotificationSamples` helper.
- **Settings → Maximum text length**: four presets (200/700/1500/3000 characters) capping a
  forwarded notification's body. Applies to real notifications and to Test-screen sends alike;
  persisted and validated (values outside 100–5000 are clamped) in `SettingsRepository`.
- Unit tests for both: each `TestSampleKind` is checked against what it claims to exercise
  (short is short, long exceeds one OBEX packet's worth of text, special-characters has
  non-ASCII content, emoji has an actual emoji code point), and a test confirms
  `NotificationFormatter` honors a custom length cap.
- Re-verified the full `BridgeRuntime` scenario suite (34 checks) against the real Kotlin
  compiler after these changes — still all green.

## [0.10.0] — History, diagnostics and dumbphone mode

### Added
- **History** tab: recent transfers (source app, short title, time, sent/failed, error) with a
  "Clear history" button. Kept in memory only (max 50 entries); only the app name and a
  sanitized title are stored, never the message body.
- **Diagnostics** tab (replaces the old Debug tab): an allow-listed technical report with a
  "Copy diagnostic info" button, plus the rolling log with a privacy warning and a clear button.
  The report deliberately excludes notification content, the receiver's name and Bluetooth
  address, and allowed-app package names; MAC-shaped text in errors is redacted.
- **Dumbphone mode** (Settings): moves the foreground status notification to a minimum-importance
  channel, forces automatic reconnection, and posts a single failure alert (at most one per
  10 minutes) when a transfer fails after every retry.
- Per-app **burst protection**: at most 10 forwarded notifications per app per minute.
- `BridgeUiState.listenerConnected`: whether Android has actually bound the listener, shown in
  Diagnostics next to "notification access granted" (the two can differ after a reinstall).
- Unit tests for `DuplicateDetector`, `RateLimiter`, `TransferHistory` and `DiagnosticsReport`
  (including negative tests that plant private values and assert they never leak).
- 24 new strings in all 7 languages.

### Changed
- Navigation is now a scrollable tab row (six sections). A bottom navigation bar supports at
  most five destinations. The selected section survives configuration changes, so switching
  language no longer sends you back to Home.
- `BridgeRuntime`, `MainViewModel` and `SettingsRepository` rewritten with readable formatting
  (same behavior apart from the fixes below). Duplicate detection and rate limiting were
  extracted into small pure classes so they can be unit tested.
- In release builds the in-app log is no longer mirrored to logcat (lines carry title-derived
  file names). Debug builds still log to the `BridgeRuntime` tag.
- The foreground notification's channel names and texts, previously hard-coded Spanish, now come
  from string resources.

### Fixed
- The notification listener could not have started on Android 7.1 (`minSdk 25`): the foreground
  notification used `Notification.Builder(context, channelId)`, which needs API 26. It now uses
  `NotificationCompat`.
- The app never forwards its own notifications any more (previously possible, if ticked in the
  allowed-apps list, and more likely now that the app posts failure alerts). The app is also
  hidden from that list.
- A failed transfer whose exception had no message was recorded as "0xA0 Success".
- The queue counter could get stuck at 1 when an item finished before its increment ran.
- A single unexpected exception (e.g. while reading settings) used to kill the worker coroutine
  and silently stop all forwarding; it is now contained per notification. Coroutine
  cancellation is no longer swallowed by the retry loop.
- A call's duplicate window could be cut short by a message check using a shorter window.
- The installed-apps list is loaded off the main thread.
- "Last OBEX response" could capture the file-name log line if a title contained `0x`.

## [0.9.0] — Bug fixes, About as its own tab, 4 more languages

### Fixed
- Onboarding no longer flashes briefly for returning users on launch. `MainViewModel.settings`
  is now nullable (`null` = "not loaded from DataStore yet"); the UI renders nothing until the
  first real value arrives, instead of briefly trusting the default (`onboardingCompleted =
  false`).
- The Home tab now scrolls (`Modifier.verticalScroll`). On tablets/short viewports the bottom
  buttons were previously unreachable because the column had no scroll modifier.
- Fixed dark mode having no contrast on the onboarding screen (and any other content rendered
  outside `Scaffold`): the theme root now wraps everything in a `Surface` using
  `MaterialTheme.colorScheme.background` / `onBackground`. Without it, the window kept its
  native (light) background while text resolved to the dark scheme's light-on-light color.
- The language picker previously saved the preference but changed nothing on screen:
  `MainActivity` is a plain `ComponentActivity`, so AppCompat's automatic activity-recreation
  hook never fired. The picker now explicitly calls `Activity.recreate()` right after
  `AppCompatDelegate.setApplicationLocales()`. Also added the missing
  `AppLocalesMetadataHolderService` manifest declaration required for the preference to
  persist correctly below Android 13.

### Changed
- The "About" section is now a full peer tab (Home, Test, Settings, Debug, **About**) instead
  of a button that opened it from Settings.

### Added
- Four more languages: Portuguese, Italian, Dutch, German (now 7 total: es, en, fr, pt, it,
  nl, de). All seven `strings.xml` files carry the exact same 82 keys.
- `.github/README.md`, `CONTRIBUTING.md` and this changelog are now in English (previously
  Spanish); in-app UI strings are unaffected and remain available in all 7 languages.

## [0.8.0] — Onboarding and "About"

### Added
- First-run tutorial (`OnboardingScreen`), 6 steps: what the app does, pairing the dumbphone,
  picking the Bluetooth device, granting notification access, choosing which apps to forward,
  and sending a test. Skippable, supports going back, and can be reopened any time from
  Settings ("Show tutorial"). Completion is persisted (`BridgeSettings.onboardingCompleted`)
  and it won't show automatically again unless reopened manually.
- "About" screen (`AboutScreen`), reachable from Settings: app name and version (read
  automatically from `BuildConfig.VERSION_NAME`), description, project purpose, technical
  summary, technologies used, license, and a link to the GitHub repository. No personal data.

### Known limitation
- The GitHub link in "About" uses a placeholder (`GITHUB_REPO_URL` in `AboutScreen.kt`) that
  needs to be replaced with the real URL before publishing.

## [0.7.0] — Theme and language

### Added
- Theme picker (System / Light / Dark) in Settings, persisted across runs
  (`BridgeSettings.themeMode`, stored in DataStore). The app now uses its own Material 3
  color schemes instead of the unstyled default theme.
- Language support: Spanish, English, French. Every visible UI string moved to `strings.xml`
  (with `values-en/` and `values-fr/` variants). The picker uses Android's modern mechanism
  (`AppCompatDelegate.setApplicationLocales` + `locales_config.xml`), which persists the
  selection automatically and also shows up in system Settings on Android 13+.

### Changed
- `MainActivity.kt` rewritten with readable formatting (previously one line per function) as
  part of this work — no behavior changes beyond what's described above.

## [0.6.0] — Getting ready for public release

### Changed
- The namespace/`applicationId` changed from `cl.renato.notificationbridge` to
  `app.notificationbridge` (removing personal information from the package metadata).
  **This change is backwards-incompatible**: installing this version does not upgrade a
  previous install, it ends up as a separate app.
- Added architectural documentation (KDoc) to the main components
  (`NotificationBridgeService`, `BridgeRuntime`, `NotificationFormatter`,
  `ObexObjectPushClient`, `ObexProtocol`, `SettingsRepository`, `Models`, UI).

### Added
- Plain-JVM unit tests for `NotificationFormatter` (file names, special/Unicode characters,
  length limits).
- GitHub Actions workflow (`.github/workflows/ci.yml`): builds, runs unit tests and lint on
  every push/PR, with no dependency on real Bluetooth hardware.
- `CONTRIBUTING.md`, this `CHANGELOG.md`, and the full Gradle Wrapper.

## [0.5.0]

### Added
- Forwarding of incoming calls (native or VoIP, e.g. WhatsApp) detected via
  `Notification.CATEGORY_CALL`, with its own toggle in Settings ("Notify calls") and a wider
  deduplication window than regular messages.

## [0.4.0]

### Added
- Automatic re-enabling after a device restart (`BootReceiver`), conditional on the app
  having been opened manually at least once.
- The "Grant permission / refresh" button now also asks to turn on Bluetooth if it's off, via
  the standard system dialog.

## [0.3.5]

### Changed
- The text file generated per notification now includes the full date and time in its footer
  (previously just the time).

## [0.3.4]

### Fixed
- "Group summary" notifications (`FLAG_GROUP_SUMMARY`) are no longer forwarded separately,
  preventing duplicates when an app bundles several notifications together.
- A failure during the OBEX disconnect step (after delivery was already confirmed) is no
  longer treated as a failed transfer and no longer triggers a duplicate resend of the same
  file.
- The deduplication window dropped from 2 minutes to a few seconds, so a legitimately
  repeated message (e.g. the same person texting "ok" twice) is no longer dropped by mistake.

## [0.3.3-api25] and earlier

- Baseline version: `NotificationListenerService` + in-memory queue + OBEX Object Push
  client over RFCOMM, with configurable filters (allowed apps, silent, ongoing, duplicates),
  a foreground service for the listener, and retries with a timeout on Bluetooth connection
  failures.
