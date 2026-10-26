# Romalala

**Open a world of songs.**

<img src="app/src/main/res/drawable-nodpi/lumi_singing.png" alt="Lumi, Romalala’s singing lantern spirit" width="128">

Romalala is an experimental Android app that helps you sing along to Japanese songs. It reads lyrics visible in Spotify and displays their pronunciation in Latin letters in a floating overlay.

For example: **世界の歌 → sekai no uta**.

Romalala provides romanization, not translation. It works from the words on your screen and does not modify Spotify or fetch lyrics from a lyrics API.

## Features

- Recognizes Japanese lyrics in Spotify’s inline preview, lyrics card, and full-screen Lyrics view.
- Shows the detected current sentence and up to two following sentences when available.
- Processes text on your device using bundled Japanese text recognition and dictionary-based pronunciation conversion.
- Includes a compact overlay and a scrollable setup screen with support for large system text.

## Build and install

### Requirements

- JDK 17.
- Android SDK Platform 35 and Android SDK Platform-Tools.
- An Android 10 (API 29) or newer device with Spotify installed and Japanese lyrics available.

Open the project in Android Studio and configure its Android SDK location, or set `ANDROID_HOME` for command-line builds. The Gradle wrapper is included.

```sh
git clone https://github.com/DerrickDDInAI/romalala.git
cd romalala
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

To install on a connected device with USB debugging enabled:

```sh
./gradlew installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Use Romalala

1. Open Romalala and tap **Allow lyric overlay**. Enable the permission in Android Settings, then return to the app.
2. Tap **Start singing** and approve Android’s screen-sharing request. Select **Spotify only** when that option is available.
3. Open a Japanese song’s Lyrics view in Spotify. Romalala displays pronunciation over the visible lyrics.
4. Stop with **Stop overlay** in Romalala, **Stop** in its notification, or Android’s screen-sharing control. Locking the screen also ends the session.

Notification permission is optional. Without it, you can still stop the overlay from Romalala or Android’s screen-sharing control.

Sharing a single app requires Android 14 or newer and device support. When sharing the whole screen, stop the overlay before switching to unrelated content: Romalala can read any visible text in the shared screen and cannot identify the foreground app.

## Privacy and permissions

Text recognition and pronunciation conversion run on the device. Application code does not save captured images or lyrics to disk or upload them to a server. Recent pronunciation results are cached in memory for the current session. Third-party SDK behavior is subject to its own policies; these statements do not represent a network-traffic audit.

| Access | Purpose |
| --- | --- |
| Display over other apps | Show pronunciation above Spotify. |
| Screen sharing | Read the lyrics visible in the window or screen you choose to share. Android requests consent when starting a session. |
| Notifications (optional) | Show the active session and provide a Stop action. |

## Known limitations

- Japanese is the only supported language. Dictionary readings may differ from a singer’s intended pronunciation, especially for names and unusual kanji readings. Long vowels may use macrons.
- Recognition runs at most once per second, plus processing time. The overlay does not provide exact karaoke timing; the current sentence is estimated from visible highlighting and recent lyric context.
- Recognition and placement depend on Spotify’s layout, text size, and highlight colors. Scrolling, clipped lyrics, split-screen windows, or layout changes can cause missing or incorrect results.
- The overlay may cover original lyrics. Limited space or missing visible lyrics can reduce the number of following sentences shown.

## Development

The app uses Kotlin and native Android views. Bundled ML Kit recognizes Japanese text, Kuromoji supplies dictionary readings, and Android ICU converts kana to Latin letters. See [app/build.gradle.kts](app/build.gradle.kts) for dependency versions.

Run the automated checks:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Tests cover lyric selection and continuity, pronunciation conversion, capture state, and native view layouts. Screenshot-based fixtures exercise selection on measured text boxes; they do not run Android text recognition on a device. Automated checks do not replace testing screen-sharing permissions, rotation, and Spotify playback on a physical device.

## Contributing

Bug reports and focused improvements are welcome. [Open an issue](https://github.com/DerrickDDInAI/romalala/issues) with reproduction steps, your Android version, and the Spotify view in use. For code changes, explain the behavior being changed and include relevant tests when appropriate.

## License

A project license has not been added. Third-party components retain their respective licenses and attribution requirements.
