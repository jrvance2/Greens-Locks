package com.greenslocks.iptv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
    /** 0 = full animations, 1 = reduced, 2 = off. */
    var animLevel by mutableIntStateOf(0)

    val posterWidth: Dp get() = (140 * cardScale).dp
    val wideWidth: Dp get() = (250 * cardScale).dp
    val favWidth: Dp get() = (230 * cardScale).dp
}

data class BgPreset(val name: String, val bg: Color, val surface: Color, val surfaceHi: Color)

val ACCENT_NAMES = listOf("Indigo", "Coral", "Teal", "Amber", "Green", "Pink")
val ACCENTS = listOf(
    Color(0xFF6C8CFF) to Color(0xFFA27BFF),
    Color(0xFFFF5C6C) to Color(0xFFFF9A5C),
    Color(0xFF2DD4BF) to Color(0xFF38BDF8),
    Color(0xFFFABE3C) to Color(0xFFFF8A3C),
    Color(0xFF4ADE80) to Color(0xFF2DD4BF),
    Color(0xFFF472B6) to Color(0xFFA78BFA),
)
val BACKGROUNDS = listOf(
    BgPreset("Navy", Color(0xFF080C16), Color(0xFF111827), Color(0xFF1B2640)),
    BgPreset("Black", Color(0xFF000000), Color(0xFF0C0C10), Color(0xFF1A1A22)),
    BgPreset("Graphite", Color(0xFF14161B), Color(0xFF1D2027), Color(0xFF2A2E38)),
)
val CARD_NAMES = listOf("Compact", "Comfortable", "Large")
val CARD_SCALES = listOf(0.86f, 1f, 1.2f)
val TEXT_NAMES = listOf("Small", "Normal", "Large")
val TEXT_SCALES = listOf(0.9f, 1f, 1.15f)
val ANIM_NAMES = listOf("Full", "Reduced", "Off")
val START_NAMES = listOf("Home", "Live TV", "Last channel")

fun applyAppearance(accent: Int, bg: Int, cards: Int, text: Int, anim: Int) {
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
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Dimens.textScale)) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Palette.accent, onPrimary = Color.White,
                secondary = Palette.accent2,
                background = Palette.bg, onBackground = Palette.text,
                surface = Palette.surface, onSurface = Palette.text,
                surfaceVariant = Palette.surfaceHi, onSurfaceVariant = Palette.muted,
                error = Palette.live,
            ),
        ) {
            Surface(color = Palette.bg, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
