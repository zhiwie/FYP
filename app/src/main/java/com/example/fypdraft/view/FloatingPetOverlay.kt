package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingPetOverlay(
    petState: PetState,
    petRepository: PetRepository,
    isPlaying: Boolean = false,
    currentScreen: String = "home",
    playerError: Boolean = false,
    justLeveledUp: Boolean = false,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Position & drag
    var offsetX by remember { mutableFloatStateOf(screenWidth - 200f) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - 400f) }
    var isDragging by remember { mutableStateOf(false) }
    var wasJustDropped by remember { mutableStateOf(false) }

    // Interaction tracking
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastSongTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var recentTapCount by remember { mutableIntStateOf(0) }
    var isFirstOpenToday by remember { mutableStateOf(true) }
    var justCompletedSong by remember { mutableStateOf(false) }
    var justFedSnack by remember { mutableStateOf(false) }
    var justGroomed by remember { mutableStateOf(false) }

    // Speech
    var showBubble by remember { mutableStateOf(false) }
    var bubbleText by remember { mutableStateOf("") }
    var currentBehavior by remember { mutableStateOf(PetBehavior.IDLE) }

    // Music tracking
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (isPlaying && !wasPlaying) {
            lastSongTime = System.currentTimeMillis()
            lastInteractionTime = System.currentTimeMillis()
            justCompletedSong = false
        } else if (!isPlaying && wasPlaying) {
            justCompletedSong = true
            delay(3000); justCompletedSong = false
        }
        wasPlaying = isPlaying
    }

    // Snack/groom effect tracking
    val prevSnacksFed = remember { mutableIntStateOf(petState.snacksFedToday) }
    LaunchedEffect(petState.snacksFedToday) {
        if (petState.snacksFedToday > prevSnacksFed.intValue) {
            justFedSnack = true; delay(3000); justFedSnack = false
        }
        prevSnacksFed.intValue = petState.snacksFedToday
    }

    val prevGroomed = remember { mutableStateOf(petState.groomedToday) }
    LaunchedEffect(petState.groomedToday) {
        if (petState.groomedToday && !prevGroomed.value) {
            justGroomed = true; delay(3000); justGroomed = false
        }
        prevGroomed.value = petState.groomedToday
    }

    // Reset timers
    LaunchedEffect(isFirstOpenToday) { if (isFirstOpenToday) { delay(4000); isFirstOpenToday = false } }
    LaunchedEffect(wasJustDropped) { if (wasJustDropped) { delay(600); wasJustDropped = false } }
    LaunchedEffect(recentTapCount) {
        if (recentTapCount > 0) {
            petRepository.recordInteraction()
            delay(4000); recentTapCount = 0
        }
    }

    // Needs decay every 30 seconds
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); petRepository.decayNeeds() }
    }

    // Behavior engine tick
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val context = PetContext(
                mood = petState.mood, isPlaying = isPlaying, currentScreen = currentScreen,
                secondsSinceLastInteraction = (now - lastInteractionTime) / 1000,
                secondsSinceLastSong = (now - lastSongTime) / 1000,
                tapCount = recentTapCount, isDragging = isDragging,
                wasJustDropped = wasJustDropped, isFirstOpenToday = isFirstOpenToday,
                playerError = playerError, justCompletedSong = justCompletedSong,
                justLeveledUp = justLeveledUp, justFedSnack = justFedSnack,
                justGroomed = justGroomed, petHappiness = petState.happiness,
                petEnergy = petState.energy, petCleanliness = petState.cleanliness,
                hasActiveEffect = petState.hasActiveEffect,
                hasCraving = petState.currentCraving != null,
                cravingSatisfied = petState.cravingSatisfied,
                bondingTier = petState.bondingTier
            )
            val newBehavior = PetBehaviorEngine.determineBehavior(context)
            if (newBehavior != currentBehavior) {
                val text = if (newBehavior == PetBehavior.CRAVING) {
                    GENRE_CRAVINGS.find { it.genre == petState.currentCraving }?.message
                } else {
                    PetBehaviorEngine.getBubbleText(newBehavior, petState.name)
                }
                if (text != null) { bubbleText = text; showBubble = true }
                currentBehavior = newBehavior
            }
            delay(500)
        }
    }

    LaunchedEffect(showBubble) { if (showBubble) { delay(3500); showBubble = false } }

    // Animations
    val animation = PetBehaviorEngine.behaviorToAnimation(currentBehavior)
    val landScale by animateFloatAsState(
        if (wasJustDropped) 1.3f else 1f,
        spring(dampingRatio = 0.3f, stiffness = 300f), label = "ls"
    )
    val landStretch by animateFloatAsState(
        if (wasJustDropped) 0.7f else 1f,
        spring(dampingRatio = 0.3f, stiffness = 300f), label = "lst"
    )
    val dragRotation by animateFloatAsState(
        if (isDragging) 15f else 0f,
        spring(dampingRatio = 0.5f), label = "dr"
    )

    val inf = rememberInfiniteTransition(label = "fp")
    val sleepBob by inf.animateFloat(
        0f,
        if (currentBehavior == PetBehavior.SLEEPING || currentBehavior == PetBehavior.FALLING_ASLEEP) 4f else 0f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "sb"
    )
    val celebScale by inf.animateFloat(
        1f, if (currentBehavior == PetBehavior.CELEBRATING) 1.25f else 1f,
        infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "cs"
    )
    val idleSway by inf.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), label = "is"
    )
    // Snack effect glow
    val effectGlow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "eg"
    )

    // Render
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), (offsetY + sleepBob).roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true; lastInteractionTime = System.currentTimeMillis(); recentTapCount = 0 },
                    onDragEnd = { isDragging = false; wasJustDropped = true; lastInteractionTime = System.currentTimeMillis() },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - 180f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - 250f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    recentTapCount++
                    lastInteractionTime = System.currentTimeMillis()
                    onTap()
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Speech bubble
            AnimatedVisibility(showBubble, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 6.dp, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(bubbleText, Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 150.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center)
                }
            }

            // Confetti
            if (currentBehavior == PetBehavior.CELEBRATING) CelebrationConfetti()

            // Pet
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = landScale * celebScale; scaleY = landStretch * celebScale
                        rotationZ = dragRotation + idleSway
                        alpha = if (currentBehavior == PetBehavior.SLEEPING) 0.7f else 1f
                    }
                    .shadow(if (isDragging) 16.dp else 6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (petState.hasActiveEffect) getMoodColor(petState.mood).copy(alpha = effectGlow)
                        else getMoodColor(petState.mood).copy(alpha = if (isDragging) 0.5f else 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Dusty overlay when cleanliness is low
                if (petState.cleanliness < 0.3f) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF795548).copy(alpha = 0.2f)))
                }

                PixelPet(petState = petState, animation = animation, modifier = Modifier.size(58.dp), isPlaying = isPlaying)

                // Sparkle effect after grooming
                if (justGroomed) {
                    SparkleEffect()
                }
            }

            Spacer(Modifier.height(2.dp))

            // Mini needs bars
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MiniBar(petState.energy, Color(0xFFFFB300), 16.dp)
                MiniBar(petState.cleanliness, Color(0xFF42A5F5), 16.dp)
                MiniBar(petState.happiness / 100f, Color(0xFFE91E63), 16.dp)
            }

            // Zzz
            if (currentBehavior == PetBehavior.SLEEPING || currentBehavior == PetBehavior.FALLING_ASLEEP) FloatingZzz()
        }
    }
}

@Composable
private fun MiniBar(value: Float, color: Color, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(Color.White.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
private fun SparkleEffect() {
    val sparkles = listOf("✨", "⭐", "✨")
    sparkles.forEachIndexed { i, s ->
        val inf = rememberInfiniteTransition(label = "sp_$i")
        val a by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(800 + i * 200), RepeatMode.Restart), label = "sa$i")
        val y by inf.animateFloat(0f, -20f, infiniteRepeatable(tween(800 + i * 200, easing = EaseOut), RepeatMode.Restart), label = "sy$i")
        Text(s, fontSize = 10.sp, modifier = Modifier.offset(x = (-10 + i * 10).dp, y = y.dp).graphicsLayer { alpha = a })
    }
}

@Composable
private fun CelebrationConfetti() {
    listOf("🎉", "⭐", "🎊", "✨", "🎵").forEachIndexed { i, p ->
        val inf = rememberInfiniteTransition(label = "cf_$i")
        val y by inf.animateFloat(0f, -40f, infiniteRepeatable(tween(600 + i * 100, easing = EaseOut), RepeatMode.Restart), label = "cy$i")
        val a by inf.animateFloat(1f, 0f, infiniteRepeatable(tween(600 + i * 100), RepeatMode.Restart), label = "ca$i")
        Text(p, fontSize = 12.sp, modifier = Modifier.offset(x = (-24 + i * 12).dp, y = y.dp).graphicsLayer { alpha = a })
    }
}

@Composable
private fun FloatingZzz() {
    val inf = rememberInfiniteTransition(label = "zzz")
    val y by inf.animateFloat(0f, -20f, infiniteRepeatable(tween(1500, easing = EaseOut), RepeatMode.Restart), label = "zy")
    val a by inf.animateFloat(0.8f, 0f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "za")
    Text("💤", fontSize = 14.sp, modifier = Modifier.offset(x = 20.dp, y = y.dp).graphicsLayer { alpha = a })
}

private fun getMoodColor(mood: String): Color = when (mood) {
    "happy" -> Color(0xFFFFB347); "sad" -> Color(0xFF667EEA); "calm" -> Color(0xFF89CFF0)
    "energetic" -> Color(0xFFFF416C); "tired" -> Color(0xFF607D8B); "focused" -> Color(0xFF11998E)
    "romantic" -> Color(0xFFEE9CA7); else -> Color(0xFF9C27B0)
}