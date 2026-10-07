# Contributing to Notification Bridge

Thanks for taking an interest. This is a small app with a narrow purpose, so the process is light.

## Reporting a bug

Open an [issue](https://github.com/yopo3r/notification-bridge/issues/new) and include:

- The model and Android version of the phone running the app.
- The model of the receiving device, if it matters. The project has only been tested with an Alcatel 3080A over OBEX Object Push. Other receivers should work but aren't guaranteed to.
- The steps to reproduce the problem.
- The output of Diagnostics → Copy diagnostic info. It contains the phone model, the Android version, the settings and some technical state. It leaves out notification content, exception details, the names of allowed apps and the receiver's name and address. Read it before you post it anyway.
- If that isn't enough, a log from a debug build: `adb logcat -s BridgeRuntime`. Release builds don't write to logcat. The log can contain package names, receiver addresses and file names derived from notification titles, so redact it first.

Never put the content of your notifications, phone numbers or Bluetooth (MAC) addresses in a public issue.

If you think you have found a security problem, don't open an issue. Follow [SECURITY.md](SECURITY.md) instead.

## Suggesting a feature

For anything bigger than a small tweak, open an issue that describes the use case before writing code. It's easier to agree on the approach first than to rework a finished pull request.

## Building and testing

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
python3 scripts/check_string_resources.py
```

The unit tests run on the plain JVM and need no device or Bluetooth hardware. Anything that touches the Bluetooth or OBEX code can only be checked on real devices, which is why pull requests that change it need to say what was tested (see below).

The Python script exists because AAPT2's error for an unescaped apostrophe in a string resource (`Can not extract resource from ...ParsedResource@...`) doesn't say which file or string is wrong. The script does.

## Pull requests

1. Fork the repository and work on a branch with a descriptive name.
2. Keep each pull request to one topic.
3. If you change the Bluetooth or OBEX code or the notification pipeline, say in the description what you tested and on which devices.
4. Run the three commands above before you open the pull request.
5. Add or update unit tests for logic that doesn't depend on Bluetooth hardware. They live in `app/src/test/`.
6. Follow the official Kotlin code style, and keep user-visible text in string resources instead of in the code.

## Translations

`values/strings.xml` is Spanish and is the default. `values-en/strings.xml` is English. Every `values*/strings.xml` file has to define the same set of keys, and plural strings need the forms the language uses.

To add a language:

1. Create `app/src/main/res/values-xx/strings.xml` with every key.
2. Add the language's name, written in that language, as a `language_*` string in every locale, and add a radio option for it in the language picker (Settings → Appearance) in `MainActivity.kt`.
3. Add the locale to `app/src/main/res/xml/locales_config.xml`.
4. Add its code to `ConfigFile.LANGUAGES`, so the language survives a configuration export and import.
5. Update the list of languages in the README.

## What to avoid

- New dependencies without a good reason. The app is deliberately small.
- Committing builds (`*.apk`), keystores, `release-signing.properties` or logcat captures that contain personal data.
- Assuming that every OBEX receiver behaves like the Alcatel 3080A. If your change depends on how one particular device behaves, say so.

## License

By contributing, you agree that your contribution will be released under the project's license (see [LICENSE](LICENSE)).
