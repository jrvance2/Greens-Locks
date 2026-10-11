package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
private fun SettingRow(label: String, desc: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Palette.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(desc, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Every change applies immediately, so this screen is its own live preview. */
@Composable
fun AppearanceScreen(
    accent: Int, bg: Int, cards: Int, text: Int, anim: Int, start: Int,
    onAccent: (Int) -> Unit, onBg: (Int) -> Unit, onCards: (Int) -> Unit,
    onText: (Int) -> Unit, onAnim: (Int) -> Unit, onStart: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(15) { delay(80); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accentWash(0.2f), Palette.bg)))
            .padding(horizontal = 36.dp, vertical = 24.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Settings  ›  Appearance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Changes apply instantly. Pick what looks best on your TV.", color = Palette.muted)
            }
            ActionButton("Done", onClick = onClose)
        }
        Spacer(Modifier.height(14.dp))
        SettingRow("Accent color", "Buttons, focus highlights and progress bars") {
            ACCENTS.forEachIndexed { i, (a, b) ->
                FocusCard(
                    (if (i == accent) Modifier.focusRequester(first) else Modifier), CircleShape, 1.12f, 3.dp,
                    onClick = { onAccent(i) },
                ) {
                    Box(
                        Modifier.size(38.dp).background(Brush.linearGradient(listOf(a, b))),
                        contentAlignment = Alignment.Center,
                    ) { if (i == accent) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        SettingRow("Background", "Navy is easy on the eyes; Black suits OLED TVs") {
            BACKGROUNDS.forEachIndexed { i, p -> Chip(p.name, bg == i) { onBg(i) } }
        }
        SettingRow("Card size", "Compact fits more on screen; Large reads better from the couch") {
            CARD_NAMES.forEachIndexed { i, n -> Chip(n, cards == i) { onCards(i) } }
        }
        SettingRow("Text size", "Scales every label in the app") {
            TEXT_NAMES.forEachIndexed { i, n -> Chip(n, text == i) { onText(i) } }
        }
        SettingRow("Animations", "Reduced or Off is smoothest on slower devices") {
            ANIM_NAMES.forEachIndexed { i, n -> Chip(n, anim == i) { onAnim(i) } }
        }
        SettingRow("Start screen", "What opens when you launch Vance TV") {
            START_NAMES.forEachIndexed { i, n -> Chip(n, start == i) { onStart(i) } }
        }
    }
}
