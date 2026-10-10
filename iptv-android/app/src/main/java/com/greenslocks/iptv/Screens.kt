package com.greenslocks.iptv

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class Shelf(val title: String, val items: List<Entry>)

// ------------------------------------------------------------------ navigation rail

@Composable
fun NavItem(glyph: String, label: String, selected: Boolean, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusCard(modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(12.dp), 1.03f, 2.dp, onClick = onClick) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Palette.accent.copy(alpha = 0.28f) else if (selected) Palette.surfaceHi else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(glyph, fontSize = 20.sp, color = if (selected || focused) Palette.accent else Palette.muted, modifier = if (compact) Modifier else Modifier.width(30.dp))
            if (!compact) {
                Text(
                    label, maxLines = 1, color = if (selected || focused) Palette.text else Palette.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun NavRail(
    items: List<Triple<String, String, Boolean>>,
    footer: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    firstFocus: Modifier = Modifier,
    onItem: (Int) -> Unit,
) {
    Column(
        modifier.fillMaxHeight().background(Palette.surface).padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        if (!compact) {
            BrandWordmark(15.sp, Modifier.padding(start = 6.dp, bottom = 24.dp))
        }
        items.forEachIndexed { i, (glyph, label, selected) ->
            NavItem(glyph, label, selected, compact, if (i == 0) firstFocus else Modifier) { onItem(i) }
        }
        Spacer(Modifier.weight(1f))
        if (!compact) {
            Text(footer, Modifier.padding(start = 14.dp), color = Palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
            Brush.linearGradient(listOf(Color(0xFF0B1226), Palette.bg, Color(0xFF14102B)))
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
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E2C57), Color(0xFF14102B), Palette.bg)))
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
            Brush.linearGradient(listOf(Color(0xFF16204A), Palette.bg, Color(0xFF120E26)))
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
                            FocusCard(Modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(10.dp), 1.02f, 2.dp, onClick = { onEpisode(ep) }) { focused ->
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
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        trailing()
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            label = { Text(searchLabel) }, modifier = Modifier.width(260.dp),
        )
    }
}
