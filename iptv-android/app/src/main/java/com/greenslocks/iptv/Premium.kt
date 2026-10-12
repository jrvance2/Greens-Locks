package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** "8.1 · 2021 · Action, Drama · 2h 10m" -> separate pills, plus a quality badge read from the title. */
@Composable
fun MetaChips(meta: String, title: String, modifier: Modifier = Modifier) {
    val parts = remember(meta) { meta.split(Regex("\\s*[\\u00B7|\\u2022]\\s*")).map { it.trim() }.filter { it.isNotEmpty() } }
    val quality = remember(title) { qualityBadge(title) }
    if (parts.isEmpty() && quality == null) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (quality != null) itemsIndexed(listOf(quality)) { _, q ->
            Text(
                q, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2)))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        itemsIndexed(parts) { i, p ->
            Text(
                if (i == 0 && p.toDoubleOrNull() != null) "★ $p" else p,
                color = Palette.text.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

fun qualityBadge(title: String): String? {
    val t = title.uppercase()
    return when {
        Regex("\\b(4K|UHD|2160P?)\\b").containsMatchIn(t) -> "4K"
        Regex("\\b(FHD|1080P?)\\b").containsMatchIn(t) -> "FHD"
        Regex("\\b(HD|720P?)\\b").containsMatchIn(t) -> "HD"
        else -> null
    }
}

/** Cast as round initials with first names, in a single scrolling row. */
@Composable
fun CastRow(cast: List<String>, modifier: Modifier = Modifier) {
    if (cast.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(cast.take(10)) { _, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(avatarBrush(n)),
                    contentAlignment = Alignment.Center,
                ) { Text(initialsOf(n), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Text(
                    n, Modifier.padding(start = 8.dp).widthIn(max = 130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Palette.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

fun avatarBrush(seed: String): Brush {
    val i = kotlin.math.abs(seed.hashCode()) % ACCENTS.size
    return Brush.linearGradient(listOf(ACCENTS[i].first, ACCENTS[i].second))
}

/** Full-screen "Who's watching?" picker shown when the app opens and more than one profile exists. */
@Composable
fun ProfilePicker(profiles: List<Profile>, kids: (Int) -> Boolean, activeId: Int, onPick: (Profile) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(10) { delay(60); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accentWash(0.20f), Palette.bg))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandWordmark(22.sp)
            Text("Who's watching?", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 22.dp, bottom = 36.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp)) {
                itemsIndexed(profiles) { i, p ->
                    val isKids = kids(p.id)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusCard(
                            Modifier.size(112.dp).then(if (p.id == activeId || (i == 0 && profiles.none { it.id == activeId })) Modifier.focusRequester(first) else Modifier),
                            CircleShape, 1.14f, 3.dp, onClick = { onPick(p) },
                        ) {
                            Box(Modifier.fillMaxSize().background(avatarBrush(p.name + p.id)), contentAlignment = Alignment.Center) {
                                Text(p.name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(p.name, Modifier.padding(top = 12.dp).widthIn(max = 130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (isKids) Text(
                            "KIDS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).background(Palette.accent).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Slowly drifting pools of the accent colors behind Home. Only the layer moves, so it costs almost nothing. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambient")
    val drift by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(40_000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val a = Palette.accent
    val b = Palette.accent2
    Box(
        modifier
            .graphicsLayer {
                scaleX = 1.35f; scaleY = 1.35f
                translationX = (drift - 0.5f) * size.width * 0.16f
                translationY = (0.5f - drift) * size.height * 0.12f
            }
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.16f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.25f), size.minDimension * 0.75f))
                drawRect(Brush.radialGradient(listOf(b.copy(alpha = 0.13f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.7f), size.minDimension * 0.8f))
                drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.08f), Color.Transparent), Offset(size.width * 0.55f, size.height * 1.0f), size.minDimension * 0.6f))
            },
    )
}
