package com.example.fypdraft.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

// Deezer API Models
data class DeezerSearchResponse(
    val data: List<DeezerTrack>?,
    val total: Int?
)

data class DeezerTrack(
    val id: Long,
    val title: String,
    val duration: Int,
    val preview: String, // 30-second preview URL
    val artist: DeezerArtist,
    val album: DeezerAlbum,
    val link: String
)

data class DeezerArtist(
    val id: Long,
    val name: String,
    val picture: String?,
    @SerializedName("picture_medium") val pictureMedium: String?,
    @SerializedName("picture_big") val pictureBig: String?
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    val cover: String?,
    @SerializedName("cover_medium") val coverMedium: String?,
    @SerializedName("cover_big") val coverBig: String?,
    @SerializedName("cover_xl") val coverXl: String?
)

data class DeezerChartResponse(
    val tracks: DeezerTracksData?
)

data class DeezerTracksData(
    val data: List<DeezerTrack>?
)

interface DeezerApiService {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50
    ): DeezerSearchResponse

    @GET("chart")
    suspend fun getChart(): DeezerChartResponse

    @GET("track/{id}")
    suspend fun getTrack(@Path("id") trackId: Long): DeezerTrack
}

// Extension to convert Deezer track to app Track model
fun DeezerTrack.toAppTrack(): com.example.fypdraft.model.Track {
    return com.example.fypdraft.model.Track(
        id = this.id.toString(),
        name = this.title,
        artist = this.artist.name,
        album = this.album.title,
        albumArtUrl = this.album.coverXl ?: this.album.coverBig ?: this.album.coverMedium ?: "",
        previewUrl = this.preview,
        durationMs = this.duration * 1000L,
        spotifyUri = null
    )
}