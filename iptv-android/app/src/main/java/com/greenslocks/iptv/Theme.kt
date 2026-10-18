package com.greenslocks.iptv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared timings and easing so every animation in the app feels related. */
object Motion {
    const val FAST = 110
    const val MED = 220
    const val SLOW = 380
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Colors are Compose state, so changing the accent or background repaints the whole app live. */
object Palette {
    var bg by mutableStateOf(Color(0xFF080C16))
    var surface by mutableStateOf(Color(0xFF111827))
    var surfaceHi by mutableStateOf(Color(0xFF1B2640))
    var accent by mutableStateOf(Color(0xFF6C8CFF))
    var accent2 by mutableStateOf(Color(0xFFA27BFF))
    val text = Color(0xFFEEF2FF)
    val muted = Color(0xFF8E9BB8)
    val live = Color(0xFFFF4D5E)
}

/** Sizing and motion preferences. */
object Dimens {
    var cardScale by mutableFloatStateOf(1f)
    var textScale by mutableFloatStateOf(1f)
    /** Zooms everything (text and layout) so a 4K TV can show more on screen. */
    var uiScale by mutableFloatStateOf(0.85f)
    /** false = icons only in the side menu (gives content more width). */
    var railLabels by mutableStateOf(true)
    /** Soft click sound when focus moves (uses the system's navigation sounds). */
    var sounds by mutableStateOf(false)

    // ---- layout options (see LayoutPrefs)
    var corner by mutableStateOf(12.dp)
    var rowScale by mutableFloatStateOf(1f)
    var guideRow by mutableStateOf(56.dp)
    /** 0 = card, 1 = minimal, 2 = hidden. */
    var overlayStyle by mutableIntStateOf(0)
    var showClock by mutableStateOf(true)
    var topNav by mutableStateOf(false)
    var homeHero by mutableStateOf(true)
    var homeChips by mutableStateOf(true)
    var glow by mutableStateOf(true)
    var heroTint by mutableStateOf(true)
    var subBg by mutableIntStateOf(1)
    var subEdge by mutableIntStateOf(1)
    var subColor by mutableIntStateOf(0)
    var skipBack by mutableIntStateOf(10)
    var skipFwd by mutableIntStateOf(10)
    var keepFrame by mutableStateOf(true)
    var ambient by mutableStateOf(true)
    var askProfile by mutableStateOf(true)
    /** What happens to playback when you leave the app: 0 stop, 1 small window (PiP), 2 keep playing. */
    var exitMode by mutableIntStateOf(0)
    /** 0 = full animations, 1 = reduced, 2 = off. */
    var animLevel by mutableIntStateOf(0)

    val posterWidth: Dp get() = (140 * cardScale).dp
    val wideWidth: Dp get() = (250 * cardScale).dp
    val favWidth: Dp get() = (230 * cardScale).dp
}

data class BgPreset(val name: String, val bg: Color, val surface: Color, val surfaceHi: Color)

private fun hueAccent(h: Float) = Color.hsv(h, 0.62f, 1f) to Color.hsv((h + 35f) % 360f, 0.55f, 1f)

val ACCENT_NAMES = listOf(
    "Indigo", "Coral", "Teal", "Amber", "Green", "Pink",
    "Red", "Orange", "Gold", "Lime", "Mint", "Cyan", "Sky", "Blue", "Violet", "Purple", "Magenta",
)
val ACCENTS = listOf(
    Color(0xFF6C8CFF) to Color(0xFFA27BFF),
    Color(0xFFFF5C6C) to Color(0xFFFF9A5C),
    Color(0xFF2DD4BF) to Color(0xFF38BDF8),
    Color(0xFFFABE3C) to Color(0xFFFF8A3C),
    Color(0xFF4ADE80) to Color(0xFF2DD4BF),
    Color(0xFFF472B6) to Color(0xFFA78BFA),
    hueAccent(355f), hueAccent(24f), hueAccent(45f), hueAccent(85f), hueAccent(150f), hueAccent(185f),
    hueAccent(205f), hueAccent(225f), hueAccent(262f), hueAccent(285f), hueAccent(315f),
)
val BACKGROUNDS = listOf(
    BgPreset("Navy", Color(0xFF080C16), Color(0xFF111827), Color(0xFF1B2640)),
    BgPreset("Black", Color(0xFF000000), Color(0xFF0C0C10), Color(0xFF1A1A22)),
    BgPreset("Graphite", Color(0xFF14161B), Color(0xFF1D2027), Color(0xFF2A2E38)),
    BgPreset("Slate", Color(0xFF0B1220), Color(0xFF162033), Color(0xFF243049)),
    BgPreset("Plum", Color(0xFF140A1E), Color(0xFF1F1330), Color(0xFF2E1D47)),
    BgPreset("Forest", Color(0xFF07130F), Color(0xFF0F1F19), Color(0xFF1A332A)),
    BgPreset("Ocean", Color(0xFF06141A), Color(0xFF0D222B), Color(0xFF163542)),
    BgPreset("Espresso", Color(0xFF14100D), Color(0xFF201A15), Color(0xFF30271F)),
    BgPreset("Crimson night", Color(0xFF160709), Color(0xFF241014), Color(0xFF3A1A20)),
    BgPreset("Midnight blue", Color(0xFF050A1A), Color(0xFF0B1630), Color(0xFF14264A)),
)
val CARD_NAMES = listOf("Compact", "Comfortable", "Large")
val CARD_SCALES = listOf(0.86f, 1f, 1.2f)
val TEXT_NAMES = listOf("Small", "Normal", "Large")
val TEXT_SCALES = listOf(0.9f, 1f, 1.15f)
val UI_NAMES = listOf("Dense 75%", "Compact 85%", "Standard 100%", "Large 115%")
val UI_SCALES = listOf(0.75f, 0.85f, 1f, 1.15f)
private fun acc(name: String) = ACCENT_NAMES.indexOf(name).coerceAtLeast(0)
private fun bg(name: String) = BACKGROUNDS.indexOfFirst { it.name == name }.coerceAtLeast(0)
val THEME_NAMES = listOf(
    "Midnight", "OLED Teal", "Warm Amber", "Forest", "Rose",
    "Plum Violet", "Ocean Cyan", "Slate Sky", "Crimson", "Espresso Gold", "Midnight Blue",
)
val THEME_PICKS = listOf(
    acc("Indigo") to bg("Navy"), acc("Teal") to bg("Black"), acc("Amber") to bg("Graphite"),
    acc("Green") to bg("Graphite"), acc("Pink") to bg("Black"),
    acc("Violet") to bg("Plum"), acc("Cyan") to bg("Ocean"), acc("Sky") to bg("Slate"),
    acc("Red") to bg("Crimson night"), acc("Gold") to bg("Espresso"), acc("Blue") to bg("Midnight blue"),
)
val ANIM_NAMES = listOf("Full", "Reduced", "Off")
val START_NAMES = listOf("Home", "Live TV", "Last channel")

fun applyAppearance(accent: Int, bg: Int, cards: Int, text: Int, anim: Int, ui: Int = 1) {
    Dimens.uiScale = UI_SCALES[ui.coerceIn(0, UI_SCALES.lastIndex)]
    val a = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)]
    Palette.accent = a.first
    Palette.accent2 = a.second
    val b = BACKGROUNDS[bg.coerceIn(0, BACKGROUNDS.lastIndex)]
    Palette.bg = b.bg
    Palette.surface = b.surface
    Palette.surfaceHi = b.surfaceHi
    Dimens.cardScale = CARD_SCALES[cards.coerceIn(0, CARD_SCALES.lastIndex)]
    Dimens.textScale = TEXT_SCALES[text.coerceIn(0, TEXT_SCALES.lastIndex)]
    Dimens.animLevel = anim.coerceIn(0, 2)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * Dimens.uiScale, d.fontScale * Dimens.textScale)) {
        MaterialTheme(
            typography = VanceTypography,
            colorScheme = darkColorScheme(
                primary = Palette.accent, onPrimary = Color.White,
                secondary = Palette.accent2,
                background = Palette.bg, onBackground = Palette.text,
                surface = Palette.surface, onSurface = Palette.text,
                surfaceVariant = Palette.surfaceHi, onSurfaceVariant = Palette.muted,
                error = Palette.live,
            ),
        ) {
            Surface(color = Palette.bg, modifier = Modifier.fillMaxSize()) {
                ProvideTextStyle(VanceTypography.bodyMedium, content = content)
            }
        }
    }
}
