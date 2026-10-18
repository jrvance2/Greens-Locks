package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun clock(ms: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

@Composable
fun ExploreScreen(
    state: ExploreState,
    sports: SportsState,
    tmdbKey: String,
    xmltv: XmltvStore,
    xmltvTick: Int,
    reminders: List<Reminder>,
    recJobs: () -> List<RecJob>,
    ruleCount: Int,
    libraryReady: Boolean,
    liveChannels: suspend () -> List<Entry>,
    findLibrary: suspend (String, Boolean) -> List<Entry>,
    onOpenEntry: (Entry) -> Unit,
    onWatch: (Entry) -> Unit,
    onProgram: (Entry, Programme) -> Unit,
    onRemoveReminder: (Reminder) -> Unit,
    onCancelRecording: (String) -> Unit,
    onOpenRules: () -> Unit,
    onOpenSports: (String, Int) -> Unit,
    onSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { first.requestFocus() } }
    Row(Modifier.fillMaxSize()) {
        SectionList(state, first)
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (state.section) {
                0 -> DiscoverView(state, tmdbKey, libraryReady, findLibrary, onOpenEntry, onSearch, onOpenSettings)
                1 -> TonightView(state, xmltv, xmltvTick, liveChannels, onWatch, onProgram, onOpenSettings)
                else -> AgendaView(state, sports, reminders, recJobs, ruleCount, onWatch, onRemoveReminder, onCancelRecording, onOpenRules, onOpenSports)
            }
        }
    }
}

@Composable
private fun SectionList(state: ExploreState, first: FocusRequester) {
    val items: List<Triple<Int, String, ImageVector>> = listOf(
        Triple(0, "Discover", Icons.Rounded.Explore),
        Triple(1, "Tonight", Icons.Rounded.Schedule),
        Triple(2, "My agenda", Icons.Rounded.Event),
    )
    Column(Modifier.width(230.dp).fillMaxHeight().background(Palette.surface).padding(horizontal = 10.dp, vertical = 14.dp)) {
        Text("Explore", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        items.forEach { (i, label, icon) ->
            val selected = state.section == i
            FocusCard(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                RoundedCornerShape(12.dp), 1f, 2.dp, onClick = { state.section = i },
            ) { focused ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(Palette.surface)
                        .background(if (focused) Palette.accent.copy(alpha = 0.28f) else if (selected) Palette.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (selected || focused) Palette.accent else Palette.muted)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected || focused) Palette.text else Palette.muted)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Discover

@Composable
private fun DiscoverView(
    state: ExploreState, tmdbKey: String, libraryReady: Boolean, findLibrary: suspend (String, Boolean) -> List<Entry>,
    onOpenEntry: (Entry) -> Unit, onSearch: (String) -> Unit, onOpenSettings: () -> Unit,
) {
    var info by remember { mutableStateOf<DiscoverItem?>(null) }
    if (tmdbKey.isBlank()) {
        EmptyState(
            "Add your movie database key", "Discover uses TMDB, which is free. Add your key in Settings → Guide & recording → Movie info.",
            "Open settings", onOpenSettings,
        )
        return
    }
    LaunchedEffect(tmdbKey, state.discoverTick) {
        if (state.rows.isNotEmpty()) return@LaunchedEffect
        state.discoverLoading = true; state.discoverError = null
        val region = Locale.getDefault().country.ifBlank { "US" }
        val rows = runCatching { TmdbDiscover.rows(tmdbKey, region) }.getOrDefault(emptyList())
        state.rows = rows
        if (rows.isEmpty()) state.discoverError = "Couldn't load TMDB. Check your key and the connection."
        state.discoverLoading = false
    }
    // Check each title against your library in the background, so rows appear instantly and the badges fill in.
    LaunchedEffect(state.rows, libraryReady) {
        if (!libraryReady) return@LaunchedEffect // titles can't be matched until your library has been indexed
        for (row in state.rows) for (item in row.items) {
            if (state.checked[item.key] == true) continue
            val hit = runCatching { LibraryMatch.best(item, findLibrary(LibraryMatch.searchQuery(item.title), item.tv)) }.getOrNull()
            if (hit != null) state.matches[item.key] = hit
            state.checked[item.key] = true
        }
    }
    val total = state.rows.sumOf { it.items.size }
    val done = state.rows.sumOf { r -> r.items.count { state.checked[it.key] == true } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Discover", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        if (state.discoverLoading) Text("Loading…", color = Palette.muted, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        else if (!libraryReady) Text("Waiting for your library to finish loading\u2026", color = Palette.muted, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        else if (done < total) Text("Checking your library $done of $total", color = Palette.muted, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        Chip("Only titles I have", state.libraryOnly) { state.toggleLibraryOnly() }
    }
    when {
        state.discoverLoading && state.rows.isEmpty() -> Column(Modifier.padding(top = 10.dp)) { repeat(2) { SkeletonShelf() } }
        state.rows.isEmpty() -> EmptyState("Nothing to show", state.discoverError ?: "Try again in a moment.", "Try again") { state.discoverTick++ }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            state.rows.forEach { row ->
                val shown = if (state.libraryOnly) row.items.filter { state.matches[it.key] != null } else row.items
                if (shown.isNotEmpty()) item(key = row.title) {
                    Column {
                        SectionTitle(row.title)
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(shown, key = { it.key }) { item ->
                                val match = state.matches[item.key]
                                val dim = state.checked[item.key] == true && match == null
                                PosterCard(
                                    item.title, item.poster,
                                    listOfNotNull(item.year.takeIf { it.isNotBlank() }, item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                                    modifier = if (dim) Modifier.alpha(0.5f) else Modifier,
                                    badge = if (match != null) "IN LIBRARY" else null,
                                ) { if (match != null) onOpenEntry(match) else info = item }
                            }
                        }
                    }
                }
            }
            if (state.libraryOnly && done >= total && state.rows.none { r -> r.items.any { state.matches[it.key] != null } }) {
                item { EmptyState("None of these are in your library", "Turn off \"Only titles I have\" to see everything.") }
            }
        }
    }
    info?.let { DiscoverInfo(it, onSearch = { q -> info = null; onSearch(q) }, onDismiss = { info = null }) }
}

@Composable
private fun DiscoverInfo(item: DiscoverItem, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Palette.surface, tonalElevation = 0.dp) {
            Row(Modifier.widthIn(min = 560.dp, max = 760.dp).padding(22.dp)) {
                Artwork(item.poster, item.title, Modifier.width(150.dp).size(width = 150.dp, height = 225.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(if (item.tv) "Series" else "Movie", item.year.takeIf { it.isNotBlank() }, item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                        color = Palette.accent, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        item.overview.ifBlank { "No description available." }, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        color = Palette.text.copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp),
                    )
                    Text("Not found in your library.", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Search my library", Modifier.focusRequester(first)) { onSearch(item.title) }
                        ActionButton("Close", primary = false, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Tonight

private val TONIGHT_MODES = listOf("Now", "Later", "Prime time")
private val TONIGHT_GENRES = listOf("All", "Movies", "Sports", "News", "Kids")
private val GENRE_KEYS = listOf("", "movies", "sports", "news", "kids")

@Composable
private fun TonightView(
    state: ExploreState, xmltv: XmltvStore, xmltvTick: Int, liveChannels: suspend () -> List<Entry>,
    onWatch: (Entry) -> Unit, onProgram: (Entry, Programme) -> Unit, onOpenSettings: () -> Unit,
) {
    LaunchedEffect(state.tonightMode, xmltvTick, state.section) {
        state.tonightLoading = true
        val has = xmltv.hasData()
        state.tonightNoGuide = !has
        if (has) {
            val at = Tonight.timeFor(state.tonightMode, System.currentTimeMillis())
            val rows = xmltv.airingAt(at)
            val names = xmltv.channelNames()
            val live = liveChannels()
            state.tonight = withContext(Dispatchers.Default) { Tonight.build(rows, names, Tonight.indexLive(live)) }
        } else state.tonight = emptyList()
        state.tonightLoading = false
    }
    val now = System.currentTimeMillis()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tonight", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        if (state.tonightLoading) Text("Updating…", color = Palette.muted, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        TONIGHT_MODES.forEachIndexed { i, label ->
            Chip(label, state.tonightMode == i) { state.tonightMode = i }
            Spacer(Modifier.width(8.dp))
        }
    }
    LazyRow(Modifier.padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(TONIGHT_GENRES) { i, label -> Chip(label, state.tonightGenre == i) { state.tonightGenre = i } }
    }
    val list = remember(state.tonight, state.tonightGenre) {
        val g = GENRE_KEYS[state.tonightGenre]
        if (g.isEmpty()) state.tonight else state.tonight.filter { it.genre == g }
    }
    when {
        state.tonightNoGuide && !state.tonightLoading -> EmptyState(
            "Tonight needs the extra guide",
            "Add your provider's guide address in Settings → Guide & recording → Extra guide (XMLTV URL).",
            "Open settings", onOpenSettings,
        )
        state.tonightLoading && list.isEmpty() -> Column { repeat(3) { SkeletonRow() } }
        list.isEmpty() -> EmptyState("Nothing found", "No matching programmes on your channels at this time. Try another time or genre.")
        else -> LazyVerticalGrid(
            GridCells.Adaptive(340.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.entry.id + "|" + it.prog.start }) { t ->
                val onAir = t.prog.start <= now && now < t.prog.stop
                FocusCard(
                    Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), 1.03f, 3.dp,
                    onLongClick = { onProgram(t.entry, t.prog) },
                    onClick = { if (onAir) onWatch(t.entry) else onProgram(t.entry, t.prog) },
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth().background(Palette.surface)
                            .background(if (focused) Palette.accent.copy(alpha = 0.18f) else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(4.dp).height(64.dp).background(genreColor(t.prog.title)))
                        Artwork(t.entry.icon, t.entry.title, Modifier.padding(10.dp).size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
                        Column(Modifier.weight(1f).padding(end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(t.prog.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(t.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                            Text(
                                (if (onAir) "On now · until " + clock(t.prog.stop) else clock(t.prog.start) + " – " + clock(t.prog.stop)),
                                color = if (onAir) Palette.accent else Palette.muted, style = MaterialTheme.typography.labelMedium,
                            )
                            if (onAir) ProgressLine((now - t.prog.start).toFloat() / (t.prog.stop - t.prog.start).coerceAtLeast(1), Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ My agenda

@Composable
private fun AgendaView(
    state: ExploreState, sports: SportsState, reminders: List<Reminder>, recJobs: () -> List<RecJob>, ruleCount: Int,
    onWatch: (Entry) -> Unit, onRemoveReminder: (Reminder) -> Unit, onCancelRecording: (String) -> Unit,
    onOpenRules: () -> Unit, onOpenSports: (String, Int) -> Unit,
) {
    var open by remember { mutableStateOf<AgendaItem?>(null) }
    LaunchedEffect(state.section, sports.follows) {
        state.games = followedGames(sports.follows)
        while (true) { delay(60_000); state.agendaTick++ }
    }
    val now = System.currentTimeMillis()
    val items = remember(reminders, state.games, state.agendaTick) { Agenda.build(System.currentTimeMillis(), reminders, recJobs(), state.games) }
    Text("My agenda", style = MaterialTheme.typography.headlineMedium)
    Text("Reminders, scheduled recordings and games for the teams you follow.", color = Palette.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
    if (items.isEmpty() && ruleCount == 0) {
        EmptyState("Nothing scheduled", "Set reminders and recordings from the Guide, or follow teams in Sports. They all show up here.")
        return
    }
    val groups = remember(items) { items.groupBy { Agenda.dayLabel(it.start, System.currentTimeMillis()) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        groups.forEach { (day, list) ->
            item(key = "d$day") { Text(day, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) }
            items(list, key = { it.kind.name + it.start + it.title }) { a ->
                val live = a.start <= now && now < a.end
                FocusCard(Modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(12.dp), 1f, 2.dp, onClick = { open = a }) { focused ->
                    Row(
                        Modifier.fillMaxWidth().background(Palette.surface)
                            .background(if (focused) Palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clock(a.start), Modifier.width(86.dp), color = if (live) Palette.live else Palette.muted, style = MaterialTheme.typography.titleSmall)
                        Box(
                            Modifier.padding(end = 12.dp).size(10.dp).clip(CircleShape).background(
                                when (a.kind) { AgendaKind.RECORDING -> Palette.live; AgendaKind.GAME -> Color(0xFF4ADE80); AgendaKind.REMINDER -> Palette.accent }
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(a.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (live) Text("NOW", color = Palette.live, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (ruleCount > 0) item(key = "rules") {
            FocusCard(Modifier.fillMaxWidth().padding(top = 18.dp), RoundedCornerShape(12.dp), 1f, 2.dp, onClick = onOpenRules) { focused ->
                Row(
                    Modifier.fillMaxWidth().background(Palette.surface).background(if (focused) Palette.accent.copy(alpha = 0.22f) else Color.Transparent).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Series recording rules", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("$ruleCount", color = Palette.muted)
                }
            }
        }
    }
    open?.let { a ->
        ActionMenuDialog(a.title, buildList {
            when (a.kind) {
                AgendaKind.REMINDER -> {
                    val r = a.reminder
                    if (r != null) {
                        add(MenuItem("Watch ${r.channel.title}") { onWatch(r.channel) })
                        add(MenuItem("Remove reminder") { onRemoveReminder(r); state.agendaTick++ })
                    }
                }
                AgendaKind.RECORDING -> add(MenuItem("Cancel recording") { onCancelRecording(a.recId); state.agendaTick++ })
                AgendaKind.GAME -> a.game?.let { g ->
                    add(MenuItem("Open in Sports") {
                        val days = Math.round((a.start - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceIn(0, 5)
                        onOpenSports(g.league, days)
                    })
                }
            }
            add(MenuItem("Close") {})
        }) { open = null }
    }
}
