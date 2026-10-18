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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dayLabel(offset: Int): String = when (offset) {
    -1 -> "Yesterday"
    0 -> "Today"
    1 -> "Tomorrow"
    else -> SimpleDateFormat("EEE d", Locale.getDefault()).format(Date(System.currentTimeMillis() + offset * 86_400_000L))
}

private fun startText(ms: Long): String {
    if (ms <= 0) return "TBD"
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    val day = SimpleDateFormat("yyyyMMdd", Locale.US)
    val today = day.format(Date())
    val that = day.format(Date(ms))
    return if (today == that) time else SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms)) + " " + time
}

private fun statusOf(g: Game): String = when {
    g.isLive -> g.status.ifBlank { "LIVE" }
    g.isFinal -> g.status.ifBlank { "Final" }
    else -> startText(g.start)
}

@Composable
fun SportsScreen(
    state: SportsState,
    findChannels: suspend (List<String>) -> List<Entry>,
    onWatch: (Entry) -> Unit,
) {
    var open by remember { mutableStateOf<Game?>(null) }
    var finder by remember { mutableStateOf<Game?>(null) }
    val firstLeague = remember { FocusRequester() }

    // Load now, then keep scores fresh: quickly while anything is live, slowly otherwise.
    LaunchedEffect(state.leagueKey, state.dayOffset, state.mode, state.tick, state.enabled) {
        state.refresh()
        while (true) {
            delay(if (state.leagueKey == "live" || state.games.any { it.isLive }) 25_000L else 120_000L)
            state.refresh(silent = true)
        }
    }
    LaunchedEffect(Unit) { delay(200); runCatching { firstLeague.requestFocus() } }

    Row(Modifier.fillMaxSize()) {
        LeagueColumn(state, firstLeague)
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            SportsHeader(state)
            when {
                state.mode == 1 && state.league()?.kind != SportKind.COMBAT && state.leagueKey != "live" && state.leagueKey != "my" -> StandingsView(state)
                else -> GamesGrid(state) { open = it }
            }
        }
    }
    open?.let { g -> GameDialog(g, state, onFind = { finder = g }, onDismiss = { open = null }) }
    finder?.let { g -> ChannelFinderDialog(g, findChannels, onPick = { e -> finder = null; open = null; onWatch(e) }, onDismiss = { finder = null }) }
}

@Composable
private fun LeagueColumn(state: SportsState, first: FocusRequester) {
    val items = listOf("live" to "Live now", "my" to "My teams") + state.leagues().map { it.key to it.name }
    LazyColumn(
        Modifier.width(230.dp).fillMaxHeight().background(Palette.surface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
    ) {
        item { Text("Sports", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)) }
        itemsIndexed(items) { i, (key, label) ->
            val selected = state.leagueKey == key
            FocusCard(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                RoundedCornerShape(12.dp), 1f, 2.dp, onClick = { state.select(key) },
            ) { focused ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (focused) Palette.accent.copy(alpha = 0.28f) else if (selected) Palette.accent.copy(alpha = 0.16f) else Palette.surface)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (key == "live") Box(Modifier.padding(end = 8.dp).size(9.dp).clip(CircleShape).background(Palette.live))
                    Text(
                        label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (selected || focused) Palette.text else Palette.muted, style = MaterialTheme.typography.titleSmall,
                    )
                    if (key == "my" && state.follows.isNotEmpty()) Text("${state.follows.size}", color = Palette.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SportsHeader(state: SportsState) {
    val l = state.league()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (state.leagueKey) { "live" -> "Live now"; "my" -> "My teams"; else -> l?.name.orEmpty() },
            Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (state.loading) Text("Updating…", color = Palette.muted, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
        if (l != null && l.kind != SportKind.COMBAT) {
            Chip("Scores", state.mode == 0) { state.mode = 0 }
            Spacer(Modifier.width(8.dp))
            Chip("Standings", state.mode == 1) { state.mode = 1 }
        }
    }
    if (state.leagueKey != "live" && state.mode == 0) {
        LazyRow(Modifier.padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed((-1..5).toList()) { _, off -> Chip(dayLabel(off), state.dayOffset == off) { state.dayOffset = off } }
        }
    } else Spacer(Modifier.size(10.dp))
}

@Composable
private fun GamesGrid(state: SportsState, onOpen: (Game) -> Unit) {
    val games = remember(state.games) {
        state.games.sortedWith(compareBy<Game> { if (it.isLive) 0 else if (it.isUpcoming) 1 else 2 }.thenBy { it.start })
    }
    when {
        state.loading && games.isEmpty() -> Column { repeat(3) { SkeletonRow() } }
        state.error != null && games.isEmpty() ->
            EmptyState("Couldn't load scores", "Check the connection and try again.", "Try again") { state.tick++ }
        games.isEmpty() -> EmptyState(
            when (state.leagueKey) {
                "live" -> "Nothing live right now"
                "my" -> if (state.follows.isEmpty()) "No teams followed yet" else "No games for your teams"
                else -> "No games on this day"
            },
            when (state.leagueKey) {
                "live" -> "Games that are in progress will show up here."
                "my" -> "Open a game or a standings row and choose Follow to see your teams here."
                else -> "Try another day above."
            },
        )
        else -> Column {
            if (state.error != null) Text("Offline – showing the last scores", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
            LazyVerticalGrid(
                GridCells.Adaptive(430.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(games, key = { it.league + it.id }) { g -> GameCard(g, state, showLeague = state.leagueKey == "live" || state.leagueKey == "my") { onOpen(g) } }
            }
        }
    }
}

@Composable
private fun GameCard(g: Game, state: SportsState, showLeague: Boolean, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), 1.03f, 3.dp, onClick = onClick) { focused ->
        Column(
            Modifier.fillMaxWidth()
                .background(Palette.surface)
                .background(if (focused) Palette.accent.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (g.isLive) Box(Modifier.padding(end = 8.dp).size(9.dp).clip(CircleShape).background(Palette.live))
                Text(
                    statusOf(g), color = if (g.isLive) Palette.live else Palette.muted,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                val tag = listOfNotNull(
                    if (showLeague) SPORT_LEAGUES.firstOrNull { it.key == g.league }?.short else null,
                    g.networks.firstOrNull(),
                ).joinToString("  ·  ")
                if (tag.isNotEmpty()) Text(tag, color = Palette.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            TeamRow(g.away, g, state)
            TeamRow(g.home, g, state)
        }
    }
}

@Composable
private fun TeamRow(t: TeamLine, g: Game, state: SportsState) {
    val dim = g.isFinal && !t.winner && g.away.score != g.home.score
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(t.logo, t.name, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (state.isFollowed(g.league, t.id)) "★ " else "") + t.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold,
                color = if (dim) Palette.muted else Palette.text,
            )
            if (t.record.isNotBlank()) Text(t.record, color = Palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (!g.isUpcoming && t.score.isNotBlank()) {
            Text(
                t.score, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = if (dim) Palette.muted else Palette.text,
            )
        }
    }
}

@Composable
private fun GameDialog(g: Game, state: SportsState, onFind: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    // Use most of the screen height, whatever the TV's resolution and density.
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Palette.surface, tonalElevation = 0.dp) {
            Column(Modifier.widthIn(min = 560.dp, max = 760.dp).heightIn(max = maxHeight).padding(horizontal = 24.dp, vertical = 18.dp)) {
                val league = SPORT_LEAGUES.firstOrNull { it.key == g.league }
                val info = listOfNotNull(
                    league?.name, g.note.takeIf { it.isNotBlank() },
                    g.networks.takeIf { it.isNotEmpty() }?.let { "TV: " + it.joinToString(", ") },
                    g.venue.takeIf { it.isNotBlank() },
                ).joinToString("  \u00B7  ")
                Text(info, color = Palette.muted, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigTeam(g.away, g, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 120.dp)) {
                        if (g.isLive) Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.live))
                        Text(
                            statusOf(g), color = if (g.isLive) Palette.live else Palette.text, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                        )
                        if (g.isUpcoming && g.start > 0) Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(g.start)), color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    BigTeam(g.home, g, Modifier.weight(1f))
                }
                // Line scores and top performers scroll if they don't fit; the buttons below always stay visible.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    LineScores(g)
                    if (g.leaders.isNotEmpty()) {
                        Text("Top performers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        g.leaders.forEach { l ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(l.label.uppercase(), Modifier.width(70.dp), color = Palette.muted, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    listOf(l.name, l.team.takeIf { it.isNotBlank() }?.let { "($it)" }).filterNotNull().joinToString(" "),
                                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(l.line, color = Palette.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Find on my channels", Modifier.fillMaxWidth().focusRequester(first), icon = Icons.Rounded.LiveTv, onClick = onFind)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(g.away, g.home).forEach { t ->
                            val f = state.isFollowed(g.league, t.id)
                            ActionButton(
                                (if (f) "Unfollow " else "Follow ") + t.abbr, Modifier.weight(1f), primary = false,
                                icon = if (f) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            ) { state.toggleFollow(g.league, t) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigTeam(t: TeamLine, g: Game, modifier: Modifier) {
    val dim = g.isFinal && !t.winner && g.away.score != g.home.score
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(t.logo, t.name, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), ContentScale.Fit)
        Text(
            t.name, Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium, color = if (dim) Palette.muted else Palette.text,
        )
        if (t.record.isNotBlank()) Text(t.record, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
        if (!g.isUpcoming && t.score.isNotBlank()) {
            Text(t.score, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = if (dim) Palette.muted else Palette.text)
        }
    }
}

@Composable
private fun LineScores(g: Game) {
    val n = maxOf(g.home.lines.size, g.away.lines.size)
    if (n == 0 || g.isUpcoming) return
    fun cell(s: String, bold: Boolean = false) = @Composable {
        Text(
            s, Modifier.width(34.dp), textAlign = TextAlign.Center, maxLines = 1,
            color = if (bold) Palette.text else Palette.muted, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.surfaceHi.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            repeat(n) { cell("${it + 1}")() }
            cell("T", true)()
        }
        listOf(g.away, g.home).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.abbr, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                repeat(n) { cell(t.lines.getOrElse(it) { "-" })() }
                cell(t.score, true)()
            }
        }
    }
}

@Composable
private fun ChannelFinderDialog(g: Game, find: suspend (List<String>) -> List<Entry>, onPick: (Entry) -> Unit, onDismiss: () -> Unit) {
    var hits by remember { mutableStateOf<List<ChannelMatch.Hit>?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(g.id) { hits = runCatching { ChannelMatch.rank(g, find(ChannelMatch.searchTerms(g))) }.getOrDefault(emptyList()) }
    LaunchedEffect(hits) { if (!hits.isNullOrEmpty()) { delay(100); runCatching { first.requestFocus() } } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Palette.surface, tonalElevation = 0.dp) {
            Column(Modifier.widthIn(min = 520.dp, max = 720.dp).padding(20.dp)) {
                Text("Where to watch", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${g.away.name} at ${g.home.name}" + if (g.networks.isNotEmpty()) "  ·  on ${g.networks.joinToString(", ")}" else "",
                    color = Palette.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                val list = hits
                when {
                    list == null -> Text("Searching your channels…", color = Palette.muted)
                    list.isEmpty() -> Column {
                        Text("No channel matches this game.", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Your provider may not carry it, or names the channel differently. Try Search for a team name, or the Guide.",
                            color = Palette.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                        )
                        ActionButton("Close", primary = false, modifier = Modifier.focusRequester(first), onClick = onDismiss)
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(list) { i, h ->
                            ListRow(
                                h.entry.title, h.why, false,
                                (if (i == 0) Modifier.focusRequester(first) else Modifier).padding(vertical = 2.dp),
                                h.entry.icon,
                            ) { onPick(h.entry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingsView(state: SportsState) {
    val league = state.league() ?: return
    val groups = state.standings
    if (groups.isEmpty()) {
        if (state.loading) Column { repeat(3) { SkeletonRow() } }
        else EmptyState("No standings available", "The league may be between seasons.", "Try again") { state.tick++ }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        groups.forEachIndexed { gi, grp ->
            item(key = "h$gi") {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(grp.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    grp.headers.forEach { Text(it, Modifier.width(58.dp), textAlign = TextAlign.Center, color = Palette.muted, style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.width(36.dp))
                }
            }
            itemsIndexed(grp.rows, key = { i, _ -> "r${gi}_$i" }) { i, r ->
                val followed = state.isFollowed(league.key, r.teamId)
                FocusCard(Modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(10.dp), 1f, 2.dp, onClick = { state.toggleFollow(league.key, r.teamId, r.name) }) { focused ->
                    Row(
                        Modifier.fillMaxWidth().background(if (focused) Palette.accent.copy(alpha = 0.22f) else Palette.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", Modifier.width(30.dp), color = Palette.muted, style = MaterialTheme.typography.bodyMedium)
                        Artwork(r.logo, r.name, Modifier.size(28.dp).clip(RoundedCornerShape(5.dp)), ContentScale.Fit)
                        Spacer(Modifier.width(12.dp))
                        Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        r.cells.forEach { Text(it, Modifier.width(58.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
                        Text(if (followed) "★" else "", Modifier.width(36.dp), textAlign = TextAlign.Center, color = Palette.accent)
                    }
                }
            }
        }
    }
}
