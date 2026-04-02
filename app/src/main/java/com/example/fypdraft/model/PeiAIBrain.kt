package com.example.fypdraft.model

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

/**
 * Pet AI behavior states.
 */
enum class PetAIState {
    IDLE, WANDERING, LINGERING, DOZY, CURIOUS, DISTRACTED, GROOVY, EXCITED, DISAPPOINTED
}

/**
 * Thought bubble types the pet can show.
 */
enum class PetThought(val emoji: String, val duration: Long) {
    MUSIC_NOTE("🎵", 3000),
    SLEEPY("💤", 4000),
    HEART("❤️", 2500),
    QUESTION("❓", 3000),
    STAR("⭐", 2000),
    HUNGRY("🍕", 3500),
    BORED("💭", 3000),
    EXCITED_THOUGHT("✨", 2000),
    NONE("", 0),
    CUSTOM("", 3000)
}

/**
 * Pet AI Brain — state machine for pet behavior.
 *
 * No dependencies on PetPersonalityEngine types.
 * Personality is applied via simple primitives through applyPersonalityParams().
 */
class PetAIBrain {

    var currentState: PetAIState = PetAIState.IDLE
        private set
    var currentThought: PetThought = PetThought.NONE
        private set
    var customThoughtEmoji: String = ""
        private set
    var wanderTarget: Offset = Offset.Zero
        private set
    var lingerTimeMs: Long = 0
        private set

    private var idleTimerMs: Long = 0
    private var lastMusicState: Boolean = false
    private var stateEntryTimeMs: Long = System.currentTimeMillis()

    // Personality-driven parameters (defaults work without personality engine)
    private var wanderIntervalMs: Long = 7_000
    private var dozyThresholdMs: Long = 10_000
    private var personalityThoughts: List<String> = listOf("🎵", "⭐", "💭")

    /**
     * Apply personality parameters using simple primitives.
     * Call this when personality profile loads.
     *
     * @param wanderFrequencyMs   How often the pet wanders (ms)
     * @param idleSpeedMultiplier Multiplier for idle-to-dozy threshold
     * @param thoughtEmojis       List of emoji strings for thought bubbles
     */
    fun applyPersonalityParams(
        wanderFrequencyMs: Long = 7_000,
        idleSpeedMultiplier: Float = 1.0f,
        thoughtEmojis: List<String> = listOf("🎵", "⭐", "💭")
    ) {
        wanderIntervalMs = wanderFrequencyMs
        dozyThresholdMs = (10_000 / idleSpeedMultiplier).toLong()
        personalityThoughts = thoughtEmojis
    }

    /**
     * Update the brain state. Call every ~500ms.
     */
    fun update(
        isMusicPlaying: Boolean,
        isUserScrolling: Boolean,
        isUserTapping: Boolean,
        boundsWidth: Float,
        boundsHeight: Float,
        deltaMs: Long = 500
    ): PetAIState {
        val now = System.currentTimeMillis()
        val timeInState = now - stateEntryTimeMs

        // Music started → GROOVY
        if (isMusicPlaying && !lastMusicState) {
            transition(PetAIState.GROOVY)
            val musicThoughts = personalityThoughts.filter { it in listOf("🎵", "🎶", "🎧", "💃", "🕺", "🔥") }
            setCustomThought(musicThoughts.randomOrNull() ?: "🎵")
            lastMusicState = true
            idleTimerMs = 0
            return currentState
        }

        // Music stopped → DISAPPOINTED
        if (!isMusicPlaying && lastMusicState) {
            transition(PetAIState.DISAPPOINTED)
            currentThought = PetThought.QUESTION
            lastMusicState = false
            return currentState
        }
        lastMusicState = isMusicPlaying

        // User tap → EXCITED
        if (isUserTapping && currentState != PetAIState.EXCITED) {
            transition(PetAIState.EXCITED)
            currentThought = PetThought.HEART
            return currentState
        }

        // User scrolling → CURIOUS
        if (isUserScrolling && currentState != PetAIState.CURIOUS && currentState != PetAIState.GROOVY) {
            transition(PetAIState.CURIOUS)
            currentThought = PetThought.STAR
            return currentState
        }

        // State-specific logic
        when (currentState) {
            PetAIState.IDLE -> {
                idleTimerMs += deltaMs
                if (idleTimerMs > dozyThresholdMs) {
                    if (Random.nextFloat() < 0.3f) {
                        transition(PetAIState.DOZY)
                        currentThought = PetThought.SLEEPY
                    } else {
                        startWander(boundsWidth, boundsHeight)
                    }
                    idleTimerMs = 0
                } else if (idleTimerMs > (wanderIntervalMs * 0.7f).toLong() && Random.nextFloat() < 0.12f) {
                    transition(PetAIState.DISTRACTED)
                    setRandomPersonalityThought()
                    wanderTarget = pickRandomEdgePoint(boundsWidth, boundsHeight)
                    idleTimerMs = 0
                }
            }
            PetAIState.WANDERING -> {
                if (timeInState > 2000) {
                    transition(PetAIState.LINGERING)
                    lingerTimeMs = Random.nextLong(3000, 8000)
                    if (Random.nextFloat() < 0.4f) setRandomPersonalityThought()
                }
            }
            PetAIState.LINGERING -> {
                if (timeInState > lingerTimeMs) {
                    if (Random.nextFloat() < 0.5f) {
                        startWander(boundsWidth, boundsHeight)
                    } else {
                        transition(PetAIState.IDLE)
                        if (Random.nextFloat() < 0.3f) setRandomPersonalityThought()
                    }
                }
            }
            PetAIState.DOZY -> {
                if (timeInState > 15_000) {
                    transition(PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
            }
            PetAIState.CURIOUS -> {
                if (!isUserScrolling && timeInState > 2000) {
                    transition(PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
            }
            PetAIState.DISTRACTED -> {
                if (timeInState > Random.nextLong(3000, 5000)) {
                    transition(PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
            }
            PetAIState.GROOVY -> {
                if (!isMusicPlaying && timeInState > 1000) {
                    transition(PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
                if (timeInState > 5000 && currentThought == PetThought.NONE && Random.nextFloat() < 0.1f) {
                    setCustomThought(personalityThoughts.randomOrNull() ?: "🎵")
                }
            }
            PetAIState.EXCITED -> {
                if (timeInState > 2000) {
                    transition(if (isMusicPlaying) PetAIState.GROOVY else PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
            }
            PetAIState.DISAPPOINTED -> {
                if (timeInState > 3000) {
                    transition(PetAIState.IDLE)
                    currentThought = PetThought.NONE
                }
            }
        }

        // Clear thoughts after duration
        if (currentThought != PetThought.NONE && currentThought != PetThought.CUSTOM && timeInState > currentThought.duration) {
            currentThought = PetThought.NONE
        }
        if (currentThought == PetThought.CUSTOM && timeInState > 3000) {
            currentThought = PetThought.NONE
        }

        return currentState
    }

    private fun setCustomThought(emoji: String) {
        customThoughtEmoji = emoji
        currentThought = PetThought.CUSTOM
    }

    private fun setRandomPersonalityThought() {
        val emoji = personalityThoughts.randomOrNull() ?: "💭"
        setCustomThought(emoji)
    }

    private fun transition(s: PetAIState) {
        currentState = s
        stateEntryTimeMs = System.currentTimeMillis()
    }

    private fun startWander(w: Float, h: Float) {
        transition(PetAIState.WANDERING)
        wanderTarget = pickWanderPoint(w, h)
        currentThought = PetThought.NONE
    }

    private fun pickWanderPoint(w: Float, h: Float): Offset {
        val m = 0.15f
        return Offset(
            w * (m + Random.nextFloat() * (1f - 2 * m)),
            h * (m + Random.nextFloat() * (1f - 2 * m))
        )
    }

    private fun pickRandomEdgePoint(w: Float, h: Float): Offset = when (Random.nextInt(4)) {
        0 -> Offset(w * 0.1f, h * Random.nextFloat())
        1 -> Offset(w * 0.9f, h * Random.nextFloat())
        2 -> Offset(w * Random.nextFloat(), h * 0.1f)
        else -> Offset(w * Random.nextFloat(), h * 0.9f)
    }

    fun forceState(s: PetAIState) {
        transition(s)
    }
}