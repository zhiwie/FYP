package com.example.fypdraft.service

import android.content.Context
import android.util.Log
import com.example.fypdraft.core.config.AppConfig
import com.example.fypdraft.model.Track
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState as SpotifyPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Spotify App Remote for background playback.
 *
 * KEY: showAuthView(false) means Spotify plays music in the background
 * WITHOUT opening the Spotify app UI. MoodSync stays as the visible player.
 *
 * NOTIFICATION FIX: We do NOT subscribe to Spotify's PlayerState via
 * subscribeToPlayerState(). That subscription keeps Spotify's MediaSession
 * active, which causes a duplicate notification. Instead, we poll the
 * player state on-demand when we need position/duration updates.
 */
class SpotifyPlaybackManager(private val context: Context) {

    private val TAG = "SpotifyPlayback"
    private var appRemote: SpotifyAppRemote? = null

    private val _connectionState = MutableStateFlow(SpotifyConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SpotifyConnectionState> = _connectionState.asStateFlow()

    private val _currentPlayerState = MutableStateFlow<SpotifyPlaybackState?>(null)
    val currentPlayerState: StateFlow<SpotifyPlaybackState?> = _currentPlayerState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Connection ───────────────────────────────────────────────────

    fun connect() {
        if (appRemote?.isConnected == true) {
            _connectionState.value = SpotifyConnectionState.CONNECTED
            return
        }

        _connectionState.value = SpotifyConnectionState.CONNECTING

        val params = ConnectionParams.Builder(AppConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(AppConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(false)  // Don't open Spotify UI — background only
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                appRemote = remote
                _connectionState.value = SpotifyConnectionState.CONNECTED
                _error.value = null
                Log.d(TAG, "Connected to Spotify App Remote (background mode)")

                // DO NOT call subscribeToPlayerState() here!
                // That subscription keeps Spotify's own MediaSession active,
                // which causes the duplicate notification.
                // Instead, we poll state on-demand via getPlayerState().
            }

            override fun onFailure(error: Throwable) {
                _connectionState.value = SpotifyConnectionState.FAILED
                val msg = parseError(error)
                _error.value = msg
                Log.e(TAG, "Connection failed: $msg", error)
            }
        })
    }

    fun disconnect() {
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _connectionState.value = SpotifyConnectionState.DISCONNECTED
        _currentPlayerState.value = null
    }

    fun isConnected(): Boolean = appRemote?.isConnected == true

    // ── Playback ─────────────────────────────────────────────────────

    fun play(spotifyUri: String) {
        val remote = appRemote
        if (remote == null || !remote.isConnected) {
            _error.value = "Not connected to Spotify"
            connect()
            return
        }

        remote.playerApi.play(spotifyUri)
            ?.setResultCallback {
                Log.d(TAG, "Playing: $spotifyUri")
                _error.value = null
                // Poll state once after play starts to get duration
                pollPlayerState()
            }
            ?.setErrorCallback { err ->
                _error.value = parsePlaybackError(err)
                Log.e(TAG, "Play failed: $spotifyUri", err)
            }
    }

    fun playTrack(track: Track) {
        play(track.spotifyUri ?: "spotify:track:${track.id}")
    }

    fun pause() {
        appRemote?.playerApi?.pause()
            ?.setResultCallback {
                Log.d(TAG, "Paused")
                pollPlayerState()
            }
            ?.setErrorCallback { Log.e(TAG, "Pause failed", it) }
    }

    fun resume() {
        appRemote?.playerApi?.resume()
            ?.setResultCallback {
                Log.d(TAG, "Resumed")
                pollPlayerState()
            }
            ?.setErrorCallback { Log.e(TAG, "Resume failed", it) }
    }

    fun skipNext() {
        appRemote?.playerApi?.skipNext()
            ?.setResultCallback {
                Log.d(TAG, "Skipped next")
                pollPlayerState()
            }
            ?.setErrorCallback { Log.e(TAG, "Skip next failed", it) }
    }

    fun skipPrevious() {
        appRemote?.playerApi?.skipPrevious()
            ?.setResultCallback {
                Log.d(TAG, "Skipped previous")
                pollPlayerState()
            }
            ?.setErrorCallback { Log.e(TAG, "Skip prev failed", it) }
    }

    fun seekTo(positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs)
            ?.setResultCallback { Log.d(TAG, "Seeked to $positionMs") }
            ?.setErrorCallback { Log.e(TAG, "Seek failed", it) }
    }

    fun toggleShuffle() {
        val cur = _currentPlayerState.value?.isShuffling ?: false
        appRemote?.playerApi?.setShuffle(!cur)
    }

    fun toggleRepeat() {
        appRemote?.playerApi?.getPlayerState()?.setResultCallback { state ->
            val next = when (state.playbackOptions.repeatMode) {
                0 -> 1; 1 -> 2; else -> 0
            }
            appRemote?.playerApi?.setRepeat(next)
        }
    }

    fun clearError() { _error.value = null }

    // ── On-demand state polling (NO subscription) ────────────────────

    /**
     * Poll Spotify's player state once. This does NOT keep Spotify's
     * MediaSession active — it's a one-shot query.
     *
     * We use this instead of subscribeToPlayerState() which would keep
     * Spotify's notification alive.
     */
    fun pollPlayerState() {
        appRemote?.playerApi?.playerState?.setResultCallback { state ->
            val track = state.track ?: return@setResultCallback
            _currentPlayerState.value = SpotifyPlaybackState(
                trackName = track.name,
                artistName = track.artist.name,
                albumName = track.album.name,
                albumArtUri = track.imageUri?.raw ?: "",
                trackUri = track.uri,
                durationMs = track.duration,
                positionMs = state.playbackPosition,
                isPaused = state.isPaused,
                isShuffling = state.playbackOptions.isShuffling,
                repeatMode = state.playbackOptions.repeatMode
            )
        }?.setErrorCallback { err ->
            Log.w(TAG, "Failed to poll player state", err)
        }
    }

    /**
     * Start periodic polling of player state for position updates.
     * Call this when playing, stop when paused.
     * The caller (ViewModel) manages the coroutine lifecycle.
     */
    fun getPosition(callback: (Long, Long, Boolean) -> Unit) {
        appRemote?.playerApi?.playerState?.setResultCallback { state ->
            callback(
                state.playbackPosition,
                state.track?.duration ?: 0L,
                state.isPaused
            )
        }
    }

    // ── Error parsing ────────────────────────────────────────────────

    private fun parseError(error: Throwable): String {
        val msg = error.message ?: error.toString()
        return when {
            msg.contains("NotInstalled", true) || msg.contains("CouldNotFind", true) ->
                "Spotify app is not installed. Please install from Play Store."
            msg.contains("NotLoggedIn", true) ->
                "Please log into the Spotify app first."
            msg.contains("UserNotAuthorized", true) ->
                "Please authorize MoodSync in Spotify."
            msg.contains("PREMIUM_REQUIRED", true) || msg.contains("premium", true) ->
                "Spotify Premium required for full playback."
            else -> "Spotify connection failed: $msg"
        }
    }

    private fun parsePlaybackError(error: Throwable): String {
        val msg = error.message ?: error.toString()
        return when {
            msg.contains("PREMIUM_REQUIRED", true) || msg.contains("premium", true) ->
                "Spotify Premium is required for on-demand playback."
            msg.contains("CONTENT_NOT_AVAILABLE", true) ->
                "This track is not available in your region."
            else -> "Playback error: $msg"
        }
    }
}

// ── Data classes ──────────────────────────────────────────────────────

enum class SpotifyConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

data class SpotifyPlaybackState(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val albumArtUri: String,
    val trackUri: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val isShuffling: Boolean,
    val repeatMode: Int
)