package com.example.fypdraft.ml

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reinforcement Learning Recommendation Engine
 *
 * Uses a contextual bandit approach:
 * - Context: user mood, time of day, recent listening patterns
 * - Actions: Spotify audio feature targets (valence, energy, danceability, tempo)
 * - Reward: composite score from 7 user signals
 *
 * State is stored in Firebase Firestore for cross-device sync.
 *
 * The model maintains a weight matrix that maps mood contexts to optimal
 * audio feature ranges, updated via gradient-free policy updates (exponential
 * weighted averaging) after each user interaction.
 */

// ── Reward signals ───────────────────────────────────────────────────

enum class RewardType(val weight: Float) {
    PLAYED(0.3f),              // User played a song
    SKIPPED(-0.5f),            // User skipped quickly
    FAVORITED(1.0f),           // Added to favorites (strongest positive)
    SUGGESTION_ACCEPTED(0.6f), // Accepted mascot suggestion
    SUGGESTION_REJECTED(-0.3f),// Rejected mascot suggestion
    MOOD_OVERRIDE(-0.2f),      // Changed mood manually (current recs were wrong)
    LISTEN_DURATION(0.0f)      // Scaled by actual duration ratio (set dynamically)
}

data class RewardEvent(
    val type: RewardType,
    val mood: String,
    val trackFeatures: AudioFeatures?,
    val timestamp: Long = System.currentTimeMillis(),
    val durationRatio: Float = 0f  // 0-1, how much of the song was listened to
)

/**
 * Spotify audio features for a track (from Spotify API or estimated)
 */
data class AudioFeatures(
    val valence: Float = 0.5f,       // 0=sad, 1=happy
    val energy: Float = 0.5f,        // 0=calm, 1=energetic
    val danceability: Float = 0.5f,  // 0=not danceable, 1=very danceable
    val tempo: Float = 120f,         // BPM
    val acousticness: Float = 0.5f,  // 0=electronic, 1=acoustic
    val instrumentalness: Float = 0.3f // 0=vocal, 1=instrumental
)

/**
 * Target audio features for Spotify recommendations API
 */
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

    // ── Model state: mood → feature preferences ──────────────────────
    // Each mood has learned optimal audio feature centers + exploration radius

    private val moodFeatureMap = mutableMapOf<String, MoodFeatureState>()
    private var totalInteractions = 0
    private var isLoaded = false

    // Default feature targets per mood (prior knowledge)
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

    /**
     * Load RL state from Firebase
     */
    suspend fun loadState() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("rl_state")
                .document(userId)
                .get().await()

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
                Log.d(TAG, "Loaded RL state: $totalInteractions interactions, ${moodFeatureMap.size} moods")
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

    /**
     * Save RL state to Firebase
     */
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

            firestore.collection("rl_state")
                .document(userId)
                .set(mapOf(
                    "totalInteractions" to totalInteractions,
                    "moodFeatures" to moodData,
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

    /**
     * Get optimal feature targets for Spotify recommendations API.
     * Uses Upper Confidence Bound (UCB) exploration strategy:
     * less-explored moods get wider feature ranges to encourage discovery.
     */
    fun getFeatureTargets(mood: String, hourOfDay: Int = -1): FeatureTargets {
        val state = moodFeatureMap[mood] ?: moodFeatureMap["neutral"]!!

        // Exploration radius: wider when fewer interactions (UCB-inspired)
        val explorationBonus = if (state.interactionCount > 0) {
            (0.15f * sqrt(ln(totalInteractions.toFloat() + 1f) / state.interactionCount)).coerceAtMost(0.3f)
        } else {
            0.3f // Maximum exploration for unknown moods
        }

        // Time-of-day adjustment
        val timeEnergyShift = when (hourOfDay) {
            in 6..9   -> -0.1f   // Morning: slightly calmer
            in 10..14 -> 0.05f   // Midday: slightly more energy
            in 15..17 -> 0f      // Afternoon: neutral
            in 18..21 -> 0.05f   // Evening: slightly up
            in 22..23 -> -0.15f  // Night: wind down
            in 0..5   -> -0.2f   // Late night: very calm
            else -> 0f
        }

        val targetValence = (state.valence + timeEnergyShift * 0.5f).coerceIn(0f, 1f)
        val targetEnergy = (state.energy + timeEnergyShift).coerceIn(0f, 1f)

        return FeatureTargets(
            targetValence = targetValence,
            targetEnergy = targetEnergy,
            targetDanceability = state.danceability,
            targetTempo = state.tempo,
            minValence = (targetValence - explorationBonus).coerceIn(0f, 1f),
            maxValence = (targetValence + explorationBonus).coerceIn(0f, 1f),
            minEnergy = (targetEnergy - explorationBonus).coerceIn(0f, 1f),
            maxEnergy = (targetEnergy + explorationBonus).coerceIn(0f, 1f)
        )
    }

    /**
     * Record a reward event and update the model.
     * This is the core RL update step.
     */
    suspend fun recordReward(event: RewardEvent) {
        val mood = event.mood
        val state = moodFeatureMap[mood] ?: return

        // Calculate composite reward
        var reward = event.type.weight
        if (event.type == RewardType.LISTEN_DURATION) {
            // Scale by how much of the song was listened to
            // >80% = strong positive, <20% = negative
            reward = (event.durationRatio - 0.3f) * 1.5f // Maps 0-1 to roughly -0.45 to +1.05
        }

        // Update features toward the track's features if reward is positive,
        // away if negative (exponential weighted moving average)
        val features = event.trackFeatures ?: return

        val learningRate = 0.1f / (1f + state.interactionCount * 0.01f) // Decaying LR
        val direction = if (reward > 0) 1f else -1f
        val magnitude = Math.abs(reward) * learningRate

        state.valence += direction * magnitude * (features.valence - state.valence)
        state.energy += direction * magnitude * (features.energy - state.energy)
        state.danceability += direction * magnitude * (features.danceability - state.danceability)
        state.tempo += direction * magnitude * (features.tempo - state.tempo) * 0.1f // Smaller tempo updates

        // Clamp values
        state.valence = state.valence.coerceIn(0f, 1f)
        state.energy = state.energy.coerceIn(0f, 1f)
        state.danceability = state.danceability.coerceIn(0f, 1f)
        state.tempo = state.tempo.coerceIn(60f, 200f)

        // Update confidence
        state.interactionCount++
        state.confidence = (state.interactionCount.toFloat() / (state.interactionCount + 10f))
            .coerceIn(0f, 0.95f)

        totalInteractions++

        moodFeatureMap[mood] = state

        // Save to Firebase periodically (every 5 interactions)
        if (totalInteractions % 5 == 0) {
            saveState()
        }

        Log.d(TAG, "RL update: mood=$mood, reward=$reward, " +
                "valence=${state.valence}, energy=${state.energy}, " +
                "interactions=${state.interactionCount}")
    }

    /**
     * Force save (call on app close)
     */
    suspend fun forceSave() {
        saveState()
    }

    fun isReady(): Boolean = isLoaded

    /**
     * Get mood-to-search query as fallback when Spotify recommendations API
     * isn't available (no seed tracks).
     * Uses learned features to generate smarter queries than static mapping.
     */
    fun getMoodSearchQuery(mood: String): String {
        val state = moodFeatureMap[mood] ?: return mood

        val descriptors = mutableListOf<String>()

        // Valence-based words
        when {
            state.valence > 0.7f -> descriptors.add("happy uplifting")
            state.valence > 0.5f -> descriptors.add("feel good")
            state.valence > 0.3f -> descriptors.add("mellow")
            else -> descriptors.add("melancholy emotional")
        }

        // Energy-based words
        when {
            state.energy > 0.7f -> descriptors.add("high energy pump up")
            state.energy > 0.5f -> descriptors.add("upbeat")
            state.energy > 0.3f -> descriptors.add("moderate")
            else -> descriptors.add("calm relaxing ambient")
        }

        // Danceability
        if (state.danceability > 0.7f) descriptors.add("dance")

        // Tempo hints
        when {
            state.tempo > 130f -> descriptors.add("fast")
            state.tempo < 90f -> descriptors.add("slow")
        }

        return descriptors.joinToString(" ")
    }
}

/**
 * Mutable state for a single mood's learned feature preferences
 */
data class MoodFeatureState(
    var valence: Float,
    var energy: Float,
    var danceability: Float,
    var tempo: Float,
    var confidence: Float,
    var interactionCount: Int
)