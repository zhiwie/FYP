package com.example.fypdraft.ml

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.ln
import kotlin.math.sqrt

enum class RewardType(val weight: Float) {
    PLAYED(0.3f),
    SKIPPED(-0.5f),
    COMPLETED(0.8f),               // ← NEW: listened to >80% of track
    REPLAYED(1.2f),                // ← NEW: played again immediately
    FAVORITED(1.0f),
    SUGGESTION_ACCEPTED(0.6f),
    SUGGESTION_REJECTED(-0.3f),
    MOOD_OVERRIDE(-0.2f),
    LISTEN_DURATION(0.0f)
}

data class RewardEvent(
    val type: RewardType,
    val mood: String,
    val trackFeatures: AudioFeatures?,
    val timestamp: Long = System.currentTimeMillis(),
    val durationRatio: Float = 0f,
    val queryUsed: String = ""     // ← NEW: which search query produced this track
)

data class AudioFeatures(
    val valence: Float = 0.5f,
    val energy: Float = 0.5f,
    val danceability: Float = 0.5f,
    val tempo: Float = 120f,
    val acousticness: Float = 0.5f,
    val instrumentalness: Float = 0.3f
)

data class FeatureTargets(
    val targetValence: Float,
    val targetEnergy: Float,
    val targetDanceability: Float,
    val targetTempo: Float,
    val minValence: Float,
    val maxValence: Float,
    val minEnergy: Float,
    val maxEnergy: Float
)

class RLRecommendationEngine {

    private val TAG = "RLEngine"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val moodFeatureMap = mutableMapOf<String, MoodFeatureState>()
    private var totalInteractions = 0
    private var isLoaded = false

    // ── NEW: Query weight table ───────────────────────────────────────
    // Maps "mood:query_keyword" → score (positive = good, negative = bad)
    // e.g. "happy:sunshine" → 0.8 means sunshine queries work well for happy mood
    private val queryWeights = mutableMapOf<String, Float>()

    // Session tracking for implicit feedback
    private var sessionStartMs = 0L
    private var currentTrackStartMs = 0L
    private var currentTrackDurationMs = 0L
    private var currentTrackQuery = ""
    private var currentMood = ""

    private val DEFAULT_FEATURES = mapOf(
        "happy"     to AudioFeatures(valence = 0.8f, energy = 0.7f, danceability = 0.7f, tempo = 125f),
        "sad"       to AudioFeatures(valence = 0.2f, energy = 0.3f, danceability = 0.3f, tempo = 85f),
        "calm"      to AudioFeatures(valence = 0.5f, energy = 0.2f, danceability = 0.3f, tempo = 90f),
        "energetic" to AudioFeatures(valence = 0.7f, energy = 0.9f, danceability = 0.8f, tempo = 140f),
        "tired"     to AudioFeatures(valence = 0.4f, energy = 0.2f, danceability = 0.2f, tempo = 80f),
        "focused"   to AudioFeatures(valence = 0.4f, energy = 0.4f, danceability = 0.3f, tempo = 100f, instrumentalness = 0.7f),
        "romantic"  to AudioFeatures(valence = 0.6f, energy = 0.4f, danceability = 0.5f, tempo = 100f, acousticness = 0.6f),
        "neutral"   to AudioFeatures(valence = 0.5f, energy = 0.5f, danceability = 0.5f, tempo = 110f)
    )

    // ── Session tracking API ──────────────────────────────────────────

    /**
     * Call this when a track starts playing.
     * Enables implicit skip/complete detection.
     */
    fun onTrackStarted(mood: String, query: String, durationMs: Long) {
        currentTrackStartMs = System.currentTimeMillis()
        currentTrackDurationMs = durationMs
        currentTrackQuery = query
        currentMood = mood
        if (sessionStartMs == 0L) sessionStartMs = currentTrackStartMs
    }

    /**
     * Call this when user skips or track ends.
     * Returns the listen ratio so HomeScreen can decide whether to fire SKIPPED/COMPLETED.
     */
    fun onTrackEnded(wasSkipped: Boolean): Float {
        if (currentTrackStartMs == 0L || currentTrackDurationMs == 0L) return 0f
        val listenedMs = System.currentTimeMillis() - currentTrackStartMs
        val ratio = (listenedMs.toFloat() / currentTrackDurationMs).coerceIn(0f, 1f)

        // Update query weights based on listen ratio
        if (currentTrackQuery.isNotEmpty() && currentMood.isNotEmpty()) {
            val queryKey = "${currentMood}:${currentTrackQuery}"
            val currentWeight = queryWeights[queryKey] ?: 0f
            // Skip in <15s = strong negative, >80% = positive, in between = proportional
            val queryReward = when {
                wasSkipped && listenedMs < 15_000 -> -0.6f
                ratio > 0.8f                      -> +0.5f
                ratio > 0.5f                      -> +0.2f
                else                              -> -0.1f
            }
            // Exponential moving average with 0.3 learning rate
            queryWeights[queryKey] = currentWeight + 0.3f * (queryReward - currentWeight)
            Log.d(TAG, "Query weight update: $queryKey → ${queryWeights[queryKey]} (ratio=$ratio)")
        }

        currentTrackStartMs = 0L
        return ratio
    }

    /**
     * Get query weight score for a candidate query (used by MoodAwareRecommender).
     * Returns value in range roughly [-1, 1]. Higher = historically better for this mood.
     */
    fun getQueryScore(mood: String, query: String): Float {
        // Score = average weight across all keywords in the query
        val keywords = query.lowercase().split(" ").filter { it.length > 3 }
        if (keywords.isEmpty()) return 0f
        var totalScore = 0f
        var hits = 0
        for (word in keywords) {
            val key = "${mood}:${word}"
            queryWeights[key]?.let { totalScore += it; hits++ }
        }
        return if (hits > 0) totalScore / hits else 0f
    }

    // ── Firebase persistence ──────────────────────────────────────────

    suspend fun loadState() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("rl_state").document(userId).get().await()
            if (doc.exists()) {
                totalInteractions = (doc.getLong("totalInteractions") ?: 0).toInt()

                @Suppress("UNCHECKED_CAST")
                val moods = doc.get("moodFeatures") as? Map<String, Map<String, Any>>
                moods?.forEach { (mood, data) ->
                    moodFeatureMap[mood] = MoodFeatureState(
                        valence = (data["valence"] as? Double)?.toFloat() ?: 0.5f,
                        energy = (data["energy"] as? Double)?.toFloat() ?: 0.5f,
                        danceability = (data["danceability"] as? Double)?.toFloat() ?: 0.5f,
                        tempo = (data["tempo"] as? Double)?.toFloat() ?: 110f,
                        confidence = (data["confidence"] as? Double)?.toFloat() ?: 0.1f,
                        interactionCount = (data["interactionCount"] as? Long)?.toInt() ?: 0
                    )
                }

                // ── Load query weights ────────────────────────────────
                @Suppress("UNCHECKED_CAST")
                val weights = doc.get("queryWeights") as? Map<String, Double>
                weights?.forEach { (key, value) ->
                    queryWeights[key] = value.toFloat()
                }

                Log.d(TAG, "Loaded RL state: $totalInteractions interactions, " +
                        "${moodFeatureMap.size} moods, ${queryWeights.size} query weights")
            } else {
                initializeDefaults()
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load RL state, using defaults", e)
            initializeDefaults()
            isLoaded = true
        }
    }

    private suspend fun saveState() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val moodData = moodFeatureMap.mapValues { (_, state) ->
                mapOf(
                    "valence" to state.valence,
                    "energy" to state.energy,
                    "danceability" to state.danceability,
                    "tempo" to state.tempo,
                    "confidence" to state.confidence,
                    "interactionCount" to state.interactionCount
                )
            }
            firestore.collection("rl_state").document(userId).set(mapOf(
                "totalInteractions" to totalInteractions,
                "moodFeatures" to moodData,
                "queryWeights" to queryWeights,          // ← persist weights
                "lastUpdated" to com.google.firebase.Timestamp.now()
            )).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save RL state", e)
        }
    }

    private fun initializeDefaults() {
        DEFAULT_FEATURES.forEach { (mood, features) ->
            moodFeatureMap[mood] = MoodFeatureState(
                valence = features.valence,
                energy = features.energy,
                danceability = features.danceability,
                tempo = features.tempo,
                confidence = 0.1f,
                interactionCount = 0
            )
        }
    }

    // ── Feature targets (unchanged) ───────────────────────────────────

    fun getFeatureTargets(mood: String, hourOfDay: Int = -1): FeatureTargets {
        val state = moodFeatureMap[mood] ?: moodFeatureMap["neutral"]!!
        val explorationBonus = if (state.interactionCount > 0) {
            (0.15f * sqrt(ln(totalInteractions.toFloat() + 1f) / state.interactionCount))
                .coerceAtMost(0.3f)
        } else 0.3f

        val timeEnergyShift = when (hourOfDay) {
            in 6..9   -> -0.1f
            in 10..14 -> 0.05f
            in 15..17 -> 0f
            in 18..21 -> 0.05f
            in 22..23 -> -0.15f
            in 0..5   -> -0.2f
            else -> 0f
        }

        val targetValence = (state.valence + timeEnergyShift * 0.5f).coerceIn(0f, 1f)
        val targetEnergy  = (state.energy  + timeEnergyShift).coerceIn(0f, 1f)

        return FeatureTargets(
            targetValence     = targetValence,
            targetEnergy      = targetEnergy,
            targetDanceability = state.danceability,
            targetTempo       = state.tempo,
            minValence        = (targetValence - explorationBonus).coerceIn(0f, 1f),
            maxValence        = (targetValence + explorationBonus).coerceIn(0f, 1f),
            minEnergy         = (targetEnergy  - explorationBonus).coerceIn(0f, 1f),
            maxEnergy         = (targetEnergy  + explorationBonus).coerceIn(0f, 1f)
        )
    }

    // ── Reward processing (updated) ───────────────────────────────────

    suspend fun recordReward(event: RewardEvent) {
        val mood = event.mood
        val state = moodFeatureMap[mood] ?: return

        var reward = event.type.weight
        if (event.type == RewardType.LISTEN_DURATION) {
            reward = (event.durationRatio - 0.3f) * 1.5f
        }

        // Update audio feature preferences if we have track features
        val features = event.trackFeatures
        if (features != null) {
            val learningRate = 0.1f / (1f + state.interactionCount * 0.01f)
            val direction = if (reward > 0) 1f else -1f
            val magnitude = kotlin.math.abs(reward) * learningRate

            state.valence      += direction * magnitude * (features.valence - state.valence)
            state.energy       += direction * magnitude * (features.energy - state.energy)
            state.danceability += direction * magnitude * (features.danceability - state.danceability)
            state.tempo        += direction * magnitude * (features.tempo - state.tempo) * 0.1f

            state.valence      = state.valence.coerceIn(0f, 1f)
            state.energy       = state.energy.coerceIn(0f, 1f)
            state.danceability = state.danceability.coerceIn(0f, 1f)
            state.tempo        = state.tempo.coerceIn(60f, 200f)
        }

        // ── NEW: also update query weights if query is provided ───────
        if (event.queryUsed.isNotEmpty()) {
            val keywords = event.queryUsed.lowercase().split(" ").filter { it.length > 3 }
            for (word in keywords) {
                val key = "${mood}:${word}"
                val current = queryWeights[key] ?: 0f
                queryWeights[key] = current + 0.2f * (reward - current)
            }
        }

        state.interactionCount++
        state.confidence = (state.interactionCount.toFloat() / (state.interactionCount + 10f))
            .coerceIn(0f, 0.95f)
        totalInteractions++
        moodFeatureMap[mood] = state

        if (totalInteractions % 5 == 0) saveState()

        Log.d(TAG, "RL update: mood=$mood, reward=$reward, " +
                "valence=${state.valence}, energy=${state.energy}, " +
                "interactions=${state.interactionCount}")
    }

    suspend fun forceSave() = saveState()
    fun isReady() = isLoaded

    fun getMoodSearchQuery(mood: String): String {
        val state = moodFeatureMap[mood] ?: return mood
        val descriptors = mutableListOf<String>()
        when {
            state.valence > 0.7f -> descriptors.add("happy uplifting")
            state.valence > 0.5f -> descriptors.add("feel good")
            state.valence > 0.3f -> descriptors.add("mellow")
            else                 -> descriptors.add("melancholy emotional")
        }
        when {
            state.energy > 0.7f -> descriptors.add("high energy pump up")
            state.energy > 0.5f -> descriptors.add("upbeat")
            state.energy > 0.3f -> descriptors.add("moderate")
            else                -> descriptors.add("calm relaxing ambient")
        }
        if (state.danceability > 0.7f) descriptors.add("dance")
        when {
            state.tempo > 130f -> descriptors.add("fast")
            state.tempo < 90f  -> descriptors.add("slow")
        }
        return descriptors.joinToString(" ")
    }
}

data class MoodFeatureState(
    var valence: Float,
    var energy: Float,
    var danceability: Float,
    var tempo: Float,
    var confidence: Float,
    var interactionCount: Int
)