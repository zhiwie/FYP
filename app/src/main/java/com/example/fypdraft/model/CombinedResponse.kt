package com.example.fypdraft.model

/**
 * Data class to hold the combined response from all three AI models
 */
data class CombinedResponse(
    val emotion: String,
    val emotionConfidence: Float,
    val musicRecommendations: List<MusicRecommendation>,
    val conversationalReply: String,
    val explanation: String
)

/**
 * Data class for individual music recommendations
 */
data class MusicRecommendation(
    val songTitle: String,
    val artist: String,
    val reason: String,
    val mood: String
)