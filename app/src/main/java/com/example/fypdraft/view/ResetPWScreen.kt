package com.example.fypdraft.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fypdraft.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

private val DarkNavy = Color(0xFF1A1A2E)
private val BodyText  = Color(0xFF1A1A2E)
private val HintText  = Color(0xFF999AAA)

@Composable
fun ResetPWScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel,
    onResetSuccess: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }

    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it); viewModel.clearMessages()
            delay(2000); onResetSuccess()
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = DarkNavy,
        unfocusedBorderColor    = Color(0xFFCCCCDD),
        focusedContainerColor   = Color.White,
        unfocusedContainerColor = Color.White,
        focusedTextColor        = BodyText,
        unfocusedTextColor      = BodyText,
        cursorColor             = DarkNavy,
        focusedLabelColor       = DarkNavy,
        unfocusedLabelColor     = HintText
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
            Text("Reset your\npassword.", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = BodyText, lineHeight = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("Enter your email to receive a reset link.", fontSize = 14.sp, color = Color(0xFF55556A))
            Spacer(Modifier.height(48.dp))

            // label-based field: hint disappears on focus, floats above border
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Email address") },
                singleLine    = true,
                shape         = RoundedCornerShape(28.dp),
                textStyle     = LocalTextStyle.current.copy(color = BodyText),
                colors        = fieldColors,
                enabled       = !uiState.isLoading
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick  = { viewModel.resetPassword(email) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(28.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = Color.White),
                enabled  = !uiState.isLoading
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                else Text("Send Reset Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(28.dp)
            ) {
                Text("Back", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}