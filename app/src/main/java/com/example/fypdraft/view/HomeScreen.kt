package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.MoodHistoryRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.ml.RLRecommendationEngine
import com.example.fypdraft.ml.RewardEvent
import com.example.fypdraft.ml.RewardType
import com.example.fypdraft.model.MascotMood
import com.example.fypdraft.model.MascotMoodDetector
import com.example.fypdraft.model.Track
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToEmotionChat: () -> Unit = {},
    onSignOut: () -> Unit = {},
    currentTab: Int = 0
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Real username from Firebase
    val currentUser = FirebaseAuth.getInstance().currentUser
    val displayName = currentUser?.displayName ?: "Friend"

    // Repositories
    val moodHistoryRepo = remember { MoodHistoryRepository() }
    val rlEngine = remember { RLRecommendationEngine() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    // Music data
    var featuredTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var moodTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSpotifyConnected by remember { mutableStateOf(false) }

    // Mascot
    var mascotMood by remember { mutableStateOf(MascotMoodDetector.detectMood()) }
    var chatMessage by remember { mutableStateOf<String?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }

    // Load RL state and music
    LaunchedEffect(Unit) {
        scope.launch {
            rlEngine.loadState()
            isSpotifyConnected = spotifyRepository?.isAuthenticated() == true

            if (isSpotifyConnected && spotifyMusicRepo != null) {
                try {
                    isLoading = true

                    // Get user's top tracks for seeding
                    val seedIds = spotifyMusicRepo.getUserTopTrackIds()

                    // RL-powered recommendations
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val targets = rlEngine.getFeatureTargets(mascotMood.mood, hour)
                    moodTracks = spotifyMusicRepo.getRecommendations(
                        featureTargets = targets,
                        seedTrackIds = seedIds,
                        limit = 15
                    )

                    // Featured / new releases
                    featuredTracks = spotifyMusicRepo.getFeaturedTracks(6)

                    // Top tracks with different seeds
                    val topTargets = rlEngine.getFeatureTargets("neutral", hour)
                    topTracks = spotifyMusicRepo.getRecommendations(
                        featureTargets = topTargets,
                        seedTrackIds = seedIds.take(2),
                        seedGenres = listOf("pop", "rock"),
                        limit = 10
                    )

                    isLoading = false
                } catch (e: Exception) {
                    isLoading = false
                }
            } else {
                isLoading = false
            }
        }
    }

    // Reload recommendations when mood changes
    LaunchedEffect(mascotMood.mood) {
        if (!isSpotifyConnected || spotifyMusicRepo == null) return@LaunchedEffect
        scope.launch {
            try {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val targets = rlEngine.getFeatureTargets(mascotMood.mood, hour)
                val seedIds = spotifyMusicRepo.getUserTopTrackIds()
                moodTracks = spotifyMusicRepo.getRecommendations(
                    featureTargets = targets,
                    seedTrackIds = seedIds,
                    limit = 15
                )
            } catch (_: Exception) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                displayName = displayName,
                email = currentUser?.email ?: "",
                onSettings = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                onSpotify = { scope.launch { drawerState.close() }; onNavigateToSpotify() },
                onSignOut = { scope.launch { drawerState.close() }; onSignOut() }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniMusicPlayer(musicPlayerViewModel, onNavigateToMusicPlayer)
                    BottomNavBar(
                        currentTab = currentTab,
                        onHome = { },
                        onSearch = onNavigateToSearch,
                        onFriends = onNavigateToFriends,
                        onLibrary = onNavigateToLibrary
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F8FA))
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top bar: profile + greeting (no notification bell)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A2E))
                                .clickable { scope.launch { drawerState.open() } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, "Profile", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(getTimeGreeting(), fontSize = 14.sp, color = Color.Gray)
                            Text("Hey, $displayName!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    // Spotify connection prompt if not connected
                    if (!isSpotifyConnected) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { onNavigateToSpotify() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1DB954))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\uD83C\uDFB5", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Connect Spotify", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Get personalized recommendations", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Mascot
                    MascotWidget(
                        mood = mascotMood,
                        chatMessage = chatMessage,
                        onQuickReply = { reply: String ->
                            scope.launch {
                                if (reply == "yes") {
                                    chatMessage = "Great! Here are tracks picked just for you \uD83C\uDFB6"
                                    rlEngine.recordReward(RewardEvent(
                                        type = RewardType.SUGGESTION_ACCEPTED,
                                        mood = mascotMood.mood,
                                        trackFeatures = null
                                    ))
                                } else {
                                    chatMessage = "No worries! Tap me anytime \uD83D\uDE0A"
                                    rlEngine.recordReward(RewardEvent(
                                        type = RewardType.SUGGESTION_REJECTED,
                                        mood = mascotMood.mood,
                                        trackFeatures = null
                                    ))
                                }
                            }
                        },
                        onTapMascot = { onNavigateToEmotionChat() },
                        onChangeMood = { showMoodPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (!isSpotifyConnected) {
                        // Show message when not connected
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Connect Spotify above to see personalized music recommendations",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Featured banner
                        if (featuredTracks.isNotEmpty()) {
                            Text("Featured for you", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(12.dp))
                            FeaturedBanner(featuredTracks.first(), musicPlayerViewModel, featuredTracks, onNavigateToMusicPlayer)
                            Spacer(Modifier.height(24.dp))
                        }

                        // RL mood tracks
                        if (moodTracks.isNotEmpty()) {
                            Text("For your ${mascotMood.mood} mood ${mascotMood.emoji}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(moodTracks) { track ->
                                    SmallTrackCard(track, moodTracks, musicPlayerViewModel, onNavigateToMusicPlayer)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        // Trending
                        if (topTracks.isNotEmpty()) {
                            Text("Trending now \uD83D\uDD25", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(topTracks) { track ->
                                    SmallTrackCard(track, topTracks, musicPlayerViewModel, onNavigateToMusicPlayer)
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showMoodPicker) {
        MoodPickerDialog(
            currentMood = mascotMood.mood,
            onSelect = { selected: String ->
                val oldMood = mascotMood.mood
                mascotMood = MascotMoodDetector.getMoodForKey(selected).copy(isUserOverride = true)
                chatMessage = null
                showMoodPicker = false

                // Save to mood history + RL signal
                moodHistoryRepo.saveMood("User changed mood from $oldMood to $selected")
                scope.launch {
                    rlEngine.recordReward(RewardEvent(
                        type = RewardType.MOOD_OVERRIDE,
                        mood = oldMood,
                        trackFeatures = null
                    ))
                }
            },
            onDismiss = { showMoodPicker = false }
        )
    }
}

// ── Shared Bottom Nav Bar (reusable across screens) ──────────────────

@Composable
fun BottomNavBar(
    currentTab: Int,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onLibrary: () -> Unit
) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(selected = currentTab == 0, onClick = onHome,
            icon = { Icon(Icons.Filled.Home, "Home") }, label = { Text("Home", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 1, onClick = onSearch,
            icon = { Icon(Icons.Filled.Search, "Search") }, label = { Text("Search", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 2, onClick = onFriends,
            icon = { Icon(Icons.Filled.People, "Friends") }, label = { Text("Friends", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 3, onClick = onLibrary,
            icon = { Icon(Icons.Filled.LibraryMusic, "Library") }, label = { Text("Library", fontSize = 11.sp) })
    }
}

// ── Featured banner ──────────────────────────────────────────────────

@Composable
private fun FeaturedBanner(track: Track, vm: MusicPlayerViewModel?, allTracks: List<Track>, onNav: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp)
            .clickable { vm?.loadTrack(track, allTracks); vm?.play(); onNav() },
        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = track.albumArtUrl, contentDescription = track.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(track.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1)
            }
            FloatingActionButton(onClick = { vm?.loadTrack(track, allTracks); vm?.play(); onNav() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                containerColor = Color.White, shape = CircleShape
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(28.dp)) }
        }
    }
}

// ── Small track card ─────────────────────────────────────────────────

@Composable
private fun SmallTrackCard(track: Track, all: List<Track>, vm: MusicPlayerViewModel?, onNav: () -> Unit) {
    Column(Modifier.width(130.dp).clickable { vm?.loadTrack(track, all); vm?.play(); onNav() }) {
        Card(Modifier.size(130.dp), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            AsyncImage(model = track.albumArtUrl, contentDescription = track.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Mini music player ────────────────────────────────────────────────

@Composable
fun MiniMusicPlayer(vm: MusicPlayerViewModel?, onNav: () -> Unit) {
    val playerState = vm?.playerState?.collectAsState()
    val track = playerState?.value?.currentTrack ?: return
    val isPlaying = playerState?.value?.isPlaying ?: false

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onNav() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(40.dp), shape = RoundedCornerShape(8.dp)) {
                AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm?.togglePlayPause() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "PlayPause", tint = Color.White)
            }
        }
    }
}

// ── Profile drawer ───────────────────────────────────────────────────

@Composable
private fun ProfileDrawerContent(displayName: String, email: String, onSettings: () -> Unit, onSpotify: () -> Unit, onSignOut: () -> Unit) {
    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = Color.White) {
        Column(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(email, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(32.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            DrawerItem(Icons.Filled.Settings, "Settings", onSettings)
            DrawerItem(Icons.Filled.Link, "Connect Spotify", onSpotify)
            DrawerItem(Icons.Filled.Favorite, "Favorites", onClick = { })
            DrawerItem(Icons.Filled.History, "Mood History", onClick = { })
            Spacer(Modifier.weight(1f))
            Divider()
            Spacer(Modifier.height(12.dp))
            DrawerItem(Icons.Filled.Logout, "Sign Out", onSignOut, Color.Red)
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Black) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

// ── Mood picker ──────────────────────────────────────────────────────

@Composable
private fun MoodPickerDialog(currentMood: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                MascotMoodDetector.allMoodKeys().forEach { mood ->
                    val data = MascotMoodDetector.getMoodForKey(mood)
                    Row(Modifier.fillMaxWidth().clickable { onSelect(mood) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(data.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(mood.replaceFirstChar { it.uppercase() }, fontSize = 16.sp,
                            fontWeight = if (mood == currentMood) FontWeight.Bold else FontWeight.Normal,
                            color = if (mood == currentMood) Color(0xFF6A5ACD) else Color.Black)
                        if (mood == currentMood) { Spacer(Modifier.weight(1f)); Icon(Icons.Filled.Check, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun getTimeGreeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Late night vibes" }
}