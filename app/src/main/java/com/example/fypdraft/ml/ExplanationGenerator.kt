package com.example.fypdraft.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Explanation Generator - Generates natural language explanations
 * combining user intent and song emotion
 */
class ExplanationGenerator(private val context: Context) {

    companion object {
        private const val MODEL_FILE = "explanation_model_fp16.tflite"

        // Pre-generated explanations for common combinations
        private val EXPLANATION_TEMPLATES = mapOf(
            "relax-calm" to "Perfect match! This calm song will help you relax and unwind. The gentle melodies create a peaceful atmosphere ideal for stress relief.",
            "relax-energetic" to "This energetic song might be too stimulating for relaxation. Consider skipping to find something more calming and soothing.",
            "energize-energetic" to "Excellent choice! This high-energy track will boost your energy levels and keep you motivated. Perfect for workouts or active tasks.",
            "energize-calm" to "This calm song won't give you the energy boost you're looking for. Try something with a faster tempo and more dynamic beats.",
            "focus-calm" to "Great for concentration! This calm music provides a non-distracting background that helps maintain focus during work or study.",
            "focus-energetic" to "This might be too energetic for focused work. Look for something with a steadier, less attention-grabbing rhythm.",
            "comfort-sad" to "This song understands your feelings. Sometimes sad music can be comforting, validating your emotions and providing catharsis.",
            "comfort-happy" to "While this happy song is uplifting, you might prefer something that matches your current mood for deeper emotional comfort.",
            "sleep-calm" to "Perfect for sleep! The calming qualities of this music will help you drift off peacefully. Keep the volume low for best results.",
            "sleep-energetic" to "This energetic track is too stimulating for sleep. Choose something gentler to help your mind and body prepare for rest.",
            "discover-happy" to "This happy song brings positive vibes! Discovering new music like this can expand your musical horizons.",
            "discover-sad" to "An emotional discovery! This song shows the depth and variety available in music. Don't be afraid to explore different moods.",
            "nostalgia-calm" to "This calm song might evoke peaceful memories from your past. Music is powerful for connecting with nostalgic feelings.",
            "romance-happy" to "A joyful romantic track! This happy love song can enhance romantic moments or remind you of special times.",
            "uplift-happy" to "Perfect uplift! This happy music will boost your mood and bring positive energy to your day."
        )
    }

    private var interpreter: Interpreter? = null

    init {
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
            android.util.Log.w("ExplanationGenerator", "Model not available, using templates", e)
            // Model is optional - we can fall back to templates
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
     * Generate explanation combining intent and emotion
     *
     * @param intentResult User's classified intent
     * @param emotionResult Song's detected emotion (optional)
     * @return ExplanationResult with natural language text
     */
    fun generateExplanation(
        intentResult: IntentResult,
        emotionResult: EmotionResult?
    ): ExplanationResult {

        val intent = intentResult.topIntent
        val emotion = emotionResult?.topEmotion ?: "neutral"

        // Try to get template-based explanation
        val key = "$intent-$emotion"
        val explanation = EXPLANATION_TEMPLATES[key] ?: generateGenericExplanation(intent, emotion)

        // Calculate overall confidence
        val intentConfidence = intentResult.confidence
        val emotionConfidence = emotionResult?.confidence ?: 0.5f
        val overallConfidence = ((intentConfidence + emotionConfidence) / 2 * 100).toInt()

        return ExplanationResult(
            text = explanation,
            confidence = overallConfidence,
            intent = intent,
            emotion = emotion,
            isTemplated = EXPLANATION_TEMPLATES.containsKey(key)
        )
    }

    private fun generateGenericExplanation(intent: String, emotion: String): String {
        val intentDescription = when (intent) {
            "relax" -> "relaxation"
            "energize" -> "an energy boost"
            "focus" -> "concentration"
            "comfort" -> "emotional comfort"
            "discover" -> "musical discovery"
            "nostalgia" -> "nostalgic feelings"
            "romance" -> "romantic moments"
            "sleep" -> "better sleep"
            "uplift" -> "mood uplift"
            else -> "your current mood"
        }

        val emotionDescription = when (emotion) {
            "happy" -> "uplifting and positive"
            "sad" -> "emotional and melancholic"
            "calm" -> "peaceful and soothing"
            "energetic" -> "dynamic and energizing"
            else -> "interesting"
        }

        return "You're looking for $intentDescription, and this $emotionDescription song might be a good fit. " +
                "Music can deeply influence our emotional state, so choose what resonates with you right now."
    }

    /**
     * Generate batch explanations for multiple songs
     */
    fun generateBatchExplanations(
        intentResult: IntentResult,
        emotionResults: List<EmotionResult>
    ): List<ExplanationResult> {
        return emotionResults.map { emotionResult ->
            generateExplanation(intentResult, emotionResult)
        }
    }

    fun close() {
        interpreter?.close()
    }
}

/**
 * Result from explanation generation
 */
data class ExplanationResult(
    val text: String,
    val confidence: Int,
    val intent: String,
    val emotion: String,
    val isTemplated: Boolean
) {
    fun getSummary(): String {
        return "AI Analysis ($confidence% confident): $text"
    }
}