package com.example.fypdraft.view

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fypdraft.model.*
import com.example.fypdraft.viewmodel.ChatGPTViewModel
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import java.text.SimpleDateFormat
import java.util.*

private val AccentGreen = Color(0xFF1DB954)
private val GlassWhite  = Color.White.copy(alpha = 0.08f)
private val UserBubble  = Color.White.copy(alpha = 0.15f)
private val SongCardBg  = Color.White.copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionChatScreen(
    chatViewModel: ChatGPTViewModel = viewModel(),
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    petState: PetState = PetState(),
    petRepository: PetRepository? = null,
    themeState: com.example.fypdraft.ui.theme.AppThemeState = com.example.fypdraft.ui.theme.AppThemeState(),
    themeManager: com.example.fypdraft.ui.theme.ThemeManager? = null,
    pendingMessage: String? = null,
    onBack: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onSongClick: (SongRecommendation) -> Unit = {}
) {
    val messages    by chatViewModel.messages.collectAsState()
    val typingState by chatViewModel.typingState.collectAsState()
    val uiState     by chatViewModel.uiState.collectAsState()

    var inputText     by remember { mutableStateOf("") }
    val listState     = rememberLazyListState()
    val focusManager  = LocalFocusManager.current
    val snackbarHost  = remember { SnackbarHostState() }
    var loadingSongId by remember { mutableStateOf<String?>(null) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var detectedMood           by remember { mutableStateOf("neutral") }

    var pendingSent by remember { mutableStateOf(false) }
    LaunchedEffect(pendingMessage) {
        if (!pendingMessage.isNullOrBlank() && !pendingSent) {
            pendingSent  = true
            detectedMood = detectMoodFromText(pendingMessage)
            petRepository?.updateMood(detectedMood)
            themeManager?.updateMood(detectedMood)
            chatViewModel.sendMessage(pendingMessage)
        }
    }

    LaunchedEffect(messages.size, typingState) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ChatUiState.Error          -> snackbarHost.showSnackbar((uiState as ChatUiState.Error).message)
            ChatUiState.NetworkError      -> snackbarHost.showSnackbar("No internet connection")
            ChatUiState.RateLimitExceeded -> snackbarHost.showSnackbar("Rate limit — wait a moment")
            else -> {}
        }
        chatViewModel.dismissError()
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { inputText = it }
    }

    val moodAccent = remember(detectedMood) {
        when (detectedMood) {
            "happy"     -> Color(0xFFFFB347)
            "sad"       -> Color(0xFF667EEA)
            "calm"      -> Color(0xFF89CFF0)
            "energetic" -> Color(0xFFFF416C)
            "chill"     -> Color(0xFF42A5F5)
            "romantic"  -> Color(0xFFEE9CA7)
            "focused"   -> Color(0xFF11998E)
            else        -> AccentGreen
        }
    }

    fun sendMessage(text: String) {
        val msg = text.trim()
        if (msg.isNotBlank()) {
            detectedMood = detectMoodFromText(msg)
            petRepository?.updateMood(detectedMood)
            themeManager?.updateMood(detectedMood)
            chatViewModel.sendMessage(msg)
            inputText = ""
            focusManager.clearFocus()
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFE57373), modifier = Modifier.size(32.dp)) },
            title = { Text("Clear all conversation?", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text  = {
                Text(
                    "This will permanently delete your entire chat history with ${petState.name}. " +
                            "All messages, song recommendations, and context will be lost and cannot be recovered.",
                    textAlign = TextAlign.Center, fontSize = 14.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { chatViewModel.clearConversation(); showClearConfirmDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Clear everything", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmDialog = false }) { Text("Keep chatting") } }
        )
    }

    val chatPalette  = themeState.activePalette
    val chatBgColors = listOf(
        chatPalette.darkTop.copy(alpha = 0.8f).compositeOver(Color(0xFF0A0A0A)),
        chatPalette.darkMid.copy(alpha = 0.5f).compositeOver(Color(0xFF0A0A0A)),
        chatPalette.darkBottom.copy(alpha = 0.3f).compositeOver(Color(0xFF0A0A0A))
    )
    val bottomBarBg = chatPalette.darkBottom.copy(alpha = 0.97f).compositeOver(Color(0xFF080808))

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = Color.Transparent,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(chatPalette.accent, chatPalette.glowColor)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("MoodSync AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(getBuddyStatusText(petState, typingState), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }

                    // ── Top-bar avatar: LayeredAvatar (small, no blink loop needed at this size) ──
                    Box(
                        modifier         = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LayeredAvatar(
                            species = AvatarSpecies.fromPetType(petState.type),
                            state   = when (typingState) {
                                TypingState.Thinking         -> AvatarState.IDLE
                                TypingState.FindingSongs      -> AvatarState.LISTENING
                                TypingState.FilteringResponse -> AvatarState.LISTENING
                                else                         -> AvatarState.IDLE
                            },
                            accessories = petState.avatarEquippedIds()
                                .mapNotNull { id -> AvatarAccessoryRegistry.all.firstOrNull { it.id == id } },
                            size = 34.dp
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, "Clear", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                text         = inputText,
                onTextChange = { inputText = it },
                onSend       = { sendMessage(inputText) },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your mood...")
                    }
                    try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                },
                isLoading  = uiState is ChatUiState.Loading,
                moodAccent = moodAccent,
                bgColor    = bottomBarBg
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(chatBgColors))
                .padding(padding)
                .imePadding()
        ) {
            if (messages.isEmpty() && typingState == TypingState.Idle) {
                EmptyChatPlaceholder(petState = petState, themeState = themeState, onSuggestionClick = { sendMessage(it) })
            } else {
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(
                            message       = message,
                            petState      = petState,
                            loadingSongId = loadingSongId,
                            moodAccent    = moodAccent,
                            onSongClick   = { song ->
                                val songId = "${song.artist}-${song.title}"
                                if (musicPlayerViewModel != null) {
                                    loadingSongId = songId
                                    musicPlayerViewModel.playFromRecommendation(song.title, song.artist) { success, _ ->
                                        loadingSongId = null
                                        if (success) onNavigateToMusicPlayer()
                                    }
                                } else onSongClick(song)
                            }
                        )
                    }
                    if (typingState != TypingState.Idle) {
                        item { BuddyTypingIndicator(typingState, petState) }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

private fun getBuddyStatusText(petState: PetState, typingState: TypingState): String = when (typingState) {
    TypingState.Thinking         -> "${petState.name} is thinking..."
    TypingState.FindingSongs      -> "${petState.name} is finding songs..."
    TypingState.FilteringResponse -> "${petState.name} is curating..."
    else                         -> "${petState.name} is listening with you"
}

private fun detectMoodFromText(text: String): String {
    val lower = text.lowercase()
    return when {
        lower.containsAny("happy", "joy", "excited", "great", "awesome", "amazing") -> "happy"
        lower.containsAny("sad", "down", "depressed", "lonely", "heartbreak", "cry") -> "sad"
        lower.containsAny("calm", "relax", "peaceful", "chill", "unwind") -> "calm"
        lower.containsAny("energetic", "pump", "workout", "hype", "dance", "party") -> "energetic"
        lower.containsAny("focus", "study", "concentrate", "work", "productive") -> "focused"
        lower.containsAny("romantic", "love", "crush", "date") -> "romantic"
        lower.containsAny("tired", "sleepy", "exhausted", "night") -> "tired"
        else -> "neutral"
    }
}

private fun String.containsAny(vararg words: String): Boolean = words.any { this.contains(it) }

private fun formatMessageTime(timestamp: Long): String {
    val cal   = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 ->
            "Yesterday " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        else ->
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

// ── Empty state — no large avatar, just emoji + text ─────────────────────
// The big PixelPet in the centre has been removed per spec.
// The pet's presence is already felt through the top-bar avatar icon.

@Composable
private fun EmptyChatPlaceholder(
    petState: PetState,
    themeState: com.example.fypdraft.ui.theme.AppThemeState,
    onSuggestionClick: (String) -> Unit
) {
    val suggestionBg = themeState.activePalette.accent.copy(alpha = 0.18f)
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mood emoji instead of a pet sprite — clean, no PixelPet dependency
        Text("🎵", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text("Hey! I'm ${petState.name}!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tell me how you're feeling and\nI'll find the perfect music for you.",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        val suggestions = listOf("I need to relax 😌", "Feeling energetic ⚡", "I'm a bit sad 😢", "Help me focus 🎯")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { text ->
                        Surface(
                            shape           = RoundedCornerShape(20.dp),
                            color           = suggestionBg,
                            shadowElevation = 8.dp,
                            modifier        = Modifier.weight(1f).clickable { onSuggestionClick(text) }
                        ) {
                            Text(
                                text,
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────
// AI messages: small LayeredAvatar circle on the left (profile-style).
// User messages: no avatar, right-aligned bubble only.

@Composable
private fun ChatBubble(
    message: ChatMessageUi,
    petState: PetState,
    loadingSongId: String?,
    moodAccent: Color,
    onSongClick: (SongRecommendation) -> Unit
) {
    val isUser    = message.sender == MessageSender.USER
    val timeLabel = formatMessageTime(message.timestamp)

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // ── AI avatar: LayeredAvatar profile circle ───────────────────
            // Static idle state — no auto-blink at this small size (32dp).
            Box(
                modifier         = Modifier.size(32.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                LayeredAvatar(
                    species     = AvatarSpecies.fromPetType(petState.type),
                    state       = AvatarState.IDLE,
                    accessories = petState.avatarEquippedIds()
                        .mapNotNull { id -> AvatarAccessoryRegistry.all.firstOrNull { it.id == id } },
                    size        = 26.dp
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier            = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.text.isNotBlank()) {
                if (isUser) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                        color = UserBubble
                    ) {
                        Text(
                            message.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color    = Color.White, fontSize = 14.sp, lineHeight = 20.sp
                        )
                    }
                } else {
                    Text(
                        message.text,
                        modifier   = Modifier.padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                        color      = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 21.sp
                    )
                }
            }

            Text(
                text     = timeLabel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(
                    start  = if (isUser) 0.dp else 2.dp,
                    end    = if (isUser) 2.dp else 0.dp,
                    top    = 3.dp, bottom = 2.dp
                )
            )

            if (message.songs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding        = PaddingValues(end = 8.dp)
                ) {
                    items(message.songs) { song ->
                        val songId = "${song.artist}-${song.title}"
                        MiniSongCard(
                            song       = song,
                            isLoading  = loadingSongId == songId,
                            moodAccent = moodAccent,
                            onClick    = { onSongClick(song) }
                        )
                    }
                }
            }
        }
    }
}

// ── Mini song card ────────────────────────────────────────────────────────

@Composable
private fun MiniSongCard(song: SongRecommendation, isLoading: Boolean, moodAccent: Color, onClick: () -> Unit) {
    Surface(
        modifier       = Modifier.width(160.dp).clickable(enabled = !isLoading, onClick = onClick),
        shape          = RoundedCornerShape(14.dp),
        color          = SongCardBg,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(moodAccent.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f)))),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("🎵", fontSize = 28.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(song.title,  fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(song.reason, fontSize = 9.sp, color = moodAccent.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                shape    = RoundedCornerShape(14.dp),
                color    = moodAccent.copy(alpha = 0.65f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isLoading) "Loading..." else "▶  Play", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

// ── Typing indicator ──────────────────────────────────────────────────────

@Composable
private fun BuddyTypingIndicator(typingState: TypingState, petState: PetState) {
    val inf    = rememberInfiniteTransition(label = "typing")
    val bounce by inf.animateFloat(0f, -6f, infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse), label = "typeBounce")

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
        // Small layered avatar bounces while AI is typing
        Box(
            modifier         = Modifier.size(40.dp).offset(y = bounce.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            LayeredAvatar(
                species = AvatarSpecies.fromPetType(petState.type),
                state   = when (typingState) {
                    TypingState.FindingSongs      -> AvatarState.LISTENING
                    TypingState.FilteringResponse -> AvatarState.LISTENING
                    else                         -> AvatarState.IDLE
                },
                accessories = petState.avatarEquippedIds()
                    .mapNotNull { id -> AvatarAccessoryRegistry.all.firstOrNull { it.id == id } },
                size = 34.dp
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val d1 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "d1")
                val d2 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400, delayMillis = 150), RepeatMode.Reverse), label = "d2")
                val d3 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400, delayMillis = 300), RepeatMode.Reverse), label = "d3")
                listOf(d1, d2, d3).forEach { a ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AccentGreen.copy(alpha = a)))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(typingState.label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String, onTextChange: (String) -> Unit, onSend: () -> Unit,
    onVoiceClick: () -> Unit, isLoading: Boolean, moodAccent: Color,
    bgColor: Color = Color(0xFF0D1F18).copy(alpha = 0.95f)
) {
    Surface(color = bgColor, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value           = text,
                onValueChange   = onTextChange,
                modifier        = Modifier.weight(1f),
                placeholder     = { Text("Tell me how you feel...", color = Color.White.copy(alpha = 0.4f)) },
                shape           = RoundedCornerShape(24.dp),
                maxLines        = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                enabled         = !isLoading,
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = moodAccent,
                    unfocusedBorderColor    = Color.White.copy(alpha = 0.15f),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color.White,
                    cursorColor             = moodAccent,
                    focusedContainerColor   = GlassWhite,
                    unfocusedContainerColor = GlassWhite
                )
            )
            Spacer(Modifier.width(8.dp))
            if (text.isBlank()) {
                IconButton(
                    onClick  = onVoiceClick, enabled = !isLoading,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(GlassWhite)
                ) {
                    Icon(Icons.Default.Mic, "Voice", tint = Color.White.copy(alpha = 0.7f))
                }
            } else {
                IconButton(
                    onClick  = onSend, enabled = !isLoading,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(if (!isLoading) moodAccent else GlassWhite)
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, "Send", tint = Color.White)
                }
            }
        }
    }
}