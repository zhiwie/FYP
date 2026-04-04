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
import androidx.compose.ui.graphics.graphicsLayer
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
 * SmartFloatingPet:
 *  - Drops in from top on first appearance
 *  - Walks slowly between left/right screen edges (like a Tamagotchi in its enclosure)
 *  - Flips horizontally to face the direction of travel
 *  - User can drag it anywhere; wander pauses while dragging
 *  - Single tap  -> EmotionChat (AI companion)
 *  - Long press  -> PetShop
 *  - Shows thought bubbles while idle
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

    val screenW = config.screenWidthDp.toFloat()
    val screenH = config.screenHeightDp.toFloat()
    val petSizeDp = 64f

    val leftEdge  = 4f
    val rightEdge = screenW - petSizeDp - 4f
    val topEdge   = 72f
    val botEdge   = screenH - petSizeDp - 100f

    // Position state
    val animX = remember { Animatable(rightEdge) }
    val animY = remember { Animatable(-petSizeDp - 20f) }

    // Whether pet is walking right (used to flip sprite)
    var facingRight by remember { mutableStateOf(false) }
    var isDragging  by remember { mutableStateOf(false) }
    var thought     by remember { mutableStateOf("") }
    var hasEntered  by remember { mutableStateOf(false) }

    // Entry drop
    LaunchedEffect(Unit) {
        delay(300)
        animY.animateTo(topEdge + 40f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
        hasEntered = true
    }

    // Auto-wander: walks slowly between edges
    LaunchedEffect(hasEntered) {
        if (!hasEntered) return@LaunchedEffect
        while (true) {
            val lingerMs = Random.nextLong(3_500, 7_000)
            if (Random.nextFloat() < 0.35f) {
                thought = when {
                    isPlaying -> listOf("🎵","🎶","🎧","✨","🕺").random()
                    else      -> listOf("💤","🌙","⭐","💭","🍕").random()
                }
                delay(2_500L.coerceAtMost(lingerMs))
                thought = ""
                delay((lingerMs - 2_500L).coerceAtLeast(0))
            } else {
                delay(lingerMs)
            }

            if (isDragging) { delay(400); continue }

            // Always target an edge strip
            val goRight  = Random.nextBoolean()
            val targetX  = if (goRight) rightEdge - Random.nextFloat() * 24f
            else         leftEdge   + Random.nextFloat() * 24f
            val targetY  = topEdge + Random.nextFloat() * (botEdge - topEdge)

            // Face the direction of travel before moving
            facingRight = targetX > animX.value

            // Walk speed: ~80-120 dp/sec so it looks like walking, not teleporting
            val distX    = abs(targetX - animX.value)
            val distY    = abs(targetY - animY.value)
            val walkMs   = ((distX + distY) * 12f).toInt().coerceIn(1_800, 6_000)

            scope.launch {
                animX.animateTo(targetX, tween(walkMs, easing = LinearEasing))
            }
            animY.animateTo(targetY, tween(walkMs, easing = EaseInOutSine))
        }
    }

    // Micro-animations
    val inf = rememberInfiniteTransition(label = "fp")
    val bobY by inf.animateFloat(
        0f, if (isPlaying) -7f else -3f,
        infiniteRepeatable(
            tween(if (isPlaying) 420 else 1_800, easing = EaseInOutSine),
            RepeatMode.Reverse),
        label = "bob"
    )
    val sway by inf.animateFloat(
        -4f, 4f,
        infiniteRepeatable(
            tween(if (isPlaying) 340 else 2_000, easing = EaseInOutSine),
            RepeatMode.Reverse),
        label = "sway"
    )

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { animX.value.dp.roundToPx() },
                        with(density) { (animY.value + bobY).dp.roundToPx() }
                    )
                }
                // Drag — user repositions the pet
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
                // Tap / long-press
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
                        .offset(y = (-22).dp)
                        .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(thought, fontSize = 15.sp) }
            }

            // Pet — flip X to face direction of travel
            Box(
                Modifier
                    .size(petSizeDp.dp)
                    .graphicsLayer(
                        scaleX    = if (facingRight) 1f else -1f,
                        scaleY    = 1f,
                        rotationZ = if (isDragging) sway * 2f else sway * 0.5f
                    )
            ) {
                LottiePetView(
                    petState  = petState,
                    animation = when {
                        isDragging         -> PetAnimation.HAPPY_BOUNCE
                        isPlaying          -> PetAnimation.DANCING
                        animX.isRunning    -> PetAnimation.HAPPY_BOUNCE  // walking
                        else               -> PetAnimation.IDLE
                    },
                    isPlaying = isPlaying,
                    modifier  = Modifier.fillMaxSize()
                )
            }
        }
    }
}