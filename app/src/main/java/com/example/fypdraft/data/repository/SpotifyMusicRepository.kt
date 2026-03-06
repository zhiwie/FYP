package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.ml.AudioFeatures
import com.example.fypdraft.ml.FeatureTargets
import com.example.fypdraft.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Spotify-backed music repository.
 * Handles: search, recommendations (using RL feature targets), audio features, playback URI.
 *
 * Requires a valid Spotify access token from SpotifyRepository.
 */
class SpotifyMusicRepository(
    private val spotifyRepository: SpotifyRepository
) {
    private val TAG = "SpotifyMusicRepo"
    private val client = OkHttpClient()
    private val baseUrl = "https://api.spotify.com/v1"

    // Cache audio features to avoid redundant API calls
    private val featureCache = mutableMapOf<String, AudioFeatures>()

    private fun getToken(): String? = spotifyRepository.getAccessToken()

    private fun authRequest(url: String): Request.Builder {
        val token = getToken() ?: throw IllegalStateException("No Spotify token")
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
    }

    /**
     * Search Spotify for tracks
     */
    suspend fun searchTracks(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val token = getToken() ?: return@withContext emptyList()

        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = authRequest("$baseUrl/search?q=$encodedQuery&type=track&limit=$limit").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.e(TAG, "Search failed: ${response.code}")
                return@withContext emptyList()
            }

            val items = JSONObject(body).getJSONObject("tracks").getJSONArray("items")
            parseTracks(items)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            emptyList()
        }
    }

    /**
     * Get recommendations using RL-generated feature targets.
     * This is the core integration point between the RL engine and Spotify.
     */
    suspend fun getRecommendations(
        featureTargets: FeatureTargets,
        seedTrackIds: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 20
    ): List<Track> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()

        try {
            val params = mutableListOf<String>()
            params.add("limit=$limit")

            // Seed tracks (max 5)
            if (seedTrackIds.isNotEmpty()) {
                params.add("seed_tracks=${seedTrackIds.take(5).joinToString(",")}")
            }

            // Seed genres if no tracks
            if (seedTrackIds.isEmpty() && seedGenres.isNotEmpty()) {
                params.add("seed_genres=${seedGenres.take(5).joinToString(",")}")
            }

            // If no seeds at all, use genre seeds based on energy/valence
            if (seedTrackIds.isEmpty() && seedGenres.isEmpty()) {
                val defaultGenres = inferGenresFromFeatures(featureTargets)
                params.add("seed_genres=${defaultGenres.joinToString(",")}")
            }

            // RL feature targets → Spotify API parameters
            params.add("target_valence=${featureTargets.targetValence}")
            params.add("target_energy=${featureTargets.targetEnergy}")
            params.add("target_danceability=${featureTargets.targetDanceability}")
            params.add("target_tempo=${featureTargets.targetTempo.toInt()}")
            params.add("min_valence=${featureTargets.minValence}")
            params.add("max_valence=${featureTargets.maxValence}")
            params.add("min_energy=${featureTargets.minEnergy}")
            params.add("max_energy=${featureTargets.maxEnergy}")

            val url = "$baseUrl/recommendations?${params.joinToString("&")}"
            val request = authRequest(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.e(TAG, "Recommendations failed: ${response.code} - $body")
                return@withContext emptyList()
            }

            val tracks = JSONObject(body).getJSONArray("tracks")
            parseTracks(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Recommendations error", e)
            emptyList()
        }
    }

    /**
     * Get user's top tracks (for seeding recommendations)
     */
    suspend fun getUserTopTrackIds(limit: Int = 5, timeRange: String = "short_term"): List<String> =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: return@withContext emptyList()
            try {
                val request = authRequest("$baseUrl/me/top/tracks?limit=$limit&time_range=$timeRange").build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                if (!response.isSuccessful) return@withContext emptyList()

                val items = JSONObject(body).getJSONArray("items")
                (0 until items.length()).map { items.getJSONObject(it).getString("id") }
            } catch (e: Exception) {
                Log.e(TAG, "Top tracks error", e)
                emptyList()
            }
        }

    /**
     * Get audio features for a track (for RL reward calculation)
     */
    suspend fun getAudioFeatures(trackId: String): AudioFeatures? = withContext(Dispatchers.IO) {
        featureCache[trackId]?.let { return@withContext it }

        val token = getToken() ?: return@withContext null
        try {
            val request = authRequest("$baseUrl/audio-features/$trackId").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(body)
            val features = AudioFeatures(
                valence = json.optDouble("valence", 0.5).toFloat(),
                energy = json.optDouble("energy", 0.5).toFloat(),
                danceability = json.optDouble("danceability", 0.5).toFloat(),
                tempo = json.optDouble("tempo", 120.0).toFloat(),
                acousticness = json.optDouble("acousticness", 0.5).toFloat(),
                instrumentalness = json.optDouble("instrumentalness", 0.3).toFloat()
            )
            featureCache[trackId] = features
            features
        } catch (e: Exception) {
            Log.e(TAG, "Audio features error for $trackId", e)
            null
        }
    }

    /**
     * Get new releases / featured playlists tracks
     */
    suspend fun getFeaturedTracks(limit: Int = 10): List<Track> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        try {
            val request = authRequest("$baseUrl/browse/new-releases?limit=$limit").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) return@withContext emptyList()

            val albums = JSONObject(body).getJSONObject("albums").getJSONArray("items")
            // Get first track from each album
            val tracks = mutableListOf<Track>()
            for (i in 0 until albums.length().coerceAtMost(limit)) {
                val album = albums.getJSONObject(i)
                val albumName = album.getString("name")
                val artist = album.getJSONArray("artists").getJSONObject(0).getString("name")
                val artUrl = album.getJSONArray("images").optJSONObject(0)?.optString("url") ?: ""
                val albumId = album.getString("id")

                tracks.add(Track(
                    id = albumId,
                    name = albumName,
                    artist = artist,
                    albumArtUrl = artUrl,
                    previewUrl = null,
                    durationMs = 0L,
                    spotifyUri = album.optString("uri")
                ))
            }
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Featured tracks error", e)
            emptyList()
        }
    }

    /**
     * Check if user has Spotify Premium
     */
    suspend fun isPremium(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        try {
            val request = authRequest("$baseUrl/me").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext false
            val product = JSONObject(body).optString("product", "free")
            product == "premium"
        } catch (e: Exception) {
            false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun parseTracks(items: JSONArray): List<Track> {
        val tracks = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            try {
                val item = items.getJSONObject(i)
                val artists = item.getJSONArray("artists")
                val artistName = artists.getJSONObject(0).getString("name")
                val album = item.optJSONObject("album")
                val images = album?.optJSONArray("images")
                val artUrl = images?.optJSONObject(0)?.optString("url") ?: ""

                tracks.add(Track(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    artist = artistName,
                    album = album?.optString("name") ?: "",
                    albumArtUrl = artUrl,
                    previewUrl = item.optString("preview_url", null),
                    durationMs = item.optLong("duration_ms", 0L),
                    spotifyUri = item.optString("uri")
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse track at index $i", e)
            }
        }
        return tracks
    }

    private fun inferGenresFromFeatures(targets: FeatureTargets): List<String> {
        val genres = mutableListOf<String>()

        when {
            targets.targetEnergy > 0.7f && targets.targetDanceability > 0.6f -> {
                genres.addAll(listOf("dance", "edm", "pop"))
            }
            targets.targetEnergy > 0.7f -> {
                genres.addAll(listOf("rock", "hip-hop", "work-out"))
            }
            targets.targetValence > 0.7f -> {
                genres.addAll(listOf("pop", "happy", "summer"))
            }
            targets.targetEnergy < 0.3f && targets.targetValence < 0.4f -> {
                genres.addAll(listOf("ambient", "chill", "sleep"))
            }
            targets.targetEnergy < 0.4f -> {
                genres.addAll(listOf("acoustic", "indie", "folk"))
            }
            else -> {
                genres.addAll(listOf("pop", "indie", "alternative"))
            }
        }

        return genres.take(5)
    }
}