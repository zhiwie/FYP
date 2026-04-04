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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fypdraft.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

private val DarkNavy = Color(0xFF1A1A2E)
private val BodyText  = Color(0xFF1A1A2E)
private val SubText   = Color(0xFF55556A)
private val HintText  = Color(0xFF999AAA)

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit = {},
    onForgotPassword: () -> Unit = {}
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val lastDisplayName = remember { FirebaseAuth.getInstance().currentUser?.displayName }
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onLoginSuccess() }

    // Key design decision: use `label` instead of `placeholder`.
    // Material3 OutlinedTextField with a `label` shows the hint text sitting
    // inside the field when empty+unfocused, then animates it to a small
    // floating label above the border on focus — so the hint DISAPPEARS the
    // instant the user taps the field, which is the requested behaviour.
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = DarkNavy,
        unfocusedBorderColor    = Color(0xFFCCCCDD),
        focusedContainerColor   = Color.White,
        unfocusedContainerColor = Color.White,
        focusedTextColor        = BodyText,
        unfocusedTextColor      = BodyText,
        cursorColor             = DarkNavy,
        focusedLabelColor       = DarkNavy,   // small floating label colour
        unfocusedLabelColor     = HintText    // idle hint colour
    )

    Box(modifier.fillMaxSize().background(Color.White).padding(horizontal = 32.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
            Spacer(Modifier.height(120.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(DarkNavy),
                    contentAlignment = Alignment.Center) { Text("\uD83C\uDFB5", fontSize = 32.sp) }
                Spacer(Modifier.width(16.dp))
                Text("MoodSync", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = BodyText)
            }

            Spacer(Modifier.height(40.dp))
            Text("Welcome back,", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = BodyText)
            Text(lastDisplayName ?: "friend", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = BodyText)
            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value         = emailOrUsername,
                onValueChange = { emailOrUsername = it },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Username or email address") },
                singleLine    = true,
                shape         = RoundedCornerShape(28.dp),
                textStyle     = LocalTextStyle.current.copy(color = BodyText),
                colors        = fieldColors,
                enabled       = !uiState.isLoading
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value                = password,
                onValueChange        = { password = it },
                modifier             = Modifier.fillMaxWidth(),
                label                = { Text("Password") },
                singleLine           = true,
                shape                = RoundedCornerShape(28.dp),
                textStyle            = LocalTextStyle.current.copy(color = BodyText),
                keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            null, tint = SubText)
                    }
                },
                colors  = fieldColors,
                enabled = !uiState.isLoading
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick  = { viewModel.signIn(emailOrUsername, password) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(28.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = Color.White),
                enabled  = !uiState.isLoading
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                else Text("LOG IN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))

            Text("Forgot Password?", fontSize = 14.sp, color = SubText, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onForgotPassword() })
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}