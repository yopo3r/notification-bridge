# Notification Bridge

Forwards your main phone's notifications to a second device over **Bluetooth Classic**, using
the **OBEX Object Push** profile — the same mechanism basic phones ("dumbphones") have used to
receive files over Bluetooth for more than twenty years, with no companion app needed on the
receiver.

## The problem it solves

If you use (or went back to using) a basic phone with no apps and no Internet as a secondary
device — to disconnect, save battery, or just because you like it — you lose the ability to
see who texted you on WhatsApp, Telegram, email, or who's calling, without pulling out your
smartphone. Notification Bridge runs on the smartphone, watches its notifications, and sends
the basic phone a text file for each one, using a file-transfer protocol that phone already
knows how to receive out of the box.

## What the app does

- Listens to Android's system notifications (messaging, email, calls — native or VoIP like
  WhatsApp's — and any other app you choose).
- Filters out what you don't want forwarded: apps not selected, silent or ongoing
  notifications, duplicates.
- Converts each allowed notification into a readable `.txt` file (app, title, content, date
  and time).
- Transfers it over Bluetooth to the paired receiving device, using OBEX Object Push.
- Retries on connection failures, with a real timeout so it never hangs indefinitely.
- Keeps a **history of recent transfers** (app, short title, time, sent/failed, error) that you
  can clear at any time.
- Has a **Diagnostics** screen with a copyable technical report. Review its device and settings
  details before sharing it publicly (see [Privacy](#privacy)).
- Has an optional **dumbphone mode** for unattended use: a minimal status notification,
  forced automatic reconnection, and an alert only when a transfer ultimately fails.
- The **Test** tab sends a fixed sample so the Bluetooth/OBEX path gets a real check, not just a
  happy-path one; which of four kinds it sends (short text, long text, special/accented
  characters, emoji) is configured in **Settings**.
- Transfer history can be **cleared automatically by age** (Settings → Borrado automático),
  defaulting to after 24 hours, configurable from 1 hour to 7 days.
- The six sections are swipeable (`HorizontalPager`), and the section tab bar fills the full
  width on tablet-sized screens instead of leaving a gap. Long lists (Home, Settings, History,
  and About) show a thin scrollbar, and History pages its content with a "show more" button
  rather than rendering everything at once.
- The maximum length of a forwarded notification body is configurable in Settings (100 to
  5000 characters).
- Custom color themes can be imported from a small `.theme` text file in Settings → Theme. The
  bundled [Tokyo Night example](examples/tokyo-night.theme) shows the supported format; invalid,
  incomplete, or newer unsupported files are rejected without changing the current theme.
- Optional **message batching / cooldown**: wait a user-set number of seconds (5-60) after the
  first message from a conversation (same app + same notification title) before sending, so
  several messages sent in a row become one file instead of one transfer each. Calls are never
  batched.
- Protects the Bluetooth link from a chatty app: one app can queue at most 10 transfers per
  minute, and identical reposts of a notification are suppressed.
- **Specifically tested against an Alcatel 3080A** as the receiver. Other phones that accept
  Bluetooth OBEX Object Push transfers should work the same way, but this project hasn't
  tested them.

### Custom themes

Theme files use `key: value` lines. Blank lines and lines beginning with `#` are comments. Version
1 requires a name and all seven colors for both light and dark appearance. Colors accept `#RRGGBB`
or `#AARRGGBB`; unknown or duplicate keys reject the whole file. Import it in Settings → Theme.
The System/Light/Dark options continue to select which palette is shown. Use **Use built-in theme**
to remove the custom palette and return to the app's original colors.

```text
version: 1
name: My Theme
light.foreground: #343B58
light.background: #D5D6DB
light.highlight: #2E7DE9
light.highlight-foreground: #FFFFFF
light.secondary: #9854F1
light.surface: #E1E2E7
light.error: #F52A65
dark.foreground: #C0CAF5
dark.background: #1A1B26
dark.highlight: #7AA2F7
dark.highlight-foreground: #1A1B26
dark.secondary: #BB9AF7
dark.surface: #24283B
dark.error: #F7768E
```

## Architecture

```
Android system notification
            │
            ▼
NotificationListenerService  (NotificationBridgeService)
            │  extracts app / title / text / category
            ▼
Notification processing
            │  filters: allowed apps, silent,
            │  ongoing, duplicates, calls
            ▼
Queue  (BridgeRuntime)
            │  serial processing, retries with timeout
            ▼
Bluetooth  (RFCOMM, socket to the receiver's OBEX service)
            │
            ▼
OBEX Object Push  (CONNECT → PUT → DISCONNECT)
            │
            ▼
Dumbphone  (e.g. Alcatel 3080A) — receives the .txt file
```

### Main components

| Component | Role |
|---|---|
| `NotificationBridgeService` | `NotificationListenerService`: entry point, receives every system notification |
| `NotificationFormatter` | Turns a notification into text/a file name (no Android dependencies) |
| `BridgeRuntime` | Queue, filters, deduplication, retries, state observed by the UI |
| `ObexObjectPushClient` | Opens the RFCOMM socket and runs the OBEX exchange |
| `ObexProtocol` | OBEX packet encoding/decoding (hand-written, no external libraries) |
| `SettingsRepository` | Persistent configuration (Jetpack DataStore) |
| `TransferHistory` / `DuplicateDetector` / `RateLimiter` / `NotificationSamples` / `MessageBatch` / `RetentionPolicy` | Pure helpers: history list, duplicate suppression, burst protection, Test-screen sample content, batch grouping/combining, age-based pruning |
| `DiagnosticsReport` | Builds the copyable, allow-listed diagnostics text (pure JVM) |
| `ErrorNotifier` / `NotificationChannels` | The app's own notifications: status (normal or quiet) and failure alert |
| `OnboardingScreen` / `AboutScreen` / `HistoryScreen` / `DiagnosticsScreen` | Compose screens |
| `ui/theme/Theme.kt` | Material 3 light/dark color schemes |
| `MainActivity` / `MainViewModel` | Jetpack Compose UI (6 sections: Home, Test, History, Settings, Diagnostics, About) |
| `BootReceiver` | Confirms the listener reconnects after a device restart |

Every major component has architectural documentation (what it does, why it exists, how it
talks to others, its limits) directly in its source file's header.

## How the notification flow works

1. Android delivers the notification to `NotificationBridgeService.onNotificationPosted()`.
2. The service discards "group summary" notifications and extracts the relevant data.
3. `BridgeRuntime.enqueue()` decides whether to forward it: checks the master switch, whether
   the app is allowed (or whether it's a call, which follows its own path independent of the
   app list), the silent/ongoing filters, deduplication, and the per-app rate limit.
4. Whatever passes the filter gets queued - or, if **batching** is on, held for up to the
   configured cooldown and combined with any other message from the same conversation that
   arrives in that window, then queued as a single item.
5. A background worker picks the queued item up, builds the file with `NotificationFormatter`,
   and transfers it.

## How Bluetooth / OBEX works

`ObexObjectPushClient` opens an **RFCOMM** socket to the paired device's standard OBEX Object
Push service UUID (`00001105-0000-1000-8000-00805F9B34FB`), and runs the OBEX sequence by hand
over that socket:

1. **CONNECT** — establishes the OBEX session and negotiates the maximum packet size.
2. **PUT** (one or more packets, continued if the file doesn't fit in one) — sends the file's
   name, MIME type and content.
3. **DISCONNECT** — closes the session.

The implementation is hand-written (no external OBEX library, since OBEX support was dropped
from the Android SDK years ago and there's no well-maintained lightweight alternative). A
watchdog closes the socket if any operation hangs past a configurable timeout, and a transfer
already confirmed by the receiver is never retried even if closing the session afterwards
fails — which is what keeps duplicates from happening.

## Requirements

- An Android phone to act as the source (`minSdk 25`, Android 7.1+; built with
  `compileSdk 37` and `targetSdk 37`).
- A second Bluetooth Classic device that accepts OBEX Object Push (tested with an
  **Alcatel 3080A**).
- Android Studio (Ladybug or newer), or JDK 17 plus the included Gradle Wrapper if you prefer
  the command line.

## Required permissions and why

| Permission | What for |
|---|---|
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Read the notifications that get forwarded |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (Android 12+) | See paired devices and transfer over RFCOMM/OBEX |
| `POST_NOTIFICATIONS` (Android 13+) | Show the foreground service's persistent notification |
| `RECEIVE_BOOT_COMPLETED` | Automatically re-enable the bridge after a phone restart |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep the listener running reliably in the background |

The app doesn't request Internet access, has no analytics or telemetry SDKs, and sends no
data outside the device itself except the `.txt` file that goes over Bluetooth to the
receiver you explicitly chose.

## Building

```bash
cd NotificationBridge
./gradlew assembleDebug
```

Clone this repository from GitHub, then run the commands from its root directory.

The APK ends up in `app/build/outputs/apk/debug/`. You can also open the folder directly in
Android Studio and run it from there (it uses the included Gradle Wrapper, no need to install
Gradle separately).

`./gradlew assembleRelease` creates an unsigned release APK. Sign release builds with a private
key kept outside the repository; never commit signing keys or passwords.

## Installing

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

or transfer the APK to the phone and install it manually (you'll need to allow "unknown
sources" for that file).

## Pairing the dumbphone

Bluetooth pairing happens **from the system settings**, not from the app:

1. On the receiving phone, turn on Bluetooth and make it visible/discoverable.
2. On the smartphone, System Settings → Bluetooth → scan for devices → pair with the
   receiver.
3. Confirm the PIN/code if the receiver asks for one.

## Setting up notification access

1. Open Notification Bridge → **Home** tab → "Notification access" button (takes you straight
   to the system settings).
2. Turn on the switch for Notification Bridge.
3. Go back to the app; Home should show "Service enabled".

> **Note:** if you reinstall the app (e.g. a new debug build), Android may not automatically
> reconnect the listener even though the permission is still listed as granted. If forwarding
> stops after a reinstall, manually turn the permission off and back on from the system
> settings.

## Testing the system

1. **Test** tab → "Grant permission / refresh" (this also turns on Bluetooth if it was off).
2. Pick the receiving device from the list and try "Send test file".
3. Check the **History** tab (did it say "Sent"?) and the **Diagnostics** tab to confirm the
   connection and OBEX responses look right, and that the file arrived at the receiver.
4. Only then: grant notification access (previous step), choose which apps to forward in
   **Settings**, and turn on "Enable bridge" (off by default, on purpose).

## Current project status

Functional and in active use for its main use case: forwarding messaging, email and calls
from an Android smartphone to an OBEX receiver (tested with an Alcatel 3080A). It's a
personal/hobby project with a working, minimalist Compose UI (Home, Test, History, Settings,
Diagnostics, About), first-run onboarding, light/dark/system theme, a 7-language picker
(Spanish, English, French, Portuguese, Italian, Dutch, German), an in-memory transfer history,
a diagnostics screen and an optional dumbphone mode.

## Repository structure

```
app/src/main/java/app/notificationbridge/
├── MainActivity.kt              Compose UI shell (swipeable, responsive tab bar) + Home, Test, Settings screens
├── MainViewModel.kt             UI ↔ Settings/BridgeRuntime adapter
├── OnboardingScreen.kt          First-run walkthrough (skippable, reopenable from Settings)
├── AboutScreen.kt               About tab: version, purpose, license
├── HistoryScreen.kt             Recent transfers
├── DiagnosticsScreen.kt         Technical report and copy button
├── diagnostics/DiagnosticsReport.kt  Allow-listed, privacy-filtered report (pure JVM)
├── BridgeApplication.kt         Initializes BridgeRuntime when the process starts
├── BootReceiver.kt              Re-enables the bridge after a device restart
├── ui/theme/Theme.kt            Material 3 light/dark color schemes
├── ui/Scrollbar.kt              Vertical scrollbar modifier (ScrollState/LazyListState)
├── bluetooth/
│   ├── ObexObjectPushClient.kt  RFCOMM socket + OBEX orchestration
│   └── ObexProtocol.kt          OBEX packet encoding
├── data/SettingsRepository.kt   Persistent configuration (DataStore)
├── format/NotificationFormatter.kt  Notification → text/file name
├── model/Models.kt              Shared data models
├── notification/
│   ├── NotificationBridgeService.kt  NotificationListenerService + foreground status
│   ├── NotificationChannels.kt  Channels/ids for the app's own notifications
│   └── ErrorNotifier.kt         Failure alert (dumbphone mode)
└── queue/
    ├── BridgeRuntime.kt         Queue, filters, retries, worker, state
    ├── DuplicateDetector.kt     Repost suppression
    ├── RateLimiter.kt           Per-app burst protection
    ├── RetentionPolicy.kt       Age-based pruning for transfer history
    └── TransferHistory.kt       History list helpers

app/src/main/res/values*/strings.xml   UI strings in 7 languages (es/en/fr/pt/it/nl/de)
app/src/main/res/xml/locales_config.xml  Declares supported locales for Android 13+
app/src/test/java/...   Unit tests (plain JVM): formatter, duplicates, rate limit, history, diagnostics
.github/workflows/ci.yml         Build + tests + lint on every push/PR
```

## Known limitations

- On manufacturers with aggressive battery management (MIUI and similar), you may need to
  manually enable "Autostart" and exclude the app from battery optimization so the listener
  doesn't get killed in the background.
- Call detection depends on the source app tagging the notification as `CATEGORY_CALL`
  (Android's standard convention); if some dialer or VoIP app doesn't do that, that particular
  call won't be detected.
- Reinstalling debug builds may require manually re-enabling notification access (see the
  setup section above).
- No external OBEX library: the implementation covers Object Push only, not other OBEX
  profiles (business cards, sync, etc.).
- The transfer history lives in memory only: it is lost when Android kills the app or the
  process restarts. Notification bodies are not added to history.
- Rate limiting drops, rather than defers, notifications from an app that exceeds 10 per minute.
- On Android below 13, the status/failure notifications may appear in the system language
  instead of the in-app language (a Service context doesn't follow per-app locales there).

## Basic troubleshooting

| Symptom | Where to look |
|---|---|
| WhatsApp (or another app) doesn't show up in "Allowed apps" | Confirm the APK declares `<queries>` in the manifest (required since Android 11) |
| Nothing reaches the receiver, but Home looks fine | Check that "Enable bridge" is ON in Settings — it's off by default |
| Stopped working after reinstalling a build | Turn notification access off and back on from the system settings |
| Duplicates arrive | Check the History tab and Diagnostics report; in a debug build, `adb logcat -s BridgeRuntime` includes filter decisions |
| Some notifications from one app never arrive | In a debug build, `adb logcat -s BridgeRuntime` shows `rate limited` decisions |
| Forwarding silently stopped | Diagnostics → *Listener connected*: if it says `no` while *Notification access granted* says `yes`, toggle notification access off and on |
| A call isn't forwarded | Confirm "Notify calls" is on in Settings; if it still doesn't work, that app may not tag the call as `CATEGORY_CALL` |
| Build fails to compile | Confirm JDK 17 and that `./gradlew` is executable (`chmod +x gradlew`) |
| Build fails with `Can not extract resource from com.android.aaptcompiler.ParsedResource@...` | A string resource has an unescaped apostrophe (`'` instead of `\'`). Run `python3 scripts/check_string_resources.py` to find exactly which one |
| Picking a language does nothing | Should self-recreate the activity automatically (`MainActivity` extends `AppCompatActivity` with an AppCompat `DayNight` theme, which is what `AppCompatDelegate.setApplicationLocales` needs to hook into); if it still doesn't change, please open an Issue |

To report an issue not covered here, open an Issue following the guide in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Privacy

These statements were checked against the code:

- All notification processing happens locally, on the phone itself.
- The app declares no `INTERNET` permission and contains no internet networking code, backend, or
  analytics or telemetry SDKs.
- The configured Bluetooth receiver is the only remote destination for forwarded notification
  data.
- Notification content is **not stored on disk**. Jetpack DataStore persists configuration:
  the selected receiver's name and Bluetooth address, allowed app package names, and preferences.
  Android cloud backup and device transfer are explicitly disabled for app data.
- Transfer history (app name, short notification title, result and error detail) exists in memory
  only; it can be cleared in the app or pruned automatically. Notification bodies are not added
  to history. In-memory queue and batching may hold notification content until transfer or process
  exit.
- **Debug builds** write troubleshooting lines to logcat; release builds do not. Debug log lines
  can include package names, receiver addresses and title-derived file names. Do not share them
  without reviewing and redacting them.
- The **Copy diagnostic info** text contains the app version, Android version and device model,
  phone model, on/off states, configuration and counts. It indicates whether a transfer failed,
  without exception details or event timestamps. Review it before sharing. It omits notification
  titles and bodies, receiver name and Bluetooth address, and allowed app names.

## License

[MIT](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The change history is in
[CHANGELOG.md](CHANGELOG.md).
