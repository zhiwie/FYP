package com.example.fypdraft.view

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.FriendProfile
import com.example.fypdraft.data.repository.FriendChatMessage
import com.example.fypdraft.data.repository.MusicMoment
import com.example.fypdraft.data.repository.SocialRepository
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 2
) {
    val scope      = rememberCoroutineScope()
    val socialRepo = remember { SocialRepository() }

    var friends             by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var myMoment            by remember { mutableStateOf<MusicMoment?>(null) }
    var isLoading           by remember { mutableStateOf(true) }
    var showAddDialog       by remember { mutableStateOf(false) }
    var showVibeCheckDialog by remember { mutableStateOf(false) }
    var addError            by remember { mutableStateOf<String?>(null) }

    // The friend whose chat is open (null = feed view)
    var chatFriend by remember { mutableStateOf<FriendProfile?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            friends  = socialRepo.getFriendsWithProfiles()
            myMoment = socialRepo.getMyMoment()
        } catch (e: Exception) { Log.e("FriendsScreen", "Load failed", e) }
        isLoading = false
    }

    val playerState  = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack
    val isPlaying    = playerState?.value?.isPlaying ?: false

    LaunchedEffect(currentTrack?.id, isPlaying) {
        if (currentTrack != null && isPlaying) {
            socialRepo.shareNowPlaying(currentTrack.name, currentTrack.artist,
                currentTrack.albumArtUrl, currentTrack.spotifyUri, "neutral")
        }
    }

    // Slide the chat panel over the feed when a friend is selected
    Box(Modifier.fillMaxSize()) {
        // ── Main feed ────────────────────────────────────────────────
        Scaffold(
            bottomBar = {
                BottomNavBar(currentTab, onNavigateToHome, onNavigateToSearch,
                    {}, onNavigateToLibrary, themeState = themeState)
            },
            floatingActionButton = {
                if (currentTrack != null) {
                    FloatingActionButton(
                        onClick = { showVibeCheckDialog = true },
                        containerColor = Color(0xFF1DB954),
                        shape = CircleShape, modifier = Modifier.size(60.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎵", fontSize = 16.sp)
                            Text("Vibe", fontSize = 9.sp, color = Color.White,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize()
                    .background(animatedMoodBrushLight(themeState))
                    .padding(padding)
            ) {
                // Header
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Friends", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        val online = friends.count { it.isOnline }
                        if (friends.isNotEmpty())
                            Text("$online listening now", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, null)
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {

                        // Story circles — tap to open chat
                        if (friends.isNotEmpty()) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    item {
                                        StoryCircle("You","Me", isPlaying,
                                            myMoment!=null, Color(0xFF1DB954)) {
                                            if (currentTrack != null) showVibeCheckDialog = true
                                        }
                                    }
                                    items(friends) { f ->
                                        StoryCircle(
                                            name         = f.displayName.split(" ").first(),
                                            initial      = f.displayName.take(1).uppercase(),
                                            isOnline     = f.isOnline,
                                            hasNewMoment = f.currentMoment != null,
                                            accentColor  = getMoodColor(f.currentMoment?.mood ?: "neutral"),
                                            onClick      = { chatFriend = f }   // ← opens chat
                                        )
                                    }
                                }
                            }
                        }

                        // My moment
                        if (myMoment != null) {
                            item {
                                Text("Your Vibe", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                MomentCard(moment = myMoment!!, isOwn = true,
                                    onReact = {}, onPlay = {},
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }

                        // Friends with moments
                        val withMoments = friends.filter { it.currentMoment != null }
                        if (withMoments.isNotEmpty()) {
                            item {
                                Text("Friends' Vibes", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            }
                            items(withMoments, key = { it.uid }) { f ->
                                MomentCard(
                                    moment   = f.currentMoment!!,
                                    isOwn    = false,
                                    onReact  = { emoji ->
                                        scope.launch {
                                            socialRepo.reactToMoment(f.uid, emoji)
                                            friends = socialRepo.getFriendsWithProfiles()
                                        }
                                    },
                                    onPlay   = {
                                        f.currentMoment?.let { m ->
                                            musicPlayerViewModel?.playFromRecommendation(
                                                m.trackTitle, m.trackArtist) { ok, _ ->
                                                if (ok) onNavigateToMusicPlayer()
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Friends list with chat button
                        item {
                            Text("All Friends", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                        items(friends, key = { it.uid }) { f ->
                            FriendRow(f, onChat = { chatFriend = f })
                        }

                        if (friends.isEmpty()) {
                            item { EmptyFriendsState(onAdd = { showAddDialog = true }) }
                        }

                        item { InviteCard() }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }

        // ── Chat panel (slides in from right) ────────────────────────
        AnimatedVisibility(
            visible = chatFriend != null,
            enter   = slideInHorizontally(initialOffsetX = { it }),
            exit    = slideOutHorizontally(targetOffsetX = { it })
        ) {
            chatFriend?.let { friend ->
                FriendChatPanel(
                    friend       = friend,
                    socialRepo   = socialRepo,
                    currentTrack = currentTrack,
                    isPlaying    = isPlaying,
                    onBack       = { chatFriend = null },
                    onPlayFriend = {
                        friend.currentMoment?.let { m ->
                            musicPlayerViewModel?.playFromRecommendation(m.trackTitle, m.trackArtist) { ok,_ ->
                                if (ok) onNavigateToMusicPlayer()
                            }
                        }
                    }
                )
            }
        }
    }

    // Dialogs
    if (showVibeCheckDialog && currentTrack != null) {
        VibeCheckDialog(
            trackTitle  = currentTrack.name,
            trackArtist = currentTrack.artist,
            albumArtUrl = currentTrack.albumArtUrl,
            onDismiss   = { showVibeCheckDialog = false },
            onPost      = { caption, mood ->
                scope.launch {
                    socialRepo.postVibeCheck(currentTrack.name, currentTrack.artist,
                        currentTrack.albumArtUrl, currentTrack.spotifyUri, mood, caption)
                    myMoment = socialRepo.getMyMoment()
                    showVibeCheckDialog = false
                }
            }
        )
    }
    if (showAddDialog) {
        AddFriendDialog(error = addError, onDismiss = { showAddDialog = false; addError = null },
            onAdd = { username ->
                scope.launch {
                    socialRepo.addFriend(username).fold(
                        onSuccess = { showAddDialog = false; addError = null
                            friends = socialRepo.getFriendsWithProfiles() },
                        onFailure = { addError = it.message }
                    )
                }
            })
    }
}

// ── Per-friend chat panel ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendChatPanel(
    friend: FriendProfile,
    socialRepo: SocialRepository,
    currentTrack: com.example.fypdraft.model.Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayFriend: () -> Unit
) {
    val scope       = rememberCoroutineScope()
    val focusMgr    = LocalFocusManager.current
    val listState   = rememberLazyListState()

    var messages  by remember { mutableStateOf<List<FriendChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(friend.uid) {
        isLoading = true
        messages  = socialRepo.getChatMessages(friend.uid)
        isLoading = false
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = Color(0xFFF5F5FA),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.Black)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(getMoodColor(friend.currentMoment?.mood ?: "neutral")
                                    .copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(friend.displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = getMoodColor(friend.currentMoment?.mood ?: "neutral"))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(friend.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (friend.isOnline) "🟢 Listening now" else "Offline",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                },
                actions = {
                    // "What they're playing" shortcut
                    if (friend.currentMoment != null) {
                        IconButton(onClick = onPlayFriend) {
                            Text("▶🎵", fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            // Chat input
            Surface(
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Message ${friend.displayName.split(" ").first()}…",
                            color = Color.Gray) },
                        shape         = RoundedCornerShape(24.dp),
                        maxLines      = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                val msg = inputText.trim()
                                inputText = ""
                                focusMgr.clearFocus()
                                scope.launch {
                                    socialRepo.sendChatMessage(friend.uid, msg)
                                    messages = socialRepo.getChatMessages(friend.uid)
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFF1DB954),
                            unfocusedBorderColor = Color(0xFFDDDDDD)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    // Share current track button
                    if (currentTrack != null && isPlaying) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    socialRepo.sendChatMessage(
                                        friend.uid,
                                        "🎵 I'm listening to: ${currentTrack.name} — ${currentTrack.artist}"
                                    )
                                    messages = socialRepo.getChatMessages(friend.uid)
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(Color(0xFF1DB954))
                        ) { Text("🎵", fontSize = 18.sp) }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = {
                            val msg = inputText.trim()
                            if (msg.isNotBlank()) {
                                inputText = ""
                                focusMgr.clearFocus()
                                scope.launch {
                                    socialRepo.sendChatMessage(friend.uid, msg)
                                    messages = socialRepo.getChatMessages(friend.uid)
                                }
                            }
                        },
                        enabled  = inputText.isNotBlank(),
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(if (inputText.isNotBlank()) Color(0xFF1A1A2E) else Color(0xFFEEEEEE))
                    ) {
                        Icon(Icons.Filled.Send, null,
                            tint = if (inputText.isNotBlank()) Color.White else Color.Gray)
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        } else if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👋", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Say hi to ${friend.displayName.split(" ").first()}!",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text("Share what you're listening to 🎵",
                        fontSize = 13.sp, color = Color.LightGray)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(msg: FriendChatMessage) {
    val isMe = msg.isFromMe
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Box(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isMe) 18.dp else 4.dp,
                            topEnd   = if (isMe) 4.dp else 18.dp,
                            bottomStart = 18.dp, bottomEnd = 18.dp
                        )
                    )
                    .background(if (isMe) Color(0xFF1DB954) else Color.White)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    msg.text,
                    color    = if (isMe) Color.White else Color.Black,
                    fontSize = 14.sp, lineHeight = 20.sp
                )
            }
            Text(
                formatTimeAgo(msg.timestamp),
                fontSize = 10.sp, color = Color.LightGray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FriendRow(friend: FriendProfile, onChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onChat() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(getMoodColor(friend.currentMoment?.mood ?: "neutral").copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(friend.displayName.take(1).uppercase(),
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = getMoodColor(friend.currentMoment?.mood ?: "neutral"))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            friend.currentMoment?.let {
                Text("🎵 ${it.trackTitle}", fontSize = 11.sp, color = Color.Gray, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            } ?: Text(if (friend.lastActive > 0) "Last seen ${formatTimeAgo(friend.lastActive)}"
            else "Never active", fontSize = 11.sp, color = Color.Gray)
        }
        Icon(Icons.Filled.ChatBubbleOutline, "Chat", tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

// ── Reused components (Story, MomentCard, etc.) ──────────────────────────

@Composable
private fun StoryCircle(
    name: String, initial: String, isOnline: Boolean,
    hasNewMoment: Boolean, accentColor: Color, onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.width(64.dp)
    ) {
        Box {
            Box(
                modifier = Modifier.size(56.dp)
                    .then(if (hasNewMoment)
                        Modifier.border(2.5.dp, Brush.linearGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.4f))), CircleShape)
                    else Modifier.border(1.dp, Color.LightGray, CircleShape))
                    .padding(3.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
            }
            if (isOnline) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White)
                    .align(Alignment.BottomEnd)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4CAF50))
                        .align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, color = Color.Black)
    }
}

@Composable
private fun MomentCard(
    moment: MusicMoment, isOwn: Boolean,
    onReact: (String) -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier
) {
    var showReactions by remember { mutableStateOf(false) }
    val moodColor = getMoodColor(moment.mood)
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(moodColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Text(moment.userName.take(1).uppercase(), fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = moodColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isOwn) "You" else moment.userName,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(moment.moodEmoji, fontSize = 12.sp)
                    }
                    Text(formatTimeAgo(moment.timestamp), fontSize = 11.sp, color = Color.Gray)
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(moodColor.copy(alpha=0.1f), Color(0xFFF8F8FA))))
                .clickable { onPlay() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(moodColor.copy(alpha=0.2f)),
                    contentAlignment = Alignment.Center) { Text("🎵", fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(moment.trackTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(moment.trackArtist, fontSize = 12.sp, color = Color.Gray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(Modifier.size(38.dp).clickable { onPlay() }, shape = CircleShape, color = moodColor) {
                    Box(contentAlignment = Alignment.Center) { Text("▶", fontSize = 14.sp, color = Color.White) }
                }
            }
            if (moment.caption.isNotBlank()) {
                Text(moment.caption, Modifier.padding(horizontal = 16.dp),
                    fontSize = 13.sp, color = Color.Black)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                moment.reactions.values.groupBy { it }.forEach { (emoji, list) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF0F0F0),
                        modifier = Modifier.padding(end = 6.dp)) {
                        Text("$emoji ${list.size}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!isOwn) {
                    IconButton(onClick = { showReactions = !showReactions }, modifier = Modifier.size(32.dp)) {
                        Text("😊", fontSize = 18.sp)
                    }
                }
            }
            AnimatedVisibility(visible = showReactions) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("🔥","💖","🎵","😍","🤩","👏","😢","⚡").forEach { emoji ->
                        Surface(shape = CircleShape, color = Color(0xFFF0F0F0),
                            modifier = Modifier.clickable { onReact(emoji); showReactions = false }) {
                            Text(emoji, Modifier.padding(8.dp), fontSize = 20.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun EmptyFriendsState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👥", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("No friends yet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Add friends to share music vibes!", fontSize = 14.sp,
            color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Add a friend")
        }
    }
}

@Composable
private fun InviteCard() {
    Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎶", fontSize = 32.sp); Spacer(Modifier.height(8.dp))
            Text("Invite friends to MoodSync", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Share your music vibes together", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)) {
                Text("Invite", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VibeCheckDialog(
    trackTitle: String, trackArtist: String, albumArtUrl: String,
    onDismiss: () -> Unit, onPost: (String, String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("happy") }
    val moods = listOf("happy" to "😊","energetic" to "⚡","calm" to "😌","sad" to "😢","focused" to "🎯","romantic" to "💕")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Share Your Vibe 🎵", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954).copy(alpha=0.2f)),
                            contentAlignment = Alignment.Center) { Text("🎵", fontSize = 20.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(trackTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            Text(trackArtist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("How are you feeling?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    moods.forEach { (mood, emoji) ->
                        Surface(shape = CircleShape,
                            color = if (selectedMood == mood) getMoodColor(mood).copy(alpha=0.2f) else Color(0xFFF0F0F0),
                            modifier = Modifier.clickable { selectedMood = mood }
                                .then(if (selectedMood == mood) Modifier.border(2.dp, getMoodColor(mood), CircleShape) else Modifier)) {
                            Text(emoji, Modifier.padding(10.dp), fontSize = 20.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = caption, onValueChange = { if (it.length <= 120) caption = it },
                    placeholder = { Text("Add a caption...") }, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(), maxLines = 3, singleLine = false)
                Text("${caption.length}/120", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
            }
        },
        confirmButton = {
            Button(onClick = { onPost(caption, selectedMood) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))) {
                Text("Share Vibe 🎵")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddFriendDialog(error: String?, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Add a friend", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter their MoodSync username:", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = username, onValueChange = { username = it },
                    placeholder = { Text("Username") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error, color = Color.Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (username.isNotBlank()) onAdd(username) }, enabled = username.isNotBlank()) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun getMoodColor(mood: String): Color = when (mood) {
    "happy" -> Color(0xFFFFB347); "sad" -> Color(0xFF667EEA); "calm" -> Color(0xFF89CFF0)
    "energetic" -> Color(0xFFFF416C); "tired" -> Color(0xFF607D8B)
    "focused" -> Color(0xFF11998E); "romantic" -> Color(0xFFEE9CA7); else -> Color(0xFF1DB954)
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000    -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else             -> "${diff / 86_400_000}d ago"
    }
}