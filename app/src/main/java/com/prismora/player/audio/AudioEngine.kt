package com.prismora.player.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import com.prismora.player.model.AudioOutput
import com.prismora.player.model.AudioOutputKind
import com.prismora.player.model.AudioRoutingMode
import com.prismora.player.model.OutputStatus
import kotlinx.coroutines.delay
import kotlin.math.max

class AudioEngine(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @Volatile private var opened = false
    @Volatile private var backend = Backend.NONE
    @Volatile private var nativeTrack: AudioTrack? = null
    @Volatile private var nativeDevice: AudioDeviceInfo? = null
    @Volatile private var nativeStarted = false
    @Volatile private var nativeWrittenFrames = 0L
    @Volatile private var nativeFrameBytes = 4

    @Volatile var routingMode: AudioRoutingMode = AudioRoutingMode.AUTO
    @Volatile var fallbackToCompatibility: Boolean = true
    @Volatile var dspActive: Boolean = false

    @Volatile var lastStatus = OutputStatus(false)
        private set

    private enum class Backend { NONE, ANDROID_BIT_PERFECT, AAUDIO }

    companion object {
        init { System.loadLibrary("miku_audio") }
        const val PCM_I16 = 1
        const val PCM_FLOAT = 2
        const val PCM_I24 = 3
        const val PCM_I32 = 4
    }

    private external fun nativeOpen(deviceId: Int, sampleRate: Int, channels: Int, format: Int, framesPerCallback: Int): Int
    private external fun nativeClose()
    private external fun nativeWritePcm(data: ByteArray, byteCount: Int): Int
    private external fun nativeFlush(): Int
    private external fun nativeGetUnderruns(): Long
    private external fun nativeActualDeviceId(): Int
    private external fun nativeActualSampleRate(): Int
    private external fun nativeActualChannelCount(): Int
    private external fun nativeActualFormat(): Int
    private external fun nativeExclusive(): Boolean
    private external fun nativeUsedBytes(): Int
    private external fun nativePause(): Int
    private external fun nativeResume(): Int

    fun outputs(): List<AudioOutput> = rawOutputs().map { device ->
        val kind = outputKind(device)
        AudioOutput(
            id = device.id,
            name = device.productName?.toString()?.ifBlank { kind.label } ?: kind.label,
            kind = kind,
            sampleRates = device.sampleRates.toList(),
            channelCounts = device.channelCounts.toList(),
            nativeBitPerfectFormats = nativeBitPerfectFormats(device)
        )
    }.sortedBy { outputPriority(it.kind) }

    private fun rawOutputs(): List<AudioDeviceInfo> = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { outputKind(it) != AudioOutputKind.OTHER || it.type == AudioDeviceInfo.TYPE_HDMI }

    private fun outputKind(device: AudioDeviceInfo): AudioOutputKind = when (device.type) {
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioOutputKind.USB

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioOutputKind.BLUETOOTH

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioOutputKind.WIRED

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioOutputKind.SPEAKER

        else -> AudioOutputKind.OTHER
    }

    private fun outputPriority(kind: AudioOutputKind): Int = when (kind) {
        AudioOutputKind.USB -> 0
        AudioOutputKind.WIRED -> 1
        AudioOutputKind.BLUETOOTH -> 2
        AudioOutputKind.SPEAKER -> 3
        AudioOutputKind.OTHER -> 4
    }

    private fun nativeBitPerfectFormats(device: AudioDeviceInfo): List<String> {
        if (Build.VERSION.SDK_INT < 34 || outputKind(device) != AudioOutputKind.USB) return emptyList()
        return runCatching {
            audioManager.getSupportedMixerAttributes(device)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
                .map { mixer ->
                    val f = mixer.format
                    "${f.sampleRate} Hz • ${bitsForEncoding(f.encoding)}-bit • ${f.channelCount}ch"
                }
                .distinct()
                .sorted()
        }.getOrElse { emptyList() }
    }

    fun open(deviceId: Int, sampleRate: Int, channels: Int, format: Int, sourceBits: Int): OutputStatus {
        close()
        val device = rawOutputs().firstOrNull { it.id == deviceId }
            ?: return OutputStatus(false, note = "Selected audio output is no longer connected").remember()
        val kind = outputKind(device)

        val wantsNativeBitPerfect =
            !dspActive && kind == AudioOutputKind.USB && routingMode != AudioRoutingMode.COMPATIBILITY

        if (wantsNativeBitPerfect) {
            val nativeStatus = openAndroidBitPerfect(device, sampleRate, channels, format, sourceBits)
            if (nativeStatus.connected) return nativeStatus.remember()

            if (routingMode == AudioRoutingMode.NATIVE_BIT_PERFECT && !fallbackToCompatibility) {
                return nativeStatus.remember()
            }
            if (!fallbackToCompatibility && routingMode == AudioRoutingMode.AUTO) {
                return nativeStatus.remember()
            }
        }

        return openAAudio(device, sampleRate, channels, format, sourceBits).remember()
    }

    private fun openAndroidBitPerfect(
        device: AudioDeviceInfo,
        sampleRate: Int,
        channels: Int,
        format: Int,
        sourceBits: Int
    ): OutputStatus {
        if (Build.VERSION.SDK_INT < 34) {
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                backend = "Android Native Bit-Perfect",
                note = "Native bit-perfect routing requires Android 14 or newer."
            )
        }

        val encoding = androidEncoding(format)
        if (encoding == AudioFormat.ENCODING_INVALID) {
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                backend = "Android Native Bit-Perfect",
                note = "This PCM format cannot use Android native bit-perfect routing."
            )
        }

        val mixer = runCatching {
            audioManager.getSupportedMixerAttributes(device).firstOrNull { attr ->
                val f = attr.format
                attr.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    f.sampleRate == sampleRate &&
                    f.encoding == encoding &&
                    f.channelCount == channels
            }
        }.getOrNull()

        if (mixer == null) {
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                sampleRate = sampleRate,
                bits = sourceBits,
                backend = "Android Native Bit-Perfect",
                note = "The DAC/Android HAL did not expose an exact $sampleRate Hz / $sourceBits-bit bit-perfect mixer format."
            )
        }

        val preferredSet = runCatching {
            audioManager.setPreferredMixerAttributes(mediaAttributes, device, mixer)
        }.getOrDefault(false)

        if (!preferredSet) {
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                backend = "Android Native Bit-Perfect",
                note = "Android rejected the requested bit-perfect mixer attributes."
            )
        }

        val f = mixer.format
        val bytesPerFrame = max(1, f.channelCount) * bytesPerSample(format)
        val minBuffer = AudioTrack.getMinBufferSize(
            f.sampleRate,
            f.channelMask.takeIf { it != 0 } ?: channelMaskFor(f.channelCount),
            f.encoding
        )
        val bufferSize = max(
            if (minBuffer > 0) minBuffer else 0,
            max(bytesPerFrame * 256, f.sampleRate * bytesPerFrame / 4)
        )

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes)
                .setAudioFormat(f)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (t: Throwable) {
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                backend = "Android Native Bit-Perfect",
                note = "AudioTrack could not open the exact USB format: ${t.message ?: "unknown error"}"
            )
        }

        if (track.state != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(device)) {
            runCatching { track.release() }
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
            return OutputStatus(
                connected = false,
                deviceName = device.productName?.toString() ?: "USB DAC",
                backend = "Android Native Bit-Perfect",
                note = "Android could not lock playback to the selected USB DAC."
            )
        }

        nativeTrack = track
        nativeDevice = device
        nativeStarted = false
        nativeWrittenFrames = 0L
        nativeFrameBytes = bytesPerFrame
        backend = Backend.ANDROID_BIT_PERFECT
        opened = true

        return OutputStatus(
            connected = true,
            deviceName = device.productName?.toString() ?: "USB DAC",
            sampleRate = f.sampleRate,
            bits = bitsForEncoding(f.encoding),
            format = nameForEncoding(f.encoding),
            exclusive = true,
            bitPerfect = true,
            backend = "Android Native Bit-Perfect",
            note = "Exact USB mixer format locked. Android mixing, volume adjustment and audio effects are disabled for this route."
        )
    }

    private fun openAAudio(
        device: AudioDeviceInfo,
        sampleRate: Int,
        channels: Int,
        format: Int,
        sourceBits: Int
    ): OutputStatus {
        val kind = outputKind(device)
        val result = nativeOpen(device.id, sampleRate, channels, format, 192)
        if (result != 0) {
            return OutputStatus(
                false,
                deviceName = device.productName.toString(),
                backend = "AAudio",
                note = "AAudio error $result"
            )
        }

        val actualFormat = nativeActualFormat()
        val actualRate = nativeActualSampleRate()
        val actualChannels = nativeActualChannelCount()
        val exclusive = nativeExclusive()
        if (actualFormat != format || actualRate != sampleRate || actualChannels != channels) {
            nativeClose()
            return OutputStatus(
                connected = false,
                deviceName = device.productName.toString(),
                sampleRate = actualRate,
                bits = bitsFor(actualFormat),
                format = nameFor(actualFormat),
                exclusive = exclusive,
                bitPerfect = false,
                backend = if (exclusive) "AAudio Exclusive" else "AAudio Shared",
                note = "The route changed the requested PCM format. Playback stopped instead of silently resampling."
            )
        }

        opened = true
        backend = Backend.AAUDIO
        val routedToRequestedDevice = nativeActualDeviceId() == device.id
        return OutputStatus(
            connected = true,
            deviceName = device.productName?.toString() ?: kind.label,
            sampleRate = actualRate,
            bits = bitsFor(actualFormat),
            format = nameFor(actualFormat),
            exclusive = exclusive,
            bitPerfect = false,
            backend = if (dspActive) "Prismora PCM DSP" else if (exclusive) "AAudio Exclusive" else "AAudio Shared",
            note = buildString {
                append("${kind.label}. Source: $sampleRate Hz / $sourceBits-bit. ")
                if (dspActive) {
                    append("DSP ACTIVE — bit-perfect output is intentionally disabled while processing audio. ")
                }
                if (!routedToRequestedDevice) append("Android did not confirm the requested output device ID. ")
                when {
                    dspActive -> append("PCM is processed at the source sample rate without forced upsampling.")
                    kind == AudioOutputKind.USB && exclusive ->
                        append("Exact-format exclusive AAudio was accepted, but this build does not label AAudio itself as verified bit-perfect.")
                    kind == AudioOutputKind.USB ->
                        append("USB compatibility route is active.")
                    kind == AudioOutputKind.BLUETOOTH ->
                        append("Android Bluetooth codec/processing is active; Bluetooth is not bit-perfect.")
                    kind == AudioOutputKind.WIRED ->
                        append("Android wired compatibility route is active.")
                    kind == AudioOutputKind.SPEAKER ->
                        append("Android speaker compatibility route is active.")
                    else ->
                        append("Android compatibility route is active.")
                }
            }
        )
    }

    fun write(pcm: ByteArray, count: Int = pcm.size): Int {
        if (!opened || count <= 0) return -1
        return when (backend) {
            Backend.ANDROID_BIT_PERFECT -> {
                val track = nativeTrack ?: return -1
                if (!nativeStarted) {
                    runCatching { track.play() }.getOrElse { return -1 }
                    nativeStarted = true
                }
                val safeCount = count.coerceAtMost(pcm.size)
                val written = track.write(pcm, 0, safeCount, AudioTrack.WRITE_BLOCKING)
                if (written > 0) nativeWrittenFrames += written / nativeFrameBytes
                written
            }
            Backend.AAUDIO -> nativeWritePcm(pcm, count.coerceAtMost(pcm.size))
            Backend.NONE -> -1
        }
    }

    fun pause(): Boolean = when (backend) {
        Backend.ANDROID_BIT_PERFECT -> {
            val track = nativeTrack ?: return false
            runCatching { track.pause(); true }.getOrDefault(false)
        }
        Backend.AAUDIO -> opened && nativePause() == 0
        Backend.NONE -> false
    }

    fun resume(): Boolean = when (backend) {
        Backend.ANDROID_BIT_PERFECT -> {
            val track = nativeTrack ?: return false
            runCatching { track.play(); nativeStarted = true; true }.getOrDefault(false)
        }
        Backend.AAUDIO -> opened && nativeResume() == 0
        Backend.NONE -> false
    }

    fun flush() {
        when (backend) {
            Backend.ANDROID_BIT_PERFECT -> runCatching {
                nativeTrack?.pause()
                nativeTrack?.flush()
                nativeWrittenFrames = 0L
                nativeStarted = false
            }
            Backend.AAUDIO -> if (opened) nativeFlush()
            Backend.NONE -> Unit
        }
    }

    fun underruns(): Long = when (backend) {
        Backend.ANDROID_BIT_PERFECT -> nativeTrack?.underrunCount?.toLong() ?: 0L
        Backend.AAUDIO -> nativeGetUnderruns()
        Backend.NONE -> 0L
    }

    suspend fun waitForDrain() {
        when (backend) {
            Backend.ANDROID_BIT_PERFECT -> {
                val track = nativeTrack ?: return
                while (opened && backend == Backend.ANDROID_BIT_PERFECT) {
                    val played = track.playbackHeadPosition.toLong() and 0xffff_ffffL
                    if (played >= nativeWrittenFrames) break
                    delay(20)
                }
            }
            Backend.AAUDIO -> while (opened && nativeUsedBytes() > 0) delay(20)
            Backend.NONE -> Unit
        }
    }

    fun close() {
        val oldBackend = backend
        opened = false
        backend = Backend.NONE

        if (oldBackend == Backend.ANDROID_BIT_PERFECT) {
            val track = nativeTrack
            nativeTrack = null
            nativeStarted = false
            nativeWrittenFrames = 0L
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.stop() }
            runCatching { track?.release() }

            val device = nativeDevice
            nativeDevice = null
            if (Build.VERSION.SDK_INT >= 34 && device != null) {
                runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
            }
        } else {
            nativeClose()
        }
    }

    private fun OutputStatus.remember() = also { lastStatus = it }

    private fun bytesPerSample(format: Int) = when (format) {
        PCM_I24 -> 3
        PCM_I32, PCM_FLOAT -> 4
        else -> 2
    }

    private fun bitsFor(format: Int) = when (format) {
        PCM_I16 -> 16
        PCM_I24 -> 24
        PCM_I32, PCM_FLOAT -> 32
        else -> 0
    }

    private fun nameFor(format: Int) = when (format) {
        PCM_I16 -> "PCM 16-bit"
        PCM_I24 -> "PCM 24-bit packed"
        PCM_I32 -> "PCM 32-bit"
        PCM_FLOAT -> "PCM float"
        else -> "Unknown PCM"
    }

    private fun androidEncoding(format: Int): Int = when (format) {
        PCM_I16 -> AudioFormat.ENCODING_PCM_16BIT
        PCM_I24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
        PCM_I32 -> AudioFormat.ENCODING_PCM_32BIT
        PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        else -> AudioFormat.ENCODING_INVALID
    }

    private fun bitsForEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }

    private fun nameForEncoding(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit packed"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"
        else -> "PCM"
    }

    private fun channelMaskFor(channels: Int): Int =
        if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
}
