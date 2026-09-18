# Architecture

## Implemented in 0.2.0
- `MainActivity`: Arabic home, reading preferences, last successful page and bookmarks.
- `ui/ReaderScreen`: page navigation, validated page jump, loading/error states, bookmark list and zoom/scroll controls.
- `data/MadaniPage`: edition identity, page bounds, source URLs, Arabic/Persian/ASCII numeric input.
- `data/PageRepository`: IO-dispatched HTTPS requests and persistent image storage in filesDir.
- Unit tests exercise numeral parsing, invalid input, restoration boundaries and endpoint numbering.

The repository uses one edition only: KSU/Ayat Hafs png_big. Each page change creates a separate keyed composable. Cancelled loads cannot display their image under a newer page number. Last position changes only when a page loads successfully.

Files are decoded and checked for PNG signature, dimensions and size before saving with temporary-file rename. Corrupt cache entries are retried from the source. Storage errors do not prevent reading a downloaded image. No credentials or network calls run on the UI thread. Network operations have connection and read timeouts. Cancellation checks run between reads; a blocked read can last until its timeout.

## Future structure
- ViewModel and lifecycle-aware state flows as audio and verse-level navigation are added.
- Room for validated local ayah metadata, text, tafsir and search.
- DataStore migration from the initial SharedPreferences settings.
- Media3 for recitation and media session.
- A separate reflowing verse reader with per-ayah semantics.
- Matching ayah bounds from the same page edition before implementing verse selection.
- Versioned content manifests with authoritative checksums and explicit redistribution terms.

No generated Quran text or OCR is used. No text rendering may claim Madani page fidelity until layout and fonts are verified against the reference.
