package com.example.fypdraft.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.fypdraft.ui.theme.AppThemeState
import com.example.fypdraft.ui.theme.animatedMoodBrush
import com.example.fypdraft.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    themeState: AppThemeState = AppThemeState(),
    onBack: () -> Unit = {}
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentSongEmotion by viewModel.currentSongEmotion.collectAsState()
    val isAnalyzingEmotion by viewModel.isAnalyzingEmotion.collectAsState()
    val spotifyConnected by viewModel.spotifyConnected.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val currentTrack = playerState.currentTrack
    val moodBrush = animatedMoodBrush(themeState)

    var likeState by remember(currentTrack?.id) { mutableIntStateOf(0) }
    var triggerCelebration by remember { mutableStateOf(false) }
    var shuffleActive by remember { mutableStateOf(false) }
    var repeatActive by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    if (currentTrack == null) {
        Box(Modifier.fillMaxSize().background(moodBrush), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Loading track...", color = Color.White.copy(alpha = 0.7f))
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(moodBrush)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

            // ── TOP BAR ──
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 48.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text("PLAYING SONG", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                IconButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White.copy(alpha = 0.8f))
                }
            }

            // ── ALBUM ART ──
            Box(Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
                Card(shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(24.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    Crossfade(targetState = currentTrack.albumArtUrl, animationSpec = tween(500), label = "art") { artUrl ->
                        if (artUrl.isNotEmpty()) {
                            AsyncImage(model = artUrl, contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(
                                themeState.activePalette.glowColor.copy(alpha = 0.4f), themeState.activePalette.accent.copy(alpha = 0.15f)
                            ))), contentAlignment = Alignment.Center) { Text("🎵", fontSize = 72.sp) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SONG INFO + LIKE/DISLIKE ──
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(currentTrack.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(currentTrack.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))

                    // ═══ LIKE — fixed position, graphicsLayer scale (no layout shift) ═══
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        // Celebration behind — also fixed in this same box
                        if (triggerCelebration) {
                            CleanCelebrationBurst(onFinished = { triggerCelebration = false })
                        }
                        ThumbButton(icon = Icons.Default.ThumbUp, isActive = likeState == 1, onClick = {
                            val wasActive = likeState == 1
                            likeState = if (wasActive) 0 else 1
                            if (!wasActive) triggerCelebration = true
                        })
                    }

                    Spacer(Modifier.width(4.dp))

                    // ═══ DISLIKE — fixed position ═══
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        ThumbButton(icon = Icons.Default.ThumbDown, isActive = likeState == -1, onClick = {
                            likeState = if (likeState == -1) 0 else -1
                            triggerCelebration = false
                        })
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (isAnalyzingEmotion) {
                    AiPillBadge("🔄", "Analyzing...", Color.White.copy(alpha = 0.1f))
                } else if (currentSongEmotion != null) {
                    AiPillBadge(currentSongEmotion!!.emoji, "${currentSongEmotion!!.topEmotion} · ${(currentSongEmotion!!.confidence * 100).toInt()}%", getMoodPillColor(currentSongEmotion!!.topEmotion))
                }

                if (playbackError != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(playbackError!!, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── PROGRESS BAR ──
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                if (playerState.duration > 0) {
                    RoundedProgressBar(progress = playerState.progress, onSeek = { viewModel.seekTo(it) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(playerState.currentPosition), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(formatDuration(playerState.duration), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── CONTROLS — white dot indicators for shuffle/repeat ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                ToggleControlButton(Icons.Default.Shuffle, "Shuffle", shuffleActive) { shuffleActive = !shuffleActive; viewModel.toggleShuffle() }
                IconButton(onClick = { viewModel.playPrevious() }, enabled = playerState.currentIndex > 0 || spotifyConnected, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = if (playerState.currentIndex > 0 || spotifyConnected) Color.White else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(36.dp))
                }
                FloatingActionButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(68.dp), shape = CircleShape, containerColor = Color.White, elevation = FloatingActionButtonDefaults.elevation(6.dp, 12.dp)) {
                    Icon(if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (playerState.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { viewModel.playNext() }, enabled = playerState.currentIndex < playerState.playlist.size - 1 || spotifyConnected, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = if (playerState.currentIndex < playerState.playlist.size - 1 || spotifyConnected) Color.White else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(36.dp))
                }
                ToggleControlButton(Icons.Default.Repeat, "Repeat", repeatActive) { repeatActive = !repeatActive; viewModel.toggleRepeat() }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showBottomSheet) {
        IosBottomSheet(
            onDismiss = { showBottomSheet = false },
            themeState = themeState,
            items = listOf(
                SheetItem(Icons.Default.Share, "Share") { showBottomSheet = false },
                SheetItem(Icons.Default.QueueMusic, "Up Next") { showBottomSheet = false },
                SheetItem(Icons.Default.Subtitles, "Lyrics") { showBottomSheet = false },
                SheetItem(Icons.Default.LibraryMusic, "Related Tracks") { showBottomSheet = false },
                SheetItem(Icons.Default.PlaylistAdd, "Add to Playlist") { showBottomSheet = false },
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// THUMB BUTTON — graphicsLayer scale (NO layout shift), always white
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ThumbButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.3f else 1f,
        animationSpec = if (isActive) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) else tween(200),
        label = "tScale"
    )
    val alpha by animateFloatAsState(if (isActive) 1f else 0.4f, tween(200), label = "tAlpha")

    // graphicsLayer for scale — does NOT affect layout, so button stays in place
    IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
        Icon(
            icon, null,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// CLEAN CELEBRATION BURST — white/gold circles, single burst
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun CleanCelebrationBurst(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        val durationNanos = 700_000_000L
        while (isActive) {
            val elapsed = withFrameNanos { it } - startTime
            progress = (elapsed.toFloat() / durationNanos).coerceIn(0f, 1f)
            if (progress >= 1f) { isActive = false; onFinished() }
        }
    }

    val particles = remember {
        List(10) {
            val angle = (it * 36f) + Random.nextFloat() * 18f
            val speed = Random.nextFloat() * 14f + 8f
            val size = Random.nextFloat() * 2.5f + 1f
            val isGold = Random.nextFloat() > 0.5f
            CelebParticle(angle, speed, size, isGold)
        }
    }

    if (isActive) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val t = 1f - (1f - progress) * (1f - progress)
            val fade = (1f - progress).coerceIn(0f, 1f)
            particles.forEach { p ->
                val rad = Math.toRadians(p.angle.toDouble())
                val dist = p.speed * t * density
                val x = cx + (cos(rad) * dist).toFloat()
                val y = cy + (sin(rad) * dist).toFloat() - (t * 3f * density)
                val color = if (p.isGold) Color(0xFFFFD700) else Color.White
                drawCircle(color.copy(alpha = fade * 0.85f), p.size * density * (1f - progress * 0.4f), Offset(x, y))
            }
        }
    }
}

private data class CelebParticle(val angle: Float, val speed: Float, val size: Float, val isGold: Boolean)

// ══════════════════════════════════════════════════════════════════════════
// TOGGLE CONTROL (Shuffle/Repeat) — WHITE dot indicator
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ToggleControlButton(icon: ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    // Always white — just brighter when active
    val alpha by animateFloatAsState(if (isActive) 1f else 0.5f, tween(250), label = "tcAlpha")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        // White dot
        AnimatedVisibility(isActive, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(Color.White))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// iOS BOTTOM SHEET — smooth slide-up with spring animation
// ══════════════════════════════════════════════════════════════════════════

private data class SheetItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun IosBottomSheet(onDismiss: () -> Unit, themeState: AppThemeState, items: List<SheetItem>) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scrimAlpha by animateFloatAsState(if (visible) 0.5f else 0f, tween(250), label = "scrim")
    val slideOffset by animateFloatAsState(
        if (visible) 0f else 1f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "slide"
    )

    fun dismissWithAnimation() { visible = false }
    LaunchedEffect(visible) { if (!visible) { delay(300); onDismiss() } }

    // ── Mood-synced colors ──
    val palette = themeState.activePalette
    // Dark translucent card using the mood's dark surface color
    val sheetBg = palette.darkSurface.copy(alpha = 0.92f)
    val cancelBg = palette.darkTop.copy(alpha = 0.85f)
    val dividerColor = Color.White.copy(alpha = 0.1f)
    val itemTextColor = Color.White.copy(alpha = 0.9f)
    val itemIconColor = palette.accent

    Dialog(onDismissRequest = { dismissWithAnimation() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismissWithAnimation() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)
                    .graphicsLayer { translationY = slideOffset * 600f }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Menu card — dark glass matching mood background
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = sheetBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        items.forEachIndexed { index, item ->
                            Row(
                                Modifier.fillMaxWidth().clickable { item.onClick() }.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(item.icon, null, tint = itemIconColor, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(item.label, color = itemTextColor, fontSize = 17.sp)
                            }
                            if (index < items.lastIndex) {
                                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Cancel — slightly different shade for distinction
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cancelBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        Modifier.fillMaxWidth().clickable { dismissWithAnimation() }.padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// ROUNDED PROGRESS BAR
// ══════════════════════════════════════════════════════════════════════════

@Composable
fun RoundedProgressBar(progress: Float, onSeek: (Float) -> Unit, activeColor: Color = Color.White, inactiveColor: Color = Color.White.copy(alpha = 0.2f), modifier: Modifier = Modifier) {
    val barH = 8.dp; val thumbR = 6.dp; var sz by remember { mutableStateOf(IntSize.Zero) }; val d = LocalDensity.current
    Box(modifier = modifier.height(barH + thumbR * 2).onSizeChanged { sz = it }
        .pointerInput(Unit) { detectTapGestures { o -> if (sz.width > 0) onSeek((o.x / sz.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> c.consume(); if (sz.width > 0) onSeek((c.position.x / sz.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().height(barH).clip(RoundedCornerShape(barH / 2)).background(inactiveColor))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.01f, 1f)).height(barH).clip(RoundedCornerShape(barH / 2)).background(activeColor).align(Alignment.CenterStart))
        if (sz.width > 0) { val off = with(d) { (progress * sz.width).toDp() - thumbR }
            Box(Modifier.offset(x = off).size(thumbR * 2).clip(CircleShape).background(activeColor).align(Alignment.CenterStart))
        }
    }
}

@Composable
private fun AiPillBadge(emoji: String, text: String, bgColor: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = bgColor, modifier = Modifier.height(28.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 14.sp); Spacer(Modifier.width(5.dp)); Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun getMoodPillColor(emotion: String): Color = when (emotion.lowercase()) {
    "happy", "joy" -> Color(0xFFFF9800).copy(alpha = 0.3f); "sad", "melancholy" -> Color(0xFF5C6BC0).copy(alpha = 0.3f)
    "calm", "relaxed" -> Color(0xFF26C6DA).copy(alpha = 0.3f); "energetic", "excited" -> Color(0xFFFF416C).copy(alpha = 0.3f)
    "angry", "aggressive" -> Color(0xFFFF5722).copy(alpha = 0.3f); "romantic", "love" -> Color(0xFFE91E63).copy(alpha = 0.3f)
    "focused" -> Color(0xFF00C853).copy(alpha = 0.3f); else -> Color.White.copy(alpha = 0.12f)
}

private fun formatDuration(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }