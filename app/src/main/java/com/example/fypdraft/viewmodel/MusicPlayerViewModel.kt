package com.example.fypdraft.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.ml.*
import com.example.fypdraft.model.AIResponse
import com.example.fypdraft.model.PlayerState
import com.example.fypdraft.model.Track
import com.example.fypdraft.service.MusicPlayerService
import com.example.fypdraft.service.SpotifyConnectionState
import com.example.fypdraft.service.SpotifyPlaybackManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerVM"

    private var spotifyPlayback: SpotifyPlaybackManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recommendationEngine: MusicRecommendationEngine? = null
    private var progressJob: Job? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    // ── Track persistence (survive process kill) ──────────────────────
    private val prefs: SharedPreferences =
        application.getSharedPreferences("moodsync_playback", Context.MODE_PRIVATE)
    private val gson = Gson()

    private data class PersistedTrack(
        val id: String,
        val name: String,
        val artist: String,
        val album: String,
        val albumArtUrl: String,
        val previewUrl: String?,
        val durationMs: Long,
        val spotifyUri: String?
    )

    private fun persistTrack(track: Track) {
        try {
            prefs.edit {
                putString("last_track", gson.toJson(PersistedTrack(
                    id = track.id, name = track.name, artist = track.artist,
                    album = track.album, albumArtUrl = track.albumArtUrl,
                    previewUrl = track.previewUrl, durationMs = track.durationMs,
                    spotifyUri = track.spotifyUri
                )))
            }
        } catch (e: Exception) { Log.w(TAG, "persistTrack failed", e) }
    }

    /**
     * Called once from [connectSpotifyPlayback] after the App Remote connects.
     *
     * Behaviour on reopen:
     * - If Spotify is still playing the same track   → sync UI to reflect
     *   "playing" and current position; user doesn't need to do anything.
     * - If Spotify is paused or on a different track → restore the persisted
     *   track in a paused state so the mini player still shows it.
     *
     * This replaces the old [restoreLastTrack] init{} call which restored
     * blindly without knowing Spotify's live state.
     */
    private fun syncWithSpotifyAfterConnect() {
        spotifyPlayback?.getPlayerInfo { sps ->
            val persisted = loadPersistedTrack()

            if (sps.trackUri.isNotEmpty()) {
                // Spotify has an active track — use its state as the source of truth
                val trackId  = sps.trackUri.removePrefix("spotify:track:")
                // Try to match against the persisted track so we keep albumArtUrl
                val liveTrack = if (persisted != null && persisted.id == trackId) {
                    persisted
                } else {
                    Track(
                        id          = trackId,
                        name        = sps.trackName,
                        artist      = sps.artistName,
                        album       = sps.albumName,
                        albumArtUrl = "",
                        previewUrl  = null,
                        durationMs  = sps.durationMs,
                        spotifyUri  = sps.trackUri
                    )
                }
                lastPolledTrackUri = sps.trackUri
                _playerState.value = _playerState.value.copy(
                    currentTrack    = liveTrack,
                    isPlaying       = !sps.isPaused,
                    currentPosition = sps.positionMs,
                    duration        = sps.durationMs,
                    progress        = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f,
                    playlist        = if (_playerState.value.playlist.isEmpty()) listOf(liveTrack)
                    else _playerState.value.playlist
                )
                if (!sps.isPaused) startSpotifyPositionPolling()
                Log.d(TAG, "Synced with Spotify on reconnect: '${liveTrack.name}' playing=${!sps.isPaused}")
            } else if (persisted != null) {
                // Spotify has nothing active — fall back to persisted track, paused
                _playerState.value = _playerState.value.copy(
                    currentTrack    = persisted,
                    isPlaying       = false,
                    progress        = 0f,
                    currentPosition = 0L,
                    playlist        = listOf(persisted)
                )
                Log.d(TAG, "Spotify idle on reconnect — showing persisted track '${persisted.name}' paused")
            }
        }
    }

    private fun loadPersistedTrack(): Track? {
        val json = prefs.getString("last_track", null) ?: return null
        return try {
            val p = gson.fromJson(json, PersistedTrack::class.java)
            Track(id = p.id, name = p.name, artist = p.artist, album = p.album,
                albumArtUrl = p.albumArtUrl, previewUrl = p.previewUrl,
                durationMs = p.durationMs, spotifyUri = p.spotifyUri)
        } catch (e: Exception) {
            Log.w(TAG, "loadPersistedTrack failed", e)
            prefs.edit { remove("last_track") }
            null
        }
    }

    // ── Audio focus ──────────────────────────────────────────────────
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // ── Notification service ──────────────────────────────────────────
    private var musicService: MusicPlayerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as MusicPlayerService.MusicBinder).getService()
            serviceBound = true
            MusicPlayerService.viewModel = this@MusicPlayerViewModel
            _playerState.value.currentTrack?.let { track ->
                musicService?.updateNotificationWithArt(
                    track.name, track.artist, _playerState.value.isPlaying, track.albumArtUrl)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { serviceBound = false; musicService = null }
    }

    fun bindMusicService(context: Context) {
        MusicPlayerService.viewModel = this
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun ensureServiceStarted() {
        val ctx = getApplication<Application>()
        if (!serviceBound) {
            MusicPlayerService.viewModel = this
            MusicPlayerService.startService(ctx)
            ctx.bindService(Intent(ctx, MusicPlayerService::class.java),
                serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindMusicService(context: Context) {
        if (serviceBound) { context.unbindService(serviceConnection); serviceBound = false }
        MusicPlayerService.viewModel = null
    }

    private fun updateNotification(track: Track, isPlaying: Boolean) {
        if (spotifyPlayback?.isConnected() == true && !_playerState.value.usingDeezerFallback) return
        musicService?.updateNotificationWithArt(track.name, track.artist, isPlaying, track.albumArtUrl)
    }

    // ── Audio focus ──────────────────────────────────────────────────
    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { }.build()
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus({ }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") am.abandonAudioFocus(null)
    }

    // ── State ────────────────────────────────────────────────────────
    private val _mlReady           = MutableStateFlow(false)
    val mlReady: StateFlow<Boolean> = _mlReady.asStateFlow()

    private val _playerState            = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentSongEmotion          = MutableStateFlow<EmotionResult?>(null)
    val currentSongEmotion: StateFlow<EmotionResult?> = _currentSongEmotion.asStateFlow()

    private val _isAnalyzingEmotion       = MutableStateFlow(false)
    val isAnalyzingEmotion: StateFlow<Boolean> = _isAnalyzingEmotion.asStateFlow()

    private val _aiError          = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _spotifyConnected       = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _playbackError          = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _notificationCommand    = MutableStateFlow<String?>(null)
    val notificationCommand: StateFlow<String?> = _notificationCommand.asStateFlow()
    fun clearNotificationCommand() { _notificationCommand.value = null }

    // ── Spotify App Remote ────────────────────────────────────────────
    private var spotifyPollingJob: Job? = null
    private var lastPolledTrackUri: String? = null

    fun connectSpotifyPlayback(context: Context) {
        if (spotifyPlayback == null) spotifyPlayback = SpotifyPlaybackManager(context)
        spotifyPlayback?.connect()

        viewModelScope.launch {
            spotifyPlayback?.connectionState?.collect { state ->
                _spotifyConnected.value = state == SpotifyConnectionState.CONNECTED
                if (state == SpotifyConnectionState.CONNECTED) {
                    Log.d(TAG, "Spotify App Remote connected")
                    _playbackError.value = null
                    // Query Spotify's live state immediately so the mini player
                    // reflects what's actually playing after an app reopen.
                    syncWithSpotifyAfterConnect()
                }
                if (state == SpotifyConnectionState.FAILED) {
                    Log.w(TAG, "Spotify App Remote failed — using preview URLs")
                    _playbackError.value = spotifyPlayback?.error?.value
                    // App Remote unavailable: fall back to persisted track shown paused
                    if (_playerState.value.currentTrack == null) {
                        loadPersistedTrack()?.let { track ->
                            _playerState.value = _playerState.value.copy(
                                currentTrack = track, isPlaying = false,
                                playlist = listOf(track)
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            spotifyPlayback?.currentPlayerState?.collect { sps ->
                if (sps != null) {
                    val cur = _playerState.value
                    _playerState.value = cur.copy(
                        isPlaying = !sps.isPaused, currentPosition = sps.positionMs,
                        duration  = sps.durationMs,
                        progress  = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                    )
                    cur.currentTrack?.let { updateNotification(it, !sps.isPaused) }
                }
            }
        }

        viewModelScope.launch { spotifyPlayback?.error?.collect { _playbackError.value = it } }
    }

    private fun startSpotifyPositionPolling() {
        spotifyPollingJob?.cancel()
        spotifyPollingJob = viewModelScope.launch {
            while (isActive) {
                spotifyPlayback?.getPlayerInfo { sps ->
                    val cur          = _playerState.value
                    val trackChanged = lastPolledTrackUri != null && lastPolledTrackUri != sps.trackUri
                    lastPolledTrackUri = sps.trackUri

                    if (trackChanged) {
                        val newTrackId = sps.trackUri.removePrefix("spotify:track:")
                        val newTrack   = Track(id = newTrackId, name = sps.trackName,
                            artist = sps.artistName, album = sps.albumName,
                            albumArtUrl = "", previewUrl = null,
                            durationMs = sps.durationMs, spotifyUri = sps.trackUri)
                        val fromPlaylist = cur.playlist.firstOrNull { it.id == newTrackId }
                        val finalTrack   = fromPlaylist ?: newTrack
                        val newIndex     = cur.playlist.indexOfFirst { it.id == newTrackId }
                            .let { if (it >= 0) it else cur.currentIndex }
                        Log.d(TAG, "Track changed via Spotify: '${finalTrack.name}'")
                        persistTrack(finalTrack)
                        _playerState.value = cur.copy(
                            currentTrack = finalTrack, currentIndex = newIndex,
                            isPlaying = !sps.isPaused, currentPosition = sps.positionMs,
                            duration  = sps.durationMs,
                            progress  = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                        )
                    } else {
                        _playerState.value = cur.copy(
                            currentPosition = sps.positionMs, duration = sps.durationMs,
                            isPlaying = !sps.isPaused,
                            progress  = if (sps.durationMs > 0) sps.positionMs.toFloat() / sps.durationMs else 0f
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopSpotifyPositionPolling() { spotifyPollingJob?.cancel(); spotifyPollingJob = null }

    fun disconnectSpotifyPlayback() { spotifyPlayback?.disconnect(); _spotifyConnected.value = false }

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
            .add(hashMapOf("userId" to uid, "title" to track.name, "artist" to track.artist,
                "trackId" to track.id, "albumArt" to track.albumArtUrl,
                "spotifyUri" to (track.spotifyUri ?: ""), "timestamp" to Timestamp.now()))
    }

    // ── AI recommendation ────────────────────────────────────────────
    fun processUserMessageWithAI(message: String, onSuccess: (AIResponse) -> Unit, onError: (String) -> Unit) {
        if (!_mlReady.value) { onError("AI not ready"); return }
        viewModelScope.launch {
            try {
                val cs     = _playerState.value
                val result = recommendationEngine?.processRecommendation(message,
                    cs.currentTrack?.let { getSongUri(it) }, cs.currentTrack?.id)
                if (result?.success == true && result.explanation != null) {
                    result.emotionResult?.let { _currentSongEmotion.value = it }
                    onSuccess(AIResponse(
                        result.intentResult?.topIntent ?: "unknown",
                        ((result.intentResult?.confidence ?: 0f) * 100).toInt(),
                        getIntentEmoji(result.intentResult?.topIntent),
                        result.emotionResult?.topEmotion,
                        ((result.emotionResult?.confidence ?: 0f) * 100).toInt(),
                        result.emotionResult?.emoji, result.explanation.text,
                        result.explanation.confidence,
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
            try { recommendationEngine?.preloadEmotions(
                tracks.mapNotNull { try { getSongUri(it) to it.id } catch (_: Exception) { null } }) }
            catch (_: Exception) {}
        }
    }

    // ── Playback ─────────────────────────────────────────────────────
    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val idx = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        stopMediaPlayer(); progressJob?.cancel()
        savePlaybackHistory(track)
        persistTrack(track)

        _playerState.value = _playerState.value.copy(
            currentTrack = track, isPlaying = false, playlist = finalPlaylist,
            currentIndex = idx, duration = track.durationMs, progress = 0f,
            currentPosition = 0L, isLoadingVideo = false, youtubeVideoId = null,
            usingDeezerFallback = false
        )
        _currentSongEmotion.value = null
        _playbackError.value      = null

        if (spotifyPlayback?.isConnected() == true) {
            val uri = track.spotifyUri ?: "spotify:track:${track.id}"
            Log.d(TAG, "Playing via Spotify App Remote: $uri")
            lastPolledTrackUri = uri
            spotifyPlayback?.play(uri)
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startSpotifyPositionPolling()
        } else if (!track.previewUrl.isNullOrEmpty()) {
            Log.d(TAG, "Playing preview URL: ${track.previewUrl}")
            stopSpotifyPositionPolling(); requestAudioFocus(); ensureServiceStarted()
            playPreviewUrl(track.previewUrl!!)
            _playerState.value = _playerState.value.copy(usingDeezerFallback = true)
        } else {
            stopSpotifyPositionPolling()
            _playbackError.value = "Install Spotify app for full playback"
        }
        if (_mlReady.value) analyzeCurrentSongEmotion()
    }

    fun loadTrackWithVideoId(track: Track, knownVideoId: String?) = loadTrack(track)

    // ── Play from AI recommendation ──────────────────────────────────
    private var spotifyMusicRepo: com.example.fypdraft.data.repository.SpotifyMusicRepository? = null
    fun setSpotifyMusicRepo(repo: com.example.fypdraft.data.repository.SpotifyMusicRepository?) { spotifyMusicRepo = repo }

    fun playFromRecommendation(songTitle: String, songArtist: String,
                               onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val repo = spotifyMusicRepo ?: run { onResult(false, "Spotify not connected"); return@launch }
                val results = repo.searchTracks("$songArtist $songTitle", 5)
                if (results.isEmpty()) { onResult(false, "Song not found on Spotify"); return@launch }
                val best = results.firstOrNull { it.name.equals(songTitle, true) && it.artist.equals(songArtist, true) }
                    ?: results.firstOrNull { it.name.contains(songTitle, true) || it.artist.contains(songArtist, true) }
                    ?: results.first()
                loadTrack(best, results); onResult(true, null)
            } catch (e: Exception) { onResult(false, "Error: ${e.message}") }
        }
    }

    // ── Preview URL playback ──────────────────────────────────────────
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
        } catch (e: Exception) { Log.e(TAG, "Preview playback failed", e) }
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
                        val pos = mp.currentPosition.toLong(); val dur = mp.duration.toLong()
                        _playerState.value = _playerState.value.copy(currentPosition = pos,
                            duration = dur, progress = if (dur > 0) pos.toFloat() / dur else 0f)
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
            // If we have a persisted/restored track but Spotify isn't currently
            // on it, call play() which resumes whatever Spotify has, or seekTo(0)
            // could be used. The standard resume() is correct here.
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
            spotifyPlayback?.pause(); stopSpotifyPositionPolling()
        } else {
            try { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() } catch (_: Exception) {}
            progressJob?.cancel()
        }
        _playerState.value = s.copy(isPlaying = false)
        s.currentTrack?.let { updateNotification(it, false) }
    }

    fun playNext() {
        val s = _playerState.value; val next = s.currentIndex + 1
        if (next < s.playlist.size) loadTrack(s.playlist[next], s.playlist)
        else spotifyPlayback?.skipNext()
    }

    fun playPrevious() {
        val s = _playerState.value; val prev = s.currentIndex - 1
        if (prev >= 0) loadTrack(s.playlist[prev], s.playlist)
        else spotifyPlayback?.skipPrevious()
    }

    fun seekTo(progress: Float) {
        val dur = _playerState.value.duration; if (dur <= 0) return
        val ms = (dur * progress).toLong()
        if (spotifyPlayback?.isConnected() == true && !_playerState.value.usingDeezerFallback)
            spotifyPlayback?.seekTo(ms)
        else mediaPlayer?.seekTo(ms.toInt())
        _playerState.value = _playerState.value.copy(progress = progress, currentPosition = ms)
    }

    fun toggleShuffle()     { spotifyPlayback?.toggleShuffle() }
    fun toggleRepeat()      { spotifyPlayback?.toggleRepeat() }
    fun clearPlaybackError(){ _playbackError.value = null; spotifyPlayback?.clearError() }
    fun useDeezerFallback() { /* no-op compat */ }

    // ── Helpers ──────────────────────────────────────────────────────
    private fun getSongUri(track: Track): Uri = Uri.parse(track.previewUrl ?: "")

    private fun getIntentEmoji(i: String?) = when (i) {
        "relax" -> "🧘"; "energize" -> "💪"; "comfort" -> "🤗"; "focus" -> "🎯"
        "discover" -> "🔍"; "nostalgia" -> "💭"; "romance" -> "💕"; "sleep" -> "😴"
        "uplift" -> "🌟"; else -> "🎵"
    }

    private fun generateSuggestedAction(i: String?, e: String?) = when {
        i == "relax"    && e == "calm"      -> "Perfect match! Keep enjoying."
        i == "energize" && e == "energetic" -> "Great choice! Keep the energy up!"
        i == "focus"                        -> "Minimize distractions and focus."
        i == "sleep"                        -> "Dim the lights and drift off."
        i == "comfort"  && e == "sad"       -> "It's okay to feel this way."
        else                                -> "Enjoy the music!"
    }

    private fun generateTips(i: String?) = when (i) {
        "relax"    -> listOf("Deep breaths", "Close your eyes", "Comfortable position")
        "energize" -> listOf("Move to the beat", "Turn it up!", "Channel the energy")
        "focus"    -> listOf("Use headphones", "Minimize distractions", "Break every 25 min")
        "sleep"    -> listOf("Keep volume low", "Sleep timer", "No screens after")
        "comfort"  -> listOf("Feel your emotions", "Music is therapy", "Reach out if needed")
        else       -> listOf("Discover new music", "Create playlists", "Enjoy your day")
    }

    fun cleanupMLModels() {
        viewModelScope.launch(Dispatchers.IO) { try { recommendationEngine?.close() } catch (_: Exception) {} }
    }

    override fun onCleared() {
        super.onCleared()
        stopMediaPlayer(); progressJob?.cancel(); stopSpotifyPositionPolling()
        abandonAudioFocus(); disconnectSpotifyPlayback()
        MusicPlayerService.viewModel = null; cleanupMLModels()
    }

    // No init{} block — track restoration now happens inside
    // connectSpotifyPlayback() via syncWithSpotifyAfterConnect(),
    // so we always have Spotify's live state before showing anything.
}