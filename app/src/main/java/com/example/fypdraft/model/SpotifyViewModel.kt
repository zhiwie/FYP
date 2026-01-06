package com.example.fypdraft.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.data.repository.SpotifyAuthState
import com.example.fypdraft.data.repository.MockTrack
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpotifyViewModel(context: Context) : ViewModel() {
    private val repository = SpotifyRepository(context)

    val authState: StateFlow<SpotifyAuthState> = repository.authState

    fun connectSpotify() {
        viewModelScope.launch {
            repository.initiateSpotifyAuth()
        }
    }

    fun isSpotifyConnected(): Boolean {
        return repository.isAuthenticated()
    }

    fun disconnectSpotify() {
        repository.signOut()
    }

    fun getUserTopTracks(): List<MockTrack> {
        return repository.getMockUserTopTracks()
    }
}