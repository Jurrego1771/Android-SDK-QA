# Changelog

## [10.0.8] — In progress (branch `10.0.8` vs `master`)

### New Features

- **Trick-play / thumbnail preview** — Seekbar now shows frame thumbnails while scrubbing when the content has preview metadata (`preview.jpg`). Integrates the `previewseekbar` and `previewseekbar-media3` libraries. New utilities: `MediastreamTimeBar`, `ThumbnailPreviewLoader`. Preview source is surfaced via `ConfigMain.preview`.

- **EU / Europe CDN zone** — New `isEurope` flag in `MediastreamPlayerConfig` (and matching `environment = EU`) that redirects API and CDN calls to the European zone.

- **Cast mini-player notification redirect** — Tapping the Cast notification while a session is active now navigates back to the active player screen instead of opening a blank activity.

### Bug Fixes

- **Thumbnail preview layout shift** — Player controls were permanently displaced upward whenever thumbnail previews were configured, because `PreviewDelegate.attachPreviewView()` internally sets the preview frame to `INVISIBLE` (which reserves layout space). Fixed by forcing `GONE` after `attachPreviewView` and resetting to `GONE` 400 ms after scrubbing stops (once the 350 ms `PreviewFadeAnimator` fade completes). Controls now only shift while a thumbnail is actively visible.

- **Subtitle VTT MIME type detection** — VTT subtitle tracks with no `Content-Type` header were not loading. The SDK now detects the MIME type by file extension before attempting a network content fetch.

- **VOD end-of-playback reliability** — Fixed several issues at the end of VOD content: `onEnd` and `onNextEpisodeIncoming` callbacks not firing reliably, video restarting instead of stopping, and unintended looping in manual repeat mode.

- **Android Auto artwork fallback** — Android Auto now displays a default poster image when the content has no artwork URL, preventing a blank/broken media card.

### Improvements

- **Keyboard / D-pad accessibility (WCAG 2.4.7)** — Time bar and live indicator now show a visible focus ring when navigated via keyboard or D-pad. General D-pad focus improvements across player controls.

- **Audio repeat mode** — Repeat mode logic now correctly accounts for audio media type to avoid unintended looping behavior.
