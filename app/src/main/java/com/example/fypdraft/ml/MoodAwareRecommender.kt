package com.example.fypdraft.ml

import com.example.fypdraft.model.PersonalityProfile
import com.example.fypdraft.model.PetPersonality
import com.example.fypdraft.model.TimeOfDay

data class RecommendedSection(
    val title:    String,
    val emoji:    String,
    val query:    String,
    val priority: Int = 0
)

object MoodAwareRecommender {

    /**
     * Generate sections with personalised queries.
     *
     * New: accepts [tasteProfile] to inject real artist/genre/language signals
     * into every query. A user who listens to K-pop will get K-pop results
     * in every section — not just the PERSONALIZED one.
     */
    fun generateSections(
        currentMood:       String,
        personalityProfile: PersonalityProfile = PersonalityProfile(),
        isUserOverride:    Boolean             = false,
        rlEngine:          RLRecommendationEngine? = null,
        tasteProfile:      UserTasteProfile    = UserTasteProfile()   // ← NEW
    ): List<RecommendedSection> {
        val sections    = mutableListOf<RecommendedSection>()
        val time        = TimeOfDay.current()
        val personality = personalityProfile.personalityType
        val peakMoodNow = personalityProfile.peakMoods[time.label]

        sections.add(RecommendedSection(
            title    = getPersonalizedTitle(personality),
            emoji    = "✨",
            query    = "PERSONALIZED",
            priority = 100
        ))

        sections.add(getMoodSection(currentMood, tasteProfile).copy(priority = 90))

        getTherapeuticSection(currentMood, tasteProfile)
            ?.let { sections.add(it.copy(priority = 85)) }

        sections.add(getTimeSection(time, personality, tasteProfile).copy(priority = 75))

        sections.add(getPersonalitySection(personality, tasteProfile).copy(priority = 70))

        if (peakMoodNow != null && peakMoodNow != currentMood) {
            sections.add(getMoodSection(peakMoodNow, tasteProfile).copy(
                title    = "Your usual ${time.label} vibe",
                emoji    = "🔄",
                priority = 60
            ))
        }

        // Artist-based section — only added if we have real history
        if (tasteProfile.topArtists.size >= 3) {
            sections.add(getArtistAffinitySection(tasteProfile).copy(priority = 80))
        }

        // Language-specific section — only added for non-English dominant users
        if (tasteProfile.dominantLanguage != "en" && tasteProfile.topArtists.isNotEmpty()) {
            sections.add(getLanguageSection(tasteProfile).copy(priority = 72))
        }

        sections.add(getDiscoverySection(personality, tasteProfile).copy(priority = 50))
        sections.add(getWildcardSection(currentMood, time, tasteProfile).copy(priority = 40))

        // RL boost/demote
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

    // ── Personalised query builder ────────────────────────────────────────

    /**
     * Builds a Spotify search query from a base keyword string + taste seeds.
     *
     * Strategy:
     *  - If user has known top artists for this mood, append 1–2 of them.
     *  - If user has a dominant genre, append its search keyword.
     *  - Language seeds are appended for non-English users to shift Spotify's
     *    ranking toward region-relevant content.
     *
     * Example output for a K-pop/happy user:
     *   "happy uplifting feel good pop" → "happy uplifting feel good pop BLACKPINK twice kpop korean"
     */
    private fun buildQuery(
        base:        String,
        mood:        String,
        tasteProfile: UserTasteProfile,
        maxArtists:  Int = 2
    ): String {
        val parts = mutableListOf(base)

        // 1. Mood-specific artist seeds from listening history
        val moodArtists = tasteProfile.moodArtistSeeds[mood]
            ?.filter { it !in tasteProfile.skippedArtists }
            ?.take(maxArtists)
            ?: emptyList()

        if (moodArtists.isNotEmpty()) {
            parts.addAll(moodArtists)
        } else if (tasteProfile.topArtists.isNotEmpty()) {
            // Fall back to general top artists if no mood-specific ones
            parts.add(tasteProfile.topArtists.first())
        }

        // 2. Dominant genre seed
        val topGenre = tasteProfile.genreWeights.entries
            .filter { it.value >= 0.3f }
            .maxByOrNull { it.value }
            ?.key

        if (topGenre != null) {
            parts.add(genreToSearchKeyword(topGenre))
        }

        // 3. Language seed for non-English users
        val langSeed = languageToSearchSeed(tasteProfile.dominantLanguage)
        if (langSeed.isNotEmpty()) parts.add(langSeed)

        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun genreToSearchKeyword(genre: String): String = when (genre) {
        "kpop"      -> "kpop korean"
        "mandopop"  -> "mandopop chinese"
        "jpop"      -> "jpop japanese"
        "malay"     -> "malay bahasa"
        "edm"       -> "edm electronic dance"
        "rnb"       -> "r&b soul"
        "indie"     -> "indie alternative"
        "hiphop"    -> "hip hop rap"
        "classical" -> "classical instrumental piano"
        else        -> genre
    }

    private fun languageToSearchSeed(lang: String): String = when (lang) {
        "ko" -> "korean kpop"
        "zh" -> "chinese mandopop"
        "ja" -> "japanese jpop"
        "ms" -> "malay bahasa"
        else -> ""
    }

    // ── Section builders (now personalised) ──────────────────────────────

    private fun getMoodSection(mood: String, t: UserTasteProfile): RecommendedSection {
        val base = when (mood) {
            "happy"     -> "happy uplifting feel good pop sunshine bright"
            "sad"       -> "sad emotional ballad comfort heartbreak"
            "energetic" -> "energetic workout hype pump up bass drop"
            "calm"      -> "chill ambient relaxing peaceful gentle lofi"
            "focused"   -> "focus study instrumental lofi beats concentration"
            "tired"     -> "soft gentle acoustic slow soothing lullaby"
            "romantic"  -> "romantic love songs r&b smooth slow dance"
            "angry"     -> "aggressive rock metal punk heavy cathartic"
            "anxious"   -> "calming anxiety relief meditation peaceful ambient"
            else        -> "popular hits trending top songs 2025"
        }
        val title = when (mood) {
            "happy"     -> "Matching your happy vibes"
            "sad"       -> "For how you're feeling"
            "energetic" -> "Fuel that energy"
            "calm"      -> "Keeping it peaceful"
            "focused"   -> "Deep focus mode"
            "tired"     -> "Easy listening"
            "romantic"  -> "Love in the air"
            "angry"     -> "Let it out"
            "anxious"   -> "Calm your mind"
            else        -> "Vibes for now"
        }
        val emoji = when (mood) {
            "happy"->"😊"; "sad"->"💙"; "energetic"->"⚡"; "calm"->"😌"
            "focused"->"🎯"; "tired"->"😴"; "romantic"->"💕"; "angry"->"🔥"
            "anxious"->"🌊"; else->"🎵"
        }
        return RecommendedSection(title, emoji, buildQuery(base, mood, t))
    }

    private fun getTherapeuticSection(mood: String, t: UserTasteProfile): RecommendedSection? {
        val (base, title, emoji) = when (mood) {
            "sad"     -> Triple("uplifting motivational happy empowering anthems",
                "Want to feel better?", "🌟")
            "angry"   -> Triple("calm peaceful gentle acoustic meditation nature",
                "Cool down with these", "🧊")
            "anxious" -> Triple("relaxing breathing meditation spa nature sounds",
                "Breathe easy", "🍃")
            "tired"   -> Triple("energetic morning motivation wake up coffee beats",
                "Pick-me-up boost", "☕")
            else      -> return null
        }
        // Therapeutic sections intentionally avoid strong artist seeds
        // so they can introduce unfamiliar calming/uplifting artists
        return RecommendedSection(title, emoji, buildQuery(base, mood, t, maxArtists = 0))
    }

    /**
     * NEW: Section built entirely from the user's real top artists.
     * "More from artists you love" — bypasses mood keywords entirely.
     */
    private fun getArtistAffinitySection(t: UserTasteProfile): RecommendedSection {
        // Use liked artists first, then top played artists
        val seeds = (t.likedArtists + t.topArtists)
            .distinct()
            .filter { it !in t.skippedArtists }
            .take(3)
        val query = seeds.joinToString(" ") + " similar"
        return RecommendedSection(
            title    = "More from artists you love",
            emoji    = "❤️",
            query    = query
        )
    }

    /**
     * NEW: Section in the user's dominant non-English language.
     * Only added when dominantLanguage != "en".
     */
    private fun getLanguageSection(t: UserTasteProfile): RecommendedSection {
        val (langLabel, baseQuery) = when (t.dominantLanguage) {
            "ko" -> "K-Pop" to "kpop korean trending popular 2025"
            "zh" -> "C-Pop" to "mandopop chinese popular hits"
            "ja" -> "J-Pop" to "jpop japanese anime popular"
            "ms" -> "Malay" to "malay bahasa popular hits"
            else -> return RecommendedSection("Global Picks","🌍","world music trending 2025")
        }
        // Seed with top artists from this language
        val artistSeeds = t.topArtists.take(2).joinToString(" ")
        return RecommendedSection(
            title = "🎵 $langLabel picks for you",
            emoji = when (t.dominantLanguage) {
                "ko" -> "🇰🇷"; "zh" -> "🇨🇳"; "ja" -> "🇯🇵"; "ms" -> "🇲🇾"; else -> "🌏"
            },
            query = "$baseQuery $artistSeeds".trim()
        )
    }

    private fun getTimeSection(time: TimeOfDay, personality: PetPersonality,
                               t: UserTasteProfile): RecommendedSection {
        val base = when (time) {
            TimeOfDay.MORNING -> when (personality) {
                PetPersonality.PARTY   -> "morning energy hype pump up dance edm"
                PetPersonality.SCHOLAR -> "morning focus study lofi beats productive"
                PetPersonality.ZEN     -> "morning meditation peaceful gentle awakening"
                else -> "morning feel good happy start day acoustic"
            }
            TimeOfDay.AFTERNOON -> when (personality) {
                PetPersonality.PARTY   -> "afternoon hype party dance trending hits"
                PetPersonality.SCHOLAR -> "afternoon focus instrumental ambient study"
                else -> "afternoon chill pop indie feel good"
            }
            TimeOfDay.EVENING -> "evening chill r&b smooth jazz soul"
            TimeOfDay.NIGHT   -> "late night r&b smooth jazz soul chill"
        }
        val title = when (time) {
            TimeOfDay.MORNING   -> "Good morning tunes"
            TimeOfDay.AFTERNOON -> "Afternoon picks"
            TimeOfDay.EVENING   -> "Evening wind-down"
            TimeOfDay.NIGHT     -> "Late night vibes"
        }
        return RecommendedSection(title, when(time){
            TimeOfDay.MORNING->"🌅"; TimeOfDay.AFTERNOON->"☀️"
            TimeOfDay.EVENING->"🌆"; TimeOfDay.NIGHT->"🌙"
        }, buildQuery(base, "neutral", t, maxArtists = 1))
    }

    private fun getPersonalitySection(personality: PetPersonality,
                                      t: UserTasteProfile): RecommendedSection {
        val base = when (personality) {
            PetPersonality.ZEN      -> "meditation yoga ambient nature sounds peaceful"
            PetPersonality.PARTY    -> "club bangers dance edm house party 2025"
            PetPersonality.EMO      -> "indie emotional alternative deep lyrics meaningful"
            PetPersonality.SCHOLAR  -> "instrumental classical piano ambient electronic focus"
            PetPersonality.EXPLORER -> "underrated indie new artists emerging debut album"
            PetPersonality.NIGHTOWL -> "dark ambient electronic experimental late night bass"
        }
        return RecommendedSection(
            "${personality.emoji} ${personality.name.lowercase().replaceFirstChar { it.uppercase() }} picks",
            when(personality){
                PetPersonality.ZEN->"🧘"; PetPersonality.PARTY->"🎉"
                PetPersonality.EMO->"🌧️"; PetPersonality.SCHOLAR->"📚"
                PetPersonality.EXPLORER->"🔍"; PetPersonality.NIGHTOWL->"🦉"
            },
            buildQuery(base, "neutral", t, maxArtists = 1)
        )
    }

    private fun getDiscoverySection(personality: PetPersonality,
                                    t: UserTasteProfile): RecommendedSection {
        // Discovery intentionally uses a genre the user has NOT explored heavily
        val weakGenre = listOf("indie","classical","jazz","reggae","folk","world")
            .firstOrNull { (t.genreWeights[it] ?: 0f) < 0.2f }
            ?: "world music"
        val base = "$weakGenre new artists emerging music"
        return RecommendedSection("Try something new", "🔮", base)
    }

    private fun getWildcardSection(mood: String, time: TimeOfDay,
                                   t: UserTasteProfile): RecommendedSection {
        val base = "acoustic covers popular songs stripped back"
        return RecommendedSection("Something different", "🎲",
            buildQuery(base, mood, t, maxArtists = 1))
    }

    // ── Helpers (unchanged) ───────────────────────────────────────────────

    private fun getPersonalizedTitle(personality: PetPersonality): String = when (personality) {
        PetPersonality.ZEN      -> "Curated for your calm soul"
        PetPersonality.PARTY    -> "Made for the party animal in you"
        PetPersonality.EMO      -> "Deep picks, just for you"
        PetPersonality.SCHOLAR  -> "Brain food, curated"
        PetPersonality.EXPLORER -> "Handpicked hidden gems"
        PetPersonality.NIGHTOWL -> "Your after-dark playlist"
    }
}