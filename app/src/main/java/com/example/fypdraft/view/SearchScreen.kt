package com.example.fypdraft.view

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.SearchHistoryRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MoodCategory(
    val label: String, val emoji: String, val query: String,
    val gradientColors: List<Color>, val glowColor: Color,
    val animEmoji: String = "", val contextTag: String = ""
)
private data class XAIChip(val text: String, val icon: String = "✨")

private fun detectMoodFromQuery(query: String): String? {
    val q = query.lowercase()
    return when {
        q.contains("stress") || q.contains("anxious") || q.contains("nervous") || q.contains("overwhelm") -> "anxious"
        q.contains("sad") || q.contains("depress") || q.contains("cry") || q.contains("heartbreak") -> "sad"
        q.contains("happy") || q.contains("joy") || q.contains("excit") || q.contains("celebrat") -> "happy"
        q.contains("anger") || q.contains("angry") || q.contains("mad") || q.contains("furious") || q.contains("rage") -> "angry"
        q.contains("focus") || q.contains("study") || q.contains("concentrat") || q.contains("work") -> "focused"
        q.contains("relax") || q.contains("chill") || q.contains("calm") || q.contains("peace") -> "calm"
        q.contains("sleep") || q.contains("tired") || q.contains("rest") || q.contains("sleepy") -> "tired"
        q.contains("love") || q.contains("romantic") || q.contains("date") -> "romantic"
        q.contains("energy") || q.contains("pump") || q.contains("workout") || q.contains("hype") -> "energetic"
        else -> null
    }
}

private fun moodToSearchQuery(mood: String): String = when (mood) {
    "anxious"   -> "calming anxiety relief meditation ambient peaceful"
    "sad"       -> "sad emotional ballad comfort heartbreak"
    "happy"     -> "happy uplifting feel good pop sunshine"
    "angry"     -> "aggressive rock metal punk heavy cathartic"
    "focused"   -> "focus study instrumental lofi beats concentration"
    "calm"      -> "chill ambient relaxing peaceful gentle lofi"
    "tired"     -> "sleep ambient lullaby soft gentle"
    "romantic"  -> "romantic love songs r&b smooth slow dance"
    "energetic" -> "energetic workout pump up bass drop hype"
    else        -> mood
}

private fun xaiChipForIndex(index: Int, mood: String, sectionTitle: String = ""): XAIChip? {
    if (index % 5 != 4) return null
    return when {
        mood == "energetic" -> XAIChip("Heart rate match detected ⚡", "⚡")
        mood == "focused"   -> XAIChip("You focus best with steady BPM", "🎯")
        mood == "sad"       -> XAIChip("Matching your emotional state", "💙")
        mood == "calm"      -> XAIChip("Matches your wind-down window", "🌙")
        mood == "happy"     -> XAIChip("Keeps those good vibes rolling", "☀️")
        sectionTitle.isNotEmpty() -> XAIChip("More from: $sectionTitle", "✨")
        else -> XAIChip("Based on your listening pattern", "✨")
    }
}

private fun moodUiAccent(mood: String?): Color = when (mood) {
    "anxious"   -> Color(0xFF26C6DA); "sad" -> Color(0xFF667EEA); "happy" -> Color(0xFFFFB347)
    "angry"     -> Color(0xFFFF416C); "focused" -> Color(0xFF11998E); "calm" -> Color(0xFF89CFF0)
    "tired"     -> Color(0xFF78909C); "romantic" -> Color(0xFFE91E63); "energetic" -> Color(0xFFFF4B2B)
    else        -> Color(0xFF9C27B0)
}

private val SEARCH_PLACEHOLDERS = listOf(
    "How are we feeling today?", "What's the vibe right now?",
    "Type a mood, feeling or song…", "Tell me what's on your mind…", "I'm feeling… (try it!)"
)

@Composable
fun SearchScreen(
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToEmotionChat: ((String?) -> Unit)? = null,
    onBack: () -> Unit = {},
    currentTab: Int = 1
) {
    val isDark        = themeState.isDark
    val primaryText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryText = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val searchBg      = if (isDark) Color(0xFF2A2A3E) else Color.White
    val iconTint      = if (isDark) Color(0xFF9E9EBB) else Color(0xFF666677)

    val scope             = rememberCoroutineScope()
    val searchHistoryRepo = remember { SearchHistoryRepository() }
    val spotifyMusicRepo  = remember(spotifyRepository) { spotifyRepository?.let { SpotifyMusicRepository(it) } }
    val hasToken          = spotifyRepository?.getAccessToken() != null

    var searchQuery    by remember { mutableStateOf("") }
    var searchResults  by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching    by remember { mutableStateOf(false) }
    var recentSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob      by remember { mutableStateOf<Job?>(null) }

    val detectedMood = remember(searchQuery) { detectMoodFromQuery(searchQuery) }
    val accentColor  = remember(detectedMood, themeState.currentMood) {
        moodUiAccent(detectedMood ?: themeState.currentMood.takeIf { it != "neutral" })
    }

    var placeholderIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3500); placeholderIdx = (placeholderIdx + 1) % SEARCH_PLACEHOLDERS.size }
    }

    val showMascotPortal = remember(searchQuery) { searchQuery.length >= 4 && detectedMood != null }

    val categories = remember {
        listOf(
            MoodCategory("Happy",     "😊", "happy uplifting feel good hits",  listOf(Color(0xFFFFD93D), Color(0xFFFF6B35)), Color(0xFFFFD93D), "☀️",  "Played while you were Positive"),
            MoodCategory("Chill",     "😌", "chill lofi relaxing beats",        listOf(Color(0xFF6EC6F5), Color(0xFF4A90D9)), Color(0xFF6EC6F5), "🌊",  "Matches your 9 PM wind-down"),
            MoodCategory("Energetic", "⚡", "energetic workout pump up",        listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)), Color(0xFFFF416C), "🔥",  "Peaks at your workout time"),
            MoodCategory("Sad",       "💙", "sad emotional heartbreak",         listOf(Color(0xFF667EEA), Color(0xFF764BA2)), Color(0xFF667EEA), "🌧️", "Played while you were Reflective"),
            MoodCategory("Focus",     "🎯", "focus study instrumental",         listOf(Color(0xFF11998E), Color(0xFF38EF7D)), Color(0xFF38EF7D), "🧠",  "85% match with your study sessions"),
            MoodCategory("Romance",   "💕", "romantic love songs slow dance",   listOf(Color(0xFFEE9CA7), Color(0xFFFFC3A0)), Color(0xFFEE9CA7), "🌹",  "Your evening favourites"),
            MoodCategory("Throwback", "🕹️","throwback 90s 2000s classics",     listOf(Color(0xFFFFA751), Color(0xFFFFE259)), Color(0xFFFFA751), "📼",  "Nostalgia detected"),
            MoodCategory("Sleep",     "😴", "sleep ambient calming lullaby",    listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)), Color(0xFF4CA1AF), "☁️",  "Matches your 11 PM routine")
        )
    }

    var recentVibes  by remember { mutableStateOf<List<Track>>(emptyList()) }
    var vibesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(hasToken) {
        if (!hasToken || spotifyMusicRepo == null) return@LaunchedEffect
        vibesLoading   = true
        recentSearches = try { searchHistoryRepo.getRecentSearches().map { it.query }.distinct().take(8) } catch (_: Exception) { emptyList() }
        recentVibes    = try {
            spotifyMusicRepo.searchTracks(moodToSearchQuery(themeState.currentMood.ifBlank { "neutral" }), 8).distinctBy { it.id }
        } catch (_: Exception) { emptyList() }
        vibesLoading   = false
    }

    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.length < 2 || spotifyMusicRepo == null) {
            searchResults = emptyList(); isSearching = false; return@LaunchedEffect
        }
        searchJob = scope.launch {
            isSearching = true; delay(400)
            val effectiveQuery = if (detectedMood != null) moodToSearchQuery(detectedMood) else searchQuery
            searchResults = spotifyMusicRepo.searchTracks(effectiveQuery, 20).distinctBy { it.id }
            isSearching   = false
            if (searchQuery.length >= 3) searchHistoryRepo.saveSearch(searchQuery)
        }
    }

    // ── KEY FIX 1: listState hoisted ABOVE Scaffold so it is never recreated ──
    val listState = rememberLazyListState()

    // ── KEY FIX 2: scroll to top whenever the major display state changes ──
    // This prevents measuring stale nodes from the previous layout
    val isShowingResults = searchResults.isNotEmpty()
    val isShowingNoResults = searchQuery.length >= 2 && !isSearching && searchResults.isEmpty()
    LaunchedEffect(isShowingResults, isShowingNoResults) {
        listState.scrollToItem(0)
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                MiniMusicPlayer(vm = musicPlayerViewModel, onNav = onNavigateToMusicPlayer, themeState = themeState)
                BottomNavBar(
                    currentTab, onHome = onNavigateToHome, onSearch = {},
                    onFriends = onNavigateToFriends, onLibrary = onNavigateToLibrary,
                    themeState = themeState
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(animatedMoodBrushLight(themeState)).padding(padding)) {
            LazyColumn(
                state               = listState,
                contentPadding      = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // ── KEY FIX 3: every item() has an explicit stable key ──

                item(key = "search_bar") {
                    SearchBarSection(
                        query = searchQuery, onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = ""; searchResults = emptyList() },
                        isSearching = isSearching, detectedMood = detectedMood,
                        accentColor = accentColor, placeholder = SEARCH_PLACEHOLDERS[placeholderIdx],
                        isDark = isDark, primaryText = primaryText,
                        secondaryText = secondaryText, searchBg = searchBg, iconTint = iconTint
                    )
                }

                item(key = "mascot_portal") {
                    AnimatedVisibility(
                        visible = showMascotPortal && onNavigateToEmotionChat != null,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        MascotPortalBanner(
                            mood = detectedMood ?: "", accentColor = accentColor,
                            isDark = isDark, onOpenChat = { onNavigateToEmotionChat?.invoke(searchQuery) }
                        )
                    }
                }

                if (!hasToken) {
                    item(key = "spotify_connect") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onNavigateToSpotify() },
                            shape    = RoundedCornerShape(16.dp),
                            colors   = CardDefaults.cardColors(containerColor = Color(0xFF1DB954))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Connect Spotify", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Tap to search millions of songs", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                            }
                        }
                    }
                    return@LazyColumn
                }

                if (searchResults.isNotEmpty()) {
                    // ── Search results state ──────────────────────────
                    item(key = "results_header") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (detectedMood != null) "Mood results for \"${detectedMood}\""
                                else "${searchResults.size} results",
                                fontSize = 13.sp, color = secondaryText
                            )
                            if (detectedMood != null) MoodDetectedPill(detectedMood, accentColor)
                        }
                    }

                    // PREFIX "result_" on key so it never clashes with browse-state items
                    itemsIndexed(
                        items = searchResults,
                        key   = { _, track -> "result_${track.id}" }
                    ) { index, track ->
                        val chip = xaiChipForIndex(index, detectedMood ?: themeState.currentMood)
                        if (chip != null) XAIBreakoutCard(chip, accentColor, isDark)
                        SearchResultItem(
                            track        = track, index = index, isDark = isDark,
                            primaryText  = primaryText, secondaryText = secondaryText,
                            iconTint     = iconTint,
                            detectedMood = detectedMood ?: themeState.currentMood,
                            accentColor  = accentColor,
                            onSendToChat = { onNavigateToEmotionChat?.invoke("More like \"${track.name}\" but ${detectedMood ?: "different"}") },
                            onClick      = { musicPlayerViewModel?.loadTrack(track, searchResults); onNavigateToMusicPlayer() }
                        )
                    }
                    item(key = "results_spacer") { Spacer(Modifier.height(16.dp)) }

                } else if (searchQuery.length >= 2 && !isSearching) {
                    // ── No results state ──────────────────────────────
                    item(key = "no_results") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No results for \"$searchQuery\"", color = secondaryText, fontSize = 14.sp)
                        }
                    }

                } else {
                    // ── Browse / default state ────────────────────────
                    if (recentVibes.isNotEmpty() || vibesLoading) {
                        item(key = "vibes_header") {
                            SectionHeader(
                                title         = "Your Recent Vibes",
                                subtitle      = "Played while you were ${themeState.currentMood.replaceFirstChar { it.uppercaseChar() }}",
                                primaryText   = primaryText,
                                secondaryText = secondaryText
                            )
                        }
                        item(key = "vibes_row") {
                            LazyRow(
                                contentPadding        = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (vibesLoading) {
                                    items(4, key = { "skeleton_$it" }) { VibeCardSkeleton(isDark) }
                                } else {
                                    items(recentVibes, key = { "vibe_${it.id}" }) { track ->
                                        VibeCard(
                                            track       = track,
                                            moodKey     = themeState.currentMood,
                                            accentColor = accentColor,
                                            isDark      = isDark,
                                            onClick     = { musicPlayerViewModel?.loadTrack(track, recentVibes); onNavigateToMusicPlayer() }
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "vibes_spacer") { Spacer(Modifier.height(28.dp)) }
                    }

                    item(key = "moods_header") {
                        SectionHeader(
                            title         = "Explore Moods",
                            subtitle      = "Tap a vibe to dive in",
                            primaryText   = primaryText,
                            secondaryText = secondaryText
                        )
                    }
                    item(key = "moods_row") {
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(categories, key = { "mood_${it.label}" }) { cat ->
                                GlassmorphicMoodCard(category = cat, isDark = isDark, onClick = { searchQuery = cat.query })
                            }
                        }
                    }
                    item(key = "moods_spacer") { Spacer(Modifier.height(28.dp)) }

                    if (recentSearches.isNotEmpty()) {
                        item(key = "recent_header") {
                            SectionHeader(
                                title         = "Recent Searches",
                                primaryText   = primaryText,
                                secondaryText = secondaryText,
                                actionLabel   = "Clear",
                                onAction      = {
                                    scope.launch { searchHistoryRepo.clearSearchHistory(); recentSearches = emptyList() }
                                }
                            )
                        }
                        items(recentSearches, key = { "recent_$it" }) { query ->
                            RecentSearchRow(query, primaryText, iconTint) { searchQuery = query }
                        }
                        item(key = "recent_spacer") { Spacer(Modifier.height(28.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBarSection(
    query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit,
    isSearching: Boolean, detectedMood: String?, accentColor: Color,
    placeholder: String, isDark: Boolean, primaryText: Color,
    secondaryText: Color, searchBg: Color, iconTint: Color
) {
    val borderColor by animateColorAsState(
        if (detectedMood != null) accentColor else if (isDark) Color(0xFF3A3A5A) else Color.LightGray,
        tween(600), label = "searchBorder"
    )
    val glowAlpha by animateFloatAsState(
        if (detectedMood != null) 0.25f else 0f, tween(600), label = "glowAlpha"
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.fillMaxWidth().drawBehind {
            if (glowAlpha > 0f) drawCircle(color = accentColor.copy(alpha = glowAlpha), radius = size.width * 0.55f, center = Offset(size.width / 2, size.height / 2))
        }) {
            OutlinedTextField(
                value         = query,
                onValueChange = onQueryChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = {
                    AnimatedContent(targetState = placeholder, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "placeholder") { text ->
                        Text(text, color = secondaryText, fontSize = 14.sp)
                    }
                },
                leadingIcon  = { Icon(Icons.Filled.Search, null, tint = if (detectedMood != null) accentColor else iconTint, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    when {
                        isSearching        -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accentColor)
                        query.isNotEmpty() -> IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear", tint = iconTint) }
                    }
                },
                shape  = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = searchBg, unfocusedContainerColor = searchBg,
                    focusedBorderColor      = borderColor,
                    unfocusedBorderColor    = if (detectedMood != null) borderColor else Color.Transparent,
                    focusedTextColor        = primaryText, unfocusedTextColor = primaryText
                ),
                singleLine = true
            )
        }
        AnimatedVisibility(visible = detectedMood != null) {
            Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                Text("Mood detected: ${detectedMood?.replaceFirstChar { it.uppercaseChar() }} • Personalising results", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MascotPortalBanner(mood: String, accentColor: Color, isDark: Boolean, onOpenChat: () -> Unit) {
    val message = when (mood) {
        "anxious"  -> "That sounds heavy. Want to talk it through? 💬"
        "sad"      -> "I'm here for you. Let's chat about it 🌧️"
        "angry"    -> "Need to vent? I'm listening 🔥"
        "focused"  -> "I'll find the perfect focus playlist for you 🎯"
        "romantic" -> "Setting the mood? Let me help 💕"
        else       -> "I can find something perfect for this vibe 🎵"
    }
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onOpenChat() },
        shape = RoundedCornerShape(16.dp), color = if (isDark) accentColor.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.10f)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val pulse by rememberInfiniteTransition(label = "portalPulse").animateFloat(0.85f, 1.0f, infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "portalScale")
            Box(Modifier.size(40.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(accentColor.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) { Text("🐾", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E))
                Text("Tap to open chat →", fontSize = 11.sp, color = accentColor)
            }
        }
    }
}

@Composable
private fun GlassmorphicMoodCard(category: MoodCategory, isDark: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "cardScale")
    Box(Modifier.width(150.dp).height(140.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(20.dp)).clickable { pressed = true; onClick() }) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(category.gradientColors)))
        Text(category.animEmoji.ifEmpty { category.emoji }, fontSize = 72.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 12.dp, y = 12.dp).graphicsLayer { alpha = 0.18f })
        Box(Modifier.fillMaxSize().background(if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.12f)))
        Box(Modifier.size(40.dp).align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp).clip(CircleShape).blur(16.dp).background(category.glowColor.copy(alpha = 0.8f)))
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(category.emoji, fontSize = 28.sp)
            Column {
                Text(category.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (category.contextTag.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.25f)) {
                        Text(category.contextTag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeCard(track: Track, moodKey: String, accentColor: Color, isDark: Boolean, onClick: () -> Unit) {
    val cardBg = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEF5)
    Column(Modifier.width(120.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).drawBehind { drawCircle(color = accentColor.copy(alpha = 0.30f), radius = size.width * 0.55f) }) {
            Card(Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(6.dp)) {
                if (track.albumArtUrl.isNotEmpty()) AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(cardBg), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 28.sp) }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.55f)) {
                Text("Played while ${moodKey.replaceFirstChar { it.uppercaseChar() }}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 8.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.name,   fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 10.sp, color = if (isDark) Color(0xFF888899) else Color(0xFF666677), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VibeCardSkeleton(isDark: Boolean) {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer").animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmerAlpha")
    val bg = if (isDark) Color(0xFF2A2A3E).copy(alpha = shimmerAlpha) else Color(0xFFDDDDEE).copy(alpha = shimmerAlpha)
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(bg))
        Spacer(Modifier.height(8.dp)); Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(bg))
        Spacer(Modifier.height(4.dp)); Box(Modifier.width(60.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(bg))
    }
}

@Composable
private fun SearchResultItem(
    track: Track, index: Int, isDark: Boolean, primaryText: Color, secondaryText: Color,
    iconTint: Color, detectedMood: String, accentColor: Color,
    onSendToChat: () -> Unit, onClick: () -> Unit
) {
    val energyLevel = remember(track.id) { val hash = track.id.hashCode().let { if (it < 0) -it else it }; (hash % 100) / 100f }
    val energyColor = when { energyLevel > 0.7f -> Color(0xFFFF416C); energyLevel > 0.4f -> Color(0xFFFFB347); else -> Color(0xFF6EC6F5) }
    val energyLabel = when { energyLevel > 0.7f -> "High energy"; energyLevel > 0.4f -> "Mid energy"; else -> "Chill" }
    val contextChip = remember(track.id, detectedMood) {
        val chips = when (detectedMood) {
            "calm", "tired"      -> listOf("Steady BPM", "Instrumental", "Low tempo", "Calming")
            "energetic", "angry" -> listOf("High BPM", "Bass-heavy", "Dynamic", "Intense")
            "sad"                -> listOf("Emotional lyrics", "Slow tempo", "Melancholic", "Comforting")
            "focused"            -> listOf("No vocals", "Steady beat", "Minimal", "Consistent")
            "happy"              -> listOf("Upbeat", "Major key", "Bright tone", "Feel-good")
            else                 -> listOf("Mood match", "Vibe match", "Similar energy", "Related")
        }
        chips[(track.id.hashCode().let { if (it < 0) -it else it }) % chips.size]
    }
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.45f)) {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(52.dp), shape = RoundedCornerShape(10.dp)) {
                if (track.albumArtUrl.isNotEmpty()) AsyncImage(model = track.albumArtUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEF5)), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 20.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} · ${track.album}", fontSize = 12.sp, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SparklineBar(energyLevel, energyColor)
                    SmallChip(energyLabel, energyColor.copy(alpha = 0.18f), energyColor)
                    SmallChip(contextChip, accentColor.copy(alpha = 0.15f), accentColor)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = iconTint, modifier = Modifier.size(22.dp))
                Icon(Icons.Filled.Chat, "More like this", tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp).clickable { onSendToChat() })
            }
        }
    }
}

@Composable
private fun XAIBreakoutCard(chip: XAIChip, accentColor: Color, isDark: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = if (isDark) 0.15f else 0.10f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(chip.icon, fontSize = 18.sp)
            Column {
                Text("Why we're showing more of this", fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(chip.text, fontSize = 12.sp, color = if (isDark) Color(0xFFCCCCDD) else Color(0xFF333344), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MoodDetectedPill(mood: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Text("🧠 ${mood.replaceFirstChar { it.uppercaseChar() }} mode", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SparklineBar(energy: Float, color: Color) {
    val bars = remember(energy) {
        val base = energy * 0.6f + 0.1f
        (0 until 8).map { i -> val v = ((i * 7 + (energy * 100).toInt()) % 5) / 10f; (base + v - 0.25f).coerceIn(0.08f, 1f) }
    }
    Row(Modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        bars.forEach { h -> Box(Modifier.width(2.dp).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color.copy(alpha = 0.85f))) }
    }
}

@Composable
private fun SmallChip(label: String, bgColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bgColor) {
        Text(label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String = "", primaryText: Color, secondaryText: Color, actionLabel: String = "", onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryText)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 11.sp, color = secondaryText)
        }
        if (actionLabel.isNotEmpty() && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = secondaryText, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun RecentSearchRow(query: String, primaryText: Color, iconTint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.History, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(query, fontSize = 14.sp, color = primaryText, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.NorthWest, null, tint = iconTint, modifier = Modifier.size(14.dp))
    }
}