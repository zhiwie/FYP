package com.example.fypdraft.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.model.CombinedResponse
import com.example.fypdraft.model.MusicRecommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

/**
 * ViewModel that communicates with the backend server
 * instead of running models locally
 */
class IntegratedMusicViewModel : ViewModel() {

    // Backend server URL - CHANGE THIS to your server's IP address
    private val BASE_URL = "http://192.168.68.139:5000" // For Android Emulator
    // Use "http://YOUR_COMPUTER_IP:5000" for real device
    // Example: "http://192.168.1.100:5000"

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // State management
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentResponse = MutableStateFlow<CombinedResponse?>(null)
    val currentResponse: StateFlow<CombinedResponse?> = _currentResponse.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Conversation history
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    /**
     * Process user input by calling the backend API
     */
    fun processUserInput(userInput: String) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null

                // Make API call to backend
                val response = analyzeText(userInput)

                // Update state with response
                _currentResponse.value = response

                // Add to conversation history
                conversationHistory.add(Pair(userInput, response.conversationalReply))

            } catch (e: IOException) {
                _error.value = "Network error: Cannot connect to server. Please check your connection."
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Call the backend /api/analyze endpoint
     */
    private suspend fun analyzeText(text: String): CombinedResponse {
        return withContext(Dispatchers.IO) {
            // Create JSON request body
            val jsonBody = JSONObject().apply {
                put("text", text)
            }

            val requestBody = jsonBody.toString().toRequestBody(JSON)

            // Build request
            val request = Request.Builder()
                .url("$BASE_URL/api/analyze")
                .post(requestBody)
                .build()

            // Execute request
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Server error: ${response.code}")
            }

            // Parse response
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from server")

            parseCombinedResponse(responseBody)
        }
    }

    /**
     * Parse JSON response from backend into CombinedResponse object
     */
    private fun parseCombinedResponse(jsonString: String): CombinedResponse {
        val json = JSONObject(jsonString)

        // Parse music recommendations
        val recommendationsArray = json.getJSONArray("musicRecommendations")
        val recommendations = mutableListOf<MusicRecommendation>()

        for (i in 0 until recommendationsArray.length()) {
            val recJson = recommendationsArray.getJSONObject(i)
            recommendations.add(
                MusicRecommendation(
                    songTitle = recJson.getString("songTitle"),
                    artist = recJson.getString("artist"),
                    reason = recJson.getString("reason"),
                    mood = recJson.getString("mood")
                )
            )
        }

        return CombinedResponse(
            emotion = json.getString("emotion"),
            emotionConfidence = json.getDouble("emotionConfidence").toFloat(),
            musicRecommendations = recommendations,
            conversationalReply = json.getString("conversationalReply"),
            explanation = json.getString("explanation")
        )
    }

    /**
     * Check if backend server is healthy
     */
    suspend fun checkServerHealth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/health")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get only emotion detection (separate endpoint)
     */
    suspend fun detectEmotionOnly(text: String): Pair<String, Float>? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("text", text)
                }

                val requestBody = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("$BASE_URL/api/emotion")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val json = JSONObject(responseBody)
                    Pair(
                        json.getString("emotion"),
                        json.getDouble("confidence").toFloat()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get recommendations for a specific emotion
     */
    suspend fun getRecommendationsForEmotion(emotion: String): List<MusicRecommendation>? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("emotion", emotion)
                    put("context", "")
                }

                val requestBody = jsonBody.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("$BASE_URL/api/recommend")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@withContext null
                    val json = JSONObject(responseBody)
                    val recsArray = json.getJSONArray("recommendations")

                    val recommendations = mutableListOf<MusicRecommendation>()
                    for (i in 0 until recsArray.length()) {
                        val recJson = recsArray.getJSONObject(i)
                        recommendations.add(
                            MusicRecommendation(
                                songTitle = recJson.getString("songTitle"),
                                artist = recJson.getString("artist"),
                                reason = recJson.getString("reason"),
                                mood = recJson.getString("mood")
                            )
                        )
                    }
                    recommendations
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get conversation history
     */
    fun getConversationHistory(): List<Pair<String, String>> {
        return conversationHistory.toList()
    }

    /**
     * Clear conversation history and state
     */
    fun clearHistory() {
        conversationHistory.clear()
        _currentResponse.value = null
        _error.value = null
    }

    /**
     * Update base URL (for production deployment)
     */
    fun updateBaseUrl(newUrl: String) {
        // You can add this functionality to switch between dev/prod servers
    }
}