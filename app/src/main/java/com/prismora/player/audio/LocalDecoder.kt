package com.prismora.player.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.prismora.player.model.OutputStatus
import com.prismora.player.model.Track
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class LocalDecoder(
    private val context: Context,
    private val audio: AudioEngine,
    private val onStatus: (OutputStatus) -> Unit,
    private val onProgress: (Long) -> Unit,
    private val onPcm: (ByteArray, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> }
) {
    suspend fun play(track: Track, deviceId: Int, startMs: Long = 0L) {
        when (track.extension.ifBlank { extension(track.uri) }) {
            "wav", "wave" -> decodeWav(track.uri, deviceId, startMs)
            "dsf" -> decodeDsf(track.uri, deviceId, startMs)
            else -> decodeMediaCodec(track.uri, deviceId, startMs)
        }
    }

    private suspend fun decodeMediaCodec(uri: Uri, deviceId: Int, startMs: Long) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("Cannot open this audio file")

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("The file contains no audio track")
            extractor.selectTrack(trackIndex)
            if (startMs > 0L) extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: error("Missing audio format")
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2) { "Only mono and stereo files are supported" }

            // Android platform decoders reliably expose interleaved 16-bit PCM for these formats.
            sourceFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val status = audio.open(deviceId, sampleRate, channels, AudioEngine.PCM_I16, 16)
            onStatus(status)
            require(status.connected) { status.note }

            decoder = MediaCodec.createDecoderByType(mime).also {
                it.configure(sourceFormat, null, null, 0)
                it.start()
            }
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder!!.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder!!.getInputBuffer(inputIndex) ?: error("Decoder input unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder!!.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder!!.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder!!.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder!!.outputFormat
                        val outputRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val outputEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(outputRate == sampleRate && outputChannels == channels && outputEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                            "Android decoder changed the PCM format; playback stopped safely"
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val output = decoder!!.getOutputBuffer(outputIndex) ?: error("Decoder output unavailable")
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val pcm = ByteArray(info.size)
                            output.get(pcm)
                            onPcm(pcm, pcm.size, AudioEngine.PCM_I16, channels, sampleRate)
                            if (audio.write(pcm) != pcm.size) error("Audio playback was interrupted")
                            onProgress((info.presentationTimeUs / 1_000L).coerceAtLeast(0L))
                        }
                        decoder!!.releaseOutputBuffer(outputIndex, false)
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            audio.waitForDrain()
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
            audio.close()
        }
    }

    private suspend fun decodeWav(uri: Uri, deviceId: Int, startMs: Long) {
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                val reader = LittleReader(input)
                require(reader.str(4) == "RIFF") { "Not a RIFF file" }
                reader.u32() // Skip RIFF size
                require(reader.str(4) == "WAVE") { "Not a WAVE file" }
                var sampleRate = 44_100
                var channels = 2
                var bits = 16
                var audioFormat = 1
                var dataSize = -1L
                while (true) {
                    val id = reader.strOrNull(4) ?: break
                    val size = reader.u32()
                    when (id) {
                        "fmt " -> {
                            audioFormat = reader.u16()
                            channels = reader.u16()
                            sampleRate = reader.u32().toInt()
                            reader.u32()
                            reader.u16()
                            bits = reader.u16()
                            if (size > 16) reader.skip(size - 16)
                        }
                        "data" -> { dataSize = size; break }
                        else -> reader.skip(size + (size and 1L))
                    }
                }
                require(dataSize >= 0) { "WAV file has no data chunk" }
                require(channels in 1..2) { "Only mono and stereo WAV is supported" }
                val format = when (audioFormat) {
                    1 -> when (bits) {
                        16 -> AudioEngine.PCM_I16
                        24 -> AudioEngine.PCM_I24
                        32 -> AudioEngine.PCM_I32
                        else -> error("Unsupported PCM depth: $bits-bit")
                    }
                    3 -> {
                        require(bits == 32) { "Only 32-bit float WAV is supported" }
                        AudioEngine.PCM_FLOAT
                    }
                    else -> error("Unsupported WAV encoding: $audioFormat")
                }
                val bytesPerFrame = channels * (bits / 8)
                val requestedSkip = ((startMs * sampleRate / 1_000L) * bytesPerFrame)
                    .coerceAtMost(dataSize)
                    .let { it - it % bytesPerFrame }
                reader.skip(requestedSkip)
                var consumed = requestedSkip

                val status = audio.open(deviceId, sampleRate, channels, format, bits)
                onStatus(status)
                require(status.connected) { status.note }
                val block = ByteArray(bytesPerFrame * 2_048)
                while (consumed < dataSize) {
                    coroutineContext.ensureActive()
                    val count = input.read(block, 0, minOf(block.size.toLong(), dataSize - consumed).toInt())
                    if (count <= 0) break
                    onPcm(block, count, format, channels, sampleRate)
                    if (audio.write(block, count) != count) error("Audio playback was interrupted")
                    consumed += count
                    onProgress(consumed / bytesPerFrame * 1_000L / sampleRate)
                }
                audio.waitForDrain()
            } ?: error("Cannot open this WAV file")
        } finally {
            audio.close()
        }
    }

    private suspend fun decodeDsf(uri: Uri, deviceId: Int, startMs: Long) {
        try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                val reader = LongReader(input)
                require(reader.str(4) == "DSD ") { "Not a DSF file" }
                reader.u64(); reader.u64(); reader.u64()
                var channels = 2
                var sampleRate = 2_822_400
                var bits = 1
                var blockSize = 4096
                var dataBytes = 0L
                while (true) {
                    val id = reader.strOrNull(4) ?: error("DSF data chunk is missing")
                    val size = reader.u64()
                    when (id) {
                        "fmt " -> {
                            require(size >= 52) { "Invalid DSF format chunk" }
                            reader.u32(); reader.u32(); reader.u32()
                            channels = reader.u32().toInt()
                            sampleRate = reader.u32().toInt()
                            bits = reader.u32().toInt()
                            reader.u64()
                            blockSize = reader.u32().toInt()
                            reader.u32()
                            if (size > 52) reader.skip(size - 52)
                        }
                        "data" -> { dataBytes = size - 12; break }
                        else -> reader.skip(size - 12)
                    }
                }
                require(bits == 1 && channels in 1..2) { "Unsupported DSF channel/bit layout" }
                require(sampleRate % 44_100 == 0) { "Unsupported DSF rate: $sampleRate" }
                val decimation = sampleRate / 44_100
                val blockGroup = blockSize.toLong() * channels
                val desiredPerChannel = startMs * sampleRate / 1_000L / 8L
                val blocksToSkip = desiredPerChannel / blockSize
                val skipped = (blocksToSkip * blockGroup).coerceAtMost(dataBytes)
                reader.skip(skipped)
                var consumed = skipped

                val status = audio.open(deviceId, 44_100, channels, AudioEngine.PCM_FLOAT, 32)
                onStatus(status)
                require(status.connected) { status.note }
                val channelData = Array(channels) { ByteArray(blockSize) }
                val filterState = FloatArray(channels)
                while (dataBytes - consumed >= blockGroup) {
                    coroutineContext.ensureActive()
                    for (channel in 0 until channels) reader.readFully(channelData[channel], 0, blockSize)
                    consumed += blockGroup
                    val samples = blockSize * 8 / decimation
                    val pcm = ByteBuffer.allocate(samples * channels * 4).order(ByteOrder.nativeOrder())
                    for (sample in 0 until samples) {
                        for (channel in 0 until channels) {
                            var ones = 0
                            repeat(decimation) { offset ->
                                val bit = sample * decimation + offset
                                val value = channelData[channel][bit / 8].toInt() and 0xff
                                ones += value shr (bit and 7) and 1
                            }
                            val raw = 2f * ones / decimation - 1f
                            filterState[channel] += 0.35f * (raw - filterState[channel])
                            pcm.putFloat(filterState[channel])
                        }
                    }
                    val bytes = pcm.array()
                    onPcm(bytes, bytes.size, AudioEngine.PCM_FLOAT, channels, 44_100)
                    if (audio.write(bytes) != bytes.size) error("Audio playback was interrupted")
                    onProgress(consumed / channels * 8L * 1_000L / sampleRate)
                }
                audio.waitForDrain()
            } ?: error("Cannot open this DSF file")
        } finally {
            audio.close()
        }
    }

    private fun extension(uri: Uri) = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase()

    private class LittleReader(private val input: java.io.InputStream) {
        fun str(length: Int) = String(readBytes(length), Charsets.US_ASCII)
        fun strOrNull(length: Int) = runCatching { str(length) }.getOrNull()
        fun u16(): Int { val b = readBytes(2); return (b[0].toInt() and 255) or ((b[1].toInt() and 255) shl 8) }
        fun u32(): Long { val b = readBytes(4); return (b[0].toLong() and 255) or ((b[1].toLong() and 255) shl 8) or ((b[2].toLong() and 255) shl 16) or ((b[3].toLong() and 255) shl 24) }
        fun skip(count: Long) { var left = count; while (left > 0) { val n = input.skip(left); if (n > 0) left -= n else if (input.read() >= 0) left-- else error("Unexpected end of file") } }
        private fun readBytes(count: Int) = ByteArray(count).also { data -> var read = 0; while (read < count) { val n = input.read(data, read, count - read); if (n < 0) error("Unexpected end of file"); read += n } }
    }

    private class LongReader(private val input: java.io.InputStream) {
        fun str(length: Int) = String(readBytes(length), Charsets.US_ASCII)
        fun strOrNull(length: Int) = runCatching { str(length) }.getOrNull()
        fun u32(): Long { val b = readBytes(4); return (b[0].toLong() and 255) or ((b[1].toLong() and 255) shl 8) or ((b[2].toLong() and 255) shl 16) or ((b[3].toLong() and 255) shl 24) }
        fun u64(): Long { val b = readBytes(8); var value = 0L; repeat(8) { value = value or ((b[it].toLong() and 255) shl (8 * it)) }; return value }
        fun skip(count: Long) { var left = count; while (left > 0) { val n = input.skip(left); if (n > 0) left -= n else if (input.read() >= 0) left-- else error("Unexpected end of file") } }
        fun readFully(data: ByteArray, offset: Int, count: Int) { var read = 0; while (read < count) { val n = input.read(data, offset + read, count - read); if (n < 0) error("Unexpected end of file"); read += n } }
        private fun readBytes(count: Int) = ByteArray(count).also { readFully(it, 0, count) }
    }
}
