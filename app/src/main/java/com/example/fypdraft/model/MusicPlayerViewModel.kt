package com.example.fypdraft.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.MusicPlayerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerViewModel(context: Context) : ViewModel() {

    private val repository = MusicPlayerRepository(context)
    val playerState: StateFlow<PlayerState> = repository.playerState

    private var progressJob: Job? = null

    init {
        startProgressTracking()
    }

    fun loadTrack(track: Track, playlist: List<Track> = emptyList()) {
        repository.loadTrack(track, playlist)
    }

    fun togglePlayPause() {
        repository.togglePlayPause()
    }

    fun play() {
        repository.play()
    }

    fun pause() {
        repository.pause()
    }

    fun seekTo(progress: Float) {
        val duration = playerState.value.duration
        val position = (duration * progress).toLong()
        repository.seekTo(position)
    }

    fun playNext() {
        repository.playNext()
    }

    fun playPrevious() {
        repository.playPrevious()
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
                        // Update will be handled by repository
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