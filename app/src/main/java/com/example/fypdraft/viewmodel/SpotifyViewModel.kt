package com.example.fypdraft.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.data.repository.SpotifyAuthState
import com.example.fypdraft.data.repository.MockTrack
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.flow.StateFlow

class SpotifyViewModel(private val context: Context) : ViewModel() {
    // Use singleton so token is shared across all screens
    private val repository = SpotifyRepository.getInstance(context)
    val authState: StateFlow<SpotifyAuthState> = repository.authState

    companion object {
        private const val TAG = "SpotifyViewModel"
        const val SPOTIFY_AUTH_REQUEST_CODE = 1337
        private val SCOPES = arrayOf(
            "streaming",
            "user-read-email",
            "user-read-private",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "playlist-read-collaborative",
            "playlist-modify-public",
            "playlist-modify-private",
            "user-top-read",
            "user-read-recently-played",
            "user-library-read",
            "app-remote-control"
        )
    }

    fun connectSpotify(activity: Activity) {
        repository.setLoading()
        val request = AuthorizationRequest.Builder(
            SpotifyRepository.CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            SpotifyRepository.REDIRECT_URI
        )
            .setScopes(SCOPES)
            .setShowDialog(true)
            .build()

        AuthorizationClient.openLoginActivity(activity, SPOTIFY_AUTH_REQUEST_CODE, request)
        Log.d(TAG, "Opening Spotify login...")
    }

    fun handleAuthResult(responseCode: Int, response: AuthorizationResponse) {
        Log.d(TAG, "Auth response type: ${response.type}, error: ${response.error}")

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                if (!token.isNullOrEmpty()) {
                    Log.d(TAG, "Spotify token received (${token.length} chars)")
                    repository.handleAuthResponse(token)
                } else {
                    repository.handleAuthError("Token was empty")
                }
            }
            AuthorizationResponse.Type.ERROR ->
                repository.handleAuthError(response.error ?: "Authentication failed")
            AuthorizationResponse.Type.EMPTY ->
                repository.handleAuthError("Login was cancelled")
            else ->
                repository.handleAuthError("Unexpected error")
        }
    }

    fun restoreAuthState() = repository.restoreAuthState()
    fun isSpotifyConnected(): Boolean = repository.isAuthenticated()
    fun disconnectSpotify() = repository.signOut()
    fun getUserTopTracks(): List<MockTrack> = repository.getUserTopTracks()
    fun getAccessToken(): String? = repository.getAccessToken()
}