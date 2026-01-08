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

    // Get mock tracks for demo (will be replaced with real Spotify data)
    fun getMockTracksForPlayer(): List<com.example.fypdraft.model.Track> {
        return listOf(
            com.example.fypdraft.model.Track(
                id = "1",
                name = "Lover",
                artist = "Taylor Swift",
                album = "Lover",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b273e787cffec20aa2a396a61647",
                previewUrl = "https://p.scdn.co/mp3-preview/6e1f4a9a4b1f4e7b8c3d5e6f7a8b9c0d1e2f3a4b",
                durationMs = 30000
            ),
            com.example.fypdraft.model.Track(
                id = "2",
                name = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36",
                previewUrl = "https://p.scdn.co/mp3-preview/7b9b8e8f9e0f1e2f3e4f5e6f7e8f9e0f1e2f3e4f",
                durationMs = 30000
            ),
            com.example.fypdraft.model.Track(
                id = "3",
                name = "Levitating",
                artist = "Dua Lipa",
                album = "Future Nostalgia",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b273fc92f0e8c72ba8d87ec2eb6e",
                previewUrl = "https://p.scdn.co/mp3-preview/8c8d9e0f1e2f3e4f5e6f7e8f9e0f1e2f3e4f5e6f",
                durationMs = 30000
            )
        )
    }
}