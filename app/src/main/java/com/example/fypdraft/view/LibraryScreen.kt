package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// LibraryScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 3
) {
    val isDark        = themeState.isDark
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val iconTint      = if (isDark) Color(0xFF9E9EBB) else Color(0xFF666677)
    val cardBg        = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEEE)
    val sheetBg       = if (isDark) Color(0xFF1C1C2E) else Color.White

    val scope            = rememberCoroutineScope()
    val favoritesRepo    = remember { FavoritesRepository() }
    val spotifyMusicRepo = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    var favorites        by remember { mutableStateOf<List<Pair<String, SongRecommendation>>>(emptyList()) }
    var playlists        by remember { mutableStateOf<List<SpotifyPlaylistInfo>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var loadingSongId    by remember { mutableStateOf<String?>(null) }

    // Playlist detail state
    var openPlaylist          by remember { mutableStateOf<SpotifyPlaylistInfo?>(null) }
    var playlistTracks        by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlistTracksLoading by remember { mutableStateOf(false) }
    var playlistError         by remember { mutableStateOf<String?>(null) }

    // Recently Played sheet state
    var showRecentlyPlayed         by remember { mutableStateOf(false) }
    var recentlyPlayedTracks       by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentlyPlayedLoading      by remember { mutableStateOf(false) }

    // Liked Songs sheet state — merges Spotify saved tracks + Firestore favorites
    var showLikedSongs             by remember { mutableStateOf(false) }
    var likedSpotifyTracks         by remember { mutableStateOf<List<Track>>(emptyList()) }
    var likedSongsLoading          by remember { mutableStateOf(false) }

    val isSpotifyConnected = spotifyRepository?.let {
        val authState by it.authState.collectAsState()
        authState.isAuthenticated
    } ?: false

    // Load favorites real-time
    LaunchedEffect(Unit) {
        try {
            favoritesRepo.observeFavorites().catch { }.collect { list -> favorites = list }
        } catch (_: Exception) {}
    }

    // Load recently played from Firestore when sheet opens
    LaunchedEffect(showRecentlyPlayed) {
        if (!showRecentlyPlayed) return@LaunchedEffect
        recentlyPlayedLoading = true
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            val snapshot = withContext(Dispatchers.IO) {
                FirebaseFirestore.getInstance()
                    .collection("playbackHistory").document(uid).collection("tracks")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()
            }
            val seen = mutableSetOf<String>()
            recentlyPlayedTracks = snapshot.documents.mapNotNull { doc ->
                val trackId = doc.getString("trackId") ?: return@mapNotNull null
                if (!seen.add(trackId)) return@mapNotNull null   // deduplicate
                Track(
                    id          = trackId,
                    name        = doc.getString("title") ?: return@mapNotNull null,
                    artist      = doc.getString("artist") ?: "",
                    album       = "",
                    albumArtUrl = doc.getString("albumArt") ?: "",
                    previewUrl  = null,
                    durationMs  = 0L,
                    spotifyUri  = doc.getString("spotifyUri")?.takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {}
        recentlyPlayedLoading = false
    }

    // Load Spotify saved tracks ("Liked Songs") when sheet opens
    LaunchedEffect(showLikedSongs) {
        if (!showLikedSongs) return@LaunchedEffect
        val repo = spotifyMusicRepo ?: return@LaunchedEffect   // not connected — skip, show Firestore only
        likedSongsLoading = true
        try {
            likedSpotifyTracks = withContext(Dispatchers.IO) {
                repo.getUserSavedTracks(50)
            }
        } catch (_: Exception) {}
        likedSongsLoading = false
    }

    // Load playlists when Spotify connects
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected && spotifyMusicRepo != null) {
            playlistsLoading = true
            try {
                playlists = withContext(Dispatchers.IO) { spotifyMusicRepo.getUserPlaylists(20) }
            } catch (_: Exception) {}
            playlistsLoading = false
        }
    }

    // Load tracks when a playlist is tapped — THIS IS THE KEY FIX
    LaunchedEffect(openPlaylist?.id) {
        val playlist = openPlaylist ?: return@LaunchedEffect
        if (spotifyMusicRepo == null) {
            playlistError = "Spotify not connected."
            return@LaunchedEffect
        }
        playlistTracksLoading = true
        playlistTracks = emptyList()
        playlistError = null
        try {
            val tracks = withContext(Dispatchers.IO) {
                spotifyMusicRepo.getPlaylistTracks(playlist.id, limit = 50)
            }
            if (tracks.isEmpty()) playlistError = "No playable tracks found in this playlist."
            else {
                playlistTracks = tracks
                // Fix track count if Spotify reported 0 (happens with "item" key playlists)
                if (openPlaylist?.trackCount == 0 && tracks.isNotEmpty()) {
                    playlists = playlists.map {
                        if (it.id == playlist.id) it.copy(trackCount = tracks.size) else it
                    }
                }
            }
        } catch (e: SecurityException) {
            // Spotify Development Mode 403: direct playlist access is blocked for
            // followed/editorial playlists. Fall back to a search-based approach
            // using the playlist name so users still get something playable.
            try {
                val fallbackTracks = withContext(Dispatchers.IO) {
                    spotifyMusicRepo.getPlaylistTracksFallback(
                        playlistId   = playlist.id,
                        playlistName = playlist.name,
                        limit        = 30
                    )
                }
                if (fallbackTracks.isEmpty()) {
                    playlistError = "Couldn't find tracks for \"${playlist.name}\".\n\nSpotify restricts access to followed playlists in Development Mode. Try opening a playlist you created yourself."
                } else {
                    playlistTracks = fallbackTracks
                    // Surface a soft banner so the user knows these are search results
                    playlistError = "~"   // sentinel: UI checks for "~" to show info banner
                }
            } catch (fe: Exception) {
                playlistError = "Couldn't load \"${playlist.name}\". Try a playlist you created yourself."
            }
        } catch (e: Exception) {
            playlistError = "Could not load tracks: ${e.message}"
        }
        playlistTracksLoading = false
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniMusicPlayer(vm = musicPlayerViewModel, onNav = onNavigateToMusicPlayer, themeState = themeState)
                    BottomNavBar(
                        currentTab = currentTab,
                        onHome     = onNavigateToHome,
                        onSearch   = onNavigateToSearch,
                        onFriends  = onNavigateToFriends,
                        onLibrary  = {},
                        themeState = themeState
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(animatedMoodBrushLight(themeState))
                    .padding(padding)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = primaryText)
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        LibraryItem(
                            icon = Icons.Filled.Favorite, iconBg = Color(0xFFFF6B6B),
                            title = "Liked Songs", subtitle = if (likedSpotifyTracks.isEmpty() && favorites.isEmpty()) "Tap to view" else "${likedSpotifyTracks.size + favorites.size} songs",
                            primaryText = primaryText, secondaryText = secondaryText, iconTint = iconTint,
                            onClick = { showLikedSongs = true }
                        )
                    }
                    item {
                        LibraryItem(
                            icon = Icons.Filled.History, iconBg = Color(0xFF6A5ACD),
                            title = "Recently Played", subtitle = "Jump back in",
                            primaryText = primaryText, secondaryText = secondaryText, iconTint = iconTint,
                            onClick = { showRecentlyPlayed = true }
                        )
                    }

                    // ── Playlists ─────────────────────────────────────────
                    if (true) {
                        if (!isSpotifyConnected) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onNavigateToSpotify() },
                                    colors   = CardDefaults.cardColors(containerColor = Color(0xFF1DB954)),
                                    shape    = RoundedCornerShape(16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Connect Spotify", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                            Text("Tap to see your playlists here", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                        }
                                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
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
                                Text(
                                    "Your Spotify Playlists",
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = primaryText,
                                    modifier   = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(playlists) { playlist ->
                                PlaylistItem(
                                    playlist      = playlist,
                                    isDark        = isDark,
                                    primaryText   = primaryText,
                                    secondaryText = secondaryText,
                                    iconTint      = iconTint,
                                    onClick       = { openPlaylist = playlist }  // ← KEY FIX
                                )
                            }
                        } else if (!playlistsLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🎵", fontSize = 40.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("No playlists found", fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Text("Create a playlist on Spotify to see it here", color = secondaryText, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // ── Playlist Detail Sheet ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = openPlaylist != null,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { openPlaylist = null }
            ) {
                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                    shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors    = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(20.dp)
                ) {
                    // Drag handle
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.width(36.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isDark) Color(0xFF3A3A5A) else Color(0xFFDDDDDD))
                        )
                    }
                    openPlaylist?.let { playlist ->
                        PlaylistDetailSheet(
                            playlist      = playlist,
                            tracks        = playlistTracks,
                            isLoading     = playlistTracksLoading,
                            errorMessage  = playlistError,
                            isDark        = isDark,
                            primaryText   = primaryText,
                            secondaryText = secondaryText,
                            onDismiss     = { openPlaylist = null },
                            onPlayTrack   = { track ->
                                musicPlayerViewModel?.playFromRecommendation(track.name, track.artist) { ok, _ ->
                                    if (ok) { openPlaylist = null; onNavigateToMusicPlayer() }
                                }
                            },
                            onPlayAll = {
                                val first = playlistTracks.firstOrNull() ?: return@PlaylistDetailSheet
                                musicPlayerViewModel?.playFromRecommendation(first.name, first.artist) { ok, _ ->
                                    if (ok) { openPlaylist = null; onNavigateToMusicPlayer() }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Recently Played Sheet ─────────────────────────────────────────
        AnimatedVisibility(
            visible  = showRecentlyPlayed,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showRecentlyPlayed = false
                    }
            ) {
                Card(
                    modifier  = Modifier.fillMaxWidth().fillMaxHeight(0.88f).align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                    shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors    = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(20.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color(0xFF3A3A5A) else Color(0xFFDDDDDD)))
                    }
                    SimpleTrackSheet(
                        title         = "Recently Played",
                        emoji         = "🕘",
                        tracks        = recentlyPlayedTracks,
                        isLoading     = recentlyPlayedLoading,
                        emptyMessage  = "No recently played tracks yet.\nStart listening to build your history!",
                        isDark        = isDark,
                        primaryText   = primaryText,
                        secondaryText = secondaryText,
                        onDismiss     = { showRecentlyPlayed = false },
                        onPlayTrack   = { track ->
                            musicPlayerViewModel?.playFromRecommendation(track.name, track.artist) { ok, _ ->
                                if (ok) { showRecentlyPlayed = false; onNavigateToMusicPlayer() }
                            }
                        }
                    )
                }
            }
        }

        // ── Liked Songs Sheet ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showLikedSongs,
            enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showLikedSongs = false
                    }
            ) {
                Card(
                    modifier  = Modifier.fillMaxWidth().fillMaxHeight(0.88f).align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                    shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors    = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(20.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (isDark) Color(0xFF3A3A5A) else Color(0xFFDDDDDD)))
                    }
                    // Merge: Spotify saved tracks first, then any app-liked songs not already present
                    val spotifyTrackIds = likedSpotifyTracks.map { it.id }.toSet()
                    val firestoreAsTrack = favorites.map { (_, song) ->
                        Track(
                            id          = "${song.artist}::${song.title}",
                            name        = song.title,
                            artist      = song.artist,
                            album       = "",
                            albumArtUrl = "",
                            previewUrl  = null,
                            durationMs  = 0L,
                            spotifyUri  = null
                        )
                    }
                    // Deduplicate firestoreAsTrack against Spotify by name+artist (case-insensitive)
                    val spotifyNameKeys = likedSpotifyTracks
                        .map { "${it.name.lowercase()}::${it.artist.lowercase()}" }.toSet()
                    val firestoreOnly = firestoreAsTrack.filter {
                        "${it.name.lowercase()}::${it.artist.lowercase()}" !in spotifyNameKeys
                    }
                    // Final dedup within each source by id
                    val mergedLiked = (likedSpotifyTracks + firestoreOnly).distinctBy { it.id }
                    SimpleTrackSheet(
                        title         = "Liked Songs",
                        emoji         = "❤️",
                        tracks        = mergedLiked,
                        isLoading     = likedSongsLoading,
                        emptyMessage  = "No liked songs yet.\nHeart a song in the player to save it here!",
                        isDark        = isDark,
                        primaryText   = primaryText,
                        secondaryText = secondaryText,
                        onDismiss     = { showLikedSongs = false },
                        onPlayTrack   = { track ->
                            musicPlayerViewModel?.playFromRecommendation(track.name, track.artist) { ok, _ ->
                                if (ok) { showLikedSongs = false; onNavigateToMusicPlayer() }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SimpleTrackSheet — shared sheet for Recently Played and Liked Songs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SimpleTrackSheet(
    title: String,
    emoji: String,
    tracks: List<Track>,
    isLoading: Boolean,
    emptyMessage: String,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onPlayTrack: (Track) -> Unit
) {
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
                Text("${tracks.size} songs", fontSize = 13.sp, color = secondaryText)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "Close", tint = secondaryText)
            }
        }

        HorizontalDivider(color = if (isDark) Color(0xFF2A2A3A) else Color(0xFFEEEEEE))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading…", fontSize = 14.sp, color = secondaryText)
                    }
                }
            }
            tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(emoji, fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(emptyMessage, fontSize = 14.sp, color = secondaryText, textAlign = TextAlign.Center)
                    }
                }
            }
            else -> {
                LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track ->
                        PlaylistTrackRow(
                            track         = track,
                            isPlaying     = false,
                            isDark        = isDark,
                            primaryText   = primaryText,
                            secondaryText = secondaryText,
                            onClick       = { onPlayTrack(track) }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist Detail Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistDetailSheet(
    playlist: SpotifyPlaylistInfo,
    tracks: List<Track>,
    isLoading: Boolean,
    errorMessage: String?,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit
) {
    var playingTrackId by remember { mutableStateOf<String?>(null) }
    val listState       = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model              = playlist.imageUrl,
                    contentDescription = playlist.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Text("🎵", fontSize = 24.sp) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(playlist.ownerName, fontSize = 13.sp, color = secondaryText)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "Close", tint = secondaryText)
            }
        }

        // ── Play All / Shuffle ────────────────────────────────────────
        if (tracks.isNotEmpty()) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick  = onPlayAll,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play All", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick  = {
                        // Shuffle: play a random track
                        val random = tracks.randomOrNull()
                        if (random != null) { playingTrackId = random.id; onPlayTrack(random) }
                    },
                    modifier = Modifier.height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1DB954))
                ) {
                    Icon(Icons.Filled.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = if (isDark) Color(0xFF2A2A3A) else Color(0xFFEEEEEE))

        // ── Track list ────────────────────────────────────────────────
        // "~" is a sentinel meaning: restricted playlist, but we found fallback tracks
        val isFallback = errorMessage == "~"
        val isHardError = errorMessage != null && !isFallback

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading tracks…", fontSize = 14.sp, color = secondaryText)
                    }
                }
            }
            isHardError -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("😕", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage!!, fontSize = 14.sp, color = secondaryText, textAlign = TextAlign.Center)
                    }
                }
            }
            tracks.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎵", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No tracks found", fontSize = 14.sp, color = secondaryText)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state          = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier       = Modifier.fillMaxSize()
                ) {
                    // Soft info banner for fallback results
                    if (isFallback) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFF3CD))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ℹ️", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Direct playlist access is restricted. Showing similar tracks based on playlist name.",
                                    fontSize = 12.sp,
                                    color    = Color(0xFF664D03)
                                )
                            }
                        }
                    }

                    items(tracks, key = { it.id }) { track ->
                        PlaylistTrackRow(
                            track         = track,
                            isPlaying     = playingTrackId == track.id,
                            isDark        = isDark,
                            primaryText   = primaryText,
                            secondaryText = secondaryText,
                            onClick       = {
                                playingTrackId = track.id
                                onPlayTrack(track)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist track row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTrackRow(
    track: Track,
    isPlaying: Boolean,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    val thumbBg = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF0F0F0)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isPlaying) Color(0xFF1DB954).copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            if (track.albumArtUrl.isNotEmpty()) {
                AsyncImage(
                    model              = track.albumArtUrl,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Text("🎵", fontSize = 20.sp)
            }
            // Playing overlay
            if (isPlaying) {
                Box(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1DB954).copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                track.name,
                fontSize   = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color      = if (isPlaying) Color(0xFF1DB954) else primaryText,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                fontSize = 12.sp, color = secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        val durationText = remember(track.durationMs) {
            if (track.durationMs > 0L) {
                val s = track.durationMs / 1000
                "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
            } else ""
        }
        if (durationText.isNotEmpty()) {
            Text(durationText, fontSize = 12.sp, color = secondaryText)
            Spacer(Modifier.width(8.dp))
        }

        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                "Play",
                tint     = if (isPlaying) Color(0xFF1DB954) else secondaryText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable composables (unchanged from original)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryItem(
    icon: ImageVector, iconBg: Color,
    title: String, subtitle: String,
    primaryText: Color, secondaryText: Color, iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
            Text(subtitle, fontSize = 13.sp, color = secondaryText)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

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
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(placeholderBg),
                contentAlignment = Alignment.Center
            ) { Text("🎵", fontSize = 22.sp) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlist.ownerName, fontSize = 12.sp, color = secondaryText, maxLines = 1)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

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
            Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, fontSize = 13.sp, color = secondaryText, maxLines = 1)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Favorite, "Remove", tint = Color.Red, modifier = Modifier.size(20.dp))
        }
    }
}