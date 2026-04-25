package com.example.fypdraft.view

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  FlappyMiniGame.kt  —  Pure game logic, zero Compose imports.
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
//  FLAPPY BIRD ENGINE  (kept for reference, not shown in UI any more)
// ══════════════════════════════════════════════════════════════════════════════

object FlappyConstants {
    const val PET_X          = 0.22f
    const val PET_HALF_SIZE  = 0.055f
    const val GRAVITY        = 0.010f
    const val JUMP_VELOCITY  = -0.060f
    const val MAX_FALL_SPEED = 0.035f
    const val OBSTACLE_WIDTH = 0.12f
    const val GAP_SIZE       = 0.34f
    const val OBSTACLE_SPEED = 0.0055f
    const val SPAWN_GAP_X    = 0.58f
    const val TICK_MS        = 16L
}

data class FlappyObstacle(val x: Float, val gapTop: Float, val passed: Boolean = false)
enum class FlappyPhase { IDLE, PLAYING, DEAD }
data class FlappyGameState(
    val phase     : FlappyPhase          = FlappyPhase.IDLE,
    val petY      : Float                = 0.42f,
    val velocityY : Float                = 0f,
    val obstacles : List<FlappyObstacle> = emptyList(),
    val score     : Int                  = 0,
    val bestScore : Int                  = 0
)

object FlappyGameEngine {
    fun tap(state: FlappyGameState): FlappyGameState = when (state.phase) {
        FlappyPhase.DEAD -> state
        else -> state.copy(
            phase     = FlappyPhase.PLAYING,
            velocityY = FlappyConstants.JUMP_VELOCITY,
            obstacles = if (state.obstacles.isEmpty()) listOf(spawnObstacle()) else state.obstacles
        )
    }
    fun tick(state: FlappyGameState): FlappyGameState {
        if (state.phase != FlappyPhase.PLAYING) return state
        val newVel = (state.velocityY + FlappyConstants.GRAVITY).coerceAtMost(FlappyConstants.MAX_FALL_SPEED)
        val newY   = state.petY + newVel
        if (newY >= 0.94f || newY <= 0.02f) return state.copy(phase = FlappyPhase.DEAD, petY = newY.coerceIn(0.02f, 0.94f), velocityY = 0f, bestScore = maxOf(state.score, state.bestScore))
        var newScore = state.score
        val moved    = state.obstacles.map { obs ->
            val nx = obs.x - FlappyConstants.OBSTACLE_SPEED
            val pass = !obs.passed && (nx + FlappyConstants.OBSTACLE_WIDTH) < FlappyConstants.PET_X
            if (pass) newScore++
            obs.copy(x = nx, passed = obs.passed || pass)
        }
        val alive   = moved.filter { it.x + FlappyConstants.OBSTACLE_WIDTH > -0.02f }
        val withNew = if ((alive.maxOfOrNull { it.x } ?: -1f) < 1f - FlappyConstants.SPAWN_GAP_X) alive + spawnObstacle() else alive
        val pl = FlappyConstants.PET_X - FlappyConstants.PET_HALF_SIZE; val pr = FlappyConstants.PET_X + FlappyConstants.PET_HALF_SIZE
        val pt = newY - FlappyConstants.PET_HALF_SIZE; val pb = newY + FlappyConstants.PET_HALF_SIZE
        val hit = withNew.any { obs -> val overX = pr > obs.x && pl < obs.x + FlappyConstants.OBSTACLE_WIDTH; overX && (pt < obs.gapTop || pb > obs.gapTop + FlappyConstants.GAP_SIZE) }
        return if (hit) state.copy(phase = FlappyPhase.DEAD, petY = newY, velocityY = 0f, obstacles = withNew, score = newScore, bestScore = maxOf(newScore, state.bestScore))
        else state.copy(petY = newY, velocityY = newVel, obstacles = withNew, score = newScore)
    }
    fun restart(bestScore: Int) = FlappyGameState(bestScore = bestScore)
    private fun spawnObstacle() = FlappyObstacle(x = 1.05f, gapTop = 0.10f + (Math.random().toFloat() * 0.46f))
}

suspend fun runFlappyGameLoop(getState: () -> FlappyGameState, onStateUpdate: (FlappyGameState) -> Unit) {
    while (getState().phase == FlappyPhase.PLAYING) { delay(FlappyConstants.TICK_MS); onStateUpdate(FlappyGameEngine.tick(getState())) }
}

// ══════════════════════════════════════════════════════════════════════════════
//  DINO RUNNER ENGINE
//
//  Coordinate system (all normalised 0..1):
//    Y=0 = top of canvas, Y=1 = bottom
//
//  Avatar occupies a rectangle:
//    left  = DINO_X
//    right = DINO_X + DINO_W
//    top   = dinoY               (dinoY is the TOP of the avatar sprite)
//    bot   = dinoY + DINO_H      (this is what lands on the ground)
//
//  Ground contact: dinoY + DINO_H >= GROUND_Y  → avatar is on ground
//
//  COLLISION FIX:
//    Previous bug: dinoB was set to GROUND_Y (0.78) instead of dinoY + DINO_H.
//    This meant the entire column below the avatar (including air) was part of
//    the hitbox, causing instant collisions even when jumping over obstacles.
//    Fix: use actual avatar bottom = dinoY + DINO_H (minus small forgiveness).
// ══════════════════════════════════════════════════════════════════════════════

object DinoConstants {
    // Avatar geometry (normalised)
    const val DINO_X    = 0.14f   // left edge of avatar column
    const val DINO_W    = 0.08f   // avatar width
    const val DINO_H    = 0.16f   // avatar height (feet to head)
    const val GROUND_Y  = 0.80f   // Y where the ground surface sits

    // Physics — tuned for casual feel
    const val GRAVITY        = 0.014f   // per 16 ms tick
    const val JUMP_VELOCITY  = -0.100f  // strong upward impulse
    const val MAX_FALL_SPEED = 0.045f

    // Obstacles
    const val SPEED_START = 0.0070f
    const val SPEED_CAP   = 0.022f
    const val SPEED_INC   = 0.00025f   // per scored point

    const val CACTUS_W    = 0.055f     // obstacle width (normalised)
    const val CACTUS_MIN_H = 0.14f    // obstacle height as fraction of canvas
    const val CACTUS_MAX_H = 0.24f
    const val SPAWN_GAP_X  = 0.60f    // min gap between obstacles

    // Collision forgiveness (shrinks hitbox inward on each side)
    const val FORGIVE_X = 0.018f
    const val FORGIVE_Y = 0.020f

    const val TICK_MS = 16L
}

data class Cactus(
    val x      : Float,
    val height : Float,   // normalised height (fraction of canvas height)
    val passed : Boolean = false
)

enum class DinoPhase { IDLE, PLAYING, DEAD }

data class DinoGameState(
    val phase      : DinoPhase    = DinoPhase.IDLE,
    // dinoY = Y coordinate of avatar TOP edge
    val dinoY      : Float        = DinoConstants.GROUND_Y - DinoConstants.DINO_H,
    val velocityY  : Float        = 0f,
    val isOnGround : Boolean      = true,
    val cacti      : List<Cactus> = emptyList(),
    val score      : Int          = 0,
    val bestScore  : Int          = 0,
    val legFrame   : Int          = 0
) {
    /** Y coordinate of avatar BOTTOM edge. */
    val avatarBottom get() = dinoY + DinoConstants.DINO_H
}

object DinoGameEngine {

    /** Y of avatar top when standing on the ground. */
    private val groundedDinoY get() = DinoConstants.GROUND_Y - DinoConstants.DINO_H

    fun tap(state: DinoGameState): DinoGameState = when (state.phase) {
        DinoPhase.DEAD -> state
        DinoPhase.IDLE,
        DinoPhase.PLAYING -> {
            if (!state.isOnGround) state   // no double-jump
            else state.copy(
                phase      = DinoPhase.PLAYING,
                velocityY  = DinoConstants.JUMP_VELOCITY,
                isOnGround = false,
                cacti      = if (state.cacti.isEmpty()) listOf(spawnCactus()) else state.cacti
            )
        }
    }

    fun tick(state: DinoGameState): DinoGameState {
        if (state.phase != DinoPhase.PLAYING) return state

        // ── Physics ───────────────────────────────────────────────────────────
        val newVel  = (state.velocityY + DinoConstants.GRAVITY).coerceAtMost(DinoConstants.MAX_FALL_SPEED)
        val rawDinoY = state.dinoY + newVel
        val newDinoY = rawDinoY.coerceAtMost(groundedDinoY)
        val landed   = rawDinoY >= groundedDinoY

        // ── Obstacle movement ─────────────────────────────────────────────────
        val speed = (DinoConstants.SPEED_START + state.score * DinoConstants.SPEED_INC).coerceAtMost(DinoConstants.SPEED_CAP)
        var newScore = state.score
        val moved = state.cacti.map { c ->
            val nx   = c.x - speed
            val pass = !c.passed && (nx + DinoConstants.CACTUS_W) < DinoConstants.DINO_X
            if (pass) newScore++
            c.copy(x = nx, passed = c.passed || pass)
        }
        val alive     = moved.filter { it.x + DinoConstants.CACTUS_W > -0.02f }
        val rightmost = alive.maxOfOrNull { it.x } ?: -1f
        val withNew   = if (rightmost < 1f - DinoConstants.SPAWN_GAP_X) alive + spawnCactus() else alive

        // ── AABB collision — avatar body box vs obstacle box ──────────────────
        // Avatar hitbox (inset by FORGIVE_X/Y for fairness):
        val avatarL = DinoConstants.DINO_X  + DinoConstants.FORGIVE_X
        val avatarR = DinoConstants.DINO_X  + DinoConstants.DINO_W  - DinoConstants.FORGIVE_X
        val avatarT = newDinoY              + DinoConstants.FORGIVE_Y
        val avatarB = newDinoY + DinoConstants.DINO_H - DinoConstants.FORGIVE_Y  // ← KEY FIX: use actual avatar bottom

        val hit = withNew.any { c ->
            // Obstacle occupies a rectangle from its left edge to right,
            // and from (GROUND_Y - c.height) at top to GROUND_Y at bottom.
            val obstL = c.x             + DinoConstants.FORGIVE_X
            val obstR = c.x + DinoConstants.CACTUS_W - DinoConstants.FORGIVE_X
            val obstT = DinoConstants.GROUND_Y - c.height + DinoConstants.FORGIVE_Y
            val obstB = DinoConstants.GROUND_Y

            val overlapX = avatarR > obstL && avatarL < obstR
            val overlapY = avatarB > obstT && avatarT < obstB
            overlapX && overlapY
        }

        val newLeg = if (landed) (state.legFrame + 1) % 24 else state.legFrame

        return if (hit) state.copy(
            phase     = DinoPhase.DEAD,
            dinoY     = newDinoY,
            velocityY = 0f,
            cacti     = withNew,
            score     = newScore,
            bestScore = maxOf(newScore, state.bestScore)
        ) else state.copy(
            dinoY      = newDinoY,
            velocityY  = if (landed) 0f else newVel,
            isOnGround = landed,
            cacti      = withNew,
            score      = newScore,
            legFrame   = newLeg
        )
    }

    fun restart(bestScore: Int) = DinoGameState(bestScore = bestScore)

    private fun spawnCactus() = Cactus(
        x      = 1.05f,
        height = DinoConstants.CACTUS_MIN_H +
                Math.random().toFloat() * (DinoConstants.CACTUS_MAX_H - DinoConstants.CACTUS_MIN_H)
    )
}

suspend fun runDinoGameLoop(getState: () -> DinoGameState, onStateUpdate: (DinoGameState) -> Unit) {
    while (getState().phase == DinoPhase.PLAYING) {
        delay(DinoConstants.TICK_MS)
        onStateUpdate(DinoGameEngine.tick(getState()))
    }
}