package com.example.fypdraft.model

class IntegratedMusicViewModel : ViewModel() {
    // Initialize all three models
    private val conversationalModel = ConversationalModel()
    private val explanationModel = ExplanationModel()
    private val emotionModel = EmotionDetectionModel()

    // Combine results from all models
    fun processUserInput(input: String): CombinedResponse {
        val emotion = emotionModel.detectEmotion(input)
        val recommendations = explanationModel.getRecommendations(emotion)
        val conversationResponse = conversationalModel.generateResponse(input, emotion)

        return CombinedResponse(
            emotion = emotion,
            recommendations = recommendations,
            conversationalReply = conversationResponse
        )
    }
}