package com.example.fypdraft.ml

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * UserTasteProfile
 *
 * Extracted from the user's actual Firestore listening history and favorites.
 * Built once per session, used by MoodAwareRecommender to personalise every
 * search query with real artist, genre, and language signals.
 *
 * Stored under: rl_state/{userId}/tasteProfile (merged into existing RL doc)
 */
data class UserTasteProfile(
    // Top artists the user has listened to most (by play count)
    val topArtists: List<String>   = emptyList(),   // e.g. ["BLACKPINK", "Jay Chou", "Bruno Mars"]

    // Inferred genre weights: genre → normalised score 0–1
    val genreWeights: Map<String, Float> = emptyMap(),  // e.g. {"kpop":0.8, "pop":0.4}

    // Inferred language: "en","ko","zh","ja","ms","mixed"
    val dominantLanguage: String   = "en",

    // Artists the user explicitly liked (from Firestore favorites)
    val likedArtists: List<String> = emptyList(),

    // Artists the user skipped quickly (negative signal)
    val skippedArtists: List<String> = emptyList(),

    // Mood → preferred artist seeds (top artist per mood)
    val moodArtistSeeds: Map<String, List<String>> = emptyMap(),   // e.g. "calm"→["Khalil Fong","Kygo"]

    val totalTracksAnalysed: Int = 0
)

object UserTasteProfileBuilder {

    private const val TAG = "TasteProfile"

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Genre inference keywords matched against artist name + track name
    private val GENRE_SIGNALS = mapOf(
        "kpop"      to listOf("blackpink","bts","twice","exo","aespa","ive","newjeans",
            "itzy","stray kids","nct","seventeen","bigbang","2ne1",
            "mamamoo","got7","shinee","monsta x","jennie","lisa",
            "jungkook","j-hope","suga","agust d"),
        "mandopop"  to listOf("jay chou","jolin tsai","eason chan","khalil fong","g.e.m.",
            "mayday","sodagreen","fish leong","stefanie sun","jj lin",
            "joker xue","xiao zhan","wang yibo","gem tang"),
        "jpop"      to listOf("yoasobi","ado","kenshi yonezu","aimer","yorushika",
            "fujii kaze","hikaru utada","one ok rock","band-maid"),
        "malay"     to listOf("siti nurhaliza","zee avi","yuna","najwa latif","faizal tahir",
            "jaclyn victor","shila amzah","hujan","meet uncle hussain"),
        "edm"       to listOf("david guetta","calvin harris","martin garrix","avicii",
            "alan walker","marshmello","the chainsmokers","deadmau5",
            "tiesto","daft punk","skrillex","zedd","kygo"),
        "rnb"       to listOf("frank ocean","sza","jhené aiko","daniel caesar","h.e.r.",
            "khalid","the weeknd","usher","alicia keys","john legend"),
        "indie"     to listOf("bon iver","sufjan stevens","lorde","hozier","passenger",
            "arctic monkeys","the 1975","vampire weekend","glass animals"),
        "pop"       to listOf("taylor swift","ariana grande","billie eilish","dua lipa",
            "ed sheeran","harry styles","olivia rodrigo","selena gomez"),
        "hiphop"    to listOf("drake","kendrick lamar","travis scott","eminem","post malone",
            "j. cole","kanye west","jay-z","cardi b","nicki minaj"),
        "classical" to listOf("beethoven","mozart","chopin","bach","tchaikovsky","debussy",
            "vivaldi","yiruma","max richter","ludovico einaudi"),
    )

    private val LANGUAGE_SIGNALS = mapOf(
        "ko" to listOf("blackpink","bts","twice","exo","aespa","ive","newjeans","itzy",
            "stray kids","nct","seventeen","bigbang","2ne1","mamamoo","got7",
            "shinee","monsta x","jennie","lisa","jungkook","j-hope","suga"),
        "zh" to listOf("jay chou","jolin tsai","eason chan","khalil fong","g.e.m.",
            "mayday","sodagreen","fish leong","stefanie sun","jj lin",
            "joker xue","xiao zhan","wang yibo","gem tang"),
        "ja" to listOf("yoasobi","ado","kenshi yonezu","aimer","yorushika",
            "fujii kaze","hikaru utada","one ok rock","band-maid"),
        "ms" to listOf("siti nurhaliza","zee avi","yuna","najwa latif","faizal tahir",
            "jaclyn victor","shila amzah","hujan","meet uncle hussain"),
    )

    /**
     * Build a [UserTasteProfile] from Firestore listening history.
     * Call once after login. Results cached in Firestore rl_state doc.
     */
    suspend fun build(): UserTasteProfile {
        val uid = auth.currentUser?.uid ?: return UserTasteProfile()
        return try {
            // ── 1. Load playback history (last 100 tracks) ──────────────
            val historySnap = db.collection("playbackHistory")
                .document(uid).collection("tracks")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100).get().await()

            data class HistoryEntry(val artist: String, val title: String,
                                    val mood: String, val skipped: Boolean)

            val history = historySnap.documents.mapNotNull { doc ->
                val artist  = doc.getString("artist")  ?: return@mapNotNull null
                val title   = doc.getString("title")   ?: return@mapNotNull null
                val mood    = doc.getString("mood")    ?: "neutral"
                val skipped = doc.getBoolean("skipped") ?: false
                HistoryEntry(artist, title, mood, skipped)
            }

            // ── 2. Load favorites ─────────────────────────────────────────
            val favSnap = db.collection("favorites")
                .document(uid).collection("songs")
                .limit(100).get().await()

            val likedArtists = favSnap.documents
                .mapNotNull { it.getString("artist") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            // ── 3. Count artist play frequency (non-skipped) ─────────────
            val artistPlayCount = mutableMapOf<String, Int>()
            val skippedArtistCount = mutableMapOf<String, Int>()
            val moodArtistCount = mutableMapOf<String, MutableMap<String, Int>>()

            history.forEach { entry ->
                val artist = entry.artist.trim()
                if (entry.skipped) {
                    skippedArtistCount[artist] = (skippedArtistCount[artist] ?: 0) + 1
                } else {
                    artistPlayCount[artist] = (artistPlayCount[artist] ?: 0) + 1
                    val moodMap = moodArtistCount.getOrPut(entry.mood) { mutableMapOf() }
                    moodMap[artist] = (moodMap[artist] ?: 0) + 1
                }
            }

            // ── 4. Top artists (played ≥ 2 times, not net-negative) ──────
            val topArtists = artistPlayCount.entries
                .filter { (artist, plays) ->
                    plays >= 2 &&
                            plays > (skippedArtistCount[artist] ?: 0)   // net positive
                }
                .sortedByDescending { it.value }
                .take(15)
                .map { it.key }

            val skippedArtists = skippedArtistCount.entries
                .filter { (artist, skips) ->
                    skips >= 3 && skips > (artistPlayCount[artist] ?: 0)
                }
                .map { it.key }
                .take(10)

            // ── 5. Genre weights from top artists ────────────────────────
            val genreScores = mutableMapOf<String, Float>()
            val allArtistsLower = topArtists.map { it.lowercase() }

            GENRE_SIGNALS.forEach { (genre, signals) ->
                val matches = allArtistsLower.count { a -> signals.any { s -> a.contains(s) } }
                if (matches > 0) {
                    genreScores[genre] = (matches.toFloat() / topArtists.size.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                }
            }

            // ── 6. Dominant language ──────────────────────────────────────
            val langScores = mutableMapOf<String, Int>()
            allArtistsLower.forEach { artistL ->
                LANGUAGE_SIGNALS.forEach { (lang, signals) ->
                    if (signals.any { s -> artistL.contains(s) }) {
                        langScores[lang] = (langScores[lang] ?: 0) + 1
                    }
                }
            }
            val dominantLang = langScores.entries
                .maxByOrNull { it.value }
                ?.takeIf { it.value >= 2 }
                ?.key ?: "en"

            // ── 7. Mood → top artist seeds ────────────────────────────────
            val moodArtistSeeds = moodArtistCount.mapValues { (_, artistMap) ->
                artistMap.entries.sortedByDescending { it.value }.take(3).map { it.key }
            }

            val profile = UserTasteProfile(
                topArtists         = topArtists,
                genreWeights       = genreScores,
                dominantLanguage   = dominantLang,
                likedArtists       = likedArtists.take(10),
                skippedArtists     = skippedArtists,
                moodArtistSeeds    = moodArtistSeeds,
                totalTracksAnalysed = history.size
            )

            Log.d(TAG, "Built taste profile: topArtists=${topArtists.take(5)}, " +
                    "lang=$dominantLang, genres=${genreScores.keys}")

            // ── 8. Persist into existing rl_state doc ─────────────────────
            db.collection("rl_state").document(uid).update(
                mapOf(
                    "tasteProfile" to mapOf(
                        "topArtists"       to profile.topArtists,
                        "genreWeights"     to profile.genreWeights,
                        "dominantLanguage" to profile.dominantLanguage,
                        "likedArtists"     to profile.likedArtists,
                        "skippedArtists"   to profile.skippedArtists,
                        "moodArtistSeeds"  to profile.moodArtistSeeds,
                        "totalAnalysed"    to profile.totalTracksAnalysed
                    )
                )
            ).await()

            profile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build taste profile", e)
            UserTasteProfile()
        }
    }

    /**
     * Load a previously saved profile from Firestore (no re-analysis needed).
     */
    suspend fun load(): UserTasteProfile {
        val uid = auth.currentUser?.uid ?: return UserTasteProfile()
        return try {
            val doc = db.collection("rl_state").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val raw = doc.get("tasteProfile") as? Map<String, Any>
                ?: return UserTasteProfile()

            @Suppress("UNCHECKED_CAST")
            UserTasteProfile(
                topArtists         = (raw["topArtists"]     as? List<String>) ?: emptyList(),
                genreWeights       = ((raw["genreWeights"]  as? Map<String, Any>)
                    ?.mapValues { (_, v) -> (v as? Double)?.toFloat() ?: 0f }) ?: emptyMap(),
                dominantLanguage   = (raw["dominantLanguage"] as? String) ?: "en",
                likedArtists       = (raw["likedArtists"]   as? List<String>) ?: emptyList(),
                skippedArtists     = (raw["skippedArtists"] as? List<String>) ?: emptyList(),
                moodArtistSeeds    = ((raw["moodArtistSeeds"] as? Map<String, Any>)
                    ?.mapValues { (_, v) -> (v as? List<String>) ?: emptyList() }) ?: emptyMap(),
                totalTracksAnalysed = (raw["totalAnalysed"] as? Long)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load taste profile", e)
            UserTasteProfile()
        }
    }
}