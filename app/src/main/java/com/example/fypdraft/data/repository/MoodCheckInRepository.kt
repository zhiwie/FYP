package com.example.fypdraft.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.moodCheckInDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "mood_check_in")

/**
 * MoodCheckInRepository
 *
 * Streak rules (enforced here, NOT in the UI):
 *   • Check-in is recorded once per day. Same-day re-saves only update the emotion,
 *     never remove the streak entry or reset the streak count.
 *   • Editing the emotion on an already-checked-in day: idempotent date save keeps
 *     streak intact. Only KEY_LAST_MOOD is overwritten.
 *   • The "Others" mood key stores a custom string as todayMood (e.g. "others:Grateful").
 */
class MoodCheckInRepository(private val context: Context) {

    companion object {
        private val KEY_LAST_DATE = stringPreferencesKey("last_check_in_date")
        private val KEY_LAST_MOOD = stringPreferencesKey("last_check_in_mood")
        private val KEY_STREAK    = intPreferencesKey("check_in_streak")
        private val KEY_TOTAL     = intPreferencesKey("total_check_ins")
        private val KEY_HISTORY   = stringPreferencesKey("check_in_history")
    }

    // ── Observable state ──────────────────────────────────────────────────

    val todayCheckIn: Flow<CheckInState> = context.moodCheckInDataStore.data.map { prefs ->
        val lastDate  = prefs[KEY_LAST_DATE]
        val today     = todayString()
        val checkedIn = lastDate == today
        CheckInState(
            checkedInToday = checkedIn,
            todayMood      = if (checkedIn) prefs[KEY_LAST_MOOD] else null,
            streak         = prefs[KEY_STREAK] ?: 0,
            totalCheckIns  = prefs[KEY_TOTAL]  ?: 0,
            checkedInDates = parseHistory(prefs[KEY_HISTORY])
        )
    }

    // ── Write ─────────────────────────────────────────────────────────────

    /**
     * Save (or update) today's check-in.
     *
     * If already checked in today:
     *   → only updates the mood label; streak/date/total are untouched.
     * If first check-in today:
     *   → records the date, increments streak (or resets on gap), increments total.
     *
     * [moodKey] can be a DailyMood.key, or "others:<custom text>" for free-form input.
     */
    suspend fun saveTodayCheckIn(moodKey: String): CheckInState {
        val todayStr = todayString()
        var result   = CheckInState()

        context.moodCheckInDataStore.edit { prefs ->
            val lastDateStr = prefs[KEY_LAST_DATE]

            if (lastDateStr == todayStr) {
                // Already checked in today — update mood only, preserve everything else
                prefs[KEY_LAST_MOOD] = moodKey
                result = CheckInState(
                    checkedInToday = true,
                    todayMood      = moodKey,
                    streak         = prefs[KEY_STREAK] ?: 1,
                    totalCheckIns  = prefs[KEY_TOTAL]  ?: 1,
                    checkedInDates = parseHistory(prefs[KEY_HISTORY])
                )
                return@edit
            }

            // First check-in today — calculate streak
            val currentStreak = prefs[KEY_STREAK] ?: 0
            val newStreak = if (lastDateStr != null) {
                val daysBetween = daysBetween(lastDateStr, todayStr)
                if (daysBetween == 1L) currentStreak + 1 else 1
            } else 1

            val newTotal   = (prefs[KEY_TOTAL] ?: 0) + 1
            val oldHistory = parseHistory(prefs[KEY_HISTORY])
            val combined   = (oldHistory + todayStr).toList()
            val trimmed    = if (combined.size > 30) combined.drop(combined.size - 30) else combined
            val newHistory = trimmed.toSet()

            prefs[KEY_LAST_DATE] = todayStr
            prefs[KEY_LAST_MOOD] = moodKey
            prefs[KEY_STREAK]    = newStreak
            prefs[KEY_TOTAL]     = newTotal
            prefs[KEY_HISTORY]   = newHistory.joinToString(",")

            result = CheckInState(
                checkedInToday = true,
                todayMood      = moodKey,
                streak         = newStreak,
                totalCheckIns  = newTotal,
                checkedInDates = newHistory
            )
        }

        syncToCloud(result)
        return result
    }

    suspend fun resetAll() = context.moodCheckInDataStore.edit { it.clear() }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun syncToCloud(state: CheckInState) {
        // TODO: Firestore push when backend sync is ready
    }

    // ── Date helpers ──────────────────────────────────────────────────────

    private fun todayString(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun daysBetween(from: String, to: String): Long {
        val fromMs = parseToMs(from)
        val toMs   = parseToMs(to)
        return (toMs - fromMs) / (24L * 60 * 60 * 1000)
    }

    private fun parseToMs(dateStr: String): Long {
        val parts = dateStr.split("-")
        if (parts.size != 3) return 0L
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR,         parts[0].toIntOrNull() ?: return 0L)
        c.set(Calendar.MONTH,        (parts[1].toIntOrNull() ?: return 0L) - 1)
        c.set(Calendar.DAY_OF_MONTH, parts[2].toIntOrNull() ?: return 0L)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND,      0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun parseHistory(raw: String?): Set<String> =
        raw?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
}

// ── Models ────────────────────────────────────────────────────────────────────

data class CheckInState(
    val checkedInToday : Boolean     = false,
    val todayMood      : String?     = null,   // key string, may be "others:<text>"
    val streak         : Int         = 0,
    val totalCheckIns  : Int         = 0,
    val checkedInDates : Set<String> = emptySet()
)

/**
 * DailyMood enum.
 * Changes from previous version:
 *   - Removed EXCITED
 *   - Added OTHERS (key = "others") — custom free-form text stored as "others:<label>"
 */
enum class DailyMood(val key: String, val emoji: String, val label: String) {
    HAPPY   ("happy",    "😊", "Happy"),
    CALM    ("calm",     "😌", "Calm"),
    SAD     ("sad",      "😢", "Sad"),
    STRESSED("stressed", "😣", "Stressed"),
    OTHERS  ("others",   "✍️", "Others");

    companion object {
        fun all()                = entries.toList()
        fun fromKey(key: String) = entries.firstOrNull { it.key == key || key.startsWith(it.key + ":") } ?: CALM

        /** Decode the stored mood key into display text + emoji. */
        fun displayFor(moodKey: String?): Pair<String, String> {
            if (moodKey == null) return Pair("", "")
            return if (moodKey.startsWith("others:")) {
                val custom = moodKey.removePrefix("others:").trim()
                Pair(custom.ifEmpty { "Others" }, "✍️")
            } else {
                val dm = entries.firstOrNull { it.key == moodKey } ?: return Pair(moodKey, "😊")
                Pair(dm.label, dm.emoji)
            }
        }
    }
}