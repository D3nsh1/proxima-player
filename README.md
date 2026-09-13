# Prismora 3.13.1

Android local music player focused on a liquid-glass/anime interface, local libraries and direct audio-output control.

## 3.9 changes

- Spotify integration has been removed completely.
- Added a classic Android media notification for local playback.
- Notification includes album artwork, Previous, Play/Pause, Next and Shuffle.
- The expanded action row intentionally uses four actions, leaving one action slot free for a future control.
- MediaSession metadata exposes track title, artist, album, duration and playback position so supported Android versions can show a seek/scrub control.
- Tapping the notification returns to Prismora.
- Shuffle in the notification controls the same shuffle state as the app.

## Existing playback

- Local FLAC/WAV/MP3/M4A and other decoder-supported audio.
- USB DAC, wired/AUX, Bluetooth and phone-speaker routes.
- Queue, shuffle, sleep timer, folders/artists/albums, synced lyrics, artwork cache and listening stats.
- Theme system and 120 Hz UI support remain intact.

## Build

Open `MikuGlassPlayer3` in Android Studio, allow Gradle sync to finish, connect the Android device and press Run. The old Rust/librespot build step is no longer required.


## 3.10.0 — Persona 3 Reload menu theme
- Reworked P3R theme around the Reload menu language: angular white/blue cut panels, cyan glass shards, red accent strokes and animated clock motif.
- Makoto artwork is integrated into the background at low opacity.
- P3R-specific header, Now Playing card, playback controls, feature buttons, navigation and generic panels.
- Faster horizontal menu-like page transitions when the P3R preset is active.

## 3.11.0 — Audio routing

- Settings / Audio page added.
- Auto, Native bit-perfect and Compatibility routing modes.
- Android 14+ USB BIT_PERFECT mixer path using AudioMixerAttributes + exact AudioTrack format.
- Automatic fallback to AAudio can be enabled/disabled.
- USB devices show Android-exposed native bit-perfect formats.
- AAudio Exclusive is no longer automatically labelled bit-perfect.


## 3.12.0 — Master roadmap foundation
- Settings now mirrors the master Prismora roadmap: Appearance, Audio, Library, Playback, Visualizer, Performance and Pro.
- Development Pro gate added: future paid features show only an OK popup; no billing is included.
- Real repeat Off/All/One behavior added.
- Visualizer smoothing, sensitivity and reflection are adjustable and persisted.
- Lite Mode is functional for backdrop/visualizer rendering.
- Playlist file-organization preference is persisted.
- Pro EQ/AutoEQ/dynamics/customization entries are scaffolded without pretending the DSP engine already exists.

## 3.12.3 — No-paywall build

- Removed the development Pro gate, unlock state, popup and reset flow.
- All features that are already implemented are directly accessible.
- Roadmap-only features are labelled Planned until their real engine exists.
- The complete version-history master file is included with the project.

## 3.13.0 — Equalizer + expanded themes

- Equalizer is a real destination in the bottom navigation.
- Real-time PCM DSP supports up to 24 parametric filters: peak, shelves and low/high pass.
- Preamp, compressor, limiter and crossfeed process 16/24/32-bit and float PCM before output.
- Built-in presets, Equalizer APO/AutoEQ text import/export and automatic per-device profiles.
- DSP forces the compatibility PCM route and reports `DSP ACTIVE`; it never claims bit-perfect playback.
- Visualizer modes: Bars, Line, Radial and Minimal, using reusable 1024-point FFT buffers.
- Player layout now has Previous/Play/Next plus Lyrics on the left and Repeat on the right.
- Added Prismora Glass/OLED, Insomnia, Cyberpunk, CRT, Y2K, Vaporwave, Cassette, MiniDisc and Portable Player themes.

## 3.13.1 — Cassette hold-to-fast-forward

- Cassette theme replaces the normal seek slider with a blue Prismora personal-stereo cassette deck.
- The deck shows album art, title/artist, animated reel position, tape progress and elapsed/total time.
- Seeking is intentionally fast-forward-only: hold the yellow `HOLD ≫` control, preview the target, then release to seek once.
- Cyberpunk now uses yellow as its primary accent; cyan remains the secondary neon color.
