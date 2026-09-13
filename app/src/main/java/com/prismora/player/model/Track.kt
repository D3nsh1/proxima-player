package com.prismora.player.model

import android.net.Uri

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mime: String? = null,
    val extension: String = "",
    val folderPath: String = "",
    val localArt: ByteArray? = null,
    val onlineArtUrl: String? = null,
    val modifiedMs: Long = 0L,
    val trackNumber: Int = 0
) {
    val signature: String get() = "${title}|${artist}|${durationMs}".lowercase()
}

data class LyricsResult(val plainLyrics: String?, val syncedLyrics: String?)

data class LyricLine(val timeMs: Long, val text: String)

data class LibraryScanState(
    val running: Boolean = false,
    val loaded: Int = 0,
    val stage: String = "",
    val restoredFromCache: Int = 0
)

data class OutputStatus(
    val connected: Boolean,
    val deviceName: String = "Not connected",
    val sampleRate: Int = 0,
    val bits: Int = 0,
    val format: String = "",
    val exclusive: Boolean = false,
    val bitPerfect: Boolean = false,
    val backend: String = "",
    val note: String = ""
)

enum class AudioRoutingMode(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    NATIVE_BIT_PERFECT("native_bit_perfect", "Native bit-perfect"),
    COMPATIBILITY("compatibility", "Compatibility");

    companion object {
        fun fromId(id: String?): AudioRoutingMode =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}


enum class RepeatMode(val id: String, val label: String) {
    OFF("off", "Repeat off"),
    ALL("all", "Repeat all"),
    ONE("one", "Repeat one");

    companion object {
        fun fromId(id: String?): RepeatMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class PlaylistOrganizationMode(val id: String, val label: String) {
    REFERENCE("reference", "Reference only"),
    COPY("copy", "Copy to playlist folder"),
    ORGANIZED("organized", "Organized mode");

    companion object {
        fun fromId(id: String?): PlaylistOrganizationMode =
            entries.firstOrNull { it.id == id } ?: ORGANIZED
    }
}

enum class AudioOutputKind(val label: String) {
    USB("USB DAC"),
    BLUETOOTH("Bluetooth"),
    WIRED("AUX / wired"),
    SPEAKER("Phone speaker"),
    OTHER("System output")
}

data class AudioOutput(
    val id: Int,
    val name: String,
    val kind: AudioOutputKind,
    val sampleRates: List<Int> = emptyList(),
    val channelCounts: List<Int> = emptyList(),
    val nativeBitPerfectFormats: List<String> = emptyList()
) {
    val supportsNativeBitPerfect: Boolean get() = nativeBitPerfectFormats.isNotEmpty()
}

enum class PlaybackPhase { IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR }

data class WrappedTrackStat(
    val id: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val listenedMs: Long
)

data class WrappedArtistStat(
    val artist: String,
    val playCount: Int,
    val listenedMs: Long
)

data class WrappedStats(
    val totalListeningMs: Long = 0L,
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val topTracks: List<WrappedTrackStat> = emptyList(),
    val topArtists: List<WrappedArtistStat> = emptyList()
)

enum class EqFilterType(val id: String, val label: String, val apoCode: String) {
    PEAK("peak", "Peak", "PK"),
    LOW_SHELF("low_shelf", "Low shelf", "LSC"),
    HIGH_SHELF("high_shelf", "High shelf", "HSC"),
    LOW_PASS("low_pass", "Low pass", "LP"),
    HIGH_PASS("high_pass", "High pass", "HP");

    companion object {
        fun fromId(id: String?): EqFilterType = entries.firstOrNull { it.id == id } ?: PEAK
        fun fromApo(code: String): EqFilterType? = when (code.uppercase()) {
            "PK", "PEQ" -> PEAK
            "LS", "LSC" -> LOW_SHELF
            "HS", "HSC" -> HIGH_SHELF
            "LP", "LPQ" -> LOW_PASS
            "HP", "HPQ" -> HIGH_PASS
            else -> null
        }
    }
}

data class EqBand(
    val id: Int,
    val enabled: Boolean = true,
    val type: EqFilterType = EqFilterType.PEAK,
    val frequencyHz: Float = 1_000f,
    val gainDb: Float = 0f,
    val q: Float = 1f
)

data class DspSettings(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val bands: List<EqBand> = defaultEqBands(),
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Float = -12f,
    val compressorRatio: Float = 3f,
    val compressorAttackMs: Float = 12f,
    val compressorReleaseMs: Float = 180f,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -1f,
    val crossfeed: Float = 0f
)

data class DspPreset(
    val id: String,
    val name: String,
    val preampDb: Float,
    val bands: List<EqBand>
)

enum class VisualizerStyle(val id: String, val label: String) {
    BARS("bars", "Bars"),
    LINE("line", "Line"),
    RADIAL("radial", "Radial"),
    MINIMAL("minimal", "Minimal");

    companion object {
        fun fromId(id: String?): VisualizerStyle = entries.firstOrNull { it.id == id } ?: BARS
    }
}

fun defaultEqBands(): List<EqBand> {
    val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
    return frequencies.mapIndexed { index, frequency ->
        EqBand(id = index + 1, frequencyHz = frequency, q = if (index == 0 || index == frequencies.lastIndex) .8f else 1f)
    }
}
