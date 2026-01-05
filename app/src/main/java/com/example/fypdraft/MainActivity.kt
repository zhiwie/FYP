package com.example.fypdraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.fypdraft.ui.theme.FYPDraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FYPDraftTheme {
                var currentScreen by remember { mutableStateOf("login") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "login" -> {
                            LoginScreen(
                                modifier = Modifier.padding(innerPadding),
                                onLogin = { user, pass ->
                                    // TODO: Add authentication logic here
                                    // For now, navigate to home after login attempt
                                    currentScreen = "home"
                                },
                                onForgotPassword = {
                                    currentScreen = "reset"
                                },
                                onCreateAccount = {
                                    currentScreen = "signup"
                                },
                                onTryDemo = {
                                    // Navigate directly to home for demo
                                    currentScreen = "home"
                                }
                            )
                        }

                        "signup" -> {
                            SignUpScreen(
                                modifier = Modifier.padding(innerPadding),
                                onSignUp = { username, email, password ->
                                    // TODO: Add registration logic here
                                    // Navigate to home or login after successful signup
                                    currentScreen = "home"
                                },
                                onNavigateToLogin = {
                                    currentScreen = "login"
                                }
                            )
                        }

                        "reset" -> {
                            ResetPWScreen(
                                modifier = Modifier.padding(innerPadding),
                                onConfirmReset = { emailOrUsername, newPassword ->
                                    // TODO: Add password reset logic here
                                    currentScreen = "login"
                                },
                                onBack = {
                                    currentScreen = "login"
                                }
                            )
                        }

                        "home" -> {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}