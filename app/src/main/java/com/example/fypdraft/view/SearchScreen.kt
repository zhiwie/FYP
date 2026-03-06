package com.example.fypdraft.view

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
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch

data class MoodCategory(
    val label: String,
    val emoji: String,
    val query: String,
    val colors: List<Color>
)

@Composable
fun SearchScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
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

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf<List<String>>(emptyList()) }

    val categories = remember {
        listOf(
            MoodCategory("Happy", "\uD83D\uDE0A", "happy upbeat", listOf(Color(0xFFFFB347), Color(0xFFFF6B6B))),
            MoodCategory("Chill", "\uD83D\uDE0C", "chill lofi", listOf(Color(0xFF89CFF0), Color(0xFF6A9BD1))),
            MoodCategory("Energetic", "\u26A1", "energetic workout", listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
            MoodCategory("Sad", "\uD83D\uDE22", "sad emotional", listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
            MoodCategory("Focus", "\uD83C\uDFAF", "focus instrumental", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
            MoodCategory("Romance", "\uD83D\uDC95", "romantic love", listOf(Color(0xFFEE9CA7), Color(0xFFFFC3A0)))
        )
    }

    // Load recent searches
    LaunchedEffect(Unit) {
        scope.launch {
            recentSearches = searchHistoryRepo.getRecentSearches().map { it.query }.distinct().take(8)
        }
    }

    // Search as user types
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2 && spotifyMusicRepo != null) {
            scope.launch {
                isSearching = true
                searchResults = spotifyMusicRepo.searchTracks(searchQuery)
                isSearching = false
                if (searchQuery.length >= 3) {
                    searchHistoryRepo.saveSearch(searchQuery)
                }
            }
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onHome = onNavigateToHome,
                onSearch = { },
                onFriends = onNavigateToFriends,
                onLibrary = onNavigateToLibrary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F8FA))
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search songs, artists, albums...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Close, "Clear", tint = Color.Gray) }
                },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color.LightGray, unfocusedBorderColor = Color.Transparent),
                singleLine = true
            )

            if (searchResults.isNotEmpty()) {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(searchResults) { track ->
                        SearchResultItem(track) {
                            musicPlayerViewModel?.loadTrack(track, searchResults)
                            musicPlayerViewModel?.play()
                            onNavigateToMusicPlayer()
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Recent searches
                    if (recentSearches.isNotEmpty()) {
                        item { Text("Recent", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        items(recentSearches) { query ->
                            Row(
                                Modifier.fillMaxWidth().clickable { searchQuery = query }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(query, fontSize = 15.sp, color = Color.Black)
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    item { Text("Browse by mood", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
                    items(categories.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { cat ->
                                MoodCategoryCard(cat, { searchQuery = cat.query }, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(Modifier.size(52.dp), shape = RoundedCornerShape(10.dp)) {
            AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 13.sp, color = Color.Gray, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MoodCategoryCard(category: MoodCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.height(100.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(category.colors)).padding(16.dp)) {
            Column { Text(category.emoji, fontSize = 28.sp); Spacer(Modifier.weight(1f)); Text(category.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}