package com.example.fypdraft.view

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  FlappyMiniGame.kt  —  Pure game logic, zero Compose imports.
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════════════════
//  FLAPPY BIRD ENGINE
//
//  FIXES APPLIED (v2):
//
//  1. JUMP_VELOCITY  raised -0.060 → -0.110  — bird jumps much higher per tap.
//
//  2. GRAVITY        lowered 0.010 → 0.006   — slower fall so the player has
//     time to react between taps.
//
//  3. MAX_FALL_SPEED raised 0.035 → 0.040    — tiny nudge to keep physics
//     consistent but allows full gravity expression.
//
//  4. GAP_SIZE       raised 0.34 → 0.46      — wider opening through pipes;
//     bird diameter is 0.11 of screen so 0.34 was barely 3× the bird.
//
//  5. OBSTACLE_WIDTH narrowed 0.12 → 0.09    — thinner pipes = easier to
//     thread.
//
//  6. OBSTACLE_SPEED slowed 0.0055 → 0.0045  — more reaction time.
//
//  7. SPAWN_GAP_X    raised 0.58 → 0.72      — more breathing room between
//     consecutive pipes.
//
//  8. FIRST_SPAWN_X  added (1.40f) — very first pipe starts far off-screen so
//     the player gets a grace period before the first obstacle arrives.
//
//  9. tap() on DEAD  now calls restart() instead of returning dead state, so
//     tapping after death restarts the game automatically.
//
// 10. Score-to-BondingPoints conversion added via FlappyRewardCalculator so
//     the caller (ViewModel / Compose) can award ⭐ bonding points when the
//     round ends.
// ══════════════════════════════════════════════════════════════════════════════

object FlappyConstants {
    const val PET_X          = 0.22f
    const val PET_HALF_SIZE  = 0.055f

    // FIX 2: slower gravity — was 0.010f
    const val GRAVITY        = 0.001f
    // FIX 1: much stronger jump — was -0.060f
    const val JUMP_VELOCITY  = -0.110f
    // FIX 3: slightly higher cap
    const val MAX_FALL_SPEED = 0.040f

    // FIX 5: thinner pipes — was 0.12f
    const val OBSTACLE_WIDTH = 0.05f
    // FIX 4: wider gap — was 0.34f
    const val GAP_SIZE       = 0.46f
    // FIX 6: slower scrolling — was 0.0055f
    const val OBSTACLE_SPEED = 0.0045f
    // FIX 7: more spacing between pipes — was 0.58f
    const val SPAWN_GAP_X    = 0.72f

    // FIX 8: first pipe spawns far off-screen for a grace period
    const val FIRST_SPAWN_X  = 1.40f
    const val NORMAL_SPAWN_X = 1.05f

    const val TICK_MS        = 16L

    // Forgiveness inset for collision box (makes hitbox slightly smaller than sprite)
    const val FORGIVE        = 0.012f
}

// ── Reward calculator ─────────────────────────────────────────────────────────
// Call this at game-over to convert score into ⭐ bonding points.
//   0 pts  → 0 ⭐
//   1–4    → 2 ⭐ (survived at least one pipe)
//   5–9    → 5 ⭐
//   10–19  → 10 ⭐
//   20–39  → 20 ⭐
//   40+    → 35 ⭐  (elite round)
// New-best bonus: +5 ⭐ whenever current score beats the stored bestScore.
object FlappyRewardCalculator {
    fun bondingPoints(score: Int, previousBest: Int): Int {
        val base = when {
            score == 0  -> 0
            score < 5   -> 2
            score < 10  -> 5
            score < 20  -> 10
            score < 40  -> 20
            else        -> 35
        }
        val newBestBonus = if (score > previousBest) 5 else 0
        return base + newBestBonus
    }
}

data class FlappyObstacle(val x: Float, val gapTop: Float, val passed: Boolean = false)
enum class FlappyPhase { IDLE, PLAYING, DEAD }

data class FlappyGameState(
    val phase     : FlappyPhase          = FlappyPhase.IDLE,
    val petY      : Float                = 0.42f,
    val velocityY : Float                = 0f,
    val obstacles : List<FlappyObstacle> = emptyList(),
    val score     : Int                  = 0,
    val bestScore : Int                  = 0,
    // Set to true on the tick that triggers DEAD, so the UI can award points once.
    val pendingReward : Boolean          = false
)

object FlappyGameEngine {

    fun tap(state: FlappyGameState): FlappyGameState = when (state.phase) {
        // FIX 9: tap-on-DEAD now auto-restarts instead of doing nothing.
        FlappyPhase.DEAD -> restart(state.bestScore)

        FlappyPhase.IDLE -> state.copy(
            phase     = FlappyPhase.PLAYING,
            velocityY = FlappyConstants.JUMP_VELOCITY,
            // FIX 8: spawn the first obstacle far away
            obstacles = listOf(spawnFirstObstacle())
        )

        FlappyPhase.PLAYING -> state.copy(
            velocityY = FlappyConstants.JUMP_VELOCITY
        )
    }

    fun tick(state: FlappyGameState): FlappyGameState {
        if (state.phase != FlappyPhase.PLAYING) return state

        // ── Physics ───────────────────────────────────────────────────────────
        val newVel = (state.velocityY + FlappyConstants.GRAVITY)
            .coerceAtMost(FlappyConstants.MAX_FALL_SPEED)
        val newY = state.petY + newVel

        // Hit ceiling or floor
        if (newY >= 0.94f || newY <= 0.02f) {
            val best = maxOf(state.score, state.bestScore)
            return state.copy(
                phase         = FlappyPhase.DEAD,
                petY          = newY.coerceIn(0.02f, 0.94f),
                velocityY     = 0f,
                bestScore     = best,
                pendingReward = true
            )
        }

        // ── Obstacle movement + scoring ───────────────────────────────────────
        var newScore = state.score
        val moved = state.obstacles.map { obs ->
            val nx   = obs.x - FlappyConstants.OBSTACLE_SPEED
            val pass = !obs.passed && (nx + FlappyConstants.OBSTACLE_WIDTH) < FlappyConstants.PET_X
            if (pass) newScore++
            obs.copy(x = nx, passed = obs.passed || pass)
        }
        val alive   = moved.filter { it.x + FlappyConstants.OBSTACLE_WIDTH > -0.02f }
        val withNew = if ((alive.maxOfOrNull { it.x } ?: -1f) < 1f - FlappyConstants.SPAWN_GAP_X)
            alive + spawnObstacle() else alive

        // ── Collision — inset hitbox (forgiveness) ────────────────────────────
        val f  = FlappyConstants.FORGIVE
        val pl = FlappyConstants.PET_X   - FlappyConstants.PET_HALF_SIZE + f
        val pr = FlappyConstants.PET_X   + FlappyConstants.PET_HALF_SIZE - f
        val pt = newY - FlappyConstants.PET_HALF_SIZE + f
        val pb = newY + FlappyConstants.PET_HALF_SIZE - f

        val hit = withNew.any { obs ->
            val overX = pr > (obs.x + f) && pl < (obs.x + FlappyConstants.OBSTACLE_WIDTH - f)
            val overY = pt < (obs.gapTop) || pb > (obs.gapTop + FlappyConstants.GAP_SIZE)
            overX && overY
        }

        return if (hit) {
            val best = maxOf(newScore, state.bestScore)
            state.copy(
                phase         = FlappyPhase.DEAD,
                petY          = newY,
                velocityY     = 0f,
                obstacles     = withNew,
                score         = newScore,
                bestScore     = best,
                pendingReward = true
            )
        } else {
            state.copy(
                petY      = newY,
                velocityY = newVel,
                obstacles = withNew,
                score     = newScore,
                pendingReward = false
            )
        }
    }

    /** Call after the UI has consumed the reward so it doesn't double-award. */
    fun clearReward(state: FlappyGameState) = state.copy(pendingReward = false)

    fun restart(bestScore: Int) = FlappyGameState(bestScore = bestScore)

    // FIX 8: first obstacle spawns further right
    private fun spawnFirstObstacle() = FlappyObstacle(
        x      = FlappyConstants.FIRST_SPAWN_X,
        gapTop = safeGapTop()
    )

    private fun spawnObstacle() = FlappyObstacle(
        x      = FlappyConstants.NORMAL_SPAWN_X,
        gapTop = safeGapTop()
    )

    // Keep gap top in a safe range so the opening is never clipped by ceiling/floor.
    //   gap must not start above 0.08 or end below 0.88
    //   gapTop range: [0.08, 0.88 - GAP_SIZE]
    private fun safeGapTop(): Float {
        val minTop = 0.08f
        val maxTop = 0.88f - FlappyConstants.GAP_SIZE   // 0.88 - 0.46 = 0.42
        return minTop + Math.random().toFloat() * (maxTop - minTop)
    }
}

suspend fun runFlappyGameLoop(
    getState: () -> FlappyGameState,
    onStateUpdate: (FlappyGameState) -> Unit
) {
    while (getState().phase == FlappyPhase.PLAYING) {
        delay(FlappyConstants.TICK_MS)
        onStateUpdate(FlappyGameEngine.tick(getState()))
    }
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
//  FIXES APPLIED:
//
//  1. SPAWN_GAP_X raised from 0.60 → 0.75 so the first cactus is always far
//     enough away that the player has time to react before it arrives.
//
//  2. CACTUS_MIN_H lowered from 0.14 → 0.10 and CACTUS_MAX_H lowered from
//     0.24 → 0.20. Taller cacti whose top was above the avatar's starting
//     dinoY (= GROUND_Y - DINO_H = 0.64) caused an overlap the instant they
//     were spawned, triggering instant death.
//
//  3. FORGIVE_X / FORGIVE_Y raised from 0.018/0.020 → 0.022/0.026 to give
//     a slightly more generous inset hitbox.  The drawn obstacle is rendered
//     1.1× wider than CACTUS_W in drawThemedObstacle, so the visual appears
//     wider than the collision — increasing forgiveness corrects this mismatch.
//
//  4. FIRST_SPAWN_DELAY_X added. The first cactus spawned on tap() starts at
//     x = 1.30 instead of 1.05, giving the player a guaranteed grace period
//     before the first obstacle arrives. Subsequent cacti still spawn at 1.05.
//
//  5. SPEED_INC reduced from 0.00025 → 0.00015 so the game does not
//     accelerate to an unplayable pace within the first few points.
//
//  6. avatarB collision uses actual avatar bottom (dinoY + DINO_H minus
//     forgiveness) — NOT GROUND_Y.  This was the root cause of the "keeps
//     losing" bug: the old code effectively extended the hitbox all the way
//     down to the ground, so any cactus that the avatar was standing next to
//     was guaranteed to overlap on the Y axis even while the dino was on flat
//     ground away from the obstacle.
// ══════════════════════════════════════════════════════════════════════════════

object DinoConstants {
    // Avatar geometry (normalised)
    const val DINO_X   = 0.14f   // left edge of avatar column
    const val DINO_W   = 0.08f   // avatar width
    const val DINO_H   = 0.16f   // avatar height (feet to head)
    const val GROUND_Y = 0.80f   // Y where the ground surface sits

    // Physics — tuned for casual feel
    const val GRAVITY        = 0.014f    // per 16 ms tick
    const val JUMP_VELOCITY  = -0.100f   // strong upward impulse
    const val MAX_FALL_SPEED = 0.045f

    // Obstacles
    const val SPEED_START = 0.0070f
    const val SPEED_CAP   = 0.022f
    // FIX 5: reduced speed ramp so the game stays playable longer
    const val SPEED_INC   = 0.00015f    // was 0.00025f

    const val CACTUS_W = 0.055f          // obstacle width (normalised)
    // FIX 2: shorter max height prevents instant collision on spawn
    const val CACTUS_MIN_H = 0.10f       // was 0.14f
    const val CACTUS_MAX_H = 0.20f       // was 0.24f
    // FIX 1: wider gap so the first obstacle doesn't appear right away
    const val SPAWN_GAP_X  = 0.75f       // was 0.60f

    // FIX 4: first cactus starts further right to give the player time to react
    const val FIRST_SPAWN_X = 1.30f      // was 1.05f (same as normal spawn)
    const val NORMAL_SPAWN_X = 1.05f

    // FIX 3: larger forgiveness compensates for the 1.1× visual width in draw
    const val FORGIVE_X = 0.022f         // was 0.018f
    const val FORGIVE_Y = 0.026f         // was 0.020f

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
                // FIX 4: first cactus spawns further away so player has reaction time
                cacti      = if (state.cacti.isEmpty()) listOf(spawnFirstCactus()) else state.cacti
            )
        }
    }

    fun tick(state: DinoGameState): DinoGameState {
        if (state.phase != DinoPhase.PLAYING) return state

        // ── Physics ───────────────────────────────────────────────────────────
        val newVel   = (state.velocityY + DinoConstants.GRAVITY).coerceAtMost(DinoConstants.MAX_FALL_SPEED)
        val rawDinoY = state.dinoY + newVel
        val newDinoY = rawDinoY.coerceAtMost(groundedDinoY)
        val landed   = rawDinoY >= groundedDinoY

        // ── Obstacle movement ─────────────────────────────────────────────────
        val speed = (DinoConstants.SPEED_START + state.score * DinoConstants.SPEED_INC)
            .coerceAtMost(DinoConstants.SPEED_CAP)
        var newScore = state.score
        val moved = state.cacti.map { c ->
            val nx   = c.x - speed
            val pass = !c.passed && (nx + DinoConstants.CACTUS_W) < DinoConstants.DINO_X
            if (pass) newScore++
            c.copy(x = nx, passed = c.passed || pass)
        }
        val alive     = moved.filter { it.x + DinoConstants.CACTUS_W > -0.02f }
        val rightmost = alive.maxOfOrNull { it.x } ?: -1f
        // FIX 1: use the wider SPAWN_GAP_X so subsequent cacti also have breathing room
        val withNew   = if (rightmost < 1f - DinoConstants.SPAWN_GAP_X) alive + spawnCactus() else alive

        // ── AABB collision — avatar body box vs obstacle box ──────────────────
        //
        // Avatar hitbox (inset by FORGIVE_X/Y for fairness):
        val avatarL = DinoConstants.DINO_X + DinoConstants.FORGIVE_X
        val avatarR = DinoConstants.DINO_X + DinoConstants.DINO_W  - DinoConstants.FORGIVE_X
        val avatarT = newDinoY             + DinoConstants.FORGIVE_Y
        // FIX 6: use actual avatar bottom = dinoY + DINO_H, NOT GROUND_Y.
        // Using GROUND_Y here was the root bug — it extended the hitbox all the
        // way to the floor even while jumping, guaranteeing a hit whenever a
        // cactus was anywhere near the avatar's X position.
        val avatarB = newDinoY + DinoConstants.DINO_H - DinoConstants.FORGIVE_Y

        val hit = withNew.any { c ->
            // Obstacle occupies a rectangle from its left edge to right,
            // and from (GROUND_Y - c.height) at top to GROUND_Y at bottom.
            val obstL = c.x                  + DinoConstants.FORGIVE_X
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

    /** First cactus of every run — spawned further right for a grace period. */
    private fun spawnFirstCactus() = Cactus(
        x      = DinoConstants.FIRST_SPAWN_X,
        height = DinoConstants.CACTUS_MIN_H +
                Math.random().toFloat() * (DinoConstants.CACTUS_MAX_H - DinoConstants.CACTUS_MIN_H)
    )

    /** All subsequent cacti spawn at the normal off-screen position. */
    private fun spawnCactus() = Cactus(
        x      = DinoConstants.NORMAL_SPAWN_X,
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