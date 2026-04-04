package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    val isVibeCheck: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val reactions: Map<String, String> = emptyMap()
)

data class FriendProfile(
    val uid: String,
    val displayName: String,
    val isOnline: Boolean = false,
    val lastActive: Long = 0L,
    val currentMoment: MusicMoment? = null
)

/** A single message in a 1-to-1 friend chat. */
data class FriendChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false
)

// ── Repository ───────────────────────────────────────────────────────────

class SocialRepository {
    private val TAG = "SocialRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid
    private fun uname(): String = auth.currentUser?.displayName ?: "Someone"

    // ── Now-playing auto-share ────────────────────────────────────────
    suspend fun shareNowPlaying(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?, mood: String
    ) {
        val me = uid() ?: return
        try {
            db.collection("moments").document(me).set(mapOf(
                "userId" to me, "userName" to uname(),
                "trackTitle" to trackTitle, "trackArtist" to trackArtist,
                "albumArtUrl" to albumArtUrl, "spotifyUri" to spotifyUri,
                "mood" to mood, "moodEmoji" to moodEmoji(mood),
                "caption" to "", "isVibeCheck" to false,
                "timestamp" to Timestamp.now(), "reactions" to emptyMap<String, String>()
            )).await()
        } catch (e: Exception) { Log.e(TAG, "shareNowPlaying", e) }
    }

    // ── Manual vibe check ─────────────────────────────────────────────
    suspend fun postVibeCheck(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?,
        mood: String, caption: String
    ) {
        val me = uid() ?: return
        val data = mapOf(
            "userId" to me, "userName" to uname(),
            "trackTitle" to trackTitle, "trackArtist" to trackArtist,
            "albumArtUrl" to albumArtUrl, "spotifyUri" to spotifyUri,
            "mood" to mood, "moodEmoji" to moodEmoji(mood),
            "caption" to caption, "isVibeCheck" to true,
            "timestamp" to Timestamp.now(), "reactions" to emptyMap<String, String>()
        )
        try {
            db.collection("moments").document(me).set(data).await()
            db.collection("moments").document(me).collection("history").add(data).await()
        } catch (e: Exception) { Log.e(TAG, "postVibeCheck", e) }
    }

    suspend fun reactToMoment(friendUid: String, emoji: String) {
        val me = uid() ?: return
        try {
            db.collection("moments").document(friendUid)
                .update("reactions.$me", emoji).await()
        } catch (e: Exception) { Log.e(TAG, "react", e) }
    }

    suspend fun getMyMoment(): MusicMoment? {
        val me = uid() ?: return null
        return try {
            val doc = db.collection("moments").document(me).get().await()
            if (doc.exists()) docToMoment(doc.id, doc.data ?: return null) else null
        } catch (_: Exception) { null }
    }

    // ── Friends ───────────────────────────────────────────────────────
    suspend fun addFriend(username: String): Result<String> {
        val me = uid() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val q = db.collection("users")
                .whereEqualTo("username", username.lowercase().trim())
                .limit(1).get().await()
            if (q.isEmpty) return Result.failure(Exception("User '$username' not found"))
            val friendDoc = q.documents.first()
            val fuid = friendDoc.id
            if (fuid == me) return Result.failure(Exception("That's you!"))
            val existing = db.collection("users").document(me).collection("friends")
                .whereEqualTo("uid", fuid).get().await()
            if (!existing.isEmpty) return Result.failure(Exception("Already friends"))
            db.collection("users").document(me).collection("friends")
                .add(mapOf("uid" to fuid, "addedAt" to Timestamp.now()))
            db.collection("users").document(fuid).collection("friends")
                .add(mapOf("uid" to me, "addedAt" to Timestamp.now()))
            Result.success(friendDoc.getString("displayName") ?: username)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFriendsWithProfiles(): List<FriendProfile> {
        val me = uid() ?: return emptyList()
        return try {
            val uids = db.collection("users").document(me).collection("friends")
                .get().await().documents.mapNotNull { it.getString("uid") }
            uids.mapNotNull { fuid ->
                try {
                    val userDoc = db.collection("users").document(fuid).get().await()
                    val name = userDoc.getString("displayName") ?: userDoc.getString("username") ?: "Unknown"
                    val momentDoc = db.collection("moments").document(fuid).get().await()
                    val moment = if (momentDoc.exists()) docToMoment(momentDoc.id, momentDoc.data ?: emptyMap()) else null
                    val lastActive = moment?.timestamp ?: 0L
                    FriendProfile(fuid, name,
                        isOnline = (System.currentTimeMillis() - lastActive) < 15 * 60 * 1000,
                        lastActive = lastActive, currentMoment = moment)
                } catch (_: Exception) { null }
            }.sortedWith(compareByDescending<FriendProfile> { it.isOnline }.thenByDescending { it.lastActive })
        } catch (e: Exception) { Log.e(TAG, "getFriends", e); emptyList() }
    }

    // ── Per-friend chat ───────────────────────────────────────────────

    /**
     * Returns the chat collection path for a conversation between [me] and [friendUid].
     * We always use the lexicographically smaller UID first so both users share the same doc.
     */
    private fun chatDocId(meUid: String, friendUid: String): String =
        if (meUid < friendUid) "${meUid}_${friendUid}" else "${friendUid}_${meUid}"

    suspend fun sendChatMessage(friendUid: String, text: String) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            val msgData = mapOf(
                "senderId"  to me,
                "text"      to text,
                "timestamp" to Timestamp.now()
            )
            db.collection("chats").document(convoId).collection("messages").add(msgData).await()
            // Update conversation metadata for badge counts etc.
            db.collection("chats").document(convoId).set(mapOf(
                "participants"  to listOf(me, friendUid),
                "lastMessage"   to text,
                "lastTimestamp" to Timestamp.now(),
                "lastSenderId"  to me
            ), com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) { Log.e(TAG, "sendChat", e) }
    }

    suspend fun getChatMessages(friendUid: String): List<FriendChatMessage> {
        val me = uid() ?: return emptyList()
        return try {
            val convoId = chatDocId(me, friendUid)
            val snap = db.collection("chats").document(convoId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(100)
                .get().await()
            snap.documents.map { doc ->
                val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                FriendChatMessage(
                    id         = doc.id,
                    senderId   = doc.getString("senderId") ?: "",
                    text       = doc.getString("text") ?: "",
                    timestamp  = ts,
                    isFromMe   = doc.getString("senderId") == me
                )
            }
        } catch (e: Exception) { Log.e(TAG, "getMessages", e); emptyList() }
    }

    // ── Helpers ──────────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun docToMoment(id: String, data: Map<String, Any?>): MusicMoment {
        val ts = when (val t = data["timestamp"]) {
            is Timestamp -> t.toDate().time
            is Long      -> t
            else         -> System.currentTimeMillis()
        }
        return MusicMoment(
            id = id, userId = data["userId"] as? String ?: "",
            userName = data["userName"] as? String ?: "Someone",
            trackTitle = data["trackTitle"] as? String ?: "",
            trackArtist = data["trackArtist"] as? String ?: "",
            albumArtUrl = data["albumArtUrl"] as? String ?: "",
            spotifyUri  = data["spotifyUri"] as? String,
            mood = data["mood"] as? String ?: "neutral",
            moodEmoji = data["moodEmoji"] as? String ?: "🎵",
            caption = data["caption"] as? String ?: "",
            isVibeCheck = data["isVibeCheck"] as? Boolean ?: false,
            timestamp = ts,
            reactions = (data["reactions"] as? Map<String, String>) ?: emptyMap()
        )
    }

    private fun moodEmoji(mood: String): String = when (mood) {
        "happy" -> "😊"; "sad" -> "😢"; "calm" -> "😌"; "energetic" -> "⚡"
        "tired" -> "😴"; "focused" -> "🎯"; "romantic" -> "💕"; else -> "🎵"
    }
}