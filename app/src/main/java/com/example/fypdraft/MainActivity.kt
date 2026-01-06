package com.example.fypdraft

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fypdraft.view.*
import com.example.fypdraft.ui.theme.FYPDraftTheme
import com.example.fypdraft.model.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.fypdraft.model.SpotifyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            enableEdgeToEdge()
            setContent {
                FYPDraftTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val spotifyViewModel = SpotifyViewModel(this)
                        MoodSyncApp(spotifyViewModel = spotifyViewModel)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            e.printStackTrace()
        }
    }
}

@Composable
fun MoodSyncApp(spotifyViewModel: SpotifyViewModel) {
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
                onNavigateToLibrary = {
                    // TODO: Navigate to library
                },
                onNavigateToSettings = {
                    currentScreen = "settings"
                },
                onNavigateToSpotify = {
                    currentScreen = "spotify"
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
    }
}