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
    val reactions: Map<String, String> = emptyMap(),
    val userMascotType: String = "CAT",
    val vibeSnapUrl: String? = null
)

data class FriendProfile(
    val uid: String,
    val displayName: String,
    val isOnline: Boolean = false,
    val lastActive: Long = 0L,
    val currentMoment: MusicMoment? = null,
    val mascotType: String = "CAT",
    val listenTogetherSessionId: String? = null
)

data class FriendChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val songTitle: String? = null,
    val songArtist: String? = null,
    val songAlbumArt: String? = null,
    val songSpotifyUri: String? = null,
    val messageType: String = "text",  // "text" | "song" | "mood" | "mood_history"
    val mood: String? = null,
    val moodEmoji: String? = null,
    val moodNote: String? = null,
    // mood_history fields
    val mhDominantMood: String? = null,
    val mhDominantEmoji: String? = null,
    val mhTrend: String? = null,
    val mhTopMoods: String? = null,      // JSON-like: "happy:45,calm:30,sad:25"
    val mhStreak: Int? = null,
    val mhTotalEntries: Int? = null,
    val mhRangeLabel: String? = null
)

data class FriendSuggestion(
    val uid: String,
    val displayName: String,
    val mutualFriendCount: Int = 0,
    val matchedByPhone: Boolean = false,
    val mascotType: String = "CAT"
)

data class ListenTogetherSession(
    val sessionId: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val participantUids: List<String> = emptyList(),
    val trackTitle: String = "",
    val trackArtist: String = "",
    val albumArtUrl: String = "",
    val spotifyUri: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

data class VibeGroup(
    val groupId: String = "",
    val genre: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// ── Repository ───────────────────────────────────────────────────────────

class SocialRepository {
    private val TAG  = "SocialRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid
    private fun uname(): String = auth.currentUser?.displayName ?: "Someone"

    // ── Add friend (both ways, atomic batch) ─────────────────────────

    suspend fun addFriend(username: String): Result<String> {
        val me = uid() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // 1. Find target user by username
            val q = db.collection("users")
                .whereEqualTo("username", username.trim().lowercase())
                .limit(1).get().await()

            if (q.isEmpty) return Result.failure(Exception("User '$username' not found"))

            val friendDoc = q.documents.first()
            val fuid      = friendDoc.id

            if (fuid == me) return Result.failure(Exception("That's you!"))

            // 2. Check already friends — query by document ID directly
            val alreadyFriends = db.collection("users").document(me)
                .collection("friends").document(fuid).get().await().exists()

            if (alreadyFriends) return Result.failure(Exception("Already friends!"))

            // 3. Fetch my own profile to write into their friends list
            val myDoc = db.collection("users").document(me).get().await()
            val now   = System.currentTimeMillis()

            // 4. Atomic batch — write BOTH sides simultaneously
            val batch = db.batch()

            // Write friend into MY friends subcollection (doc ID = their UID)
            batch.set(
                db.collection("users").document(me)
                    .collection("friends").document(fuid),
                mapOf(
                    "uid"         to fuid,
                    "username"    to (friendDoc.getString("username") ?: ""),
                    "displayName" to (friendDoc.getString("displayName") ?: username),
                    "email"       to (friendDoc.getString("email") ?: ""),
                    "addedAt"     to now
                )
            )

            // Write me into THEIR friends subcollection (doc ID = my UID)
            batch.set(
                db.collection("users").document(fuid)
                    .collection("friends").document(me),
                mapOf(
                    "uid"         to me,
                    "username"    to (myDoc.getString("username") ?: ""),
                    "displayName" to (myDoc.getString("displayName") ?: uname()),
                    "email"       to (myDoc.getString("email") ?: ""),
                    "addedAt"     to now
                )
            )

            batch.commit().await()
            Log.d(TAG, "addFriend success: $me <-> $fuid")
            Result.success(friendDoc.getString("displayName") ?: username)

        } catch (e: Exception) {
            Log.e(TAG, "addFriend failed", e)
            Result.failure(e)
        }
    }

    // ── Remove friend (both ways, atomic batch) ───────────────────────

    suspend fun removeFriend(friendUid: String): Result<Unit> {
        val me = uid() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Find the friend document in MY list (doc ID = friendUid directly)
            val myFriendRef     = db.collection("users").document(me)
                .collection("friends").document(friendUid)
            val theirFriendRef  = db.collection("users").document(friendUid)
                .collection("friends").document(me)

            // Also find by "uid" field in case old docs used add() with random IDs
            val myOldDocs = db.collection("users").document(me)
                .collection("friends").whereEqualTo("uid", friendUid).get().await()
            val theirOldDocs = db.collection("users").document(friendUid)
                .collection("friends").whereEqualTo("uid", me).get().await()

            val batch = db.batch()

            // Delete the known-ID documents
            batch.delete(myFriendRef)
            batch.delete(theirFriendRef)

            // Also delete any old random-ID documents (from before the fix)
            myOldDocs.documents.forEach { batch.delete(it.reference) }
            theirOldDocs.documents.forEach { batch.delete(it.reference) }

            batch.commit().await()
            Log.d(TAG, "removeFriend success: $me <-> $friendUid")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "removeFriend failed", e)
            Result.failure(e)
        }
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
                        val doc    = db.collection("users").document(candidateUid).get().await()
                        val name   = doc.getString("displayName") ?: doc.getString("username") ?: return@mapNotNull null
                        val mascot = doc.getString("mascotType") ?: "CAT"
                        FriendSuggestion(uid = candidateUid, displayName = name, mutualFriendCount = count, mascotType = mascot)
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
                            val name   = d.getString("displayName") ?: d.getString("username") ?: return@forEach
                            val mascot = d.getString("mascotType") ?: "CAT"
                            suggestions.add(FriendSuggestion(uid = candidateUid, displayName = name, matchedByPhone = true, mascotType = mascot))
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
        albumArtUrl: String, spotifyUri: String?, mood: String,
        mascotType: String = "CAT"
    ) {
        val me = uid() ?: return
        try {
            db.collection("moments").document(me).set(mapOf(
                "userId"         to me,
                "userName"       to uname(),
                "trackTitle"     to trackTitle,
                "trackArtist"    to trackArtist,
                "albumArtUrl"    to albumArtUrl,
                "spotifyUri"     to spotifyUri,
                "mood"           to mood,
                "moodEmoji"      to moodEmoji(mood),
                "caption"        to "",
                "isVibeCheck"    to false,
                "timestamp"      to Timestamp.now(),
                "reactions"      to emptyMap<String, String>(),
                "userMascotType" to mascotType,
                "vibeSnapUrl"    to null
            )).await()
        } catch (e: Exception) { Log.e(TAG, "shareNowPlaying", e) }
    }

    // ── Manual vibe check ─────────────────────────────────────────────

    suspend fun postVibeCheck(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?,
        mood: String, caption: String,
        mascotType: String = "CAT",
        vibeSnapUrl: String? = null
    ) {
        val me   = uid() ?: return
        val data = mapOf(
            "userId"         to me,
            "userName"       to uname(),
            "trackTitle"     to trackTitle,
            "trackArtist"    to trackArtist,
            "albumArtUrl"    to albumArtUrl,
            "spotifyUri"     to spotifyUri,
            "mood"           to mood,
            "moodEmoji"      to moodEmoji(mood),
            "caption"        to caption,
            "isVibeCheck"    to true,
            "timestamp"      to Timestamp.now(),
            "reactions"      to emptyMap<String, String>(),
            "userMascotType" to mascotType,
            "vibeSnapUrl"    to vibeSnapUrl
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
            // Use document ID as UID (new approach) with fallback to "uid" field (old docs)
            val uids = db.collection("users").document(me).collection("friends")
                .get().await().documents.map { doc ->
                    // Prefer "uid" field; fall back to document ID
                    doc.getString("uid")?.takeIf { it.isNotBlank() } ?: doc.id
                }.filter { it.isNotBlank() }.distinct()

            uids.mapNotNull { fuid ->
                try {
                    val userDoc   = db.collection("users").document(fuid).get().await()
                    val name      = userDoc.getString("displayName")
                        ?: userDoc.getString("username") ?: "Unknown"
                    val mascot    = userDoc.getString("mascotType") ?: "CAT"
                    val momentDoc = db.collection("moments").document(fuid).get().await()
                    val moment    = if (momentDoc.exists())
                        docToMoment(momentDoc.id, momentDoc.data ?: emptyMap()) else null
                    val lastActive    = moment?.timestamp ?: 0L
                    val ltSession     = getActiveListenTogetherSession(fuid)

                    FriendProfile(
                        uid                     = fuid,
                        displayName             = name,
                        isOnline                = (System.currentTimeMillis() - lastActive) < 15 * 60_000,
                        lastActive              = lastActive,
                        currentMoment           = moment,
                        mascotType              = mascot,
                        listenTogetherSessionId = ltSession?.sessionId
                    )
                } catch (_: Exception) { null }
            }.sortedWith(
                compareByDescending<FriendProfile> { it.isOnline }
                    .thenByDescending { it.lastActive }
            )
        } catch (e: Exception) { Log.e(TAG, "getFriends", e); emptyList() }
    }

    // ── Listen Together ───────────────────────────────────────────────

    suspend fun startListenTogetherSession(
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?,
        friendUids: List<String>
    ): ListenTogetherSession? {
        val me = uid() ?: return null
        return try {
            val sessionData = mapOf(
                "hostUid"         to me,
                "hostName"        to uname(),
                "participantUids" to (friendUids + me).distinct(),
                "trackTitle"      to trackTitle,
                "trackArtist"     to trackArtist,
                "albumArtUrl"     to albumArtUrl,
                "spotifyUri"      to spotifyUri,
                "startedAt"       to Timestamp.now(),
                "isActive"        to true
            )
            val ref = db.collection("listenTogether").add(sessionData).await()
            ListenTogetherSession(
                sessionId        = ref.id,
                hostUid          = me,
                hostName         = uname(),
                participantUids  = (friendUids + me).distinct(),
                trackTitle       = trackTitle,
                trackArtist      = trackArtist,
                albumArtUrl      = albumArtUrl,
                spotifyUri       = spotifyUri,
                startedAt        = System.currentTimeMillis(),
                isActive         = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "startListenTogetherSession", e)
            null
        }
    }

    suspend fun endListenTogetherSession(sessionId: String) {
        try {
            db.collection("listenTogether").document(sessionId)
                .update("isActive", false).await()
        } catch (e: Exception) { Log.e(TAG, "endSession", e) }
    }

    suspend fun getActiveListenTogetherSession(friendUid: String): ListenTogetherSession? {
        return try {
            val snap = db.collection("listenTogether")
                .whereArrayContains("participantUids", friendUid)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get().await()
            if (snap.isEmpty) return null
            val doc  = snap.documents.first()
            val data = doc.data ?: return null
            @Suppress("UNCHECKED_CAST")
            ListenTogetherSession(
                sessionId        = doc.id,
                hostUid          = data["hostUid"]         as? String ?: "",
                hostName         = data["hostName"]         as? String ?: "",
                participantUids  = (data["participantUids"] as? List<String>) ?: emptyList(),
                trackTitle       = data["trackTitle"]       as? String ?: "",
                trackArtist      = data["trackArtist"]      as? String ?: "",
                albumArtUrl      = data["albumArtUrl"]      as? String ?: "",
                spotifyUri       = data["spotifyUri"]       as? String,
                startedAt        = (data["startedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                isActive         = data["isActive"]         as? Boolean ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "getActiveListenTogetherSession", e)
            null
        }
    }

    fun listenToMyActiveSessions(
        onUpdate: (ListenTogetherSession?) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val me = uid() ?: run {
            onUpdate(null)
            return object : com.google.firebase.firestore.ListenerRegistration { override fun remove() {} }
        }
        return db.collection("listenTogether")
            .whereArrayContains("participantUids", me)
            .whereEqualTo("isActive", true)
            .limit(1)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e(TAG, "listenToMyActiveSessions", error); return@addSnapshotListener }
                if (snap == null || snap.isEmpty) { onUpdate(null); return@addSnapshotListener }
                val doc  = snap.documents.first()
                val data = doc.data ?: run { onUpdate(null); return@addSnapshotListener }
                @Suppress("UNCHECKED_CAST")
                onUpdate(ListenTogetherSession(
                    sessionId        = doc.id,
                    hostUid          = data["hostUid"]         as? String ?: "",
                    hostName         = data["hostName"]         as? String ?: "",
                    participantUids  = (data["participantUids"] as? List<String>) ?: emptyList(),
                    trackTitle       = data["trackTitle"]       as? String ?: "",
                    trackArtist      = data["trackArtist"]      as? String ?: "",
                    albumArtUrl      = data["albumArtUrl"]      as? String ?: "",
                    spotifyUri       = data["spotifyUri"]       as? String,
                    startedAt        = (data["startedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                    isActive         = data["isActive"]         as? Boolean ?: false
                ))
            }
    }

    fun listenToFriendMoment(
        friendUid: String,
        onUpdate: (MusicMoment?) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("moments").document(friendUid)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e(TAG, "listenToFriendMoment", error); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { onUpdate(null); return@addSnapshotListener }
                onUpdate(docToMoment(snap.id, snap.data ?: emptyMap()))
            }
    }

    fun listenToChatMessages(
        friendUid: String,
        onUpdate: (List<FriendChatMessage>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val me = uid() ?: run {
            onUpdate(emptyList())
            return object : com.google.firebase.firestore.ListenerRegistration { override fun remove() {} }
        }
        val convoId = chatDocId(me, friendUid)
        return db.collection("chats").document(convoId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e(TAG, "listenToChat", error); return@addSnapshotListener }
                val messages = snap?.documents?.map { doc ->
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                    FriendChatMessage(
                        id              = doc.id,
                        senderId        = doc.getString("senderId") ?: "",
                        text            = doc.getString("text") ?: "",
                        timestamp       = ts,
                        isFromMe        = doc.getString("senderId") == me,
                        songTitle       = doc.getString("songTitle"),
                        songArtist      = doc.getString("songArtist"),
                        songAlbumArt    = doc.getString("songAlbumArt"),
                        songSpotifyUri  = doc.getString("songSpotifyUri"),
                        messageType     = doc.getString("messageType") ?: "text",
                        mood            = doc.getString("mood"),
                        moodEmoji       = doc.getString("moodEmoji"),
                        moodNote        = doc.getString("moodNote"),
                        mhDominantMood  = doc.getString("mhDominantMood"),
                        mhDominantEmoji = doc.getString("mhDominantEmoji"),
                        mhTrend         = doc.getString("mhTrend"),
                        mhTopMoods      = doc.getString("mhTopMoods"),
                        mhStreak        = doc.getLong("mhStreak")?.toInt(),
                        mhTotalEntries  = doc.getLong("mhTotalEntries")?.toInt(),
                        mhRangeLabel    = doc.getString("mhRangeLabel")
                    )
                } ?: emptyList()
                onUpdate(messages)
            }
    }

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
                .add(mapOf(
                    "senderId"    to me,
                    "text"        to text,
                    "timestamp"   to Timestamp.now(),
                    "messageType" to "text"
                )).await()
        } catch (e: Exception) { Log.e(TAG, "sendChat", e) }
    }

    suspend fun sendSongMessage(
        friendUid: String,
        trackTitle: String, trackArtist: String,
        albumArtUrl: String, spotifyUri: String?
    ) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            val preview = "🎵 $trackTitle — $trackArtist"
            db.collection("chats").document(convoId).set(
                mapOf(
                    "participants"  to listOf(me, friendUid),
                    "lastMessage"   to preview,
                    "lastTimestamp" to Timestamp.now(),
                    "lastSenderId"  to me
                ),
                SetOptions.merge()
            ).await()
            db.collection("chats").document(convoId).collection("messages")
                .add(mapOf(
                    "senderId"       to me,
                    "text"           to preview,
                    "timestamp"      to Timestamp.now(),
                    "messageType"    to "song",
                    "songTitle"      to trackTitle,
                    "songArtist"     to trackArtist,
                    "songAlbumArt"   to albumArtUrl,
                    "songSpotifyUri" to spotifyUri
                )).await()
        } catch (e: Exception) { Log.e(TAG, "sendSongMessage", e) }
    }

    suspend fun sendMoodMessage(
        friendUid: String,
        mood: String,
        moodEmoji: String,
        note: String = ""
    ) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            val preview = "$moodEmoji Feeling $mood${if (note.isNotBlank()) " · $note" else ""}"
            db.collection("chats").document(convoId).set(
                mapOf(
                    "participants"  to listOf(me, friendUid),
                    "lastMessage"   to preview,
                    "lastTimestamp" to Timestamp.now(),
                    "lastSenderId"  to me
                ),
                SetOptions.merge()
            ).await()
            db.collection("chats").document(convoId).collection("messages")
                .add(mapOf(
                    "senderId"    to me,
                    "text"        to preview,
                    "timestamp"   to Timestamp.now(),
                    "messageType" to "mood",
                    "mood"        to mood,
                    "moodEmoji"   to moodEmoji,
                    "moodNote"    to note
                )).await()
        } catch (e: Exception) { Log.e(TAG, "sendMoodMessage", e) }
    }

    suspend fun sendMoodHistoryMessage(
        friendUid: String,
        dominantMood: String,
        dominantEmoji: String,
        trend: String,
        topMoods: String,          // "happy:45,calm:30,sad:25"
        currentStreak: Int,
        totalEntries: Int,
        rangeLabel: String
    ) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            val preview = "$dominantEmoji My $rangeLabel mood report — feeling $dominantMood mostly"
            db.collection("chats").document(convoId).set(
                mapOf(
                    "participants"  to listOf(me, friendUid),
                    "lastMessage"   to preview,
                    "lastTimestamp" to Timestamp.now(),
                    "lastSenderId"  to me
                ),
                SetOptions.merge()
            ).await()
            db.collection("chats").document(convoId).collection("messages")
                .add(mapOf(
                    "senderId"       to me,
                    "text"           to preview,
                    "timestamp"      to Timestamp.now(),
                    "messageType"    to "mood_history",
                    "mhDominantMood" to dominantMood,
                    "mhDominantEmoji" to dominantEmoji,
                    "mhTrend"        to trend,
                    "mhTopMoods"     to topMoods,
                    "mhStreak"       to currentStreak,
                    "mhTotalEntries" to totalEntries,
                    "mhRangeLabel"   to rangeLabel
                )).await()
        } catch (e: Exception) { Log.e(TAG, "sendMoodHistoryMessage", e) }
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
                        id              = doc.id,
                        senderId        = doc.getString("senderId") ?: "",
                        text            = doc.getString("text") ?: "",
                        timestamp       = ts,
                        isFromMe        = doc.getString("senderId") == me,
                        songTitle       = doc.getString("songTitle"),
                        songArtist      = doc.getString("songArtist"),
                        songAlbumArt    = doc.getString("songAlbumArt"),
                        songSpotifyUri  = doc.getString("songSpotifyUri"),
                        messageType     = doc.getString("messageType") ?: "text",
                        mood            = doc.getString("mood"),
                        moodEmoji       = doc.getString("moodEmoji"),
                        moodNote        = doc.getString("moodNote"),
                        mhDominantMood  = doc.getString("mhDominantMood"),
                        mhDominantEmoji = doc.getString("mhDominantEmoji"),
                        mhTrend         = doc.getString("mhTrend"),
                        mhTopMoods      = doc.getString("mhTopMoods"),
                        mhStreak        = doc.getLong("mhStreak")?.toInt(),
                        mhTotalEntries  = doc.getLong("mhTotalEntries")?.toInt(),
                        mhRangeLabel    = doc.getString("mhRangeLabel")
                    )
                }
        } catch (e: Exception) { Log.e(TAG, "getMessages", e); emptyList() }
    }

    // ── Real-time: my own moment ─────────────────────────────────────

    /**
     * Live snapshot on the current user's moment document.
     * Fires immediately on attach and on every remote write.
     */
    fun listenToMyMoment(
        onUpdate: (MusicMoment?) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val me = uid() ?: run {
            onUpdate(null)
            return object : com.google.firebase.firestore.ListenerRegistration { override fun remove() {} }
        }
        return db.collection("moments").document(me)
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e(TAG, "listenToMyMoment", error); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { onUpdate(null); return@addSnapshotListener }
                onUpdate(docToMoment(snap.id, snap.data ?: emptyMap()))
            }
    }

    // ── Real-time: full friends list + their moments ──────────────────

    /**
     * Attaches a single snapshot listener to the friends subcollection.
     * Whenever the list of friend UIDs changes (add / remove), it re-fetches
     * all friend profiles + moments and fires [onUpdate].
     *
     * Additionally, this helper keeps per-friend moment listeners alive so
     * any friend changing their "now playing" is reflected immediately.
     *
     * Returns a single [ListenerRegistration] that, when removed, cleans up
     * both the friends-list listener and all per-friend moment listeners.
     */
    fun listenToFriendsRealTime(
        onUpdate: (List<FriendProfile>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val me = uid() ?: run {
            onUpdate(emptyList())
            return object : com.google.firebase.firestore.ListenerRegistration { override fun remove() {} }
        }

        // Per-friend moment listeners keyed by UID; replaced whenever the friends list changes
        val momentListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        // Latest known moment per friend — updated independently by moment listeners
        val latestMoments   = mutableMapOf<String, MusicMoment?>()
        // Latest known profiles (without moment) — set on friends-list change
        val baseProfiles    = mutableMapOf<String, FriendProfile>()

        fun emitCurrent() {
            val profiles = baseProfiles.values.map { fp ->
                val moment     = latestMoments[fp.uid]
                val lastActive = moment?.timestamp ?: fp.lastActive
                fp.copy(
                    currentMoment = moment,
                    isOnline      = (System.currentTimeMillis() - lastActive) < 15 * 60_000,
                    lastActive    = lastActive
                )
            }.sortedWith(
                compareByDescending<FriendProfile> { it.isOnline }
                    .thenByDescending { it.lastActive }
            )
            onUpdate(profiles)
        }

        // Attach a moment listener for a single friend
        fun attachMomentListener(fuid: String) {
            momentListeners[fuid]?.remove()   // cancel any previous listener for this uid
            momentListeners[fuid] = db.collection("moments").document(fuid)
                .addSnapshotListener { snap, error ->
                    if (error != null) return@addSnapshotListener
                    latestMoments[fuid] = if (snap != null && snap.exists())
                        docToMoment(snap.id, snap.data ?: emptyMap()) else null
                    emitCurrent()
                }
        }

        // Watch the friends subcollection for membership changes
        val friendsListListener = db.collection("users").document(me)
            .collection("friends")
            .addSnapshotListener { snap, error ->
                if (error != null) { Log.e(TAG, "listenToFriendsRealTime:list", error); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener

                val newUids = snap.documents.map { doc ->
                    doc.getString("uid")?.takeIf { it.isNotBlank() } ?: doc.id
                }.filter { it.isNotBlank() }.distinct()

                // Remove moment listeners for friends no longer in the list
                val removedUids = momentListeners.keys - newUids.toSet()
                removedUids.forEach { uid ->
                    momentListeners.remove(uid)?.remove()
                    latestMoments.remove(uid)
                    baseProfiles.remove(uid)
                }

                // Fetch profile + attach moment listener for each friend
                newUids.forEach { fuid ->
                    db.collection("users").document(fuid).get()
                        .addOnSuccessListener { userDoc ->
                            val name   = userDoc.getString("displayName")
                                ?: userDoc.getString("username") ?: "Unknown"
                            val mascot = userDoc.getString("mascotType") ?: "CAT"
                            baseProfiles[fuid] = FriendProfile(
                                uid         = fuid,
                                displayName = name,
                                mascotType  = mascot
                            )
                            if (!momentListeners.containsKey(fuid)) {
                                attachMomentListener(fuid)
                            }
                            emitCurrent()
                        }
                        .addOnFailureListener { e -> Log.e(TAG, "listenToFriendsRealTime:profile $fuid", e) }
                }

                // If all friends were removed, emit empty list immediately
                if (newUids.isEmpty()) onUpdate(emptyList())
            }

        // Return a composite registration that tears everything down
        return object : com.google.firebase.firestore.ListenerRegistration {
            override fun remove() {
                friendsListListener.remove()
                momentListeners.values.forEach { it.remove() }
                momentListeners.clear()
            }
        }
    }

    // ── Mascot type sync ──────────────────────────────────────────────

    suspend fun updateMyMascotType(mascotType: String) {
        val me = uid() ?: return
        try {
            db.collection("users").document(me)
                .set(mapOf("mascotType" to mascotType), SetOptions.merge()).await()
        } catch (e: Exception) { Log.e(TAG, "updateMascotType", e) }
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
            id             = id,
            userId         = data["userId"]        as? String ?: "",
            userName       = data["userName"]       as? String ?: "Someone",
            trackTitle     = data["trackTitle"]     as? String ?: "",
            trackArtist    = data["trackArtist"]    as? String ?: "",
            albumArtUrl    = data["albumArtUrl"]    as? String ?: "",
            spotifyUri     = data["spotifyUri"]     as? String,
            mood           = data["mood"]           as? String ?: "neutral",
            moodEmoji      = data["moodEmoji"]      as? String ?: "🎵",
            caption        = data["caption"]        as? String ?: "",
            isVibeCheck    = data["isVibeCheck"]    as? Boolean ?: false,
            timestamp      = ts,
            reactions      = (data["reactions"]     as? Map<String, String>) ?: emptyMap(),
            userMascotType = data["userMascotType"] as? String ?: "CAT",
            vibeSnapUrl    = data["vibeSnapUrl"]    as? String
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