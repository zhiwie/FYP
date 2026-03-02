package com.example.fypdraft.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fypdraft.data.repository.ChatGPTRepository
import com.example.fypdraft.data.repository.YouTubeMusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatGPTViewModel : ViewModel() {

    private val TAG = "ChatGPTViewModel"
    private val chatRepo    = ChatGPTRepository()
    private val youtubeRepo = YouTubeMusicRepository()

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ── Init: restore previous chat ───────────────────────────────────────────

    init {
        viewModelScope.launch {
            val history = chatRepo.loadConversationHistory()
            if (history.isNotEmpty()) _messages.value = history
        }
    }

    // ── Send a message ────────────────────────────────────────────────────────

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        viewModelScope.launch {
            addMessage(ChatMessageUi(sender = MessageSender.USER, text = userText.trim()))

            _typingState.value = TypingState.Thinking
            _uiState.value = ChatUiState.Loading

            chatRepo.sendMessage(userText.trim()).fold(
                onSuccess = { (aiText, songs) ->
                    _typingState.value = TypingState.FindingSongs

                    val enrichedSongs = enrichWithYoutube(songs)

                    _typingState.value = TypingState.FilteringResponse

                    addMessage(
                        ChatMessageUi(
                            sender = MessageSender.AI,
                            text   = aiText,
                            songs  = enrichedSongs
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

    // ── YouTube enrichment ────────────────────────────────────────────────────
    // Builds a minimal Track so YouTubeMusicRepository can do the search.
    // albumArtUrl is required (non-nullable) by the Track data class → pass "".

    private suspend fun enrichWithYoutube(songs: List<SongRecommendation>): List<SongRecommendation> {
        return songs.map { song ->
            try {
                val fakeTrack = Track(
                    id          = "${song.artist}-${song.title}",
                    name        = song.title,
                    artist      = song.artist,
                    albumArtUrl = "",      // ← required field; no art available from ChatGPT alone
                    previewUrl  = null,
                    durationMs  = 0L
                )
                val videoId = youtubeRepo.getYouTubeVideoId(fakeTrack)
                song.copy(youtubeVideoId = videoId)
            } catch (e: Exception) {
                Log.w(TAG, "YouTube lookup failed for ${song.artist} - ${song.title}")
                song
            }
        }
    }

    // ── Edit a message and regenerate ─────────────────────────────────────────

    fun editMessage(messageIndex: Int, newText: String) {
        viewModelScope.launch {
            _messages.value = _messages.value.take(messageIndex)
            sendMessage(newText)
        }
    }

    // ── Clear conversation ────────────────────────────────────────────────────

    fun clearConversation() {
        viewModelScope.launch {
            chatRepo.clearConversation()
            _messages.value = emptyList()
        }
    }

    fun dismissError() {
        _uiState.value = ChatUiState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addMessage(msg: ChatMessageUi) {
        _messages.value = _messages.value + msg
    }
}