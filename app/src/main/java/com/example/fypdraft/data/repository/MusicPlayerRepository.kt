package com.example.fypdraft.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.fypdraft.model.PlayerState
import com.example.fypdraft.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayerRepository(private val context: Context) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val TAG = "MusicPlayerRepository"

    init {
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            setOnCompletionListener {
                Log.d(TAG, "Track completed")
                playNext()
            }

            setOnPreparedListener {
                Log.d(TAG, "MediaPlayer prepared")
                val duration = it.duration.toLong()
                _playerState.value = _playerState.value.copy(
                    duration = duration,
                    isPlaying = false
                )
            }

            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                _playerState.value = _playerState.value.copy(
                    isPlaying = false
                )
                true
            }
        }
    }

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        try {
            // Stop current playback
            mediaPlayer?.stop()
            mediaPlayer?.reset()

            val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
            val currentIndex = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

            _playerState.value = PlayerState(
                currentTrack = track,
                isPlaying = false,
                progress = 0f,
                currentPosition = 0L,
                duration = track.durationMs,
                playlist = finalPlaylist,
                currentIndex = currentIndex
            )

            // Only prepare if preview URL exists
            if (!track.previewUrl.isNullOrEmpty()) {
                mediaPlayer?.apply {
                    setDataSource(track.previewUrl)
                    prepareAsync()
                }
                Log.d(TAG, "Loading track: ${track.name} with preview")
            } else {
                Log.w(TAG, "Track ${track.name} has no preview URL")
                _playerState.value = _playerState.value.copy(
                    duration = track.durationMs
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading track", e)
        }
    }

    fun play() {
        try {
            val currentTrack = _playerState.value.currentTrack
            if (currentTrack?.previewUrl.isNullOrEmpty()) {
                Log.w(TAG, "Cannot play: no preview URL")
                return
            }

            mediaPlayer?.start()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            Log.d(TAG, "Playing: ${currentTrack?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            Log.d(TAG, "Paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing track", e)
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(position: Long) {
        try {
            mediaPlayer?.seekTo(position.toInt())
            _playerState.value = _playerState.value.copy(
                currentPosition = position,
                progress = position.toFloat() / _playerState.value.duration.toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun getCurrentPosition(): Long {
        return try {
            val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
            _playerState.value = _playerState.value.copy(
                currentPosition = position,
                progress = if (_playerState.value.duration > 0) {
                    position.toFloat() / _playerState.value.duration.toFloat()
                } else 0f
            )
            position
        } catch (e: Exception) {
            Log.e(TAG, "Error getting position", e)
            0L
        }
    }

    fun playNext() {
        val currentState = _playerState.value
        val nextIndex = currentState.currentIndex + 1

        if (nextIndex < currentState.playlist.size) {
            val nextTrack = currentState.playlist[nextIndex]
            loadTrack(nextTrack, currentState.playlist)
            play()
            Log.d(TAG, "Playing next: ${nextTrack.name}")
        } else {
            Log.d(TAG, "No next track available")
        }
    }

    fun playPrevious() {
        val currentState = _playerState.value
        val previousIndex = currentState.currentIndex - 1

        if (previousIndex >= 0) {
            val previousTrack = currentState.playlist[previousIndex]
            loadTrack(previousTrack, currentState.playlist)
            play()
            Log.d(TAG, "Playing previous: ${previousTrack.name}")
        } else {
            Log.d(TAG, "No previous track available")
        }
    }

    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d(TAG, "MediaPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }
    }
}