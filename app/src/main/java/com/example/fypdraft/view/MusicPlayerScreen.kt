package com.example.fypdraft.view

import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import com.example.fypdraft.service.MusicPlayerService

@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit = {}
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentSongEmotion by viewModel.currentSongEmotion.collectAsState()
    val isAnalyzingEmotion by viewModel.isAnalyzingEmotion.collectAsState()
    val notificationCommand by viewModel.notificationCommand.collectAsState()
    val currentTrack = playerState.currentTrack
    val lifecycleOwner = LocalLifecycleOwner.current

    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF6794D2), Color(0xFF354C6C), Color.Black)
    )

    // ── No track at all — first ever open with nothing loaded ────────────
    if (currentTrack == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(bgBrush),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Loading track...", color = Color.White.copy(alpha = 0.7f))
            }
        }
        return
    }

    // ── WebView state ────────────────────────────────────────────────────
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var fallbackTriggered by remember(currentTrack.id) { mutableStateOf(false) }

    LaunchedEffect(playerState.youtubeVideoId) {
        isPlayerReady = false
        playerError = null
    }

    // ── Lifecycle ────────────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE ->
                    webViewRef.value?.evaluateJavascript(
                        "if(window.player) window.player.pauseVideo();", null
                    )
                Lifecycle.Event.ON_DESTROY -> webViewRef.value?.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Sync play/pause → WebView ────────────────────────────────────────
    LaunchedEffect(playerState.isPlaying, isPlayerReady) {
        if (isPlayerReady && webViewRef.value != null && !playerState.usingDeezerFallback) {
            val js = if (playerState.isPlaying)
                "if(window.player && window.player.playVideo) window.player.playVideo();"
            else
                "if(window.player && window.player.pauseVideo) window.player.pauseVideo();"
            webViewRef.value?.evaluateJavascript(js, null)
        }
    }

    // ── Notification commands → WebView ──────────────────────────────────
    LaunchedEffect(notificationCommand) {
        val cmd = notificationCommand ?: return@LaunchedEffect
        if (!playerState.usingDeezerFallback && webViewRef.value != null) {
            when (cmd) {
                MusicPlayerService.ACTION_PLAY ->
                    webViewRef.value?.evaluateJavascript(
                        "if(window.player && window.player.playVideo) window.player.playVideo();", null
                    )
                MusicPlayerService.ACTION_PAUSE ->
                    webViewRef.value?.evaluateJavascript(
                        "if(window.player && window.player.pauseVideo) window.player.pauseVideo();", null
                    )
            }
        }
        viewModel.clearNotificationCommand()
    }

    // ── UI ───────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text("Now Playing", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Album art — Crossfade only here for a polished feel
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.size(280.dp)
            ) {
                Crossfade(
                    targetState = currentTrack.albumArtUrl,
                    animationSpec = tween(400),
                    label = "art"
                ) { artUrl ->
                    if (artUrl.isNotEmpty()) {
                        AsyncImage(
                            model = artUrl,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.radialGradient(listOf(Color(0xFF6A5ACD), Color(0xFF2E1065)))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵", fontSize = 80.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Song info
            Text(
                text = currentTrack.name,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = currentTrack.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // AI emotion badge
            if (isAnalyzingEmotion) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Magenta.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("🤖 Analyzing song emotion...", color = Color.White, fontSize = 12.sp)
                    }
                }
            } else if (currentSongEmotion != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Magenta.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(currentSongEmotion!!.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AI detected: ${currentSongEmotion!!.topEmotion}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(currentSongEmotion!!.confidence * 100).toInt()}% confident", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Status indicator
            when {
                playerState.isLoadingVideo -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Finding best source...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                playerState.usingDeezerFallback -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color.Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Playing 30s preview via Deezer", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                playerState.youtubeVideoId != null && isPlayerReady -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Playing full track via YouTube", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                playerState.youtubeVideoId != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading YouTube player...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                else -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color.Yellow)
                            Spacer(Modifier.width(8.dp))
                            Text("Track not available", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // YouTube embed
            val showEmbed = playerState.youtubeVideoId != null &&
                    !playerState.isLoadingVideo &&
                    !playerState.usingDeezerFallback

            Box(modifier = Modifier.fillMaxWidth().height(if (showEmbed) 200.dp else 0.dp)) {
                if (showEmbed) {
                    key(playerState.youtubeVideoId) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewRef.value = this
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
                                        <!DOCTYPE html><html><head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1">
                                        <style>*{margin:0;padding:0}body{background:#000;overflow:hidden}#player{width:100%;height:200px}</style>
                                        </head><body><div id="player"></div><script>
                                        var tag=document.createElement('script');tag.src="https://www.youtube.com/iframe_api";
                                        document.getElementsByTagName('script')[0].parentNode.insertBefore(tag,document.getElementsByTagName('script')[0]);
                                        var player;function onYouTubeIframeAPIReady(){
                                        player=new YT.Player('player',{height:'200',width:'100%',videoId:'$videoId',
                                        playerVars:{autoplay:1,controls:1,playsinline:1,rel:0,fs:0},
                                        events:{onReady:function(e){window.playerReady=true;e.target.playVideo()},
                                        onError:function(e){window.playerError=e.data;if(e.data==150||e.data==152)window.useFallback=true}}});
                                        window.player=player}</script></body></html>
                                    """.trimIndent()

                                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)

                                    postDelayed(object : Runnable {
                                        var checkCount = 0
                                        override fun run() {
                                            if (fallbackTriggered) return

                                            evaluateJavascript("window.useFallback === true") { result ->
                                                if (result == "true" && !fallbackTriggered) {
                                                    fallbackTriggered = true
                                                    playerError = "YouTube restricted"
                                                    viewModel.useDeezerFallback()
                                                    return@evaluateJavascript
                                                }
                                            }
                                            evaluateJavascript("window.playerReady === true") { result ->
                                                if (result == "true") isPlayerReady = true
                                            }

                                            checkCount++
                                            when {
                                                fallbackTriggered -> {}
                                                isPlayerReady -> {}
                                                checkCount >= 10 -> {
                                                    if (!fallbackTriggered) {
                                                        fallbackTriggered = true
                                                        playerError = "YouTube timeout"
                                                        viewModel.useDeezerFallback()
                                                    }
                                                }
                                                else -> postDelayed(this, 1000)
                                            }
                                        }
                                    }, 1000)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = {
                        webViewRef.value?.destroy(); webViewRef.value = null
                        isPlayerReady = false; playerError = null; fallbackTriggered = false
                        viewModel.playPrevious()
                    },
                    enabled = playerState.currentIndex > 0
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous",
                        tint = if (playerState.currentIndex > 0) Color.White else Color.Gray,
                        modifier = Modifier.size(40.dp))
                }

                val canPlay = isPlayerReady || playerState.usingDeezerFallback
                FloatingActionButton(
                    onClick = { if (canPlay) viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    containerColor = if (canPlay) Color.White else Color.Gray
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.Black, modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = {
                        webViewRef.value?.destroy(); webViewRef.value = null
                        isPlayerReady = false; playerError = null; fallbackTriggered = false
                        viewModel.playNext()
                    },
                    enabled = playerState.currentIndex < playerState.playlist.size - 1
                ) {
                    Icon(Icons.Default.SkipNext, "Next",
                        tint = if (playerState.currentIndex < playerState.playlist.size - 1) Color.White else Color.Gray,
                        modifier = Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = Color.White.copy(alpha = 0.7f)) }
                IconButton(onClick = { }) { Icon(Icons.Default.Repeat, "Repeat", tint = Color.White.copy(alpha = 0.7f)) }
                IconButton(onClick = { }) { Icon(Icons.Default.FavoriteBorder, "Favorite", tint = Color.White.copy(alpha = 0.7f)) }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}