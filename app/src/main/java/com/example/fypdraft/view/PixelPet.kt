package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.fypdraft.model.PetAnimation
import com.example.fypdraft.model.PetState
import com.example.fypdraft.model.PetType

/**
 * Draws a pixel-art pet on Canvas with mood-reactive animations.
 * Each pet type has a distinct silhouette drawn with filled rectangles
 * (pixels) for that authentic Tamagotchi feel.
 */
@Composable
fun PixelPet(
    petState: PetState,
    animation: PetAnimation = petState.animationForMood(),
    modifier: Modifier = Modifier.size(120.dp),
    isPlaying: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pet")

    // ── Bounce / movement based on animation state ──────────────────

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = when (animation) {
            PetAnimation.HAPPY_BOUNCE -> -12f
            PetAnimation.ENERGETIC_JUMP -> -18f
            PetAnimation.DANCING -> -6f
            PetAnimation.LISTENING -> -4f
            PetAnimation.SAD_DROOP -> 3f
            PetAnimation.SLEEPY_NOD -> 2f
            else -> -2f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (animation) {
                    PetAnimation.ENERGETIC_JUMP -> 250
                    PetAnimation.HAPPY_BOUNCE -> 400
                    PetAnimation.DANCING -> 500
                    PetAnimation.SAD_DROOP -> 2000
                    PetAnimation.SLEEPY_NOD -> 2500
                    PetAnimation.LISTENING -> 800
                    else -> 1200
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Side-to-side for dancing
    val sway by infiniteTransition.animateFloat(
        initialValue = when (animation) {
            PetAnimation.DANCING -> -6f
            PetAnimation.ENERGETIC_JUMP -> -4f
            else -> 0f
        },
        targetValue = when (animation) {
            PetAnimation.DANCING -> 6f
            PetAnimation.ENERGETIC_JUMP -> 4f
            else -> 0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (animation) {
                    PetAnimation.DANCING -> 300
                    PetAnimation.ENERGETIC_JUMP -> 200
                    else -> 1000
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    // Blink cycle
    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val isBlinking = blinkPhase > 95f // Blink for ~5% of the cycle

    // Sleepy eye droop
    val eyeDroop by infiniteTransition.animateFloat(
        initialValue = if (animation == PetAnimation.SLEEPY_NOD) 0f else 0f,
        targetValue = if (animation == PetAnimation.SLEEPY_NOD) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeDroop"
    )

    // Music listening ear wiggle
    val earWiggle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying || animation == PetAnimation.LISTENING) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "earWiggle"
    )

    Canvas(modifier = modifier) {
        val px = size.width / 16f // 16x16 pixel grid
        val offsetX = sway
        val offsetY = bounceOffset

        // Draw the pet based on type
        when (petState.type) {
            PetType.CAT -> drawCat(px, offsetX, offsetY, petState, animation, isBlinking, eyeDroop, earWiggle)
            PetType.DOG -> drawDog(px, offsetX, offsetY, petState, animation, isBlinking, eyeDroop, earWiggle)
            PetType.BEAR -> drawBear(px, offsetX, offsetY, petState, animation, isBlinking, eyeDroop, earWiggle)
            PetType.BUNNY -> drawBunny(px, offsetX, offsetY, petState, animation, isBlinking, eyeDroop, earWiggle)
        }

        // Draw accessories on top
        drawAccessories(px, offsetX, offsetY, petState)
    }
}

// ── Pixel helper ─────────────────────────────────────────────────────────

private fun DrawScope.pixel(x: Int, y: Int, px: Float, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) {
    drawRect(
        color = color,
        topLeft = Offset(x * px + offsetX, y * px + offsetY),
        size = Size(px + 0.5f, px + 0.5f) // +0.5 to avoid gaps
    )
}

private fun DrawScope.pixels(coords: List<Pair<Int, Int>>, px: Float, color: Color, ox: Float = 0f, oy: Float = 0f) {
    coords.forEach { (x, y) -> pixel(x, y, px, color, ox, oy) }
}

// ── Cat ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawCat(px: Float, ox: Float, oy: Float, pet: PetState, anim: PetAnimation, blink: Boolean, eyeDroop: Float, earWiggle: Float) {
    val body = Color(0xFFFF9800)      // Orange
    val dark = Color(0xFFE65100)       // Dark orange
    val white = Color.White
    val pink = Color(0xFFFF80AB)
    val black = Color(0xFF1A1A1A)

    // Ears (with wiggle)
    val earOff = earWiggle * 1f
    pixels(listOf(3 to 2, 4 to 2, 3 to 3), px, dark, ox, oy - earOff)       // Left ear
    pixels(listOf(11 to 2, 12 to 2, 12 to 3), px, dark, ox, oy + earOff)    // Right ear
    pixel(4, 3, px, pink, ox, oy - earOff)  // Inner ear
    pixel(11, 3, px, pink, ox, oy + earOff) // Inner ear

    // Head
    val headPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) headPixels.add(x to 3)
    for (x in 3..12) { headPixels.add(x to 4); headPixels.add(x to 5); headPixels.add(x to 6) }
    for (x in 4..11) headPixels.add(x to 7)
    pixels(headPixels, px, body, ox, oy)

    // Eyes
    if (blink || eyeDroop > 0.8f) {
        pixel(5, 5, px, black, ox, oy); pixel(10, 5, px, black, ox, oy) // Closed eyes (line)
    } else if (anim == PetAnimation.SAD_DROOP) {
        pixel(5, 5, px, black, ox, oy); pixel(10, 5, px, black, ox, oy)
        pixel(5, 6, px, Color(0xFF42A5F5), ox, oy) // Tear
    } else if (anim == PetAnimation.LOVE_EYES) {
        pixel(5, 5, px, Color(0xFFE91E63), ox, oy); pixel(10, 5, px, Color(0xFFE91E63), ox, oy) // Heart eyes
    } else {
        // Normal eyes with shine
        pixel(5, 5, px, black, ox, oy); pixel(6, 5, px, black, ox, oy)
        pixel(10, 5, px, black, ox, oy); pixel(9, 5, px, black, ox, oy)
        pixel(5, 5, px, white, ox, oy) // Shine left
        pixel(10, 5, px, white, ox, oy) // Shine right
    }

    // Nose & mouth
    pixel(7, 6, px, pink, ox, oy); pixel(8, 6, px, pink, ox, oy)
    if (anim == PetAnimation.HAPPY_BOUNCE || anim == PetAnimation.DANCING) {
        pixel(6, 7, px, dark, ox, oy); pixel(9, 7, px, dark, ox, oy) // Smile
    }

    // Whiskers
    pixel(2, 5, px, dark, ox, oy); pixel(1, 5, px, dark, ox, oy)   // Left
    pixel(13, 5, px, dark, ox, oy); pixel(14, 5, px, dark, ox, oy) // Right
    pixel(2, 6, px, dark, ox, oy); pixel(13, 6, px, dark, ox, oy)

    // Body
    val bodyPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) { bodyPixels.add(x to 8); bodyPixels.add(x to 9); bodyPixels.add(x to 10) }
    for (x in 5..10) bodyPixels.add(x to 11)
    pixels(bodyPixels, px, body, ox, oy)

    // Belly
    pixels(listOf(6 to 9, 7 to 9, 8 to 9, 9 to 9, 7 to 10, 8 to 10), px, white, ox, oy)

    // Feet
    pixels(listOf(4 to 12, 5 to 12, 10 to 12, 11 to 12), px, dark, ox, oy)

    // Tail
    pixels(listOf(12 to 9, 13 to 8, 14 to 7, 14 to 6), px, dark, ox, oy)
}

// ── Dog ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawDog(px: Float, ox: Float, oy: Float, pet: PetState, anim: PetAnimation, blink: Boolean, eyeDroop: Float, earWiggle: Float) {
    val body = Color(0xFF8D6E63)       // Brown
    val dark = Color(0xFF5D4037)
    val white = Color.White
    val pink = Color(0xFFFF80AB)
    val black = Color(0xFF1A1A1A)

    // Floppy ears (with wiggle)
    val earOff = earWiggle * 2f
    pixels(listOf(2 to 4, 2 to 5, 2 to 6, 3 to 4, 3 to 6), px, dark, ox - earOff, oy)   // Left ear
    pixels(listOf(12 to 4, 13 to 4, 13 to 5, 13 to 6, 12 to 6), px, dark, ox + earOff, oy) // Right ear

    // Head
    val headPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) headPixels.add(x to 3)
    for (x in 3..12) { headPixels.add(x to 4); headPixels.add(x to 5); headPixels.add(x to 6) }
    for (x in 4..11) headPixels.add(x to 7)
    pixels(headPixels, px, body, ox, oy)

    // Muzzle
    pixels(listOf(6 to 6, 7 to 6, 8 to 6, 9 to 6, 6 to 7, 7 to 7, 8 to 7, 9 to 7), px, white, ox, oy)

    // Eyes
    if (blink || eyeDroop > 0.8f) {
        pixel(5, 5, px, black, ox, oy); pixel(10, 5, px, black, ox, oy)
    } else if (anim == PetAnimation.HAPPY_BOUNCE) {
        // Happy squint eyes
        pixel(5, 5, px, black, ox, oy); pixel(10, 5, px, black, ox, oy)
    } else {
        pixel(5, 5, px, black, ox, oy); pixel(6, 5, px, black, ox, oy)
        pixel(10, 5, px, black, ox, oy); pixel(9, 5, px, black, ox, oy)
        pixel(5, 5, px, white, ox, oy); pixel(10, 5, px, white, ox, oy)
    }

    // Nose
    pixel(7, 6, px, black, ox, oy); pixel(8, 6, px, black, ox, oy)

    // Tongue (when happy)
    if (anim == PetAnimation.HAPPY_BOUNCE || anim == PetAnimation.DANCING) {
        pixel(7, 8, px, pink, ox, oy); pixel(8, 8, px, pink, ox, oy)
    }

    // Body
    val bodyPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) { bodyPixels.add(x to 8); bodyPixels.add(x to 9); bodyPixels.add(x to 10) }
    for (x in 5..10) bodyPixels.add(x to 11)
    pixels(bodyPixels, px, body, ox, oy)

    // Belly
    pixels(listOf(6 to 9, 7 to 9, 8 to 9, 9 to 9, 7 to 10, 8 to 10), px, white, ox, oy)

    // Feet
    pixels(listOf(4 to 12, 5 to 12, 10 to 12, 11 to 12), px, dark, ox, oy)

    // Tail (wagging when happy)
    val tailOff = if (anim == PetAnimation.HAPPY_BOUNCE || anim == PetAnimation.DANCING) earWiggle * 3f else 0f
    pixels(listOf(12 to 8, 13 to 7, 14 to 6), px, dark, ox + tailOff, oy)
}

// ── Bear ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawBear(px: Float, ox: Float, oy: Float, pet: PetState, anim: PetAnimation, blink: Boolean, eyeDroop: Float, earWiggle: Float) {
    val body = Color(0xFF795548)
    val dark = Color(0xFF4E342E)
    val light = Color(0xFFA1887F)
    val pink = Color(0xFFFF80AB)
    val black = Color(0xFF1A1A1A)
    val white = Color.White

    // Round ears
    pixels(listOf(3 to 2, 4 to 2, 3 to 3, 4 to 3), px, dark, ox, oy)   // Left
    pixels(listOf(11 to 2, 12 to 2, 11 to 3, 12 to 3), px, dark, ox, oy) // Right
    pixel(4, 3, px, pink, ox, oy); pixel(11, 3, px, pink, ox, oy) // Inner

    // Head (rounder)
    val headPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) headPixels.add(x to 3)
    for (x in 3..12) { headPixels.add(x to 4); headPixels.add(x to 5); headPixels.add(x to 6) }
    for (x in 4..11) headPixels.add(x to 7)
    pixels(headPixels, px, body, ox, oy)

    // Face patch
    pixels(listOf(5 to 5, 6 to 5, 9 to 5, 10 to 5, 6 to 6, 7 to 6, 8 to 6, 9 to 6), px, light, ox, oy)

    // Eyes
    if (blink || eyeDroop > 0.8f) {
        pixel(6, 5, px, black, ox, oy); pixel(9, 5, px, black, ox, oy)
    } else {
        pixel(6, 5, px, black, ox, oy); pixel(9, 5, px, black, ox, oy)
        if (anim != PetAnimation.SAD_DROOP) {
            pixel(6, 5, px, white, ox, oy) // Keep these as the shine dots
        }
    }

    // Nose
    pixel(7, 6, px, black, ox, oy); pixel(8, 6, px, black, ox, oy)

    // Blush
    pixel(4, 6, px, pink, ox, oy); pixel(11, 6, px, pink, ox, oy)

    // Body (chunkier)
    val bodyPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 3..12) { bodyPixels.add(x to 8); bodyPixels.add(x to 9); bodyPixels.add(x to 10) }
    for (x in 4..11) bodyPixels.add(x to 11)
    pixels(bodyPixels, px, body, ox, oy)

    // Belly
    pixels(listOf(6 to 9, 7 to 9, 8 to 9, 9 to 9, 6 to 10, 7 to 10, 8 to 10, 9 to 10), px, light, ox, oy)

    // Feet
    pixels(listOf(4 to 12, 5 to 12, 6 to 12, 9 to 12, 10 to 12, 11 to 12), px, dark, ox, oy)
}

// ── Bunny ────────────────────────────────────────────────────────────────

private fun DrawScope.drawBunny(px: Float, ox: Float, oy: Float, pet: PetState, anim: PetAnimation, blink: Boolean, eyeDroop: Float, earWiggle: Float) {
    val body = Color(0xFFE0E0E0)      // Light grey
    val dark = Color(0xFF9E9E9E)
    val pink = Color(0xFFFF80AB)
    val black = Color(0xFF1A1A1A)
    val white = Color.White

    // Long ears (with wiggle)
    val earOff = earWiggle * 1.5f
    pixels(listOf(5 to 0, 5 to 1, 5 to 2, 6 to 0, 6 to 1, 6 to 2), px, body, ox - earOff, oy)
    pixel(6, 1, px, pink, ox - earOff, oy) // Inner left
    pixels(listOf(9 to 0, 9 to 1, 9 to 2, 10 to 0, 10 to 1, 10 to 2), px, body, ox + earOff, oy)
    pixel(9, 1, px, pink, ox + earOff, oy) // Inner right

    // Head
    val headPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) headPixels.add(x to 3)
    for (x in 3..12) { headPixels.add(x to 4); headPixels.add(x to 5); headPixels.add(x to 6) }
    for (x in 4..11) headPixels.add(x to 7)
    pixels(headPixels, px, body, ox, oy)

    // Eyes (big and round for bunny)
    if (blink || eyeDroop > 0.8f) {
        pixel(5, 5, px, black, ox, oy); pixel(10, 5, px, black, ox, oy)
    } else {
        pixel(5, 5, px, black, ox, oy); pixel(6, 5, px, black, ox, oy)
        pixel(10, 5, px, black, ox, oy); pixel(9, 5, px, black, ox, oy)
        pixel(5, 5, px, Color(0xFFE91E63), ox, oy) // Red eye shine
        pixel(10, 5, px, Color(0xFFE91E63), ox, oy)
    }

    // Nose & mouth (Y shape)
    pixel(7, 6, px, pink, ox, oy); pixel(8, 6, px, pink, ox, oy)
    pixel(7, 7, px, dark, ox, oy); pixel(8, 7, px, dark, ox, oy)

    // Cheeks
    pixel(4, 6, px, pink, ox, oy); pixel(11, 6, px, pink, ox, oy)

    // Body (small and round)
    val bodyPixels = mutableListOf<Pair<Int, Int>>()
    for (x in 4..11) { bodyPixels.add(x to 8); bodyPixels.add(x to 9); bodyPixels.add(x to 10) }
    for (x in 5..10) bodyPixels.add(x to 11)
    pixels(bodyPixels, px, body, ox, oy)

    // Belly
    pixels(listOf(6 to 9, 7 to 9, 8 to 9, 9 to 9, 7 to 10, 8 to 10), px, white, ox, oy)

    // Feet
    pixels(listOf(4 to 12, 5 to 12, 6 to 12, 9 to 12, 10 to 12, 11 to 12), px, dark, ox, oy)

    // Puffy tail
    pixels(listOf(12 to 10, 13 to 10, 12 to 11, 13 to 11), px, white, ox, oy)
}

// ── Accessories ──────────────────────────────────────────────────────────

private fun DrawScope.drawAccessories(px: Float, ox: Float, oy: Float, pet: PetState) {
    // Hat
    pet.equippedHat?.let { hatId ->
        when (hatId) {
            "hat_music" -> {
                // Headphones
                pixels(listOf(2 to 2, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 7 to 1, 8 to 1, 9 to 1, 10 to 1, 11 to 1, 12 to 1, 13 to 2), px, Color(0xFF424242), ox, oy)
                pixels(listOf(2 to 3, 2 to 4, 3 to 3, 3 to 4), px, Color(0xFF616161), ox, oy)
                pixels(listOf(12 to 3, 12 to 4, 13 to 3, 13 to 4), px, Color(0xFF616161), ox, oy)
            }
            "hat_crown" -> {
                pixels(listOf(5 to 1, 7 to 0, 8 to 0, 10 to 1), px, Color(0xFFFFD700), ox, oy)
                for (x in 5..10) pixel(x, 2, px, Color(0xFFFFD700), ox, oy)
                pixel(6, 1, px, Color(0xFFFFD700), ox, oy); pixel(9, 1, px, Color(0xFFFFD700), ox, oy)
                pixel(7, 1, px, Color(0xFFE91E63), ox, oy) // Gem
            }
            "hat_party" -> {
                pixels(listOf(7 to 0, 7 to 1, 6 to 1, 8 to 1, 6 to 2, 7 to 2, 8 to 2, 9 to 2), px, Color(0xFFE91E63), ox, oy)
                pixel(7, 0, px, Color(0xFFFFEB3B), ox, oy) // Star on top
            }
            "hat_beanie" -> {
                for (x in 4..11) { pixel(x, 1, px, Color(0xFF1565C0), ox, oy); pixel(x, 2, px, Color(0xFF1565C0), ox, oy) }
                pixel(7, 0, px, Color(0xFFBBDEFB), ox, oy); pixel(8, 0, px, Color(0xFFBBDEFB), ox, oy) // Pom
            }
            "hat_wizard" -> {
                pixel(7, -2, px, Color(0xFF311B92), ox, oy)
                pixels(listOf(6 to -1, 7 to -1, 8 to -1), px, Color(0xFF311B92), ox, oy)
                for (x in 5..10) pixel(x, 0, px, Color(0xFF4527A0), ox, oy)
                for (x in 4..11) pixel(x, 1, px, Color(0xFF4527A0), ox, oy)
                pixel(7, -1, px, Color(0xFFFFD700), ox, oy) // Star
            }
            "hat_flower" -> {
                pixel(5, 1, px, Color(0xFFFF80AB), ox, oy)
                pixel(6, 1, px, Color(0xFFFFEB3B), ox, oy)
                pixel(7, 1, px, Color(0xFFFF80AB), ox, oy)
                pixel(8, 2, px, Color(0xFF81C784), ox, oy)
                pixel(9, 1, px, Color(0xFFCE93D8), ox, oy)
                pixel(10, 1, px, Color(0xFFFFEB3B), ox, oy)
                pixel(11, 1, px, Color(0xFFFF80AB), ox, oy)
            }
        }
    }

    // Glasses
    pet.equippedGlasses?.let { glassId ->
        when (glassId) {
            "glass_cool" -> {
                for (x in 4..6) pixel(x, 5, px, Color(0xFF212121), ox, oy)
                for (x in 9..11) pixel(x, 5, px, Color(0xFF212121), ox, oy)
                pixel(7, 5, px, Color(0xFF212121), ox, oy); pixel(8, 5, px, Color(0xFF212121), ox, oy) // Bridge
            }
            "glass_nerd" -> {
                for (x in 4..6) { pixel(x, 4, px, Color(0xFF424242), ox, oy); pixel(x, 6, px, Color(0xFF424242), ox, oy) }
                pixel(4, 5, px, Color(0xFF424242), ox, oy); pixel(6, 5, px, Color(0xFF424242), ox, oy)
                for (x in 9..11) { pixel(x, 4, px, Color(0xFF424242), ox, oy); pixel(x, 6, px, Color(0xFF424242), ox, oy) }
                pixel(9, 5, px, Color(0xFF424242), ox, oy); pixel(11, 5, px, Color(0xFF424242), ox, oy)
                pixel(7, 5, px, Color(0xFF424242), ox, oy); pixel(8, 5, px, Color(0xFF424242), ox, oy)
            }
            "glass_star" -> {
                pixel(5, 5, px, Color(0xFFFFD700), ox, oy); pixel(10, 5, px, Color(0xFFFFD700), ox, oy)
                pixel(7, 5, px, Color(0xFFFFD700), ox, oy); pixel(8, 5, px, Color(0xFFFFD700), ox, oy)
            }
            "glass_heart" -> {
                pixel(5, 5, px, Color(0xFFE91E63), ox, oy); pixel(6, 5, px, Color(0xFFE91E63), ox, oy)
                pixel(9, 5, px, Color(0xFFE91E63), ox, oy); pixel(10, 5, px, Color(0xFFE91E63), ox, oy)
                pixel(7, 5, px, Color(0xFFE91E63), ox, oy); pixel(8, 5, px, Color(0xFFE91E63), ox, oy)
            }
        }
    }

    // Necklace
    pet.equippedNecklace?.let { neckId ->
        val neckColor = when (neckId) {
            "neck_note" -> Color(0xFFFFD700)
            "neck_heart" -> Color(0xFFE91E63)
            "neck_star" -> Color(0xFFFFEB3B)
            "neck_moon" -> Color(0xFFC5CAE9)
            else -> Color(0xFFFFD700)
        }
        pixels(listOf(6 to 8, 7 to 8, 8 to 8, 9 to 8), px, neckColor, ox, oy)
        pixel(7, 9, px, neckColor, ox, oy) // Pendant
    }
}