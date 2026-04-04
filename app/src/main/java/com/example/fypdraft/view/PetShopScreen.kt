package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.*
import kotlinx.coroutines.launch

// ── Warm sandy palette matching the screenshots ───────────────────────────
private val BgSand      = Color(0xFFF5EDD8)
private val CardSand    = Color(0xFFE8D9BC)
private val CardDeep    = Color(0xFFCFBB9A)
private val AccentOrange = Color(0xFFFF8C42)
private val AccentStar  = Color(0xFFFFD700)
private val TextDark    = Color(0xFF3A2A1A)
private val TextMid     = Color(0xFF7A6050)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetShopScreen(
    petState: PetState,
    petRepository: PetRepository,
    onBack: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }      // 0=MoodEqualiser 1=Dressing
    var snackbarText by remember { mutableStateOf<String?>(null) }

    // Animate pet entrance
    val petScale by animateFloatAsState(
        targetValue = 1f, animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "petScale"
    )

    Scaffold(
        containerColor = BgSand,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(petState.name, fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null, tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgSand),
                actions = {
                    // Star currency display
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardSand)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${petState.bondingPoints}",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(BgSand)
                .padding(padding)
        ) {
            // ── Pet display + level bar ───────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFAEDCC), BgSand))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Pet sprite
                    Box(
                        Modifier
                            .size(120.dp)
                            .graphicsLayer(scaleX = petScale, scaleY = petScale)
                    ) {
                        PixelPet(
                            petState  = petState,
                            animation = PetAnimation.HAPPY_BOUNCE,
                            modifier  = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Level pill + XP bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentOrange)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text("Lv ${petState.level}", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .width(160.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardDeep)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(petState.xpProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentOrange)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${petState.xp}/${petState.xpForNextLevel}",
                            fontSize = 11.sp, color = TextMid)
                    }
                }
            }

            // ── Tab row ───────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSand)
            ) {
                listOf("🎮 Mood Lab", "👕 Dressing").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedTab == i) CardDeep else Color.Transparent)
                            .clickable { selectedTab = i }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold,
                            color = if (selectedTab == i) TextDark else TextMid,
                            fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Tab content ───────────────────────────────────────────
            when (selectedTab) {
                0 -> MoodLabTab(petState, petRepository, onNavigateToMusicPlayer)
                1 -> DressingTab(petState, petRepository,
                    onBuy = { acc ->
                        val ok = petRepository.buyAccessory(acc)
                        snackbarText = if (ok) "Bought ${acc.name}! ✨" else "Not enough ⭐"
                    },
                    onEquip = { acc ->
                        petRepository.equipAccessory(acc)
                        snackbarText = "Equipped ${acc.name}!"
                    }
                )
            }
        }
    }

    // Snackbar overlay
    snackbarText?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_000)
            snackbarText = null
        }
        Box(
            Modifier.fillMaxSize().padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A2E))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Tab 1 — Mood Lab (Image 1 reference: pet status + activity grid)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MoodLabTab(
    petState: PetState,
    petRepository: PetRepository,
    onNavigateToMusicPlayer: () -> Unit
) {
    val inf  = rememberInfiniteTransition(label = "ml")
    val glow by inf.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(1_200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Needs status (energy / happiness / cleanliness) ───────────
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSand),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                    Spacer(Modifier.height(10.dp))
                    NeedBar("⚡ Energy",     petState.energy,       Color(0xFFFFB347))
                    Spacer(Modifier.height(6.dp))
                    NeedBar("💖 Happiness",  petState.happiness / 100f, Color(0xFFFF80AB))
                    Spacer(Modifier.height(6.dp))
                    NeedBar("✨ Cleanliness", petState.cleanliness,  Color(0xFF7BE8D8))
                }
            }
        }

        // ── Genre craving ─────────────────────────────────────────────
        if (petState.currentCraving != null && !petState.cravingSatisfied) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.graphicsLayer(alpha = glow)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤤", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Craving ${petState.currentCraving}!",
                                fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Play some ${petState.currentCraving?.lowercase()} to satisfy it",
                                fontSize = 12.sp, color = TextMid)
                        }
                    }
                }
            }
        }

        // ── Activity grid (Image 1/2 reference) ───────────────────────
        item {
            Text("Activities", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = TextDark, modifier = Modifier.padding(top = 4.dp))
        }

        // Big centre button — "Earn Stars" (领火星 equivalent)
        item {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardSand)
                        .clickable { onNavigateToMusicPlayer() }
                        .graphicsLayer(scaleX = glow * 0.05f + 0.95f, scaleY = glow * 0.05f + 0.95f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 36.sp)
                        Text("Earn Stars", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = TextDark)
                        Text("⭐ ${petState.bondingPoints}", fontSize = 11.sp, color = TextMid)
                    }
                }
            }
        }

        // 2-column grid of activities
        val activities = listOf(
            ActivityItem("🎴", "Sprite Cards", "Collect pet forms"),
            ActivityItem("📖", "Bestiary",     "View collection"),
            ActivityItem("🛠️", "Work",         "Earn stars"),
            ActivityItem("🎮", "Play",         "Mini games"),
            ActivityItem("🤖", "AI Chat",      "Talk to your pet"),
            ActivityItem("😊", "Emote Pack",   "Custom emotes"),
            ActivityItem("🏆", "Rankings",     "Star leaderboard"),
            ActivityItem("🛁", "Groom",        "Clean your pet"),
        )
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(340.dp)
            ) {
                items(activities) { act ->
                    ActivityCard(act, onClick = {
                        if (act.label == "Groom") petRepository.groom()
                    })
                }
            }
        }

        // ── Daily missions ─────────────────────────────────────────────
        item {
            Text("Daily Missions", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = TextDark, modifier = Modifier.padding(top = 4.dp))
        }
        item {
            val missions = getDailyMissions(System.currentTimeMillis())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                missions.forEach { mission ->
                    val done = mission.id in petState.completedMissions
                    val met  = mission.checkComplete(petState)
                    MissionRow(
                        mission   = mission,
                        isDone    = done,
                        isMet     = met,
                        onClaim   = { petRepository.checkAndClaimMission(mission) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

private data class ActivityItem(val emoji: String, val label: String, val sub: String)

@Composable
private fun ActivityCard(act: ActivityItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSand),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CardDeep),
                contentAlignment = Alignment.Center
            ) { Text(act.emoji, fontSize = 20.sp) }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(act.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                Text(act.sub,   fontSize = 10.sp, color = TextMid)
            }
        }
    }
}

@Composable
private fun NeedBar(label: String, value: Float, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = TextMid)
            Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = TextMid)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(CardDeep)
        ) {
            Box(
                Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(color)
            )
        }
    }
}

@Composable
private fun MissionRow(
    mission: DailyMission,
    isDone: Boolean,
    isMet: Boolean,
    onClaim: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDone) CardDeep else CardSand),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(mission.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mission.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                Text(mission.description, fontSize = 11.sp, color = TextMid)
            }
            when {
                isDone -> Text("✅", fontSize = 18.sp)
                isMet  -> Button(
                    onClick  = onClaim,
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape    = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("+${mission.reward}⭐", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                else   -> Text("⭐${mission.reward}", fontSize = 12.sp, color = TextMid)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Tab 2 — Dressing (Image 2 reference: category grid → items scroll)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun DressingTab(
    petState: PetState,
    petRepository: PetRepository,
    onBuy: (PetAccessory) -> Unit,
    onEquip: (PetAccessory) -> Unit
) {
    // Category filter
    var selectedCategory by remember { mutableStateOf<AccessoryCategory?>(null) }

    val allUnlocked = petState.unlockedAccessories()
    val filtered    = if (selectedCategory == null) allUnlocked
    else allUnlocked.filter { it.category == selectedCategory }

    Column(Modifier.fillMaxSize()) {
        // ── Category chips ────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip("All", null, selectedCategory == null) {
                    selectedCategory = null
                }
            }
            items(AccessoryCategory.entries) { cat ->
                val emoji = when (cat) {
                    AccessoryCategory.HAT      -> "🧢"
                    AccessoryCategory.GLASSES  -> "👓"
                    AccessoryCategory.NECKLACE -> "📿"
                    AccessoryCategory.OUTFIT   -> "👕"
                }
                CategoryChip("$emoji ${cat.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    cat, selectedCategory == cat) { selectedCategory = cat }
            }
        }

        // ── Item grid (Image 2 style: 2-column cards) ─────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { acc ->
                val owned    = petState.ownsAccessory(acc.id)
                val equipped = petState.equippedHat == acc.id ||
                        petState.equippedGlasses == acc.id ||
                        petState.equippedNecklace == acc.id ||
                        petState.equippedOutfit == acc.id
                val canAfford = petState.canAfford(acc)

                DressingCard(
                    acc      = acc,
                    owned    = owned,
                    equipped = equipped,
                    canAfford = canAfford,
                    onAction = {
                        if (owned) onEquip(acc) else onBuy(acc)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String, cat: AccessoryCategory?, selected: Boolean, onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) CardDeep else CardSand)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) AccentOrange else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TextDark else TextMid)
    }
}

@Composable
private fun DressingCard(
    acc: PetAccessory,
    owned: Boolean,
    equipped: Boolean,
    canAfford: Boolean,
    onAction: () -> Unit
) {
    Card(
        shape  = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                equipped -> Color(0xFFFFF3E0)
                owned    -> CardSand
                else     -> CardSand.copy(alpha = 0.7f)
            }
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = Modifier
            .then(if (equipped) Modifier.border(2.dp, AccentOrange, RoundedCornerShape(18.dp)) else Modifier)
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji icon in a circle
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(CardDeep),
                contentAlignment = Alignment.Center
            ) { Text(acc.emoji, fontSize = 30.sp) }

            Spacer(Modifier.height(8.dp))
            Text(acc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark,
                textAlign = TextAlign.Center)
            Text(acc.description, fontSize = 10.sp, color = TextMid,
                textAlign = TextAlign.Center, maxLines = 2)

            Spacer(Modifier.height(8.dp))

            when {
                equipped -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(AccentOrange).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Equipped ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                owned -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(CardDeep).clickable { onAction() }.padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Equip", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                else -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (canAfford) AccentStar else Color.LightGray)
                        .clickable(enabled = canAfford) { onAction() }.padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (acc.price == 0) "Free (Lv ${acc.requiredLevel})"
                        else "⭐ ${acc.price}",
                        color = if (canAfford) Color(0xFF3A2A1A) else Color.Gray,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
            }
        }
    }
}