package com.example.fypdraft.view

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.FriendChatMessage
import com.example.fypdraft.data.repository.FriendProfile
import com.example.fypdraft.data.repository.FriendSuggestion
import com.example.fypdraft.data.repository.MusicMoment
import com.example.fypdraft.data.repository.SocialRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.model.PetState
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun FriendsScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    petState: PetState? = null,
    spotifyRepository: SpotifyRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 2
) {
    val isDark        = themeState.isDark
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)

    val scope            = rememberCoroutineScope()
    val context          = LocalContext.current
    val socialRepo       = remember { SocialRepository() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository.getInstance(it, context) }
    }
    val myUid       = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val currentUser = FirebaseAuth.getInstance().currentUser

    var myDisplayName by remember {
        mutableStateOf(
            currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: "You"
        )
    }

    var friends             by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var myMoment            by remember { mutableStateOf<MusicMoment?>(null) }
    var activityFeed        by remember { mutableStateOf<List<ActivityEvent>>(emptyList()) }
    var isLoading           by remember { mutableStateOf(true) }
    var showAddDialog       by remember { mutableStateOf(false) }
    var showVibeCheckDialog by remember { mutableStateOf(false) }
    var showQrDialog        by remember { mutableStateOf(false) }
    var showQrScanner       by remember { mutableStateOf(false) }
    var showEditProfile     by remember { mutableStateOf(false) }
    var addError            by remember { mutableStateOf<String?>(null) }
    var chatFriend          by remember { mutableStateOf<FriendProfile?>(null) }
    var vibeHistoryFriend   by remember { mutableStateOf<FriendProfile?>(null) }
    var friendToRemove      by remember { mutableStateOf<FriendProfile?>(null) }

    suspend fun refreshAll() {
        friends      = socialRepo.getFriendsWithProfiles()
        myMoment     = socialRepo.getMyMoment()
        activityFeed = buildActivityFeed(friends, myMoment)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        try { refreshAll() } catch (e: Exception) { Log.e("FriendsScreen", "Load failed", e) }
        isLoading = false
    }

    val playerState  = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack
    val isPlaying    = playerState?.value?.isPlaying ?: false
    val myMascotType = petState?.type?.name ?: "CAT"

    LaunchedEffect(currentTrack?.id, isPlaying) {
        if (currentTrack != null && isPlaying) {
            socialRepo.shareNowPlaying(
                currentTrack.name, currentTrack.artist,
                currentTrack.albumArtUrl, currentTrack.spotifyUri,
                "neutral", myMascotType
            )
            myMoment     = socialRepo.getMyMoment()
            activityFeed = buildActivityFeed(friends, myMoment)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniMusicPlayer(vm = musicPlayerViewModel, onNav = onNavigateToMusicPlayer, themeState = themeState)
                    BottomNavBar(currentTab, onNavigateToHome, onNavigateToSearch, {}, onNavigateToLibrary, themeState = themeState)
                }
            },
            floatingActionButton = {
                if (currentTrack != null) {
                    FloatingActionButton(onClick = { showVibeCheckDialog = true }, containerColor = Color(0xFF1DB954), shape = CircleShape, modifier = Modifier.size(60.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎵", fontSize = 16.sp)
                            Text("Vibe", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(animatedMoodBrushLight(themeState)).padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Friends", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = primaryText)
                        val onlineCount = friends.count { it.isOnline }
                        if (friends.isNotEmpty()) Text("$onlineCount listening now", fontSize = 12.sp, color = secondaryText)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = "Add Friend",
                            tint = primaryText,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1DB954)) }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {

                        // ── SECTION 1: Mascot Row ──────────────────────
                        if (friends.isNotEmpty() || myMoment != null) {
                            item(key = "mascot_row") {
                                MascotRow(myMascotType = myMascotType, myDisplayName = myDisplayName, myMoment = myMoment, isPlaying = isPlaying, friends = friends, isDark = isDark, onMyTap = { showEditProfile = true }, onFriendTap = { vibeHistoryFriend = it })
                            }
                        }

                        // ── SECTION 2: Your Vibe Card ──────────────────
                        val currentMyMoment = myMoment
                        if (currentMyMoment != null) {
                            item(key = "my_vibe_label") { SectionLabel("Your Vibe", primaryText) }
                            item(key = "my_vibe_card") {
                                MomentCard(moment = currentMyMoment, isOwn = true, primaryText = primaryText, isDark = isDark, onReact = {},
                                    onPlay = { musicPlayerViewModel?.playFromRecommendation(currentMyMoment.trackTitle, currentMyMoment.trackArtist) { _, _ -> } },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        } else if (currentTrack != null) {
                            item(key = "share_vibe_prompt") {
                                ShareVibePrompt(trackTitle = currentTrack.name, isDark = isDark, primaryText = primaryText, onClick = { showVibeCheckDialog = true })
                            }
                        }

                        // ── SECTION 3: Activity Feed ───────────────────
                        if (activityFeed.isNotEmpty()) {
                            item(key = "activity_label") { SectionLabel("What's Happening", primaryText) }
                            // PREFIX "activity_" — keys must be unique across the ENTIRE LazyColumn
                            items(activityFeed, key = { "activity_${it.emoji}_${it.text}" }) { event ->
                                ActivityFeedRow(event, primaryText, secondaryText)
                            }
                        }

                        // ── SECTION 4: Friends' Vibes ──────────────────
                        val withMoments = friends.filter { it.currentMoment != null }
                        if (withMoments.isNotEmpty()) {
                            item(key = "vibes_label") { SectionLabel("Friends' Vibes", primaryText) }
                            // PREFIX "vibe_" — CRITICAL: same UIDs appear in Section 5 with "friend_" prefix
                            // Without different prefixes, Compose sees duplicate keys and crashes on scroll
                            items(withMoments, key = { "vibe_${it.uid}" }) { f ->
                                val moment = f.currentMoment!!
                                MomentCard(
                                    moment = moment, isOwn = false, primaryText = primaryText, isDark = isDark,
                                    onReact = { emoji ->
                                        scope.launch {
                                            socialRepo.reactToMoment(f.uid, emoji)
                                            friends      = socialRepo.getFriendsWithProfiles()
                                            activityFeed = buildActivityFeed(friends, myMoment)
                                        }
                                    },
                                    onPlay = { musicPlayerViewModel?.playFromRecommendation(moment.trackTitle, moment.trackArtist) { _, _ -> } },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // ── SECTION 5: All Friends ─────────────────────
                        item(key = "friends_label") { SectionLabel("All Friends", primaryText) }
                        // PREFIX "friend_" — different from "vibe_" above
                        items(friends, key = { "friend_${it.uid}" }) { f ->
                            FriendRow(friend = f, primaryText = primaryText, secondaryText = secondaryText, onChat = { chatFriend = f }, onRemove = { friendToRemove = f })
                        }

                        if (friends.isEmpty()) {
                            item(key = "empty_state") { EmptyFriendsState(primaryText = primaryText, secondaryText = secondaryText, isDark = isDark, onAdd = { showAddDialog = true }) }
                        }

                        item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }

        AnimatedVisibility(visible = chatFriend != null, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it })) {
            val friend = chatFriend
            if (friend != null) {
                FriendChatPanel(friend = friend, socialRepo = socialRepo, currentTrack = currentTrack, isPlaying = isPlaying, onBack = { chatFriend = null },
                    onPlayFriend = { friend.currentMoment?.let { m -> musicPlayerViewModel?.playFromRecommendation(m.trackTitle, m.trackArtist) { ok, _ -> if (ok) onNavigateToMusicPlayer() } } })
            }
        }

        val historyFriend = vibeHistoryFriend
        if (historyFriend != null) {
            VibeHistorySheet(friend = historyFriend, socialRepo = socialRepo, isDark = isDark, onDismiss = { vibeHistoryFriend = null },
                onPlay = { moment -> musicPlayerViewModel?.playFromRecommendation(moment.trackTitle, moment.trackArtist) { ok, _ -> if (ok) onNavigateToMusicPlayer() } })
        }

        AnimatedVisibility(visible = showQrScanner, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it }), modifier = Modifier.fillMaxSize()) {
            QrScannerScreen(
                onScannedUid = { uid ->
                    showQrScanner = false
                    scope.launch {
                        val doc   = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                        val uname = doc.getString("username")
                        if (uname != null) {
                            socialRepo.addFriend(uname).fold(onSuccess = { refreshAll() }, onFailure = { addError = it.message; showAddDialog = true })
                        } else { addError = "User not found for this QR code"; showAddDialog = true }
                    }
                },
                onBack = { showQrScanner = false }
            )
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────

    if (showEditProfile) {
        EditProfileDialog(currentName = myDisplayName, isDark = isDark, onDismiss = { showEditProfile = false },
            onSave = { newName ->
                scope.launch {
                    try {
                        currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(newName).build())?.await()
                        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(myUid)
                            .update("displayName", newName, "username", newName.lowercase().trim()).await()
                        myDisplayName = newName; showEditProfile = false
                    } catch (e: Exception) { Log.e("FriendsScreen", "Profile update failed", e) }
                }
            })
    }

    if (showVibeCheckDialog) {
        VibeCheckDialog(currentTrack = currentTrack, spotifyMusicRepo = spotifyMusicRepo, isDark = isDark, onDismiss = { showVibeCheckDialog = false },
            onPost = { title, artist, albumArt, spotifyUri, caption, mood ->
                scope.launch {
                    socialRepo.postVibeCheck(title, artist, albumArt, spotifyUri, mood, caption, myMascotType)
                    myMoment = socialRepo.getMyMoment(); activityFeed = buildActivityFeed(friends, myMoment); showVibeCheckDialog = false
                }
            })
    }

    if (showQrDialog) { QrCodeDialog(uid = myUid, onDismiss = { showQrDialog = false }, onScanFriend = { showQrScanner = true }) }

    if (showAddDialog) {
        AddFriendDialog(
            socialRepo = socialRepo, error = addError, isDark = isDark,
            onDismiss  = { showAddDialog = false; addError = null },
            onScanQr   = { showAddDialog = false; showQrScanner = true },
            onShowMyQr = { showAddDialog = false; showQrDialog = true },
            onAdd      = { username -> scope.launch { socialRepo.addFriend(username).fold(onSuccess = { showAddDialog = false; addError = null; refreshAll() }, onFailure = { addError = it.message }) } },
            onAddByUid = { uid ->
                scope.launch {
                    val doc   = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                    val uname = doc.getString("username") ?: return@launch
                    socialRepo.addFriend(uname).fold(onSuccess = { showAddDialog = false; addError = null; refreshAll() }, onFailure = { addError = it.message })
                }
            }
        )
    }

    val removingFriend = friendToRemove
    if (removingFriend != null) {
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Remove Friend", fontWeight = FontWeight.Bold) },
            text  = { Text("Remove ${removingFriend.displayName} from your friends? They won't be notified.") },
            confirmButton = {
                Button(onClick = { scope.launch { socialRepo.removeFriend(removingFriend.uid).fold(onSuccess = { friendToRemove = null; refreshAll() }, onFailure = { friendToRemove = null }) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { friendToRemove = null }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mascot Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MascotRow(
    myMascotType: String, myDisplayName: String, myMoment: MusicMoment?,
    isPlaying: Boolean, friends: List<FriendProfile>, isDark: Boolean,
    onMyTap: () -> Unit, onFriendTap: (FriendProfile) -> Unit
) {
    Column {
        Text("Now Listening", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677),
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "mascot_me") {
                MascotAvatarItem(mascotEmoji = petTypeToEmoji(myMascotType), name = myDisplayName.split(" ").first(),
                    isOnline = isPlaying, isPlaying = isPlaying, hasNewMoment = myMoment != null,
                    accentColor = Color(0xFF1DB954), isDark = isDark, isMe = true, onClick = onMyTap)
            }
            // LazyRow is a separate composition scope — keys only need to be unique within this LazyRow
            items(friends, key = { "mascot_${it.uid}" }) { f ->
                MascotAvatarItem(mascotEmoji = petTypeToEmoji(f.mascotType), name = f.displayName.split(" ").first(),
                    isOnline = f.isOnline, isPlaying = f.currentMoment != null && f.isOnline,
                    hasNewMoment = f.currentMoment != null, accentColor = getMoodColor(f.currentMoment?.mood ?: "neutral"),
                    isDark = isDark, isMe = false, onClick = { onFriendTap(f) }, isListeningTogether = f.listenTogetherSessionId != null)
            }
        }
    }
}

@Composable
private fun MascotAvatarItem(
    mascotEmoji: String, name: String, isOnline: Boolean, isPlaying: Boolean,
    hasNewMoment: Boolean, accentColor: Color, isDark: Boolean,
    isMe: Boolean = false, onClick: () -> Unit, isListeningTogether: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.width(68.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (isPlaying) Box(Modifier.size(66.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)))
            Box(
                modifier = Modifier.size(58.dp)
                    .then(if (hasNewMoment) Modifier.border(2.5.dp, Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.3f))), CircleShape) else Modifier.border(1.dp, if (isDark) Color(0xFF3A3A5A) else Color.LightGray, CircleShape))
                    .padding(3.dp).clip(CircleShape).background(if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5FA)),
                contentAlignment = Alignment.Center
            ) { Text(mascotEmoji, fontSize = 26.sp) }
            if (isOnline) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (isDark) Color(0xFF1C1C2E) else Color.White).align(Alignment.BottomEnd)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4CAF50)).align(Alignment.Center))
                }
            }
            if (isMe) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1A1A2E)).align(Alignment.TopEnd), contentAlignment = Alignment.Center) { Text("✏️", fontSize = 9.sp) }
            if (isListeningTogether) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF7B2FBE)).align(Alignment.TopEnd), contentAlignment = Alignment.Center) { Text("👥", fontSize = 9.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E))
        if (isPlaying) Text("♪", fontSize = 9.sp, color = accentColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Share Vibe Prompt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShareVibePrompt(trackTitle: String, isDark: Boolean, primaryText: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onClick() }, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2A2A3E) else Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1DB954).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Share what you're vibing to!", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                Text(trackTitle, fontSize = 11.sp, color = Color(0xFF666677), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1DB954)) {
                Text("Share", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity feed
// ─────────────────────────────────────────────────────────────────────────────

private data class ActivityEvent(val emoji: String, val text: String, val timeAgo: String)

private fun buildActivityFeed(friends: List<FriendProfile>, myMoment: MusicMoment?): List<ActivityEvent> {
    val events = mutableListOf<ActivityEvent>()
    myMoment?.reactions?.forEach { (_, emoji) -> events += ActivityEvent(emoji, "Someone reacted to your vibe", formatTimeAgo(myMoment.timestamp)) }
    friends.filter { it.currentMoment != null }.sortedByDescending { it.currentMoment!!.timestamp }.take(5).forEach { f ->
        val m = f.currentMoment!!
        events += ActivityEvent("🎵", "${f.displayName.split(" ").first()} is vibing to \"${m.trackTitle}\"", formatTimeAgo(m.timestamp))
    }
    friends.filter { it.listenTogetherSessionId != null }.forEach { f ->
        events += ActivityEvent("👥", "${f.displayName.split(" ").first()} started a Listen Together session", "now")
    }
    return events.take(8)
}

// ─────────────────────────────────────────────────────────────────────────────
// Vibe History Sheet — uses scrollable Column instead of LazyColumn
// to avoid "measure on deactivated node" crash inside Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VibeHistorySheet(
    friend: FriendProfile, socialRepo: SocialRepository, isDark: Boolean,
    onDismiss: () -> Unit, onPlay: (MusicMoment) -> Unit
) {
    var history by remember { mutableStateOf<List<MusicMoment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(friend.uid) { loading = true; history = try { socialRepo.getVibeHistory(friend.uid) } catch (_: Exception) { emptyList() }; loading = false }

    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = if (isDark) Color(0xFF1C1C2E) else Color.White, modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(getMoodColor(friend.currentMoment?.mood ?: "neutral").copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(petTypeToEmoji(friend.mascotType), fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${friend.displayName.split(" ").first()}'s Vibe Story", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryText)
                        Text("Last 24 hours", fontSize = 12.sp, color = secondaryText)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = secondaryText) }
                }
                Spacer(Modifier.height(16.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1DB954)) }
                    history.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(petTypeToEmoji(friend.mascotType), fontSize = 40.sp); Spacer(Modifier.height(8.dp))
                            Text("No vibes shared yet today", color = secondaryText, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                    else -> {
                        // scrollable Column avoids LazyColumn-in-Dialog crash
                        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { moment -> VibeHistoryItem(moment, isDark, primaryText, secondaryText, onPlay) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeHistoryItem(moment: MusicMoment, isDark: Boolean, primaryText: Color, secondaryText: Color, onPlay: (MusicMoment) -> Unit) {
    val moodColor = getMoodColor(moment.mood)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFFF5F5FA))
        .clickable { onPlay(moment) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(moodColor)); Spacer(Modifier.width(10.dp))
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(moodColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            if (moment.albumArtUrl.isNotEmpty()) AsyncImage(model = moment.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("🎵", fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(moment.trackTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(moment.trackArtist, fontSize = 11.sp, color = secondaryText, maxLines = 1)
            Text(formatTimeAgo(moment.timestamp), fontSize = 10.sp, color = moodColor)
        }
        Text(moment.moodEmoji, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QR Code dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QrCodeDialog(uid: String, onDismiss: () -> Unit, onScanFriend: () -> Unit = {}) {
    val qrBitmap = remember(uid) { generateQrBitmap("moodsync://add-friend/$uid", 512) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("My Music Code", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1A1A2E)); Spacer(Modifier.height(4.dp))
                Text("Let friends scan this to add you instantly", fontSize = 12.sp, color = Color(0xFF666677), textAlign = TextAlign.Center); Spacer(Modifier.height(20.dp))
                if (qrBitmap != null) {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                } else {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1DB954)) }
                }
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF5F5FA)) {
                    Text(uid.take(16) + "…", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp, color = Color(0xFF666677), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onDismiss(); onScanFriend() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Scan a Friend's Code")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (_: Exception) { null }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity feed row / Section label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityFeedRow(event: ActivityEvent, primaryText: Color, secondaryText: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1DB954).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Text(event.emoji, fontSize = 14.sp) }
        Spacer(Modifier.width(10.dp))
        Text(event.text, fontSize = 13.sp, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(event.timeAgo, fontSize = 10.sp, color = secondaryText)
    }
}

@Composable
private fun SectionLabel(title: String, primaryText: Color) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Moment card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MomentCard(
    moment: MusicMoment, isOwn: Boolean, primaryText: Color, isDark: Boolean = false,
    onReact: (String) -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier
) {
    var showReactions by remember { mutableStateOf(false) }
    val moodColor = getMoodColor(moment.mood)
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2A2A3E) else Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(moodColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(petTypeToEmoji(moment.userMascotType), fontSize = 20.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isOwn) "You" else moment.userName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = primaryText)
                        Spacer(Modifier.width(6.dp)); Text(moment.moodEmoji, fontSize = 12.sp)
                        if (moment.isVibeCheck) { Spacer(Modifier.width(6.dp)); Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1DB954).copy(alpha = 0.15f)) { Text("VIBE CHECK", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DB954)) } }
                    }
                    Text(formatTimeAgo(moment.timestamp), fontSize = 11.sp, color = Color(0xFF666677))
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(moodColor.copy(alpha = 0.1f), if (isDark) Color(0xFF2A2A3E) else Color(0xFFF8F8FA))))
                .clickable { onPlay() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(moodColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    if (moment.albumArtUrl.isNotEmpty()) AsyncImage(model = moment.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
                    else Text("🎵", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(moment.trackTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = primaryText)
                    Text(moment.trackArtist, fontSize = 12.sp, color = Color(0xFF666677), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(Modifier.size(38.dp), shape = CircleShape, color = moodColor) { Box(contentAlignment = Alignment.Center) { Text("▶", fontSize = 14.sp, color = Color.White) } }
            }
            if (moment.vibeSnapUrl != null) {
                AsyncImage(model = moment.vibeSnapUrl, contentDescription = "Vibe Snap", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
            }
            if (moment.caption.isNotBlank()) Text(moment.caption, Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = primaryText)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                moment.reactions.values.groupBy { it }.forEach { (emoji, list) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF0F0F0), modifier = Modifier.padding(end = 6.dp)) {
                        Text("$emoji ${list.size}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 13.sp, color = primaryText)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!isOwn) IconButton(onClick = { showReactions = !showReactions }, modifier = Modifier.size(32.dp)) { Text("😊", fontSize = 18.sp) }
            }
            androidx.compose.animation.AnimatedVisibility(visible = showReactions) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("🔥", "💖", "🎵", "😍", "🤩", "👏", "😢", "⚡").forEach { emoji ->
                        Surface(shape = CircleShape, color = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF0F0F0),
                            modifier = Modifier.clickable { onReact(emoji); showReactions = false }) { Text(emoji, Modifier.padding(8.dp), fontSize = 20.sp) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Friend row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendRow(
    friend: FriendProfile, primaryText: Color = Color(0xFF1A1A2E),
    secondaryText: Color = Color(0xFF666677), onChat: () -> Unit, onRemove: () -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onChat() }, onLongClick = { onRemove() }).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(getMoodColor(friend.currentMoment?.mood ?: "neutral").copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(petTypeToEmoji(friend.mascotType), fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(friend.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryText)
                if (friend.listenTogetherSessionId != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF7B2FBE).copy(alpha = 0.12f)) {
                        Text("👥 Together", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, color = Color(0xFF7B2FBE), fontWeight = FontWeight.Bold)
                    }
                }
            }
            val moment = friend.currentMoment
            if (moment != null) Text("🎵 ${moment.trackTitle}", fontSize = 11.sp, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(if (friend.lastActive > 0) "Last seen ${formatTimeAgo(friend.lastActive)}" else "Never active", fontSize = 11.sp, color = secondaryText)
            Text("Hold to remove", fontSize = 9.sp, color = secondaryText.copy(alpha = 0.5f))
        }
        Icon(Icons.Filled.ChatBubbleOutline, "Chat", tint = secondaryText, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat panel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendChatPanel(
    friend: FriendProfile, socialRepo: SocialRepository,
    currentTrack: com.example.fypdraft.model.Track?,
    isPlaying: Boolean, onBack: () -> Unit, onPlayFriend: () -> Unit
) {
    val scope     = rememberCoroutineScope()
    val focusMgr  = LocalFocusManager.current
    val listState = rememberLazyListState()
    var messages  by remember { mutableStateOf<List<FriendChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.runtime.DisposableEffect(friend.uid) {
        val reg = socialRepo.listenToChatMessages(friend.uid) { incoming -> messages = incoming; isLoading = false }
        onDispose { reg.remove() }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Scaffold(
        containerColor = Color(0xFFF5F5FA),
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF1A1A2E)) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(getMoodColor(friend.currentMoment?.mood ?: "neutral").copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text(petTypeToEmoji(friend.mascotType), fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(friend.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A2E))
                            Text(if (friend.isOnline) "🟢 Listening now" else "Offline", fontSize = 11.sp, color = Color(0xFF666677))
                        }
                    }
                },
                actions = { if (friend.currentMoment != null) IconButton(onClick = onPlayFriend) { Text("▶🎵", fontSize = 16.sp) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Message ${friend.displayName.split(" ").first()}…", color = Color(0xFF666677)) },
                        shape = RoundedCornerShape(24.dp), maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { val msg = inputText.trim(); if (msg.isNotBlank()) { inputText = ""; focusMgr.clearFocus(); scope.launch { socialRepo.sendChatMessage(friend.uid, msg) } } }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1DB954), unfocusedBorderColor = Color(0xFFDDDDDD)))
                    Spacer(Modifier.width(8.dp))
                    if (currentTrack != null && isPlaying) {
                        IconButton(onClick = { scope.launch { socialRepo.sendSongMessage(friend.uid, currentTrack.name, currentTrack.artist, currentTrack.albumArtUrl, currentTrack.spotifyUri) } },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1DB954))) { Text("🎵", fontSize = 18.sp) }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = { val msg = inputText.trim(); if (msg.isNotBlank()) { inputText = ""; focusMgr.clearFocus(); scope.launch { socialRepo.sendChatMessage(friend.uid, msg) } } },
                        enabled = inputText.isNotBlank(), modifier = Modifier.size(44.dp).clip(CircleShape).background(if (inputText.isNotBlank()) Color(0xFF1A1A2E) else Color(0xFFEEEEEE))) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (inputText.isNotBlank()) Color.White else Color(0xFF666677))
                    }
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1DB954)) }
            messages.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(petTypeToEmoji(friend.mascotType), fontSize = 52.sp); Spacer(Modifier.height(12.dp))
                    Text("Say hi to ${friend.displayName.split(" ").first()}!", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666677)); Spacer(Modifier.height(6.dp))
                    Text("Share what you're listening to 🎵", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                }
            }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(messages, key = { it.id }) { msg -> if (msg.messageType == "song") SongMessageBubble(msg) else ChatMessageBubble(msg) }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(msg: FriendChatMessage) {
    val isMe = msg.isFromMe
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 280.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Box(Modifier.clip(RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(if (isMe) Color(0xFF1DB954) else Color.White).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(msg.text, color = if (isMe) Color.White else Color(0xFF1A1A2E), fontSize = 14.sp, lineHeight = 20.sp)
            }
            Text(formatTimeAgo(msg.timestamp), fontSize = 10.sp, color = Color(0xFFAAAAAA), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun SongMessageBubble(msg: FriendChatMessage) {
    val isMe = msg.isFromMe
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(shape = RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = if (isMe) Color(0xFF1DB954).copy(alpha = 0.15f) else Color.White, shadowElevation = 2.dp) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        if (!msg.songAlbumArt.isNullOrEmpty()) AsyncImage(model = msg.songAlbumArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text("🎵", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(msg.songTitle ?: "Unknown Track", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A2E), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(msg.songArtist ?: "", fontSize = 11.sp, color = Color(0xFF666677), maxLines = 1); Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1DB954)) { Text("▶ Play", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Text(formatTimeAgo(msg.timestamp), fontSize = 10.sp, color = Color(0xFFAAAAAA), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add friend dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddFriendDialog(
    socialRepo: SocialRepository, error: String?, isDark: Boolean,
    onDismiss: () -> Unit, onAdd: (String) -> Unit,
    onAddByUid: (String) -> Unit, onScanQr: () -> Unit, onShowMyQr: () -> Unit = {}
) {
    var username    by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<FriendSuggestion>>(emptyList()) }
    var loadingSugg by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { loadingSugg = true; suggestions = socialRepo.getMutualFriendSuggestions(); loadingSugg = false }

    val bgColor   = if (isDark) Color(0xFF1C1C2E) else Color.White
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFDDDDEE) else Color(0xFF111122)
    val fieldBorderUnfocused = if (isDark) Color(0xFF3A3A5A) else Color(0xFFCCCCCC)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(20.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    "Add a Friend",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = textColor
                )
                Spacer(Modifier.height(16.dp))

                // Scan QR button
                Button(
                    onClick  = onScanQr,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954), contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan a Friend's QR Code", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))

                // Show My QR Code button
                OutlinedButton(
                    onClick  = onShowMyQr,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, subColor.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(16.dp), tint = textColor)
                    Spacer(Modifier.width(6.dp))
                    Text("Show My QR Code", fontSize = 13.sp, color = textColor)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = subColor.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Text("Or search by username", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = subColor)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value         = username,
                    onValueChange = { username = it },
                    placeholder   = { Text("Enter exact username", color = subColor) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier.fillMaxWidth(),
                    leadingIcon   = { Icon(Icons.Filled.Search, null, tint = subColor) },
                    supportingText = {
                        Text(
                            "Usernames are lowercase — e.g. \"johndoe\" not \"JohnDoe\"",
                            fontSize = 11.sp,
                            color    = subColor
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor        = textColor,
                        unfocusedTextColor      = textColor,
                        focusedBorderColor      = Color(0xFF1DB954),
                        unfocusedBorderColor    = fieldBorderUnfocused,
                        focusedLabelColor       = Color(0xFF1DB954),
                        unfocusedLabelColor     = subColor,
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor             = Color(0xFF1DB954)
                    )
                )

                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = Color(0xFFE57373), fontSize = 12.sp)
                }

                if (suggestions.isNotEmpty() || loadingSugg) {
                    Spacer(Modifier.height(16.dp))
                    Text("People you might know", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = subColor)
                    Spacer(Modifier.height(8.dp))
                    when {
                        loadingSugg -> Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF1DB954))
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.take(5).forEach { sug ->
                                SuggestionRow(suggestion = sug, isDark = isDark, textColor = textColor, onAdd = { onAddByUid(sug.uid) })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = subColor, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick  = { if (username.isNotBlank()) onAdd(username.trim().lowercase()) },
                        enabled  = username.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Color(0xFF1DB954),
                            contentColor           = Color.White,
                            disabledContainerColor = Color(0xFF1DB954).copy(alpha = 0.35f),
                            disabledContentColor   = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Add Friend", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: FriendSuggestion, isDark: Boolean = false, textColor: Color = Color(0xFF1A1A2E), onAdd: () -> Unit) {
    val rowBg    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5FA)
    val subColor = if (isDark) Color(0xFFBBBBCC) else Color(0xFF555566)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(rowBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1DB954).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(petTypeToEmoji(suggestion.mascotType), fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(suggestion.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
            val reason = when {
                suggestion.matchedByPhone && suggestion.mutualFriendCount > 0 -> "📱 In your contacts · ${suggestion.mutualFriendCount} mutual"
                suggestion.matchedByPhone  -> "📱 In your contacts"
                suggestion.mutualFriendCount > 0 -> "${suggestion.mutualFriendCount} mutual friend${if (suggestion.mutualFriendCount > 1) "s" else ""}"
                else -> "You might know this person"
            }
            Text(reason, fontSize = 11.sp, color = subColor)
        }
        TextButton(onClick = onAdd, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1DB954)), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vibe check dialog — uses scrollable Column to avoid LazyColumn-in-Dialog crash
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VibeCheckDialog(
    currentTrack: com.example.fypdraft.model.Track?, spotifyMusicRepo: SpotifyMusicRepository?,
    isDark: Boolean, onDismiss: () -> Unit, onPost: (String, String, String, String?, String, String) -> Unit
) {
    val bgColor   = if (isDark) Color(0xFF1C1C2E) else Color.White
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)

    var step          by remember { mutableStateOf(0) }
    var searchQuery   by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.example.fypdraft.model.Track>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf(currentTrack) }
    var caption       by remember { mutableStateOf("") }
    var selectedMood  by remember { mutableStateOf("happy") }
    val moods = listOf("happy" to "😊", "energetic" to "⚡", "calm" to "😌", "sad" to "😢", "focused" to "🎯", "romantic" to "💕")

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) { searchResults = emptyList(); return@LaunchedEffect }
        isSearching = true; kotlinx.coroutines.delay(400)
        searchResults = try { spotifyMusicRepo?.searchTracks(searchQuery, 10)?.distinctBy { it.id } ?: emptyList() } catch (_: Exception) { emptyList() }
        isSearching = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = bgColor, modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step == 1) { IconButton(onClick = { step = 0 }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = subColor) }; Spacer(Modifier.width(4.dp)) }
                    Text(if (step == 0) "Choose a Song to Share 🎵" else "Add Your Vibe ✨", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null, tint = subColor) }
                }
                Spacer(Modifier.height(14.dp))

                if (step == 0) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search for a song…", color = subColor) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = subColor) },
                        trailingIcon = { if (isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1DB954)) },
                        singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1DB954), unfocusedBorderColor = if (isDark) Color(0xFF3A3A5A) else Color(0xFFDDDDDD), focusedTextColor = textColor, unfocusedTextColor = textColor))
                    Spacer(Modifier.height(10.dp))
                    if (currentTrack != null && searchQuery.isBlank()) {
                        Text("Currently Playing", fontSize = 12.sp, color = subColor, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
                        SongPickRow(title = currentTrack.name, artist = currentTrack.artist, albumArtUrl = currentTrack.albumArtUrl, isSelected = selectedTrack?.id == currentTrack.id, isDark = isDark, textColor = textColor, onClick = { selectedTrack = currentTrack })
                        Spacer(Modifier.height(10.dp))
                    }
                    if (searchResults.isNotEmpty()) { Text("Search Results", fontSize = 12.sp, color = subColor, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)) }
                    // scrollable Column — no LazyColumn inside Dialog
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchResults.forEach { track ->
                            SongPickRow(title = track.name, artist = track.artist, albumArtUrl = track.albumArtUrl, isSelected = selectedTrack?.id == track.id, isDark = isDark, textColor = textColor, onClick = { selectedTrack = track })
                        }
                    }
                    if (spotifyMusicRepo == null) Text("Connect Spotify to search songs", fontSize = 13.sp, color = subColor, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { if (selectedTrack != null) step = 1 }, enabled = selectedTrack != null, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))) { Text("Next →", fontWeight = FontWeight.Bold) }

                } else {
                    val t = selectedTrack!!
                    Surface(shape = RoundedCornerShape(12.dp), color = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5F5)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                if (t.albumArtUrl.isNotEmpty()) AsyncImage(model = t.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) else Text("🎵", fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column { Text(t.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, color = textColor); Text(t.artist, fontSize = 12.sp, color = subColor, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(14.dp)); Text("How are you feeling?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        moods.forEach { (mood, emoji) ->
                            Surface(shape = CircleShape, color = if (selectedMood == mood) getMoodColor(mood).copy(alpha = 0.2f) else if (isDark) Color(0xFF2A2A3E) else Color(0xFFF0F0F0),
                                modifier = Modifier.clickable { selectedMood = mood }.then(if (selectedMood == mood) Modifier.border(2.dp, getMoodColor(mood), CircleShape) else Modifier)) { Text(emoji, Modifier.padding(10.dp), fontSize = 20.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = caption, onValueChange = { if (it.length <= 120) caption = it }, placeholder = { Text("Add a caption…", color = subColor) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1DB954), focusedTextColor = textColor, unfocusedTextColor = textColor))
                    Text("${caption.length}/120", fontSize = 10.sp, color = subColor, modifier = Modifier.align(Alignment.End))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { onPost(t.name, t.artist, t.albumArtUrl, t.spotifyUri, caption, selectedMood) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))) { Text("Share Vibe 🎵", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SongPickRow(title: String, artist: String, albumArtUrl: String, isSelected: Boolean, isDark: Boolean, textColor: Color, onClick: () -> Unit) {
    val rowBg = when { isSelected -> Color(0xFF1DB954).copy(alpha = 0.12f); isDark -> Color(0xFF2A2A3E); else -> Color(0xFFF8F8FA) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(rowBg).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            if (albumArtUrl.isNotEmpty()) AsyncImage(model = albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("🎵", fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, fontSize = 11.sp, color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677), maxLines = 1)
        }
        if (isSelected) Icon(Icons.Filled.Check, null, tint = Color(0xFF1DB954), modifier = Modifier.size(18.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Profile dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditProfileDialog(currentName: String, isDark: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name      by remember { mutableStateOf(currentName) }
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column {
                Text("Display Name", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1DB954), focusedTextColor = textColor, unfocusedTextColor = textColor))
                Spacer(Modifier.height(8.dp)); Text("This updates your name across the whole app including the Home screen", fontSize = 12.sp, color = subColor)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank() && name.trim() != currentName, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = subColor) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFriendsState(
    primaryText: Color = Color(0xFF1A1A2E),
    secondaryText: Color = Color(0xFF666677),
    isDark: Boolean = false,
    onAdd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("🐱", "🐶", "🐻", "🐰").forEach { Text(it, fontSize = 36.sp) } }
        Spacer(Modifier.height(16.dp))
        Text("No friends yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
        Spacer(Modifier.height(8.dp))
        Text("Add friends to share music vibes!", fontSize = 14.sp, color = secondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1DB954),
                contentColor   = Color.White
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a friend", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun petTypeToEmoji(mascotType: String): String = when (mascotType.uppercase()) {
    "CAT" -> "🐱"; "DOG" -> "🐶"; "BEAR" -> "🐻"; "BUNNY" -> "🐰"; else -> "🐱"
}

private fun getMoodColor(mood: String): Color = when (mood) {
    "happy" -> Color(0xFFFFB347); "sad" -> Color(0xFF667EEA); "calm" -> Color(0xFF89CFF0)
    "energetic" -> Color(0xFFFF416C); "tired" -> Color(0xFF607D8B); "focused" -> Color(0xFF11998E); "romantic" -> Color(0xFFEE9CA7)
    else -> Color(0xFF1DB954)
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    return when { diff < 60_000 -> "just now"; diff < 3_600_000 -> "${diff / 60_000}m ago"; diff < 86_400_000 -> "${diff / 3_600_000}h ago"; else -> "${diff / 86_400_000}d ago" }
}