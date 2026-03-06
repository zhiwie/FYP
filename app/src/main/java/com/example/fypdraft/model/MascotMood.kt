package com.example.fypdraft.model

/**
 * Represents the mascot's detected mood state.
 * Combines signals from: time of day, recent plays, user input, current track emotion.
 */
data class MascotMood(
    val mood: String = "neutral",       // happy, sad, calm, energetic, tired, neutral
    val emoji: String = "😊",
    val greeting: String = "Hey! What are we vibing to today?",
    val suggestion: String? = null,     // e.g. "You seem low energy, want some pump-up tracks?"
    val isUserOverride: Boolean = false  // true if user manually set this mood
)

object MascotMoodDetector {

    // Emoji map for each mood — will be replaced with Lottie animation keys later
    private val MOOD_MAP = mapOf(
        "happy"     to MascotMood("happy",     "😊", "You're glowing today!", "Keep the good vibes going?"),
        "sad"       to MascotMood("sad",       "😢", "Hey, I'm here for you.", "Want something uplifting, or should we sit with this feeling?"),
        "calm"      to MascotMood("calm",      "😌", "Nice and easy today.", "Some chill tunes to match?"),
        "energetic" to MascotMood("energetic", "⚡", "Let's gooo!", "Ready for a high-energy playlist?"),
        "tired"     to MascotMood("tired",     "😴", "Long day, huh?", "Some soothing music to wind down?"),
        "focused"   to MascotMood("focused",   "🎯", "In the zone!", "I'll keep the distractions away."),
        "romantic"  to MascotMood("romantic",   "💕", "Feeling the love?", "Some romantic picks for you?"),
        "neutral"   to MascotMood("neutral",   "🎵", "Hey! What are we vibing to today?", null)
    )

    /**
     * Detect mood from multiple signals.
     * Priority: userOverride > currentTrackEmotion > listeningHistory > timeOfDay
     */
    fun detectMood(
        userOverride: String? = null,
        currentTrackEmotion: String? = null,
        recentMoods: List<String> = emptyList(),
        hourOfDay: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    ): MascotMood {

        // 1. User explicitly set mood
        if (userOverride != null) {
            return (MOOD_MAP[userOverride] ?: MOOD_MAP["neutral"]!!).copy(isUserOverride = true)
        }

        // 2. Current track emotion (from TFLite analysis)
        if (currentTrackEmotion != null && MOOD_MAP.containsKey(currentTrackEmotion)) {
            return MOOD_MAP[currentTrackEmotion]!!
        }

        // 3. Recent listening history — most frequent mood
        if (recentMoods.isNotEmpty()) {
            val dominant = recentMoods.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (dominant != null && MOOD_MAP.containsKey(dominant)) {
                return MOOD_MAP[dominant]!!
            }
        }

        // 4. Time of day fallback
        val timeMood = when (hourOfDay) {
            in 5..8   -> "calm"       // Early morning
            in 9..11  -> "focused"    // Morning work
            in 12..14 -> "energetic"  // Midday
            in 15..17 -> "focused"    // Afternoon
            in 18..20 -> "happy"      // Evening
            in 21..23 -> "calm"       // Night wind-down
            else      -> "tired"      // Late night / early hours
        }

        return MOOD_MAP[timeMood] ?: MOOD_MAP["neutral"]!!
    }

    fun getMoodForKey(key: String): MascotMood {
        return MOOD_MAP[key] ?: MOOD_MAP["neutral"]!!
    }

    fun allMoodKeys(): List<String> = MOOD_MAP.keys.toList()
}