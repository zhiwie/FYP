package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEFE7FF),
            Color(0xFFFFF3D6),
            Color(0xFFDCEBFF)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Account Section
                Text(
                    text = "Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xD1FFF9F4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Filled.Person,
                            title = "Profile",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Email,
                            title = "Email Preferences",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Lock,
                            title = "Privacy",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Music Section
                Text(
                    text = "Music",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xD1FFF9F4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Filled.LibraryMusic,
                            title = "Spotify Connection",
                            subtitle = "Connect your Spotify account",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Settings,
                            title = "Playback Settings",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Download,
                            title = "Download Quality",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Mood Preferences
                Text(
                    text = "Mood Preferences",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xD1FFF9F4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Filled.Favorite,
                            title = "Mood History",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Notifications,
                            title = "Mood Reminders",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // About Section
                Text(
                    text = "About",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xD1FFF9F4)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Filled.Info,
                            title = "About MoodSync",
                            subtitle = "Version 1.0.0",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Help,
                            title = "Help & Support",
                            onClick = { /* TODO */ }
                        )
                        Divider(color = Color.LightGray)
                        SettingsItem(
                            icon = Icons.Filled.Policy,
                            title = "Terms & Privacy",
                            onClick = { /* TODO */ }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Sign Out Button
                Button(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFF7B7B7B),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Navigate",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}