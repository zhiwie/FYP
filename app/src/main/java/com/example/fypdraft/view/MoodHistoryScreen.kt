package com.example.fypdraft.view

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.data.repository.MoodAnalytics
import com.example.fypdraft.data.repository.MoodAnalyticsEntry
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.AnalyticsTimeRange
import com.example.fypdraft.viewmodel.MoodHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

private val MOOD_COLORS = mapOf(
    "happy"     to Color(0xFFFF9800), "sad"      to Color(0xFF5C6BC0),
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
    val bgBrush       = animatedMoodBrushLight(themeState)

    val isDark       = themeState.isDark
    val primaryText  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val cardBg       = if (isDark) Color(0xFF1E1E2E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.90f)
    val summaryCardBg = if (isDark) Color(0xFF2A2A3E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f)
    val chipBg       = if (isDark) Color(0xFF2A2A3E) else Color(0xFFF0F0F8)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Mood Analytics", fontWeight = FontWeight.Bold, color = primaryText)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = primaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = primaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(bgBrush).padding(padding)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading mood data...", color = secondaryText)
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = secondaryText, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
                analytics == null || analytics!!.totalEntries == 0 ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
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
                else -> {
                    val data = analytics!!
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        TimeRangeSelector(selectedRange, chipBg, primaryText) { viewModel.setTimeRange(it) }
                        Spacer(Modifier.height(16.dp))
                        SummaryRow(data, summaryCardBg, primaryText, secondaryText)
                        Spacer(Modifier.height(16.dp))
                        AnalyticsCard("Mood Distribution", "📊", cardBg, primaryText) {
                            MoodDistributionChart(data.moodDistribution, data.totalEntries, primaryText)
                        }
                        Spacer(Modifier.height(12.dp))
                        AnalyticsCard("Mood Timeline", "📈", cardBg, primaryText) {
                            MoodTimelineChart(data.moodTimeline)
                        }
                        Spacer(Modifier.height(12.dp))
                        AnalyticsCard("Mood by Time of Day", "🕐", cardBg, primaryText) {
                            MoodByHourChart(data.moodByHour, primaryText, secondaryText)
                        }
                        Spacer(Modifier.height(12.dp))
                        AnalyticsCard("Mood by Day of Week", "📅", cardBg, primaryText) {
                            MoodByDayOfWeekChart(data.moodByDayOfWeek, secondaryText)
                        }
                        Spacer(Modifier.height(12.dp))
                        AnalyticsCard("Insights & Streaks", "💡", cardBg, primaryText) {
                            InsightsSection(data, primaryText)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selected: AnalyticsTimeRange,
    chipBg: Color,
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
                label    = { Text(range.label, fontSize = 13.sp) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF6A5ACD),
                    selectedLabelColor     = Color.White,
                    containerColor         = chipBg,
                    labelColor             = textColor
                )
            )
        }
    }
}

@Composable
private fun SummaryRow(
    data: MoodAnalytics,
    cardBg: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(
            MOOD_EMOJIS[data.dominantMood] ?: "🎵", "Top Mood",
            data.dominantMood.replaceFirstChar { it.uppercase() },
            cardBg, primaryText, secondaryText, Modifier.weight(1f)
        )
        SummaryCard("📝", "Entries", "${data.totalEntries}", cardBg, primaryText, secondaryText, Modifier.weight(1f))
        SummaryCard(
            when (data.recentTrend) { "improving" -> "📈"; "declining" -> "📉"; else -> "➡️" },
            "Trend", data.recentTrend.replaceFirstChar { it.uppercase() },
            cardBg, primaryText, secondaryText, Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    emoji: String, label: String, value: String,
    cardBg: Color, primaryText: Color, secondaryText: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier,
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 11.sp, color = secondaryText)
        }
    }
}

@Composable
private fun AnalyticsCard(
    title: String, emoji: String,
    cardBg: Color, primaryText: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("$emoji $title", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryText)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MoodDistributionChart(
    distribution: Map<String, Int>,
    total: Int,
    primaryText: Color
) {
    if (distribution.isEmpty()) { Text("No data", color = primaryText.copy(alpha = 0.5f)); return }
    val maxCount = distribution.values.maxOrNull() ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        distribution.forEach { (mood, count) ->
            val frac  = count.toFloat() / maxCount
            val pct   = if (total > 0) (count * 100f / total).toInt() else 0
            val color = MOOD_COLORS[mood] ?: Color.Gray
            val animW by animateFloatAsState(frac, tween(600, easing = FastOutSlowInEasing), label = "bar_$mood")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(MOOD_EMOJIS[mood] ?: "🎵", fontSize = 18.sp, modifier = Modifier.width(28.dp))
                Text(
                    mood.replaceFirstChar { it.uppercase() }, fontSize = 13.sp,
                    modifier = Modifier.width(72.dp), fontWeight = FontWeight.Medium,
                    color = primaryText
                )
                Box(Modifier.weight(1f).height(20.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)))
                    Box(Modifier.fillMaxHeight().fillMaxWidth(animW.coerceAtLeast(0.02f)).clip(RoundedCornerShape(10.dp)).background(color))
                }
                Spacer(Modifier.width(8.dp))
                Text("$pct%", fontSize = 12.sp, color = primaryText.copy(alpha = 0.6f), modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
private fun MoodTimelineChart(entries: List<MoodAnalyticsEntry>) {
    if (entries.isEmpty()) { Text("No data", color = Color.Gray); return }
    val daily = entries.groupBy { it.dateString }
        .mapValues { (_, e) -> e.groupingBy { it.mood }.eachCount().maxByOrNull { it.value }?.key ?: "neutral" }
        .toSortedMap()
    val moodValues = mapOf(
        "happy" to 5, "energetic" to 4, "focused" to 4, "romantic" to 4,
        "calm" to 3, "neutral" to 3, "nostalgic" to 2, "tired" to 2,
        "anxious" to 1, "sad" to 1, "angry" to 0
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.height(140.dp).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("😊", fontSize = 12.sp); Text("😌", fontSize = 12.sp); Text("😢", fontSize = 12.sp)
        }
        Canvas(Modifier.height(140.dp).width((daily.size * 36).coerceAtLeast(200).dp)) {
            val w = size.width; val h = size.height; val pad = 16f; val chartH = h - pad * 2
            val spacing = if (daily.size > 1) (w - pad * 2) / (daily.size - 1) else w / 2
            for (i in 0..5) {
                val y = pad + chartH * (1f - i / 5f)
                drawLine(Color.Gray.copy(alpha = 0.15f), Offset(pad, y), Offset(w - pad, y), 1f)
            }
            val points = daily.entries.toList().mapIndexed { idx, (_, mood) ->
                val x = pad + idx * spacing
                val v = moodValues[mood] ?: 3
                Offset(x, pad + chartH * (1f - v / 5f)) to mood
            }
            if (points.size > 1) {
                val path = Path(); path.moveTo(points[0].first.x, points[0].first.y)
                for (i in 1 until points.size) {
                    val p = points[i - 1].first; val c = points[i].first; val mx = (p.x + c.x) / 2
                    path.cubicTo(mx, p.y, mx, c.y, c.x, c.y)
                }
                drawPath(path, Color(0xFF6A5ACD).copy(alpha = 0.4f), style = Stroke(2.5f, cap = StrokeCap.Round))
            }
            points.forEach { (off, mood) ->
                val c = MOOD_COLORS[mood] ?: Color.Gray
                drawCircle(c.copy(alpha = 0.3f), 10f, off); drawCircle(c, 6f, off)
                drawCircle(Color.White.copy(alpha = 0.7f), 2.5f, off)
            }
        }
    }
}

@Composable
private fun MoodByHourChart(
    moodByHour: Map<Int, Map<String, Int>>,
    primaryText: Color,
    secondaryText: Color
) {
    if (moodByHour.isEmpty()) { Text("No data", color = secondaryText); return }
    data class TB(val label: String, val emoji: String, val hours: IntRange)
    val blocks = listOf(
        TB("Morning",   "🌅", 5..11),
        TB("Afternoon", "☀️", 12..16),
        TB("Evening",   "🌆", 17..20),
        TB("Night",     "🌙", 21..23)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            val moods = mutableMapOf<String, Int>()
            for (h in b.hours) moodByHour[h]?.forEach { (m, c) -> moods[m] = (moods[m] ?: 0) + c }
            if (b.label == "Night") for (h in 0..4) moodByHour[h]?.forEach { (m, c) -> moods[m] = (moods[m] ?: 0) + c }
            if (moods.isEmpty()) return@forEach
            val dom      = moods.maxByOrNull { it.value }?.key ?: "neutral"
            val domColor = MOOD_COLORS[dom] ?: Color.Gray
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = domColor.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, domColor.copy(alpha = 0.3f))
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.emoji, fontSize = 20.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryText)
                        Text("Usually $dom (${moods.values.sum()} entries)", fontSize = 11.sp, color = secondaryText)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        moods.entries.sortedByDescending { it.value }.take(3).forEach { (m, _) ->
                            Text(MOOD_EMOJIS[m] ?: "🎵", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodByDayOfWeekChart(
    moodByDow: Map<Int, Map<String, Int>>,
    secondaryText: Color
) {
    if (moodByDow.isEmpty()) { Text("No data", color = secondaryText); return }
    val days = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    val maxTotal = days.maxOfOrNull { moodByDow[it]?.values?.sum() ?: 0 }?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height
        val bw = w / (days.size * 2f); val sp = bw
        days.forEachIndexed { idx, day ->
            val moods = moodByDow[day] ?: return@forEachIndexed
            val total = moods.values.sum()
            val barH  = (total.toFloat() / maxTotal) * (h - 30f)
            val x     = idx * (bw + sp) + sp / 2; var yOff = h - 20f
            moods.entries.sortedByDescending { it.value }.forEach { (mood, count) ->
                val segH = (count.toFloat() / total) * barH
                drawRoundRect(MOOD_COLORS[mood] ?: Color.Gray, Offset(x, yOff - segH), Size(bw, segH), CornerRadius(4f))
                yOff -= segH
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        days.forEach {
            Text(
                DOW_LABELS[it] ?: "", fontSize = 11.sp, color = secondaryText,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InsightsSection(data: MoodAnalytics, primaryText: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data.streaks.currentStreak > 0)
            InsightChip("🔥", "${data.streaks.currentStreak}-day logging streak!", Color(0xFFFF9800), primaryText)
        if (data.streaks.longestStreak > 1)
            InsightChip("🏆", "Longest streak: ${data.streaks.longestStreak} days", Color(0xFFFFD700), primaryText)
        data.streaks.currentMoodStreak?.let { (m, c) ->
            if (c > 2) InsightChip(MOOD_EMOJIS[m] ?: "🎵", "Feeling $m for $c entries in a row", MOOD_COLORS[m] ?: Color.Gray, primaryText)
        }
        InsightChip("📊", "Average %.1f mood logs per day".format(data.averageMoodsPerDay), Color(0xFF6A5ACD), primaryText)
        when (data.recentTrend) {
            "improving" -> InsightChip("📈", "Your mood has been trending positively this week!", Color(0xFF00C853), primaryText)
            "declining" -> InsightChip("💙", "Your mood dipped this week. Consider activities that lift your spirits.", Color(0xFF5C6BC0), primaryText)
            else        -> InsightChip("➡️", "Your mood has been stable recently.", Color(0xFF78909C), primaryText)
        }
        val topPct = data.moodDistribution[data.dominantMood]?.let { (it * 100f / data.totalEntries).toInt() } ?: 0
        InsightChip(
            MOOD_EMOJIS[data.dominantMood] ?: "🎵",
            "You feel ${data.dominantMood} $topPct% of the time — your signature vibe!",
            MOOD_COLORS[data.dominantMood] ?: Color.Gray,
            primaryText
        )
    }
}

@Composable
private fun InsightChip(emoji: String, text: String, color: Color, primaryText: Color) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 13.sp, color = primaryText, modifier = Modifier.weight(1f))
        }
    }
}