package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class SearchHistoryEntry(
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

class SearchHistoryRepository {

    private val TAG = "SearchHistoryRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Call this from HomeScreen whenever user searches (query.length >= 3)
    fun saveSearch(query: String) {
        val userId = auth.currentUser?.uid ?: return
        if (query.isBlank()) return

        val data = hashMapOf(
            "query"     to query.trim(),
            "timestamp" to Timestamp.now()
        )

        db.collection("searchHistory")
            .document(userId)
            .collection("queries")
            .add(data)
            .addOnSuccessListener { Log.d(TAG, "✅ Search saved: $query") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Failed to save search", e) }
    }

    // Load last 20 searches for display (e.g. in HomeScreen suggestions)
    suspend fun getRecentSearches(): List<SearchHistoryEntry> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("searchHistory")
                .document(userId)
                .collection("queries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val query = doc.getString("query") ?: return@mapNotNull null
                val ts    = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                SearchHistoryEntry(query = query, timestamp = ts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load search history", e)
            emptyList()
        }
    }

    // Clear all search history
    suspend fun clearSearchHistory() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val snapshot = db.collection("searchHistory")
                .document(userId)
                .collection("queries")
                .get()
                .await()
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Log.d(TAG, "✅ Search history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear search history", e)
        }
    }
}