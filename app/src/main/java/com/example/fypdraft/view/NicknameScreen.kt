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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkNavy = Color(0xFF1A1A2E)

@Composable
fun NicknameScreen(
    onContinue: (String) -> Unit = {}
) {
    var nickname by remember { mutableStateOf("") }

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
            Spacer(Modifier.height(120.dp))

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

            Spacer(Modifier.height(40.dp))

            Text(
                text = "What should we\ncall you?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 40.sp
            )

            Spacer(Modifier.weight(1f))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Preferred nickname",
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Gray,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onContinue(nickname.trim().ifBlank { "Friend" }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkNavy,
                    contentColor = Color.White
                )
            ) {
                Text("Let\u2019s Go", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}