# Prismora v3.9 — local playback notification

Changes in this build:

- Spotify has been removed from the app UI, navigation, ViewModel, manifest callbacks, native Rust/librespot sources and build scripts.
- The old Spotify-styled launcher artwork is no longer used; the launcher now uses the Prismora/Miku waveform vector.
- Added a classic Android media playback notification for local music.
- Album artwork is used as the media notification artwork.
- Notification actions: Previous, Play/Pause, Next, Shuffle.
- Only four action buttons are created, intentionally leaving the fifth media-action slot free for a future control.
- MediaSession publishes title, artist, album, duration, current position and seek support. Android/System UI can expose a scrub/seek control where supported.
- Notification progress follows the local Prismora decoder.
- Shuffle from the notification changes the same shuffle state used inside Prismora.
- Tapping the notification opens Prismora.
- Added Android notification runtime permission handling.

The Rust/librespot build step is no longer needed for this version.
