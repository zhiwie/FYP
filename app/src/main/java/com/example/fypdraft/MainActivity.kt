package com.example.fypdraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import com.example.fypdraft.model.AuthViewModel
import com.example.fypdraft.model.MusicPlayerViewModel
import com.example.fypdraft.model.SpotifyViewModel
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.view.*
import com.spotify.sdk.android.auth.AuthorizationClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private lateinit var musicPlayerViewModel: MusicPlayerViewModel
    var spotifyViewModel: SpotifyViewModel? = null
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "🚀 Starting MoodSync App...")

            musicPlayerViewModel = ViewModelProvider(
                this,
                MusicPlayerViewModelFactory(application)
            )[MusicPlayerViewModel::class.java]

            enableEdgeToEdge()
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppInitializer(
                            activity = this@MainActivity,
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
                        ErrorScreen(errorMessage = "Failed to initialize: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "📲 onNewIntent received: ${intent.data}")
        setIntent(intent)
    }

    @Deprecated("Required for Spotify Auth SDK callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SpotifyViewModel.SPOTIFY_AUTH_REQUEST_CODE) {
            Log.d(TAG, "📲 Spotify auth result received via onActivityResult")
            val response = AuthorizationClient.getResponse(resultCode, data)
            Log.d(TAG, "Response type: ${response.type}, token null: ${response.accessToken == null}")
            spotifyViewModel?.handleAuthResult(resultCode, response)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            musicPlayerViewModel.cleanupMLModels()
            Log.d(TAG, "🧹 Resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}

@Composable
fun AppInitializer(
    activity: MainActivity,
    musicPlayerViewModel: MusicPlayerViewModel
) {
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                Log.d("AppInitializer", "⏳ Initializing ML models...")
                musicPlayerViewModel.initializeMLModels()
                delay(500)
                isInitialized = true
                Log.d("AppInitializer", "✅ ML models ready!")
            } catch (e: Exception) {
                Log.e("AppInitializer", "⚠️ ML initialization failed, continuing anyway", e)
                initError = e.message
                isInitialized = true
            }
        }
    }

    if (!isInitialized) {
        LoadingScreen()
    } else {
        val spotifyViewModel = remember {
            SpotifyViewModel(activity).also { activity.spotifyViewModel = it }
        }

        MoodSyncApp(
            activity = activity,
            spotifyViewModel = spotifyViewModel,
            musicPlayerViewModel = musicPlayerViewModel,
            mlInitError = initError
        )
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(errorMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Error: $errorMessage",
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun MoodSyncApp(
    activity: MainActivity,
    spotifyViewModel: SpotifyViewModel,
    musicPlayerViewModel: MusicPlayerViewModel,
    mlInitError: String? = null
) {
    val authViewModel: AuthViewModel = viewModel()

    var currentScreen by remember {
        mutableStateOf(
            try {
                if (authViewModel.isUserLoggedIn()) "home" else "login"
            } catch (e: Exception) {
                Log.e("MoodSyncApp", "Error checking login status", e)
                "login"
            }
        )
    }

    LaunchedEffect(mlInitError) {
        if (mlInitError != null) {
            Log.w("MoodSyncApp", "⚠️ App running without ML features: $mlInitError")
        }
    }

    when (currentScreen) {
        "login" -> LoginScreen(
            viewModel = authViewModel,
            onLoginSuccess = { currentScreen = "home" },
            onForgotPassword = { currentScreen = "reset" },
            onCreateAccount = { currentScreen = "signup" },
            onTryDemo = { currentScreen = "home" }
        )
        "signup" -> SignUpScreen(
            viewModel = authViewModel,
            onSignUpSuccess = { currentScreen = "login" },
            onNavigateToLogin = { currentScreen = "login" }
        )
        "reset" -> ResetPWScreen(
            viewModel = authViewModel,
            onResetSuccess = { currentScreen = "login" },
            onBack = { currentScreen = "login" }
        )
        "home" -> HomeScreen(
            musicPlayerViewModel = musicPlayerViewModel,
            onNavigateToLibrary = { /* TODO */ },
            onNavigateToSettings = { currentScreen = "settings" },
            onNavigateToSpotify = { currentScreen = "spotify" },
            onNavigateToMusicPlayer = { currentScreen = "musicplayer" },
            onNavigateToEmotionChat = { currentScreen = "emotionchat" },
            onSignOut = {
                authViewModel.signOut()
                currentScreen = "login"
            }
        )
        "settings" -> SettingsScreen(
            onBack = { currentScreen = "home" },
            onSignOut = {
                authViewModel.signOut()
                currentScreen = "login"
            }
        )
        "spotify" -> SpotifyConnectionScreen(
            spotifyViewModel = spotifyViewModel,
            onBack = { currentScreen = "home" }
        )
        "musicplayer" -> MusicPlayerScreen(
            viewModel = musicPlayerViewModel,
            onBack = { currentScreen = "home" }
        )
        "emotionchat" -> EmotionChatScreen(
            viewModel = musicPlayerViewModel,
            onBack = { currentScreen = "home" }
        )
    }
}

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