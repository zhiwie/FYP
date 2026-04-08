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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ── Active bottom sheet type ─────────────────────────────────────────────

private enum class SheetType { NONE, ACCOUNT, PREFERENCES, ABOUT }

// ── Google Apps Script endpoint ──────────────────────────────────────────
private const val APPS_SCRIPT_URL =
    "https://script.google.com/macros/s/AKfycbzZ0pQiymIryP5YyStmf-pm9gOu4j9tIjpKxOPAKVZwGEVlxIKVweH4J3huTWLtufcNHw/exec"

private suspend fun postTicket(payload: JSONObject): JSONObject =
    withContext(Dispatchers.IO) {
        val conn = (URL(APPS_SCRIPT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod           = "POST"
            doOutput                = true
            connectTimeout          = 15_000
            readTimeout             = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code     = conn.responseCode
            val stream   = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "{}"
            JSONObject(response)
        } finally { conn.disconnect() }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeState: AppThemeState = AppThemeState(),
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {}
) {
    val auth  = FirebaseAuth.getInstance()
    val user  = auth.currentUser
    val scope = rememberCoroutineScope()

    // ── Theme-aware colors ────────────────────────────────────────────────
    val isDark         = themeState.isDark
    val primaryText    = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val dividerColor   = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE0E0E0)
    val sheetBg        = if (isDark) Color(0xFF1C1C2E) else Color.White
    val iconTint       = if (isDark) Color(0xFF9E9EBB) else Color(0xFF666677)
    val logoutBg       = if (isDark) Color(0xFF2A1A1A) else Color(0xFFE0E0E0)

    var activeSheet       by remember { mutableStateOf(SheetType.NONE) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }

    var username        by remember { mutableStateOf(user?.displayName ?: "User") }
    var email           by remember { mutableStateOf(user?.email ?: "") }
    var notificationsOn by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Settings, null, tint = iconTint, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Settings and Privacy", fontWeight = FontWeight.SemiBold, color = primaryText)
                        }
                    },
                    navigationIcon = {
                        Row(
                            Modifier.clickable { onBack() }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ChevronLeft, "Back", modifier = Modifier.size(28.dp), tint = primaryText)
                            Text("Back", fontWeight = FontWeight.SemiBold, color = primaryText)
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

                // ── Account ──────────────────────────────────────────────
                SectionHeader(title = "Account", textColor = primaryText, dividerColor = dividerColor)
                SettingsRow("Username",     username,   primaryText, secondaryText, iconTint) { activeSheet = SheetType.ACCOUNT }
                SettingsRow("Password",     "••••••••", primaryText, secondaryText, iconTint) { activeSheet = SheetType.ACCOUNT }
                SettingsRow("Email address", email,     primaryText, secondaryText, iconTint) { activeSheet = SheetType.ACCOUNT }

                Spacer(Modifier.height(28.dp))

                // ── Notifications ─────────────────────────────────────────
                SectionHeader(title = "Notifications", textColor = primaryText, dividerColor = dividerColor)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Allow notifications", fontSize = 15.sp, color = primaryText)
                    Text(
                        if (notificationsOn) "ON" else "OFF",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (notificationsOn) Color(0xFF4CAF50) else secondaryText,
                        modifier   = Modifier.clickable { notificationsOn = !notificationsOn }
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── Preferences ───────────────────────────────────────────
                SectionHeader(title = "Preferences", textColor = primaryText, dividerColor = dividerColor)
                SettingsRow("Language",   "English",                                    primaryText, secondaryText, iconTint) { activeSheet = SheetType.PREFERENCES }
                SettingsRow("Appearance", if (themeState.isDark) "Dark Mode" else "Light Mode", primaryText, secondaryText, iconTint) { onNavigateToTheme() }

                Spacer(Modifier.height(28.dp))

                // ── Support ───────────────────────────────────────────────
                SectionHeader(title = "Support", textColor = primaryText, dividerColor = dividerColor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { activeSheet = SheetType.ABOUT }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Send Us a Message",                            fontSize = 15.sp, color = primaryText)
                        Text("Report a bug, request a feature, or get help", fontSize = 12.sp, color = secondaryText)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.weight(1f))

                // ── Log Out ───────────────────────────────────────────────
                Button(
                    onClick  = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = logoutBg, contentColor = Color.Red)
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(12.dp))

                // ── Delete Account ────────────────────────────────────────
                Button(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = logoutBg, contentColor = Color.Red)
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Bottom sheet overlay ──────────────────────────────────────────
        AnimatedVisibility(
            visible = activeSheet != SheetType.NONE,
            enter   = slideInVertically(initialOffsetY = { it }),
            exit    = slideOutVertically(targetOffsetY = { it })
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { activeSheet = SheetType.NONE }
            ) {
                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.80f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                    shape     = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors    = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Drag handle
                        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.width(40.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isDark) Color(0xFF3A3A5A) else Color.LightGray)
                            )
                        }
                        when (activeSheet) {
                            SheetType.ACCOUNT -> AccountSheet(
                                username          = username,
                                email             = email,
                                isDark            = isDark,
                                onUsernameChanged = { username = it },
                                onDismiss         = { activeSheet = SheetType.NONE }
                            )
                            SheetType.PREFERENCES -> PreferencesSheet(
                                isDark            = isDark,
                                onNavigateToTheme = { activeSheet = SheetType.NONE; onNavigateToTheme() },
                                onDismiss         = { activeSheet = SheetType.NONE }
                            )
                            SheetType.ABOUT -> SupportSheet(
                                userName  = username,
                                userEmail = email,
                                isDark    = isDark,
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // ── Sign out dialog ───────────────────────────────────────────────────
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title   = { Text("Log Out") },
            text    = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Log Out", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Delete account dialog ─────────────────────────────────────────────
    if (showDeleteDialog) {
        var deletePassword by remember { mutableStateOf("") }
        var deleteError    by remember { mutableStateOf<String?>(null) }
        var isDeleting     by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account", color = Color.Red, fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text("This action is permanent and cannot be undone. All your data will be deleted.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value                = deletePassword,
                        onValueChange        = { deletePassword = it },
                        label                = { Text("Enter password to confirm") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine           = true,
                        modifier             = Modifier.fillMaxWidth()
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
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, textColor: Color, dividerColor: Color) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 12.dp))
    HorizontalDivider(color = dividerColor)
}

// ── Settings row ──────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    label: String, value: String,
    textColor: Color, valueColor: Color, iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = textColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 15.sp, color = valueColor)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Account bottom sheet ──────────────────────────────────────────────────

@Composable
private fun AccountSheet(
    username: String, email: String, isDark: Boolean = false,
    onUsernameChanged: (String) -> Unit, onDismiss: () -> Unit
) {
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    val textColor  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val labelColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)

    var editUsername    by remember { mutableStateOf(username) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword    by remember { mutableStateOf(false) }
    var message         by remember { mutableStateOf<String?>(null) }
    var isSaving        by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Text("Edit Account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(Modifier.height(20.dp))

        Text("Username", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = editUsername, onValueChange = { editUsername = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(20.dp))
        Text("Email", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = email, onValueChange = {}, modifier = Modifier.fillMaxWidth(),
            enabled = false, singleLine = true, shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
        Text("Change Password", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = currentPassword, onValueChange = { currentPassword = it },
            label = { Text("Current password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                }
            },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPassword, onValueChange = { newPassword = it }, label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm new password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
        )

        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, fontSize = 13.sp, color = if (message!!.startsWith("✓")) Color(0xFF4CAF50) else Color.Red)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    isSaving = true; message = null
                    try {
                        if (editUsername != username && editUsername.isNotBlank()) {
                            val update = UserProfileChangeRequest.Builder().setDisplayName(editUsername).build()
                            auth.currentUser?.updateProfile(update)?.await()
                            FirebaseFirestore.getInstance().collection("users")
                                .document(auth.currentUser?.uid ?: "").update("displayName", editUsername).await()
                            onUsernameChanged(editUsername)
                        }
                        if (newPassword.isNotBlank()) {
                            if (newPassword != confirmPassword) { message = "Passwords don't match"; isSaving = false; return@launch }
                            if (newPassword.length < 6) { message = "Password must be at least 6 characters"; isSaving = false; return@launch }
                            val cred = EmailAuthProvider.getCredential(email, currentPassword)
                            auth.currentUser?.reauthenticate(cred)?.await()
                            auth.currentUser?.updatePassword(newPassword)?.await()
                        }
                        message = "✓ Changes saved!"
                    } catch (e: Exception) { message = e.message ?: "Failed to save" }
                    isSaving = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), enabled = !isSaving
        ) {
            if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Save Changes", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = labelColor)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Preferences bottom sheet ──────────────────────────────────────────────

@Composable
private fun PreferencesSheet(
    isDark: Boolean = false,
    onNavigateToTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    val textColor  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val labelColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val iconTint   = Color(0xFF6A5ACD)

    var selectedLanguage by remember { mutableStateOf("English") }
    val languages = listOf("English", "\u4e2d\u6587", "Bahasa Melayu", "\u65e5\u672c\u8a9e", "\ud55c\uad6d\uc5b4")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Text("Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(Modifier.height(20.dp))
        Text("Language", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        languages.forEach { lang ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedLanguage = lang }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedLanguage == lang, onClick = { selectedLanguage = lang })
                Spacer(Modifier.width(12.dp))
                Text(lang, fontSize = 16.sp, color = textColor)
            }
        }

        Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
        Text("Appearance", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().clickable { onNavigateToTheme() }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Palette, null, tint = iconTint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Theme & Colors",                    fontWeight = FontWeight.SemiBold, color = textColor)
                Text("Dynamic mood colors, dark mode", fontSize = 12.sp, color = labelColor)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = labelColor)
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Help & Support sheet ──────────────────────────────────────────────────

@Composable
private fun SupportSheet(
    userName: String, userEmail: String,
    isDark: Boolean = false,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val textColor  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val labelColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val inputBg    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5F5)

    var category    by remember { mutableStateOf("General Feedback") }
    var description by remember { mutableStateOf("") }
    var isSending   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }

    val categories = listOf("General Feedback","Bug Report","Feature Request","Account Issue","Music / Spotify Issue","Other")
    val categoryEmoji = mapOf(
        "General Feedback" to "💬","Bug Report" to "🐛","Feature Request" to "✨",
        "Account Issue" to "🔐","Music / Spotify Issue" to "🎵","Other" to "📋"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text("Send Us a Message", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text("We'll get back to you as soon as possible", fontSize = 13.sp, color = labelColor)
        Spacer(Modifier.height(20.dp))

        if (isSubmitted) {
            Card(
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text("Message Sent!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Spacer(Modifier.height(8.dp))
                    Text("An automatic confirmation has been sent to your email. We typically respond within 3-5 business days.",
                        fontSize = 13.sp, color = Color(0xFF388E3C))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
            ) { Text("Done", fontWeight = FontWeight.Bold) }
        } else {
            Text("Category *", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            categories.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { cat ->
                        FilterChip(
                            selected = category == cat, onClick = { category = cat },
                            label    = { Text("${categoryEmoji[cat]} $cat", fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1A1A2E),
                                selectedLabelColor     = Color.White,
                                containerColor         = inputBg,
                                labelColor             = textColor
                            )
                        )
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("Description *", fontSize = 13.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = description, onValueChange = { if (it.length <= 500) description = it },
                placeholder = { Text("Describe your issue in detail...", color = labelColor) },
                modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 8, shape = RoundedCornerShape(12.dp)
            )
            Text("${description.length}/500", fontSize = 11.sp, color = labelColor, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))

            if (errorMsg != null) {
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(errorMsg!!, fontSize = 12.sp, color = Color.Red)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (description.isBlank()) { errorMsg = "Please describe your issue."; return@Button }
                    errorMsg = null; isSending = true
                    scope.launch {
                        try {
                            val payload = JSONObject().apply {
                                put("name", userName.ifBlank { "MoodSync User" }); put("email", userEmail)
                                put("category", category); put("subject", category)
                                put("message", description); put("appVersion", "1.0.0")
                            }
                            val result = postTicket(payload)
                            if (result.optBoolean("ok", false)) isSubmitted = true
                            else errorMsg = result.optString("error", "Submission failed. Please try again.")
                        } catch (e: Exception) { errorMsg = "Network error: ${e.message ?: "Please check your connection."}" }
                        isSending = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                enabled  = !isSending, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
            ) {
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Submitting...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Submit Ticket", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = labelColor)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}