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

/** "8.1 · 2021 · Action, Drama · 2h 10m" -> separate pills, plus quality, age rating, format words and "added" info. */
@Composable
fun MetaChips(
    meta: String, title: String, modifier: Modifier = Modifier,
    certification: String = "", tags: List<String> = emptyList(), added: Long = 0L, watched: Boolean = false,
) {
    val parts = remember(meta) { meta.split(Regex("\\s*[\\u00B7|\\u2022]\\s*")).map { it.trim() }.filter { it.isNotEmpty() } }
    val quality = remember(title) { qualityBadge(title) }
    val addedLabel = remember(added) { AddedText.label(added, System.currentTimeMillis()) }
    val isNew = remember(added) { AddedText.isNew(added, System.currentTimeMillis()) }
    if (parts.isEmpty() && quality == null && certification.isBlank() && tags.isEmpty() && addedLabel == null && !watched) return
    fun gradient(text: String): @Composable () -> Unit = {
        Text(
            text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2)))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isNew) item { gradient("NEW")() }
        if (watched) item { gradient("\u2713 Watched")() }
        if (quality != null) item { gradient(quality)() }
        if (certification.isNotBlank()) item {
            Text(
                certification, color = Palette.text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Palette.text.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        itemsIndexed(parts) { i, p ->
            Text(
                if (i == 0 && p.toDoubleOrNull() != null) "\u2605 $p" else p,
                color = Palette.text.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        itemsIndexed(tags) { _, t ->
            Text(
                t, color = Palette.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Palette.accent.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (addedLabel != null) item { Text(addedLabel, color = Palette.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) }
    }
}

private val q4k = Regex("\\b(4K|UHD|2160P?)\\b")
private val qFhd = Regex("\\b(FHD|1080P?)\\b")
private val qHd = Regex("\\b(HD|720P?)\\b")

fun qualityBadge(title: String): String? {
    val t = title.uppercase()
    return when {
        q4k.containsMatchIn(t) -> "4K"
        qFhd.containsMatchIn(t) -> "FHD"
        qHd.containsMatchIn(t) -> "HD"
        else -> null
    }
}

/** Cast as round initials with first names, in a single scrolling row. Select an actor to see their movies in your library. */
@Composable
fun CastRow(cast: List<String>, ids: List<Int> = emptyList(), onActor: (String, Int) -> Unit = { _, _ -> }, modifier: Modifier = Modifier) {
    if (cast.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(cast.take(10)) { i, n ->
            val id = ids.getOrNull(i) ?: 0
            val chip: @Composable (Boolean) -> Unit = { focused ->
                Row(
                    Modifier.background(if (focused) Palette.accent.copy(alpha = 0.25f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(avatarBrush(n)), contentAlignment = Alignment.Center) {
                        Text(initialsOf(n), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        n, Modifier.padding(start = 8.dp).widthIn(max = 130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (focused) Palette.text else Palette.muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (id > 0) FocusCard(Modifier, RoundedCornerShape(50), 1.04f, 2.dp, onClick = { onActor(n, id) }) { focused -> chip(focused) }
            else chip(false)
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
        0f, if (Dimens.animLevel == 0) 1f else 0f,
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
                // Draw the glow once into a texture and just slide it, instead of re-filling gradients every frame.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
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
