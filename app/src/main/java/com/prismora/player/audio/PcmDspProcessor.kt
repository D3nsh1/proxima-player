package com.prismora.player.audio

import com.prismora.player.model.DspSettings
import com.prismora.player.model.EqBand
import com.prismora.player.model.EqFilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Real-time, allocation-free PCM DSP used by every local decoder path. */
class PcmDspProcessor(initial: DspSettings = DspSettings()) {
    @Volatile private var requested = sanitize(initial)
    private var applied: DspSettings? = null
    private var appliedRate = 0
    private var appliedChannels = 0
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var compressorEnvelope = 0f

    fun update(settings: DspSettings) {
        requested = sanitize(settings)
    }

    @Synchronized
    fun reset() {
        filters.forEach { channel -> channel.forEach(Biquad::reset) }
        compressorEnvelope = 0f
    }

    @Synchronized
    fun processInPlace(data: ByteArray, byteCount: Int, format: Int, channels: Int, sampleRate: Int) {
        val settings = requested
        if (!settings.enabled || byteCount <= 0 || channels !in 1..2 || sampleRate <= 0) return
        ensureFilters(settings, sampleRate, channels)

        val bytesPerSample = when (format) {
            AudioEngine.PCM_I24 -> 3
            AudioEngine.PCM_I32, AudioEngine.PCM_FLOAT -> 4
            else -> 2
        }
        val frameBytes = bytesPerSample * channels
        val frames = min(byteCount, data.size) / frameBytes
        if (frames <= 0) return

        val preamp = dbToLinear(settings.preampDb)
        val crossfeed = settings.crossfeed.coerceIn(0f, .65f)
        val attack = coefficient(settings.compressorAttackMs, sampleRate)
        val release = coefficient(settings.compressorReleaseMs, sampleRate)
        val threshold = dbToLinear(settings.compressorThresholdDb)
        val ceiling = dbToLinear(settings.limiterCeilingDb).coerceIn(.1f, 1f)

        var frameOffset = 0
        repeat(frames) {
            var left = readSample(data, frameOffset, format) * preamp
            var right = if (channels == 2) readSample(data, frameOffset + bytesPerSample, format) * preamp else left

            if (channels == 2 && crossfeed > 0f) {
                val originalLeft = left
                val originalRight = right
                val direct = 1f - crossfeed * .5f
                left = originalLeft * direct + originalRight * crossfeed * .5f
                right = originalRight * direct + originalLeft * crossfeed * .5f
            }

            filters.getOrNull(0)?.forEach { left = it.process(left) }
            if (channels == 2) filters.getOrNull(1)?.forEach { right = it.process(right) }
            else right = left

            val peak = max(abs(left), abs(right))
            compressorEnvelope = if (peak > compressorEnvelope) {
                attack * compressorEnvelope + (1f - attack) * peak
            } else {
                release * compressorEnvelope + (1f - release) * peak
            }

            var gain = 1f
            if (settings.compressorEnabled && compressorEnvelope > threshold && threshold > 0f) {
                val overDb = linearToDb(compressorEnvelope) - settings.compressorThresholdDb
                val reducedDb = overDb - overDb / settings.compressorRatio.coerceAtLeast(1f)
                gain *= dbToLinear(-reducedDb)
            }
            if (settings.limiterEnabled) {
                val postCompressorPeak = peak * gain
                if (postCompressorPeak > ceiling) gain *= ceiling / postCompressorPeak
            }

            writeSample(data, frameOffset, format, (left * gain).coerceIn(-1f, 1f))
            if (channels == 2) writeSample(data, frameOffset + bytesPerSample, format, (right * gain).coerceIn(-1f, 1f))
            frameOffset += frameBytes
        }
    }

    private fun ensureFilters(settings: DspSettings, sampleRate: Int, channels: Int) {
        if (applied == settings && appliedRate == sampleRate && appliedChannels == channels) return
        val active = settings.bands.filter { it.enabled }
        filters = Array(channels) {
            Array(active.size) { index -> Biquad.from(active[index], sampleRate) }
        }
        applied = settings
        appliedRate = sampleRate
        appliedChannels = channels
        compressorEnvelope = 0f
    }

    private fun sanitize(value: DspSettings): DspSettings = value.copy(
        preampDb = value.preampDb.coerceIn(-24f, 12f),
        bands = value.bands.take(24).map { band ->
            band.copy(
                frequencyHz = band.frequencyHz.coerceIn(10f, 40_000f),
                gainDb = band.gainDb.coerceIn(-24f, 24f),
                q = band.q.coerceIn(.1f, 20f)
            )
        },
        compressorThresholdDb = value.compressorThresholdDb.coerceIn(-60f, 0f),
        compressorRatio = value.compressorRatio.coerceIn(1f, 20f),
        compressorAttackMs = value.compressorAttackMs.coerceIn(.1f, 250f),
        compressorReleaseMs = value.compressorReleaseMs.coerceIn(10f, 2_000f),
        limiterCeilingDb = value.limiterCeilingDb.coerceIn(-12f, 0f),
        crossfeed = value.crossfeed.coerceIn(0f, .65f)
    )

    private fun coefficient(milliseconds: Float, sampleRate: Int): Float =
        exp(-1f / (sampleRate * milliseconds.coerceAtLeast(.1f) / 1_000f))

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    private fun linearToDb(value: Float): Float = (20.0 * ln(value.coerceAtLeast(1e-9f).toDouble()) / ln(10.0)).toFloat()

    private fun readSample(data: ByteArray, offset: Int, format: Int): Float = when (format) {
        AudioEngine.PCM_I24 -> {
            var value = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16)
            if (value and 0x800000 != 0) value = value or -0x1000000
            value / 8_388_608f
        }
        AudioEngine.PCM_I32 -> {
            val value = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                (data[offset + 3].toInt() shl 24)
            (value / 2_147_483_648.0).toFloat()
        }
        AudioEngine.PCM_FLOAT -> {
            val bits = (data[offset].toInt() and 0xff) or
                ((data[offset + 1].toInt() and 0xff) shl 8) or
                ((data[offset + 2].toInt() and 0xff) shl 16) or
                (data[offset + 3].toInt() shl 24)
            Float.fromBits(bits).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
        }
        else -> {
            val value = (data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)
            value.toShort() / 32768f
        }
    }

    private fun writeSample(data: ByteArray, offset: Int, format: Int, sample: Float) {
        when (format) {
            AudioEngine.PCM_I24 -> {
                val value = (sample * 8_388_607f).toInt().coerceIn(-8_388_608, 8_388_607)
                data[offset] = value.toByte()
                data[offset + 1] = (value shr 8).toByte()
                data[offset + 2] = (value shr 16).toByte()
            }
            AudioEngine.PCM_I32 -> {
                val value = (sample.toDouble() * 2_147_483_647.0).toLong().coerceIn(-2_147_483_648L, 2_147_483_647L).toInt()
                data[offset] = value.toByte()
                data[offset + 1] = (value shr 8).toByte()
                data[offset + 2] = (value shr 16).toByte()
                data[offset + 3] = (value shr 24).toByte()
            }
            AudioEngine.PCM_FLOAT -> {
                val value = sample.toBits()
                data[offset] = value.toByte()
                data[offset + 1] = (value shr 8).toByte()
                data[offset + 2] = (value shr 16).toByte()
                data[offset + 3] = (value shr 24).toByte()
            }
            else -> {
                val value = (sample * 32767f).toInt().coerceIn(-32768, 32767)
                data[offset] = value.toByte()
                data[offset + 1] = (value shr 8).toByte()
            }
        }
    }

    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float
    ) {
        private var z1 = 0f
        private var z2 = 0f

        fun process(input: Float): Float {
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            return output.takeIf { it.isFinite() } ?: 0f
        }

        fun reset() { z1 = 0f; z2 = 0f }

        companion object {
            fun from(band: EqBand, sampleRate: Int): Biquad {
                val frequency = band.frequencyHz.coerceIn(10f, sampleRate * .49f)
                val omega = (2.0 * PI * frequency / sampleRate).toFloat()
                val cosine = cos(omega)
                val sine = sin(omega)
                val q = band.q.coerceIn(.1f, 20f)
                val alpha = sine / (2f * q)
                val a = 10f.pow(band.gainDb / 40f)

                var b0: Float
                var b1: Float
                var b2: Float
                var a0: Float
                var a1: Float
                var a2: Float
                when (band.type) {
                    EqFilterType.PEAK -> {
                        b0 = 1f + alpha * a; b1 = -2f * cosine; b2 = 1f - alpha * a
                        a0 = 1f + alpha / a; a1 = -2f * cosine; a2 = 1f - alpha / a
                    }
                    EqFilterType.LOW_SHELF -> {
                        val slope = sqrt(a) / q
                        b0 = a * ((a + 1f) - (a - 1f) * cosine + slope * sine)
                        b1 = 2f * a * ((a - 1f) - (a + 1f) * cosine)
                        b2 = a * ((a + 1f) - (a - 1f) * cosine - slope * sine)
                        a0 = (a + 1f) + (a - 1f) * cosine + slope * sine
                        a1 = -2f * ((a - 1f) + (a + 1f) * cosine)
                        a2 = (a + 1f) + (a - 1f) * cosine - slope * sine
                    }
                    EqFilterType.HIGH_SHELF -> {
                        val slope = sqrt(a) / q
                        b0 = a * ((a + 1f) + (a - 1f) * cosine + slope * sine)
                        b1 = -2f * a * ((a - 1f) + (a + 1f) * cosine)
                        b2 = a * ((a + 1f) + (a - 1f) * cosine - slope * sine)
                        a0 = (a + 1f) - (a - 1f) * cosine + slope * sine
                        a1 = 2f * ((a - 1f) - (a + 1f) * cosine)
                        a2 = (a + 1f) - (a - 1f) * cosine - slope * sine
                    }
                    EqFilterType.LOW_PASS -> {
                        b0 = (1f - cosine) / 2f; b1 = 1f - cosine; b2 = (1f - cosine) / 2f
                        a0 = 1f + alpha; a1 = -2f * cosine; a2 = 1f - alpha
                    }
                    EqFilterType.HIGH_PASS -> {
                        b0 = (1f + cosine) / 2f; b1 = -(1f + cosine); b2 = (1f + cosine) / 2f
                        a0 = 1f + alpha; a1 = -2f * cosine; a2 = 1f - alpha
                    }
                }
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }
}
