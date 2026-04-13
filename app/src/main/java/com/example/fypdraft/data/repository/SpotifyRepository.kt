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

class SpotifyRepository private constructor(private val context: Context) {

    private val _authState = MutableStateFlow(SpotifyAuthState())
    val authState: StateFlow<SpotifyAuthState> = _authState

    private val httpClient = OkHttpClient()
    private var tokenTimestamp: Long = 0L

    companion object {
        private const val TAG = "SpotifyRepository"
        private const val PREFS_NAME = "spotify_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"

        // ── Bump this number any time you add/remove OAuth scopes ────────
        // Current scopes include playlist-read-private + playlist-read-collaborative.
        // Bumping this clears any old token that was issued without those scopes,
        // forcing a clean re-authentication on the next launch.
        private const val KEY_SCOPE_VERSION = "scope_version"
        private const val CURRENT_SCOPE_VERSION = 2  // <── bump when scopes change

        private const val TOKEN_LIFETIME_MS = 3000_000L // 50 minutes

        const val CLIENT_ID = "f5bf4e9ecc5e4f32a622aeb60aca64ea"
        const val REDIRECT_URI = "fypdraft://callback"

        @Volatile
        private var INSTANCE: SpotifyRepository? = null

        fun getInstance(context: Context): SpotifyRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpotifyRepository(context.applicationContext).also { inst ->
                    // Clear stale token immediately if scopes have changed
                    inst.clearIfScopeVersionMismatch()
                    INSTANCE = inst
                }
            }
        }
    }

    // ── Scope version guard ───────────────────────────────────────────────

    /**
     * If the stored scope version doesn't match CURRENT_SCOPE_VERSION,
     * the saved token was issued with a different (older) set of scopes.
     * Clear everything so the user re-authenticates and gets a fresh token
     * that includes all required scopes (playlist-read-private etc.).
     */
    private fun clearIfScopeVersionMismatch() {
        val prefs        = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(KEY_SCOPE_VERSION, 0)
        if (storedVersion != CURRENT_SCOPE_VERSION) {
            Log.w(TAG, "Scope version mismatch (stored=$storedVersion, current=$CURRENT_SCOPE_VERSION). Clearing token.")
            prefs.edit().clear().apply()
            prefs.edit().putInt(KEY_SCOPE_VERSION, CURRENT_SCOPE_VERSION).apply()
            tokenTimestamp = 0L
            _authState.value = SpotifyAuthState(isAuthenticated = false)
        }
    }

    // ── Auth handling ─────────────────────────────────────────────────────

    fun handleAuthResponse(accessToken: String) {
        tokenTimestamp = System.currentTimeMillis()
        saveAccessToken(accessToken)
        _authState.value = SpotifyAuthState(
            isAuthenticated = true,
            accessToken     = accessToken,
            isLoading       = false
        )
        Log.d(TAG, "Spotify auth successful — token length: ${accessToken.length}")
    }

    fun handleAuthError(error: String) {
        _authState.value = SpotifyAuthState(
            isAuthenticated = false,
            errorMessage    = error,
            isLoading       = false
        )
        Log.e(TAG, "Spotify auth error: $error")
    }

    fun setLoading() {
        _authState.value = _authState.value.copy(isLoading = true)
    }

    private fun saveAccessToken(token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putBoolean(KEY_IS_CONNECTED, true)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .putInt(KEY_SCOPE_VERSION, CURRENT_SCOPE_VERSION)  // always stamp version on save
            .apply()
    }

    // ── Token management ──────────────────────────────────────────────────

    fun isTokenExpired(): Boolean {
        if (tokenTimestamp == 0L) return false
        return (System.currentTimeMillis() - tokenTimestamp) > TOKEN_LIFETIME_MS
    }

    fun getAccessToken(): String? {
        val memToken = _authState.value.accessToken
        if (memToken != null) {
            if (!isTokenExpired()) return memToken
            Log.w(TAG, "In-memory token expired")
            _authState.value = _authState.value.copy(
                isAuthenticated = false,
                accessToken     = null,
                errorMessage    = "Session expired. Please reconnect Spotify."
            )
            return null
        }

        val prefs          = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token          = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val savedTimestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)

        tokenTimestamp = savedTimestamp
        if (isTokenExpired()) {
            Log.w(TAG, "Stored token expired (${(System.currentTimeMillis() - savedTimestamp) / 1000}s ago)")
            _authState.value = _authState.value.copy(
                isAuthenticated = false,
                errorMessage    = "Session expired. Please reconnect Spotify."
            )
            return null
        }

        _authState.value = SpotifyAuthState(isAuthenticated = true, accessToken = token)
        return token
    }

    fun markTokenExpired() {
        Log.w(TAG, "Token marked as expired due to 401 response")
        _authState.value = SpotifyAuthState(
            isAuthenticated = false,
            accessToken     = null,
            errorMessage    = "Session expired. Please reconnect Spotify."
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        tokenTimestamp   = 0L
        _authState.value = SpotifyAuthState(isAuthenticated = false)
    }

    fun restoreAuthState() {
        val token = getAccessToken()
        if (token != null) {
            _authState.value = SpotifyAuthState(
                isAuthenticated = true,
                accessToken     = token,
                isLoading       = false
            )
            Log.d(TAG, "Auth state restored from SharedPreferences")
        } else {
            Log.d(TAG, "No valid token to restore")
        }
    }

    // ── User top tracks (blocking — call from background thread) ─────────

    fun getUserTopTracks(): List<TopTrack> {
        val token = getAccessToken() ?: return emptyList()
        return try {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=5&time_range=short_term")
                .addHeader("Authorization", "Bearer $token")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.code == 401) { markTokenExpired(); return emptyList() }
            val body = response.body?.string() ?: return emptyList()
            if (!response.isSuccessful) return emptyList()
            val items  = JSONObject(body).getJSONArray("items")
            val tracks = mutableListOf<TopTrack>()
            for (i in 0 until items.length()) {
                val t = items.getJSONObject(i)
                tracks.add(TopTrack(
                    name   = t.getString("name"),
                    artist = t.getJSONArray("artists").getJSONObject(0).getString("name"),
                    album  = t.getJSONObject("album").getString("name")
                ))
            }
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching top tracks", e)
            emptyList()
        }
    }
}