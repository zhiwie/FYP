package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Smart Floating Pet that:
 *   1. Enters from top of screen (Mary Poppins descent)
 *   2. Wanders around screen edges to minimize UI blockage
 *   3. Shows thought bubbles
 *   4. Has spring-physics movement with anticipation
 *   5. Stays within screen bounds
 *   6. Reacts to music state
 *
 * Replaces the static FloatingPetOverlay when the mascot widget
 * is scrolled off screen (or on non-Home screens).
 */
@Composable
fun SmartFloatingPet(
    petState: PetState,
    petRepository: PetRepository,
    isPlaying: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val screenHeightDp = config.screenHeightDp.toFloat()

    // Pet size
    val petSize = 60f // dp

    // Safe bounds (keep pet within screen, biased toward edges)
    val minX = 4f
    val maxX = screenWidthDp - petSize - 4f
    val minY = 60f  // Below status bar
    val maxY = screenHeightDp - petSize - 120f // Above bottom nav

    // ── Position state with spring animation ─────────────────────────
    val posX = remember { Animatable(maxX) } // Start at right edge
    val posY = remember { Animatable(-petSize - 20f) } // Start above screen (off-screen top)

    // Entry animation — float down from top
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        // Mary Poppins descent: start from top, gentle spring to initial position
        posY.animateTo(
            minY + 40f,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)
        )
        hasEntered = true
    }

    // ── Wander logic ─────────────────────────────────────────────────
    // The pet wanders along the edges of the screen to minimize blockage
    var wanderPhase by remember { mutableIntStateOf(0) } // 0=idle, 1=moving, 2=lingering
    var currentThought by remember { mutableStateOf("") }

    LaunchedEffect(hasEntered) {
        if (!hasEntered) return@LaunchedEffect

        while (true) {
            // Linger at current position for 4-8 seconds
            wanderPhase = 2
            val lingerTime = Random.nextLong(4000, 8000)

            // Maybe show a thought
            if (Random.nextFloat() < 0.3f) {
                currentThought = when {
                    isPlaying -> listOf("🎵", "🎶", "🎧", "✨", "💃").random()
                    else -> listOf("💤", "💭", "🌙", "⭐", "🍕", "❓").random()
                }
                delay(2500)
                currentThought = ""
                delay(lingerTime - 2500)
            } else {
                delay(lingerTime)
            }

            // Pick a new position — biased toward edges
            wanderPhase = 1
            val newPos = pickEdgeBiasedPosition(minX, maxX, minY, maxY, petSize)

            // Move with spring physics
            posX.animateTo(newPos.first, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow))
            posY.animateTo(newPos.second, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow))

            wanderPhase = 0
        }
    }

    // ── Alive animations ─────────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "floatPet")

    // Gentle floating bob
    val floatY by inf.animateFloat(
        0f, if (isPlaying) -6f else -3f,
        infiniteRepeatable(tween(if (isPlaying) 500 else 2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "floatBob"
    )

    // Slight rotation sway
    val sway by inf.animateFloat(
        -5f, 5f,
        infiniteRepeatable(tween(if (isPlaying) 400 else 3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "floatSway"
    )

    // Scale pulse when moving
    val movePulse by animateFloatAsState(
        if (wanderPhase == 1) 1.1f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "movePulse"
    )

    // ── Render ───────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = posX.value.dp, y = posY.value.dp)
                .graphicsLayer {
                    translationY = floatY
                    rotationZ = sway
                    scaleX = movePulse
                    scaleY = movePulse
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTap() }
        ) {
            // Thought bubble above pet
            if (currentThought.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(currentThought, fontSize = 14.sp)
                }
            }

            // Pet
            LottiePetView(
                petState = petState,
                animation = when {
                    isPlaying -> PetAnimation.DANCING
                    wanderPhase == 1 -> PetAnimation.HAPPY_BOUNCE
                    else -> PetAnimation.IDLE
                },
                isPlaying = isPlaying,
                modifier = Modifier.size(petSize.dp)
            )
        }
    }
}

/**
 * Pick a position biased toward screen edges to minimize UI blockage.
 * 70% chance of edge position, 30% chance of anywhere (but still near edge).
 */
private fun pickEdgeBiasedPosition(
    minX: Float, maxX: Float, minY: Float, maxY: Float, petSize: Float
): Pair<Float, Float> {
    val edge = Random.nextInt(4)
    return when {
        Random.nextFloat() < 0.7f -> {
            // Stick to an edge
            when (edge) {
                0 -> minX to Random.nextFloat() * (maxY - minY) + minY           // Left
                1 -> maxX to Random.nextFloat() * (maxY - minY) + minY           // Right
                2 -> Random.nextFloat() * (maxX - minX) + minX to minY           // Top
                else -> Random.nextFloat() * (maxX - minX) + minX to maxY        // Bottom
            }
        }
        else -> {
            // Near-edge but not exactly on it
            val margin = (maxX - minX) * 0.2f
            val x = if (Random.nextBoolean()) minX + Random.nextFloat() * margin else maxX - Random.nextFloat() * margin
            val y = Random.nextFloat() * (maxY - minY) + minY
            x to y
        }
    }
}