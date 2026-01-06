package com.example.fypdraft.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpotifyAuthState(
    val isAuthenticated: Boolean = false,
    val accessToken: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

data class MockTrack(
    val name: String,
    val artist: String,
    val album: String
)

class SpotifyRepository(private val context: Context) {

    private val _authState = MutableStateFlow(SpotifyAuthState())
    val authState: StateFlow<SpotifyAuthState> = _authState

    companion object {
        private const val TAG = "SpotifyRepository"
        private const val PREFS_NAME = "spotify_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_CONNECTED = "is_connected"

        // TODO: When Spotify allows new app registration, add real credentials:
        // 1. Go to https://developer.spotify.com/dashboard
        // 2. Create new app with redirect URI: fypdraft://callback
        // 3. Copy Client ID and paste below
        // 4. Uncomment the real implementation
        private const val USE_MOCK = true // Set to false when you have real credentials
    }

    // Mock authentication - simulates Spotify login
    suspend fun initiateSpotifyAuth(): Boolean {
        if (USE_MOCK) {
            _authState.value = _authState.value.copy(isLoading = true)

            // Simulate network delay
            delay(1500)

            // Simulate successful authentication
            val mockToken = "mock_spotify_token_${System.currentTimeMillis()}"
            saveAccessToken(mockToken)

            _authState.value = SpotifyAuthState(
                isAuthenticated = true,
                accessToken = mockToken,
                isLoading = false
            )

            Log.d(TAG, "Mock Spotify auth successful!")
            return true
        }

        // TODO: Real implementation will go here
        return false
    }

    private fun saveAccessToken(token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putBoolean(KEY_IS_CONNECTED, true)
            .apply()
        Log.d(TAG, "Access token saved")
    }

    fun getAccessToken(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun isAuthenticated(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CONNECTED, false)
    }

    fun signOut() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _authState.value = SpotifyAuthState(isAuthenticated = false)
        Log.d(TAG, "Spotify signed out")
    }

    // Mock: Get user's top tracks
    fun getMockUserTopTracks(): List<MockTrack> {
        return listOf(
            MockTrack("Blinding Lights", "The Weeknd", "After Hours"),
            MockTrack("Levitating", "Dua Lipa", "Future Nostalgia"),
            MockTrack("Save Your Tears", "The Weeknd", "After Hours"),
            MockTrack("good 4 u", "Olivia Rodrigo", "SOUR"),
            MockTrack("Peaches", "Justin Bieber", "Justice")
        )
    }
}