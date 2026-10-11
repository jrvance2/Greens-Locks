package com.greenslocks.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class Shelf(val title: String, val items: List<Entry>)

/** The accent color faded into the background, so gradients follow the chosen theme. */
fun accentWash(alpha: Float, second: Boolean = false): Color =
    (if (second) Palette.accent2 else Palette.accent).copy(alpha = alpha).compositeOver(Palette.bg)

// ------------------------------------------------------------------ navigation rail

enum class RailIconType { HOME, LIVE, MOVIES, SERIES, FAVORITES, GUIDE, SETTINGS }

data class RailItem(val icon: RailIconType, val label: String, val selected: Boolean)

@Composable
fun RailIcon(type: RailIconType, color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (type) {
            RailIconType.HOME -> drawPath(
                Path().apply {
                    moveTo(w * 0.08f, h * 0.5f); lineTo(w * 0.5f, h * 0.1f); lineTo(w * 0.92f, h * 0.5f)
                    lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.8f, h * 0.9f); lineTo(w * 0.2f, h * 0.9f)
                    lineTo(w * 0.2f, h * 0.5f); close()
                }, color,
            )
            RailIconType.LIVE -> {
                drawCircle(color, radius = w * 0.4f, style = Stroke(width = w * 0.11f))
                drawCircle(color, radius = w * 0.16f)
            }
            RailIconType.MOVIES -> drawPath(
                Path().apply { moveTo(w * 0.25f, h * 0.1f); lineTo(w * 0.25f, h * 0.9f); lineTo(w * 0.92f, h * 0.5f); close() }, color,
            )
            RailIconType.SERIES -> for (k in 0..2) {
                drawRoundRect(
                    color, topLeft = Offset(w * (0.08f + k * 0.08f), h * (0.1f + k * 0.3f)),
                    size = Size(w * 0.7f, h * 0.22f), cornerRadius = CornerRadius(w * 0.06f),
                )
            }
            RailIconType.FAVORITES -> drawPath(
                Path().apply {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) w * 0.46f else w * 0.2f
                        val ang = Math.toRadians((-90 + i * 36).toDouble())
                        val x = w * 0.5f + (r * Math.cos(ang)).toFloat()
                        val y = h * 0.54f + (r * Math.sin(ang)).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }, color,
            )
            RailIconType.GUIDE -> for (gx in 0..2) for (gy in 0..2) {
                drawRoundRect(
                    color, topLeft = Offset(w * (0.06f + gx * 0.32f), h * (0.06f + gy * 0.32f)),
                    size = Size(w * 0.24f, h * 0.24f), cornerRadius = CornerRadius(w * 0.05f),
                )
            }
            RailIconType.SETTINGS -> {
                drawCircle(color, radius = w * 0.38f, style = Stroke(width = w * 0.16f))
                drawCircle(color, radius = w * 0.12f)
            }
        }
    }
}

@Composable
fun IconRail(
    items: List<RailItem>,
    modifier: Modifier = Modifier,
    onItem: (Int) -> Unit,
) {
    Column(
        modifier.fillMaxHeight().background(Palette.surface).padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Palette.accent, Palette.accent2))),
            contentAlignment = Alignment.Center,
        ) { Text("V", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
        Spacer(Modifier.height(14.dp))
        items.forEachIndexed { i, item ->
            FocusCard(Modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(12.dp), 1f, 2.dp, onClick = { onItem(i) }) { focused ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            if (focused) Palette.accent.copy(alpha = 0.3f)
                            else if (item.selected) Palette.accent.copy(alpha = 0.18f) else Color.Transparent
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RailIcon(item.icon, if (item.selected || focused) Palette.accent else Palette.muted)
                    Text(
                        item.label, fontSize = 10.sp, maxLines = 1,
                        color = if (item.selected || focused) Palette.text else Palette.muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ login

@Composable
fun LoginScreen(
    useUrl: Boolean,
    urlText: String,
    server: String,
    user: String,
    pass: String,
    showPass: Boolean,
    status: String,
    busy: Boolean,
    canCancel: Boolean,
    onUrl: (String) -> Unit,
    onServer: (String) -> Unit,
    onUser: (String) -> Unit,
    onPass: (String) -> Unit,
    onTogglePass: () -> Unit,
    onToggleMode: () -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(accentWash(0.22f), Palette.bg, accentWash(0.12f, true)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(20.dp)).background(Palette.surface.copy(alpha = 0.92f)).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandWordmark(30.sp)
            Text("Sign in with your provider login", color = Palette.muted, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
            if (useUrl) {
                OutlinedTextField(
                    value = urlText, onValueChange = onUrl, singleLine = true,
                    label = { Text("Playlist URL") }, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = server, onValueChange = onServer, singleLine = true,
                    label = { Text("Server (http://host:port)") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = onUser, singleLine = true,
                    label = { Text("Username") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = pass, onValueChange = onPass, singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Text(
                if (busy) "Connecting…" else status,
                Modifier.padding(top = 12.dp), color = if (status.startsWith("Failed")) Palette.live else Palette.muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Connect", onClick = onConnect)
                if (!useUrl) ActionButton(if (showPass) "Hide password" else "Show password", primary = false, onClick = onTogglePass)
                if (canCancel) ActionButton("Cancel", primary = false, onClick = onCancel)
            }
            Row(Modifier.padding(top = 10.dp)) {
                ActionButton(if (useUrl) "Use username / password" else "Use playlist URL", primary = false, onClick = onToggleMode)
            }
        }
    }
}

// ------------------------------------------------------------------ home hero

@Composable
fun HeroBanner(
    entry: Entry,
    label: String,
    title: String,
    subtitle: String,
    canOpenDetails: Boolean,
    playFocus: Modifier,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(260.dp)
            .background(Brush.horizontalGradient(listOf(accentWash(0.32f), accentWash(0.14f, true), Palette.bg)))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = Palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (subtitle.isNotEmpty()) Text(subtitle, color = Palette.muted, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("▶  Play", playFocus, onClick = onPlay)
                    if (canOpenDetails) ActionButton("Details", primary = false, onClick = onDetails)
                }
            }
            Artwork(
                entry.icon, title,
                Modifier.fillMaxHeight().aspectRatio(if (entry.kind == Kind.LIVE) 1.6f else 2f / 3f)
                    .clip(RoundedCornerShape(14.dp)),
                if (entry.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
            )
        }
    }
}

// ------------------------------------------------------------------ detail page

@Composable
fun DetailScreen(
    entry: Entry,
    name: String,
    detail: Detail?,
    resumeMs: Long,
    isFavorite: Boolean,
    episodes: List<Entry>,
    episodeProgress: (Entry) -> Float?,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onFavorite: () -> Unit,
    onEpisode: (Entry) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(entry.key(), episodes.size) { delay(250); runCatching { first.requestFocus() } }
    val poster = detail?.poster?.takeIf { it.isNotBlank() } ?: entry.icon
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    var season by remember(entry.key()) { mutableStateOf<Int?>(null) }
    val activeSeason = season ?: seasons.firstOrNull()

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(accentWash(0.26f), Palette.bg, accentWash(0.12f, true)))
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Artwork(
                poster, name,
                Modifier.width(210.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail == null) {
                    Text("Loading details…", color = Palette.muted, modifier = Modifier.padding(top = 6.dp))
                } else {
                    if (detail.meta.isNotBlank()) Text(detail.meta, color = Palette.accent, modifier = Modifier.padding(top = 6.dp))
                    if (detail.plot.isNotBlank()) {
                        Text(detail.plot, maxLines = 5, overflow = TextOverflow.Ellipsis, color = Palette.text.copy(alpha = 0.85f), modifier = Modifier.padding(top = 10.dp))
                    }
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (entry.kind == Kind.MOVIE) {
                        ActionButton(
                            if (resumeMs > 15_000) "▶  Resume ${fmtTime(resumeMs)}" else "▶  Play",
                            Modifier.focusRequester(first), onClick = onPlay,
                        )
                        if (resumeMs > 15_000) ActionButton("Restart", primary = false, onClick = onRestart)
                    }
                    ActionButton(
                        if (isFavorite) "★  Favorited" else "☆  Add to favorites", primary = false,
                        modifier = if (entry.kind == Kind.MOVIE) Modifier else Modifier.focusRequester(first),
                        onClick = onFavorite,
                    )
                }
                if (entry.kind == Kind.SERIES) {
                    if (seasons.size > 1) {
                        LazyRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            itemsIndexed(seasons) { _, s ->
                                Chip(if (s > 0) "Season $s" else "Episodes", activeSeason == s) { season = s }
                            }
                        }
                    }
                    if (episodes.isEmpty()) {
                        Text("Loading episodes…", color = Palette.muted, modifier = Modifier.padding(top = 16.dp))
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        itemsIndexed(episodes.filter { it.season == activeSeason }) { _, ep ->
                            val p = episodeProgress(ep)
                            FocusCard(Modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(10.dp), 1f, 2.dp, onClick = { onEpisode(ep) }) { focused ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(if (focused) Palette.accent.copy(alpha = 0.22f) else Palette.surface)
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Text(ep.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p != null) ProgressLine(p, Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ fullscreen overlay

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun BoxScope.FullscreenOverlay(
    visible: Boolean,
    entry: Entry,
    name: String,
    isLive: Boolean,
    favorite: Boolean,
    epg: List<Programme>,
    now: Long,
    position: Long,
    duration: Long,
    hint: String,
) {
    AnimatedVisibility(visible, Modifier.align(Alignment.BottomStart)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(horizontal = 56.dp, vertical = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.icon.isNotBlank() && isLive) {
                    Artwork(entry.icon, name, Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Fit)
                    Spacer(Modifier.width(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive) {
                            Text(
                                "● LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.live).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            (if (favorite) "★ " else "") + name + if (isLive && entry.num > 0) "   ·  Ch ${entry.num}" else "",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isLive) {
                        val (cur, next) = nowNext(epg, now)
                        if (cur != null) {
                            Text("${cur.title}  ·  until ${clockText(cur.stop)}", modifier = Modifier.padding(top = 4.dp))
                            ProgressLine(((now - cur.start).toFloat() / (cur.stop - cur.start).coerceAtLeast(1)), Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        if (next != null) Text("Next ${clockText(next.start)}: ${next.title}", color = Palette.muted, modifier = Modifier.padding(top = 6.dp))
                    } else if (entry.kind == Kind.EPISODE && entry.parent.isNotEmpty()) {
                        Text(entry.title, color = Palette.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            if (!isLive && duration > 0) {
                ProgressLine(position.toFloat() / duration, Modifier.fillMaxWidth().padding(top = 14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(position), color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                    Text(fmtTime(duration), color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(hint, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

fun clockText(ms: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))

@Composable
fun BrowseHeader(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    searchLabel: String = "Search",
    searchWidth: Dp = 260.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        trailing()
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            label = { Text(searchLabel) }, modifier = Modifier.width(searchWidth),
        )
    }
}
