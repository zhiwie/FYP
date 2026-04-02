package com.example.fypdraft.ml

import com.example.fypdraft.model.PersonalityProfile
import com.example.fypdraft.model.PetPersonality
import com.example.fypdraft.model.TimeOfDay

/**
 * A single music section to display on the HomeScreen.
 */
data class RecommendedSection(
    val title: String,
    val emoji: String,
    val query: String,
    val priority: Int = 0  // Higher = shown first
)

/**
 * Generates personalized music section recommendations based on:
 *   1. Current detected mood
 *   2. Pet personality type (from listening history)
 *   3. Time of day
 *   4. Peak mood at this time (from mood history patterns)
 *   5. Therapeutic opposite mood suggestion
 *
 * Instead of 8 hardcoded sections, this produces a dynamic list that
 * feels different every time based on who you are and how you feel.
 */
object MoodAwareRecommender {

    /**
     * Generate personalized sections.
     *
     * @param currentMood        The user's current mood (detected or manual)
     * @param personalityProfile The user's personality profile (from PetPersonalityEngine)
     * @param isUserOverride     Whether the user manually set their mood
     * @return Ordered list of sections, most relevant first
     */
    fun generateSections(
        currentMood: String,
        personalityProfile: PersonalityProfile = PersonalityProfile(),
        isUserOverride: Boolean = false
    ): List<RecommendedSection> {
        val sections = mutableListOf<RecommendedSection>()
        val time = TimeOfDay.current()
        val personality = personalityProfile.personalityType
        val peakMoodNow = personalityProfile.peakMoods[time.label]

        // ══════════════════════════════════════════════════════════════
        // 1. PERSONALIZED (always first)
        // ══════════════════════════════════════════════════════════════
        sections.add(RecommendedSection(
            title = getPersonalizedTitle(personality),
            emoji = "✨",
            query = "PERSONALIZED",
            priority = 100
        ))

        // ══════════════════════════════════════════════════════════════
        // 2. MOOD-MATCHED SECTION (based on current mood)
        // ══════════════════════════════════════════════════════════════
        val moodSection = getMoodSection(currentMood)
        sections.add(moodSection.copy(priority = 90))

        // ══════════════════════════════════════════════════════════════
        // 3. THERAPEUTIC / OPPOSITE MOOD (help shift mood if negative)
        // ══════════════════════════════════════════════════════════════
        val therapeutic = getTherapeuticSection(currentMood)
        if (therapeutic != null) {
            sections.add(therapeutic.copy(priority = 85))
        }

        // ══════════════════════════════════════════════════════════════
        // 4. TIME-AWARE SECTION
        // ══════════════════════════════════════════════════════════════
        val timeSection = getTimeSection(time, personality)
        sections.add(timeSection.copy(priority = 75))

        // ══════════════════════════════════════════════════════════════
        // 5. PERSONALITY-SPECIFIC SECTION
        // ══════════════════════════════════════════════════════════════
        val personalitySection = getPersonalitySection(personality)
        sections.add(personalitySection.copy(priority = 70))

        // ══════════════════════════════════════════════════════════════
        // 6. PEAK MOOD PATTERN (what you usually listen to at this time)
        // ══════════════════════════════════════════════════════════════
        if (peakMoodNow != null && peakMoodNow != currentMood) {
            val peakSection = getMoodSection(peakMoodNow)
            sections.add(peakSection.copy(
                title = "Your usual ${time.label} vibe",
                emoji = "🔄",
                priority = 60
            ))
        }

        // ══════════════════════════════════════════════════════════════
        // 7. DISCOVERY SECTION (based on personality)
        // ══════════════════════════════════════════════════════════════
        sections.add(getDiscoverySection(personality).copy(priority = 50))

        // ══════════════════════════════════════════════════════════════
        // 8. WILDCARD (something unexpected)
        // ══════════════════════════════════════════════════════════════
        sections.add(getWildcardSection(currentMood, time).copy(priority = 40))

        // Sort by priority (highest first), deduplicate by query
        return sections
            .distinctBy { it.query }
            .sortedByDescending { it.priority }
            .take(8)
    }

    // ── Mood-matched sections ────────────────────────────────────────

    private fun getMoodSection(mood: String): RecommendedSection = when (mood) {
        "happy" -> RecommendedSection("Matching your happy vibes", "😊",
            "happy uplifting feel good pop sunshine bright")
        "sad" -> RecommendedSection("For how you're feeling", "💙",
            "sad emotional ballad comfort songs heartbreak")
        "energetic" -> RecommendedSection("Fuel that energy", "⚡",
            "energetic workout hype pump up bass drop")
        "calm" -> RecommendedSection("Keeping it peaceful", "😌",
            "chill ambient relaxing peaceful gentle lofi")
        "focused" -> RecommendedSection("Deep focus mode", "🎯",
            "focus study instrumental lofi beats concentration")
        "tired" -> RecommendedSection("Easy listening", "😴",
            "soft gentle acoustic slow soothing lullaby")
        "romantic" -> RecommendedSection("Love in the air", "💕",
            "romantic love songs r&b smooth slow dance")
        "angry" -> RecommendedSection("Let it out", "🔥",
            "aggressive rock metal punk heavy cathartic")
        "anxious" -> RecommendedSection("Calm your mind", "🌊",
            "calming anxiety relief meditation peaceful ambient")
        else -> RecommendedSection("Vibes for now", "🎵",
            "popular hits trending top songs 2025")
    }

    // ── Therapeutic / opposite mood sections ─────────────────────────

    private fun getTherapeuticSection(mood: String): RecommendedSection? = when (mood) {
        "sad" -> RecommendedSection("Want to feel better?", "🌟",
            "uplifting motivational happy empowering anthems")
        "angry" -> RecommendedSection("Cool down with these", "🧊",
            "calm peaceful gentle acoustic meditation nature")
        "anxious" -> RecommendedSection("Breathe easy", "🍃",
            "relaxing breathing meditation spa nature sounds")
        "tired" -> RecommendedSection("Pick-me-up boost", "☕",
            "energetic morning motivation wake up coffee beats")
        // Positive moods don't need therapeutic correction
        "happy", "energetic", "romantic" -> null
        else -> null
    }

    // ── Time-aware sections ──────────────────────────────────────────

    private fun getTimeSection(time: TimeOfDay, personality: PetPersonality): RecommendedSection = when (time) {
        TimeOfDay.MORNING -> when (personality) {
            PetPersonality.PARTY -> RecommendedSection("Morning pump-up", "🌅", "morning energy hype pump up dance edm")
            PetPersonality.SCHOLAR -> RecommendedSection("Morning focus", "🌅", "morning focus study lofi beats productive")
            PetPersonality.ZEN -> RecommendedSection("Mindful morning", "🌅", "morning meditation peaceful gentle awakening")
            else -> RecommendedSection("Good morning tunes", "🌅", "morning feel good happy start day acoustic")
        }
        TimeOfDay.AFTERNOON -> when (personality) {
            PetPersonality.PARTY -> RecommendedSection("Afternoon bangers", "☀️", "afternoon hype party dance trending hits")
            PetPersonality.SCHOLAR -> RecommendedSection("Afternoon deep work", "☀️", "afternoon focus instrumental ambient study")
            else -> RecommendedSection("Afternoon vibes", "☀️", "afternoon chill pop indie feel good")
        }
        TimeOfDay.EVENING -> when (personality) {
            PetPersonality.NIGHTOWL -> RecommendedSection("The night begins", "🌆", "evening pre-game hype party starting")
            PetPersonality.EMO -> RecommendedSection("Evening reflections", "🌆", "evening emotional indie reflective acoustic")
            PetPersonality.ZEN -> RecommendedSection("Sunset wind-down", "🌆", "evening calm sunset peaceful ambient chill")
            else -> RecommendedSection("Evening mood", "🌆", "evening chill r&b smooth jazz soul")
        }
        TimeOfDay.NIGHT -> when (personality) {
            PetPersonality.NIGHTOWL -> RecommendedSection("Peak ${personality.emoji} hours", "🌙", "late night vibes deep house electronic dark")
            PetPersonality.SCHOLAR -> RecommendedSection("Late study session", "🌙", "late night study lofi beats focus ambient")
            PetPersonality.EMO -> RecommendedSection("Late night feels", "🌙", "late night sad emotional r&b slow")
            else -> RecommendedSection("Late night", "🌙", "late night r&b smooth jazz soul chill")
        }
    }

    // ── Personality-specific sections ─────────────────────────────────

    private fun getPersonalitySection(personality: PetPersonality): RecommendedSection = when (personality) {
        PetPersonality.ZEN -> RecommendedSection("${personality.emoji} Zen picks", "🧘",
            "meditation yoga ambient nature sounds peaceful")
        PetPersonality.PARTY -> RecommendedSection("${personality.emoji} Club ready", "🎉",
            "club bangers dance edm house party 2025")
        PetPersonality.EMO -> RecommendedSection("${personality.emoji} Deep cuts", "🌧️",
            "indie emotional alternative deep lyrics meaningful")
        PetPersonality.SCHOLAR -> RecommendedSection("${personality.emoji} Brain fuel", "📚",
            "instrumental classical piano ambient electronic focus")
        PetPersonality.EXPLORER -> RecommendedSection("${personality.emoji} Hidden gems", "🔍",
            "underrated indie new artists emerging debut album")
        PetPersonality.NIGHTOWL -> RecommendedSection("${personality.emoji} After dark", "🦉",
            "dark ambient electronic experimental late night bass")
    }

    // ── Discovery sections ───────────────────────────────────────────

    private fun getDiscoverySection(personality: PetPersonality): RecommendedSection {
        // Suggest something outside their usual comfort zone
        return when (personality) {
            PetPersonality.ZEN -> RecommendedSection("Try something new", "🔮",
                "upbeat pop dance fun unexpected surprise")
            PetPersonality.PARTY -> RecommendedSection("Slow it down?", "🔮",
                "acoustic calm indie folk singer songwriter")
            PetPersonality.EMO -> RecommendedSection("Bright side", "🔮",
                "happy pop sunshine feel good cheerful bright")
            PetPersonality.SCHOLAR -> RecommendedSection("Creative break", "🔮",
                "creative jazz experimental art pop eclectic")
            PetPersonality.EXPLORER -> RecommendedSection("World sounds", "🔮",
                "world music afrobeat latin asian kpop global")
            PetPersonality.NIGHTOWL -> RecommendedSection("Daytime experiment", "🔮",
                "morning acoustic bright indie pop folk fresh")
        }
    }

    // ── Wildcard ─────────────────────────────────────────────────────

    private fun getWildcardSection(mood: String, time: TimeOfDay): RecommendedSection {
        val wildcards = listOf(
            RecommendedSection("Throwback hits", "⏪", "throwback hits nostalgia 2000s 2010s classics"),
            RecommendedSection("Acoustic covers", "🎸", "acoustic covers popular songs stripped back"),
            RecommendedSection("Movie soundtracks", "🎬", "movie soundtrack cinematic epic orchestral"),
            RecommendedSection("Coffee shop vibes", "☕", "coffee shop jazz acoustic chill background"),
            RecommendedSection("Road trip energy", "🚗", "road trip singalong anthems driving songs"),
            RecommendedSection("Rainy day", "🌧️", "rainy day cozy acoustic warm gentle"),
            RecommendedSection("Workout beast mode", "🏋️", "workout gym beast mode heavy bass pump"),
            RecommendedSection("K-Pop hits", "🇰🇷", "kpop trending popular korean music 2025"),
        )
        return wildcards.random()
    }

    // ── Personalized title based on personality ──────────────────────

    private fun getPersonalizedTitle(personality: PetPersonality): String = when (personality) {
        PetPersonality.ZEN -> "Curated for your zen"
        PetPersonality.PARTY -> "Your party starters"
        PetPersonality.EMO -> "Handpicked for your soul"
        PetPersonality.SCHOLAR -> "Your focus playlist"
        PetPersonality.EXPLORER -> "Discoveries for you"
        PetPersonality.NIGHTOWL -> "Your midnight picks"
    }
}