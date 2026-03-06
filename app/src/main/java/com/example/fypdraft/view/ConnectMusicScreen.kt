package com.example.fypdraft.view

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.viewmodel.SpotifyViewModel

private val DarkNavy = Color(0xFF1A1A2E)
private val SpotifyGreen = Color(0xFF1DB954)
private val YouTubeRed = Color(0xFFFF0000)

@Composable
fun ConnectMusicScreen(
    spotifyViewModel: SpotifyViewModel,
    onSkip: () -> Unit = {},
    onConnected: () -> Unit = {}
) {
    val authState by spotifyViewModel.authState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) onConnected()
    }

    var selectedPlatform by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(120.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DarkNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83C\uDFB5", fontSize = 32.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "MoodSync",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Give our AI a head\nstart. Sync app first.",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 40.sp
            )

            Spacer(Modifier.height(56.dp))

            // Platform icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Spotify
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { selectedPlatform = "spotify" }
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedPlatform == "spotify") SpotifyGreen.copy(alpha = 0.15f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u2261", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Spotify", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }

                // YouTube Music
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { selectedPlatform = "youtube" }
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedPlatform == "youtube") YouTubeRed.copy(alpha = 0.15f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(YouTubeRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u25B6", fontSize = 20.sp, color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("YouTube Music", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }

            Spacer(Modifier.weight(1f))

            // Connect button
            Button(
                onClick = {
                    when (selectedPlatform) {
                        "spotify" -> spotifyViewModel.connectSpotify(context as Activity)
                        "youtube" -> onConnected()
                        else -> { }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkNavy,
                    contentColor = Color.White
                ),
                enabled = selectedPlatform != null
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Skip for now.",
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onSkip() }
            )

            Spacer(Modifier.height(60.dp))
        }
    }
}