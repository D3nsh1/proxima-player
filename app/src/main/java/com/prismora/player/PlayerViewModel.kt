package com.prismora.player

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.prismora.player.audio.AudioEngine
import com.prismora.player.audio.LocalDecoder
import com.prismora.player.audio.PcmDspProcessor
import com.prismora.player.audio.SpectrumAnalyzer
import com.prismora.player.library.MusicLibrary
import com.prismora.player.metadata.MetadataService
import com.prismora.player.notification.PlaybackNotificationController
import com.prismora.player.model.AudioOutput
import com.prismora.player.model.AudioRoutingMode
import com.prismora.player.model.DspPreset
import com.prismora.player.model.DspSettings
import com.prismora.player.model.EqBand
import com.prismora.player.model.EqFilterType
import com.prismora.player.model.LibraryScanState
import com.prismora.player.model.LyricLine
import com.prismora.player.model.LyricsResult
import com.prismora.player.model.OutputStatus
import com.prismora.player.model.PlaybackPhase
import com.prismora.player.model.RepeatMode
import com.prismora.player.model.PlaylistOrganizationMode
import com.prismora.player.model.Track
import com.prismora.player.model.VisualizerStyle
import com.prismora.player.model.WrappedArtistStat
import com.prismora.player.model.WrappedStats
import com.prismora.player.model.WrappedTrackStat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val library = MusicLibrary(app)
    private val metadata = MetadataService(app)
    private val audio = AudioEngine(app)
    private val spectrumAnalyzer = SpectrumAnalyzer(40)
    private val preferences = app.getSharedPreferences("player_state", 0)
    private val gson = Gson()
    private val libraryArtworkCache = object : LruCache<String, ByteArray>(24 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = (value.size / 1024).coerceAtLeast(1)
    }

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue
    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current
    private val _cover = MutableStateFlow<ByteArray?>(null)
    val cover: StateFlow<ByteArray?> = _cover
    private val _lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyrics: StateFlow<LyricsResult?> = _lyrics
    private val _lyricLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricLines: StateFlow<List<LyricLine>> = _lyricLines
    private val _phase = MutableStateFlow(PlaybackPhase.IDLE)
    val phase: StateFlow<PlaybackPhase> = _phase
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs
    private val _status = MutableStateFlow(OutputStatus(false, note = "Choose an audio output"))
    val status: StateFlow<OutputStatus> = _status
    private val _audioOutputs = MutableStateFlow<List<AudioOutput>>(emptyList())
    val audioOutputs: StateFlow<List<AudioOutput>> = _audioOutputs
    private val _selectedOutputId = MutableStateFlow<Int?>(null)
    val selectedOutputId: StateFlow<Int?> = _selectedOutputId
    private val _audioRoutingMode = MutableStateFlow(
        AudioRoutingMode.fromId(preferences.getString("audio_routing_mode", AudioRoutingMode.AUTO.id))
    )
    val audioRoutingMode: StateFlow<AudioRoutingMode> = _audioRoutingMode
    private val _audioFallbackEnabled = MutableStateFlow(preferences.getBoolean("audio_fallback_enabled", true))
    val audioFallbackEnabled: StateFlow<Boolean> = _audioFallbackEnabled
    private val _shuffleEnabled = MutableStateFlow(preferences.getBoolean("shuffle_enabled", false))
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled
    private val _repeatMode = MutableStateFlow(RepeatMode.fromId(preferences.getString("repeat_mode", RepeatMode.OFF.id)))
    val repeatMode: StateFlow<RepeatMode> = _repeatMode
    private val _queuePersistenceEnabled = MutableStateFlow(preferences.getBoolean("queue_persistence_enabled", true))
    val queuePersistenceEnabled: StateFlow<Boolean> = _queuePersistenceEnabled
    private val _liteMode = MutableStateFlow(preferences.getBoolean("lite_mode", false))
    val liteMode: StateFlow<Boolean> = _liteMode
    private val _autoLiteMode = MutableStateFlow(preferences.getBoolean("auto_lite_mode", false))
    val autoLiteMode: StateFlow<Boolean> = _autoLiteMode
    private val _visualizerSmoothing = MutableStateFlow(preferences.getFloat("visualizer_smoothing", .28f))
    val visualizerSmoothing: StateFlow<Float> = _visualizerSmoothing
    private val _visualizerSensitivity = MutableStateFlow(preferences.getFloat("visualizer_sensitivity", 1f))
    val visualizerSensitivity: StateFlow<Float> = _visualizerSensitivity
    private val _visualizerReflection = MutableStateFlow(preferences.getFloat("visualizer_reflection", .18f))
    val visualizerReflection: StateFlow<Float> = _visualizerReflection
    private val _playlistOrganizationMode = MutableStateFlow(
        PlaylistOrganizationMode.fromId(preferences.getString("playlist_organization_mode", PlaylistOrganizationMode.ORGANIZED.id))
    )
    val playlistOrganizationMode: StateFlow<PlaylistOrganizationMode> = _playlistOrganizationMode
    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs
    private val _message = MutableStateFlow("Loading library cache…")
    val message: StateFlow<String> = _message
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search
    private val _libraryScan = MutableStateFlow(LibraryScanState(running = true, loaded = 0, stage = "Loading cache"))
    val libraryScan: StateFlow<LibraryScanState> = _libraryScan
    private val _spectrum = MutableStateFlow(List(40) { 0f })
    val spectrum: StateFlow<List<Float>> = _spectrum
    private val _visualizerStyle = MutableStateFlow(
        VisualizerStyle.fromId(preferences.getString("visualizer_style", VisualizerStyle.BARS.id))
    )
    val visualizerStyle: StateFlow<VisualizerStyle> = _visualizerStyle
    private val _dspSettings = MutableStateFlow(
        preferences.getString("dsp_settings_json", null)?.let { raw ->
            runCatching { gson.fromJson(raw, DspSettings::class.java) }.getOrNull()
        } ?: DspSettings()
    )
    val dspSettings: StateFlow<DspSettings> = _dspSettings
    private val dspProcessor = PcmDspProcessor(_dspSettings.value)
    private val _autoDeviceDspProfile = MutableStateFlow(preferences.getBoolean("auto_device_dsp_profile", true))
    val autoDeviceDspProfile: StateFlow<Boolean> = _autoDeviceDspProfile
    private val _deviceDspProfiles = MutableStateFlow(loadDeviceDspProfiles())
    val deviceDspProfiles: StateFlow<Map<String, DspSettings>> = _deviceDspProfiles
    private val _themeId = MutableStateFlow(preferences.getString("theme_id", "miku") ?: "miku")
    val themeId: StateFlow<String> = _themeId
    private val _wrappedStats = MutableStateFlow(WrappedStats())
    val wrappedStats: StateFlow<WrappedStats> = _wrappedStats

    val dspPresets: List<DspPreset> = builtInDspPresets()

    private lateinit var playbackNotification: PlaybackNotificationController
    private var playbackJob: Job? = null
    private var metadataJob: Job? = null
    private var sleepJob: Job? = null
    private var libraryScanJob: Job? = null
    private var autoNextJob: Job? = null
    private var sleepOptionIndex = 0
    private var pendingRestart = false
    @Volatile private var playbackGeneration = 0L

    private val playCountMap = mutableMapOf<String, Int>()
    private val listenedMsMap = mutableMapOf<String, Long>()
    private var totalListeningMs = 0L
    private var countedPlayTrackId: String? = null
    private var lastWrappedRefreshMs = 0L

    private data class WrappedCache(
        val playCountMap: Map<String, Int> = emptyMap(),
        val listenedMsMap: Map<String, Long> = emptyMap(),
        val totalListeningMs: Long = 0L
    )

    private data class NotificationSnapshot(
        val track: Track?,
        val cover: ByteArray?,
        val phase: PlaybackPhase,
        val positionMs: Long,
        val durationMs: Long
    )

    init {
        playbackNotification = PlaybackNotificationController(
            context = app,
            previousAction = { previous() },
            playAction = { if (_phase.value != PlaybackPhase.PLAYING && _phase.value != PlaybackPhase.LOADING) togglePlayPause() },
            pauseAction = { if (_phase.value == PlaybackPhase.PLAYING || _phase.value == PlaybackPhase.LOADING) pause() },
            togglePlayPauseAction = { togglePlayPause() },
            nextAction = { next() },
            toggleShuffleAction = { toggleShuffle() },
            seekAction = { seekTo(it) }
        )
        audio.routingMode = _audioRoutingMode.value
        audio.fallbackToCompatibility = _audioFallbackEnabled.value
        audio.dspActive = _dspSettings.value.enabled
        spectrumAnalyzer.setTuning(_visualizerSmoothing.value, _visualizerSensitivity.value)
        observePlaybackNotification()
        restoreWrappedCache()
        refreshOutputs()
        restoreLibrary()
    }

    private fun observePlaybackNotification() {
        viewModelScope.launch {
            val playback = combine(_current, _cover, _phase, _positionMs, _durationMs) { track, cover, phase, position, duration ->
                NotificationSnapshot(track, cover, phase, position, duration)
            }
            combine(playback, _shuffleEnabled) { snapshot, shuffle -> snapshot to shuffle }
                .collect { (snapshot, shuffle) ->
                    playbackNotification.update(
                        track = snapshot.track,
                        coverBytes = snapshot.cover,
                        phase = snapshot.phase,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        shuffleEnabled = shuffle
                    )
                }
        }
    }

    private fun restoreWrappedCache() {
        val raw = preferences.getString("wrapped_cache_json", null) ?: return
        val type = object : TypeToken<WrappedCache>() {}.type
        val parsed = runCatching { gson.fromJson<WrappedCache>(raw, type) }.getOrNull() ?: return
        playCountMap.putAll(parsed.playCountMap)
        listenedMsMap.putAll(parsed.listenedMsMap)
        totalListeningMs = parsed.totalListeningMs
        recomputeWrappedStats()
    }

    private fun persistWrappedCache() {
        val payload = WrappedCache(
            playCountMap = playCountMap,
            listenedMsMap = listenedMsMap,
            totalListeningMs = totalListeningMs
        )
        preferences.edit().putString("wrapped_cache_json", gson.toJson(payload)).apply()
    }

    private fun recomputeWrappedStats() {
        val tracksById = (_tracks.value + listOfNotNull(_current.value)).associateBy { it.id }
        val topTracks = listenedMsMap.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }
                .thenByDescending { playCountMap[it.key] ?: 0 })
            .take(10)
            .mapNotNull { entry ->
                val track = tracksById[entry.key] ?: return@mapNotNull null
                WrappedTrackStat(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    playCount = playCountMap[track.id] ?: 0,
                    listenedMs = entry.value
                )
            }

        val artistStats = topTracks
            .groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .map { (artist, stats) ->
                WrappedArtistStat(
                    artist = artist,
                    playCount = stats.sumOf { it.playCount },
                    listenedMs = stats.sumOf { it.listenedMs }
                )
            }
            .sortedWith(compareByDescending<WrappedArtistStat> { it.listenedMs }.thenByDescending { it.playCount })
            .take(8)

        _wrappedStats.value = WrappedStats(
            totalListeningMs = totalListeningMs,
            totalPlays = playCountMap.values.sum(),
            uniqueTracks = playCountMap.count { it.value > 0 },
            uniqueArtists = artistStats.size,
            topTracks = topTracks,
            topArtists = artistStats
        )
    }

    private fun registerPlayStart(track: Track, startMs: Long) {
        if (countedPlayTrackId == track.id || startMs > 5_000L) return
        countedPlayTrackId = track.id
        playCountMap[track.id] = (playCountMap[track.id] ?: 0) + 1
        recomputeWrappedStats()
        persistWrappedCache()
    }

    private fun recordListening(track: Track, deltaMs: Long) {
        if (deltaMs <= 0L) return
        listenedMsMap[track.id] = (listenedMsMap[track.id] ?: 0L) + deltaMs
        totalListeningMs += deltaMs
        val now = SystemClock.elapsedRealtime()
        if (now - lastWrappedRefreshMs >= 1_000L) {
            lastWrappedRefreshMs = now
            recomputeWrappedStats()
        }
    }

    private fun restoreLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = library.loadCached()
            if (cached.isNotEmpty()) {
                _tracks.value = cached
                val restoredQueue = restorePersistedQueue(cached)
                _queue.value = if (_queuePersistenceEnabled.value && restoredQueue.isNotEmpty()) restoredQueue else cached
                _libraryScan.value = LibraryScanState(
                    running = false,
                    loaded = cached.size,
                    stage = "Library restored from cache",
                    restoredFromCache = cached.size
                )
                _message.value = "${cached.size} tracks loaded from cache"
                recomputeWrappedStats()
                if (_current.value == null) {
                    val lastId = preferences.getString("last_track_id", null)
                    val initial = cached.firstOrNull { it.id == lastId } ?: cached.first()
                    withContext(Dispatchers.Main.immediate) { select(initial, autoPlay = false) }
                }
            } else {
                withContext(Dispatchers.Main.immediate) { scanMediaStore() }
            }
        }
    }

    private fun restorePersistedQueue(available: List<Track>): List<Track> {
        if (!_queuePersistenceEnabled.value) return emptyList()
        val raw = preferences.getString("queue_track_ids", null) ?: return emptyList()
        val ids: List<String> = runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return emptyList()
        val byId = available.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.distinctBy { it.id }
    }

    private fun persistQueue() {
        if (!_queuePersistenceEnabled.value) return
        preferences.edit().putString("queue_track_ids", gson.toJson(_queue.value.map { it.id })).apply()
    }

    fun setQueuePersistenceEnabled(enabled: Boolean) {
        _queuePersistenceEnabled.value = enabled
        preferences.edit().putBoolean("queue_persistence_enabled", enabled).apply()
        if (enabled) persistQueue() else preferences.edit().remove("queue_track_ids").apply()
        _message.value = if (enabled) "Queue persistence on" else "Queue persistence off"
    }

    fun moveQueueItem(trackId: String, delta: Int) {
        if (delta == 0) return
        val list = _queue.value.toMutableList()
        val from = list.indexOfFirst { it.id == trackId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        val item = list.removeAt(from)
        list.add(to, item)
        _queue.value = list
        persistQueue()
        _message.value = "Queue reordered"
    }

    fun scanMediaStore() {
        if (libraryScanJob?.isActive == true) return
        libraryScanJob = viewModelScope.launch(Dispatchers.IO) {
            _libraryScan.value = LibraryScanState(running = true, loaded = 0, stage = "Scanning music")
            _message.value = "Scanning music… 0 tracks"
            val result = library.scanAll { updatedList ->
                _tracks.value = updatedList
                if (_queue.value.isEmpty()) _queue.value = updatedList
                _libraryScan.value = LibraryScanState(running = true, loaded = updatedList.size, stage = "Scanning music")
                _message.value = "Scanning music… ${updatedList.size} tracks"
            }
            library.saveCached(result)
            _tracks.value = result
            if (_queue.value.isEmpty()) _queue.value = result
            val currentId = _current.value?.id
            val selected = result.firstOrNull { it.id == currentId } ?: result.firstOrNull()
            if (selected != null && _current.value?.id != selected.id) {
                withContext(Dispatchers.Main.immediate) { select(selected, autoPlay = false) }
            }
            _libraryScan.value = LibraryScanState(running = false, loaded = result.size, stage = "Ready")
            _message.value = if (result.isEmpty()) "Add a music folder to begin" else "${result.size} tracks ready"
            recomputeWrappedStats()
        }
    }

    fun importFolder(tree: Uri) {
        if (libraryScanJob?.isActive == true) return
        libraryScanJob = viewModelScope.launch(Dispatchers.IO) {
            _libraryScan.value = LibraryScanState(running = true, loaded = 0, stage = "Reading folder")
            _message.value = "Reading folder… 0 tracks"
            val added = library.scanTree(tree) { updatedAdded ->
                val merged = (_tracks.value + updatedAdded).associateBy { it.signature }.values
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _tracks.value = merged
                if (_queue.value.isEmpty()) _queue.value = merged
                _libraryScan.value = LibraryScanState(running = true, loaded = merged.size, stage = "Reading folder")
                _message.value = "Reading folder… ${updatedAdded.size} tracks"
            }
            val finalMerged = (_tracks.value + added).associateBy { it.signature }.values
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            _tracks.value = finalMerged
            if (_queue.value.isEmpty()) _queue.value = finalMerged
            library.saveCached(finalMerged)
            if (_current.value == null && finalMerged.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) { select(finalMerged.first(), autoPlay = false) }
            }
            _libraryScan.value = LibraryScanState(running = false, loaded = finalMerged.size, stage = "Ready")
            _message.value = if (added.isEmpty()) "No supported audio files found" else "Added ${added.size} tracks • ${finalMerged.size} total"
            recomputeWrappedStats()
        }
    }

    fun permissionDenied() {
        _message.value = if (_tracks.value.isNotEmpty()) {
            "Using cached library — storage permission denied"
        } else {
            "Storage permission denied — use Add folder instead"
        }
    }

    fun setSearch(value: String) { _search.value = value }

    fun setTheme(id: String) {
        _themeId.value = id
        preferences.edit().putString("theme_id", id).apply()
    }

    fun refreshOutputs() {
        val devices = audio.outputs()
        _audioOutputs.value = devices
        val old = _selectedOutputId.value
        val selected = old?.takeIf { id -> devices.any { it.id == id } } ?: devices.firstOrNull()?.id
        if (old != null && selected != old && _status.value.connected) stopPlayback(resetPosition = false)
        _selectedOutputId.value = selected
        if (selected != old) applyDeviceProfileIfAvailable(selected)
        if (devices.isEmpty()) {
            _status.value = OutputStatus(false, note = "No audio output detected")
        } else if (!_status.value.connected) {
            _status.value = OutputStatus(
                false,
                deviceName = devices.first { it.id == _selectedOutputId.value }.name,
                note = "Ready for playback"
            )
        }
    }

    fun selectOutput(id: Int) {
        if (_audioOutputs.value.any { it.id == id }) {
            stopPlayback(resetPosition = false)
            _selectedOutputId.value = id
            applyDeviceProfileIfAvailable(id)
            _status.value = OutputStatus(
                false,
                deviceName = _audioOutputs.value.first { it.id == id }.name,
                note = "Selected — press Play"
            )
        }
    }

    fun setAudioRoutingMode(mode: AudioRoutingMode) {
        if (_audioRoutingMode.value == mode) return
        stopPlayback(resetPosition = false)
        _audioRoutingMode.value = mode
        audio.routingMode = mode
        preferences.edit().putString("audio_routing_mode", mode.id).apply()
        _status.value = _selectedOutputId.value?.let { id ->
            _audioOutputs.value.firstOrNull { it.id == id }?.let { output ->
                OutputStatus(false, deviceName = output.name, note = "${mode.label} selected — press Play")
            }
        } ?: OutputStatus(false, note = "${mode.label} selected")
    }

    fun setAudioFallbackEnabled(enabled: Boolean) {
        if (_audioFallbackEnabled.value == enabled) return
        stopPlayback(resetPosition = false)
        _audioFallbackEnabled.value = enabled
        audio.fallbackToCompatibility = enabled
        preferences.edit().putBoolean("audio_fallback_enabled", enabled).apply()
        _message.value = if (enabled) "Audio compatibility fallback enabled" else "Strict audio routing enabled"
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        preferences.edit().putBoolean("shuffle_enabled", _shuffleEnabled.value).apply()
        _message.value = if (_shuffleEnabled.value) "Shuffle on" else "Shuffle off"
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        preferences.edit().putString("repeat_mode", mode.id).apply()
        _message.value = mode.label
    }

    fun setLiteMode(enabled: Boolean) {
        _liteMode.value = enabled
        preferences.edit().putBoolean("lite_mode", enabled).apply()
        _message.value = if (enabled) "Lite mode on" else "Lite mode off"
    }

    fun setAutoLiteMode(enabled: Boolean) {
        _autoLiteMode.value = enabled
        preferences.edit().putBoolean("auto_lite_mode", enabled).apply()
        _message.value = if (enabled) "Auto Lite recommendation enabled" else "Auto Lite recommendation disabled"
    }

    fun setVisualizerSmoothing(value: Float) {
        val safe = value.coerceIn(0f, 1f)
        _visualizerSmoothing.value = safe
        preferences.edit().putFloat("visualizer_smoothing", safe).apply()
        spectrumAnalyzer.setTuning(safe, _visualizerSensitivity.value)
    }

    fun setVisualizerSensitivity(value: Float) {
        val safe = value.coerceIn(.55f, 1.8f)
        _visualizerSensitivity.value = safe
        preferences.edit().putFloat("visualizer_sensitivity", safe).apply()
        spectrumAnalyzer.setTuning(_visualizerSmoothing.value, safe)
    }

    fun setVisualizerReflection(value: Float) {
        val safe = value.coerceIn(0f, .45f)
        _visualizerReflection.value = safe
        preferences.edit().putFloat("visualizer_reflection", safe).apply()
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _visualizerStyle.value = style
        preferences.edit().putString("visualizer_style", style.id).apply()
        _message.value = "Visualizer: ${style.label}"
    }

    fun setDspEnabled(enabled: Boolean) {
        if (_dspSettings.value.enabled == enabled) return
        updateDsp(_dspSettings.value.copy(enabled = enabled), restartRoute = true)
        _message.value = if (enabled) "DSP ACTIVE — bit-perfect disabled" else "DSP bypassed"
    }

    fun setDspPreamp(db: Float) = updateDsp(_dspSettings.value.copy(preampDb = db.coerceIn(-24f, 12f)))

    fun updateEqBand(updated: EqBand) {
        val bands = _dspSettings.value.bands.map { if (it.id == updated.id) updated else it }
        updateDsp(_dspSettings.value.copy(bands = bands))
    }

    fun addEqBand() {
        val settings = _dspSettings.value
        if (settings.bands.size >= 24) {
            _message.value = "Maximum 24 EQ bands"
            return
        }
        val nextId = (settings.bands.maxOfOrNull { it.id } ?: 0) + 1
        updateDsp(settings.copy(bands = settings.bands + EqBand(nextId)))
        _message.value = "EQ band added"
    }

    fun removeEqBand(id: Int) {
        val settings = _dspSettings.value
        if (settings.bands.size <= 1) return
        updateDsp(settings.copy(bands = settings.bands.filterNot { it.id == id }))
        _message.value = "EQ band removed"
    }

    fun setCompressorEnabled(enabled: Boolean) =
        updateDsp(_dspSettings.value.copy(compressorEnabled = enabled))

    fun setCompressorThreshold(db: Float) =
        updateDsp(_dspSettings.value.copy(compressorThresholdDb = db.coerceIn(-60f, 0f)))

    fun setCompressorRatio(ratio: Float) =
        updateDsp(_dspSettings.value.copy(compressorRatio = ratio.coerceIn(1f, 20f)))

    fun setCompressorAttack(milliseconds: Float) =
        updateDsp(_dspSettings.value.copy(compressorAttackMs = milliseconds.coerceIn(.1f, 250f)))

    fun setCompressorRelease(milliseconds: Float) =
        updateDsp(_dspSettings.value.copy(compressorReleaseMs = milliseconds.coerceIn(10f, 2_000f)))

    fun setLimiterEnabled(enabled: Boolean) =
        updateDsp(_dspSettings.value.copy(limiterEnabled = enabled))

    fun setLimiterCeiling(db: Float) =
        updateDsp(_dspSettings.value.copy(limiterCeilingDb = db.coerceIn(-12f, 0f)))

    fun setCrossfeed(amount: Float) =
        updateDsp(_dspSettings.value.copy(crossfeed = amount.coerceIn(0f, .65f)))

    fun applyDspPreset(preset: DspPreset) {
        val current = _dspSettings.value
        updateDsp(current.copy(enabled = true, preampDb = preset.preampDb, bands = preset.bands), restartRoute = !current.enabled)
        _message.value = "Preset: ${preset.name}"
    }

    fun resetDsp() {
        val wasEnabled = _dspSettings.value.enabled
        updateDsp(DspSettings(enabled = wasEnabled))
        _message.value = "Equalizer reset"
    }

    fun importEqualizerApo(text: String): Boolean {
        val preamp = Regex("""(?i)preamp\s*:\s*([+-]?\d+(?:[.,]\d+)?)\s*dB""")
            .find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()
        val imported = mutableListOf<EqBand>()
        text.lineSequence().forEach { line ->
            if (!line.contains("Filter", ignoreCase = true) || line.contains("OFF", ignoreCase = true)) return@forEach
            val typeCode = Regex("""(?i)\b(PK|PEQ|LSC?|HSC?|LPQ?|HPQ?)\b""").find(line)?.value ?: return@forEach
            val type = EqFilterType.fromApo(typeCode) ?: return@forEach
            val frequency = Regex("""(?i)Fc\s+([0-9]+(?:[.,][0-9]+)?)\s*Hz""").find(line)
                ?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull() ?: return@forEach
            val gain = Regex("""(?i)Gain\s+([+-]?\d+(?:[.,]\d+)?)\s*dB""").find(line)
                ?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull() ?: 0f
            val q = Regex("""(?i)Q\s+([0-9]+(?:[.,][0-9]+)?)""").find(line)
                ?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull() ?: .707f
            imported += EqBand(imported.size + 1, true, type, frequency, gain, q)
        }
        if (imported.isEmpty()) {
            _message.value = "No Equalizer APO / AutoEQ filters found"
            return false
        }
        val current = _dspSettings.value
        updateDsp(
            current.copy(
                enabled = true,
                preampDb = (preamp ?: current.preampDb).coerceIn(-24f, 12f),
                bands = imported.take(24)
            ),
            restartRoute = !current.enabled
        )
        _message.value = "Imported ${imported.size.coerceAtMost(24)} AutoEQ filters"
        return true
    }

    fun exportEqualizerApo(): String = buildString {
        val settings = _dspSettings.value
        appendLine("Preamp: ${formatDspNumber(settings.preampDb)} dB")
        settings.bands.forEachIndexed { index, band ->
            append("Filter ${index + 1}: ${if (band.enabled) "ON" else "OFF"} ${band.type.apoCode}")
            append(" Fc ${formatDspNumber(band.frequencyHz)} Hz")
            if (band.type != EqFilterType.LOW_PASS && band.type != EqFilterType.HIGH_PASS) {
                append(" Gain ${formatDspNumber(band.gainDb)} dB")
            }
            appendLine(" Q ${formatDspNumber(band.q)}")
        }
    }

    fun setAutoDeviceDspProfile(enabled: Boolean) {
        _autoDeviceDspProfile.value = enabled
        preferences.edit().putBoolean("auto_device_dsp_profile", enabled).apply()
        if (enabled) applyDeviceProfileIfAvailable(_selectedOutputId.value)
    }

    fun saveDspProfileForCurrentDevice() {
        val key = selectedOutputProfileKey() ?: run {
            _message.value = "Select an output first"
            return
        }
        val updated = _deviceDspProfiles.value + (key to _dspSettings.value)
        _deviceDspProfiles.value = updated
        preferences.edit().putString("dsp_device_profiles_json", gson.toJson(updated)).apply()
        _message.value = "DSP profile saved for $key"
    }

    fun deleteDspProfileForCurrentDevice() {
        val key = selectedOutputProfileKey() ?: return
        val updated = _deviceDspProfiles.value - key
        _deviceDspProfiles.value = updated
        preferences.edit().putString("dsp_device_profiles_json", gson.toJson(updated)).apply()
        _message.value = "DSP profile removed for $key"
    }

    private fun updateDsp(settings: DspSettings, restartRoute: Boolean = false) {
        _dspSettings.value = settings
        dspProcessor.update(settings)
        audio.dspActive = settings.enabled
        preferences.edit().putString("dsp_settings_json", gson.toJson(settings)).apply()
        if (restartRoute && (_phase.value == PlaybackPhase.PLAYING || _phase.value == PlaybackPhase.LOADING)) {
            startPlayback(_positionMs.value)
        } else if (settings.enabled && _status.value.connected) {
            _status.value = _status.value.copy(
                bitPerfect = false,
                backend = "Prismora PCM DSP",
                note = "DSP ACTIVE — EQ/dynamics are processing PCM at the source sample rate."
            )
        }
    }

    private fun selectedOutputProfileKey(): String? = _selectedOutputId.value?.let { id ->
        _audioOutputs.value.firstOrNull { it.id == id }?.name
    }

    private fun applyDeviceProfileIfAvailable(deviceId: Int?) {
        if (!_autoDeviceDspProfile.value || deviceId == null) return
        val key = _audioOutputs.value.firstOrNull { it.id == deviceId }?.name ?: return
        val profile = _deviceDspProfiles.value[key] ?: return
        updateDsp(profile, restartRoute = profile.enabled != _dspSettings.value.enabled)
        _message.value = "Loaded DSP profile for $key"
    }

    private fun loadDeviceDspProfiles(): Map<String, DspSettings> {
        val raw = preferences.getString("dsp_device_profiles_json", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, DspSettings>>() {}.type
        return runCatching { gson.fromJson<Map<String, DspSettings>>(raw, type) }.getOrDefault(emptyMap())
    }

    fun setPlaylistOrganizationMode(mode: PlaylistOrganizationMode) {
        _playlistOrganizationMode.value = mode
        preferences.edit().putString("playlist_organization_mode", mode.id).apply()
        _message.value = "Playlist organization: ${mode.label}"
    }

    fun getLibraryArt(track: Track): ByteArray? {
        track.localArt?.let { return it }
        libraryArtworkCache.get(track.id)?.let { return it }
        val cached = runCatching { metadata.readLocalCached(track.uri, track.modifiedMs) }.getOrNull()
        return cached?.artwork?.also { libraryArtworkCache.put(track.id, it) }
    }

    fun cycleSleepTimer() {
        val options = intArrayOf(0, 15, 30, 60, 90)
        sleepOptionIndex = (sleepOptionIndex + 1) % options.size
        setSleepTimerMinutes(options[sleepOptionIndex])
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        val safeMinutes = minutes.coerceAtLeast(0)
        sleepOptionIndex = when (safeMinutes) {
            15 -> 1
            30 -> 2
            60 -> 3
            90 -> 4
            else -> 0
        }
        _sleepRemainingMs.value = safeMinutes * 60_000L
        if (safeMinutes == 0) {
            _message.value = "Sleep timer off"
            return
        }
        _message.value = "Sleep timer: $safeMinutes min"
        sleepJob = viewModelScope.launch {
            while (_sleepRemainingMs.value > 0L) {
                delay(1_000L)
                _sleepRemainingMs.value = (_sleepRemainingMs.value - 1_000L).coerceAtLeast(0L)
            }
            sleepOptionIndex = 0
            pause()
            _message.value = "Sleep timer finished"
        }
    }

    fun select(track: Track, autoPlay: Boolean = true) {
        if (_queue.value.isEmpty() || _queue.value.none { it.id == track.id }) {
            _queue.value = _tracks.value
            persistQueue()
        }
        countedPlayTrackId = null
        stopPlayback(resetPosition = true)
        _current.value = track
        preferences.edit().putString("last_track_id", track.id).apply()
        _durationMs.value = track.durationMs
        _cover.value = track.localArt
        _lyrics.value = null
        _lyricLines.value = emptyList()
        loadMetadata(track)
        if (autoPlay) startPlayback(0L)
    }

    fun selectFromList(track: Track, list: List<Track>, autoPlay: Boolean = true) {
        _queue.value = list
        persistQueue()
        countedPlayTrackId = null
        select(track, autoPlay)
    }

    fun togglePlayPause() {
        when (_phase.value) {
            PlaybackPhase.PLAYING, PlaybackPhase.LOADING -> pause()
            PlaybackPhase.PAUSED -> {
                if (pendingRestart) {
                    startPlayback(_positionMs.value)
                } else if (audio.resume()) {
                    _phase.value = PlaybackPhase.PLAYING
                    _message.value = ""
                } else {
                    startPlayback(_positionMs.value)
                }
            }
            else -> startPlayback(_positionMs.value)
        }
    }

    fun play() {
        startPlayback(_positionMs.value)
    }

    fun pause() {
        when (_phase.value) {
            PlaybackPhase.LOADING -> hardPause()
            PlaybackPhase.PLAYING -> {
                if (audio.pause()) {
                    _phase.value = PlaybackPhase.PAUSED
                    _message.value = "Paused"
                    _spectrum.value = spectrumAnalyzer.reset()
                    persistWrappedCache()
                } else {
                    hardPause()
                }
            }
            else -> Unit
        }
    }

    private fun hardPause() {
        playbackGeneration++
        autoNextJob?.cancel()
        playbackJob?.cancel()
        audio.close()
        pendingRestart = true
        _phase.value = PlaybackPhase.PAUSED
        _message.value = "Paused"
        _spectrum.value = spectrumAnalyzer.reset()
        persistWrappedCache()
    }

    fun seekTo(position: Long) {
        val target = position.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _positionMs.value = target
        if (_phase.value == PlaybackPhase.PLAYING || _phase.value == PlaybackPhase.LOADING) {
            startPlayback(target)
        } else if (_phase.value == PlaybackPhase.PAUSED) {
            playbackGeneration++
            playbackJob?.cancel()
            audio.close()
            pendingRestart = true
            _spectrum.value = spectrumAnalyzer.reset()
        }
    }

    fun next() {
        val list = _queue.value.ifEmpty { _tracks.value }
        if (list.isEmpty()) return
        val nextTrack = if (_shuffleEnabled.value && list.size > 1) {
            list.filterNot { it.id == _current.value?.id }.random()
        } else {
            val index = list.indexOfFirst { it.id == _current.value?.id }
                .let { if (it < 0) 0 else (it + 1) % list.size }
            list[index]
        }
        selectFromList(nextTrack, list, autoPlay = true)
    }

    fun previous() {
        if (_positionMs.value > 5_000L) {
            seekTo(0L)
            return
        }
        val list = _queue.value.ifEmpty { _tracks.value }
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == _current.value?.id }.let { if (it < 0) 0 else it }
        selectFromList(list[(currentIndex - 1 + list.size) % list.size], list, autoPlay = true)
    }

    private fun startPlayback(startMs: Long) {
        val track = _current.value ?: return
        audio.dspActive = _dspSettings.value.enabled
        refreshOutputs()
        val deviceId = _selectedOutputId.value
        if (deviceId == null) {
            _phase.value = PlaybackPhase.ERROR
            _message.value = "Choose an audio output first"
            return
        }

        autoNextJob?.cancel()
        val previous = playbackJob
        previous?.cancel()
        audio.close()
        pendingRestart = false
        _spectrum.value = spectrumAnalyzer.reset()
        dspProcessor.reset()
        registerPlayStart(track, startMs)

        val generation = ++playbackGeneration
        val outputName = _audioOutputs.value.firstOrNull { it.id == deviceId }?.name ?: "audio output"
        _phase.value = PlaybackPhase.LOADING
        _message.value = "Opening $outputName…"

        var lastSpectrumNs = 0L
        var lastProgressMs = startMs
        val newJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                previous?.join()
                if (!isActive || generation != playbackGeneration) return@launch

                val decoder = LocalDecoder(
                    context = getApplication<Application>(),
                    audio = audio,
                    onStatus = { newStatus ->
                        if (generation == playbackGeneration) {
                            _status.value = newStatus
                            if (newStatus.connected && _phase.value == PlaybackPhase.LOADING) {
                                _phase.value = PlaybackPhase.PLAYING
                                _message.value = ""
                            }
                        }
                    },
                    onProgress = { progress ->
                        if (generation == playbackGeneration) {
                            val safeProgress = progress.coerceAtMost(
                                _durationMs.value.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
                            )
                            val delta = (safeProgress - lastProgressMs).coerceIn(0L, 4_000L)
                            lastProgressMs = safeProgress
                            _positionMs.value = safeProgress
                            if (_phase.value != PlaybackPhase.PAUSED && delta > 0L) {
                                recordListening(track, delta)
                            }
                        }
                    },
                    onPcm = { pcm, count, format, channels, sampleRate ->
                        if (generation == playbackGeneration) {
                            dspProcessor.processInPlace(pcm, count, format, channels, sampleRate)
                            if (_phase.value != PlaybackPhase.PAUSED) {
                                val now = System.nanoTime()
                                if (now - lastSpectrumNs >= 8_333_333L) {
                                    lastSpectrumNs = now
                                    _spectrum.value = spectrumAnalyzer.analyze(pcm, count, format, channels, sampleRate)
                                }
                            }
                        }
                    }
                )

                decoder.play(track, deviceId, startMs)
                if (!isActive || generation != playbackGeneration) return@launch
                _phase.value = PlaybackPhase.FINISHED
                _positionMs.value = _durationMs.value
                _spectrum.value = spectrumAnalyzer.reset()
                _message.value = "Finished"
                persistWrappedCache()
                withContext(Dispatchers.Main.immediate) {
                    if (generation == playbackGeneration) scheduleAutoNext(generation)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == playbackGeneration) {
                    _phase.value = PlaybackPhase.ERROR
                    _spectrum.value = spectrumAnalyzer.reset()
                    _message.value = error.message ?: "Playback failed"
                    persistWrappedCache()
                }
            }
        }
        playbackJob = newJob
        newJob.invokeOnCompletion {
            if (playbackJob === newJob) playbackJob = null
        }
    }

    private fun scheduleAutoNext(generation: Long) {
        autoNextJob?.cancel()
        autoNextJob = viewModelScope.launch {
            delay(80L)
            if (generation != playbackGeneration || _phase.value != PlaybackPhase.FINISHED) return@launch
            when (_repeatMode.value) {
                RepeatMode.ONE -> startPlayback(0L)
                RepeatMode.ALL -> next()
                RepeatMode.OFF -> {
                    val list = _queue.value.ifEmpty { _tracks.value }
                    val index = list.indexOfFirst { it.id == _current.value?.id }
                    if (_shuffleEnabled.value && list.size > 1) next()
                    else if (index >= 0 && index < list.lastIndex) selectFromList(list[index + 1], list, autoPlay = true)
                    else {
                        _message.value = "Queue finished"
                        _phase.value = PlaybackPhase.FINISHED
                    }
                }
            }
        }
    }

    private fun loadMetadata(track: Track) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch(Dispatchers.IO) {
            val local = runCatching { metadata.readLocalCached(track.uri, track.modifiedMs) }.getOrNull()
            val enriched = if (local != null) track.copy(
                title = local.title.takeUnless { it == "Unknown Track" } ?: track.title,
                artist = local.artist.takeUnless { it == "Unknown Artist" } ?: track.artist,
                album = local.album.takeUnless { it == "Unknown Album" } ?: track.album,
                durationMs = local.durationMs.takeIf { it > 0 } ?: track.durationMs,
                localArt = local.artwork ?: track.localArt,
                trackNumber = local.trackNumber.takeIf { it > 0 } ?: track.trackNumber
            ) else track
            if (_current.value?.id != track.id) return@launch
            _current.value = enriched
            _queue.value = _queue.value.map { if (it.id == enriched.id) enriched else it }
            _durationMs.value = enriched.durationMs
            _cover.value = enriched.localArt ?: metadata.artwork(enriched.title, enriched.artist)
            val result = metadata.lyrics(
                enriched.title,
                enriched.artist,
                enriched.album,
                (enriched.durationMs / 1_000L).toInt()
            )
            if (_current.value?.id == track.id) {
                _lyrics.value = result
                _lyricLines.value = parseSyncedLyrics(result?.syncedLyrics)
            }
            recomputeWrappedStats()
        }
    }

    private fun stopPlayback(resetPosition: Boolean) {
        playbackGeneration++
        autoNextJob?.cancel()
        autoNextJob = null
        playbackJob?.cancel()
        audio.close()
        pendingRestart = false
        _phase.value = PlaybackPhase.IDLE
        _spectrum.value = spectrumAnalyzer.reset()
        if (resetPosition) _positionMs.value = 0L
        persistWrappedCache()
    }

    private fun parseSyncedLyrics(value: String?): List<LyricLine> {
        if (value.isNullOrBlank()) return emptyList()
        val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        return value.lineSequence().mapNotNull { row ->
            val match = timestamp.find(row) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionText = match.groupValues[3]
            val millis = when (fractionText.length) {
                1 -> fractionText.toLong() * 100L
                2 -> fractionText.toLong() * 10L
                3 -> fractionText.toLong()
                else -> 0L
            }
            LyricLine((minutes * 60L + seconds) * 1_000L + millis, row.replace(timestamp, "").trim())
        }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
    }

    override fun onCleared() {
        playbackGeneration++
        playbackJob?.cancel()
        metadataJob?.cancel()
        sleepJob?.cancel()
        libraryScanJob?.cancel()
        autoNextJob?.cancel()
        audio.close()
        playbackNotification.release()
        persistWrappedCache()
        super.onCleared()
    }
}

private fun formatDspNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.2f", value)

private fun builtInDspPresets(): List<DspPreset> {
    fun bands(vararg gains: Float): List<EqBand> {
        val frequencies = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        return frequencies.mapIndexed { index, frequency ->
            EqBand(index + 1, true, EqFilterType.PEAK, frequency, gains.getOrElse(index) { 0f }, 1f)
        }
    }
    return listOf(
        DspPreset("flat", "Flat", 0f, bands(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        DspPreset("bass", "Bass boost", -4f, bands(5f, 4.5f, 3f, 1.5f, 0f, 0f, 0f, 0f, -.5f, -1f)),
        DspPreset("vocal", "Vocal clarity", -3f, bands(-1f, -1f, -.5f, 0f, 1f, 2f, 3f, 2f, .5f, 0f)),
        DspPreset("warm", "Warm", -3f, bands(2f, 2.5f, 2f, 1.5f, .5f, 0f, -.5f, -1f, -1f, -1.5f)),
        DspPreset("bright", "Bright", -4f, bands(-1f, -.5f, 0f, 0f, 0f, .5f, 1.5f, 2.5f, 3f, 2f)),
        DspPreset("vshape", "V-shape", -5f, bands(4f, 3.5f, 2f, 0f, -1.5f, -2f, -1f, 1f, 3f, 3.5f))
    )
}
