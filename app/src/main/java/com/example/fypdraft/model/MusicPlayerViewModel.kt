package com.example.fypdraft.model

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.YouTubeMusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playlist: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val youtubeVideoId: String? = null,
    val isLoadingVideo: Boolean = false,
    val usingDeezerFallback: Boolean = false // NEW
)

class MusicPlayerViewModel(context: Context) : ViewModel() {

    private val youtubeRepository = YouTubeMusicRepository()
    private var mediaPlayer: MediaPlayer? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val currentIndex = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // Debug logging
        android.util.Log.d("MusicPlayer", "Loading track: ${track.name} by ${track.artist}")
        android.util.Log.d("MusicPlayer", "Track preview URL: ${track.previewUrl}")
        android.util.Log.d("MusicPlayer", "Preview URL is ${if (track.previewUrl.isNullOrEmpty()) "EMPTY" else "VALID"}")

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
                android.util.Log.d("MusicPlayer", "No YouTube found, auto-starting Deezer preview")
                playDeezerPreview(track.previewUrl)
            }
        }
    }

    fun useDeezerFallback() {
        val currentTrack = _playerState.value.currentTrack
        android.util.Log.d("MusicPlayer", "Attempting Deezer fallback for: ${currentTrack?.name}")
        android.util.Log.d("MusicPlayer", "Preview URL: ${currentTrack?.previewUrl}")

        if (currentTrack != null && !currentTrack.previewUrl.isNullOrEmpty()) {
            _playerState.value = _playerState.value.copy(
                usingDeezerFallback = true,
                youtubeVideoId = null
            )
            playDeezerPreview(currentTrack.previewUrl)
        } else {
            android.util.Log.e("MusicPlayer", "No preview URL available for fallback")
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

                setOnErrorListener { mp, what, extra ->
                    android.util.Log.e("MusicPlayer", "MediaPlayer error: what=$what, extra=$extra")
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
            android.util.Log.e("MusicPlayer", "Error playing Deezer preview", e)
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

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}