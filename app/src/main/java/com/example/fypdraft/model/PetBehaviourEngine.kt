package com.example.fypdraft.model

import java.util.Calendar

/**
 * Pet Behavior Engine — the brain that makes the pet feel alive.
 * Uses priority-based state machine driven by multiple context signals.
 */

// ── Behavior states ──────────────────────────────────────────────────────

enum class PetBehavior(val label: String) {
    IDLE("Just chillin'"),
    BREATHING("..."),

    // Idle / boredom cycle
    LOOKING_AROUND("Hmm..."),
    YAWNING("*yaaawn*"),
    SITTING_DOWN("Taking a seat~"),
    FALLING_ASLEEP("Zzz..."),
    SLEEPING("💤"),

    // Music reactions
    LISTENING("♪ ♪ ♪"),
    HEAD_BOBBING("🎵 Bop bop!"),
    DANCING("Let's dance! 💃"),
    SINGING_ALONG("La la la~ 🎤"),

    // User interactions
    HAPPY_PET("That feels nice! 💖"),
    ANNOYED_POKE("Hey! 😤"),
    PICKED_UP("Wheee~!"),
    LANDED("*bounce*"),
    CELEBRATING("WOOO! 🎉🎊"),

    // Emotional mirroring
    WORRIED("You okay? 😟"),
    DETERMINED("We got this! 💪"),
    CHEERING("You're amazing! ⭐"),
    COMFORTING("I'm here for you 💙"),

    // Environmental
    SLEEPY_NIGHT("It's late... 🌙"),
    MORNING_STRETCH("Good morning! ☀️"),
    WAVING("Welcome back! 👋"),
    CHASING_FLY("Get back here! 🪰"),

    // Needs-based
    HUNGRY("Feed me music! 🎵"),
    LONELY("Play something? 🥺"),
    DIRTY("I need a bath... 🛁"),
    LOW_ENERGY("So tired... ⚡"),

    // Snack reactions
    EATING_SNACK("Nom nom nom! 🍪"),
    SUGAR_RUSH("ZOOOM! ⚡✨"),
    SPARKLE_CLEAN("So sparkly! ✨"),

    // Craving
    CRAVING("I'm craving something... 🤔"),

    // Bonding tier reactions
    GIFTING("I found this for you! 🎁"),
    SPECIAL_DANCE("Our song! 🎵💫"),

    EXCITED_NEW_SONG("Ooh new song! ✨"),
}

/**
 * Context signals for behavior decisions
 */
data class PetContext(
    val mood: String = "neutral",
    val isPlaying: Boolean = false,
    val currentScreen: String = "home",
    val secondsSinceLastInteraction: Long = 0,
    val secondsSinceLastSong: Long = 0,
    val tapCount: Int = 0,
    val isDragging: Boolean = false,
    val wasJustDropped: Boolean = false,
    val isFirstOpenToday: Boolean = false,
    val playerError: Boolean = false,
    val justCompletedSong: Boolean = false,
    val justLeveledUp: Boolean = false,
    val justFedSnack: Boolean = false,
    val justGroomed: Boolean = false,
    val petHappiness: Int = 80,
    val petEnergy: Float = 1f,
    val petCleanliness: Float = 1f,
    val hasActiveEffect: Boolean = false,
    val hasCraving: Boolean = false,
    val cravingSatisfied: Boolean = false,
    val bondingTier: BondingTier = BondingTier.STRANGER
)

object PetBehaviorEngine {

    fun determineBehavior(context: PetContext): PetBehavior {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // ── Priority 1: Direct interaction ───────────────────────────
        if (context.wasJustDropped) return PetBehavior.LANDED
        if (context.isDragging) return PetBehavior.PICKED_UP
        if (context.justGroomed) return PetBehavior.SPARKLE_CLEAN
        if (context.justFedSnack && context.hasActiveEffect) return PetBehavior.SUGAR_RUSH
        if (context.justFedSnack) return PetBehavior.EATING_SNACK
        if (context.tapCount >= 5) return PetBehavior.ANNOYED_POKE
        if (context.tapCount >= 2) return PetBehavior.HAPPY_PET
        if (context.justLeveledUp) return PetBehavior.CELEBRATING

        // ── Priority 2: Welcome back ─────────────────────────────────
        if (context.isFirstOpenToday) return PetBehavior.WAVING

        // ── Priority 3: Music reactions ──────────────────────────────
        if (context.isPlaying) {
            // Bonding tier special reaction
            if (context.bondingTier == BondingTier.SOULMATE) return PetBehavior.SPECIAL_DANCE

            return when {
                context.hasActiveEffect -> PetBehavior.SUGAR_RUSH // Snack effect + music
                context.mood == "energetic" -> PetBehavior.DANCING
                context.mood == "happy" -> PetBehavior.HEAD_BOBBING
                context.mood == "sad" -> PetBehavior.COMFORTING
                context.mood == "romantic" -> PetBehavior.SINGING_ALONG
                context.mood == "calm" -> PetBehavior.LISTENING
                else -> PetBehavior.LISTENING
            }
        }

        if (context.justCompletedSong) return PetBehavior.CHEERING

        // ── Priority 4: Needs alerts ─────────────────────────────────
        if (context.petEnergy < 0.15f) return PetBehavior.LOW_ENERGY
        if (context.petCleanliness < 0.2f) return PetBehavior.DIRTY
        if (context.petHappiness < 30) return PetBehavior.LONELY

        // ── Priority 5: Craving ──────────────────────────────────────
        if (context.hasCraving && !context.cravingSatisfied && context.secondsSinceLastInteraction < 5) {
            return PetBehavior.CRAVING
        }

        // ── Priority 6: Emotional mirroring ──────────────────────────
        if (context.playerError) return PetBehavior.WORRIED
        if (context.mood == "sad" && !context.isPlaying) return PetBehavior.COMFORTING
        if (context.mood == "focused") return PetBehavior.DETERMINED

        // ── Priority 7: Environmental ────────────────────────────────
        if (hour in 23..23 || hour in 0..5) return PetBehavior.SLEEPY_NIGHT
        if (hour in 6..8 && context.secondsSinceLastInteraction < 10) return PetBehavior.MORNING_STRETCH

        // ── Priority 8: Status indicators ────────────────────────────
        if (context.secondsSinceLastSong > 300 && context.petHappiness < 50) return PetBehavior.HUNGRY

        // ── Priority 9: Random events (5% chance per tick when idle > 15s) ──
        if (context.secondsSinceLastInteraction > 15 && Math.random() < 0.003) {
            return PetBehavior.CHASING_FLY
        }

        // ── Priority 10: Boredom cycle ───────────────────────────────
        val idle = context.secondsSinceLastInteraction
        return when {
            idle > 120 -> PetBehavior.SLEEPING
            idle > 90 -> PetBehavior.FALLING_ASLEEP
            idle > 60 -> PetBehavior.SITTING_DOWN
            idle > 40 -> PetBehavior.YAWNING
            idle > 20 -> PetBehavior.LOOKING_AROUND
            idle > 10 -> PetBehavior.BREATHING
            else -> PetBehavior.IDLE
        }
    }

    fun behaviorToAnimation(behavior: PetBehavior): PetAnimation = when (behavior) {
        PetBehavior.IDLE, PetBehavior.BREATHING, PetBehavior.LOOKING_AROUND -> PetAnimation.IDLE
        PetBehavior.YAWNING, PetBehavior.SITTING_DOWN, PetBehavior.FALLING_ASLEEP,
        PetBehavior.SLEEPING, PetBehavior.SLEEPY_NIGHT -> PetAnimation.SLEEPY_NOD
        PetBehavior.LISTENING, PetBehavior.HEAD_BOBBING -> PetAnimation.LISTENING
        PetBehavior.DANCING, PetBehavior.SINGING_ALONG, PetBehavior.SPECIAL_DANCE -> PetAnimation.DANCING
        PetBehavior.CELEBRATING, PetBehavior.CHASING_FLY, PetBehavior.SUGAR_RUSH -> PetAnimation.ENERGETIC_JUMP
        PetBehavior.HAPPY_PET, PetBehavior.SPARKLE_CLEAN -> PetAnimation.LOVE_EYES
        PetBehavior.ANNOYED_POKE, PetBehavior.DETERMINED -> PetAnimation.FOCUSED_STARE
        PetBehavior.PICKED_UP, PetBehavior.LANDED, PetBehavior.WAVING,
        PetBehavior.MORNING_STRETCH, PetBehavior.CHEERING, PetBehavior.EXCITED_NEW_SONG -> PetAnimation.HAPPY_BOUNCE
        PetBehavior.WORRIED, PetBehavior.COMFORTING, PetBehavior.HUNGRY,
        PetBehavior.LONELY, PetBehavior.DIRTY, PetBehavior.LOW_ENERGY -> PetAnimation.SAD_DROOP
        PetBehavior.EATING_SNACK, PetBehavior.GIFTING -> PetAnimation.EATING
        PetBehavior.CRAVING -> PetAnimation.IDLE
    }

    fun getBubbleText(behavior: PetBehavior, petName: String): String? = when (behavior) {
        PetBehavior.SLEEPING -> "💤"
        PetBehavior.WAVING -> "Hey! I missed you! 👋"
        PetBehavior.MORNING_STRETCH -> "Good morning! ☀️"
        PetBehavior.SLEEPY_NIGHT -> "Getting sleepy... 🌙"
        PetBehavior.HUNGRY -> "Play me a song? 🎵"
        PetBehavior.LONELY -> "I'm bored... 🥺"
        PetBehavior.DIRTY -> "I need a bath! 🛁"
        PetBehavior.LOW_ENERGY -> "So tired... feed me? ⚡"
        PetBehavior.CELEBRATING -> "LEVEL UP! 🎉🎊"
        PetBehavior.COMFORTING -> "I'm here for you 💙"
        PetBehavior.WORRIED -> "Everything okay? 😟"
        PetBehavior.CHEERING -> "Great taste! ⭐"
        PetBehavior.ANNOYED_POKE -> "Hey, easy! 😤"
        PetBehavior.HAPPY_PET -> "That's nice~ 💖"
        PetBehavior.DANCING -> "♪ ♪ ♪"
        PetBehavior.SINGING_ALONG -> "La la la~ 🎤"
        PetBehavior.SUGAR_RUSH -> "ZOOOOOM! ⚡✨"
        PetBehavior.EATING_SNACK -> "Nom nom! 🍪"
        PetBehavior.SPARKLE_CLEAN -> "So fresh! ✨"
        PetBehavior.CRAVING -> null // Craving message comes from the genre
        PetBehavior.SPECIAL_DANCE -> "Our special song! 💫"
        PetBehavior.CHASING_FLY -> "Come back here! 🪰"
        PetBehavior.GIFTING -> "I found something! 🎁"
        else -> null
    }
}