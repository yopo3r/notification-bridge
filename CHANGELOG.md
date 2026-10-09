# Changelog

All notable changes to Notification Bridge are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions roughly follow [Semantic Versioning](https://semver.org/). Below 1.0, a minor release can still change behavior.

## [1.0.0] - 2026-10-08

### Added
- A disclosure dialog before the app opens Android's notification-access screen. It says that titles and text (which can include messages and one-time codes) are read, that they go only to the receiver over Bluetooth, and that the app neither encrypts them nor hides codes. Translated into all 11 languages.
- `PRIVACY.md`, a standalone privacy policy for store listings.
- Fastlane metadata (`fastlane/metadata/android`) in English and Spanish, with the store icon, feature graphic and screenshots, for F-Droid and for filling in Google Play.
- Unit tests for `ObexProtocol`: packet and header encoding, response parsing, packet-size limits, and connection-id parsing with damaged headers.
- `LICENSE-ARTWORK.md` and an Artwork section in the README: the logo, banner and tutorial illustrations were generated with Google Gemini and are dedicated to the public domain under CC0 1.0. The Bluetooth symbol in them is a trademark of the Bluetooth SIG and is excluded.
- `.gitignore`, which keeps keystores and `release-signing.properties` out of version control.

### Changed
- The application ID is now `io.github.yopo3r.notificationbridge`. It is a different app to Android from `app.notificationbridge`, so earlier installs are not updated and must be reinstalled. The code namespace is unchanged.
- The foreground service and its status notification now run only while Enable bridge is on, instead of whenever notification access is granted.
- When a notification has no title or text, the file sent to the receiver says `(no title)` and `(no visible content)` instead of Spanish placeholders. Paired devices without a name show a translated "(unnamed)". Bluetooth error messages are in English.
- `proguard-rules.pro` moved from the project root to `app/`, where Gradle looks for it (it was never read while minification was off).
- Release builds are minified and their resources are shrunk. The release APK no longer carries the Android Gradle Plugin's dependency-metadata block (F-Droid requires this; the Play bundle keeps it).
- `ObexProtocol.kt` and `ObexObjectPushClient.kt` are reformatted into readable Kotlin and commented. Behaviour is unchanged.
- Notification access is detected with `NotificationManagerCompat.getEnabledListenerPackages` instead of parsing a system setting.
- CONTRIBUTING and the README run `lintRelease` too and document `bundleRelease`.

### Fixed
- A message that arrived at the moment its batch was flushed could be lost and leave its History row stuck on "combined into batch". Batches are now closed under a lock and a late message starts a new one.
- The Bluetooth watchdog's timeout flag is atomic, so a timeout is always classified as one.
- If Android disconnects the notification listener while access is still granted, the app now asks it to rebind.

## [0.15.0] - 2026-10-07

### Added
- vMessage as a second message format. Settings → Forwarding → Message format chooses between the plain text file (the default and the only format before) and a vMessage (`.vmg`, OBEX type `text/x-vmsg`) that compatible phones file in their SMS inbox. It follows the layout used by Nokia Series 40 phones: version 1.1, an unread inbox message, the app name as sender, and a UTF-8 body with the title and text. The Receiver tab's test send uses the selected format too.
- `message-format: text` or `vmessage` in configuration files. The key is optional, so files exported by earlier versions still import and keep the text file format.
- The diagnostics report lists the message format.

### Changed
- File names take their extension from the chosen format (`.txt` or `.vmg`).
- The links menu (⋮) now uses the app's surface color, corner radius and text color instead of Material's defaults, so it follows the light, dark and custom themes.

### Fixed
- The three dots of the links menu are drawn instead of typed as a text character, which centers them in the button.

### Notes
- vMessage dialects differ between phones, and this one hasn't been checked on a real receiver yet. The text file stays the default for that reason.

## [0.14.2] - 2026-10-07

### Added
- Illustrations on all six onboarding screens (step 6 reuses the picture from step 1). They sit in a slot of fixed size, above the text in portrait and beside it in landscape, so they stay in the same place when you move between steps.
- Settings → Privacy → Hide in screenshots and recents. When on, the app blocks screenshots and screen recording and blanks its preview in the recent-apps list (`FLAG_SECURE`). Off by default.

### Changed
- The Test tab is now called Receiver (translated in all 11 languages), since it is where you choose the receiver as well as send a test. The onboarding texts and the import message that point to it use the new name, and the Spanish page header no longer says "Prueba de".
- The import confirmation now lists the apps a configuration file would allow (the first twelve, by name) next to the receiver name.
- Annotation targets on `SettingsSection` are explicit (`@param:`), which silences a Kotlin compiler warning.

### Removed
- `BootReceiver` and the `RECEIVE_BOOT_COMPLETED` permission. The receiver only refreshed in-memory state and wrote a log line. Android rebinds the notification listener after a restart without it.

### Security
- Importing a configuration file only selects a receiver that is already paired with the phone, matched by address or, without an address, by a unique name. The receiver is shown under the paired device's own name, not the name in the file. Before, an address from the file was selected without being checked.
- The in-memory queue is limited to 100 transfers and pending batches to 20 conversations. A notification arriving when the queue is full is dropped and recorded as a failure. A new conversation arriving when 20 batches are pending is sent straight away without batching. Clear history now also discards pending batches.

### Documentation
- Rewrote the README, CONTRIBUTING.md and SECURITY.md, and tidied this changelog.

## [0.14.1] - 2026-10-06

### Added
- Links menu in the top bar (the ⋮ button), with the GitHub repository, Report an issue and Get new themes (the `notification-bridge-themes` repository). Links open in the browser, and a device without a browser doesn't crash. The button has a localized accessibility label.
- Japanese, Korean, Russian and Simplified Chinese, for 11 languages in total (es, en, fr, pt, it, nl, de, ja, ko, ru, zh). Each has a complete `values-xx/strings.xml`, an entry in the Settings language picker (written in its own language) and an entry in `locales_config.xml`, so it also shows up in Android's per-app language settings. Russian has all four plural forms. Japanese, Korean and Chinese only use `other`.
- Restore default options, at the bottom of Settings → Advanced. A confirmation dialog lists what will be reset. Confirming clears every stored option in one DataStore transaction: receiver, bridge switch, allowed apps, filters, batching, text length, theme and custom theme, dumbphone mode, auto-clear and test content. The language goes back to the system default. The tutorial flag and the in-memory history are left alone. The dialog fades in with a small bounce and the warning icon pulses, which stops when system animations are off (`ui/WarningDialog`, `SettingsRepository.resetToDefaults()`, `MainViewModel.restoreDefaults()`).
- Strings for the links menu, the restore dialog and the language names in every locale.

### Changed
- `ConfigFile.LANGUAGES` accepts `ja`, `ko`, `ru` and `zh`, so the language survives an export and import.
- The language picker is in alphabetical order: names in Latin script first (Deutsch, English, Español, Français, Italiano, Nederlands, Português), then the rest by their English name (简体中文, 日本語, 한국어, Русский). `ConfigFile.LANGUAGES` uses the same order.
- The README lists 11 languages.

### Fixed
- Closing the tutorial from Settings → Show tutorial, by finishing or skipping it, no longer jumps to Home. It is now drawn as an overlay, so you return to the same tab, scroll position and expanded settings sections, and the system Back button closes it. The first run still ends on Home.

### Tests
- `ConfigFileTest` uses `"ar"` as its unsupported language (it used `"ja"`, which is supported now) and checks that `ja`, `ko`, `ru` and `zh` are accepted.

## [0.14.0] - 2026-10-05

### Added
- Readiness checklist on Home. It lists notification access, listener connection, Bluetooth, receiver selected and paired, bridge enabled, battery optimization and the last successful transfer, with a summary of "N to fix" or "All set". A row that needs attention can be tapped and opens the screen that fixes it: listener settings, the Bluetooth prompt, Test, Settings, or the system battery-optimization list. A value that can't be determined, such as Bluetooth before the permission is granted, shows "Unknown" instead of a guess. The logic is in `ReadinessChecklist` and has unit tests.
- Configuration file (Settings → Configuration file). It exports your setup to a plain `key: value` text file and imports it on another build or phone. The file holds the allowed apps, the four filters, batching (on or off, and the interval), maximum text length, theme (mode and custom palette), language and dumbphone mode. The receiver is optional: name only (the default), none, or name and Bluetooth address. The address is left out by default because it identifies the device.
  - Importing shows what will be replaced and asks first, applies everything in one transaction, and never changes the bridge switch.
  - The whole file is rejected if it has an unknown, duplicate or out-of-range value.
  - Without an address, the receiver is selected only if exactly one paired device has that name.
  - Not included: bridge on or off, tutorial state, history, auto-clear, auto-reconnect and the test content.
  - The logic is in `config/ConfigFile` and has unit tests.
- Clearer failures in History. A retrying transfer reads "Attempt 2 of 3 · Receiver unavailable". A failed one gets a short explanation and one action:
  - Receiver not paired: Open Bluetooth settings.
  - Bluetooth permission missing: Grant permission.
  - Receiver rejected the file: Send compatibility test.
  - Timed out or receiver unavailable: Retry now.
  - Bluetooth off, no receiver chosen and unclassified failures are handled too (the last one points to Diagnostics).

  A banner on History opens the notification-access settings when the listener is disconnected. Problems that only you can fix stop after one attempt instead of backing off, and they no longer trigger the five-minute pause. The dumbphone-mode alert gives the same reason, and Diagnostics keeps to reason names and counts.

  Retry now needs the original notification, so the last five failed items are kept in memory, for ten minutes at most, and are discarded when you retry or clear the history. The logic is in `FailureReason`, `FailureClassifier` and `RetryBuffer`, all with unit tests.

### Changed
- History no longer overstates delivery. "Sent" is now "Transferred to device" (the receiver's OBEX server accepted the file, which says nothing about anyone seeing it), and a line on the History screen explains that. Records move in place through Queued, Connecting and Transferred to device or Failed.
- Notifications that never left the phone are recorded too: Dropped by filter (ongoing, silent or calls turned off, with the reason), Rate-limited, and Combined into batch (one row per batch while its cooldown runs, then it continues as a normal transfer with its message count).
- Not recorded, on purpose: bridge disabled, app not in the allowed list, and reposted duplicates. They would flood the list and keep titles from apps you never allowed. When the 50-entry history is full, rows that were never sent are removed first, so they can't push out real transfers.
- Diagnostics reports the last transfer as "transferred to device" instead of "success".
- App state is read again every time the app resumes, so coming back from a system settings screen updates Home without a restart.
- Settings is organized into six collapsible sections, all collapsed at first and each with a one-line description: Connection (bridge on or off, auto-reconnect), Forwarding (silent, ongoing and duplicate filters, calls, maximum text length, allowed apps), Privacy (automatic history clearing), Reliability (batching and its interval, dumbphone mode), Appearance (theme, custom theme, language) and Advanced (test message, configuration file, tutorial). No setting was removed or renamed. Which sections are open survives rotation and language changes.
- The scrollbar fades in quickly and fades out slowly instead of using the default spring.

### Fixed
- The tutorial's Back and Next buttons were hidden behind the on-screen system buttons on phones with three-button navigation. The top row now clears the status bar and display cutouts as well. The screen applies the system-bar insets itself, because the edge-to-edge activity doesn't do it outside a Scaffold.
- Scrollbar thumbs on Home, Diagnostics and About. Home's thumb was drawn inside the scrolling content and moved with it. On Diagnostics and About, which have a few items of very different heights, the thumb jumped because the list height was estimated from the visible items only. It now uses the heights of every item seen so far.

## [0.13.1] - 2026-10-04

### Changed
- Diagnostics reports the transfer status without exception text or exact event times.
- Removed the unused in-memory diagnostic log, which could hold file names derived from notification titles. Debug builds still write troubleshooting lines to logcat. Release builds don't.
- Updated the build to the current SDK, Gradle, Compose and AndroidX stable releases.

### Fixed
- The short Bluetooth test sample is back within its documented size limit.
- Count strings use proper plural forms in all seven languages.

### Security
- App data is excluded from Android cloud backup and device-to-device transfer, including the receiver address and the allowed-app configuration.

## [0.13.0]

### Added
- Swipe navigation. The six sections (Home, Test, History, Settings, Diagnostics, About) are a `HorizontalPager` that stays in sync with the tab bar.
- Responsive tab bar. On narrow screens it scrolls horizontally as before. From 600 dp wide (tablets, landscape) it fills the full width instead of leaving a gap.
- Thin vertical scrollbars (`ui/Scrollbar.kt`) on Home, Test, Settings, History, Diagnostics and About whenever the content overflows.
- "Show more" paging in History and in the Diagnostics log, with a first page of 15 and 30 entries. Before, the whole list was rendered every time.
- Automatic clearing of the history and the log by age (default 24 hours, configurable from 1 hour to 7 days), through a new `RetentionPolicy` helper with unit tests and a periodic cleanup loop in `BridgeRuntime`. The setting is included in the diagnostics report. (The log itself was removed in 0.13.1.)

### Changed
- The test content picker (short, long, special characters, emoji) moved from the Test screen (now the Receiver tab) to Settings. That screen sends whatever kind is selected there. It is stored as `BridgeSettings.testSampleKind`.

### Fixed
- Changing the language in Settings now takes effect immediately. The `recreate()` call added in 0.9.0 wasn't enough: `MainActivity` didn't extend `AppCompatActivity` and the app theme didn't inherit from an AppCompat theme, so AppCompat's own recreate-on-locale-change hook never ran. `MainActivity` now extends `AppCompatActivity`, `values/styles.xml` and `values-night/styles.xml` inherit from `Theme.AppCompat.DayNight.NoActionBar`, and the manual `recreate()` is gone.

## [0.12.1]

### Fixed
- Two strings in `values-fr/strings.xml` (`settings_batching_desc` and `settings_batching_seconds`, added in 0.12.0) had a raw apostrophe instead of `\'`. AAPT2 rejects that with an error that names neither the file nor the string (`Can not extract resource from com.android.aaptcompiler.ParsedResource@...`).

### Added
- `scripts/check_string_resources.py` scans every `values*/strings.xml` for unescaped apostrophes and reports the file and string. It runs in CI as its own step before the build.

## [0.12.0]

### Added
- Message batching (Settings → Batch messages (cooldown)). The first message from a conversation (same app and same notification title) starts a cooldown of 5 to 60 seconds, set with a slider. Anything from the same conversation that arrives before it ends is sent as one file instead of one transfer per message. Calls are never batched. The grouping key and the combined text come from a new `MessageBatch` helper with unit tests.
- Diagnostics shows how many messages are waiting in a cooldown (`BridgeUiState.batchedMessageCount`) and the batching setting.

### Notes
- The cooldown is a fixed window that starts at the first message. It doesn't restart on every new message. A restarting countdown could, in theory, never end during a busy conversation. With a fixed window the wait is never longer than the value you set, and a fast conversation may occasionally be split across two files.
- A single batch holds at most 50 messages, to protect memory.

## [0.11.0]

### Added
- The Test tab can send four kinds of sample, chosen with radio buttons: short text, long text, special or accented characters, and emoji (`NotificationSamples`).
- Settings → Maximum text length, with presets of 200, 700, 1500 and 3000 characters. It caps the body of a forwarded notification and applies to real notifications and test sends alike. Stored values outside 100 to 5000 are clamped in `SettingsRepository`.
- Unit tests check that each sample kind exercises what it claims (the long one exceeds what fits in one OBEX packet, the special-characters one has non-ASCII content, the emoji one has an actual emoji code point) and that `NotificationFormatter` respects a custom length.

## [0.10.0]

### Added
- History tab with recent transfers (source app, short title, time, result and error) and a Clear history button. It is kept in memory only, holds at most 50 entries and stores the app name and a sanitized title, never the message text.
- Diagnostics tab, replacing the old Debug tab. It has an allow-listed technical report with a Copy diagnostic info button, plus the rolling log with a privacy warning and a clear button. The report leaves out notification content, the receiver's name and Bluetooth address and the package names of allowed apps, and redacts anything shaped like a MAC address in error text.
- Dumbphone mode in Settings. It moves the foreground status notification to a minimum-importance channel, forces automatic reconnection and posts one failure alert (at most one every 10 minutes) when a transfer still fails after every retry.
- Burst protection: at most 10 forwarded notifications per app per minute.
- `BridgeUiState.listenerConnected` shows whether Android has actually bound the listener. Diagnostics displays it next to "notification access granted", because the two can differ after a reinstall.
- Unit tests for `DuplicateDetector`, `RateLimiter`, `TransferHistory` and `DiagnosticsReport`, including tests that plant private values and check they never leak into the report.
- New strings in all seven languages.

### Changed
- Navigation is a scrollable tab row, because a bottom navigation bar takes five destinations at most and there are now six sections. The selected section survives configuration changes, so switching language no longer sends you back to Home.
- Duplicate detection and rate limiting moved into small separate classes so they can be unit tested. `BridgeRuntime`, `MainViewModel` and `SettingsRepository` were reformatted.
- Release builds no longer copy the in-app log to logcat, since its lines contain file names derived from titles. Debug builds still log under the `BridgeRuntime` tag.
- The channel names and texts of the foreground notification were hard-coded in Spanish and now come from string resources.

### Fixed
- On Android 7.1 (`minSdk 25`) the notification listener couldn't have started: the foreground notification used `Notification.Builder(context, channelId)`, which needs API 26. It uses `NotificationCompat` now.
- The app no longer forwards its own notifications. That was possible if it was ticked in the allowed-apps list, and more likely once the app started posting failure alerts. The app is also hidden from that list.
- A failed transfer whose exception had no message was recorded as "0xA0 Success".
- The queue counter could get stuck at 1 when an item finished before its increment ran.
- One unexpected exception, while reading settings for example, used to kill the worker coroutine and silently stop all forwarding. It is now handled per notification, and coroutine cancellation is no longer swallowed by the retry loop.
- A call's duplicate window could be cut short by a check that used a shorter window.
- The list of installed apps is loaded off the main thread.
- The "last OBEX response" could capture the file-name log line if a title contained `0x`.

## [0.9.0]

### Added
- Portuguese, Italian, Dutch and German, for seven languages in total (es, en, fr, pt, it, nl, de). All seven `strings.xml` files have the same keys.
- README, CONTRIBUTING.md and this changelog are in English now (they were in Spanish). The app's own strings are still available in all seven languages.

### Changed
- About is a tab of its own (Home, Test, Settings, Debug, About) instead of a button in Settings.

### Fixed
- The onboarding screen no longer flashes for returning users at launch. `MainViewModel.settings` is nullable now, where `null` means "not loaded from DataStore yet", and the UI draws nothing until the first real value arrives instead of trusting the default (`onboardingCompleted = false`).
- The Home tab scrolls (`Modifier.verticalScroll`). On tablets and short screens its bottom buttons were out of reach.
- Dark mode had no contrast on the onboarding screen or anything else drawn outside a `Scaffold`. The window kept its native light background while the text used the dark scheme's light color. The theme root now wraps everything in a `Surface` that uses the scheme's `background` and `onBackground`.
- The language picker saved the choice but changed nothing on screen, because `MainActivity` was a plain `ComponentActivity` and AppCompat's recreate hook never fired. The picker now calls `Activity.recreate()` after `AppCompatDelegate.setApplicationLocales()`. The missing `AppLocalesMetadataHolderService` manifest entry was added too, which the choice needs to persist below Android 13. (See 0.13.0 for the final fix.)

## [0.8.0]

### Added
- First-run tutorial (`OnboardingScreen`) with six steps: what the app does, pairing the receiver, picking the Bluetooth device, granting notification access, choosing apps to forward, and sending a test. It can be skipped, you can go back, and you can reopen it from Settings (Show tutorial). Completion is stored (`BridgeSettings.onboardingCompleted`), so it only appears again if you reopen it.
- About screen (`AboutScreen`), reachable from Settings. It shows the app name and version (from `BuildConfig.VERSION_NAME`), a description, the purpose of the project, a technical summary, the technologies used, the license and a link to the GitHub repository (a placeholder URL at this point). No personal data is involved.

## [0.7.0]

### Added
- Theme picker (System, Light, Dark) in Settings, kept between runs (`BridgeSettings.themeMode`, stored in DataStore). The app uses its own Material 3 color schemes instead of the plain default theme.
- Spanish, English and French. Every visible string moved to `strings.xml`, with `values-en/` and `values-fr/` variants. The picker uses Android's per-app language support (`AppCompatDelegate.setApplicationLocales` and `locales_config.xml`), which keeps the choice and also lists the app in the system settings on Android 13 and later.

## [0.6.0]

### Changed
- The namespace and `applicationId` changed from `cl.renato.notificationbridge` to `app.notificationbridge`, to remove personal information from the package metadata. This is not backward compatible: installing this version doesn't update an earlier install, it becomes a separate app.
- Added architecture documentation (KDoc) to the main components: `NotificationBridgeService`, `BridgeRuntime`, `NotificationFormatter`, `ObexObjectPushClient`, `ObexProtocol`, `SettingsRepository`, `Models` and the UI.

### Added
- Unit tests for `NotificationFormatter` on the plain JVM: file names, special and Unicode characters, length limits.
- A GitHub Actions workflow that builds the app and runs the unit tests and lint on every push and pull request, with no need for Bluetooth hardware.
- CONTRIBUTING.md, this changelog and the full Gradle wrapper.

## [0.5.0]

### Added
- Forwarding of incoming calls, native or VoIP (WhatsApp, for example), detected through `Notification.CATEGORY_CALL`. It has its own switch in Settings (Notify calls) and a longer duplicate window than regular messages.

## [0.4.0]

### Added
- Automatic re-enabling after a device restart (`BootReceiver`), as long as the app had been opened manually at least once. Removed again in 0.14.2.
- The Grant permission / refresh button also asks to turn Bluetooth on when it's off, using the standard system dialog.

## [0.3.5]

### Changed
- The text file for each notification now ends with the full date and time instead of the time alone.

## [0.3.4]

### Fixed
- Group summary notifications (`FLAG_GROUP_SUMMARY`) are no longer forwarded on their own. That caused duplicates when an app bundled several notifications.
- A failure during the OBEX disconnect step, after delivery was already confirmed, no longer counts as a failed transfer and no longer causes the same file to be sent again.
- The duplicate window went from 2 minutes down to a few seconds, so a message that is legitimately repeated (the same person texting "ok" twice) isn't dropped by mistake.

## [0.3.3-api25] and earlier

- Baseline: a `NotificationListenerService`, an in-memory queue and an OBEX Object Push client over RFCOMM, with configurable filters (allowed apps, silent, ongoing, duplicates), a foreground service for the listener, and retries with a timeout when the Bluetooth connection fails.
