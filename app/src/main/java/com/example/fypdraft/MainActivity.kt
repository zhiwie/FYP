package com.example.fypdraft

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fypdraft.view.*
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.model.AuthViewModel
import com.example.fypdraft.model.SpotifyViewModel
import com.example.fypdraft.model.MusicPlayerViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var musicPlayerViewModel: MusicPlayerViewModel
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "🚀 Starting MoodSync App...")

            // Initialize MusicPlayerViewModel with Application context
            musicPlayerViewModel = ViewModelProvider(
                this,
                MusicPlayerViewModelFactory(application)
            )[MusicPlayerViewModel::class.java]

            enableEdgeToEdge()
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // Show loading screen while ML models initialize
                        AppInitializer(musicPlayerViewModel)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            e.printStackTrace()

            // Still try to show the app even if ML models fail
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ErrorScreen(errorMessage = "Failed to initialize: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up ML models
            musicPlayerViewModel.cleanupMLModels()
            Log.d(TAG, "🧹 Resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}

@Composable
fun AppInitializer(musicPlayerViewModel: MusicPlayerViewModel) {
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                Log.d("AppInitializer", "⏳ Initializing ML models...")

                // Initialize ML models in background
                musicPlayerViewModel.initializeMLModels()

                // Small delay to show loading screen
                delay(500)

                isInitialized = true
                Log.d("AppInitializer", "✅ ML models ready!")
            } catch (e: Exception) {
                Log.e("AppInitializer", "⚠️ ML initialization failed, continuing anyway", e)
                initError = e.message
                // Still allow app to continue
                isInitialized = true
            }
        }
    }

    when {
        !isInitialized -> {
            LoadingScreen()
        }
        else -> {
            val spotifyViewModel = SpotifyViewModel(
                androidx.compose.ui.platform.LocalContext.current as ComponentActivity
            )

            MoodSyncApp(
                spotifyViewModel = spotifyViewModel,
                musicPlayerViewModel = musicPlayerViewModel,
                mlInitError = initError
            )
        }
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

    // Show warning if ML models failed to load
    LaunchedEffect(mlInitError) {
        if (mlInitError != null) {
            Log.w("MoodSyncApp", "⚠️ App running without ML features: $mlInitError")
        }
    }

    when (currentScreen) {
        "login" -> {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    currentScreen = "home"
                },
                onForgotPassword = {
                    currentScreen = "reset"
                },
                onCreateAccount = {
                    currentScreen = "signup"
                },
                onTryDemo = {
                    currentScreen = "home"
                }
            )
        }

        "signup" -> {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = {
                    currentScreen = "login"
                },
                onNavigateToLogin = {
                    currentScreen = "login"
                }
            )
        }

        "reset" -> {
            ResetPWScreen(
                viewModel = authViewModel,
                onResetSuccess = {
                    currentScreen = "login"
                },
                onBack = {
                    currentScreen = "login"
                }
            )
        }

        "home" -> {
            HomeScreen(
                musicPlayerViewModel = musicPlayerViewModel,
                onNavigateToLibrary = {
                    // TODO: Navigate to library
                },
                onNavigateToSettings = {
                    currentScreen = "settings"
                },
                onNavigateToSpotify = {
                    currentScreen = "spotify"
                },
                onNavigateToMusicPlayer = {
                    currentScreen = "musicplayer"
                },
                onNavigateToEmotionChat = {
                    currentScreen = "emotionchat"
                },
                onSignOut = {
                    authViewModel.signOut()
                    currentScreen = "login"
                }
            )
        }

        "settings" -> {
            SettingsScreen(
                onBack = {
                    currentScreen = "home"
                },
                onSignOut = {
                    authViewModel.signOut()
                    currentScreen = "login"
                }
            )
        }

        "spotify" -> {
            SpotifyConnectionScreen(
                spotifyViewModel = spotifyViewModel,
                onBack = {
                    currentScreen = "home"
                }
            )
        }

        "musicplayer" -> {
            MusicPlayerScreen(
                viewModel = musicPlayerViewModel,
                onBack = {
                    currentScreen = "home"
                }
            )
        }

        "emotionchat" -> {
            EmotionChatScreen(
                viewModel = musicPlayerViewModel,
                onBack = {
                    currentScreen = "home"
                }
            )
        }
    }
}

// ViewModelFactory to pass Application to MusicPlayerViewModel
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