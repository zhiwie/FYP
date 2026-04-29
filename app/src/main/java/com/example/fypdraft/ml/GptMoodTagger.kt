package com.example.fypdraft.ml

import android.util.Log
import com.example.fypdraft.core.config.AppConfig
import com.example.fypdraft.core.network.NetworkModule
import com.example.fypdraft.data.api.ChatGPTRequest
import com.example.fypdraft.data.api.OpenAIMessage
import com.example.fypdraft.model.MoodResult
import com.example.fypdraft.model.TrackMood

/**
 * GptMoodTagger
 *
 * GPT fallback for mood tagging when MetadataEmotionTagger confidence < 0.65
 * or returns NEUTRAL.
 *
 * Design principles:
 *  • Called ONLY when metadata confidence is insufficient — not for every song.
 *  • Uses GPT-3.5-turbo with a short, structured prompt (< 150 tokens input).
 *  • Expects a strict JSON response to avoid parsing fragility.
 *  • Returns [MoodResult] with source = "gpt" and the raw GPT reasoning as reason.
 *  • Returns [MoodResult.NEUTRAL] on any failure — never throws.
 *
 * Cost estimate: ~0.0002 USD per call on gpt-3.5-turbo.
 * In practice fires for < 20% of tracks (those the artist/keyword tagger misses).
 */
object GptMoodTagger {

    private const val TAG = "GptMoodTagger"

    // Minimum confidence to accept a GPT result
    private const val GPT_MIN_CONFIDENCE = 0.75

    private val SYSTEM_PROMPT = """
        You are a music mood classifier. You will receive a song title, artist, and album.
        Reply ONLY with a JSON object — no markdown, no explanation.
        
        JSON format:
        {
          "mood": "<one of: happy, sad, calm, energetic, neutral>",
          "confidence": <0.0 to 1.0>,
          "reason": "<one sentence explaining the mood>"
        }
        
        Mood definitions:
        - happy: positive, joyful, upbeat, celebratory
        - sad: melancholic, sorrowful, heartbroken, lonely
        - calm: peaceful, relaxed, gentle, acoustic, ambient
        - energetic: high-energy, intense, hype, dance, workout
        - neutral: ambiguous or insufficient information
        
        Consider the song title language — Chinese, Korean, Japanese, Malay titles are valid.
        Be decisive. Only use neutral if you genuinely cannot tell.
    """.trimIndent()

    /**
     * Ask GPT to classify the mood of a track.
     *
     * This is a suspend function — call from a coroutine (e.g. IO dispatcher).
     *
     * @param name    Song title
     * @param artist  Artist name
     * @param album   Album name (optional but improves accuracy)
     * @return [MoodResult] with source="gpt", or [MoodResult.NEUTRAL] on failure.
     */
    suspend fun tag(name: String, artist: String, album: String = ""): MoodResult {
        val albumPart = if (album.isNotBlank()) ", album: \"$album\"" else ""
        val userPrompt = "Song: \"$name\", Artist: \"$artist\"$albumPart"

        return try {
            val start    = System.currentTimeMillis()
            val response = NetworkModule.chatGPTApi.sendMessage(
                authorization = "Bearer ${AppConfig.OPENAI_API_KEY}",
                request = ChatGPTRequest(
                    model     = "gpt-3.5-turbo",
                    maxTokens = 120,
                    temperature = 0.2,          // low temp → consistent, deterministic
                    messages  = listOf(
                        OpenAIMessage(role = "system", content = SYSTEM_PROMPT),
                        OpenAIMessage(role = "user",   content = userPrompt)
                    )
                )
            )
            val elapsed = System.currentTimeMillis() - start
            Log.d(TAG, "GPT mood call: ${elapsed}ms | '$name' by '$artist'")

            if (!response.isSuccessful) {
                Log.w(TAG, "GPT error ${response.code()} — returning NEUTRAL")
                return MoodResult.NEUTRAL.copy(reason = "GPT API error ${response.code()}")
            }

            val raw = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
                ?: return MoodResult.NEUTRAL.copy(reason = "GPT returned empty response")

            parseGptResponse(raw, name, artist)

        } catch (e: Exception) {
            Log.e(TAG, "GptMoodTagger.tag failed for '$name' by '$artist'", e)
            MoodResult.NEUTRAL.copy(reason = "GPT call failed: ${e.message}")
        }
    }

    // ── Response parser ──────────────────────────────────────────────

    private fun parseGptResponse(raw: String, name: String, artist: String): MoodResult {
        return try {
            // Strip any accidental markdown fences
            val json = raw.replace("```json", "").replace("```", "").trim()

            // Simple key extraction — avoids a full JSON library dependency
            val moodStr  = extractJsonString(json, "mood")   ?: "neutral"
            val confStr  = extractJsonDouble(json, "confidence") ?: 0.5
            val reason   = extractJsonString(json, "reason") ?: "GPT classification"

            val mood = TrackMood.fromLabel(moodStr)
            val conf = confStr.coerceIn(0.0, 1.0)

            if (mood == TrackMood.NEUTRAL || conf < GPT_MIN_CONFIDENCE) {
                Log.d(TAG, "GPT result below threshold: $moodStr conf=$conf → NEUTRAL")
                return MoodResult.NEUTRAL.copy(
                    reason = "GPT confidence ${"%.0f".format(conf * 100)}% below threshold: $reason"
                )
            }

            MoodResult(
                mood       = mood,
                confidence = conf,
                source     = "gpt",
                reason     = reason
            ).also {
                Log.d(TAG, "GPT → ${it.mood.emoji} ${it.mood.label} " +
                        "(${(it.confidence * 100).toInt()}%) | '$name' by '$artist' | $reason")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GPT response: $raw", e)
            MoodResult.NEUTRAL.copy(reason = "GPT parse error")
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractJsonDouble(json: String, key: String): Double? {
        val pattern = Regex(""""$key"\s*:\s*([\d.]+)""")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}