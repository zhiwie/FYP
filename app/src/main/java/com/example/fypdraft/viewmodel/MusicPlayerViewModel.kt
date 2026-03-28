package com.example.fypdraft.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.ml.*
import com.example.fypdraft.model.AIResponse
import com.example.fypdraft.model.PlayerState
import com.example.fypdraft.model.Track
import com.example.fypdraft.service.MusicPlayerService
import com.example.fypdraft.service.SpotifyConnectionState
import com.example.fypdraft.service.SpotifyPlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerVM"

    private var spotifyPlayback: SpotifyPlaybackManager? = null
    private var mediaPlayer: MediaPlayer? = null  // Fallback for preview URLs
    private var recommendationEngine: MusicRecommendationEngine? = null
    private var progressJob: Job? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Audio focus — lets us take control away from Spotify's own session
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // ── Notification service ─────────────────────────────────────────
    private var musicService: MusicPlayerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicPlayerService.MusicBinder).getService()
            serviceBound = true
            MusicPlayerService.viewModel = this@MusicPlayerViewModel
            _playerState.value.currentTrack?.let { track ->
                musicService?.updateNotificationWithArt(
                    track.name, track.artist,
                    _playerState.value.isPlaying,
                    track.albumArtUrl
                )
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { serviceBound = false; musicService = null }
    }

    fun bindMusicService(context: Context) {
        MusicPlayerService.viewModel = this
        // DON'T start the foreground service here — only start it when
        // preview URL playback actually needs a notification.
        // When Spotify App Remote is used, Spotify's own notification handles it.
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Start the foreground notification service for preview URL playback.
     * Only called when Spotify App Remote is NOT available.
     */
    private fun ensureServiceStarted() {
        val ctx = getApplication<Application>()
        if (!serviceBound) {
            MusicPlayerService.viewModel = this
            MusicPlayerService.startService(ctx)
            ctx.bindService(
                Intent(ctx, MusicPlayerService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun unbindMusicService(context: Context) {
        if (serviceBound) { context.unbindService(serviceConnection); serviceBound = false }
        MusicPlayerService.viewModel = null
    }

    /**
     * Update the notification with track info AND album art URL.
     * ONLY updates when NOT using Spotify App Remote — when Spotify
     * is handling playback, its own notification is shown instead.
     */
    private fun updateNotification(track: Track, isPlaying: Boolean) {
        // Don't show our notification when Spotify App Remote is playing
        // — Spotify already shows its own notification with full controls
        if (spotifyPlayback?.isConnected() == true && !_playerState.value.usingDeezerFallback) {
            return
        }
        musicService?.updateNotificationWithArt(
            track.name, track.artist, isPlaying, track.albumArtUrl
        )
    }

    // ── Audio focus ──────────────────────────────────────────────────

    /**
     * Request audio focus so Android treats MoodSync as the active media app.
     * This causes Spotify's own media session to lose focus, which hides
     * or deprioritizes its notification.
     */
    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* Spotify handles actual playback */ }
                .build()
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { /* no-op */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    // ── State ────────────────────────────────────────────────────────

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

    private val _spotifyConnected = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    // Compat
    private val _notificationCommand = MutableStateFlow<String?>(null)
    val notificationCommand: StateFlow<String?> = _notificationCommand.asStateFlow()
    fun clearNotificationCommand() { _notificationCommand.value = null }

    // ── Spotify App Remote connection ────────────────────────────────

    // Job for polling Spotify position
    private var spotifyPollingJob: Job? = null

    fun connectSpotifyPlayback(context: Context) {
        if (spotifyPlayback == null) {
            spotifyPlayback = SpotifyPlaybackManager(context)
        }
        spotifyPlayback?.connect()

        viewModelScope.launch {
            spotifyPlayback?.connectionState?.collect { state ->
                _spotifyConnected.value = state == SpotifyConnectionState.CONNECTED
                if (state == SpotifyConnectionState.CONNECTED) {
                    Log.d(TAG, "Spotify App Remote connected — playback will use Spotify")
                    _playbackError.value = null
                }
                if (state == SpotifyConnectionState.FAILED) {
                    Log.w(TAG, "Spotify App Remote failed — will use preview URLs")
                    _playbackError.value = spotifyPlayback?.error?.value
                }
            }
        }

        // Collect one-shot poll results from SpotifyPlaybackManager
        // (these only fire after play/pause/skip, NOT continuously)
        viewModelScope.launch {
            spotifyPlayback?.currentPlayerState?.collect { sps ->
                if (sps != null) {
                    val cur = _playerState.value
                    _playerState.value = cur.copy(
                        isPlaying = !sps.isPaused,
                        currentPosition = sps.positionMs,
                        duration = sps.durationMs,
                        progress = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                    )
                    cur.currentTrack?.let { updateNotification(it, !sps.isPaused) }
                }
            }
        }

        viewModelScope.launch {
            spotifyPlayback?.error?.collect { _playbackError.value = it }
        }
    }

    /**
     * Start polling Spotify for position AND track info every second.
     * Detects when Spotify auto-advances to the next song and updates
     * the currentTrack in playerState so album art/title/artist refresh.
     */
    private var lastPolledTrackUri: String? = null

    private fun startSpotifyPositionPolling() {
        spotifyPollingJob?.cancel()
        spotifyPollingJob = viewModelScope.launch {
            while (isActive) {
                spotifyPlayback?.getPlayerInfo { sps ->
                    val cur = _playerState.value

                    // Detect if Spotify changed to a different track
                    // (e.g. auto-advance, skip from Spotify notification, etc.)
                    val trackChanged = lastPolledTrackUri != null && lastPolledTrackUri != sps.trackUri
                    lastPolledTrackUri = sps.trackUri

                    if (trackChanged) {
                        // Spotify moved to a new track — update currentTrack
                        // Extract Spotify track ID from URI: "spotify:track:ABC123" -> "ABC123"
                        val newTrackId = sps.trackUri.removePrefix("spotify:track:")
                        val newTrack = Track(
                            id = newTrackId,
                            name = sps.trackName,
                            artist = sps.artistName,
                            album = sps.albumName,
                            albumArtUrl = "", // Will be empty — Spotify App Remote doesn't give HTTP URLs
                            previewUrl = null,
                            durationMs = sps.durationMs,
                            spotifyUri = sps.trackUri
                        )

                        // Try to find this track in our playlist first (it has proper albumArtUrl)
                        val fromPlaylist = cur.playlist.firstOrNull { it.id == newTrackId }
                        val finalTrack = fromPlaylist ?: newTrack

                        // Update index if found in playlist
                        val newIndex = cur.playlist.indexOfFirst { it.id == newTrackId }
                            .let { if (it >= 0) it else cur.currentIndex }

                        Log.d(TAG, "Track changed via Spotify: '${finalTrack.name}' by ${finalTrack.artist}")

                        _playerState.value = cur.copy(
                            currentTrack = finalTrack,
                            currentIndex = newIndex,
                            isPlaying = !sps.isPaused,
                            currentPosition = sps.positionMs,
                            duration = sps.durationMs,
                            progress = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                        )
                    } else {
                        // Same track — just update position
                        _playerState.value = cur.copy(
                            currentPosition = sps.positionMs,
                            duration = sps.durationMs,
                            isPlaying = !sps.isPaused,
                            progress = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopSpotifyPositionPolling() {
        spotifyPollingJob?.cancel()
        spotifyPollingJob = null
    }

    fun disconnectSpotifyPlayback() {
        spotifyPlayback?.disconnect()
        _spotifyConnected.value = false
    }

    // ── ML ────────────────────────────────────────────────────────────

    fun initializeMLModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recommendationEngine = MusicRecommendationEngine(getApplication())
                withContext(Dispatchers.Main) { _mlReady.value = true }
            } catch (e: Exception) {
                Log.e(TAG, "ML init failed", e)
                withContext(Dispatchers.Main) { _aiError.value = "ML: ${e.message}"; _mlReady.value = false }
            }
        }
    }

    // ── Firebase ─────────────────────────────────────────────────────

    private fun savePlaybackHistory(track: Track) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("playbackHistory").document(uid).collection("tracks")
            .add(hashMapOf(
                "userId" to uid, "title" to track.name, "artist" to track.artist,
                "trackId" to track.id, "albumArt" to track.albumArtUrl,
                "spotifyUri" to (track.spotifyUri ?: ""), "timestamp" to Timestamp.now()
            ))
    }

    // ── AI recommendation ────────────────────────────────────────────

    fun processUserMessageWithAI(message: String, onSuccess: (AIResponse) -> Unit, onError: (String) -> Unit) {
        if (!_mlReady.value) { onError("AI not ready"); return }
        viewModelScope.launch {
            try {
                val cs = _playerState.value
                val result = recommendationEngine?.processRecommendation(message, cs.currentTrack?.let { getSongUri(it) }, cs.currentTrack?.id)
                if (result?.success == true && result.explanation != null) {
                    result.emotionResult?.let { _currentSongEmotion.value = it }
                    onSuccess(AIResponse(
                        result.intentResult?.topIntent ?: "unknown",
                        ((result.intentResult?.confidence ?: 0f) * 100).toInt(),
                        getIntentEmoji(result.intentResult?.topIntent),
                        result.emotionResult?.topEmotion,
                        ((result.emotionResult?.confidence ?: 0f) * 100).toInt(),
                        result.emotionResult?.emoji,
                        result.explanation.text, result.explanation.confidence,
                        generateSuggestedAction(result.intentResult?.topIntent, result.emotionResult?.topEmotion),
                        generateTips(result.intentResult?.topIntent)
                    ))
                } else onError(result?.errorMessage ?: "Failed")
            } catch (e: Exception) { onError("AI error: ${e.message}") }
        }
    }

    fun analyzeCurrentSongEmotion() {
        if (!_mlReady.value) return
        val track = _playerState.value.currentTrack ?: return
        viewModelScope.launch {
            _isAnalyzingEmotion.value = true
            try { _currentSongEmotion.value = recommendationEngine?.processAudio(getSongUri(track), track.id) }
            catch (e: Exception) { Log.e(TAG, "Emotion error", e) }
            finally { _isAnalyzingEmotion.value = false }
        }
    }

    fun preloadPlaylistEmotions(tracks: List<Track>) {
        if (!_mlReady.value) return
        viewModelScope.launch {
            try { recommendationEngine?.preloadEmotions(tracks.mapNotNull { try { getSongUri(it) to it.id } catch (_: Exception) { null } }) }
            catch (_: Exception) {}
        }
    }

    // ── Playback ─────────────────────────────────────────────────────

    /**
     * Load a track and start playback.
     *
     * Priority:
     * 1. Spotify App Remote (full song, requires Premium + Spotify app)
     * 2. Preview URL via MediaPlayer (30s clip, works without Premium)
     * 3. No playback available — show error
     */
    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val idx = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // Stop any current playback
        stopMediaPlayer()
        progressJob?.cancel()

        savePlaybackHistory(track)

        _playerState.value = _playerState.value.copy(
            currentTrack = track, isPlaying = false, playlist = finalPlaylist,
            currentIndex = idx, duration = track.durationMs, progress = 0f,
            currentPosition = 0L, isLoadingVideo = false, youtubeVideoId = null,
            usingDeezerFallback = false
        )
        _currentSongEmotion.value = null
        _playbackError.value = null

        // Try Spotify App Remote first — Spotify handles its own notification
        if (spotifyPlayback?.isConnected() == true) {
            val uri = track.spotifyUri ?: "spotify:track:${track.id}"
            Log.d(TAG, "Playing via Spotify App Remote: $uri")
            lastPolledTrackUri = uri  // Reset so polling doesn't falsely detect a change
            spotifyPlayback?.play(uri)
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startSpotifyPositionPolling()
        }
        // Fallback: preview URL — needs our own notification service
        else if (!track.previewUrl.isNullOrEmpty()) {
            Log.d(TAG, "Playing preview URL: ${track.previewUrl}")
            stopSpotifyPositionPolling()
            requestAudioFocus()
            ensureServiceStarted()
            playPreviewUrl(track.previewUrl!!)
            _playerState.value = _playerState.value.copy(usingDeezerFallback = true)
        }
        // No playback
        else {
            stopSpotifyPositionPolling()
            _playbackError.value = "Install Spotify app for full playback"
        }

        if (_mlReady.value) analyzeCurrentSongEmotion()
    }

    fun loadTrackWithVideoId(track: Track, knownVideoId: String?) {
        loadTrack(track) // Spotify-first, ignore videoId
    }

    // ── Play from AI recommendation ──────────────────────────────────

    private var spotifyMusicRepo: com.example.fypdraft.data.repository.SpotifyMusicRepository? = null

    /**
     * Set the SpotifyMusicRepository so we can search for songs.
     * Call this from your Activity/NavHost when setting up the ViewModel.
     */
    fun setSpotifyMusicRepo(repo: com.example.fypdraft.data.repository.SpotifyMusicRepository?) {
        spotifyMusicRepo = repo
    }

    /**
     * Search Spotify for a song recommended by the AI chat, then play it.
     * This bridges the gap between ChatGPT's text recommendations and
     * actual Spotify playback.
     */
    fun playFromRecommendation(
        songTitle: String,
        songArtist: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                val repo = spotifyMusicRepo
                if (repo == null) {
                    Log.e(TAG, "SpotifyMusicRepository not set — cannot search")
                    onResult(false, "Spotify not connected")
                    return@launch
                }

                // Search Spotify for "artist title" to find the exact track
                val query = "$songArtist $songTitle"
                Log.d(TAG, "Searching Spotify for AI recommendation: '$query'")

                val results = repo.searchTracks(query, 5)

                if (results.isEmpty()) {
                    Log.w(TAG, "No Spotify results for '$query'")
                    onResult(false, "Song not found on Spotify")
                    return@launch
                }

                // Try to find an exact match first, otherwise take the first result
                val bestMatch = results.firstOrNull { track ->
                    track.name.equals(songTitle, ignoreCase = true) &&
                            track.artist.equals(songArtist, ignoreCase = true)
                } ?: results.firstOrNull { track ->
                    track.name.contains(songTitle, ignoreCase = true) ||
                            track.artist.contains(songArtist, ignoreCase = true)
                } ?: results.first()

                Log.d(TAG, "Found match: '${bestMatch.name}' by ${bestMatch.artist}")
                loadTrack(bestMatch, results)
                onResult(true, null)

            } catch (e: Exception) {
                Log.e(TAG, "playFromRecommendation failed", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    // ── Preview URL playback (fallback) ──────────────────────────────

    private fun playPreviewUrl(url: String) {
        stopMediaPlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(url)
                setOnPreparedListener {
                    start()
                    _playerState.value = _playerState.value.copy(isPlaying = true, duration = duration.toLong())
                    _playerState.value.currentTrack?.let { t -> updateNotification(t, true) }
                    startProgressTracking()
                }
                setOnErrorListener { _, w, e -> Log.e(TAG, "MediaPlayer error: $w/$e"); true }
                setOnCompletionListener { playNext() }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Preview playback failed", e)
        }
    }

    private fun stopMediaPlayer() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        val dur = mp.duration.toLong()
                        _playerState.value = _playerState.value.copy(
                            currentPosition = pos,
                            duration = dur,
                            progress = if (dur > 0) pos.toFloat() / dur else 0f
                        )
                    }
                }
                delay(500)
            }
        }
    }

    // ── Controls ─────────────────────────────────────────────────────

    fun togglePlayPause() { if (_playerState.value.isPlaying) pause() else play() }

    fun play() {
        val s = _playerState.value
        if (spotifyPlayback?.isConnected() == true && !s.usingDeezerFallback) {
            spotifyPlayback?.resume()
            startSpotifyPositionPolling()
        } else {
            requestAudioFocus()
            mediaPlayer?.let { if (!it.isPlaying) it.start(); startProgressTracking() }
        }
        _playerState.value = s.copy(isPlaying = true)
        s.currentTrack?.let { updateNotification(it, true) }
    }

    fun pause() {
        val s = _playerState.value
        if (spotifyPlayback?.isConnected() == true && !s.usingDeezerFallback) {
            spotifyPlayback?.pause()
            stopSpotifyPositionPolling()
        } else {
            try { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() } catch (_: Exception) {}
            progressJob?.cancel()
        }
        _playerState.value = s.copy(isPlaying = false)
        s.currentTrack?.let { updateNotification(it, false) }
    }

    fun playNext() {
        val s = _playerState.value
        val next = s.currentIndex + 1
        if (next < s.playlist.size) loadTrack(s.playlist[next], s.playlist)
        else spotifyPlayback?.skipNext()
    }

    fun playPrevious() {
        val s = _playerState.value
        val prev = s.currentIndex - 1
        if (prev >= 0) loadTrack(s.playlist[prev], s.playlist)
        else spotifyPlayback?.skipPrevious()
    }

    fun seekTo(progress: Float) {
        val dur = _playerState.value.duration
        if (dur <= 0) return
        val ms = (dur * progress).toLong()
        if (spotifyPlayback?.isConnected() == true && !_playerState.value.usingDeezerFallback) {
            spotifyPlayback?.seekTo(ms)
        } else {
            mediaPlayer?.seekTo(ms.toInt())
        }
        _playerState.value = _playerState.value.copy(progress = progress, currentPosition = ms)
    }

    fun toggleShuffle() { spotifyPlayback?.toggleShuffle() }
    fun toggleRepeat() { spotifyPlayback?.toggleRepeat() }
    fun clearPlaybackError() { _playbackError.value = null; spotifyPlayback?.clearError() }
    fun useDeezerFallback() { /* no-op compat */ }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getSongUri(track: Track): Uri = Uri.parse(track.previewUrl ?: "")

    private fun getIntentEmoji(i: String?) = when (i) {
        "relax" -> "🧘"; "energize" -> "💪"; "comfort" -> "🤗"; "focus" -> "🎯"
        "discover" -> "🔍"; "nostalgia" -> "💭"; "romance" -> "💕"; "sleep" -> "😴"
        "uplift" -> "🌟"; else -> "🎵"
    }

    private fun generateSuggestedAction(i: String?, e: String?) = when {
        i == "relax" && e == "calm" -> "Perfect match! Keep enjoying."
        i == "energize" && e == "energetic" -> "Great choice! Keep the energy up!"
        i == "focus" -> "Minimize distractions and focus."
        i == "sleep" -> "Dim the lights and drift off."
        i == "comfort" && e == "sad" -> "It's okay to feel this way."
        else -> "Enjoy the music!"
    }

    private fun generateTips(i: String?) = when (i) {
        "relax" -> listOf("Deep breaths", "Close your eyes", "Comfortable position")
        "energize" -> listOf("Move to the beat", "Turn it up!", "Channel the energy")
        "focus" -> listOf("Use headphones", "Minimize distractions", "Break every 25 min")
        "sleep" -> listOf("Keep volume low", "Sleep timer", "No screens after")
        "comfort" -> listOf("Feel your emotions", "Music is therapy", "Reach out if needed")
        else -> listOf("Discover new music", "Create playlists", "Enjoy your day")
    }

    fun cleanupMLModels() {
        viewModelScope.launch(Dispatchers.IO) { try { recommendationEngine?.close() } catch (_: Exception) {} }
    }

    override fun onCleared() {
        super.onCleared()
        stopMediaPlayer()
        progressJob?.cancel()
        stopSpotifyPositionPolling()
        abandonAudioFocus()
        disconnectSpotifyPlayback()
        MusicPlayerService.viewModel = null
        cleanupMLModels()
    }
}