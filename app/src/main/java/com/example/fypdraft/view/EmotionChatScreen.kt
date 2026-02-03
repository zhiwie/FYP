package com.example.fypdraft.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fypdraft.model.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionChatScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit = {}
) {
    // UI State
    var userInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<AIResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Music Companion") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // User Input Section
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("How are you feeling?") },
                placeholder = { Text("Tell me about your mood... (e.g., 'I need to relax', 'feeling energetic')") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                minLines = 3,
                maxLines = 5,
                trailingIcon = {
                    if (userInput.isNotBlank() && !isProcessing) {
                        IconButton(
                            onClick = {
                                // Process the message
                                isProcessing = true
                                errorMessage = null

                                // Call the AI processing
                                viewModel.processUserMessageWithAI(
                                    message = userInput,
                                    onSuccess = { response ->
                                        aiResponse = response
                                        isProcessing = false
                                        userInput = "" // Clear input
                                    },
                                    onError = { error ->
                                        errorMessage = error
                                        isProcessing = false
                                    }
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        isProcessing = true
                        errorMessage = null

                        viewModel.processUserMessageWithAI(
                            message = userInput,
                            onSuccess = { response ->
                                aiResponse = response
                                isProcessing = false
                                userInput = ""
                            },
                            onError = { error ->
                                errorMessage = error
                                isProcessing = false
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing && userInput.isNotBlank()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isProcessing) "Processing..." else "Get AI Recommendations")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Display
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // AI Response Display
            aiResponse?.let { response ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Intent Detection Result
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "🎯 Your Intent",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = response.intentEmoji,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = response.intent.uppercase(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Text(
                                            text = "${response.intentConfidence}% confident",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Current Song Emotion
                    if (response.currentSongEmotion != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "🎵 Current Song Vibe",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = response.emotionEmoji ?: "😊",
                                            style = MaterialTheme.typography.headlineMedium
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = response.currentSongEmotion.uppercase(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                            Text(
                                                text = "${response.emotionConfidence}% confident",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AI Explanation
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "✨ AI Recommendation",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = response.explanation,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Match quality
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Match Quality:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LinearProgressIndicator(
                                        progress = response.overallConfidence / 100f,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp),
                                        color = when {
                                            response.overallConfidence >= 75 -> MaterialTheme.colorScheme.primary
                                            response.overallConfidence >= 50 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${response.overallConfidence}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Suggested Action
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "💡 Suggested Action",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = response.suggestedAction,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Quick Tips
                    if (response.tips.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "💭 Quick Tips",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    response.tips.forEach { tip ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(text = "• ", style = MaterialTheme.typography.bodyMedium)
                                            Text(text = tip, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Initial state - show helpful prompt
            if (aiResponse == null && errorMessage == null && !isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "👋 Welcome to AI Music Companion!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tell me how you're feeling and I'll recommend the perfect music using advanced AI emotion detection.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Try saying:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        listOf(
                            "\"I need to relax\"",
                            "\"Feeling energetic today!\"",
                            "\"Help me focus on work\"",
                            "\"I'm feeling sad\""
                        ).forEach { example ->
                            Text(
                                text = "• $example",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Data class for AI response
data class AIResponse(
    val intent: String,
    val intentConfidence: Int,
    val intentEmoji: String,
    val currentSongEmotion: String?,
    val emotionConfidence: Int,
    val emotionEmoji: String?,
    val explanation: String,
    val overallConfidence: Int,
    val suggestedAction: String,
    val tips: List<String>
)