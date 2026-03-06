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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatGPTViewModel : ViewModel() {

    private val TAG = "ChatGPTViewModel"
    private val chatRepo = ChatRepository()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val history = chatRepo.loadHistory()
            if (history.isNotEmpty()) _messages.value = history
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        viewModelScope.launch {
            addMessage(ChatMessageUi(sender = MessageSender.USER, text = userText.trim()))

            _typingState.value = TypingState.Thinking
            _uiState.value = ChatUiState.Loading

            chatRepo.sendMessage(userText.trim()).fold(
                onSuccess = { (aiText, songs) ->
                    _typingState.value = TypingState.FilteringResponse

                    addMessage(
                        ChatMessageUi(
                            sender = MessageSender.AI,
                            text = aiText,
                            songs = songs
                        )
                    )
                    _typingState.value = TypingState.Idle
                    _uiState.value = ChatUiState.Idle
                },
                onFailure = { error ->
                    Log.e(TAG, "sendMessage failed: ${error.message}")
                    _typingState.value = TypingState.Idle

                    val errorMessage = when {
                        error.message?.contains("RATE_LIMIT") == true ||
                                error.message?.contains("QUOTA") == true -> {
                            _uiState.value = ChatUiState.RateLimitExceeded
                            "Your OpenAI quota has been exceeded. Please check your billing at platform.openai.com."
                        }
                        error.message?.contains("Unable to resolve host") == true ||
                                error.message?.contains("timeout") == true -> {
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

    fun clearConversation() {
        viewModelScope.launch {
            chatRepo.clearConversation()
            _messages.value = emptyList()
        }
    }

    fun dismissError() {
        _uiState.value = ChatUiState.Idle
    }

    private fun addMessage(msg: ChatMessageUi) {
        _messages.value = _messages.value + msg
    }
}