package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.core.network.NetworkModule
import com.example.fypdraft.data.api.toAppTrack
import com.example.fypdraft.model.Track

/**
 * Handles music discovery via Deezer (no auth required).
 * Renamed from DeezerRepository to reflect its role.
 */
class MusicSearchRepository {

    private val TAG = "MusicSearchRepo"
    private val api = NetworkModule.deezerApi

    suspend fun searchTracks(query: String, limit: Int = 50): List<Track> {
        if (query.isBlank()) return emptyList()
        return try {
            api.search(query = query, limit = limit).data?.map { it.toAppTrack() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    suspend fun getTopTracks(): List<Track> {
        return try {
            api.getChart().tracks?.data?.map { it.toAppTrack() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Chart failed", e)
            emptyList()
        }
    }

    suspend fun getTracksByMood(mood: String): List<Track> {
        val query = when (mood.lowercase()) {
            "happy", "energetic" -> "happy upbeat dance"
            "sad", "melancholy"  -> "sad emotional ballad"
            "calm", "relaxed"    -> "calm peaceful ambient"
            "focus", "study"     -> "focus instrumental concentration"
            "romantic"           -> "romantic love songs"
            "chill"              -> "chill relaxing lounge"
            else                 -> mood
        }
        return searchTracks(query)
    }
}