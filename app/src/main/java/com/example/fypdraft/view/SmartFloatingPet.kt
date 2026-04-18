package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * SmartFloatingPet — floating draggable mascot overlay.
 *
 * Replaces the old LottiePetView / PixelPet renderer with the new
 * [LiveAvatar] layered system. No pre-rendered combined images are used —
 * each visual state + accessory is composited at runtime from individual PNGs.
 *
 *  Behaviour:
 *  - Drops in from top on first appearance
 *  - Wanders slowly between screen edges (Tamagotchi style)
 *  - Flips horizontally to face the direction of travel
 *  - Shows thought bubbles while idle
 *  - Single tap  → EmotionChat
 *  - Long press  → PetShop
 */
@Composable
fun SmartFloatingPet(
    petState: PetState,
    petRepository: PetRepository,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config  = LocalConfiguration.current
    val scope   = rememberCoroutineScope()

    val screenW  = config.screenWidthDp.toFloat()
    val screenH  = config.screenHeightDp.toFloat()
    val petSizeDp = 80f          // slightly larger for the layered avatar

    val leftEdge  = 4f
    val rightEdge = screenW - petSizeDp - 4f
    val topEdge   = 72f
    val botEdge   = screenH - petSizeDp - 100f

    // ── Position state ────────────────────────────────────────────────────
    val animX = remember { Animatable(rightEdge) }
    val animY = remember { Animatable(-petSizeDp - 20f) }

    var facingRight by remember { mutableStateOf(false) }
    var isDragging  by remember { mutableStateOf(false) }
    var thought     by remember { mutableStateOf("") }
    var hasEntered  by remember { mutableStateOf(false) }

    // ── Entry drop ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(300)
        animY.animateTo(
            topEdge + 40f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)
        )
        hasEntered = true
    }

    // ── Auto-wander ───────────────────────────────────────────────────────
    LaunchedEffect(hasEntered) {
        if (!hasEntered) return@LaunchedEffect
        while (true) {
            val lingerMs = Random.nextLong(3_500, 7_000)

            if (Random.nextFloat() < 0.35f) {
                thought = when {
                    isPlaying -> listOf("🎵", "🎶", "🎧", "✨", "🕺").random()
                    else      -> listOf("💤", "🌙", "⭐", "💭", "🍕").random()
                }
                delay(2_500L.coerceAtMost(lingerMs))
                thought = ""
                delay((lingerMs - 2_500L).coerceAtLeast(0))
            } else {
                delay(lingerMs)
            }

            if (isDragging) { delay(400); continue }

            val goRight = Random.nextBoolean()
            val targetX = if (goRight) rightEdge - Random.nextFloat() * 24f
            else         leftEdge   + Random.nextFloat() * 24f
            val targetY = topEdge + Random.nextFloat() * (botEdge - topEdge)

            facingRight = targetX > animX.value

            val distX  = abs(targetX - animX.value)
            val distY  = abs(targetY - animY.value)
            val walkMs = ((distX + distY) * 12f).toInt().coerceIn(1_800, 6_000)

            scope.launch { animX.animateTo(targetX, tween(walkMs, easing = LinearEasing)) }
            animY.animateTo(targetY, tween(walkMs, easing = EaseInOutSine))
        }
    }

    // ── Render ────────────────────────────────────────────────────────────
    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { animX.value.dp.roundToPx() },
                        with(density) { animY.value.dp.roundToPx() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart  = { isDragging = true },
                        onDragEnd    = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag       = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val nx = (animX.value + dragAmount.x / density.density)
                                    .coerceIn(0f, screenW - petSizeDp)
                                val ny = (animY.value + dragAmount.y / density.density)
                                    .coerceIn(0f, screenH - petSizeDp)
                                animX.snapTo(nx)
                                animY.snapTo(ny)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap       = { onTap() },
                        onLongPress = { onLongPress() }
                    )
                }
        ) {
            // Thought bubble
            if (thought.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-24).dp)
                        .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(thought, fontSize = 15.sp) }
            }

            // ── LiveAvatar replaces LottiePetView / PixelPet ─────────────
            LiveAvatar(
                petState       = petState,
                isMusicPlaying = isPlaying,
                equippedIds    = petState.avatarEquippedIds(),
                size           = petSizeDp.dp,
                facingRight    = facingRight,
                isDragging     = isDragging,
                modifier       = Modifier.size(petSizeDp.dp)
            )
        }
    }
}