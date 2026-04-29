package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.model.PetState
import kotlinx.coroutines.delay

// ── Colour palette ────────────────────────────────────────────────────────────
private val SkyTop       = Color(0xFF87CEEB)
private val SkyBot       = Color(0xFFB8E4F9)
private val GroundTop    = Color(0xFF6DBB5A)
private val GroundBot    = Color(0xFF8B6914)
private val PipeGreen    = Color(0xFF4CAF50)
private val PipeDark     = Color(0xFF388E3C)
private val PipeTrim     = Color(0xFF66BB6A)
private val CloudWhite   = Color(0xFFF0F8FF).copy(alpha = 0.85f)
private val ScoreYellow  = Color(0xFFFFD700)
private val OverlayBg    = Color(0x99000000)
private val AccentOrange = Color(0xFFFF8C42)

// ─────────────────────────────────────────────────────────────────────────────
//  FlappyGameScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlappyGameScreen(
    petState: PetState,
    petRepository: PetRepository,
    onBack: () -> Unit = {}
) {
    var gameState   by remember { mutableStateOf(FlappyGameState()) }
    var rewardText  by remember { mutableStateOf<String?>(null) }
    var showReward  by remember { mutableStateOf(false) }
    var cloudOffset by remember { mutableStateOf(0f) }

    // Measure the rendered canvas size in px so we can convert normalised
    // game coords → Dp without needing BoxWithConstraints (which caused the
    // toDp / type-inference errors).
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density    = LocalDensity.current

    // ── Game loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(gameState.phase) {
        if (gameState.phase == FlappyPhase.PLAYING) {
            runFlappyGameLoop(
                getState      = { gameState },
                onStateUpdate = { gameState = it }
            )
        }
    }

    // ── Cloud parallax loop ───────────────────────────────────────────────────
    LaunchedEffect(gameState.phase) {
        while (gameState.phase == FlappyPhase.PLAYING) {
            delay(16L)
            cloudOffset = (cloudOffset + 0.3f) % 1000f
        }
    }

    // ── Award bonding points exactly once per death ───────────────────────────
    LaunchedEffect(gameState.pendingReward) {
        if (gameState.pendingReward && gameState.phase == FlappyPhase.DEAD) {
            val earned = FlappyRewardCalculator.bondingPoints(
                score        = gameState.score,
                previousBest = gameState.bestScore
            )
            if (earned > 0) {
                petRepository.addBondingPointsFromGame(earned)
                rewardText = "⭐ +$earned bonding points!"
                showReward = true
                delay(2_500L)
                showReward = false
            }
            gameState = FlappyGameEngine.clearReward(gameState)
        }
    }

    // ── Bird tilt ─────────────────────────────────────────────────────────────
    val tiltTarget: Float = when {
        gameState.phase == FlappyPhase.DEAD -> 90f
        gameState.velocityY < -0.04f        -> -25f
        gameState.velocityY > 0.02f         -> 30f
        else                                -> 0f
    }
    val birdTilt by animateFloatAsState(
        targetValue   = tiltTarget,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label         = "birdTilt"
    )

    val isFlapping: Boolean = gameState.phase == FlappyPhase.PLAYING && gameState.velocityY < 0f

    // ── Bird position in Dp ───────────────────────────────────────────────────
    // Calculated from measured px size + LocalDensity — no BoxWithConstraints needed.
    val birdSizeDp: Dp = 56.dp
    val birdOffsetX: Dp
    val birdOffsetY: Dp
    if (canvasSize == IntSize.Zero) {
        birdOffsetX = 0.dp
        birdOffsetY = 0.dp
    } else {
        val halfBirdPx: Float = with(density) { birdSizeDp.toPx() } / 2f
        val rawX: Float = FlappyConstants.PET_X * canvasSize.width.toFloat()  - halfBirdPx
        val rawY: Float = gameState.petY         * canvasSize.height.toFloat() - halfBirdPx
        birdOffsetX = with(density) { rawX.toDp() }
        birdOffsetY = with(density) { rawY.toDp() }
    }

    // ── Root Box ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { gameState = FlappyGameEngine.tap(gameState) }
            }
            .onSizeChanged { size -> canvasSize = size }
    ) {
        // ── Canvas game world ─────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W: Float = size.width
            val H: Float = size.height

            // Sky
            drawRect(
                brush = Brush.verticalGradient(listOf(SkyTop, SkyBot)),
                size  = size
            )

            // Clouds
            drawClouds(W, H, cloudOffset)

            // Pipes
            gameState.obstacles.forEach { obs ->
                drawPipe(
                    x         = obs.x * W,
                    gapTop    = obs.gapTop * H,
                    gapBottom = (obs.gapTop + FlappyConstants.GAP_SIZE) * H,
                    width     = FlappyConstants.OBSTACLE_WIDTH * W,
                    canvasH   = H
                )
            }

            // Ground
            val groundY: Float = H * 0.94f
            drawRect(
                brush   = Brush.verticalGradient(
                    colors = listOf(GroundTop, GroundBot),
                    startY = groundY,
                    endY   = H
                ),
                topLeft = Offset(0f, groundY),
                size    = Size(W, H - groundY)
            )
            drawRect(
                color   = Color(0xFF3A8A2A),
                topLeft = Offset(0f, groundY),
                size    = Size(W, 4.dp.toPx())
            )
        }

        // ── Bird (LiveAvatar) ─────────────────────────────────────────────────
        if (canvasSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .offset(x = birdOffsetX, y = birdOffsetY)
                    .size(birdSizeDp)
                    .graphicsLayer { rotationZ = birdTilt }
            ) {
                LiveAvatar(
                    petState       = petState,
                    isMusicPlaying = isFlapping,
                    equippedIds    = petState.avatarEquippedIds(),
                    size           = birdSizeDp
                )
            }
        }

        // ── Score HUD ─────────────────────────────────────────────────────────
        if (gameState.phase != FlappyPhase.IDLE) {
            ScoreHud(score = gameState.score, bestScore = gameState.bestScore)
        }

        // ── Back button ───────────────────────────────────────────────────────
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // ── IDLE splash ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = gameState.phase == FlappyPhase.IDLE,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            IdleSplash(petName = petState.name)
        }

        // ── DEAD overlay ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = gameState.phase == FlappyPhase.DEAD,
            enter   = fadeIn() + scaleIn(initialScale = 0.85f),
            exit    = fadeOut() + scaleOut()
        ) {
            DeadOverlay(
                score      = gameState.score,
                bestScore  = gameState.bestScore,
                rewardText = if (showReward) rewardText else null
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Canvas helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawClouds(W: Float, H: Float, offset: Float) {
    data class CloudDef(val xFrac: Float, val yFrac: Float, val w: Float)
    val clouds = listOf(
        CloudDef(0.15f, 0.08f, 70f),
        CloudDef(0.55f, 0.14f, 90f),
        CloudDef(0.80f, 0.06f, 60f)
    )
    clouds.forEach { c ->
        val cx: Float = ((c.xFrac * W) - offset * 0.4f).mod(W + c.w) - c.w / 2f
        val cy: Float = c.yFrac * H
        val cH: Float = c.w * 0.45f
        drawOval(CloudWhite, Offset(cx,               cy),              Size(c.w,          cH))
        drawOval(CloudWhite, Offset(cx + c.w * 0.2f,  cy - cH * 0.4f), Size(c.w * 0.65f,  cH * 0.8f))
        drawOval(CloudWhite, Offset(cx + c.w * 0.55f, cy - cH * 0.2f), Size(c.w * 0.5f,   cH * 0.7f))
    }
}

private fun DrawScope.drawPipe(
    x: Float, gapTop: Float, gapBottom: Float, width: Float, canvasH: Float
) {
    val capH: Float = 18.dp.toPx()
    val capW: Float = width + 10.dp.toPx()
    val capX: Float = x - 5.dp.toPx()

    // ── Top pipe ──────────────────────────────────────────────────────────────
    if (gapTop > 0f) {
        drawRect(color = PipeGreen, topLeft = Offset(x, 0f),                        size = Size(width,       gapTop))
        drawRect(color = PipeDark,  topLeft = Offset(x + width - 6.dp.toPx(), 0f),  size = Size(6.dp.toPx(), gapTop))
        drawRect(color = PipeTrim,  topLeft = Offset(x + 4.dp.toPx(), 0f),          size = Size(4.dp.toPx(), gapTop))
        // Cap
        drawRoundRect(color = PipeGreen, topLeft = Offset(capX,                      gapTop - capH), size = Size(capW,       capH), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(color = PipeDark,  topLeft = Offset(capX + capW - 7.dp.toPx(), gapTop - capH), size = Size(7.dp.toPx(), capH), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(color = PipeTrim,  topLeft = Offset(capX + 4.dp.toPx(),        gapTop - capH), size = Size(5.dp.toPx(), capH), cornerRadius = CornerRadius(4.dp.toPx()))
    }

    // ── Bottom pipe ───────────────────────────────────────────────────────────
    val pipeH: Float = canvasH - gapBottom
    if (pipeH > 0f) {
        // Cap
        drawRoundRect(color = PipeGreen, topLeft = Offset(capX,                      gapBottom), size = Size(capW,       capH), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(color = PipeDark,  topLeft = Offset(capX + capW - 7.dp.toPx(), gapBottom), size = Size(7.dp.toPx(), capH), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(color = PipeTrim,  topLeft = Offset(capX + 4.dp.toPx(),        gapBottom), size = Size(5.dp.toPx(), capH), cornerRadius = CornerRadius(4.dp.toPx()))
        // Body
        drawRect(color = PipeGreen, topLeft = Offset(x,                        gapBottom + capH), size = Size(width,       pipeH - capH))
        drawRect(color = PipeDark,  topLeft = Offset(x + width - 6.dp.toPx(),  gapBottom + capH), size = Size(6.dp.toPx(), pipeH - capH))
        drawRect(color = PipeTrim,  topLeft = Offset(x + 4.dp.toPx(),          gapBottom + capH), size = Size(4.dp.toPx(), pipeH - capH))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HUD composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.ScoreHud(score: Int, bestScore: Int) {
    Column(
        modifier            = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = "$score",
            fontSize   = 52.sp,
            fontWeight = FontWeight.Black,
            color      = Color.White,
            style      = LocalTextStyle.current.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(2f, 2f), 4f)
            )
        )
        if (bestScore > 0 && score < bestScore) {
            Text(
                text  = "Best $bestScore",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(1f, 1f), 2f)
                )
            )
        }
    }
}

@Composable
private fun IdleSplash(petName: String) {
    val bounce  = rememberInfiniteTransition(label = "splash_bounce")
    val offsetY by bounce.animateFloat(
        initialValue  = 0f,
        targetValue   = -10f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashY"
    )
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.offset(y = offsetY.dp)
        ) {
            Text("🐦", fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "Flappy $petName",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Black,
                color      = Color.White,
                style      = LocalTextStyle.current.copy(
                    shadow = Shadow(Color(0xFF1A3A1A), Offset(2f, 2f), 6f)
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Tap anywhere to start",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(1f, 1f), 3f)
                )
            )
        }
    }
}

@Composable
private fun DeadOverlay(
    score: Int,
    bestScore: Int,
    rewardText: String?
) {
    val isNewBest: Boolean = score > 0 && score >= bestScore

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(OverlayBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(32.dp)
        ) {
            Text(
                text       = if (isNewBest) "🏆 New Best!" else "💥 Game Over",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Black,
                color      = if (isNewBest) ScoreYellow else Color.White
            )

            Spacer(Modifier.height(20.dp))

            // Score card
            Column(
                modifier            = Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Score", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                Text(
                    text       = "$score",
                    fontSize   = 56.sp,
                    fontWeight = FontWeight.Black,
                    color      = Color.White
                )
                if (bestScore > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = "Best: $bestScore", fontSize = 15.sp, color = ScoreYellow)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Reward toast
            if (rewardText != null) {
                val pulse = rememberInfiniteTransition(label = "reward_pulse")
                val scale by pulse.animateFloat(
                    initialValue  = 1f,
                    targetValue   = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(durationMillis = 500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rewardScale"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .background(AccentOrange.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text       = rewardText,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))

            // Blinking restart prompt
            val tapPulse = rememberInfiniteTransition(label = "tap_pulse")
            val tapAlpha by tapPulse.animateFloat(
                initialValue  = 0.5f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(durationMillis = 700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "tapAlpha"
            )
            Text(
                text      = "Tap anywhere to try again",
                fontSize  = 15.sp,
                color     = Color.White.copy(alpha = tapAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}