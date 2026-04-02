package com.example.fypdraft.model

import android.util.Log
import com.example.fypdraft.data.repository.MoodAnalyticsEntry
import com.example.fypdraft.data.repository.MoodHistoryRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.*

// ══════════════════════════════════════════════════════════════════════════
// PERSONALITY TYPES
// ══════════════════════════════════════════════════════════════════════════

/**
 * Pet personality archetypes — computed from the user's listening patterns.
 *
 * Each type influences: pet idle speed, wander frequency, thought bubble
 * content, default animation style, greeting tone, and equalizer intensity.
 */
enum class PetPersonality(
    val displayName: String,
    val emoji: String,
    val description: String,
    val idleSpeedMultiplier: Float,     // 1.0 = normal, <1 = slower, >1 = faster
    val wanderFrequencyMs: Long,        // Base wander interval
    val equalizerIntensity: Float,      // 0.5 = subtle, 1.0 = normal, 1.5 = intense
    val bounciness: Float               // Spring damping ratio for movement
) {
    ZEN(
        "Zen Master", "🧘", "Calm, collected, loves peaceful vibes",
        idleSpeedMultiplier = 0.6f,
        wanderFrequencyMs = 10_000,
        equalizerIntensity = 0.7f,
        bounciness = 0.9f               // Very smooth, minimal bounce
    ),
    PARTY(
        "Party Animal", "🎉", "Energetic, always ready to dance",
        idleSpeedMultiplier = 1.4f,
        wanderFrequencyMs = 4_000,
        equalizerIntensity = 1.4f,
        bounciness = 0.4f               // Very bouncy
    ),
    EMO(
        "Deep Feeler", "🌧️", "Sensitive, drawn to emotional music",
        idleSpeedMultiplier = 0.8f,
        wanderFrequencyMs = 8_000,
        equalizerIntensity = 0.85f,
        bounciness = 0.7f
    ),
    SCHOLAR(
        "Study Buddy", "📚", "Focused, productive, minimal distractions",
        idleSpeedMultiplier = 0.7f,
        wanderFrequencyMs = 12_000,
        equalizerIntensity = 0.6f,
        bounciness = 0.85f              // Smooth, deliberate
    ),
    EXPLORER(
        "Music Explorer", "🎵", "Eclectic taste, always discovering",
        idleSpeedMultiplier = 1.1f,
        wanderFrequencyMs = 5_000,
        equalizerIntensity = 1.1f,
        bounciness = 0.55f
    ),
    NIGHTOWL(
        "Night Owl", "🦉", "Most active late at night",
        idleSpeedMultiplier = 0.9f,
        wanderFrequencyMs = 7_000,
        equalizerIntensity = 0.8f,
        bounciness = 0.65f
    )
}

// ══════════════════════════════════════════════════════════════════════════
// TIME-BASED PATTERNS
// ══════════════════════════════════════════════════════════════════════════

enum class TimeOfDay(val label: String, val hours: IntRange) {
    MORNING("morning", 5..11),
    AFTERNOON("afternoon", 12..16),
    EVENING("evening", 17..20),
    NIGHT("night", 21..23);

    companion object {
        fun current(): TimeOfDay {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (h) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..20 -> EVENING
                else -> NIGHT
            }
        }

        fun fromHour(h: Int): TimeOfDay = when (h) {
            in 5..11 -> MORNING; in 12..16 -> AFTERNOON; in 17..20 -> EVENING; else -> NIGHT
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// PERSONALITY PROFILE (persisted to Firebase)
// ══════════════════════════════════════════════════════════════════════════

data class PersonalityProfile(
    val personalityType: PetPersonality = PetPersonality.EXPLORER,
    val moodProfile: Map<String, Float> = emptyMap(),       // mood → weight (0..1)
    val peakMoods: Map<String, String> = emptyMap(),         // timeOfDay → dominant mood
    val activeHours: Map<String, Int> = emptyMap(),          // timeOfDay → session count
    val favoriteGenres: Map<String, Int> = emptyMap(),       // genre → play count
    val totalSessions: Int = 0,
    val dominantMood: String = "neutral",
    val lastUpdated: Long = 0
) {
    /**
     * Get a personalized greeting based on time of day, personality, and mood patterns.
     */
    fun getGreeting(petName: String): String {
        val time = TimeOfDay.current()
        val peakMood = peakMoods[time.label] ?: dominantMood

        return when (personalityType) {
            PetPersonality.ZEN -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "Breathe in, breathe out... Good morning! ☀️",
                    "A peaceful morning to you. Let's start gently 🌿",
                    "The world is quiet and beautiful right now 🧘"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "Flowing through the afternoon... 🌊",
                    "Stay centered, you're doing great 😌",
                    "A mindful break with some music? 🎶"
                )
                TimeOfDay.EVENING -> listOf(
                    "The day is winding down beautifully 🌅",
                    "Time to let go of the day's weight 🍃",
                    "Evening peace... what shall we listen to? 🎵"
                )
                TimeOfDay.NIGHT -> listOf(
                    "The night is our sanctuary 🌙",
                    "Stillness and stars... perfect for music 🌟",
                    "Let the night songs carry you ✨"
                )
            }
            PetPersonality.PARTY -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "GOOD MORNING LET'S GOOO! ⚡",
                    "Rise and SHINE! Time for bangers! 🔥",
                    "Coffee + beats = unstoppable! ☕🎶"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "The vibes are IMMACULATE right now! 🎉",
                    "Afternoon slump? NOT ON MY WATCH! 💪",
                    "Turn it UP! The day's not over! 🔊"
                )
                TimeOfDay.EVENING -> listOf(
                    "Party doesn't stop at sunset! 🌇🎵",
                    "Evening energy CHECK! What are we playing? 🎧",
                    "The night is young and so are we! 🌟"
                )
                TimeOfDay.NIGHT -> listOf(
                    "LATE NIGHT VIBES! Best time for music! 🌙🎶",
                    "Who needs sleep when there's THIS playlist? 🔥",
                    "The after-hours are where it's AT! ✨"
                )
            }
            PetPersonality.EMO -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "Hey... I'm here. How are you feeling? 💙",
                    "Morning light hits different... 🌤️",
                    "Whatever today brings, we'll face it together 🤝"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "The afternoon can feel heavy sometimes... 💭",
                    "I've been thinking about that song you played... 🎵",
                    "Want to feel something real? I have ideas 💜"
                )
                TimeOfDay.EVENING -> listOf(
                    "Evenings are for feeling everything 🌆",
                    "The sunset makes me emotional... 🥺",
                    "Let's find something that speaks to your soul 💫"
                )
                TimeOfDay.NIGHT -> listOf(
                    "Late nights are when we're most honest... 🌙",
                    "It's okay to feel things deeply 💙",
                    "The quiet hours... just us and the music 🎶"
                )
            }
            PetPersonality.SCHOLAR -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "Good morning! Ready to be productive? 📚",
                    "Fresh mind, fresh start. Let's focus 🎯",
                    "I've queued some study beats for you 🎧"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "Deep work mode activated 💪",
                    "You've been crushing it! Keep going 📈",
                    "Want some focus-enhancing beats? 🎵"
                )
                TimeOfDay.EVENING -> listOf(
                    "Great work today! Time to wind down 📖",
                    "You earned a break. Music for relaxing? 🎶",
                    "Switching from focus to chill mode 🌅"
                )
                TimeOfDay.NIGHT -> listOf(
                    "Burning the midnight oil? I'll keep you company 🦉",
                    "Late study session? I've got the perfect lo-fi 📚",
                    "You work so hard. Don't forget to rest 💙"
                )
            }
            PetPersonality.NIGHTOWL -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "*yawns* ...oh, you're up early? ☀️😴",
                    "Morning already? I just got to sleep... 💤",
                    "I'll wake up after a few songs... 🎵"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "Okay, NOW I'm awake! What's playing? 🎧",
                    "The afternoon is my morning ☕",
                    "Finally feeling alive! Music time? 🎶"
                )
                TimeOfDay.EVENING -> listOf(
                    "THIS is when the magic happens! 🌆✨",
                    "Evening = peak $petName hours! 🌟",
                    "The best music comes out at dusk 🎵"
                )
                TimeOfDay.NIGHT -> listOf(
                    "NOW we're talking! Late night is MY time! 🌙",
                    "The night belongs to us! What's the vibe? 🦉",
                    "2 AM? That's called prime time 🔥"
                )
            }
            PetPersonality.EXPLORER -> when (time) {
                TimeOfDay.MORNING -> listOf(
                    "New day, new sounds to discover! 🔍",
                    "I found some interesting tracks overnight 🎵",
                    "Ready to explore the music world? 🌍"
                )
                TimeOfDay.AFTERNOON -> listOf(
                    "Have you heard this genre before? 🎧",
                    "Let's venture into something unexpected! 🗺️",
                    "Your taste is so eclectic — love that! ✨"
                )
                TimeOfDay.EVENING -> listOf(
                    "What musical adventure tonight? 🌅",
                    "I've been curating something special... 🎶",
                    "Evening discoveries are the best! 🔍"
                )
                TimeOfDay.NIGHT -> listOf(
                    "Late night = deep cuts time 🌙",
                    "The rarest tracks come out at night 🎵",
                    "Let's go down a musical rabbit hole 🕳️"
                )
            }
        }.random()
    }

    /**
     * Get thought bubble emojis that match the personality.
     */
    fun getThoughtBubbles(): List<String> = when (personalityType) {
        PetPersonality.ZEN -> listOf("🧘", "🌿", "☕", "🌊", "🍃", "🌸", "😌")
        PetPersonality.PARTY -> listOf("🎉", "🔥", "💃", "🕺", "🎵", "⚡", "🥳")
        PetPersonality.EMO -> listOf("💙", "🌧️", "💭", "🥺", "🎵", "💜", "🖤")
        PetPersonality.SCHOLAR -> listOf("📚", "🎯", "💡", "☕", "📖", "🧠", "✍️")
        PetPersonality.EXPLORER -> listOf("🔍", "🗺️", "🌍", "🎧", "✨", "🆕", "🎵")
        PetPersonality.NIGHTOWL -> listOf("🦉", "🌙", "⭐", "🌟", "🎵", "☕", "💤")
    }
}

// ══════════════════════════════════════════════════════════════════════════
// PERSONALITY ENGINE — computes + persists personality from mood data
// ══════════════════════════════════════════════════════════════════════════

class PetPersonalityEngine {

    private val TAG = "PersonalityEngine"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val moodRepo = MoodHistoryRepository()

    private val _profile = MutableStateFlow(PersonalityProfile())
    val profile: StateFlow<PersonalityProfile> = _profile.asStateFlow()

    /**
     * Load personality from Firebase. If stale (>6 hours), recalculate.
     */
    suspend fun loadOrCompute() {
        val userId = auth.currentUser?.uid ?: return

        try {
            // Try to load existing profile
            val doc = db.collection("petPersonality").document(userId).get().await()

            if (doc.exists()) {
                val lastUpdated = doc.getLong("lastUpdated") ?: 0
                val sixHoursAgo = System.currentTimeMillis() - (6 * 60 * 60 * 1000)

                if (lastUpdated > sixHoursAgo) {
                    // Fresh enough — use cached
                    _profile.value = parseProfile(doc.data ?: emptyMap())
                    Log.d(TAG, "Loaded cached personality: ${_profile.value.personalityType.displayName}")
                    return
                }
            }

            // Stale or missing — recalculate from mood history
            recompute()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load personality", e)
            // Use defaults
        }
    }

    /**
     * Recalculate personality from the last 14 days of mood data.
     */
    suspend fun recompute() {
        val userId = auth.currentUser?.uid ?: return

        try {
            val entries = moodRepo.getMoodHistoryForDays(14)
            if (entries.isEmpty()) {
                Log.d(TAG, "No mood data — using default personality")
                return
            }

            // ── Compute mood profile (weighted distribution) ─────────
            val moodCounts = entries.groupingBy { it.mood }.eachCount()
            val total = entries.size.toFloat()
            val moodProfile = moodCounts.mapValues { (_, count) -> count / total }

            // ── Compute time-of-day patterns ─────────────────────────
            val byTime = entries.groupBy { TimeOfDay.fromHour(it.hourOfDay).label }
            val activeHours = byTime.mapValues { (_, e) -> e.size }
            val peakMoods = byTime.mapValues { (_, e) ->
                e.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key ?: "neutral"
            }

            // ── Determine personality type ───────────────────────────
            val personalityType = computePersonalityType(moodProfile, activeHours)
            val dominantMood = moodProfile.maxByOrNull { it.value }?.key ?: "neutral"

            val newProfile = PersonalityProfile(
                personalityType = personalityType,
                moodProfile = moodProfile,
                peakMoods = peakMoods,
                activeHours = activeHours,
                totalSessions = entries.size,
                dominantMood = dominantMood,
                lastUpdated = System.currentTimeMillis()
            )

            _profile.value = newProfile

            // ── Persist to Firebase ──────────────────────────────────
            val data = hashMapOf(
                "personalityType" to personalityType.name,
                "moodProfile" to moodProfile,
                "peakMoods" to peakMoods,
                "activeHours" to activeHours,
                "totalSessions" to entries.size,
                "dominantMood" to dominantMood,
                "lastUpdated" to System.currentTimeMillis()
            )

            db.collection("petPersonality").document(userId).set(data).await()
            Log.d(TAG, "Personality computed: ${personalityType.displayName} (${entries.size} entries)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute personality", e)
        }
    }

    /**
     * Determine personality archetype from mood distribution and usage patterns.
     */
    private fun computePersonalityType(
        moodProfile: Map<String, Float>,
        activeHours: Map<String, Int>
    ): PetPersonality {
        val calm = (moodProfile["calm"] ?: 0f) + (moodProfile["focused"] ?: 0f)
        val energetic = (moodProfile["energetic"] ?: 0f) + (moodProfile["happy"] ?: 0f)
        val emotional = (moodProfile["sad"] ?: 0f) + (moodProfile["romantic"] ?: 0f)
        val focused = moodProfile["focused"] ?: 0f

        val nightSessions = activeHours["night"] ?: 0
        val totalSessions = activeHours.values.sum().coerceAtLeast(1)
        val nightRatio = nightSessions.toFloat() / totalSessions

        // Check for Night Owl first (strong signal)
        if (nightRatio > 0.4f && nightSessions > 3) return PetPersonality.NIGHTOWL

        // Check dominant mood clusters
        return when {
            calm > 0.45f && focused > 0.2f -> PetPersonality.SCHOLAR
            calm > 0.4f -> PetPersonality.ZEN
            energetic > 0.45f -> PetPersonality.PARTY
            emotional > 0.35f -> PetPersonality.EMO
            // Diverse mood distribution = Explorer
            moodProfile.size >= 4 && moodProfile.values.all { it < 0.35f } -> PetPersonality.EXPLORER
            else -> PetPersonality.EXPLORER
        }
    }

    private fun parseProfile(data: Map<String, Any?>): PersonalityProfile {
        val typeName = data["personalityType"] as? String ?: "EXPLORER"
        val personality = try { PetPersonality.valueOf(typeName) } catch (_: Exception) { PetPersonality.EXPLORER }

        @Suppress("UNCHECKED_CAST")
        return PersonalityProfile(
            personalityType = personality,
            moodProfile = (data["moodProfile"] as? Map<String, Number>)?.mapValues { it.value.toFloat() } ?: emptyMap(),
            peakMoods = (data["peakMoods"] as? Map<String, String>) ?: emptyMap(),
            activeHours = (data["activeHours"] as? Map<String, Number>)?.mapValues { it.value.toInt() } ?: emptyMap(),
            totalSessions = (data["totalSessions"] as? Number)?.toInt() ?: 0,
            dominantMood = data["dominantMood"] as? String ?: "neutral",
            lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: 0
        )
    }
}