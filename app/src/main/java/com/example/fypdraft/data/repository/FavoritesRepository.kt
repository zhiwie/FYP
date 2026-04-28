package com.example.fypdraft.data.repository

import com.example.fypdraft.model.SongRecommendation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

class FavoritesRepository {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserId() = auth.currentUser?.uid
        ?: throw IllegalStateException("User not logged in")

    suspend fun saveFavorite(song: SongRecommendation) {
        saveFavoriteGetId(song)
    }

    /** Saves and returns the new Firestore document ID so the caller can remove it later. */
    suspend fun saveFavoriteGetId(song: SongRecommendation): String {
        val userId = getUserId()
        val data = hashMapOf(
            "artist"         to song.artist,
            "title"          to song.title,
            "reason"         to song.reason,
            "youtubeVideoId" to song.youtubeVideoId,
            "savedAt"        to Timestamp.now()
        )
        val ref = db.collection("favorites")
            .document(userId)
            .collection("songs")
            .add(data)
            .await()
        return ref.id
    }

    /** Returns the Firestore doc ID if this track is already liked, null otherwise. */
    suspend fun findFavorite(title: String, artist: String): String? {
        val userId = getUserId()
        val snapshot = db.collection("favorites")
            .document(userId)
            .collection("songs")
            .whereEqualTo("title",  title)
            .whereEqualTo("artist", artist)
            .limit(1)
            .get().await()
        return snapshot.documents.firstOrNull()?.id
    }

    suspend fun removeFavorite(songId: String) {
        val userId = getUserId()
        db.collection("favorites")
            .document(userId)
            .collection("songs")
            .document(songId)
            .delete()
            .await()
    }

    fun observeFavorites(): Flow<List<Pair<String, SongRecommendation>>> = callbackFlow {
        val userId = getUserId()
        val listener = db.collection("favorites")
            .document(userId)
            .collection("songs")
            .orderBy("savedAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val songs = snapshot.documents.map { doc ->
                    doc.id to SongRecommendation(
                        artist    = doc.getString("artist") ?: "",
                        title     = doc.getString("title") ?: "",
                        reason    = doc.getString("reason") ?: "",
                        youtubeVideoId = doc.getString("youtubeVideoId") ?: ""
                    )
                }
                trySend(songs)
            }
        awaitClose { listener.remove() }
    }
}