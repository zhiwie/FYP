package com.example.fypdraft.view

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Active bottom sheet type ─────────────────────────────────────────────

private enum class SheetType {
    NONE, ACCOUNT, NOTIFICATIONS, PREFERENCES, APPEARANCE, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeState: AppThemeState = AppThemeState(),
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val scope = rememberCoroutineScope()

    var activeSheet by remember { mutableStateOf(SheetType.NONE) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // User data
    var username by remember { mutableStateOf(user?.displayName ?: "User") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var notificationsOn by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        // ── Main settings page ───────────────────────────────────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Settings, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Settings and Privacy", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = {
                        Row(
                            Modifier.clickable { onBack() }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ChevronLeft, "Back", modifier = Modifier.size(28.dp))
                            Text("Back", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(animatedMoodBrushLight(themeState))
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // ── Account section ──────────────────────────────────
                SectionHeader("Account")
                SettingsRow("Username", username) { activeSheet = SheetType.ACCOUNT }
                SettingsRow("Password", "••••••••") { activeSheet = SheetType.ACCOUNT }
                SettingsRow("Email address", email) { activeSheet = SheetType.ACCOUNT }

                Spacer(Modifier.height(28.dp))

                // ── Notifications ────────────────────────────────────
                SectionHeader("Notifications")
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Allow notifications", fontSize = 15.sp)
                    Text(
                        if (notificationsOn) "ON" else "OFF",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (notificationsOn) Color(0xFF4CAF50) else Color.Gray,
                        modifier = Modifier.clickable { notificationsOn = !notificationsOn }
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── Preferences ──────────────────────────────────────
                SectionHeader("Preferences")
                SettingsRow("Language", "English") { activeSheet = SheetType.PREFERENCES }
                SettingsRow("Appearance", if (themeState.isDark) "Dark Mode" else "Light Mode") {
                    onNavigateToTheme()
                }

                Spacer(Modifier.height(28.dp))

                // ── About ────────────────────────────────────────────
                SectionHeader("About")
                SettingsRow("About MoodSync", "Version 1.0.0") { activeSheet = SheetType.ABOUT }
                SettingsRow("Help & Support", "") { }
                SettingsRow("Terms & Privacy", "") { }

                Spacer(Modifier.weight(1f))

                // ── Log Out button ───────────────────────────────────
                Button(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Red
                    )
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(12.dp))

                // ── Delete Account button ────────────────────────────
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Red
                    )
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Bottom sheet overlay ─────────────────────────────────────
        AnimatedVisibility(
            visible = activeSheet != SheetType.NONE,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { activeSheet = SheetType.NONE }
            ) {
                // The sheet itself — 75% of screen height
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) { }, // Block clicks through
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Drag handle
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.width(40.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.LightGray)
                            )
                        }

                        // Sheet content based on type
                        when (activeSheet) {
                            SheetType.ACCOUNT -> AccountSheet(
                                username = username,
                                email = email,
                                onUsernameChanged = { username = it },
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            SheetType.PREFERENCES -> PreferencesSheet(
                                onNavigateToTheme = {
                                    activeSheet = SheetType.NONE
                                    onNavigateToTheme()
                                },
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            SheetType.ABOUT -> AboutSheet(
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // ── Sign out dialog ──────────────────────────────────────────────
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Log Out", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Delete account dialog ────────────────────────────────────────
    if (showDeleteDialog) {
        var deletePassword by remember { mutableStateOf("") }
        var deleteError by remember { mutableStateOf<String?>(null) }
        var isDeleting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This action is permanent and cannot be undone. All your data will be deleted.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Enter password to confirm") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError!!, color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            try {
                                val cred = EmailAuthProvider.getCredential(email, deletePassword)
                                user?.reauthenticate(cred)?.await()
                                // Delete Firestore data
                                val uid = user?.uid
                                if (uid != null) {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("users").document(uid).delete().await()
                                    db.collection("pets").document(uid).delete().await()
                                    db.collection("moments").document(uid).delete().await()
                                }
                                user?.delete()?.await()
                                onSignOut()
                            } catch (e: Exception) {
                                deleteError = e.message ?: "Failed to delete account"
                            }
                            isDeleting = false
                        }
                    },
                    enabled = deletePassword.isNotBlank() && !isDeleting
                ) {
                    if (isDeleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Delete Forever", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Section header ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        color = Color.Black, modifier = Modifier.padding(bottom = 12.dp)
    )
    Divider(color = Color.LightGray.copy(alpha = 0.5f))
}

// ── Settings row (label + value, tappable) ───────────────────────────────

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = Color.Black)
        Text(value, fontSize = 15.sp, color = Color.Gray)
    }
}

// ── Account bottom sheet ─────────────────────────────────────────────────

@Composable
private fun AccountSheet(
    username: String,
    email: String,
    onUsernameChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var editUsername by remember { mutableStateOf(username) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Text("Edit Account", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Username
        Text("Username", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = editUsername,
            onValueChange = { editUsername = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Email (read-only)
        Text("Email", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = email,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Change Password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = { Text("Current password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm new password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, fontSize = 13.sp,
                color = if (message!!.startsWith("✓")) Color(0xFF4CAF50) else Color.Red)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    message = null
                    try {
                        // Update username
                        if (editUsername != username && editUsername.isNotBlank()) {
                            val profileUpdate = UserProfileChangeRequest.Builder()
                                .setDisplayName(editUsername).build()
                            auth.currentUser?.updateProfile(profileUpdate)?.await()
                            FirebaseFirestore.getInstance()
                                .collection("users").document(auth.currentUser?.uid ?: "")
                                .update("displayName", editUsername).await()
                            onUsernameChanged(editUsername)
                        }

                        // Update password
                        if (newPassword.isNotBlank()) {
                            if (newPassword != confirmPassword) {
                                message = "Passwords don't match"
                                isSaving = false
                                return@launch
                            }
                            if (newPassword.length < 6) {
                                message = "Password must be at least 6 characters"
                                isSaving = false
                                return@launch
                            }
                            val cred = EmailAuthProvider.getCredential(email, currentPassword)
                            auth.currentUser?.reauthenticate(cred)?.await()
                            auth.currentUser?.updatePassword(newPassword)?.await()
                        }

                        message = "✓ Changes saved!"
                    } catch (e: Exception) {
                        message = e.message ?: "Failed to save"
                    }
                    isSaving = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSaving
        ) {
            if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Save Changes", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = Color.Gray)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Preferences bottom sheet ─────────────────────────────────────────────

@Composable
private fun PreferencesSheet(
    onNavigateToTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("English") }
    val languages = listOf("English", "中文", "Bahasa Melayu", "日本語", "한국어")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Text("Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text("Language", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        languages.forEach { lang ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedLanguage = lang }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedLanguage == lang, onClick = { selectedLanguage = lang })
                Spacer(Modifier.width(12.dp))
                Text(lang, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Appearance", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().clickable { onNavigateToTheme() }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Palette, null, tint = Color(0xFF6A5ACD))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Theme & Colors", fontWeight = FontWeight.SemiBold)
                Text("Dynamic mood colors, dark mode", fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

// ── About bottom sheet ───────────────────────────────────────────────────

@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text("🎵", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("MoodSync", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.0.0", fontSize = 14.sp, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                AboutItem("🎭", "Mood-based music recommendations")
                Spacer(Modifier.height(10.dp))
                AboutItem("🤖", "AI-powered emotion detection")
                Spacer(Modifier.height(10.dp))
                AboutItem("🐱", "Virtual pet companion")
                Spacer(Modifier.height(10.dp))
                AboutItem("👥", "Social music sharing")
                Spacer(Modifier.height(10.dp))
                AboutItem("🎵", "Powered by Spotify")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Built as a Final Year Project", fontSize = 13.sp, color = Color.Gray)
        Text("© 2026 MoodSync", fontSize = 12.sp, color = Color.Gray)

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutItem(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
}