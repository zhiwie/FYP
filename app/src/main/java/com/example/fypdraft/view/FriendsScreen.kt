package com.example.fypdraft.view

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
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
                FriendChatPanel(
                    friend               = friend,
                    socialRepo           = socialRepo,
                    currentTrack         = currentTrack,
                    isPlaying            = isPlaying,
                    themeState           = themeState,
                    onBack               = { chatFriend = null },
                    onPlayFriend         = { friend.currentMoment?.let { m -> musicPlayerViewModel?.playFromRecommendation(m.trackTitle, m.trackArtist) { ok, _ -> if (ok) onNavigateToMusicPlayer() } } },
                    spotifyMusicRepo     = spotifyMusicRepo,
                    musicPlayerViewModel = musicPlayerViewModel
                )
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
// Chat panel — rich version with voice input, song search, mood share
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendChatPanel(
    friend               : FriendProfile,
    socialRepo           : SocialRepository,
    currentTrack         : com.example.fypdraft.model.Track?,
    isPlaying            : Boolean,
    themeState           : AppThemeState = AppThemeState(),
    onBack               : () -> Unit,
    onPlayFriend         : () -> Unit,
    spotifyMusicRepo     : com.example.fypdraft.data.repository.SpotifyMusicRepository? = null,
    musicPlayerViewModel : MusicPlayerViewModel? = null
) {
    val scope     = rememberCoroutineScope()
    val focusMgr  = LocalFocusManager.current
    val listState = rememberLazyListState()
    var messages  by remember { mutableStateOf<List<FriendChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Attachment panel state
    var showAttachPanel   by remember { mutableStateOf(false) }
    var showSongSearch    by remember { mutableStateOf(false) }
    var showMoodPicker    by remember { mutableStateOf(false) }
    var showMoodHistory   by remember { mutableStateOf(false) }
    var songQuery         by remember { mutableStateOf("") }
    var songResults       by remember { mutableStateOf<List<com.example.fypdraft.model.Track>>(emptyList()) }
    var isSongSearching   by remember { mutableStateOf(false) }
    // Mood history — loaded lazily when the chip is tapped
    var moodHistoryLoading by remember { mutableStateOf(false) }
    var moodHistoryData    by remember { mutableStateOf<com.example.fypdraft.data.repository.MoodAnalytics?>(null) }
    val moodHistoryRepo    = remember { com.example.fypdraft.data.repository.MoodHistoryRepository() }

    val isDark = themeState.isDark
    val speed  = themeState.transitionSpeed
    val p      = themeState.activePalette

    // Animated colours — all transition with the mood theme
    val accent by androidx.compose.animation.animateColorAsState(p.accent, androidx.compose.animation.core.tween(speed), label = "acc")
    val topBarBg by androidx.compose.animation.animateColorAsState(
        if (isDark) p.darkTop.copy(0.97f) else p.accent.copy(0.92f), androidx.compose.animation.core.tween(speed), label = "top"
    )
    val bottomBarBg by androidx.compose.animation.animateColorAsState(
        if (isDark) p.darkBottom.copy(0.97f) else p.lightBottom.copy(0.97f), androidx.compose.animation.core.tween(speed), label = "btm"
    )
    val inputBg by androidx.compose.animation.animateColorAsState(
        if (isDark) Color(0xFF22223A) else Color(0xFFFFFFFF), androidx.compose.animation.core.tween(speed), label = "ibg"
    )
    val inputTextColor = if (isDark) Color(0xFFEEEEFF) else Color(0xFF111122)
    val inputHint      = if (isDark) Color(0xFF8888AA) else Color(0xFF888899)
    val inputBorder by androidx.compose.animation.animateColorAsState(
        if (isDark) Color(0xFF4A4A6A) else Color(0xFFBBBBCC), androidx.compose.animation.core.tween(speed), label = "brd"
    )
    val bubbleMeBg by androidx.compose.animation.animateColorAsState(accent, androidx.compose.animation.core.tween(speed), label = "bme")
    val bubbleFriendBg by androidx.compose.animation.animateColorAsState(
        if (isDark) Color(0xFF2C2C44) else Color(0xFFFFFFFF), androidx.compose.animation.core.tween(speed), label = "bfr"
    )
    val bubbleFriendText by androidx.compose.animation.animateColorAsState(
        if (isDark) Color(0xFFE8E8F4) else Color(0xFF111122), androidx.compose.animation.core.tween(speed), label = "bft"
    )
    val emptyColor by androidx.compose.animation.animateColorAsState(
        if (isDark) Color(0xFFAAAAAA) else Color(0xFF444455), androidx.compose.animation.core.tween(speed), label = "emp"
    )

    // Voice input
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { inputText = it }
    }
    fun launchVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say something to your friend…")
        }
        try { voiceLauncher.launch(intent) } catch (_: Exception) {}
    }

    fun sendText() {
        val msg = inputText.trim()
        if (msg.isNotBlank()) {
            inputText = ""
            focusMgr.clearFocus()
            scope.launch { socialRepo.sendChatMessage(friend.uid, msg) }
        }
    }

    // Real-time message listener
    androidx.compose.runtime.DisposableEffect(friend.uid) {
        val reg = socialRepo.listenToChatMessages(friend.uid) { msgs -> messages = msgs; isLoading = false }
        onDispose { reg.remove() }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    // Debounced song search
    LaunchedEffect(songQuery) {
        if (songQuery.length < 2) { songResults = emptyList(); return@LaunchedEffect }
        isSongSearching = true
        kotlinx.coroutines.delay(400)
        songResults = spotifyMusicRepo?.searchTracks(songQuery, 6) ?: emptyList()
        isSongSearching = false
    }

    // Mood picker dialog
    if (showMoodPicker) {
        val moods = listOf(
            "happy" to "😊", "calm" to "😌", "sad" to "😢", "energetic" to "⚡",
            "romantic" to "💕", "focused" to "🎯", "stressed" to "😣", "tired" to "😴",
            "nervous" to "😰", "neutral" to "😐"
        )
        val dialogBg   = if (isDark) Color(0xFF1C1C2E) else Color.White
        val dialogText = if (isDark) Color(0xFFE8E8F0) else Color(0xFF111122)
        AlertDialog(
            onDismissRequest = { showMoodPicker = false },
            containerColor   = dialogBg,
            shape            = RoundedCornerShape(20.dp),
            title = { Text("Share your mood", fontWeight = FontWeight.Bold, color = dialogText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    moods.forEach { (moodKey, emoji) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch { socialRepo.sendMoodMessage(friend.uid, moodKey, emoji) }
                                    showMoodPicker  = false
                                    showAttachPanel = false
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(moodKey.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = dialogText)
                        }
                    }
                }
            },
            confirmButton  = {},
            dismissButton  = { TextButton(onClick = { showMoodPicker = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor      = Color.Transparent,
        contentWindowInsets = WindowInsets.ime,   // keyboard lifts bottom bar cleanly
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(0.25f)), contentAlignment = Alignment.Center) {
                            Text(petTypeToEmoji(friend.mascotType), fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(friend.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text(if (friend.isOnline) "🟢 Listening now" else "Offline", fontSize = 11.sp, color = Color.White.copy(0.75f))
                        }
                    }
                },
                actions = {
                    if (friend.currentMoment != null) {
                        IconButton(onClick = onPlayFriend) { Text("▶🎵", fontSize = 16.sp) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
            )
        },
        bottomBar = {
            Column {
                // ── Attach panel (slides up above input) ──────────────────
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAttachPanel,
                    enter   = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220)),
                    exit    = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(180))
                ) {
                    Surface(color = bottomBarBg, shadowElevation = 4.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {

                            // Quick-share currently playing track
                            if (currentTrack != null && isPlaying) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent.copy(0.12f))
                                        .clickable {
                                            scope.launch { socialRepo.sendSongMessage(friend.uid, currentTrack.name, currentTrack.artist, currentTrack.albumArtUrl, currentTrack.spotifyUri) }
                                            showAttachPanel = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎵", fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Share current song", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF111122))
                                        Text("${currentTrack.name} · ${currentTrack.artist}", fontSize = 11.sp, color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Filled.Send, null, tint = accent, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            // Chip row — Find song + My mood + Mood Stats
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    AttachChip(emoji = "🔍", label = "Find song", accent = accent, isDark = isDark) {
                                        showSongSearch  = !showSongSearch
                                        showMoodHistory = false
                                    }
                                }
                                item {
                                    AttachChip(emoji = "😊", label = "My mood", accent = accent, isDark = isDark) {
                                        showMoodPicker = true
                                    }
                                }
                                item {
                                    AttachChip(
                                        emoji  = if (moodHistoryLoading) "⏳" else "📊",
                                        label  = "Mood stats",
                                        accent = accent,
                                        isDark = isDark
                                    ) {
                                        showMoodHistory = !showMoodHistory
                                        showSongSearch  = false
                                        if (moodHistoryData == null && !moodHistoryLoading) {
                                            scope.launch {
                                                moodHistoryLoading = true
                                                val entries = moodHistoryRepo.getMoodHistoryForDays(30)
                                                moodHistoryData = moodHistoryRepo.computeAnalytics(entries)
                                                moodHistoryLoading = false
                                            }
                                        }
                                    }
                                }
                            }

                            // Mood history preview panel
                            androidx.compose.animation.AnimatedVisibility(visible = showMoodHistory) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    val data = moodHistoryData
                                    if (moodHistoryLoading) {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                                                Text("Loading your mood report…", fontSize = 12.sp, color = inputHint)
                                            }
                                        }
                                    } else if (data == null || data.totalEntries == 0) {
                                        Text("No mood data yet — start checking in daily!", fontSize = 13.sp, color = inputHint, modifier = Modifier.padding(8.dp))
                                    } else {
                                        // Mini mood analytics card
                                        val moodEmojis = mapOf(
                                            "happy" to "😊", "calm" to "😌", "sad" to "😢", "energetic" to "⚡",
                                            "focused" to "🎯", "tired" to "😴", "romantic" to "💕", "stressed" to "😣",
                                            "angry" to "😤", "anxious" to "😰", "nostalgic" to "💭", "neutral" to "🎵"
                                        )
                                        val domEmoji   = moodEmojis[data.dominantMood] ?: "🎵"
                                        val trendEmoji = when (data.recentTrend) { "improving" -> "📈"; "declining" -> "📉"; else -> "➡️" }
                                        val topMoodsStr = data.moodDistribution.entries
                                            .sortedByDescending { it.value }.take(3)
                                            .joinToString(",") { (m, c) ->
                                                val pct = if (data.totalEntries > 0) (c * 100f / data.totalEntries).toInt() else 0
                                                "$m:$pct"
                                            }

                                        Surface(
                                            shape  = RoundedCornerShape(16.dp),
                                            color  = if (isDark) Color(0xFF1E1E3A) else Color(0xFFF5F5FF),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(domEmoji, fontSize = 28.sp)
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text("30-day report", fontSize = 11.sp, color = if (isDark) Color(0xFF9999AA) else Color(0xFF666677))
                                                        Text(
                                                            "Mostly ${data.dominantMood.replaceFirstChar { it.uppercase() }} $trendEmoji",
                                                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                                            color = if (isDark) Color.White else Color(0xFF111122)
                                                        )
                                                    }
                                                    Text("${data.totalEntries} entries", fontSize = 11.sp, color = accent, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                // Top 3 mood bars
                                                data.moodDistribution.entries.sortedByDescending { it.value }.take(3).forEach { (mood, count) ->
                                                    val pct = if (data.totalEntries > 0) count.toFloat() / data.totalEntries else 0f
                                                    val emoji = moodEmojis[mood] ?: "🎵"
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                                        Text("$emoji ${mood.replaceFirstChar { it.uppercase() }}", fontSize = 12.sp, modifier = Modifier.width(90.dp), color = if (isDark) Color(0xFFDDDDEE) else Color(0xFF333344))
                                                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f))) {
                                                            Box(Modifier.height(6.dp).fillMaxWidth(pct).clip(RoundedCornerShape(3.dp)).background(accent))
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("${(pct * 100).toInt()}%", fontSize = 11.sp, color = if (isDark) Color(0xFF9999AA) else Color(0xFF666677))
                                                    }
                                                }
                                                if (data.streaks.currentStreak > 0) {
                                                    Spacer(Modifier.height(6.dp))
                                                    Text("🔥 ${data.streaks.currentStreak}-day check-in streak", fontSize = 12.sp, color = Color(0xFFFF6B35), fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            socialRepo.sendMoodHistoryMessage(
                                                                friend.uid,
                                                                data.dominantMood, domEmoji,
                                                                data.recentTrend, topMoodsStr,
                                                                data.streaks.currentStreak,
                                                                data.totalEntries, "30 days"
                                                            )
                                                        }
                                                        showMoodHistory = false
                                                        showAttachPanel = false
                                                    },
                                                    colors  = ButtonDefaults.buttonColors(containerColor = accent),
                                                    shape   = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Share this report", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Song search field + results
                            androidx.compose.animation.AnimatedVisibility(visible = showSongSearch) {
                                Column {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value         = songQuery,
                                        onValueChange = { songQuery = it },
                                        placeholder   = { Text("Search any song…", color = inputHint) },
                                        singleLine    = true,
                                        shape         = RoundedCornerShape(12.dp),
                                        modifier      = Modifier.fillMaxWidth(),
                                        leadingIcon   = { Icon(Icons.Filled.Search, null, tint = inputHint) },
                                        trailingIcon  = {
                                            if (isSongSearching)
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                                            else if (songQuery.isNotBlank())
                                                IconButton(onClick = { songQuery = ""; songResults = emptyList() }) {
                                                    Icon(Icons.Filled.Close, null, tint = inputHint, modifier = Modifier.size(18.dp))
                                                }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accent, unfocusedBorderColor = inputBorder,
                                            focusedTextColor = inputTextColor, unfocusedTextColor = inputTextColor,
                                            focusedContainerColor = inputBg, unfocusedContainerColor = inputBg,
                                            focusedPlaceholderColor = inputHint, unfocusedPlaceholderColor = inputHint
                                        )
                                    )
                                    if (songResults.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        songResults.take(5).forEach { track ->
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        scope.launch { socialRepo.sendSongMessage(friend.uid, track.name, track.artist, track.albumArtUrl, track.spotifyUri) }
                                                        songQuery = ""; songResults = emptyList()
                                                        showSongSearch = false; showAttachPanel = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(accent.copy(0.15f)), contentAlignment = Alignment.Center) {
                                                    if (track.albumArtUrl.isNotEmpty())
                                                        AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
                                                    else Text("🎵", fontSize = 16.sp)
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color.White else Color(0xFF111122), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(track.artist, fontSize = 11.sp, color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677), maxLines = 1)
                                                }
                                                Icon(Icons.Filled.Send, null, tint = accent.copy(0.7f), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Main input bar ─────────────────────────────────────────
                Box(Modifier.fillMaxWidth().background(bottomBarBg).navigationBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Attachment toggle (+/×)
                        IconButton(
                            onClick  = { showAttachPanel = !showAttachPanel },
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(if (showAttachPanel) accent.copy(0.25f) else inputBorder.copy(0.3f))
                        ) {
                            Icon(
                                if (showAttachPanel) Icons.Filled.Close else Icons.Filled.Add,
                                null, tint = if (showAttachPanel) accent else inputHint
                            )
                        }
                        Spacer(Modifier.width(6.dp))

                        // Text input
                        OutlinedTextField(
                            value         = inputText,
                            onValueChange = { inputText = it },
                            modifier      = Modifier.weight(1f),
                            placeholder   = { Text("Message ${friend.displayName.split(" ").first()}…", color = inputHint) },
                            shape         = RoundedCornerShape(24.dp),
                            maxLines      = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendText() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor        = accent,
                                unfocusedBorderColor      = inputBorder,
                                focusedTextColor          = inputTextColor,
                                unfocusedTextColor        = inputTextColor,
                                cursorColor               = accent,
                                focusedContainerColor     = inputBg,
                                unfocusedContainerColor   = inputBg,
                                focusedPlaceholderColor   = inputHint,
                                unfocusedPlaceholderColor = inputHint
                            )
                        )
                        Spacer(Modifier.width(6.dp))

                        // Mic (empty) / Send (has text)
                        if (inputText.isBlank()) {
                            IconButton(
                                onClick  = { launchVoice() },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(inputBorder.copy(0.3f))
                            ) {
                                Icon(Icons.Filled.Mic, "Voice input", tint = inputHint)
                            }
                        } else {
                            IconButton(
                                onClick  = { sendText() },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(accent)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(animatedMoodBrushLight(themeState)).padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent)
                }
                messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(petTypeToEmoji(friend.mascotType), fontSize = 52.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Say hi to ${friend.displayName.split(" ").first()}!", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = emptyColor)
                        Spacer(Modifier.height(6.dp))
                        Text("Share songs, moods, or just chat 🎵", fontSize = 13.sp, color = emptyColor.copy(0.7f))
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        when (msg.messageType) {
                            "song"         -> FriendSongBubble(msg, isDark, accent, musicPlayerViewModel)
                            "mood"         -> FriendMoodBubble(msg, isDark, bubbleMeBg, bubbleFriendBg, bubbleFriendText)
                            "mood_history" -> FriendMoodHistoryBubble(msg, isDark, accent, bubbleMeBg, bubbleFriendBg, bubbleFriendText)
                            else           -> FriendTextBubble(msg, bubbleMeBg, bubbleFriendBg, bubbleFriendText)
                        }
                    }
                }
            }
        }
    }
}

// ── Attach chip ───────────────────────────────────────────────────────────────

@Composable
private fun AttachChip(emoji: String, label: String, accent: Color, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.06f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color(0xFFDDDDEE) else Color(0xFF333344))
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────────────────

@Composable
private fun FriendTextBubble(
    msg: FriendChatMessage,
    bubbleMeBg: Color,
    bubbleFriendBg: Color,
    bubbleFriendText: Color
) {
    val isMe = msg.isFromMe
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 280.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(if (isMe) bubbleMeBg else bubbleFriendBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, color = if (isMe) Color.White else bubbleFriendText, fontSize = 14.sp, lineHeight = 20.sp)
            }
            FriendTimestamp(msg.timestamp)
        }
    }
}

@Composable
private fun FriendSongBubble(
    msg: FriendChatMessage,
    isDark: Boolean,
    accent: Color,
    musicPlayerViewModel: MusicPlayerViewModel? = null
) {
    val isMe      = msg.isFromMe
    val bg        = if (isMe) accent.copy(0.15f) else if (isDark) Color(0xFF2C2C3E) else Color.White
    val titleCol  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subCol    = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    var loading   by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = bg, shadowElevation = 2.dp
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(0.2f)), contentAlignment = Alignment.Center) {
                        if (!msg.songAlbumArt.isNullOrEmpty())
                            AsyncImage(model = msg.songAlbumArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text("🎵", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(msg.songTitle ?: "Unknown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = titleCol, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(msg.songArtist ?: "", fontSize = 11.sp, color = subCol, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape    = RoundedCornerShape(8.dp),
                            color    = accent,
                            modifier = Modifier.clickable(enabled = !loading) {
                                if (musicPlayerViewModel != null && msg.songTitle != null) {
                                    loading = true
                                    musicPlayerViewModel.playFromRecommendation(msg.songTitle, msg.songArtist ?: "") { _, _ -> loading = false }
                                }
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (loading) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Color.White)
                                else Text("▶", fontSize = 10.sp, color = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text(if (loading) "Loading…" else "Play", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            FriendTimestamp(msg.timestamp)
        }
    }
}

@Composable
private fun FriendMoodBubble(
    msg: FriendChatMessage,
    isDark: Boolean,
    bubbleMeBg: Color,
    bubbleFriendBg: Color,
    bubbleFriendText: Color
) {
    val isMe     = msg.isFromMe
    val emoji    = msg.moodEmoji ?: "😊"
    val moodName = (msg.mood ?: "neutral").replaceFirstChar { it.uppercase() }
    val note     = msg.moodNote?.takeIf { it.isNotBlank() }
    val bg       = if (isMe) bubbleMeBg.copy(0.85f) else bubbleFriendBg

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 240.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = bg, shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Feeling $moodName", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (isMe) Color.White else bubbleFriendText)
                            if (note != null)
                                Text(note, fontSize = 12.sp, color = if (isMe) Color.White.copy(0.8f) else bubbleFriendText.copy(0.7f))
                        }
                    }
                }
            }
            FriendTimestamp(msg.timestamp)
        }
    }
}

@Composable
private fun FriendMoodHistoryBubble(
    msg: FriendChatMessage,
    isDark: Boolean,
    accent: Color,
    bubbleMeBg: Color,
    bubbleFriendBg: Color,
    bubbleFriendText: Color
) {
    val isMe        = msg.isFromMe
    val domMood     = msg.mhDominantMood ?: "neutral"
    val domEmoji    = msg.mhDominantEmoji ?: "🎵"
    val trend       = msg.mhTrend ?: "stable"
    val rangeLabel  = msg.mhRangeLabel ?: "30 days"
    val totalEntries = msg.mhTotalEntries ?: 0
    val streak      = msg.mhStreak ?: 0
    val trendEmoji  = when (trend) { "improving" -> "📈"; "declining" -> "📉"; else -> "➡️" }

    // Parse "happy:45,calm:30,sad:25"
    val topMoods: List<Pair<String, Int>> = msg.mhTopMoods
        ?.split(",")?.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0].trim() to (parts[1].trim().toIntOrNull() ?: 0) else null
        } ?: emptyList()

    val moodEmojis = mapOf(
        "happy" to "😊", "calm" to "😌", "sad" to "😢", "energetic" to "⚡",
        "focused" to "🎯", "tired" to "😴", "romantic" to "💕", "stressed" to "😣",
        "angry" to "😤", "anxious" to "😰", "nostalgic" to "💭", "neutral" to "🎵"
    )

    val cardBg    = if (isMe) bubbleMeBg.copy(0.12f) else if (isDark) Color(0xFF1E1E3A) else Color(0xFFF0F0FA)
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF111122)
    val subColor  = if (isDark) Color(0xFF9999AA) else Color(0xFF666677)

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = cardBg,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.padding(14.dp).widthIn(min = 220.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mood report · $rangeLabel", fontSize = 11.sp, color = subColor)
                            Text(
                                "$domEmoji Mostly ${domMood.replaceFirstChar { it.uppercase() }} $trendEmoji",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor
                            )
                        }
                        if (totalEntries > 0) {
                            Text("$totalEntries check-ins", fontSize = 10.sp, color = accent, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (topMoods.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        topMoods.forEach { (mood, pct) ->
                            val emoji = moodEmojis[mood] ?: "🎵"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("$emoji ${mood.replaceFirstChar { it.uppercase() }}", fontSize = 11.sp, modifier = Modifier.width(88.dp), color = textColor)
                                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.07f))) {
                                    Box(Modifier.height(5.dp).fillMaxWidth(pct / 100f).clip(RoundedCornerShape(3.dp)).background(accent.copy(if (isMe) 0.85f else 0.75f)))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("$pct%", fontSize = 10.sp, color = subColor)
                            }
                        }
                    }

                    if (streak > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("🔥 $streak-day streak", fontSize = 11.sp, color = Color(0xFFFF6B35), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            FriendTimestamp(msg.timestamp)
        }
    }
}

@Composable
private fun FriendTimestamp(timestamp: Long) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(0.18f), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(formatTimeAgo(timestamp), fontSize = 10.sp, color = Color.White.copy(0.85f), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
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