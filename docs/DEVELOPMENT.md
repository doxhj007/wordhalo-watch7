# Word Halo / 词间 0.20.0

Personal native Wear OS vocabulary app for Samsung Galaxy Watch7 44mm, 480×480, One UI Watch 8 / Wear OS 6 / API 36. Java 11, platform Views, SQLite. No runtime libraries, ads or account system. Permissions: VIBRATE and INTERNET. Package `cn.wordhalo.watch`, versionCode 5.

The provisional vocabulary is still **1945 ECDICT CET6-extension words**, not the requested 百词斩六级核心（过考版）2551-word edition. The app and guide disclose this gap. Do not relabel it as that book.

## Build

Use JDK 17+ (tested with 21), Python 3, Android SDK platform 36 and build-tools 36.0.0. Building existing bundled assets requires no Python packages or network access:

```text
python tools/build.py --platform SDK/platforms/android-36 --tools SDK/build-tools/36.0.0 --jdk JDK --work build-local --key private-signing --out WordHalo.apk
```

This runs aapt2, javac, D8, zipalign and apksigner and verifies the result. **Reuse the original signing key** for updates without uninstalling; that key remains in the author's private local backup and is not included in this repository. Package and word IDs remain unchanged. Gradle configuration is also included but was not the build path used for delivery; a default Gradle debug key will differ.

## Media and licenses

`assets/scenes.json` pins 83 reviewed Commons images and their authors, source pages and licenses. `assets/word-media.json` maps 339 words to related scenes and provides 22 authored near-sound mnemonics plus generic IPA. Multiple words may share one scene. Images are displayed as associations, not literal definitions or etymology. See ../PHOTO-CREDITS.md. The app shows attribution in each picture's detail pages.

Photos retain their original licenses (mostly CC BY / CC BY-SA, plus public-domain works); the source code license does not replace them. Bundled photos were resized and encoded to WebP. The ECDICT data license is included in app/src/main/assets/ECDICT-LICENSE.txt. Photo licenses are listed separately. No separate source-code license has been selected for this private project. The APK includes no redistributed third-party pronunciation recordings.

`tools/prepare_media.py` needs Pillow and an ECDICT CSV in the indicated work directory. It uses pinned scenes when present. Newly searched replacements require manual contact-sheet inspection; search results are NOT automatically shown in the app. Generic IPA is from ECDICT. UK/US IPA is retrieved on demand from the public Youdao web dictionary endpoint; it is never fabricated by transforming generic IPA.

Only explicit play taps call MediaPlayer. Pronunciation streams directly from the HTTPS UK/US URL with prepareAsync; no audio download or persistent audio cache is used. There is no prefetch UI. Leaving the page, switching words or pausing releases the player, abandons audio focus and invalidates late callbacks. A 15-second preparation timeout provides an explicit retry path. Each replay uses the remote source. The system media stack may buffer the active stream; the app never saves a recording. An upgrade migration removes only known legacy hashed audio files and their partials, preserving progress, images and phonetics.

Photos and phonetic metadata retain the bounded 48 MiB cache. Request-scoped cancellation disconnects slow metadata/photo connections off the UI thread; canceled callbacks cannot update subsequent pages. Declared response lengths are checked before files are committed. Public services can be unavailable. No learning progress is uploaded, and no phone companion, wake lock or background service is required.

See https://developer.android.com/reference/android/media/MediaPlayer for the platform streaming lifecycle.

## Word data

TSV columns remain headword, short Chinese meaning, POS, bank bitmask, legacy illustration scene and hint, preserving the original word IDs and existing progress tables. Schema 2 adds a separate daily practice table. The new image catalog is separate; the 55 legacy vector tags in vocabulary-info.json are not the active photo count. ECDICT license and source hash are embedded in assets.

```text
python tools/prepare_words.py path/to/ecdict.csv
python tools/prepare_words.py path/to/ecdict.csv exact-2551.txt
```

The optional second file must be a verified UTF-8 list with exactly 2551 distinct headwords. The tool checks count and missing definitions before replacing the vocabulary. This is a developer build step, not account sync. A future replacement also needs media and saved-session ID validation before release.

## Interaction

Clear haptics use explicitly sustained waveform envelopes because short vendor primitives were too faint in the user's Watch7 test. Correct: 154 ms including a pause; error: 308 ms; bezel: 34 ms; complete: 294 ms. Values are requested timings, not measured motor output. Global touch-haptics and interruption policies are respected. Results cannot be interrupted by lightweight navigation cues. Everything stops on pause.

FocusHalo animates one outline between real answer rectangles, retargeting from its current position on fast turns. Native rotary scroll events and an outer-screen touch gesture are supported. Rotation never submits a quiz answer. Study cards require an explicit confirmation at the last card before starting a quiz. Reduced-motion preference and Android animation policy are supported. Rapid rotary menu steps accumulate against the destination, while touch cancels that animation. Study/flash/memory cards use a short directional transition. Tapping a word or IPA opens a UK/US pronunciation card and returns to the same learning state; rotation selects an accent without starting audio.

## Focused review

The mistake hub supports non-grading flashcards even while a quiz is in progress. Flashcards can filter all words, trouble-marked words or words practiced today. Unshuffled mistakes are ordered by lapse count, then due time and headword. Optional seeded shuffle is stable across openings. Each scope remembers a headword ID rather than an index, so filtered-list changes do not silently move the bookmark to another word. Existing all-word numeric bookmarks migrate on first use.

The reveal button becomes “再想想” and “下一词”; hiding the definition never modifies scheduling. Empty scopes accept rotary input without division-by-zero errors. Quiz feedback contrasts the chosen meaning with the correct meaning, with an accessible large tap target for associations. Completed sessions offer a unique list of that session's mistakes without changing first-attempt results. Saved Activity state restores nested pronunciation/association/flash pages, their card order and reveal state; audio is never restarted automatically. Word IDs remain unchanged. Schema version 2 adds the daily practice table without altering existing progress or session tables.

## Version 0.20

Session configuration supports 5/10/20-word groups and an optional recall mode. Recall state and reveal state are persisted with backward-compatible JSON defaults. Grading before reveal is rejected in both the activity and engine. Immediate retries still cannot inflate first-attempt results. Changes to preferences only affect the next group.

Favorites persist as a copied SharedPreferences string set, independently of scheduling. Each word has an explicit star control and a long-press tool page; the latter hides memory cues while a quiz answer is still pending. Flashcards add a favorites scope. New static vector dashboard graphics show goal ticks and daily activity without animation loops. Larger system fonts use bounded text autosizing. Result and navigation haptic strengths are independent; bookmark feedback is a distinct envelope.

SQLite schema 2 adds `practice(day,word,correct)` with a composite primary key. Each graded first attempt is committed in the same transaction as progress/session, using conflict-ignore insertion for idempotent daily records. Legacy progress is NOT synthesized into daily history. The original progress/state tables and word IDs are preserved; tests open a real schema-1 database and verify its migration.

`assets/memory-notes.json` provides 51 authored construction, contrast and imagery cues; `VOCABULARY-REVIEW.json` records 68 reviewed meaning/POS corrections. The original ECDICT provenance is retained. These corrections address found issues rather than claiming a complete dictionary audit. The prepare_words.py override source is updated so corrections survive a vocabulary rebuild.

## Tests

```text
javac -encoding UTF-8 -d test-classes app/src/main/java/cn/wordhalo/watch/Engine.java tests/EngineTest.java
java -cp test-classes cn.wordhalo.watch.EngineTest app/src/main/assets/words.tsv
```

EngineTest checks 10018 assertions across all vocabulary choices and 80 complete randomized sessions. DeviceFlowTest uses Robolectric 4.16.1, JUnit 4.13.2 and native Android 16 graphics. Its 31 tests cover progress recovery, native photo decoding, long answer/IPA layout, focus interpolation, accumulated rotary scrolling, haptic policy, remote playback source selection, replay, completion, timeout/error handling, stale prepared callbacks, scoped request cancellation and legacy audio cleanup isolation, filtered/shuffled bookmarks, empty scopes, current-quiz preservation, long mistake contrasts, completed-group review and nested-page Activity recreation, self-rated recall, batch sizes, schema-1 migration, daily deduplication, favorites, independent touch levels, all authored note layouts and large system fonts. All transport and player tests use controlled fakes; they make no live network requests and do not claim physical-watch connectivity validation.

The supplied screenshots are actual native View renders in the desktop Android test runtime. Physical vibration strength, Samsung touch-bezel event delivery and Bluetooth-network behavior still require the user's real watch. See 使用说明.md and validation.json in this directory for delivery checks.

The native DeviceFlowTest harness was run in the development workspace; its dependency setup is not bundled here. The EngineTest command above is independently runnable. The Gradle configuration is provided for reference and was not used to build the distributed APK.
