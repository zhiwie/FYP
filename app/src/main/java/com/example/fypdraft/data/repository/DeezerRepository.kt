package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.data.api.DeezerApiService
import com.example.fypdraft.data.api.toAppTrack
import com.example.fypdraft.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeezerRepository {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    companion object {
        private const val TAG = "DeezerRepository"
        private const val BASE_URL = "https://api.deezer.com/"
    }

    private val api: DeezerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeezerApiService::class.java)
    }

    suspend fun searchTracks(query: String): List<Track> {
        if (query.isBlank()) return emptyList()

        _isLoading.value = true
        return try {
            val response = api.search(query = query, limit = 50)
            val tracks = response.data?.map { it.toAppTrack() } ?: emptyList()
            Log.d(TAG, "Found ${tracks.size} tracks for: $query")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error searching tracks", e)
            emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun getTopTracks(): List<Track> {
        _isLoading.value = true
        return try {
            val response = api.getChart()
            val tracks = response.tracks?.data?.map { it.toAppTrack() } ?: emptyList()
            Log.d(TAG, "Got ${tracks.size} top tracks")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top tracks", e)
            emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun getPlaylistByMood(mood: String): List<Track> {
        val searchTerm = when (mood.lowercase()) {
            "happy", "energetic" -> "happy upbeat dance"
            "sad", "melancholy" -> "sad emotional ballad"
            "calm", "relaxed" -> "calm peaceful ambient"
            "workout", "gym" -> "workout motivation energy"
            "focus", "study" -> "focus instrumental concentration"
            "party" -> "party dance club"
            "romantic" -> "romantic love songs"
            "chill" -> "chill relaxing lounge"
            else -> mood
        }

        return searchTracks(searchTerm)
    }
}