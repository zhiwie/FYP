package com.example.fypdraft.view

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.SearchHistoryRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MoodCategory(
    val label: String,
    val emoji: String,
    val query: String,
    val colors: List<Color>
)

@Composable
fun SearchScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 1
) {
    val scope = rememberCoroutineScope()
    val searchHistoryRepo = remember { SearchHistoryRepository() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    val hasToken = spotifyRepository?.getAccessToken() != null

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val categories = remember {
        listOf(
            MoodCategory("Happy", "😊", "happy uplifting feel good hits", listOf(Color(0xFFFFB347), Color(0xFFFF6B6B))),
            MoodCategory("Chill", "😌", "chill lofi relaxing beats", listOf(Color(0xFF89CFF0), Color(0xFF6A9BD1))),
            MoodCategory("Energetic", "⚡", "energetic workout pump up", listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
            MoodCategory("Sad", "😢", "sad emotional heartbreak", listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
            MoodCategory("Focus", "🎯", "focus study instrumental concentration", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
            MoodCategory("Romance", "💕", "romantic love songs slow dance", listOf(Color(0xFFEE9CA7), Color(0xFFFFC3A0))),
            MoodCategory("Throwback", "🕹️", "throwback 90s 2000s classics", listOf(Color(0xFFFFA751), Color(0xFFFFE259))),
            MoodCategory("Sleep", "😴", "sleep ambient calming lullaby", listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)))
        )
    }

    // Load recent searches
    LaunchedEffect(Unit) {
        if (hasToken) {
            recentSearches = try {
                searchHistoryRepo.getRecentSearches().map { it.query }.distinct().take(8)
            } catch (_: Exception) { emptyList() }
        }
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()

        if (searchQuery.length < 2 || spotifyMusicRepo == null) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        searchJob = scope.launch {
            isSearching = true
            delay(400)

            Log.d("SearchScreen", "Searching Spotify for: '$searchQuery'")
            val results = spotifyMusicRepo.searchTracks(searchQuery, 10)
            Log.d("SearchScreen", "Got ${results.size} results")

            searchResults = results
            isSearching = false

            if (searchQuery.length >= 3) {
                searchHistoryRepo.saveSearch(searchQuery)
            }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(currentTab, onHome = onNavigateToHome, onSearch = {}, onFriends = onNavigateToFriends, onLibrary = onNavigateToLibrary, themeState = themeState)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(animatedMoodBrushLight(themeState)).padding(padding)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search songs, artists, albums...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    when {
                        isSearching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        searchQuery.isNotEmpty() -> IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                            Icon(Icons.Filled.Close, "Clear", tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.LightGray, unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            if (!hasToken) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Connect Spotify to search music", color = Color.Gray, fontSize = 15.sp)
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        Text("${searchResults.size} results", fontSize = 13.sp, color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(searchResults) { track ->
                        SearchResultItem(track) {
                            musicPlayerViewModel?.loadTrack(track, searchResults)
                            onNavigateToMusicPlayer()
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else if (searchQuery.length >= 2 && !isSearching) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No results for \"$searchQuery\"", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Recent searches", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                TextButton(onClick = {
                                    scope.launch { searchHistoryRepo.clearSearchHistory(); recentSearches = emptyList() }
                                }) { Text("Clear", color = Color.Gray, fontSize = 13.sp) }
                            }
                        }
                        items(recentSearches) { query ->
                            Row(
                                Modifier.fillMaxWidth().clickable { searchQuery = query }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(query, fontSize = 15.sp, color = Color.Black, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.NorthWest, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    item {
                        Text("Browse by mood", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(categories.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { cat ->
                                MoodCategoryCard(cat, { searchQuery = cat.query }, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(track: Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(Modifier.size(52.dp), shape = RoundedCornerShape(10.dp)) {
            if (track.albumArtUrl.isNotEmpty()) {
                AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · ${track.album}", fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MoodCategoryCard(category: MoodCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.height(120.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(category.colors)).padding(16.dp)) {
            Column {
                Text(category.emoji, fontSize = 32.sp)
                Spacer(Modifier.weight(1f))
                Text(category.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}