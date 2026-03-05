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
import com.example.fypdraft.viewmodel.AuthViewModel
import com.example.fypdraft.viewmodel.ChatGPTViewModel
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.example.fypdraft.viewmodel.SpotifyViewModel
import com.example.fypdraft.model.Track
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.view.EmotionChatScreen
import com.example.fypdraft.view.HomeScreen
import com.example.fypdraft.view.LoginScreen
import com.example.fypdraft.view.MusicPlayerScreen
import com.example.fypdraft.view.ResetPWScreen
import com.example.fypdraft.view.SettingsScreen
import com.example.fypdraft.view.SignUpScreen
import com.example.fypdraft.view.SpotifyConnectionScreen
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

            // ── Bind the background music notification service ─────────────
            musicPlayerViewModel.bindMusicService(this)

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
        try {
            // ── Unbind service and clean up ML models ──────────────────────
            musicPlayerViewModel.unbindMusicService(this)
            musicPlayerViewModel.cleanupMLModels()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
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

@Composable
fun MoodSyncApp(
    activity: MainActivity,
    spotifyViewModel: SpotifyViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    mlInitError: String? = null
) {
    val authViewModel:    AuthViewModel    = viewModel()
    val chatGPTViewModel: ChatGPTViewModel = viewModel()

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

    fun navigateTo(screen: String) {
        if (backStack.lastOrNull() == screen) return
        backStack = backStack + screen
        Log.d("Nav", "→ $screen   stack=$backStack")
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
            Log.d("Nav", "← back   stack=$backStack")
        }
    }

    fun replaceStack(screen: String) {
        backStack = listOf(screen)
        Log.d("Nav", "↺ reset → $screen")
    }

    fun navigateFromBottomNav(destination: String) {
        if (backStack.lastOrNull() == destination) return
        val trimmed = backStack.dropLastWhile { it !in Screen.BOTTOM_NAV_SCREENS }
        val base = if (trimmed.isNotEmpty()) trimmed.dropLast(1) else emptyList()
        backStack = base + destination
        Log.d("Nav", "⊡ tab → $destination   stack=$backStack")
    }

    // ── Android back button ───────────────────────────────────────────────────
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    // ── Screen routing ────────────────────────────────────────────────────────

    when (currentScreen) {

        Screen.LOGIN -> LoginScreen(
            viewModel = authViewModel,
            onLoginSuccess = { replaceStack(Screen.HOME) },
            onForgotPassword = { navigateTo(Screen.RESET) },
            onCreateAccount = { navigateTo(Screen.SIGNUP) },
            onTryDemo = { replaceStack(Screen.HOME) }
        )

        Screen.SIGNUP -> SignUpScreen(
            viewModel = authViewModel,
            onSignUpSuccess = { navigateBack() },
            onNavigateToLogin = { navigateBack() }
        )

        Screen.RESET -> ResetPWScreen(
            viewModel = authViewModel,
            onResetSuccess = { navigateBack() },
            onBack = { navigateBack() }
        )

        Screen.HOME -> HomeScreen(
            musicPlayerViewModel = musicPlayerViewModel,
            onNavigateToLibrary = { /* TODO */ },
            onNavigateToSettings = { navigateFromBottomNav(Screen.SETTINGS) },
            onNavigateToSpotify = { navigateFromBottomNav(Screen.SPOTIFY) },
            onNavigateToMusicPlayer = { navigateTo(Screen.MUSIC_PLAYER) },
            onNavigateToEmotionChat = { navigateTo(Screen.EMOTION_CHAT) },
            onSignOut = {
                authViewModel.signOut()
                replaceStack(Screen.LOGIN)
            }
        )

        Screen.SETTINGS -> SettingsScreen(
            onBack = { navigateBack() },
            onSignOut = {
                authViewModel.signOut()
                replaceStack(Screen.LOGIN)
            }
        )

        Screen.SPOTIFY -> SpotifyConnectionScreen(
            spotifyViewModel = spotifyViewModel,
            onBack = { navigateBack() }
        )

        Screen.MUSIC_PLAYER -> MusicPlayerScreen(
            viewModel = musicPlayerViewModel,
            onBack = { navigateBack() }
        )

        Screen.EMOTION_CHAT -> EmotionChatScreen(
            chatViewModel = chatGPTViewModel,
            onBack = { navigateBack() },
            onSongClick = { song ->
                val track = Track(
                    id = "${song.artist}-${song.title}",
                    name = song.title,
                    artist = song.artist,
                    albumArtUrl = "",
                    previewUrl = null,
                    durationMs = 0L
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