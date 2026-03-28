package com.example.fypdraft.view

import android.util.Log
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
import com.example.fypdraft.model.MascotMoodDetector
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.model.PetState
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val TAG = "HomeScreen"

private data class MusicSection(val title: String, val emoji: String, val tracks: List<Track>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    petRepository: PetRepository? = null,
    themeState: AppThemeState = AppThemeState(),
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

    val currentUser = FirebaseAuth.getInstance().currentUser
    val displayName = currentUser?.displayName ?: "Friend"

    val moodHistoryRepo = remember { MoodHistoryRepository() }
    val rlEngine = remember { RLRecommendationEngine() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    // REACTIVE: observe auth state via StateFlow
    val spotifyAuthState by spotifyRepository?.authState?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val isSpotifyConnected = spotifyAuthState?.isAuthenticated == true

    var isLoading by remember { mutableStateOf(false) }
    var sections by remember { mutableStateOf<List<MusicSection>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    var mascotMood by remember { mutableStateOf(MascotMoodDetector.detectMood()) }
    var chatMessage by remember { mutableStateOf<String?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }

    // Pet state
    val localPetRepo = remember { petRepository ?: PetRepository() }
    val petState by localPetRepo.petState.collectAsState()

    // Load pet from Firebase on first load
    LaunchedEffect(Unit) {
        localPetRepo.loadPet()
    }

    // Sync mascot mood to pet mood
    LaunchedEffect(mascotMood.mood) {
        localPetRepo.updateMood(mascotMood.mood)
    }

    // KEY FIX: Load when isSpotifyConnected changes to true
    LaunchedEffect(isSpotifyConnected) {
        Log.d(TAG, "=== LaunchedEffect triggered === isSpotifyConnected=$isSpotifyConnected, hasLoaded=$hasLoaded, sectionsSize=${sections.size}")

        if (!isSpotifyConnected || spotifyMusicRepo == null) {
            Log.d(TAG, "Not connected or no repo - skipping load")
            if (!isSpotifyConnected) {
                sections = emptyList()
                hasLoaded = false
            }
            return@LaunchedEffect
        }

        // Don't reload if we already have data
        if (hasLoaded && sections.isNotEmpty()) {
            Log.d(TAG, "Already loaded ${sections.size} sections - skipping")
            return@LaunchedEffect
        }

        isLoading = true
        loadError = null
        Log.d(TAG, "Starting to load recommendations...")

        try { rlEngine.loadState() } catch (_: Exception) {}

        try {
            // ── Verify token is valid BEFORE launching parallel calls ──
            val token = spotifyRepository?.getAccessToken()
            if (token == null) {
                Log.e(TAG, "No valid token before loading sections")
                loadError = "Session expired. Please reconnect Spotify."
                isLoading = false
                return@LaunchedEffect
            }
            Log.d(TAG, "Token verified: ${token.take(10)}... — launching section loads")

            data class SectionDef(val title: String, val emoji: String, val query: String)

            val sectionDefs = listOf(
                SectionDef("Picked for you", "✨", "PERSONALIZED"),
                SectionDef("Trending now", "🔥", "top hits 2025 trending popular"),
                SectionDef("Uplift your mood", "🌟", "happy uplifting feel good pop"),
                SectionDef("Energy boost", "⚡", "energetic workout high energy dance"),
                SectionDef("Feeling down?", "💙", "sad emotional ballad comfort songs"),
                SectionDef("Focus mode", "🎯", "focus study instrumental lofi beats"),
                SectionDef("Chill vibes", "😌", "chill lofi ambient relaxing vibes"),
                SectionDef("Late night", "🌙", "late night r&b smooth jazz soul")
            )

            val deferreds = sectionDefs.map { def ->
                def to async {
                    try {
                        if (def.query == "PERSONALIZED") {
                            spotifyMusicRepo.getPersonalizedTracks(10)
                        } else {
                            spotifyMusicRepo.searchTracks(def.query, 10)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "${def.title} failed: ${e.message}")
                        emptyList()
                    }
                }
            }

            val results = mutableListOf<MusicSection>()
            for ((def, deferred) in deferreds) {
                val tracks = deferred.await()
                Log.d(TAG, "${def.emoji} ${def.title}: ${tracks.size} tracks")
                if (tracks.isNotEmpty()) {
                    results.add(MusicSection(def.title, def.emoji, tracks))
                }
            }

            sections = results
            hasLoaded = true
            Log.d(TAG, "=== DONE: ${sections.size} sections loaded ===")

            if (sections.isEmpty()) {
                // All sections empty — verify if the token is actually still valid
                val testToken = spotifyRepository?.getAccessToken()
                if (testToken == null) {
                    loadError = "Session expired. Please reconnect Spotify."
                    Log.w(TAG, "All sections empty — token is gone, marking expired")
                    spotifyRepository?.markTokenExpired()
                } else {
                    loadError = "No results found. Tap to retry."
                    Log.w(TAG, "All sections empty but token still valid: ${testToken.take(10)}...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal load error", e)
            loadError = "Failed: ${e.message}"
        }

        isLoading = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(displayName, currentUser?.email ?: "",
                onSettings = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                onSpotify = { scope.launch { drawerState.close() }; onNavigateToSpotify() },
                onSignOut = { scope.launch { drawerState.close() }; onSignOut() })
        }
    ) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniMusicPlayer(musicPlayerViewModel, onNavigateToMusicPlayer)
                    BottomNavBar(currentTab, {}, onNavigateToSearch, onNavigateToFriends, onNavigateToLibrary)
                }
            }
        ) { padding ->
            val bgBrush = animatedMoodBrushLight(themeState)
            Box(modifier.fillMaxSize().background(bgBrush).padding(padding)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

                    // Top bar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1A1A2E)).clickable { scope.launch { drawerState.open() } }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, "Profile", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(getTimeGreeting(), fontSize = 14.sp, color = Color.Gray)
                            Text("Hey, $displayName!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    // Spotify connect / expired prompts
                    if (!isSpotifyConnected) {
                        val errorMsg = spotifyAuthState?.errorMessage
                        val isExpired = errorMsg != null && errorMsg.contains("expired", ignoreCase = true)

                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onNavigateToSpotify() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isExpired) Color(0xFFFF9800) else Color(0xFF1DB954))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isExpired) Icon(Icons.Filled.Warning, null, tint = Color.White) else Text("🎵", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (isExpired) "Session expired" else "Connect Spotify", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(if (isExpired) "Tap to reconnect" else "Get personalized recommendations", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Mascot with pixel pet
                    MascotWidget(
                        mood = mascotMood,
                        petState = petState,
                        petRepository = localPetRepo,
                        chatMessage = chatMessage,
                        onQuickReply = { reply ->
                            scope.launch {
                                if (reply == "yes") { chatMessage = "Here are tracks just for you 🎶"; rlEngine.recordReward(RewardEvent(RewardType.SUGGESTION_ACCEPTED, mascotMood.mood, null)) }
                                else { chatMessage = "Tap me anytime 😊"; rlEngine.recordReward(RewardEvent(RewardType.SUGGESTION_REJECTED, mascotMood.mood, null)) }
                            }
                        },
                        onTapMascot = { onNavigateToEmotionChat() },
                        onChangeMood = { showMoodPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Content
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(8.dp))
                                    Text("Loading your music...", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                        !isSpotifyConnected -> {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Connect Spotify to see recommendations", color = Color.Gray)
                            }
                        }
                        loadError != null -> {
                            Card(Modifier.fillMaxWidth().padding(16.dp).clickable {
                                // Retry — reset so LaunchedEffect re-triggers
                                hasLoaded = false
                                loadError = null
                                sections = emptyList()
                            }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Refresh, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Tap to retry", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(loadError ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        sections.isEmpty() && hasLoaded -> {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No recommendations yet. Try changing your mood!", color = Color.Gray)
                            }
                        }
                        else -> {
                            sections.forEach { section ->
                                Text("${section.emoji} ${section.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 20.dp))
                                Spacer(Modifier.height(12.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(section.tracks) { track -> SmallTrackCard(track, section.tracks, musicPlayerViewModel, onNavigateToMusicPlayer) }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showMoodPicker) {
        MoodPickerDialog(mascotMood.mood, onSelect = { selected ->
            val old = mascotMood.mood
            mascotMood = MascotMoodDetector.getMoodForKey(selected).copy(isUserOverride = true)
            chatMessage = null; showMoodPicker = false
            moodHistoryRepo.saveMood("Changed $old -> $selected")
            scope.launch { rlEngine.recordReward(RewardEvent(RewardType.MOOD_OVERRIDE, old, null)) }
        }, onDismiss = { showMoodPicker = false })
    }
}

@Composable
private fun SmallTrackCard(track: Track, all: List<Track>, vm: MusicPlayerViewModel?, onNav: () -> Unit) {
    Column(Modifier.width(130.dp).clickable { vm?.loadTrack(track, all); onNav() }) {
        Card(Modifier.size(130.dp), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(4.dp)) {
            if (track.albumArtUrl.isNotEmpty()) AsyncImage(model = track.albumArtUrl, contentDescription = track.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 32.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BottomNavBar(currentTab: Int, onHome: () -> Unit, onSearch: () -> Unit, onFriends: () -> Unit, onLibrary: () -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(selected = currentTab == 0, onClick = onHome, icon = { Icon(Icons.Filled.Home, "Home") }, label = { Text("Home", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 1, onClick = onSearch, icon = { Icon(Icons.Filled.Search, "Search") }, label = { Text("Search", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 2, onClick = onFriends, icon = { Icon(Icons.Filled.People, "Friends") }, label = { Text("Friends", fontSize = 11.sp) })
        NavigationBarItem(selected = currentTab == 3, onClick = onLibrary, icon = { Icon(Icons.Filled.LibraryMusic, "Library") }, label = { Text("Library", fontSize = 11.sp) })
    }
}

@Composable
fun MiniMusicPlayer(vm: MusicPlayerViewModel?, onNav: () -> Unit) {
    val ps = vm?.playerState?.collectAsState()
    val track = ps?.value?.currentTrack ?: return
    val playing = ps?.value?.isPlaying ?: false
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onNav() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(40.dp), shape = RoundedCornerShape(8.dp)) {
                if (track.albumArtUrl.isNotEmpty()) AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(Color(0xFF6A5ACD)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm?.togglePlayPause() }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "PlayPause", tint = Color.White) }
        }
    }
}

@Composable
private fun ProfileDrawerContent(displayName: String, email: String, onSettings: () -> Unit, onSpotify: () -> Unit, onSignOut: () -> Unit) {
    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = Color.White) {
        Column(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF1A1A2E)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(16.dp)); Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(email, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(32.dp)); Divider(); Spacer(Modifier.height(16.dp))
            DrawerItem(Icons.Filled.Settings, "Settings", onSettings); DrawerItem(Icons.Filled.Link, "Connect Spotify", onSpotify)
            DrawerItem(Icons.Filled.Favorite, "Favorites", {}); DrawerItem(Icons.Filled.History, "Mood History", {})
            Spacer(Modifier.weight(1f)); Divider(); Spacer(Modifier.height(12.dp)); DrawerItem(Icons.Filled.Logout, "Sign Out", onSignOut, Color.Red)
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Black) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(16.dp)); Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
private fun MoodPickerDialog(currentMood: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
        text = { Column { MascotMoodDetector.allMoodKeys().forEach { mood -> val d = MascotMoodDetector.getMoodForKey(mood)
            Row(Modifier.fillMaxWidth().clickable { onSelect(mood) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.emoji, fontSize = 24.sp); Spacer(Modifier.width(14.dp))
                Text(mood.replaceFirstChar { it.uppercase() }, fontSize = 16.sp, fontWeight = if (mood == currentMood) FontWeight.Bold else FontWeight.Normal, color = if (mood == currentMood) Color(0xFF6A5ACD) else Color.Black)
                if (mood == currentMood) { Spacer(Modifier.weight(1f)); Icon(Icons.Filled.Check, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(20.dp)) }
            }
        } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun getTimeGreeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Late night vibes" }
}