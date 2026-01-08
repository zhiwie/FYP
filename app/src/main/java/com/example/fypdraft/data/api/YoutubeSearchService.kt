package com.example.fypdraft.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class YouTubeSearchService {

    companion object {
        private const val TAG = "YouTubeSearchService"

        // TODO: Replace with your YouTube API key from Google Cloud Console
        private const val YOUTUBE_API_KEY = "AIzaSyDVsq1vdAz-cumF3e8voJ6PVv2Jgv5eajE"

        suspend fun searchVideoId(songName: String, artistName: String): String? {
            return withContext(Dispatchers.IO) {
                try {
                    // Try multiple search queries
                    val searchQueries = listOf(
                        "$songName $artistName official audio",
                        "$songName $artistName official",
                        "$songName by $artistName",
                        "$artistName $songName"
                    )

                    for (query in searchQueries) {
                        Log.d(TAG, "🔍 Searching YouTube: $query")

                        val encodedQuery = URLEncoder.encode(query, "UTF-8")
                        val apiUrl = "https://www.googleapis.com/youtube/v3/search?" +
                                "part=snippet" +
                                "&q=$encodedQuery" +
                                "&type=video" +
                                "&videoCategoryId=10" + // Music category
                                "&maxResults=5" +
                                "&key=$YOUTUBE_API_KEY"

                        val connection = URL(apiUrl).openConnection()
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000

                        val response = connection.getInputStream().bufferedReader().readText()
                        val json = JSONObject(response)

                        // Check if we have results
                        if (json.has("items")) {
                            val items = json.getJSONArray("items")

                            // Try each video until we find an embeddable one
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val videoId = item.getJSONObject("id").getString("videoId")

                                // Verify video is embeddable
                                if (isVideoEmbeddable(videoId)) {
                                    Log.d(TAG, "✓ Found embeddable video: $videoId for: $query")
                                    return@withContext videoId
                                } else {
                                    Log.d(TAG, "✗ Video $videoId not embeddable, trying next...")
                                }
                            }
                        }
                    }

                    Log.w(TAG, "❌ No embeddable video found for: $songName - $artistName")
                    null

                } catch (e: Exception) {
                    Log.e(TAG, "YouTube API error", e)
                    null
                }
            }
        }

        private suspend fun isVideoEmbeddable(videoId: String): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val apiUrl = "https://www.googleapis.com/youtube/v3/videos?" +
                            "part=status" +
                            "&id=$videoId" +
                            "&key=$YOUTUBE_API_KEY"

                    val connection = URL(apiUrl).openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val response = connection.getInputStream().bufferedReader().readText()
                    val json = JSONObject(response)

                    if (json.has("items")) {
                        val items = json.getJSONArray("items")
                        if (items.length() > 0) {
                            val status = items.getJSONObject(0).getJSONObject("status")
                            val embeddable = status.getBoolean("embeddable")
                            Log.d(TAG, "Video $videoId embeddable: $embeddable")
                            return@withContext embeddable
                        }
                    }

                    false
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify embeddable status for: $videoId", e)
                    // If we can't verify, assume it's okay to try
                    true
                }
            }
        }
    }
}