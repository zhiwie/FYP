package com.example.fypdraft.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fypdraft.model.MascotMood

/**
 * LOTTIE INTEGRATION PLAN:
 * Replace AnimatedMascotEmoji with:
 *   val composition by rememberLottieComposition(LottieCompositionSpec.Asset("mascot_${mood.mood}.json"))
 *   val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
 *   LottieAnimation(composition, { progress }, modifier = Modifier.size(120.dp))
 *
 * Required files in assets/: mascot_happy.json, mascot_sad.json, mascot_calm.json,
 *   mascot_energetic.json, mascot_tired.json, mascot_focused.json,
 *   mascot_romantic.json, mascot_neutral.json
 *
 * Dependency: implementation "com.airbnb.android:lottie-compose:6.3.0"
 */

@Composable
fun MascotWidget(
    mood: MascotMood,
    chatMessage: String?,
    onQuickReply: (String) -> Unit,
    onTapMascot: () -> Unit,
    onChangeMood: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(getMoodGradient(mood.mood)),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Speech bubble
                val displayMessage = chatMessage ?: mood.greeting

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 15.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Mascot emoji (replace with Lottie later)
                AnimatedMascotEmoji(
                    mood = mood,
                    onClick = onTapMascot,
                    modifier = Modifier.size(100.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Mood label + change button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Feeling ${mood.mood}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.clickable { onChangeMood() }
                    ) {
                        Text(
                            text = "Change",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Quick reply chips
                if (mood.suggestion != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickReplyChip("Yes please!", { onQuickReply("yes") }, Modifier.weight(1f))
                        QuickReplyChip("Not now", { onQuickReply("no") }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedMascotEmoji(
    mood: MascotMood,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascotBreath")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood.mood) {
                    "energetic" -> 400
                    "calm"      -> 2000
                    "tired"     -> 2500
                    "sad"       -> 1800
                    else        -> 1200
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = if (mood.mood == "energetic") -5f else 0f,
        targetValue = if (mood.mood == "energetic") 5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable { onClick() }
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mood.emoji,
            fontSize = 56.sp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun QuickReplyChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.25f),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun getMoodGradient(mood: String): List<Color> = when (mood) {
    "happy"     -> listOf(Color(0xFFFFB347), Color(0xFFFF6B6B))
    "sad"       -> listOf(Color(0xFF667EEA), Color(0xFF764BA2))
    "calm"      -> listOf(Color(0xFF89CFF0), Color(0xFF6A9BD1))
    "energetic" -> listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
    "tired"     -> listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
    "focused"   -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    "romantic"  -> listOf(Color(0xFFEE9CA7), Color(0xFFFFC3A0))
    else        -> listOf(Color(0xFF6A5ACD), Color(0xFF9B59B6))
}