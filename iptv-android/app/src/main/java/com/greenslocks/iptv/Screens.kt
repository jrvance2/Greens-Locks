package com.greenslocks.iptv

import androidx.tv.material3.Tab as TvTab
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
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

enum class RailIconType { HOME, SEARCH, LIVE, MOVIES, SERIES, SPORTS, EXPLORE, FAVORITES, GUIDE, SETTINGS }

data class RailItem(val icon: RailIconType, val label: String, val selected: Boolean)

@Composable
fun RailIcon(type: RailIconType, color: Color, modifier: Modifier = Modifier.size(26.dp)) {
    androidx.compose.material3.Icon(
        when (type) {
            RailIconType.HOME -> androidx.compose.material.icons.Icons.Rounded.Home
            RailIconType.SEARCH -> androidx.compose.material.icons.Icons.Rounded.Search
            RailIconType.LIVE -> androidx.compose.material.icons.Icons.Rounded.LiveTv
            RailIconType.MOVIES -> androidx.compose.material.icons.Icons.Rounded.Movie
            RailIconType.SERIES -> androidx.compose.material.icons.Icons.Rounded.VideoLibrary
            RailIconType.SPORTS -> androidx.compose.material.icons.Icons.Rounded.SportsScore
            RailIconType.EXPLORE -> androidx.compose.material.icons.Icons.Rounded.Explore
            RailIconType.FAVORITES -> androidx.compose.material.icons.Icons.Rounded.Star
            RailIconType.GUIDE -> androidx.compose.material.icons.Icons.Rounded.CalendarViewDay
            RailIconType.SETTINGS -> androidx.compose.material.icons.Icons.Rounded.Settings
        },
        null, modifier, tint = color,
    )
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
    pair: PairInfo?,
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
      Row(
        Modifier.widthIn(max = if (pair != null) 900.dp else 540.dp).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(20.dp)).background(Palette.surface.copy(alpha = 0.92f)).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandWordmark(30.sp)
            Text("Sign in with your provider login", color = Palette.muted, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
            if (useUrl) {
                TvTextField(
                    value = urlText, onValueChange = onUrl, singleLine = true,
                    label = "Playlist URL", modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TvTextField(
                    value = server, onValueChange = onServer, singleLine = true,
                    label = "Server (http://host:port)", modifier = Modifier.fillMaxWidth(),
                )
                TvTextField(
                    value = user, onValueChange = onUser, singleLine = true,
                    label = "Username", modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TvTextField(
                    value = pass, onValueChange = onPass, singleLine = true,
                    label = "Password",
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
        if (pair != null) {
            Column(
                Modifier.width(250.dp).clip(RoundedCornerShape(20.dp)).background(Palette.surface.copy(alpha = 0.92f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Type it on your phone", fontWeight = FontWeight.SemiBold)
                Text("Scan, or open the address below on a phone on the same Wi-Fi.", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                QrCode(pair.url, 150.dp, Modifier.clip(RoundedCornerShape(8.dp)))
                Text(pair.url.removePrefix("http://"), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
                Text("Code", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text(pair.code, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Palette.accent)
            }
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
    backdrop: String = "",
    dots: Int = 0,
    dot: Int = 0,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tint by remember(entry.icon) { mutableStateOf<Color?>(null) }
    LaunchedEffect(entry.icon) {
        tint = if (Dimens.heroTint && entry.icon.startsWith("http") && entry.kind != Kind.LIVE) dominantColor(ctx, entry.icon) else null
    }
    val wash = tint ?: Palette.accent
    val animatedWash by androidx.compose.animation.animateColorAsState(wash, label = "heroWash")
    Box(
        Modifier.fillMaxWidth().height(300.dp)
            .background(Brush.horizontalGradient(listOf(animatedWash.copy(alpha = 0.40f), animatedWash.copy(alpha = 0.16f), Palette.bg)))
    ) {
        androidx.compose.animation.Crossfade(
            targetState = Triple(backdrop.ifBlank { entry.icon }, entry.kind != Kind.LIVE, backdrop.isBlank()),
            animationSpec = androidx.compose.animation.core.tween(Motion.SLOW, easing = Motion.ease),
            label = "heroArt",
        ) { (url, show, plain) ->
            if (show) Box(Modifier.fillMaxSize()) {
                val kb = rememberInfiniteTransition(label = "kenburns")
                val zoom by kb.animateFloat(
                    1f, if (Dimens.animLevel == 0) 1.07f else 1f,
                    infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom",
                )
                Artwork(url, "", Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }.alpha(if (plain) 0.18f else 0.5f), alignment = Alignment.TopCenter)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Palette.bg.copy(alpha = 0.92f), Palette.bg.copy(alpha = 0.55f), Color.Transparent))
                    )
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Palette.bg)))
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = Palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (subtitle.isNotEmpty()) Text(subtitle, color = Palette.muted, modifier = Modifier.padding(top = 4.dp))
                if (dots > 1) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(dots) { i ->
                        val w by androidx.compose.animation.core.animateDpAsState(if (i == dot) 22.dp else 6.dp, tween(Motion.MED), label = "dot")
                        Box(Modifier.size(width = w, height = 6.dp).clip(RoundedCornerShape(50)).background(if (i == dot) Palette.accent else Color.White.copy(alpha = 0.3f)))
                    }
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Play", playFocus, icon = Icons.Rounded.PlayArrow, onClick = onPlay)
                    if (canOpenDetails) ActionButton("Details", primary = false, icon = Icons.Rounded.Info, onClick = onDetails)
                }
            }
            Artwork(
                entry.icon, title,
                Modifier.fillMaxHeight().aspectRatio(if (entry.kind == Kind.LIVE) 1.6f else 2f / 3f)
                    .shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
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
    inMyList: Boolean,
    onMyList: () -> Unit,
    onTrailer: () -> Unit,
    onEpisode: (Entry) -> Unit,
    versions: List<Entry> = emptyList(),
    onVersion: (Entry) -> Unit = {},
    rows: List<Pair<String, List<Entry>>> = emptyList(),
    onOpenEntry: (Entry) -> Unit = {},
    onActor: (String, Int) -> Unit = { _, _ -> },
    watched: Boolean = false,
    onToggleWatched: () -> Unit = {},
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
        val backdrop = detail?.backdrop.orEmpty()
        if (backdrop.isNotBlank()) {
            Artwork(backdrop, "", Modifier.fillMaxSize().alpha(0.5f))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Palette.bg.copy(alpha = 0.97f), Palette.bg.copy(alpha = 0.6f), Color.Transparent))
                )
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Palette.bg.copy(alpha = 0.9f))))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Artwork(
                poster, name,
                Modifier.width(210.dp).aspectRatio(2f / 3f).shadow(24.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).then(if (entry.kind == Kind.SERIES) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
                Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail == null) {
                    Text("Loading details…", color = Palette.muted, modifier = Modifier.padding(top = 6.dp))
                } else {
                    if (detail.tagline.isNotBlank()) Text(detail.tagline, color = Palette.muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
                    MetaChips(
                        detail.meta, entry.title, Modifier.padding(top = 8.dp),
                        certification = detail.certification, tags = remember(entry.title) { TechInfo.formatTags(entry.title) },
                        added = entry.added, watched = watched,
                    )
                    if (detail.ratings.isNotBlank()) Text(detail.ratings, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    if (detail.tech.isNotBlank()) Text(detail.tech, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (versions.size > 1) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Versions", color = Palette.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(versions) { _, v -> Chip(TechInfo.qualityLabel(v.title), v.id == entry.id) { if (v.id != entry.id) onVersion(v) } }
                        }
                    }
                }
                if (detail != null) {
                    if (detail.plot.isNotBlank()) {
                        Text(detail.plot, maxLines = 5, overflow = TextOverflow.Ellipsis, color = Palette.text.copy(alpha = 0.85f), modifier = Modifier.padding(top = 10.dp))
                    }
                    CastRow(detail.cast, detail.castIds, onActor, Modifier.padding(top = 12.dp))
                    if (detail.director.isNotBlank()) {
                        Text(
                            (if (entry.kind == Kind.SERIES) "Created by " else "Directed by ") + detail.director,
                            color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                LazyRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp)) {
                    if (entry.kind == Kind.MOVIE) {
                        item {
                            ActionButton(
                                if (resumeMs > 15_000) "Resume ${fmtTime(resumeMs)}" else "Play",
                                Modifier.focusRequester(first), icon = Icons.Rounded.PlayArrow, onClick = onPlay,
                            )
                        }
                        if (resumeMs > 15_000) item { ActionButton("Restart", primary = false, icon = Icons.Rounded.Replay, onClick = onRestart) }
                    }
                    item {
                        ActionButton(
                            if (isFavorite) "Favorited" else "Add to favorites", primary = false,
                            icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            modifier = if (entry.kind == Kind.MOVIE) Modifier else Modifier.focusRequester(first),
                            onClick = onFavorite,
                        )
                    }
                    item { ActionButton(if (inMyList) "In My list" else "My list", primary = false, icon = if (inMyList) Icons.Rounded.Check else Icons.Rounded.Add, onClick = onMyList) }
                    if (!detail?.trailer.isNullOrBlank()) item { ActionButton("Trailer", primary = false, icon = Icons.Rounded.Movie, onClick = onTrailer) }
                    if (entry.kind == Kind.MOVIE) item {
                        ActionButton(if (watched) "Mark unwatched" else "Mark watched", primary = false, icon = Icons.Rounded.Check, onClick = onToggleWatched)
                    }
                }
                if (entry.kind == Kind.MOVIE) rows.forEach { (title, list) -> DetailRow(title, list, onOpenEntry) }
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
                            FocusCard(Modifier.fillMaxWidth().padding(vertical = 3.dp), RoundedCornerShape(12.dp), 1f, 2.dp, onClick = { onEpisode(ep) }) { focused ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (focused) Palette.accent.copy(alpha = 0.22f) else Palette.surface)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(width = 124.dp, height = 70.dp).clip(RoundedCornerShape(8.dp))) {
                                        Artwork(ep.icon, ep.title, Modifier.fillMaxSize())
                                        Box(Modifier.align(Alignment.Center).size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = Color.White)
                                        }
                                        if (p != null) ProgressLine(p, Modifier.align(Alignment.BottomStart).fillMaxWidth())
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
    catchUp: Boolean = false,
    badge: String = "",
    behindLiveSec: Int = 0,
    sleepLeftMin: Int = 0,
) {
    if (Dimens.showClock) AnimatedVisibility(visible, Modifier.align(Alignment.TopEnd)) {
        Text(
            clockText(now), color = Palette.text, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(28.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
    if (Dimens.overlayStyle == 2) return
    if (Dimens.overlayStyle == 1) {
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomStart)) {
            val (cur, _) = nowNext(epg, now)
            Column(
                Modifier.padding(horizontal = 40.dp, vertical = 28.dp).widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Text(
                            "\u25CF", color = Palette.live, modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        (if (favorite) "\u2605 " else "") + name + if (isLive && entry.num > 0) "  \u00B7  ${entry.num}" else "",
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isLive && cur != null) {
                    Text(cur.title, color = Palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    ProgressLine((now - cur.start).toFloat() / (cur.stop - cur.start).coerceAtLeast(1), Modifier.fillMaxWidth().padding(top = 6.dp))
                } else if (!isLive && duration > 0) {
                    ProgressLine(position.toFloat() / duration, Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(
                        "${fmtTime(position)} / ${fmtTime(duration)}", color = Palette.muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        return
    }
    AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomStart),
        enter = androidx.compose.animation.fadeIn(tween(Motion.MED)) + androidx.compose.animation.slideInVertically(tween(Motion.SLOW, easing = Motion.ease)) { it / 4 },
        exit = androidx.compose.animation.fadeOut(tween(Motion.MED)) + androidx.compose.animation.slideOutVertically(tween(Motion.MED)) { it / 4 },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.58f), Color.Black.copy(alpha = 0.86f))))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp)
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
                        } else if (catchUp) {
                            Text(
                                "↺ CATCH-UP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.accent).padding(horizontal = 8.dp, vertical = 2.dp),
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
                ProgressLine(position.toFloat() / duration, Modifier.fillMaxWidth().padding(top = 14.dp), knob = true)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(position), color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                    Text(fmtTime(duration), color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (behindLiveSec > 3) {
                Text(
                    "⏸  ${behindLiveSec / 60}:%02d behind live  ·  fast-forward to jump back to live".format(behindLiveSec % 60),
                    color = Palette.accent, modifier = Modifier.padding(top = 8.dp),
                )
            }
            val extras = listOfNotNull(badge.ifEmpty { null }, if (sleepLeftMin > 0) "Sleep in $sleepLeftMin min" else null)
            if (extras.isNotEmpty()) {
                Text(extras.joinToString("   ·   "), color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            Text(hint, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
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
        TvTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            label = searchLabel, modifier = Modifier.width(searchWidth),
        )
    }
}

/** "Next episode in 8s" card shown when an episode ends. */
@Composable
fun BoxScope.NextUpCard(title: String, secs: Int) {
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 200.dp).width(320.dp)
            .clip(RoundedCornerShape(16.dp)).background(Palette.surface.copy(alpha = 0.95f)).padding(18.dp),
    ) {
        Text("UP NEXT", color = Palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text("Playing in ${secs}s", color = Palette.muted, modifier = Modifier.padding(top = 6.dp))
        Text("OK: play now   ·   Back: cancel", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Shown while the saved login connects, so the app never opens on a blank or half-built screen. */
@Composable
fun SplashScreen() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash")
    val sweep by t.animateFloat(
        -0.4f, 1.0f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing)),
        label = "sweep",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0.86f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 180f), label = "pop",
    )
    val fade by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "fade")
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accentWash(0.22f), Palette.bg))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandWordmark(38.sp, Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = fade })
            Box(Modifier.padding(top = 30.dp).size(width = 220.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.4f).graphicsLayer { translationX = sweep * 220.dp.toPx() }
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Palette.accent, Palette.accent2, Color.Transparent))))
            }
            Text("Loading your library\u2026", color = Palette.muted, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/** Navigation across the top of the screen instead of down the side (Compose for TV tab row). */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TopNav(items: List<RailItem>, modifier: Modifier = Modifier, onItem: (RailIconType) -> Unit) {
    Row(
        modifier.fillMaxWidth().height(60.dp).background(Palette.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandWordmark(15.sp)
        Spacer(Modifier.width(22.dp))
        androidx.tv.material3.TabRow(
            selectedTabIndex = items.indexOfFirst { it.selected }.coerceAtLeast(0),
            indicator = { _, _ -> },
            separator = { Spacer(Modifier.width(4.dp)) },
        ) {
            items.forEachIndexed { i, item ->
                TvTab(
                    selected = item.selected, onFocus = {}, onClick = { onItem(item.icon) },
                    colors = androidx.tv.material3.TabDefaults.pillIndicatorTabColors(
                        contentColor = Palette.muted, selectedContentColor = Palette.text,
                        focusedContentColor = Palette.text, focusedSelectedContentColor = Palette.text,
                    ),
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (item.selected) Palette.accent.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RailIcon(item.icon, if (item.selected) Palette.accent else Palette.muted)
                        if (Dimens.railLabels) {
                            Spacer(Modifier.width(8.dp))
                            Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
