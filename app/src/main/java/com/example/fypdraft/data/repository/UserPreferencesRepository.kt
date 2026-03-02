package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UserPreferences(
    val preferredGenres: List<String> = emptyList(),
    val theme: String = "light",                  // "light" or "dark"
    val moodRemindersEnabled: Boolean = false,
    val language: String = "en"
)

class UserPreferencesRepository {

    private val TAG = "UserPreferencesRepo"
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun prefsDoc() = auth.currentUser?.uid?.let {
        db.collection("users").document(it)
    }

    // Save preferences into the existing users/{userId} document
    suspend fun savePreferences(prefs: UserPreferences) {
        val doc = prefsDoc() ?: return
        val data = mapOf(
            "preferences" to mapOf(
                "preferredGenres"       to prefs.preferredGenres,
                "theme"                 to prefs.theme,
                "moodRemindersEnabled"  to prefs.moodRemindersEnabled,
                "language"              to prefs.language
            )
        )
        try {
            doc.update(data).await()
            Log.d(TAG, "✅ Preferences saved")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save preferences", e)
        }
    }

    // Load preferences from Firestore
    suspend fun loadPreferences(): UserPreferences {
        val doc = prefsDoc() ?: return UserPreferences()
        return try {
            val snapshot = doc.get().await()
            @Suppress("UNCHECKED_CAST")
            val raw = snapshot.get("preferences") as? Map<String, Any> ?: return UserPreferences()

            UserPreferences(
                preferredGenres        = (raw["preferredGenres"] as? List<String>) ?: emptyList(),
                theme                  = (raw["theme"] as? String) ?: "light",
                moodRemindersEnabled   = (raw["moodRemindersEnabled"] as? Boolean) ?: false,
                language               = (raw["language"] as? String) ?: "en"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preferences", e)
            UserPreferences()
        }
    }
}