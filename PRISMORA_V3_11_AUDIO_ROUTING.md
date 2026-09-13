# Prismora 3.11.0 — Audio routing

New audio route architecture:
- Auto: on USB, try Android 14+ exact BIT_PERFECT mixer first, otherwise compatibility fallback.
- Native bit-perfect: strict USB native mixer mode.
- Compatibility: existing AAudio engine.
- Settings / Audio contains routing mode, fallback, output device and live route status.

Important decoder status:
- WAV PCM 16/24/32-bit is preserved.
- Current MP3/AAC and MediaCodec FLAC path decodes to 16-bit PCM.
- DSF is currently converted to 44.1 kHz float PCM.
- A future direct userspace USB driver / native FLAC path can be added later without changing the Settings / Audio UI.
