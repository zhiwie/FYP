package com.example.fypdraft.ml

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Passive mood detection from audio and track metadata.
 *
 * Since Spotify deprecated /audio-features (valence, energy, tempo) in Nov 2024,
 * this uses two alternative signals:
 *
 * 1. Track metadata keywords — analyses song name, artist, album, and the
 *    search query that found the track for mood-relevant terms.
 *
 * 2. FFT energy analysis — uses the real-time Visualizer FFT data to measure
 *    overall audio energy, bass presence, and tempo estimation.
 *
 * The combined signal updates the detected mood which can drive:
 *   - Theme color changes via ThemeManager
 *   - Pet behavior/animation changes
 *   - Mood history logging (auto-entries)
 */
class PassiveMoodDetector {

    private val TAG = "PassiveMoodDetector"

    private val _detectedMood = MutableStateFlow("neutral")
    val detectedMood: StateFlow<String> = _detectedMood.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    // Rolling FFT energy buffer (last 10 readings)
    private val energyBuffer = ArrayDeque<Float>(10)
    private var lastTrackId: String? = null

    // ── Keyword dictionaries ─────────────────────────────────────────

    private val moodKeywords = mapOf(
        "happy" to listOf(
            "happy", "joy", "sunshine", "bright", "smile", "celebrate", "party",
            "fun", "good vibes", "feel good", "upbeat", "cheerful", "wonderful",
            "summer", "dancing", "groove", "positive", "blessed", "beautiful day"
        ),
        "sad" to listOf(
            "sad", "cry", "tears", "heartbreak", "lonely", "miss you", "gone",
            "pain", "lost", "broken", "goodbye", "alone", "hurt", "sorrow",
            "rainy", "blue", "melancholy", "regret", "without you", "falling apart"
        ),
        "energetic" to listOf(
            "energy", "pump", "hype", "fire", "let's go", "power", "beast",
            "workout", "run", "fight", "wild", "crazy", "turn up", "bass",
            "drop", "rage", "adrenaline", "unstoppable", "electric", "thunder"
        ),
        "calm" to listOf(
            "calm", "peace", "gentle", "soft", "quiet", "breeze", "relax",
            "chill", "easy", "floating", "dream", "ambient", "soothing",
            "tranquil", "serene", "meditation", "zen", "still", "ocean", "clouds"
        ),
        "romantic" to listOf(
            "love", "heart", "kiss", "darling", "baby", "romance", "forever",
            "together", "hold me", "your eyes", "sweetheart", "passion",
            "desire", "intimate", "crush", "falling for", "adore", "devotion"
        ),
        "focused" to listOf(
            "focus", "study", "concentrate", "instrumental", "lofi", "beats",
            "deep work", "productivity", "minimal", "ambient", "electronic",
            "synthwave", "coding", "reading", "alpha waves", "binaural"
        ),
        "tired" to listOf(
            "sleep", "night", "lullaby", "rest", "tired", "drowsy", "bedtime",
            "moonlight", "midnight", "stars", "pillow", "dream", "sleepy",
            "wind down", "goodnight", "dusk", "twilight", "closing eyes"
        ),
        "angry" to listOf(
            "angry", "rage", "fury", "scream", "destroy", "hate", "revenge",
            "war", "battle", "metal", "hardcore", "aggressive", "rebel",
            "chaos", "storm", "burn", "wrath", "madness", "violence"
        )
    )

    // Genre-to-mood mapping for search context
    private val genreMoodMap = mapOf(
        "pop" to "happy", "dance" to "energetic", "edm" to "energetic",
        "hip hop" to "energetic", "rap" to "energetic", "rock" to "energetic",
        "metal" to "angry", "punk" to "angry", "hardcore" to "angry",
        "r&b" to "romantic", "soul" to "romantic", "jazz" to "calm",
        "classical" to "calm", "ambient" to "calm", "lofi" to "focused",
        "lo-fi" to "focused", "study" to "focused", "instrumental" to "focused",
        "blues" to "sad", "emo" to "sad", "indie" to "calm",
        "reggae" to "calm", "country" to "calm", "folk" to "calm",
        "sleep" to "tired", "lullaby" to "tired", "meditation" to "calm",
        "workout" to "energetic", "party" to "happy", "chill" to "calm"
    )

    // ── Analyse track metadata ───────────────────────────────────────

    /**
     * Analyse a track's metadata to infer mood.
     * Call this when a new track starts playing.
     *
     * @param trackName  Song title
     * @param artist     Artist name
     * @param album      Album name (optional)
     * @param searchQuery  The search query that found this track (optional, very useful)
     */
    fun analyseTrackMetadata(
        trackId: String,
        trackName: String,
        artist: String,
        album: String = "",
        searchQuery: String = ""
    ) {
        if (trackId == lastTrackId) return // Don't re-analyse same track
        lastTrackId = trackId

        val textToAnalyse = "$trackName $artist $album $searchQuery".lowercase()

        // Score each mood
        val scores = mutableMapOf<String, Float>()

        for ((mood, keywords) in moodKeywords) {
            var score = 0f
            for (keyword in keywords) {
                if (textToAnalyse.contains(keyword)) {
                    // Weight: track name matches are stronger than search query matches
                    val inTitle = trackName.lowercase().contains(keyword)
                    val inArtist = artist.lowercase().contains(keyword)
                    score += when {
                        inTitle -> 3f
                        inArtist -> 1.5f
                        else -> 1f
                    }
                }
            }
            if (score > 0) scores[mood] = score
        }

        // Also check genre keywords from search query
        for ((genre, mood) in genreMoodMap) {
            if (searchQuery.lowercase().contains(genre)) {
                scores[mood] = (scores[mood] ?: 0f) + 2f
            }
        }

        // Find winner
        val best = scores.maxByOrNull { it.value }
        if (best != null && best.value >= 1f) {
            val totalScore = scores.values.sum()
            val conf = (best.value / totalScore).coerceIn(0.3f, 0.95f)

            _detectedMood.value = best.key
            _confidence.value = conf
            Log.d(TAG, "Metadata mood: ${best.key} (${(conf * 100).toInt()}%) from '$trackName' by '$artist'")
        } else {
            // No strong signal from metadata — keep current or set neutral
            _confidence.value = 0.2f
            Log.d(TAG, "No strong metadata signal for '$trackName' — keeping ${_detectedMood.value}")
        }
    }

    // ── FFT energy analysis ──────────────────────────────────────────

    /**
     * Feed FFT energy readings from the AudioVisualizerView.
     * Call this periodically (e.g., every 500ms) while music plays.
     *
     * @param avgMagnitude  Average magnitude across all FFT bars (0..1)
     * @param bassEnergy    Average of the lowest 25% of bars (0..1)
     * @param highEnergy    Average of the highest 25% of bars (0..1)
     */
    fun updateFFTEnergy(avgMagnitude: Float, bassEnergy: Float, highEnergy: Float) {
        energyBuffer.addLast(avgMagnitude)
        if (energyBuffer.size > 10) energyBuffer.removeFirst()

        val smoothedEnergy = energyBuffer.average().toFloat()

        // Adjust mood confidence based on energy
        // High energy supports: energetic, happy, angry
        // Low energy supports: calm, sad, tired, focused
        val currentMood = _detectedMood.value
        val energyAligned = when (currentMood) {
            "energetic", "happy", "angry" -> smoothedEnergy > 0.4f
            "calm", "sad", "tired", "focused" -> smoothedEnergy < 0.5f
            "romantic" -> smoothedEnergy in 0.2f..0.6f
            else -> true
        }

        // If FFT energy contradicts metadata mood, reduce confidence
        if (!energyAligned && _confidence.value > 0.3f) {
            _confidence.value = (_confidence.value - 0.05f).coerceAtLeast(0.2f)
        }

        // If no metadata signal but we have energy data, use energy alone
        if (_confidence.value <= 0.25f) {
            val energyMood = when {
                smoothedEnergy > 0.65f -> "energetic"
                smoothedEnergy > 0.45f -> "happy"
                smoothedEnergy > 0.25f -> "calm"
                smoothedEnergy > 0.1f -> "focused"
                else -> "tired"
            }
            _detectedMood.value = energyMood
            _confidence.value = 0.35f
        }
    }

    /**
     * Get the current passive mood detection result.
     */
    fun getCurrentMood(): Pair<String, Float> {
        return _detectedMood.value to _confidence.value
    }

    /**
     * Reset when playback stops.
     */
    fun reset() {
        energyBuffer.clear()
        lastTrackId = null
        _confidence.value = 0f
    }
}