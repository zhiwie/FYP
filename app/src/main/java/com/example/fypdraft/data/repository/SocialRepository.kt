package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ── Data models ──────────────────────────────────────────────────────────

data class MusicMoment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val trackTitle: String = "",
    val trackArtist: String = "",
    val albumArtUrl: String = "",
    val spotifyUri: String? = null,
    val mood: String = "neutral",
    val moodEmoji: String = "🎵",
    val caption: String = "",
    val isVibeCheck: Boolean = false, // true = manual post, false = auto
    val timestamp: Long = System.currentTimeMillis(),
    val reactions: Map<String, String> = emptyMap() // userId -> emoji
)

data class FriendProfile(
    val uid: String,
    val displayName: String,
    val isOnline: Boolean = false,
    val lastActive: Long = 0L,
    val currentMoment: MusicMoment? = null
)

// ── Repository ───────────────────────────────────────────────────────────

class SocialRepository {
    private val TAG = "SocialRepository"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userId(): String? = auth.currentUser?.uid
    private fun userName(): String = auth.currentUser?.displayName ?: "Someone"

    // ── Auto-share current track ─────────────────────────────────────

    suspend fun shareNowPlaying(
        trackTitle: String,
        trackArtist: String,
        albumArtUrl: String,
        spotifyUri: String?,
        mood: String
    ) {
        val uid = userId() ?: return
        try {
            val moment = mapOf(
                "userId" to uid,
                "userName" to userName(),
                "trackTitle" to trackTitle,
                "trackArtist" to trackArtist,
                "albumArtUrl" to albumArtUrl,
                "spotifyUri" to spotifyUri,
                "mood" to mood,
                "moodEmoji" to getMoodEmoji(mood),
                "caption" to "",
                "isVibeCheck" to false,
                "timestamp" to Timestamp.now(),
                "reactions" to emptyMap<String, String>()
            )
            // Update the user's "current" moment (overwrites previous)
            db.collection("moments").document(uid).set(moment).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share now playing", e)
        }
    }

    // ── Manual vibe check post ───────────────────────────────────────

    suspend fun postVibeCheck(
        trackTitle: String,
        trackArtist: String,
        albumArtUrl: String,
        spotifyUri: String?,
        mood: String,
        caption: String
    ) {
        val uid = userId() ?: return
        try {
            val moment = mapOf(
                "userId" to uid,
                "userName" to userName(),
                "trackTitle" to trackTitle,
                "trackArtist" to trackArtist,
                "albumArtUrl" to albumArtUrl,
                "spotifyUri" to spotifyUri,
                "mood" to mood,
                "moodEmoji" to getMoodEmoji(mood),
                "caption" to caption,
                "isVibeCheck" to true,
                "timestamp" to Timestamp.now(),
                "reactions" to emptyMap<String, String>()
            )
            // Post to the user's current moment
            db.collection("moments").document(uid).set(moment).await()
            // Also add to the feed history
            db.collection("moments").document(uid)
                .collection("history").add(moment).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post vibe check", e)
        }
    }

    // ── React to a friend's moment ───────────────────────────────────

    suspend fun reactToMoment(friendUid: String, emoji: String) {
        val uid = userId() ?: return
        try {
            db.collection("moments").document(friendUid)
                .update("reactions.$uid", emoji).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to react", e)
        }
    }

    // ── Get friends' moments (feed) ──────────────────────────────────

    suspend fun getFriendsMoments(): List<MusicMoment> {
        val uid = userId() ?: return emptyList()
        try {
            // Get friend UIDs
            val friendDocs = db.collection("users").document(uid)
                .collection("friends").get().await()
            val friendUids = friendDocs.documents.mapNotNull { it.getString("uid") }

            if (friendUids.isEmpty()) return emptyList()

            val moments = mutableListOf<MusicMoment>()
            for (fuid in friendUids) {
                try {
                    val doc = db.collection("moments").document(fuid).get().await()
                    if (doc.exists()) {
                        moments.add(documentToMoment(doc.id, doc.data ?: continue))
                    }
                } catch (_: Exception) {}
            }

            // Sort by most recent
            return moments.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load friends moments", e)
            return emptyList()
        }
    }

    // ── Get friend list with profiles ────────────────────────────────

    suspend fun getFriendsWithProfiles(): List<FriendProfile> {
        val uid = userId() ?: return emptyList()
        try {
            val friendDocs = db.collection("users").document(uid)
                .collection("friends").get().await()
            val friendUids = friendDocs.documents.mapNotNull { it.getString("uid") }

            return friendUids.mapNotNull { fuid ->
                try {
                    val userDoc = db.collection("users").document(fuid).get().await()
                    val name = userDoc.getString("displayName")
                        ?: userDoc.getString("username") ?: "Unknown"

                    val momentDoc = db.collection("moments").document(fuid).get().await()
                    val moment = if (momentDoc.exists()) {
                        documentToMoment(momentDoc.id, momentDoc.data ?: emptyMap())
                    } else null

                    val lastActive = moment?.timestamp ?: 0L
                    val isOnline = (System.currentTimeMillis() - lastActive) < 15 * 60 * 1000

                    FriendProfile(
                        uid = fuid,
                        displayName = name,
                        isOnline = isOnline,
                        lastActive = lastActive,
                        currentMoment = moment
                    )
                } catch (_: Exception) { null }
            }.sortedWith(compareByDescending<FriendProfile> { it.isOnline }
                .thenByDescending { it.lastActive })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load friends", e)
            return emptyList()
        }
    }

    // ── Add friend by username ───────────────────────────────────────

    suspend fun addFriend(username: String): Result<String> {
        val uid = userId() ?: return Result.failure(Exception("Not logged in"))
        try {
            val query = db.collection("users")
                .whereEqualTo("username", username.lowercase().trim())
                .limit(1).get().await()

            if (query.isEmpty) return Result.failure(Exception("User '$username' not found"))

            val friendDoc = query.documents.first()
            val friendUid = friendDoc.id

            if (friendUid == uid) return Result.failure(Exception("That's you!"))

            val existing = db.collection("users").document(uid)
                .collection("friends")
                .whereEqualTo("uid", friendUid).get().await()

            if (!existing.isEmpty) return Result.failure(Exception("Already friends"))

            // Add both directions
            db.collection("users").document(uid).collection("friends")
                .add(mapOf("uid" to friendUid, "addedAt" to Timestamp.now()))
            db.collection("users").document(friendUid).collection("friends")
                .add(mapOf("uid" to uid, "addedAt" to Timestamp.now()))

            val name = friendDoc.getString("displayName") ?: username
            return Result.success(name)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // ── Get my current moment ────────────────────────────────────────

    suspend fun getMyMoment(): MusicMoment? {
        val uid = userId() ?: return null
        return try {
            val doc = db.collection("moments").document(uid).get().await()
            if (doc.exists()) documentToMoment(doc.id, doc.data ?: return null)
            else null
        } catch (_: Exception) { null }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun documentToMoment(id: String, data: Map<String, Any?>): MusicMoment {
        val ts = data["timestamp"]
        val timeMs = when (ts) {
            is Timestamp -> ts.toDate().time
            is Long -> ts
            else -> System.currentTimeMillis()
        }
        return MusicMoment(
            id = id,
            userId = data["userId"] as? String ?: "",
            userName = data["userName"] as? String ?: "Someone",
            trackTitle = data["trackTitle"] as? String ?: "",
            trackArtist = data["trackArtist"] as? String ?: "",
            albumArtUrl = data["albumArtUrl"] as? String ?: "",
            spotifyUri = data["spotifyUri"] as? String,
            mood = data["mood"] as? String ?: "neutral",
            moodEmoji = data["moodEmoji"] as? String ?: "🎵",
            caption = data["caption"] as? String ?: "",
            isVibeCheck = data["isVibeCheck"] as? Boolean ?: false,
            timestamp = timeMs,
            reactions = (data["reactions"] as? Map<String, String>) ?: emptyMap()
        )
    }

    private fun getMoodEmoji(mood: String): String = when (mood) {
        "happy" -> "😊"; "sad" -> "😢"; "calm" -> "😌"; "energetic" -> "⚡"
        "tired" -> "😴"; "focused" -> "🎯"; "romantic" -> "💕"; else -> "🎵"
    }
}