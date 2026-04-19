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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
// Sheet type enum  (only Account remains as a full sheet; support is inline dialog)
// ─────────────────────────────────────────────────────────────────────────────

private enum class SheetType { NONE, ACCOUNT }

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
// Prefs helper
// ─────────────────────────────────────────────────────────────────────────────

private object SettingsPrefs {
    private const val NAME = "moodsync_settings"
    fun get(ctx: android.content.Context) =
        ctx.getSharedPreferences(NAME, android.content.Context.MODE_PRIVATE)!!

    fun privateProfile(ctx: android.content.Context) = get(ctx).getBoolean("private_profile", false)
    fun showListening(ctx: android.content.Context)   = get(ctx).getBoolean("show_listening", true)
    fun mascotVisible(ctx: android.content.Context)   = get(ctx).getBoolean("mascot_visible", true)

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
    themeManager: com.example.fypdraft.ui.theme.ThemeManager? = null,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    floatingMascotEnabled: Boolean = true,
    onFloatingMascotToggle: (Boolean) -> Unit = {}
) {
    val ctx   = LocalContext.current
    val auth  = FirebaseAuth.getInstance()
    val user  = auth.currentUser
    val scope = rememberCoroutineScope()

    val isDark        = themeState.isDark
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFCCCCDD) else Color(0xFF222233)
    val sheetBg       = if (isDark) Color(0xFF1C1C2E) else Color.White
    val cardBg        = if (isDark) Color(0xFF242438) else Color(0xFFF8F8FC)
    val dividerColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)

    var activeSheet       by remember { mutableStateOf(SheetType.NONE) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showClearDialog   by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf(user?.displayName ?: "User") }
    var email    by remember { mutableStateOf(user?.email ?: "") }

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

                // ── Profile card ──────────────────────────────────────────
                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable { activeSheet = SheetType.ACCOUNT },
                    shape     = RoundedCornerShape(20.dp),
                    colors    = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape)
                                .background(Color(0xFF5C6BC0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                username.take(1).uppercase(),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF5C6BC0)
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

                // ── Quick actions grid (2 × 2) ────────────────────────────
                // Each tile is a compact card with icon + label + control inline.
                Text(
                    "Quick Settings",
                    fontSize  = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color     = secondaryText,
                    modifier  = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // Row 1: Floating mascot toggle + Theme switch
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Floating Mascot toggle
                    QuickTileSwitch(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Filled.Pets,
                        iconTint    = Color(0xFF9C27B0),
                        label       = "Floating Mascot",
                        checked     = floatingMascotEnabled,
                        cardBg      = cardBg,
                        primaryText = primaryText,
                        subText     = secondaryText,
                        onToggle    = { onFloatingMascotToggle(it) }
                    )

                    // Theme: Light / Dark toggle — switches instantly via ThemeManager
                    QuickTileSwitch(
                        modifier    = Modifier.weight(1f),
                        icon        = if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        iconTint    = if (isDark) Color(0xFF7C4DFF) else Color(0xFFFFB300),
                        label       = if (isDark) "Dark Mode" else "Light Mode",
                        checked     = isDark,
                        cardBg      = cardBg,
                        primaryText = primaryText,
                        subText     = secondaryText,
                        onToggle    = { wantDark ->
                            themeManager?.setMode(
                                if (wantDark) com.example.fypdraft.ui.theme.BackgroundMode.DYNAMIC_DARK
                                else          com.example.fypdraft.ui.theme.BackgroundMode.DYNAMIC_LIGHT
                            ) ?: onNavigateToTheme()
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Row 2: Support ticket + Clear cache
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Support ticket
                    QuickTileButton(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Filled.HeadsetMic,
                        iconTint    = Color(0xFF00897B),
                        label       = "Support",
                        sublabel    = "Send a ticket",
                        cardBg      = cardBg,
                        primaryText = primaryText,
                        subText     = secondaryText,
                        onClick     = { showSupportDialog = true }
                    )

                    // Clear cache
                    QuickTileButton(
                        modifier    = Modifier.weight(1f),
                        icon        = Icons.Filled.DeleteSweep,
                        iconTint    = Color(0xFFEF6C00),
                        label       = "Clear Cache",
                        sublabel    = "Free up space",
                        cardBg      = cardBg,
                        primaryText = primaryText,
                        subText     = secondaryText,
                        onClick     = { showClearDialog = true }
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = dividerColor)
                Spacer(Modifier.height(16.dp))

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

                TextButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    Text("Delete Account", color = Color(0xFFE53935).copy(alpha = 0.7f), fontSize = 13.sp)
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    "MoodSync v1.0.0",
                    fontSize = 12.sp, color = secondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Account & Privacy sheet ───────────────────────────────────────
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
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { activeSheet = SheetType.NONE }
            ) {
                Card(
                    modifier  = Modifier
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
                        AccountPrivacySheet(
                            username          = username,
                            email             = email,
                            isDark            = isDark,
                            onUsernameChanged = { username = it },
                            onDismiss         = { activeSheet = SheetType.NONE }
                        )
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
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.primary) } }
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
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.primary) } }
        )
    }

    // ── Support ticket dialog ─────────────────────────────────────────────
    if (showSupportDialog) {
        SupportTicketDialog(
            userName  = username,
            userEmail = email,
            isDark    = isDark,
            onDismiss = { showSupportDialog = false }
        )
    }

    // ── Clear cache dialog ────────────────────────────────────────────────
    if (showClearDialog) {
        var cleared by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
//            icon  = { Text("🧹", fontSize = 32.sp) },
            title = { Text(if (cleared) "Cache Cleared!" else "Clear Cache?") },
            text  = {
                Text(
                    if (cleared) "Local data has been cleared. The app will reload fresh data on next launch."
                    else "This will clear locally stored data and temporary files. Your account and music history are not affected.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                if (!cleared) {
                    Button(
                        onClick = {
                            ctx.cacheDir.deleteRecursively()
                            cleared = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
                    ) { Text("Clear", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                if (!cleared) TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick tile components
// ─────────────────────────────────────────────────────────────────────────────

/** A tile with a Switch inside — for boolean toggles. */
@Composable
private fun QuickTileSwitch(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    checked: Boolean,
    cardBg: Color,
    primaryText: Color,
    subText: Color,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
            // graphicsLayer scale avoids the internal Compose layout API
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor       = Color.White,
                    checkedTrackColor       = iconTint,
                    uncheckedThumbColor     = Color.White,
                    uncheckedTrackColor     = subText.copy(alpha = 0.3f)
                ),
                modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
            )
        }
    }
}

/** A tile that acts as a button — for actions. */
@Composable
private fun QuickTileButton(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    sublabel: String,
    cardBg: Color,
    primaryText: Color,
    subText: Color,
    onClick: () -> Unit
) {
    Card(
        modifier  = modifier.clickable { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
            Text(sublabel, fontSize = 11.sp, color = subText)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Account & Privacy Sheet (unchanged from before — full profile + password)
// ─────────────────────────────────────────────────────────────────────────────

/** Consistent OutlinedTextField colours that stay readable in both themes. */
@Composable
private fun outlinedFieldColors(textColor: Color, subColor: Color) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor        = textColor,
        unfocusedTextColor      = textColor,
        disabledTextColor       = subColor,
        focusedBorderColor      = Color(0xFF5C6BC0),
        unfocusedBorderColor    = subColor,
        disabledBorderColor     = subColor.copy(alpha = 0.4f),
        focusedLabelColor       = Color(0xFF5C6BC0),
        unfocusedLabelColor     = subColor,
        focusedContainerColor   = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor  = Color.Transparent
    )

@Composable
private fun AccountPrivacySheet(
    username: String, email: String, isDark: Boolean,
    onUsernameChanged: (String) -> Unit, onDismiss: () -> Unit
) {
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val ctx   = LocalContext.current

    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFDDDDEE) else Color(0xFF111122)
    val divColor  = if (isDark) Color(0xFF2A2A3A) else Color(0xFFE8E8F0)

    var editUsername by remember { mutableStateOf(username) }
    var currentPw    by remember { mutableStateOf("") }
    var newPw        by remember { mutableStateOf("") }
    var confirmPw    by remember { mutableStateOf("") }
    var showPw       by remember { mutableStateOf(false) }
    var message      by remember { mutableStateOf<String?>(null) }
    var isSaving     by remember { mutableStateOf(false) }
    var isSuccess    by remember { mutableStateOf(false) }

    var privateProfile by remember { mutableStateOf(SettingsPrefs.privateProfile(ctx)) }
    var showListening  by remember { mutableStateOf(SettingsPrefs.showListening(ctx)) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SheetTitle("Account & Privacy", textColor)
        SheetSubtitle("Manage your profile and what others see", subColor)

        // ── Profile ───────────────────────────────────────────────────
        SectionDivider("Profile", subColor, divColor)
        Text("Username", fontSize = 13.sp, color = subColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value         = editUsername,
            onValueChange = { editUsername = it },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            placeholder   = { Text("Your username", color = subColor) },
            colors        = outlinedFieldColors(textColor, subColor)
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
            shape         = RoundedCornerShape(12.dp),
            colors        = outlinedFieldColors(textColor, subColor)
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

        // ── Change Password ───────────────────────────────────────────
        SectionDivider("Change Password", subColor, divColor)
        OutlinedTextField(
            value                = currentPw,
            onValueChange        = { currentPw = it },
            label                = { Text("Current password", color = subColor) },
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon         = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = subColor)
                }
            },
            modifier   = Modifier.fillMaxWidth(),
            singleLine = true,
            shape      = RoundedCornerShape(12.dp),
            colors     = outlinedFieldColors(textColor, subColor)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = newPw,
            onValueChange        = { newPw = it },
            label                = { Text("New password", color = subColor) },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            shape                = RoundedCornerShape(12.dp),
            colors               = outlinedFieldColors(textColor, subColor)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value                = confirmPw,
            onValueChange        = { confirmPw = it },
            label                = { Text("Confirm new password", color = subColor) },
            visualTransformation = PasswordVisualTransformation(),
            modifier             = Modifier.fillMaxWidth(),
            singleLine           = true,
            shape                = RoundedCornerShape(12.dp),
            colors               = outlinedFieldColors(textColor, subColor)
        )

        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
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
// Support ticket dialog — custom Dialog+Card so colours match the app theme
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupportTicketDialog(
    userName: String,
    userEmail: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val scope       = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var isSending   by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    val bgColor   = if (isDark) Color(0xFF1C1C2E) else Color.White
    val textColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor  = if (isDark) Color(0xFFDDDDEE) else Color(0xFF111122)
    val accentColor = Color(0xFF00897B)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(20.dp),
            modifier  = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                Text("🎧", fontSize = 36.sp)
//                Spacer(Modifier.height(8.dp))
                Text(
                    if (isSubmitted) "Ticket Sent!" else "Send Support Ticket",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = textColor
                )
                Spacer(Modifier.height(12.dp))

                if (isSubmitted) {
                    Text(
                        "We've received your message and will get back to you shortly.",
                        fontSize = 14.sp,
                        color    = subColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    Text(
                        "Describe your issue below:",
                        fontSize = 13.sp,
                        color    = subColor,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = description,
                        onValueChange = { if (it.length <= 500) description = it },
                        placeholder   = { Text("Describe your issue…", color = subColor) },
                        modifier      = Modifier.fillMaxWidth().height(130.dp),
                        maxLines      = 6,
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor        = textColor,
                            unfocusedTextColor      = textColor,
                            focusedBorderColor      = accentColor,
                            unfocusedBorderColor    = subColor.copy(alpha = 0.5f),
                            focusedLabelColor       = accentColor,
                            unfocusedLabelColor     = subColor,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor             = accentColor
                        )
                    )
                    Text(
                        "${description.length}/500",
                        fontSize = 11.sp,
                        color    = subColor,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    )

                    if (errorMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, color = Color(0xFFE57373), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = subColor, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                if (description.isBlank()) { errorMsg = "Please describe your issue."; return@Button }
                                errorMsg = null; isSending = true
                                scope.launch {
                                    try {
                                        val payload = JSONObject().apply {
                                            put("name",       userName.ifBlank { "MoodSync User" })
                                            put("email",      userEmail)
                                            put("category",   "Bug Report")
                                            put("subject",    "Support Request")
                                            put("message",    description)
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
                            enabled  = !isSending,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSending) "Sending…" else "Send", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sheet components (kept for AccountPrivacySheet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetTitle(text: String, color: Color) {
    Spacer(Modifier.height(8.dp))
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetSubtitle(text: String, color: Color) {
    Text(text, fontSize = 13.sp, color = color)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider(label: String, subColor: Color, divColor: Color) {
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = divColor)
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color    = subColor
        )
        HorizontalDivider(Modifier.weight(1f), color = divColor)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ToggleRow(
    label: String, description: String,
    checked: Boolean, textColor: Color, subColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label,       fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(description, fontSize = 12.sp, color = subColor)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = Color(0xFF1DB954),
                uncheckedThumbColor = Color.White
            )
        )
    }
}