# Content integration

## Candidates
- Al Quran Cloud: https://alquran.cloud/cdn — documented ayah images and recitation CDN. Ayah images are NOT full Madani pages.
- QUL: https://qul.tarteel.ai/resources — downloadable datasets, layouts, tafsir and recitation timing. Not a hosted API.
- Quran image generator: https://github.com/quran/quran.com-images — page generation and glyph bounds. Code and content have separate rights.
- Quran Foundation: https://api-docs.quran.foundation/ — evaluate content access and authentication before integration.

No production page endpoint or content redistribution license has been approved in this repository.

## Required dataset contract
A versioned edition identifies riwayah, layout version, page count, source attribution and redistribution terms.
Each page identifies its image or exact-layout rendering data, dimensions and checksum.
Each ayah identifies surah number, ayah number, trusted Unicode text and page placement.
Bounds must use the same image dimensions and edition; scale them using the same transform as the displayed image.
Recitation records identify reciter and ayah, or validated timestamps in a surah recording.
Tafsir records retain author/source and ayah grouping.

## Rendering decision
Use exact-layout text only after comparison establishes that the supplied fonts and layout reproduce the reference.
Otherwise use verified page images and matching bounds, with separate semantic/text reading mode.
Never reconstruct Quran text with OCR or generative models.
Never present a generic Arabic font or assembled ayah images as an exact Madani page.

## Offline and safety
Download to temporary files, verify hashes and completeness, then atomically activate a dataset.
Keep the previous valid edition when download fails.
Persist user reading state independently from content caches.
Use HTTPS, cache allowed content, and do not embed API secrets in Android.
Any provider requiring a confidential client secret needs a backend or a supported public-client flow.
Free access does not guarantee unlimited requests or redistribution rights.
