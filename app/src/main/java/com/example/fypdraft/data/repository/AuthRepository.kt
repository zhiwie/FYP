package com.example.fypdraft.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class AuthResult(
    val success: Boolean,
    val message: String,
    val user: FirebaseUser? = null
)

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    suspend fun signUp(
        username: String,
        email: String,
        password: String
    ): AuthResult {
        return try {
            // Validate password
            if (!isPasswordValid(password)) {
                return AuthResult(
                    success = false,
                    message = "Password must be at least 6 characters and contain both letters and numbers"
                )
            }

            // Create user
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                // Update profile with username
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()
                user.updateProfile(profileUpdates).await()

                // Send email verification
                user.sendEmailVerification().await()

                // Save additional user data to Firestore
                val userData = hashMapOf(
                    "username" to username,
                    "email" to email,
                    "createdAt" to System.currentTimeMillis(),
                    "emailVerified" to false
                )
                firestore.collection("users").document(user.uid).set(userData).await()

                AuthResult(
                    success = true,
                    message = "Account created! Please verify your email.",
                    user = user
                )
            } else {
                AuthResult(success = false, message = "Failed to create account")
            }
        } catch (e: Exception) {
            AuthResult(success = false, message = e.message ?: "Sign up failed")
        }
    }

    suspend fun signIn(emailOrUsername: String, password: String): AuthResult {
        return try {
            // Check if input is email or username
            val email = if (emailOrUsername.contains("@")) {
                emailOrUsername
            } else {
                // Query Firestore to find email by username
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("username", emailOrUsername)
                    .get()
                    .await()

                if (querySnapshot.documents.isEmpty()) {
                    return AuthResult(success = false, message = "User not found")
                }
                querySnapshot.documents[0].getString("email") ?: return AuthResult(
                    success = false,
                    message = "User not found"
                )
            }

            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                // Check if email is verified
                if (!user.isEmailVerified) {
                    return AuthResult(
                        success = false,
                        message = "Please verify your email before logging in"
                    )
                }

                AuthResult(
                    success = true,
                    message = "Login successful",
                    user = user
                )
            } else {
                AuthResult(success = false, message = "Login failed")
            }
        } catch (e: Exception) {
            AuthResult(success = false, message = e.message ?: "Login failed")
        }
    }

    suspend fun resetPassword(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult(
                success = true,
                message = "Password reset email sent. Please check your inbox."
            )
        } catch (e: Exception) {
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