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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.ALL_ACCESSORIES
import com.example.fypdraft.model.AccessoryCategory
import com.example.fypdraft.model.MascotMood
import com.example.fypdraft.model.PersonalityProfile
import com.example.fypdraft.model.PetAIBrain
import com.example.fypdraft.model.PetAIState
import com.example.fypdraft.model.PetAnimation
import com.example.fypdraft.model.PetPersonality
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.model.PetState
import com.example.fypdraft.model.PetThought
import com.example.fypdraft.model.PetType
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MascotWidget(
    mood: MascotMood,
    petState: PetState,
    petRepository: PetRepository,
    chatMessage: String?,
    onQuickReply: (String) -> Unit,
    onTapMascot: () -> Unit,
    onChangeMood: () -> Unit,
    isPlayingMusic: Boolean = false,
    personalityProfile: PersonalityProfile = PersonalityProfile(),
    modifier: Modifier = Modifier
) {
    var showCustomize by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var showHearts by remember { mutableStateOf(false) }
    var currentAnimation by remember(mood.mood) { mutableStateOf(petState.animationForMood()) }

    val equalizerColors = remember(mood.mood) { getEqualizerColors(mood.mood) }

    // ── Pet AI Brain ─────────────────────────────────────────────────
    val brain = remember { PetAIBrain() }
    var aiState by remember { mutableStateOf(PetAIState.IDLE) }
    var thoughtState by remember { mutableStateOf(PetThought.NONE) }
    var customThoughtText by remember { mutableStateOf("") }

    // Apply personality to brain when profile changes
    LaunchedEffect(personalityProfile.personalityType) {
        brain.applyPersonalityParams(
            wanderFrequencyMs = personalityProfile.personalityType.wanderFrequencyMs,
            idleSpeedMultiplier = personalityProfile.personalityType.idleSpeedMultiplier,
            thoughtEmojis = personalityProfile.getThoughtBubbles()
        )
    }

    // Personalized greeting
    val personalizedGreeting = remember(personalityProfile, mood.mood) {
        if (personalityProfile.totalSessions > 0) {
            personalityProfile.getGreeting(petState.name)
        } else {
            mood.greeting
        }
    }

    // Room dimensions
    var roomSize by remember { mutableStateOf(IntSize(300, 140)) }

    // Pet position — explicit Float type for Animatable
    val petPosX = remember { Animatable(0.5f) }
    val petPosY = remember { Animatable(0.5f) }

    // Anticipation crouch
    var isCrouching by remember { mutableStateOf(false) }

    // ── AI update loop ───────────────────────────────────────────────
    LaunchedEffect(isPlayingMusic) {
        while (true) {
            delay(500)
            val newState = brain.update(
                isMusicPlaying = isPlayingMusic,
                isUserScrolling = false,
                isUserTapping = false,
                boundsWidth = roomSize.width.toFloat(),
                boundsHeight = roomSize.height.toFloat()
            )

            aiState = newState
            thoughtState = brain.currentThought
            customThoughtText = brain.customThoughtEmoji

            // Update animation based on AI state
            currentAnimation = when (newState) {
                PetAIState.GROOVY -> PetAnimation.DANCING
                PetAIState.DOZY -> PetAnimation.IDLE
                PetAIState.EXCITED -> PetAnimation.HAPPY_BOUNCE
                PetAIState.CURIOUS -> PetAnimation.IDLE
                PetAIState.DISAPPOINTED -> PetAnimation.IDLE
                else -> if (tapCount > 0) currentAnimation else petState.animationForMood()
            }

            // Handle wander movement
            if (newState == PetAIState.WANDERING) {
                val target = brain.wanderTarget
                val w = roomSize.width.coerceAtLeast(1)
                val h = roomSize.height.coerceAtLeast(1)
                val targetFracX = (target.x / w).coerceIn(0.1f, 0.9f)
                val targetFracY = (target.y / h).coerceIn(0.15f, 0.85f)

                // Anticipation crouch
                isCrouching = true
                delay(200)
                isCrouching = false

                // Spring-physics movement
                petPosX.animateTo(
                    targetFracX,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                petPosY.animateTo(
                    targetFracY,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
            }

            // Groovy: return to center
            if (newState == PetAIState.GROOVY && petPosX.value != 0.5f) {
                petPosX.animateTo(0.5f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                petPosY.animateTo(0.45f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    // Tap handling
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            showHearts = true
            brain.forceState(PetAIState.EXCITED)
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

    // When music plays, override to dancing
    LaunchedEffect(isPlayingMusic) {
        if (isPlayingMusic && tapCount == 0) {
            currentAnimation = PetAnimation.DANCING
        } else if (!isPlayingMusic && tapCount == 0) {
            currentAnimation = petState.animationForMood()
        }
    }

    // ── Alive animations ─────────────────────────────────────────────
    val breathingTransition = rememberInfiniteTransition(label = "breathing")

    val breathScale by breathingTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (aiState == PetAIState.DOZY) 1.06f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isPlayingMusic -> 800
                    aiState == PetAIState.DOZY -> 3500
                    else -> 2500
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val bounceY by breathingTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlayingMusic && aiState == PetAIState.GROOVY) -10f else -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlayingMusic) 350 else 3000,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    val swayAngle by breathingTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (aiState == PetAIState.CURIOUS) 1200 else 3000,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    // Crouch squash
    val crouchScale by animateFloatAsState(
        targetValue = if (isCrouching) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "crouch"
    )

    // Blink
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val interval = if (aiState == PetAIState.DOZY) 8000L else Random.nextLong(3000, 6000)
            delay(interval)
            isBlinking = true
            val blinkDur = if (aiState == PetAIState.DOZY) 400L else 150L
            delay(blinkDur)
            isBlinking = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Edit, "Customize pet",
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
                Text(
                    "${petState.name} · Lv.${petState.level}",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                // ═══ PET'S ROOM ═══
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { roomSize = it }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            tapCount++
                            onTapMascot()
                        }
                ) {
                    // Equalizer background
                    AudioVisualizerView(
                        isPlaying = isPlayingMusic,
                        mood = mood.mood,
                        barColors = equalizerColors,
                        audioSessionId = 0,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── WANDERING PET ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val petW = 100.dp.toPx()
                                val petH = 100.dp.toPx()
                                translationX = (petPosX.value * (size.width - petW)) - (size.width - petW) / 2
                                translationY = (petPosY.value * (size.height - petH)) - (size.height - petH) / 2 + bounceY

                                scaleX = breathScale * crouchScale
                                scaleY = breathScale * (if (isCrouching) 1.1f else 1f) * crouchScale

                                rotationZ = when (aiState) {
                                    PetAIState.CURIOUS, PetAIState.GROOVY -> swayAngle
                                    PetAIState.DOZY -> swayAngle * 0.3f
                                    else -> 0f
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow
                        Box(
                            Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Pet
                        LottiePetView(
                            petState = petState,
                            animation = currentAnimation,
                            isPlaying = isPlayingMusic,
                            modifier = Modifier.size(80.dp)
                        )

                        // Blink overlay
                        if (isBlinking || aiState == PetAIState.DOZY) {
                            Box(
                                Modifier
                                    .size(80.dp)
                                    .graphicsLayer {
                                        scaleY = if (aiState == PetAIState.DOZY) 0.88f else 0.93f
                                    }
                            )
                        }

                        // Hearts
                        if (showHearts) {
                            FloatingHeartsEffect()
                        }

                        // ── THOUGHT BUBBLE ──
                        val showThought = thoughtState != PetThought.NONE
                        if (showThought) {
                            val bubbleEmoji = if (thoughtState == PetThought.CUSTOM) {
                                customThoughtText
                            } else {
                                thoughtState.emoji
                            }
                            if (bubbleEmoji.isNotEmpty()) {
                                ThoughtBubbleView(
                                    emoji = bubbleEmoji,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 20.dp, y = (-10).dp)
                                )
                            }
                        }
                    }
                }

                // XP bar
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        "XP", fontSize = 10.sp,
                        color = Color.Black.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.1f))
                    ) {
                        Box(
                            Modifier
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

                // Speech bubble — uses personalized greeting
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        chatMessage ?: personalizedGreeting,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Mood label
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

                // Quick reply
                if (mood.suggestion != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickReplyChipView("Yes please!", { onQuickReply("yes") }, Modifier.weight(1f))
                        QuickReplyChipView("Not now", { onQuickReply("no") }, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showCustomize) {
        PetCustomizeDialogView(petState, petRepository) { showCustomize = false }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// THOUGHT BUBBLE
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ThoughtBubbleView(emoji: String, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "tbPulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "tbAlpha"
    )
    val floatY by rememberInfiniteTransition(label = "tbFloat").animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "tbFloatY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = floatY
            }
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(emoji, fontSize = 16.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════
// FLOATING HEARTS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingHeartsEffect() {
    val hearts = listOf("💖", "✨", "💕")
    hearts.forEachIndexed { i, heart ->
        val inf = rememberInfiniteTransition(label = "heart_$i")
        val y by inf.animateFloat(
            initialValue = 0f,
            targetValue = -50f,
            animationSpec = infiniteRepeatable(tween(800 + i * 200, easing = EaseOut), RepeatMode.Restart),
            label = "hy$i"
        )
        val a by inf.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart),
            label = "ha$i"
        )
        Text(
            heart, fontSize = 16.sp,
            modifier = Modifier
                .offset(x = (-16 + i * 16).dp, y = y.dp)
                .graphicsLayer { alpha = a }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// QUICK REPLY CHIP
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickReplyChipView(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.25f),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// PET CUSTOMIZATION DIALOG
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun PetCustomizeDialogView(
    petState: PetState,
    petRepository: PetRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var nameInput by remember { mutableStateOf(petState.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize ${petState.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Pet Type", "Accessories", "Name").forEachIndexed { i, t ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                            Text(t, modifier = Modifier.padding(vertical = 12.dp), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PetType.values().forEach { type ->
                                val sel = petState.type == type
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (sel) Color(0xFF6A5ACD).copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .clickable { petRepository.changePetType(type) }
                                        .padding(12.dp)
                                ) {
                                    PixelPet(
                                        petState = petState.copy(type = type),
                                        animation = PetAnimation.IDLE,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        type.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (sel) {
                                        Icon(
                                            Icons.Filled.CheckCircle, null,
                                            tint = Color(0xFF6A5ACD),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        AccessoryCategory.values().forEach { cat ->
                            Text(
                                cat.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ALL_ACCESSORIES.filter { it.category == cat }) { acc ->
                                    val unlocked = acc.requiredLevel <= petState.level
                                    val eq = when (acc.category) {
                                        AccessoryCategory.HAT -> petState.equippedHat == acc.id
                                        AccessoryCategory.GLASSES -> petState.equippedGlasses == acc.id
                                        AccessoryCategory.NECKLACE -> petState.equippedNecklace == acc.id
                                        AccessoryCategory.OUTFIT -> petState.equippedOutfit == acc.id
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = when {
                                            eq -> Color(0xFF6A5ACD).copy(alpha = 0.2f)
                                            unlocked -> Color(0xFFF5F5F5)
                                            else -> Color(0xFFE0E0E0)
                                        },
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable(enabled = unlocked) {
                                                if (eq) petRepository.unequipCategory(acc.category)
                                                else petRepository.equipAccessory(acc)
                                            }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Text(acc.emoji, fontSize = 24.sp)
                                            Text(
                                                acc.name, fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                            if (!unlocked) {
                                                Text("Lv.${acc.requiredLevel}", fontSize = 8.sp, color = Color.Gray)
                                            } else if (eq) {
                                                Text("Equipped", fontSize = 8.sp, color = Color(0xFF6A5ACD))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

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
                        ) { Text("Save Name") }

                        Spacer(Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pet Stats", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Level: ${petState.level}", fontSize = 12.sp)
                                Text("XP: ${petState.xp}/${petState.xpForNextLevel}", fontSize = 12.sp)
                                Text("Songs: ${petState.totalSongsPlayed}", fontSize = 12.sp)
                                Text("Happy: ${petState.happiness}%", fontSize = 12.sp)
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

// ══════════════════════════════════════════════════════════════════════════
// EQUALIZER COLORS
// ══════════════════════════════════════════════════════════════════════════

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