package com.example.fypdraft.data.api

import retrofit2.Response
import retrofit2.http.*

// Spotify API Response Models
data class SpotifySearchResponse(
    val tracks: TracksResult
)

data class TracksResult(
    val items: List<SpotifyTrack>
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,
    val preview_url: String?,
    val duration_ms: Long,
    val uri: String
)

data class SpotifyArtist(
    val name: String
)

data class SpotifyAlbum(
    val name: String,
    val images: List<SpotifyImage>
)

data class SpotifyImage(
    val url: String,
    val height: Int?,
    val width: Int?
)

data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrack>
)

interface SpotifyApiService {

    @GET("search")
    suspend fun searchTracks(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): Response<SpotifySearchResponse>

    @GET("recommendations")
    suspend fun getRecommendations(
        @Header("Authorization") authorization: String,
        @Query("seed_tracks") seedTracks: String,
        @Query("limit") limit: Int = 20
    ): Response<SpotifyRecommendationsResponse>

    @GET("me/top/tracks")
    suspend fun getUserTopTracks(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20,
        @Query("time_range") timeRange: String = "medium_term"
    ): Response<TracksResult>
}