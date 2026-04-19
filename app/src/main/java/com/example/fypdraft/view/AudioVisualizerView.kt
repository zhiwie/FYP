package com.example.fypdraft.view

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.*
import kotlin.random.Random

private const val TAG = "AudioVisualizer"
private const val BAR_COUNT = 14
private const val PARTICLE_COUNT = 30

private data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var radius: Float, var alpha: Float,
    var life: Float, var maxLife: Float,
    var colorIndex: Int
)

/**
 * Real-time audio-reactive equalizer that sits BEHIND the pixel pet
 * in the MascotWidget on HomeScreen.
 *
 * When music is playing:
 *   - If RECORD_AUDIO is granted → captures real FFT data from the audio session
 *   - Otherwise → uses a mood-driven simulated animation
 *
 * When music is NOT playing:
 *   - Shows a gentle idle breathing animation
 *
 * The bars are mood-colored using the provided color palette.
 *
 * @param isPlaying    Whether music is currently playing.
 * @param mood         Current mood string (happy, sad, calm, energetic, etc.)
 * @param barColors    List of colors for the equalizer bars (from mood palette).
 * @param audioSessionId  Audio session to capture (0 = global output mix).
 * @param modifier     Standard compose modifier.
 */
@Composable
fun AudioVisualizerView(
    isPlaying: Boolean = false,
    mood: String = "neutral",
    barColors: List<Color> = listOf(Color(0xFF9C27B0), Color(0xFF7C4DFF), Color(0xFFE040FB)),
    audioSessionId: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // Raw FFT magnitudes
    var magnitudes by remember { mutableStateOf(FloatArray(BAR_COUNT) { 0f }) }
    // Smoothed for display
    var smoothed by remember { mutableStateOf(FloatArray(BAR_COUNT) { 0f }) }
    // Peak hold
    var peaks by remember { mutableStateOf(FloatArray(BAR_COUNT) { 0f }) }
    // Particles
    var particles by remember { mutableStateOf(mutableListOf<Particle>()) }
    // Energy level
    var energy by remember { mutableFloatStateOf(0f) }

    // ── Visualizer API (only when playing + permission granted) ──────
    DisposableEffect(audioSessionId, hasPermission, isPlaying) {
        var visualizer: Visualizer? = null

        if (hasPermission && isPlaying) {
            try {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                                if (fft == null || fft.size < 4) return
                                val newMags = FloatArray(BAR_COUNT)
                                val fftSize = fft.size / 2
                                val binsPerBar = max(1, fftSize / BAR_COUNT)
                                for (i in 0 until BAR_COUNT) {
                                    var sum = 0.0
                                    for (j in 0 until binsPerBar) {
                                        val idx = (i * binsPerBar + j) * 2
                                        if (idx + 1 < fft.size) {
                                            val real = fft[idx].toFloat()
                                            val imag = fft[idx + 1].toFloat()
                                            sum += sqrt((real * real + imag * imag).toDouble())
                                        }
                                    }
                                    val avg = (sum / binsPerBar).toFloat()
                                    newMags[i] = (avg / 120f).coerceIn(0f, 1f)
                                }
                                // Frequency-dependent gain
                                for (i in 0 until BAR_COUNT) {
                                    val gain = when {
                                        i < BAR_COUNT / 4 -> 1.3f
                                        i < BAR_COUNT / 2 -> 1.0f
                                        i < 3 * BAR_COUNT / 4 -> 0.85f
                                        else -> 0.7f
                                    }
                                    newMags[i] = (newMags[i] * gain).coerceIn(0f, 1f)
                                }
                                magnitudes = newMags
                            }
                        },
                        Visualizer.getMaxCaptureRate(), false, true
                    )
                    enabled = true
                }
                Log.d(TAG, "Visualizer attached (session=$audioSessionId)")
            } catch (e: Exception) {
                Log.e(TAG, "Visualizer failed: ${e.message}")
                visualizer = null
            }
        }

        onDispose {
            try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        }
    }

    // ── Simulation phase for fallback ────────────────────────────────
    val simTransition = rememberInfiniteTransition(label = "sim")
    val simPhase by simTransition.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "simPhase"
    )

    // ── Frame ticker ─────────────────────────────────────────────────
    val frameTick by rememberInfiniteTransition(label = "frame").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16, easing = LinearEasing), RepeatMode.Restart),
        label = "tick"
    )

    // Mood-based animation parameters
    val moodSpeed = when (mood) {
        "energetic" -> 1.5f; "happy" -> 1.2f; "focused" -> 0.9f
        "calm" -> 0.5f; "sad" -> 0.4f; "tired" -> 0.3f
        else -> 0.8f
    }
    val moodAmplitude = when (mood) {
        "energetic" -> 0.9f; "happy" -> 0.75f; "focused" -> 0.6f
        "calm" -> 0.4f; "sad" -> 0.3f; "tired" -> 0.2f
        else -> 0.5f
    }

    // ── Update smoothed values + particles ───────────────────────────
    LaunchedEffect(frameTick, isPlaying) {
        if (!isPlaying) {
            // Idle: collapse all bars to zero → flat horizontal line
            smoothed = FloatArray(BAR_COUNT) { i ->
                smoothed[i] + (0f - smoothed[i]) * 0.15f   // smooth decay to flat
            }
            peaks    = FloatArray(BAR_COUNT) { 0f }
            energy   = 0f
            particles = particles.mapNotNull { p ->
                p.life -= 0.08f; p.alpha = p.life.coerceIn(0f, 1f) * 0.5f
                if (p.life > 0f) p else null
            }.toMutableList()
            return@LaunchedEffect
        }

        // Source: real FFT or simulation
        val source = if (!hasPermission || magnitudes.all { it == 0f }) {
            FloatArray(BAR_COUNT) { i ->
                val freq = i.toFloat() / BAR_COUNT
                val base = moodAmplitude * (0.3f + 0.5f * sin(simPhase * moodSpeed + freq * 6f))
                val detail = 0.12f * sin(simPhase * moodSpeed * 2.3f + i * 0.7f)
                val jitter = Random.nextFloat() * 0.06f
                (base + detail + jitter).coerceIn(0f, 1f)
            }
        } else magnitudes

        // Smooth
        val newSmoothed = FloatArray(BAR_COUNT) { i ->
            val t = source[i]; val c = smoothed[i]
            if (t > c) c + (t - c) * 0.35f else c + (t - c) * 0.12f
        }
        smoothed = newSmoothed

        // Peaks
        peaks = FloatArray(BAR_COUNT) { i ->
            if (newSmoothed[i] > peaks[i]) newSmoothed[i] else peaks[i] * 0.995f
        }

        // Energy
        energy = newSmoothed.average().toFloat()

        // Spawn particles
        if (energy > 0.25f && particles.size < PARTICLE_COUNT) {
            val spawnCount = ((energy - 0.25f) * 6).toInt().coerceIn(0, 2)
            val newParticles = particles.toMutableList()
            repeat(spawnCount) {
                val barIdx = Random.nextInt(BAR_COUNT)
                if (newSmoothed[barIdx] > 0.4f) {
                    newParticles.add(Particle(
                        x = barIdx.toFloat() / BAR_COUNT,
                        y = 1f - newSmoothed[barIdx],
                        vx = (Random.nextFloat() - 0.5f) * 0.015f,
                        vy = -(Random.nextFloat() * 0.012f + 0.003f),
                        radius = Random.nextFloat() * 2.5f + 1.5f,
                        alpha = Random.nextFloat() * 0.4f + 0.4f,
                        life = 1f,
                        maxLife = Random.nextFloat() * 0.5f + 0.5f,
                        colorIndex = Random.nextInt(barColors.size)
                    ))
                }
            }
            particles = newParticles
        }

        // Update particles
        particles = particles.mapNotNull { p ->
            p.x += p.vx; p.y += p.vy
            p.life -= 0.02f / p.maxLife
            p.alpha = p.life.coerceIn(0f, 1f) * 0.7f
            p.radius *= 0.997f
            if (p.life > 0f && p.alpha > 0.01f) p else null
        }.toMutableList()
    }

    // ── Canvas ───────────────────────────────────────────────────────
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // ── Idle: single flat horizontal line ────────────────────────
        if (!isPlaying && smoothed.all { it < 0.01f }) {
            val lineColor = if (barColors.isNotEmpty()) barColors[0].copy(alpha = 0.35f)
            else Color.White.copy(alpha = 0.35f)
            drawLine(
                color       = lineColor,
                start       = Offset(0f, midY),
                end         = Offset(w, midY),
                strokeWidth = 2.dp.toPx(),
                cap         = StrokeCap.Round
            )
            return@Canvas
        }

        // ── Playing (or decaying to flat): full bar animation ─────────
        val barWidth      = w / (BAR_COUNT * 1.8f)
        val totalBarsWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barWidth * 0.8f
        val startX        = (w - totalBarsWidth) / 2
        val gap           = barWidth * 0.8f

        for (i in 0 until BAR_COUNT) {
            val barH   = smoothed[i] * h * 0.85f
            val x      = startX + i * (barWidth + gap)
            val top    = h - barH
            val color  = barColors[i % barColors.size]

            // Glow
            if (smoothed[i] > 0.2f) {
                drawRoundRect(
                    color        = color.copy(alpha = smoothed[i] * 0.15f),
                    topLeft      = Offset(x - 1.5f, top - 1.5f),
                    size         = Size(barWidth + 3f, barH + 3f),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }

            // Bar
            drawRoundRect(
                color        = color.copy(alpha = 0.4f + smoothed[i] * 0.4f),
                topLeft      = Offset(x, top),
                size         = Size(barWidth, barH.coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(barWidth / 2)
            )

            // Peak dot (only while music is actually playing)
            if (isPlaying && peaks[i] > 0.08f) {
                val peakY = h - peaks[i] * h * 0.85f - 3f
                drawCircle(
                    color  = Color.White.copy(alpha = 0.7f),
                    radius = barWidth / 3f,
                    center = Offset(x + barWidth / 2, peakY)
                )
            }
        }

        // Particles
        for (p in particles) {
            val px    = p.x * w; val py = p.y * h
            val color = if (p.colorIndex < barColors.size) barColors[p.colorIndex] else barColors[0]
            drawCircle(color.copy(alpha = p.alpha * 0.3f), p.radius * 2f, Offset(px, py))
            drawCircle(color.copy(alpha = p.alpha), p.radius, Offset(px, py))
        }
    }
}