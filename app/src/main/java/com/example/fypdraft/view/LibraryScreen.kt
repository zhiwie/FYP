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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.data.repository.FavoritesRepository
import com.example.fypdraft.model.SongRecommendation
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.example.fypdraft.model.Track
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 3
) {
    val scope = rememberCoroutineScope()
    val favoritesRepo = remember { FavoritesRepository() }

    var favorites by remember { mutableStateOf<List<Pair<String, SongRecommendation>>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Favorites", "Playlists")

    // Load real favorites from Firebase
    LaunchedEffect(Unit) {
        try {
            favoritesRepo.observeFavorites()
                .catch { /* User not logged in or error */ }
                .collect { list -> favorites = list }
        } catch (_: Exception) {}
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onHome = onNavigateToHome,
                onSearch = onNavigateToSearch,
                onFriends = onNavigateToFriends,
                onLibrary = { }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F8FA))
                .padding(padding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 13.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF1A1A2E), selectedLabelColor = Color.White)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Liked songs count
                item {
                    LibraryItem(
                        icon = Icons.Filled.Favorite,
                        iconBg = Color(0xFFFF6B6B),
                        title = "Liked Songs",
                        subtitle = "${favorites.size} songs",
                        onClick = { selectedFilter = "Favorites" }
                    )
                }

                item {
                    LibraryItem(
                        icon = Icons.Filled.History,
                        iconBg = Color(0xFF6A5ACD),
                        title = "Recently Played",
                        subtitle = "Jump back in",
                        onClick = { }
                    )
                }

                // Show favorites when filter is "Favorites" or "All"
                if (selectedFilter == "Favorites" || selectedFilter == "All") {
                    if (favorites.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text("Your Favorites", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        items(favorites) { (docId, song) ->
                            FavoriteItem(
                                song = song,
                                onPlay = {
                                    val track = Track(
                                        id = "${song.artist}-${song.title}",
                                        name = song.title,
                                        artist = song.artist,
                                        albumArtUrl = "",
                                        previewUrl = null,
                                        durationMs = 0L
                                    )
                                    musicPlayerViewModel?.loadTrack(track)
                                    musicPlayerViewModel?.play()
                                    onNavigateToMusicPlayer()
                                },
                                onRemove = {
                                    scope.launch {
                                        try { favoritesRepo.removeFavorite(docId) } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    } else if (selectedFilter == "Favorites") {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No favorites yet. Like songs to see them here!", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun LibraryItem(icon: ImageVector, iconBg: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FavoriteItem(song: SongRecommendation, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEEEE)),
            contentAlignment = Alignment.Center
        ) { Text("\uD83C\uDFB5", fontSize = 20.sp) }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 13.sp, color = Color.Gray, maxLines = 1)
        }

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Favorite, "Remove", tint = Color.Red, modifier = Modifier.size(20.dp))
        }
    }
}