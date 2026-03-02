package com.example.fypdraft.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FavoritesViewModel : ViewModel() {

    private val repository = FavoritesRepository()

    private val _favorites = MutableStateFlow<List<Pair<String, SongRecommendation>>>(emptyList())
    val favorites: StateFlow<List<Pair<String, SongRecommendation>>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            try {
                repository.observeFavorites()
                    .catch { e -> _message.value = "Failed to load favorites: ${e.message}" }
                    .collect { list -> _favorites.value = list }
            } catch (e: Exception) {
                _message.value = "Not logged in"
            }
        }
    }

    fun addFavorite(song: SongRecommendation) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveFavorite(song)
                _message.value = "Added to favorites ❤️"
            } catch (e: Exception) {
                _message.value = "Failed to save: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeFavorite(songId: String) {
        viewModelScope.launch {
            try {
                repository.removeFavorite(songId)
                _message.value = "Removed from favorites"
            } catch (e: Exception) {
                _message.value = "Failed to remove: ${e.message}"
            }
        }
    }

    fun isFavorite(title: String, artist: String): Boolean {
        return _favorites.value.any { (_, song) ->
            song.title == title && song.artist == artist
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}