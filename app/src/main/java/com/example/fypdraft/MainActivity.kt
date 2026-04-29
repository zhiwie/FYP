package com.example.fypdraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fypdraft.viewmodel.AuthViewModel
import com.example.fypdraft.viewmodel.ChatGPTViewModel
import com.example.fypdraft.viewmodel.MoodHistoryViewModel
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.example.fypdraft.viewmodel.SpotifyViewModel
import com.example.fypdraft.data.repository.SpotifyMusicRepository
import com.example.fypdraft.data.repository.SpotifyRepository
import com.example.fypdraft.model.PetRepository
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.ThemeManager
import com.example.fypdraft.view.*
import com.google.firebase.auth.FirebaseAuth
import com.spotify.sdk.android.auth.AuthorizationClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Screen {
    const val WELCOME       = "welcome"
    const val LOGIN         = "login"
    const val SIGNUP        = "signup"
    const val NICKNAME      = "nickname"
    const val CONNECT_MUSIC = "connect_music"
    const val RESET         = "reset"
    const val HOME          = "home"
    const val SEARCH        = "search"
    const val FRIENDS       = "friends"
    const val LIBRARY       = "library"
    const val SETTINGS      = "settings"
    const val SPOTIFY       = "spotify"
    const val MUSIC_PLAYER  = "musicplayer"
    const val EMOTION_CHAT  = "emotionchat"
    const val PET_SHOP      = "petshop"
    const val THEME         = "theme"
    const val MOOD_HISTORY  = "mood_history"

    fun tabIndex(screen: String): Int = when (screen) {
        HOME -> 0; SEARCH -> 1; FRIENDS -> 2; LIBRARY -> 3; else -> -1
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var musicPlayerViewModel: MusicPlayerViewModel
    var spotifyViewModel: SpotifyViewModel? = null
    private val TAG = "MainActivity"
    val spotifyRepository: SpotifyRepository by lazy { SpotifyRepository.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            musicPlayerViewModel = ViewModelProvider(
                this,
                MusicPlayerViewModelFactory(application)
            )[MusicPlayerViewModel::class.java]
            musicPlayerViewModel.bindMusicService(this)
            enableEdgeToEdge()
            setContent {
                FYPDraftTheme {
                    Surface(Modifier.fillMaxSize()) {
                        AppInitializer(this@MainActivity, musicPlayerViewModel)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            setContent {
                FYPDraftTheme {
                    Surface(Modifier.fillMaxSize()) {
                        ErrorScreen("Failed: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }

    @Deprecated("Required for Spotify Auth SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyViewModel.SPOTIFY_AUTH_REQUEST_CODE) {
            spotifyViewModel?.handleAuthResult(
                resultCode,
                AuthorizationClient.getResponse(resultCode, data)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            musicPlayerViewModel.disconnectSpotifyPlayback()
            musicPlayerViewModel.unbindMusicService(this)
            musicPlayerViewModel.cleanupMLModels()
        } catch (_: Exception) {}
    }
}

@Composable
fun AppInitializer(activity: MainActivity, musicPlayerViewModel: MusicPlayerViewModel) {
    var isInitialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                musicPlayerViewModel.initializeMLModels()
                if (activity.spotifyRepository.isAuthenticated()) {
                    musicPlayerViewModel.connectSpotifyPlayback(activity)
                    musicPlayerViewModel.setSpotifyMusicRepo(
                        SpotifyMusicRepository.getInstance(activity.spotifyRepository, activity)
                    )
                }
                delay(500)
            } catch (_: Exception) {}
            isInitialized = true
        }
    }
    if (!isInitialized) LoadingScreen()
    else {
        val spotifyViewModel = remember {
            SpotifyViewModel(activity).also {
                activity.spotifyViewModel = it
                it.restoreAuthState()
            }
        }
        MoodSyncApp(activity, spotifyViewModel, musicPlayerViewModel, activity.spotifyRepository)
    }
}

@Composable
fun MoodSyncApp(
    activity: MainActivity,
    spotifyViewModel: SpotifyViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    spotifyRepository: SpotifyRepository
) {
    val authViewModel: AuthViewModel       = viewModel()
    val chatGPTViewModel: ChatGPTViewModel = viewModel()
    val petRepository    = remember { PetRepository() }
    val petState         by petRepository.petState.collectAsState()
    val themeManager     = remember { ThemeManager(activity) }
    val themeState       by themeManager.themeState.collectAsState()

    LaunchedEffect(petState.mood) { themeManager.updateMood(petState.mood) }
    LaunchedEffect(Unit) { petRepository.loadPet() }

    // ── MOOD PERSISTENCE — lifted here so it survives all navigation ──────────
    var savedMoodKey by rememberSaveable { mutableStateOf<String?>(null) }

    val startScreen = remember {
        try { if (authViewModel.isUserLoggedIn()) Screen.HOME else Screen.WELCOME }
        catch (_: Exception) { Screen.WELCOME }
    }
    var backStack by rememberSaveable { mutableStateOf(listOf(startScreen)) }
    val currentScreen = backStack.lastOrNull() ?: Screen.HOME
    val currentTab    = Screen.tabIndex(currentScreen)

    fun navigateTo(s: String)   { if (backStack.lastOrNull() != s) backStack = backStack + s }
    fun navigateBack(): Boolean { if (backStack.size <= 1) return false; backStack = backStack.dropLast(1); return true }
    fun replaceStack(s: String) { backStack = listOf(s) }
    fun bottomNav(dest: String) { if (backStack.lastOrNull() != dest) backStack = listOf(Screen.HOME, dest) }

    BackHandler(enabled = backStack.size > 1) { navigateBack() }

    val spotifyAuthState by spotifyViewModel.authState.collectAsState()
    LaunchedEffect(spotifyAuthState.isAuthenticated) {
        if (spotifyAuthState.isAuthenticated) {
            musicPlayerViewModel.connectSpotifyPlayback(activity)
            musicPlayerViewModel.setSpotifyMusicRepo(SpotifyMusicRepository.getInstance(spotifyRepository, activity))
        }
    }

    val playerState    by musicPlayerViewModel.playerState.collectAsState()
    val isPlaying      = playerState.isPlaying
    val currentTrackId = playerState.currentTrack?.id
    LaunchedEffect(currentTrackId) { if (currentTrackId != null) petRepository.addXP(10) }

    // ── Floating mascot user preference ──────────────────────────────────────
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var floatingMascotEnabled by remember {
        mutableStateOf(
            ctx.getSharedPreferences("moodsync_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("mascot_visible", true)
        )
    }

    // ── Floating pet visibility ───────────────────────────────────────────────
    var isMascotWidgetVisible by remember { mutableStateOf(true) }
    val showFloatingPet = when {
        currentScreen in listOf(
            Screen.WELCOME, Screen.LOGIN, Screen.SIGNUP,
            Screen.NICKNAME, Screen.CONNECT_MUSIC, Screen.RESET,
        ) -> false
        !floatingMascotEnabled                              -> false
        currentScreen == Screen.HOME                       -> !isMascotWidgetVisible
        else                                               -> true
    }

    // ── Sign-out helper ───────────────────────────────────────────────────────
    fun handleSignOut() {
        chatGPTViewModel.clearConversation()
        authViewModel.signOut()
        savedMoodKey = null
        replaceStack(Screen.WELCOME)
    }

    Box(Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.WELCOME -> WelcomeScreen(
                onSignUp = { navigateTo(Screen.SIGNUP) },
                onLogIn  = { navigateTo(Screen.LOGIN)  }
            )

            Screen.LOGIN -> LoginScreen(
                viewModel        = authViewModel,
                onLoginSuccess   = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    chatGPTViewModel.reloadHistoryForUser(uid)
                    replaceStack(Screen.HOME)
                },
                onForgotPassword = { navigateTo(Screen.RESET) }
            )

            Screen.SIGNUP -> SignUpScreen(
                viewModel         = authViewModel,
                onSignUpSuccess   = { navigateTo(Screen.NICKNAME) },
                onNavigateToLogin = { navigateBack() }
            )

            Screen.NICKNAME      -> NicknameScreen(onContinue = { _ -> navigateTo(Screen.CONNECT_MUSIC) })
            Screen.CONNECT_MUSIC -> ConnectMusicScreen(
                spotifyViewModel = spotifyViewModel,
                onSkip      = { replaceStack(Screen.HOME) },
                onConnected = { replaceStack(Screen.HOME) }
            )
            Screen.RESET -> ResetPWScreen(
                viewModel      = authViewModel,
                onResetSuccess = { navigateBack() },
                onBack         = { navigateBack() }
            )

            Screen.HOME -> HomeScreen(
                musicPlayerViewModel      = musicPlayerViewModel,
                spotifyRepository         = spotifyRepository,
                petRepository             = petRepository,
                themeState                = themeState,
                isPlayingMusic            = isPlaying,
                savedMoodKey              = savedMoodKey,
                onMoodSelected            = { key -> savedMoodKey = key },
                onNavigateToSearch        = { bottomNav(Screen.SEARCH) },
                onNavigateToFriends       = { bottomNav(Screen.FRIENDS) },
                onNavigateToLibrary       = { bottomNav(Screen.LIBRARY) },
                onNavigateToSettings      = { navigateTo(Screen.SETTINGS) },
                onNavigateToSpotify       = { navigateTo(Screen.SPOTIFY) },
                onNavigateToMusicPlayer   = { navigateTo(Screen.MUSIC_PLAYER) },
                onNavigateToEmotionChat   = { navigateTo(Screen.EMOTION_CHAT) },
                onNavigateToMoodHistory   = { navigateTo(Screen.MOOD_HISTORY) },
                onSignOut                 = { handleSignOut() },
                onMascotVisibilityChanged = { visible -> isMascotWidgetVisible = visible },
                currentTab                = 0
            )

            Screen.SEARCH -> SearchScreen(
                musicPlayerViewModel    = musicPlayerViewModel,
                spotifyRepository       = spotifyRepository,
                themeState              = themeState,
                onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) },
                onNavigateToHome        = { navigateBack() },
                onNavigateToFriends     = { bottomNav(Screen.FRIENDS) },
                onNavigateToLibrary     = { bottomNav(Screen.LIBRARY) },
                onNavigateToSpotify     = { navigateTo(Screen.SPOTIFY) },
                currentTab              = 1
            )

            Screen.FRIENDS -> FriendsScreen(
                musicPlayerViewModel    = musicPlayerViewModel,
                petState                = petState,
                spotifyRepository       = spotifyRepository,
                themeState              = themeState,
                onNavigateToHome        = { navigateBack() },
                onNavigateToSearch      = { bottomNav(Screen.SEARCH) },
                onNavigateToLibrary     = { bottomNav(Screen.LIBRARY) },
                onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) },
                currentTab              = 2
            )

            Screen.LIBRARY -> LibraryScreen(
                musicPlayerViewModel    = musicPlayerViewModel,
                spotifyRepository       = spotifyRepository,
                themeState              = themeState,
                onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) },
                onNavigateToHome        = { navigateBack() },
                onNavigateToSearch      = { bottomNav(Screen.SEARCH) },
                onNavigateToFriends     = { bottomNav(Screen.FRIENDS) },
                onNavigateToSpotify     = { navigateTo(Screen.SPOTIFY) },
                currentTab              = 3
            )

            Screen.SETTINGS -> SettingsScreen(
                themeState              = themeState,
                themeManager            = themeManager,
                onBack                  = { navigateBack() },
                onSignOut               = { handleSignOut() },
                onNavigateToTheme       = { navigateTo(Screen.THEME) },
                floatingMascotEnabled   = floatingMascotEnabled,
                onFloatingMascotToggle  = { enabled ->
                    floatingMascotEnabled = enabled
                    ctx.getSharedPreferences("moodsync_settings", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("mascot_visible", enabled).apply()
                }
            )

            Screen.SPOTIFY       -> SpotifyConnectionScreen(spotifyViewModel = spotifyViewModel, onBack = { navigateBack() })
            Screen.THEME         -> ThemeSelectionScreen(themeManager = themeManager, onBack = { navigateBack() })
            Screen.MUSIC_PLAYER  -> MusicPlayerScreen(viewModel = musicPlayerViewModel, themeState = themeState, onBack = { navigateBack() })

            Screen.EMOTION_CHAT -> EmotionChatScreen(
                chatViewModel           = chatGPTViewModel,
                musicPlayerViewModel    = musicPlayerViewModel,
                petState                = petState,
                petRepository           = petRepository,
                themeState              = themeState,
                themeManager            = themeManager,
                onBack                  = { navigateBack() },
                onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) }
            )

            Screen.PET_SHOP -> PetShopScreen(
                petState                = petState,
                petRepository           = petRepository,
                onBack                  = { navigateBack() },
                onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) }
            )

            Screen.MOOD_HISTORY -> {
                val moodHistoryViewModel: MoodHistoryViewModel = viewModel()
                MoodHistoryScreen(
                    viewModel  = moodHistoryViewModel,
                    themeState = themeState,
                    onBack     = { navigateBack() }
                )
            }
        }

        // ── Floating pet: tap opens the Customise buddy sheet ─────────────
        var showPetCustomise by remember { mutableStateOf(false) }

        if (showFloatingPet) {
            SmartFloatingPet(
                petState      = petState,
                petRepository = petRepository,
                isPlaying     = isPlaying,
                onTap         = { showPetCustomise = true }
            )
        }

        if (showPetCustomise) {
            PetCustomiseSheet(
                petState      = petState,
                petRepository = petRepository,
                onDismiss     = { showPetCustomise = false },
                onVisitShop   = { showPetCustomise = false; navigateTo(Screen.PET_SHOP) }
            )
        }
    }
}

@Composable fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: $msg", color = MaterialTheme.colorScheme.error)
    }
}

class MusicPlayerViewModelFactory(private val app: android.app.Application) :
    ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return MusicPlayerViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}