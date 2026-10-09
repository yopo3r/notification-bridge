# Privacy policy

Notification Bridge, version 1.0.0 and later. Last updated 8 October 2026.

Notification Bridge is free software that runs entirely on your phone. It has no server, no account and no Internet permission, so the developer receives no data from you.

## What the app reads

With the notification access you grant in Android's settings, the app reads the app name, title and text of notifications from the apps you select in Settings → Forwarding, and incoming-call notifications if you leave Notify calls on. These can include private messages and one-time codes. It ignores every other app.

## Where it goes

The text of each forwarded notification is sent over Bluetooth to the one receiver you chose, as a text file or a vMessage. Nothing is sent anywhere else. The app does not encrypt the file itself, relies on Android's paired Bluetooth connection, and does not detect or hide one-time codes. What the receiver does with the file afterwards is outside the app's control, so only select apps whose notifications you are comfortable having on that device.

## What it stores

- Settings (receiver name and Bluetooth address, selected app package names, filters and preferences) are stored on the phone with Jetpack DataStore. Android backup and device-to-device transfer are turned off for this data.
- Notification content is never written to disk. While a notification waits to be sent it is held in memory; a failed one that can be retried stays in memory for at most 10 minutes (five items at most). Everything is lost when the app process ends.
- The transfer history lists the app name, a title of at most 40 characters, the result and an error detail, in memory only, never the message text. You can clear it at any time, and it clears itself after 24 hours by default.
- Debug builds write troubleshooting lines to logcat; release builds do not log.

## What it does not do

No analytics, no advertising, no crash reporting, no tracking, no third-party SDKs, and no sharing of data with the developer or anyone else.

## Your controls

Revoke notification access, Bluetooth permission or notification permission in Android's settings at any time; switch off Enable bridge in Settings → Connection; clear the history in the History tab; or uninstall the app, which removes everything it stored. Configuration files you export are plain text you control; the receiver's Bluetooth address is left out unless you choose to include it.

## Children

The app is not directed at children and collects nothing from anyone.

## Changes and contact

Changes to this policy are listed in [CHANGELOG.md](CHANGELOG.md). Questions: open an issue at https://github.com/yopo3r/notification-bridge/issues. Security problems: see [SECURITY.md](SECURITY.md).
