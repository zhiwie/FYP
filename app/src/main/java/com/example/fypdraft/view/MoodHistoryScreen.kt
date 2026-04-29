package com.example.fypdraft.view

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.data.repository.MoodAnalytics
import com.example.fypdraft.data.repository.MoodAnalyticsEntry
import com.example.fypdraft.ui.theme.*
import com.example.fypdraft.viewmodel.AnalyticsTimeRange
import com.example.fypdraft.viewmodel.MoodHistoryViewModel
import java.util.*

// ─── Mood palette maps ────────────────────────────────────────────────────────

private val MOOD_COLORS = mapOf(
    "happy"     to Color(0xFFFF9800), "sad"       to Color(0xFF5C6BC0),
    "calm"      to Color(0xFF26C6DA), "energetic" to Color(0xFFFF416C),
    "focused"   to Color(0xFF00C853), "tired"     to Color(0xFF78909C),
    "romantic"  to Color(0xFFE91E63), "angry"     to Color(0xFFFF5722),
    "anxious"   to Color(0xFFFF9800), "nostalgic" to Color(0xFF9C27B0),
    "neutral"   to Color(0xFFAB47BC)
)

private val MOOD_EMOJIS = mapOf(
    "happy" to "😊", "sad" to "😢", "calm" to "😌", "energetic" to "⚡",
    "focused" to "🎯", "tired" to "😴", "romantic" to "💕", "angry" to "😤",
    "anxious" to "😰", "nostalgic" to "💭", "neutral" to "🎵"
)

private val DOW_LABELS = mapOf(
    Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat"
)

// ─── Share helper ─────────────────────────────────────────────────────────────

private fun buildShareText(data: MoodAnalytics, range: AnalyticsTimeRange): String {
    val dominantEmoji = MOOD_EMOJIS[data.dominantMood] ?: "🎵"
    val trendArrow = when (data.recentTrend) { "improving" -> "📈"; "declining" -> "📉"; else -> "➡️" }
    val topMoods = data.moodDistribution.entries.sortedByDescending { it.value }.take(3)
        .joinToString("  ") { (mood, count) ->
            val pct = if (data.totalEntries > 0) (count * 100f / data.totalEntries).toInt() else 0
            "${MOOD_EMOJIS[mood] ?: "🎵"} ${mood.replaceFirstChar { it.uppercase() }} $pct%"
        }
    val streakLine = when {
        data.streaks.currentStreak > 1 -> "🔥 ${data.streaks.currentStreak}-day logging streak!"
        data.streaks.longestStreak > 1  -> "🏆 Personal best: ${data.streaks.longestStreak}-day streak"
        else -> ""
    }
    val trendLine = when (data.recentTrend) {
        "improving" -> "Things have been looking up lately ✨"
        "declining" -> "Going through a tough patch — but I'm aware of it 💙"
        else        -> "Keeping a steady vibe 😌"
    }
    return buildString {
        appendLine("📊 My MoodSync Report — Last ${range.label}")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("$dominantEmoji Dominant mood: ${data.dominantMood.replaceFirstChar { it.uppercase() }}")
        appendLine("$trendArrow Trend: ${data.recentTrend.replaceFirstChar { it.uppercase() }}")
        appendLine("📝 Total check-ins: ${data.totalEntries}")
        appendLine()
        appendLine("🎭 Mood breakdown:")
        appendLine(topMoods)
        if (streakLine.isNotBlank()) { appendLine(); appendLine(streakLine) }
        appendLine()
        appendLine(trendLine)
        appendLine()
        appendLine("Tracked with MoodSync 🎧✨")
    }.trim()
}

// ─── Mood-reactive background brush ──────────────────────────────────────────
// Reads from the dominant mood IN THE DATA, not the global themeState mood,
// so the background reflects what the stats actually say.

@Composable
private fun moodDataBrush(dominantMood: String, themeState: AppThemeState): Brush {
    val palette = MoodPalettes.forMood(dominantMood)
    val speed   = themeState.transitionSpeed
    val dark    = themeState.isDark

    val c1 by animateColorAsState(if (dark) palette.darkTop    else palette.lightTop,    tween(speed), label = "bg1")
    val c2 by animateColorAsState(if (dark) palette.darkMid    else palette.lightMid,    tween(speed), label = "bg2")
    val c3 by animateColorAsState(if (dark) palette.darkBottom else palette.lightBottom, tween(speed), label = "bg3")

    return Brush.verticalGradient(listOf(c1, c2, c3))
}

@Composable
private fun moodAccentColor(dominantMood: String, themeState: AppThemeState): Color {
    val c by animateColorAsState(MoodPalettes.forMood(dominantMood).accent, tween(themeState.transitionSpeed), label = "acc")
    return c
}

// ─── Main screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodHistoryScreen(
    viewModel: MoodHistoryViewModel,
    themeState: AppThemeState = AppThemeState(),
    onBack: () -> Unit = {}
) {
    val analytics     by viewModel.analytics.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val error         by viewModel.error.collectAsState()
    val context       = LocalContext.current

    // Background driven by dominant mood in the DATA (not chat state)
    val dominantMood  = analytics?.dominantMood ?: themeState.currentMood
    val bgBrush       = moodDataBrush(dominantMood, themeState)
    val accentColor   = moodAccentColor(dominantMood, themeState)
    val palette       = MoodPalettes.forMood(dominantMood)

    val isDark        = themeState.isDark
    val primaryText   by animateColorAsState(if (isDark) palette.darkText    else palette.lightText,    tween(themeState.transitionSpeed), label = "pt")
    val secondaryText by animateColorAsState(if (isDark) palette.darkTextSub else palette.lightTextSub, tween(themeState.transitionSpeed), label = "st")
    val cardBg        by animateColorAsState(
        if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.82f),
        tween(themeState.transitionSpeed), label = "cb"
    )
    val chipBg        by animateColorAsState(accentColor.copy(alpha = 0.15f), tween(themeState.transitionSpeed), label = "ch")

    val onShare: () -> Unit = {
        val data = analytics
        if (data != null && data.totalEntries > 0) {
            val text = buildShareText(data, selectedRange)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "My MoodSync Report")
            }
            context.startActivity(Intent.createChooser(intent, "Share your mood stats via…"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Analytics", fontWeight = FontWeight.Bold, color = primaryText) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = primaryText) }
                },
                actions = {
                    val hasData = analytics != null && analytics!!.totalEntries > 0
                    if (hasData) {
                        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = primaryText) }
                    }
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = primaryText) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(bgBrush).padding(padding)) {
            when {
                isLoading -> LoadingState(secondaryText)
                error != null -> ErrorState(error!!, secondaryText) { viewModel.refresh() }
                analytics == null || analytics!!.totalEntries == 0 -> EmptyState(primaryText, secondaryText)
                else -> {
                    val data = analytics!!
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        // Time range chips
                        TimeRangeSelector(selectedRange, chipBg, accentColor, primaryText) { viewModel.setTimeRange(it) }
                        Spacer(Modifier.height(20.dp))

                        // Hero banner — dominant mood at a glance
                        HeroMoodBanner(data, accentColor, primaryText, secondaryText, cardBg)
                        Spacer(Modifier.height(12.dp))

                        // 3-number quick stats
                        QuickStatsRow(data, accentColor, cardBg, primaryText, secondaryText)
                        Spacer(Modifier.height(24.dp))

                        // Mood breakdown
                        SectionLabel("Mood Breakdown", "📊", primaryText)
                        Spacer(Modifier.height(10.dp))
                        MoodBreakdownList(data.moodDistribution, data.totalEntries, accentColor, cardBg, primaryText, secondaryText)
                        Spacer(Modifier.height(24.dp))

                        // Timeline
                        SectionLabel("Mood Timeline", "📈", primaryText)
                        Spacer(Modifier.height(10.dp))
                        GlassCard(cardBg) { MoodTimelineChart(data.moodTimeline, accentColor) }
                        Spacer(Modifier.height(24.dp))

                        // Time of day
                        SectionLabel("Time of Day", "🕐", primaryText)
                        Spacer(Modifier.height(10.dp))
                        MoodByHourChart(data.moodByHour, accentColor, cardBg, primaryText, secondaryText)
                        Spacer(Modifier.height(24.dp))

                        // Day of week
                        SectionLabel("Day of Week", "📅", primaryText)
                        Spacer(Modifier.height(10.dp))
                        GlassCard(cardBg) {
                            MoodByDayOfWeekChart(data.moodByDayOfWeek, accentColor, secondaryText, primaryText)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// ─── State screens ────────────────────────────────────────────────────────────

@Composable
private fun LoadingState(secondaryText: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading mood data…", color = secondaryText)
        }
    }
}

@Composable
private fun ErrorState(msg: String, secondaryText: Color, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(msg, color = secondaryText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyState(primaryText: Color, secondaryText: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("📊", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("No mood data yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)
            Spacer(Modifier.height(8.dp))
            Text(
                "Start logging moods through the mascot chat or mood picker to see analytics here!",
                color = secondaryText, textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Time range selector ──────────────────────────────────────────────────────

@Composable
private fun TimeRangeSelector(
    selected: AnalyticsTimeRange,
    chipBg: Color,
    accentColor: Color,
    textColor: Color,
    onSelect: (AnalyticsTimeRange) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalyticsTimeRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick  = { onSelect(range) },
                label    = {
                    Text(
                        range.label, fontSize = 13.sp,
                        fontWeight = if (range == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor,
                    selectedLabelColor     = Color.White,
                    containerColor         = chipBg,
                    labelColor             = textColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = range == selected,
                    selectedBorderColor = accentColor,
                    borderColor = accentColor.copy(alpha = 0.3f),
                    borderWidth = 1.dp, selectedBorderWidth = 0.dp
                ),
                shape = RoundedCornerShape(50)
            )
        }
    }
}

// ─── Hero banner ──────────────────────────────────────────────────────────────

@Composable
private fun HeroMoodBanner(
    data: MoodAnalytics,
    accentColor: Color,
    primaryText: Color,
    secondaryText: Color,
    cardBg: Color
) {
    val dominantEmoji = MOOD_EMOJIS[data.dominantMood] ?: "🎵"
    val topPct = data.moodDistribution[data.dominantMood]
        ?.let { (it * 100f / data.totalEntries).toInt() } ?: 0

    val infiniteTransition = rememberInfiniteTransition(label = "hero_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .drawBehind {
                drawCircle(
                    accentColor.copy(alpha = glowAlpha),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.12f, size.height * 0.4f)
                )
            }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(dominantEmoji, fontSize = 34.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Your vibe this period",
                    fontSize = 11.sp, color = secondaryText,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    data.dominantMood.replaceFirstChar { it.uppercase() },
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = accentColor
                )
                Text(
                    "$topPct% of the time · ${data.totalEntries} check-ins",
                    fontSize = 12.sp, color = secondaryText
                )
                Spacer(Modifier.height(8.dp))
                val trendEmoji = when (data.recentTrend) { "improving" -> "📈"; "declining" -> "📉"; else -> "➡️" }
                val trendLabel = when (data.recentTrend) { "improving" -> "Trending up"; "declining" -> "Trending down"; else -> "Stable" }
                val trendColor = when (data.recentTrend) { "improving" -> Color(0xFF00C853); "declining" -> Color(0xFF5C6BC0); else -> Color(0xFF78909C) }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(trendColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("$trendEmoji $trendLabel", fontSize = 12.sp, color = trendColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Quick 3-stat row ─────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(
    data: MoodAnalytics,
    accentColor: Color,
    cardBg: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStatCard("🔥", "${data.streaks.currentStreak}", "day streak",  accentColor, cardBg, primaryText, secondaryText, Modifier.weight(1f))
        MiniStatCard("🏆", "${data.streaks.longestStreak}", "best streak", accentColor, cardBg, primaryText, secondaryText, Modifier.weight(1f))
        MiniStatCard("📅", "%.1f".format(data.averageMoodsPerDay), "logs/day",   accentColor, cardBg, primaryText, secondaryText, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(
    emoji: String, value: String, label: String,
    accentColor: Color, cardBg: Color,
    primaryText: Color, secondaryText: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(cardBg)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = accentColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 11.sp, color = secondaryText, textAlign = TextAlign.Center)
        }
    }
}

// ─── Mood breakdown pill list ─────────────────────────────────────────────────

@Composable
private fun MoodBreakdownList(
    distribution: Map<String, Int>,
    total: Int,
    accentColor: Color,
    cardBg: Color,
    primaryText: Color,
    secondaryText: Color
) {
    if (distribution.isEmpty()) return

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        distribution.entries.sortedByDescending { it.value }.forEachIndexed { idx, (mood, count) ->
            val pct   = if (total > 0) count * 100f / total else 0f
            val color = MOOD_COLORS[mood] ?: accentColor
            val animW by animateFloatAsState(
                pct / 100f, tween(700, delayMillis = idx * 60, easing = FastOutSlowInEasing),
                label = "bar_$mood"
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(MOOD_EMOJIS[mood] ?: "🎵", fontSize = 20.sp, modifier = Modifier.width(30.dp))
                Text(
                    mood.replaceFirstChar { it.uppercase() },
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = primaryText, modifier = Modifier.width(74.dp)
                )
                // Thin progress bar
                Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animW.coerceAtLeast(0.03f)).clip(RoundedCornerShape(50)).background(color))
                }
                Spacer(Modifier.width(8.dp))
                Text("${pct.toInt()}%", fontSize = 12.sp, color = secondaryText, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                // Count badge
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("$count", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Section label ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String, emoji: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ─── Glass card wrapper ───────────────────────────────────────────────────────

@Composable
private fun GlassCard(cardBg: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardBg).padding(16.dp)
    ) { content() }
}

// ─── Timeline chart ───────────────────────────────────────────────────────────

@Composable
private fun MoodTimelineChart(entries: List<MoodAnalyticsEntry>, accentColor: Color) {
    if (entries.isEmpty()) { Text("No data yet", color = Color.Gray); return }

    val daily = entries.groupBy { it.dateString }
        .mapValues { (_, e) -> e.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key ?: "neutral" }
        .toSortedMap()

    val moodValues = mapOf(
        "happy" to 5, "energetic" to 4, "focused" to 4, "romantic" to 4,
        "calm" to 3, "neutral" to 3, "nostalgic" to 2, "tired" to 2,
        "anxious" to 1, "sad" to 1, "angry" to 0
    )

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.height(130.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("😊", "😌", "😢").forEach { Text(it, fontSize = 11.sp) }
        }
        Canvas(Modifier.height(130.dp).width((daily.size * 34).coerceAtLeast(240).dp)) {
            val w = size.width; val h = size.height
            val pad = 12f; val chartH = h - pad * 2
            val spacing = if (daily.size > 1) (w - pad * 2) / (daily.size - 1) else w / 2

            for (i in 0..4) {
                val y = pad + chartH * (1f - i / 4f)
                drawLine(Color.Gray.copy(alpha = 0.12f), Offset(pad, y), Offset(w - pad, y), 1f)
            }

            val points = daily.entries.toList().mapIndexed { idx, (_, mood) ->
                val x = pad + idx * spacing
                val v = moodValues[mood] ?: 3
                Offset(x, pad + chartH * (1f - v / 5f)) to mood
            }

            if (points.size > 1) {
                // Filled area
                val fillPath = Path()
                fillPath.moveTo(points.first().first.x, h)
                fillPath.lineTo(points.first().first.x, points.first().first.y)
                for (i in 1 until points.size) {
                    val p = points[i - 1].first; val c = points[i].first; val mx = (p.x + c.x) / 2
                    fillPath.cubicTo(mx, p.y, mx, c.y, c.x, c.y)
                }
                fillPath.lineTo(points.last().first.x, h); fillPath.close()
                drawPath(fillPath, Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.22f), Color.Transparent)))

                // Line
                val linePath = Path()
                linePath.moveTo(points[0].first.x, points[0].first.y)
                for (i in 1 until points.size) {
                    val p = points[i - 1].first; val c = points[i].first; val mx = (p.x + c.x) / 2
                    linePath.cubicTo(mx, p.y, mx, c.y, c.x, c.y)
                }
                drawPath(linePath, accentColor.copy(alpha = 0.8f), style = Stroke(2.5f, cap = StrokeCap.Round))
            }

            points.forEach { (off, mood) ->
                val c = MOOD_COLORS[mood] ?: Color.Gray
                drawCircle(c.copy(alpha = 0.28f), 9f, off)
                drawCircle(c, 5f, off)
                drawCircle(Color.White.copy(alpha = 0.8f), 2f, off)
            }
        }
    }
}

// ─── Mood by hour ─────────────────────────────────────────────────────────────

@Composable
private fun MoodByHourChart(
    moodByHour: Map<Int, Map<String, Int>>,
    accentColor: Color,
    cardBg: Color,
    primaryText: Color,
    secondaryText: Color
) {
    if (moodByHour.isEmpty()) { Text("No data", color = secondaryText); return }

    data class TimeBlock(val label: String, val emoji: String, val hours: IntRange)
    val blocks = listOf(
        TimeBlock("Morning",   "🌅", 5..11),
        TimeBlock("Afternoon", "☀️", 12..16),
        TimeBlock("Evening",   "🌆", 17..20),
        TimeBlock("Night",     "🌙", 21..23)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            val moods = mutableMapOf<String, Int>()
            for (h in b.hours) moodByHour[h]?.forEach { (m, c) -> moods[m] = (moods[m] ?: 0) + c }
            if (b.label == "Night") for (h in 0..4) moodByHour[h]?.forEach { (m, c) -> moods[m] = (moods[m] ?: 0) + c }
            if (moods.isEmpty()) return@forEach

            val dom      = moods.maxByOrNull { it.value }?.key ?: "neutral"
            val domColor = MOOD_COLORS[dom] ?: accentColor

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(b.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Text("Mostly $dom", fontSize = 11.sp, color = secondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    moods.entries.sortedByDescending { it.value }.take(3).forEach { (m, _) ->
                        Text(MOOD_EMOJIS[m] ?: "🎵", fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(domColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("${moods.values.sum()}", fontSize = 11.sp, color = domColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Mood by day of week ──────────────────────────────────────────────────────

@Composable
private fun MoodByDayOfWeekChart(
    moodByDow: Map<Int, Map<String, Int>>,
    accentColor: Color,
    secondaryText: Color,
    primaryText: Color
) {
    if (moodByDow.isEmpty()) { Text("No data", color = secondaryText); return }

    val days = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    val maxTotal = days.maxOfOrNull { moodByDow[it]?.values?.sum() ?: 0 }?.coerceAtLeast(1) ?: 1

    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width; val h = size.height
            val bw = w / (days.size * 2f); val sp = bw
            days.forEachIndexed { idx, day ->
                val moods = moodByDow[day] ?: return@forEachIndexed
                val total = moods.values.sum()
                val barH  = (total.toFloat() / maxTotal) * (h - 24f)
                val x     = idx * (bw + sp) + sp / 2
                var yOff  = h - 14f
                moods.entries.sortedByDescending { it.value }.forEach { (mood, count) ->
                    val segH = (count.toFloat() / total) * barH
                    drawRoundRect(MOOD_COLORS[mood] ?: Color.Gray, Offset(x, yOff - segH), Size(bw, segH), CornerRadius(4f))
                    yOff -= segH
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            days.forEach {
                Text(DOW_LABELS[it] ?: "", fontSize = 10.sp, color = secondaryText,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─── Insights ─────────────────────────────────────────────────────────────────

@Composable
private fun InsightsSection(
    data: MoodAnalytics,
    accentColor: Color,
    cardBg: Color,
    primaryText: Color
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (data.streaks.currentStreak > 0)
            InsightRow("🔥", "${data.streaks.currentStreak}-day logging streak!", Color(0xFFFF9800), primaryText)
        if (data.streaks.longestStreak > 1)
            InsightRow("🏆", "Longest streak: ${data.streaks.longestStreak} days", Color(0xFFFFD700), primaryText)
        data.streaks.currentMoodStreak?.let { (m, c) ->
            if (c > 2) InsightRow(MOOD_EMOJIS[m] ?: "🎵", "Feeling $m for $c check-ins in a row", MOOD_COLORS[m] ?: accentColor, primaryText)
        }
        InsightRow("📊", "Average %.1f mood logs per day".format(data.averageMoodsPerDay), accentColor, primaryText)
        when (data.recentTrend) {
            "improving" -> InsightRow("📈", "Your mood has been trending positively!", Color(0xFF00C853), primaryText)
            "declining" -> InsightRow("💙", "Your mood dipped — consider some self-care.", Color(0xFF5C6BC0), primaryText)
            else        -> InsightRow("➡️", "Your mood has been stable recently.", Color(0xFF78909C), primaryText)
        }
        val topPct = data.moodDistribution[data.dominantMood]?.let { (it * 100f / data.totalEntries).toInt() } ?: 0
        InsightRow(
            MOOD_EMOJIS[data.dominantMood] ?: "🎵",
            "You feel ${data.dominantMood} $topPct% of the time — your signature vibe!",
            MOOD_COLORS[data.dominantMood] ?: accentColor,
            primaryText
        )
    }
}

@Composable
private fun InsightRow(emoji: String, text: String, color: Color, primaryText: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 17.sp) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = primaryText, modifier = Modifier.weight(1f))
    }
}

// ─── Share banner ─────────────────────────────────────────────────────────────

@Composable
private fun ShareBanner(range: AnalyticsTimeRange, accentColor: Color, onShare: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onShare)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share your ${range.label} report", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                Text("Send to WhatsApp, Instagram & more 📤", fontSize = 12.sp, color = accentColor.copy(alpha = 0.7f))
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}