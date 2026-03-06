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

@Composable
fun FriendsScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 2
) {
    // Mock data — replace with Firebase friends query
    val friendActivities = remember {
        listOf(
            FriendActivity("Alex", "\uD83E\uDDD1", "energetic", "\u26A1", "Blinding Lights", "The Weeknd", "2m ago", true),
            FriendActivity("Sarah", "\uD83D\uDC69", "calm", "\uD83D\uDE0C", "Weightless", "Marconi Union", "15m ago", true),
            FriendActivity("Mike", "\uD83D\uDC68", "happy", "\uD83D\uDE0A", "Happy", "Pharrell Williams", "1h ago", false),
            FriendActivity("Luna", "\uD83D\uDC67", "sad", "\uD83D\uDE22", "Someone Like You", "Adele", "30m ago", true),
            FriendActivity("Jay", "\uD83E\uDDD4", "focused", "\uD83C\uDFAF", "Lo-Fi Study Beats", "ChilledCow", "5m ago", true),
            FriendActivity("Emma", "\uD83D\uDC69\u200D\uD83E\uDDB0", "romantic", "\uD83D\uDC95", "All of Me", "John Legend", "3h ago", false)
        )
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onHome = onNavigateToHome,
                onSearch = onNavigateToSearch,
                onFriends = { },
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Friends", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { }) { Icon(Icons.Filled.PersonAdd, "Add Friend", tint = Color.Black) }
            }

            val onlineCount = friendActivities.count { it.isOnline }
            Text("$onlineCount friends online", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Spacer(Modifier.height(8.dp))

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(friendActivities) { activity -> FriendActivityCard(activity) }

                item {
                    Spacer(Modifier.height(16.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83C\uDFB6", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Invite friends to MoodSync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("See what your friends are vibing to", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                                Text("Invite", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendActivityCard(activity: FriendActivity) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                    Text(activity.avatarEmoji, fontSize = 24.sp)
                }
                if (activity.isOnline) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).align(Alignment.BottomEnd)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50)).align(Alignment.Center))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(activity.friendName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(activity.moodEmoji, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(activity.timeAgo, fontSize = 11.sp, color = Color.Gray)
                }
                if (activity.currentTrack != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${activity.currentTrack} \u00B7 ${activity.currentArtist}", fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Feeling ${activity.mood}", fontSize = 12.sp, color = Color(0xFF6A5ACD))
            }
        }
    }
}