package com.example.fypdraft.ml

import com.example.fypdraft.model.PersonalityProfile
import com.example.fypdraft.model.PetPersonality
import com.example.fypdraft.model.TimeOfDay

data class RecommendedSection(
    val title: String,
    val emoji: String,
    val query: String,
    val priority: Int = 0
)

object MoodAwareRecommender {

    /**
     * Generate sections, optionally reweighted by learned RL query scores.
     * Pass rlEngine after it has loaded state for personalized ordering.
     */
    fun generateSections(
        currentMood: String,
        personalityProfile: PersonalityProfile = PersonalityProfile(),
        isUserOverride: Boolean = false,
        rlEngine: RLRecommendationEngine? = null   // ← NEW param
    ): List<RecommendedSection> {
        val sections = mutableListOf<RecommendedSection>()
        val time = TimeOfDay.current()
        val personality = personalityProfile.personalityType
        val peakMoodNow = personalityProfile.peakMoods[time.label]

        sections.add(RecommendedSection(
            title = getPersonalizedTitle(personality), emoji = "✨",
            query = "PERSONALIZED", priority = 100
        ))
        sections.add(getMoodSection(currentMood).copy(priority = 90))
        getTherapeuticSection(currentMood)?.let { sections.add(it.copy(priority = 85)) }
        sections.add(getTimeSection(time, personality).copy(priority = 75))
        sections.add(getPersonalitySection(personality).copy(priority = 70))
        if (peakMoodNow != null && peakMoodNow != currentMood) {
            sections.add(getMoodSection(peakMoodNow).copy(
                title = "Your usual ${time.label} vibe", emoji = "🔄", priority = 60
            ))
        }
        sections.add(getDiscoverySection(personality).copy(priority = 50))
        sections.add(getWildcardSection(currentMood, time).copy(priority = 40))

        // ── NEW: boost/demote sections by learned query scores ────────
        // rlEngine.getQueryScore returns ~[-1, 1]; we scale it to ±30 priority
        // points so RL influence is real but can't completely override
        // the semantic priority ordering above.
        val scored = sections
            .distinctBy { it.query }
            .map { section ->
                val rlBoost = if (rlEngine != null && section.query != "PERSONALIZED") {
                    (rlEngine.getQueryScore(currentMood, section.query) * 30f).toInt()
                } else 0
                section.copy(priority = section.priority + rlBoost)
            }
            .sortedByDescending { it.priority }
            .take(8)

        return scored
    }

    // ── All private helpers unchanged from your original ─────────────

    private fun getMoodSection(mood: String): RecommendedSection = when (mood) {
        "happy"     -> RecommendedSection("Matching your happy vibes", "😊",
            "happy uplifting feel good pop sunshine bright")
        "sad"       -> RecommendedSection("For how you're feeling", "💙",
            "sad emotional ballad comfort songs heartbreak")
        "energetic" -> RecommendedSection("Fuel that energy", "⚡",
            "energetic workout hype pump up bass drop")
        "calm"      -> RecommendedSection("Keeping it peaceful", "😌",
            "chill ambient relaxing peaceful gentle lofi")
        "focused"   -> RecommendedSection("Deep focus mode", "🎯",
            "focus study instrumental lofi beats concentration")
        "tired"     -> RecommendedSection("Easy listening", "😴",
            "soft gentle acoustic slow soothing lullaby")
        "romantic"  -> RecommendedSection("Love in the air", "💕",
            "romantic love songs r&b smooth slow dance")
        "angry"     -> RecommendedSection("Let it out", "🔥",
            "aggressive rock metal punk heavy cathartic")
        "anxious"   -> RecommendedSection("Calm your mind", "🌊",
            "calming anxiety relief meditation peaceful ambient")
        else        -> RecommendedSection("Vibes for now", "🎵",
            "popular hits trending top songs 2025")
    }

    private fun getTherapeuticSection(mood: String): RecommendedSection? = when (mood) {
        "sad"     -> RecommendedSection("Want to feel better?", "🌟",
            "uplifting motivational happy empowering anthems")
        "angry"   -> RecommendedSection("Cool down with these", "🧊",
            "calm peaceful gentle acoustic meditation nature")
        "anxious" -> RecommendedSection("Breathe easy", "🍃",
            "relaxing breathing meditation spa nature sounds")
        "tired"   -> RecommendedSection("Pick-me-up boost", "☕",
            "energetic morning motivation wake up coffee beats")
        else      -> null
    }

    private fun getTimeSection(time: TimeOfDay, personality: PetPersonality): RecommendedSection =
        when (time) {
            TimeOfDay.MORNING -> when (personality) {
                PetPersonality.PARTY   -> RecommendedSection("Morning pump-up", "🌅",
                    "morning energy hype pump up dance edm")
                PetPersonality.SCHOLAR -> RecommendedSection("Morning focus", "🌅",
                    "morning focus study lofi beats productive")
                PetPersonality.ZEN     -> RecommendedSection("Mindful morning", "🌅",
                    "morning meditation peaceful gentle awakening")
                else -> RecommendedSection("Good morning tunes", "🌅",
                    "morning feel good happy start day acoustic")
            }
            TimeOfDay.AFTERNOON -> when (personality) {
                PetPersonality.PARTY   -> RecommendedSection("Afternoon bangers", "☀️",
                    "afternoon hype party dance trending hits")
                PetPersonality.SCHOLAR -> RecommendedSection("Afternoon deep work", "☀️",
                    "afternoon focus instrumental ambient study")
                else -> RecommendedSection("Afternoon vibes", "☀️",
                    "afternoon chill pop indie feel good")
            }
            TimeOfDay.EVENING -> when (personality) {
                PetPersonality.NIGHTOWL -> RecommendedSection("The night begins", "🌆",
                    "evening pre-game hype party starting")
                PetPersonality.EMO      -> RecommendedSection("Evening reflections", "🌆",
                    "evening emotional indie reflective acoustic")
                PetPersonality.ZEN      -> RecommendedSection("Sunset wind-down", "🌆",
                    "evening calm sunset peaceful ambient chill")
                else -> RecommendedSection("Evening mood", "🌆",
                    "evening chill r&b smooth jazz soul")
            }
            TimeOfDay.NIGHT -> when (personality) {
                PetPersonality.NIGHTOWL -> RecommendedSection(
                    "Peak ${personality.emoji} hours", "🌙",
                    "late night vibes deep house electronic dark")
                PetPersonality.SCHOLAR  -> RecommendedSection("Late study session", "🌙",
                    "late night study lofi beats focus ambient")
                PetPersonality.EMO      -> RecommendedSection("Late night feels", "🌙",
                    "late night sad emotional r&b slow")
                else -> RecommendedSection("Late night", "🌙",
                    "late night r&b smooth jazz soul chill")
            }
        }

    private fun getPersonalitySection(personality: PetPersonality): RecommendedSection =
        when (personality) {
            PetPersonality.ZEN      -> RecommendedSection("${personality.emoji} Zen picks", "🧘",
                "meditation yoga ambient nature sounds peaceful")
            PetPersonality.PARTY    -> RecommendedSection("${personality.emoji} Club ready", "🎉",
                "club bangers dance edm house party 2025")
            PetPersonality.EMO      -> RecommendedSection("${personality.emoji} Deep cuts", "🌧️",
                "indie emotional alternative deep lyrics meaningful")
            PetPersonality.SCHOLAR  -> RecommendedSection("${personality.emoji} Brain fuel", "📚",
                "instrumental classical piano ambient electronic focus")
            PetPersonality.EXPLORER -> RecommendedSection("${personality.emoji} Hidden gems", "🔍",
                "underrated indie new artists emerging debut album")
            PetPersonality.NIGHTOWL -> RecommendedSection("${personality.emoji} After dark", "🦉",
                "dark ambient electronic experimental late night bass")
        }

    private fun getDiscoverySection(personality: PetPersonality): RecommendedSection =
        when (personality) {
            PetPersonality.ZEN      -> RecommendedSection("Try something new", "🔮",
                "upbeat pop dance fun unexpected surprise")
            PetPersonality.PARTY    -> RecommendedSection("Slow it down?", "🔮",
                "acoustic calm indie folk singer songwriter")
            PetPersonality.EMO      -> RecommendedSection("Bright side", "🔮",
                "happy pop sunshine feel good cheerful bright")
            PetPersonality.SCHOLAR  -> RecommendedSection("Creative break", "🔮",
                "creative jazz experimental art pop eclectic")
            PetPersonality.EXPLORER -> RecommendedSection("World sounds", "🔮",
                "world music afrobeat latin asian kpop global")
            PetPersonality.NIGHTOWL -> RecommendedSection("Daytime experiment", "🔮",
                "morning acoustic bright indie pop folk fresh")
        }

    private fun getWildcardSection(mood: String, time: TimeOfDay): RecommendedSection {
        val wildcards = listOf(
            RecommendedSection("Throwback hits",     "⏪", "throwback hits nostalgia 2000s 2010s classics"),
            RecommendedSection("Acoustic covers",    "🎸", "acoustic covers popular songs stripped back"),
            RecommendedSection("Movie soundtracks",  "🎬", "movie soundtrack cinematic epic orchestral"),
            RecommendedSection("Coffee shop vibes",  "☕", "coffee shop jazz acoustic chill background"),
            RecommendedSection("Road trip energy",   "🚗", "road trip singalong anthems driving songs"),
            RecommendedSection("Rainy day",          "🌧️", "rainy day cozy acoustic warm gentle"),
            RecommendedSection("Workout beast mode", "🏋️", "workout gym beast mode heavy bass pump"),
            RecommendedSection("K-Pop hits",         "🇰🇷", "kpop trending popular korean music 2025")
        )
        return wildcards.random()
    }

    private fun getPersonalizedTitle(personality: PetPersonality): String = when (personality) {
        PetPersonality.ZEN      -> "Curated for your zen"
        PetPersonality.PARTY    -> "Your party starters"
        PetPersonality.EMO      -> "Handpicked for your soul"
        PetPersonality.SCHOLAR  -> "Your focus playlist"
        PetPersonality.EXPLORER -> "Discoveries for you"
        PetPersonality.NIGHTOWL -> "Your midnight picks"
    }
}