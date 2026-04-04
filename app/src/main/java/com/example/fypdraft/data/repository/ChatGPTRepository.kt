package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.core.config.AppConfig
import com.example.fypdraft.core.network.NetworkModule
import com.example.fypdraft.data.api.ChatGPTRequest
import com.example.fypdraft.data.api.OpenAIMessage
import com.example.fypdraft.model.ChatMessageUi
import com.example.fypdraft.model.MessageSender
import com.example.fypdraft.model.SongRecommendation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ChatRepository {

    private val TAG = "ChatRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // In-memory conversation context sent to OpenAI each turn.
    // Must be cleared on sign-out so a new session starts fresh.
    private val conversationHistory = mutableListOf<OpenAIMessage>()

    // ── Send message to OpenAI ────────────────────────────────────────────

    suspend fun sendMessage(userMessage: String): Result<Pair<String, List<SongRecommendation>>> {
        return try {
            conversationHistory.add(OpenAIMessage(role = "user", content = userMessage))

            val messages = buildList {
                add(OpenAIMessage(role = "system", content = AppConfig.CHATGPT_SYSTEM_PROMPT))
                addAll(conversationHistory.takeLast(20))
            }

            val response = NetworkModule.chatGPTApi.sendMessage(
                authorization = "Bearer ${AppConfig.OPENAI_API_KEY}",
                request = ChatGPTRequest(messages = messages)
            )

            if (!response.isSuccessful) {
                val err = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "OpenAI error ${response.code()}: $err")
                if (response.code() == 429) return Result.failure(Exception("RATE_LIMIT_EXCEEDED"))
                return Result.failure(Exception("API error ${response.code()}"))
            }

            val aiText = response.body()?.choices?.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response"))

            conversationHistory.add(OpenAIMessage(role = "assistant", content = aiText))

            val songs     = parseSongs(aiText)
            val cleanText = stripSongLines(aiText)
            saveToFirebase(userMessage, aiText, songs)

            Result.success(cleanText to songs)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            Result.failure(e)
        }
    }

    // ── Song parsing ──────────────────────────────────────────────────────

    private fun parseSongs(text: String): List<SongRecommendation> {
        val songPattern   = Regex("""🎵\s*SONG:\s*(.+?)\s*-\s*(.+)""")
        val reasonPattern = Regex("""💭\s*REASON:\s*(.+)""")

        val songMatches   = songPattern.findAll(text).toList()
        val reasonMatches = reasonPattern.findAll(text).toList()

        if (songMatches.isNotEmpty()) {
            return songMatches.mapIndexedNotNull { i, match ->
                val artist = match.groupValues[1].trim()
                val title  = match.groupValues[2].trim()
                val reason = reasonMatches.getOrNull(i)?.groupValues?.get(1)?.trim() ?: ""
                if (artist.isNotEmpty() && title.isNotEmpty())
                    SongRecommendation(artist = artist, title = title, reason = reason)
                else null
            }
        }

        // Fallback: "Artist - Title" lines
        return Regex("""^([^-\n]{2,50})\s*-\s*([^-\n]{2,80})$""", RegexOption.MULTILINE)
            .findAll(text).take(5).mapNotNull { m ->
                val artist = m.groupValues[1].trim()
                val title  = m.groupValues[2].trim()
                if (artist.isNotEmpty() && title.isNotEmpty())
                    SongRecommendation(artist = artist, title = title)
                else null
            }.toList()
    }

    private fun stripSongLines(text: String): String =
        text.lines()
            .filter { !it.trimStart().startsWith("🎵") && !it.trimStart().startsWith("💭") }
            .joinToString("\n").trim()

    // ── Firebase persistence ──────────────────────────────────────────────

    private fun saveToFirebase(
        userMsg: String,
        aiResp: String,
        songs: List<SongRecommendation>
    ) {
        // Always resolve UID at call time so we never write to the wrong user's doc
        val userId = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "userId"          to userId,
            "userMessage"     to userMsg,
            "aiResponse"      to aiResp,
            "recommendations" to songs.map {
                mapOf("artist" to it.artist, "title" to it.title, "reason" to it.reason)
            },
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("conversations")
            .document(userId)
            .collection("messages")
            .add(data)
    }

    /**
     * Load persisted chat history for [userId].
     *
     * Accepts an explicit UID rather than reading from [auth.currentUser] so
     * that this always loads for the correct account immediately after sign-in,
     * before Firebase auth state fully propagates.
     */
    suspend fun loadHistory(userId: String): List<ChatMessageUi> {
        if (userId.isBlank()) return emptyList()
        return try {
            val snapshot = firestore
                .collection("conversations").document(userId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(50)
                .get().await()

            // Rebuild in-memory context from what was persisted
            conversationHistory.clear()

            snapshot.documents.flatMap { doc ->
                val userMsg = doc.getString("userMessage") ?: return@flatMap emptyList()
                val aiMsg   = doc.getString("aiResponse")  ?: return@flatMap emptyList()

                @Suppress("UNCHECKED_CAST")
                val rawSongs = doc.get("recommendations") as? List<Map<String, String>> ?: emptyList()
                val songs = rawSongs.map {
                    SongRecommendation(
                        artist = it["artist"] ?: "",
                        title  = it["title"]  ?: "",
                        reason = it["reason"] ?: ""
                    )
                }

                conversationHistory.add(OpenAIMessage("user",      userMsg))
                conversationHistory.add(OpenAIMessage("assistant", aiMsg))

                listOf(
                    ChatMessageUi(sender = MessageSender.USER, text = userMsg),
                    ChatMessageUi(sender = MessageSender.AI,   text = stripSongLines(aiMsg), songs = songs)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadHistory failed", e)
            emptyList()
        }
    }

    /**
     * Wipe both in-memory context and Firestore records for [userId].
     *
     * Called on sign-out so the next user session starts completely clean.
     * Accepts an explicit UID for the same reason as [loadHistory].
     */
    suspend fun clearConversation(userId: String) {
        // Always clear in-memory context, even if Firestore delete fails
        conversationHistory.clear()
        if (userId.isBlank()) return
        try {
            val snapshot = firestore
                .collection("conversations").document(userId)
                .collection("messages")
                .get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(TAG, "clearConversation failed", e)
        }
    }
}