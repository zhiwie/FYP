package com.example.fypdraft.view

import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Friend data from Firebase.
 */
data class FriendData(
    val uid: String,
    val displayName: String,
    val mood: String = "neutral",
    val moodEmoji: String = "🎵",
    val currentTrack: String? = null,
    val currentArtist: String? = null,
    val lastActive: Long = 0L,
    val isOnline: Boolean = false
)

@Composable
fun FriendsScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onBack: () -> Unit = {},
    currentTab: Int = 2
) {
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid

    var friends by remember { mutableStateOf<List<FriendData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }

    // Load friends list from Firebase
    LaunchedEffect(currentUserId) {
        if (currentUserId == null) { isLoading = false; return@LaunchedEffect }

        try {
            // Get friend UIDs from friends subcollection
            val friendDocs = db.collection("users")
                .document(currentUserId)
                .collection("friends")
                .get().await()

            val friendUids = friendDocs.documents.mapNotNull { it.getString("uid") }

            if (friendUids.isEmpty()) {
                friends = emptyList()
                isLoading = false
                return@LaunchedEffect
            }

            // Fetch each friend's profile + current activity
            val friendList = mutableListOf<FriendData>()
            for (uid in friendUids) {
                try {
                    val userDoc = db.collection("users").document(uid).get().await()
                    val name = userDoc.getString("displayName") ?: userDoc.getString("username") ?: "Unknown"

                    // Get latest mood
                    val moodDoc = db.collection("moodHistory").document(uid)
                        .collection("entries")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1).get().await()
                    val mood = moodDoc.documents.firstOrNull()?.getString("mood") ?: "neutral"

                    // Get latest playback
                    val playDoc = db.collection("playbackHistory").document(uid)
                        .collection("tracks")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1).get().await()
                    val trackName = playDoc.documents.firstOrNull()?.getString("title")
                    val artistName = playDoc.documents.firstOrNull()?.getString("artist")
                    val playTimestamp = playDoc.documents.firstOrNull()?.getTimestamp("timestamp")?.toDate()?.time ?: 0L

                    // Consider "online" if active in last 15 minutes
                    val isOnline = (System.currentTimeMillis() - playTimestamp) < 15 * 60 * 1000

                    friendList.add(FriendData(
                        uid = uid,
                        displayName = name,
                        mood = mood,
                        moodEmoji = getMoodEmoji(mood),
                        currentTrack = trackName,
                        currentArtist = artistName,
                        lastActive = playTimestamp,
                        isOnline = isOnline
                    ))
                } catch (e: Exception) {
                    Log.w("FriendsScreen", "Failed to load friend $uid", e)
                }
            }

            // Sort: online first, then by last active
            friends = friendList.sortedWith(compareByDescending<FriendData> { it.isOnline }.thenByDescending { it.lastActive })

        } catch (e: Exception) {
            Log.e("FriendsScreen", "Failed to load friends", e)
        }

        isLoading = false
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(currentTab, onHome = onNavigateToHome, onSearch = onNavigateToSearch, onFriends = {}, onLibrary = onNavigateToLibrary)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(Color(0xFFF8F8FA)).padding(padding)) {

            // Header
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Friends", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.PersonAdd, "Add Friend", tint = Color.Black)
                }
            }

            val onlineCount = friends.count { it.isOnline }
            if (friends.isNotEmpty()) {
                Text(
                    "$onlineCount friend${if (onlineCount != 1) "s" else ""} online",
                    fontSize = 14.sp, color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (friends.isEmpty()) {
                        item {
                            // Empty state
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("👥", fontSize = 56.sp)
                                Spacer(Modifier.height(16.dp))
                                Text("No friends yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Spacer(Modifier.height(8.dp))
                                Text("Add friends by their username to see\nwhat they're listening to!", fontSize = 14.sp, color = Color.Gray)
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add a friend")
                                }
                            }
                        }
                    } else {
                        items(friends, key = { it.uid }) { friend ->
                            FriendActivityCard(friend)
                        }
                    }

                    // Invite card
                    item {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                        ) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎶", fontSize = 32.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Invite friends to MoodSync", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("See what your friends are vibing to", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { /* TODO: Share intent */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(20.dp)
                                ) { Text("Invite", color = Color(0xFF1A1A2E), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Add friend dialog
    if (showAddDialog) {
        AddFriendDialog(
            error = addError,
            onDismiss = { showAddDialog = false; addError = null },
            onAdd = { username ->
                scope.launch {
                    addError = null
                    if (currentUserId == null) { addError = "Not logged in"; return@launch }

                    try {
                        // Search for user by username
                        val queryResult = db.collection("users")
                            .whereEqualTo("username", username.lowercase().trim())
                            .limit(1)
                            .get().await()

                        if (queryResult.isEmpty) {
                            addError = "User '$username' not found"
                            return@launch
                        }

                        val friendDoc = queryResult.documents.first()
                        val friendUid = friendDoc.id

                        if (friendUid == currentUserId) {
                            addError = "You can't add yourself!"
                            return@launch
                        }

                        // Check if already friends
                        val existing = db.collection("users").document(currentUserId)
                            .collection("friends")
                            .whereEqualTo("uid", friendUid)
                            .get().await()

                        if (!existing.isEmpty) {
                            addError = "Already friends with $username"
                            return@launch
                        }

                        // Add friend (both directions)
                        db.collection("users").document(currentUserId)
                            .collection("friends")
                            .add(mapOf("uid" to friendUid, "addedAt" to com.google.firebase.Timestamp.now()))

                        db.collection("users").document(friendUid)
                            .collection("friends")
                            .add(mapOf("uid" to currentUserId, "addedAt" to com.google.firebase.Timestamp.now()))

                        showAddDialog = false
                        addError = null

                        // Reload friends
                        val name = friendDoc.getString("displayName") ?: username
                        friends = friends + FriendData(uid = friendUid, displayName = name)

                    } catch (e: Exception) {
                        addError = "Error: ${e.message}"
                    }
                }
            }
        )
    }
}

@Composable
private fun FriendActivityCard(friend: FriendData) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar with online indicator
            Box {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(friend.displayName.take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                }
                if (friend.isOnline) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White).align(Alignment.BottomEnd)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50)).align(Alignment.Center))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(friend.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(friend.moodEmoji, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTimeAgo(friend.lastActive), fontSize = 11.sp, color = Color.Gray)
                }

                if (friend.currentTrack != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${friend.currentTrack} · ${friend.currentArtist ?: ""}",
                            fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text("Feeling ${friend.mood}", fontSize = 12.sp, color = Color(0xFF6A5ACD))
            }
        }
    }
}

@Composable
private fun AddFriendDialog(
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter their MoodSync username:", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (username.isNotBlank()) onAdd(username) },
                enabled = username.isNotBlank()
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getMoodEmoji(mood: String): String = when (mood) {
    "happy" -> "😊"; "sad" -> "😢"; "calm" -> "😌"; "energetic" -> "⚡"
    "tired" -> "😴"; "focused" -> "🎯"; "romantic" -> "💕"; else -> "🎵"
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}