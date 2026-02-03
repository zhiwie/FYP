package com.example.fypdraft.ml

import android.content.Context
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audio Emotion Tagger - Analyzes audio files and predicts emotion
 *
 * Usage:
 *   val tagger = AudioEmotionTagger(context)
 *   val result = tagger.tagAudio(songUri)
 *   println(result.topEmotion) // "happy"
 *   println(result.confidence) // 0.78
 */
class AudioEmotionTagger(private val context: Context) {

    companion object {
        // Use FP16 model for better balance of size and accuracy
        private const val MODEL_FILE = "audio_tagging_model_fp16.tflite"

        // Model expects mel-spectrogram: 128 mel bins x 1292 time frames
        private const val N_MELS = 128
        private const val TIME_FRAMES = 1292
        private const val NUM_EMOTIONS = 4

        val EMOTION_CLASSES = listOf("happy", "sad", "calm", "energetic")

        // Emoji mapping for better UX
        val EMOTION_EMOJI = mapOf(
            "happy" to "😊",
            "sad" to "😢",
            "calm" to "😌",
            "energetic" to "⚡"
        )
    }

    private var interpreter: Interpreter? = null
    private val audioPreprocessor = AudioPreprocessor(context)

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val model = loadModelFile(MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false) // Better compatibility
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load audio tagging model", e)
        }
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Tag audio file with emotion
     *
     * @param audioUri URI to audio file (local or content URI)
     * @return EmotionResult with probabilities and top emotion
     */
    suspend fun tagAudio(audioUri: Uri): EmotionResult = withContext(Dispatchers.IO) {
        // Extract mel-spectrogram from audio
        val melSpectrogram = audioPreprocessor.extractMelSpectrogram(audioUri)

        // Prepare input tensor: [1, 128, 1292, 1]
        val inputBuffer = ByteBuffer.allocateDirect(
            1 * N_MELS * TIME_FRAMES * 1 * 4 // 4 bytes per float
        ).apply {
            order(ByteOrder.nativeOrder())

            // Fill with mel-spectrogram data
            for (i in 0 until N_MELS) {
                for (j in 0 until TIME_FRAMES) {
                    putFloat(melSpectrogram[i][j])
                }
            }
        }

        // Prepare output tensor
        val outputBuffer = ByteBuffer.allocateDirect(NUM_EMOTIONS * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run inference
        interpreter?.run(inputBuffer, outputBuffer)

        // Parse output
        outputBuffer.rewind()
        val probabilities = FloatArray(NUM_EMOTIONS) { outputBuffer.float }

        // Find top emotion
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val topEmotion = EMOTION_CLASSES[maxIndex]
        val confidence = probabilities[maxIndex]

        // Create emotion map
        val emotionMap = EMOTION_CLASSES.zip(probabilities.toList()).toMap()

        EmotionResult(
            topEmotion = topEmotion,
            confidence = confidence,
            probabilities = probabilities,
            emotionMap = emotionMap,
            emoji = EMOTION_EMOJI[topEmotion] ?: ""
        )
    }

    /**
     * Batch process multiple audio files (for playlist analysis)
     */
    suspend fun tagAudioBatch(audioUris: List<Uri>): List<EmotionResult> {
        return audioUris.map { tagAudio(it) }
    }

    /**
     * Get dominant emotion from multiple tags (for playlist mood)
     */
    fun getDominantEmotion(results: List<EmotionResult>): String {
        val emotionCounts = mutableMapOf<String, Float>()

        results.forEach { result ->
            emotionCounts[result.topEmotion] =
                (emotionCounts[result.topEmotion] ?: 0f) + result.confidence
        }

        return emotionCounts.maxByOrNull { it.value }?.key ?: "calm"
    }

    fun close() {
        interpreter?.close()
    }
}

/**
 * Result from emotion tagging
 */
data class EmotionResult(
    val topEmotion: String,
    val confidence: Float,
    val probabilities: FloatArray,
    val emotionMap: Map<String, Float>,
    val emoji: String
) {
    /**
     * Get human-readable description
     */
    fun getDescription(): String {
        return "$emoji This song feels $topEmotion (${(confidence * 100).toInt()}% confidence)"
    }

    /**
     * Get all emotions above threshold
     */
    fun getSignificantEmotions(threshold: Float = 0.2f): Map<String, Float> {
        return emotionMap.filter { it.value >= threshold }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmotionResult
        if (topEmotion != other.topEmotion) return false
        if (confidence != other.confidence) return false
        if (!probabilities.contentEquals(other.probabilities)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = topEmotion.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        return result
    }
}