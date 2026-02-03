package com.example.fypdraft.ml

import android.app.Application
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Music Recommendation Engine
 *
 * Changes from the previous version:
 *   1. IntentClassifier failure no longer kills the whole engine.
 *   2. isReady() returns true as long as ANY model loaded.
 *   3. processRecommendation() uses a keyword fallback when the
 *      IntentClassifier model is unavailable.
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
        initializeModels()
    }

    /**
     * Verify that required model files exist in assets
     */
    private fun verifyAssets(): AssetVerificationResult {
        val requiredFiles = listOf(
            "audio_tagging_model_fp16.tflite",
            "explanation_model_fp16.tflite",
            "intent_classifier_optimized.tflite"
        )

        val optionalFiles = listOf(
            "intent_parameters.json",
            "tokenizer_config.json"
        )

        val missingRequired = mutableListOf<String>()
        val missingOptional = mutableListOf<String>()

        Log.d(TAG, "🔍 Verifying assets...")

        requiredFiles.forEach { filename ->
            try {
                application.assets.open(filename).use {
                    Log.d(TAG, "✅ Found required file: $filename")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Missing required file: $filename", e)
                missingRequired.add(filename)
            }
        }

        optionalFiles.forEach { filename ->
            try {
                application.assets.open(filename).use {
                    Log.d(TAG, "✅ Found optional file: $filename")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Missing optional file: $filename (this is OK)")
                missingOptional.add(filename)
            }
        }

        return AssetVerificationResult(
            allRequiredPresent = missingRequired.isEmpty(),
            missingRequired = missingRequired,
            missingOptional = missingOptional
        )
    }

    /**
     * Initialize all AI models.  Every model is wrapped in its own
     * try/catch — a failure in one must never prevent the others from
     * loading.
     */
    private fun initializeModels() {
        Log.d(TAG, "=".repeat(60))
        Log.d(TAG, "🤖 INITIALIZING AI MODELS")
        Log.d(TAG, "=".repeat(60))

        // Step 1: Verify assets exist (informational only — we still
        // attempt each model individually so we get precise error messages)
        val assetCheck = verifyAssets()
        if (!assetCheck.allRequiredPresent) {
            Log.w(TAG, "⚠️ Some model files missing: ${assetCheck.missingRequired.joinToString()}")
            Log.w(TAG, "   Continuing — will skip any model whose file is absent")
        }

        Log.d(TAG, "-".repeat(60))

        // Step 2: Intent Classifier
        Log.d(TAG, "📝 Loading IntentClassifier...")
        try {
            intentClassifier = IntentClassifier(application)
            modelsLoadedSuccessfully.intentClassifier = true
            Log.d(TAG, "✅ IntentClassifier loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to load IntentClassifier (keyword fallback will be used)", e)
            modelsLoadedSuccessfully.intentClassifier = false
            // Do NOT rethrow — the rest of the pipeline still works
        }

        Log.d(TAG, "-".repeat(60))

        // Step 3: Audio Emotion Tagger
        Log.d(TAG, "🎵 Loading AudioEmotionTagger...")
        try {
            audioEmotionTagger = AudioEmotionTagger(application)
            modelsLoadedSuccessfully.audioEmotionTagger = true
            Log.d(TAG, "✅ AudioEmotionTagger loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to load AudioEmotionTagger (non-critical)", e)
            modelsLoadedSuccessfully.audioEmotionTagger = false
        }

        Log.d(TAG, "-".repeat(60))

        // Step 4: Explanation Generator
        Log.d(TAG, "💡 Loading ExplanationGenerator...")
        try {
            explanationGenerator = ExplanationGenerator(application)
            modelsLoadedSuccessfully.explanationGenerator = true
            Log.d(TAG, "✅ ExplanationGenerator loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to load ExplanationGenerator (template fallback will be used)", e)
            modelsLoadedSuccessfully.explanationGenerator = false
        }

        Log.d(TAG, "=".repeat(60))
        Log.d(TAG, "🎉 MODEL INITIALIZATION COMPLETE")
        Log.d(TAG, "=".repeat(60))
        Log.d(TAG, "📊 Models Status:")
        Log.d(TAG, "   Intent Classifier:     ${if (modelsLoadedSuccessfully.intentClassifier) "✅ READY" else "⚠️ keyword fallback"}")
        Log.d(TAG, "   Audio Emotion Tagger:  ${if (modelsLoadedSuccessfully.audioEmotionTagger) "✅ READY" else "⚠️ UNAVAILABLE"}")
        Log.d(TAG, "   Explanation Generator: ${if (modelsLoadedSuccessfully.explanationGenerator) "✅ READY" else "⚠️ template fallback"}")
        Log.d(TAG, "=".repeat(60))

        // Only throw if literally zero models loaded — the app has nothing
        // to offer in that case.
        if (!isReady()) {
            throw RuntimeException(
                "AI model initialization failed: no models loaded at all. " +
                        "Check that .tflite files are present in app/src/main/assets/"
            )
        }
    }

    /**
     * The engine is usable as long as at least one model is available.
     */
    fun isReady(): Boolean {
        return modelsLoadedSuccessfully.intentClassifier ||
                modelsLoadedSuccessfully.audioEmotionTagger ||
                modelsLoadedSuccessfully.explanationGenerator
    }

    /**
     * Get detailed status of all models
     */
    fun getStatus(): EngineStatus {
        return EngineStatus(
            isReady = isReady(),
            modelsLoaded = modelsLoadedSuccessfully,
            cacheSize = emotionCache.size,
            capabilities = listOf(
                "Intent Classification" to modelsLoadedSuccessfully.intentClassifier,
                "Audio Emotion Tagging" to modelsLoadedSuccessfully.audioEmotionTagger,
                "Smart Explanations" to modelsLoadedSuccessfully.explanationGenerator
            )
        )
    }

    /**
     * Process user recommendation request
     */
    suspend fun processRecommendation(
        userMessage: String,
        currentSongUri: Uri?,
        currentSongId: String?
    ): RecommendationResult = withContext(Dispatchers.IO) {

        if (!isReady()) {
            return@withContext RecommendationResult(
                success = false,
                intentResult = null,
                emotionResult = null,
                explanation = null,
                errorMessage = "AI models are not ready. Please check logs."
            )
        }

        try {
            Log.d(TAG, "📝 Processing recommendation...")
            Log.d(TAG, "   User message: $userMessage")
            Log.d(TAG, "   Song URI: $currentSongUri")
            Log.d(TAG, "   Song ID: $currentSongId")

            // ── Step 1: Classify intent (model or keyword fallback) ──
            val intentResult = if (intentClassifier != null) {
                intentClassifier!!.classifyIntent(userMessage)
            } else {
                Log.d(TAG, "📝 IntentClassifier unavailable — using keyword fallback")
                keywordFallbackIntent(userMessage)
            }

            Log.d(TAG, "🎯 Intent: ${intentResult.topIntent} (${(intentResult.confidence * 100).toInt()}%)")

            // ── Step 2: Analyse current-song emotion (if tagger loaded) ──
            var emotionResult: EmotionResult? = null

            if (currentSongUri != null && currentSongId != null && audioEmotionTagger != null) {
                try {
                    emotionResult = getCachedOrAnalyzeEmotion(currentSongUri, currentSongId)
                    Log.d(TAG, "🎵 Emotion: ${emotionResult?.topEmotion} (${(emotionResult?.confidence?.times(100))?.toInt()}%)")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to analyze emotion, continuing without it", e)
                }
            } else if (audioEmotionTagger == null) {
                Log.d(TAG, "⚠️ Audio emotion tagger not available, skipping emotion analysis")
            }

            // ── Step 3: Generate explanation (model or template fallback) ──
            val explanation = if (explanationGenerator != null) {
                try {
                    explanationGenerator?.generateExplanation(intentResult, emotionResult)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to generate AI explanation, using template", e)
                    generateFallbackExplanation(intentResult, emotionResult)
                }
            } else {
                generateFallbackExplanation(intentResult, emotionResult)
            }

            Log.d(TAG, "💡 Explanation: ${explanation?.text?.take(100)}...")

            return@withContext RecommendationResult(
                success = true,
                intentResult = intentResult,
                emotionResult = emotionResult,
                explanation = explanation,
                errorMessage = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Recommendation processing failed", e)
            return@withContext RecommendationResult(
                success = false,
                intentResult = null,
                emotionResult = null,
                explanation = null,
                errorMessage = "Processing failed: ${e.message}"
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Keyword fallback — used when IntentClassifier model is unavailable.
    // Word lists mirror the training data's label↔keyword associations.
    // ─────────────────────────────────────────────────────────────────────
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

        var bestIntent = "discover"   // default when nothing matches
        var bestScore  = 0

        for ((intent, keywords) in intentKeywords) {
            val score = keywords.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }

        val confidence = if (bestScore > 0) 0.6f else 0.3f
        val probs = FloatArray(IntentClassifier.INTENT_CLASSES.size) { 0.1f }
        val idx = IntentClassifier.INTENT_CLASSES.indexOf(bestIntent).coerceAtLeast(0)
        probs[idx] = confidence

        return IntentResult(
            topIntent     = bestIntent,
            confidence    = confidence,
            probabilities = probs,
            allIntents    = IntentClassifier.INTENT_CLASSES.zip(probs.toList()).toMap()
        )
    }

    /**
     * Generate fallback explanation when ExplanationGenerator model isn't available
     */
    private fun generateFallbackExplanation(
        intentResult: IntentResult,
        emotionResult: EmotionResult?
    ): ExplanationResult {
        val intent = intentResult.topIntent
        val emotion = emotionResult?.topEmotion ?: "neutral"

        val text = when {
            emotion != "neutral" ->
                "Based on your request to $intent and this $emotion song, here's what we recommend."
            else ->
                "Based on your request to $intent, here's what we recommend."
        }

        return ExplanationResult(
            text = text,
            confidence = 50,
            intent = intent,
            emotion = emotion,
            isTemplated = true
        )
    }

    /**
     * Analyze audio emotion
     */
    suspend fun processAudio(audioUri: Uri, songId: String): EmotionResult? {
        if (audioEmotionTagger == null) {
            Log.w(TAG, "⚠️ Audio emotion tagger not available")
            return null
        }

        return try {
            getCachedOrAnalyzeEmotion(audioUri, songId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to process audio", e)
            null
        }
    }

    /**
     * Get cached emotion or analyze new
     */
    private suspend fun getCachedOrAnalyzeEmotion(
        audioUri: Uri,
        songId: String
    ): EmotionResult? {
        emotionCache[songId]?.let {
            Log.d(TAG, "📦 Using cached emotion for: $songId")
            return it
        }

        Log.d(TAG, "🔬 Analyzing emotion for: $songId")

        return try {
            val result = audioEmotionTagger?.tagAudio(audioUri)

            if (result != null) {
                emotionCache[songId] = result
                Log.d(TAG, "✅ Emotion cached for: $songId")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to analyze emotion for: $songId", e)
            null
        }
    }

    /**
     * Preload emotions for multiple songs (for playlist)
     */
    suspend fun preloadEmotions(uriPairs: List<Pair<Uri, String>>) {
        if (audioEmotionTagger == null) {
            Log.w(TAG, "⚠️ Cannot preload - audio emotion tagger not available")
            return
        }

        Log.d(TAG, "📚 Preloading ${uriPairs.size} song emotions...")

        uriPairs.forEach { (uri, songId) ->
            if (!emotionCache.containsKey(songId)) {
                try {
                    getCachedOrAnalyzeEmotion(uri, songId)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to preload emotion for: $songId", e)
                }
            }
        }

        Log.d(TAG, "✅ Preloading complete. Cache size: ${emotionCache.size}")
    }

    /**
     * Clear emotion cache
     */
    fun clearCache() {
        emotionCache.clear()
        Log.d(TAG, "🧹 Emotion cache cleared")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = emotionCache.size,
            songs = emotionCache.keys.toList()
        )
    }

    /**
     * Clean up resources
     */
    fun close() {
        Log.d(TAG, "🛑 Closing recommendation engine")
        audioEmotionTagger?.close()
        intentClassifier?.close()
        explanationGenerator?.close()
        emotionCache.clear()
    }
}

// ---------------------------------------------------------------------------
// Data classes (unchanged)
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
) {
    fun getSummary(): String {
        val ready = capabilities.filter { it.second }.map { it.first }
        val unavailable = capabilities.filter { !it.second }.map { it.first }

        return buildString {
            appendLine("Engine Status: ${if (isReady) "READY ✅" else "NOT READY ❌"}")
            if (ready.isNotEmpty()) {
                appendLine("Available: ${ready.joinToString()}")
            }
            if (unavailable.isNotEmpty()) {
                appendLine("Unavailable: ${unavailable.joinToString()}")
            }
            appendLine("Cached emotions: $cacheSize songs")
        }
    }
}

data class RecommendationResult(
    val success: Boolean,
    val intentResult: IntentResult?,
    val emotionResult: EmotionResult?,
    val explanation: ExplanationResult?,
    val errorMessage: String?
) {
    fun getSummary(): String {
        return if (success) {
            "Intent: ${intentResult?.topIntent}, Emotion: ${emotionResult?.topEmotion ?: "N/A"}"
        } else {
            "Error: $errorMessage"
        }
    }
}

data class CacheStats(
    val size: Int,
    val songs: List<String>
)