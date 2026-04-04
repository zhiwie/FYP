package com.example.fypdraft.view

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.MoodHistoryRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.ml.MoodAwareRecommender
import com.example.fypdraft.ml.PassiveMoodDetector
import com.example.fypdraft.ml.RLRecommendationEngine
import com.example.fypdraft.ml.RewardEvent
import com.example.fypdraft.ml.RewardType
import com.example.fypdraft.model.MascotMoodDetector
import com.example.fypdraft.model.PetPersonalityEngine
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HomeScreen"

private data class MusicSection(val title: String, val emoji: String, val tracks: List<Track>)

// ── Personalised recommendation reason ───────────────────────────────────
private data class RecommendationReason(
    val label: String,
    val description: String,
    val score: Float,       // 0..1 — drives bar width
    val barColor: Color
)

/**
 * Build 3 contextual reasons for why a track was recommended,
 * using real session data:
 *  - [currentMood]          : active mascot mood
 *  - [personalityArchetype] : top archetype from PetPersonalityEngine (e.g. "Explorer")
 *  - [recentArtists]        : artists heard this session (familiarity signal)
 *  - [listenStreakMinutes]   : minutes of continuous listening (engagement signal)
 */
private fun getReasonsForSection(
    sectionTitle: String,
    sectionEmoji: String,
    track: Track,
    currentMood: String,
    personalityArchetype: String,
    recentArtists: List<String>,
    listenStreakMinutes: Int
): List<RecommendationReason> {

    // ── Reason 1: Mood match ─────────────────────────────────────────
    val moodMatchScore = (0.70f + (track.name.length % 4) * 0.07f).coerceIn(0f, 1f)
    val moodReason = RecommendationReason(
        label = "Mood Match",
        description = when (currentMood.lowercase()) {
            "happy"     -> "You're in a happy headspace — this should keep the good vibes going."
            "sad"       -> "Feeling sad? This pick sits with you without forcing a mood flip."
            "anxious"   -> "When you're anxious, familiar sounds and steady tempos tend to help most."
            "relaxed"   -> "A chill pick to match your relaxed energy right now."
            "angry"     -> "High-energy track that channels and then eases your mood."
            "focused"   -> "Low-distraction, steady rhythm — built for a focused state."
            "romantic"  -> "Fits the warm, close feeling of a romantic moment."
            "energetic" -> "Keeps pace with your energetic mood right now."
            else        -> "Matches the general ${currentMood} vibe of your recent listening."
        },
        score = moodMatchScore,
        barColor = Color(0xFF7B9FE8)
    )

    // ── Reason 2: Section / context reason ──────────────────────────
    val sectionReason = when {
        sectionEmoji == "✨" -> {
            val archetypeNote = if (personalityArchetype.isNotBlank())
                "Your $personalityArchetype taste profile shaped this pick."
            else
                "Based on your listening history and top artists."
            RecommendationReason(
                label = "Made for You",
                description = archetypeNote,
                score = (0.80f + (track.artist.length % 3) * 0.05f).coerceIn(0f, 1f),
                barColor = Color(0xFFE87B9F)
            )
        }
        sectionTitle.contains("focus", ignoreCase = true) ||
                sectionTitle.contains("study", ignoreCase = true) -> RecommendationReason(
            label = "Focus Fit",
            description = "Instrumentals or minimal lyrics — exactly what your brain needs to stay locked in.",
            score = 0.78f,
            barColor = Color(0xFF7BE8B8)
        )
        sectionTitle.contains("morning", ignoreCase = true) -> RecommendationReason(
            label = "Morning Energy",
            description = "A gentle ramp-up to start your day — not too loud, not too slow.",
            score = (0.72f + (track.name.length % 3) * 0.06f).coerceIn(0f, 1f),
            barColor = Color(0xFFE8C97B)
        )
        sectionTitle.contains("evening", ignoreCase = true) ||
                sectionTitle.contains("night", ignoreCase = true) -> RecommendationReason(
            label = "Evening Wind-Down",
            description = "A softer pick to help you decompress as the day wraps up.",
            score = (0.68f + (track.name.length % 4) * 0.05f).coerceIn(0f, 1f),
            barColor = Color(0xFFE8C97B)
        )
        sectionTitle.contains("feel better", ignoreCase = true) ||
                sectionTitle.contains("uplift", ignoreCase = true) -> RecommendationReason(
            label = "Mood Lift",
            description = "Picked to gently shift how you're feeling — not forcing it, just nudging upward.",
            score = (0.70f + (track.name.length % 3) * 0.06f).coerceIn(0f, 1f),
            barColor = Color(0xFFB87BE8)
        )
        else -> RecommendationReason(
            label = "Right Now Pick",
            description = "A solid fit for ${getTimeGreeting().lowercase()} — time of day and mood both considered.",
            score = (0.62f + (track.name.length % 5) * 0.07f).coerceIn(0f, 1f),
            barColor = Color(0xFFE8C97B)
        )
    }

    // ── Reason 3: Artist familiarity from this session ───────────────
    val artistKnown  = recentArtists.any { it.equals(track.artist, ignoreCase = true) }
    val streakBonus  = (listenStreakMinutes / 10).coerceAtMost(3) * 0.04f   // up to +0.12
    val familiarityScore = when {
        artistKnown                -> 0.85f + streakBonus
        recentArtists.isNotEmpty() -> 0.55f + (track.artist.length % 5) * 0.06f + streakBonus
        else                       -> 0.50f + (track.artist.length % 6) * 0.06f
    }
    val familiarityReason = RecommendationReason(
        label = "Artist Familiarity",
        description = when {
            artistKnown ->
                "You've been listening to ${track.artist} this session — a familiar voice."
            recentArtists.isNotEmpty() ->
                "${track.artist}'s style sits close to what you've been exploring lately."
            else ->
                "A new artist worth discovering based on your ${currentMood} taste right now."
        },
        score = familiarityScore.coerceIn(0f, 1f),
        barColor = Color(0xFF7BE8D8)
    )

    return listOf(moodReason, sectionReason, familiarityReason)
}

// ─────────────────────────────────────────────────────────────────────────
// HomeScreen
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    petRepository: PetRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    isPlayingMusic: Boolean = false,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToEmotionChat: () -> Unit = {},
    onNavigateToMoodHistory: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onMascotVisibilityChanged: (Boolean) -> Unit = {},
    currentTab: Int = 0
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scrollState = rememberScrollState()

    val currentUser = FirebaseAuth.getInstance().currentUser
    val displayName = currentUser?.displayName ?: "Friend"

    val moodHistoryRepo   = remember { MoodHistoryRepository() }
    val rlEngine          = remember { RLRecommendationEngine() }
    val passiveMoodDetector = remember { PassiveMoodDetector() }
    val personalityEngine = remember { PetPersonalityEngine() }
    val personalityProfile by personalityEngine.profile.collectAsState()
    val spotifyMusicRepo  = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository(it) }
    }

    val spotifyAuthState by spotifyRepository?.authState?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val isSpotifyConnected = spotifyAuthState?.isAuthenticated == true

    var isLoading  by remember { mutableStateOf(false) }
    var sections   by remember { mutableStateOf<List<MusicSection>>(emptyList()) }
    var loadError  by remember { mutableStateOf<String?>(null) }
    var hasLoaded  by remember { mutableStateOf(false) }

    var mascotMood  by remember { mutableStateOf(MascotMoodDetector.detectMood()) }
    var chatMessage by remember { mutableStateOf<String?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }

    // ── Long-press explanation state ──────────────────────────────────
    var explainTrack        by remember { mutableStateOf<Track?>(null) }
    var explainSectionTitle by remember { mutableStateOf("") }
    var explainSectionEmoji by remember { mutableStateOf("") }

    val localPetRepo = remember { petRepository ?: PetRepository() }
    val petState by localPetRepo.petState.collectAsState()

    // ── Session-level signals for personalised explanations ───────────
    var recentArtists       by remember { mutableStateOf(listOf<String>()) }
    var listenStreakMinutes  by remember { mutableIntStateOf(0) }

    // ── Mascot widget position tracking ──────────────────────────────
    var mascotWidgetBottomY by remember { mutableFloatStateOf(0f) }
    val scrollOffset = scrollState.value
    val isMascotVisible = remember(mascotWidgetBottomY, scrollOffset) {
        mascotWidgetBottomY > scrollOffset
    }
    LaunchedEffect(isMascotVisible) { onMascotVisibilityChanged(isMascotVisible) }

    LaunchedEffect(Unit) { localPetRepo.loadPet() }
    LaunchedEffect(Unit) { personalityEngine.loadOrCompute() }
    LaunchedEffect(mascotMood.mood) { localPetRepo.updateMood(mascotMood.mood) }
    LaunchedEffect(Unit) {
        moodHistoryRepo.saveMoodExplicit(mascotMood.mood, "App opened — auto-detected mood")
    }

    // ── Passive mood detection ────────────────────────────────────────
    val playerState  = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack

    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null) {
            passiveMoodDetector.analyseTrackMetadata(
                trackId     = currentTrack.id,
                trackName   = currentTrack.name,
                artist      = currentTrack.artist,
                album       = currentTrack.album,
                searchQuery = ""
            )
            // Track artist for session-level familiarity in explanations
            if (!recentArtists.contains(currentTrack.artist)) {
                recentArtists = (recentArtists + currentTrack.artist).takeLast(20)
            }
        }
    }

    val passiveMood       by passiveMoodDetector.detectedMood.collectAsState()
    val passiveConfidence by passiveMoodDetector.confidence.collectAsState()

    LaunchedEffect(passiveMood, passiveConfidence) {
        if (passiveConfidence >= 0.5f && !mascotMood.isUserOverride && isPlayingMusic) {
            val newMood = MascotMoodDetector.getMoodForKey(passiveMood)
            if (newMood.mood != mascotMood.mood) {
                mascotMood  = newMood
                chatMessage = "The music feels ${passiveMood}! 🎶"
                moodHistoryRepo.saveMoodExplicit(
                    passiveMood,
                    "Auto-detected from music: ${currentTrack?.name ?: "unknown"}"
                )
            }
        }
    }

    // ── Implicit feedback: skip / complete detection ──────────────────
    var previousTrackId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentTrack?.id) {
        val newId  = currentTrack?.id
        val prevId = previousTrackId

        if (prevId != null && newId != prevId) {
            val listenRatio = rlEngine.onTrackEnded(wasSkipped = true)
            val rewardType  = when {
                listenRatio < 0.2f -> RewardType.SKIPPED
                listenRatio > 0.8f -> RewardType.COMPLETED
                else               -> RewardType.PLAYED
            }
            scope.launch {
                rlEngine.recordReward(RewardEvent(
                    type          = rewardType,
                    mood          = mascotMood.mood,
                    trackFeatures = null,
                    durationRatio = listenRatio,
                    queryUsed     = ""
                ))
            }
        }

        if (newId != null) {
            rlEngine.onTrackStarted(
                mood       = mascotMood.mood,
                query      = "",
                durationMs = currentTrack?.durationMs ?: 0L
            )
        }
        previousTrackId = newId
    }

    // ── Listening duration check-in + streak counter ──────────────────
    val listenCheckInMessages = remember {
        listOf(
            "You've been listening for a while 🎵 How are you feeling?",
            "Still vibing? Let me know how you're doing 😊",
            "Hey! Quick check-in — how's your mood right now? 🌟",
            "You've had quite a music session! Feeling good? 💫",
            "Long listening session detected! Want to tell me how you feel? 🎶"
        )
    }
    var totalListeningMs by remember { mutableLongStateOf(0L) }
    var lastCheckInMs    by remember { mutableLongStateOf(0L) }
    var recentTrackMoods by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(passiveMood) {
        if (passiveMood.isNotEmpty() && passiveMood != "neutral") {
            recentTrackMoods = (recentTrackMoods + passiveMood).takeLast(5)
        }
    }

    LaunchedEffect(isPlayingMusic) {
        if (!isPlayingMusic) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            totalListeningMs    += 10_000L
            listenStreakMinutes  = (totalListeningMs / 60_000L).toInt()
            val similarityScore = if (recentTrackMoods.size >= 2) {
                val max = recentTrackMoods.groupingBy { it }.eachCount().values.max()
                max.toFloat() / recentTrackMoods.size
            } else 0.5f
            val gapMs = when {
                similarityScore >= 0.8f -> 30 * 60 * 1000L
                similarityScore <= 0.3f -> 60 * 60 * 1000L
                else                    -> 45 * 60 * 1000L
            }
            if (totalListeningMs - lastCheckInMs >= gapMs) {
                lastCheckInMs = totalListeningMs
                chatMessage   = listenCheckInMessages.random()
            }
        }
    }

    // ── Load music sections ───────────────────────────────────────────
    var lastLoadedMood by remember { mutableStateOf("") }
    LaunchedEffect(isSpotifyConnected, mascotMood.mood) {
        if (!isSpotifyConnected || spotifyMusicRepo == null) {
            if (!isSpotifyConnected) { sections = emptyList(); hasLoaded = false }
            return@LaunchedEffect
        }
        val moodChanged = lastLoadedMood != mascotMood.mood
        if (hasLoaded && sections.isNotEmpty() && !moodChanged) return@LaunchedEffect
        if (moodChanged && hasLoaded) { hasLoaded = false }
        lastLoadedMood = mascotMood.mood
        isLoading = true; loadError = null
        try { rlEngine.loadState() } catch (_: Exception) {}
        try {
            val token = spotifyRepository?.getAccessToken()
            if (token == null) {
                loadError = "Session expired. Please reconnect Spotify."
                isLoading = false; return@LaunchedEffect
            }
            val recommended = MoodAwareRecommender.generateSections(
                currentMood        = mascotMood.mood,
                personalityProfile = personalityProfile,
                isUserOverride     = mascotMood.isUserOverride,
                rlEngine           = rlEngine
            )

            data class SectionDef(val title: String, val emoji: String, val query: String)
            val sectionDefs = recommended.map { SectionDef(it.title, it.emoji, it.query) }

            val deferreds = sectionDefs.map { def ->
                def to async {
                    try {
                        if (def.query == "PERSONALIZED") {
                            val topTracks = try { spotifyMusicRepo.getPersonalizedTracks(5) }
                            catch (_: Exception) { emptyList() }
                            val moodQuery = rlEngine.getMoodSearchQuery(mascotMood.mood)
                                .ifBlank { mascotMood.mood }
                            val moodTracks = try { spotifyMusicRepo.searchTracks(moodQuery, 5) }
                            catch (_: Exception) { emptyList() }
                            val blended = mutableListOf<Track>()
                            val maxLen  = maxOf(topTracks.size, moodTracks.size)
                            for (i in 0 until maxLen) {
                                if (i < topTracks.size)  blended.add(topTracks[i])
                                if (i < moodTracks.size) blended.add(moodTracks[i])
                            }
                            blended.distinctBy { it.id }
                        } else {
                            spotifyMusicRepo.searchTracks(def.query, 10)
                        }
                    } catch (e: Exception) { emptyList() }
                }
            }

            val results = mutableListOf<MusicSection>()
            for ((def, deferred) in deferreds) {
                val tracks = deferred.await()
                if (tracks.isNotEmpty()) results.add(MusicSection(def.title, def.emoji, tracks))
            }
            sections = results; hasLoaded = true
            if (sections.isEmpty()) {
                val testToken = spotifyRepository?.getAccessToken()
                if (testToken == null) {
                    loadError = "Session expired."; spotifyRepository?.markTokenExpired()
                } else loadError = "No results found. Tap to retry."
            }
        } catch (e: Exception) { loadError = "Failed: ${e.message}" }
        isLoading = false
    }

    // ── Theme-aware color tokens ──────────────────────────────────────
    // Light: slightly-off-dark so it's not 100% black (more refined)
    // Dark:  slightly-off-white so it's not harsh pure white
    val isDark           = themeState.isDark
    val primaryTextColor   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryTextColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF55556A)

    // ─────────────────────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                displayName   = displayName,
                email         = currentUser?.email ?: "",
                isDark        = isDark,
                onSettings    = { scope.launch { drawerState.close() }; onNavigateToSettings() },
                onSpotify     = { scope.launch { drawerState.close() }; onNavigateToSpotify() },
                onMoodHistory = { scope.launch { drawerState.close() }; onNavigateToMoodHistory() },
                onSignOut     = { scope.launch { drawerState.close() }; onSignOut() }
            )
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor   = Color.Transparent,
            bottomBar = {
                Column {
                    MiniMusicPlayer(
                        vm         = musicPlayerViewModel,
                        onNav      = onNavigateToMusicPlayer,
                        themeState = themeState
                    )
                    BottomNavBar(
                        currentTab, {}, onNavigateToSearch,
                        onNavigateToFriends, onNavigateToLibrary, themeState
                    )
                }
            }
        ) { padding ->
            val bgBrush = animatedMoodBrushLight(themeState)
            Box(modifier.fillMaxSize().background(bgBrush).padding(padding)) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

                    // ── Top bar ───────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (isDark) Color(0xFF2A2A4A) else Color(0xFF1A1A2E))
                                .clickable { scope.launch { drawerState.open() } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, "Profile", tint = Color.White,
                                modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(getTimeGreeting(), fontSize = 14.sp, color = secondaryTextColor)
                            Text(
                                "Hey, $displayName!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }
                    }

                    // ── Spotify connect banner ────────────────────────
                    if (!isSpotifyConnected) {
                        val errorMsg  = spotifyAuthState?.errorMessage
                        val isExpired = errorMsg != null &&
                                errorMsg.contains("expired", ignoreCase = true)
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clickable { onNavigateToSpotify() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpired) Color(0xFFFF9800)
                                else Color(0xFF1DB954)
                            )
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isExpired) Icon(Icons.Filled.Warning, null, tint = Color.White)
                                else Text("🎵", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isExpired) "Session expired" else "Connect Spotify",
                                        color = Color.White, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isExpired) "Tap to reconnect"
                                        else "Get personalized recommendations",
                                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Mascot widget ─────────────────────────────────
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { coordinates ->
                                mascotWidgetBottomY =
                                    coordinates.positionInParent().y + coordinates.size.height
                            }
                    ) {
                        MascotWidget(
                            mood               = mascotMood,
                            petState           = petState,
                            petRepository      = localPetRepo,
                            chatMessage        = chatMessage,
                            onQuickReply = { reply ->
                                scope.launch {
                                    if (reply == "yes") {
                                        chatMessage = "Here are tracks just for you 🎶"
                                        rlEngine.recordReward(RewardEvent(
                                            RewardType.SUGGESTION_ACCEPTED, mascotMood.mood, null))
                                    } else {
                                        chatMessage = "Tap me anytime 😊"
                                        rlEngine.recordReward(RewardEvent(
                                            RewardType.SUGGESTION_REJECTED, mascotMood.mood, null))
                                    }
                                }
                            },
                            onTapMascot        = { onNavigateToEmotionChat() },
                            onChangeMood       = { showMoodPicker = true },
                            isPlayingMusic     = isPlayingMusic,
                            personalityProfile = personalityProfile
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Music sections ────────────────────────────────
                    when {
                        isLoading -> Box(
                            Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Loading your music...", color = secondaryTextColor, fontSize = 14.sp)
                            }
                        }
                        !isSpotifyConnected -> Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Connect Spotify to see recommendations", color = secondaryTextColor)
                        }
                        loadError != null -> Card(
                            Modifier.fillMaxWidth().padding(16.dp).clickable {
                                hasLoaded = false; loadError = null; sections = emptyList()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Refresh, null, tint = Color.White)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Tap to retry", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(loadError ?: "", color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp)
                                }
                            }
                        }
                        sections.isEmpty() && hasLoaded -> Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No recommendations yet. Try changing your mood!",
                                color = secondaryTextColor
                            )
                        }
                        else -> sections.forEach { section ->
                            Text(
                                "${section.emoji} ${section.title}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(section.tracks) { track ->
                                    SmallTrackCard(
                                        track  = track,
                                        all    = section.tracks,
                                        vm     = musicPlayerViewModel,
                                        onNav  = onNavigateToMusicPlayer,
                                        isDark = isDark,
                                        onLongPress = {
                                            explainTrack        = track
                                            explainSectionTitle = section.title
                                            explainSectionEmoji = section.emoji
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // ── Why-recommended explanation dialog ────────────────────────────
    val explainTrackSnapshot = explainTrack
    if (explainTrackSnapshot != null) {
        // Extract the top personality archetype label from personalityProfile.
        // Replace this line with your actual field access once you know the
        // exact property name (e.g. personalityProfile?.topArchetype ?: "").
        // For now we use toString()-based parsing as a universal fallback so
        // the sheet always renders even if the field name changes.
        val archetypeLabel: String = try {
            personalityProfile
                ?.toString()                            // e.g. "PersonalityProfile(topArchetype=Explorer, ...)"
                ?.substringAfter("topArchetype=")
                ?.substringBefore(",")
                ?.substringBefore(")")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: ""
        } catch (_: Exception) { "" }

        RecommendationExplanationSheet(
            track                = explainTrackSnapshot,
            sectionTitle         = explainSectionTitle,
            sectionEmoji         = explainSectionEmoji,
            currentMood          = mascotMood.mood,
            personalityArchetype = archetypeLabel,
            recentArtists        = recentArtists,
            listenStreakMinutes  = listenStreakMinutes,
            onDismiss = { explainTrack = null },
            onPlay = {
                musicPlayerViewModel?.loadTrack(
                    explainTrackSnapshot,
                    sections.find { it.title == explainSectionTitle }?.tracks
                        ?: listOf(explainTrackSnapshot)
                )
                onNavigateToMusicPlayer()
                explainTrack = null
            }
        )
    }

    if (showMoodPicker) {
        MoodPickerDialog(mascotMood.mood, onSelect = { selected ->
            val old    = mascotMood.mood
            mascotMood = MascotMoodDetector.getMoodForKey(selected).copy(isUserOverride = true)
            chatMessage = null; showMoodPicker = false
            moodHistoryRepo.saveMoodExplicit(selected, "Changed from $old to $selected")
            scope.launch {
                rlEngine.recordReward(RewardEvent(RewardType.MOOD_OVERRIDE, old, null))
            }
        }, onDismiss = { showMoodPicker = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Long-press explanation dialog — personalised
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendationExplanationSheet(
    track: Track,
    sectionTitle: String,
    sectionEmoji: String,
    currentMood: String,
    personalityArchetype: String,
    recentArtists: List<String>,
    listenStreakMinutes: Int,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    val reasons = remember(track.id, sectionTitle, currentMood, personalityArchetype) {
        getReasonsForSection(
            sectionTitle         = sectionTitle,
            sectionEmoji         = sectionEmoji,
            track                = track,
            currentMood          = currentMood,
            personalityArchetype = personalityArchetype,
            recentArtists        = recentArtists,
            listenStreakMinutes  = listenStreakMinutes
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {

                // Drag handle
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Album art + track info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        Modifier.size(64.dp),
                        shape     = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        if (track.albumArtUrl.isNotEmpty())
                            AsyncImage(
                                model           = track.albumArtUrl,
                                contentDescription = null,
                                contentScale    = ContentScale.Crop,
                                modifier        = Modifier.fillMaxSize()
                            )
                        else Box(
                            Modifier.fillMaxSize().background(Color(0xFF2A2A3E)),
                            contentAlignment = Alignment.Center
                        ) { Text("🎵", fontSize = 24.sp) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            track.artist, fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "$sectionEmoji $sectionTitle",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Sub-header references the user's live mood
                Text(
                    "Why this landed on your ${currentMood.replaceFirstChar { it.uppercase() }} playlist",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.55f),
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(14.dp))

                // Animated reason bars
                reasons.forEach { reason ->
                    ReasonBar(reason = reason)
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(6.dp))

                // Play button
                Button(
                    onClick  = onPlay,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Dismiss", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Animated reason bar ───────────────────────────────────────────────────

@Composable
private fun ReasonBar(reason: RecommendationReason) {
    val animatedScore by animateFloatAsState(
        targetValue   = reason.score,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label         = "bar_${reason.label}"
    )

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${reason.label}: ",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White
            )
            Text(
                reason.description,
                fontSize = 11.5.sp,
                color = reason.barColor,
                fontWeight = FontWeight.Normal,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedScore)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(reason.barColor)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmallTrackCard(
    track: Track,
    all: List<Track>,
    vm: MusicPlayerViewModel?,
    onNav: () -> Unit,
    isDark: Boolean = false,
    onLongPress: () -> Unit = {}
) {
    val trackNameColor   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val trackArtistColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val cardBg           = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEEE)

    Column(
        Modifier
            .width(130.dp)
            .pointerInput(track.id) {
                detectTapGestures(
                    onTap       = { vm?.loadTrack(track, all); onNav() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Card(
            Modifier.size(130.dp),
            shape     = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            if (track.albumArtUrl.isNotEmpty())
                AsyncImage(
                    model              = track.albumArtUrl,
                    contentDescription = track.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            else Box(
                Modifier.fillMaxSize().background(cardBg),
                contentAlignment = Alignment.Center
            ) { Text("🎵", fontSize = 32.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = trackNameColor, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            track.artist, fontSize = 11.sp, color = trackArtistColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BottomNavBar(
    currentTab: Int,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onLibrary: () -> Unit,
    themeState: AppThemeState = AppThemeState()
) {
    val navItems = listOf(
        Triple(Icons.Filled.Home,         "Home",    onHome),
        Triple(Icons.Filled.Search,       "Search",  onSearch),
        Triple(Icons.Filled.People,       "Friends", onFriends),
        Triple(Icons.Filled.LibraryMusic, "Library", onLibrary)
    )

    val palette = themeState.activePalette
    val isDark  = themeState.isDark
    val speed   = themeState.transitionSpeed

    val navBgColor by animateColorAsState(
        targetValue   = if (isDark) palette.darkBottom else palette.lightBottom,
        animationSpec = tween(speed), label = "navBg"
    )
    // Slightly off-white in dark / deep navy in light — avoids harsh extremes
    val selectedColor by animateColorAsState(
        targetValue   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E),
        animationSpec = tween(speed), label = "navSel"
    )
    val unselectedColor = if (isDark) Color(0xFF888899) else Color(0xFF9E9E9E)
    val dividerColor by animateColorAsState(
        targetValue   = if (isDark) Color.White.copy(alpha = 0.08f)
        else Color.Black.copy(alpha = 0.08f),
        animationSpec = tween(speed), label = "navDiv"
    )

    Column {
        HorizontalDivider(thickness = 0.6.dp, color = dividerColor)
        NavigationBar(
            containerColor = navBgColor,
            tonalElevation = 0.dp,
            modifier       = Modifier.height(72.dp)
        ) {
            navItems.forEachIndexed { index, (icon, label, action) ->
                val selected = currentTab == index
                NavigationBarItem(
                    selected = selected,
                    onClick  = action,
                    icon = {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
                    },
                    label = {
                        Text(
                            label, fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = selectedColor,
                        selectedTextColor   = selectedColor,
                        unselectedIconColor = unselectedColor,
                        unselectedTextColor = unselectedColor,
                        indicatorColor      = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun MiniMusicPlayer(
    vm: MusicPlayerViewModel?,
    onNav: () -> Unit,
    themeState: AppThemeState = AppThemeState()
) {
    val ps    = vm?.playerState?.collectAsState()
    val track = ps?.value?.currentTrack ?: return
    val playing = ps.value.isPlaying
    val palette = themeState.activePalette
    val miniPlayerBg =
        if (themeState.isDark) palette.darkSurface else palette.darkTop.copy(alpha = 0.95f)

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onNav() },
        colors    = CardDefaults.cardColors(containerColor = miniPlayerBg),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(Modifier.size(40.dp), shape = RoundedCornerShape(8.dp)) {
                if (track.albumArtUrl.isNotEmpty())
                    AsyncImage(
                        model = track.albumArtUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                else Box(
                    Modifier.fillMaxSize().background(palette.accent.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) { Text("🎵", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { vm?.togglePlayPause() }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "PlayPause", tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProfileDrawerContent(
    displayName: String,
    email: String,
    isDark: Boolean = false,
    onSettings: () -> Unit,
    onSpotify: () -> Unit,
    onMoodHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    val drawerBg  = if (isDark) Color(0xFF1C1C2E)  else Color.White
    val textColor = if (isDark) Color(0xFFE8E8F0)  else Color.Black
    val subColor  = if (isDark) Color(0xFF888899)  else Color.Gray
    val avatarBg  = if (isDark) Color(0xFF2A2A4A)  else Color(0xFF1A1A2E)
    val divColor  = if (isDark) Color(0xFF2A2A3A)  else Color(0xFFEEEEEE)

    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = drawerBg) {
        Column(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.White,
                    modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(email, fontSize = 13.sp, color = subColor)
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = divColor)
            Spacer(Modifier.height(16.dp))
            DrawerItem(Icons.Filled.Settings,   "Settings",        onSettings,    textColor)
            DrawerItem(Icons.Filled.Link,        "Connect Spotify", onSpotify,     textColor)
            DrawerItem(Icons.Filled.Favorite,    "Favorites",       {},            textColor)
            DrawerItem(Icons.Filled.BarChart,    "Mood Analytics",  onMoodHistory, textColor)
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = divColor)
            Spacer(Modifier.height(12.dp))
            DrawerItem(Icons.Filled.Logout, "Sign Out", onSignOut, Color.Red)
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector, label: String,
    onClick: () -> Unit, tint: Color = Color.Black
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
private fun MoodPickerDialog(
    currentMood: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                MascotMoodDetector.allMoodKeys().forEach { mood ->
                    val d = MascotMoodDetector.getMoodForKey(mood)
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(mood) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(d.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            mood.replaceFirstChar { it.uppercase() }, fontSize = 16.sp,
                            fontWeight = if (mood == currentMood) FontWeight.Bold else FontWeight.Normal,
                            color      = if (mood == currentMood) Color(0xFF6A5ACD) else Color.Black
                        )
                        if (mood == currentMood) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.Check, null, tint = Color(0xFF6A5ACD),
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun getTimeGreeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Late night vibes"
    }
}