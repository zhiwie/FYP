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
            Log.d(TAG, "Using cached video ID for: $cacheKey")
            return it
        }

        // Search YouTube
        val videoId = YouTubeSearchService.searchVideoId(track.name, track.artist)

        // Cache result
        videoId?.let {
            videoIdCache[cacheKey] = it
        }

        return videoId
    }

    fun clearCache() {
        videoIdCache.clear()
    }
}