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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fypdraft.data.repository.DailyMood
import com.example.fypdraft.data.repository.MoodHistoryRepository
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.ml.MoodAwareRecommender
import com.example.fypdraft.ml.PassiveMoodDetector
import com.example.fypdraft.ml.RLRecommendationEngine
import com.example.fypdraft.ml.RewardEvent
import com.example.fypdraft.ml.RewardType
import com.example.fypdraft.ml.UserTasteProfile
import com.example.fypdraft.ml.UserTasteProfileBuilder
import com.example.fypdraft.model.MascotMoodDetector
import com.example.fypdraft.model.PetPersonalityEngine
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrushLight
import com.example.fypdraft.viewmodel.MoodCheckInViewModel
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HomeScreen"

private data class MusicSection(val title: String, val emoji: String, val tracks: List<Track>)

private data class RecommendationReason(
    val label: String, val description: String, val score: Float, val barColor: Color
)

private fun getReasonsForSection(
    sectionTitle: String, sectionEmoji: String, track: Track,
    currentMood: String, personalityArchetype: String,
    recentArtists: List<String>, listenStreakMinutes: Int
): List<RecommendationReason> {
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
        score = moodMatchScore, barColor = Color(0xFF7B9FE8)
    )
    val sectionReason = when {
        sectionEmoji == "✨" -> RecommendationReason(
            "Made for You",
            if (personalityArchetype.isNotBlank()) "Your $personalityArchetype taste profile shaped this pick."
            else "Based on your listening history and top artists.",
            (0.80f + (track.artist.length % 3) * 0.05f).coerceIn(0f, 1f), Color(0xFFE87B9F))
        sectionTitle.contains("focus", ignoreCase = true) || sectionTitle.contains("study", ignoreCase = true) ->
            RecommendationReason("Focus Fit", "Instrumentals or minimal lyrics — exactly what your brain needs.", 0.78f, Color(0xFF7BE8B8))
        sectionTitle.contains("morning", ignoreCase = true) ->
            RecommendationReason("Morning Energy", "A gentle ramp-up — not too loud, not too slow.",
                (0.72f + (track.name.length % 3) * 0.06f).coerceIn(0f, 1f), Color(0xFFE8C97B))
        sectionTitle.contains("evening", ignoreCase = true) || sectionTitle.contains("night", ignoreCase = true) ->
            RecommendationReason("Evening Wind-Down", "A softer pick to decompress as the day wraps up.",
                (0.68f + (track.name.length % 4) * 0.05f).coerceIn(0f, 1f), Color(0xFFE8C97B))
        sectionTitle.contains("feel better", ignoreCase = true) || sectionTitle.contains("uplift", ignoreCase = true) ->
            RecommendationReason("Mood Lift", "Gently nudging you upward — not forcing it.",
                (0.70f + (track.name.length % 3) * 0.06f).coerceIn(0f, 1f), Color(0xFFB87BE8))
        else ->
            RecommendationReason("Right Now Pick",
                "A solid fit for ${getTimeGreeting().lowercase()} — time and mood considered.",
                (0.62f + (track.name.length % 5) * 0.07f).coerceIn(0f, 1f), Color(0xFFE8C97B))
    }
    val artistKnown      = recentArtists.any { it.equals(track.artist, ignoreCase = true) }
    val streakBonus      = (listenStreakMinutes / 10).coerceAtMost(3) * 0.04f
    val familiarityScore = when {
        artistKnown                -> 0.85f + streakBonus
        recentArtists.isNotEmpty() -> 0.55f + (track.artist.length % 5) * 0.06f + streakBonus
        else                       -> 0.50f + (track.artist.length % 6) * 0.06f
    }
    return listOf(
        moodReason, sectionReason,
        RecommendationReason("Artist Familiarity",
            when {
                artistKnown                -> "You've been listening to ${track.artist} this session — a familiar voice."
                recentArtists.isNotEmpty() -> "${track.artist}'s style sits close to what you've been exploring."
                else                       -> "A new artist worth discovering based on your ${currentMood} taste."
            },
            familiarityScore.coerceIn(0f, 1f), Color(0xFF7BE8D8))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  HomeScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    spotifyRepository: SpotifyRepository? = null,
    petRepository: PetRepository? = null,
    themeState: AppThemeState = AppThemeState(),
    isPlayingMusic: Boolean = false,
    savedMoodKey: String? = null,
    onMoodSelected: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSpotify: () -> Unit = {},
    onNavigateToMusicPlayer: () -> Unit = {},
    onNavigateToEmotionChat: (pendingMessage: String?) -> Unit = {},
    onNavigateToMoodHistory: () -> Unit = {},
    onNavigateToPetShop: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onMascotVisibilityChanged: (Boolean) -> Unit = {},
    currentTab: Int = 0
) {
    val scope       = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scrollState = rememberScrollState()
    val context     = LocalContext.current

    val currentUser    = FirebaseAuth.getInstance().currentUser
    val rawDisplayName = currentUser?.displayName
    val displayName    = when {
        !rawDisplayName.isNullOrBlank() -> rawDisplayName
        !currentUser?.email.isNullOrBlank() ->
            currentUser!!.email!!.substringBefore("@").replaceFirstChar { it.uppercase() }
        else -> "Friend"
    }

    val moodHistoryRepo     = remember { MoodHistoryRepository() }
    val rlEngine            = remember { RLRecommendationEngine() }
    val passiveMoodDetector = remember { PassiveMoodDetector() }
    val personalityEngine   = remember { PetPersonalityEngine() }
    val personalityProfile  by personalityEngine.profile.collectAsState()
    val spotifyMusicRepo    = remember(spotifyRepository) {
        spotifyRepository?.let { SpotifyMusicRepository.getInstance(it, context) }
    }

    val spotifyAuthState by spotifyRepository?.authState?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val isSpotifyConnected = spotifyAuthState?.isAuthenticated == true

    var isLoading    by remember { mutableStateOf(false) }
    var sections     by remember { mutableStateOf<List<MusicSection>>(emptyList()) }
    var loadError    by remember { mutableStateOf<String?>(null) }
    var hasLoaded    by remember { mutableStateOf(false) }
    var tasteProfile by remember { mutableStateOf(UserTasteProfile()) }

    var mascotMood by remember {
        val initial = if (savedMoodKey != null)
            MascotMoodDetector.getMoodForKey(savedMoodKey).copy(isUserOverride = true)
        else MascotMoodDetector.detectMood()
        mutableStateOf(initial)
    }
    LaunchedEffect(savedMoodKey) {
        val key = savedMoodKey
        if (key != null && (!mascotMood.isUserOverride || mascotMood.mood != key))
            mascotMood = MascotMoodDetector.getMoodForKey(key).copy(isUserOverride = true)
    }

    var chatMessage         by remember { mutableStateOf<String?>(null) }
    var showMoodPicker      by remember { mutableStateOf(false) }
    var explainTrack        by remember { mutableStateOf<Track?>(null) }
    var explainSectionTitle by remember { mutableStateOf("") }
    var explainSectionEmoji by remember { mutableStateOf("") }

    val localPetRepo = remember { petRepository ?: PetRepository() }
    val petState     by localPetRepo.petState.collectAsState()

    var recentArtists      by remember { mutableStateOf(listOf<String>()) }
    var listenStreakMinutes by remember { mutableIntStateOf(0) }

    var hubCardBottomY by remember { mutableFloatStateOf(0f) }
    val scrollOffset   = scrollState.value
    val isHubVisible   = remember(hubCardBottomY, scrollOffset) { hubCardBottomY > scrollOffset }
    LaunchedEffect(isHubVisible) { onMascotVisibilityChanged(isHubVisible) }

    val checkInVm: MoodCheckInViewModel = viewModel(factory = MoodCheckInViewModel.factory(context))
    val checkInState by checkInVm.state.collectAsState()

    // ── Collect current track mood from ViewModel ─────────────────────────
    val trackMoodResult by (musicPlayerViewModel?.currentMood?.collectAsState()
        ?: remember { mutableStateOf(null) })

    LaunchedEffect(Unit) { localPetRepo.loadPet() }
    LaunchedEffect(Unit) { personalityEngine.loadOrCompute() }
    LaunchedEffect(mascotMood.mood) { localPetRepo.updateMood(mascotMood.mood) }
    LaunchedEffect(Unit) { moodHistoryRepo.saveMoodExplicit(mascotMood.mood, "App opened") }

    // ── Load / rebuild taste profile once per login ───────────────────────
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid ?: return@LaunchedEffect
        tasteProfile = try {
            val loaded = UserTasteProfileBuilder.load()
            if (loaded.totalTracksAnalysed < 10 || loaded.topArtists.isEmpty()) {
                UserTasteProfileBuilder.build()
            } else {
                loaded
            }
        } catch (_: Exception) { UserTasteProfile() }
        Log.d(TAG, "Taste profile: topArtists=${tasteProfile.topArtists.take(3)}, " +
                "lang=${tasteProfile.dominantLanguage}, genres=${tasteProfile.genreWeights.keys}")
    }

    val playerState  = musicPlayerViewModel?.playerState?.collectAsState()
    val currentTrack = playerState?.value?.currentTrack

    // ── Track metadata passive mood detection ─────────────────────────────
    LaunchedEffect(currentTrack?.id) {
        if (currentTrack != null) {
            passiveMoodDetector.analyseTrackMetadata(
                currentTrack.id, currentTrack.name, currentTrack.artist, currentTrack.album, ""
            )
            if (!recentArtists.contains(currentTrack.artist))
                recentArtists = (recentArtists + currentTrack.artist).takeLast(20)
        }
    }

    // ── Save each played track to Firestore playbackHistory ───────────────
    // Used by UserTasteProfileBuilder to learn artist/genre/language preferences
    LaunchedEffect(currentTrack?.id) {
        val track = currentTrack ?: return@LaunchedEffect
        val uid   = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        try {
            FirebaseFirestore.getInstance()
                .collection("playbackHistory")
                .document(uid)
                .collection("tracks")
                .add(
                    mapOf(
                        "trackId"    to track.id,
                        "title"      to track.name,
                        "artist"     to track.artist,
                        "albumArt"   to track.albumArtUrl,
                        "spotifyUri" to (track.spotifyUri ?: ""),
                        // Use the mood tag already on the track (assigned at parse time)
                        "mood"       to track.mood.label,
                        "skipped"    to false,
                        "timestamp"  to Timestamp.now()
                    )
                )
        } catch (_: Exception) {}
    }

    val passiveMood       by passiveMoodDetector.detectedMood.collectAsState()
    val passiveConfidence by passiveMoodDetector.confidence.collectAsState()

    LaunchedEffect(passiveMood, passiveConfidence) {
        if (savedMoodKey != null) {
            if (!mascotMood.isUserOverride || mascotMood.mood != savedMoodKey)
                mascotMood = MascotMoodDetector.getMoodForKey(savedMoodKey).copy(isUserOverride = true)
            return@LaunchedEffect
        }
        if (passiveConfidence >= 0.5f && isPlayingMusic) {
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
            if (savedMoodKey != null)
                mascotMood = MascotMoodDetector.getMoodForKey(savedMoodKey).copy(isUserOverride = true)
        }
        if (newId != null)
            rlEngine.onTrackStarted(mascotMood.mood, "", currentTrack?.durationMs ?: 0L)
        previousTrackId = newId
    }

    val listenCheckInMessages = remember {
        listOf(
            "You've been listening for a while 🎵 How are you feeling?",
            "Still vibing? Let me know how you're doing 😊",
            "Hey! Quick check-in — how's your mood right now? 🌟",
            "You've had quite a music session! Feeling good? 💫"
        )
    }
    var totalListeningMs by remember { mutableLongStateOf(0L) }
    var lastCheckInMs    by remember { mutableLongStateOf(0L) }
    var recentTrackMoods by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(passiveMood) {
        if (passiveMood.isNotEmpty() && passiveMood != "neutral")
            recentTrackMoods = (recentTrackMoods + passiveMood).takeLast(5)
    }
    LaunchedEffect(isPlayingMusic) {
        if (!isPlayingMusic) return@LaunchedEffect
        while (true) {
            delay(10_000L); totalListeningMs += 10_000L
            listenStreakMinutes = (totalListeningMs / 60_000L).toInt()
            val sim = if (recentTrackMoods.size >= 2) {
                recentTrackMoods.groupingBy { it }.eachCount().values.max().toFloat() / recentTrackMoods.size
            } else 0.5f
            val gap = when {
                sim >= 0.8f -> 30 * 60 * 1000L
                sim <= 0.3f -> 60 * 60 * 1000L
                else        -> 45 * 60 * 1000L
            }
            if (totalListeningMs - lastCheckInMs >= gap) {
                lastCheckInMs = totalListeningMs
                chatMessage   = listenCheckInMessages.random()
            }
        }
    }

    // ── Load music sections (re-runs on mood change or Spotify connect) ───
    var lastLoadedMood by remember { mutableStateOf("") }
    LaunchedEffect(isSpotifyConnected, mascotMood.mood) {
        if (!isSpotifyConnected || spotifyMusicRepo == null) {
            if (!isSpotifyConnected) { sections = emptyList(); hasLoaded = false }
            return@LaunchedEffect
        }
        val moodChanged  = lastLoadedMood != mascotMood.mood
        // Skip reload when already loaded and mood unchanged
        if (hasLoaded && sections.isNotEmpty() && !moodChanged) return@LaunchedEffect
        if (moodChanged && hasLoaded) hasLoaded = false
        lastLoadedMood = mascotMood.mood; isLoading = true; loadError = null
        try { rlEngine.loadState() } catch (_: Exception) {}
        try {
            val token = spotifyRepository?.getAccessToken()
            if (token == null) {
                loadError = "Session expired. Please reconnect Spotify."
                isLoading = false
                return@LaunchedEffect
            }

            // ── Rate-limit check — don't fire any request while blocked ─────
            if (spotifyMusicRepo.isRateLimited()) {
                val waitSec = spotifyMusicRepo.rateLimitRemainingSeconds()
                loadError = "⏳ Spotify rate limit active. Retrying in ${waitSec}s…"
                isLoading = false
                return@LaunchedEffect
            }

            // ── Personalised section generation ──────────────────────────
            // Cap at 5 sections; fetch SEQUENTIALLY to respect rate-limit gate.
            val recommended = MoodAwareRecommender.generateSections(
                currentMood        = mascotMood.mood,
                personalityProfile = personalityProfile,
                isUserOverride     = mascotMood.isUserOverride,
                rlEngine           = rlEngine,
                tasteProfile       = tasteProfile
            ).take(5)

            data class SectionDef(val title: String, val emoji: String, val query: String)

            val results = mutableListOf<MusicSection>()
            for (sec in recommended) {
                // Re-check rate limit before each section
                if (spotifyMusicRepo.isRateLimited()) {
                    val waitSec = spotifyMusicRepo.rateLimitRemainingSeconds()
                    Log.w("HomeScreen", "Rate-limited mid-load — stopping (${waitSec}s remaining)")
                    break
                }
                val def = SectionDef(sec.title, sec.emoji, sec.query)
                try {
                    val tracks = if (def.query == "PERSONALIZED") {
                        val top   = try { spotifyMusicRepo.getPersonalizedTracks(5) }
                        catch (_: Exception) { emptyList() }
                        val q     = rlEngine.getMoodSearchQuery(mascotMood.mood).ifBlank { mascotMood.mood }
                        val mood2 = try { spotifyMusicRepo.searchTracks(q, 5) }
                        catch (_: Exception) { emptyList() }
                        val b = mutableListOf<Track>()
                        for (i in 0 until maxOf(top.size, mood2.size)) {
                            if (i < top.size)  b.add(top[i])
                            if (i < mood2.size) b.add(mood2[i])
                        }
                        b.distinctBy { it.id }
                    } else {
                        spotifyMusicRepo.searchTracks(def.query, 10)
                    }
                    if (tracks.isNotEmpty()) {
                        results.add(MusicSection(def.title, def.emoji, tracks))
                        sections = results.toList()   // progressive render
                    }
                } catch (_: Exception) { /* skip failed section, continue */ }
            }
            sections  = results
            hasLoaded = true
            if (sections.isEmpty()) {
                if (spotifyMusicRepo.isRateLimited()) {
                    val waitSec = spotifyMusicRepo.rateLimitRemainingSeconds()
                    loadError = "⏳ Spotify requests are paused. Try again in ${waitSec}s."
                } else if (spotifyRepository?.getAccessToken() == null) {
                    loadError = "Session expired."
                    spotifyRepository?.markTokenExpired()
                } else {
                    loadError = "No results found. Tap to retry."
                }
            }
        } catch (e: Exception) { loadError = "Failed: ${e.message}" }
        isLoading = false
    }

    // ── Auto-retry poller — when rate-limited, re-check every 15 s ──────────
    // When the rate-limit window expires this sets hasLoaded=false which
    // re-triggers the main LaunchedEffect above for a clean retry.
    LaunchedEffect(isSpotifyConnected) {
        while (true) {
            delay(15_000)
            val repo = spotifyMusicRepo ?: continue
            if (!repo.isRateLimited() && loadError?.startsWith("⏳") == true) {
                // Rate-limit window just expired — clear error and trigger reload
                loadError  = null
                hasLoaded  = false
                sections   = emptyList()
            }
        }
    }

    val isDark             = themeState.isDark
    val primaryTextColor   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val secondaryTextColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF55556A)

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
            Box(
                modifier
                    .fillMaxSize()
                    .background(animatedMoodBrushLight(themeState))
                    .padding(padding)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {

                    // ── Top bar ───────────────────────────────────────────
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
                            Icon(Icons.Filled.Person, "Profile",
                                tint     = Color.White,
                                modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("${getTimeGreeting()}, $displayName!",
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color      = primaryTextColor)
                            Text(getMoodSubtitle(mascotMood.mood),
                                fontSize = 13.sp,
                                color    = secondaryTextColor)
                        }
                    }

                    // ── Spotify connect banner ────────────────────────────
                    if (!isSpotifyConnected) {
                        val errorMsg  = spotifyAuthState?.errorMessage
                        val isExpired = errorMsg != null &&
                                errorMsg.contains("expired", ignoreCase = true)
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clickable { onNavigateToSpotify() },
                            shape  = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpired) Color(0xFFFF9800) else Color(0xFF1DB954)
                            )
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isExpired)
                                    Icon(Icons.Filled.Warning, null, tint = Color.White)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isExpired) "Session expired" else "Connect Spotify",
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isExpired) "Tap to reconnect"
                                        else "Get personalized recommendations",
                                        color    = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Buddy Hub Card ────────────────────────────────────
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { coords ->
                                hubCardBottomY =
                                    coords.positionInParent().y + coords.size.height
                            }
                    ) {
                        BuddyHubCard(
                            mood               = mascotMood,
                            petState           = petState,
                            petRepository      = localPetRepo,
                            chatMessage        = chatMessage,
                            onChangeMood       = { showMoodPicker = true },
                            onEditMascot       = { onNavigateToPetShop() },
                            onOpenChat         = { msg -> onNavigateToEmotionChat(msg) },
                            isPlayingMusic     = isPlayingMusic,
                            personalityProfile = personalityProfile,
                            checkInState       = checkInState,
                            onCheckIn          = { mood -> checkInVm.checkIn(mood) },
                            onCheckInRaw       = { key -> checkInVm.checkInRaw(key) },
                            onMoodPick         = { key ->
                                // 1. Update mascot mood immediately (isUserOverride = true prevents passive override)
                                val old = mascotMood.mood
                                mascotMood = MascotMoodDetector.getMoodForKey(key).copy(isUserOverride = true)
                                // 2. Persist mood key → MoodSyncApp.savedMoodKey updates → themeManager.updateMood fires
                                onMoodSelected(key)
                                // 3. updateMood on petRepo triggers LaunchedEffect(mascotMood.mood) above
                                //    which calls localPetRepo.updateMood → petState.mood → MoodSyncApp theme update
                                localPetRepo.updateMood(key)
                                // 4. Reset so music sections reload for the new mood
                                hasLoaded = false
                                chatMessage = null
                                // 5. Log to mood history
                                moodHistoryRepo.saveMoodExplicit(key, "Emotion picker: changed from $old to $key")
                                // 6. Tell RL engine mood was manually overridden
                                scope.launch {
                                    rlEngine.recordReward(RewardEvent(RewardType.MOOD_OVERRIDE, old, null))
                                }
                            },
                            themeState         = themeState
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Spacer(Modifier.height(24.dp))

                    // ── Music sections ────────────────────────────────────
                    when {
                        isLoading -> Box(
                            Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Loading your music...",
                                    color    = secondaryTextColor,
                                    fontSize = 14.sp)
                            }
                        }

                        !isSpotifyConnected -> Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Connect Spotify to see recommendations",
                                color = secondaryTextColor)
                        }

                        loadError != null -> RateLimitOrErrorCard(
                            error          = loadError ?: "",
                            isRateLimited  = spotifyMusicRepo?.isRateLimited() == true,
                            remainingSecs  = spotifyMusicRepo?.rateLimitRemainingSeconds() ?: 0L,
                            onClearAndRetry = {
                                spotifyMusicRepo?.clearRateLimit()
                                hasLoaded = false
                                loadError = null
                                sections  = emptyList()
                            },
                            onRetry = {
                                hasLoaded = false
                                loadError = null
                                sections  = emptyList()
                            }
                        )

                        sections.isEmpty() && hasLoaded -> Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recommendations yet. Try changing your mood!",
                                color = secondaryTextColor)
                        }

                        else -> sections.forEach { section ->
                            Text(
                                "${section.emoji} ${section.title}",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = primaryTextColor,
                                modifier   = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding        = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(section.tracks) { track ->
                                    SmallTrackCard(
                                        track   = track,
                                        all     = section.tracks,
                                        vm      = musicPlayerViewModel,
                                        onNav   = onNavigateToMusicPlayer,
                                        isDark  = isDark,
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

    // ── Recommendation explanation sheet ─────────────────────────────────
    val explainTrackSnapshot = explainTrack
    if (explainTrackSnapshot != null) {
        val archetypeLabel = try {
            personalityProfile?.toString()
                ?.substringAfter("topArchetype=")
                ?.substringBefore(",")
                ?.substringBefore(")")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
        } catch (_: Exception) { "" }
        RecommendationExplanationSheet(
            track                = explainTrackSnapshot,
            sectionTitle         = explainSectionTitle,
            sectionEmoji         = explainSectionEmoji,
            currentMood          = mascotMood.mood,
            personalityArchetype = archetypeLabel,
            recentArtists        = recentArtists,
            listenStreakMinutes  = listenStreakMinutes,
            onDismiss            = { explainTrack = null },
            onPlay               = {
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

    // ── Mood picker dialog ────────────────────────────────────────────────
    if (showMoodPicker) {
        MoodPickerDialog(
            currentMood = mascotMood.mood,
            isDark      = isDark,
            onSelect    = { selected ->
                val old = mascotMood.mood
                mascotMood = MascotMoodDetector.getMoodForKey(selected).copy(isUserOverride = true)
                onMoodSelected(selected)
                chatMessage    = null
                showMoodPicker = false
                moodHistoryRepo.saveMoodExplicit(selected, "Changed from $old to $selected")
                scope.launch {
                    rlEngine.recordReward(RewardEvent(RewardType.MOOD_OVERRIDE, old, null))
                }
            },
            onDismiss = { showMoodPicker = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendationExplanationSheet(
    track: Track, sectionTitle: String, sectionEmoji: String, currentMood: String,
    personalityArchetype: String, recentArtists: List<String>, listenStreakMinutes: Int,
    onDismiss: () -> Unit, onPlay: () -> Unit
) {
    val reasons = remember(track.id, sectionTitle, currentMood, personalityArchetype) {
        getReasonsForSection(
            sectionTitle, sectionEmoji, track, currentMood,
            personalityArchetype, recentArtists, listenStreakMinutes
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f)))
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(Modifier.size(64.dp), shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(4.dp)) {
                        if (track.albumArtUrl.isNotEmpty())
                            AsyncImage(model = track.albumArtUrl, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(Color(0xFF2A2A3E)),
                            contentAlignment = Alignment.Center) { Text("🎵", fontSize = 24.sp) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.name, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f)) {
                            Text("$sectionEmoji $sectionTitle",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Why this landed on your ${currentMood.replaceFirstChar { it.uppercase() }} playlist",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.55f), lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                reasons.forEach { r -> ReasonBar(r); Spacer(Modifier.height(16.dp)) }
                Spacer(Modifier.height(6.dp))
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

@Composable
private fun ReasonBar(reason: RecommendationReason) {
    val animatedScore by animateFloatAsState(reason.score, tween(800, easing = EaseOutCubic), label = "bar")
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${reason.label}: ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(reason.description, fontSize = 11.5.sp, color = reason.barColor,
                fontWeight = FontWeight.Normal, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(animatedScore).fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(reason.barColor))
        }
    }
}

@Composable
private fun SmallTrackCard(
    track: Track, all: List<Track>, vm: MusicPlayerViewModel?,
    onNav: () -> Unit, isDark: Boolean = false, onLongPress: () -> Unit = {}
) {
    val nameColor   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val artistColor = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val cardBg      = if (isDark) Color(0xFF2A2A3E) else Color(0xFFEEEEEE)
    Column(
        Modifier.width(130.dp).pointerInput(track.id) {
            detectTapGestures(
                onTap      = { vm?.loadTrack(track, all); onNav() },
                onLongPress = { onLongPress() }
            )
        }
    ) {
        Card(Modifier.size(130.dp), shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(4.dp)) {
            if (track.albumArtUrl.isNotEmpty())
                AsyncImage(model = track.albumArtUrl, contentDescription = track.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(cardBg),
                contentAlignment = Alignment.Center) { Text("🎵", fontSize = 32.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 11.sp, color = artistColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BottomNavBar(
    currentTab: Int,
    onHome:    () -> Unit,
    onSearch:  () -> Unit,
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
    val p      = themeState.activePalette
    val isDark = themeState.isDark
    val speed  = themeState.transitionSpeed
    val navBg      by animateColorAsState(if (isDark) p.darkBottom else p.lightBottom, tween(speed), label = "navBg")
    val selColor   by animateColorAsState(if (isDark) Color(0xFFFFFFFF) else Color(0xFF1A1A2E),  tween(speed), label = "navSel")
    val unselColor by animateColorAsState(if (isDark) Color(0xFF888899) else Color(0xFF757585),  tween(speed), label = "navUnsel")
    val divColor   by animateColorAsState(
        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
        tween(speed), label = "navDiv")
    Column {
        HorizontalDivider(thickness = 0.6.dp, color = divColor)
        NavigationBar(
            containerColor = navBg,
            tonalElevation = 0.dp,
            modifier       = Modifier.height(72.dp)
        ) {
            navItems.forEachIndexed { i, (icon, label, action) ->
                val sel = currentTab == i
                NavigationBarItem(
                    selected        = sel,
                    onClick         = action,
                    icon  = { Icon(icon, label, modifier = Modifier.size(22.dp)) },
                    label = {
                        Text(label, fontSize = 10.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines   = 1)
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor   = selColor,
                        selectedTextColor   = selColor,
                        unselectedIconColor = unselColor,
                        unselectedTextColor = unselColor,
                        indicatorColor      = Color.Transparent
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rate-limit / error card shown in the HomeScreen music section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RateLimitOrErrorCard(
    error          : String,
    isRateLimited  : Boolean,
    remainingSecs  : Long,
    onClearAndRetry: () -> Unit,
    onRetry        : () -> Unit
) {
    // Live countdown — ticks every second while rate-limited
    var countdown by remember(remainingSecs) { mutableStateOf(remainingSecs) }
    LaunchedEffect(isRateLimited, remainingSecs) {
        if (!isRateLimited) return@LaunchedEffect
        countdown = remainingSecs
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
    }

    val cardColor   = if (isRateLimited) Color(0xFF37474F) else Color(0xFFFF9800)
    val titleText   = if (isRateLimited) "Spotify rate limit" else "Couldn't load music"
    val bodyText    = if (isRateLimited)
        "Spotify asked us to wait. Auto-retrying when the window expires."
    else error

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(titleText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(bodyText,  color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            if (isRateLimited && countdown > 0) {
                Spacer(Modifier.height(10.dp))
                // Countdown progress bar
                val totalSecs = remainingSecs.coerceAtLeast(1L).toFloat()
                val progress  = (countdown.toFloat() / totalSecs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress         = { 1f - progress },
                    modifier         = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color            = Color(0xFF80CBC4),
                    trackColor       = Color.White.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(6.dp))
                val mins = countdown / 60
                val secs = countdown % 60
                Text(
                    if (mins > 0) "Retrying in ${mins}m ${secs}s…" else "Retrying in ${secs}s…",
                    color    = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                // "I've waited — try now" button in case SharedPrefs has a stale timestamp
                OutlinedButton(
                    onClick = onClearAndRetry,
                    border  = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Force retry now", color = Color.White, fontSize = 13.sp)
                }
            } else if (!isRateLimited) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRetry,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MiniMusicPlayer(
    vm:         MusicPlayerViewModel?,
    onNav:      () -> Unit,
    themeState: AppThemeState = AppThemeState()
) {
    val ps = vm?.playerState?.collectAsState()
    val t  = ps?.value?.currentTrack ?: return
    val p  = themeState.activePalette
    val bg = if (themeState.isDark) p.darkSurface else p.darkTop.copy(alpha = 0.95f)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onNav() },
        colors    = CardDefaults.cardColors(containerColor = bg),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(Modifier.size(40.dp), shape = RoundedCornerShape(8.dp)) {
                if (t.albumArtUrl.isNotEmpty())
                    AsyncImage(t.albumArtUrl, null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(p.accent.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center) { Text("🎵", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name,   fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm?.togglePlayPause() }) {
                Icon(
                    if (ps.value.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "PlayPause", tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProfileDrawerContent(
    displayName:  String,
    email:        String,
    isDark:       Boolean = false,
    onSettings:   () -> Unit,
    onSpotify:    () -> Unit,
    onMoodHistory: () -> Unit,
    onSignOut:    () -> Unit
) {
    val bg  = if (isDark) Color(0xFF1C1C2E) else Color.White
    val tc  = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val sc  = if (isDark) Color(0xFF888899) else Color(0xFF666677)
    val av  = if (isDark) Color(0xFF2A2A4A) else Color(0xFF1A1A2E)
    val div = if (isDark) Color(0xFF2A2A3A) else Color(0xFFEEEEEE)
    ModalDrawerSheet(Modifier.width(300.dp), drawerContainerColor = bg) {
        Column(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(av),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tc)
            Text(email, fontSize = 13.sp, color = sc)
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = div)
            Spacer(Modifier.height(16.dp))
            DrawerItem(Icons.Filled.Settings,  "Settings",        onSettings,    tc)
            DrawerItem(Icons.Filled.Link,      "Connect Spotify", onSpotify,     tc)
            DrawerItem(Icons.Filled.BarChart,  "Mood Recap",      onMoodHistory, tc)
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = div)
            Spacer(Modifier.height(12.dp))
            DrawerItem(Icons.AutoMirrored.Filled.Logout, "Sign Out", onSignOut, Color.Red)
        }
    }
}

@Composable
private fun DrawerItem(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit,
    tint:    Color
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
    isDark:      Boolean = false,
    onSelect:    (String) -> Unit,
    onDismiss:   () -> Unit
) {
    val textColor     = if (isDark) Color(0xFFE8E8F0) else Color(0xFF1A1A2E)
    val subColor      = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666677)
    val selectedColor = Color(0xFF6A5ACD)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?", fontWeight = FontWeight.Bold, color = textColor) },
        text  = {
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
                            mood.replaceFirstChar { it.uppercase() },
                            fontSize   = 16.sp,
                            fontWeight = if (mood == currentMood) FontWeight.Bold else FontWeight.Normal,
                            color      = if (mood == currentMood) selectedColor else textColor
                        )
                        if (mood == currentMood) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.Check, null,
                                tint     = selectedColor,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = subColor) }
        }
    )
}

private fun getTimeGreeting(): String =
    when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Good night"
    }

private fun getMoodSubtitle(mood: String): String = when (mood.lowercase()) {
    "happy"     -> "You're in a great mood today ✨"
    "sad"       -> "Here's something to lift you up 🌧️"
    "energetic" -> "Let's keep that energy going ⚡"
    "relaxed"   -> "Time to unwind 🌿"
    "focused"   -> "Stay in the zone 🎯"
    "romantic"  -> "Setting the mood 💫"
    "tired"     -> "Gentle vibes incoming 🌙"
    else        -> "What's your vibe today? 🎶"
}