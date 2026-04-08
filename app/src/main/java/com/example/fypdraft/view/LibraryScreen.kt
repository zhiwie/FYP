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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.FavoritesRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyPlaylistInfo
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.model.SongRecommendation
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 3
) {
    val isDark = themeState.isDark

    // ── Theme-aware colors ────────────────────────────────────────────────
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val iconTint      = if (isDark) Color(0xFF9E9EBB) else Color(0xFF666677)
    val cardBg        = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEEE)

    val scope            = rememberCoroutineScope()
    val favoritesRepo    = remember { FavoritesRepository() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    var favorites        by remember { mutableStateOf<List<Pair<String, SongRecommendation>>>(emptyList()) }
    var playlists        by remember { mutableStateOf<List<SpotifyPlaylistInfo>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var selectedFilter   by remember { mutableStateOf("All") }
    val filters          = listOf("All", "Favorites", "Playlists")
    var loadingSongId    by remember { mutableStateOf<String?>(null) }

    val isSpotifyConnected = spotifyRepository?.let {
        val authState by it.authState.collectAsState()
        authState.isAuthenticated
    } ?: false

    LaunchedEffect(Unit) {
        try {
            favoritesRepo.observeFavorites().catch { }.collect { list -> favorites = list }
        } catch (_: Exception) {}
    }

    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected && spotifyMusicRepo != null) {
            playlistsLoading = true
            try { playlists = withContext(Dispatchers.IO) { spotifyMusicRepo.getUserPlaylists(10) } } catch (_: Exception) {}
            playlistsLoading = false
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab, onHome = onNavigateToHome, onSearch = onNavigateToSearch,
                onFriends = onNavigateToFriends, onLibrary = { }, themeState = themeState
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(animatedMoodBrushLight(themeState)).padding(padding)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = primaryText)
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick  = { selectedFilter = filter },
                        label    = { Text(filter, fontSize = 13.sp) },
                        shape    = RoundedCornerShape(20.dp),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1A1A2E),
                            selectedLabelColor     = Color.White,
                            containerColor         = cardBg,
                            labelColor             = primaryText
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Quick access ───────────────────────────────────────
                item {
                    LibraryItem(
                        icon = Icons.Filled.Favorite, iconBg = Color(0xFFFF6B6B),
                        title = "Liked Songs", subtitle = "${favorites.size} songs",
                        primaryText = primaryText, secondaryText = secondaryText, iconTint = iconTint,
                        onClick = { selectedFilter = "Favorites" }
                    )
                }
                item {
                    LibraryItem(
                        icon = Icons.Filled.History, iconBg = Color(0xFF6A5ACD),
                        title = "Recently Played", subtitle = "Jump back in",
                        primaryText = primaryText, secondaryText = secondaryText, iconTint = iconTint,
                        onClick = { }
                    )
                }

                // ── Spotify playlists ──────────────────────────────────
                if (selectedFilter == "Playlists" || selectedFilter == "All") {
                    if (!isSpotifyConnected) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors   = CardDefaults.cardColors(containerColor = Color(0xFF1DB954).copy(alpha = 0.1f)),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎵", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Connect Spotify", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primaryText)
                                        Text("See your playlists here", fontSize = 12.sp, color = secondaryText)
                                    }
                                }
                            }
                        }
                    } else if (playlistsLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF1DB954))
                            }
                        }
                    } else if (playlists.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Your Spotify Playlists", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = primaryText, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(playlists) { playlist ->
                            PlaylistItem(
                                playlist = playlist, isDark = isDark,
                                primaryText = primaryText, secondaryText = secondaryText, iconTint = iconTint,
                                onClick = {}
                            )
                        }
                    }
                }

                // ── Favorites ──────────────────────────────────────────
                if (selectedFilter == "Favorites" || selectedFilter == "All") {
                    if (favorites.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Your Favorites", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = primaryText, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(favorites) { (docId, song) ->
                            val songId    = "${song.artist}-${song.title}"
                            val isLoading = loadingSongId == songId
                            FavoriteItem(
                                song = song, isLoading = isLoading,
                                isDark = isDark, primaryText = primaryText, secondaryText = secondaryText,
                                onPlay = {
                                    if (musicPlayerViewModel != null) {
                                        loadingSongId = songId
                                        musicPlayerViewModel.playFromRecommendation(song.title, song.artist) { success, _ ->
                                            loadingSongId = null
                                            if (success) onNavigateToMusicPlayer()
                                        }
                                    }
                                },
                                onRemove = { scope.launch { try { favoritesRepo.removeFavorite(docId) } catch (_: Exception) {} } }
                            )
                        }
                    } else if (selectedFilter == "Favorites") {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💖", fontSize = 40.sp); Spacer(Modifier.height(8.dp))
                                    Text("No favorites yet", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = primaryText)
                                    Text("Like songs to see them here!", color = secondaryText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ── Library quick access item ─────────────────────────────────────────────

@Composable
private fun LibraryItem(
    icon: ImageVector, iconBg: Color, title: String, subtitle: String,
    primaryText: Color, secondaryText: Color, iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
            Text(subtitle, fontSize = 13.sp, color = secondaryText)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

// ── Spotify playlist item ─────────────────────────────────────────────────

@Composable
private fun PlaylistItem(
    playlist: SpotifyPlaylistInfo,
    isDark: Boolean = false,
    primaryText: Color = Color(0xFF1A1A2E),
    secondaryText: Color = Color(0xFF666677),
    iconTint: Color = Color(0xFF666677),
    onClick: () -> Unit
) {
    val placeholderBg = if (isDark) Color(0xFF2A2A3E) else Color(0xFF1DB954).copy(alpha = 0.2f)
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playlist.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = playlist.imageUrl, contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(placeholderBg),
                contentAlignment = Alignment.Center
            ) { Text("🎵", fontSize = 22.sp) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} tracks · ${playlist.ownerName}", fontSize = 12.sp,
                color = secondaryText, maxLines = 1)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

// ── Favorite song item ────────────────────────────────────────────────────

@Composable
private fun FavoriteItem(
    song: SongRecommendation, isLoading: Boolean = false,
    isDark: Boolean = false,
    primaryText: Color = Color(0xFF1A1A2E),
    secondaryText: Color = Color(0xFF666677),
    onPlay: () -> Unit, onRemove: () -> Unit
) {
    val thumbBg = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEEE)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isLoading) { onPlay() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1DB954))
            else Text("🎵", fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title,  fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText,   maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 13.sp, color = secondaryText, maxLines = 1)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Favorite, "Remove", tint = Color.Red, modifier = Modifier.size(20.dp))
        }
    }
}