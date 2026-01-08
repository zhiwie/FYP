package com.example.fypdraft.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.YouTubeMusicRepository
import com.example.fypdraft.data.repository.MusicPlayerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Enhanced PlayerState with YouTube support
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playlist: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val youtubeVideoId: String? = null,  // NEW: YouTube video ID
    val isLoadingVideo: Boolean = false   // NEW: Loading state
)

class MusicPlayerViewModel(context: Context) : ViewModel() {

    private val repository = MusicPlayerRepository(context)
    private val youtubeRepository = YouTubeMusicRepository()

    // Use custom PlayerState instead of repository's state
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null

    init {
        startProgressTracking()
        // Observe repository state changes
        observeRepositoryState()
    }

    private fun observeRepositoryState() {
        viewModelScope.launch {
            repository.playerState.collect { repoState ->
                // Sync with repository state but keep YouTube fields
                _playerState.value = _playerState.value.copy(
                    isPlaying = repoState.isPlaying,
                    progress = repoState.progress,
                    currentPosition = repoState.currentPosition,
                    duration = repoState.duration
                )
            }
        }
    }

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        val finalPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val currentIndex = finalPlaylist.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // Update state immediately
        _playerState.value = PlayerState(
            currentTrack = track,
            isPlaying = false,
            playlist = finalPlaylist,
            currentIndex = currentIndex,
            duration = track.durationMs,
            isLoadingVideo = true
        )

        // Load into repository for preview playback (if available)
        repository.loadTrack(track, finalPlaylist)

        // Load YouTube video ID for full playback
        viewModelScope.launch {
            val videoId = youtubeRepository.getYouTubeVideoId(track)
            _playerState.value = _playerState.value.copy(
                youtubeVideoId = videoId,
                isLoadingVideo = false
            )
        }
    }

    fun togglePlayPause() {
        repository.togglePlayPause()
        _playerState.value = _playerState.value.copy(
            isPlaying = !_playerState.value.isPlaying
        )
    }

    fun play() {
        repository.play()
        _playerState.value = _playerState.value.copy(isPlaying = true)
    }

    fun pause() {
        repository.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun seekTo(progress: Float) {
        val duration = playerState.value.duration
        val position = (duration * progress).toLong()
        repository.seekTo(position)
        _playerState.value = _playerState.value.copy(
            currentPosition = position,
            progress = progress
        )
    }

    fun playNext() {
        val currentState = _playerState.value
        val nextIndex = currentState.currentIndex + 1

        if (nextIndex < currentState.playlist.size) {
            val nextTrack = currentState.playlist[nextIndex]
            loadTrack(nextTrack, currentState.playlist)
            play()
        }
    }

    fun playPrevious() {
        val currentState = _playerState.value
        val previousIndex = currentState.currentIndex - 1

        if (previousIndex >= 0) {
            val previousTrack = currentState.playlist[previousIndex]
            loadTrack(previousTrack, currentState.playlist)
            play()
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (playerState.value.isPlaying) {
                    val currentPos = repository.getCurrentPosition()
                    val duration = playerState.value.duration
                    if (duration > 0) {
                        val progress = currentPos.toFloat() / duration.toFloat()
                        _playerState.value = _playerState.value.copy(
                            currentPosition = currentPos,
                            progress = progress
                        )
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        repository.release()
    }
}