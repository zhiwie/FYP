package com.example.fypdraft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.tooling.preview.Preview
import com.example.fypdraft.ui.theme.FYPDraftTheme


@Composable
fun LoginScreen(
    onLogin: (emailOrUsername: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onTryDemo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Simple UI state (later you can move to ViewModel)
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Background similar to your screenshot (soft gradient)
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEFE7FF), // light purple
            Color(0xFFFFF3D6), // light warm
            Color(0xFFDCEBFF)  // light blue
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 110.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "MoodSync",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Explore more about yourself while\nenjoying music.",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )

            Spacer(Modifier.height(48.dp))

            // Login card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xD1FFF9F4) // close to your semi-transparent card
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Log In",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = emailOrUsername,
                        onValueChange = { emailOrUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Email or Username") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Password") },
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

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgotten Password?",
                            fontSize = 13.sp,
                            color = Color(0xFF0723AA),
                            modifier = Modifier.clickable { onForgotPassword() }
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // Login button (grey, rounded)
                    Button(
                        onClick = { onLogin(emailOrUsername, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7B7B7B),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // OR divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = Color.White)
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Divider(modifier = Modifier.weight(1f), color = Color.White)
            }

            Spacer(Modifier.height(22.dp))

            // Create account
            Button(
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B7B7B),
                    contentColor = Color.White
                )
            ) {
                Text("Create New Account", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(16.dp))

            // Try demo
            Button(
                onClick = onTryDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7B7B7B),
                    contentColor = Color.White
                )
            ) {
                Text("Try Demo", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
    FYPDraftTheme {
        LoginScreen()
    }
}