package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun MascotWidget(
    mood: MascotMood,
    petState: PetState,
    petRepository: PetRepository,
    chatMessage: String?,
    onQuickReply: (String) -> Unit,
    onTapMascot: () -> Unit,
    onChangeMood: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomize by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var showHearts by remember { mutableStateOf(false) }
    var currentAnimation by remember(mood.mood) { mutableStateOf(petState.animationForMood()) }

    // React to taps like a pet
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            showHearts = true
            currentAnimation = when {
                tapCount >= 3 -> PetAnimation.LOVE_EYES
                tapCount >= 2 -> PetAnimation.HAPPY_BOUNCE
                else -> PetAnimation.DANCING
            }
            delay(2500)
            showHearts = false
            currentAnimation = petState.animationForMood()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // Semi-transparent glassmorphism — lets dynamic background show through
                    Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            // Customize button (top-right)
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Customize pet",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clickable { showCustomize = true }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Pet name & level ─────────────────────────────────
                Text(
                    text = "${petState.name} · Lv.${petState.level}",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                // ── Pet + Equalizer area ─────────────────────────────

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            tapCount++
                            onTapMascot()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Equalizer bars behind pet
                    MoodEqualizer(mood = mood.mood, modifier = Modifier.fillMaxSize())

                    // Pixel pet in center
                    Box(contentAlignment = Alignment.Center) {
                        // Glow circle
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        )

                        PixelPet(
                            petState = petState,
                            animation = currentAnimation,
                            modifier = Modifier.size(100.dp),
                            isPlaying = false
                        )

                        // Floating hearts
                        if (showHearts) {
                            FloatingHearts()
                        }
                    }
                }

                // ── XP progress bar ──────────────────────────────────

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text("XP", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(petState.xpProgress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFFFD700))
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${petState.xp}/${petState.xpForNextLevel}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Speech bubble ────────────────────────────────────

                val displayMessage = chatMessage ?: mood.greeting
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Mood label + change ──────────────────────────────

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Feeling ${mood.mood}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.clickable { onChangeMood() }
                    ) {
                        Text(
                            "Change",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // ── Quick reply chips ────────────────────────────────

                if (mood.suggestion != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickReplyChip("Yes please!", { onQuickReply("yes") }, Modifier.weight(1f))
                        QuickReplyChip("Not now", { onQuickReply("no") }, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // ── Pet customization dialog ─────────────────────────────────────

    if (showCustomize) {
        PetCustomizeDialog(
            petState = petState,
            petRepository = petRepository,
            onDismiss = { showCustomize = false }
        )
    }
}

// ── Floating hearts ──────────────────────────────────────────────────────

@Composable
private fun FloatingHearts() {
    val hearts = listOf("💖", "✨", "💕")
    hearts.forEachIndexed { i, heart ->
        val inf = rememberInfiniteTransition(label = "heart_$i")
        val y by inf.animateFloat(0f, -50f, infiniteRepeatable(tween(800 + i * 200, easing = EaseOut), RepeatMode.Restart), label = "hy$i")
        val a by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart), label = "ha$i")
        Text(heart, fontSize = 16.sp, modifier = Modifier.offset(x = (-16 + i * 16).dp, y = y.dp).graphicsLayer { alpha = a })
    }
}

// ── Equalizer bars ───────────────────────────────────────────────────────

@Composable
private fun MoodEqualizer(mood: String, modifier: Modifier = Modifier) {
    val barCount = 14
    val colors = getEqualizerColors(mood)
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        for (i in 0 until barCount) {
            EqualizerBar(i, mood, colors[i % colors.size])
        }
    }
}

@Composable
private fun EqualizerBar(index: Int, mood: String, color: Color) {
    val inf = rememberInfiniteTransition(label = "bar_$index")
    val speed = when (mood) {
        "energetic" -> 250 + index * 40; "happy" -> 450 + index * 60
        "calm" -> 1500 + index * 150; "sad" -> 2000 + index * 200
        "tired" -> 2500 + index * 200; "focused" -> 700 + index * 80
        else -> 900 + index * 90
    }
    val minH = when (mood) { "energetic" -> 15f; "happy" -> 12f; "calm" -> 6f; "sad" -> 4f; "tired" -> 3f; else -> 8f }
    val maxH = when (mood) { "energetic" -> 90f; "happy" -> 70f; "calm" -> 40f; "sad" -> 25f; "tired" -> 20f; "focused" -> 55f; else -> 45f }

    val h by inf.animateFloat(
        minH + (index % 3) * 8f, maxH - (index % 4) * 6f,
        infiniteRepeatable(tween(speed, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bh$index"
    )
    Box(Modifier.width(6.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.35f)))
}

// ── Quick reply chip ─────────────────────────────────────────────────────

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.25f),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
    }
}

// ── Pet customization dialog ─────────────────────────────────────────────

@Composable
private fun PetCustomizeDialog(
    petState: PetState,
    petRepository: PetRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Pet Type", "Accessories", "Name")
    var nameInput by remember { mutableStateOf(petState.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize ${petState.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Tab row
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                            Text(title, modifier = Modifier.padding(vertical = 12.dp), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    // ── Pet type picker ──
                    0 -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PetType.values().forEach { type ->
                                val isSelected = petState.type == type
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Color(0xFF6A5ACD).copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { petRepository.changePetType(type) }
                                        .padding(12.dp)
                                ) {
                                    PixelPet(
                                        petState = petState.copy(type = type),
                                        animation = PetAnimation.IDLE,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(type.displayName, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ── Accessories ──
                    1 -> {
                        val categories = AccessoryCategory.values()
                        categories.forEach { category ->
                            Text(
                                category.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ALL_ACCESSORIES.filter { it.category == category }) { acc ->
                                    val unlocked = acc.requiredLevel <= petState.level
                                    val equipped = when (acc.category) {
                                        AccessoryCategory.HAT -> petState.equippedHat == acc.id
                                        AccessoryCategory.GLASSES -> petState.equippedGlasses == acc.id
                                        AccessoryCategory.NECKLACE -> petState.equippedNecklace == acc.id
                                        AccessoryCategory.OUTFIT -> petState.equippedOutfit == acc.id
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = when {
                                            equipped -> Color(0xFF6A5ACD).copy(alpha = 0.2f)
                                            unlocked -> Color(0xFFF5F5F5)
                                            else -> Color(0xFFE0E0E0)
                                        },
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable(enabled = unlocked) {
                                                if (equipped) petRepository.unequipCategory(acc.category)
                                                else petRepository.equipAccessory(acc)
                                            }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Text(acc.emoji, fontSize = 24.sp)
                                            Text(
                                                acc.name,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                            if (!unlocked) {
                                                Text("Lv.${acc.requiredLevel}", fontSize = 8.sp, color = Color.Gray)
                                            } else if (equipped) {
                                                Text("Equipped", fontSize = 8.sp, color = Color(0xFF6A5ACD))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // ── Name editor ──
                    2 -> {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { if (it.length <= 12) nameInput = it },
                            label = { Text("Pet name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { petRepository.renamePet(nameInput) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = nameInput.isNotBlank() && nameInput != petState.name
                        ) {
                            Text("Save Name")
                        }

                        Spacer(Modifier.height(16.dp))

                        // Pet stats
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pet Stats", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Level: ${petState.level}", fontSize = 12.sp)
                                Text("XP: ${petState.xp}/${petState.xpForNextLevel}", fontSize = 12.sp)
                                Text("Songs played: ${petState.totalSongsPlayed}", fontSize = 12.sp)
                                Text("Happiness: ${petState.happiness}%", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

// ── Colors ───────────────────────────────────────────────────────────────

private fun getEqualizerColors(mood: String): List<Color> = when (mood) {
    "happy" -> listOf(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFFFF6347))
    "sad" -> listOf(Color(0xFF6495ED), Color(0xFF7B68EE), Color(0xFF9370DB))
    "calm" -> listOf(Color(0xFF87CEEB), Color(0xFF98D8C8), Color(0xFFB0E0E6))
    "energetic" -> listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00))
    "tired" -> listOf(Color(0xFF607D8B), Color(0xFF78909C), Color(0xFF90A4AE))
    "focused" -> listOf(Color(0xFF00E676), Color(0xFF00BFA5), Color(0xFF1DE9B6))
    "romantic" -> listOf(Color(0xFFFF6B9D), Color(0xFFC471ED), Color(0xFFFF9A8B))
    else -> listOf(Color(0xFF9C27B0), Color(0xFF7C4DFF), Color(0xFFE040FB))
}

private fun getMoodGradient(mood: String): List<Color> = when (mood) {
    "happy" -> listOf(Color(0xFFFFB347), Color(0xFFFF6B6B))
    "sad" -> listOf(Color(0xFF667EEA), Color(0xFF764BA2))
    "calm" -> listOf(Color(0xFF89CFF0), Color(0xFF6A9BD1))
    "energetic" -> listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
    "tired" -> listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
    "focused" -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    "romantic" -> listOf(Color(0xFFEE9CA7), Color(0xFFFFC3A0))
    else -> listOf(Color(0xFF6A5ACD), Color(0xFF9B59B6))
}

