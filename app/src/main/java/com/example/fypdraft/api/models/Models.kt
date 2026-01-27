package com.example.fypdraft.api.models

// Health Check
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String
)

// Chat
data class ChatRequest(
    val message: String,
    val user_id: String
)

data class ChatResponse(
    val success: Boolean,
    val response: String,
    val user_id: String,
    val timestamp: String
)

// Emotion Detection
data class EmotionTextRequest(
    val text: String
)

data class EmotionResponse(
    val success: Boolean,
    val emotions: Map<String, Double>,
    val dominant_emotion: String,
    val confidence: Double,
    val timestamp: String
)

// Music Recommendations
data class RecommendRequest(
    val user_id: String,
    val emotion: String,
    val listening_history: List<String>,
    val top_k: Int = 10
)

data class Song(
    val song_id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val mood: String,
    val score: Double,
    val explanation: String
)

data class RecommendationResponse(
    val success: Boolean,
    val user_id: String,
    val emotion: String,
    val recommendations: List<Song>,
    val timestamp: String
)

// User Profile
data class ProfileRequest(
    val user_id: String,
    val song_id: String,
    val rating: Int,
    val emotion: String
)

data class ProfileResponse(
    val success: Boolean,
    val message: String,
    val user_id: String,
    val timestamp: String
)