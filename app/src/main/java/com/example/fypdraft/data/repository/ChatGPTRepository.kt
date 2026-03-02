package com.example.fypdraft.data.repository

import android.util.Log
import com.example.fypdraft.api.RetrofitClient
import com.example.fypdraft.data.api.ApiConfig
import com.example.fypdraft.data.api.ChatGPTRequest
import com.example.fypdraft.data.api.OpenAIMessage
import com.example.fypdraft.model.ChatMessageUi
import com.example.fypdraft.model.MessageSender
import com.example.fypdraft.model.SongRecommendation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ChatGPTRepository {

    private val TAG = "ChatGPTRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // In-memory conversation history for context (last 20 messages)
    private val conversationHistory = mutableListOf<OpenAIMessage>()

    // ── Send message to OpenAI ────────────────────────────────────────────────

    suspend fun sendMessage(userMessage: String): Result<Pair<String, List<SongRecommendation>>> {
        return try {
            // Add user turn to history
            conversationHistory.add(OpenAIMessage(role = "user", content = userMessage))

            // Build messages: system prompt + capped history
            val messages = mutableListOf(
                OpenAIMessage(role = "system", content = ApiConfig.OPENAI_SYSTEM_PROMPT)
            )
            val slice = if (conversationHistory.size > 20)
                conversationHistory.takeLast(20) else conversationHistory
            messages.addAll(slice)

            // Call OpenAI via RetrofitClient
            val response = RetrofitClient.chatGPTApiService.sendMessage(
                authorization = "Bearer ${ApiConfig.OPENAI_API_KEY}",
                request = ChatGPTRequest(messages = messages)
            )

            if (!response.isSuccessful) {
                val errBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "OpenAI error ${response.code()}: $errBody")
                if (response.code() == 429) return Result.failure(Exception("RATE_LIMIT_EXCEEDED"))
                return Result.failure(Exception("API error ${response.code()}: $errBody"))
            }

            val aiText = response.body()?.choices?.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response from OpenAI"))

            // Add assistant turn to history
            conversationHistory.add(OpenAIMessage(role = "assistant", content = aiText))

            // Parse structured song lines out of the response
            val songs = parseSongs(aiText)

            // Show only prose in the chat bubble — remove the 🎵/💭 lines
            val cleanText = stripSongLines(aiText)

            // Save to Firebase (non-blocking)
            saveToFirebase(userMessage, aiText, songs)

            Result.success(Pair(cleanText, songs))

        } catch (e: Exception) {
            Log.e(TAG, "sendMessage exception", e)
            Result.failure(e)
        }
    }

    // ── Parse song recommendations from GPT response ──────────────────────────
    //
    // Expected format per song:
    //   🎵 SONG: Artist Name - Song Title
    //   💭 REASON: Why it matches

    private fun parseSongs(text: String): List<SongRecommendation> {
        val songs = mutableListOf<SongRecommendation>()

        val songPattern   = Regex("""🎵\s*SONG:\s*(.+?)\s*-\s*(.+)""")
        val reasonPattern = Regex("""💭\s*REASON:\s*(.+)""")

        val songMatches   = songPattern.findAll(text).toList()
        val reasonMatches = reasonPattern.findAll(text).toList()

        songMatches.forEachIndexed { i, match ->
            val artist = match.groupValues[1].trim()
            val title  = match.groupValues[2].trim()
            val reason = reasonMatches.getOrNull(i)?.groupValues?.get(1)?.trim() ?: ""
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                songs.add(SongRecommendation(artist = artist, title = title, reason = reason))
            }
        }

        // Fallback: try "Artist - Title" lines if primary markers are missing
        if (songs.isEmpty()) {
            val fallback = Regex("""^([^-\n]{2,50})\s*-\s*([^-\n]{2,80})$""", RegexOption.MULTILINE)
            fallback.findAll(text).take(5).forEach { m ->
                val artist = m.groupValues[1].trim()
                val title  = m.groupValues[2].trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) {
                    songs.add(SongRecommendation(artist = artist, title = title))
                }
            }
        }

        Log.d(TAG, "Parsed ${songs.size} songs")
        return songs
    }

    // Remove the formatted song lines so only prose remains in the chat bubble
    private fun stripSongLines(text: String): String =
        text.lines()
            .filter { line ->
                !line.trimStart().startsWith("🎵") &&
                        !line.trimStart().startsWith("💭")
            }
            .joinToString("\n")
            .trim()

    // ── Firebase: save exchange ───────────────────────────────────────────────

    private fun saveToFirebase(
        userMessage: String,
        aiResponse: String,
        songs: List<SongRecommendation>
    ) {
        val userId = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "userId"          to userId,
            "userMessage"     to userMessage,
            "aiResponse"      to aiResponse,
            "recommendations" to songs.map {
                mapOf("artist" to it.artist, "title" to it.title, "reason" to it.reason)
            },
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("conversations")
            .document(userId)
            .collection("messages")
            .add(data)
            .addOnFailureListener { e -> Log.e(TAG, "Firebase save failed", e) }
    }

    // ── Firebase: load history on app start ───────────────────────────────────

    suspend fun loadConversationHistory(): List<ChatMessageUi> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = firestore
                .collection("conversations").document(userId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(50)
                .get().await()

            val uiMessages = mutableListOf<ChatMessageUi>()
            snapshot.documents.forEach { doc ->
                val userMsg = doc.getString("userMessage") ?: return@forEach
                val aiMsg   = doc.getString("aiResponse")  ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val rawSongs = doc.get("recommendations") as? List<Map<String, String>> ?: emptyList()
                val songs = rawSongs.map {
                    SongRecommendation(
                        artist = it["artist"] ?: "",
                        title  = it["title"]  ?: "",
                        reason = it["reason"] ?: ""
                    )
                }

                uiMessages.add(ChatMessageUi(sender = MessageSender.USER, text = userMsg))
                uiMessages.add(ChatMessageUi(sender = MessageSender.AI,   text = stripSongLines(aiMsg), songs = songs))

                // Rebuild in-memory history for context continuity
                conversationHistory.add(OpenAIMessage("user",      userMsg))
                conversationHistory.add(OpenAIMessage("assistant", aiMsg))
            }
            uiMessages
        } catch (e: Exception) {
            Log.e(TAG, "loadConversationHistory failed", e)
            emptyList()
        }
    }

    // ── Firebase: clear conversation ──────────────────────────────────────────

    suspend fun clearConversation() {
        val userId = auth.currentUser?.uid ?: return
        conversationHistory.clear()
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