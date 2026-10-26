# Romalala — Japanese lyric companion for Android

An on-device prototype that reads visible Japanese text and displays romaji over Spotify. It does not modify Spotify or use unofficial lyrics endpoints.

## Local Git repository

This workspace is `/Users/derrickvanfrausum/Documents/ChatGPT/Romalala/Romalala`. The local repository and Gradle project are named **Romalala**. The GitHub remote is `git@github.com:DerrickDDInAI/romalala.git`.

## Run

1. Open this directory in Android Studio with **JDK 17** and **Android SDK 35**. The Gradle 8.9 wrapper is included.
2. Build and install on an Android 10+ phone:
   ```sh
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```
3. Tap **Allow lyric overlay**, enable the permission in Android Settings, then return and tap **Start singing**.
4. Allow notifications if you want the notification's **Stop** action. Denial does not block capture; you can always stop from the app.
5. In Android's screen-sharing dialog, choose **Spotify only** when available. Open the song's Lyrics view.
6. Stop from the app, notification, or Android's screen-sharing control. Locking the screen also ends the session.

App-only capture requires Android 14+ and device support. With full-screen sharing, any visible app may be recognized; this prototype cannot identify the foreground app. Stop capture before switching to unrelated content. Android protections and secure windows are respected.

## Behavior

- Bundled ML Kit Japanese OCR runs at most once per second, with only one recognition/conversion job in flight.
- The full frame is recognized, scaled to at most 960 pixels wide to bound OCR cost. This retains headings and playback controls that identify the layout.
- Prefer the small current-line preview above the title/artist pair and seek bar. Reject a supposed title/artist pair when it continues an established lyric column: wrapped lyrics near the progress bar must not override a full-screen highlighted lyric. If the preview is absent, look below a recognized Lyrics heading; otherwise find a repeated column of large Japanese text for full-screen lyrics. Coordinates and font sizes are relative to the captured window, rather than a fixed phone resolution.
- Within a lyrics panel, sample the OCR glyph boxes for white text, which marks the active lyric in the supplied blue and green themes. Keep wrapped current rows and following rows until sentence grouping, then show up to two following sentences. When a highlight disappears, promote the previously detected next sentence once and hold it until a real lyric highlight returns. Music-note-only rows are ignored. Match the remembered sentence against the full detected panel so scrolling or changed wrapping does not send the overlay back to earlier lyrics. Before any highlight has been established, use neutral context from the middle of the detected text group.
- Isolated Japanese titles and artwork text without supporting layout cues produce no selection. Lyrics cards below the previous 88% cutoff can now be recognized. The inline preview takes priority over a duplicate lyric in the card.
- Kuromoji supplies dictionary readings, then Android ICU converts kana to Latin letters. Particle readings は→wa, へ→e, を→o are handled by part of speech. Half-width kana is normalized.
- A bounded, in-memory cache stores the last 128 converted sentences for the current session. The overlay rebuilds its text only when the sentence text or highlight roles change.
- The overlay is an inset, rounded dark-plum card branded Romalala. A detected or continuity-selected current sentence uses larger cream type and a lavender current-line badge; up to two following sentences are smaller and muted, without additional labels. Previous sentences are not substituted for missing upcoming lines. Wrapped active rows are joined before conversion, so Japanese words split by wrapping can regain their dictionary reading. Without a highlight or a remembered next sentence, the card displays neutral context without a Current line badge. Empty/error results, hidden captures, resizing, stale capture gaps, and loss of the remembered sentence from the panel reset continuity.
- A compact 28 dp Lumi lantern spirit uses the approved watercolor avatar beside the first sentence, with a three-leaf crown, glowing belly, smooth seated body and short leaf tail. Neutral context slightly dims the same artwork; the expression does not claim playback synchronization. If its width would add wrapping, on narrow cards, or above 1.2× system font scale, a 24 dp version moves into the header. The lyric font is never reduced to fit the companion. Lumi is decorative and excluded from accessibility announcements. In a short window, following sentences are hidden from the bottom until the card fits; the current sentence retains priority.
- In full-screen lyrics, the card is positioned above the estimated playback controls. For inline previews, it sits above the track metadata; card-only layouts use the lower screen area. Window opacity stays within Android's reported limit for passing touches through an overlay.
- Empty/failed recognition clears the overlay. A four-second timeout clears it if capture stops producing results. Android 14+ captured-content visibility changes also hide it.
- Screen rotation and app-window resizing replace the capture surface without reusing the consent token. Android 10–13 use configuration changes to track rotation.
- Capture, conversion, resize, and resource cleanup are serialized on a worker thread. Generation checks reject old results after resize, visibility changes, and Stop.
- Captured images and lyrics are neither written to disk nor sent to a server by application code. OCR is bundled. Third-party SDK behavior is governed by its own documentation; this is not a network-traffic audit.

## Code review and alternatives

The review found and addressed these issues:

| Issue | Implemented change |
| --- | --- |
| Kuromoji's duplicate contributor/license resources prevented APK packaging | Merge the attribution files while retaining the dictionary notice |
| Cropping a bitmap can return the original bitmap; recycling the padded source could recycle the OCR input | Copy visible RGBA rows into a separate owned bitmap; support padding and a last row without trailing padding |
| An image reader could be closed on the main thread while the OCR worker copied its pixels | Capture and surface lifetime now have one worker-thread owner |
| Delayed OCR/conversion could recreate an overlay after Stop, or show text from a previous orientation | Invalidate generations immediately on Stop and on capture changes; check again when publishing to the UI |
| The detector was never closed, and shutdown could discard queued completion work | Keep the worker alive until the active task finishes, then close the detector and quit |
| Blank frames and OCR errors left old lyrics displayed indefinitely | Clear empty/error results, hidden captures, and stale results |
| Stop from the activity started a service even when nothing was running | Use `stopService` directly |
| Android 13+ notification permission was missing, hiding the convenient Stop action | Optional notification permission request and an app-based stop fallback |
| Controls could overlap system bars with target SDK 35 | Scrollable setup screen with system-bar/cutout insets |
| Every frame repeated conversion work; one-character Japanese lyrics were discarded | Bounded conversion cache; accept single-character and half-width kana lines |

### Architecture choices

**Keep OCR for this prototype.** It works with the text the user can already see and needs no lyrics account or backend. Android's [MediaProjection API](https://developer.android.com/media/grow/media-projection) provides explicit capture consent and app-only sharing. Its drawbacks are battery cost, recognition errors, and dependence on screen layout.

**Prefer licensed synchronized lyrics for a product with exact timing.** A lyrics provider such as [Musixmatch](https://developer.musixmatch.com/) could replace screen OCR if suitable access, licensing, Japanese coverage, and synchronized data are available. Pair this with authorized playback information. The [Spotify Android SDK](https://developer.spotify.com/documentation/android) supports playback integration; Spotify's documented [track API](https://developer.spotify.com/documentation/web-api/reference/get-track) does not supply lyric text. This alternative needs provider evaluation and an integration project, so it is not implemented here.

**Keep Kuromoji + ICU for now.** It is already local and dictionary-aware. A different tokenizer or a custom kana table should be chosen only after testing song-specific readings, memory use, and APK size. A kana-only converter would not solve kanji pronunciation. For difficult names or deliberate lyric readings, a small user correction dictionary would be a useful next step.

## Romalala / Lumi design

The visible app name is **Romalala** and the menu headline is exactly **Open a world of songs**. The application ID and source namespace are `com.example.romalala`. Romalala installs separately from the previous app and requires its own overlay and screen-sharing permissions. This release still supports Japanese; additional languages are not implemented.

The native cream-and-lavender menu pairs the approved singing Lumi artwork with a musical staff and **世界の歌 → sekai no uta** example. It retains only the app name, requested headline, language direction, state-dependent main action, and a collapsible setup guide. Open Spotify is available during an active session. No footer slogan, future-language promise, redundant subtitle, separate replay button, or nonfunctional settings icon is displayed.

`drawable-nodpi/lumi_singing.png` is the exact transparent avatar supplied on 2026-10-25, including its smooth lower body and matching feet. The adaptive launcher and compact overlay share this artwork; the themed launcher retains a simplified silhouette with a single pointed leaf tail. The original approved source is preserved in `design-proposals/romalala/lumi-approved.png`.

Tapping the illustration replays the pronunciation example. A note moves along the staff as the three Latin groups appear, while the Japanese characters remain visible. It plays once on first appearance and finishes with the complete phrase. System-disabled animation and touch exploration skip motion. The illustration has an accessible replay action; the small overlay avatar is decorative. At large text sizes, the illustration stacks above the phrase. The menu is scrollable and its content width is capped in landscape.

The main action follows the actual permission/session state: **Allow lyric overlay → Start singing → Stop overlay**. The setup guide explains screen sharing, approximate lyric detection, pronunciation versus translation, on-device processing, and how to stop. Service state changes update an open menu, including capture ending outside the app. Insets preserve access around system bars and cutouts.

## Remaining limitations

- Updates are limited to approximately one per second plus OCR latency. One current sentence plus two following sentences is a target, not a guarantee: inline previews, the end of a song, clipped lyrics, or limited card space may provide fewer. The app does not invent missing lyrics. Highlight selection follows visible white text when it is distinct; it is not exact karaoke timing. During a highlight gap, the remembered next sentence is an inference and stays selected until a real highlight returns. If it is no longer recognized in the panel, selection returns to neutral geometric context.
- The detector uses text geometry and UI anchors rather than a trained layout model. Missing OCR anchors, uncommon layouts/localizations, heavily clipped cards, very small text, or different highlight colors can still yield missing or incorrect selections. Manual region calibration would be a useful fallback for those cases.
- Overlay placement estimates control positions from OCR and scales them into the available screen area. Split-screen windows, unusual controls, or capture/display differences can produce offsets. The card can still cover original lyrics. Very long text or large accessibility fonts may truncate context.
- ICU may produce macrons for long vowels; this is not an ASCII-only romaji mode. Dictionary readings cannot reliably infer every name, artistic kanji reading, or pronunciation chosen by a singer.
- The user confirmed capture, overlay display, and romaji conversion work on their phone. The redesigned card and updated placement still need a phone check; screenshot geometry tests do not establish Android ML Kit accuracy or playback behavior.

## Verification

Romalala 0.6.3 (version code 11), 2026-10-25: uses the new application ID `com.example.romalala`, matching the source namespace. The packaged activity, capture service, permissions, and app labels were verified. All 51 tests, `lintDebug`, and `assembleDebug` pass; lint reports 0 errors and 4 existing warnings. This build installs separately from the previous app. Enable overlay permission and start screen sharing for this new installation. The local Git repository remains in this workspace under **Romalala**, with the GitHub remote configured for `DerrickDDInAI/romalala`. No physical-device installation or playback check was performed. Deliverable: `../Romalala-0.6.3-debug.apk`.

Romalala 0.6.2 (version code 10), 2026-10-25: the local repository, Gradle project, source namespace (`com.example.romalala`), capture service, worker/display names, service actions, and overlay branding resource now use Romalala. This earlier build retained the previous installation ID; 0.6.3 supersedes it with the new Romalala package. The debug signing certificate matches 0.6.1. All 51 tests, `lintDebug`, and `assembleDebug` pass; lint reports 0 errors and 4 existing warnings. The packaged app label is Romalala across every packaged locale. No physical-device installation or playback check was performed. Deliverable: `../Romalala-0.6.2-debug.apk`.

Romalala 0.6.1 (version code 9), 2026-10-25: all 51 tests, `lintDebug`, and `assembleDebug` pass. Lint reports 0 errors and 4 existing warnings. Native renders cover the simplified menu at 320 dp, 1.5× and 2× font scale, landscape, expanded setup guide, active session, conversion midpoint, launcher icon and compact overlay. The approved PNG retains transparency. The APK uses the same package and signing certificate as 0.6.0. No physical-device installation, Spotify playback, or real Android permission-dialog check was performed for this version. Deliverable: `../Romalala-0.6.1-debug.apk`; previews: `../design-proposals/romalala/implemented-0.6.1/`.

Romalala 0.6.0 (version code 8), 2026-10-25: all 50 unit tests and the debug build pass. Android lint reports 0 errors and 4 existing warnings (target SDK, two dependency versions, and backup configuration). Native renders were inspected for the menu, a conversion middle frame, active-session menu, adaptive launcher icon, and overlay. Tests cover narrow (320 dp), large-font (1.5×), landscape, permission/session action states, animation cancellation restoring all pronunciation groups, and transparent mascot rendering; the existing lyric-selection and overlay-layout regressions remain passing. Final Lumi has one short, softly scalloped leafy tail close to its body. The new APK uses the same application ID and signing certificate as the supplied 0.5.2 APK. Physical-device playback, real permission dialogs, and launcher behavior on a phone remain unverified for this release.


```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Verified on 2026-10-16: debug APK build succeeded; all 32 tests passed; Android lint reported 0 errors and 5 warnings. The remaining warnings concern the existing target SDK/dependency versions, backup configuration, and missing launcher icon. These are release-readiness follow-ups. Capture, conversion, and the existing overlay were confirmed on the user's phone. The redesigned card and updated placement still need a device check.

Verified on 2026-10-24 for lyric continuity: all 43 tests passed and the debug APK built successfully. Android lint reported 0 errors and 5 existing warnings. The added sequence regressions cover highlight loss, highlighted music notes, repeated frames without highlighting, scrolling and changed wrapping, repeated lyric text, real highlights after inference or seeking, resets, changed songs, and the end of visible lyrics. Live Spotify playback on a phone has not been verified for this fix.

The 2026-10-25 follow-up addresses a separate full-screen layout misclassification reported with two Ponyo screenshots. A wrapped lyric above the seek bar could resemble track metadata and make a later lyric appear to be the inline current-line preview. The new regression reproduces that failure using screenshot geometry with an alternate OCR box for the partly obscured Japanese row, and passes with the lyric-column check. Both measured frames, the alternate box at three capture scales, and a genuine wrapped preview with Japanese track metadata are covered. Version 0.5.2 (code 7) passes all 46 tests and builds successfully. These tests use macOS Vision measurements plus an annotated OCR variation; they do not run Android ML Kit on the screenshots or establish live playback behavior.

Tests cover unpadded/padded image buffers, missing last-row padding, crop offsets, stale-result rejection, all three lyrics layouts, wrapped text, duplicate preview/card content, Japanese metadata rejection, scaling, white-highlight colors, particles, half-width kana, multiline conversion, highlight propagation into the card, sentence grouping, and native card rendering at standard/narrow widths, long lines, and 1.4× font scale. The preview check verifies that the avatar never overlaps the primary text and that the sample lyrics remain complete within the card. Bitmap/ICU tests use Robolectric with Android API 29.

`app/src/test/resources/spotify-layouts.json` contains text boxes measured from the six supplied screenshots using macOS Vision. Lyric/metadata text is replaced by placeholders; the screenshot images remain in the user's reference folder. One regression test checks all six layouts at three scales. Highlight flags in the blue full-screen fixture are annotated from the screenshot. This validates selection on measured boxes, not the Android OCR model itself. `app/build/reports/overlay-preview/` contains actual Android view renders for the current-line, neutral-context, narrow, long-line, large-font, and short-window card states. These are native view renders at the actual overlay opacity, not device captures.

Before a release, exercise on Android 10/13 and 14/15+:

- Grant/deny/cancel permissions; double-tap Start; Stop before any lyrics appear.
- Rotate during OCR; hide/show the selected Spotify window; leave Lyrics view.
- Stop while OCR is running, revoke overlay permission, lock the phone, and stop from Android's sharing control. No text should reappear afterward.
- Try long lines, mixed English/Japanese, repeated lyrics, landscape, and large system text.

## Dependencies and licenses

- ML Kit Japanese OCR: `com.google.mlkit:text-recognition-japanese:16.0.1` (bundled model).
- Kuromoji IPADIC: `com.atilika.kuromoji:kuromoji-ipadic:0.9.0` (Apache-2.0; retain dictionary notices).
- Android ICU transliteration: Android 10+.
- AndroidX Activity/Core; JUnit and Robolectric for tests.

Review dependency licenses and dictionary attributions before distribution. Avoid saving or redistributing copyrighted lyrics without permission.
