package com.example.fypdraft.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Background modes ─────────────────────────────────────────────────────

enum class BackgroundMode {
    DYNAMIC_LIGHT,  // Mood colors, light theme
    DYNAMIC_DARK,   // Mood colors, dark theme
    STATIC_PRESET,  // User picks a fixed mood color
    CUSTOM_PHOTO    // Blurred photo background (future)
}

// ── Mood color palettes (light + dark variants) ──────────────────────────

data class MoodPalette(
    // Light mode
    val lightTop: Color,
    val lightMid: Color,
    val lightBottom: Color,
    val lightSurface: Color,
    val lightText: Color,
    val lightTextSub: Color,

    // Dark mode
    val darkTop: Color,
    val darkMid: Color,
    val darkBottom: Color,
    val darkSurface: Color,
    val darkText: Color,
    val darkTextSub: Color,

    // Shared
    val accent: Color,
    val glowColor: Color,
    val mascotTint: Color   // Tint for the mascot card area
)

object MoodPalettes {
    // Each palette has VIVID light and dark variants

    val HAPPY = MoodPalette(
        lightTop = Color(0xFFFFE0B2), lightMid = Color(0xFFFFCC80), lightBottom = Color(0xFFFFF3E0),
        lightSurface = Color(0xFFFFF8E1), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF795548),
        darkTop = Color(0xFF4A2800), darkMid = Color(0xFF3E1F00), darkBottom = Color(0xFF1A0E00),
        darkSurface = Color(0xFF2A1800), darkText = Color.White, darkTextSub = Color(0xFFFFCC80),
        accent = Color(0xFFFF9800), glowColor = Color(0xFFFFD54F), mascotTint = Color(0xFFFF9800)
    )

    val SAD = MoodPalette(
        lightTop = Color(0xFFBBDEFB), lightMid = Color(0xFF90CAF9), lightBottom = Color(0xFFE3F2FD),
        lightSurface = Color(0xFFE8EAF6), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF546E7A),
        darkTop = Color(0xFF0D1B3E), darkMid = Color(0xFF0A1628), darkBottom = Color(0xFF060D1A),
        darkSurface = Color(0xFF0E1832), darkText = Color.White, darkTextSub = Color(0xFF90CAF9),
        accent = Color(0xFF667EEA), glowColor = Color(0xFF7986CB), mascotTint = Color(0xFF5C6BC0)
    )

    val CALM = MoodPalette(
        lightTop = Color(0xFFB2EBF2), lightMid = Color(0xFF80DEEA), lightBottom = Color(0xFFE0F7FA),
        lightSurface = Color(0xFFE0F2F1), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF00695C),
        darkTop = Color(0xFF003540), darkMid = Color(0xFF002830), darkBottom = Color(0xFF001518),
        darkSurface = Color(0xFF003040), darkText = Color.White, darkTextSub = Color(0xFF80DEEA),
        accent = Color(0xFF26C6DA), glowColor = Color(0xFF80DEEA), mascotTint = Color(0xFF00ACC1)
    )

    val ENERGETIC = MoodPalette(
        lightTop = Color(0xFFFFCDD2), lightMid = Color(0xFFEF9A9A), lightBottom = Color(0xFFFFF3E0),
        lightSurface = Color(0xFFFCE4EC), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFFC62828),
        darkTop = Color(0xFF4A0000), darkMid = Color(0xFF3E0A00), darkBottom = Color(0xFF1A0500),
        darkSurface = Color(0xFF3E0000), darkText = Color.White, darkTextSub = Color(0xFFFF8A80),
        accent = Color(0xFFFF416C), glowColor = Color(0xFFFF8A65), mascotTint = Color(0xFFFF5252)
    )

    val FOCUSED = MoodPalette(
        lightTop = Color(0xFFB2DFDB), lightMid = Color(0xFF80CBC4), lightBottom = Color(0xFFE8F5E9),
        lightSurface = Color(0xFFE0F2F1), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF2E7D32),
        darkTop = Color(0xFF003D33), darkMid = Color(0xFF002E26), darkBottom = Color(0xFF001A14),
        darkSurface = Color(0xFF003830), darkText = Color.White, darkTextSub = Color(0xFFA5D6A7),
        accent = Color(0xFF11998E), glowColor = Color(0xFF69F0AE), mascotTint = Color(0xFF00C853)
    )

    val TIRED = MoodPalette(
        lightTop = Color(0xFFCFD8DC), lightMid = Color(0xFFB0BEC5), lightBottom = Color(0xFFECEFF1),
        lightSurface = Color(0xFFECEFF1), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF607D8B),
        darkTop = Color(0xFF1A2228), darkMid = Color(0xFF141C22), darkBottom = Color(0xFF0A0F12),
        darkSurface = Color(0xFF1C2630), darkText = Color.White, darkTextSub = Color(0xFF90A4AE),
        accent = Color(0xFF78909C), glowColor = Color(0xFF90A4AE), mascotTint = Color(0xFF607D8B)
    )

    val ROMANTIC = MoodPalette(
        lightTop = Color(0xFFF8BBD0), lightMid = Color(0xFFF48FB1), lightBottom = Color(0xFFFCE4EC),
        lightSurface = Color(0xFFFCE4EC), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFFC2185B),
        darkTop = Color(0xFF3E0020), darkMid = Color(0xFF2E0018), darkBottom = Color(0xFF1A000E),
        darkSurface = Color(0xFF3A0020), darkText = Color.White, darkTextSub = Color(0xFFF48FB1),
        accent = Color(0xFFE91E63), glowColor = Color(0xFFF48FB1), mascotTint = Color(0xFFEC407A)
    )

    val NEUTRAL = MoodPalette(
        lightTop = Color(0xFFE1BEE7), lightMid = Color(0xFFCE93D8), lightBottom = Color(0xFFF3E5F5),
        lightSurface = Color(0xFFF3E5F5), lightText = Color(0xFF1A1A1A), lightTextSub = Color(0xFF7B1FA2),
        darkTop = Color(0xFF1A0033), darkMid = Color(0xFF140028), darkBottom = Color(0xFF0A0014),
        darkSurface = Color(0xFF1C0030), darkText = Color.White, darkTextSub = Color(0xFFCE93D8),
        accent = Color(0xFF9C27B0), glowColor = Color(0xFFCE93D8), mascotTint = Color(0xFFAB47BC)
    )

    fun forMood(mood: String): MoodPalette = when (mood.lowercase()) {
        "happy" -> HAPPY
        "sad", "down" -> SAD
        "calm", "chill", "relax" -> CALM
        "energetic", "hype", "excited" -> ENERGETIC
        "focused", "study" -> FOCUSED
        "tired", "sleepy" -> TIRED
        "romantic", "love" -> ROMANTIC
        else -> NEUTRAL
    }

    fun allPresets(): List<Pair<String, MoodPalette>> = listOf(
        "Happy" to HAPPY, "Calm" to CALM, "Energetic" to ENERGETIC,
        "Focused" to FOCUSED, "Sad" to SAD, "Tired" to TIRED,
        "Romantic" to ROMANTIC, "Neutral" to NEUTRAL
    )
}

// ── Theme state ──────────────────────────────────────────────────────────

data class AppThemeState(
    val mode: BackgroundMode = BackgroundMode.DYNAMIC_LIGHT,
    val currentMood: String = "neutral",
    val staticPreset: String = "neutral",
    val transitionSpeed: Int = 2000
) {
    val isDark: Boolean get() = mode == BackgroundMode.DYNAMIC_DARK
    val isDynamic: Boolean get() = mode == BackgroundMode.DYNAMIC_LIGHT || mode == BackgroundMode.DYNAMIC_DARK

    val activePalette: MoodPalette get() = when (mode) {
        BackgroundMode.DYNAMIC_LIGHT, BackgroundMode.DYNAMIC_DARK -> MoodPalettes.forMood(currentMood)
        BackgroundMode.STATIC_PRESET -> MoodPalettes.forMood(staticPreset)
        BackgroundMode.CUSTOM_PHOTO -> MoodPalettes.NEUTRAL
    }
}

// ── Theme manager ────────────────────────────────────────────────────────

class ThemeManager(context: Context) {
    private val TAG = "ThemeManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeState = MutableStateFlow(loadFromPrefs())
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    private fun loadFromPrefs(): AppThemeState {
        val modeStr = prefs.getString("bg_mode", "DYNAMIC_LIGHT") ?: "DYNAMIC_LIGHT"
        val mode = try { BackgroundMode.valueOf(modeStr) } catch (_: Exception) { BackgroundMode.DYNAMIC_LIGHT }
        val preset = prefs.getString("static_preset", "neutral") ?: "neutral"
        val speed = prefs.getInt("transition_speed", 2000)
        return AppThemeState(mode = mode, staticPreset = preset, transitionSpeed = speed)
    }

    fun setMode(mode: BackgroundMode) {
        _themeState.value = _themeState.value.copy(mode = mode)
        prefs.edit().putString("bg_mode", mode.name).apply()
    }

    fun setStaticPreset(mood: String) {
        _themeState.value = _themeState.value.copy(staticPreset = mood, mode = BackgroundMode.STATIC_PRESET)
        prefs.edit().putString("static_preset", mood).putString("bg_mode", "STATIC_PRESET").apply()
    }

    fun setTransitionSpeed(ms: Int) {
        _themeState.value = _themeState.value.copy(transitionSpeed = ms)
        prefs.edit().putInt("transition_speed", ms).apply()
    }

    fun updateMood(mood: String) {
        if (_themeState.value.isDynamic) {
            _themeState.value = _themeState.value.copy(currentMood = mood)
        }
    }
}

// ── Composable helpers ───────────────────────────────────────────────────

/**
 * VIVID full-screen background. Uses 3-color gradient for more depth.
 * In dark mode: deep rich colors. In light mode: warm saturated pastels.
 */
@Composable
fun animatedMoodBrush(themeState: AppThemeState): Brush {
    val p = themeState.activePalette
    val s = themeState.transitionSpeed
    val dark = themeState.isDark

    val c1 by animateColorAsState(if (dark) p.darkTop else p.lightTop, tween(s), label = "b1")
    val c2 by animateColorAsState(if (dark) p.darkMid else p.lightMid, tween(s), label = "b2")
    val c3 by animateColorAsState(if (dark) p.darkBottom else p.lightBottom, tween(s), label = "b3")

    return Brush.verticalGradient(listOf(c1, c2, c3))
}

/**
 * Subtle background for screens with content cards on top.
 * Still CLEARLY tinted — not almost-white like before.
 * Dark mode: deep mood-tinted black. Light mode: colored pastels.
 */
@Composable
fun animatedMoodBrushLight(themeState: AppThemeState): Brush {
    val p = themeState.activePalette
    val s = themeState.transitionSpeed
    val dark = themeState.isDark

    val top by animateColorAsState(
        if (dark) p.darkTop else p.lightTop,
        tween(s), label = "lt"
    )
    val mid by animateColorAsState(
        if (dark) p.darkMid else p.lightMid,
        tween(s), label = "lm"
    )
    val bottom by animateColorAsState(
        if (dark) p.darkBottom else p.lightBottom,
        tween(s), label = "lb"
    )

    return Brush.verticalGradient(listOf(top, mid, bottom))
}

/**
 * Animated text color that adapts to dark/light mode
 */
@Composable
fun animatedTextColor(themeState: AppThemeState): Color {
    val c by animateColorAsState(
        if (themeState.isDark) themeState.activePalette.darkText
        else themeState.activePalette.lightText,
        tween(themeState.transitionSpeed), label = "txt"
    )
    return c
}

/**
 * Animated secondary text color
 */
@Composable
fun animatedTextSubColor(themeState: AppThemeState): Color {
    val c by animateColorAsState(
        if (themeState.isDark) themeState.activePalette.darkTextSub
        else themeState.activePalette.lightTextSub,
        tween(themeState.transitionSpeed), label = "txts"
    )
    return c
}

/**
 * Animated surface color for cards
 */
@Composable
fun animatedSurfaceColor(themeState: AppThemeState): Color {
    val c by animateColorAsState(
        if (themeState.isDark) themeState.activePalette.darkSurface
        else themeState.activePalette.lightSurface,
        tween(themeState.transitionSpeed), label = "srf"
    )
    return c
}

@Composable
fun animatedAccentColor(themeState: AppThemeState): Color {
    val c by animateColorAsState(themeState.activePalette.accent, tween(themeState.transitionSpeed), label = "acc")
    return c
}

@Composable
fun animatedGlowColor(themeState: AppThemeState): Color {
    val c by animateColorAsState(themeState.activePalette.glowColor, tween(themeState.transitionSpeed), label = "glw")
    return c
}

@Composable
fun animatedMascotTint(themeState: AppThemeState): Color {
    val c by animateColorAsState(themeState.activePalette.mascotTint, tween(themeState.transitionSpeed), label = "msc")
    return c
}