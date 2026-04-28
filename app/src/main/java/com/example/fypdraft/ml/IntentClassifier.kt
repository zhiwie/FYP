package com.example.fypdraft.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Intent Classifier - Classifies user text into music intents
 *
 * Example intents: relax, energize, focus, comfort, discover, etc.
 */
class IntentClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "intent_classifier_optimized.tflite"
        private const val TOKENIZER_FILE = "tokenizer_config.json"

        // Must match the MAX_LEN used during training (was 15, NOT 50)
        private const val MAX_SEQUENCE_LENGTH = 15
        private const val NUM_INTENTS = 9

        // Order must match sorted(set(labels)) from the training script.
        // Cross-check against the "label_mapping" key in tokenizer_config.json
        // and reorder here if they differ.
        val INTENT_CLASSES = listOf(
            "comfort",
            "discover",
            "energize",
            "focus",
            "nostalgia",
            "romance",
            "relax",
            "sleep",
            "uplift"
        )
    }

    private var interpreter: Interpreter? = null
    private lateinit var tokenizer: JsonTokenizer

    init {
        // Load the real tokenizer BEFORE the model so we fail fast with a
        // clear error if tokenizer_config.json is missing or malformed.
        tokenizer = JsonTokenizer(context, TOKENIZER_FILE)
        loadModel()
    }

    private fun loadModel() {
        try {
            val model = loadModelFile(MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load intent classifier model", e)
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
     * Classify user intent from text
     *
     * @param text User's input text
     * @return IntentResult with top intent and confidence
     */
    fun classifyIntent(text: String): IntentResult {
        // Tokenize and pad/truncate to exactly MAX_SEQUENCE_LENGTH
        val tokens = tokenizer.tokenize(text, MAX_SEQUENCE_LENGTH)

        // Input tensor: int32 [1, MAX_SEQUENCE_LENGTH]
        val inputBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
            order(ByteOrder.nativeOrder())
            tokens.forEach { putInt(it) }
        }

        // Output tensor: float32 [1, NUM_INTENTS]
        val outputBuffer = ByteBuffer.allocateDirect(NUM_INTENTS * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run inference
//        interpreter?.run(inputBuffer, outputBuffer)
        // ── PERF TIMING ──────────────────────────────────────────────────
        val startTime = System.currentTimeMillis()
        interpreter?.run(inputBuffer, outputBuffer)
        val elapsed = System.currentTimeMillis() - startTime
        Log.d("PERF", "TFLite intent classification: ${elapsed}ms | input: \"$text\"")
        // ─────────────────────────────────────────────────────────────────



        // Parse output
        outputBuffer.rewind()
        val probabilities = FloatArray(NUM_INTENTS) { outputBuffer.float }

        // Find top intent
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val topIntent = INTENT_CLASSES[maxIndex]
        val confidence = probabilities[maxIndex]

        return IntentResult(
            topIntent = topIntent,
            confidence = confidence,
            probabilities = probabilities,
            allIntents = INTENT_CLASSES.zip(probabilities.toList()).toMap()
        )
    }

    fun close() {
        interpreter?.close()
    }
}

// ---------------------------------------------------------------------------
// JsonTokenizer — reads the real word_index from tokenizer_config.json
//
// WHY the old SimpleTokenizer was broken:
//   It mapped synonym *groups* to the same index (relax/calm/chill → 1).
//   Keras Tokenizer assigns every word its own unique, frequency-ranked index.
//   The model was trained on those unique indices, so feeding it the collapsed
//   ones produced garbage predictions even when the model loaded successfully.
// ---------------------------------------------------------------------------
class JsonTokenizer(context: Context, configFilename: String) {

    private val wordToIndex: Map<String, Int>

    // Keras reserves index 1 for <OOV> when oov_token is set in the
    // training script. Any word not seen during training maps here.
    private val oovIndex: Int

    init {
        val json = context.assets.open(configFilename).bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(json)
        val wordIndexObj = root.getJSONObject("word_index")

        val map = mutableMapOf<String, Int>()
        wordIndexObj.keys().forEach { key ->
            map[key] = wordIndexObj.getInt(key)
        }
        wordToIndex = map

        oovIndex = wordToIndex["<oov>"] ?: 1
    }

    /**
     * Tokenize, truncate/pad to maxLength.
     * Mirrors the Python preprocessing:
     *   - lowercase
     *   - strip non-alphanumeric (keep spaces)
     *   - split on whitespace
     *   - map each token via word_index (OOV → oovIndex)
     *   - post-pad with 0 to maxLength
     */
    fun tokenize(text: String, maxLength: Int): IntArray {
        val words = text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")   // keep digits too, matching Python \W behaviour
            .split(" ")
            .filter { it.isNotEmpty() }

        val indices = words.take(maxLength).map { word ->
            wordToIndex[word] ?: oovIndex
        }

        // Post-pad with 0s to maxLength (matches padding='post' in Python)
        return IntArray(maxLength) { i ->
            if (i < indices.size) indices[i] else 0
        }
    }
}

// ---------------------------------------------------------------------------
// IntentResult (unchanged from original)
// ---------------------------------------------------------------------------
data class IntentResult(
    val topIntent: String,
    val confidence: Float,
    val probabilities: FloatArray,
    val allIntents: Map<String, Float>
) {
    fun getDescription(): String {
        return "Intent: $topIntent (${(confidence * 100).toInt()}% confident)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IntentResult
        if (topIntent != other.topIntent) return false
        if (confidence != other.confidence) return false
        if (!probabilities.contentEquals(other.probabilities)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = topIntent.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        return result
    }
}