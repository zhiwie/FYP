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
import com.example.fypdraft.data.repository.MusicSearchRepository
import com.example.fypdraft.model.MascotMood
import com.example.fypdraft.model.MascotMoodDetector
import com.example.fypdraft.model.Track
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToEmotionChat: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val musicSearchRepo = remember { MusicSearchRepository() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var featuredTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var moodTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var mascotMood by remember { mutableStateOf(MascotMoodDetector.detectMood()) }
    var chatMessage by remember { mutableStateOf<String?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                topTracks = musicSearchRepo.getTopTracks().take(10)
                featuredTracks = musicSearchRepo.getTracksByMood("trending").take(6)
                moodTracks = musicSearchRepo.getTracksByMood(mascotMood.mood).take(10)
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    LaunchedEffect(mascotMood.mood) {
        scope.launch {
            try {
                moodTracks = musicSearchRepo.getTracksByMood(mascotMood.mood).take(10)
            } catch (_: Exception) {}
        }
    }

    // Profile drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                onSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onSpotify = {
                    scope.launch { drawerState.close() }
                    onNavigateToSpotify()
                },
                onSignOut = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniMusicPlayer(
                        musicPlayerViewModel = musicPlayerViewModel,
                        onNavigateToMusicPlayer = onNavigateToMusicPlayer
                    )

                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Filled.Home, "Home") },
                            label = { Text("Home", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; onNavigateToSearch() },
                            icon = { Icon(Icons.Filled.Search, "Search") },
                            label = { Text("Search", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2; onNavigateToFriends() },
                            icon = { Icon(Icons.Filled.People, "Friends") },
                            label = { Text("Friends", fontSize = 11.sp) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3; onNavigateToLibrary() },
                            icon = { Icon(Icons.Filled.LibraryMusic, "Library") },
                            label = { Text("Library", fontSize = 11.sp) }
                        )
                    }
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
                    // Top bar
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
                            Text("Ready to vibe?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(onClick = { }) {
                            Icon(Icons.Filled.Notifications, "Notifications", tint = Color.Black)
                        }
                    }

                    // Mascot widget
                    MascotWidget(
                        mood = mascotMood,
                        chatMessage = chatMessage,
                        onQuickReply = { reply: String ->
                            if (reply == "yes") {
                                chatMessage = "Great! Here's some ${mascotMood.mood} tracks for you \uD83C\uDFB6"
                            } else {
                                chatMessage = "No worries! Tap me anytime \uD83D\uDE0A"
                            }
                        },
                        onTapMascot = { onNavigateToEmotionChat() },
                        onChangeMood = { showMoodPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Featured banner
                        if (featuredTracks.isNotEmpty()) {
                            Text(
                                "Featured for you",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(12.dp))

                            FeaturedBanner(
                                track = featuredTracks.first(),
                                musicPlayerViewModel = musicPlayerViewModel,
                                allTracks = featuredTracks,
                                onNavigateToMusicPlayer = onNavigateToMusicPlayer
                            )

                            Spacer(Modifier.height(24.dp))
                        }

                        // Mood tracks
                        if (moodTracks.isNotEmpty()) {
                            Text(
                                "For your ${mascotMood.mood} mood ${mascotMood.emoji}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(12.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(moodTracks) { track ->
                                    SmallTrackCard(track, moodTracks, musicPlayerViewModel, onNavigateToMusicPlayer)
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        // Top tracks
                        if (topTracks.isNotEmpty()) {
                            Text(
                                "Trending now \uD83D\uDD25",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(12.dp))

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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

    // Mood picker dialog
    if (showMoodPicker) {
        MoodPickerDialog(
            currentMood = mascotMood.mood,
            onSelect = { selected: String ->
                mascotMood = MascotMoodDetector.getMoodForKey(selected).copy(isUserOverride = true)
                chatMessage = null
                showMoodPicker = false
            },
            onDismiss = { showMoodPicker = false }
        )
    }
}

// ── Featured banner ──────────────────────────────────────────────────

@Composable
private fun FeaturedBanner(
    track: Track,
    musicPlayerViewModel: MusicPlayerViewModel?,
    allTracks: List<Track>,
    onNavigateToMusicPlayer: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp)
            .clickable {
                musicPlayerViewModel?.loadTrack(track, allTracks)
                musicPlayerViewModel?.play()
                onNavigateToMusicPlayer()
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(track.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1)
            }
            FloatingActionButton(
                onClick = {
                    musicPlayerViewModel?.loadTrack(track, allTracks)
                    musicPlayerViewModel?.play()
                    onNavigateToMusicPlayer()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp),
                containerColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ── Small track card ─────────────────────────────────────────────────

@Composable
private fun SmallTrackCard(
    track: Track,
    allTracks: List<Track>,
    musicPlayerViewModel: MusicPlayerViewModel?,
    onNavigateToMusicPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable {
                musicPlayerViewModel?.loadTrack(track, allTracks)
                musicPlayerViewModel?.play()
                onNavigateToMusicPlayer()
            }
    ) {
        Card(
            modifier = Modifier.size(130.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Mini music player ────────────────────────────────────────────────

@Composable
fun MiniMusicPlayer(
    musicPlayerViewModel: MusicPlayerViewModel?,
    onNavigateToMusicPlayer: () -> Unit
) {
    val playerState = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack
    val isPlaying = playerState?.value?.isPlaying ?: false

    if (currentTrack == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onNavigateToMusicPlayer() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp)) {
                AsyncImage(model = currentTrack.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(currentTrack.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(currentTrack.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { musicPlayerViewModel?.togglePlayPause() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White)
            }
        }
    }
}

// ── Profile drawer ───────────────────────────────────────────────────

@Composable
private fun ProfileDrawerContent(
    onSettings: () -> Unit,
    onSpotify: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = Color.White) {
        Column(modifier = Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Your Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("username@email.com", fontSize = 13.sp, color = Color.Gray)
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
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

// ── Mood picker dialog ───────────────────────────────────────────────

@Composable
private fun MoodPickerDialog(
    currentMood: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                MascotMoodDetector.allMoodKeys().forEach { mood ->
                    val moodData = MascotMoodDetector.getMoodForKey(mood)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mood) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(moodData.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            mood.replaceFirstChar { it.uppercase() },
                            fontSize = 16.sp,
                            fontWeight = if (mood == currentMood) FontWeight.Bold else FontWeight.Normal,
                            color = if (mood == currentMood) Color(0xFF6A5ACD) else Color.Black
                        )
                        if (mood == currentMood) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.Check, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun getTimeGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Late night vibes"
    }
}