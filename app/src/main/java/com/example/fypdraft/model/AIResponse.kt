package com.example.fypdraft.model

/**
 * AIResponse — result produced by MusicPlayerViewModel.processUserMessageWithAI()
 * using the on-device TFLite recommendation engine.
 *
 * Kept in the model package so both MusicPlayerViewModel and any view that needs
 * it can import from the same place without a circular view→model dependency.
 */
data class AIResponse(
    val intent: String,
    val intentConfidence: Int,
    val intentEmoji: String,
    val currentSongEmotion: String?,
    val emotionConfidence: Int,
    val emotionEmoji: String?,
    val explanation: String,
    val overallConfidence: Int,
    val suggestedAction: String,
    val tips: List<String>
)