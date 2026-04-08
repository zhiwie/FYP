package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.fypdraft.model.ALL_ACCESSORIES
import com.example.fypdraft.model.AccessoryCategory
import com.example.fypdraft.model.MascotMood
import com.example.fypdraft.model.PersonalityProfile
import com.example.fypdraft.model.PetAIBrain
import com.example.fypdraft.model.PetAIState
import com.example.fypdraft.model.PetAnimation
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
    onEditMascot: () -> Unit = {},
    onOpenChat: (message: String?) -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    isPlayingMusic: Boolean = false,
    personalityProfile: PersonalityProfile = PersonalityProfile(),
    modifier: Modifier = Modifier
) {
    var tapCount         by remember { mutableStateOf(0) }
    var showHearts       by remember { mutableStateOf(false) }
    var currentAnimation by remember(mood.mood) { mutableStateOf(petState.animationForMood()) }
    var showCustomize    by remember { mutableStateOf(false) }

    val equalizerColors = remember(mood.mood) { getEqualizerColors(mood.mood) }

    val brain             = remember { PetAIBrain() }
    var aiState           by remember { mutableStateOf(PetAIState.IDLE) }
    var thoughtState      by remember { mutableStateOf(PetThought.NONE) }
    var customThoughtText by remember { mutableStateOf("") }

    LaunchedEffect(personalityProfile.personalityType) {
        brain.applyPersonalityParams(
            wanderFrequencyMs   = personalityProfile.personalityType.wanderFrequencyMs,
            idleSpeedMultiplier = personalityProfile.personalityType.idleSpeedMultiplier,
            thoughtEmojis       = personalityProfile.getThoughtBubbles()
        )
    }

    var roomSize    by remember { mutableStateOf(IntSize(300, 140)) }
    val petPosX     = remember { Animatable(0.5f) }
    val petPosY     = remember { Animatable(0.5f) }
    var isCrouching by remember { mutableStateOf(false) }

    LaunchedEffect(isPlayingMusic) {
        while (true) {
            delay(500)
            val newState = brain.update(
                isMusicPlaying = isPlayingMusic, isUserScrolling = false, isUserTapping = false,
                boundsWidth = roomSize.width.toFloat(), boundsHeight = roomSize.height.toFloat()
            )
            aiState = newState; thoughtState = brain.currentThought; customThoughtText = brain.customThoughtEmoji
            currentAnimation = when (newState) {
                PetAIState.GROOVY        -> PetAnimation.DANCING
                PetAIState.DOZY         -> PetAnimation.IDLE
                PetAIState.EXCITED      -> PetAnimation.HAPPY_BOUNCE
                PetAIState.CURIOUS      -> PetAnimation.IDLE
                PetAIState.DISAPPOINTED -> PetAnimation.IDLE
                else -> if (tapCount > 0) currentAnimation else petState.animationForMood()
            }
            if (newState == PetAIState.WANDERING) {
                val target = brain.wanderTarget
                val w = roomSize.width.coerceAtLeast(1); val h = roomSize.height.coerceAtLeast(1)
                isCrouching = true; delay(200); isCrouching = false
                petPosX.animateTo((target.x / w).coerceIn(0.1f, 0.9f),
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
                petPosY.animateTo((target.y / h).coerceIn(0.15f, 0.85f),
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
            }
            if (newState == PetAIState.GROOVY && petPosX.value != 0.5f) {
                petPosX.animateTo(0.5f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                petPosY.animateTo(0.45f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            showHearts = true; brain.forceState(PetAIState.EXCITED)
            currentAnimation = when {
                tapCount >= 5 -> PetAnimation.LOVE_EYES
                tapCount >= 3 -> PetAnimation.DANCING
                tapCount >= 2 -> PetAnimation.HAPPY_BOUNCE
                else          -> PetAnimation.DANCING
            }
            delay(if (tapCount >= 3) 3000 else 2000)
            showHearts = false; currentAnimation = petState.animationForMood()
        }
    }

    LaunchedEffect(isPlayingMusic) {
        if (isPlayingMusic && tapCount == 0)       currentAnimation = PetAnimation.DANCING
        else if (!isPlayingMusic && tapCount == 0) currentAnimation = petState.animationForMood()
    }

    val breathingTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by breathingTransition.animateFloat(
        1f, if (aiState == PetAIState.DOZY) 1.06f else 1.03f,
        infiniteRepeatable(tween(when { isPlayingMusic -> 800; aiState == PetAIState.DOZY -> 3500; else -> 2500 }, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breathScale"
    )
    val bounceY by breathingTransition.animateFloat(
        0f, if (isPlayingMusic && aiState == PetAIState.GROOVY) -10f else -2f,
        infiniteRepeatable(tween(if (isPlayingMusic) 350 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bounceY"
    )
    val swayAngle by breathingTransition.animateFloat(-3f, 3f,
        infiniteRepeatable(tween(if (aiState == PetAIState.CURIOUS) 1200 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sway"
    )
    val tapSquishX by animateFloatAsState(
        if (tapCount > 0 && showHearts) if (tapCount >= 3) 1.15f else 1.08f else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh), label = "tapSquishX"
    )
    val tapSquishY by animateFloatAsState(
        if (tapCount > 0 && showHearts) if (tapCount >= 3) 0.88f else 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh), label = "tapSquishY"
    )
    val crouchScale by animateFloatAsState(
        if (isCrouching) 0.85f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "crouch"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(if (aiState == PetAIState.DOZY) 8000L else Random.nextLong(3000, 6000))
            isBlinking = true
            delay(if (aiState == PetAIState.DOZY) 400L else 150L)
            isBlinking = false
        }
    }

    if (showCustomize) {
        PetCustomiseSheet(
            petState    = petState, petRepository = petRepository,
            onDismiss   = { showCustomize = false },
            onVisitShop = { showCustomize = false; onEditMascot() }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            // Edit icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd).size(32.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)).clickable { showCustomize = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Edit, "Customise", tint = Color.White, modifier = Modifier.size(16.dp)) }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${petState.name} · Lv.${petState.level}", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                // Pet room — tap = Talking Tom reaction
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { roomSize = it }
                        .pointerInput(Unit) { detectTapGestures(onTap = { tapCount++ }) }
                ) {
                    AudioVisualizerView(
                        isPlaying = isPlayingMusic, mood = mood.mood,
                        barColors = equalizerColors, audioSessionId = 0,
                        modifier  = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val petW = 100.dp.toPx(); val petH = 100.dp.toPx()
                            translationX = (petPosX.value * (size.width - petW)) - (size.width - petW) / 2
                            translationY = (petPosY.value * (size.height - petH)) - (size.height - petH) / 2 + bounceY
                            scaleX    = breathScale * crouchScale * tapSquishX
                            scaleY    = breathScale * (if (isCrouching) 1.1f else 1f) * crouchScale * tapSquishY
                            rotationZ = when (aiState) { PetAIState.CURIOUS, PetAIState.GROOVY -> swayAngle; PetAIState.DOZY -> swayAngle * 0.3f; else -> 0f }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(90.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
                        LottiePetView(petState = petState, animation = currentAnimation, isPlaying = isPlayingMusic, modifier = Modifier.size(80.dp))
                        if (isBlinking || aiState == PetAIState.DOZY) {
                            Box(Modifier.size(80.dp).graphicsLayer { scaleY = if (aiState == PetAIState.DOZY) 0.88f else 0.93f })
                        }
                        if (showHearts) FloatingHeartsEffect()
                        if (thoughtState != PetThought.NONE) {
                            val bubbleEmoji = if (thoughtState == PetThought.CUSTOM) customThoughtText else thoughtState.emoji
                            if (bubbleEmoji.isNotEmpty()) {
                                ThoughtBubbleView(emoji = bubbleEmoji,
                                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-10).dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // XP bar
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text("XP", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.1f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(petState.xpProgress).clip(RoundedCornerShape(3.dp)).background(Color(0xFFFFD700)))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${petState.xp}/${petState.xpForNextLevel}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                }

                Spacer(Modifier.height(12.dp))

                // ── TAP TO CHAT BUTTON ────────────────────────────────────────────────────
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(null) }
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat with ${petState.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                text     = chatMessage ?: "Tell me how you're feeling…",
                                fontSize = 12.sp,
                                color    = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // ArrowForwardIos is the correct Material icon for a right-pointing chevron
                        Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Mood label + change button
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Feeling ${mood.mood}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.25f), modifier = Modifier.clickable { onChangeMood() }) {
                        Text("Change", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// PetCustomiseSheet
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun PetCustomiseSheet(
    petState: PetState, petRepository: PetRepository,
    onDismiss: () -> Unit, onVisitShop: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var nameInput   by remember { mutableStateOf(petState.name) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Customise ${petState.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Species", "Accessories", "Name").forEachIndexed { i, label ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                            Text(label, Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (selectedTab) {
                        0 -> {
                            Text("Choose your mascot species", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                            PetType.values().toList().chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { type ->
                                        val sel = petState.type == type
                                        Surface(
                                            shape  = RoundedCornerShape(16.dp),
                                            color  = if (sel) Color(0xFF6A5ACD).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6A5ACD)) else null,
                                            modifier = Modifier.weight(1f).clickable { petRepository.changePetType(type) }
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                                PixelPet(petState = petState.copy(type = type), animation = PetAnimation.IDLE, modifier = Modifier.size(72.dp))
                                                Spacer(Modifier.height(8.dp))
                                                Text(type.displayName, fontSize = 13.sp,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sel) Color(0xFF6A5ACD) else MaterialTheme.colorScheme.onSurface)
                                                if (sel) { Spacer(Modifier.height(4.dp)); Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(18.dp)) }
                                            }
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onVisitShop, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Filled.Store, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text("Visit Pet Shop", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        1 -> {
                            AccessoryCategory.values().forEach { cat ->
                                Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(ALL_ACCESSORIES.filter { it.category == cat }) { acc ->
                                        val unlocked = acc.requiredLevel <= petState.level
                                        val equipped = when (acc.category) {
                                            AccessoryCategory.HAT      -> petState.equippedHat == acc.id
                                            AccessoryCategory.GLASSES  -> petState.equippedGlasses == acc.id
                                            AccessoryCategory.NECKLACE -> petState.equippedNecklace == acc.id
                                            AccessoryCategory.OUTFIT   -> petState.equippedOutfit == acc.id
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = when { equipped -> Color(0xFF6A5ACD).copy(alpha = 0.2f); unlocked -> MaterialTheme.colorScheme.surfaceVariant; else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) },
                                            modifier = Modifier.width(76.dp).clickable(enabled = unlocked) {
                                                if (equipped) petRepository.unequipCategory(acc.category) else petRepository.equipAccessory(acc)
                                            }
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                                Text(acc.emoji, fontSize = 26.sp)
                                                Text(acc.name, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
                                                when { !unlocked -> Text("Lv.${acc.requiredLevel}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); equipped -> Text("Equipped", fontSize = 8.sp, color = Color(0xFF6A5ACD)) }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        2 -> {
                            OutlinedTextField(value = nameInput, onValueChange = { if (it.length <= 12) nameInput = it },
                                label = { Text("Pet name (max 12 chars)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { petRepository.renamePet(nameInput) }, modifier = Modifier.fillMaxWidth(),
                                enabled = nameInput.isNotBlank() && nameInput != petState.name) { Text("Save Name") }
                            Spacer(Modifier.height(20.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Pet Stats", fontWeight = FontWeight.Bold, fontSize = 13.sp); Spacer(Modifier.height(6.dp))
                                    Text("Level: ${petState.level}", fontSize = 12.sp)
                                    Text("XP: ${petState.xp}/${petState.xpForNextLevel}", fontSize = 12.sp)
                                    Text("Songs played: ${petState.totalSongsPlayed}", fontSize = 12.sp)
                                    Text("Happiness: ${petState.happiness}%", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PetCustomizeDialogView(petState: PetState, petRepository: PetRepository, onDismiss: () -> Unit) {
    PetCustomiseSheet(petState = petState, petRepository = petRepository, onDismiss = onDismiss, onVisitShop = onDismiss)
}

@Composable
private fun ThoughtBubbleView(emoji: String, modifier: Modifier = Modifier) {
    val alpha  by rememberInfiniteTransition(label = "tbPulse").animateFloat(0.7f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "tbAlpha")
    val floatY by rememberInfiniteTransition(label = "tbFloat").animateFloat(0f, -4f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "tbFloatY")
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha; translationY = floatY }.background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(emoji, fontSize = 16.sp)
    }
}

@Composable
private fun FloatingHeartsEffect() {
    listOf("💖", "✨", "💕").forEachIndexed { i, heart ->
        val inf = rememberInfiniteTransition(label = "heart_$i")
        val y by inf.animateFloat(0f, -50f, infiniteRepeatable(tween(800 + i * 200, easing = EaseOut), RepeatMode.Restart), label = "hy$i")
        val a by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart), label = "ha$i")
        Text(heart, fontSize = 16.sp, modifier = Modifier.offset(x = (-16 + i * 16).dp, y = y.dp).graphicsLayer { alpha = a })
    }
}

private fun getEqualizerColors(mood: String): List<Color> = when (mood) {
    "happy"     -> listOf(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFFFF6347))
    "sad"       -> listOf(Color(0xFF6495ED), Color(0xFF7B68EE), Color(0xFF9370DB))
    "calm"      -> listOf(Color(0xFF87CEEB), Color(0xFF98D8C8), Color(0xFFB0E0E6))
    "energetic" -> listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00))
    "tired"     -> listOf(Color(0xFF607D8B), Color(0xFF78909C), Color(0xFF90A4AE))
    "focused"   -> listOf(Color(0xFF00E676), Color(0xFF00BFA5), Color(0xFF1DE9B6))
    "romantic"  -> listOf(Color(0xFFFF6B9D), Color(0xFFC471ED), Color(0xFFFF9A8B))
    else        -> listOf(Color(0xFF9C27B0), Color(0xFF7C4DFF), Color(0xFFE040FB))
}