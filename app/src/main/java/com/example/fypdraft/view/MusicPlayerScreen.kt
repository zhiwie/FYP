package com.example.fypdraft.view

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.fypdraft.viewmodel.MusicPlayerViewModel

@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit = {}
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentSongEmotion by viewModel.currentSongEmotion.collectAsState()
    val isAnalyzingEmotion by viewModel.isAnalyzingEmotion.collectAsState()
    val spotifyConnected by viewModel.spotifyConnected.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val currentTrack = playerState.currentTrack

    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF6794D2), Color(0xFF354C6C), Color.Black)
    )

    if (currentTrack == null) {
        Box(Modifier.fillMaxSize().background(bgBrush), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Loading track...", color = Color.White.copy(alpha = 0.7f))
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(bgBrush)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Now Playing", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Album art
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.size(280.dp)
            ) {
                Crossfade(targetState = currentTrack.albumArtUrl, animationSpec = tween(400), label = "art") { artUrl ->
                    if (artUrl.isNotEmpty()) {
                        AsyncImage(model = artUrl, contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF6A5ACD), Color(0xFF2E1065)))),
                            contentAlignment = Alignment.Center
                        ) { Text("🎵", fontSize = 80.sp) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Song info
            Text(currentTrack.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(currentTrack.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // AI emotion badge
            if (isAnalyzingEmotion) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Magenta.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("🤖 Analyzing song emotion...", color = Color.White, fontSize = 12.sp)
                    }
                }
            } else if (currentSongEmotion != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Magenta.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(currentSongEmotion!!.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AI detected: ${currentSongEmotion!!.topEmotion}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(currentSongEmotion!!.confidence * 100).toInt()}% confident", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Playback status
            if (playbackError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color.Yellow)
                        Spacer(Modifier.width(8.dp))
                        Text(playbackError!!, color = Color.White, fontSize = 12.sp, maxLines = 2)
                    }
                }
            } else if (spotifyConnected) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Playing via Spotify", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color.Yellow)
                        Spacer(Modifier.width(8.dp))
                        Text("Spotify not connected. Go to Settings to connect.", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress bar
            if (playerState.duration > 0) {
                Slider(
                    value = playerState.progress,
                    onValueChange = { viewModel.seekTo(it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(playerState.currentPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(formatDuration(playerState.duration), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Playback controls
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    enabled = playerState.currentIndex > 0 || spotifyConnected
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous",
                        tint = if (playerState.currentIndex > 0 || spotifyConnected) Color.White else Color.Gray,
                        modifier = Modifier.size(40.dp))
                }

                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    containerColor = if (spotifyConnected) Color.White else Color.Gray
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.Black, modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    enabled = playerState.currentIndex < playerState.playlist.size - 1 || spotifyConnected
                ) {
                    Icon(Icons.Default.SkipNext, "Next",
                        tint = if (playerState.currentIndex < playerState.playlist.size - 1 || spotifyConnected) Color.White else Color.Gray,
                        modifier = Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // Additional controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(Icons.Default.Repeat, "Repeat", tint = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { /* TODO: Favorite */ }) {
                    Icon(Icons.Default.FavoriteBorder, "Favorite", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}