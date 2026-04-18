package com.example.fypdraft.view

// ═══════════════════════════════════════════════════════════════════════════════
//  LayeredAvatarSystem.kt
//
//  Modular, layered mascot avatar for MoodSync.
//
//  Architecture
//  ─────────────
//  Each avatar is rendered by stacking Compose Image layers in a Box:
//
//      ┌─────────────────────────┐
//      │  shadow.png             │  ← always present, bottom-most
//      │  {pet}_idle.png         │  ← base state (idle/sleepy/sad/blink/up)
//      │  {pet}_sunglasses.png   │  ← optional accessory (GLASSES category)
//      │  {pet}_tie.png          │  ← optional accessory (NECKLACE category)
//      └─────────────────────────┘
//
//  All PNGs are 512×512 with transparent backgrounds and share the same
//  coordinate space — no alignment math needed, just stack and render.
//
//  Behaviour mapping (app context → avatar state)
//  ────────────────────────────────────────────────
//  • Music playing              → AvatarState.LISTENING  (uses cat_up / penguin_up / elephant up)
//  • Inactive / low energy      → AvatarState.SLEEPY
//  • Error / sad mood           → AvatarState.SAD
//  • Periodic auto-blink        → AvatarState.BLINK      (shown for ~150ms then back to IDLE)
//  • Default                    → AvatarState.IDLE
//
//  Animations (all Compose, no extra libraries)
//  ────────────────────────────────────────────
//  • Bounce  — gentle vertical sine when idle / listening
//  • Breathe — subtle vertical scale pulse (body expand/contract)
//  • Blink   — auto-triggered every ~4-6 seconds (BLINK state for 150ms)
//  • Dance   — faster bounce + slight rotation when music plays
//  • Sway    — small rotationZ while dragging in SmartFloatingPet
// ═══════════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fypdraft.R
import com.example.fypdraft.model.*
import kotlinx.coroutines.delay
import kotlin.random.Random

// ── Avatar state enum ─────────────────────────────────────────────────────────

/**
 * Visual states that map to full-body base images.
 * BLINK is a transient state (shown for ~150ms) that auto-returns to IDLE.
 */
enum class AvatarState {
    IDLE,      // default resting look
    SLEEPY,    // eyes half-closed, used when inactive / low energy
    SAD,       // droopy expression, used on error or sad mood
    LISTENING, // perked up / head tilted, used while music plays (the "_up" sprites)
    BLINK      // brief eyes-closed frame, auto-triggered periodically
}

// ── Accessory category enum (aligned to your actual PNGs) ────────────────────

/**
 * Categories that have real drawable assets.
 * Only one item per category may be equipped at a time.
 */
enum class AvatarAccessoryCategory {
    GLASSES,   // cat_sunglasses / penguin_sunglasses / elephant_sunglasses
    NECKLACE   // cat_tie / penguin_tie / elephant_tie
}

/**
 * A single equipped overlay accessory.
 */
data class AvatarAccessory(
    val id: String,
    val category: AvatarAccessoryCategory,
    val label: String
)

// ── Predefined accessory catalogue (matching your drawable names) ─────────────

object AvatarAccessoryRegistry {
    val SUNGLASSES = AvatarAccessory("sunglasses", AvatarAccessoryCategory.GLASSES, "Sunglasses 😎")
    val TIE        = AvatarAccessory("tie",        AvatarAccessoryCategory.NECKLACE, "Tie 👔")

    val all = listOf(SUNGLASSES, TIE)
}

// ── Pet species supported by the asset set ────────────────────────────────────

enum class AvatarSpecies(val key: String, val displayName: String, val emoji: String) {
    CAT("cat",       "Kitty",   "🐱"),
    PENGUIN("penguin", "Penguin", "🐧"),
    ELEPHANT("elephant", "Ellie", "🐘");

    companion object {
        fun fromPetType(type: PetType): AvatarSpecies = when (type) {
            PetType.CAT  -> CAT
            PetType.DOG  -> PENGUIN   // fallback: dog → penguin sprite
            PetType.BEAR -> ELEPHANT  // fallback: bear → elephant sprite
            PetType.BUNNY -> CAT      // fallback: bunny → cat sprite
        }
    }
}

// ── Asset resolver ────────────────────────────────────────────────────────────

/**
 * Maps (species, state, accessory) → drawable resource IDs.
 * All images live in res/drawable/.
 */
object AvatarAssets {

    /** Returns the drawable res-ID for the base body layer. */
    fun baseRes(species: AvatarSpecies, state: AvatarState): Int = when (species) {
        AvatarSpecies.CAT -> when (state) {
            AvatarState.IDLE      -> R.drawable.cat_idle
            AvatarState.SLEEPY    -> R.drawable.cat_sleepy
            AvatarState.SAD       -> R.drawable.cat_sad
            AvatarState.LISTENING -> R.drawable.cat_up
            AvatarState.BLINK     -> R.drawable.cat_blink
        }
        AvatarSpecies.PENGUIN -> when (state) {
            AvatarState.IDLE      -> R.drawable.penguin_idle
            AvatarState.SLEEPY    -> R.drawable.penguin_sleepy
            AvatarState.SAD       -> R.drawable.penguin_sad
            AvatarState.LISTENING -> R.drawable.penguin_up
            AvatarState.BLINK     -> R.drawable.penguin_blink
        }
        AvatarSpecies.ELEPHANT -> when (state) {
            AvatarState.IDLE      -> R.drawable.elephant_idle
            AvatarState.SLEEPY    -> R.drawable.elephant_sleepy
            AvatarState.SAD       -> R.drawable.elephant_sad
            AvatarState.LISTENING -> R.drawable.elephant_up
            AvatarState.BLINK     -> R.drawable.elephant_blink
        }
    }

    /** Returns the drawable res-ID for an accessory overlay, or null if not applicable. */
    fun accessoryRes(species: AvatarSpecies, accessory: AvatarAccessory): Int? = when (accessory.category) {
        AvatarAccessoryCategory.GLASSES -> when (species) {
            AvatarSpecies.CAT      -> R.drawable.cat_sunglasses
            AvatarSpecies.PENGUIN  -> R.drawable.penguin_sunglasses
            AvatarSpecies.ELEPHANT -> R.drawable.elephant_sunglasses
        }
        AvatarAccessoryCategory.NECKLACE -> when (species) {
            AvatarSpecies.CAT      -> R.drawable.cat_tie
            AvatarSpecies.PENGUIN  -> R.drawable.penguin_tie
            AvatarSpecies.ELEPHANT -> R.drawable.elephant_tie
        }
    }

    val shadowRes: Int = R.drawable.shadow
}

// ── Avatar state resolver (app context → AvatarState) ────────────────────────

/**
 * Derives the correct AvatarState from the current app context.
 * Priority: explicit blink override > music playing > mood/energy fallback.
 */
fun resolveAvatarState(
    isBlinking: Boolean,
    isMusicPlaying: Boolean,
    petMood: String,
    energy: Float
): AvatarState = when {
    isBlinking                              -> AvatarState.BLINK
    isMusicPlaying                          -> AvatarState.LISTENING
    energy < 0.25f                          -> AvatarState.SLEEPY
    petMood in listOf("sad", "tired")       -> AvatarState.SLEEPY
    petMood == "sad" && energy >= 0.25f     -> AvatarState.SAD
    else                                    -> AvatarState.IDLE
}

// ── Main composable ───────────────────────────────────────────────────────────

/**
 * LayeredAvatar
 *
 * Renders a fully layered mascot at runtime by compositing:
 *   1. Shadow  (bottom)
 *   2. Base body state image
 *   3. Accessories (in category order: GLASSES, then NECKLACE)
 *
 * All compositing is done via Compose Box stacking — no bitmaps are merged.
 *
 * @param species       Which animal set to use
 * @param state         Current visual state (IDLE / SLEEPY / SAD / LISTENING / BLINK)
 * @param accessories   List of active accessories (one per category enforced)
 * @param isMusicPlaying Whether music is playing (drives animation speed)
 * @param size          Rendered square size; all layers share this
 * @param facingRight   Flips the whole avatar horizontally when walking left
 * @param isDragging    Adds a slight tilt when user is dragging the pet
 * @param modifier      External Modifier
 */
@Composable
fun LayeredAvatar(
    species: AvatarSpecies,
    state: AvatarState,
    accessories: List<AvatarAccessory> = emptyList(),
    isMusicPlaying: Boolean = false,
    size: Dp = 96.dp,
    facingRight: Boolean = true,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ── Infinite animation values ─────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "avatar_anim")

    // Bounce: vertical offset
    val bounceY by inf.animateFloat(
        initialValue = 0f,
        targetValue = when {
            isMusicPlaying                   -> -8f   // energetic dancing bob
            state == AvatarState.LISTENING   -> -5f
            state == AvatarState.SLEEPY      -> 1.5f  // slight sink
            state == AvatarState.SAD         -> 2f
            else                             -> -3f   // gentle idle float
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isMusicPlaying) 380 else 1_600,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    // Breathe: subtle vertical scale (chest expand/contract)
    val breatheScale by inf.animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            AvatarState.SLEEPY -> 1.03f
            AvatarState.IDLE   -> 1.015f
            else               -> 1.01f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AvatarState.SLEEPY) 2_800 else 2_000,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Sway: rotationZ for dancing and dragging
    val swayZ by inf.animateFloat(
        initialValue = if (isMusicPlaying) -4f else -1f,
        targetValue  = if (isMusicPlaying) 4f  else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isMusicPlaying) 320 else 2_200,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swayZ"
    )

    // ── Render ────────────────────────────────────────────────────────────
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // All layers are placed inside a single animated Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY   = bounceY
                    scaleY         = breatheScale
                    scaleX         = if (facingRight) 1f else -1f
                    rotationZ      = if (isDragging) swayZ * 2.5f else if (isMusicPlaying) swayZ else 0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f) // pivot at feet
                },
            contentAlignment = Alignment.Center
        ) {
            // Layer 1: Shadow (bottom)
            // Note: negative padding is illegal in Compose — use graphicsLayer translationY instead.
            androidx.compose.foundation.Image(
                painter    = painterResource(AvatarAssets.shadowRes),
                contentDescription = null,
                modifier   = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        scaleY       = 0.35f
                        alpha        = 0.35f
                        // push shadow slightly below the avatar feet + counter the bounce
                        translationY = 4.dp.toPx() + (-bounceY * 0.8f)
                    }
            )

            // Layer 2: Base body state image
            androidx.compose.foundation.Image(
                painter            = painterResource(AvatarAssets.baseRes(species, state)),
                contentDescription = species.displayName,
                modifier           = Modifier.fillMaxSize()
            )

            // Layer 3+: Accessories (GLASSES first so tie renders on top)
            val glasses = accessories.firstOrNull { it.category == AvatarAccessoryCategory.GLASSES }
            val necklace = accessories.firstOrNull { it.category == AvatarAccessoryCategory.NECKLACE }

            glasses?.let { acc ->
                AvatarAssets.accessoryRes(species, acc)?.let { resId ->
                    androidx.compose.foundation.Image(
                        painter            = painterResource(resId),
                        contentDescription = acc.label,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }

            necklace?.let { acc ->
                AvatarAssets.accessoryRes(species, acc)?.let { resId ->
                    androidx.compose.foundation.Image(
                        painter            = painterResource(resId),
                        contentDescription = acc.label,
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── LiveAvatar: full lifecycle + auto-blink composable ────────────────────────

/**
 * LiveAvatar wraps LayeredAvatar and manages:
 *  - Auto-blink every 4–7 seconds (shows BLINK state for 150ms)
 *  - App-context → AvatarState resolution
 *  - Accessory deduplication (category constraint)
 *
 * Drop this in anywhere you previously used LottiePetView or PixelPet.
 *
 * @param petState        Current PetState from PetRepository
 * @param isMusicPlaying  Whether Spotify / local music is actively playing
 * @param equippedIds     List of accessory IDs currently equipped (from petState)
 * @param size            Rendered size
 * @param facingRight     Flip direction for wandering
 * @param isDragging      Extra tilt while user drags
 */
@Composable
fun LiveAvatar(
    petState: PetState,
    isMusicPlaying: Boolean,
    equippedIds: List<String> = emptyList(),
    size: Dp = 96.dp,
    facingRight: Boolean = true,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val species = AvatarSpecies.fromPetType(petState.type)

    // ── Resolve accessories from equippedIds ──────────────────────────────
    // Accept only one per category; known IDs: "sunglasses", "tie"
    val accessories: List<AvatarAccessory> = remember(equippedIds) {
        val result = mutableListOf<AvatarAccessory>()
        val seenCategories = mutableSetOf<AvatarAccessoryCategory>()
        for (id in equippedIds) {
            val acc = AvatarAccessoryRegistry.all.firstOrNull { it.id == id } ?: continue
            if (acc.category !in seenCategories) {
                result.add(acc)
                seenCategories.add(acc.category)
            }
        }
        result
    }

    // ── Auto-blink state ──────────────────────────────────────────────────
    var isBlinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            // Wait 4–7 seconds between blinks
            delay(Random.nextLong(4_000L, 7_000L))
            isBlinking = true
            delay(150L) // blink frame duration
            isBlinking = false
        }
    }

    // ── Derive visual state from app context ──────────────────────────────
    val avatarState = resolveAvatarState(
        isBlinking     = isBlinking,
        isMusicPlaying = isMusicPlaying,
        petMood        = petState.mood,
        energy         = petState.energy
    )

    LayeredAvatar(
        species        = species,
        state          = avatarState,
        accessories    = accessories,
        isMusicPlaying = isMusicPlaying,
        size           = size,
        facingRight    = facingRight,
        isDragging     = isDragging,
        modifier       = modifier
    )
}

// ── Convenience extension: build equippedIds list from PetState ───────────────

/**
 * Maps PetState equipped slots → layered accessory token list.
 *
 * The new system stores tokens directly:
 *   equippedGlasses  = "sunglasses"  → shows cat/penguin/elephant_sunglasses.png
 *   equippedNecklace = "tie"         → shows cat/penguin/elephant_tie.png
 *
 * Legacy values (e.g. "glass_cool", "neck_note") are ignored here because
 * they refer to the old emoji-only accessory system that has no PNG overlays.
 */
fun PetState.avatarEquippedIds(): List<String> {
    val ids = mutableListOf<String>()
    if (equippedGlasses == "sunglasses") ids.add("sunglasses")
    if (equippedNecklace == "tie")       ids.add("tie")
    return ids
}