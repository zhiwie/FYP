package com.example.fypdraft.view

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionScreen(
    themeManager: ThemeManager,
    onBack: () -> Unit = {}
) {
    val themeState by themeManager.themeState.collectAsState()
    val bgBrush = animatedMoodBrush(themeState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(bgBrush).padding(padding)
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // ── Live preview ─────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Live Preview", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))

                        // Mini mock screen
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            val p = themeState.activePalette
                            val dark = themeState.isDark
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(
                                        if (dark) p.darkTop else p.lightTop,
                                        if (dark) p.darkMid else p.lightMid,
                                        if (dark) p.darkBottom else p.lightBottom
                                    ))
                                ).padding(12.dp)
                            ) {
                                val txtColor = if (dark) Color.White else Color.Black
                                Column {
                                    Text("Good morning", color = txtColor.copy(alpha = 0.6f), fontSize = 10.sp)
                                    Text("Hey, User!", color = txtColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Row {
                                        repeat(3) {
                                            Box(
                                                Modifier.size(40.dp, 48.dp)
                                                    .padding(end = 6.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(txtColor.copy(alpha = 0.15f))
                                            )
                                        }
                                    }
                                }

                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(p.glowColor.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Background mode selector ─────────────────────────
                Text("Background Mode", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                BackgroundMode.values().forEach { mode ->
                    val isSelected = themeState.mode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { themeManager.setMode(mode) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, themeState.activePalette.accent) else null
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { themeManager.setMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = themeState.activePalette.accent,
                                    unselectedColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (mode) {
                                        BackgroundMode.DYNAMIC_LIGHT -> "☀️ Dynamic Light"
                                        BackgroundMode.DYNAMIC_DARK -> "🌙 Dynamic Dark"
                                        BackgroundMode.STATIC_PRESET -> "🎯 Static Preset"
                                        BackgroundMode.CUSTOM_PHOTO -> "📷 Custom Photo"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    when (mode) {
                                        BackgroundMode.DYNAMIC_LIGHT -> "Vivid pastel mood colors that shift automatically"
                                        BackgroundMode.DYNAMIC_DARK -> "Deep rich mood colors on dark backgrounds"
                                        BackgroundMode.STATIC_PRESET -> "Pick a fixed mood color scheme"
                                        BackgroundMode.CUSTOM_PHOTO -> "Upload a photo as your background"
                                    },
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, null, tint = themeState.activePalette.accent)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Static preset picker (shown when STATIC_PRESET selected) ──
                if (themeState.mode == BackgroundMode.STATIC_PRESET) {
                    Text("Choose Your Vibe", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Pick a mood color that stays forever", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(MoodPalettes.allPresets()) { (name, palette) ->
                            val isSelected = themeState.staticPreset.equals(name, ignoreCase = true)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { themeManager.setStaticPreset(name.lowercase()) }
                                    .width(80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(palette.lightTop, palette.lightMid)))
                                        .then(
                                            if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    name, fontSize = 12.sp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ── Dynamic mode info ────────────────────────────────
                if (themeState.isDynamic) {
                    Text("How It Works", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            DynamicInfoRow("🎵", "Music mood", "Colors shift based on what you're listening to")
                            Spacer(Modifier.height(10.dp))
                            DynamicInfoRow("💬", "Chat mood", "Typing mood words in chat changes the vibe")
                            Spacer(Modifier.height(10.dp))
                            DynamicInfoRow("🐱", "Pet mood", "Your pet's mood syncs the colors too")
                            Spacer(Modifier.height(10.dp))
                            DynamicInfoRow("⏰", "Time of day", "Calm at night, bright in the morning")
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ── Transition speed ─────────────────────────────────
                Text("Transition Speed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("How fast colors change", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐢", fontSize = 20.sp)
                    Slider(
                        value = themeState.transitionSpeed.toFloat(),
                        onValueChange = { themeManager.setTransitionSpeed(it.toInt()) },
                        valueRange = 500f..5000f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = themeState.activePalette.accent,
                            activeTrackColor = themeState.activePalette.accent
                        )
                    )
                    Text("🐇", fontSize = 20.sp)
                }
                Text(
                    "${themeState.transitionSpeed / 1000.0}s",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ── Mood color reference ─────────────────────────────
                Text("Mood Colors", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("How each mood looks", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))

                MoodPalettes.allPresets().chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (name, palette) ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Brush.horizontalGradient(listOf(palette.lightTop, palette.lightMid)))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        // Fill empty space if odd number
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DynamicInfoRow(emoji: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}