package com.example.fypdraft.ml

import android.app.Application
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Music Recommendation Engine with Performance Logging for Final Evaluation
 */
class MusicRecommendationEngine(private val application: Application) {

    private val TAG = "RecommendationEngine"

    // AI Components
    private var audioEmotionTagger: AudioEmotionTagger? = null
    private var intentClassifier: IntentClassifier? = null
    private var explanationGenerator: ExplanationGenerator? = null

    // Track which models loaded successfully
    private var modelsLoadedSuccessfully = ModelsStatus()

    // Cache for analyzed songs
    private val emotionCache = mutableMapOf<String, EmotionResult>()

    init {
        // High-level initialization call
        initializeModels()
    }

    /**
     * Initialize all AI models with individual latency tracking.
     */
    private fun initializeModels() {
        val totalStartTime = System.currentTimeMillis()

        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "🤖 INITIALIZING AI MODELS")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "Initializing AI Stack...")

        // Step 1: Verify assets exist
        verifyAssets()

        // Step 2: Intent Classifier
        val intentStart = System.currentTimeMillis()
        Log.d(TAG, "📝 Loading IntentClassifier...")
        try {
            intentClassifier = IntentClassifier(application)
            modelsLoadedSuccessfully.intentClassifier = true
            Log.i(TAG, "IntentClassifier loaded (${System.currentTimeMillis() - intentStart}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to load IntentClassifier (keyword fallback will be used)", e)
            modelsLoadedSuccessfully.intentClassifier = false
        }

        // Step 3: Audio Emotion Tagger
        val audioStart = System.currentTimeMillis()
        Log.d(TAG, "🎵 Loading AudioEmotionTagger...")
        try {
            audioEmotionTagger = AudioEmotionTagger(application)
            modelsLoadedSuccessfully.audioEmotionTagger = true
            Log.i(TAG, "AudioEmotionTagger loaded (${System.currentTimeMillis() - audioStart}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to load AudioEmotionTagger (non-critical)", e)
            modelsLoadedSuccessfully.audioEmotionTagger = false
        }

        // Step 4: Explanation Generator
        val explanationStart = System.currentTimeMillis()
        Log.d(TAG, "💡 Loading ExplanationGenerator...")
        try {
            explanationGenerator = ExplanationGenerator(application)
            modelsLoadedSuccessfully.explanationGenerator = true
            Log.i(TAG, "ExplanationGenerator loaded (${System.currentTimeMillis() - explanationStart}ms)")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to load ExplanationGenerator (template fallback used)", e)
            modelsLoadedSuccessfully.explanationGenerator = false
        }

        val totalTime = System.currentTimeMillis() - totalStartTime
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "🎉 MODEL INITIALIZATION COMPLETE")
        Log.i(TAG, "Engine isReady = ${isReady()} ($totalTime ms total)")
        Log.i(TAG, "=".repeat(60))

        if (!isReady()) {
            throw RuntimeException("AI model initialization failed: no models loaded.")
        }
    }

    // ... Rest of your existing functions (isReady, getStatus, processRecommendation, etc.) stay exactly as they are

    /**
     * Verify that required model files exist in assets
     */
    private fun verifyAssets(): AssetVerificationResult {
        val requiredFiles = listOf(
            "audio_tagging_model_fp16.tflite",
            "explanation_model_fp16.tflite",
            "intent_classifier_optimized.tflite"
        )
        val missingRequired = mutableListOf<String>()
        val missingOptional = mutableListOf<String>()

        requiredFiles.forEach { filename ->
            try {
                application.assets.open(filename).use { }
            } catch (e: Exception) {
                missingRequired.add(filename)
            }
        }

        return AssetVerificationResult(
            allRequiredPresent = missingRequired.isEmpty(),
            missingRequired = missingRequired,
            missingOptional = missingOptional
        )
    }

    fun isReady(): Boolean = modelsLoadedSuccessfully.intentClassifier ||
            modelsLoadedSuccessfully.audioEmotionTagger ||
            modelsLoadedSuccessfully.explanationGenerator

    suspend fun processRecommendation(
        userMessage: String,
        currentSongUri: Uri?,
        currentSongId: String?
    ): RecommendationResult = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext RecommendationResult(false, null, null, null, "Not ready")
        }
        try {
            val intentResult = if (intentClassifier != null) {
                intentClassifier!!.classifyIntent(userMessage)
            } else {
                keywordFallbackIntent(userMessage)
            }

            var emotionResult: EmotionResult? = null
            if (currentSongUri != null && currentSongId != null && audioEmotionTagger != null) {
                emotionResult = getCachedOrAnalyzeEmotion(currentSongUri, currentSongId)
            }

            val explanation = if (explanationGenerator != null) {
                try {
                    explanationGenerator?.generateExplanation(intentResult, emotionResult)
                } catch (e: Exception) {
                    generateFallbackExplanation(intentResult, emotionResult)
                }
            } else {
                generateFallbackExplanation(intentResult, emotionResult)
            }

            return@withContext RecommendationResult(true, intentResult, emotionResult, explanation, null)
        } catch (e: Exception) {
            return@withContext RecommendationResult(false, null, null, null, e.message)
        }
    }

    private fun keywordFallbackIntent(text: String): IntentResult {
        val lower = text.lowercase()
        val intentKeywords = mapOf(
            "relax"     to listOf("relax","calm","chill","peaceful","unwind","soothing","gentle","soft","quiet","zen","lofi","mellow","ambient"),
            "energize"  to listOf("pump","energy","upbeat","workout","hype","party","dance","bass","intense","adrenaline","fast","gym","running"),
            "comfort"   to listOf("sad","comfort","lonely","heartbreak","crying","blue","missing","rainy","melancholy"),
            "focus"     to listOf("focus","concentrate","study","work","productivity","deep","instrumental","no lyrics","coding","think"),
            "discover"  to listOf("surprise","new","explore","trending","hidden","fresh","random","discover","different","indie","experimental"),
            "nostalgia" to listOf("throwback","old","childhood","classic","retro","90s","memories","nostalgic"),
            "romance"   to listOf("romantic","love","date","intimate","sensual","relationship"),
            "sleep"     to listOf("sleep","bedtime","lullaby","dream","drowsy","nighttime","rest"),
            "uplift"    to listOf("happy","cheer","smile","positive","joy","sunshine","good mood","celebrate","grateful","inspiring")
        )

        var bestIntent = "discover"
        var bestScore  = 0
        for ((intent, keywords) in intentKeywords) {
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }
        val confidence = if (bestScore > 0) 0.6f else 0.3f
        val probs = FloatArray(9) { 0.1f }
        return IntentResult(bestIntent, confidence, probs, emptyMap())
    }

    private fun generateFallbackExplanation(intentResult: IntentResult, emotionResult: EmotionResult?): ExplanationResult {
        val intent = intentResult.topIntent
        val emotion = emotionResult?.topEmotion ?: "neutral"
        return ExplanationResult("Based on $intent and $emotion song, here's what we recommend.", 50, intent, emotion, true)
    }

    private suspend fun getCachedOrAnalyzeEmotion(audioUri: Uri, songId: String): EmotionResult? {
        emotionCache[songId]?.let { return it }
        return try {
            val result = audioEmotionTagger?.tagAudio(audioUri)
            if (result != null) emotionCache[songId] = result
            result
        } catch (e: Exception) { null }
    }

    fun close() {
        audioEmotionTagger?.close()
        intentClassifier?.close()
        explanationGenerator?.close()
    }
}
// ---------------------------------------------------------------------------
// Supporting Data Classes for the Recommendation Engine
// ---------------------------------------------------------------------------

data class AssetVerificationResult(
    val allRequiredPresent: Boolean,
    val missingRequired: List<String>,
    val missingOptional: List<String>
)

data class ModelsStatus(
    var intentClassifier: Boolean = false,
    var audioEmotionTagger: Boolean = false,
    var explanationGenerator: Boolean = false
)

data class EngineStatus(
    val isReady: Boolean,
    val modelsLoaded: ModelsStatus,
    val cacheSize: Int,
    val capabilities: List<Pair<String, Boolean>>
)

data class RecommendationResult(
    val success: Boolean,
    val intentResult: IntentResult?,
    val emotionResult: EmotionResult?,
    val explanation: ExplanationResult?,
    val errorMessage: String?
)

data class CacheStats(
    val size: Int,
    val songs: List<String>
)