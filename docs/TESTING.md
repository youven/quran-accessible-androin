# Verification

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
