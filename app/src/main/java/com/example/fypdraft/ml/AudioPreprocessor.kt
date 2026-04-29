package com.example.fypdraft.ml

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * AudioPreprocessor — produces a real mel-spectrogram via:
 *   1. MediaExtractor + MediaCodec  →  decoded 16-bit PCM
 *   2. Resampling to 22 050 Hz
 *   3. STFT (Hann window, 2048-point FFT, 512-sample hop)
 *   4. Mel filterbank (128 bands, 0–11 025 Hz)
 *   5. Log compression + global min-max normalisation → [0, 1]
 *
 * Output shape: [128 mel bins × 1292 time frames]  (matches TFLite model input)
 */
class AudioPreprocessor(private val context: Context) {

    companion object {
        private const val TAG            = "AudioPreprocessor"
        private const val TARGET_SR      = 22050          // Hz the model was trained at
        private const val N_FFT          = 2048           // FFT window length (samples)
        private const val HOP_LENGTH     = 512            // hop between frames (samples)
        private const val N_MELS         = 128
        private const val TARGET_FRAMES  = 1292
        private const val F_MIN          = 0.0            // Hz
        private const val F_MAX          = 11025.0        // Hz  (= TARGET_SR / 2)
        private const val MAX_SAMPLES    = TARGET_SR * 30 // 30-second preview cap
    }

    // ── Public API ────────────────────────────────────────────────────

    suspend fun extractMelSpectrogram(audioUri: Uri): Array<FloatArray> =
        withContext(Dispatchers.IO) {
            try {
                val pcm = decodeToPcm(audioUri)
                if (pcm.first.isEmpty()) {
                    Log.w(TAG, "PCM decode returned 0 samples — using fallback")
                    return@withContext fallback()
                }

                val resampled = if (pcm.second != TARGET_SR)
                    resample(pcm.first, pcm.second, TARGET_SR)
                else
                    pcm.first

                val capped = if (resampled.size > MAX_SAMPLES)
                    resampled.copyOf(MAX_SAMPLES)
                else
                    resampled

                Log.d(TAG, "PCM ready: ${capped.size} samples @ $TARGET_SR Hz")

                val mel = melSpectrogram(capped)
                Log.d(TAG, "Mel-spectrogram: ${mel.size} × ${mel[0].size}")
                mel

            } catch (e: Exception) {
                Log.e(TAG, "extractMelSpectrogram failed", e)
                fallback()
            }
        }

    // ── Step 1: decode MP3/AAC → raw PCM via MediaCodec ─────────────

    /**
     * Returns Pair(samples: FloatArray, sampleRate: Int).
     * Mixes down to mono if stereo.
     */
    private fun decodeToPcm(uri: Uri): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        val allSamples = mutableListOf<Float>()
        var sampleRate = TARGET_SR
        var channelCount = 1

        try {
            extractor.setDataSource(context, uri, null)

            // Find audio track
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = fmt
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found")
                return Pair(floatArrayOf(), TARGET_SR)
            }

            extractor.selectTrack(trackIndex)
            sampleRate   = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime     = format.getString(MediaFormat.KEY_MIME)!!

            Log.d(TAG, "Audio: mime=$mime sr=$sampleRate ch=$channelCount")

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info        = MediaCodec.BufferInfo()
            var inputDone   = false
            var outputDone  = false
            val timeoutUs   = 10_000L

            while (!outputDone) {
                // Feed compressed data into codec
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size  = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoded PCM output
                val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.order(ByteOrder.LITTLE_ENDIAN)

                    // Decoded output is always 16-bit PCM signed
                    val shortCount = info.size / 2
                    val shorts = ShortArray(shortCount)
                    outBuf.asShortBuffer().get(shorts)

                    // Mix to mono and normalise → [-1, 1]
                    var i = 0
                    while (i < shortCount) {
                        var sum = 0f
                        for (ch in 0 until channelCount) {
                            sum += shorts[i + ch] / 32768f
                        }
                        allSamples.add(sum / channelCount)
                        i += channelCount
                        if (allSamples.size >= MAX_SAMPLES) break
                    }

                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                        outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFmt   = codec.outputFormat
                    sampleRate   = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }

                if (allSamples.size >= MAX_SAMPLES) break
            }

            codec.stop()
            codec.release()
            Log.d(TAG, "Decoded ${allSamples.size} mono samples")

        } catch (e: Exception) {
            Log.e(TAG, "decodeToPcm failed", e)
        } finally {
            extractor.release()
        }

        return Pair(allSamples.toFloatArray(), sampleRate)
    }

    // ── Step 2: linear resample ───────────────────────────────────────

    private fun resample(input: FloatArray, fromHz: Int, toHz: Int): FloatArray {
        if (fromHz == toHz) return input
        val ratio  = fromHz.toDouble() / toHz.toDouble()
        val outLen = (input.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val srcPos = i * ratio
            val lo     = srcPos.toInt().coerceIn(0, input.size - 1)
            val hi     = (lo + 1).coerceIn(0, input.size - 1)
            val frac   = (srcPos - lo).toFloat()
            input[lo] * (1f - frac) + input[hi] * frac
        }
    }

    // ── Step 3 + 4: STFT → power spectrum → mel filterbank ──────────

    private fun melSpectrogram(samples: FloatArray): Array<FloatArray> {
        val hannWindow = FloatArray(N_FFT) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (N_FFT - 1)))).toFloat()
        }

        val melFilters = buildMelFilterbank()
        // Raw power spectrogram — filled per frame below
        val powerSpec  = Array(N_MELS) { FloatArray(TARGET_FRAMES) { 0f } }

        for (frame in 0 until TARGET_FRAMES) {
            val start = frame * HOP_LENGTH

            // Build windowed frame (zero-pad if past end of signal)
            val windowed = FloatArray(N_FFT) { i ->
                val idx = start + i
                if (idx < samples.size) samples[idx] * hannWindow[i] else 0f
            }

            // Real FFT → power spectrum (N_FFT/2+1 bins)
            val power = rfftPower(windowed)

            // Apply mel filterbank → linear power per mel bin
            for (m in 0 until N_MELS) {
                var energy = 0f
                for (k in melFilters[m].indices) {
                    energy += melFilters[m][k] * power[k]
                }
                powerSpec[m][frame] = max(energy, 1e-10f)
            }
        }

        // ── librosa.power_to_db(mel, ref=np.max) ─────────────────────
        // ref = global max of the linear power spectrogram
        // dB  = 10 * log10(power / ref)   clamped at top - 80 dB
        var refMax = 1e-10f
        for (m in 0 until N_MELS) for (t in 0 until TARGET_FRAMES) {
            if (powerSpec[m][t] > refMax) refMax = powerSpec[m][t]
        }
        val result = Array(N_MELS) { m ->
            FloatArray(TARGET_FRAMES) { t ->
                val db = 10f * log10(powerSpec[m][t] / refMax)
                max(db, -80f)   // librosa top_db=80 default
            }
        }

        // ── min-max normalise to [0, 1] (matches training pipeline) ──
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (m in 0 until N_MELS) for (t in 0 until TARGET_FRAMES) {
            if (result[m][t] < minV) minV = result[m][t]
            if (result[m][t] > maxV) maxV = result[m][t]
        }
        val range = maxV - minV
        if (range > 0f) {
            for (m in 0 until N_MELS) for (t in 0 until TARGET_FRAMES) {
                result[m][t] = (result[m][t] - minV) / range
            }
        }

        return result
    }

    // ── Real FFT (Cooley-Tukey, radix-2) → power spectrum ────────────

    /**
     * Returns power spectrum of length N_FFT/2+1.
     * Input must be length N_FFT (power of 2).
     */
    private fun rfftPower(x: FloatArray): FloatArray {
        val n   = x.size   // 2048
        val re  = DoubleArray(n) { x[it].toDouble() }
        val im  = DoubleArray(n)

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] } }
        }

        // Cooley-Tukey iterative FFT
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len
            val wRe = cos(ang); val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0; var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k]           = uRe + vRe
                    im[i + k]           = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val tmpRe = curRe * wRe - curIm * wIm
                    curIm     = curRe * wIm + curIm * wRe
                    curRe     = tmpRe
                }
                i += len
            }
            len = len shl 1
        }

        // Power spectrum for positive frequencies only (bins 0 … N_FFT/2)
        val half = n / 2 + 1
        return FloatArray(half) { k ->
            (re[k] * re[k] + im[k] * im[k]).toFloat()
        }
    }

    // ── Step 4 helper: build mel filterbank ──────────────────────────

    /**
     * Returns [N_MELS × (N_FFT/2+1)] triangular mel filter weights.
     * Matches librosa.filters.mel(sr=22050, n_fft=2048, n_mels=128).
     */
    private fun buildMelFilterbank(): Array<FloatArray> {
        val numBins = N_FFT / 2 + 1   // 1025

        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val melMin  = hzToMel(F_MIN)
        val melMax  = hzToMel(F_MAX)

        // N_MELS + 2 equally-spaced mel points
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            melMin + i * (melMax - melMin) / (N_MELS + 1)
        }
        // Convert back to Hz, then to FFT bin index
        val binFreqs = DoubleArray(numBins) { k -> k.toDouble() * TARGET_SR / N_FFT }
        val fftBins  = DoubleArray(N_MELS + 2) { i ->
            val hz = melToHz(melPoints[i])
            // Find nearest FFT bin
            binFreqs.indexOfFirst { it >= hz }.let { idx ->
                if (idx < 0) (numBins - 1).toDouble() else idx.toDouble()
            }
        }

        return Array(N_MELS) { m ->
            FloatArray(numBins) { k ->
                val lower  = fftBins[m]
                val center = fftBins[m + 1]
                val upper  = fftBins[m + 2]
                when {
                    k < lower  || k > upper -> 0f
                    k < center -> ((k - lower) / (center - lower)).toFloat()
                    else       -> ((upper - k) / (upper - center)).toFloat()
                }
            }
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────

    private fun fallback(): Array<FloatArray> {
        Log.w(TAG, "Using fallback spectrogram — predictions will be inaccurate")
        return Array(N_MELS) { FloatArray(TARGET_FRAMES) { 0.5f } }
    }
}