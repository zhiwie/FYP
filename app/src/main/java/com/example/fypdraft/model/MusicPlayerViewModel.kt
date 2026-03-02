package com.example.fypdraft.model

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.YouTubeMusicRepository
import com.example.fypdraft.ml.*
// AIResponse is now in com.example.fypdraft.model — no view import needed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

/**
 * Unified MusicPlayerViewModel
 * Combines music playback with AI emotion detection and recommendations.
 */
class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerViewModel"

    private val youtubeRepository = YouTubeMusicRepository()
    private var mediaPlayer: MediaPlayer? = null

    private var recommendationEngine: MusicRecommendationEngine? = null

    // ── Firebase ──────────────────────────────────────────────────────────────
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _mlReady = MutableStateFlow(false)
    val mlReady: StateFlow<Boolean> = _mlReady.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentSongEmotion = MutableStateFlow<EmotionResult?>(null)
    val currentSongEmotion: StateFlow<EmotionResult?> = _currentSongEmotion.asStateFlow()

    private val _isAnalyzingEmotion = MutableStateFlow(false)
    val isAnalyzingEmotion: StateFlow<Boolean> = _isAnalyzingEmotion.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // ── ML init ───────────────────────────────────────────────────────────────

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

    // ── Firebase: save playback history ──────────────────────────────────────

    private fun savePlaybackHistory(track: Track) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w(TAG, "⚠️ Cannot save playback history — user not logged in")
            return
        }
        val data = hashMapOf(
            "userId"    to userId,
            "title"     to track.name,
            "artist"    to track.artist,
            "trackId"   to track.id,
            "albumArt"  to (track.albumArtUrl ?: ""),
            "timestamp" to Timestamp.now()
        )
        firestore.collection("playbackHistory")
            .document(userId)
            .collection("tracks")
            .add(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Playback history saved: ${track.name} by ${track.artist}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save playback history", e)
            }
    }

    // ── TFLite recommendation (used by the old EmotionChatScreen) ─────────────
    // AIResponse is now imported from com.example.fypdraft.model (same package)

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

                val currentState   = _playerState.value
                val currentSongUri = currentState.currentTrack?.let { getSongUri(it) }
                val currentSongId  = currentState.currentTrack?.id

                val result = recommendationEngine?.processRecommendation(
                    userMessage    = message,
                    currentSongUri = currentSongUri,
                    currentSongId  = currentSongId
                )

                if (result?.success == true && result.explanation != null) {
                    val response = AIResponse(
                        intent             = result.intentResult?.topIntent ?: "unknown",
                        intentConfidence   = ((result.intentResult?.confidence ?: 0f) * 100).toInt(),
                        intentEmoji        = getIntentEmoji(result.intentResult?.topIntent),
                        currentSongEmotion = result.emotionResult?.topEmotion,
                        emotionConfidence  = ((result.emotionResult?.confidence ?: 0f) * 100).toInt(),
                        emotionEmoji       = result.emotionResult?.emoji,
                        explanation        = result.explanation.text,
                        overallConfidence  = result.explanation.confidence,
                        suggestedAction    = generateSuggestedAction(
                            result.intentResult?.topIntent,
                            result.emotionResult?.topEmotion
                        ),
                        tips = generateTips(result.intentResult?.topIntent)
                    )

                    result.emotionResult?.let { _currentSongEmotion.value = it }

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

    fun analyzeCurrentSongEmotion() {
        if (!_mlReady.value) return
        val currentTrack = _playerState.value.currentTrack ?: return

        viewModelScope.launch {
            _isAnalyzingEmotion.value = true
            try {
                val uri    = getSongUri(currentTrack)
                val result = recommendationEngine?.processAudio(uri, currentTrack.id)
                _currentSongEmotion.value  = result
                _isAnalyzingEmotion.value  = false
            } catch (e: Exception) {
                Log.e(TAG, "❌ Emotion analysis error", e)
                _isAnalyzingEmotion.value = false
            }
        }
    }

    fun preloadPlaylistEmotions(tracks: List<Track>) {
        if (!_mlReady.value) return
        viewModelScope.launch {
            try {
                val uriPairs = tracks.mapNotNull { track ->
                    try { getSongUri(track) to track.id } catch (e: Exception) { null }
                }
                recommendationEngine?.preloadEmotions(uriPairs)
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading emotions", e)
            }
        }
    }

    // ── Music player ──────────────────────────────────────────────────────────

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val currentIndex  = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        Log.d(TAG, "Loading track: ${track.name} by ${track.artist}")
        savePlaybackHistory(track) // ← Save to Firebase

        mediaPlayer?.release()
        mediaPlayer = null

        _playerState.value = PlayerState(
            currentTrack   = track,
            isPlaying      = false,
            playlist       = finalPlaylist,
            currentIndex   = currentIndex,
            duration       = track.durationMs,
            isLoadingVideo = true
        )

        viewModelScope.launch {
            val videoId = youtubeRepository.getYouTubeVideoId(track)
            _playerState.value = _playerState.value.copy(
                youtubeVideoId      = videoId,
                isLoadingVideo      = false,
                usingDeezerFallback = videoId == null
            )

            if (videoId == null && !track.previewUrl.isNullOrEmpty()) {
                playDeezerPreview(track.previewUrl)
            }

            if (_mlReady.value) analyzeCurrentSongEmotion()
        }
    }

    /**
     * Load a track that already has a known YouTube video ID (from ChatGPT recommendations).
     * Skips the YouTube search — player starts immediately.
     * Falls back to normal search if knownVideoId is null.
     */
    fun loadTrackWithVideoId(track: Track, knownVideoId: String?) {
        Log.d(TAG, "Loading track with video ID: ${track.name} by ${track.artist}")
        savePlaybackHistory(track) // ← Save to Firebase

        mediaPlayer?.release()
        mediaPlayer = null

        _playerState.value = PlayerState(
            currentTrack        = track,
            isPlaying           = false,
            playlist            = listOf(track),
            currentIndex        = 0,
            duration            = track.durationMs,
            youtubeVideoId      = knownVideoId,
            isLoadingVideo      = knownVideoId == null,
            usingDeezerFallback = false
        )

        if (knownVideoId == null) {
            viewModelScope.launch {
                val videoId = youtubeRepository.getYouTubeVideoId(track)
                _playerState.value = _playerState.value.copy(
                    youtubeVideoId      = videoId,
                    isLoadingVideo      = false,
                    usingDeezerFallback = videoId == null
                )
                if (videoId == null && !track.previewUrl.isNullOrEmpty()) {
                    playDeezerPreview(track.previewUrl)
                }
            }
        }
    }

    fun useDeezerFallback() {
        val currentTrack = _playerState.value.currentTrack
        if (currentTrack != null && !currentTrack.previewUrl.isNullOrEmpty()) {
            _playerState.value = _playerState.value.copy(
                usingDeezerFallback = true,
                youtubeVideoId      = null
            )
            playDeezerPreview(currentTrack.previewUrl)
        } else {
            _playerState.value = _playerState.value.copy(
                usingDeezerFallback = false,
                youtubeVideoId      = null
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
                        duration  = duration.toLong()
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _playerState.value = _playerState.value.copy(
                        isPlaying           = false,
                        usingDeezerFallback = false
                    )
                    true
                }
                setOnCompletionListener { playNext() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing Deezer preview", e)
            _playerState.value = _playerState.value.copy(
                isPlaying           = false,
                usingDeezerFallback = false
            )
        }
    }

    fun togglePlayPause() {
        val state = _playerState.value
        if (state.usingDeezerFallback) {
            mediaPlayer?.let { player ->
                if (state.isPlaying) { player.pause(); _playerState.value = state.copy(isPlaying = false) }
                else                 { player.start(); _playerState.value = state.copy(isPlaying = true)  }
            }
        } else {
            _playerState.value = state.copy(isPlaying = !state.isPlaying)
        }
    }

    fun play() {
        if (_playerState.value.usingDeezerFallback) mediaPlayer?.start()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    fun pause() {
        if (_playerState.value.usingDeezerFallback) mediaPlayer?.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun playNext() {
        val state     = _playerState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.playlist.size) {
            loadTrack(state.playlist[nextIndex], state.playlist)
            play()
        }
    }

    fun playPrevious() {
        val state          = _playerState.value
        val previousIndex  = state.currentIndex - 1
        if (previousIndex >= 0) {
            loadTrack(state.playlist[previousIndex], state.playlist)
            play()
        }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            player.seekTo((player.duration * progress).toInt())
            _playerState.value = _playerState.value.copy(progress = progress)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getSongUri(track: Track): Uri = Uri.parse(track.previewUrl ?: "")

    private fun getIntentEmoji(intent: String?) = when (intent) {
        "relax"     -> "🧘"
        "energize"  -> "💪"
        "comfort"   -> "🤗"
        "focus"     -> "🎯"
        "discover"  -> "🔍"
        "nostalgia" -> "💭"
        "romance"   -> "💕"
        "sleep"     -> "😴"
        "uplift"    -> "🌟"
        else        -> "🎵"
    }

    private fun generateSuggestedAction(intent: String?, emotion: String?) = when {
        intent == "relax"    && emotion == "calm"      -> "Perfect match! Continue enjoying this calming music."
        intent == "energize" && emotion == "energetic" -> "Great choice! This energetic track will keep you pumped up."
        intent == "relax"    && emotion == "energetic" -> "This song might be too energetic. Try skipping to find something calmer."
        intent == "energize" && emotion == "calm"      -> "This song is too calm for your energetic mood. Try the next track!"
        intent == "focus"                              -> "Minimize distractions and let this music help you concentrate."
        intent == "sleep"                              -> "Dim the lights and let this soothing music help you drift off."
        intent == "comfort"  && emotion == "sad"       -> "It's okay to feel this way. This music is here to comfort you."
        else                                           -> "Enjoy this music that matches your current vibe!"
    }

    private fun generateTips(intent: String?) = when (intent) {
        "relax"    -> listOf("Find a comfortable position", "Take deep breaths while listening", "Close your eyes and focus on the music")
        "energize" -> listOf("Move your body to the beat", "Turn up the volume (safely!)", "Use this energy for your activities")
        "focus"    -> listOf("Minimize visual distractions", "Use headphones for better immersion", "Take breaks every 25-30 minutes")
        "sleep"    -> listOf("Keep volume low", "Use a sleep timer if available", "Avoid screens after this")
        "comfort"  -> listOf("Allow yourself to feel emotions", "Music can be therapeutic", "Reach out to someone if you need support")
        else       -> listOf("Discover new music based on your mood", "Create playlists for different feelings", "Let music enhance your day")
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

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