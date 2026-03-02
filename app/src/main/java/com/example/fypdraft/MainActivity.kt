package com.example.fypdraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fypdraft.model.AuthViewModel
import com.example.fypdraft.model.ChatGPTViewModel
import com.example.fypdraft.model.MusicPlayerViewModel
import com.example.fypdraft.model.SpotifyViewModel
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.view.*
import com.spotify.sdk.android.auth.AuthorizationClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Screen name constants ─────────────────────────────────────────────────────

object Screen {
    const val LOGIN        = "login"
    const val SIGNUP       = "signup"
    const val RESET        = "reset"
    const val HOME         = "home"
    const val SETTINGS     = "settings"
    const val SPOTIFY      = "spotify"
    const val MUSIC_PLAYER = "musicplayer"
    const val EMOTION_CHAT = "emotionchat"

    // Bottom nav tabs replace each other instead of stacking
    val BOTTOM_NAV_SCREENS = setOf(HOME, SETTINGS, SPOTIFY)
}

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var musicPlayerViewModel: MusicPlayerViewModel
    var spotifyViewModel: SpotifyViewModel? = null
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            musicPlayerViewModel = ViewModelProvider(
                this,
                MusicPlayerViewModelFactory(application)
            )[MusicPlayerViewModel::class.java]

            enableEdgeToEdge()
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppInitializer(
                            activity             = this@MainActivity,
                            musicPlayerViewModel = musicPlayerViewModel
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ErrorScreen("Failed to initialize: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Deprecated("Required for Spotify Auth SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SpotifyViewModel.SPOTIFY_AUTH_REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)
            spotifyViewModel?.handleAuthResult(resultCode, response)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { musicPlayerViewModel.cleanupMLModels() } catch (e: Exception) { }
    }
}

// ── AppInitializer ────────────────────────────────────────────────────────────

@Composable
fun AppInitializer(
    activity: MainActivity,
    musicPlayerViewModel: MusicPlayerViewModel
) {
    var isInitialized by remember { mutableStateOf(false) }
    var initError     by remember { mutableStateOf<String?>(null) }
    val scope         = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                musicPlayerViewModel.initializeMLModels()
                delay(500)
            } catch (e: Exception) {
                initError = e.message
            }
            isInitialized = true
        }
    }

    if (!isInitialized) {
        LoadingScreen()
    } else {
        val spotifyViewModel = remember {
            SpotifyViewModel(activity).also { activity.spotifyViewModel = it }
        }
        MoodSyncApp(
            activity             = activity,
            spotifyViewModel     = spotifyViewModel,
            musicPlayerViewModel = musicPlayerViewModel,
            mlInitError          = initError
        )
    }
}

// ── Navigation state ──────────────────────────────────────────────────────────
//
// Stored as a plain Compose state list so it survives recomposition.
// We use List<String> + mutableStateOf so that every assignment (=) triggers
// recomposition reliably — avoiding the removeLast() / dropLast() version
// mismatch that caused crashes on some Compose versions.

@Composable
fun MoodSyncApp(
    activity: MainActivity,
    spotifyViewModel: SpotifyViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    mlInitError: String? = null
) {
    val authViewModel:    AuthViewModel    = viewModel()
    val chatGPTViewModel: ChatGPTViewModel = viewModel()

    // Single source of truth: a plain immutable list wrapped in mutableStateOf.
    // Every navigation op replaces the whole list — no mutation methods needed,
    // so there is zero risk of hitting missing API methods (removeLast, etc.).
    var backStack by remember {
        val start = try {
            if (authViewModel.isUserLoggedIn()) Screen.HOME else Screen.LOGIN
        } catch (e: Exception) {
            Screen.LOGIN
        }
        mutableStateOf(listOf(start))
    }

    val currentScreen = backStack.lastOrNull() ?: Screen.LOGIN

    // ── Navigation helpers ────────────────────────────────────────────────────

    /** Push a new screen. No-op if already on top. */
    fun navigateTo(screen: String) {
        if (backStack.lastOrNull() == screen) return
        backStack = backStack + screen
        Log.d("Nav", "→ $screen   stack=$backStack")
    }

    /** Pop the top screen. If only one screen remains the system handles back. */
    fun navigateBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)   // dropLast is a stdlib function — always available
            Log.d("Nav", "← back   stack=$backStack")
        }
    }

    /**
     * Replace the entire stack with a single screen.
     * Used after login / logout so the user cannot press back into auth screens.
     */
    fun replaceStack(screen: String) {
        backStack = listOf(screen)
        Log.d("Nav", "↺ reset → $screen")
    }

    /**
     * Bottom-nav tab navigation.
     *
     * Tabs are peers — they replace each other, never stack.
     * Pressing back from any bottom-nav screen exits the app (stack size == 1).
     */
    fun navigateFromBottomNav(destination: String) {
        if (backStack.lastOrNull() == destination) return

        // Drop any screens that sit above a bottom-nav screen (e.g. MusicPlayer
        // opened from Settings), then swap the bottom-nav screen for destination.
        val trimmed = backStack.dropLastWhile { it !in Screen.BOTTOM_NAV_SCREENS }
        // trimmed now ends at a bottom-nav screen (or is empty if none found)
        val base = if (trimmed.isNotEmpty()) trimmed.dropLast(1) else emptyList()
        backStack = base + destination
        Log.d("Nav", "⊡ tab → $destination   stack=$backStack")
    }

    // ── Android back button ───────────────────────────────────────────────────
    // Intercept back only when there is more than one screen on the stack.
    // When stack size == 1, the system default fires → app exits.
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    // ── Screen routing ────────────────────────────────────────────────────────

    when (currentScreen) {

        // Auth screens
        Screen.LOGIN -> LoginScreen(
            viewModel        = authViewModel,
            onLoginSuccess   = { replaceStack(Screen.HOME) },
            onForgotPassword = { navigateTo(Screen.RESET) },
            onCreateAccount  = { navigateTo(Screen.SIGNUP) },
            onTryDemo        = { replaceStack(Screen.HOME) }
        )

        Screen.SIGNUP -> SignUpScreen(
            viewModel         = authViewModel,
            onSignUpSuccess   = { navigateBack() },
            onNavigateToLogin = { navigateBack() }
        )

        Screen.RESET -> ResetPWScreen(
            viewModel      = authViewModel,
            onResetSuccess = { navigateBack() },
            onBack         = { navigateBack() }
        )

        // Home — root screen for logged-in users
        Screen.HOME -> HomeScreen(
            musicPlayerViewModel    = musicPlayerViewModel,
            onNavigateToLibrary     = { /* TODO */ },
            onNavigateToSettings    = { navigateFromBottomNav(Screen.SETTINGS) },
            onNavigateToSpotify     = { navigateFromBottomNav(Screen.SPOTIFY) },
            onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) },
            onNavigateToEmotionChat = { navigateTo(Screen.EMOTION_CHAT) },
            onSignOut = {
                authViewModel.signOut()
                replaceStack(Screen.LOGIN)
            }
        )

        // Bottom-nav peer screens — back exits app
        Screen.SETTINGS -> SettingsScreen(
            onBack    = { navigateBack() },
            onSignOut = {
                authViewModel.signOut()
                replaceStack(Screen.LOGIN)
            }
        )

        Screen.SPOTIFY -> SpotifyConnectionScreen(
            spotifyViewModel = spotifyViewModel,
            onBack           = { navigateBack() }
        )

        // Full-screen destinations pushed on top of the stack
        Screen.MUSIC_PLAYER -> MusicPlayerScreen(
            viewModel = musicPlayerViewModel,
            onBack    = { navigateBack() }
        )

        // EmotionChat — chat with GPT, song cards push MusicPlayer on top
        // back: musicplayer → emotionchat → home  ✓
        Screen.EMOTION_CHAT -> EmotionChatScreen(
            chatViewModel = chatGPTViewModel,
            onBack        = { navigateBack() },
            onSongClick   = { song ->
                val track = Track(
                    id          = "${song.artist}-${song.title}",
                    name        = song.title,
                    artist      = song.artist,
                    albumArtUrl = "",
                    previewUrl  = null,
                    durationMs  = 0L
                )
                musicPlayerViewModel.loadTrackWithVideoId(track, song.youtubeVideoId)
                navigateTo(Screen.MUSIC_PLAYER)
            }
        )
    }
}

// ── Helper screens ────────────────────────────────────────────────────────────

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(errorMessage: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Error: $errorMessage", color = MaterialTheme.colorScheme.error)
    }
}

// ── ViewModel factory ─────────────────────────────────────────────────────────

class MusicPlayerViewModelFactory(
    private val application: android.app.Application
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicPlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}