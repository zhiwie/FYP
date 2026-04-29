package com.example.fypdraft.model

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

// ── Pet types ────────────────────────────────────────────────────────────

enum class PetType(val displayName: String, val emoji: String) {
    CAT("Kitty", "🐱"), DOG("Puppy", "🐶"), BEAR("Teddy", "🐻"), BUNNY("Bunbun", "🐰")
}

// ── Animation states ─────────────────────────────────────────────────────

enum class PetAnimation {
    IDLE, HAPPY_BOUNCE, SAD_DROOP, ENERGETIC_JUMP, SLEEPY_NOD,
    FOCUSED_STARE, LOVE_EYES, DANCING, EATING, LISTENING
}

// ── Bonding tiers ────────────────────────────────────────────────────────

enum class BondingTier(val tierName: String, val minLevel: Int, val description: String) {
    STRANGER("Stranger", 1, "Basic taps, blinking, yawning"),
    PLAYMATE("Playmate", 6, "Feeding, petting, dance moves"),
    BEST_FRIEND("Best Friend", 16, "Daily playlists, new skins"),
    SOULMATE("Soulmate", 31, "Special animations for favorite songs")
}

fun getBondingTier(level: Int): BondingTier = when {
    level >= 31 -> BondingTier.SOULMATE
    level >= 16 -> BondingTier.BEST_FRIEND
    level >= 6 -> BondingTier.PLAYMATE
    else -> BondingTier.STRANGER
}

// ── Accessories ──────────────────────────────────────────────────────────

data class PetAccessory(
    val id: String, val name: String, val emoji: String,
    val category: AccessoryCategory, val requiredLevel: Int,
    val price: Int = 0, // 0 = free/level-unlocked, >0 = costs bonding points
    val description: String
)

enum class AccessoryCategory { HAT, GLASSES, NECKLACE, OUTFIT }

val ALL_ACCESSORIES = listOf(
    PetAccessory("hat_music", "Headphones", "🎧", AccessoryCategory.HAT, 1, 0, "Born to listen"),
    PetAccessory("hat_crown", "Crown", "👑", AccessoryCategory.HAT, 5, 50, "Music royalty"),
    PetAccessory("hat_party", "Party Hat", "🎉", AccessoryCategory.HAT, 3, 30, "Let's celebrate!"),
    PetAccessory("hat_beanie", "Beanie", "🧶", AccessoryCategory.HAT, 2, 20, "Cozy vibes"),
    PetAccessory("hat_wizard", "Wizard Hat", "🧙", AccessoryCategory.HAT, 8, 80, "Musical wizard"),
    PetAccessory("hat_flower", "Flower Crown", "🌸", AccessoryCategory.HAT, 4, 40, "Garden party"),
    PetAccessory("hat_dj", "DJ Cap", "🎹", AccessoryCategory.HAT, 10, 100, "Drop the beat"),
    PetAccessory("glass_cool", "Sunglasses", "😎", AccessoryCategory.GLASSES, 2, 20, "Too cool"),
    PetAccessory("glass_nerd", "Nerd Glasses", "🤓", AccessoryCategory.GLASSES, 3, 25, "Smart cookie"),
    PetAccessory("glass_star", "Star Shades", "⭐", AccessoryCategory.GLASSES, 6, 60, "Superstar"),
    PetAccessory("glass_heart", "Heart Glasses", "💕", AccessoryCategory.GLASSES, 4, 35, "Love is blind"),
    PetAccessory("neck_note", "Music Note", "🎵", AccessoryCategory.NECKLACE, 1, 0, "Musical soul"),
    PetAccessory("neck_heart", "Heart Pendant", "💖", AccessoryCategory.NECKLACE, 3, 30, "Full of love"),
    PetAccessory("neck_star", "Star Chain", "✨", AccessoryCategory.NECKLACE, 5, 50, "Shining bright"),
    PetAccessory("neck_moon", "Moon Pendant", "🌙", AccessoryCategory.NECKLACE, 7, 70, "Night owl"),
    PetAccessory("fit_tshirt", "Band Tee", "👕", AccessoryCategory.OUTFIT, 2, 15, "Concert ready"),
    PetAccessory("fit_hoodie", "Cozy Hoodie", "🧥", AccessoryCategory.OUTFIT, 4, 40, "Snug as a bug"),
    PetAccessory("fit_cape", "Super Cape", "🦸", AccessoryCategory.OUTFIT, 6, 60, "Hero mode"),
    PetAccessory("fit_tux", "Fancy Tuxedo", "🤵", AccessoryCategory.OUTFIT, 10, 120, "Dapper!")
)

// ── Snacks (consumable items that affect pet needs) ──────────────────────

data class PetSnack(
    val id: String, val name: String, val emoji: String,
    val price: Int, val energyBoost: Int, val happinessBoost: Int,
    val description: String, val effect: String
)

val ALL_SNACKS = listOf(
    PetSnack("snack_note", "Music Note", "🎵", 5, 10, 5, "A tasty note!", "Sugar rush: faster animations for 30s"),
    PetSnack("snack_vinyl", "Mini Vinyl", "💿", 10, 20, 10, "Vintage flavor", "Nostalgic glow for 1 min"),
    PetSnack("snack_star", "Star Cookie", "⭐", 15, 15, 20, "Sweet starlight", "Sparkle trail for 2 min"),
    PetSnack("snack_heart", "Love Candy", "💗", 20, 10, 30, "Pure love", "Hearts float around for 1 min"),
    PetSnack("snack_thunder", "Thunder Bolt", "⚡", 25, 40, 5, "ZAP!", "Hyper energy for 45s"),
    PetSnack("snack_cake", "Music Cake", "🎂", 50, 50, 50, "Party time!", "Full restore + celebration"),
    PetSnack("snack_rainbow", "Rainbow Drop", "🌈", 35, 30, 30, "All the vibes", "Rainbow glow for 2 min"),
)

// ── Daily missions ───────────────────────────────────────────────────────

data class DailyMission(
    val id: String, val title: String, val description: String,
    val reward: Int, val emoji: String,
    val checkComplete: (PetState) -> Boolean
)

fun getDailyMissions(startOfDay: Long): List<DailyMission> = listOf(
    DailyMission("m_highfive", "High Five!", "Tap the mascot while it's waving", 10, "🖐️") { it.totalInteractions > 0 },
    DailyMission("m_lullaby", "Lullaby", "Play a calm song to put the mascot to bed", 15, "🌙") { it.calmSongsToday > 0 },
    DailyMission("m_danceoff", "Dance Off!", "Play 3 energetic songs", 20, "💃") { it.energeticSongsToday >= 3 },
    DailyMission("m_feedme", "Feed Me!", "Feed your pet 2 snacks", 10, "🍪") { it.snacksFedToday >= 2 },
    DailyMission("m_explorer", "Explorer", "Listen to 5 different songs", 25, "🗺️") { it.uniqueSongsToday >= 5 },
    DailyMission("m_groomer", "Sparkle Clean", "Groom your pet once", 15, "✨") { it.groomedToday },
)

// ── Genre cravings ───────────────────────────────────────────────────────

data class GenreCraving(
    val genre: String, val query: String, val emoji: String,
    val message: String, val reward: Int
)

val GENRE_CRAVINGS = listOf(
    GenreCraving("Lo-Fi", "lofi chill beats", "🎧", "I'm feeling a bit Lo-Fi today... got any?", 15),
    GenreCraving("Jazz", "smooth jazz", "🎷", "Something jazzy would hit the spot~", 15),
    GenreCraving("Pop", "pop hits upbeat", "🎤", "I wanna sing along to something catchy!", 10),
    GenreCraving("Rock", "rock energetic", "🎸", "Let's ROCK! 🤘", 15),
    GenreCraving("Classical", "classical piano", "🎹", "Something elegant, please~", 20),
    GenreCraving("R&B", "rnb smooth soul", "🎵", "Smooth vibes tonight~", 15),
    GenreCraving("Electronic", "electronic dance", "🔊", "DROP THE BASS!", 15),
)

// ── Pet state (expanded) ─────────────────────────────────────────────────

data class PetState(
    val name: String = "Buddy",
    val type: PetType = PetType.CAT,
    val xp: Int = 0,
    val level: Int = 1,
    val mood: String = "neutral",

    // Equipped items
    val equippedHat: String? = null,
    val equippedGlasses: String? = null,
    val equippedNecklace: String? = null,
    val equippedOutfit: String? = null,

    // Stats
    val totalSongsPlayed: Int = 0,
    val totalMinutesListened: Int = 0,
    val happiness: Int = 80,

    // Needs system (0.0 - 1.0)
    val energy: Float = 1f,
    val cleanliness: Float = 1f,

    // Currency
    val bondingPoints: Int = 0,

    // Inventory (owned item IDs)
    val ownedAccessories: List<String> = listOf("hat_music", "neck_note"), // Starter items
    val ownedSnacks: Map<String, Int> = emptyMap(), // snackId -> count

    // Daily tracking
    val totalInteractions: Int = 0,
    val calmSongsToday: Int = 0,
    val energeticSongsToday: Int = 0,
    val snacksFedToday: Int = 0,
    val uniqueSongsToday: Int = 0,
    val groomedToday: Boolean = false,
    val completedMissions: List<String> = emptyList(),
    val lastDailyReset: Long = 0,
    val lastOpenDate: String = "",

    // Active effects from snacks
    val activeEffect: String? = null, // snack ID
    val effectExpiry: Long = 0,

    // Genre craving
    val currentCraving: String? = null, // genre name
    val cravingSatisfied: Boolean = false
) {
    val xpForNextLevel: Int get() = level * 100
    val xpProgress: Float get() = xp.toFloat() / xpForNextLevel
    val bondingTier: BondingTier get() = getBondingTier(level)

    val hasActiveEffect: Boolean get() = activeEffect != null && System.currentTimeMillis() < effectExpiry

    fun unlockedAccessories(): List<PetAccessory> = ALL_ACCESSORIES.filter { it.requiredLevel <= level }
    fun canAfford(item: PetAccessory): Boolean = bondingPoints >= item.price
    fun canAffordSnack(snack: PetSnack): Boolean = bondingPoints >= snack.price
    fun ownsAccessory(id: String): Boolean = id in ownedAccessories
    fun snackCount(id: String): Int = ownedSnacks[id] ?: 0

    fun animationForMood(): PetAnimation = when (mood) {
        "happy" -> PetAnimation.HAPPY_BOUNCE; "sad" -> PetAnimation.SAD_DROOP
        "energetic" -> PetAnimation.ENERGETIC_JUMP; "calm" -> PetAnimation.IDLE
        "tired" -> PetAnimation.SLEEPY_NOD; "focused" -> PetAnimation.FOCUSED_STARE
        "romantic" -> PetAnimation.LOVE_EYES; else -> PetAnimation.IDLE
    }
}

// ── Pet repository ───────────────────────────────────────────────────────

class PetRepository {
    private val TAG = "PetRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _petState = MutableStateFlow(PetState())
    val petState: StateFlow<PetState> = _petState.asStateFlow()

    // ── Loading gate ──────────────────────────────────────────────────
    // Starts false; set to true once loadPet() completes (success or error).
    // The UI should wait for this before rendering avatar-dependent content.
    private val _petLoaded = MutableStateFlow(false)
    val petLoaded: StateFlow<Boolean> = _petLoaded.asStateFlow()

    private fun userId(): String? = auth.currentUser?.uid
    private fun petDoc() = userId()?.let { firestore.collection("pets").document(it) }

    private fun saveFullState(state: PetState) {
        petDoc()?.set(stateToMap(state))
            ?.addOnFailureListener { Log.e(TAG, "Save failed", it) }
    }

    private fun saveMerge(fields: Map<String, Any?>) {
        petDoc()?.set(fields, SetOptions.merge())
            ?.addOnFailureListener { Log.e(TAG, "Merge save failed", it) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadPet(): PetState {
        val doc = petDoc() ?: return PetState()
        return try {
            val s = doc.get().await()
            if (s.exists()) {
                val state = PetState(
                    name = s.getString("name") ?: "Buddy",
                    type = try { PetType.valueOf(s.getString("type") ?: "CAT") } catch (_: Exception) { PetType.CAT },
                    xp = (s.getLong("xp") ?: 0).toInt(),
                    level = (s.getLong("level") ?: 1).toInt(),
                    mood = s.getString("mood") ?: "neutral",
                    equippedHat = s.getString("equippedHat"),
                    equippedGlasses = s.getString("equippedGlasses"),
                    equippedNecklace = s.getString("equippedNecklace"),
                    equippedOutfit = s.getString("equippedOutfit"),
                    totalSongsPlayed = (s.getLong("totalSongsPlayed") ?: 0).toInt(),
                    totalMinutesListened = (s.getLong("totalMinutesListened") ?: 0).toInt(),
                    happiness = (s.getLong("happiness") ?: 80).toInt(),
                    energy = (s.getDouble("energy") ?: 1.0).toFloat(),
                    cleanliness = (s.getDouble("cleanliness") ?: 1.0).toFloat(),
                    bondingPoints = (s.getLong("bondingPoints") ?: 0).toInt(),
                    ownedAccessories = (s.get("ownedAccessories") as? List<String>) ?: listOf("hat_music", "neck_note"),
                    ownedSnacks = (s.get("ownedSnacks") as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap(),
                    totalInteractions = (s.getLong("totalInteractions") ?: 0).toInt(),
                    calmSongsToday = (s.getLong("calmSongsToday") ?: 0).toInt(),
                    energeticSongsToday = (s.getLong("energeticSongsToday") ?: 0).toInt(),
                    snacksFedToday = (s.getLong("snacksFedToday") ?: 0).toInt(),
                    uniqueSongsToday = (s.getLong("uniqueSongsToday") ?: 0).toInt(),
                    groomedToday = s.getBoolean("groomedToday") ?: false,
                    completedMissions = (s.get("completedMissions") as? List<String>) ?: emptyList(),
                    lastDailyReset = s.getLong("lastDailyReset") ?: 0,
                    lastOpenDate = s.getString("lastOpenDate") ?: "",
                    activeEffect = s.getString("activeEffect"),
                    effectExpiry = s.getLong("effectExpiry") ?: 0,
                    currentCraving = s.getString("currentCraving"),
                    cravingSatisfied = s.getBoolean("cravingSatisfied") ?: false
                )
                _petState.value = checkDailyReset(state)
                _petLoaded.value = true
                _petState.value
            } else {
                val default = PetState()
                saveFullState(default)
                _petState.value = default
                _petLoaded.value = true
                default
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            _petLoaded.value = true   // unblock UI even on error; use defaults
            PetState()
        }
    }

    private fun checkDailyReset(state: PetState): PetState {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        return if (state.lastOpenDate != today) {
            // New day — reset dailies, pick new craving
            val craving = GENRE_CRAVINGS.random().genre
            state.copy(
                calmSongsToday = 0, energeticSongsToday = 0, snacksFedToday = 0,
                uniqueSongsToday = 0, groomedToday = false, totalInteractions = 0,
                completedMissions = emptyList(), lastOpenDate = today,
                lastDailyReset = System.currentTimeMillis(),
                currentCraving = craving, cravingSatisfied = false,
                // Needs decay overnight
                energy = (state.energy - 0.2f).coerceAtLeast(0f),
                cleanliness = (state.cleanliness - 0.15f).coerceAtLeast(0f),
                happiness = (state.happiness - 10).coerceAtLeast(0)
            )
        } else state
    }

    // ── Actions ──────────────────────────────────────────────────────

    fun addXP(amount: Int) {
        val cur = _petState.value
        var newXP = cur.xp + amount
        var newLevel = cur.level
        while (newXP >= newLevel * 100) { newXP -= newLevel * 100; newLevel++ }
        val bp = amount / 2 // Earn bonding points alongside XP
        val updated = cur.copy(
            xp = newXP, level = newLevel,
            totalSongsPlayed = cur.totalSongsPlayed + 1,
            happiness = (cur.happiness + 3).coerceAtMost(100),
            bondingPoints = cur.bondingPoints + bp,
            uniqueSongsToday = cur.uniqueSongsToday + 1
        )
        _petState.value = updated
        saveFullState(updated)
    }

    fun recordSongMood(mood: String) {
        val cur = _petState.value
        val updated = when (mood) {
            "calm", "tired" -> cur.copy(calmSongsToday = cur.calmSongsToday + 1)
            "energetic", "happy" -> cur.copy(energeticSongsToday = cur.energeticSongsToday + 1)
            else -> cur
        }
        _petState.value = updated
        saveMerge(mapOf("calmSongsToday" to updated.calmSongsToday, "energeticSongsToday" to updated.energeticSongsToday))
    }

    fun recordInteraction() {
        val cur = _petState.value
        val updated = cur.copy(totalInteractions = cur.totalInteractions + 1)
        _petState.value = updated
        saveMerge(mapOf("totalInteractions" to updated.totalInteractions))
    }

    fun updateMood(mood: String) {
        _petState.value = _petState.value.copy(mood = mood)
        saveMerge(mapOf("mood" to mood))
    }

    fun changePetType(type: PetType) {
        _petState.value = _petState.value.copy(type = type)
        saveMerge(mapOf("type" to type.name))
    }

    fun renamePet(newName: String) {
        _petState.value = _petState.value.copy(name = newName)
        saveMerge(mapOf("name" to newName))
    }

    // ── Needs ────────────────────────────────────────────────────────

    fun feedSnack(snack: PetSnack) {
        val cur = _petState.value
        val count = cur.snackCount(snack.id)
        if (count <= 0) return

        val newSnacks = cur.ownedSnacks.toMutableMap()
        newSnacks[snack.id] = count - 1
        if (newSnacks[snack.id] == 0) newSnacks.remove(snack.id)

        val updated = cur.copy(
            ownedSnacks = newSnacks,
            energy = (cur.energy + snack.energyBoost / 100f).coerceAtMost(1f),
            happiness = (cur.happiness + snack.happinessBoost).coerceAtMost(100),
            snacksFedToday = cur.snacksFedToday + 1,
            activeEffect = snack.id,
            effectExpiry = System.currentTimeMillis() + 60_000L // 1 minute effect
        )
        _petState.value = updated
        saveFullState(updated)
    }

    fun groom() {
        val cur = _petState.value
        val updated = cur.copy(
            cleanliness = 1f,
            happiness = (cur.happiness + 10).coerceAtMost(100),
            groomedToday = true,
            bondingPoints = cur.bondingPoints + 5
        )
        _petState.value = updated
        saveFullState(updated)
    }

    fun decayNeeds() {
        val cur = _petState.value
        val updated = cur.copy(
            energy = (cur.energy - 0.002f).coerceAtLeast(0f),
            cleanliness = (cur.cleanliness - 0.001f).coerceAtLeast(0f)
        )
        _petState.value = updated
        // Don't save every tick — save periodically
    }

    // ── Shop ─────────────────────────────────────────────────────────

    fun buyAccessory(acc: PetAccessory): Boolean {
        val cur = _petState.value
        if (cur.bondingPoints < acc.price || cur.ownsAccessory(acc.id)) return false
        val updated = cur.copy(
            bondingPoints = cur.bondingPoints - acc.price,
            ownedAccessories = cur.ownedAccessories + acc.id
        )
        _petState.value = updated
        saveFullState(updated)
        return true
    }

    fun buySnack(snack: PetSnack, quantity: Int = 1): Boolean {
        val cur = _petState.value
        val totalCost = snack.price * quantity
        if (cur.bondingPoints < totalCost) return false
        val newSnacks = cur.ownedSnacks.toMutableMap()
        newSnacks[snack.id] = (newSnacks[snack.id] ?: 0) + quantity
        val updated = cur.copy(bondingPoints = cur.bondingPoints - totalCost, ownedSnacks = newSnacks)
        _petState.value = updated
        saveFullState(updated)
        return true
    }

    // ── Equipment ────────────────────────────────────────────────────

    fun equipAccessory(acc: PetAccessory) {
        if (!_petState.value.ownsAccessory(acc.id)) return
        val cur = _petState.value
        val updated = when (acc.category) {
            AccessoryCategory.HAT -> cur.copy(equippedHat = acc.id)
            AccessoryCategory.GLASSES -> cur.copy(equippedGlasses = acc.id)
            AccessoryCategory.NECKLACE -> cur.copy(equippedNecklace = acc.id)
            AccessoryCategory.OUTFIT -> cur.copy(equippedOutfit = acc.id)
        }
        _petState.value = updated
        saveFullState(updated)
    }

    fun unequipCategory(category: AccessoryCategory) {
        val cur = _petState.value
        val updated = when (category) {
            AccessoryCategory.HAT -> cur.copy(equippedHat = null)
            AccessoryCategory.GLASSES -> cur.copy(equippedGlasses = null)
            AccessoryCategory.NECKLACE -> cur.copy(equippedNecklace = null)
            AccessoryCategory.OUTFIT -> cur.copy(equippedOutfit = null)
        }
        _petState.value = updated
        saveFullState(updated)
    }

    /**
     * Equip a layered-avatar accessory token directly (no ownership check).
     * Used by the new LayeredAvatarSystem where accessories are PNG overlays,
     * not items from the legacy ALL_ACCESSORIES list.
     *
     * [token]    — "sunglasses" or "tie"
     * [category] — which slot to write into (GLASSES or NECKLACE)
     */
    fun equipLayeredAccessory(token: String, category: AccessoryCategory) {
        val cur = _petState.value
        val updated = when (category) {
            AccessoryCategory.GLASSES  -> cur.copy(equippedGlasses  = token)
            AccessoryCategory.NECKLACE -> cur.copy(equippedNecklace = token)
            AccessoryCategory.HAT      -> cur.copy(equippedHat      = token)
            AccessoryCategory.OUTFIT   -> cur.copy(equippedOutfit   = token)
        }
        _petState.value = updated
        saveMerge(
            when (category) {
                AccessoryCategory.GLASSES  -> mapOf("equippedGlasses"  to token)
                AccessoryCategory.NECKLACE -> mapOf("equippedNecklace" to token)
                AccessoryCategory.HAT      -> mapOf("equippedHat"      to token)
                AccessoryCategory.OUTFIT   -> mapOf("equippedOutfit"   to token)
            }
        )
    }

    // ── Missions ─────────────────────────────────────────────────────

    fun checkAndClaimMission(mission: DailyMission): Boolean {
        val cur = _petState.value
        if (mission.id in cur.completedMissions) return false
        if (!mission.checkComplete(cur)) return false
        val updated = cur.copy(
            completedMissions = cur.completedMissions + mission.id,
            bondingPoints = cur.bondingPoints + mission.reward
        )
        _petState.value = updated
        saveFullState(updated)
        return true
    }

    fun satisfyCraving() {
        val cur = _petState.value
        if (cur.cravingSatisfied) return
        val updated = cur.copy(
            cravingSatisfied = true,
            bondingPoints = cur.bondingPoints + 15,
            happiness = (cur.happiness + 15).coerceAtMost(100)
        )
        _petState.value = updated
        saveFullState(updated)
    }
// ── Game rewards ─────────────────────────────────────────────────

    /**
     * Awards bonding points earned from the Flappy mini-game.
     * Uses saveMerge (not saveFullState) so it's a lightweight update.
     */
    fun addBondingPointsFromGame(points: Int) {
        if (points <= 0) return
        val cur     = _petState.value
        val updated = cur.copy(bondingPoints = cur.bondingPoints + points)
        _petState.value = updated
        saveMerge(mapOf("bondingPoints" to updated.bondingPoints))
    }
    // ── Serialization helper ─────────────────────────────────────────

    private fun stateToMap(s: PetState): Map<String, Any?> = mapOf(
        "name" to s.name, "type" to s.type.name, "xp" to s.xp, "level" to s.level,
        "mood" to s.mood, "equippedHat" to s.equippedHat, "equippedGlasses" to s.equippedGlasses,
        "equippedNecklace" to s.equippedNecklace, "equippedOutfit" to s.equippedOutfit,
        "totalSongsPlayed" to s.totalSongsPlayed, "totalMinutesListened" to s.totalMinutesListened,
        "happiness" to s.happiness, "energy" to s.energy.toDouble(), "cleanliness" to s.cleanliness.toDouble(),
        "bondingPoints" to s.bondingPoints, "ownedAccessories" to s.ownedAccessories,
        "ownedSnacks" to s.ownedSnacks, "totalInteractions" to s.totalInteractions,
        "calmSongsToday" to s.calmSongsToday, "energeticSongsToday" to s.energeticSongsToday,
        "snacksFedToday" to s.snacksFedToday, "uniqueSongsToday" to s.uniqueSongsToday,
        "groomedToday" to s.groomedToday, "completedMissions" to s.completedMissions,
        "lastDailyReset" to s.lastDailyReset, "lastOpenDate" to s.lastOpenDate,
        "activeEffect" to s.activeEffect, "effectExpiry" to s.effectExpiry,
        "currentCraving" to s.currentCraving, "cravingSatisfied" to s.cravingSatisfied
    )
}