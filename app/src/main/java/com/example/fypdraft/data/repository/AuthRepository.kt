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
    suspend fun signUp(
        username: String,
        email: String,
        password: String,
        phoneNumber: String = ""        // new param — pass "" if not collected yet
    ): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                if (!isPasswordValid(password)) return@withTimeout AuthResult(
                    false, "Password must be at least 6 characters and contain letters and numbers"
                )

                // Username uniqueness check
                val taken = try {
                    !firestore.collection("users")
                        .whereEqualTo("username", username.trim().lowercase())
                        .limit(1).get().await().isEmpty
                } catch (_: Exception) { false }

                if (taken) return@withTimeout AuthResult(false, "Username already taken")

                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user   = result.user
                    ?: return@withTimeout AuthResult(false, "Failed to create account")

                try {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(username.trim()).build()
                    ).await()

                    val userData = hashMapOf(
                        "uid"           to user.uid,
                        "username"      to username.trim().lowercase(),
                        "displayName"   to username.trim(),
                        "email"         to email.trim().lowercase(),
                        "phoneNumber"   to phoneNumber.trim(),   // e.g. "+60123456789"
                        "createdAt"     to System.currentTimeMillis(),
                        "emailVerified" to false,
                        "phoneHash"     to null
                    )

                    firestore.collection("users")
                        .document(user.uid)
                        .set(userData)
                        .await()

                    Log.d(TAG, "Firestore users doc created for ${user.uid}")

                    user.sendEmailVerification()
                    AuthResult(true, "Account created! Please verify your email.", user)

                } catch (e: Exception) {
                    Log.e(TAG, "Post-signup Firestore write failed", e)
                    // Firestore write failed — delete the Auth account so
                    // the user can try again cleanly instead of being stuck
                    try { user.delete().await() } catch (_: Exception) {}
                    AuthResult(false, "Account setup failed. Please try again.")
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
                    // Look up email by username
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

                // Backfill missing Firestore doc (safety net for old accounts)
                try {
                    val docRef = firestore.collection("users").document(user.uid)
                    val doc    = docRef.get().await()
                    if (!doc.exists()) {
                        val userData = hashMapOf(
                            "uid"           to user.uid,
                            "username"      to (user.displayName?.trim()?.lowercase()
                                ?: email.substringBefore("@").lowercase()),
                            "displayName"   to (user.displayName?.takeIf { it.isNotBlank() }
                                ?: email.substringBefore("@")),
                            "email"         to email.trim().lowercase(),
                            "phoneNumber"   to "",
                            "createdAt"     to System.currentTimeMillis(),
                            "emailVerified" to user.isEmailVerified,
                            "phoneHash"     to null
                        )
                        docRef.set(userData).await()
                        Log.d(TAG, "Backfilled missing Firestore doc for ${user.uid}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Backfill failed (non-fatal)", e)
                    // Non-fatal — login still succeeds even if backfill fails
                }

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