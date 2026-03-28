package com.example.fypdraft.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetShopScreen(
    petState: PetState,
    petRepository: PetRepository,
    onBack: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🛒 Shop", "🎒 Inventory", "📋 Missions", "🍪 Snacks")
    var showFeedDialog by remember { mutableStateOf(false) }
    var feedResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pet Shop", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        // Bonding points display
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💎", fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${petState.bondingPoints}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }) {
                        Text(title, modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp), fontSize = 13.sp)
                    }
                }
            }

            when (selectedTab) {
                0 -> ShopTab(petState, petRepository)
                1 -> InventoryTab(petState, petRepository)
                2 -> MissionsTab(petState, petRepository)
                3 -> SnacksTab(petState, petRepository)
            }
        }
    }

    // Feed result snackbar
    feedResult?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            feedResult = null
        }
    }
}

// ── Shop Tab ─────────────────────────────────────────────────────────────

@Composable
private fun ShopTab(petState: PetState, repo: PetRepository) {
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        // Bonding tier info
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF6A5ACD).copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Tier: ${petState.bondingTier.tierName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(petState.bondingTier.description, fontSize = 12.sp, color = Color.Gray)
                    Text("Level ${petState.level} · ${petState.bondingPoints} 💎", fontSize = 12.sp, color = Color(0xFFFFB300))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Accessories shop by category
        AccessoryCategory.values().forEach { category ->
            Text(
                "${getCategoryEmoji(category)} ${category.name.lowercase().replaceFirstChar { it.uppercase() }}",
                fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))

            val items = ALL_ACCESSORIES.filter { it.category == category }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { acc ->
                    ShopItemCard(
                        accessory = acc,
                        petState = petState,
                        onBuy = { repo.buyAccessory(acc) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text("🍪 Snack Shop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ALL_SNACKS) { snack ->
                SnackShopCard(
                    snack = snack,
                    petState = petState,
                    onBuy = { repo.buySnack(snack) }
                )
            }
        }
    }
}

@Composable
private fun ShopItemCard(accessory: PetAccessory, petState: PetState, onBuy: () -> Unit) {
    val owned = petState.ownsAccessory(accessory.id)
    val canAfford = petState.canAfford(accessory)
    val levelOk = accessory.requiredLevel <= petState.level

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            owned -> Color(0xFF4CAF50).copy(alpha = 0.1f)
            !levelOk -> Color(0xFFE0E0E0)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 2.dp,
        modifier = Modifier.width(120.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(accessory.emoji, fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text(accessory.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(accessory.description, fontSize = 9.sp, color = Color.Gray,
                textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(6.dp))

            when {
                owned -> Text("✓ Owned", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                !levelOk -> Text("🔒 Lv.${accessory.requiredLevel}", fontSize = 10.sp, color = Color.Gray)
                accessory.price == 0 -> Text("Free!", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                else -> {
                    Button(
                        onClick = onBuy,
                        enabled = canAfford,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                    ) {
                        Text("${accessory.price} 💎", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SnackShopCard(snack: PetSnack, petState: PetState, onBuy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.width(110.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Text(snack.emoji, fontSize = 28.sp)
            Text(snack.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("+${snack.energyBoost}⚡ +${snack.happinessBoost}💖", fontSize = 8.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onBuy,
                enabled = petState.canAffordSnack(snack),
                modifier = Modifier.height(26.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                Text("${snack.price} 💎", fontSize = 10.sp, color = Color.White)
            }
            val owned = petState.snackCount(snack.id)
            if (owned > 0) Text("×$owned in bag", fontSize = 8.sp, color = Color(0xFF4CAF50))
        }
    }
}

// ── Inventory Tab ────────────────────────────────────────────────────────

@Composable
private fun InventoryTab(petState: PetState, repo: PetRepository) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Pet preview with current equipment
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                PixelPet(
                    petState = petState,
                    animation = PetAnimation.IDLE,
                    modifier = Modifier.size(100.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("${petState.name} · Lv.${petState.level}", fontWeight = FontWeight.Bold)

                // Needs bars
                Spacer(Modifier.height(8.dp))
                NeedBar("⚡ Energy", petState.energy, Color(0xFFFFB300))
                NeedBar("✨ Clean", petState.cleanliness, Color(0xFF42A5F5))
                NeedBar("💖 Happy", petState.happiness / 100f, Color(0xFFE91E63))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Equipped items
        Text("Equipped", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        AccessoryCategory.values().forEach { cat ->
            val equippedId = when (cat) {
                AccessoryCategory.HAT -> petState.equippedHat
                AccessoryCategory.GLASSES -> petState.equippedGlasses
                AccessoryCategory.NECKLACE -> petState.equippedNecklace
                AccessoryCategory.OUTFIT -> petState.equippedOutfit
            }
            val equipped = equippedId?.let { id -> ALL_ACCESSORIES.find { it.id == id } }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${getCategoryEmoji(cat)} ${cat.name.lowercase().replaceFirstChar { it.uppercase() }}:",
                    fontSize = 13.sp, modifier = Modifier.width(90.dp))
                if (equipped != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF6A5ACD).copy(alpha = 0.1f),
                        modifier = Modifier.clickable { repo.unequipCategory(cat) }
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("${equipped.emoji} ${equipped.name}", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("✕", fontSize = 10.sp, color = Color.Red)
                        }
                    }
                } else {
                    Text("Empty", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Owned items grid
        Text("Your Items (${petState.ownedAccessories.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        val ownedItems = ALL_ACCESSORIES.filter { petState.ownsAccessory(it.id) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ownedItems) { acc ->
                val isEquipped = when (acc.category) {
                    AccessoryCategory.HAT -> petState.equippedHat == acc.id
                    AccessoryCategory.GLASSES -> petState.equippedGlasses == acc.id
                    AccessoryCategory.NECKLACE -> petState.equippedNecklace == acc.id
                    AccessoryCategory.OUTFIT -> petState.equippedOutfit == acc.id
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEquipped) Color(0xFF6A5ACD).copy(alpha = 0.15f) else Color(0xFFF5F5F5),
                    modifier = Modifier.clickable {
                        if (isEquipped) repo.unequipCategory(acc.category)
                        else repo.equipAccessory(acc)
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(10.dp).width(60.dp)
                    ) {
                        Text(acc.emoji, fontSize = 24.sp)
                        Text(acc.name, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
                        if (isEquipped) Text("Worn", fontSize = 8.sp, color = Color(0xFF6A5ACD))
                    }
                }
            }
        }
    }
}

@Composable
private fun NeedBar(label: String, value: Float, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.width(80.dp))
        Box(
            Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray.copy(alpha = 0.3f))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(color)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("${(value * 100).toInt()}%", fontSize = 10.sp, color = Color.Gray)
    }
}

// ── Missions Tab ─────────────────────────────────────────────────────────

@Composable
private fun MissionsTab(petState: PetState, repo: PetRepository) {
    val missions = getDailyMissions(petState.lastDailyReset)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Daily Missions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Complete missions to earn 💎 Bonding Points!", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        missions.forEach { mission ->
            val completed = mission.id in petState.completedMissions
            val canClaim = !completed && mission.checkComplete(petState)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        completed -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        canClaim -> Color(0xFFFFB300).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mission.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mission.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(mission.description, fontSize = 11.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        completed -> Text("✓", fontSize = 20.sp, color = Color(0xFF4CAF50))
                        canClaim -> Button(
                            onClick = { repo.checkAndClaimMission(mission) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("+${mission.reward} 💎", fontSize = 11.sp) }
                        else -> Text("${mission.reward} 💎", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Genre craving
        if (petState.currentCraving != null) {
            val craving = GENRE_CRAVINGS.find { it.genre == petState.currentCraving }
            if (craving != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (petState.cravingSatisfied)
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else Color(0xFFE1BEE7).copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(craving.emoji, fontSize = 32.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Genre Craving!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(craving.message, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (petState.cravingSatisfied) {
                            Text("✓ +${craving.reward}💎", fontSize = 12.sp, color = Color(0xFF4CAF50))
                        } else {
                            Text("+${craving.reward} 💎", fontSize = 12.sp, color = Color(0xFFFFB300))
                        }
                    }
                }
            }
        }
    }
}

// ── Snacks Tab ───────────────────────────────────────────────────────────

@Composable
private fun SnacksTab(petState: PetState, repo: PetRepository) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Your Snack Bag", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Drag snacks onto your pet to feed them!", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        // Needs display
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                NeedBar("⚡ Energy", petState.energy, Color(0xFFFFB300))
                NeedBar("✨ Clean", petState.cleanliness, Color(0xFF42A5F5))
                NeedBar("💖 Happy", petState.happiness / 100f, Color(0xFFE91E63))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { repo.groom() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
                ) {
                    Text("✨ Groom Pet (+5💎)")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Owned snacks
        val ownedSnacks = ALL_SNACKS.filter { petState.snackCount(it.id) > 0 }
        if (ownedSnacks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍪", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No snacks yet!", color = Color.Gray)
                    Text("Buy some from the Shop tab", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            ownedSnacks.forEach { snack ->
                val count = petState.snackCount(snack.id)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snack.emoji, fontSize = 32.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(snack.name, fontWeight = FontWeight.SemiBold)
                            Text(snack.effect, fontSize = 11.sp, color = Color.Gray)
                            Text("+${snack.energyBoost}⚡ +${snack.happinessBoost}💖", fontSize = 10.sp, color = Color(0xFFFFB300))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("×$count", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { repo.feedSnack(snack) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("Feed 🍴", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Active effect display
        if (petState.hasActiveEffect) {
            Spacer(Modifier.height(12.dp))
            val effectSnack = ALL_SNACKS.find { it.id == petState.activeEffect }
            if (effectSnack != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Active: ${effectSnack.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(effectSnack.effect, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun getCategoryEmoji(cat: AccessoryCategory) = when (cat) {
    AccessoryCategory.HAT -> "🎩"
    AccessoryCategory.GLASSES -> "👓"
    AccessoryCategory.NECKLACE -> "📿"
    AccessoryCategory.OUTFIT -> "👔"
}