package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class MoodEntry(
    val mood: String,
    val userMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MoodAnalyticsEntry(
    val mood: String,
    val userMessage: String,
    val timestamp: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val dateString: String
)

data class MoodAnalytics(
    val totalEntries: Int,
    val moodDistribution: Map<String, Int>,
    val moodByHour: Map<Int, Map<String, Int>>,
    val moodByDayOfWeek: Map<Int, Map<String, Int>>,
    val moodTimeline: List<MoodAnalyticsEntry>,
    val dailyMoodSummary: Map<String, String>,
    val streaks: MoodStreaks,
    val averageMoodsPerDay: Float,
    val dominantMood: String,
    val recentTrend: String
)

data class MoodStreaks(
    val currentStreak: Int,
    val longestStreak: Int,
    val currentMoodStreak: Pair<String, Int>?
)

class MoodHistoryRepository {

    private val TAG = "MoodHistoryRepository"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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

    fun detectMood(userMessage: String): String {
        val lower = userMessage.lowercase()
        for ((mood, keywords) in moodKeywords) {
            if (keywords.any { lower.contains(it) }) return mood
        }
        return "neutral"
    }

    fun saveMood(userMessage: String) {
        val userId = auth.currentUser?.uid ?: return
        val detectedMood = detectMood(userMessage)
        val data = hashMapOf("mood" to detectedMood, "userMessage" to userMessage, "timestamp" to Timestamp.now())
        db.collection("moodHistory").document(userId).collection("entries").add(data)
            .addOnSuccessListener { Log.d(TAG, "Mood saved: $detectedMood") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save mood", e) }
    }

    fun saveMoodExplicit(mood: String, message: String = "") {
        val userId = auth.currentUser?.uid ?: return
        val data = hashMapOf("mood" to mood, "userMessage" to message.ifEmpty { "Mood set to $mood" }, "timestamp" to Timestamp.now())
        db.collection("moodHistory").document(userId).collection("entries").add(data)
            .addOnSuccessListener { Log.d(TAG, "Explicit mood saved: $mood") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save mood", e) }
    }

    suspend fun getMoodHistory(): List<MoodEntry> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            db.collection("moodHistory").document(userId).collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(30).get().await()
                .documents.mapNotNull { doc ->
                    val mood = doc.getString("mood") ?: return@mapNotNull null
                    val msg = doc.getString("userMessage") ?: ""
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    MoodEntry(mood, msg, ts)
                }
        } catch (e: Exception) { Log.e(TAG, "Failed to load mood history", e); emptyList() }
    }

    suspend fun getMoodHistoryForDays(days: Int = 30): List<MoodAnalyticsEntry> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val cutoff = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.time
        return try {
            db.collection("moodHistory").document(userId).collection("entries")
                .whereGreaterThan("timestamp", Timestamp(cutoff))
                .orderBy("timestamp", Query.Direction.ASCENDING).limit(500).get().await()
                .documents.mapNotNull { doc ->
                    val mood = doc.getString("mood") ?: return@mapNotNull null
                    val msg = doc.getString("userMessage") ?: ""
                    val ts = doc.getTimestamp("timestamp")?.toDate() ?: return@mapNotNull null
                    val cal = Calendar.getInstance().apply { time = ts }
                    MoodAnalyticsEntry(mood, msg, ts.time, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.DAY_OF_WEEK), dateFormat.format(ts))
                }
        } catch (e: Exception) { Log.e(TAG, "Failed to load analytics", e); emptyList() }
    }

    fun computeAnalytics(entries: List<MoodAnalyticsEntry>): MoodAnalytics {
        if (entries.isEmpty()) return MoodAnalytics(0, emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), MoodStreaks(0, 0, null), 0f, "neutral", "stable")
        val distribution = entries.groupingBy { it.mood }.eachCount().toList().sortedByDescending { it.second }.toMap()
        val byHour = entries.groupBy { it.hourOfDay }.mapValues { (_, e) -> e.groupingBy { it.mood }.eachCount() }
        val byDow = entries.groupBy { it.dayOfWeek }.mapValues { (_, e) -> e.groupingBy { it.mood }.eachCount() }
        val dailySummary = entries.groupBy { it.dateString }.mapValues { (_, e) -> e.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key ?: "neutral" }
        val streaks = computeStreaks(entries, dailySummary)
        val uniqueDays = entries.map { it.dateString }.toSet().size
        val avgPerDay = if (uniqueDays > 0) entries.size.toFloat() / uniqueDays else 0f
        val dominant = distribution.keys.firstOrNull() ?: "neutral"
        val trend = computeTrend(entries)
        return MoodAnalytics(entries.size, distribution, byHour, byDow, entries, dailySummary, streaks, avgPerDay, dominant, trend)
    }

    private fun computeStreaks(entries: List<MoodAnalyticsEntry>, dailySummary: Map<String, String>): MoodStreaks {
        if (dailySummary.isEmpty()) return MoodStreaks(0, 0, null)
        val sortedDates = dailySummary.keys.sorted()
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        var currentStreak = 0
        if (sortedDates.contains(today) || sortedDates.contains(yesterday)) {
            currentStreak = 1
            val start = if (sortedDates.contains(today)) today else yesterday
            val cal = Calendar.getInstance().apply { time = dateFormat.parse(start)!! }
            while (true) { cal.add(Calendar.DAY_OF_YEAR, -1); if (dailySummary.containsKey(dateFormat.format(cal.time))) currentStreak++ else break }
        }
        var longest = 1; var temp = 1
        for (i in 1 until sortedDates.size) {
            val diff = ((dateFormat.parse(sortedDates[i])!!.time - dateFormat.parse(sortedDates[i - 1])!!.time) / 86400000).toInt()
            if (diff == 1) { temp++; longest = maxOf(longest, temp) } else temp = 1
        }
        var moodStreak: Pair<String, Int>? = null
        if (entries.isNotEmpty()) { val last = entries.last().mood; var c = 0; for (i in entries.indices.reversed()) { if (entries[i].mood == last) c++ else break }; moodStreak = last to c }
        return MoodStreaks(currentStreak, longest, moodStreak)
    }

    private fun computeTrend(entries: List<MoodAnalyticsEntry>): String {
        val positive = setOf("happy", "energetic", "calm", "focused", "romantic")
        val now = System.currentTimeMillis(); val week = 7L * 86400000; val twoWeeks = 14L * 86400000
        val recent = entries.filter { now - it.timestamp < week }
        val older = entries.filter { val a = now - it.timestamp; a in week..twoWeeks }
        if (recent.isEmpty() || older.isEmpty()) return "stable"
        fun ratio(l: List<MoodAnalyticsEntry>) = l.count { it.mood in positive }.toFloat() / l.size
        val diff = ratio(recent) - ratio(older)
        return when { diff > 0.15f -> "improving"; diff < -0.15f -> "declining"; else -> "stable" }
    }
}