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

private val DarkNavy = Color(0xFF1A1A2E)

@Composable
fun WelcomeScreen(
    onSignUp: () -> Unit = {},
    onLogIn: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(160.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DarkNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83C\uDFB5", fontSize = 32.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "MoodSync",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.height(48.dp))

            Text(
                text = "Understand what\nyou listen to. Ready?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 40.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onSignUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkNavy,
                    contentColor = Color.White
                )
            ) {
                Text("SIGN UP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onLogIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Text(
                    "LOG IN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}