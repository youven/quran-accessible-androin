# Architecture

## Current
Single Android app module. MainActivity hosts a small Compose setup/preview screen.
A local SharedPreferences flag persists the initial accessibility preference. This is a starter, not the final architecture.

## Planned structure
- core/model: edition, page, ayah reference, reciter and tafsir models.
- core/data: repositories connecting validated remote content with Room and downloaded files.
- core/audio: Media3 playback and media session.
- feature/mushaf: fixed-page renderer, zoom transform and matching ayah bounds.
- feature/accessible: reflowing text with per-ayah semantics.
- feature/tafsir, feature/search, feature/downloads and feature/settings.
- DataStore replaces the initial preference storage as settings expand.

UI -> ViewModel -> Repository -> local data / remote adapter.
Downloaded local data is the primary reading source. Remote failures must not erase valid local data.
All views share the same ayah identity and edition. Reader implementations must not invent separate verse numbering.

## Next engineering tasks
1. Generate and commit the official Gradle 8.11.1 wrapper; build and lint with JDK 17 / SDK 35.
2. Select and document a licensed reference edition and sample pages with bounds.
3. Implement page rendering and compare with the reference before integrating all pages.
4. Add local metadata, bookmarks and accessible verse reader.
5. Add audio and tafsir, then offline downloads.
6. Run device, accessibility and content verification before publishing.
