# Word Halo / 词间 0.27.0

Native Wear OS vocabulary app for round, square and rectangular watches, with Samsung Galaxy Watch7 44mm as the physical reference device. Wear OS 6 / API 36, Java 11, platform Views and SQLite. No runtime libraries, ads or account system. Permissions: VIBRATE and INTERNET. Package `cn.wordhalo.watch`, versionCode 12. The project owner/user has exercised the core install, learning, haptic, touch-bezel, network and background-exit flows on a physical Watch7 44mm; other display profiles are automated native-render scopes.

Version 0.22 contains six concrete, separate CET4/CET6 books: Star, New Oriental and Flash for each level. The active book is always one exact book bit, never a combined mask. The requested 百词斩六级核心（过考版）2551-word edition remains excluded until its exact list and order can be verified.

## Build

Use JDK 17+ (tested with 21), Python 3, Android SDK platform 36 and build-tools 36.0.0. Building existing bundled assets requires no Python packages or network access:

```text
python tools/build.py --platform SDK/platforms/android-36 --tools SDK/build-tools/36.0.0 --jdk JDK --work build-local --key private-signing --out WordHalo.apk
```

This runs aapt2, javac, D8, zipalign and apksigner and verifies the result. **Reuse the original signing key** for updates without uninstalling; that key remains in the author's private local backup and is not included in this repository. Package and word IDs remain unchanged. Gradle configuration is also included but was not the build path used for delivery; a default Gradle debug key will differ.

## Media and licenses

`assets/visual-mnemonics.json` maps 72 high-frequency abstract words to unique 512×512 offline WebP illustrations and word-specific Chinese visual cues. `assets/stock-photo-curation.json` limits each fallback Commons photo to at most three semantically direct words. The resulting UI pictures 216 words: 72 dedicated illustrations plus 144 curated photo associations from 72 Commons sources. `assets/word-media.json` also provides 15 authored near-sound mnemonics, 50 memory notes and generic IPA. Images are memory associations, not literal definitions or etymology. See ../PHOTO-CREDITS.md and VISUAL-MNEMONICS.md; the app shows source details on each picture's final card.

Photos retain their original licenses (mostly CC BY / CC BY-SA, plus public-domain works); the source code license does not replace them. Bundled photos were resized and encoded to WebP. The ECDICT data license is included in app/src/main/assets/ECDICT-LICENSE.txt. Photo licenses are listed separately. No separate source-code license has been selected for this private project. The APK includes no redistributed third-party pronunciation recordings.

`tools/prepare_media.py` needs Pillow and an ECDICT CSV in the indicated work directory. It uses pinned scenes when present. Newly searched replacements require manual contact-sheet inspection; search results are NOT automatically shown in the app. Generic IPA is from ECDICT. UK/US IPA is retrieved on demand from the public Youdao web dictionary endpoint; it is never fabricated by transforming generic IPA.

`MediaLibrary` pre-warms the platform `TextToSpeech` engine only while the activity is in the foreground. An explicit UK/US tap selects an installed non-network regional voice when available, speaks immediately, and queries regional IPA independently. Missing or failed local voices fall back to the existing HTTPS dictionary recording when online access is enabled. There is no pronunciation prefetch and no persistent audio cache. Leaving the activity shuts down TTS, releases MediaPlayer and audio focus, cancels tracked HTTP connections, destroys request pools and invalidates late callbacks. The fallback stream keeps the 15-second timeout. An upgrade migration removes only known legacy hashed audio files and partials, preserving progress, images and phonetics.

Dedicated illustrations are APK assets and never create a network request. Commons photos and phonetic metadata retain the bounded 48 MiB cache. Request-scoped cancellation disconnects slow metadata/photo connections off the UI thread; canceled callbacks cannot update subsequent pages. Declared response lengths are checked before files are committed. Public services can be unavailable. No learning progress is uploaded, and no phone companion, wake lock or background service is required.

See https://developer.android.com/reference/android/media/MediaPlayer for the platform streaming lifecycle.

## Word data

`words.tsv` stores the 7981-headword union once; its columns are headword, short Chinese meaning, POS, legacy bank bitmask, illustration scene, hint and concrete-book membership mask. `book-orders.tsv` stores each book bit and ordered headword, preserving all six independent memberships and source orders. Schema 2 and existing word-keyed progress remain unchanged, so overlapping books can share personal memory state while showing separate totals and schedules.

```text
python tools/prepare_branded_books.py --dictionary-root path/to/DictionaryData --cet4-flash path/to/cet4.xlsx --cet6-flash path/to/cet6.xlsx --ecdict path/to/ecdict.csv
```

The generator validates every expected book count, keeps source order, hashes all inputs and writes `vocabulary-info.json`. Full source and license details are in ../VOCABULARY-SOURCES.md. `prepare_words.py` is retained only for reproducing older single-list releases and must not generate 0.22+ assets.

## Interaction

Clear haptics use explicitly sustained waveform envelopes because short vendor primitives were too faint in the user's Watch7 test. Correct: 154 ms including a pause; error: 308 ms; bezel: 34 ms; complete: 294 ms. Values are requested timings, not measured motor output. Global touch-haptics and interruption policies are respected. Results cannot be interrupted by lightweight navigation cues. Everything stops on pause.

Page navigation uses two `SceneLayer` instances: the old hierarchy is blocked and animated out as one hardware-composited layer while the new hierarchy enters as one layer. In-page turns use the same coordinator with shorter vertical travel, and answer reveals use a scale crossfade. A new transition settles the previous one before retargeting, so rapid input cannot leave orphan views or competing child animators. `FocusHalo` retargets from its current rectangle with distance-aware timing. The progress arc, button press feedback, rotary scroll and focus scale share the same emphasized-decelerate curve. There are no looping animations. Native rotary events and an outer-screen touch gesture are supported. Rotation never submits a quiz answer. Reduced-motion preference, Android's animator policy and activity pause cancel or bypass every transition.

Version 0.26 adds a visible five-card quiet-study entry. `Engine.startQuiet` uses the normal due/new selection and recall grading, skips the separate exposure pass, and stores a backward-compatible `quiet` flag in session JSON. It never invokes media APIs automatically. A rotary step can reveal the card, then rotary focus selects the visible self-rating buttons; confirmation remains explicit. Each grade commits scheduling and the advanced session in one SQLite transaction. The outer progress arc is based on unique first attempts so a queued retry never moves it backward.

Back navigation now begins only inside the leftmost 20% of the display. The active `SceneLayer` follows the drag with bounded translation, alpha and scale, returns with the shared easing below threshold, and commits on distance or velocity. Vertical intent cancels the candidate before the menu receives a false back. Pause, render and detachment cancel the temporary animator and normalize the layer.

## Focused review

The mistake hub supports non-grading flashcards even while a quiz is in progress. Flashcards can filter all words, trouble-marked words or words practiced today. Unshuffled mistakes are ordered by lapse count, then due time and headword. Optional seeded shuffle is stable across openings. Each scope remembers a headword ID rather than an index, so filtered-list changes do not silently move the bookmark to another word. Existing all-word numeric bookmarks migrate on first use.

The reveal button becomes “再想想” and “下一词”; hiding the definition never modifies scheduling. Empty scopes accept rotary input without division-by-zero errors. Quiz feedback contrasts the chosen meaning with the correct meaning, with an accessible large tap target for associations. Completed sessions offer a unique list of that session's mistakes without changing first-attempt results. Saved Activity state restores nested pronunciation/association/flash pages, their card order and reveal state; audio is never restarted automatically. Word IDs remain unchanged. Schema version 2 adds the daily practice table without altering existing progress or session tables.

## Version 0.21

`MemoryCurve` derives the last review time from the persisted due time and stage interval, predicts exponential retention, and uses 85% as the normal review threshold. Trouble words start in the low-retention zone and remain scheduled for the existing ten-minute correction review. Due eligibility remains exact; when more due words exist than fit in a group, the lowest predicted retention wins.

The fourth dashboard page draws an allocation-free seven-day forecast and reports exact due counts for the next 24 hours and three days. Its page, session and return origin survive Activity recreation. No new database column or migration is required, so existing progress remains compatible.

## Version 0.22

`BookCatalog` defines six exact books and `Store.bookWords` reconstructs each ordered list from `book-orders.tsv`. Book selection is single-choice, split into CET4 and CET6 tabs, and applies to the next group. Progress dashboards, new-word limits, due counts, flashcards and memory-note lists are scoped to the active book. Overlapping headwords retain their word-keyed memory state.

Saved session JSON now stores one `book` bit. Old sessions infer a default book from their legacy level; sessions containing missing IDs or invalid positions are discarded during load while progress, favorites and settings remain intact.

## Version 0.23

Seventy-two CET6-heavy legal, medical, economic, collaboration and academic words now override generic shared photos with dedicated AMOLED-oriented illustrations. Each has a concise Chinese cue that explicitly connects visible elements to the headword's core meaning. The compact study card shows the uncropped square image beside the cue; the detail card enlarges it and provides a generated-art source page. Reviewed atlas hashes and the exact row-major word mapping are stored in `visual-mnemonics.json` and documented in `VISUAL-MNEMONICS.md`.

Fallback stock photos are curated down to 144 associations, with no source image used for more than three words. This removes 52 weak mappings while retaining all 72 reviewed Commons assets for directly related vocabulary. Generated art stays offline; the existing high-resolution network path is only used by Commons photo cards.

Panel surfaces now use quiet gradients and selected states. Rotary focus combines the moving outline with a short card scale transition and a one-shot glow; it never loops. Answer feedback uses a status capsule and the completion page uses the existing vector success artwork. Reduced-motion and lifecycle behavior remain unchanged.

## Version 0.24

UK/US playback now prefers the pre-warmed Wear OS TTS engine and selects the requested regional locale. This removes the DNS, TLS and remote buffering delay from the normal path. The previous dictionary stream remains a controlled fallback when a local regional voice is missing. TTS is shut down on pause/stop, and neither path persists recordings.

The visual system now uses flat OLED surfaces, quieter borders, a single-line rotary focus and restrained scale transitions. UK/US controls and the pronunciation card use a compact typographic hierarchy. Large gradients, focus glow and overshoot were removed while preserving visible selection and reduced-motion behavior.

## Version 0.25

Full-page changes now retain the previous and next View hierarchies briefly and animate each as one composited scene. Forward navigation, back navigation and in-page card turns have distinct direction and distance while sharing one timing system. The old scene intercepts touches until removal, and rapid navigation settles the previous transition before starting another.

Rotary focus retargets from its current bounds with distance-aware duration and a quiet alpha ramp. Answer reveal, pressed controls, menu scrolling and the outer progress arc use the same non-overshooting easing. The motion preference and platform animation policy still disable all of these paths; pause removes outgoing layers and cancels progress/focus animators.

## Version 0.26

The home screen exposes a five-card silent quick-study path that uses the existing due-first scheduler, self-rating rules, retry queue and SQLite progress. A visible reveal button keeps the feature discoverable while a rotary step offers the faster one-handed path. It makes no automatic media or network call, persists the exact card and reveal state, and advances immediately after an explicit remembered/forgotten grade.

Back navigation begins at the left edge and visually follows the finger. Vertical intent is rejected, short drags return with the shared easing and committed drags enter the existing composited back transition. The gesture, quiet layouts and enlarged system text are covered by the API 36 native suite.

## Version 0.27

The coordinate system now separates uniform short-edge scale from a shape-aware horizontal transform. Round displays retain the Watch7 composition exactly. Non-round displays map the original circular safe band outward by 10%, or 16% at the official 225dp large-display breakpoint, while text and image height remain uniformly scaled. Rectangular displays center the resulting 480-unit canvas on the long axis instead of stretching it.

`Dial` renders the existing arc on round displays and a rounded-square perimeter with a top progress segment on non-round displays. Touch-bezel hit testing likewise switches from radial bands to distance from the four physical edges. Crown events, DPAD input, direct touch and left-edge back remain device-independent platform paths.

## Version 0.20

Session configuration supports 5/10/20-word groups and an optional recall mode. Recall state and reveal state are persisted with backward-compatible JSON defaults. Grading before reveal is rejected in both the activity and engine. Immediate retries still cannot inflate first-attempt results. Changes to preferences only affect the next group.

Favorites persist as a copied SharedPreferences string set, independently of scheduling. Each word has an explicit star control and a long-press tool page; the latter hides memory cues while a quiz answer is still pending. Flashcards add a favorites scope. New static vector dashboard graphics show goal ticks and daily activity without animation loops. Larger system fonts use bounded text autosizing. Result and navigation haptic strengths are independent; bookmark feedback is a distinct envelope.

SQLite schema 2 adds `practice(day,word,correct)` with a composite primary key. Each graded first attempt is committed in the same transaction as progress/session, using conflict-ignore insertion for idempotent daily records. Legacy progress is NOT synthesized into daily history. The original progress/state tables and word IDs are preserved; tests open a real schema-1 database and verify its migration.

`assets/memory-notes.json` contributes 50 construction, contrast and imagery cues present in the current union; `VOCABULARY-REVIEW.json` records 68 reviewed meaning/POS corrections. The original ECDICT provenance is retained. These corrections address found issues rather than claiming a complete dictionary audit. `prepare_branded_books.py` applies the reviewed overrides during a vocabulary rebuild.

## Tests

```text
javac -encoding UTF-8 -d test-classes app/src/main/java/cn/wordhalo/watch/BookCatalog.java app/src/main/java/cn/wordhalo/watch/MemoryCurve.java app/src/main/java/cn/wordhalo/watch/Engine.java tests/EngineTest.java
java -cp test-classes cn.wordhalo.watch.EngineTest app/src/main/assets/words.tsv app/src/main/assets/book-orders.tsv
```

EngineTest checks 63,834 assertions across the exact membership/order of all six books, vocabulary choices, quiet-session invariants and 80 complete randomized sessions. DeviceFlowTest uses Robolectric 4.16.1, JUnit 4.13.2 and native Android 16 graphics. Its 42 tests add 480px square, 384px round and 400×480px rectangular profiles, checking scene bounds, text fit, large-display expansion, quiet-study layout and square-edge bezel input. Existing coverage includes offline restoration, composed motion, books, memory scheduling, database migration, all media, haptics, audio fallback and complete background shutdown. Transport/player tests use controlled fakes and make no live network requests.

The supplied screenshots are actual native View renders in the desktop Android test runtime. Physical Watch7 coverage is user verification, recorded separately in validation.json; the automated suite does not pretend to measure motor strength, Samsung bezel delivery, TTS voice quality or Bluetooth-proxy latency. See 使用说明.md and validation.json for the exact scopes.

The native DeviceFlowTest harness was run in the development workspace; its dependency setup is not bundled here. The EngineTest command above is independently runnable. The Gradle configuration is provided for reference and was not used to build the distributed APK.
