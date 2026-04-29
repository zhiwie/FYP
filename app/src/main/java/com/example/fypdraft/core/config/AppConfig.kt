package com.example.fypdraft.core.config

import com.example.fypdraft.BuildConfig

/**
 * Single source of truth for all configuration.
 * API keys come from BuildConfig (set in local.properties / CI secrets).
 */
object AppConfig {

    // ── OpenAI ─────────────────────────────────────────────
    const val OPENAI_BASE_URL = "https://api.openai.com/"
    val OPENAI_API_KEY: String get() = BuildConfig.OPENAI_API_KEY

    // ── Deezer (no auth needed — public API) ──────────────────────────
    const val DEEZER_BASE_URL = "https://api.deezer.com/"

    // ── Spotify ────────────────────────────────────────────
    const val SPOTIFY_CLIENT_ID = "667c083092c747f1bef171ed8aacb46e"
    const val SPOTIFY_REDIRECT_URI = "fypdraft://callback"

    // ── Timeouts ───────────────────────────────────────────
    const val CONNECT_TIMEOUT_SECS = 30L
    const val READ_TIMEOUT_SECS = 30L
    const val WRITE_TIMEOUT_SECS = 30L

    // ── ChatGPT System Prompt ──────────────────────────────
    val CHATGPT_SYSTEM_PROMPT = """
        You are MoodSync, an empathetic music recommendation assistant.
        
        When the user describes their mood or feelings:
        1. Acknowledge their mood warmly in 1-2 sentences.
        2. Recommend exactly 6 songs or above that match their mood.
        3. Use EXACTLY this format for each song:
        
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