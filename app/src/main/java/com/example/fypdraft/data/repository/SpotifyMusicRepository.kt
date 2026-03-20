package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spotify-backed music repository.
 *
 * NOTE: As of November 2024, Spotify DEPRECATED these endpoints for new apps:
 * - /recommendations (returns 404)
 * - /audio-features (returns 404)
 * - /audio-analysis (returns 404)
 *
 * All "recommendation" functionality now uses /search with curated queries.
 * This approach is used by the community as the official workaround.
 *
 * FIX: execute() no longer calls markTokenExpired() directly.
 * This prevents parallel API calls from nuking the session when one gets a 401.
 * Instead, the caller (HomeScreen) checks the token after all calls complete.
 */
class SpotifyMusicRepository(
    private val spotifyRepository: SpotifyRepository
) {
    private val TAG = "SpotifyMusicRepo"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://api.spotify.com/v1"

    /**
     * Snapshot the token once so parallel calls all use the same value.
     * This avoids repeated getAccessToken() calls that can trigger
     * expiry checks mid-batch and nuke the session.
     */
    private fun getToken(): String? {
        val token = spotifyRepository.getAccessToken()
        if (token == null) {
            Log.e(TAG, "No valid Spotify token available")
        }
        return token
    }

    private fun buildRequest(url: String, token: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
    }

    /**
     * Execute an HTTP request and return the response body.
     *
     * IMPORTANT: Does NOT call markTokenExpired() on 401.
     * This is intentional — during parallel loads, a single 401
     * should not nuke the entire session. The caller decides
     * what to do when all results come back empty.
     */
    private fun execute(request: Request): String? {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            when {
                response.code == 401 -> {
                    Log.e(TAG, "401 Unauthorized for ${request.url} — token may be expired")
                    null
                }
                response.code == 429 -> {
                    val retryAfter = response.header("Retry-After", "?")
                    Log.w(TAG, "429 Rate limited for ${request.url} — retry after ${retryAfter}s")
                    null
                }
                !response.isSuccessful -> {
                    Log.e(TAG, "API [${response.code}] ${request.url}: ${body?.take(300)}")
                    null
                }
                else -> body
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error for ${request.url}", e)
            null
        }
    }

    // ── Search (primary method for everything) ───────────────────────

    /**
     * Search Spotify for tracks with automatic pagination.
     *
     * As of February 2026, Spotify's Development Mode apps have a
     * max search limit of 10 per request (was 50). We paginate
     * automatically to fetch the desired number of results.
     *
     * See: https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide
     */
    private val SPOTIFY_MAX_SEARCH_LIMIT = 10

    suspend fun searchTracks(query: String, limit: Int = 10): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val token = getToken() ?: run {
            Log.e(TAG, "❌ No token — skipping search for '$query'")
            return@withContext emptyList()
        }

        val allTracks = mutableListOf<Track>()
        var offset = 0
        val desired = limit.coerceAtLeast(1)
        var done = false

        while (allTracks.size < desired && !done) {
            val pageSize = minOf(SPOTIFY_MAX_SEARCH_LIMIT, desired - allTracks.size)

            val httpUrl = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("api.spotify.com")
                .addPathSegments("v1/search")
                .addQueryParameter("q", query)
                .addQueryParameter("type", "track")
                .addQueryParameter("limit", pageSize.toString())
                .addQueryParameter("offset", offset.toString())
                .build()

            Log.d(TAG, "🔍 Searching: $httpUrl")

            val request = Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val body = execute(request)
            if (body == null) {
                Log.e(TAG, "❌ Request failed for search '$query' (offset=$offset)")
                done = true
                continue
            }

            val tracksObj = JSONObject(body).optJSONObject("tracks")
            if (tracksObj == null) {
                Log.e(TAG, "❌ No 'tracks' object in response for '$query': ${body.take(300)}")
                done = true
                continue
            }

            val items = tracksObj.optJSONArray("items")
            if (items == null || items.length() == 0) {
                Log.d(TAG, "📭 No more results for '$query' at offset=$offset")
                done = true
                continue
            }

            val pageTracks = parseTracks(items)
            allTracks.addAll(pageTracks)
            offset += pageTracks.size

            Log.d(TAG, "📦 Page for '$query': ${pageTracks.size} tracks (total so far: ${allTracks.size})")

            // If we got fewer than requested, there are no more results
            if (pageTracks.size < pageSize) done = true
        }

        Log.d(TAG, "✅ Search '$query': ${allTracks.size} total results")
        allTracks
    }

    // ── Mood-based recommendations via search ────────────────────────

    /**
     * Get tracks for a specific mood/vibe using curated search queries.
     * This replaces the deprecated /recommendations endpoint.
     */
    suspend fun getTracksForMood(mood: String, limit: Int = 15): List<Track> {
        val query = moodToSearchQuery(mood)
        return searchTracks(query, limit)
    }

    /**
     * Get personalized recommendations by searching based on the user's
     * top artists/tracks. This mimics what /recommendations used to do.
     */
    suspend fun getPersonalizedTracks(limit: Int = 15): List<Track> = withContext(Dispatchers.IO) {
        // Get user's top tracks to extract artist names
        val topTracks = getUserTopTracks(5)
        if (topTracks.isEmpty()) {
            Log.d(TAG, "No top tracks — falling back to popular search")
            return@withContext searchTracks("top hits 2025 popular", limit)
        }

        // Search using the user's top artists as seeds
        val artistNames = topTracks.map { it.artist }.distinct().take(3)
        val query = artistNames.joinToString(" ") + " similar"
        Log.d(TAG, "Personalized query: '$query'")

        val results = searchTracks(query, limit)

        // Filter out tracks the user already has in their top
        val topIds = topTracks.map { it.id }.toSet()
        val filtered = results.filter { it.id !in topIds }
        if (filtered.isNotEmpty()) filtered else results
    }

    // ── User top tracks ──────────────────────────────────────────────

    /**
     * Get user's top tracks (for seeding personalized recommendations)
     */
    suspend fun getUserTopTracks(limit: Int = 5, timeRange: String = "short_term"): List<Track> =
        withContext(Dispatchers.IO) {
            val token = getToken() ?: return@withContext emptyList()

            val safeLimit = limit.coerceIn(1, 50)
            val url = "$baseUrl/me/top/tracks?limit=$safeLimit&time_range=$timeRange"
            Log.d(TAG, "📋 Fetching top tracks: $url")

            val request = buildRequest(url, token)
            val body = execute(request)

            if (body == null && timeRange == "short_term") {
                Log.d(TAG, "short_term failed — trying medium_term")
                return@withContext getUserTopTracks(limit, "medium_term")
            }
            if (body == null) return@withContext emptyList()

            val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
            val tracks = parseTracks(items)
            Log.d(TAG, "✅ Top tracks ($timeRange): ${tracks.size}")
            tracks
        }

    /**
     * Get user's top track IDs only (lighter weight)
     */
    suspend fun getUserTopTrackIds(limit: Int = 5, timeRange: String = "short_term"): List<String> {
        return getUserTopTracks(limit, timeRange).map { it.id }
    }

    // ── Featured / trending ──────────────────────────────────────────

    suspend fun getFeaturedTracks(limit: Int = 10): List<Track> {
        return searchTracks("top hits 2025 trending popular", limit)
    }

    // ── User playlists ───────────────────────────────────────────────

    suspend fun getUserPlaylists(limit: Int = 20): List<SpotifyPlaylistInfo> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()

        val safeLimit = limit.coerceIn(1, 50)
        val request = buildRequest("$baseUrl/me/playlists?limit=$safeLimit", token)
        val body = execute(request) ?: return@withContext emptyList()

        val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
        val playlists = mutableListOf<SpotifyPlaylistInfo>()
        for (i in 0 until items.length()) {
            try {
                val item = items.getJSONObject(i)
                playlists.add(SpotifyPlaylistInfo(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                    imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: "",
                    ownerName = item.optJSONObject("owner")?.optString("display_name") ?: ""
                ))
            } catch (_: Exception) {}
        }
        playlists
    }

    // ── Premium check ────────────────────────────────────────────────

    suspend fun isPremium(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val request = buildRequest("$baseUrl/me", token)
        val body = execute(request) ?: return@withContext false
        JSONObject(body).optString("product", "free") == "premium"
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun parseTracks(items: JSONArray): List<Track> {
        val tracks = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            try {
                val item = items.getJSONObject(i)
                val artistName = item.getJSONArray("artists").getJSONObject(0).getString("name")
                val album = item.optJSONObject("album")
                val artUrl = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: ""
                val previewUrl = item.optString("preview_url", "null")
                val uri = item.optString("uri", "null")

                tracks.add(Track(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    artist = artistName,
                    album = album?.optString("name") ?: "",
                    albumArtUrl = artUrl,
                    previewUrl = if (previewUrl != "null" && previewUrl.isNotBlank()) previewUrl else null,
                    durationMs = item.optLong("duration_ms", 0L),
                    spotifyUri = if (uri != "null" && uri.isNotBlank()) uri else null
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Parse error at $i", e)
            }
        }
        return tracks
    }

    /**
     * Convert a mood keyword into a rich Spotify search query.
     * These queries are curated to return genre-appropriate results.
     */
    private fun moodToSearchQuery(mood: String): String = when (mood.lowercase()) {
        "happy", "uplift" -> "happy uplifting feel good pop hits"
        "sad", "down", "comfort" -> "sad emotional ballad heartbreak"
        "calm", "relax" -> "calm relaxing acoustic peaceful"
        "energetic", "energy", "workout" -> "energetic workout high energy dance"
        "focus", "study" -> "focus study instrumental lofi beats"
        "chill" -> "chill lofi ambient relaxing vibes"
        "romantic", "romance", "love" -> "romantic love songs slow dance"
        "sleep", "night" -> "sleep ambient calm lullaby piano"
        "throwback", "nostalgia" -> "throwback 90s 2000s classic hits"
        "trending", "popular" -> "top hits 2025 trending popular"
        else -> "top music 2025 popular hits"
    }
}

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String,
    val ownerName: String
)