package com.example.fypdraft.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

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

    private val httpClient = OkHttpClient()

    companion object {
        private const val TAG = "SpotifyRepository"
        private const val PREFS_NAME = "spotify_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_CONNECTED = "is_connected"

        const val CLIENT_ID = "f5bf4e9ecc5e4f32a622aeb60aca64ea"
        const val REDIRECT_URI = "fypdraft://callback"
    }

    fun handleAuthResponse(accessToken: String) {
        saveAccessToken(accessToken)
        _authState.value = SpotifyAuthState(
            isAuthenticated = true,
            accessToken = accessToken,
            isLoading = false
        )
        Log.d(TAG, "Spotify auth successful!")
    }

    fun handleAuthError(error: String) {
        _authState.value = SpotifyAuthState(
            isAuthenticated = false,
            errorMessage = error,
            isLoading = false
        )
        Log.e(TAG, "Spotify auth error: $error")
    }

    fun setLoading() {
        _authState.value = _authState.value.copy(isLoading = true)
    }

    private fun saveAccessToken(token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putBoolean(KEY_IS_CONNECTED, true)
            .apply()
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
    }

    fun getUserTopTracks(): List<MockTrack> {
        val token = getAccessToken() ?: run {
            Log.e(TAG, "No access token available")
            return emptyList()
        }

        return try {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=5&time_range=short_term")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                Log.e(TAG, "API call failed: ${response.code}")
                return emptyList()
            }

            val json = JSONObject(body)
            val items = json.getJSONArray("items")
            val tracks = mutableListOf<MockTrack>()

            for (i in 0 until items.length()) {
                val track = items.getJSONObject(i)
                val name = track.getString("name")
                val artist = track.getJSONArray("artists")
                    .getJSONObject(0)
                    .getString("name")
                val album = track.getJSONObject("album").getString("name")
                tracks.add(MockTrack(name, artist, album))
            }

            Log.d(TAG, "✅ Fetched ${tracks.size} top tracks from Spotify")
            tracks

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top tracks", e)
            emptyList()
        }
    }
    fun restoreAuthState() {
        val token = getAccessToken()
        if (token != null) {
            _authState.value = SpotifyAuthState(
                isAuthenticated = true,
                accessToken = token,
                isLoading = false
            )
            Log.d(TAG, "✅ Auth state restored from SharedPreferences")
        }
    }
}