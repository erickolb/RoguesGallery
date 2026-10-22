# Changelog

## Unreleased

- Fade out the floating title and controls after 30 seconds without interaction; tap the cover to show them again.
- Keep the controls visible when no covers are enabled.

## 1.0.1 — 2026-10-14

- Refresh collection issue data in the background after every 12 hours of active slideshow playback.
- Reapply current selection settings and account-specific exclusions without leaving the gallery.
- Keep existing covers if refreshing fails; retry after 15 more minutes of playback.
- Pausing the slideshow or leaving the app pauses the refresh clock.

## 1.0 — 2026-10-13

- First public release of Rogues Gallery for Android 8.0 and newer.
- Display individual Have It issue covers and variants in a full-size slideshow with floating controls.
- Configure cover selection, saved selections, exclusions, playback order, 3–600 second timing, and optional cross-fades.
- Cache covers privately and provide an in-app privacy policy.

- Add automated signed APK/App Bundle builds with certificate checks and checksums; matching version tags publish releases, while manual runs only save artifacts.

- Rename the project and repository links to Rogues Gallery / RoguesGallery.
- Change the Android application ID and namespace to `net.chateaulore.roguesgallery`.
- Android treats this as a separate app from the earlier application ID; local settings and sign-in sessions do not transfer.

## 1.0-rc1 — 2026-10-13

- Label the approved 0.2.3 feature set as the first 1.0 release candidate.
- Increase Android versionCode to 10; no application behavior changes.

## 0.2.3 — 2026-10-13

- Open the slideshow with title and controls hidden for a full-size presentation.
- Tap the cover to show or hide a translucent title/control overlay without resizing the image.
- Keep the overlay visible when no covers are enabled so collection options remain accessible.

## 0.2.2 — 2026-10-13

- Configure the slideshow delay from 3 to 600 seconds (default 15 seconds).
- Cross-fade covers by default, with a setting for instant smash cuts.
- Keep the current cover visible while the next image loads.
- Center a compact control row with consistent 56dp button heights.

## 0.2.1 — 2026-10-13

- Start playing automatically when opening the slideshow.
- Show a play icon and Play label while paused, or a pause icon and Pause label while playing.
- Use double-arrow previous/next controls and a three-dot options button, with accessibility labels.

## 0.2.0 — 2026-10-13

- Rename the import action to Load My Collection and update the sign-in instructions.
- Add settings and an in-app Data Use / Privacy Policy, with a matching website policy.
- List the full collection before choosing up to 1000 covers by default; configure the maximum and random or LCG-ordered selection.
- Choose fresh each session by default, or remember eligible selected issues and fill missing/excluded/unavailable slots.
- Configure random or sequential slideshow playback independently of selection.
- Exclude the current cover and manage enabled issues in a full collection checklist.
- Cache displayed covers privately with size/count limits and a clear-cache control.
- Keep exclusions and optional selected issue references separate for each League account.

## 0.1.4

- Minimum viable slideshow confirmed in a signed-in League session.
- Use LCG navy for the launcher icon background.
