package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.MusicSearchRepository
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.example.fypdraft.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class MoodPlaylist(
    val title: String,
    val subtitle: String,
    val searchQuery: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToEmotionChat: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val musicSearchRepo = remember { MusicSearchRepository() }
    val scope = rememberCoroutineScope()

    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var similarEnergyTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var liftUpMoodTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }

    // Load music on first composition
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                topTracks = musicSearchRepo.getTopTracks().take(10)
                similarEnergyTracks = musicSearchRepo.getTracksByMood("energetic").take(10)
                liftUpMoodTracks = musicSearchRepo.getTracksByMood("happy").take(10)
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    // Handle search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            scope.launch {
                isSearching = true
                searchResults = musicSearchRepo.searchTracks(searchQuery).take(20)
                isSearching = false
            }
        } else {
            searchResults = emptyList()
        }
    }

    val moodPlaylists = remember {
        listOf(
            MoodPlaylist("Late Night", "Grooves", "late night chill"),
            MoodPlaylist("High Energy", "Workout", "high energy workout"),
            MoodPlaylist("Peaceful", "Relax", "peaceful calm")
        )
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEFE7FF),
            Color(0xFFFFF3D6),
            Color(0xFFDCEBFF)
        )
    )

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
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            onNavigateToLibrary()
                        },
                        icon = { Icon(Icons.Filled.LibraryMusic, "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            onNavigateToSpotify()
                        },
                        icon = { Icon(Icons.Filled.Link, "Connect") },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            onNavigateToSettings()
                        },
                        icon = { Icon(Icons.Filled.Settings, "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = { Text("Search songs, artists...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))

                // Search results
                if (searchResults.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(searchResults) { track ->
                            TrackCard(
                                track = track,
                                allTracks = searchResults,
                                musicPlayerViewModel = musicPlayerViewModel,
                                onNavigateToMusicPlayer = onNavigateToMusicPlayer
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // Mood Equaliser Card
                MoodEqualiserCard()

                Spacer(Modifier.height(24.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Loading music...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Playlist Recommendation",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    // Similar Energy
                    if (similarEnergyTracks.isNotEmpty()) {
                        Text(
                            text = "Similar Energy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(similarEnergyTracks) { track ->
                                TrackCard(
                                    track = track,
                                    allTracks = similarEnergyTracks,
                                    musicPlayerViewModel = musicPlayerViewModel,
                                    onNavigateToMusicPlayer = onNavigateToMusicPlayer
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // Lift Up Your Mood
                    if (liftUpMoodTracks.isNotEmpty()) {
                        Text(
                            text = "Lift Up Your Mood",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(liftUpMoodTracks) { track ->
                                TrackCard(
                                    track = track,
                                    allTracks = liftUpMoodTracks,
                                    musicPlayerViewModel = musicPlayerViewModel,
                                    onNavigateToMusicPlayer = onNavigateToMusicPlayer
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // Top Tracks
                    if (topTracks.isNotEmpty()) {
                        Text(
                            text = "Top Tracks Right Now",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(topTracks) { track ->
                                TrackCard(
                                    track = track,
                                    allTracks = topTracks,
                                    musicPlayerViewModel = musicPlayerViewModel,
                                    onNavigateToMusicPlayer = onNavigateToMusicPlayer
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }

                    // Mix Based on Your Mood
                    Text(
                        text = "Mix Based on Your Mood",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(moodPlaylists) { playlist ->
                            MoodPlaylistCard(
                                playlist = playlist,
                                musicSearchRepo = musicSearchRepo,
                                musicPlayerViewModel = musicPlayerViewModel,
                                onNavigateToMusicPlayer = onNavigateToMusicPlayer,
                                scope = scope
                            )
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }

            // Floating Chat Button
            FloatingActionButton(
                onClick = onNavigateToEmotionChat,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 140.dp)
                    .size(64.dp),
                containerColor = Color(0xFF6A5ACD),
                shape = CircleShape
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = "AI Music Chat",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "AI",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TrackCard(
    track: Track,
    allTracks: List<Track>,
    musicPlayerViewModel: MusicPlayerViewModel?,
    onNavigateToMusicPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable {
                musicPlayerViewModel?.loadTrack(track, allTracks)
                musicPlayerViewModel?.play()
                onNavigateToMusicPlayer()
            }
    ) {
        Card(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = null,
                error = null
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            maxLines = 2
        )
        Text(
            text = track.artist,
            fontSize = 11.sp,
            color = Color.Gray,
            maxLines = 1
        )
    }
}

@Composable
fun MoodEqualiserCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable { /* TODO */ },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mood Equaliser",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "🎵",
                    fontSize = 80.sp,
                    modifier = Modifier.padding(start = 120.dp, top = 20.dp)
                )
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(2.dp)
                        .background(Color(0xFF6495ED))
                )
            }
        }
    }
}

@Composable
fun MiniMusicPlayer(
    musicPlayerViewModel: MusicPlayerViewModel?,
    onNavigateToMusicPlayer: () -> Unit
) {
    val playerState = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack
    val isPlaying = playerState?.value?.isPlaying ?: false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .clickable { onNavigateToMusicPlayer() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8E8E8)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                if (currentTrack != null) {
                    AsyncImage(
                        model = currentTrack.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF6495ED))
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.name ?: "No track playing",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1
                )
                Text(
                    text = currentTrack?.artist ?: "Tap a song to play",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            IconButton(onClick = {
                musicPlayerViewModel?.togglePlayPause()
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun MoodPlaylistCard(
    playlist: MoodPlaylist,
    musicSearchRepo: MusicSearchRepository,
    musicPlayerViewModel: MusicPlayerViewModel?,
    onNavigateToMusicPlayer: () -> Unit,
    scope: CoroutineScope
) {
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable {
                scope.launch {
                    isLoading = true
                    val tracks = musicSearchRepo.searchTracks(playlist.searchQuery).take(20)
                    if (tracks.isNotEmpty()) {
                        musicPlayerViewModel?.loadTrack(tracks.first(), tracks)
                        musicPlayerViewModel?.play()
                        onNavigateToMusicPlayer()
                    }
                    isLoading = false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF696969)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
        Text(
            text = playlist.subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}