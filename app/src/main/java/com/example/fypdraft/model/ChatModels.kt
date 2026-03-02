package com.example.fypdraft.model

import java.util.UUID

// ── Song recommendation (returned by ChatGPT, then enriched with YouTube ID) ──

data class SongRecommendation(
    val artist: String,
    val title: String,
    val reason: String = "",
    // Filled after YouTube lookup via existing YouTubeMusicRepository
    val youtubeVideoId: String? = null
)

// ── Chat message ──────────────────────────────────────────────────────────────

enum class MessageSender { USER, AI }

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val songs: List<SongRecommendation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

// ── Typing indicator states ───────────────────────────────────────────────────

sealed class TypingState {
    object Idle : TypingState()
    object Thinking : TypingState()
    object FindingSongs : TypingState()
    object FilteringResponse : TypingState()
}

val TypingState.label: String
    get() = when (this) {
        TypingState.Idle -> ""
        TypingState.Thinking -> "🤔 Thinking..."
        TypingState.FindingSongs -> "🎵 Finding songs for you..."
        TypingState.FilteringResponse -> "✨ Getting recommendations..."
    }

// ── Screen-level UI state ─────────────────────────────────────────────────────

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    object NetworkError : ChatUiState()
    object RateLimitExceeded : ChatUiState()
}