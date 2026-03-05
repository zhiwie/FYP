package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.data.api.YouTubeSearchService
import com.example.fypdraft.model.Track

class YouTubeRepository {

    private val TAG = "YouTubeRepo"
    private val cache = mutableMapOf<String, String>()

    suspend fun getVideoId(track: Track): String? {
        val key = "${track.name}-${track.artist}"
        cache[key]?.let { return it }

        val videoId = YouTubeSearchService.searchVideoId(track.name, track.artist)
        videoId?.let { cache[key] = it }
        return videoId
    }

    fun clearCache() = cache.clear()
}