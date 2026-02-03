package com.example.fypdraft.model

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.YouTubeMusicRepository
import com.example.fypdraft.ml.*
import com.example.fypdraft.view.AIResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified MusicPlayerViewModel
 * Combines music playback with AI emotion detection and recommendations
 */
class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerViewModel"

    // ==========================================
    // Music Player Components
    // ==========================================

    private val youtubeRepository = YouTubeMusicRepository()
    private var mediaPlayer: MediaPlayer? = null

    // ==========================================
    // AI/ML Components
    // ==========================================

    private var recommendationEngine: MusicRecommendationEngine? = null

    private val _mlReady = MutableStateFlow(false)
    val mlReady: StateFlow<Boolean> = _mlReady.asStateFlow()

    // ==========================================
    // Player State
    // ==========================================

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // ==========================================
    // AI State
    // ==========================================

    private val _currentSongEmotion = MutableStateFlow<EmotionResult?>(null)
    val currentSongEmotion: StateFlow<EmotionResult?> = _currentSongEmotion.asStateFlow()

    private val _isAnalyzingEmotion = MutableStateFlow(false)
    val isAnalyzingEmotion: StateFlow<Boolean> = _isAnalyzingEmotion.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // ==========================================
    // Initialization
    // ==========================================

    /**
     * Initialize ML models - should be called from MainActivity
     */
    fun initializeMLModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🚀 Initializing ML Recommendation Engine...")

                recommendationEngine = MusicRecommendationEngine(getApplication())

                withContext(Dispatchers.Main) {
                    _mlReady.value = true
                    Log.d(TAG, "✅ ML models ready!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize ML models", e)
                withContext(Dispatchers.Main) {
                    _aiError.value = "ML initialization failed: ${e.message}"
                    _mlReady.value = false
                }
            }
        }
    }

    // ==========================================
    // Core AI Function - Process User Message
    // ==========================================

    /**
     * Process user message with AI models
     * Call this from EmotionChatScreen
     */
    fun processUserMessageWithAI(
        message: String,
        onSuccess: (AIResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!_mlReady.value) {
            onError("AI models are not ready yet. Please wait...")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🎯 Processing user message: $message")

                // Get current song info if available
                val currentState = _playerState.value
                val currentSongUri = currentState.currentTrack?.let {
                    getSongUri(it)
                }
                val currentSongId = currentState.currentTrack?.id

                // Process with recommendation engine
                val result = recommendationEngine?.processRecommendation(
                    userMessage = message,
                    currentSongUri = currentSongUri,
                    currentSongId = currentSongId
                )

                if (result?.success == true && result.explanation != null) {
                    // Build AI response
                    val response = AIResponse(
                        intent = result.intentResult?.topIntent ?: "unknown",
                        intentConfidence = ((result.intentResult?.confidence ?: 0f) * 100).toInt(),
                        intentEmoji = getIntentEmoji(result.intentResult?.topIntent),
                        currentSongEmotion = result.emotionResult?.topEmotion,
                        emotionConfidence = ((result.emotionResult?.confidence ?: 0f) * 100).toInt(),
                        emotionEmoji = result.emotionResult?.emoji,
                        explanation = result.explanation.text,
                        overallConfidence = result.explanation.confidence,
                        suggestedAction = generateSuggestedAction(
                            result.intentResult?.topIntent,
                            result.emotionResult?.topEmotion
                        ),
                        tips = generateTips(result.intentResult?.topIntent)
                    )

                    // Store current song emotion
                    result.emotionResult?.let {
                        _currentSongEmotion.value = it
                    }

                    Log.d(TAG, "✅ AI Response generated successfully")
                    onSuccess(response)
                } else {
                    val error = result?.errorMessage ?: "Failed to process message"
                    Log.w(TAG, "⚠️ AI processing failed: $error")
                    onError(error)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ AI processing error", e)
                onError("AI processing failed: ${e.message}")
            }
        }
    }

    /**
     * Analyze emotion of currently playing song
     */
    fun analyzeCurrentSongEmotion() {
        if (!_mlReady.value) {
            Log.w(TAG, "⚠️ ML models not ready for emotion analysis")
            return
        }

        val currentTrack = _playerState.value.currentTrack ?: return

        viewModelScope.launch {
            _isAnalyzingEmotion.value = true

            try {
                Log.d(TAG, "🎵 Analyzing emotion for: ${currentTrack.name}")

                val uri = getSongUri(currentTrack)
                val result = recommendationEngine?.processAudio(uri, currentTrack.id)

                _currentSongEmotion.value = result
                _isAnalyzingEmotion.value = false

                Log.d(TAG, "✅ Emotion detected: ${result?.topEmotion} ${result?.emoji}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Emotion analysis error", e)
                _isAnalyzingEmotion.value = false
            }
        }
    }

    /**
     * Preload emotions for playlist
     */
    fun preloadPlaylistEmotions(tracks: List<Track>) {
        if (!_mlReady.value) {
            Log.w(TAG, "⚠️ ML models not ready for preloading")
            return
        }

        viewModelScope.launch {
            try {
                val uriPairs = tracks.mapNotNull { track ->
                    try {
                        val uri = getSongUri(track)
                        uri to track.id
                    } catch (e: Exception) {
                        null
                    }
                }

                Log.d(TAG, "📚 Preloading emotions for ${uriPairs.size} tracks")
                recommendationEngine?.preloadEmotions(uriPairs)
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading emotions", e)
            }
        }
    }

    // ==========================================
    // Music Player Functions
    // ==========================================

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val currentIndex = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // Debug logging
        Log.d(TAG, "Loading track: ${track.name} by ${track.artist}")
        Log.d(TAG, "Track preview URL: ${track.previewUrl}")

        // Release previous media player if using Deezer
        mediaPlayer?.release()
        mediaPlayer = null

        _playerState.value = PlayerState(
            currentTrack = track,
            isPlaying = false,
            playlist = finalPlaylist,
            currentIndex = currentIndex,
            duration = track.durationMs,
            isLoadingVideo = true
        )

        // Try to load YouTube video ID
        viewModelScope.launch {
            val videoId = youtubeRepository.getYouTubeVideoId(track)
            _playerState.value = _playerState.value.copy(
                youtubeVideoId = videoId,
                isLoadingVideo = false,
                usingDeezerFallback = videoId == null // Use Deezer if no YouTube found
            )

            // If no YouTube video, start Deezer preview automatically
            if (videoId == null && !track.previewUrl.isNullOrEmpty()) {
                Log.d(TAG, "No YouTube found, auto-starting Deezer preview")
                playDeezerPreview(track.previewUrl)
            }

            // Analyze emotion of newly loaded track (if ML ready)
            if (_mlReady.value) {
                analyzeCurrentSongEmotion()
            }
        }
    }

    fun useDeezerFallback() {
        val currentTrack = _playerState.value.currentTrack
        Log.d(TAG, "Attempting Deezer fallback for: ${currentTrack?.name}")

        if (currentTrack != null && !currentTrack.previewUrl.isNullOrEmpty()) {
            _playerState.value = _playerState.value.copy(
                usingDeezerFallback = true,
                youtubeVideoId = null
            )
            playDeezerPreview(currentTrack.previewUrl)
        } else {
            Log.e(TAG, "No preview URL available for fallback")
            _playerState.value = _playerState.value.copy(
                usingDeezerFallback = false,
                youtubeVideoId = null
            )
        }
    }

    private fun playDeezerPreview(previewUrl: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSource(previewUrl)

                setOnPreparedListener {
                    start()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        duration = duration.toLong()
                    )
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        usingDeezerFallback = false
                    )
                    true
                }

                setOnCompletionListener {
                    playNext()
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing Deezer preview", e)
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                usingDeezerFallback = false
            )
        }
    }

    fun togglePlayPause() {
        val currentState = _playerState.value

        if (currentState.usingDeezerFallback) {
            // Control MediaPlayer
            mediaPlayer?.let { player ->
                if (currentState.isPlaying) {
                    player.pause()
                    _playerState.value = currentState.copy(isPlaying = false)
                } else {
                    player.start()
                    _playerState.value = currentState.copy(isPlaying = true)
                }
            }
        } else {
            // YouTube player is controlled by WebView
            _playerState.value = currentState.copy(
                isPlaying = !currentState.isPlaying
            )
        }
    }

    fun play() {
        val currentState = _playerState.value

        if (currentState.usingDeezerFallback) {
            mediaPlayer?.start()
        }

        _playerState.value = currentState.copy(isPlaying = true)
    }

    fun pause() {
        val currentState = _playerState.value

        if (currentState.usingDeezerFallback) {
            mediaPlayer?.pause()
        }

        _playerState.value = currentState.copy(isPlaying = false)
    }

    fun playNext() {
        val currentState = _playerState.value
        val nextIndex = currentState.currentIndex + 1

        if (nextIndex < currentState.playlist.size) {
            loadTrack(currentState.playlist[nextIndex], currentState.playlist)
            play()
        }
    }

    fun playPrevious() {
        val currentState = _playerState.value
        val previousIndex = currentState.currentIndex - 1

        if (previousIndex >= 0) {
            loadTrack(currentState.playlist[previousIndex], currentState.playlist)
            play()
        }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            val duration = player.duration
            val position = (duration * progress).toInt()
            player.seekTo(position)
            _playerState.value = _playerState.value.copy(progress = progress)
        }
    }

    // ==========================================
    // Helper Functions
    // ==========================================

    private fun getSongUri(track: Track): Uri {
        // Return URI from preview URL
        return Uri.parse(track.previewUrl ?: "")
    }

    private fun getIntentEmoji(intent: String?): String {
        return when (intent) {
            "relax" -> "🧘"
            "energize" -> "💪"
            "comfort" -> "🤗"
            "focus" -> "🎯"
            "discover" -> "🔍"
            "nostalgia" -> "💭"
            "romance" -> "💕"
            "sleep" -> "😴"
            "uplift" -> "🌟"
            else -> "🎵"
        }
    }

    private fun generateSuggestedAction(intent: String?, emotion: String?): String {
        return when {
            intent == "relax" && emotion == "calm" ->
                "Perfect match! Continue enjoying this calming music."
            intent == "energize" && emotion == "energetic" ->
                "Great choice! This energetic track will keep you pumped up."
            intent == "relax" && emotion == "energetic" ->
                "This song might be too energetic. Try skipping to find something calmer."
            intent == "energize" && emotion == "calm" ->
                "This song is too calm for your energetic mood. Try the next track!"
            intent == "focus" ->
                "Minimize distractions and let this music help you concentrate."
            intent == "sleep" ->
                "Dim the lights and let this soothing music help you drift off."
            intent == "comfort" && emotion == "sad" ->
                "It's okay to feel this way. This music is here to comfort you."
            else ->
                "Enjoy this music that matches your current vibe!"
        }
    }

    private fun generateTips(intent: String?): List<String> {
        return when (intent) {
            "relax" -> listOf(
                "Find a comfortable position",
                "Take deep breaths while listening",
                "Close your eyes and focus on the music"
            )
            "energize" -> listOf(
                "Move your body to the beat",
                "Turn up the volume (safely!)",
                "Use this energy for your activities"
            )
            "focus" -> listOf(
                "Minimize visual distractions",
                "Use headphones for better immersion",
                "Take breaks every 25-30 minutes"
            )
            "sleep" -> listOf(
                "Keep volume low",
                "Use a sleep timer if available",
                "Avoid screens after this"
            )
            "comfort" -> listOf(
                "Allow yourself to feel emotions",
                "Music can be therapeutic",
                "Reach out to someone if you need support"
            )
            else -> listOf(
                "Discover new music based on your mood",
                "Create playlists for different feelings",
                "Let music enhance your day"
            )
        }
    }

    // ==========================================
    // Cleanup
    // ==========================================

    fun cleanupMLModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recommendationEngine?.close()
                Log.d(TAG, "🛑 ML resources cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up ML resources", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        cleanupMLModels()
    }
}