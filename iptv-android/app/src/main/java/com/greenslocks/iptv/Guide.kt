package com.greenslocks.iptv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.DateFormat
import java.util.Date

private const val HALF_HOUR = 30 * 60_000L
private const val WINDOW = 6 * 60 * 60_000L
private const val DP_PER_MIN = 5f
private val CHANNEL_COL = 170.dp

private fun msToDp(ms: Long): Dp = (ms / 60_000f * DP_PER_MIN).dp
private fun hhmm(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

/** Lazily fetched programmes per channel, a few requests at a time. */
class GuideData(
    private val provider: Provider,
    /** An extra guide (XMLTV) to fill gaps, or to take priority when [extraFirst] is set. */
    private val extra: (suspend (Entry, Long, Long) -> List<Programme>)? = null,
    private val extraFirst: Boolean = false,
) {
    val cache: SnapshotStateMap<String, List<Programme>> = mutableStateMapOf()
    private val gate = Semaphore(6)

    suspend fun load(e: Entry) {
        if (cache.containsKey(e.id)) return
        gate.withPermit {
            if (cache.containsKey(e.id)) return@withPermit
            val now = System.currentTimeMillis()
            val primary = runCatching { provider.guideEpg(e, 3 * 60 * 60_000L, 9 * 60 * 60_000L) }.getOrDefault(emptyList())
            val fx = extra
            val more = if (fx == null) emptyList() else runCatching { fx(e, now - 3 * 60 * 60_000L, now + 9 * 60 * 60_000L) }.getOrDefault(emptyList())
            cache[e.id] = when {
                extraFirst && more.isNotEmpty() -> more
                primary.isNotEmpty() -> primary
                else -> more
            }
        }
    }
}

@Composable
fun GuideScreen(
    channels: List<Entry>,
    nameOf: (Entry) -> String,
    guide: GuideData,
    now: Long,
    onPlay: (Entry) -> Unit,
    onCatchUp: (Entry, Programme) -> Unit,
    reminderKeys: Set<String>,
    onRemind: (Entry, Programme) -> Unit,
    onClose: () -> Unit,
) {
    val hs = rememberScrollState()
    var query by remember { mutableStateOf("") }
    // Start two hours back so recent programmes (catch-up) are visible, then scroll to just before now.
    val windowStart = remember { val t = now - 2 * 60 * 60_000L; t - t % HALF_HOUR }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(Unit) {
        delay(350)
        hs.scrollTo(with(density) { msToDp((now - windowStart - HALF_HOUR).coerceAtLeast(0)).toPx().toInt() })
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(300); runCatching { first.requestFocus() } }
    // Searching programme titles needs the guide for (up to) every channel in the list.
    LaunchedEffect(query) {
        if (query.length >= 2) channels.take(400).forEach { ch -> launch { guide.load(ch) } }
    }
    val rows = if (query.length < 2) channels else channels.filter { ch ->
        guide.cache[ch.id].orEmpty().any { it.title.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(Palette.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TV Guide", style = MaterialTheme.typography.headlineSmall, color = Palette.accent)
            Spacer(Modifier.width(16.dp))
            TvTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = "Find a programme", modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ActionButton("Close", primary = false, onClick = onClose)
        }
        Row(Modifier.fillMaxWidth().padding(start = CHANNEL_COL).horizontalScroll(hs)) {
            for (i in 0 until (WINDOW / HALF_HOUR).toInt()) {
                Box(Modifier.width(msToDp(HALF_HOUR))) {
                    Text(hhmm(windowStart + i * HALF_HOUR), style = MaterialTheme.typography.labelMedium, color = Palette.muted)
                }
            }
        }
        if (query.length >= 2 && rows.isEmpty()) {
            Text("No matches yet (loading guide data…)", Modifier.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows) { i, ch ->
                LaunchedEffect(ch.id) { guide.load(ch) }
                GuideRow(
                    name = nameOf(ch), icon = ch.icon,
                    programmes = guide.cache[ch.id],
                    windowStart = windowStart, now = now, query = query, hs = hs,
                    firstFocus = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onCatchUp = { p -> onCatchUp(ch, p) },
                    isReminded = { p -> "${ch.id}:${p.start}" in reminderKeys },
                    onRemind = { p -> onRemind(ch, p) },
                ) { onPlay(ch) }
            }
        }
    }
}

@Composable
private fun GuideRow(
    name: String,
    icon: String,
    programmes: List<Programme>?,
    windowStart: Long,
    now: Long,
    query: String,
    hs: ScrollState,
    firstFocus: Modifier,
    onCatchUp: (Programme) -> Unit,
    isReminded: (Programme) -> Boolean,
    onRemind: (Programme) -> Unit,
    onPlay: () -> Unit,
) {
    val end = windowStart + WINDOW
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(CHANNEL_COL).height(Dimens.guideRow).padding(end = 4.dp)) {
            FocusCard(firstFocus.fillMaxSize(), RoundedCornerShape(8.dp), 1.0f, 2.dp, onClick = onPlay) { focused ->
                Row(
                    Modifier.fillMaxSize()
                        .background(if (focused) Palette.accent.copy(alpha = 0.25f) else Palette.surface)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(icon, name, Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(5.dp)), androidx.compose.ui.layout.ContentScale.Fit)
                    Spacer(Modifier.width(8.dp))
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(Modifier.weight(1f).horizontalScroll(hs)) {
            if (programmes == null) {
                Text("Loading…", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            } else {
                var cursor = windowStart
                for (p in programmes.sortedBy { it.start }) {
                    val s = maxOf(p.start, cursor)
                    val e = minOf(p.stop, end)
                    if (e <= s) continue
                    if (s > cursor) Spacer(Modifier.width(msToDp(s - cursor)))
                    val match = query.length >= 2 && p.title.contains(query, ignoreCase = true)
                    Box(Modifier.width(maxOf(msToDp(e - s), 48.dp)).height(Dimens.guideRow).padding(end = 2.dp)) {
                        val airing = p.start <= now && now < p.stop
                        val past = p.stop <= now
                        FocusCard(Modifier.fillMaxSize(), RoundedCornerShape(6.dp), 1.0f, 2.dp, onClick = {
                            when {
                                past -> onCatchUp(p)
                                airing -> onPlay()
                                else -> onRemind(p)
                            }
                        }) { focused ->
                            val frac = ((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                            val cellBg: androidx.compose.ui.graphics.Brush = when {
                                focused -> androidx.compose.ui.graphics.SolidColor(Palette.accent.copy(alpha = 0.35f))
                                match -> androidx.compose.ui.graphics.SolidColor(Palette.accent2.copy(alpha = 0.45f))
                                airing -> androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    0f to Palette.accent.copy(alpha = 0.30f), frac to Palette.accent.copy(alpha = 0.30f),
                                    frac to Palette.surfaceHi, 1f to Palette.surfaceHi,
                                )
                                else -> androidx.compose.ui.graphics.SolidColor(Palette.surface)
                            }
                            Row(Modifier.fillMaxSize().background(cellBg)) {
                            Box(Modifier.width(4.dp).fillMaxHeight().background(genreColor(p.title)))
                            Column(
                                Modifier.fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    p.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (past) Palette.muted else Palette.text,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${hhmm(p.start)} – ${hhmm(p.stop)}" + (if (past && p.archive) "  ↺" else ""),
                                        maxLines = 1, color = Palette.muted, style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (isReminded(p)) {
                                        Spacer(Modifier.width(6.dp))
                                        BellIcon(12.dp, Palette.accent)
                                    }
                                }
                            }
                            }
                        }
                    }
                    cursor = e
                }
                if (cursor < end) Spacer(Modifier.width(msToDp(end - cursor)))
            }
        }
    }
}

private val sportsRx = Regex("(?i)\\b(nfl|nba|mlb|nhl|ncaa|football|soccer|basketball|baseball|hockey|golf|tennis|ufc|boxing|racing|nascar|sport|sports|league|cup|match|game day|wrestling|f1)\\b")
private val newsRx = Regex("(?i)\\b(news|report|weather|headlines?|today|tonight|morning|politics|business|market)\\b")
private val kidsRx = Regex("(?i)\\b(kids?|cartoon|junior|disney|paw patrol|sesame|bluey|nick|toon|animated)\\b")
private val movieRx = Regex("(?i)\\b(movie|film|cinema|premiere|thriller|western|romance)\\b")

/** A rough genre guess from the title: "sports", "news", "kids", "movies" or "". */
fun genreOf(title: String): String = when {
    sportsRx.containsMatchIn(title) -> "sports"
    newsRx.containsMatchIn(title) -> "news"
    kidsRx.containsMatchIn(title) -> "kids"
    movieRx.containsMatchIn(title) -> "movies"
    else -> ""
}

/** The genre guess as a thin colored bar on guide cells. */
fun genreColor(title: String): androidx.compose.ui.graphics.Color = when (genreOf(title)) {
    "sports" -> androidx.compose.ui.graphics.Color(0xFF4ADE80)
    "news" -> androidx.compose.ui.graphics.Color(0xFF60A5FA)
    "kids" -> androidx.compose.ui.graphics.Color(0xFFFABE3C)
    "movies" -> androidx.compose.ui.graphics.Color(0xFFF472B6)
    else -> androidx.compose.ui.graphics.Color.Transparent
}
