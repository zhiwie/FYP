package com.example.fypdraft.ml

import android.util.Log
import com.example.fypdraft.model.MoodResult
import com.example.fypdraft.model.Track
import com.example.fypdraft.model.TrackMood

/**
 * MoodPipeline
 *
 * Single entry point for all mood tagging. Encodes the decision flow:
 *
 *   1. MetadataEmotionTagger (instant, offline)
 *      └─ confidence ≥ 0.65 AND mood ≠ NEUTRAL  →  use metadata result
 *      └─ otherwise  →  GptMoodTagger (async, ~500 ms)
 *         └─ confidence ≥ 0.75 AND mood ≠ NEUTRAL  →  use GPT result
 *         └─ otherwise  →  NEUTRAL
 *
 * [tagSync]  — synchronous, metadata only. Use at parse time (no coroutine needed).
 * [tagFull]  — suspend, runs GPT fallback if needed. Use in ViewModel/coroutine.
 */
object MoodPipeline {

    private const val TAG = "MoodPipeline"

    /** Threshold below which GPT fallback is triggered. */
    const val METADATA_CONFIDENCE_THRESHOLD = 0.65

    /** Threshold below which GPT result is rejected. */
    const val GPT_CONFIDENCE_THRESHOLD = 0.75

    // ─────────────────────────────────────────────────────────────────
    // Synchronous — metadata only
    // Use in parseTracks() where coroutines are not available.
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tag instantly using metadata. Never calls GPT.
     * Returns a [MoodResult] — may be NEUTRAL if confidence is low.
     */
    fun tagSync(name: String, artist: String, album: String = ""): MoodResult =
        MetadataEmotionTagger.tag(name, artist, album)

    /**
     * Apply tagSync to a [Track] and return a new copy with mood fields set.
     */
    fun tagSync(track: Track): Track =
        track.withMood(tagSync(track.name, track.artist, track.album))

    // ─────────────────────────────────────────────────────────────────
    // Full pipeline — metadata + optional GPT fallback
    // Use in ViewModel when a track is loaded for playback.
    // ─────────────────────────────────────────────────────────────────

    /**
     * Full pipeline with GPT fallback.
     *
     * Decision flow:
     *   metadata.confidence ≥ 0.65 AND mood ≠ NEUTRAL  →  return metadata
     *   else call GPT:
     *     gpt.confidence ≥ 0.75 AND mood ≠ NEUTRAL     →  return gpt
     *     else                                           →  NEUTRAL
     *
     * @param track  The track to tag.
     * @param forceGpt  Skip metadata and go straight to GPT (testing only).
     */
    suspend fun tagFull(track: Track, forceGpt: Boolean = false): MoodResult {
        // Fast path: track already has a confident mood from parseTracks()
        if (!forceGpt
            && track.mood != TrackMood.NEUTRAL
            && track.moodConfidence >= METADATA_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Using existing mood: ${track.mood.emoji} ${track.mood.label} " +
                    "(${(track.moodConfidence * 100).toInt()}%) [${track.moodSource}] | '${track.name}'")
            return MoodResult(
                mood       = track.mood,
                confidence = track.moodConfidence,
                source     = track.moodSource,
                reason     = track.moodReason
            )
        }

        // Metadata stage
        val metadata = if (forceGpt) MoodResult.NEUTRAL
        else MetadataEmotionTagger.tag(track.name, track.artist, track.album)

        if (metadata.mood != TrackMood.NEUTRAL
            && metadata.confidence >= METADATA_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Metadata sufficient: ${metadata.mood.emoji} " +
                    "(${(metadata.confidence * 100).toInt()}%) | '${track.name}'")
            return metadata
        }

        // GPT fallback
        Log.d(TAG, "Metadata confidence ${(metadata.confidence * 100).toInt()}% < threshold " +
                "— calling GPT for '${track.name}' by '${track.artist}'")

        val gptResult = GptMoodTagger.tag(track.name, track.artist, track.album)

        return when {
            gptResult.mood != TrackMood.NEUTRAL
                    && gptResult.confidence >= GPT_CONFIDENCE_THRESHOLD -> {
                Log.d(TAG, "GPT used: ${gptResult.mood.emoji} " +
                        "(${(gptResult.confidence * 100).toInt()}%) | '${track.name}'")
                gptResult
            }
            else -> {
                Log.d(TAG, "Both stages low confidence → NEUTRAL | '${track.name}'")
                MoodResult.NEUTRAL.copy(
                    reason = "Metadata conf=${(metadata.confidence*100).toInt()}%, " +
                            "GPT conf=${(gptResult.confidence*100).toInt()}% — both below threshold"
                )
            }
        }
    }
}