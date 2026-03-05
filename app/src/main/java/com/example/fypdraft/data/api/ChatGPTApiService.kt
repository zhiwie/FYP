package com.example.fypdraft.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatGPTApiService {
    @POST("v1/chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatGPTRequest
    ): Response<ChatGPTResponse>
}

data class ChatGPTRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<OpenAIMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 800,
    val temperature: Double = 0.7
)

data class OpenAIMessage(
    val role: String,   // "system" | "user" | "assistant"
    val content: String
)

data class ChatGPTResponse(
    val id: String?,
    val choices: List<OpenAIChoice>?,
    val error: OpenAIError?
)

data class OpenAIChoice(
    val message: OpenAIMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class OpenAIError(
    val message: String,
    val type: String?
)