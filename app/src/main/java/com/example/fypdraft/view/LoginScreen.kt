package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fypdraft.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

private val DarkNavy = Color(0xFF1A1A2E)

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit = {},
    onForgotPassword: () -> Unit = {}
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Try to get last known display name (from previous session)
    val lastDisplayName = remember {
        FirebaseAuth.getInstance().currentUser?.displayName
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onLoginSuccess() }

    Box(modifier.fillMaxSize().background(Color.White).padding(horizontal = 32.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
            Spacer(Modifier.height(120.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(DarkNavy), contentAlignment = Alignment.Center) {
                    Text("\uD83C\uDFB5", fontSize = 32.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text("MoodSync", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(Modifier.height(40.dp))

            Text("Welcome back,", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(
                text = lastDisplayName ?: "friend",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value = emailOrUsername, onValueChange = { emailOrUsername = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Username or email address", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Gray, unfocusedBorderColor = Color.LightGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                enabled = !uiState.isLoading
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Password", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Color.Gray)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Gray, unfocusedBorderColor = Color.LightGray, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                enabled = !uiState.isLoading
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { viewModel.signIn(emailOrUsername, password) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = Color.White),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                else Text("LOG IN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))

            Text("Forgot Password?", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onForgotPassword() })
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}