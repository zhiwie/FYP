package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Friends social feed.
 *
 * MVP: Uses mock data. In production, this would pull from Firebase
 * friends collection with real-time listeners.
 */

data class FriendActivity(
    val friendName: String,
    val avatarEmoji: String,
    val mood: String,
    val moodEmoji: String,
    val currentTrack: String?,
    val currentArtist: String?,
    val timeAgo: String,
    val isOnline: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit = {}
) {
    // Mock data — replace with Firebase query
    val friendActivities = remember {
        listOf(
            FriendActivity("Alex", "🧑", "energetic", "⚡", "Blinding Lights", "The Weeknd", "2m ago", true),
            FriendActivity("Sarah", "👩", "calm", "😌", "Weightless", "Marconi Union", "15m ago", true),
            FriendActivity("Mike", "👨", "happy", "😊", "Happy", "Pharrell Williams", "1h ago", false),
            FriendActivity("Luna", "👧", "sad", "😢", "Someone Like You", "Adele", "30m ago", true),
            FriendActivity("Jay", "🧔", "focused", "🎯", "Lo-Fi Study Beats", "ChilledCow", "5m ago", true),
            FriendActivity("Emma", "👩‍🦰", "romantic", "💕", "All of Me", "John Legend", "3h ago", false)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8FA))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Friends",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* TODO: Add friends */ }) {
                Icon(Icons.Filled.PersonAdd, "Add Friend", tint = Color.Black)
            }
        }

        // Online count
        val onlineCount = friendActivities.count { it.isOnline }
        Text(
            text = "$onlineCount friends online",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Feed
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(friendActivities) { activity ->
                FriendActivityCard(activity)
            }

            // Invite section at bottom
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎶", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Invite friends to MoodSync",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "See what your friends are vibing to",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { /* TODO: Share intent */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Invite", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendActivityCard(activity: FriendActivity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(activity.avatarEmoji, fontSize = 24.sp)
                }
                if (activity.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        activity.friendName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(activity.moodEmoji, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        activity.timeAgo,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                if (activity.currentTrack != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${activity.currentTrack} · ${activity.currentArtist}",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "Feeling ${activity.mood}",
                    fontSize = 12.sp,
                    color = Color(0xFF6A5ACD)
                )
            }
        }
    }
}