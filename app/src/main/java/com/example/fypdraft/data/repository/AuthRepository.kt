package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest

data class AuthResult(
    val success: Boolean,
    val message: String,
    val user: FirebaseUser? = null
)

class AuthRepository {
    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG        = "AuthRepository"
        private const val TIMEOUT_MS = 10_000L
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isUserLoggedIn(): Boolean       = auth.currentUser != null

    // ── Sign up ───────────────────────────────────────────────────────
    suspend fun signUp(username: String, email: String, password: String): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                if (!isPasswordValid(password)) return@withTimeout AuthResult(
                    false, "Password must be at least 6 characters and contain letters and numbers"
                )

                // Username uniqueness check
                val taken = try {
                    !firestore.collection("users")
                        .whereEqualTo("username", username.lowercase())
                        .limit(1).get().await().isEmpty
                } catch (_: Exception) { false }

                if (taken) return@withTimeout AuthResult(false, "Username already taken")

                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user   = result.user ?: return@withTimeout AuthResult(false, "Failed to create account")

                try {
                    // Set Firebase Auth display name
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(username).build()
                    ).await()

                    // ── Write the public profile to Firestore ─────────
                    // This is the ONLY place you ever need to create the users/{uid} document.
                    // Firestore creates the collection automatically on first write.
                    // The fields written here are the ones friend-search queries rely on.
                    val userData = hashMapOf(
                        "uid"         to user.uid,
                        "username"    to username.lowercase(),   // for Option A search
                        "displayName" to username,
                        "email"       to email.lowercase(),
                        "createdAt"   to System.currentTimeMillis(),
                        "emailVerified" to false,
                        // phoneHash is null until user opts in to contact matching (Option C)
                        // It is written later by SocialRepository.uploadContactHashes()
                        "phoneHash"   to null
                    )
                    firestore.collection("users").document(user.uid).set(userData).await()

                    // Firestore does NOT require pre-creating the friends subcollection.
                    // It is created automatically when the first friend is added via addFriend().

                    user.sendEmailVerification()
                    AuthResult(true, "Account created! Please verify your email.", user)
                } catch (e: Exception) {
                    Log.e(TAG, "Post-signup ops failed", e)
                    AuthResult(true, "Account created!", user)   // auth succeeded even if profile write fails
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AuthResult(false, "Sign up timed out. Check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "signUp error", e)
            AuthResult(false, when {
                e.message?.contains("email address is already in use") == true ->
                    "This email is already registered"
                e.message?.contains("network") == true ->
                    "Network error. Please check your connection."
                else -> e.message ?: "Sign up failed"
            })
        }
    }

    // ── Sign in ───────────────────────────────────────────────────────
    suspend fun signIn(emailOrUsername: String, password: String): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                val input = emailOrUsername.trim()
                val email = if (input.contains("@")) {
                    input.lowercase()
                } else {
                    // Option A: look up email by username
                    val snap = try {
                        firestore.collection("users")
                            .whereEqualTo("username", input.lowercase())
                            .limit(1).get().await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Username lookup failed", e)
                        return@withTimeout AuthResult(false, "Error finding user. Check your connection.")
                    }
                    if (snap.documents.isEmpty())
                        return@withTimeout AuthResult(false, "User not found. Check your username or email.")
                    snap.documents[0].getString("email")?.lowercase()
                        ?: return@withTimeout AuthResult(false, "User data error. Please contact support.")
                }

                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user   = result.user
                    ?: return@withTimeout AuthResult(false, "Login failed")
                AuthResult(true, "Login successful", user)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AuthResult(false, "Login timed out. Check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "signIn error", e)
            AuthResult(false, when {
                e.message?.contains("no user record") == true           -> "No account found with this email"
                e.message?.contains("password is invalid") == true
                        || e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true -> "Incorrect password"
                e.message?.contains("network") == true                  -> "Network error. Check your connection."
                else -> e.message ?: "Login failed"
            })
        }
    }

    // ── Reset password ────────────────────────────────────────────────
    suspend fun resetPassword(email: String): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                auth.sendPasswordResetEmail(email.trim().lowercase()).await()
                AuthResult(true, "Password reset email sent. Please check your inbox.")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AuthResult(false, "Request timed out. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "resetPassword error", e)
            AuthResult(false, e.message ?: "Failed to send reset email")
        }
    }

    fun signOut() { auth.signOut() }

    private fun isPasswordValid(p: String): Boolean =
        p.length >= 6 && p.any { it.isLetter() } && p.any { it.isDigit() }

    fun isEmailValid(email: String): Boolean =
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}