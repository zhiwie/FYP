package com.example.fypdraft.ml

import android.util.Log
import com.example.fypdraft.model.MoodResult
import com.example.fypdraft.model.TrackMood

/**
 * MetadataEmotionTagger
 *
 * Pure offline, synchronous mood tagging from track name + artist + album.
 * Zero network calls. Zero TFLite. Runs in < 0.1 ms.
 *
 * Pipeline:
 *  1. Known-artist table — direct artist → mood mapping with high confidence.
 *  2. Weighted multilingual keyword scoring:
 *       title  match = keyword weight × 3
 *       artist match = keyword weight × 2
 *       album  match = keyword weight × 1
 *  3. Both signals combined → highest confidence wins.
 *
 * Returns [MoodResult] with confidence + source + reason for UI display
 * and for the GPT fallback decision.
 *
 * Confidence scale:
 *   artist only            → 0.80
 *   keyword only (strong)  → 0.70  (score ≥ 9)
 *   keyword only (medium)  → 0.55  (score ≥ 5)
 *   keyword only (weak)    → 0.40  (score ≥ 2)
 *   artist + keyword agree → 0.92
 *   artist + keyword differ→ 0.75  (artist wins, keyword adds nuance)
 *   no match               → 0.0   (NEUTRAL)
 */
object MetadataEmotionTagger {

    private const val TAG = "MetadataEmotionTagger"

    // ── Confidence thresholds ────────────────────────────────────────
    private const val CONF_ARTIST_ONLY          = 0.80
    private const val CONF_ARTIST_KEYWORD_AGREE = 0.92
    private const val CONF_ARTIST_KEYWORD_DIFF  = 0.75
    private const val CONF_KEYWORD_STRONG       = 0.70   // score ≥ 9
    private const val CONF_KEYWORD_MEDIUM       = 0.55   // score ≥ 5
    private const val CONF_KEYWORD_WEAK         = 0.40   // score ≥ 2
    private const val KEYWORD_SCORE_STRONG      = 9f
    private const val KEYWORD_SCORE_MEDIUM      = 5f
    private const val KEYWORD_SCORE_MIN         = 2f

    // ─────────────────────────────────────────────────────────────────
    // 1. Known-artist table
    // ─────────────────────────────────────────────────────────────────
    private val ARTIST_MOOD = mapOf(
        // K-Pop / K-R&B
        "blackpink"         to TrackMood.ENERGETIC,
        "bts"               to TrackMood.HAPPY,
        "twice"             to TrackMood.HAPPY,
        "exo"               to TrackMood.ENERGETIC,
        "red velvet"        to TrackMood.HAPPY,
        "shinee"            to TrackMood.ENERGETIC,
        "stray kids"        to TrackMood.ENERGETIC,
        "nct"               to TrackMood.ENERGETIC,
        "aespa"             to TrackMood.ENERGETIC,
        "ive"               to TrackMood.HAPPY,
        "newjeans"          to TrackMood.HAPPY,
        "itzy"              to TrackMood.ENERGETIC,
        "mamamoo"           to TrackMood.HAPPY,
        "got7"              to TrackMood.HAPPY,
        "monsta x"          to TrackMood.ENERGETIC,
        "seventeen"         to TrackMood.HAPPY,
        "bigbang"           to TrackMood.ENERGETIC,
        "2ne1"              to TrackMood.ENERGETIC,
        "super junior"      to TrackMood.HAPPY,
        "wonder girls"      to TrackMood.HAPPY,
        "girl's generation" to TrackMood.HAPPY,
        "snsd"              to TrackMood.HAPPY,
        "jennie"            to TrackMood.HAPPY,
        "lisa"              to TrackMood.ENERGETIC,
        "rosé"              to TrackMood.SAD,
        "jungkook"          to TrackMood.HAPPY,
        "j-hope"            to TrackMood.ENERGETIC,
        "suga"              to TrackMood.SAD,
        "agust d"           to TrackMood.SAD,
        "rm "               to TrackMood.CALM,
        // J-Pop / Anime
        "yoasobi"           to TrackMood.ENERGETIC,
        "ado"               to TrackMood.ENERGETIC,
        "kenshi yonezu"     to TrackMood.HAPPY,
        "aimer"             to TrackMood.SAD,
        "yorushika"         to TrackMood.SAD,
        "fujii kaze"        to TrackMood.CALM,
        "hikaru utada"      to TrackMood.SAD,
        "one ok rock"       to TrackMood.ENERGETIC,
        "band-maid"         to TrackMood.ENERGETIC,
        // Mandopop / C-Pop
        "jay chou"          to TrackMood.HAPPY,
        "jolin tsai"        to TrackMood.ENERGETIC,
        "eason chan"        to TrackMood.SAD,
        "khalil fong"       to TrackMood.CALM,
        "joker xue"         to TrackMood.SAD,
        "xiao zhan"         to TrackMood.HAPPY,
        "wang yibo"         to TrackMood.ENERGETIC,
        "g.e.m."            to TrackMood.HAPPY,
        "gem tang"          to TrackMood.HAPPY,
        "mayday"            to TrackMood.HAPPY,
        "sodagreen"         to TrackMood.SAD,
        "fish leong"        to TrackMood.SAD,
        "stefanie sun"      to TrackMood.HAPPY,
        "jj lin"            to TrackMood.HAPPY,
        "jim cummings"      to TrackMood.CALM,
        // Western Pop / Dance
        "taylor swift"      to TrackMood.HAPPY,
        "ariana grande"     to TrackMood.HAPPY,
        "billie eilish"     to TrackMood.SAD,
        "dua lipa"          to TrackMood.ENERGETIC,
        "the weeknd"        to TrackMood.SAD,
        "ed sheeran"        to TrackMood.HAPPY,
        "post malone"       to TrackMood.SAD,
        "drake"             to TrackMood.SAD,
        "travis scott"      to TrackMood.ENERGETIC,
        "kendrick lamar"    to TrackMood.ENERGETIC,
        "beyoncé"           to TrackMood.ENERGETIC,
        "rihanna"           to TrackMood.ENERGETIC,
        "lady gaga"         to TrackMood.ENERGETIC,
        "katy perry"        to TrackMood.HAPPY,
        "justin bieber"     to TrackMood.HAPPY,
        "harry styles"      to TrackMood.HAPPY,
        "olivia rodrigo"    to TrackMood.SAD,
        "sabrina carpenter" to TrackMood.HAPPY,
        "charli xcx"        to TrackMood.ENERGETIC,
        "sia"               to TrackMood.ENERGETIC,
        "adele"             to TrackMood.SAD,
        "sam smith"         to TrackMood.SAD,
        "lewis capaldi"     to TrackMood.SAD,
        "shawn mendes"      to TrackMood.HAPPY,
        "charlie puth"      to TrackMood.HAPPY,
        "selena gomez"      to TrackMood.HAPPY,
        "camila cabello"    to TrackMood.HAPPY,
        "doja cat"          to TrackMood.ENERGETIC,
        "lizzo"             to TrackMood.HAPPY,
        "meghan trainor"    to TrackMood.HAPPY,
        "p!nk"              to TrackMood.ENERGETIC,
        "eminem"            to TrackMood.ENERGETIC,
        "linkin park"       to TrackMood.ENERGETIC,
        "imagine dragons"   to TrackMood.ENERGETIC,
        "coldplay"          to TrackMood.HAPPY,
        "maroon 5"          to TrackMood.HAPPY,
        "one direction"     to TrackMood.HAPPY,
        "jackson 5"         to TrackMood.HAPPY,
        "michael jackson"   to TrackMood.HAPPY,
        "bruno mars"        to TrackMood.HAPPY,
        "john legend"       to TrackMood.CALM,
        "frank ocean"       to TrackMood.SAD,
        "daniel caesar"     to TrackMood.CALM,
        "h.e.r."            to TrackMood.CALM,
        "sza"               to TrackMood.SAD,
        "jhené aiko"        to TrackMood.CALM,
        "khalid"            to TrackMood.SAD,
        "james arthur"      to TrackMood.SAD,
        "james blunt"       to TrackMood.SAD,
        "passenger"         to TrackMood.SAD,
        "hozier"            to TrackMood.SAD,
        "lana del rey"      to TrackMood.SAD,
        "lorde"             to TrackMood.SAD,
        "bon iver"          to TrackMood.SAD,
        "sufjan stevens"    to TrackMood.CALM,
        "norah jones"       to TrackMood.CALM,
        "jack johnson"      to TrackMood.CALM,
        "ben harper"        to TrackMood.CALM,
        "mac miller"        to TrackMood.SAD,
        // EDM / Dance
        "david guetta"      to TrackMood.ENERGETIC,
        "calvin harris"     to TrackMood.ENERGETIC,
        "martin garrix"     to TrackMood.ENERGETIC,
        "avicii"            to TrackMood.ENERGETIC,
        "alan walker"       to TrackMood.ENERGETIC,
        "marshmello"        to TrackMood.ENERGETIC,
        "the chainsmokers"  to TrackMood.ENERGETIC,
        "kygo"              to TrackMood.CALM,
        "flume"             to TrackMood.CALM,
        "odesza"            to TrackMood.CALM,
        // Malay / SEA
        "siti nurhaliza"    to TrackMood.CALM,
        "zee avi"           to TrackMood.CALM,
        "yuna"              to TrackMood.CALM,
        "najwa latif"       to TrackMood.SAD,
        "faizal tahir"      to TrackMood.ENERGETIC,
        "jaclyn victor"     to TrackMood.HAPPY,
        "shila amzah"       to TrackMood.HAPPY,
        "hujan"             to TrackMood.SAD,
        "meet uncle hussain" to TrackMood.HAPPY,
    )

    // ─────────────────────────────────────────────────────────────────
    // 2. Keyword rules
    // ─────────────────────────────────────────────────────────────────
    private data class KeywordRule(val keyword: String, val mood: TrackMood, val weight: Float = 1f)

    private val KEYWORD_RULES = listOf(
        // ── ENERGETIC ────────────────────────────────────────────────
        KeywordRule("ddu-du",            TrackMood.ENERGETIC, 4f),
        KeywordRule("boombayah",         TrackMood.ENERGETIC, 4f),
        KeywordRule("kill this love",    TrackMood.ENERGETIC, 4f),
        KeywordRule("how you like that", TrackMood.ENERGETIC, 4f),
        KeywordRule("shut down",         TrackMood.ENERGETIC, 2f),
        KeywordRule("energetic",         TrackMood.ENERGETIC, 3f),
        KeywordRule("pump it",           TrackMood.ENERGETIC, 3f),
        KeywordRule("let's go",          TrackMood.ENERGETIC, 2f),
        KeywordRule("power",             TrackMood.ENERGETIC, 1.5f),
        KeywordRule("fire",              TrackMood.ENERGETIC, 1.5f),
        KeywordRule("hype",              TrackMood.ENERGETIC, 2f),
        KeywordRule("beast mode",        TrackMood.ENERGETIC, 3f),
        KeywordRule("workout",           TrackMood.ENERGETIC, 2f),
        KeywordRule("wild",              TrackMood.ENERGETIC, 1.5f),
        KeywordRule("crazy",             TrackMood.ENERGETIC, 1f),
        KeywordRule("turn up",           TrackMood.ENERGETIC, 2f),
        KeywordRule("bass",              TrackMood.ENERGETIC, 1f),
        KeywordRule("drop",              TrackMood.ENERGETIC, 1f),
        KeywordRule("rage",              TrackMood.ENERGETIC, 2f),
        KeywordRule("adrenaline",        TrackMood.ENERGETIC, 2f),
        KeywordRule("electric",          TrackMood.ENERGETIC, 1.5f),
        KeywordRule("thunder",           TrackMood.ENERGETIC, 1.5f),
        KeywordRule("bang",              TrackMood.ENERGETIC, 1.5f),
        KeywordRule("monster",           TrackMood.ENERGETIC, 1.5f),
        KeywordRule("warrior",           TrackMood.ENERGETIC, 2f),
        KeywordRule("battle",            TrackMood.ENERGETIC, 1.5f),
        KeywordRule("rebel",             TrackMood.ENERGETIC, 1.5f),
        KeywordRule("riot",              TrackMood.ENERGETIC, 2f),
        KeywordRule("galok",             TrackMood.ENERGETIC, 2f),
        // ── HAPPY ────────────────────────────────────────────────────
        KeywordRule("happy",             TrackMood.HAPPY, 3f),
        KeywordRule("joyful",            TrackMood.HAPPY, 3f),
        KeywordRule("uplifting",         TrackMood.HAPPY, 3f),
        KeywordRule("joy",               TrackMood.HAPPY, 2f),
        KeywordRule("sunshine",          TrackMood.HAPPY, 2f),
        KeywordRule("bright",            TrackMood.HAPPY, 1.5f),
        KeywordRule("smile",             TrackMood.HAPPY, 2f),
        KeywordRule("celebrate",         TrackMood.HAPPY, 2f),
        KeywordRule("party",             TrackMood.HAPPY, 2f),
        KeywordRule("fun",               TrackMood.HAPPY, 1.5f),
        KeywordRule("feel good",         TrackMood.HAPPY, 2.5f),
        KeywordRule("upbeat",            TrackMood.HAPPY, 2f),
        KeywordRule("cheerful",          TrackMood.HAPPY, 2f),
        KeywordRule("wonderful",         TrackMood.HAPPY, 2f),
        KeywordRule("summer",            TrackMood.HAPPY, 1.5f),
        KeywordRule("dancing",           TrackMood.HAPPY, 2f),
        KeywordRule("groove",            TrackMood.HAPPY, 1.5f),
        KeywordRule("positive",          TrackMood.HAPPY, 1.5f),
        KeywordRule("blessed",           TrackMood.HAPPY, 2f),
        KeywordRule("good vibes",        TrackMood.HAPPY, 2.5f),
        KeywordRule("lucky",             TrackMood.HAPPY, 1.5f),
        KeywordRule("amazing",           TrackMood.HAPPY, 1.5f),
        KeywordRule("perfect",           TrackMood.HAPPY, 1.5f),
        KeywordRule("bubblegum",         TrackMood.HAPPY, 2f),
        KeywordRule("bloom",             TrackMood.HAPPY, 1.5f),
        KeywordRule("rainbow",           TrackMood.HAPPY, 2f),
        KeywordRule("fairytale",         TrackMood.HAPPY, 2f),
        KeywordRule("raya",              TrackMood.HAPPY, 2f),
        KeywordRule("bahagia",           TrackMood.HAPPY, 3f),
        KeywordRule("gembira",           TrackMood.HAPPY, 3f),
        KeywordRule("ceria",             TrackMood.HAPPY, 3f),
        KeywordRule("快乐",              TrackMood.HAPPY, 3f),
        KeywordRule("幸福",              TrackMood.HAPPY, 3f),
        KeywordRule("开心",              TrackMood.HAPPY, 3f),
        KeywordRule("嬉しい",            TrackMood.HAPPY, 3f),
        KeywordRule("楽しい",            TrackMood.HAPPY, 3f),
        // ── SAD ──────────────────────────────────────────────────────
        KeywordRule("sad",               TrackMood.SAD, 3f),
        KeywordRule("cry",               TrackMood.SAD, 2.5f),
        KeywordRule("tears",             TrackMood.SAD, 2.5f),
        KeywordRule("heartbreak",        TrackMood.SAD, 3f),
        KeywordRule("heartbroken",       TrackMood.SAD, 3f),
        KeywordRule("lonely",            TrackMood.SAD, 2.5f),
        KeywordRule("miss you",          TrackMood.SAD, 3f),
        KeywordRule("pain",              TrackMood.SAD, 2f),
        KeywordRule("broken",            TrackMood.SAD, 2f),
        KeywordRule("goodbye",           TrackMood.SAD, 2.5f),
        KeywordRule("alone",             TrackMood.SAD, 2f),
        KeywordRule("hurt",              TrackMood.SAD, 2f),
        KeywordRule("sorrow",            TrackMood.SAD, 2.5f),
        KeywordRule("melancholy",        TrackMood.SAD, 3f),
        KeywordRule("regret",            TrackMood.SAD, 2f),
        KeywordRule("without you",       TrackMood.SAD, 3f),
        KeywordRule("falling apart",     TrackMood.SAD, 3f),
        KeywordRule("drown",             TrackMood.SAD, 2f),
        KeywordRule("empty",             TrackMood.SAD, 1.5f),
        KeywordRule("numb",              TrackMood.SAD, 2f),
        KeywordRule("bleeding",          TrackMood.SAD, 2f),
        KeywordRule("sorry",             TrackMood.SAD, 1.5f),
        KeywordRule("never see you again", TrackMood.SAD, 4f),
        KeywordRule("sedih",             TrackMood.SAD, 3f),
        KeywordRule("rindu",             TrackMood.SAD, 3f),
        KeywordRule("tangis",            TrackMood.SAD, 3f),
        KeywordRule("pergi",             TrackMood.SAD, 2f),
        KeywordRule("伤心",              TrackMood.SAD, 3f),
        KeywordRule("难过",              TrackMood.SAD, 3f),
        KeywordRule("悲",               TrackMood.SAD, 2f),
        KeywordRule("泪",               TrackMood.SAD, 2f),
        KeywordRule("寂寞",              TrackMood.SAD, 2.5f),
        KeywordRule("悲しい",            TrackMood.SAD, 3f),
        KeywordRule("泣く",              TrackMood.SAD, 2.5f),
        // ── CALM ─────────────────────────────────────────────────────
        KeywordRule("calm",              TrackMood.CALM, 3f),
        KeywordRule("peaceful",          TrackMood.CALM, 3f),
        KeywordRule("peace",             TrackMood.CALM, 2.5f),
        KeywordRule("gentle",            TrackMood.CALM, 2f),
        KeywordRule("soft",              TrackMood.CALM, 1.5f),
        KeywordRule("quiet",             TrackMood.CALM, 2f),
        KeywordRule("breeze",            TrackMood.CALM, 2f),
        KeywordRule("relax",             TrackMood.CALM, 2.5f),
        KeywordRule("chill",             TrackMood.CALM, 2f),
        KeywordRule("floating",          TrackMood.CALM, 2f),
        KeywordRule("ambient",           TrackMood.CALM, 2f),
        KeywordRule("soothing",          TrackMood.CALM, 2.5f),
        KeywordRule("tranquil",          TrackMood.CALM, 3f),
        KeywordRule("serene",            TrackMood.CALM, 3f),
        KeywordRule("meditation",        TrackMood.CALM, 2.5f),
        KeywordRule("zen",               TrackMood.CALM, 2.5f),
        KeywordRule("ocean",             TrackMood.CALM, 2f),
        KeywordRule("piano",             TrackMood.CALM, 1.5f),
        KeywordRule("acoustic",          TrackMood.CALM, 2f),
        KeywordRule("lofi",              TrackMood.CALM, 2f),
        KeywordRule("lo-fi",             TrackMood.CALM, 2f),
        KeywordRule("sleep",             TrackMood.CALM, 2f),
        KeywordRule("lullaby",           TrackMood.CALM, 3f),
        KeywordRule("tenang",            TrackMood.CALM, 3f),
        KeywordRule("damai",             TrackMood.CALM, 3f),
        KeywordRule("平静",              TrackMood.CALM, 3f),
        KeywordRule("宁静",              TrackMood.CALM, 3f),
        KeywordRule("静か",              TrackMood.CALM, 3f),
    )

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tag a track and return a full [MoodResult].
     * Call at parse time — this is synchronous and runs in < 0.1 ms.
     */
    fun tag(name: String, artist: String, album: String = ""): MoodResult {
        val nameL   = name.lowercase().trim()
        val artistL = artist.lowercase().trim()
        val albumL  = album.lowercase().trim()

        // ── Step 1: artist lookup ─────────────────────────────────────
        var artistMatch: Pair<String, TrackMood>? = null
        for ((fragment, mood) in ARTIST_MOOD) {
            if (artistL.contains(fragment)) {
                artistMatch = fragment to mood
                break
            }
        }

        // ── Step 2: keyword scoring ───────────────────────────────────
        val scores = mutableMapOf<TrackMood, Float>()
        val matchedKeywords = mutableMapOf<TrackMood, MutableList<String>>()

        for (rule in KEYWORD_RULES) {
            val kw = rule.keyword
            val scoreAdded = when {
                nameL.contains(kw)   -> rule.weight * 3f
                artistL.contains(kw) -> rule.weight * 2f
                albumL.contains(kw)  -> rule.weight * 1f
                else                 -> 0f
            }
            if (scoreAdded > 0f) {
                scores[rule.mood] = (scores[rule.mood] ?: 0f) + scoreAdded
                matchedKeywords.getOrPut(rule.mood) { mutableListOf() }.add(kw)
            }
        }

        val topKeyword = scores.maxByOrNull { it.value }

        // ── Step 3: combine signals → MoodResult ─────────────────────
        return when {
            // Artist match + keyword agree → highest confidence
            artistMatch != null && topKeyword != null
                    && topKeyword.value >= KEYWORD_SCORE_MIN
                    && topKeyword.key == artistMatch.second -> {
                val kwList = matchedKeywords[topKeyword.key]?.take(3)?.joinToString(", ") ?: ""
                MoodResult(
                    mood       = artistMatch.second,
                    confidence = CONF_ARTIST_KEYWORD_AGREE,
                    source     = "artist+keyword",
                    reason     = "Artist '${artist}' matched; keywords: $kwList"
                )
            }

            // Artist match + keyword disagree → artist wins, medium-high confidence
            artistMatch != null && topKeyword != null && topKeyword.value >= KEYWORD_SCORE_MIN -> {
                MoodResult(
                    mood       = artistMatch.second,
                    confidence = CONF_ARTIST_KEYWORD_DIFF,
                    source     = "artist",
                    reason     = "Artist '${artist}' matched '${artistMatch.first}'; " +
                            "keyword suggested ${topKeyword.key.label} but artist takes priority"
                )
            }

            // Artist match alone
            artistMatch != null -> {
                MoodResult(
                    mood       = artistMatch.second,
                    confidence = CONF_ARTIST_ONLY,
                    source     = "artist",
                    reason     = "Artist '${artist}' matched known entry '${artistMatch.first}'"
                )
            }

            // Keyword only — strong
            topKeyword != null && topKeyword.value >= KEYWORD_SCORE_STRONG -> {
                val kwList = matchedKeywords[topKeyword.key]?.take(3)?.joinToString(", ") ?: ""
                MoodResult(
                    mood       = topKeyword.key,
                    confidence = CONF_KEYWORD_STRONG,
                    source     = "keyword",
                    reason     = "Strong keyword match (score=${topKeyword.value.toInt()}): $kwList"
                )
            }

            // Keyword only — medium
            topKeyword != null && topKeyword.value >= KEYWORD_SCORE_MEDIUM -> {
                val kwList = matchedKeywords[topKeyword.key]?.take(3)?.joinToString(", ") ?: ""
                MoodResult(
                    mood       = topKeyword.key,
                    confidence = CONF_KEYWORD_MEDIUM,
                    source     = "keyword",
                    reason     = "Keyword match (score=${topKeyword.value.toInt()}): $kwList"
                )
            }

            // Keyword only — weak
            topKeyword != null && topKeyword.value >= KEYWORD_SCORE_MIN -> {
                val kwList = matchedKeywords[topKeyword.key]?.take(2)?.joinToString(", ") ?: ""
                MoodResult(
                    mood       = topKeyword.key,
                    confidence = CONF_KEYWORD_WEAK,
                    source     = "keyword",
                    reason     = "Weak keyword match (score=${topKeyword.value.toInt()}): $kwList"
                )
            }

            // No signal
            else -> {
                Log.d(TAG, "No match → NEUTRAL | '$name' by '$artist'")
                MoodResult.NEUTRAL.copy(
                    reason = "No artist or keyword match for '$name' by '$artist'"
                )
            }
        }.also { result ->
            Log.d(TAG, "tag → ${result.mood.emoji} ${result.mood.label} " +
                    "(${(result.confidence * 100).toInt()}%) [${result.source}] " +
                    "| '$name' by '$artist'")
        }
    }
}