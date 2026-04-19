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
import com.example.fypdraft.ui.theme.AppThemeState
import kotlinx.coroutines.delay
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  MascotWidget
//
//  Key change from previous version:
//  • The old LottiePetView / PixelPet call (line ~290) is replaced with
//    LiveAvatar — which layers shadow + body state + accessories at runtime.
//  • isBlinking is now handled entirely inside LiveAvatar so there is no
//    duplicate blink logic here.
//  • All other widget structure, AI brain, tabs, chat button, etc. is retained.
// ─────────────────────────────────────────────────────────────────────────────

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
    themeState: AppThemeState = AppThemeState(),
    modifier: Modifier = Modifier
) {
    val isDark = themeState.isDark

    // ── Theme colours ─────────────────────────────────────────────────────
    val widgetSurface    = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.28f)
    val primaryText      = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText    = if (isDark) Color(0xFFBBBBCC) else Color(0xFF444455)
    val editIconBg       = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFF1A1A2E).copy(alpha = 0.15f)
    val editIconTint     = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val xpTrack          = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)
    val xpText           = if (isDark) Color(0xFFAAAAAA) else Color(0xFF444455)
    val chatBtnBg        = if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFF1A1A2E).copy(alpha = 0.10f)
    val chatIconBg       = if (isDark) Color.White.copy(alpha = 0.20f) else Color(0xFF1A1A2E).copy(alpha = 0.15f)
    val chatIconTint     = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val chatTitleColor   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val chatSubColor     = if (isDark) Color(0xFFAAAAAA) else Color(0xFF555566)
    val chatArrowTint    = if (isDark) Color.White.copy(alpha = 0.50f) else Color(0xFF1A1A2E).copy(alpha = 0.40f)
    val feelingTextColor = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val changeBtnBg      = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFF1A1A2E).copy(alpha = 0.12f)
    val changeBtnText    = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)

    // ── Widget state ──────────────────────────────────────────────────────
    var tapCount         by remember { mutableStateOf(0) }
    var showHearts       by remember { mutableStateOf(false) }
    var showCustomize    by remember { mutableStateOf(false) }

    val equalizerColors  = remember(mood.mood) { getEqualizerColors(mood.mood) }

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

    // ── AI brain loop ─────────────────────────────────────────────────────
    LaunchedEffect(isPlayingMusic) {
        while (true) {
            delay(500)
            val newState = brain.update(
                isMusicPlaying = isPlayingMusic, isUserScrolling = false, isUserTapping = false,
                boundsWidth    = roomSize.width.toFloat(), boundsHeight = roomSize.height.toFloat()
            )
            aiState = newState
            thoughtState = brain.currentThought
            customThoughtText = brain.customThoughtEmoji

            if (newState == PetAIState.WANDERING) {
                val target = brain.wanderTarget
                val w = roomSize.width.coerceAtLeast(1); val h = roomSize.height.coerceAtLeast(1)
                isCrouching = true; delay(200); isCrouching = false
                petPosX.animateTo(
                    (target.x / w).coerceIn(0.1f, 0.9f),
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
                petPosY.animateTo(
                    (target.y / h).coerceIn(0.15f, 0.85f),
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
            }
            if (newState == PetAIState.GROOVY && petPosX.value != 0.5f) {
                petPosX.animateTo(0.5f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                petPosY.animateTo(0.45f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    // ── Tap reaction ──────────────────────────────────────────────────────
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            showHearts = true
            brain.forceState(PetAIState.EXCITED)
            delay(if (tapCount >= 3) 3000 else 2000)
            showHearts = false
        }
    }

    // ── Physical animations (wander / squish) ─────────────────────────────
    val breathingTransition = rememberInfiniteTransition(label = "breathing")
    val bounceY by breathingTransition.animateFloat(
        0f,
        if (isPlayingMusic && aiState == PetAIState.GROOVY) -10f else -2f,
        infiniteRepeatable(tween(if (isPlayingMusic) 350 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bounceY"
    )
    val swayAngle by breathingTransition.animateFloat(
        -3f, 3f,
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

    if (showCustomize) {
        PetCustomiseSheet(
            petState      = petState,
            petRepository = petRepository,
            onDismiss     = { showCustomize = false },
            onVisitShop   = { showCustomize = false; onEditMascot() }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(widgetSurface, shape = RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            // ── Edit icon ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd).size(32.dp).clip(CircleShape)
                    .background(editIconBg).clickable { showCustomize = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, "Customise", tint = editIconTint, modifier = Modifier.size(16.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

                // Pet name + level
                Text(
                    "${petState.name} · Lv.${petState.level}",
                    fontSize = 12.sp, color = secondaryText, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                // ── Pet room ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { roomSize = it }
                        .pointerInput(Unit) { detectTapGestures(onTap = { tapCount++ }) }
                ) {
                    // Animated equalizer background
                    AudioVisualizerView(
                        isPlaying    = isPlayingMusic, mood = mood.mood,
                        barColors    = equalizerColors, audioSessionId = 0,
                        modifier     = Modifier.fillMaxSize()
                    )

                    // Pet position + squish transforms
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val petW = 100.dp.toPx(); val petH = 100.dp.toPx()
                                translationX = (petPosX.value * (size.width - petW)) - (size.width - petW) / 2
                                translationY = (petPosY.value * (size.height - petH)) - (size.height - petH) / 2 + bounceY
                                scaleX    = crouchScale * tapSquishX
                                scaleY    = (if (isCrouching) 1.1f else 1f) * crouchScale * tapSquishY
                                rotationZ = when (aiState) {
                                    PetAIState.CURIOUS, PetAIState.GROOVY -> swayAngle
                                    PetAIState.DOZY -> swayAngle * 0.3f
                                    else -> 0f
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle glow ring behind avatar
                        Box(
                            Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // ══ LAYERED AVATAR — replaces LottiePetView/PixelPet ══
                        // LiveAvatar handles:
                        //   • Shadow layer
                        //   • Body state image (idle/sleepy/sad/listening/blink)
                        //   • Accessory overlays (sunglasses, tie)
                        //   • Auto-blink every 4-7 seconds
                        //   • Bounce + breathe + sway animations
                        LiveAvatar(
                            petState       = petState,
                            isMusicPlaying = isPlayingMusic,
                            equippedIds    = petState.avatarEquippedIds(),
                            size           = 88.dp,
                            facingRight    = true,
                            isDragging     = false,
                            modifier       = Modifier.size(88.dp)
                        )

                        // Floating hearts on tap
                        if (showHearts) FloatingHeartsEffect()

                        // Thought bubble
                        if (thoughtState != PetThought.NONE) {
                            val bubbleEmoji = if (thoughtState == PetThought.CUSTOM) customThoughtText
                            else thoughtState.emoji
                            if (bubbleEmoji.isNotEmpty()) {
                                ThoughtBubbleView(
                                    emoji    = bubbleEmoji,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 20.dp, y = (-10).dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // XP bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text("XP", fontSize = 10.sp, color = xpText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(xpTrack)) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(petState.xpProgress)
                                .clip(RoundedCornerShape(3.dp)).background(Color(0xFFFFD700))
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("${petState.xp}/${petState.xpForNextLevel}", fontSize = 10.sp, color = xpText)
                }

                Spacer(Modifier.height(12.dp))

                // Chat button
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = chatBtnBg,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(null) }
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier         = Modifier.size(36.dp).clip(CircleShape).background(chatIconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Chat, null, tint = chatIconTint, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat with ${petState.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = chatTitleColor)
                            Text(
                                text     = chatMessage ?: "Tell me how you're feeling…",
                                fontSize = 12.sp, color = chatSubColor,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Filled.ArrowForwardIos, null, tint = chatArrowTint, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Feeling X + Change
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Feeling ${mood.mood}", fontSize = 13.sp, color = feelingTextColor, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = changeBtnBg, modifier = Modifier.clickable { onChangeMood() }) {
                        Text("Change", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = changeBtnText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PetCustomiseSheet — species + accessories + name tabs
//  (Accessories tab now shows the real layered avatar accessories)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PetCustomiseSheet(
    petState: PetState, petRepository: PetRepository,
    onDismiss: () -> Unit, onVisitShop: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var nameInput   by remember { mutableStateOf(petState.name) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    when (selectedTab) {
                        // ── Tab 0: Species ────────────────────────────────
                        // Only show the 3 species that have actual PNG assets.
                        // AvatarSpecies → PetType mapping: CAT→CAT, PENGUIN→DOG, ELEPHANT→BEAR
                        0 -> {
                            Text(
                                "Choose your mascot species", fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // The 3 supported species with their backing PetType and display name
                            val supportedSpecies = listOf(
                                Triple(AvatarSpecies.CAT,      PetType.CAT,  "Kitty"),
                                Triple(AvatarSpecies.PENGUIN,  PetType.DOG,  "Penguin"),
                                Triple(AvatarSpecies.ELEPHANT, PetType.BEAR, "Elephant")
                            )

                            // First row: Kitty + Penguin
                            // Second row: Elephant (centred)
                            supportedSpecies.chunked(2).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    row.forEach { (species, petType, label) ->
                                        val sel = petState.type == petType
                                        Surface(
                                            shape    = RoundedCornerShape(16.dp),
                                            color    = if (sel) Color(0xFF6A5ACD).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border   = if (sel) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6A5ACD)) else null,
                                            modifier = Modifier.weight(1f).clickable { petRepository.changePetType(petType) }
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier            = Modifier.padding(16.dp)
                                            ) {
                                                LayeredAvatar(
                                                    species = species,
                                                    state   = AvatarState.IDLE,
                                                    size    = 72.dp
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    label, fontSize = 13.sp,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color      = if (sel) Color(0xFF6A5ACD) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (sel) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF6A5ACD), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    // If odd row (Elephant alone), fill the other half with empty space
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // ── Tab 1: Accessories (layered system) ───────────
                        // Accessories are stored directly as "sunglasses"/"tie" in
                        // equippedGlasses/equippedNecklace — no ownership required.
                        1 -> {
                            val species = AvatarSpecies.fromPetType(petState.type)

                            // Derive equipped state directly from petState fields
                            val glassesEquipped  = petState.equippedGlasses == "sunglasses"
                            val necklaceEquipped = petState.equippedNecklace == "tie"

                            val previewAccessories = buildList {
                                if (glassesEquipped)  add(AvatarAccessoryRegistry.SUNGLASSES)
                                if (necklaceEquipped) add(AvatarAccessoryRegistry.TIE)
                            }

                            Text(
                                "Tap to toggle. One item per category.",
                                fontSize = 12.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Live preview
                            Box(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                LayeredAvatar(
                                    species     = species,
                                    state       = AvatarState.IDLE,
                                    accessories = previewAccessories,
                                    size        = 90.dp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Eyewear row
                            Text("Eyewear", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp))
                            Surface(
                                shape    = RoundedCornerShape(12.dp),
                                color    = if (glassesEquipped) Color(0xFF6A5ACD).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.width(80.dp).clickable {
                                    if (glassesEquipped) petRepository.unequipCategory(AccessoryCategory.GLASSES)
                                    else petRepository.equipLayeredAccessory("sunglasses", AccessoryCategory.GLASSES)
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)) {
                                    Text("😎", fontSize = 28.sp)
                                    Text("Sunglasses", fontSize = 10.sp, textAlign = TextAlign.Center)
                                    if (glassesEquipped) Text("Equipped", fontSize = 8.sp,
                                        color = Color(0xFF6A5ACD))
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Neckwear row
                            Text("Neckwear", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp))
                            Surface(
                                shape    = RoundedCornerShape(12.dp),
                                color    = if (necklaceEquipped) Color(0xFF6A5ACD).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.width(80.dp).clickable {
                                    if (necklaceEquipped) petRepository.unequipCategory(AccessoryCategory.NECKLACE)
                                    else petRepository.equipLayeredAccessory("tie", AccessoryCategory.NECKLACE)
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)) {
                                    Text("👔", fontSize = 28.sp)
                                    Text("Tie", fontSize = 10.sp, textAlign = TextAlign.Center)
                                    if (necklaceEquipped) Text("Equipped", fontSize = 8.sp,
                                        color = Color(0xFF6A5ACD))
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Text(
                                "More accessories coming soon in the Pet Shop!",
                                fontSize = 11.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth()
                            )
                        }

                        // ── Tab 2: Name + stats ───────────────────────────
                        2 -> {
                            OutlinedTextField(
                                value         = nameInput,
                                onValueChange = { if (it.length <= 12) nameInput = it },
                                label         = { Text("Pet name (max 12 chars)") },
                                singleLine    = true, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick  = { petRepository.renamePet(nameInput) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled  = nameInput.isNotBlank() && nameInput != petState.name
                            ) { Text("Save Name") }
                            Spacer(Modifier.height(20.dp))
                            Card(
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Pet Stats", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
//  Local private helpers (unchanged from original)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThoughtBubbleView(emoji: String, modifier: Modifier = Modifier) {
    val alpha  by rememberInfiniteTransition(label = "tbPulse").animateFloat(0.7f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "tbAlpha")
    val floatY by rememberInfiniteTransition(label = "tbFloat").animateFloat(0f, -4f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "tbFloatY")
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha; translationY = floatY }
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(emoji, fontSize = 16.sp) }
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