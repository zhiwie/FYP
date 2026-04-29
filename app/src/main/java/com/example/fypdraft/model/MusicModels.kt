package com.example.fypdraft.model

/**
 * Core music models — single source of truth.
 *
 * [Track] carries full mood provenance: mood tag, confidence, source,
 * and a human-readable reason. All fields are assigned at parse time by
 * [MetadataEmotionTagger] / [GptMoodTagger]. No audio download or TFLite
 * model is involved at any point.
 */

// ── Mood tag enum ────────────────────────────────────────────────────────────

enum class TrackMood(val label: String, val emoji: String) {
    HAPPY    ("happy",     "😊"),
    SAD      ("sad",       "😢"),
    CALM     ("calm",      "😌"),
    ENERGETIC("energetic", "⚡"),
    NEUTRAL  ("neutral",   "🎵");

    companion object {
        fun fromLabel(label: String): TrackMood =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: NEUTRAL
    }
}

// ── Mood result — carries full provenance ────────────────────────────────────

/**
 * Full result returned by both [MetadataEmotionTagger] and [GptMoodTagger].
 *
 * @param mood        The assigned mood.
 * @param confidence  0.0–1.0. Used to decide whether to try the GPT fallback.
 * @param source      One of: "artist", "keyword", "artist+keyword", "gpt", "neutral".
 * @param reason      Human-readable explanation shown in the UI debug line.
 */
data class MoodResult(
    val mood:       TrackMood,
    val confidence: Double,
    val source:     String,
    val reason:     String
) {
    companion object {
        val NEUTRAL = MoodResult(
            mood       = TrackMood.NEUTRAL,
            confidence = 0.0,
            source     = "neutral",
            reason     = "No confident match from metadata or GPT"
        )
    }
}

// ── Track ────────────────────────────────────────────────────────────────────

data class Track(
    val id:             String,
    val name:           String,
    val artist:         String,
    val album:          String  = "",
    val albumArtUrl:    String,
    val previewUrl:     String?,     // kept for MediaPlayer playback only
    val durationMs:     Long,
    val spotifyUri:     String? = null,
    // ── Mood provenance ───────────────────────────────────────────────
    val mood:           TrackMood = TrackMood.NEUTRAL,
    val moodConfidence: Double    = 0.0,
    val moodSource:     String    = "neutral",
    val moodReason:     String    = ""
) {
    /** Convenience — true when a real mood was assigned with meaningful confidence. */
    val hasMood: Boolean get() = mood != TrackMood.NEUTRAL && moodConfidence > 0.0

    /** Apply a [MoodResult] and return a new copy of this track. */
    fun withMood(result: MoodResult) = copy(
        mood           = result.mood,
        moodConfidence = result.confidence,
        moodSource     = result.source,
        moodReason     = result.reason
    )
}

// ── Playlist / PlayerState ───────────────────────────────────────────────────

data class Playlist(
    val id:          String,
    val name:        String,
    val description: String,
    val tracks:      List<Track>,
    val imageUrl:    String?
)

data class PlayerState(
    val currentTrack:        Track?      = null,
    val isPlaying:           Boolean     = false,
    val progress:            Float       = 0f,
    val currentPosition:     Long        = 0L,
    val duration:            Long        = 0L,
    val playlist:            List<Track> = emptyList(),
    val currentIndex:        Int         = 0,
    val usingDeezerFallback: Boolean     = false
)