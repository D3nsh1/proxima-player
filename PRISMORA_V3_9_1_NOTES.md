# Prismora v3.9.1 — Google Play package rename

- Changed Android namespace from `com.miku.glassplayer` to `com.prismora.player`.
- Changed Android application ID from `com.miku.glassplayer` to `com.prismora.player`.
- Moved Kotlin source packages to `com.prismora.player`.
- Updated notification action identifiers to the new package.
- Updated native AAudio JNI symbols to match the new Kotlin package.
- App display name remains **Prismora**.
- Version bumped to `3.9.1-prismora-package` / versionCode `18`.

Important: `com.prismora.player` should be treated as the permanent Play Store application ID once the first production release is published.


## 3.10.2
- Persona 3 Reload theme: stronger now-playing highlight in Library and Queue (white active panel, pink accent, PLAYING badge).
- Visualizer updated to better fit P3R look with sharper cyan/white bars and lighter reflection.
- Spectrum smoothing reduced for a more responsive motion.
