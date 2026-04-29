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
     * so every refresh returns genuinely different Spotify results.
     */
    fun generateSections(
        currentMood:        String,
        personalityProfile: PersonalityProfile     = PersonalityProfile(),
        isUserOverride:     Boolean                = false,
        rlEngine:           RLRecommendationEngine? = null,
        tasteProfile:       UserTasteProfile       = UserTasteProfile(),
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

        if (tasteProfile.topArtists.size >= 3) {
            sections.add(getArtistAffinitySection(tasteProfile).copy(priority = 80))
        }

        if (tasteProfile.dominantLanguage != "en" && tasteProfile.topArtists.isNotEmpty()) {
            sections.add(getLanguageSection(tasteProfile).copy(priority = 72))
        }

        sections.add(getDiscoverySection(personality, tasteProfile).copy(priority = 50))
        sections.add(getWildcardSection(currentMood, time, tasteProfile).copy(priority = 40))

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

    // ── Synonym query pools ───────────────────────────────────────────────
    // Spotify treats these as different queries → genuinely different results.

    private val MOOD_QUERY_POOLS = mapOf(
        "happy" to listOf(
            "happy uplifting feel good pop sunshine bright",
            "joyful cheerful fun dance positive vibes music",
            "upbeat celebrate party groove summer hits 2024",
            "smile good mood energizing pop feel great music",
            "carefree breezy light pop fun indie happy songs"
        ),
        "sad" to listOf(
            "sad emotional ballad comfort heartbreak songs",
            "melancholy heartbroken lonely slow ballad music",
            "sorrowful tearful acoustic soft sad indie songs",
            "bittersweet longing emotional piano sad music",
            "rainy day sad slow emotional breakup songs"
        ),
        "energetic" to listOf(
            "energetic workout hype pump up bass drop",
            "high energy adrenaline intense power anthem gym",
            "beast mode hustle grind hype rap trap fire",
            "turnt lit hype festival edm drop banger 2024",
            "aggressive hard hitting heavy bass run sprint pump"
        ),
        "calm" to listOf(
            "chill ambient relaxing peaceful gentle lofi",
            "tranquil serene mellow acoustic soft meditation",
            "quiet cosy warm soft background music relax",
            "gentle piano slow calm instrumental ambient focus",
            "zen peaceful nature sounds lofi study coffee"
        ),
        "focused" to listOf(
            "focus study instrumental lofi beats concentration",
            "deep work concentration flow state ambient music",
            "productivity coding study beats minimal music",
            "brain focus alpha waves instrumental study lofi",
            "studying reading work from home ambient music"
        ),
        "tired" to listOf(
            "soft gentle acoustic slow soothing lullaby",
            "wind down slow tempo mellow soft quiet night",
            "sleepy drowsy soft indie folk night music",
            "gentle sleep calming night slow acoustic music",
            "cosy quiet slow ambient bedtime sleep music"
        ),
        "romantic" to listOf(
            "romantic love songs r&b smooth slow dance",
            "intimate love acoustic guitar romantic ballad",
            "slow dance date night soul r&b smooth love",
            "sensual moody r&b love bedroom late night",
            "warm tender love pop romantic heartfelt songs"
        ),
        "angry" to listOf(
            "aggressive rock metal punk heavy cathartic",
            "rage fury hard rock screaming intense music",
            "angry loud heavy guitar metal punk songs",
            "cathartic release loud heavy angry rap metal",
            "intense aggressive dark heavy music angst"
        ),
        "anxious" to listOf(
            "calming anxiety relief meditation peaceful ambient",
            "breathing slow calm soothing anxiety help music",
            "gentle reassuring warm slow soft music calm",
            "grounding mindful peaceful soft anxiety calm",
            "stress relief slow gentle calming nature music"
        )
    )

    private val TIME_QUERY_POOLS = mapOf(
        "morning" to listOf(
            "morning feel good happy start day acoustic",
            "wake up fresh morning energy gentle pop music",
            "sunrise morning acoustic indie bright new day",
            "good morning positive upbeat start day pop",
            "morning coffee acoustic chill start day music"
        ),
        "afternoon" to listOf(
            "afternoon chill pop indie feel good",
            "midday upbeat pop groove bright afternoon music",
            "afternoon feel good indie pop sunny music",
            "daytime energy pop bright cheerful afternoon",
            "afternoon vibes chill pop sunny indie music"
        ),
        "evening" to listOf(
            "evening chill r&b smooth jazz soul",
            "sunset evening mellow r&b smooth music",
            "after work unwind evening smooth r&b jazz",
            "evening relax smooth mellow soul jazz music",
            "twilight evening soft r&b chill music"
        ),
        "night" to listOf(
            "late night r&b smooth jazz soul chill",
            "midnight dark ambient electronic moody music",
            "night drive dark slow moody music late",
            "late night chill slow r&b moody music",
            "night quiet slow ambient dark moody music"
        )
    )

    private val PERSONALITY_QUERY_POOLS = mapOf(
        "ZEN" to listOf(
            "meditation yoga ambient nature sounds peaceful",
            "zen mindful calm spiritual ambient slow music",
            "peaceful nature birds water ambient meditation",
            "yoga flow gentle peaceful ambient music calm",
            "mindfulness slow breath calm nature ambient"
        ),
        "PARTY" to listOf(
            "club bangers dance edm house party 2025",
            "party hits dance floor edm banger 2024",
            "rave techno dance floor club edm music",
            "festival dance music pop edm party 2024",
            "hype party hits dance floor top songs"
        ),
        "EMO" to listOf(
            "indie emotional alternative deep lyrics meaningful",
            "emo sad indie rock emotional alternative lyrics",
            "deep meaningful sad alternative indie music",
            "introspective sad indie emotional alternative",
            "heartfelt raw emotional indie rock alternative"
        ),
        "SCHOLAR" to listOf(
            "instrumental classical piano ambient electronic focus",
            "study deep work focus brain lofi classical",
            "concentration flow state study ambient music",
            "smart focus classical instrumental piano music",
            "lofi hip hop study beats brain focus music"
        ),
        "EXPLORER" to listOf(
            "underrated indie new artists emerging debut album",
            "hidden gems new music discovery indie 2024",
            "underground indie emerging artists fresh music",
            "new rising artists indie folk alternative 2024",
            "fresh finds new music underrated artists indie"
        ),
        "NIGHTOWL" to listOf(
            "dark ambient electronic experimental late night bass",
            "midnight dark moody electronic bass music",
            "late night dark ambient synth electronic music",
            "nocturnal dark bass underground electronic music",
            "after midnight dark moody ambient electronic"
        )
    )

    private fun pickQuery(pool: List<String>): String =
        pool[0]

    // ── Personalised query builder ────────────────────────────────────────

    private fun buildQuery(
        base:         String,
        mood:         String,
        tasteProfile: UserTasteProfile,
        maxArtists:   Int = 2
    ): String {
        val parts = mutableListOf(base)

        val moodArtists = tasteProfile.moodArtistSeeds[mood]
            ?.filter { it !in tasteProfile.skippedArtists }
            ?.take(maxArtists)
            ?: emptyList()

        if (moodArtists.isNotEmpty()) {
            parts.addAll(moodArtists)
        } else if (tasteProfile.topArtists.isNotEmpty()) {
            parts.add(tasteProfile.topArtists.first())
        }

        val topGenre = tasteProfile.genreWeights.entries
            .filter { it.value >= 0.3f }
            .maxByOrNull { it.value }?.key
        if (topGenre != null) parts.add(genreToSearchKeyword(topGenre))

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

    // ── Section builders ──────────────────────────────────────────────────

    private fun getMoodSection(
        mood: String, t: UserTasteProfile
    ): RecommendedSection {
        val pool  = MOOD_QUERY_POOLS[mood] ?: MOOD_QUERY_POOLS["happy"]!!
        val base  = pickQuery(pool)
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

    private fun getTherapeuticSection(
        mood: String, t: UserTasteProfile
    ): RecommendedSection? {
        val (base, title, emoji) = when (mood) {
            "sad"     -> Triple("uplifting motivational happy empowering anthems", "Want to feel better?", "🌟")
            "angry"   -> Triple("calm peaceful gentle acoustic meditation nature", "Cool down with these", "🧊")
            "anxious" -> Triple("relaxing breathing meditation spa nature sounds", "Breathe easy", "🍃")
            "tired"   -> Triple("energetic morning motivation wake up coffee beats", "Pick-me-up boost", "☕")
            else      -> return null
        }
        return RecommendedSection(title, emoji, buildQuery(base, mood, t, maxArtists = 0))
    }

    private fun getArtistAffinitySection(
        t: UserTasteProfile
    ): RecommendedSection {
        // Rotate which artists we seed with across refreshes for variety
        val allSeeds = (t.likedArtists + t.topArtists)
            .distinct()
            .filter { it !in t.skippedArtists }
        // Shift the window of artists used: refresh 0→[0,1,2], refresh 1→[1,2,3], etc.
        val start   = 0
        val seeds   = allSeeds.drop(start).take(3).ifEmpty { allSeeds.take(3) }
        val query   = seeds.joinToString(" ") + " similar"
        return RecommendedSection("More from artists you love", "❤️", query)
    }

    private fun getLanguageSection(
        t: UserTasteProfile
    ): RecommendedSection {
        val pools = when (t.dominantLanguage) {
            "ko" -> listOf(
                "kpop korean trending popular 2024",
                "korean pop music popular hits recent",
                "kpop girl group boy band trending songs",
                "korean music popular chart hits 2024",
                "kpop new releases trending korean artists"
            )
            "zh" -> listOf(
                "mandopop chinese popular hits",
                "chinese pop music trending taiwan hong kong",
                "mandarin pop music popular recent hits",
                "chinese music popular chart hits artists",
                "c-pop mandopop trending songs artists"
            )
            "ja" -> listOf(
                "jpop japanese anime popular",
                "japanese music popular chart hits 2024",
                "j-pop trending songs japanese artists recent",
                "anime ost japanese popular music hits",
                "jpop girl group boy band japanese trending"
            )
            "ms" -> listOf(
                "malay bahasa popular hits",
                "lagu melayu popular trending malaysia",
                "malay music popular chart hits artists",
                "bahasa malaysia popular songs trending",
                "lagu popular melayu terbaru hits"
            )
            else -> listOf("world music trending popular 2024")
        }
        val base = pickQuery(pools)
        val artistSeeds = t.topArtists.take(2).joinToString(" ")
        val (langLabel, langEmoji) = when (t.dominantLanguage) {
            "ko" -> "K-Pop" to "🇰🇷"
            "zh" -> "C-Pop" to "🇨🇳"
            "ja" -> "J-Pop" to "🇯🇵"
            "ms" -> "Malay" to "🇲🇾"
            else -> "Global" to "🌏"
        }
        return RecommendedSection(
            "$langEmoji $langLabel picks for you", langEmoji,
            "$base $artistSeeds".trim()
        )
    }

    private fun getTimeSection(
        time: TimeOfDay, personality: PetPersonality,
        t: UserTasteProfile
    ): RecommendedSection {
        val timeKey = time.label.lowercase()
        val pool    = TIME_QUERY_POOLS[timeKey] ?: TIME_QUERY_POOLS["afternoon"]!!
        val base    = pickQuery(pool)
        val title   = when (time) {
            TimeOfDay.MORNING   -> "Good morning tunes"
            TimeOfDay.AFTERNOON -> "Afternoon picks"
            TimeOfDay.EVENING   -> "Evening wind-down"
            TimeOfDay.NIGHT     -> "Late night vibes"
        }
        val emoji   = when (time) {
            TimeOfDay.MORNING->"🌅"; TimeOfDay.AFTERNOON->"☀️"
            TimeOfDay.EVENING->"🌆"; TimeOfDay.NIGHT->"🌙"
        }
        return RecommendedSection(title, emoji, buildQuery(base, "neutral", t, maxArtists = 1))
    }

    private fun getPersonalitySection(
        personality: PetPersonality, t: UserTasteProfile
    ): RecommendedSection {
        val key  = personality.name
        val pool = PERSONALITY_QUERY_POOLS[key] ?: PERSONALITY_QUERY_POOLS["EXPLORER"]!!
        val base = pickQuery(pool)
        return RecommendedSection(
            "${personality.emoji} ${personality.name.lowercase().replaceFirstChar { it.uppercase() }} picks",
            when(personality) {
                PetPersonality.ZEN->"🧘"; PetPersonality.PARTY->"🎉"
                PetPersonality.EMO->"🌧️"; PetPersonality.SCHOLAR->"📚"
                PetPersonality.EXPLORER->"🔍"; PetPersonality.NIGHTOWL->"🦉"
            },
            buildQuery(base, "neutral", t, maxArtists = 1)
        )
    }

    private fun getDiscoverySection(
        personality: PetPersonality, t: UserTasteProfile
    ): RecommendedSection {
        val weakGenre = listOf("indie","classical","jazz","reggae","folk","world")
            .firstOrNull { (t.genreWeights[it] ?: 0f) < 0.2f } ?: "world music"
        // Rotate discovery angle each refresh
        val discoveryAngles = listOf(
            "$weakGenre new artists emerging music",
            "$weakGenre hidden gems underrated recent",
            "$weakGenre fresh releases debut 2024",
            "$weakGenre independent artists new music",
            "$weakGenre rising artists new discovery"
        )
        return RecommendedSection("Try something new", "🔮",
            pickQuery(discoveryAngles))
    }

    private fun getWildcardSection(
        mood: String, time: TimeOfDay, t: UserTasteProfile
    ): RecommendedSection {
        val wildcards = listOf(
            "acoustic covers popular songs stripped back",
            "throwback hits nostalgia 2000s 2010s classics",
            "viral tiktok trending songs 2024 popular",
            "road trip singalong anthems driving songs",
            "coffee shop acoustic chill background music"
        )
        return RecommendedSection("Something different", "🎲",
            buildQuery(pickQuery(wildcards), mood, t, maxArtists = 1))
    }

    private fun getPersonalizedTitle(personality: PetPersonality): String = when (personality) {
        PetPersonality.ZEN      -> "Curated for your calm soul"
        PetPersonality.PARTY    -> "Made for the party animal in you"
        PetPersonality.EMO      -> "Deep picks, just for you"
        PetPersonality.SCHOLAR  -> "Brain food, curated"
        PetPersonality.EXPLORER -> "Handpicked hidden gems"
        PetPersonality.NIGHTOWL -> "Your after-dark playlist"
    }
}