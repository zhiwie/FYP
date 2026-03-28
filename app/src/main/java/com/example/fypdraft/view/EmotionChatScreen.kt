package com.example.fypdraft.view

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ── Theme colors matching Home screen ────────────────────────────────────

private val ChatBgDark = Color(0xFF0A1A15)
private val ChatBgGradient = listOf(Color(0xFF0D2B1F), Color(0xFF0A1A15), Color(0xFF091215))
private val AccentGreen = Color(0xFF1DB954)
private val AccentTeal = Color(0xFF11998E)
private val BubbleAI = Color(0xFF1A2E28)
private val BubbleUser = Color(0xFF1DB954)
private val GlassWhite = Color.White.copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionChatScreen(
    chatViewModel: ChatGPTViewModel = viewModel(),
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    petState: PetState = PetState(),
    petRepository: PetRepository? = null,
    themeState: com.example.fypdraft.ui.theme.AppThemeState = com.example.fypdraft.ui.theme.AppThemeState(),
    themeManager: com.example.fypdraft.ui.theme.ThemeManager? = null,
    onBack: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onSongClick: (SongRecommendation) -> Unit = {}
) {
    val messages by chatViewModel.messages.collectAsState()
    val typingState by chatViewModel.typingState.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }
    var loadingSongId by remember { mutableStateOf<String?>(null) }

    // Detect mood from user's last message for color sync
    var detectedMood by remember { mutableStateOf("neutral") }

    // Auto-scroll
    LaunchedEffect(messages.size, typingState) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Error snackbar
    LaunchedEffect(uiState) {
        when (uiState) {
            is ChatUiState.Error -> snackbarHost.showSnackbar((uiState as ChatUiState.Error).message)
            ChatUiState.NetworkError -> snackbarHost.showSnackbar("No internet connection")
            ChatUiState.RateLimitExceeded -> snackbarHost.showSnackbar("Rate limit — wait a moment")
            else -> {}
        }
        chatViewModel.dismissError()
    }

    // Voice
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { inputText = it }
    }

    // Mood color sync based on user input
    val moodAccent = remember(detectedMood) {
        when (detectedMood) {
            "happy" -> Color(0xFFFFB347)
            "sad" -> Color(0xFF667EEA)
            "calm" -> Color(0xFF89CFF0)
            "energetic" -> Color(0xFFFF416C)
            "chill" -> Color(0xFF42A5F5)
            "romantic" -> Color(0xFFEE9CA7)
            "focused" -> Color(0xFF11998E)
            else -> AccentGreen
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Color.Transparent,
        topBar = {
            // Gradient header that shifts with mood
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(
                        themeState.activePalette.accent,
                        themeState.activePalette.glowColor
                    )))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("MoodSync AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        // "Last active" hook
                        Text(
                            getBuddyStatusText(petState, typingState),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    // Mini Buddy avatar in header
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelPet(
                            petState = petState,
                            animation = when (typingState) {
                                TypingState.Thinking -> PetAnimation.FOCUSED_STARE
                                TypingState.FindingSongs -> PetAnimation.LISTENING
                                TypingState.FilteringResponse -> PetAnimation.DANCING
                                else -> petState.animationForMood()
                            },
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { chatViewModel.clearConversation() }) {
                            Icon(Icons.Default.Delete, "Clear", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Glassmorphism input bar
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    val msg = inputText.trim()
                    if (msg.isNotBlank()) {
                        // Detect mood keywords and sync to pet + theme
                        detectedMood = detectMoodFromText(msg)
                        petRepository?.updateMood(detectedMood)
                        themeManager?.updateMood(detectedMood)
                        chatViewModel.sendMessage(msg)
                        inputText = ""
                        focusManager.clearFocus()
                    }
                },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your mood...")
                    }
                    try { voiceLauncher.launch(intent) } catch (_: Exception) {}
                },
                isLoading = uiState is ChatUiState.Loading,
                moodAccent = moodAccent
            )
        }
    ) { padding ->
        // Dynamic dark background that shifts with mood
        val chatPalette = themeState.activePalette
        val chatBgColors = listOf(
            chatPalette.darkTop.copy(alpha = 0.8f).compositeOver(Color(0xFF0A0A0A)),
            chatPalette.darkMid.copy(alpha = 0.5f).compositeOver(Color(0xFF0A0A0A)),
            chatPalette.darkBottom.copy(alpha = 0.3f).compositeOver(Color(0xFF0A0A0A))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(chatBgColors))
                .padding(padding)
        ) {
            if (messages.isEmpty() && typingState == TypingState.Idle) {
                EmptyChatPlaceholder(petState)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Group messages with time headers
                    var lastTimeGroup = ""

                    items(messages, key = { it.id }) { message ->
                        val timeGroup = getTimeGroup(message.timestamp)
                        if (timeGroup != lastTimeGroup) {
                            lastTimeGroup = timeGroup
                            // Centered time header
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = GlassWhite
                                ) {
                                    Text(
                                        timeGroup,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        ChatBubble(
                            message = message,
                            petState = petState,
                            loadingSongId = loadingSongId,
                            moodAccent = moodAccent,
                            onSongClick = { song ->
                                val songId = "${song.artist}-${song.title}"
                                if (musicPlayerViewModel != null) {
                                    loadingSongId = songId
                                    musicPlayerViewModel.playFromRecommendation(
                                        songTitle = song.title,
                                        songArtist = song.artist
                                    ) { success, _ ->
                                        loadingSongId = null
                                        if (success) onNavigateToMusicPlayer()
                                    }
                                } else {
                                    onSongClick(song)
                                }
                            }
                        )
                    }

                    // Typing indicator with Buddy animation
                    if (typingState != TypingState.Idle) {
                        item { BuddyTypingIndicator(typingState, petState) }
                    }
                }
            }
        }
    }
}

// ── Buddy status text ────────────────────────────────────────────────────

private fun getBuddyStatusText(petState: PetState, typingState: TypingState): String = when (typingState) {
    TypingState.Thinking -> "${petState.name} is thinking..."
    TypingState.FindingSongs -> "${petState.name} is finding songs..."
    TypingState.FilteringResponse -> "${petState.name} is curating..."
    else -> "${petState.name} is listening with you"
}

// ── Mood detection from text ─────────────────────────────────────────────

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

// ── Time grouping ────────────────────────────────────────────────────────

private fun getTimeGroup(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()

    return when {
        diff < 60_000 -> "Just now"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            "Today, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))}"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 ->
            "Yesterday, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))}"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun getRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

// ── Empty state with Buddy ───────────────────────────────────────────────

@Composable
private fun EmptyChatPlaceholder(petState: PetState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Buddy greeting
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            PixelPet(
                petState = petState,
                animation = PetAnimation.HAPPY_BOUNCE,
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Hey! I'm ${petState.name}!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text("Tell me how you're feeling and\nI'll find the perfect music for you.", fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)

        Spacer(Modifier.height(28.dp))

        // Suggestion chips
        val suggestions = listOf("I need to relax 😌", "Feeling energetic ⚡", "I'm a bit sad 😢", "Help me focus 🎯")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { text ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = GlassWhite,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ── Chat bubble ──────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(
    message: ChatMessageUi,
    petState: PetState,
    loadingSongId: String?,
    moodAccent: Color,
    onSongClick: (SongRecommendation) -> Unit
) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // Buddy avatar (AI messages only)
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                PixelPet(
                    petState = petState,
                    animation = PetAnimation.IDLE,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Text bubble
            if (message.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 18.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 18.dp,
                        bottomStart = 18.dp, bottomEnd = 18.dp
                    ),
                    color = if (isUser) moodAccent else BubbleAI,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        message.text,
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (isUser) Color.White else Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp, lineHeight = 20.sp
                    )
                }
            }

            // Song cards — horizontal scroll like "Picked for you"
            if (message.songs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(message.songs) { song ->
                        val songId = "${song.artist}-${song.title}"
                        val isLoading = loadingSongId == songId
                        MiniSongCard(song = song, isLoading = isLoading, moodAccent = moodAccent, onClick = { onSongClick(song) })
                    }
                }
            }
        }
    }
}

// ── Mini song card (matching "Picked for you" style) ─────────────────────

@Composable
private fun MiniSongCard(
    song: SongRecommendation,
    isLoading: Boolean,
    moodAccent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.width(160.dp).clickable(enabled = !isLoading, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A2A24),
        tonalElevation = 4.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            // Album art placeholder with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(moodAccent.copy(alpha = 0.4f), Color(0xFF1A1A2E)))),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("🎵", fontSize = 28.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(song.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (song.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(song.reason, fontSize = 9.sp, color = moodAccent.copy(alpha = 0.8f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
            }

            Spacer(Modifier.height(6.dp))

            // Play button
            Surface(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = moodAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isLoading) "Loading..." else "▶  Play", fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

// ── Buddy typing indicator ───────────────────────────────────────────────

@Composable
private fun BuddyTypingIndicator(typingState: TypingState, petState: PetState) {
    val inf = rememberInfiniteTransition(label = "typing")
    val bounce by inf.animateFloat(
        0f, -6f,
        infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "typeBounce"
    )

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
        // Animated Buddy thinking
        Box(
            modifier = Modifier
                .size(40.dp)
                .offset(y = bounce.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            PixelPet(
                petState = petState,
                animation = when (typingState) {
                    TypingState.Thinking -> PetAnimation.FOCUSED_STARE
                    TypingState.FindingSongs -> PetAnimation.LISTENING
                    TypingState.FilteringResponse -> PetAnimation.DANCING
                    else -> PetAnimation.IDLE
                },
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = BubbleAI
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated dots
                val dot1 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "d1")
                val dot2 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400, delayMillis = 150), RepeatMode.Reverse), label = "d2")
                val dot3 by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(400, delayMillis = 300), RepeatMode.Reverse), label = "d3")

                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(AccentGreen.copy(alpha = alpha))
                    )
                    Spacer(Modifier.width(4.dp))
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    typingState.label,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── Glassmorphism input bar ──────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isLoading: Boolean,
    moodAccent: Color
) {
    Surface(
        color = Color(0xFF0D1F18).copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text field with glass effect
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tell ${" "}me how you feel...", color = Color.White.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = moodAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = moodAccent,
                    focusedContainerColor = GlassWhite,
                    unfocusedContainerColor = GlassWhite
                )
            )

            Spacer(Modifier.width(8.dp))

            // Voice button
            IconButton(
                onClick = onVoiceClick,
                enabled = !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GlassWhite)
            ) {
                Icon(Icons.Default.Mic, "Voice", tint = Color.White.copy(alpha = 0.7f))
            }

            Spacer(Modifier.width(6.dp))

            // Send button
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank() && !isLoading) moodAccent else GlassWhite)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, "Send",
                        tint = if (text.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}