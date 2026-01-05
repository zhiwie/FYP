package com.example.fypdraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.ui.theme.FYPDraftTheme

@Composable
fun ResetPWScreen(
    modifier: Modifier = Modifier,
    onConfirmReset: (emailOrUsername: String, newPassword: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    var emailOrUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEFE7FF),
            Color(0xFFFFF3D6),
            Color(0xFFDCEBFF)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card containing the form
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xD1FFF9F4)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reset Password",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(Modifier.height(30.dp))

                    OutlinedTextField(
                        value = emailOrUsername,
                        onValueChange = { emailOrUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Email or Username") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("New Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))

            // Confirm Reset button
            Button(
                onClick = { onConfirmReset(emailOrUsername, newPassword) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B7B7B),
                    contentColor = Color.White
                )
            ) {
                Text("Confirm Reset", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Back button
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B7B7B),
                    contentColor = Color.White
                )
            ) {
                Text("Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewResetPWScreen() {
    FYPDraftTheme {
        ResetPWScreen()
    }
}