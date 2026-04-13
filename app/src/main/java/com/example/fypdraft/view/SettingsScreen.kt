package com.example.fypdraft.view

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
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

// ─────────────────────────────────────────────────────────────────────────────
// Sheet type enum
// ─────────────────────────────────────────────────────────────────────────────

private enum class SheetType {
    NONE, ACCOUNT, PRIVACY, NOTIFICATIONS, MUSIC_AI, SOCIAL, APPEARANCE, SUPPORT
}

// ─────────────────────────────────────────────────────────────────────────────
// Google Apps Script endpoint (support tickets)
// ─────────────────────────────────────────────────────────────────────────────

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
            val code   = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            JSONObject(stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "{}")
        } finally { conn.disconnect() }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Prefs helper — SharedPreferences backed settings
// ─────────────────────────────────────────────────────────────────────────────

private object SettingsPrefs {
    private const val NAME = "moodsync_settings"

    fun get(ctx: android.content.Context) =
        ctx.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)!!

    // Privacy
    fun privateProfile(ctx: android.content.Context)      = get(ctx).getBoolean("private_profile", false)
    fun showListening(ctx: android.content.Context)        = get(ctx).getBoolean("show_listening", true)

    // Notifications
    fun notifVibeSnap(ctx: android.content.Context)        = get(ctx).getBoolean("notif_vibe_snap", true)
    fun notifFriendRequest(ctx: android.content.Context)   = get(ctx).getBoolean("notif_friend_req", true)
    fun notifVibeReaction(ctx: android.content.Context)    = get(ctx).getBoolean("notif_vibe_react", true)
    fun notifRecommend(ctx: android.content.Context)       = get(ctx).getBoolean("notif_recommend", true)
    fun notifUpdates(ctx: android.content.Context)         = get(ctx).getBoolean("notif_updates", false)

    // Music & AI
    fun streamingQuality(ctx: android.content.Context)     = get(ctx).getString("streaming_quality", "Standard") ?: "Standard"
    fun downloadQuality(ctx: android.content.Context)      = get(ctx).getString("download_quality", "Standard") ?: "Standard"
    fun mascotLevel(ctx: android.content.Context)          = get(ctx).getFloat("mascot_level", 1f)
    fun mascotVisible(ctx: android.content.Context)        = get(ctx).getBoolean("mascot_visible", true)
    fun discoveryLevel(ctx: android.content.Context)       = get(ctx).getFloat("discovery_level", 0.5f)

    // Social
    fun syncContacts(ctx: android.content.Context)         = get(ctx).getBoolean("sync_contacts", false)

    fun save(ctx: android.content.Context, key: String, value: Any) {
        val ed = get(ctx).edit()
        when (value) {
            is Boolean -> ed.putBoolean(key, value)
            is Float   -> ed.putFloat(key, value)
            is String  -> ed.putString(key, value)
        }
        ed.apply()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeState: AppThemeState = AppThemeState(),
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {}
) {
    val ctx   = LocalContext.current
    val auth  = FirebaseAuth.getInstance()
    val user  = auth.currentUser
    val scope = rememberCoroutineScope()

    val isDark        = themeState.isDark
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val dividerColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)
    val sheetBg       = if (isDark) Color(0xFF1C1C2E) else Color.White
    val cardBg        = if (isDark) Color(0xFF242438) else Color(0xFFF8F8FC)

    var activeSheet       by remember { mutableStateOf(SheetType.NONE) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }

    // User info state
    var username by remember { mutableStateOf(user?.displayName ?: "User") }
    var email    by remember { mutableStateOf(user?.email ?: "") }

    // ── Section definitions ───────────────────────────────────────────────
    data class SettingsSection(
        val icon: ImageVector,
        val iconTint: Color,
        val title: String,
        val subtitle: String,
        val sheet: SheetType
    )

    val sections = listOf(
        SettingsSection(Icons.Filled.ManageAccounts, Color(0xFF5C6BC0), "Account & Privacy",     "Profile, password, security, data",    SheetType.ACCOUNT),
        SettingsSection(Icons.Filled.Notifications,  Color(0xFFEF6C00), "Notifications",          "Vibes, reactions, recommendations",    SheetType.NOTIFICATIONS),
        SettingsSection(Icons.Filled.MusicNote,      Color(0xFF1DB954), "Music & AI",             "Quality, mascot, discovery level",     SheetType.MUSIC_AI),
        SettingsSection(Icons.Filled.Group,          Color(0xFF0288D1), "Social & Connections",   "Contacts, linked accounts, blocked",   SheetType.SOCIAL),
        SettingsSection(Icons.Filled.Palette,        Color(0xFFAB47BC), "Appearance",             "Theme, dark mode, accent colors",      SheetType.APPEARANCE),
        SettingsSection(Icons.Filled.HelpOutline,    Color(0xFF00897B), "Support & Feedback",     "Help, report a bug, clear cache",      SheetType.SUPPORT),
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = primaryText)
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
            ) {
                // ── Profile card ─────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable { activeSheet = SheetType.ACCOUNT },
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .background(Color(0xFF5C6BC0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                username.take(1).uppercase(),
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color(0xFF5C6BC0)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(username, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryText)
                            Text(email, fontSize = 13.sp, color = secondaryText)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = secondaryText, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Settings sections ─────────────────────────────────────
                sections.forEach { section ->
                    SettingsSectionCard(
                        icon      = section.icon,
                        iconTint  = section.iconTint,
                        title     = section.title,
                        subtitle  = section.subtitle,
                        cardBg    = cardBg,
                        textColor = primaryText,
                        subColor  = secondaryText,
                        onClick   = { activeSheet = section.sheet }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Log Out ───────────────────────────────────────────────
                Button(
                    onClick  = { showSignOutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(50.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF2A1A1A) else Color(0xFFFFF0F0),
                        contentColor   = Color(0xFFE53935)
                    )
                ) {
                    Icon(Icons.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))

                // ── Delete Account ────────────────────────────────────────
                TextButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    Text("Delete Account", color = Color(0xFFE53935).copy(alpha = 0.7f), fontSize = 13.sp)
                }

                Spacer(Modifier.height(32.dp))

                // ── App version ───────────────────────────────────────────
                Text(
                    "MoodSync v1.0.0",
                    fontSize = 12.sp,
                    color    = secondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Bottom Sheet overlay ──────────────────────────────────────────
        AnimatedVisibility(
            visible = activeSheet != SheetType.NONE,
            enter   = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit    = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { activeSheet = SheetType.NONE }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                    shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors    = CardDefaults.cardColors(containerColor = sheetBg),
                    elevation = CardDefaults.cardElevation(20.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Drag handle
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.width(36.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isDark) Color(0xFF3A3A5A) else Color(0xFFDDDDDD))
                            )
                        }
                        when (activeSheet) {
                            SheetType.ACCOUNT -> AccountPrivacySheet(
                                username          = username,
                                email             = email,
                                isDark            = isDark,
                                onUsernameChanged = { username = it },
                                onDismiss         = { activeSheet = SheetType.NONE }
                            )
                            SheetType.NOTIFICATIONS -> NotificationsSheet(
                                isDark    = isDark,
                                ctx       = ctx,
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            SheetType.MUSIC_AI -> MusicAiSheet(
                                isDark    = isDark,
                                ctx       = ctx,
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            SheetType.SOCIAL -> SocialConnectionsSheet(
                                isDark    = isDark,
                                ctx       = ctx,
                                onDismiss = { activeSheet = SheetType.NONE }
                            )
                            SheetType.APPEARANCE -> AppearanceSheet(
                                isDark            = isDark,
                                themeState        = themeState,
                                onNavigateToTheme = { activeSheet = SheetType.NONE; onNavigateToTheme() },
                                onDismiss         = { activeSheet = SheetType.NONE }
                            )
                            SheetType.SUPPORT -> SupportSheet(
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
                    Text("Log Out", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
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
            title = { Text("Delete Account", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(
                        "This action is permanent and cannot be undone. All your data, mascot progress, and listening history will be deleted.",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value                = deletePassword,
                        onValueChange        = { deletePassword = it },
                        label                = { Text("Enter password to confirm") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine           = true,
                        modifier             = Modifier.fillMaxWidth(),
                        shape                = RoundedCornerShape(12.dp)
                    )
                    if (deleteError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError!!, color = Color(0xFFE53935), fontSize = 12.sp)
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
                                    db.collection("contactHashes").document(uid).delete().await()
                                    db.collection("moodHistory").document(uid).delete().await()
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
                    else Text("Delete Forever", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings section card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(
    icon: ImageVector, iconTint: Color,
    title: String, subtitle: String,
    cardBg: Color, textColor: Color, subColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(subtitle, fontSize = 12.sp, color = subColor)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = subColor, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheet shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetTitle(text: String, color: Color) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetSubtitle(text: String, color: Color) {
    Text(text, fontSize = 13.sp, color = color)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SectionDivider(label: String, color: Color, divColor: Color) {
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = divColor)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(
    label: String, description: String? = null,
    checked: Boolean, textColor: Color, subColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = textColor, fontWeight = FontWeight.Medium)
            if (description != null)
                Text(description, fontSize = 12.sp, color = subColor, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor       = Color.White,
                checkedTrackColor       = Color(0xFF1DB954),
                uncheckedThumbColor     = Color.White,
                uncheckedTrackColor     = Color(0xFFCCCCCC)
            )
        )
    }
}

@Composable
private fun QualitySelector(
    label: String,
    selected: String,
    options: List<String>,
    textColor: Color,
    subColor: Color,
    cardBg: Color,
    onSelect: (String) -> Unit
) {
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSelected = opt == selected
            Surface(
                modifier  = Modifier.weight(1f).clickable { onSelect(opt) },
                shape     = RoundedCornerShape(10.dp),
                color     = if (isSelected) Color(0xFF1DB954) else cardBg,
                shadowElevation = if (isSelected) 4.dp else 0.dp
            ) {
                Text(
                    opt,
                    modifier    = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    fontSize    = 12.sp,
                    fontWeight  = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color       = if (isSelected) Color.White else subColor,
                    textAlign   = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Account & Privacy Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountPrivacySheet(
    username: String, email: String, isDark: Boolean,
    onUsernameChanged: (String) -> Unit, onDismiss: () -> Unit
) {
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val ctx   = LocalContext.current

    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)

    var editUsername    by remember { mutableStateOf(username) }
    var currentPw       by remember { mutableStateOf("") }
    var newPw           by remember { mutableStateOf("") }
    var confirmPw       by remember { mutableStateOf("") }
    var showPw          by remember { mutableStateOf(false) }
    var message         by remember { mutableStateOf<String?>(null) }
    var isSaving        by remember { mutableStateOf(false) }
    var isSuccess       by remember { mutableStateOf(false) }

    // Privacy toggles — loaded from prefs
    var privateProfile  by remember { mutableStateOf(SettingsPrefs.privateProfile(ctx)) }
    var showListening   by remember { mutableStateOf(SettingsPrefs.showListening(ctx)) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Account & Privacy", textColor)
        SheetSubtitle("Manage your profile and what others see", subColor)

        // ── Edit Profile ──────────────────────────────────────────────
        SectionDivider("Profile", subColor, divColor)
        Text("Username", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = editUsername,
            onValueChange = { editUsername = it },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            placeholder   = { Text("Your username") }
        )
        Spacer(Modifier.height(12.dp))
        Text("Email", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = email,
            onValueChange = {},
            enabled       = false,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp)
        )

        // ── Social Privacy ────────────────────────────────────────────
        SectionDivider("Social Privacy", subColor, divColor)
        ToggleRow(
            label       = "Private Profile",
            description = "Hide your Vibe from non-friends",
            checked     = privateProfile,
            textColor   = textColor,
            subColor    = subColor
        ) {
            privateProfile = it
            SettingsPrefs.save(ctx, "private_profile", it)
            // Sync to Firestore
            scope.launch {
                runCatching {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(auth.currentUser?.uid ?: "")
                        .update("privateProfile", it)
                        .await()
                }
            }
        }
        ToggleRow(
            label       = "Show Listening Activity",
            description = "Let friends see what you're playing live",
            checked     = showListening,
            textColor   = textColor,
            subColor    = subColor
        ) {
            showListening = it
            SettingsPrefs.save(ctx, "show_listening", it)
            scope.launch {
                runCatching {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(auth.currentUser?.uid ?: "")
                        .update("showListening", it)
                        .await()
                }
            }
        }

        // ── Security ──────────────────────────────────────────────────
        SectionDivider("Security", subColor, divColor)

        // 2FA info row (placeholder — real 2FA needs Firebase phone auth)
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = Color(0xFF5C6BC0).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Two-Factor Authentication", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text("Extra layer of security for your account", fontSize = 12.sp, color = subColor)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF5C6BC0)) {
                    Text("Soon", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Manage devices row
        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = Color(0xFF5C6BC0).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Devices, null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Manage Devices", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text("See where you're logged in", fontSize = 12.sp, color = subColor)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF5C6BC0)) {
                    Text("Soon", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Change Password ───────────────────────────────────────────
        SectionDivider("Change Password", subColor, divColor)
        OutlinedTextField(
            value                = currentPw,
            onValueChange        = { currentPw = it },
            label                = { Text("Current password") },
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon         = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                }
            },
            modifier  = Modifier.fillMaxWidth(),
            singleLine = true,
            shape     = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = newPw,
            onValueChange        = { newPw = it },
            label                = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            shape                = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = confirmPw,
            onValueChange        = { confirmPw = it },
            label                = { Text("Confirm new password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            shape                = RoundedCornerShape(12.dp)
        )

        // ── Data Management ───────────────────────────────────────────
        SectionDivider("Data Management", subColor, divColor)
        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = Color(0xFF00897B).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().clickable { /* TODO: generate data export */ }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = Color(0xFF00897B), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Download Personal Data", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text("See what MoodSync AI has learned about you", fontSize = 12.sp, color = subColor)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = subColor, modifier = Modifier.size(18.dp))
            }
        }

        // Message / status
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message!!,
                    Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color    = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFE53935)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    isSaving = true; message = null; isSuccess = false
                    try {
                        if (editUsername != username && editUsername.isNotBlank()) {
                            val update = UserProfileChangeRequest.Builder()
                                .setDisplayName(editUsername).build()
                            auth.currentUser?.updateProfile(update)?.await()
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(auth.currentUser?.uid ?: "")
                                .update("displayName", editUsername, "username", editUsername.lowercase().trim())
                                .await()
                            onUsernameChanged(editUsername)
                        }
                        if (newPw.isNotBlank()) {
                            if (newPw != confirmPw)  { message = "Passwords don't match"; isSaving = false; return@launch }
                            if (newPw.length < 6)    { message = "Password must be at least 6 characters"; isSaving = false; return@launch }
                            val cred = EmailAuthProvider.getCredential(email, currentPw)
                            auth.currentUser?.reauthenticate(cred)?.await()
                            auth.currentUser?.updatePassword(newPw)?.await()
                        }
                        message = "✓ Changes saved!"; isSuccess = true
                    } catch (e: Exception) { message = e.message ?: "Failed to save" }
                    isSaving = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            enabled  = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = subColor)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Notifications Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsSheet(
    isDark: Boolean,
    ctx: android.content.Context,
    onDismiss: () -> Unit
) {
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)

    var vibeSnap     by remember { mutableStateOf(SettingsPrefs.notifVibeSnap(ctx)) }
    var friendReq    by remember { mutableStateOf(SettingsPrefs.notifFriendRequest(ctx)) }
    var vibeReaction by remember { mutableStateOf(SettingsPrefs.notifVibeReaction(ctx)) }
    var recommend    by remember { mutableStateOf(SettingsPrefs.notifRecommend(ctx)) }
    var appUpdates   by remember { mutableStateOf(SettingsPrefs.notifUpdates(ctx)) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Notifications", textColor)
        SheetSubtitle("Choose exactly what pings you want", subColor)

        SectionDivider("Social Alerts", subColor, divColor)
        ToggleRow(
            label       = "Vibe-Snap Reminders",
            description = "Prompt to share a photo when a great song plays",
            checked     = vibeSnap,
            textColor   = textColor,
            subColor    = subColor
        ) { vibeSnap = it; SettingsPrefs.save(ctx, "notif_vibe_snap", it) }

        ToggleRow(
            label       = "New Friend Requests",
            description = "When someone finds you via contacts or socials",
            checked     = friendReq,
            textColor   = textColor,
            subColor    = subColor
        ) { friendReq = it; SettingsPrefs.save(ctx, "notif_friend_req", it) }

        ToggleRow(
            label       = "Vibe Reactions",
            description = "When a friend reacts to your shared music",
            checked     = vibeReaction,
            textColor   = textColor,
            subColor    = subColor
        ) { vibeReaction = it; SettingsPrefs.save(ctx, "notif_vibe_react", it) }

        SectionDivider("Music & System", subColor, divColor)

        ToggleRow(
            label       = "Personalised Recommendations",
            description = "New music the AI thinks you'll love",
            checked     = recommend,
            textColor   = textColor,
            subColor    = subColor
        ) { recommend = it; SettingsPrefs.save(ctx, "notif_recommend", it) }

        ToggleRow(
            label       = "App Updates & New Features",
            description = "News about mascot skins and new tools",
            checked     = appUpdates,
            textColor   = textColor,
            subColor    = subColor
        ) { appUpdates = it; SettingsPrefs.save(ctx, "notif_updates", it) }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
        ) { Text("Done", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Music & AI Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MusicAiSheet(
    isDark: Boolean,
    ctx: android.content.Context,
    onDismiss: () -> Unit
) {
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)
    val cardBg    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF0F0F8)

    val qualityOptions = listOf("Data Saver", "Standard", "High Fidelity")

    var streamQuality  by remember { mutableStateOf(SettingsPrefs.streamingQuality(ctx)) }
    var downloadQuality by remember { mutableStateOf(SettingsPrefs.downloadQuality(ctx)) }
    var mascotLevel    by remember { mutableStateOf(SettingsPrefs.mascotLevel(ctx)) }
    var mascotVisible  by remember { mutableStateOf(SettingsPrefs.mascotVisible(ctx)) }
    var discoveryLevel by remember { mutableStateOf(SettingsPrefs.discoveryLevel(ctx)) }

    val mascotLabels    = listOf("Quiet", "Helpful", "Frequent")
    val discoveryLabels = listOf("Familiar", "Balanced", "Discovery")

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Music & AI", textColor)
        SheetSubtitle("Control quality, your mascot, and how adventurous the AI is", subColor)

        // ── Audio Quality ─────────────────────────────────────────────
        SectionDivider("Audio Quality", subColor, divColor)

        QualitySelector(
            label    = "Streaming Quality",
            selected = streamQuality,
            options  = qualityOptions,
            textColor = textColor,
            subColor  = subColor,
            cardBg    = cardBg,
            onSelect  = { streamQuality = it; SettingsPrefs.save(ctx, "streaming_quality", it) }
        )

        QualitySelector(
            label    = "Download Quality",
            selected = downloadQuality,
            options  = qualityOptions,
            textColor = textColor,
            subColor  = subColor,
            cardBg    = cardBg,
            onSelect  = { downloadQuality = it; SettingsPrefs.save(ctx, "download_quality", it) }
        )

        // ── Mascot ────────────────────────────────────────────────────
        SectionDivider("Mascot Configuration", subColor, divColor)

        ToggleRow(
            label       = "Show Mascot",
            description = "Display floating mascot across the app",
            checked     = mascotVisible,
            textColor   = textColor,
            subColor    = subColor
        ) { mascotVisible = it; SettingsPrefs.save(ctx, "mascot_visible", it) }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Interaction Level", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(
                mascotLabels[(mascotLevel * 2).toInt().coerceIn(0, 2)],
                fontSize   = 13.sp,
                color      = Color(0xFF1DB954),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "How chatty and reactive your mascot is",
            fontSize = 12.sp, color = subColor
        )
        Slider(
            value         = mascotLevel,
            onValueChange = { mascotLevel = it; SettingsPrefs.save(ctx, "mascot_level", it) },
            valueRange    = 0f..1f,
            steps         = 1,
            colors        = SliderDefaults.colors(
                thumbColor       = Color(0xFF1DB954),
                activeTrackColor = Color(0xFF1DB954)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            mascotLabels.forEach { Text(it, fontSize = 11.sp, color = subColor) }
        }

        // ── Discovery Level ───────────────────────────────────────────
        SectionDivider("Recommendation Adventure", subColor, divColor)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Discovery Level", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(
                discoveryLabels[(discoveryLevel * 2f).toInt().coerceIn(0, 2)],
                fontSize   = 13.sp,
                color      = Color(0xFF1DB954),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "How far the AI ventures outside your comfort zone",
            fontSize = 12.sp, color = subColor
        )
        Slider(
            value         = discoveryLevel,
            onValueChange = { discoveryLevel = it; SettingsPrefs.save(ctx, "discovery_level", it) },
            valueRange    = 0f..1f,
            steps         = 1,
            colors        = SliderDefaults.colors(
                thumbColor       = Color(0xFF1DB954),
                activeTrackColor = Color(0xFF1DB954)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            discoveryLabels.forEach { Text(it, fontSize = 11.sp, color = subColor) }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
        ) { Text("Done", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Social & Connections Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SocialConnectionsSheet(
    isDark: Boolean,
    ctx: android.content.Context,
    onDismiss: () -> Unit
) {
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)

    var syncContacts      by remember { mutableStateOf(SettingsPrefs.syncContacts(ctx)) }
    var instagramLinked   by remember { mutableStateOf(false) }
    var facebookLinked    by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Social & Connections", textColor)
        SheetSubtitle("Manage how you find and connect with friends", subColor)

        SectionDivider("Contact Sync", subColor, divColor)
        ToggleRow(
            label       = "Sync Contacts",
            description = "Periodically check your contacts for MoodSync users",
            checked     = syncContacts,
            textColor   = textColor,
            subColor    = subColor
        ) { syncContacts = it; SettingsPrefs.save(ctx, "sync_contacts", it) }

        SectionDivider("Linked Accounts", subColor, divColor)

        // Instagram
        LinkedAccountRow(
            platform  = "Instagram",
            emoji     = "📸",
            color     = Color(0xFFE1306C),
            linked    = instagramLinked,
            textColor = textColor,
            subColor  = subColor,
            onToggle  = { instagramLinked = it }
        )
        Spacer(Modifier.height(8.dp))

        // Facebook
        LinkedAccountRow(
            platform  = "Facebook",
            emoji     = "👥",
            color     = Color(0xFF1877F2),
            linked    = facebookLinked,
            textColor = textColor,
            subColor  = subColor,
            onToggle  = { facebookLinked = it }
        )

        SectionDivider("Blocked Users", subColor, divColor)
        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5FA),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Block, null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Manage Blocked Users", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text("No blocked users", fontSize = 12.sp, color = subColor)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = subColor, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
        ) { Text("Done", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LinkedAccountRow(
    platform: String, emoji: String, color: Color,
    linked: Boolean,
    textColor: Color, subColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = color.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(platform, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Text(if (linked) "Connected" else "Not connected", fontSize = 12.sp, color = if (linked) color else subColor)
            }
            Switch(
                checked         = linked,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = color,
                    uncheckedTrackColor = Color(0xFFCCCCCC)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Appearance Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppearanceSheet(
    isDark: Boolean,
    themeState: AppThemeState,
    onNavigateToTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)
    val cardBg    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5FA)

    var themeMode by remember { mutableStateOf(if (isDark) "Dark" else "Light") }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Appearance", textColor)
        SheetSubtitle("Tune the visual vibe of the app", subColor)

        SectionDivider("Theme Mode", subColor, divColor)

        listOf("Light" to "☀️", "Dark" to "🌙", "System" to "📱").forEach { (mode, emoji) ->
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = if (themeMode == mode) Color(0xFF1A1A2E).copy(alpha = if (isDark) 0.6f else 1f) else cardBg,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { themeMode = mode }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(mode, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = if (themeMode == mode) Color.White else textColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (themeMode == mode) {
                        Icon(Icons.Filled.Check, null, tint = Color(0xFF1DB954), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        SectionDivider("Mood Colors & Theme", subColor, divColor)

        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = Color(0xFFAB47BC).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToTheme() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, null, tint = Color(0xFFAB47BC), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Theme & Mood Colors", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text(
                        "Current: ${themeState.activePalette.accent.let { "Custom" }}  •  Dynamic mood mode",
                        fontSize = 12.sp, color = subColor
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = subColor, modifier = Modifier.size(18.dp))
            }
        }

        SectionDivider("App Icon", subColor, divColor)

        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = cardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎨", fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Custom App Icon", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                    Text("Match your mascot's style", fontSize = 12.sp, color = subColor)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFAB47BC)) {
                    Text("Premium", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
        ) { Text("Done", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Support & Feedback Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupportSheet(
    userName: String, userEmail: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val scope  = rememberCoroutineScope()
    val ctx    = LocalContext.current

    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)
    val cardBg    = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5FA)
    val inputBg   = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF5F5F5)

    var tab         by remember { mutableStateOf(0) } // 0=FAQ, 1=Contact, 2=Feature
    var category    by remember { mutableStateOf("General Feedback") }
    var description by remember { mutableStateOf("") }
    var isSending   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }

    val categories = listOf("General Feedback", "Bug Report", "Feature Request", "Account Issue", "Music / Spotify Issue", "Other")
    val categoryEmoji = mapOf(
        "General Feedback" to "💬", "Bug Report" to "🐛", "Feature Request" to "✨",
        "Account Issue" to "🔐", "Music / Spotify Issue" to "🎵", "Other" to "📋"
    )

    val faqItems = listOf(
        "How does mood detection work?" to "MoodSync analyses what you're listening to and uses our AI to infer your emotional state. You can also set it manually.",
        "Can I use MoodSync without Spotify?" to "Yes — you can browse recommendations and use the AI chat, but playback requires Spotify connected.",
        "How do I level up my mascot?" to "Play music, react to vibes, complete daily missions, and feed your mascot snacks to earn XP.",
        "Is my listening data private?" to "By default your listening activity is shared with friends. Toggle 'Private Profile' in Account & Privacy to hide it.",
        "How do I cancel a Listen Together session?" to "Tap the purple 'End' button on the Listen Together banner in the Friends screen."
    )

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Support & Feedback", textColor)
        SheetSubtitle("Help, bug reports, feature ideas", subColor)

        // Tab bar
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(4.dp)
        ) {
            listOf("FAQ", "Contact", "Feature").forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (tab == i) Color(0xFF1A1A2E) else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (tab == i) Color.White else subColor
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (tab) {
            // ── FAQ ───────────────────────────────────────────────────
            0 -> {
                faqItems.forEachIndexed { i, (q, a) ->
                    var expanded by remember { mutableStateOf(false) }
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = cardBg,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { expanded = !expanded }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.weight(1f))
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    null, tint = subColor, modifier = Modifier.size(20.dp)
                                )
                            }
                            AnimatedVisibility(visible = expanded) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    Text(a, fontSize = 13.sp, color = subColor, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }

                // Clear Cache
                SectionDivider("Storage", subColor, divColor)
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = cardBg,
                    modifier = Modifier.fillMaxWidth().clickable {
                        // Clear app cache
                        try { ctx.cacheDir.deleteRecursively(); cacheCleared = true } catch (_: Exception) {}
                    }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CleaningServices, null, tint = Color(0xFF00897B), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Clear Cache", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Text(
                                if (cacheCleared) "✓ Cache cleared!" else "Free up storage without deleting your data",
                                fontSize = 12.sp,
                                color    = if (cacheCleared) Color(0xFF1DB954) else subColor
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = subColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Contact / Bug Report ──────────────────────────────────
            1 -> {
                if (isSubmitted) {
                    Card(
                        colors   = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Message Sent!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "We typically respond within 3–5 business days.",
                                fontSize = 13.sp, color = Color(0xFF388E3C),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text("Category", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    categories.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick  = { category = cat },
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
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Description", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = description,
                        onValueChange = { if (it.length <= 500) description = it },
                        placeholder   = { Text("Describe your issue in detail...", color = subColor) },
                        modifier      = Modifier.fillMaxWidth().height(140.dp),
                        maxLines      = 8,
                        shape         = RoundedCornerShape(12.dp)
                    )
                    Text("${description.length}/500", fontSize = 11.sp, color = subColor, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))

                    if (errorMsg != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(errorMsg!!, fontSize = 12.sp, color = Color(0xFFE53935))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (description.isBlank()) { errorMsg = "Please describe your issue."; return@Button }
                            errorMsg = null; isSending = true
                            scope.launch {
                                try {
                                    val payload = JSONObject().apply {
                                        put("name", userName.ifBlank { "MoodSync User" })
                                        put("email", userEmail)
                                        put("category", category)
                                        put("subject", category)
                                        put("message", description)
                                        put("appVersion", "1.0.0")
                                    }
                                    val result = postTicket(payload)
                                    if (result.optBoolean("ok", false)) isSubmitted = true
                                    else errorMsg = result.optString("error", "Submission failed. Please try again.")
                                } catch (e: Exception) {
                                    errorMsg = "Network error: ${e.message ?: "Check your connection."}"
                                }
                                isSending = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        enabled  = !isSending,
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Submitting…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Submit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Feature Request ───────────────────────────────────────
            2 -> {
                var featureIdea  by remember { mutableStateOf("") }
                var featureSent  by remember { mutableStateOf(false) }
                var featureSending by remember { mutableStateOf(false) }
                var featureError by remember { mutableStateOf<String?>(null) }

                if (featureSent) {
                    Card(
                        colors   = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✨", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Thanks for your idea!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("We read every suggestion.", fontSize = 13.sp, color = Color(0xFF388E3C))
                        }
                    }
                } else {
                    Text(
                        "Have an idea for a mascot interaction, music tool, or social feature? Tell us!",
                        fontSize = 13.sp, color = subColor, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = featureIdea,
                        onValueChange = { if (it.length <= 300) featureIdea = it },
                        placeholder   = { Text("My idea is…", color = subColor) },
                        modifier      = Modifier.fillMaxWidth().height(130.dp),
                        maxLines      = 6,
                        shape         = RoundedCornerShape(12.dp)
                    )
                    Text("${featureIdea.length}/300", fontSize = 11.sp, color = subColor, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))

                    if (featureError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(featureError!!, color = Color(0xFFE53935), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (featureIdea.isBlank()) { featureError = "Please describe your idea."; return@Button }
                            featureError = null; featureSending = true
                            scope.launch {
                                try {
                                    val payload = JSONObject().apply {
                                        put("name", userName.ifBlank { "MoodSync User" })
                                        put("email", userEmail)
                                        put("category", "Feature Request")
                                        put("subject", "Feature Request")
                                        put("message", featureIdea)
                                        put("appVersion", "1.0.0")
                                    }
                                    val result = postTicket(payload)
                                    if (result.optBoolean("ok", false)) featureSent = true
                                    else featureError = result.optString("error", "Submission failed.")
                                } catch (e: Exception) { featureError = "Network error." }
                                featureSending = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(14.dp),
                        enabled  = !featureSending,
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        if (featureSending) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Sending…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Send Idea ✨", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close", color = subColor)
        }
        Spacer(Modifier.height(24.dp))
    }
}