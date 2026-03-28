package com.example.fypdraft.view

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.FriendProfile
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
    val scope = rememberCoroutineScope()
    val socialRepo = remember { SocialRepository() }

    var friends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var myMoment by remember { mutableStateOf<MusicMoment?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showVibeCheckDialog by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    // Load data
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            friends = socialRepo.getFriendsWithProfiles()
            myMoment = socialRepo.getMyMoment()
        } catch (e: Exception) {
            Log.e("FriendsScreen", "Load failed", e)
        }
        isLoading = false
    }

    // Auto-share what user is currently playing
    val playerState = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack
    val isPlaying = playerState?.value?.isPlaying ?: false

    LaunchedEffect(currentTrack?.id, isPlaying) {
        if (currentTrack != null && isPlaying) {
            socialRepo.shareNowPlaying(
                trackTitle = currentTrack.name,
                trackArtist = currentTrack.artist,
                albumArtUrl = currentTrack.albumArtUrl,
                spotifyUri = currentTrack.spotifyUri,
                mood = "neutral"
            )
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(currentTab, onNavigateToHome, onNavigateToSearch, {}, onNavigateToLibrary)
        },
        floatingActionButton = {
            // Vibe Check button (like BeReal's capture button)
            if (currentTrack != null) {
                FloatingActionButton(
                    onClick = { showVibeCheckDialog = true },
                    containerColor = Color(0xFF1DB954),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 18.sp)
                        Text("Vibe!", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
            // ── Header ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Friends", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    val onlineCount = friends.count { it.isOnline }
                    if (friends.isNotEmpty()) {
                        Text("$onlineCount listening now", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.PersonAdd, "Add", tint = Color.Black)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // ── Story-style friend circles ───────────────────
                    if (friends.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // "Your vibe" circle
                                item {
                                    StoryCircle(
                                        name = "You",
                                        initial = "Me",
                                        isOnline = isPlaying,
                                        hasNewMoment = myMoment != null,
                                        accentColor = Color(0xFF1DB954),
                                        onClick = { if (currentTrack != null) showVibeCheckDialog = true }
                                    )
                                }
                                items(friends) { friend ->
                                    StoryCircle(
                                        name = friend.displayName.split(" ").first(),
                                        initial = friend.displayName.take(1).uppercase(),
                                        isOnline = friend.isOnline,
                                        hasNewMoment = friend.currentMoment != null,
                                        accentColor = getMoodColor(friend.currentMoment?.mood ?: "neutral"),
                                        onClick = { /* scroll to their card */ }
                                    )
                                }
                            }
                        }
                    }

                    // ── My current moment card ───────────────────────
                    if (myMoment != null) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Your Vibe", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                            MomentCard(
                                moment = myMoment!!,
                                isOwn = true,
                                onReact = {},
                                onPlay = {},
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ── Friends' moments feed ────────────────────────
                    val friendsWithMoments = friends.filter { it.currentMoment != null }
                    if (friendsWithMoments.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Friends' Vibes", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                        items(friendsWithMoments, key = { it.uid }) { friend ->
                            MomentCard(
                                moment = friend.currentMoment!!,
                                isOwn = false,
                                onReact = { emoji ->
                                    scope.launch {
                                        socialRepo.reactToMoment(friend.uid, emoji)
                                        // Refresh
                                        friends = socialRepo.getFriendsWithProfiles()
                                    }
                                },
                                onPlay = {
                                    friend.currentMoment?.let { m ->
                                        musicPlayerViewModel?.playFromRecommendation(
                                            songTitle = m.trackTitle,
                                            songArtist = m.trackArtist
                                        ) { success, _ ->
                                            if (success) onNavigateToMusicPlayer()
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ── Friends without moments ──────────────────────
                    val friendsIdle = friends.filter { it.currentMoment == null }
                    if (friendsIdle.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Offline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.Gray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                        items(friendsIdle, key = { it.uid }) { friend ->
                            IdleFriendRow(friend)
                        }
                    }

                    // ── Empty state ──────────────────────────────────
                    if (friends.isEmpty()) {
                        item { EmptyFriendsState(onAdd = { showAddDialog = true }) }
                    }

                    // ── Invite card ──────────────────────────────────
                    item {
                        Spacer(Modifier.height(16.dp))
                        InviteCard()
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // ── Vibe Check dialog ────────────────────────────────────────────
    if (showVibeCheckDialog && currentTrack != null) {
        VibeCheckDialog(
            trackTitle = currentTrack.name,
            trackArtist = currentTrack.artist,
            albumArtUrl = currentTrack.albumArtUrl,
            onDismiss = { showVibeCheckDialog = false },
            onPost = { caption, mood ->
                scope.launch {
                    socialRepo.postVibeCheck(
                        trackTitle = currentTrack.name,
                        trackArtist = currentTrack.artist,
                        albumArtUrl = currentTrack.albumArtUrl,
                        spotifyUri = currentTrack.spotifyUri,
                        mood = mood,
                        caption = caption
                    )
                    myMoment = socialRepo.getMyMoment()
                    showVibeCheckDialog = false
                }
            }
        )
    }

    // ── Add friend dialog ────────────────────────────────────────────
    if (showAddDialog) {
        AddFriendDialog(
            error = addError,
            onDismiss = { showAddDialog = false; addError = null },
            onAdd = { username ->
                scope.launch {
                    socialRepo.addFriend(username).fold(
                        onSuccess = {
                            showAddDialog = false; addError = null
                            friends = socialRepo.getFriendsWithProfiles()
                        },
                        onFailure = { addError = it.message }
                    )
                }
            }
        )
    }
}

// ── Story circle (like Instagram/BeReal top row) ─────────────────────────

@Composable
private fun StoryCircle(
    name: String,
    initial: String,
    isOnline: Boolean,
    hasNewMoment: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.width(64.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .then(
                        if (hasNewMoment) Modifier.border(2.5.dp, Brush.linearGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.5f))
                        ), CircleShape)
                        else Modifier.border(1.dp, Color.LightGray, CircleShape)
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
            }
            if (isOnline) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(Color.White)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4CAF50)).align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 11.sp, color = Color.Black, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ── Music Moment Card (BeReal-style) ─────────────────────────────────────

@Composable
private fun MomentCard(
    moment: MusicMoment,
    isOwn: Boolean,
    onReact: (String) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReactions by remember { mutableStateOf(false) }
    val moodColor = getMoodColor(moment.mood)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Header: name + time + mood
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(moodColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(moment.userName.take(1).uppercase(), fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = moodColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isOwn) "You" else moment.userName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(moment.moodEmoji, fontSize = 13.sp)
                        if (moment.isVibeCheck) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1DB954).copy(alpha = 0.15f)) {
                                Text("VIBE CHECK", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DB954))
                            }
                        }
                    }
                    Text(formatTimeAgo(moment.timestamp), fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Album art + track info (the "moment")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(moodColor.copy(alpha = 0.1f), Color(0xFFF8F8FA))))
                    .clickable { onPlay() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                if (moment.albumArtUrl.isNotEmpty()) {
                    AsyncImage(
                        model = moment.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                            .background(moodColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🎵", fontSize = 26.sp) }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(moment.trackTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(moment.trackArtist, fontSize = 13.sp, color = Color.Gray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Play button
                Surface(
                    Modifier.size(40.dp).clickable { onPlay() },
                    shape = CircleShape,
                    color = moodColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("▶", fontSize = 16.sp, color = Color.White)
                    }
                }
            }

            // Caption
            if (moment.caption.isNotBlank()) {
                Text(
                    moment.caption,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 14.sp, color = Color.Black
                )
            }

            // Reactions row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show existing reactions
                if (moment.reactions.isNotEmpty()) {
                    moment.reactions.values.groupBy { it }.forEach { (emoji, list) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF0F0F0),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("$emoji ${list.size}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // React button (not on own moments)
                if (!isOwn) {
                    IconButton(onClick = { showReactions = !showReactions }, modifier = Modifier.size(32.dp)) {
                        Text("😊", fontSize = 18.sp)
                    }
                }
            }

            // Reaction picker
            AnimatedVisibility(visible = showReactions) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("🔥", "💖", "🎵", "😍", "🤩", "👏", "😢", "⚡").forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0F0F0),
                            modifier = Modifier.clickable {
                                onReact(emoji)
                                showReactions = false
                            }
                        ) {
                            Text(emoji, Modifier.padding(8.dp), fontSize = 20.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Idle friend row ──────────────────────────────────────────────────────

@Composable
private fun IdleFriendRow(friend: FriendProfile) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center
        ) {
            Text(friend.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                if (friend.lastActive > 0) "Last seen ${formatTimeAgo(friend.lastActive)}" else "No activity yet",
                fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

// ── Vibe Check dialog ────────────────────────────────────────────────────

@Composable
private fun VibeCheckDialog(
    trackTitle: String,
    trackArtist: String,
    albumArtUrl: String,
    onDismiss: () -> Unit,
    onPost: (caption: String, mood: String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("happy") }
    val moods = listOf("happy" to "😊", "energetic" to "⚡", "calm" to "😌",
        "sad" to "😢", "focused" to "🎯", "romantic" to "💕")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Your Vibe 🎵", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Track preview
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🎵", fontSize = 20.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(trackTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            Text(trackArtist, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Mood picker
                Text("How are you feeling?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    moods.forEach { (mood, emoji) ->
                        Surface(
                            shape = CircleShape,
                            color = if (selectedMood == mood) getMoodColor(mood).copy(alpha = 0.2f) else Color(0xFFF0F0F0),
                            modifier = Modifier.clickable { selectedMood = mood }
                                .then(if (selectedMood == mood) Modifier.border(2.dp, getMoodColor(mood), CircleShape) else Modifier)
                        ) {
                            Text(emoji, Modifier.padding(10.dp), fontSize = 20.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Caption
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 120) caption = it },
                    placeholder = { Text("Add a caption...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    singleLine = false
                )
                Text("${caption.length}/120", fontSize = 10.sp, color = Color.Gray,
                    modifier = Modifier.align(Alignment.End))
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(caption, selectedMood) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
            ) { Text("Share Vibe 🎵") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Empty state ──────────────────────────────────────────────────────────

@Composable
private fun EmptyFriendsState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👥", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("No friends yet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Add friends to see what they're\nlistening to!", fontSize = 14.sp,
            color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a friend")
        }
    }
}

// ── Invite card ──────────────────────────────────────────────────────────

@Composable
private fun InviteCard() {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎶", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Invite friends to MoodSync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Share your music vibes together", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { /* TODO: Share intent */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) { Text("Invite", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold) }
        }
    }
}

// ── Add friend dialog ────────────────────────────────────────────────────

@Composable
private fun AddFriendDialog(error: String?, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter their MoodSync username:", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    placeholder = { Text("Username") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.Red, fontSize = 13.sp)
                }
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

// ── Helpers ──────────────────────────────────────────────────────────────

private fun getMoodColor(mood: String): Color = when (mood) {
    "happy" -> Color(0xFFFFB347); "sad" -> Color(0xFF667EEA); "calm" -> Color(0xFF89CFF0)
    "energetic" -> Color(0xFFFF416C); "tired" -> Color(0xFF607D8B); "focused" -> Color(0xFF11998E)
    "romantic" -> Color(0xFFEE9CA7); else -> Color(0xFF1DB954)
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}