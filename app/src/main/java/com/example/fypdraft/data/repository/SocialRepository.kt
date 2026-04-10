package com.example.fypdraft.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

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

data class FriendChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false
)

data class FriendSuggestion(
    val uid: String,
    val displayName: String,
    val mutualFriendCount: Int = 0,
    val matchedByPhone: Boolean = false
)

// ── Repository ───────────────────────────────────────────────────────────

class SocialRepository {
    private val TAG = "SocialRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid
    private fun uname(): String = auth.currentUser?.displayName ?: "Someone"

    // ── Add friend by username ────────────────────────────────────────

    suspend fun addFriend(username: String): Result<String> {
        val me = uid() ?: return Result.failure(Exception("Not logged in"))
        return try {
            val q = db.collection("users")
                .whereEqualTo("username", username.lowercase().trim())
                .limit(1).get().await()

            if (q.isEmpty) return Result.failure(Exception("User '$username' not found"))

            val friendDoc = q.documents.first()
            val fuid      = friendDoc.id
            if (fuid == me) return Result.failure(Exception("That's you!"))

            val existing = db.collection("users").document(me)
                .collection("friends").whereEqualTo("uid", fuid).get().await()
            if (!existing.isEmpty) return Result.failure(Exception("Already friends"))

            db.collection("users").document(me).collection("friends")
                .add(mapOf("uid" to fuid, "addedAt" to Timestamp.now())).await()
            db.collection("users").document(fuid).collection("friends")
                .add(mapOf("uid" to me, "addedAt" to Timestamp.now())).await()

            Result.success(friendDoc.getString("displayName") ?: username)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Mutual friend suggestions ─────────────────────────────────────

    suspend fun getMutualFriendSuggestions(limit: Int = 10): List<FriendSuggestion> {
        val me = uid() ?: return emptyList()
        return try {
            val myFriendUids = db.collection("users").document(me)
                .collection("friends").get().await()
                .documents.mapNotNull { it.getString("uid") }.toSet()

            if (myFriendUids.isEmpty()) return emptyList()

            val mutualCount = mutableMapOf<String, Int>()
            for (fuid in myFriendUids) {
                try {
                    val theirFriends = db.collection("users").document(fuid)
                        .collection("friends").get().await()
                        .documents.mapNotNull { it.getString("uid") }
                    for (candidate in theirFriends) {
                        if (candidate != me && candidate !in myFriendUids) {
                            mutualCount[candidate] = (mutualCount[candidate] ?: 0) + 1
                        }
                    }
                } catch (_: Exception) {}
            }

            mutualCount.entries
                .sortedByDescending { it.value }
                .take(limit)
                .mapNotNull { (candidateUid, count) ->
                    try {
                        val doc  = db.collection("users").document(candidateUid).get().await()
                        val name = doc.getString("displayName") ?: doc.getString("username") ?: return@mapNotNull null
                        FriendSuggestion(uid = candidateUid, displayName = name, mutualFriendCount = count)
                    } catch (_: Exception) { null }
                }
        } catch (e: Exception) {
            Log.e(TAG, "getMutualFriendSuggestions", e)
            emptyList()
        }
    }

    // ── Phone contact matching ────────────────────────────────────────

    suspend fun uploadContactHashes(context: Context): Boolean {
        val me = uid() ?: return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return false

        return try {
            val numbers = mutableSetOf<String>()
            val cursor  = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER),
                null, null, null
            )
            cursor?.use {
                val col = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                while (it.moveToNext()) {
                    val num = it.getString(col)?.trim() ?: continue
                    if (num.isNotEmpty()) numbers.add(num)
                }
            }

            val hashes = numbers.map { sha256(it) }
            db.collection("contactHashes").document(me)
                .set(mapOf("hashes" to hashes, "updatedAt" to Timestamp.now())).await()

            val myNumber = getMyOwnNumber(context)
            if (myNumber != null) {
                val myHash = sha256(myNumber)
                db.collection("users").document(me).update("phoneHash", myHash).await()
            }

            Log.d(TAG, "Uploaded ${hashes.size} contact hashes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadContactHashes failed", e)
            false
        }
    }

    suspend fun getPhoneContactSuggestions(): List<FriendSuggestion> {
        val me = uid() ?: return emptyList()
        return try {
            val doc = db.collection("contactHashes").document(me).get().await()
            @Suppress("UNCHECKED_CAST")
            val hashes = (doc.get("hashes") as? List<String>) ?: return emptyList()
            if (hashes.isEmpty()) return emptyList()

            val myFriendUids = db.collection("users").document(me)
                .collection("friends").get().await()
                .documents.mapNotNull { it.getString("uid") }.toSet()

            val suggestions = mutableListOf<FriendSuggestion>()
            hashes.chunked(30).forEach { batch ->
                try {
                    val snap = db.collection("users").whereIn("phoneHash", batch).get().await()
                    snap.documents.forEach { d ->
                        val candidateUid = d.id
                        if (candidateUid != me && candidateUid !in myFriendUids) {
                            val name = d.getString("displayName") ?: d.getString("username") ?: return@forEach
                            suggestions.add(FriendSuggestion(uid = candidateUid, displayName = name, matchedByPhone = true))
                        }
                    }
                } catch (_: Exception) {}
            }
            suggestions.distinctBy { it.uid }
        } catch (e: Exception) {
            Log.e(TAG, "getPhoneContactSuggestions failed", e)
            emptyList()
        }
    }

    suspend fun getAllSuggestions(context: Context? = null): List<FriendSuggestion> {
        val mutual = getMutualFriendSuggestions()
        val phone  = if (context != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) getPhoneContactSuggestions() else emptyList()

        val map = mutableMapOf<String, FriendSuggestion>()
        mutual.forEach { map[it.uid] = it }
        phone.forEach { phoneSug ->
            map[phoneSug.uid] = (map[phoneSug.uid] ?: phoneSug).copy(matchedByPhone = true)
        }
        return map.values
            .sortedWith(compareByDescending<FriendSuggestion> { it.mutualFriendCount }
                .thenByDescending { it.matchedByPhone })
    }

    // ── Now-playing auto-share ────────────────────────────────────────

    suspend fun shareNowPlaying(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?, mood: String
    ) {
        val me = uid() ?: return
        try {
            db.collection("moments").document(me).set(mapOf(
                "userId"      to me,
                "userName"    to uname(),
                "trackTitle"  to trackTitle,
                "trackArtist" to trackArtist,
                "albumArtUrl" to albumArtUrl,
                "spotifyUri"  to spotifyUri,
                "mood"        to mood,
                "moodEmoji"   to moodEmoji(mood),
                "caption"     to "",
                "isVibeCheck" to false,
                "timestamp"   to Timestamp.now(),
                "reactions"   to emptyMap<String, String>()
            )).await()
        } catch (e: Exception) { Log.e(TAG, "shareNowPlaying", e) }
    }

    // ── Manual vibe check ─────────────────────────────────────────────

    suspend fun postVibeCheck(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?,
        mood: String, caption: String
    ) {
        val me   = uid() ?: return
        val data = mapOf(
            "userId"      to me,
            "userName"    to uname(),
            "trackTitle"  to trackTitle,
            "trackArtist" to trackArtist,
            "albumArtUrl" to albumArtUrl,
            "spotifyUri"  to spotifyUri,
            "mood"        to mood,
            "moodEmoji"   to moodEmoji(mood),
            "caption"     to caption,
            "isVibeCheck" to true,
            "timestamp"   to Timestamp.now(),
            "reactions"   to emptyMap<String, String>()
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

    // ── Vibe history (last 24 h for a friend) ─────────────────────────

    suspend fun getVibeHistory(friendUid: String): List<MusicMoment> {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        return try {
            val snap = db.collection("moments")
                .document(friendUid)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            snap.documents.mapNotNull { doc ->
                val data   = doc.data ?: return@mapNotNull null
                val moment = docToMoment(doc.id, data)
                if (moment.timestamp >= cutoff) moment else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getVibeHistory failed", e)
            emptyList()
        }
    }

    // ── Friends list with profiles ────────────────────────────────────

    suspend fun getFriendsWithProfiles(): List<FriendProfile> {
        val me = uid() ?: return emptyList()
        return try {
            val uids = db.collection("users").document(me).collection("friends")
                .get().await().documents.mapNotNull { it.getString("uid") }
            uids.mapNotNull { fuid ->
                try {
                    val userDoc = db.collection("users").document(fuid).get().await()
                    val name    = userDoc.getString("displayName")
                        ?: userDoc.getString("username") ?: "Unknown"
                    val momentDoc = db.collection("moments").document(fuid).get().await()
                    val moment    = if (momentDoc.exists())
                        docToMoment(momentDoc.id, momentDoc.data ?: emptyMap()) else null
                    val lastActive = moment?.timestamp ?: 0L
                    FriendProfile(
                        fuid, name,
                        isOnline      = (System.currentTimeMillis() - lastActive) < 15 * 60_000,
                        lastActive    = lastActive,
                        currentMoment = moment
                    )
                } catch (_: Exception) { null }
            }.sortedWith(
                compareByDescending<FriendProfile> { it.isOnline }
                    .thenByDescending { it.lastActive }
            )
        } catch (e: Exception) { Log.e(TAG, "getFriends", e); emptyList() }
    }

    // ── Chat ──────────────────────────────────────────────────────────

    private fun chatDocId(a: String, b: String): String =
        if (a < b) "${a}_${b}" else "${b}_${a}"

    suspend fun sendChatMessage(friendUid: String, text: String) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            db.collection("chats").document(convoId).set(
                mapOf(
                    "participants"  to listOf(me, friendUid),
                    "lastMessage"   to text,
                    "lastTimestamp" to Timestamp.now(),
                    "lastSenderId"  to me
                ),
                SetOptions.merge()
            ).await()
            db.collection("chats").document(convoId).collection("messages")
                .add(mapOf("senderId" to me, "text" to text, "timestamp" to Timestamp.now()))
                .await()
        } catch (e: Exception) { Log.e(TAG, "sendChat", e) }
    }

    suspend fun getChatMessages(friendUid: String): List<FriendChatMessage> {
        val me = uid() ?: return emptyList()
        return try {
            val convoId = chatDocId(me, friendUid)
            db.collection("chats").document(convoId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(100).get().await()
                .documents.map { doc ->
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: System.currentTimeMillis()
                    FriendChatMessage(
                        id       = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        text     = doc.getString("text") ?: "",
                        timestamp = ts,
                        isFromMe  = doc.getString("senderId") == me
                    )
                }
        } catch (e: Exception) { Log.e(TAG, "getMessages", e); emptyList() }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getMyOwnNumber(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            @Suppress("MissingPermission")
            tm?.line1Number?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToMoment(id: String, data: Map<String, Any?>): MusicMoment {
        val ts = when (val t = data["timestamp"]) {
            is Timestamp -> t.toDate().time
            is Long      -> t
            else         -> System.currentTimeMillis()
        }
        return MusicMoment(
            id          = id,
            userId      = data["userId"]      as? String ?: "",
            userName    = data["userName"]    as? String ?: "Someone",
            trackTitle  = data["trackTitle"]  as? String ?: "",
            trackArtist = data["trackArtist"] as? String ?: "",
            albumArtUrl = data["albumArtUrl"] as? String ?: "",
            spotifyUri  = data["spotifyUri"]  as? String,
            mood        = data["mood"]        as? String ?: "neutral",
            moodEmoji   = data["moodEmoji"]   as? String ?: "🎵",
            caption     = data["caption"]     as? String ?: "",
            isVibeCheck = data["isVibeCheck"] as? Boolean ?: false,
            timestamp   = ts,
            reactions   = (data["reactions"]  as? Map<String, String>) ?: emptyMap()
        )
    }

    private fun moodEmoji(mood: String): String = when (mood) {
        "happy"     -> "😊"
        "sad"       -> "😢"
        "calm"      -> "😌"
        "energetic" -> "⚡"
        "tired"     -> "😴"
        "focused"   -> "🎯"
        "romantic"  -> "💕"
        else        -> "🎵"
    }
}