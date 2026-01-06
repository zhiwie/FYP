package com.example.fypdraft

import android.os.Bundle
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
import com.example.fypdraft.view.HomeScreen
import com.example.fypdraft.view.ResetPWScreen
import com.example.fypdraft.view.SignUpScreen
import com.example.fypdraft.model.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FYPDraftTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MoodSyncApp()
                }
            }
        }
    }
}

@Composable
fun MoodSyncApp() {
    val authViewModel: AuthViewModel = viewModel()
    var currentScreen by remember {
        mutableStateOf(
            if (authViewModel.isUserLoggedIn()) "home" else "login"
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
                    // TODO: Navigate to Spotify connection
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
    }
}