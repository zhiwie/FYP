package com.example.fypdraft.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.ml.MetadataEmotionTagger
import com.example.fypdraft.model.PlayerState
import com.example.fypdraft.model.Track
import com.example.fypdraft.model.TrackMood
import com.example.fypdraft.service.MusicPlayerService
import com.example.fypdraft.service.SpotifyConnectionState
import com.example.fypdraft.service.SpotifyPlaybackManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.fypdraft.ml.MoodPipeline
import com.example.fypdraft.model.MoodResult

/**
 * MusicPlayerViewModel
 *
 * Playback: Spotify App Remote → Deezer 30-s preview → error.
 *
 * Mood tagging:
 *   • Spotify tracks: Deezer search fetches bpm + gain + genre_id → instant rule-based tag.
 *   • Deezer-only tracks: features are captured during searchDeezer() — zero extra calls.
 *   • No TFLite model. No audio download. No AudioEmotionTagger. No AudioPreprocessor.
 *
 * The [currentMood] StateFlow replaces the old currentSongEmotion flow.
 * [isAnalyzingEmotion] is kept for UI compatibility but is always false
 * (tagging is synchronous / <1 ms).
 */
class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MusicPlayerVM"

    // ── Public state ─────────────────────────────────────────────────

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    /** Current track mood — set synchronously when a track loads. Never null after first track. */
    private val _currentMood = MutableStateFlow<MoodResult?>(null)
    val currentMood: StateFlow<MoodResult?> = _currentMood.asStateFlow()

    /**
     * Kept for UI compatibility with MusicPlayerScreen / HomeScreen.
     * Always emits false — tagging is instant, never shows a spinner.
     */
    private val _isAnalyzingEmotion = MutableStateFlow(false)
    val isAnalyzingEmotion: StateFlow<Boolean> = _isAnalyzingEmotion.asStateFlow()

    private val _spotifyConnected = MutableStateFlow(false)
    val spotifyConnected: StateFlow<Boolean> = _spotifyConnected.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    // ── Internal ─────────────────────────────────────────────────────

    private var mediaPlayer:          MediaPlayer?           = null
    private var positionJob:          Job?                   = null
    private var musicService:         MusicPlayerService?    = null
    private var spotifyPlaybackManager: SpotifyPlaybackManager? = null
    private var spotifyMusicRepo:     SpotifyMusicRepository? = null
    private var isServiceBound = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Last-track persistence (SharedPreferences) ───────────────────

    private val prefs: SharedPreferences =
        application.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

    /** Persist track so the mini-player can show it next app launch. */
    private fun saveLastTrack(track: Track) {
        prefs.edit()
            .putString("last_track_id",          track.id)
            .putString("last_track_name",         track.name)
            .putString("last_track_artist",       track.artist)
            .putString("last_track_album",        track.album)
            .putString("last_track_art_url",      track.albumArtUrl)
            .putString("last_track_spotify_uri",  track.spotifyUri ?: "")
            .putLong  ("last_track_duration_ms",  track.durationMs)
            .apply()
    }

    /** Restore the last played track from SharedPreferences, if any. */
    private fun restoreLastTrack(): Track? {
        val name = prefs.getString("last_track_name", null) ?: return null
        val id   = prefs.getString("last_track_id",   null) ?: return null
        return Track(
            id          = id,
            name        = name,
            artist      = prefs.getString("last_track_artist",      "") ?: "",
            album       = prefs.getString("last_track_album",       "") ?: "",
            albumArtUrl = prefs.getString("last_track_art_url",     "") ?: "",
            previewUrl  = null,
            durationMs  = prefs.getLong  ("last_track_duration_ms",  0L),
            spotifyUri  = prefs.getString("last_track_spotify_uri", "")
                .takeIf { !it.isNullOrBlank() }
        )
    }

    init {
        // Restore last played track so the mini-player is never empty
        restoreLastTrack()?.let { last ->
            _playerState.value = _playerState.value.copy(
                currentTrack = last,
                isPlaying    = false
            )
            Log.d(TAG, "Restored last track: '${last.name}' by ${last.artist}")
        }
    }

    // ── Service binding ──────────────────────────────────────────────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? MusicPlayerService.MusicBinder ?: return
            musicService = b.getService()
            MusicPlayerService.viewModel = this@MusicPlayerViewModel
            isServiceBound = true
            Log.d(TAG, "MusicPlayerService connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isServiceBound = false
        }
    }

    fun bindMusicService(context: Context) {
        MusicPlayerService.startService(context)
        context.bindService(
            Intent(context, MusicPlayerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbindMusicService(context: Context) {
        if (isServiceBound) {
            try { context.unbindService(serviceConnection) } catch (_: Exception) {}
            isServiceBound = false
            MusicPlayerService.viewModel = null
        }
    }

    // ── Spotify App Remote ───────────────────────────────────────────

    fun connectSpotifyPlayback(context: Context) {
        if (spotifyPlaybackManager == null)
            spotifyPlaybackManager = SpotifyPlaybackManager(context)
        spotifyPlaybackManager?.connect()
        viewModelScope.launch {
            spotifyPlaybackManager?.connectionState?.collect { state ->
                _spotifyConnected.value = (state == SpotifyConnectionState.CONNECTED)
                Log.d(TAG, "Spotify connection state: $state")
            }
        }
    }

    fun disconnectSpotifyPlayback() {
        spotifyPlaybackManager?.disconnect()
        _spotifyConnected.value = false
    }

    fun setSpotifyMusicRepo(repo: SpotifyMusicRepository) {
        spotifyMusicRepo = repo
    }

    // ── ML model lifecycle (no-op — kept for call-site compatibility) ─

    /**
     * Called from MainActivity. Previously initialised TFLite models.
     * Now a no-op — metadata tagging needs no model loading.
     */
    fun initializeMLModels() {
        Log.d(TAG, "initializeMLModels: metadata-only mode, nothing to load")
    }

    /** Called from MainActivity.onDestroy. No-op in metadata-only mode. */
    fun cleanupMLModels() {
        Log.d(TAG, "cleanupMLModels: nothing to clean up")
    }

    // ── Track loading ────────────────────────────────────────────────

    /**
     * Load [track] and begin playback. Mood is tagged synchronously from the
     * track's own [Track.mood] field (set at parse time). If the track was
     * created without features (e.g. a Spotify result with no Deezer lookup
     * yet) a background Deezer fetch resolves the mood within ~200 ms.
     */
    fun loadTrack(track: Track, playlist: List<Track> = listOf(track)) {
        saveLastTrack(track)   // persist so mini-player always shows the last song
        val index = playlist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _playerState.value = _playerState.value.copy(
            currentTrack     = track,
            playlist         = playlist,
            currentIndex     = index,
            isPlaying        = false,
            progress         = 0f,
            currentPosition  = 0L,
            duration         = track.durationMs.coerceAtLeast(0L)
        )
        _playbackError.value = null

        // ── Mood — full pipeline (metadata sync + optional GPT async) ────
        val existingMood = track.mood
        val existingConf = track.moodConfidence
        if (existingMood != TrackMood.NEUTRAL
            && existingConf >= MoodPipeline.METADATA_CONFIDENCE_THRESHOLD) {
            // Track was already tagged with high confidence at parse time — use it instantly
            _currentMood.value = MoodResult(
                mood       = existingMood,
                confidence = existingConf,
                source     = track.moodSource,
                reason     = track.moodReason
            )
            Log.d(TAG, "⚡ Mood reused: ${existingMood.emoji} ${existingMood.label} " +
                    "(${(existingConf * 100).toInt()}%) [${track.moodSource}] | '${track.name}'")
        } else {
            // Low confidence or NEUTRAL — run full pipeline (may call GPT) in background
            _currentMood.value = null
            viewModelScope.launch(Dispatchers.IO) {
                val result = MoodPipeline.tagFull(track)
                withContext(Dispatchers.Main) {
                    if (_playerState.value.currentTrack?.id == track.id) {
                        _currentMood.value = result
                        Log.d(TAG, "⚡ Mood resolved: ${result.mood.emoji} ${result.mood.label} " +
                                "(${(result.confidence * 100).toInt()}%) [${result.source}] | '${track.name}'")
                    }
                }
            }
        }

        // ── Playback ──────────────────────────────────────────────────
        val spotifyUri = track.spotifyUri
        if (_spotifyConnected.value && !spotifyUri.isNullOrBlank()) {
            playViaSpotify(track, spotifyUri)
        } else {
            playViaPreview(track)
        }
    }

    /**
     * Search-by-name entry point used by EmotionChat, Friends, Library, Search screens.
     */
    fun playFromRecommendation(
        title:    String,
        artist:   String,
        callback: (Boolean, Track?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                val repo = spotifyMusicRepo
                if (repo != null) {
                    val results = repo.searchTracks("$title $artist", 5)
                    val match   = results.firstOrNull {
                        it.name.contains(title, ignoreCase = true) ||
                                it.artist.contains(artist, ignoreCase = true)
                    } ?: results.firstOrNull()
                    if (match != null) {
                        loadTrack(match, results)
                        callback(true, match)
                        return@launch
                    }
                }
                val deezerTrack = searchDeezer("$title $artist")
                if (deezerTrack != null) {
                    loadTrack(deezerTrack)
                    callback(true, deezerTrack)
                    return@launch
                }
                _playbackError.value = "Could not find '$title'"
                callback(false, null)
            } catch (e: Exception) {
                Log.e(TAG, "playFromRecommendation failed", e)
                _playbackError.value = "Playback failed: ${e.message}"
                callback(false, null)
            }
        }
    }

    // ── Mood tagging helpers ─────────────────────────────────────────

    /**
     * Fetch Deezer bpm + gain + genre for a track and return its mood.
     * Called only when a track arrives without pre-computed features.
     * ~150–250 ms network round-trip. Returns [TrackMood.NEUTRAL] on failure.
     */
    // ── Playback via Spotify App Remote ─────────────────────────────

    private fun playViaSpotify(track: Track, uri: String) {
        spotifyPlaybackManager?.playTrack(track)
        _playerState.value = _playerState.value.copy(isPlaying = true)
        updateNotification(track, true)
        startSpotifyPositionPolling()
    }

    private fun startSpotifyPositionPolling() {
        positionJob?.cancel()
        val mgr = spotifyPlaybackManager ?: return
        positionJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                mgr.pollPlayerState()
                val sps = mgr.currentPlayerState.value ?: continue
                val dur = _playerState.value.duration.coerceAtLeast(1L)
                _playerState.value = _playerState.value.copy(
                    currentPosition = sps.positionMs,
                    duration        = sps.durationMs.takeIf { it > 0 } ?: dur,
                    progress        = (sps.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                    isPlaying       = !sps.isPaused
                )
            }
        }
    }

    // ── Playback via Deezer 30-second preview ────────────────────────

    private fun playViaPreview(track: Track) {
        viewModelScope.launch {
            val previewUrl = resolvePreviewUrl(track)
            if (previewUrl.isNullOrBlank()) {
                _playbackError.value = "No preview available for \"${track.name}\""
                return@launch
            }
            withContext(Dispatchers.Main) { setupMediaPlayer(previewUrl, track) }
            // ── No audio emotion analysis — removed entirely ──────────
        }
    }

    private suspend fun resolvePreviewUrl(track: Track): String? {
        if (!track.previewUrl.isNullOrBlank()) return track.previewUrl
        return searchDeezer("${track.name} ${track.artist}")?.previewUrl
    }

    private fun setupMediaPlayer(url: String, track: Track) {
        releaseMediaPlayer()
        _playbackError.value = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = true,
                        duration  = duration.toLong().coerceAtLeast(0L)
                    )
                    start()
                    updateNotification(track, true)
                    startPositionTracking()
                }
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(isPlaying = false, progress = 1f)
                    updateNotification(track, false)
                    positionJob?.cancel()
                    val st   = _playerState.value
                    val next = st.currentIndex + 1
                    if (next < st.playlist.size) loadTrack(st.playlist[next], st.playlist)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _playbackError.value = "Playback error (code $what)"
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer setup failed", e)
            _playbackError.value = "Could not load preview: ${e.message}"
        }
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val mp = mediaPlayer ?: break
                try {
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        val dur = mp.duration.toLong().coerceAtLeast(1L)
                        _playerState.value = _playerState.value.copy(
                            currentPosition = pos,
                            duration        = dur,
                            progress        = (pos.toFloat() / dur).coerceIn(0f, 1f)
                        )
                    }
                } catch (_: IllegalStateException) { break }
            }
        }
    }

    // ── Deezer search (used for playback fallback) ───────────────────

    /**
     * Search Deezer for a playable track. Also tags mood from the result's
     * bpm / gain / genre so the returned [Track] already has [Track.mood] set.
     */
    private suspend fun searchDeezer(query: String): Track? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val body    = httpClient.newCall(
                Request.Builder()
                    .url("https://api.deezer.com/search?q=$encoded&limit=5")
                    .build()
            ).execute().body?.string() ?: return@withContext null

            val data = JSONObject(body).optJSONArray("data") ?: return@withContext null

            for (i in 0 until data.length()) {
                val item    = data.getJSONObject(i)
                val preview = item.optString("preview", "")
                if (preview.isBlank() || preview == "null") continue

                val album   = item.optJSONObject("album")
                val artist  = item.optJSONObject("artist")
                val trackName  = item.optString("title", "Unknown")
                val artistName = item.optJSONObject("artist")?.optString("name", "Unknown") ?: "Unknown"
                val albumName  = album?.optString("title", "") ?: ""
                val moodResult = MoodPipeline.tagSync(trackName, artistName, albumName)

                return@withContext Track(
                    id             = item.optString("id", "deezer_$i"),
                    name           = trackName,
                    artist         = artistName,
                    album          = albumName,
                    albumArtUrl    = album?.optString("cover_medium", "") ?: "",
                    previewUrl     = preview,
                    durationMs     = item.optInt("duration", 30) * 1000L,
                    spotifyUri     = null,
                    mood           = moodResult.mood,
                    moodConfidence = moodResult.confidence,
                    moodSource     = moodResult.source,
                    moodReason     = moodResult.reason
                )
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "searchDeezer failed: ${e.message}")
            null
        }
    }

    // ── Playback controls ────────────────────────────────────────────

    fun play() {
        val track = _playerState.value.currentTrack ?: return
        if (_spotifyConnected.value && !track.spotifyUri.isNullOrBlank()) {
            spotifyPlaybackManager?.resume()
            _playerState.value = _playerState.value.copy(isPlaying = true)
        } else {
            mediaPlayer?.let {
                try {
                    it.start()
                    _playerState.value = _playerState.value.copy(isPlaying = true)
                    startPositionTracking()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaPlayer.start() failed", e)
                }
            } ?: playViaPreview(track)
        }
        updateNotification(track, true)
    }

    fun pause() {
        val track = _playerState.value.currentTrack
        if (_spotifyConnected.value && !track?.spotifyUri.isNullOrBlank()) {
            spotifyPlaybackManager?.pause()
        } else {
            try { mediaPlayer?.pause() } catch (_: Exception) {}
        }
        _playerState.value = _playerState.value.copy(isPlaying = false)
        positionJob?.cancel()
        if (track != null) updateNotification(track, false)
    }

    fun togglePlayPause() { if (_playerState.value.isPlaying) pause() else play() }

    fun playNext() {
        val st = _playerState.value
        val next = if (st.currentIndex < st.playlist.size - 1) st.currentIndex + 1 else return
        loadTrack(st.playlist[next], st.playlist)
    }

    fun playPrevious() {
        val st = _playerState.value
        if (st.currentPosition > 3000L) { seekTo(0f); return }
        val prev = if (st.currentIndex > 0) st.currentIndex - 1 else return
        loadTrack(st.playlist[prev], st.playlist)
    }

    fun seekTo(progress: Float) {
        val st    = _playerState.value
        val track = st.currentTrack ?: return
        val p     = progress.coerceIn(0f, 1f)
        if (_spotifyConnected.value && !track.spotifyUri.isNullOrBlank()) {
            spotifyPlaybackManager?.seekTo((p * st.duration).toLong())
        } else {
            try {
                val mp    = mediaPlayer ?: return
                val posMs = (p * mp.duration).toInt()
                mp.seekTo(posMs)
                _playerState.value = st.copy(
                    progress        = p,
                    currentPosition = posMs.toLong()
                )
            } catch (_: Exception) {}
        }
    }

    fun toggleShuffle() { spotifyPlaybackManager?.toggleShuffle() }
    fun toggleRepeat()  { spotifyPlaybackManager?.toggleRepeat()  }

    // ── Notification ─────────────────────────────────────────────────

    private fun updateNotification(track: Track, isPlaying: Boolean) {
        musicService?.updateNotificationWithArt(
            title       = track.name,
            artist      = track.artist,
            isPlaying   = isPlaying,
            albumArtUrl = track.albumArtUrl.takeIf { it.isNotBlank() }
        )
    }
    /**
     * Polls Spotify's current player state and syncs it into [playerState].
     * Called periodically by MiniMusicPlayer (every 5s) so the bar always
     * shows the real currently-playing track, not just what the app loaded.
     *
     * Safe to call even when not connected — SpotifyPlaybackManager guards it.
     */
    fun refreshFromSpotify() {
        spotifyPlaybackManager?.getPlayerInfo { spotifyState ->
            val currentUri  = _playerState.value.currentTrack?.spotifyUri
            val incomingUri = "spotify:track:${spotifyState.trackUri.substringAfterLast(":")}"

            // Only update if the track actually changed — avoids unnecessary recomposition
            if (spotifyState.trackUri.isNotBlank() && spotifyState.trackUri != currentUri) {
                // Build a Track from the Spotify state
                val updatedTrack = com.example.fypdraft.model.Track(
                    id           = spotifyState.trackUri.substringAfterLast(":"),
                    name         = spotifyState.trackName,
                    artist       = spotifyState.artistName,
                    album        = spotifyState.albumName,
                    albumArtUrl  = "", // album art resolved separately via AppRemote image API
                    previewUrl   = null,
                    durationMs   = spotifyState.durationMs,
                    spotifyUri   = spotifyState.trackUri
                )
                _playerState.value = _playerState.value.copy(
                    currentTrack = updatedTrack,
                    isPlaying    = !spotifyState.isPaused,
                    duration     = spotifyState.durationMs,
                    currentPosition = spotifyState.positionMs
                )
            } else {
                // Same track — just sync play state + position
                _playerState.value = _playerState.value.copy(
                    isPlaying       = !spotifyState.isPaused,
                    currentPosition = spotifyState.positionMs
                )
            }
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    private fun releaseMediaPlayer() {
        positionJob?.cancel()
        try { mediaPlayer?.stop()    } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseMediaPlayer()
        disconnectSpotifyPlayback()
    }
}