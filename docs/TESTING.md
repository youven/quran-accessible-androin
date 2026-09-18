# Verification

## Automated
Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
GitHub Actions runs the same checks and uploads a debug APK on success.
Unit tests verify Arabic/Persian/ASCII page input, malformed/out-of-range rejection, restoration bounds and first/last source URLs.
The source sample manifest records PNG dimensions and SHA-256 after downloading and visually checking pages 1, 2 and 604 on 2026-09-18. These are observational checksums, not a publisher-signed content manifest.

## Device checklist (not yet executed)
- Fresh launch -> start at page 1; previous disabled.
- Jump to ٦٠٤ -> last page with Ikhlas, Falaq and Nas; next disabled.
- Invalid 0, 605, empty and non-numeric entries cannot navigate.
- Open page 2, return home, force-stop and reopen: resume goes to page 2.
- Add a bookmark, reopen app, select it, remove it, and confirm persistence.
- Navigate rapidly on a slow connection: page number and displayed image must agree.
- Open several pages online, enable airplane mode, reopen those pages successfully.
- Request an unvisited page offline: error and retry visible; last successful position preserved.
- Restore network, retry, and confirm the image loads.
- Corrupt a saved PNG in a debug environment: it must redownload instead of displaying corrupt data.
- Simulate storage exhaustion: readable image stays visible and offline-saving warning appears.
- Zoom in/out, pan in all directions, reset, then navigate to another page.
- Rotate portrait/landscape and test small screens and 200% system font scaling.
- Hide tools to maximize the page and restore tools afterward.
- TalkBack reads all controls in logical Arabic order; it must not imply that a page image exposes Quran text.
- Test switch/keyboard access to navigation, dialogs and zoom/pan controls.

## Release gates
Full 604-page review, source permission/attribution review, matching verse metadata, device tests and user accessibility testing remain required. A green compile/lint run does not establish Quran content accuracy or accessibility usability.
