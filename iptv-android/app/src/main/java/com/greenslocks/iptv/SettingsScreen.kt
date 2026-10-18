package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/**
 * One row in the settings screen. A row with [choices] opens a list to pick from; any other row runs
 * [action] when selected (toggles just flip and the screen refreshes).
 */
data class SettingItem(
    val title: String,
    val value: String? = null,
    val desc: String? = null,
    val enabled: Boolean = true,
    /** Close the settings screen after the action (for rows that open another full screen). */
    val closes: Boolean = false,
    val swatch: Color? = null,
    val choices: List<String>? = null,
    val selected: Int = 0,
    val onPick: (Int) -> Unit = {},
    val action: () -> Unit = {},
)

private fun sectionIcon(title: String): ImageVector = when {
    title.startsWith("Appearance") -> Icons.Rounded.Palette
    title.startsWith("Layout") -> Icons.Rounded.Dashboard
    title.startsWith("Playback") -> Icons.Rounded.PlayCircle
    title.startsWith("Guide") -> Icons.Rounded.Schedule
    title.startsWith("Sports") -> Icons.Rounded.SportsScore
    title.startsWith("Profiles") -> Icons.Rounded.Person
    title.startsWith("Data") -> Icons.Rounded.CloudSync
    title.startsWith("About") -> Icons.Rounded.Info
    else -> Icons.Rounded.Tune
}

data class SettingSection(val title: String, val hint: String, val items: List<SettingItem>)

/** Settings with categories on the left and that category's options on the right. */
@Composable
fun SettingsScreen(sections: List<SettingSection>, onClose: () -> Unit) {
    var sel by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<SettingItem?>(null) }
    val first = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { repeat(15) { delay(80); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    var q by remember { mutableStateOf("") }
    val picked = sections.getOrNull(sel.coerceIn(0, (sections.size - 1).coerceAtLeast(0)))
    val section = if (q.isBlank()) picked else {
        val hits = sections.flatMap { sec ->
            sec.items.filter { it.title.contains(q, true) || it.desc.orEmpty().contains(q, true) || it.value.orEmpty().contains(q, true) }
                .map { it.copy(desc = listOfNotNull(sec.title, it.desc).joinToString("  \u00B7  ")) }
        }
        SettingSection("Search", if (hits.isEmpty()) "Nothing matches \u201C$q\u201D." else "${hits.size} results for \u201C$q\u201D", hits)
    }

    Row(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accentWash(0.16f), Palette.bg)))) {
        Column(Modifier.width(300.dp).fillMaxHeight().background(Palette.surface).padding(horizontal = 16.dp, vertical = 22.dp)) {
            BrandWordmark(16.sp, Modifier.padding(start = 6.dp, bottom = 10.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp))
            TvTextField(q, { q = it }, "Search settings", Modifier.fillMaxWidth().padding(bottom = 10.dp))
            sections.forEachIndexed { i, s ->
                FocusCard(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    RoundedCornerShape(12.dp), 1f, 2.dp,
                    onFocus = { sel = i },
                    onClick = { sel = i; focusManager.moveFocus(FocusDirection.Right) },
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (focused) Palette.accent.copy(alpha = 0.28f) else if (i == sel) Palette.accent.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(sectionIcon(s.title), null, Modifier.size(22.dp), tint = if (focused || i == sel) Palette.accent else Palette.muted)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s.title, style = MaterialTheme.typography.titleSmall,
                            color = if (focused || i == sel) Palette.text else Palette.muted,
                        )
                    }
                }
            }
            Text(
                "Back to close", color = Palette.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 6.dp, top = 18.dp),
            )
        }
        if (section != null) {
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 26.dp, bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(sectionIcon(section.title), null, Modifier.size(30.dp), tint = Palette.accent)
                            Spacer(Modifier.width(12.dp))
                            Text(section.title, style = MaterialTheme.typography.headlineMedium)
                        }
                        if (section.hint.isNotBlank()) Text(section.hint, color = Palette.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                items(section.items) { item -> SettingRow(item) { if (item.choices != null) picking = item else { item.action(); if (item.closes) onClose() } } }
                item { Spacer(Modifier.size(40.dp)) }
            }
        }
    }

    picking?.let { item ->
        ActionMenuDialog(
            item.title,
            (item.choices ?: emptyList()).mapIndexed { i, c ->
                MenuItem((if (i == item.selected) "✓  " else "      ") + c) { item.onPick(i) }
            },
        ) { picking = null }
    }
}

@Composable
private fun SettingRow(item: SettingItem, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), 1f, 2.dp, onClick = { if (item.enabled) onClick() }) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Palette.accent.copy(alpha = 0.22f) else Palette.surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title, style = MaterialTheme.typography.titleSmall,
                    color = if (item.enabled) Palette.text else Palette.muted,
                )
                if (!item.desc.isNullOrBlank()) {
                    Text(item.desc, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (item.value != null) {
                Row(
                    Modifier.padding(start = 16.dp).clip(RoundedCornerShape(50)).background(Palette.surfaceHi)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.swatch != null) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(item.swatch))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(item.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    if (item.choices != null) Text("  ▸", color = Palette.muted)
                }
            }
        }
    }
}
