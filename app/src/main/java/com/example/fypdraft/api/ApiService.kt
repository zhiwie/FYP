package com.example.fypdraft.api

import com.example.fypdraft.api.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("/")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("api/chat")
    suspend fun sendChatMessage(
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @POST("api/emotion/text")
    suspend fun detectEmotionFromText(
        @Body request: EmotionTextRequest
    ): Response<EmotionResponse>

    @Multipart
    @POST("api/emotion/audio")
    suspend fun detectEmotionFromAudio(
        @Part audio: MultipartBody.Part
    ): Response<EmotionResponse>

    @POST("api/recommend")
    suspend fun getRecommendations(
        @Body request: RecommendRequest
    ): Response<RecommendationResponse>

    @POST("api/user/profile")
    suspend fun updateUserProfile(
        @Body request: ProfileRequest
    ): Response<ProfileResponse>
}