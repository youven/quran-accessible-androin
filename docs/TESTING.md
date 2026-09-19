# Verification

## 0.3.3: complete page recovery and font cmap validation (2026-09-19)

Two additional failures were reproduced against the public provider:

- Pages 5 and 100 contain an ordinary U+0020 separator between a rub-el-hizb
  ornament and its word. Their QCF fonts do not map that separator. The new
  `FontCoverage` reads the font's Unicode cmap directly, accepts this layout
  space, and requires every visible symbol to exist in the downloaded font.
  It rejects empty/space-only tokens, other missing characters, malformed fonts,
  and .notdef mappings. No system fallback font participates in validation.
- Page 600's `by_page` response advertised 25 verses but returned 21, starting
  at 100:10. Requests through `by_chapter/100` supplied the missing 100:6–100:9
  with their original QCF codes and page/line metadata. `PageRecovery` fetches
  the relevant chapters whenever the declared page count is incomplete, filters
  words by the requested page, and validates the rebuilt result with the same
  strict `PageParser`. It never reduces the expected count or invents text.

Fresh complete-page cache names avoid reusing incomplete legacy JSON. Recovered
responses are validated before caching. Cached font bytes are decoded and their
coverage checked on every disk load; invalid entries are deleted and fetched
again. Cache writes use a temporary file and rename. Cancellation propagates.

**Results:** 26 JVM tests passed: 8 cmap tests, 5 previous glyph-contract tests,
7 page-parser tests and 6 recovery tests. The page-600 regression uses real
provider fixtures and verifies all 25 verses from 100:6 through 102:8, lines
1–15, and chapter header/basmala positions. It rejects incomplete chapters,
missing chapters and wrong-page recovery.

`tools/FontAudit.java` exercised the production parser, recovery and cmap reader
against downloaded data/fonts for pages **1, 2, 3, 4, 5, 50, 100, 187, 200,
300, 400, 500, 600, 601, 603, 604**: all **1,801 word/verse tokens** passed.
The audit retains the incomplete page-600 response so recovery is exercised.
`tools/fetch_audit_samples.py OUTPUT_DIR PAGE...` downloads these public samples
without bundling fonts in the app. FontAudit can be compiled/run with the
compiled data classes, Kotlin stdlib and org.json on the Java classpath.

Fixtures `page600-incomplete.json`, `chapter100.json`, `chapter101.json` and
`chapter102.json` were retrieved from `https://api.quran.com/api/v4/verses/`
(`by_page/600` and `by_chapter/100` through `102`) on 2026-09-19, using
`words=true&word_fields=code_v2,text_uthmani&per_page=50`. Only fields consumed by
the parser are retained; original codes, Arabic text and metadata are unchanged.

All data-layer Kotlin sources, including `PageRepository`, also compiled with
Kotlin 2.1.20 against the real Android SDK 35 `android.jar`, the Java coverage
class and coroutines. This checks integration signatures but does not package
or run the app.

These are data/JVM checks, not Android rendering verification or an audit of all
604 pages. A full Android build and device acceptance pass are still required.
On a device, install 0.3.3 over the previous version, open 5, 100 and 600, revisit
them from cache, and check that page 600 starts with 100:6 and ends with 102:8.
Then verify portrait/landscape, zoom, TalkBack and recovery after an interrupted
font download. No claim of a pixel-exact printed Mushaf is made.

## 0.3.2: multi-glyph QCF regression (2026-09-19)

Page 2 contains three word tokens with two QCF code points (FC46+FC47,
FC48+FC49, FC67+FC68). Page 1 has only single-code-point tokens. The previous
`Paint.hasGlyph(word.glyph)` call incorrectly requested a single ligature for
each complete token. See the [Android contract](https://developer.android.com/reference/android/graphics/Paint#hasGlyph(java.lang.String)).

`QcfGlyphCoverage` checks each Unicode code point without changing the display
token. The new regression tests model this documented single-glyph predicate,
exercise fixtures for pages 1, 2, 50, 187 and 604, and reject missing symbols and
empty tokens. They also keep surrogate pairs intact. All five new tests passed
using JUnit 4.13.2 on Java 17 (2026-09-19). These are JVM unit tests,
not native Android Paint or on-device rendering tests. The downloaded page-2
font cmap contains all code points in the page-2 API response.

Device acceptance: install 0.3.2 over 0.3.1 and open pages 1, 2, 3, 50 and 604,
including a repeat visit to page 2 with cached fonts. Check that all words and
verse markers appear. Genuine glyph-validation failures now have a separate
message from font-download failures. A full Android build and this device
acceptance pass have not been run for 0.3.2 in this environment.

## Previous 0.3.0 build verification

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with JDK17 and SDK35.

Verified locally on 2026-09-18: `assembleDebug`, `testDebugUnitTest`, and `lintDebug` succeeded. Lint completed with non-blocking warnings (including unused legacy resources and the custom touch view click heuristic; clicks do call performClick through GestureDetector). All 15 unit tests passed (0 failures, 0 errors). A debug APK was produced. This is build/data verification; on-device gestures, TalkBack behavior and all-page visual equivalence remain manual acceptance work.

Unit coverage:
- Arabic/Persian/ASCII page entry and invalid values.
- Real provider JSON: Fatiha/Baqarah eight-line opening pages and page604's three chapters.
- Incomplete pagination, missing verses and wrong-page data fail closed.
- Tawbah has no extra basmala.
- Finger-focused zoom maintains the touched document point.
- Landscape width fit, bottom-of-page reachability, bounded pan/zoom and reset.

Manual device acceptance (not yet completed):
1. Compare pages1,2,50,187,604 against the SAME 1421H Madani edition; then audit all604 pages before release. Include chapter transitions, long verses crossing pages, verse ornaments and special marks.
2. Pinch on a word; it stays beneath the fingers. Check double tap/reset and non-gesture zoom controls.
3. Rotate in both directions; landscape uses width and the final line remains reachable. Test large Android font settings and small screens.
4. Enable TalkBack, open «النص الميسّر وقارئ الشاشة», traverse verses and controls. QCF private glyph codes must never be spoken. Verify Unicode text with an Arabic screen reader.
5. Disconnect during a request; retry succeeds; changing pages during download never displays an old page under a new number.
6. Check first/last navigation limits, bookmarks, restart/resume and Arabic page entry.

Known visual limitation: surah title ornaments and print borders are not reproduced. Tests verify data and gesture math, not theological review or complete visual equivalence.
