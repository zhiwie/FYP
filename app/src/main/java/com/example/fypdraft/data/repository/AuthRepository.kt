package com.example.fypdraft.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class AuthResult(
    val success: Boolean,
    val message: String,
    val user: FirebaseUser? = null
)

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "AuthRepository"
        private const val TIMEOUT_MS = 10000L // 10 seconds timeout
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    suspend fun signUp(
        username: String,
        email: String,
        password: String
    ): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                // Validate password
                if (!isPasswordValid(password)) {
                    return@withTimeout AuthResult(
                        success = false,
                        message = "Password must be at least 6 characters and contain both letters and numbers"
                    )
                }

                // Check if username already exists (with timeout)
                val usernameExists = try {
                    val querySnapshot = firestore.collection("users")
                        .whereEqualTo("username", username.lowercase())
                        .limit(1)
                        .get()
                        .await()
                    !querySnapshot.isEmpty
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking username", e)
                    false
                }

                if (usernameExists) {
                    return@withTimeout AuthResult(
                        success = false,
                        message = "Username already taken"
                    )
                }

                // Create user
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    try {
                        // Update profile with username (non-blocking for user experience)
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()
                        user.updateProfile(profileUpdates).await()

                        // Save user data to Firestore with lowercase username for case-insensitive search
                        val userData = hashMapOf(
                            "username" to username.lowercase(),
                            "displayName" to username,
                            "email" to email.lowercase(),
                            "createdAt" to System.currentTimeMillis(),
                            "emailVerified" to false
                        )
                        firestore.collection("users").document(user.uid).set(userData).await()

                        // Send email verification (don't await - let it happen in background)
                        user.sendEmailVerification()

                        AuthResult(
                            success = true,
                            message = "Account created! Please verify your email.",
                            user = user
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in post-signup operations", e)
                        // User is created but some operations failed
                        AuthResult(
                            success = true,
                            message = "Account created successfully!",
                            user = user
                        )
                    }
                } else {
                    AuthResult(success = false, message = "Failed to create account")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Sign up timeout", e)
            AuthResult(success = false, message = "Sign up is taking too long. Please check your internet connection and try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Sign up error", e)
            val errorMessage = when {
                e.message?.contains("email address is already in use") == true ->
                    "This email is already registered"
                e.message?.contains("network") == true ->
                    "Network error. Please check your connection."
                else -> e.message ?: "Sign up failed"
            }
            AuthResult(success = false, message = errorMessage)
        }
    }

    suspend fun signIn(emailOrUsername: String, password: String): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                val input = emailOrUsername.trim()

                // Check if input is email or username
                val email = if (input.contains("@")) {
                    input.lowercase()
                } else {
                    // Query Firestore to find email by username with timeout
                    try {
                        val querySnapshot = firestore.collection("users")
                            .whereEqualTo("username", input.lowercase())
                            .limit(1)
                            .get()
                            .await()

                        if (querySnapshot.documents.isEmpty()) {
                            return@withTimeout AuthResult(
                                success = false,
                                message = "User not found. Please check your username or email."
                            )
                        }
                        querySnapshot.documents[0].getString("email")?.lowercase()
                            ?: return@withTimeout AuthResult(
                                success = false,
                                message = "User data error. Please contact support."
                            )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying username", e)
                        return@withTimeout AuthResult(
                            success = false,
                            message = "Error finding user. Please check your connection."
                        )
                    }
                }

                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Optional: Skip email verification for faster testing
                    // Remove this check if you want to enforce email verification
                    /*
                    if (!user.isEmailVerified) {
                        return@withTimeout AuthResult(
                            success = false,
                            message = "Please verify your email before logging in"
                        )
                    }
                    */

                    AuthResult(
                        success = true,
                        message = "Login successful",
                        user = user
                    )
                } else {
                    AuthResult(success = false, message = "Login failed")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Sign in timeout", e)
            AuthResult(success = false, message = "Login is taking too long. Please check your internet connection.")
        } catch (e: Exception) {
            Log.e(TAG, "Sign in error", e)
            val errorMessage = when {
                e.message?.contains("no user record") == true ->
                    "No account found with this email"
                e.message?.contains("password is invalid") == true ||
                        e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                    "Incorrect password"
                e.message?.contains("network") == true ->
                    "Network error. Please check your connection."
                else -> e.message ?: "Login failed"
            }
            AuthResult(success = false, message = errorMessage)
        }
    }

    suspend fun resetPassword(email: String): AuthResult {
        return try {
            withTimeout(TIMEOUT_MS) {
                auth.sendPasswordResetEmail(email.trim().lowercase()).await()
                AuthResult(
                    success = true,
                    message = "Password reset email sent. Please check your inbox."
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AuthResult(success = false, message = "Request timeout. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error", e)
            AuthResult(success = false, message = e.message ?: "Failed to send reset email")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun isPasswordValid(password: String): Boolean {
        if (password.length < 6) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}