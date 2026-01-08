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

        // This searches YouTube without needing API key
        // Uses web scraping method (more reliable for your use case)
        suspend fun searchVideoId(songName: String, artistName: String): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val query = "$songName $artistName official audio"
                    val encodedQuery = URLEncoder.encode(query, "UTF-8")

                    // YouTube search URL
                    val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

                    val connection = URL(searchUrl).openConnection()
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                    val response = connection.getInputStream().bufferedReader().readText()

                    // Extract video ID from response
                    val videoIdPattern = """"videoId":"([^"]+)"""".toRegex()
                    val match = videoIdPattern.find(response)

                    val videoId = match?.groupValues?.get(1)

                    if (videoId != null) {
                        Log.d(TAG, "Found YouTube video ID: $videoId for $query")
                    } else {
                        Log.w(TAG, "No video ID found for: $query")
                    }

                    videoId
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching YouTube", e)
                    null
                }
            }
        }
    }
}