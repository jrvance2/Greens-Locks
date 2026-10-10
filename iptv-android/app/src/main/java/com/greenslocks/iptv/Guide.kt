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
class GuideData(private val provider: Provider) {
    val cache: SnapshotStateMap<String, List<Programme>> = mutableStateMapOf()
    private val gate = Semaphore(6)

    suspend fun load(e: Entry) {
        if (cache.containsKey(e.id)) return
        gate.withPermit {
            if (cache.containsKey(e.id)) return@withPermit
            cache[e.id] = runCatching { provider.epg(e, 10) }.getOrDefault(emptyList())
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
    onClose: () -> Unit,
) {
    val hs = rememberScrollState()
    var query by remember { mutableStateOf("") }
    val windowStart = remember { now - now % HALF_HOUR }
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
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Find a programme") }, modifier = Modifier.weight(1f),
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
                    name = nameOf(ch),
                    programmes = guide.cache[ch.id],
                    windowStart = windowStart, now = now, query = query, hs = hs,
                    firstFocus = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { onPlay(ch) }
            }
        }
    }
}

@Composable
private fun GuideRow(
    name: String,
    programmes: List<Programme>?,
    windowStart: Long,
    now: Long,
    query: String,
    hs: ScrollState,
    firstFocus: Modifier,
    onPlay: () -> Unit,
) {
    val end = windowStart + WINDOW
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(CHANNEL_COL).height(56.dp).padding(end = 4.dp)) {
            FocusCard(firstFocus.fillMaxSize(), RoundedCornerShape(8.dp), 1.0f, 2.dp, onClick = onPlay) { focused ->
                Row(
                    Modifier.fillMaxSize()
                        .background(if (focused) Palette.accent.copy(alpha = 0.25f) else Palette.surface)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
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
                    Box(Modifier.width(maxOf(msToDp(e - s), 48.dp)).height(56.dp).padding(end = 2.dp)) {
                        ListRow(
                            p.title, "${hhmm(p.start)} – ${hhmm(p.stop)}",
                            selected = match || (p.start <= now && now < p.stop),
                            modifier = Modifier.fillMaxSize(),
                        ) { if (p.start <= now) onPlay() }
                    }
                    cursor = e
                }
                if (cursor < end) Spacer(Modifier.width(msToDp(end - cursor)))
            }
        }
    }
}
