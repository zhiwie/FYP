package com.example.fypdraft.view

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.fypdraft.model.MusicPlayerViewModel

@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit = {}
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentTrack = playerState.currentTrack
    val lifecycleOwner = LocalLifecycleOwner.current

    // Show loading if no track
    if (currentTrack == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading track...", color = Color.White)
            }
        }
        return
    }

    // WebView state
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Reset player state when track changes
    LaunchedEffect(playerState.youtubeVideoId) {
        isPlayerReady = false
        playerError = null
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.evaluateJavascript("if(window.player) window.player.pauseVideo();", null)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    webView?.destroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
        }
    }

    // Handle play/pause state changes
    LaunchedEffect(playerState.isPlaying, isPlayerReady) {
        if (isPlayerReady && webView != null) {
            if (playerState.isPlaying) {
                webView?.evaluateJavascript("if(window.player) window.player.playVideo();", null)
            } else {
                webView?.evaluateJavascript("if(window.player) window.player.pauseVideo();", null)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6794D2),
                        Color(0xFF354C6C),
                        Color.Black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Now Playing",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Album art
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.size(280.dp)
            ) {
                AsyncImage(
                    model = currentTrack.albumArtUrl,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Song info
            Text(
                text = currentTrack.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentTrack.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status indicator
            when {
                playerState.isLoadingVideo -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Finding song on YouTube...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                playerState.usingDeezerFallback -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Blue.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.Cyan
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Playing 30s preview from Deezer",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                playerError != null -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Yellow.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Yellow
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$playerError - using preview",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                playerState.youtubeVideoId != null && isPlayerReady -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Playing full track from YouTube",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                playerState.youtubeVideoId != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Loading YouTube player...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                else -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Yellow.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.Yellow
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Track not available",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Player - Try YouTube first, fallback to Deezer preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (playerState.youtubeVideoId != null) 200.dp else 0.dp)
            ) {
                if (playerState.youtubeVideoId != null && !playerState.isLoadingVideo) {
                    // Try YouTube embed
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webView = this
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    allowFileAccess = true
                                    allowContentAccess = true
                                }

                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()

                                val videoId = playerState.youtubeVideoId
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1">
                                        <style>
                                            * { margin: 0; padding: 0; }
                                            body { background: #000; overflow: hidden; }
                                            #player { width: 100%; height: 200px; }
                                        </style>
                                    </head>
                                    <body>
                                        <div id="player"></div>
                                        <script>
                                            var tag = document.createElement('script');
                                            tag.src = "https://www.youtube.com/iframe_api";
                                            var firstScriptTag = document.getElementsByTagName('script')[0];
                                            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
                                            
                                            var player;
                                            
                                            function onYouTubeIframeAPIReady() {
                                                player = new YT.Player('player', {
                                                    height: '200',
                                                    width: '100%',
                                                    videoId: '$videoId',
                                                    playerVars: {
                                                        'autoplay': 1,
                                                        'controls': 1,
                                                        'playsinline': 1,
                                                        'rel': 0,
                                                        'fs': 0
                                                    },
                                                    events: {
                                                        'onReady': function(e) {
                                                            console.log('Player ready');
                                                            window.playerReady = true;
                                                            e.target.playVideo();
                                                        },
                                                        'onError': function(e) {
                                                            console.error('Player error:', e.data);
                                                            window.playerError = e.data;
                                                            // Error 150/152 = not embeddable
                                                            if (e.data == 150 || e.data == 152) {
                                                                window.useFallback = true;
                                                            }
                                                        }
                                                    }
                                                });
                                                window.player = player;
                                            }
                                        </script>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)

                                // Check for errors and trigger fallback
                                postDelayed(object : Runnable {
                                    var checkCount = 0
                                    override fun run() {
                                        // Check if YouTube flagged for fallback
                                        evaluateJavascript("window.useFallback === true") { result ->
                                            if (result == "true") {
                                                android.util.Log.d("MusicPlayer", "YouTube error detected, switching to Deezer")
                                                playerError = "YouTube restricted"
                                                viewModel.useDeezerFallback()
                                                return@evaluateJavascript
                                            }
                                        }

                                        // Check if player is ready
                                        evaluateJavascript("window.playerReady === true") { result ->
                                            if (result == "true") {
                                                isPlayerReady = true
                                                android.util.Log.d("MusicPlayer", "YouTube player ready")
                                            }
                                        }

                                        // Check for error code
                                        evaluateJavascript("window.playerError") { errorResult ->
                                            if (errorResult != null && errorResult != "null" && errorResult != "undefined") {
                                                val errorCode = errorResult.trim('"')
                                                android.util.Log.w("MusicPlayer", "YouTube error code: $errorCode")
                                                // If error after 5 checks, use fallback
                                                if (checkCount >= 5) {
                                                    playerError = "YouTube error $errorCode"
                                                    viewModel.useDeezerFallback()
                                                }
                                            }
                                        }

                                        checkCount++
                                        if (checkCount < 10 && !isPlayerReady && playerError == null) {
                                            postDelayed(this, 1000)
                                        } else if (checkCount >= 10 && !isPlayerReady) {
                                            // Timeout - use fallback
                                            android.util.Log.w("MusicPlayer", "YouTube timeout, using Deezer fallback")
                                            playerError = "YouTube timeout"
                                            viewModel.useDeezerFallback()
                                        }
                                    }
                                }, 1000)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Previous button
                IconButton(
                    onClick = {
                        webView?.destroy()
                        webView = null
                        isPlayerReady = false
                        playerError = null
                        viewModel.playPrevious()
                    },
                    enabled = playerState.currentIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (playerState.currentIndex > 0) Color.White else Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause button
                FloatingActionButton(
                    onClick = {
                        if (isPlayerReady) {
                            viewModel.togglePlayPause()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    containerColor = if (isPlayerReady) Color.White else Color.Gray
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying)
                            Icons.Default.Pause
                        else
                            Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = {
                        webView?.destroy()
                        webView = null
                        isPlayerReady = false
                        playerError = null
                        viewModel.playNext()
                    },
                    enabled = playerState.currentIndex < playerState.playlist.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = if (playerState.currentIndex < playerState.playlist.size - 1)
                            Color.White
                        else
                            Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Additional controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* TODO: Shuffle */ }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = { /* TODO: Repeat */ }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = { /* TODO: Add to favorites */ }) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}