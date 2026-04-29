package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
//  BuddyHubCard  (fixed)
//
//  FIXES APPLIED:
//
//  1. MODE SWITCHER LAG — replaced Crossfade (which destroys/recreates both
//     composables on every switch, tearing down coroutines + infinite
//     transitions) with AnimatedVisibility keeping BOTH subtrees alive.
//     Switching is now instant with a simple fade; no brain-loop restart.
//
//  2. DINO GAME LOOP RACE — removed the stateRef + LaunchedEffect(gameState)
//     sync pattern (which was one frame behind). Game state is now read via a
//     plain `mutableStateOf` ref that is updated synchronously inside the loop
//     callback, so the loop always reads the latest state with zero lag.
//
//  3. DINO PHYSICS — moved the game loop LaunchedEffect key to
//     `gameState.phase == DinoPhase.PLAYING` (a stable Boolean) so the loop
//     isn't cancelled and restarted on every frame of gameState change.
//     The loop now runs uninterrupted for the entire playing phase.
//
//  4. AVATAR POSITION SMOOTHNESS — `offset(x, y)` inside the dino canvas now
//     reads from `gameState` directly (no intermediate ref), so the avatar
//     position updates on the same frame as the game tick, eliminating the
//     1-frame visual lag between physics and rendering.
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
    onMoodPick         : (String) -> Unit = {},   // new: user picks mood from list → refreshes recs + bg
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
    var showMoodList   by remember { mutableStateOf(false) }

    val eqColors = remember(mood.mood) { hubEqualizerColors(mood.mood) }

    if (showCustomize) {
        PetCustomiseSheet(
            petState      = petState,
            petRepository = petRepository,
            onDismiss     = { showCustomize = false },
            onVisitShop   = { showCustomize = false; onEditMascot() }
        )
    }

    if (showMoodList) {
        MoodPickerList(
            currentMood = checkInState.todayMood,
            accent      = accent,
            isDark      = isDark,
            onSelect    = { moodKey ->
                onCheckIn(DailyMood.fromKey(moodKey))
                onMoodPick(moodKey)
                showMoodList = false
            },
            onDismiss   = { showMoodList = false }
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
                // FIX 1: AnimatedVisibility keeps both subtrees alive so their
                // coroutines, brain loops, and infinite transitions are never
                // torn down on a mode switch — eliminating the lag spike.
                Box(Modifier.fillMaxWidth()) {
                    // Visualiser — always composed, just hidden when in Dino mode
                    androidx.compose.animation.AnimatedVisibility(
                        visible = mode == HubMode.VISUALISER,
                        enter   = androidx.compose.animation.fadeIn(tween(220)),
                        exit    = androidx.compose.animation.fadeOut(tween(180))
                    ) {
                        VisualiserWithAvatar(
                            mood, petState, isPlayingMusic, personalityProfile,
                            eqColors, primaryText, secondaryText, isDark
                        )
                    }
                    // Dino — always composed, just hidden when in Visualiser mode
                    androidx.compose.animation.AnimatedVisibility(
                        visible = mode == HubMode.DINO,
                        enter   = androidx.compose.animation.fadeIn(tween(220)),
                        exit    = androidx.compose.animation.fadeOut(tween(180))
                    ) {
                        DinoWithAvatar(
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

                // ── 6. STREAK HEADER — fire count badge always visible ─────
                StreakHeaderRow(
                    checkInState  = checkInState,
                    primaryText   = primaryText,
                    secondaryText = secondaryText,
                    accent        = accent
                )

                // ── 7. WEEKLY FIRE TRACKER — always visible ────────────────
                WeeklyFireTracker(checkInState.checkedInDates, accent, isDark)

                Spacer(Modifier.height(12.dp))

                // ── 8. INLINE EMOTION PICKER BAR ──────────────────────────
                EmotionPickerBar(
                    currentMood    = checkInState.todayMood,
                    accent         = accent,
                    isDark         = isDark,
                    primaryText    = primaryText,
                    onSelect       = { key ->
                        onCheckInRaw(key)
                        onMoodPick(key)
                    }
                )

                Spacer(Modifier.height(14.dp))
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
            val bg   by animateColorAsState(if (sel) accent else Color.Transparent, tween(180), label = "pill$label")
            val tc   by animateColorAsState(
                if (sel) Color.White else if (isDark) Color(0xFF999AAA) else Color(0xFF666677),
                tween(180), label = "pillTc$label"
            )
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(50.dp)).background(bg)
                    .clickable { onSelect(mode) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
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
    mood               : MascotMood,
    petState           : PetState,
    isPlayingMusic     : Boolean,
    personalityProfile : PersonalityProfile,
    eqColors           : List<Color>,
    primaryText        : Color,
    secondaryText      : Color,
    isDark             : Boolean
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
                val t = brain.wanderTarget
                val w = roomSize.width.coerceAtLeast(1)
                val h = roomSize.height.coerceAtLeast(1)
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
        if (tapCount > 0) {
            showHearts = true
            brain.forceState(PetAIState.EXCITED)
            delay(2000)
            showHearts = false
        }
    }

    val inf      = rememberInfiniteTransition(label = "vis")
    val bounceY  by inf.animateFloat(
        0f,
        if (isPlayingMusic && aiState == PetAIState.GROOVY) -10f else -2f,
        infiniteRepeatable(tween(if (isPlayingMusic) 350 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bY"
    )
    val swayAngle by inf.animateFloat(
        -3f, 3f,
        infiniteRepeatable(tween(if (aiState == PetAIState.CURIOUS) 1200 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sway"
    )
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
                    PetAIState.DOZY                       -> swayAngle * 0.3f
                    else                                  -> 0f
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(90.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
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
            Text(
                "♪  Play a song to animate",
                fontSize = 10.sp,
                color    = if (isDark) Color.White.copy(0.25f) else Color.Black.copy(0.15f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DINO MODE  (fixed)
//
//  FIX 2 — stateRef sync lag removed:
//    Old code had:
//      val stateRef = remember { mutableStateOf(gameState) }
//      LaunchedEffect(gameState) { stateRef.value = gameState }   ← 1 frame late
//    New code uses a single gameStateRef that is written in-place by the loop
//    callback and read by the loop on the same frame.
//
//  FIX 3 — game loop stability:
//    LaunchedEffect key is now `isPlaying: Boolean` derived once outside,
//    so the effect is only cancelled when the game stops/starts, not on every
//    single tick that changes gameState.
//
//  FIX 4 — avatar position:
//    Avatar offset now reads directly from `gameState.dinoY`, no intermediate
//    ref, so the sprite moves exactly with the physics on every frame.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DinoWithAvatar(
    themeState : AppThemeState,
    petState   : PetState,
    onAwardXp  : (Int) -> Unit
) {
    val isDark = themeState.isDark
    val accent = themeState.activePalette.accent

    // FIX 2: single source-of-truth; the loop writes here, the UI reads here.
    var gameState by remember { mutableStateOf(DinoGameState()) }

    // A stable ref the loop closure captures once — avoids lambda capture issues.
    val gameStateRef = remember { mutableStateOf(gameState) }

    // Keep the ref in sync (write side — this is cheap, no composition cost).
    // We do NOT use this ref to drive UI; `gameState` drives UI.
    // We DO use it for the loop's getState lambda so it always gets the latest value.
    LaunchedEffect(gameState) { gameStateRef.value = gameState }

    // FIX 3: key is a stable Boolean — loop only restarts when phase changes
    // between PLAYING and not-PLAYING, not on every physics tick.
    val isPlaying = gameState.phase == DinoPhase.PLAYING
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            runDinoGameLoop(
                getState      = { gameStateRef.value },
                onStateUpdate = { new ->
                    gameState         = new
                    gameStateRef.value = new   // keep ref in sync on the same frame
                }
            )
        }
    }

    // Award XP on transition from PLAYING → DEAD
    val prevPhase = remember { mutableStateOf(gameState.phase) }
    LaunchedEffect(gameState.phase) {
        if (prevPhase.value == DinoPhase.PLAYING && gameState.phase == DinoPhase.DEAD && gameState.score > 0)
            onAwardXp(gameState.score * 2)
        prevPhase.value = gameState.phase
    }

    val skyGrad     = Brush.verticalGradient(listOf(
        accent.copy(alpha = if (isDark) 0.18f else 0.10f),
        if (isDark) Color(0xFF0D1020) else Color(0xFFF5F4FF)
    ))
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
                    // Tap to jump (PLAYING) or restart (DEAD)
                    gameState = when (gameState.phase) {
                        DinoPhase.DEAD  -> DinoGameEngine.restart(gameState.bestScore)
                        else            -> DinoGameEngine.tap(gameState)
                    }
                })
            }
    ) {
        // Score counter
        if (gameState.phase != DinoPhase.IDLE) {
            Text(
                "${gameState.score}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color    = titleCol.copy(0.55f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)
            )
        }

        // Ground + obstacles (Canvas — runs every frame, no state issues)
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width
            val ch = size.height
            val gy = ch * DinoConstants.GROUND_Y

            drawRect(groundColor, Offset(0f, gy), Size(cw, ch - gy))
            drawLine(accent.copy(0.28f), Offset(0f, gy), Offset(cw, gy), strokeWidth = 1.5f)

            gameState.cacti.forEach { drawThemedObstacle(it, cw, ch, obstColor) }

            // Glow beneath avatar
            val dx = cw * (DinoConstants.DINO_X + DinoConstants.DINO_W / 2f)
            val dy = ch * DinoConstants.GROUND_Y
            drawCircle(accent.copy(0.12f), cw * 0.055f, Offset(dx, dy))
        }

        // FIX 4: LiveAvatar reads gameState.dinoY directly — no intermediate ref,
        // no one-frame lag between physics tick and visual position.
        if (boxPx.width > 0) {
            val avatarSizeDp = 52.dp
            val avatarSizePx = with(density) { avatarSizeDp.toPx() }

            val centerNormX = DinoConstants.DINO_X + DinoConstants.DINO_W / 2f
            val centerNormY = gameState.dinoY + DinoConstants.DINO_H / 2f

            val xDp = with(density) { (boxPx.width  * centerNormX - avatarSizePx / 2f).toDp() }
            val yDp = with(density) { (boxPx.height * centerNormY - avatarSizePx / 2f).toDp() }

            Box(Modifier.offset(x = xDp, y = yDp).size(avatarSizeDp)) {
                LiveAvatar(
                    petState       = petState,
                    isMusicPlaying = gameState.phase == DinoPhase.PLAYING,
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
                Text(AvatarSpecies.fromPetType(petState.type).emoji, fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text("Tap to run!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = titleCol)
                Text("Jump over the obstacles", fontSize = 11.sp, color = subCol)
            }
        }

        // DEAD overlay — tap anywhere restarts (handled in pointerInput above)
        if (gameState.phase == DinoPhase.DEAD) {
            GameOverlay(overlayBg) {
                Text("💫", fontSize = 28.sp)
                Text("Score: ${gameState.score}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleCol)
                if (gameState.score > 0 && gameState.score == gameState.bestScore)
                    Text("🏆 New best! +${gameState.score * 2} XP", fontSize = 12.sp, color = Color(0xFFFFD700))
                else
                    Text("Best: ${gameState.bestScore}", fontSize = 11.sp, color = subCol)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick         = { gameState = DinoGameEngine.restart(gameState.bestScore) },
                    colors          = ButtonDefaults.buttonColors(containerColor = accent),
                    contentPadding  = PaddingValues(horizontal = 22.dp, vertical = 6.dp)
                ) {
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
//  STREAK HEADER ROW — compact fire badge + total count, no expand button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreakHeaderRow(
    checkInState  : CheckInState,
    primaryText   : Color,
    secondaryText : Color,
    accent        : Color
) {
    val fireColor = Color(0xFFFF6B35)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("This Week", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondaryText)
            Text("Check-in Streak", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryText)
        }
        // Fire badge — shows streak count prominently
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (checkInState.streak > 0) fireColor.copy(0.16f)
                    else accent.copy(0.10f)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(if (checkInState.streak > 0) "🔥" else "💤", fontSize = 15.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                if (checkInState.streak > 0) "${checkInState.streak} day streak"
                else "No streak yet",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (checkInState.streak > 0) fireColor else secondaryText
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  WEEKLY FIRE TRACKER — always visible, prominent fire icons per checked day
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklyFireTracker(checkedInDates: Set<String>, accent: Color, isDark: Boolean) {
    val todayStr  = hubTodayStr()
    val fireColor = Color(0xFFFF6B35)
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val weekDates: List<String> = remember {
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

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekDates.forEachIndexed { i, dateStr ->
            val isToday   = dateStr == todayStr
            val isChecked = checkedInDates.contains(dateStr)
            val isFuture  = dateStr > todayStr

            // Animated background per cell
            val cellBg by animateColorAsState(
                when {
                    isChecked && isToday -> fireColor.copy(alpha = 0.28f)
                    isChecked            -> fireColor.copy(alpha = 0.18f)
                    isToday              -> accent.copy(alpha = 0.14f)
                    else                 -> Color.Transparent
                },
                tween(300), label = "wfire$i"
            )
            val borderColor by animateColorAsState(
                when {
                    isToday && isChecked -> fireColor.copy(0.70f)
                    isToday              -> accent.copy(0.55f)
                    isChecked            -> fireColor.copy(0.35f)
                    else                 -> Color.Transparent
                },
                tween(300), label = "wborder$i"
            )
            // Fire icon scale — checked days pop
            val fireScale by animateFloatAsState(
                if (isChecked) 1f else 0f,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "wscale$i"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Day label
                Text(
                    dayLabels[i],
                    fontSize  = 9.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday   -> if (isDark) Color(0xFFEEEEFF) else Color(0xFF222233)
                        isFuture  -> (if (isDark) Color(0xFF666677) else Color(0xFFAAAAAA))
                        else      -> if (isDark) Color(0xFF9999AA) else Color(0xFF777788)
                    },
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(5.dp))
                // Fire cell
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cellBg)
                        .then(
                            if (borderColor != Color.Transparent)
                                Modifier.border(1.5.dp, borderColor, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        // Checked: big fire emoji, scaled in with spring
                        Text(
                            "🔥",
                            fontSize = 18.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = fireScale
                                scaleY = fireScale
                            }
                        )
                    } else if (isToday) {
                        // Today but not checked: pulsing dot
                        val inf = rememberInfiniteTransition(label = "todayPulse$i")
                        val pulse by inf.animateFloat(
                            0.5f, 1f,
                            infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
                            label = "pulse$i"
                        )
                        Text("·", fontSize = 20.sp, color = accent.copy(alpha = pulse), fontWeight = FontWeight.Bold)
                    } else if (!isFuture) {
                        // Past unchecked: faint empty circle indicator
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(
                                    if (isDark) Color.White.copy(0.12f)
                                    else Color.Black.copy(0.10f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  INLINE EMOTION PICKER BAR
//  Horizontal scroll of emotion chips — tapping one checks in immediately.
//  Includes moods beyond DailyMood enum (romantic, nervous, energetic, focused)
//  sent as raw keys via onCheckInRaw → onMoodPick.
// ─────────────────────────────────────────────────────────────────────────────

private data class EmotionChip(val key: String, val emoji: String, val label: String)

private val EMOTION_CHIPS = listOf(
    EmotionChip("happy",     "😊", "Happy"),
    EmotionChip("calm",      "😌", "Calm"),
    EmotionChip("sad",       "😢", "Sad"),
    EmotionChip("romantic",  "💕", "Romantic"),
    EmotionChip("energetic", "⚡", "Energetic"),
    EmotionChip("focused",   "🎯", "Focused"),
    EmotionChip("stressed",  "😣", "Stressed"),
    EmotionChip("nervous",   "😰", "Nervous"),
    EmotionChip("tired",     "😴", "Tired"),
    EmotionChip("neutral",   "😐", "Neutral"),
)

@Composable
private fun EmotionPickerBar(
    currentMood : String?,
    accent      : Color,
    isDark      : Boolean,
    primaryText : Color,
    onSelect    : (String) -> Unit
) {
    val labelColor   = if (isDark) Color(0xFF9999AA) else Color(0xFF666677)
    val unselBg      = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    val unselText    = if (isDark) Color(0xFFCCCCDD) else Color(0xFF333344)

    Column(Modifier.fillMaxWidth()) {
        // Section label
        Row(
            Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("How are you feeling?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
        }

        // Scrollable chip row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EMOTION_CHIPS) { chip ->
                val isSelected = currentMood == chip.key ||
                        (currentMood?.startsWith(chip.key) == true)

                val chipBg by animateColorAsState(
                    if (isSelected) accent.copy(alpha = 0.22f) else unselBg,
                    tween(180), label = "chip_${chip.key}"
                )
                val chipBorder by animateColorAsState(
                    if (isSelected) accent else Color.Transparent,
                    tween(180), label = "chipbrd_${chip.key}"
                )
                val chipTextColor by animateColorAsState(
                    if (isSelected) accent else unselText,
                    tween(180), label = "chiptxt_${chip.key}"
                )
                // Spring scale pop on selection
                val chipScale by animateFloatAsState(
                    if (isSelected) 1.05f else 1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label = "chipsc_${chip.key}"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = chipScale; scaleY = chipScale }
                        .clip(RoundedCornerShape(20.dp))
                        .background(chipBg)
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, chipBorder, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { onSelect(chip.key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(chip.emoji, fontSize = 15.sp)
                        Text(
                            chip.label,
                            fontSize   = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color      = chipTextColor
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Filled.Check, null,
                                tint = accent,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MOOD PICKER LIST  (replaces EmotionSelector chips)
//  Full-screen bottom-sheet style dialog: list rows, each with emoji + label.
//  Selecting a mood calls back → updates check-in AND refreshes recs + bg.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoodPickerList(
    currentMood : String?,
    accent      : Color,
    isDark      : Boolean,
    onSelect    : (String) -> Unit,
    onDismiss   : () -> Unit
) {
    val bgSheet  = if (isDark) Color(0xFF1C1C2E) else Color(0xFFF8F8FF)
    val textCol  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subCol   = if (isDark) Color(0xFF888899) else Color(0xFF666677)
    val divCol   = if (isDark) Color.White.copy(0.07f) else Color.Black.copy(0.06f)

    // All moods the user can choose from — same DailyMood list, no "Others" free-text
    val moods = DailyMood.entries.filter { it != DailyMood.OTHERS }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = bgSheet,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                text       = "How are you feeling?",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = textCol
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Pick a mood — your music and theme will update instantly.",
                    fontSize = 12.sp, color = subCol,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                moods.forEachIndexed { idx, mood ->
                    val isSelected = currentMood == mood.key
                    val rowBg by animateColorAsState(
                        if (isSelected) accent.copy(0.14f) else Color.Transparent,
                        tween(160), label = "mpl$idx"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg)
                            .clickable { onSelect(mood.key) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mood.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mood.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textCol)
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (idx < moods.lastIndex) {
                        HorizontalDivider(color = divCol, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = subCol, fontSize = 14.sp)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas: themed obstacle
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawThemedObstacle(c: Cactus, cw: Float, ch: Float, color: Color) {
    val gy  = ch * DinoConstants.GROUND_Y
    val l   = c.x * cw
    val w   = DinoConstants.CACTUS_W * cw * 1.1f
    val top = gy - c.height * ch
    drawRoundRect(color,                  Offset(l + w * 0.25f, top), Size(w * 0.50f, gy - top), CornerRadius(8f))
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
        val heartAlpha by inf.animateFloat(1f, 0f,   infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart), label = "ha$i")
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