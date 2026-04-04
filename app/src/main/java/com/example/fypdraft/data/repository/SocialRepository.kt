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

/** A suggested user the current user might know (Options B & C). */
data class FriendSuggestion(
    val uid: String,
    val displayName: String,
    val mutualFriendCount: Int = 0,         // Option B
    val matchedByPhone: Boolean = false      // Option C
)

// ── Repository ───────────────────────────────────────────────────────────

class SocialRepository {
    private val TAG = "SocialRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String? = auth.currentUser?.uid
    private fun uname(): String = auth.currentUser?.displayName ?: "Someone"

    // ════════════════════════════════════════════════════════════════
    // OPTION A — Username search (already working)
    // addFriend() below implements this.  It is called from the dialog.
    // ════════════════════════════════════════════════════════════════

    suspend fun addFriend(username: String): Result<String> {
        val me = uid() ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Option A: query users WHERE username == "..." LIMIT 1
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

            // Write both directions so each user's friend list is self-contained.
            // Firestore creates users/{uid}/friends/ automatically on first write.
            db.collection("users").document(me).collection("friends")
                .add(mapOf("uid" to fuid, "addedAt" to Timestamp.now())).await()
            db.collection("users").document(fuid).collection("friends")
                .add(mapOf("uid" to me, "addedAt" to Timestamp.now())).await()

            Result.success(friendDoc.getString("displayName") ?: username)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ════════════════════════════════════════════════════════════════
    // OPTION B — "People you might know" via mutual friends
    //
    // Algorithm:
    //  1. Load my friend UIDs.
    //  2. For each friend, load their friend UIDs.
    //  3. Count how many of my friends know each candidate UID.
    //  4. Remove UIDs already in my list or equal to my own UID.
    //  5. Return top suggestions sorted by mutual-friend count.
    //
    // This is pure Firestore reads — no extra collections, no Cloud Function.
    // ════════════════════════════════════════════════════════════════

    suspend fun getMutualFriendSuggestions(limit: Int = 10): List<FriendSuggestion> {
        val me = uid() ?: return emptyList()
        return try {
            // Step 1: my friends
            val myFriendUids = db.collection("users").document(me)
                .collection("friends").get().await()
                .documents.mapNotNull { it.getString("uid") }.toSet()

            if (myFriendUids.isEmpty()) return emptyList()

            // Step 2 & 3: their friends, counted
            val mutualCount = mutableMapOf<String, Int>()   // candidateUid → count
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
                } catch (_: Exception) { /* skip unreachable friend */ }
            }

            // Step 4 & 5: resolve display names, return top results
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

    // ════════════════════════════════════════════════════════════════
    // OPTION C — Phone contact matching
    //
    // HOW IT WORKS (no Cloud Function needed for basic matching):
    //
    // 1. On device: read contacts, normalise numbers, SHA-256 hash each.
    // 2. Upload the SET of hashes to contactHashes/{myUid}  (a single doc).
    //    Firestore creates this collection automatically on first write.
    // 3. To find matches: query users WHERE phoneHash IN [myHashes].
    //    Firestore IN supports up to 30 values per query; we batch them.
    //
    // PRIVACY NOTE: we never upload raw phone numbers — only SHA-256 hashes.
    // The hash is one-way: a user's number can only be matched if it appears
    // in the uploader's contact list AND the other user has also stored their
    // hash in their profile.  Explain this in your FYP write-up.
    //
    // REQUIRES: READ_CONTACTS permission declared in AndroidManifest.xml and
    // granted by the user at runtime before calling these functions.
    // ════════════════════════════════════════════════════════════════

    /**
     * Read device contacts, hash each number, upload to Firestore, and
     * write the user's own hash into their profile so others can match them.
     *
     * Call this once after the user grants READ_CONTACTS.
     * Safe to call again to refresh.
     */
    suspend fun uploadContactHashes(context: Context): Boolean {
        val me = uid() ?: return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return false

        return try {
            // Read raw phone numbers from device contacts
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

            // SHA-256 hash each number
            val hashes = numbers.map { sha256(it) }

            // Store hashes in a dedicated collection (indexed, queryable)
            // Firestore creates this collection automatically on first write.
            db.collection("contactHashes").document(me)
                .set(mapOf("hashes" to hashes, "updatedAt" to Timestamp.now()))
                .await()

            // Also write this user's own phone hash to their profile so others can match them.
            // We only write if the user granted permission — no silent collection.
            val myNumber = getMyOwnNumber(context)
            if (myNumber != null) {
                val myHash = sha256(myNumber)
                db.collection("users").document(me)
                    .update("phoneHash", myHash).await()
            }

            Log.d(TAG, "Uploaded ${hashes.size} contact hashes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadContactHashes failed", e)
            false
        }
    }

    /**
     * Query Firestore for users whose phoneHash matches any hash in my contacts.
     * Returns suggestions not already in my friend list.
     *
     * Firestore IN queries are limited to 30 values; we batch them.
     */
    suspend fun getPhoneContactSuggestions(): List<FriendSuggestion> {
        val me = uid() ?: return emptyList()
        return try {
            // Load my uploaded hashes
            val doc = db.collection("contactHashes").document(me).get().await()
            @Suppress("UNCHECKED_CAST")
            val hashes = (doc.get("hashes") as? List<String>) ?: return emptyList()
            if (hashes.isEmpty()) return emptyList()

            // My current friend UIDs (to exclude)
            val myFriendUids = db.collection("users").document(me)
                .collection("friends").get().await()
                .documents.mapNotNull { it.getString("uid") }.toSet()

            // Batch queries: Firestore IN supports max 30 values
            val suggestions = mutableListOf<FriendSuggestion>()
            hashes.chunked(30).forEach { batch ->
                try {
                    val snap = db.collection("users")
                        .whereIn("phoneHash", batch)
                        .get().await()
                    snap.documents.forEach { d ->
                        val candidateUid = d.id
                        if (candidateUid != me && candidateUid !in myFriendUids) {
                            val name = d.getString("displayName") ?: d.getString("username") ?: return@forEach
                            suggestions.add(FriendSuggestion(
                                uid             = candidateUid,
                                displayName     = name,
                                matchedByPhone  = true
                            ))
                        }
                    }
                } catch (_: Exception) { /* skip failed batch */ }
            }
            suggestions.distinctBy { it.uid }
        } catch (e: Exception) {
            Log.e(TAG, "getPhoneContactSuggestions failed", e)
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Combined suggestions (B + C merged, de-duplicated)
    // ════════════════════════════════════════════════════════════════

    suspend fun getAllSuggestions(context: Context? = null): List<FriendSuggestion> {
        val mutual = getMutualFriendSuggestions()

        val phone = if (context != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) getPhoneContactSuggestions() else emptyList()

        // Merge: if a UID appears in both, keep the mutual-friend one and set matchedByPhone
        val map = mutableMapOf<String, FriendSuggestion>()
        mutual.forEach { map[it.uid] = it }
        phone.forEach { phoneSug ->
            map[phoneSug.uid] = (map[phoneSug.uid] ?: phoneSug)
                .copy(matchedByPhone = true)
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

    // ── Friends list with profiles ────────────────────────────────────
    suspend fun getFriendsWithProfiles(): List<FriendProfile> {
        val me = uid() ?: return emptyList()
        return try {
            val uids = db.collection("users").document(me).collection("friends")
                .get().await().documents.mapNotNull { it.getString("uid") }
            uids.mapNotNull { fuid ->
                try {
                    val userDoc = db.collection("users").document(fuid).get().await()
                    val name = userDoc.getString("displayName")
                        ?: userDoc.getString("username") ?: "Unknown"
                    val momentDoc = db.collection("moments").document(fuid).get().await()
                    val moment = if (momentDoc.exists())
                        docToMoment(momentDoc.id, momentDoc.data ?: emptyMap()) else null
                    val lastActive = moment?.timestamp ?: 0L
                    FriendProfile(fuid, name,
                        isOnline   = (System.currentTimeMillis() - lastActive) < 15 * 60_000,
                        lastActive = lastActive,
                        currentMoment = moment)
                } catch (_: Exception) { null }
            }.sortedWith(compareByDescending<FriendProfile> { it.isOnline }.thenByDescending { it.lastActive })
        } catch (e: Exception) { Log.e(TAG, "getFriends", e); emptyList() }
    }

    // ── Per-friend chat ───────────────────────────────────────────────
    /** Deterministic convo ID: always smaller UID first */
    private fun chatDocId(a: String, b: String): String =
        if (a < b) "${a}_${b}" else "${b}_${a}"

    suspend fun sendChatMessage(friendUid: String, text: String) {
        val me = uid() ?: return
        try {
            val convoId = chatDocId(me, friendUid)
            // Ensure the conversation doc exists with participants list
            // (needed for security rules to work on first message)
            db.collection("chats").document(convoId).set(
                mapOf("participants" to listOf(me, friendUid),
                    "lastMessage"   to text,
                    "lastTimestamp" to Timestamp.now(),
                    "lastSenderId"  to me),
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
                        id        = doc.id,
                        senderId  = doc.getString("senderId") ?: "",
                        text      = doc.getString("text") ?: "",
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

    /** Best-effort: try to find the SIM's own number. May return null on many devices. */
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
            id = id, userId = data["userId"] as? String ?: "",
            userName = data["userName"] as? String ?: "Someone",
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
        "happy" -> "😊"; "sad" -> "😢"; "calm" -> "😌"; "energetic" -> "⚡"
        "tired" -> "😴"; "focused" -> "🎯"; "romantic" -> "💕"; else -> "🎵"
    }
}