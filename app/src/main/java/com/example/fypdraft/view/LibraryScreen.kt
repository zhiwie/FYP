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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(
    onBack: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Playlists", "Artists", "Albums")

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
                text = "Your Library",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* TODO: Add/create playlist */ }) {
                Icon(Icons.Filled.Add, "Add", tint = Color.Black)
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 13.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1A1A2E),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Library items
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Favorites section
            item {
                LibraryItem(
                    icon = Icons.Filled.Favorite,
                    iconBg = Color(0xFFFF6B6B),
                    title = "Liked Songs",
                    subtitle = "Your favorites",
                    onClick = onNavigateToFavorites
                )
            }

            // Recently played
            item {
                LibraryItem(
                    icon = Icons.Filled.History,
                    iconBg = Color(0xFF6A5ACD),
                    title = "Recently Played",
                    subtitle = "Jump back in",
                    onClick = { /* TODO */ }
                )
            }

            // Mood playlists (auto-generated)
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your Mood Playlists",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val moodPlaylists = listOf(
                Triple("Happy Vibes 😊", "32 songs", Color(0xFFFFB347)),
                Triple("Chill Zone 😌", "18 songs", Color(0xFF89CFF0)),
                Triple("Workout Energy ⚡", "24 songs", Color(0xFFFF416C)),
                Triple("Late Night 🌙", "15 songs", Color(0xFF2C3E50)),
                Triple("Focus Mode 🎯", "20 songs", Color(0xFF11998E))
            )

            items(moodPlaylists) { (title, subtitle, color) ->
                LibraryItem(
                    icon = Icons.Filled.QueueMusic,
                    iconBg = color,
                    title = title,
                    subtitle = subtitle,
                    onClick = { /* TODO */ }
                )
            }

            // Empty space at bottom for mini player
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun LibraryItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
}