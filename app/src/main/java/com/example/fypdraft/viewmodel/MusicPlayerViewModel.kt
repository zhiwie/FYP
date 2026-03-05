package com.example.fypdraft.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.YouTubeRepository
import com.example.fypdraft.ml.*
import com.example.fypdraft.model.AIResponse
import com.example.fypdraft.model.PlayerState
import com.example.fypdraft.model.Track
import com.example.fypdraft.service.MusicPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerViewModel"

    private val youtubeRepository = YouTubeRepository()
    private var mediaPlayer: MediaPlayer? = null
    private var recommendationEngine: MusicRecommendationEngine? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var musicService: MusicPlayerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as MusicPlayerService.MusicBinder
            musicService = b.getService()
            serviceBound = true
            Log.d(TAG, "✅ MusicPlayerService connected")

            MusicPlayerService.viewModel = this@MusicPlayerViewModel

            _playerState.value.currentTrack?.let { track ->
                musicService?.updateNotification(
                    title     = track.name,
                    artist    = track.artist,
                    isPlaying = _playerState.value.isPlaying
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
            Log.d(TAG, "MusicPlayerService disconnected")
        }
    }

    fun bindMusicService(context: Context) {
        MusicPlayerService.viewModel = this
        MusicPlayerService.startService(context)
        val intent = Intent(context, MusicPlayerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Binding MusicPlayerService...")
    }

    fun unbindMusicService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        MusicPlayerService.viewModel = null
        Log.d(TAG, "Unbound MusicPlayerService")
    }

    private fun updateNotification(track: Track, isPlaying: Boolean) {
        musicService?.updateNotification(
            title     = track.name,
            artist    = track.artist,
            isPlaying = isPlaying
        )
    }

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
        savePlaybackHistory(track)
        updateNotification(track, isPlaying = false)

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

        viewModelScope.launch {
            val videoId = youtubeRepository.getVideoId(track)
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

    fun loadTrackWithVideoId(track: Track, knownVideoId: String?) {
        Log.d(TAG, "Loading track with video ID: ${track.name} by ${track.artist}")
        savePlaybackHistory(track)
        updateNotification(track, isPlaying = false)

        mediaPlayer?.release()
        mediaPlayer = null

        _playerState.value = PlayerState(
            currentTrack = track,
            isPlaying = false,
            playlist = listOf(track),
            currentIndex = 0,
            duration = track.durationMs,
            youtubeVideoId = knownVideoId,
            isLoadingVideo = knownVideoId == null,
            usingDeezerFallback = false
        )

        if (knownVideoId == null) {
            viewModelScope.launch {
                val videoId = youtubeRepository.getVideoId(track)
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
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(previewUrl)
                setOnPreparedListener {
                    start()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        duration  = duration.toLong()
                    )
                    _playerState.value.currentTrack?.let { updateNotification(it, true) }
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
                if (state.isPlaying) {
                    player.pause()
                    _playerState.value = state.copy(isPlaying = false)
                    state.currentTrack?.let { updateNotification(it, false) }
                } else {
                    player.start()
                    _playerState.value = state.copy(isPlaying = true)
                    state.currentTrack?.let { updateNotification(it, true) }
                }
            }
        } else {
            val newPlaying = !state.isPlaying
            _playerState.value = state.copy(isPlaying = newPlaying)
            state.currentTrack?.let { updateNotification(it, newPlaying) }
        }
    }

    private val _notificationCommand = MutableStateFlow<String?>(null)
    val notificationCommand: StateFlow<String?> = _notificationCommand.asStateFlow()

    fun clearNotificationCommand() {
        _notificationCommand.value = null
    }

    fun play() {
        val state = _playerState.value
        Log.d(TAG, "▶ play() called — usingDeezer=${state.usingDeezerFallback}, mediaPlayer=${mediaPlayer != null}")

        if (state.usingDeezerFallback) {
            val player = mediaPlayer
            if (player != null) {
                try {
                    if (!player.isPlaying) {
                        player.start()
                    }
                    _playerState.value = state.copy(isPlaying = true)
                    state.currentTrack?.let { updateNotification(it, true) }
                    Log.d(TAG, "▶ Deezer play resumed")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPlayer.start() failed, re-preparing", e)
                    state.currentTrack?.previewUrl?.let { playDeezerPreview(it) }
                }
            } else {
                Log.d(TAG, "▶ MediaPlayer is null, re-preparing Deezer track")
                state.currentTrack?.previewUrl?.let { playDeezerPreview(it) }
                    ?: Log.w(TAG, "No preview URL available")
            }
        } else {
            _playerState.value = state.copy(isPlaying = true)
            _notificationCommand.value = MusicPlayerService.ACTION_PLAY
            state.currentTrack?.let { updateNotification(it, true) }
            Log.d(TAG, "▶ YouTube play — emitted notification command")
        }
    }

    fun pause() {
        val state = _playerState.value
        Log.d(TAG, "⏸ pause() called — usingDeezer=${state.usingDeezerFallback}, mediaPlayer=${mediaPlayer != null}")

        if (state.usingDeezerFallback) {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
                Log.d(TAG, "⏸ Deezer paused")
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer.pause() failed", e)
            }
            _playerState.value = state.copy(isPlaying = false)
            state.currentTrack?.let { updateNotification(it, false) }
        } else {
            _playerState.value = state.copy(isPlaying = false)
            _notificationCommand.value = MusicPlayerService.ACTION_PAUSE
            state.currentTrack?.let { updateNotification(it, false) }
            Log.d(TAG, "⏸ YouTube pause — emitted notification command")
        }
    }

    fun playNext() {
        val state = _playerState.value
        Log.d(TAG, "⏭ playNext() called — index=${state.currentIndex}, playlistSize=${state.playlist.size}")
        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.playlist.size) {
            loadTrack(state.playlist[nextIndex], state.playlist)
        } else {
            Log.d(TAG, "⏭ Already at last track")
        }
    }

    fun playPrevious() {
        val state = _playerState.value
        Log.d(TAG, "⏮ playPrevious() called — index=${state.currentIndex}")
        val previousIndex = state.currentIndex - 1
        if (previousIndex >= 0) {
            loadTrack(state.playlist[previousIndex], state.playlist)
        } else {
            Log.d(TAG, "⏮ Already at first track")
        }
    }

    fun seekTo(progress: Float) {
        mediaPlayer?.let { player ->
            player.seekTo((player.duration * progress).toInt())
            _playerState.value = _playerState.value.copy(progress = progress)
        }
    }

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
        MusicPlayerService.viewModel = null
        cleanupMLModels()
    }
}