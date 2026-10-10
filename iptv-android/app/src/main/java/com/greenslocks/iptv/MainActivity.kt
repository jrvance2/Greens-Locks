package com.greenslocks.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets you set the playlist from a PC without typing on the remote:
        // adb shell am start -n com.greenslocks.iptv/.MainActivity --es playlist_url "<url>"
        intent.getStringExtra("playlist_url")?.let {
            getSharedPreferences("iptv", MODE_PRIVATE).edit().putString("url", it).apply()
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

private enum class Tab(val label: String) {
    LIVE("Live"), MOVIES("Movies"), SERIES("Series"), FAVORITES("★");

    fun kind(): Kind? = when (this) {
        LIVE -> Kind.LIVE
        MOVIES -> Kind.MOVIE
        SERIES -> Kind.SERIES
        FAVORITES -> null
    }
}

private fun redact(msg: String?): String =
    (msg ?: "unknown error").replace(Regex("(?i)(username|password|user|pass)=[^&\\s]*"), "$1=***")

private fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun posKey(e: Entry) = "pos_${e.kind}_${e.id}"

@Composable
private fun PlayerSurface(player: ExoPlayer, showController: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.useController = showController },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val bg = when {
        focused -> colors.primary
        selected -> colors.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (focused) colors.onPrimary else colors.onSurface
    Column(
        modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = source, indication = null,
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle, color = fg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NowNext(epg: List<Programme>, now: Long, modifier: Modifier = Modifier) {
    val (cur, next) = nowNext(epg, now)
    Column(modifier) {
        if (cur != null) Text("Now: ${cur.title} (until ${clock(cur.stop)})", style = MaterialTheme.typography.bodyMedium)
        if (next != null) Text("Next ${clock(next.start)}: ${next.title}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val isTv = remember {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }

    var urlText by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
    var provider by remember { mutableStateOf<Provider?>(null) }
    var showSetup by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Paste your playlist URL and press Connect") }
    var busy by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(Tab.LIVE) }
    val catCache = remember { mutableStateMapOf<Tab, List<Category>>() }
    var category by remember { mutableStateOf<Category?>(null) }
    var entries by remember { mutableStateOf(emptyList<Entry>()) }
    var series by remember { mutableStateOf<Entry?>(null) }
    var episodes by remember { mutableStateOf(emptyList<Entry>()) }
    var query by remember { mutableStateOf("") }
    var listVersion by remember { mutableIntStateOf(0) }

    var current by remember { mutableStateOf<Entry?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf(emptyList<Programme>()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("fav", emptySet())!!.toSet()) }
    var showInfo by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    fun isFavorite(e: Entry) = e.encode() in favorites
    fun toggleFavorite(e: Entry) {
        val key = e.encode()
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("fav", favorites).apply()
    }
    val favoriteEntries = remember(favorites) {
        favorites.mapNotNull(::decodeEntry).sortedBy { it.title.lowercase() }
    }

    fun launchLoad(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching { block() }.onFailure { status = "Failed: ${redact(it.message)}" }
            busy = false
        }
    }

    suspend fun loadCategories(p: Provider, t: Tab) {
        val k = t.kind() ?: return
        if (k != Kind.LIVE && !p.supportsVod) {
            status = "Movies and series need an Xtream-style login URL (get.php?username=...)"
            return
        }
        if (catCache[t] == null) catCache[t] = p.categories(k)
        listVersion++
    }

    fun connect() {
        prefs.edit().putString("url", urlText).apply()
        launchLoad {
            status = "Connecting…"
            val p = connectProvider(urlText)
            provider = p
            catCache.clear()
            category = null; series = null; entries = emptyList(); episodes = emptyList()
            tab = Tab.LIVE
            status = if (p.supportsVod) "Connected" else "Playlist loaded (live only)"
            showSetup = false
            loadCategories(p, Tab.LIVE)
            if (current == null) {
                current = prefs.getString("last", null)?.let(::decodeEntry)?.takeIf { it.kind == Kind.LIVE }
            }
        }
    }
    LaunchedEffect(Unit) { if (urlText.isNotBlank()) connect() }

    fun openTab(t: Tab) {
        tab = t; category = null; series = null; query = ""
        val p = provider ?: return
        if (t == Tab.FAVORITES) listVersion++ else launchLoad { loadCategories(p, t) }
    }
    fun openCategory(c: Category) {
        val p = provider ?: return
        val kind = tab.kind() ?: return
        category = c; query = ""; entries = emptyList()
        launchLoad { entries = p.entries(kind, c); listVersion++ }
    }
    fun openSeries(e: Entry) {
        val p = provider ?: return
        series = e; query = ""; episodes = emptyList()
        launchLoad { episodes = p.episodes(e); listVersion++ }
    }
    fun goBack() {
        if (series != null) series = null else category = null
        query = ""
        listVersion++
    }
    fun onEntry(e: Entry) {
        when {
            e.kind == Kind.SERIES -> openSeries(e)
            current == e -> fullscreen = true // second press on the playing item
            else -> current = e
        }
    }

    val showingCategories = tab != Tab.FAVORITES && category == null && series == null
    val cats = catCache[tab].orEmpty()
    val listEntries = when {
        series != null -> episodes
        tab == Tab.FAVORITES -> favoriteEntries
        else -> entries
    }
    val shownCats = remember(cats, query) { cats.filter { it.name.contains(query, ignoreCase = true) } }
    val shownEntries = remember(listEntries, query) {
        listEntries.filter { it.title.contains(query, ignoreCase = true) }
    }

    fun step(delta: Int) {
        val list = shownEntries.filter { it.kind == Kind.LIVE }
        if (list.isEmpty()) return
        val i = list.indexOf(current)
        current = if (i < 0) list[0] else list[(i + delta).mod(list.size)]
    }

    // Playback
    LaunchedEffect(current) {
        val e = current ?: return@LaunchedEffect
        val p = provider ?: return@LaunchedEffect
        if (e.kind == Kind.LIVE) prefs.edit().putString("last", e.encode()).apply()
        player.setMediaItem(MediaItem.fromUri(p.streamUrl(e)))
        player.prepare()
        if (e.kind != Kind.LIVE) {
            val saved = prefs.getLong(posKey(e), 0L)
            if (saved > 15_000) player.seekTo(saved)
        }
        player.playWhenReady = true
    }
    // Remember where you stopped in movies/episodes
    LaunchedEffect(current) {
        val e = current ?: return@LaunchedEffect
        if (e.kind == Kind.LIVE) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val d = player.duration
            val pos = player.currentPosition
            if (d > 0 && pos > d - 30_000) prefs.edit().remove(posKey(e)).apply()
            else if (player.isPlaying) prefs.edit().putLong(posKey(e), pos).apply()
        }
    }
    // Now/next for live channels
    LaunchedEffect(current, now / 300_000) {
        val e = current
        val p = provider
        epg = if (e != null && p != null && e.kind == Kind.LIVE) {
            runCatching { p.epg(e) }.getOrDefault(emptyList())
        } else emptyList()
    }
    // Fullscreen info overlay
    LaunchedEffect(current, fullscreen, infoTick) {
        showInfo = true
        delay(5000)
        showInfo = false
    }
    LaunchedEffect(showInfo) {
        while (showInfo) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    val firstItem = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(listVersion) {
        if (isTv) {
            delay(250)
            runCatching { firstItem.requestFocus() }
        }
    }
    LaunchedEffect(fullscreen) { if (fullscreen) runCatching { rootFocus.requestFocus() } }

    BackHandler(enabled = series != null || category != null) { goBack() }
    BackHandler(enabled = fullscreen) { fullscreen = false; listVersion++ }

    val browser: @Composable ColumnScope.() -> Unit = {
        if (showSetup || provider == null) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlText, onValueChange = { urlText = it },
                    label = { Text("Playlist URL") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::connect) { Text("Connect") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (busy) "Loading…" else status,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            if (!showSetup && provider != null) {
                TextButton(onClick = { showSetup = true }) { Text("Playlist") }
            }
        }
        if (provider != null) {
            Row(Modifier.padding(vertical = 4.dp)) {
                Tab.values().forEach { t ->
                    FilterChip(tab == t, { openTab(t) }, { Text(t.label) })
                    Spacer(Modifier.width(6.dp))
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            if (category != null || series != null) {
                TextButton(onClick = ::goBack) { Text("◀  ${series?.title ?: category?.name.orEmpty()}") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                if (showingCategories) {
                    itemsIndexed(shownCats) { i, c ->
                        ListRow(
                            c.name, "", selected = false,
                            modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                        ) { openCategory(c) }
                    }
                } else {
                    itemsIndexed(shownEntries) { i, e ->
                        ListRow(
                            (if (isFavorite(e)) "★ " else "") + e.title,
                            if (tab == Tab.FAVORITES) e.kind.name.lowercase().replaceFirstChar { it.uppercase() } else "",
                            selected = e == current,
                            modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                            onLongClick = { toggleFavorite(e) },
                        ) { onEntry(e) }
                    }
                }
            }
        }
    }

    val isLive = current?.kind == Kind.LIVE
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (!fullscreen || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                infoTick++
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> if (isLive) { step(-1); true } else false
                    Key.DirectionDown, Key.ChannelDown -> if (isLive) { step(1); true } else false
                    Key.DirectionLeft -> if (!isLive) {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                    } else false
                    Key.DirectionRight -> if (!isLive) { player.seekTo(player.currentPosition + 10_000); true } else false
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.Menu -> { current?.let(::toggleFavorite); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        if (fullscreen && current != null) {
            PlayerSurface(player, showController = false, modifier = Modifier.fillMaxSize().background(Color.Black))
            if (showInfo) {
                val e = current!!
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f)).padding(24.dp)
                ) {
                    Text((if (isFavorite(e)) "★ " else "") + e.title, style = MaterialTheme.typography.headlineSmall)
                    if (isLive) NowNext(epg, now) else {
                        Text("${fmtTime(position)} / ${fmtTime(duration)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Left/Right: seek 10s, OK: pause", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Menu: favorite", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (isTv) {
            Row(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) { browser() }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    val e = current
                    if (e != null) {
                        PlayerSurface(player, showController = false, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Text(e.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
                        if (isLive) NowNext(epg, now)
                        Text(
                            "OK: play, OK again: fullscreen, hold OK: favorite. Back: up a level.",
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Pick a channel, movie or episode to start watching")
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 8.dp)) {
                val e = current
                if (e != null) {
                    PlayerSurface(player, showController = !isLive, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(e.title, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
                    if (isLive) NowNext(epg, now)
                }
                browser()
            }
        }
    }
}
