package com.example.fypdraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()

    fun signUp(username: String, email: String, password: String) {
        // Validate inputs
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "All fields are required")
            return
        }

        if (!authRepository.isEmailValid(email)) {
            _uiState.value = AuthUiState(errorMessage = "Invalid email format")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.signUp(username, email, password)
            _uiState.value = AuthUiState(
                isLoading = false,
                isSuccess = result.success,
                errorMessage = if (!result.success) result.message else null,
                successMessage = if (result.success) result.message else null
            )
        }
    }

    fun signIn(emailOrUsername: String, password: String) {
        if (emailOrUsername.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Email/Username and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.signIn(emailOrUsername, password)
            _uiState.value = AuthUiState(
                isLoading = false,
                isSuccess = result.success,
                errorMessage = if (!result.success) result.message else null,
                successMessage = if (result.success) result.message else null
            )
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState(errorMessage = "Email is required")
            return
        }

        if (!authRepository.isEmailValid(email)) {
            _uiState.value = AuthUiState(errorMessage = "Invalid email format")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val result = authRepository.resetPassword(email)
            _uiState.value = AuthUiState(
                isLoading = false,
                isSuccess = result.success,
                errorMessage = if (!result.success) result.message else null,
                successMessage = if (result.success) result.message else null
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}