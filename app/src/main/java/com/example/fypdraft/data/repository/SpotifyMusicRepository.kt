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
 * - /audio-features  (returns 404)
 * - /audio-analysis  (returns 404)
 *
 * All "recommendation" functionality now uses /search with curated queries.
 */
class SpotifyMusicRepository(
    private val spotifyRepository: SpotifyRepository
) {
    private val TAG     = "SpotifyMusicRepo"
    private val client  = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://api.spotify.com/v1"

    private fun getToken(): String? {
        val token = spotifyRepository.getAccessToken()
        if (token == null) Log.e(TAG, "No valid Spotify token available")
        return token
    }

    private fun buildRequest(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

    private fun execute(request: Request): String? {
        return try {
            val response = client.newCall(request).execute()
            val body     = response.body?.string()
            when {
                response.code == 401 -> {
                    Log.e(TAG, "401 Unauthorized for ${request.url} — token may be expired")
                    null
                }
                response.code == 429 -> {
                    val retryAfter = response.header("Retry-After", "?")
                    Log.w(TAG, "429 Rate limited — retry after ${retryAfter}s")
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

    // ── Search ───────────────────────────────────────────────────────

    private val SPOTIFY_MAX_SEARCH_LIMIT = 10

    suspend fun searchTracks(query: String, limit: Int = 10): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val token   = getToken() ?: return@withContext emptyList()

        val allTracks = mutableListOf<Track>()
        var offset    = 0
        val desired   = limit.coerceAtLeast(1)
        var done      = false

        while (allTracks.size < desired && !done) {
            val pageSize = minOf(SPOTIFY_MAX_SEARCH_LIMIT, desired - allTracks.size)
            val httpUrl  = okhttp3.HttpUrl.Builder()
                .scheme("https").host("api.spotify.com")
                .addPathSegments("v1/search")
                .addQueryParameter("q",      query)
                .addQueryParameter("type",   "track")
                .addQueryParameter("limit",  pageSize.toString())
                .addQueryParameter("offset", offset.toString())
                .build()

            val request    = Request.Builder().url(httpUrl).addHeader("Authorization", "Bearer $token").build()
            val body       = execute(request) ?: break
            val tracksObj  = JSONObject(body).optJSONObject("tracks") ?: break
            val items      = tracksObj.optJSONArray("items") ?: break
            if (items.length() == 0) break

            val pageTracks = parseTracks(items)
            allTracks.addAll(pageTracks)
            offset += pageTracks.size
            if (pageTracks.size < pageSize) done = true
        }

        // ── FIX: deduplicate at search level too ──────────────────────
        allTracks.distinctBy { it.id }
    }

    // ── Playlist tracks ──────────────────────────────────────────────

    suspend fun getPlaylistTracks(playlistId: String, limit: Int = 50): List<Track> =
        withContext(Dispatchers.IO) {
            val token     = getToken() ?: return@withContext emptyList()
            val allTracks = mutableListOf<Track>()
            var offset    = 0
            val pageSize  = 10
            var done      = false

            while (allTracks.size < limit && !done) {
                val url      = "$baseUrl/playlists/$playlistId/items?limit=$pageSize&offset=$offset"
                Log.d(TAG, "🎵 Playlist $playlistId — page offset=$offset")

                val response = getPlaylistResponse(url, token)
                if (response == null) {
                    Log.e(TAG, "❌ Null response for playlist $playlistId at offset=$offset"); break
                }
                if (response == "403") throw SecurityException("403")

                val json  = JSONObject(response)
                val items = json.optJSONArray("items")
                if (items == null || items.length() == 0) {
                    Log.d(TAG, "📭 No items in playlist $playlistId at offset=$offset"); break
                }

                var pageAdded = 0
                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        Log.d(TAG, "🔍 Item $i raw: ${item.toString().take(300)}")

                        if (item.isNull("track")) { Log.d(TAG, "⏭️  Item $i: track is null, skipping"); continue }
                        val trackObj = item.getJSONObject("track")

                        val id = trackObj.optString("id", "").trim()
                        if (id.isEmpty() || id == "null") { Log.d(TAG, "⏭️  Item $i: missing id, skipping"); continue }

                        val name = trackObj.optString("name", "").trim()
                        if (name.isEmpty()) { Log.d(TAG, "⏭️  Item $i: missing name, skipping"); continue }

                        val artists    = trackObj.optJSONArray("artists")
                        val artistName = artists?.optJSONObject(0)?.optString("name", "") ?: ""
                        if (artistName.isEmpty()) { Log.d(TAG, "⏭️  Item $i: missing artist, skipping"); continue }

                        val album     = trackObj.optJSONObject("album")
                        val artUrl    = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url", "") ?: ""
                        val albumName = album?.optString("name", "") ?: ""

                        val rawPreview = trackObj.optString("preview_url", "")
                        val preview    = if (rawPreview.isNotBlank() && rawPreview != "null") rawPreview else null
                        val rawUri     = trackObj.optString("uri", "")
                        val uri        = if (rawUri.isNotBlank() && rawUri != "null") rawUri else null
                        val durationMs = trackObj.optLong("duration_ms", 0L)

                        allTracks.add(Track(
                            id          = id,
                            name        = name,
                            artist      = artistName,
                            album       = albumName,
                            albumArtUrl = artUrl,
                            previewUrl  = preview,
                            durationMs  = durationMs,
                            spotifyUri  = uri
                        ))
                        pageAdded++
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️  Parse error at item $i: ${e.message}")
                    }
                }

                Log.d(TAG, "📝 Page at offset=$offset: $pageAdded added, total=${allTracks.size}")
                offset += items.length()
                val hasNext = !json.isNull("next") && json.optString("next").isNotBlank()
                if (!hasNext) done = true
            }

            Log.d(TAG, "✅ Playlist $playlistId: ${allTracks.size} tracks total")
            // ── FIX: deduplicate playlist results ─────────────────────
            allTracks.distinctBy { it.id }.take(limit)
        }

    // ── Mood-based recommendations ───────────────────────────────────

    suspend fun getTracksForMood(mood: String, limit: Int = 15): List<Track> =
        searchTracks(moodToSearchQuery(mood), limit)

    suspend fun getPersonalizedTracks(limit: Int = 15): List<Track> = withContext(Dispatchers.IO) {
        val topTracks = getUserTopTracks(5)
        Log.d("RecoDebug", "Top tracks count: ${topTracks.size}")

        if (topTracks.isEmpty()) {
            Log.d("RecoDebug", "No top tracks — falling back to search")
            return@withContext searchTracks("top hits 2025 popular", limit)
        }

        val artistNames = topTracks.map { it.artist }.distinct().take(3)
        val query       = artistNames.joinToString(" ") + " similar"
        Log.d("RecoDebug", "Query: $query")

        val results = searchTracks(query, limit)
        Log.d("RecoDebug", "Search results: ${results.size}")

        val topIds   = topTracks.map { it.id }.toSet()
        val filtered = results.filter { it.id !in topIds }
        // ── FIX: deduplicate personalized results ─────────────────────
        (if (filtered.isNotEmpty()) filtered else results).distinctBy { it.id }
    }

    // ── User top tracks ──────────────────────────────────────────────

    suspend fun getUserTopTracks(limit: Int = 5, timeRange: String = "short_term"): List<Track> =
        withContext(Dispatchers.IO) {
            val token     = getToken() ?: return@withContext emptyList()
            val safeLimit = limit.coerceIn(1, 50)
            val url       = "$baseUrl/me/top/tracks?limit=$safeLimit&time_range=$timeRange"
            val body      = execute(buildRequest(url, token))

            Log.d("RecoDebug", "getUserTopTracks($timeRange) body null: ${body == null}")
            Log.d("RecoDebug", "getUserTopTracks($timeRange) body: ${body?.take(200)}")

            if (body == null && timeRange == "short_term")
                return@withContext getUserTopTracks(limit, "medium_term")
            if (body == null) return@withContext emptyList()

            parseTracks(JSONObject(body).optJSONArray("items") ?: return@withContext emptyList())
        }

    suspend fun getUserTopTrackIds(limit: Int = 5, timeRange: String = "short_term"): List<String> =
        getUserTopTracks(limit, timeRange).map { it.id }

    // ── Featured / trending ──────────────────────────────────────────

    suspend fun getFeaturedTracks(limit: Int = 10): List<Track> =
        searchTracks("top hits 2025 trending popular", limit)

    // ── User playlists ───────────────────────────────────────────────

    suspend fun getUserPlaylists(limit: Int = 20): List<SpotifyPlaylistInfo> = withContext(Dispatchers.IO) {
        val token     = getToken() ?: return@withContext emptyList()
        val safeLimit = limit.coerceIn(1, 50)
        val body      = execute(buildRequest("$baseUrl/me/playlists?limit=$safeLimit", token))
            ?: return@withContext emptyList()

        val items     = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
        val playlists = mutableListOf<SpotifyPlaylistInfo>()
        for (i in 0 until items.length()) {
            try {
                val item = items.getJSONObject(i)
                playlists.add(SpotifyPlaylistInfo(
                    id         = item.getString("id"),
                    name       = item.getString("name"),
                    trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                    imageUrl   = item.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: "",
                    ownerName  = item.optJSONObject("owner")?.optString("display_name") ?: ""
                ))
            } catch (_: Exception) {}
        }
        playlists
    }

    // ── Premium check ────────────────────────────────────────────────

    suspend fun isPremium(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val body  = execute(buildRequest("$baseUrl/me", token)) ?: return@withContext false
        JSONObject(body).optString("product", "free") == "premium"
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getPlaylistResponse(url: String, token: String): String? {
        return try {
            val response = client.newCall(buildRequest(url, token)).execute()
            val body     = response.body?.string()
            when (response.code) {
                403  -> "403"
                401  -> { Log.e(TAG, "401 on $url"); null }
                429  -> { Log.w(TAG, "429 rate limited on $url"); null }
                else -> if (response.isSuccessful) body else {
                    Log.e(TAG, "API [${response.code}] $url: ${body?.take(200)}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: $url", e)
            null
        }
    }

    /**
     * Parse a JSONArray of Spotify track objects into Track models.
     * ── FIX: distinctBy { id } at the root so every caller is safe ──
     */
    private fun parseTracks(items: JSONArray): List<Track> {
        val tracks = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            try {
                val item       = items.getJSONObject(i)
                val artistName = item.getJSONArray("artists").getJSONObject(0).getString("name")
                val album      = item.optJSONObject("album")
                val artUrl     = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url") ?: ""
                val previewUrl = item.optString("preview_url", "null")
                val uri        = item.optString("uri", "null")
                tracks.add(Track(
                    id          = item.getString("id"),
                    name        = item.getString("name"),
                    artist      = artistName,
                    album       = album?.optString("name") ?: "",
                    albumArtUrl = artUrl,
                    previewUrl  = if (previewUrl != "null" && previewUrl.isNotBlank()) previewUrl else null,
                    durationMs  = item.optLong("duration_ms", 0L),
                    spotifyUri  = if (uri != "null" && uri.isNotBlank()) uri else null
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Parse error at $i", e)
            }
        }
        // ── THE ROOT FIX: dedup here means every single caller is safe ──
        return tracks.distinctBy { it.id }
    }

    private fun moodToSearchQuery(mood: String): String = when (mood.lowercase()) {
        "happy", "uplift"               -> "happy uplifting feel good pop hits"
        "sad", "down", "comfort"        -> "sad emotional ballad heartbreak"
        "calm", "relax"                 -> "calm relaxing acoustic peaceful"
        "energetic", "energy","workout" -> "energetic workout high energy dance"
        "focus", "study"                -> "focus study instrumental lofi beats"
        "chill"                         -> "chill lofi ambient relaxing vibes"
        "romantic", "romance", "love"   -> "romantic love songs slow dance"
        "sleep", "night"                -> "sleep ambient calm lullaby piano"
        "throwback", "nostalgia"        -> "throwback 90s 2000s classic hits"
        "trending", "popular"           -> "top hits 2025 trending popular"
        else                            -> "top music 2025 popular hits"
    }
}

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String,
    val ownerName: String
)