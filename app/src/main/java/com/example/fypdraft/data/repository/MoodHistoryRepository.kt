package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class MoodEntry(
    val mood: String,
    val userMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MoodHistoryRepository {

    private val TAG = "MoodHistoryRepository"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Common mood keywords to detect from user messages
    private val moodKeywords = mapOf(
        "happy"     to listOf("happy", "great", "amazing", "wonderful", "excited", "joyful", "glad", "cheerful"),
        "sad"       to listOf("sad", "depressed", "down", "unhappy", "miserable", "crying", "upset", "heartbroken"),
        "angry"     to listOf("angry", "frustrated", "annoyed", "furious", "mad", "irritated", "rage"),
        "anxious"   to listOf("anxious", "nervous", "worried", "stressed", "overwhelmed", "panic", "tense"),
        "calm"      to listOf("calm", "relaxed", "peaceful", "chill", "serene", "tranquil", "at ease"),
        "energetic" to listOf("energetic", "pumped", "motivated", "hyped", "active", "workout", "exercise"),
        "tired"     to listOf("tired", "exhausted", "sleepy", "drained", "fatigued", "worn out"),
        "romantic"  to listOf("romantic", "love", "crush", "date", "heartfelt", "affectionate"),
        "focused"   to listOf("focus", "concentrate", "study", "work", "productive", "deep work"),
        "nostalgic" to listOf("nostalgic", "memories", "old times", "childhood", "miss", "remember")
    )

    // Detect mood from user's chat message
    fun detectMood(userMessage: String): String {
        val lower = userMessage.lowercase()
        for ((mood, keywords) in moodKeywords) {
            if (keywords.any { lower.contains(it) }) return mood
        }
        return "neutral"
    }

    // Save mood entry to Firestore
    fun saveMood(userMessage: String) {
        val userId = auth.currentUser?.uid ?: return
        val detectedMood = detectMood(userMessage)

        val data = hashMapOf(
            "mood"        to detectedMood,
            "userMessage" to userMessage,
            "timestamp"   to Timestamp.now()
        )

        db.collection("moodHistory")
            .document(userId)
            .collection("entries")
            .add(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Mood saved: $detectedMood")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save mood", e)
            }
    }

    // Load last 30 mood entries for history display
    suspend fun getMoodHistory(): List<MoodEntry> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val snapshot = db.collection("moodHistory")
                .document(userId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val mood        = doc.getString("mood") ?: return@mapNotNull null
                val userMessage = doc.getString("userMessage") ?: ""
                val ts          = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                MoodEntry(mood = mood, userMessage = userMessage, timestamp = ts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mood history", e)
            emptyList()
        }
    }
}