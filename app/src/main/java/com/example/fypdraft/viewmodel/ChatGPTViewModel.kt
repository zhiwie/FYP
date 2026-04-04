package com.example.fypdraft.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.ChatRepository
import com.example.fypdraft.model.ChatMessageUi
import com.example.fypdraft.model.ChatUiState
import com.example.fypdraft.model.MessageSender
import com.example.fypdraft.model.SongRecommendation
import com.example.fypdraft.model.TypingState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatGPTViewModel : ViewModel() {

    private val TAG      = "ChatGPTViewModel"
    private val chatRepo = ChatRepository()
    private val auth     = FirebaseAuth.getInstance()

    private val _messages    = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private val _uiState     = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Load history for whoever is currently signed in.
        // Uses explicit UID so we never accidentally load the wrong account's history.
        val uid = auth.currentUser?.uid ?: ""
        viewModelScope.launch {
            val history = chatRepo.loadHistory(uid)
            if (history.isNotEmpty()) _messages.value = history
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        viewModelScope.launch {
            addMessage(ChatMessageUi(sender = MessageSender.USER, text = userText.trim()))

            _typingState.value = TypingState.Thinking
            _uiState.value     = ChatUiState.Loading

            chatRepo.sendMessage(userText.trim()).fold(
                onSuccess = { (aiText, songs) ->
                    _typingState.value = TypingState.FilteringResponse
                    addMessage(ChatMessageUi(sender = MessageSender.AI, text = aiText, songs = songs))
                    _typingState.value = TypingState.Idle
                    _uiState.value     = ChatUiState.Idle
                },
                onFailure = { error ->
                    Log.e(TAG, "sendMessage failed: ${error.message}")
                    _typingState.value = TypingState.Idle

                    val errorMessage = when {
                        error.message?.contains("RATE_LIMIT") == true ||
                                error.message?.contains("QUOTA")      == true -> {
                            _uiState.value = ChatUiState.RateLimitExceeded
                            "Your OpenAI quota has been exceeded. Please check your billing at platform.openai.com."
                        }
                        error.message?.contains("Unable to resolve host") == true ||
                                error.message?.contains("timeout")                == true -> {
                            _uiState.value = ChatUiState.NetworkError
                            "It looks like you're offline. Check your connection and try again."
                        }
                        else -> {
                            _uiState.value = ChatUiState.Error(error.message ?: "Unknown error")
                            "Sorry, something went wrong. Please try again."
                        }
                    }
                    addMessage(ChatMessageUi(sender = MessageSender.AI, text = errorMessage))
                }
            )
        }
    }

    fun editMessage(messageIndex: Int, newText: String) {
        viewModelScope.launch {
            _messages.value = _messages.value.take(messageIndex)
            sendMessage(newText)
        }
    }

    /**
     * Wipe messages from memory immediately (so the UI clears at once),
     * then delete from Firestore in the background.
     *
     * Called explicitly by [MoodSyncApp] on sign-out BEFORE navigating away,
     * so the next user never sees a previous user's chat.
     */
    fun clearConversation() {
        // Clear UI immediately — no coroutine needed for the state update
        _messages.value    = emptyList()
        _typingState.value = TypingState.Idle
        _uiState.value     = ChatUiState.Idle

        // Delete Firestore records for the user who is still signed in at this moment.
        // We resolve the UID now, before auth.signOut() is called by AuthViewModel.
        val uid = auth.currentUser?.uid ?: ""
        viewModelScope.launch {
            chatRepo.clearConversation(uid)
        }
    }

    /**
     * Load history for a specific [userId].
     *
     * Called from [MoodSyncApp] after a successful sign-in to ensure the
     * correct account's history is shown, even if the ViewModel was
     * already alive from a previous session in the same Activity.
     */
    fun reloadHistoryForUser(userId: String) {
        _messages.value = emptyList()      // clear any stale messages instantly
        viewModelScope.launch {
            val history = chatRepo.loadHistory(userId)
            if (history.isNotEmpty()) _messages.value = history
        }
    }

    fun dismissError() {
        _uiState.value = ChatUiState.Idle
    }

    private fun addMessage(msg: ChatMessageUi) {
        _messages.value = _messages.value + msg
    }
}