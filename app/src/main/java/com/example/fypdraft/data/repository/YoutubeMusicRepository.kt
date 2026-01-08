package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.data.api.YouTubeSearchService
import com.example.fypdraft.model.Track

class YouTubeMusicRepository {

    companion object {
        private const val TAG = "YouTubeMusicRepository"
    }

    // Cache to avoid searching same song twice
    private val videoIdCache = mutableMapOf<String, String>()

    suspend fun getYouTubeVideoId(track: Track): String? {
        val cacheKey = "${track.name}-${track.artist}"

        // Check cache first
        videoIdCache[cacheKey]?.let {
            Log.d(TAG, "✓ Using cached video ID for: $cacheKey")
            return it
        }

        Log.d(TAG, "🔍 Searching YouTube for: ${track.name} by ${track.artist}")

        // Search YouTube - the API version already checks if videos are embeddable
        var videoId = YouTubeSearchService.searchVideoId(track.name, track.artist)

        // If not found, try with simplified search
        if (videoId == null) {
            Log.d(TAG, "Retrying with simplified search...")
            videoId = searchWithFallback(track)
        }

        // Cache successful result
        videoId?.let {
            videoIdCache[cacheKey] = it
            Log.d(TAG, "✓ Successfully found and cached video: $it")
        } ?: Log.e(TAG, "✗ Could not find playable video for: $cacheKey")

        return videoId
    }

    private suspend fun searchWithFallback(track: Track): String? {
        // Try artist name only (for well-known artists)
        return YouTubeSearchService.searchVideoId(track.artist, "music")
    }

    fun clearCache() {
        videoIdCache.clear()
        Log.d(TAG, "Cache cleared")
    }
}