package com.example.fypdraft.model

import java.util.UUID

data class SongRecommendation(
    val artist: String,
    val title: String,
    val reason: String = "",
    val youtubeVideoId: String? = null
)

enum class MessageSender { USER, AI }

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val songs: List<SongRecommendation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

sealed class TypingState(val label: String) {
    object Idle : TypingState("")
    object Thinking : TypingState("🤔 Thinking...")
    object FindingSongs : TypingState("🎵 Finding songs...")
    object FilteringResponse : TypingState("✨ Getting recommendations...")
}

sealed class ChatUiState {
    object Idle : ChatUiState()
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    object NetworkError : ChatUiState()
    object RateLimitExceeded : ChatUiState()
}