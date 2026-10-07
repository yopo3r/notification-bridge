# Security Policy

## Supported versions

Security fixes go into the latest release and the main branch. Older versions aren't patched, so if you hit a problem on one, update first and check whether it still happens.

## Reporting a vulnerability

Please report security problems privately, not in a public issue.

Use GitHub's private vulnerability reporting: open the repository's Security tab and choose Report a vulnerability. If that option isn't available, open an issue that only says you have a security report, without any details, and ask for a private way to send it.

It helps if the report includes:

- the app version (shown on the About tab) and your Android version and phone model;
- what you found and why you think it is a security or privacy problem;
- the steps to reproduce it, or a short proof of concept;
- the impact as you see it.

Don't include real notification content, phone numbers or Bluetooth addresses. Made-up or redacted values are enough to show the problem.

This is a one-person project, so a reply can take a few days. Once a fix is released, the issue can be described publicly, and you'll be credited in the changelog if you want to be.

## What is in scope

- How the app handles notification content: the listener, the queue, batching and formatting.
- The Bluetooth and OBEX code, including how it parses what a receiver sends back.
- Importing configuration and theme files.
- What the app stores, logs or exposes on the phone: settings, logcat output, the diagnostics report, backups and screenshots.

## What is not in scope

- Bugs in Android, the Bluetooth stack, a manufacturer's software or the receiving phone. Those should go to their vendors.
- What the receiver does with a file after it accepts it, such as where it is stored and who can read it.
- Attacks that need a modified build of the app, a rooted phone, or access to an unlocked phone.

## How the app treats data

Some behavior is by design, so it helps to know it before reporting it. The app sends notification text to the paired receiver you selected, as a plain text file, over Android's secure RFCOMM connection. It adds no encryption of its own, and it doesn't try to recognize or hide sensitive content such as one-time codes. Only the apps you allow are forwarded. The app has no `INTERNET` permission, doesn't write notification content to disk and keeps no analytics. The full description is in the [Privacy section of the README](README.md#privacy).
