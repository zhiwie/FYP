package com.example.fypdraft.data.repository

data class SpotifyAudioFeatures(
    val trackId:          String,
    val danceability:     Float,
    val energy:           Float,
    val loudness:         Float,
    val speechiness:      Float,
    val acousticness:     Float,
    val instrumentalness: Float,
    val liveness:         Float,
    val valence:          Float,
    val tempo:            Float
) {
    val isAvailable: Boolean get() = danceability >= 0f

    companion object {
        fun unavailable(trackId: String) = SpotifyAudioFeatures(
            trackId, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f
        )
    }
}