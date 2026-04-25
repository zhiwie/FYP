package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.data.repository.CheckInState
import com.example.fypdraft.data.repository.DailyMood
import com.example.fypdraft.model.*
import com.example.fypdraft.ui.theme.AppThemeState
import kotlinx.coroutines.delay
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
//  BuddyHubCard
//
//  Layout (top → bottom):
//  ┌──────────────────────────────────────────────┐
//  │  Buddy · Lv.X                      [✏ Edit]  │  Header
//  │  [● Visualiser]  [○ Dino]                     │  Mode pill
//  │  ┌─────── Dynamic Content Area ─────────┐    │
//  │  │  Visualiser: equaliser + avatar        │    │
//  │  │  Dino: game with LiveAvatar sprite     │    │
//  │  └────────────────────────────────────── ┘    │
//  │  XP ████░░░░ 10/200                           │  XP bar
//  │  ┌─ Chat with Buddy ──────────────────── ┐   │  Chat btn
//  │  └───────────────────────────────────────┘   │
//  ├──────────────────────────────────────────────┤
//  │  😊 Feeling Calm   🔥 3-day  [▼ expand]      │  Streak row (collapsible)
//  │  ┌──── Expanded ────────────────────────┐    │
//  │  │  Mon Tue Wed Thu Fri Sat Sun          │    │  Weekly tracker (🔥)
//  │  │  [😊][😌][😢][😣][✍️]               │    │  Emotion circles
//  │  └──────────────────────────────────────┘    │
//  └──────────────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

enum class HubMode { VISUALISER, DINO }

@Composable
fun BuddyHubCard(
    mood               : MascotMood,
    petState           : PetState,
    petRepository      : PetRepository,
    chatMessage        : String?,
    onChangeMood       : () -> Unit,
    onEditMascot       : () -> Unit,
    onOpenChat         : (String?) -> Unit,
    isPlayingMusic     : Boolean,
    personalityProfile : PersonalityProfile,
    checkInState       : CheckInState,
    onCheckIn          : (DailyMood) -> Unit,
    onCheckInRaw       : (String) -> Unit = {},
    themeState         : AppThemeState,
    modifier           : Modifier = Modifier
) {
    val isDark        = themeState.isDark
    val accent        = themeState.activePalette.accent
    val cardBg        = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.28f)
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFBBBBCC) else Color(0xFF444455)
    val divColor      = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

    var mode           by remember { mutableStateOf(HubMode.VISUALISER) }
    var showCustomize  by remember { mutableStateOf(false) }
    // Streak section collapsed by default so mascot + songs are first glance
    var streakExpanded by remember { mutableStateOf(false) }

    val eqColors = remember(mood.mood) { hubEqualizerColors(mood.mood) }

    if (showCustomize) {
        PetCustomiseSheet(
            petState      = petState,
            petRepository = petRepository,
            onDismiss     = { showCustomize = false },
            onVisitShop   = { showCustomize = false; onEditMascot() }
        )
    }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(24.dp))) {
            Column(Modifier.fillMaxWidth()) {

                // ── 1. HEADER ─────────────────────────────────────────────
                HubHeader(petState, primaryText, secondaryText, isDark) { showCustomize = true }

                // ── 2. MODE PILL ──────────────────────────────────────────
                ModePillToggle(mode, accent, isDark) { mode = it }

                // ── 3. DYNAMIC CONTENT ────────────────────────────────────
                Crossfade(targetState = mode, animationSpec = tween(300), label = "hubContent") { m ->
                    when (m) {
                        HubMode.VISUALISER -> VisualiserWithAvatar(
                            mood, petState, isPlayingMusic, personalityProfile, eqColors,
                            primaryText, secondaryText, isDark
                        )
                        HubMode.DINO -> DinoWithAvatar(
                            themeState = themeState,
                            petState   = petState,
                            onAwardXp  = { petRepository.addXP(it) }
                        )
                    }
                }

                // ── 4. XP BAR ─────────────────────────────────────────────
                XpBarRow(petState, secondaryText, isDark)

                Spacer(Modifier.height(4.dp))

                // ── 5. CHAT BUTTON ────────────────────────────────────────
                ChatButton(petState, chatMessage, primaryText, secondaryText, isDark) { onOpenChat(null) }

                HorizontalDivider(color = divColor, thickness = 0.8.dp, modifier = Modifier.padding(top = 12.dp))

                // ── 6. STREAK SUMMARY ROW (always visible, tappable) ──────
                StreakSummaryRow(
                    checkInState  = checkInState,
                    primaryText   = primaryText,
                    secondaryText = secondaryText,
                    accent        = accent,
                    expanded      = streakExpanded,
                    onToggle      = { streakExpanded = !streakExpanded }
                )

                // ── 7 & 8. EXPANDABLE SECTION: weekly tracker + emotion ───
                AnimatedVisibility(
                    visible       = streakExpanded,
                    enter         = expandVertically(tween(260)),
                    exit          = shrinkVertically(tween(220))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(8.dp))
                        WeeklyFireTracker(checkInState.checkedInDates, accent, isDark)
                        Spacer(Modifier.height(10.dp))
                        EmotionSelector(checkInState, accent, isDark, onCheckIn, onCheckInRaw)
                        Spacer(Modifier.height(14.dp))
                    }
                }

                // Bottom padding when collapsed
                if (!streakExpanded) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HubHeader(
    petState      : PetState,
    primaryText   : Color,
    secondaryText : Color,
    isDark        : Boolean,
    onEdit        : () -> Unit
) {
    val editBg = if (isDark) Color.White.copy(0.18f) else Color(0xFF1A1A2E).copy(0.14f)
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("${petState.name} · Lv.${petState.level}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = secondaryText)
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(editBg).clickable { onEdit() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Edit, "Edit", tint = primaryText, modifier = Modifier.size(15.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MODE PILL TOGGLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModePillToggle(current: HubMode, accent: Color, isDark: Boolean, onSelect: (HubMode) -> Unit) {
    val trackBg = if (isDark) Color.White.copy(0.09f) else Color.Black.copy(0.07f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50.dp)).background(trackBg)
    ) {
        listOf(HubMode.VISUALISER to "Visualiser", HubMode.DINO to "Dino").forEach { (mode, label) ->
            val icon = if (mode == HubMode.VISUALISER) Icons.Filled.Equalizer else Icons.Filled.SportsEsports
            val sel  = current == mode
            val bg   by animateColorAsState(if (sel) accent else Color.Transparent, tween(220), label = "pill$label")
            val tc   by animateColorAsState(if (sel) Color.White else if (isDark) Color(0xFF999AAA) else Color(0xFF666677), tween(220), label = "pillTc$label")
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50.dp)).background(bg)
                    .clickable { onSelect(mode) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = tc, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = tc)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  VISUALISER MODE — avatar wandering on equaliser
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VisualiserWithAvatar(
    mood           : MascotMood,
    petState       : PetState,
    isPlayingMusic : Boolean,
    personalityProfile : PersonalityProfile,
    eqColors       : List<Color>,
    primaryText    : Color,
    secondaryText  : Color,
    isDark         : Boolean
) {
    val brain         = remember { PetAIBrain() }
    var aiState       by remember { mutableStateOf(PetAIState.IDLE) }
    var thoughtState  by remember { mutableStateOf(PetThought.NONE) }
    var customThought by remember { mutableStateOf("") }
    var roomSize      by remember { mutableStateOf(IntSize(300, 160)) }
    val petPosX       = remember { Animatable(0.5f) }
    val petPosY       = remember { Animatable(0.5f) }
    var isCrouching   by remember { mutableStateOf(false) }
    var tapCount      by remember { mutableStateOf(0) }
    var showHearts    by remember { mutableStateOf(false) }

    LaunchedEffect(personalityProfile.personalityType) {
        brain.applyPersonalityParams(
            personalityProfile.personalityType.wanderFrequencyMs,
            personalityProfile.personalityType.idleSpeedMultiplier,
            personalityProfile.getThoughtBubbles()
        )
    }
    LaunchedEffect(isPlayingMusic) {
        while (true) {
            delay(500)
            val s = brain.update(isPlayingMusic, false, false, roomSize.width.toFloat(), roomSize.height.toFloat())
            aiState = s; thoughtState = brain.currentThought; customThought = brain.customThoughtEmoji
            if (s == PetAIState.WANDERING) {
                val t = brain.wanderTarget; val w = roomSize.width.coerceAtLeast(1); val h = roomSize.height.coerceAtLeast(1)
                isCrouching = true; delay(200); isCrouching = false
                petPosX.animateTo((t.x / w).coerceIn(0.1f, 0.9f), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
                petPosY.animateTo((t.y / h).coerceIn(0.15f, 0.85f), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
            }
            if (s == PetAIState.GROOVY && petPosX.value != 0.5f) {
                petPosX.animateTo(0.5f, spring(0.7f, Spring.StiffnessMediumLow))
                petPosY.animateTo(0.45f, spring(0.7f, Spring.StiffnessMediumLow))
            }
        }
    }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) { showHearts = true; brain.forceState(PetAIState.EXCITED); delay(2000); showHearts = false }
    }

    val inf = rememberInfiniteTransition(label = "vis")
    val bounceY  by inf.animateFloat(0f, if (isPlayingMusic && aiState == PetAIState.GROOVY) -10f else -2f, infiniteRepeatable(tween(if (isPlayingMusic) 350 else 3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "bY")
    val swayAngle by inf.animateFloat(-3f, 3f, infiniteRepeatable(tween(if (aiState == PetAIState.CURIOUS) 1200 else 3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "sway")
    val sqX      by animateFloatAsState(if (showHearts) 1.08f else 1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh), label = "sqX")
    val sqY      by animateFloatAsState(if (showHearts) 0.94f else 1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh), label = "sqY")
    val crouchSc by animateFloatAsState(if (isCrouching) 0.85f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "cr")

    Box(
        Modifier.fillMaxWidth().height(170.dp)
            .onSizeChanged { roomSize = it }
            .pointerInput(Unit) { detectTapGestures(onTap = { tapCount++ }) }
    ) {
        AudioVisualizerView(
            isPlaying = isPlayingMusic, mood = mood.mood,
            barColors = eqColors, audioSessionId = 0,
            modifier  = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val petW = 100.dp.toPx(); val petH = 100.dp.toPx()
                translationX = (petPosX.value * (roomSize.width  - petW)) - (roomSize.width  - petW) / 2f
                translationY = (petPosY.value * (roomSize.height - petH)) - (roomSize.height - petH) / 2f + bounceY
                scaleX    = crouchSc * sqX
                scaleY    = (if (isCrouching) 1.1f else 1f) * crouchSc * sqY
                rotationZ = when (aiState) {
                    PetAIState.CURIOUS, PetAIState.GROOVY -> swayAngle
                    PetAIState.DOZY -> swayAngle * 0.3f
                    else -> 0f
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(90.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
            // LiveAvatar uses petState.type → AvatarSpecies, so the correct
            // PNG (cat/penguin/elephant) is always rendered from Firestore data.
            LiveAvatar(
                petState       = petState,
                isMusicPlaying = isPlayingMusic,
                equippedIds    = petState.avatarEquippedIds(),
                size           = 88.dp,
                facingRight    = true,
                isDragging     = false,
                modifier       = Modifier.size(88.dp)
            )
            if (showHearts) FloatingHeartsEffect()
            if (thoughtState != PetThought.NONE) {
                val emoji = if (thoughtState == PetThought.CUSTOM) customThought else thoughtState.emoji
                if (emoji.isNotEmpty())
                    ThoughtBubbleView(emoji, Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-10).dp))
            }
        }
        if (!isPlayingMusic) {
            Text("♪  Play a song to animate", fontSize = 10.sp,
                color = if (isDark) Color.White.copy(0.25f) else Color.Black.copy(0.15f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DINO MODE
//
//  Avatar: uses LiveAvatar composable (same PNG sprite + accessories as home).
//  The LiveAvatar is positioned at the dino's normalised coordinates using
//  LocalDensity to convert from px → dp offsets inside a Box.
//
//  Collision: uses DinoGameState.avatarBottom (dinoY + DINO_H) for the bottom
//  of the hitbox, NOT GROUND_Y. This was the root cause of instant collisions.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DinoWithAvatar(
    themeState : AppThemeState,
    petState   : PetState,
    onAwardXp  : (Int) -> Unit
) {
    val isDark = themeState.isDark
    val accent = themeState.activePalette.accent

    var gameState by remember { mutableStateOf(DinoGameState()) }
    val stateRef  = remember { mutableStateOf(gameState) }
    LaunchedEffect(gameState) { stateRef.value = gameState }

    val isActive = gameState.phase == DinoPhase.PLAYING
    LaunchedEffect(isActive) {
        if (isActive) runDinoGameLoop({ stateRef.value }) { gameState = it }
    }

    val prevPhase = remember { mutableStateOf(gameState.phase) }
    LaunchedEffect(gameState.phase) {
        if (prevPhase.value == DinoPhase.PLAYING && gameState.phase == DinoPhase.DEAD && gameState.score > 0)
            onAwardXp(gameState.score * 2)
        prevPhase.value = gameState.phase
    }

    val skyGrad  = Brush.verticalGradient(listOf(accent.copy(alpha = if (isDark) 0.18f else 0.10f), if (isDark) Color(0xFF0D1020) else Color(0xFFF5F4FF)))
    val groundColor = if (isDark) Color(0xFF2C2C44) else Color(0xFFDDE8F5)
    val obstColor   = accent.copy(alpha = 0.72f)
    val titleCol    = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subCol      = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val overlayBg   = (if (isDark) Color(0xFF1A1A2E) else Color(0xFFF4F3FF)).copy(alpha = 0.88f)

    val density = LocalDensity.current
    var boxPx   by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier.fillMaxWidth().height(170.dp)
            .background(skyGrad)
            .onSizeChanged { boxPx = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (gameState.phase != DinoPhase.DEAD)
                        gameState = DinoGameEngine.tap(gameState)
                })
            }
    ) {
        // Score
        if (gameState.phase != DinoPhase.IDLE) {
            Text("${gameState.score}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = titleCol.copy(0.55f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
        }

        // Ground + obstacles
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width; val ch = size.height
            val gy = ch * DinoConstants.GROUND_Y
            drawRect(groundColor, Offset(0f, gy), Size(cw, ch - gy))
            drawLine(accent.copy(0.28f), Offset(0f, gy), Offset(cw, gy), strokeWidth = 1.5f)
            gameState.cacti.forEach { drawThemedObstacle(it, cw, ch, obstColor) }
            // Subtle glow beneath avatar
            val dx = cw * (DinoConstants.DINO_X + DinoConstants.DINO_W / 2)
            val dy = ch * DinoConstants.GROUND_Y
            drawCircle(accent.copy(0.12f), cw * 0.055f, Offset(dx, dy))
        }

        // ── LiveAvatar positioned at dino coordinates ──────────────────────
        // We use the same LiveAvatar composable so the correct species + accessories
        // from petState are always shown — the avatar is never a generic emoji.
        if (boxPx.width > 0) {
            val avatarSizeDp = 52.dp  // avatar rendered size in the game
            val avatarSizePx = with(density) { avatarSizeDp.toPx() }

            // Centre of the avatar in normalised coordinates
            val centerNormX = DinoConstants.DINO_X + DinoConstants.DINO_W / 2f
            val centerNormY = gameState.dinoY + DinoConstants.DINO_H / 2f

            // Convert to dp offsets (top-left of avatar box)
            val xDp = with(density) { (boxPx.width  * centerNormX - avatarSizePx / 2f).toDp() }
            val yDp = with(density) { (boxPx.height * centerNormY - avatarSizePx / 2f).toDp() }

            Box(Modifier.offset(x = xDp, y = yDp).size(avatarSizeDp)) {
                LiveAvatar(
                    petState       = petState,
                    isMusicPlaying = false,
                    equippedIds    = petState.avatarEquippedIds(),
                    size           = avatarSizeDp,
                    facingRight    = true,
                    isDragging     = false,
                    modifier       = Modifier.fillMaxSize()
                )
            }
        }

        // IDLE overlay
        if (gameState.phase == DinoPhase.IDLE) {
            GameOverlay(overlayBg) {
                // Show the pet species emoji for the splash screen before game starts
                Text(AvatarSpecies.fromPetType(petState.type).emoji, fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text("Tap to run!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = titleCol)
                Text("Jump over the obstacles", fontSize = 11.sp, color = subCol)
            }
        }

        // DEAD overlay
        if (gameState.phase == DinoPhase.DEAD) {
            GameOverlay(overlayBg) {
                Text("💫", fontSize = 28.sp)
                Text("Score: ${gameState.score}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleCol)
                if (gameState.score > 0 && gameState.score == gameState.bestScore)
                    Text("🏆 New best! +${gameState.score * 2} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
                else Text("Best: ${gameState.bestScore}", fontSize = 11.sp, color = subCol)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { gameState = DinoGameEngine.restart(gameState.bestScore) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Try again", fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  XP BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun XpBarRow(petState: PetState, secondaryText: Color, isDark: Boolean) {
    val xpTrack = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.10f)
    val xpFill  by animateFloatAsState(petState.xpProgress, tween(600, easing = EaseOutCubic), label = "xp")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("XP", fontSize = 10.sp, color = secondaryText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(xpTrack)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(xpFill).clip(RoundedCornerShape(3.dp)).background(Color(0xFFFFD700)))
        }
        Spacer(Modifier.width(6.dp))
        Text("${petState.xp}/${petState.xpForNextLevel}", fontSize = 10.sp, color = secondaryText)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CHAT BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatButton(petState: PetState, chatMessage: String?, primaryText: Color, secondaryText: Color, isDark: Boolean, onOpen: () -> Unit) {
    val bg     = if (isDark) Color.White.copy(0.14f) else Color(0xFF1A1A2E).copy(0.10f)
    val iconBg = if (isDark) Color.White.copy(0.20f) else Color(0xFF1A1A2E).copy(0.15f)
    Surface(shape = RoundedCornerShape(16.dp), color = bg, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpen() }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Chat, null, tint = primaryText, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Chat with ${petState.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                Text(chatMessage ?: "Tell me how you're feeling…", fontSize = 12.sp, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowForwardIos, null, tint = primaryText.copy(0.40f), modifier = Modifier.size(14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  STREAK SUMMARY ROW  (collapsible header)
//  "😊 Feeling Calm     🔥 3-day streak  [▼]"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreakSummaryRow(
    checkInState  : CheckInState,
    primaryText   : Color,
    secondaryText : Color,
    accent        : Color,
    expanded      : Boolean,
    onToggle      : () -> Unit
) {
    val fireColor = Color(0xFFFF6B35)
    val (label, emoji) = DailyMood.displayFor(checkInState.todayMood)

    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: feeling label
        if (checkInState.checkedInToday && label.isNotEmpty()) {
            Text("$emoji Feeling $label", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
        } else {
            Text("Daily Check-in", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText)
        }

        // Right: streak badge + chevron
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checkInState.streak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(fireColor.copy(0.14f)).padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🔥", fontSize = 13.sp)
                    Spacer(Modifier.width(3.dp))
                    Text("${checkInState.streak}-day streak", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fireColor)
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint     = primaryText.copy(0.55f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  WEEKLY FIRE TRACKER  Mon → Sun
//  Checked days: 🔥 emoji on accent-tinted circle
//  Today: accent border ring
//  Future: dimmed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyFireTracker(checkedInDates: Set<String>, accent: Color, isDark: Boolean) {
    val todayStr  = hubTodayStr()
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val weekDates : List<String> = remember {
        val c   = Calendar.getInstance()
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val back = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        c.add(Calendar.DAY_OF_YEAR, -back)
        (0..6).map { i ->
            val cl = c.clone() as Calendar
            cl.add(Calendar.DAY_OF_YEAR, i)
            "%04d-%02d-%02d".format(cl.get(Calendar.YEAR), cl.get(Calendar.MONTH) + 1, cl.get(Calendar.DAY_OF_MONTH))
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        weekDates.forEachIndexed { i, dateStr ->
            val isToday   = dateStr == todayStr
            val isChecked = checkedInDates.contains(dateStr)
            val isFuture  = dateStr > todayStr

            val dotBg by animateColorAsState(
                when {
                    isChecked -> accent.copy(alpha = 0.22f)
                    isToday   -> accent.copy(alpha = 0.12f)
                    else      -> Color.Transparent
                },
                tween(280), label = "wdot$i"
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
                Text(
                    dayLabels[i],
                    fontSize  = 9.sp,
                    color     = (if (isDark) Color(0xFF888899) else Color(0xFF999AAA)).copy(alpha = if (isFuture) 0.35f else 1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(dotBg)
                        .then(if (isToday && !isChecked) Modifier.border(1.5.dp, accent.copy(0.60f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    // Checked days show 🔥; today (not yet checked) shows nothing (ring only)
                    if (isChecked) {
                        Text("🔥", fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EMOTION SELECTOR
//  Chips are CIRCLES (spec: circle shape, not rectangle).
//  Happy 😊 | Calm 😌 | Sad 😢 | Stressed 😣 | Others ✍️
//  "Others" opens inline text field.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmotionSelector(
    checkInState : CheckInState,
    accent       : Color,
    isDark       : Boolean,
    onSelect     : (DailyMood) -> Unit,
    onSelectRaw  : (String) -> Unit
) {
    var showOthersInput by remember { mutableStateOf(false) }
    var othersText      by remember { mutableStateOf("") }
    val focusRequester  = remember { FocusRequester() }
    val focusManager    = LocalFocusManager.current

    val selectedKey = checkInState.todayMood

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (showOthersInput) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value          = othersText,
                    onValueChange  = { othersText = it.take(40) },
                    modifier       = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder    = { Text("How are you feeling?", fontSize = 13.sp) },
                    singleLine     = true,
                    keyboardOptions= KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions= KeyboardActions(onDone = {
                        if (othersText.isNotBlank()) { onSelectRaw("others:${othersText.trim()}"); showOthersInput = false; focusManager.clearFocus() }
                    }),
                    shape          = RoundedCornerShape(14.dp),
                    colors         = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.15f))
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = { if (othersText.isNotBlank()) { onSelectRaw("others:${othersText.trim()}"); showOthersInput = false; focusManager.clearFocus() } },
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(accent)
                ) { Icon(Icons.Filled.Check, "Confirm", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            // ── Circle chip row ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DailyMood.all().forEach { mood ->
                    val isSelected = when {
                        mood == DailyMood.OTHERS -> selectedKey?.startsWith("others:") == true
                        else                     -> selectedKey == mood.key
                    }
                    val chipBg by animateColorAsState(
                        if (isSelected) accent.copy(0.22f) else if (isDark) Color.White.copy(0.07f) else Color.Black.copy(0.05f),
                        tween(200), label = "chip${mood.key}"
                    )

                    // ── CIRCLE shape — 56 dp diameter ───────────────────
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)                                // ← circle, not rectangle
                            .background(chipBg)
                            .then(if (isSelected) Modifier.border(1.5.dp, accent, CircleShape) else Modifier)
                            .clickable {
                                if (mood == DailyMood.OTHERS) showOthersInput = true else onSelect(mood)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(mood.emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
                            Text(mood.label, fontSize = 8.5.sp, color = if (isDark) Color(0xFFBBBBCC) else Color(0xFF555566), textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }

            if (checkInState.checkedInToday) {
                Spacer(Modifier.height(6.dp))
                Text("Tap to update today's mood", fontSize = 10.sp,
                    color = if (isDark) Color(0xFF888899) else Color(0xFF999AAA),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas: themed obstacle (abstract totem, not pixel cactus)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawThemedObstacle(c: Cactus, cw: Float, ch: Float, color: Color) {
    val gy  = ch * DinoConstants.GROUND_Y
    val l   = c.x * cw
    val w   = DinoConstants.CACTUS_W * cw * 1.1f
    val top = gy - c.height * ch
    drawRoundRect(color, Offset(l + w * 0.25f, top), Size(w * 0.50f, gy - top), CornerRadius(8f))
    drawRoundRect(color.copy(alpha = 0.60f), Offset(l, top), Size(w, (gy - top) * 0.22f), CornerRadius(6f))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared UI helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameOverlay(bgColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun ThoughtBubbleView(emoji: String, modifier: Modifier = Modifier) {
    val bubbleAlpha by rememberInfiniteTransition(label = "tbP").animateFloat(0.7f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "tbA")
    val floatY      by rememberInfiniteTransition(label = "tbF").animateFloat(0f, -4f, infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse), label = "tbY")
    Box(
        modifier.graphicsLayer { alpha = bubbleAlpha; translationY = floatY }
            .background(Color.White.copy(0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(emoji, fontSize = 16.sp) }
}

@Composable
private fun FloatingHeartsEffect() {
    listOf("💖", "✨", "💕").forEachIndexed { i, h ->
        val inf        = rememberInfiniteTransition(label = "h$i")
        val y          by inf.animateFloat(0f, -50f, infiniteRepeatable(tween(800 + i * 200, easing = EaseOut), RepeatMode.Restart), label = "hy$i")
        val heartAlpha by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart), label = "ha$i")
        Text(h, fontSize = 16.sp, modifier = Modifier.offset(x = (-16 + i * 16).dp, y = y.dp).graphicsLayer { alpha = heartAlpha })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Colour + date helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun hubEqualizerColors(mood: String): List<Color> = when (mood) {
    "happy"     -> listOf(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFFFF6347))
    "sad"       -> listOf(Color(0xFF6495ED), Color(0xFF7B68EE), Color(0xFF9370DB))
    "calm"      -> listOf(Color(0xFF87CEEB), Color(0xFF98D8C8), Color(0xFFB0E0E6))
    "energetic" -> listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00))
    "tired"     -> listOf(Color(0xFF607D8B), Color(0xFF78909C), Color(0xFF90A4AE))
    "focused"   -> listOf(Color(0xFF00E676), Color(0xFF00BFA5), Color(0xFF1DE9B6))
    "romantic"  -> listOf(Color(0xFFFF6B9D), Color(0xFFC471ED), Color(0xFFFF9A8B))
    else        -> listOf(Color(0xFF9C27B0), Color(0xFF7C4DFF), Color(0xFFE040FB))
}

private fun hubTodayStr(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}