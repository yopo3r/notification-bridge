# Contributing to Notification Bridge

Thanks for your interest in the project. It's a small, single-purpose app, so the rules are
simple.

## Reporting a bug

Open an [Issue](../../issues/new) and include:

- Model and Android version of the phone running the app (the "smartphone").
- Model of the receiving device, if relevant (the project was tested against an
  **Alcatel 3080A** via OBEX Object Push; other OBEX receivers should work but aren't
  guaranteed).
- Steps to reproduce the issue.
- The output of **Diagnostics → Copy diagnostic info**. Review it before posting: it includes
  the phone model, Android version, settings and technical state, but omits notification content,
  exception details, allowed app names, and the receiver's name and address.
- If the problem needs more detail, a log from a **debug build** using
  `adb logcat -s BridgeRuntime` (release builds don't write to logcat). Review and redact it
  before sharing; it can contain package names, receiver addresses, and title-derived file names.
- **Never** include the actual content of your notifications, phone numbers, or MAC
  addresses in a public issue.

## Proposing a feature

Open an Issue describing the use case before sending a large Pull Request, to align
expectations first.

## Pull Requests

1. Fork the repo and work on a branch with a descriptive name.
2. Keep changes focused — one PR, one topic.
3. If you touch the Bluetooth/OBEX logic or the notification pipeline, explain in the PR
   description what you tested and on which device(s).
4. Run `python3 scripts/check_string_resources.py` and
  `./gradlew testDebugUnitTest lintDebug` locally before opening the PR. The first check exists
  because AAPT2's error for an unescaped apostrophe
   in a string resource is close to useless (see the troubleshooting table in the README).
5. Add or update unit tests for new logic that doesn't depend on real Bluetooth hardware
   (see `app/src/test/`).
6. If you add or change a translation, keep the same set of string keys across every
   `values*/strings.xml` file (English is the most complete reference).

## What to avoid

- Don't add new dependencies without justification — the project is intentionally kept
  lightweight.
- Don't commit builds (`*.apk`), keystores, or logcat captures containing personal data.
- Don't assume every OBEX receiver behaves the same way; if your change depends on a specific
  device's behavior, say so explicitly.

## License

By contributing, you agree that your code will be published under the project's license
(see [LICENSE](LICENSE)).
