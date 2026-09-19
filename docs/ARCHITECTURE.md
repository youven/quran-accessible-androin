# Text reader architecture

- `PageRepository`: bounded HTTPS downloads on IO, cancellation checks, all API pagination, session cache of three pages, transient Typeface files. A page is ready only when its text, matching font and chapter metadata load.
- `PageParser`: pure data validation; retains original word order, glyphs and page/line indices. Unicode remains separate from display glyphs for accessibility.
- `MushafTextView`: native Canvas text, QCF word ligatures placed right-to-left at their natural widths; one font size per page, centered short lines. No bitmap page rendering. Original line assignments are retained at every zoom.
- `PageViewport`: platform-independent zoom anchor and pan constraints. Portrait fits page, landscape fits width. Rotation resets viewport to an appropriate fit.
- `ReaderScreen`: compact controls, page jumps and bookmarks, loading/retry states; separate reflowable Unicode reading mode with scalable text and TalkBack semantics.

Edition is QCF V2 / mushaf 1 throughout. No mixing V1 page indices with V2 fonts. The first two pages retain their exceptional eight-line structure. Titles use the provider’s surah-name font; the surrounding printed ornaments are not yet reproduced. Full print facsimile is not claimed.

No audio, tafsir or search implementation yet. No persistent offline font cache. Existing image cache from 0.2 is no longer read; it is not deleted automatically.
