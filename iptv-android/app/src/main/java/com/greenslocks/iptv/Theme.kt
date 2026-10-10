package com.greenslocks.iptv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFF080C16)
    val surface = Color(0xFF111827)
    val surfaceHi = Color(0xFF1B2640)
    val accent = Color(0xFF6C8CFF)
    val accent2 = Color(0xFFA27BFF)
    val text = Color(0xFFEEF2FF)
    val muted = Color(0xFF8E9BB8)
    val live = Color(0xFFFF4D5E)
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
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
