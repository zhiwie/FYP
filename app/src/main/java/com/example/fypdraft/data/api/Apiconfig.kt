package com.example.fypdraft.data.api

import com.example.fypdraft.BuildConfig  // ← fixes "unresolved reference: BuildConfig"

object ApiConfig {

    // ── Existing Local Backend ────────────────────────────────────────────────
    const val BASE_URL = "http://192.168.6.96:5000"

    const val HEALTH_ENDPOINT  = "/health"
    const val ANALYZE_ENDPOINT = "/api/analyze"
    val HEALTH_URL  = "$BASE_URL$HEALTH_ENDPOINT"
    val ANALYZE_URL = "$BASE_URL$ANALYZE_ENDPOINT"

    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT    = 30L
    const val WRITE_TIMEOUT   = 30L

    // ── OpenAI ────────────────────────────────────────────────────────────────
    const val OPENAI_BASE_URL = "https://api.openai.com/"

    val OPENAI_API_KEY: String
        get() = BuildConfig.OPENAI_API_KEY

    // ── YouTube Data API ──────────────────────────────────────────────────────
    val YOUTUBE_API_KEY: String
        get() = BuildConfig.YOUTUBE_API_KEY

    // ── ChatGPT system prompt ─────────────────────────────────────────────────
    val OPENAI_SYSTEM_PROMPT = """
        You are MoodSync, an empathetic music recommendation assistant.
        
        When the user describes their mood or feelings:
        1. Acknowledge their mood warmly in 1-2 sentences.
        2. Recommend exactly 3-5 songs that match their mood.
        3. Use EXACTLY this format for each song — do not change the emoji markers:
        
        🎵 SONG: Artist Name - Song Title
        💭 REASON: One sentence explaining why this matches their mood
        
        Rules:
        - Keep intro text short (2-3 sentences max).
        - Always include the 🎵 and 💭 emoji markers exactly as shown.
        - Recommend real, well-known songs available on streaming platforms.
        - Vary genres unless the user specifies a preference.
        - Be warm and conversational, not robotic.
    """.trimIndent()
}