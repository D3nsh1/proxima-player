package com.prismora.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** FFT spectrum meter tuned for smooth 120 Hz-capable Compose rendering. */
class SpectrumAnalyzer(private val barCount: Int = 40) {
    private val smoothed = FloatArray(barCount)
    private val raw = FloatArray(barCount)
    private var real = DoubleArray(1024)
    private var imaginary = DoubleArray(1024)
    @Volatile private var smoothing = .28f
    @Volatile private var sensitivity = 1f

    fun setTuning(smoothing: Float, sensitivity: Float) {
        this.smoothing = smoothing.coerceIn(0f, 1f)
        this.sensitivity = sensitivity.coerceIn(.55f, 1.8f)
    }

    fun reset(): List<Float> {
        smoothed.fill(0f)
        return smoothed.toList()
    }

    fun analyze(
        pcm: ByteArray,
        byteCount: Int,
        format: Int,
        channels: Int,
        sampleRate: Int
    ): List<Float> {
        if (byteCount <= 0 || channels <= 0 || sampleRate <= 0) return smoothed.toList()
        val bytesPerSample = when (format) {
            AudioEngine.PCM_I24 -> 3
            AudioEngine.PCM_I32, AudioEngine.PCM_FLOAT -> 4
            else -> 2
        }
        val frameBytes = bytesPerSample * channels
        val availableFrames = byteCount / frameBytes
        if (availableFrames < 128) return smoothed.toList()

        var n = 128
        while (n * 2 <= min(1024, availableFrames)) n *= 2
        if (real.size != n) {
            real = DoubleArray(n)
            imaginary = DoubleArray(n)
        } else {
            real.fill(0.0)
            imaginary.fill(0.0)
        }
        val stride = max(1, availableFrames / n)
        var frame = 0
        var peak = 0f
        for (i in 0 until n) {
            var mono = 0f
            val baseFrame = min(frame, availableFrames - 1)
            for (channel in 0 until channels) {
                val offset = baseFrame * frameBytes + channel * bytesPerSample
                mono += sampleAt(pcm, offset, format)
            }
            mono /= channels.toFloat()
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            real[i] = mono * window
            peak = max(peak, kotlin.math.abs(mono))
            frame += stride
        }

        fft(real, imaginary)

        val minHz = 45.0
        val maxHz = min(18_000.0, sampleRate * 0.47)
        raw.fill(0f)
        for (bar in 0 until barCount) {
            val lowT = bar.toDouble() / barCount
            val highT = (bar + 1).toDouble() / barCount
            val lowHz = minHz * (maxHz / minHz).pow(lowT)
            val highHz = minHz * (maxHz / minHz).pow(highT)
            val startBin = max(1, (lowHz * n / sampleRate).toInt())
            val endBin = max(startBin + 1, (highHz * n / sampleRate).toInt()).coerceAtMost(n / 2)
            var power = 0.0
            var bins = 0
            for (bin in startBin until endBin) {
                val magnitude = 2.0 * sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) / n
                power += magnitude * magnitude
                bins++
            }
            val amplitude = sqrt(power / max(1, bins))
            val db = 20.0 * ln(amplitude + 1e-9) / ln(10.0)
            var normalized = ((db + 78.0) / 78.0).toFloat().coerceIn(0f, 1f).pow(.62f)
            val frequencyLift = .92f + .16f * (bar.toFloat() / max(1, barCount - 1))
            val levelLift = (.90f + peak * .55f).coerceIn(.90f, 1.22f)
            raw[bar] = (normalized * frequencyLift * levelLift * sensitivity).coerceIn(0f, 1f)
        }
        val spatialSide = .02f + smoothing * .10f
        val spatialCenter = 1f - spatialSide * 2f
        val attack = .96f - smoothing * .30f
        val release = .52f - smoothing * .34f
        for (bar in 0 until barCount) {
            val left = raw[max(0, bar - 1)]
            val right = raw[min(barCount - 1, bar + 1)]
            val normalized = raw[bar] * spatialCenter + left * spatialSide + right * spatialSide
            val old = smoothed[bar]
            val blend = if (normalized > old) attack else release
            smoothed[bar] = old + (normalized - old) * blend.coerceIn(.12f, .96f)
        }
        return smoothed.toList()
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < n) {
                var waveReal = 1.0
                var waveImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * waveReal - imaginary[odd] * waveImaginary
                    val oddImaginary = real[odd] * waveImaginary + imaginary[odd] * waveReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = waveReal * stepReal - waveImaginary * stepImaginary
                    waveImaginary = waveReal * stepImaginary + waveImaginary * stepReal
                    waveReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private fun sampleAt(data: ByteArray, offset: Int, format: Int): Float {
        if (offset < 0 || offset >= data.size) return 0f
        return when (format) {
            AudioEngine.PCM_I24 -> {
                if (offset + 2 >= data.size) 0f else {
                    var value = (data[offset].toInt() and 0xff) or
                        ((data[offset + 1].toInt() and 0xff) shl 8) or
                        ((data[offset + 2].toInt() and 0xff) shl 16)
                    if (value and 0x800000 != 0) value = value or -0x1000000
                    value / 8_388_608f
                }
            }
            AudioEngine.PCM_I32 -> {
                if (offset + 3 >= data.size) 0f else {
                    val value = (data[offset].toInt() and 0xff) or
                        ((data[offset + 1].toInt() and 0xff) shl 8) or
                        ((data[offset + 2].toInt() and 0xff) shl 16) or
                        (data[offset + 3].toInt() shl 24)
                    value / 2_147_483_648f
                }
            }
            AudioEngine.PCM_FLOAT -> {
                if (offset + 3 >= data.size) 0f else {
                    val bits = (data[offset].toInt() and 0xff) or
                        ((data[offset + 1].toInt() and 0xff) shl 8) or
                        ((data[offset + 2].toInt() and 0xff) shl 16) or
                        (data[offset + 3].toInt() shl 24)
                    Float.fromBits(bits).takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
                }
            }
            else -> {
                if (offset + 1 >= data.size) 0f else {
                    val value = (data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)
                    value.toShort() / 32768f
                }
            }
        }
    }
}
