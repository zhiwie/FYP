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

data class TopTrack(
    val name: String,
    val artist: String,
    val album: String
)

/**
 * Spotify auth + token manager.
 *
 * IMPORTANT: Use SpotifyRepository.getInstance(context) to get the singleton.
 * This ensures all classes (HomeScreen, SearchScreen, SpotifyViewModel,
 * SpotifyMusicRepository) share the same token state.
 */
class SpotifyRepository private constructor(private val context: Context) {

    private val _authState = MutableStateFlow(SpotifyAuthState())
    val authState: StateFlow<SpotifyAuthState> = _authState

    private val httpClient = OkHttpClient()

    // Track when the token was saved so we can detect expiry
    private var tokenTimestamp: Long = 0L

    companion object {
        private const val TAG = "SpotifyRepository"
        private const val PREFS_NAME = "spotify_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"

        // Token expires after ~3600 seconds; re-auth after 3000 to be safe
        private const val TOKEN_LIFETIME_MS = 3000_000L // 50 minutes

        const val CLIENT_ID = "f5bf4e9ecc5e4f32a622aeb60aca64ea"
        const val REDIRECT_URI = "fypdraft://callback"

        @Volatile
        private var INSTANCE: SpotifyRepository? = null

        fun getInstance(context: Context): SpotifyRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Called after successful Spotify OAuth redirect
     */
    fun handleAuthResponse(accessToken: String) {
        tokenTimestamp = System.currentTimeMillis()
        saveAccessToken(accessToken)
        _authState.value = SpotifyAuthState(
            isAuthenticated = true,
            accessToken = accessToken,
            isLoading = false
        )
        Log.d(TAG, "Spotify auth successful! Token length: ${accessToken.length}")
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
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Check if token is likely expired.
     * Spotify implicit grant tokens last ~1 hour.
     */
    fun isTokenExpired(): Boolean {
        if (tokenTimestamp == 0L) return false // Can't tell, assume valid
        return (System.currentTimeMillis() - tokenTimestamp) > TOKEN_LIFETIME_MS
    }

    fun getAccessToken(): String? {
        // Check in-memory first — if we have it and it's not expired, return immediately
        val memToken = _authState.value.accessToken
        if (memToken != null) {
            if (!isTokenExpired()) return memToken
            // Token expired — mark it
            Log.w(TAG, "In-memory token expired")
            _authState.value = _authState.value.copy(
                isAuthenticated = false,
                accessToken = null,
                errorMessage = "Session expired. Please reconnect Spotify."
            )
            return null
        }

        // No in-memory token — try SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val savedTimestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)

        tokenTimestamp = savedTimestamp
        if (isTokenExpired()) {
            Log.w(TAG, "Stored token expired (${(System.currentTimeMillis() - savedTimestamp) / 1000}s ago)")
            _authState.value = _authState.value.copy(
                isAuthenticated = false,
                errorMessage = "Session expired. Please reconnect Spotify."
            )
            return null
        }

        // Restore to memory so subsequent calls are fast
        _authState.value = SpotifyAuthState(isAuthenticated = true, accessToken = token)
        return token
    }

    /**
     * Called when an API returns 401 — marks token as expired
     * so the UI can prompt re-authentication.
     */
    fun markTokenExpired() {
        Log.w(TAG, "Token marked as expired due to 401 response")
        _authState.value = SpotifyAuthState(
            isAuthenticated = false,
            accessToken = null,
            errorMessage = "Session expired. Please reconnect Spotify."
        )
        // Clear stored token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .putBoolean(KEY_IS_CONNECTED, false)
            .apply()
    }

    fun isAuthenticated(): Boolean {
        if (isTokenExpired()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CONNECTED, false) && getAccessToken() != null
    }

    fun signOut() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        tokenTimestamp = 0L
        _authState.value = SpotifyAuthState(isAuthenticated = false)
    }

    fun getUserTopTracks(): List<TopTrack> {
        val token = getAccessToken() ?: return emptyList()

        return try {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=5&time_range=short_term")
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.code == 401) {
                markTokenExpired()
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()

            val json = JSONObject(body)
            val items = json.getJSONArray("items")
            val tracks = mutableListOf<TopTrack>()

            for (i in 0 until items.length()) {
                val track = items.getJSONObject(i)
                tracks.add(TopTrack(
                    name = track.getString("name"),
                    artist = track.getJSONArray("artists").getJSONObject(0).getString("name"),
                    album = track.getJSONObject("album").getString("name")
                ))
            }
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top tracks", e)
            emptyList()
        }
    }

    fun restoreAuthState() {
        val token = getAccessToken() // This also checks expiry
        if (token != null) {
            _authState.value = SpotifyAuthState(
                isAuthenticated = true,
                accessToken = token,
                isLoading = false
            )
            Log.d(TAG, "Auth state restored from SharedPreferences")
        } else {
            Log.d(TAG, "No valid token to restore")
        }
    }
}