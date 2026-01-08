package com.example.fypdraft.model

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String,
    val previewUrl: String?, // 30-second preview MP3 URL from Spotify
    val durationMs: Long,
    val spotifyUri: String? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val tracks: List<Track>,
    val imageUrl: String?
)

//declared in musicplayerviewmodel.kt
//data class PlayerState(
//    val currentTrack: Track? = null,
//    val isPlaying: Boolean = false,
//    val progress: Float = 0f, // 0f to 1f
//    val currentPosition: Long = 0L,
//    val duration: Long = 0L,
//    val playlist: List<Track> = emptyList(),
//    val currentIndex: Int = 0
//)