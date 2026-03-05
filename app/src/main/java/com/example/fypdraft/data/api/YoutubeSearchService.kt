package com.example.fypdraft.data.api

import android.util.Log
import com.example.fypdraft.core.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

object YouTubeSearchService {

    private const val TAG = "YouTubeSearch"

    suspend fun searchVideoId(songName: String, artistName: String): String? =
        withContext(Dispatchers.IO) {
            val queries = listOf(
                "$songName $artistName official audio",
                "$songName $artistName official",
                "$songName by $artistName"
            )

            for (query in queries) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.googleapis.com/youtube/v3/search?" +
                        "part=snippet&q=$encoded&type=video&videoCategoryId=10" +
                        "&maxResults=5&key=${AppConfig.YOUTUBE_API_KEY}"

                try {
                    val json = JSONObject(URL(url).readText())
                    val items = json.optJSONArray("items") ?: continue

                    for (i in 0 until items.length()) {
                        val videoId = items.getJSONObject(i)
                            .getJSONObject("id").getString("videoId")
                        if (isEmbeddable(videoId)) return@withContext videoId
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed for: $query", e)
                }
            }
            null
        }

    private fun isEmbeddable(videoId: String): Boolean {
        return try {
            val url = "https://www.googleapis.com/youtube/v3/videos?" +
                    "part=status&id=$videoId&key=${AppConfig.YOUTUBE_API_KEY}"
            val json = JSONObject(URL(url).readText())
            json.optJSONArray("items")
                ?.optJSONObject(0)
                ?.optJSONObject("status")
                ?.optBoolean("embeddable", false) ?: false
        } catch (e: Exception) {
            true // assume embeddable if check fails
        }
    }
}