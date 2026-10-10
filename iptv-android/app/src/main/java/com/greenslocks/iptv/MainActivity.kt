package com.greenslocks.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
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

private const val FAVORITES = "\u2605 Favorites"

private fun redact(msg: String?): String =
    (msg ?: "unknown error").replace(Regex("(?i)(username|password|user|pass)=[^&\\s]*"), "$1=***")

private fun epgUrlFor(playlistUrl: String, fromHeader: String?): String? =
    fromHeader ?: if ("get.php" in playlistUrl) playlistUrl.replace("get.php", "xmltv.php") else null

private fun clock(ms: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private suspend fun loadPlaylist(url: String): Playlist = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
    M3uParser.parse(conn.getInputStream().bufferedReader().use { it.readText() })
}

@Composable
private fun PlayerSurface(player: ExoPlayer, showController: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = showController } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    ch: Channel,
    selected: Boolean,
    favorite: Boolean,
    subtitle: String,
    modifier: Modifier,
    onLongClick: () -> Unit,
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
        Text((if (favorite) "\u2605 " else "") + ch.name, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle, color = fg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NowNext(ch: Channel, epg: Map<String, List<Programme>>, now: Long, modifier: Modifier = Modifier) {
    val (cur, next) = Epg.nowNext(epg[ch.tvgId.lowercase()], now)
    Column(modifier) {
        if (cur != null) Text("Now: ${cur.title} (until ${clock(cur.stop)})", style = MaterialTheme.typography.bodyMedium)
        if (next != null) Text("Next ${clock(next.start)}: ${next.title}", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val isTv = remember {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }

    var playlistUrl by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var status by remember { mutableStateOf("Enter the URL of your M3U playlist") }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf<Channel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf(emptyMap<String, List<Programme>>()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("fav", emptySet())!!.toSet()) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    fun toggleFavorite(ch: Channel) {
        favorites = if (ch.url in favorites) favorites - ch.url else favorites + ch.url
        prefs.edit().putStringSet("fav", favorites).apply()
    }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(current) {
        current?.let {
            prefs.edit().putString("last", it.url).apply()
            player.setMediaItem(MediaItem.fromUri(it.url))
            player.prepare()
            player.playWhenReady = true
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    LaunchedEffect(current, fullscreen) {
        showInfo = true
        delay(5000)
        showInfo = false
    }

    fun load() {
        prefs.edit().putString("url", playlistUrl).apply()
        status = "Loading…"
        scope.launch {
            runCatching { loadPlaylist(playlistUrl.trim()) }
                .onSuccess { pl ->
                    channels = pl.channels
                    status = "${pl.channels.size} channels"
                    if (current == null) {
                        val last = prefs.getString("last", null)
                        current = pl.channels.firstOrNull { it.url == last }
                    }
                    epgUrlFor(playlistUrl.trim(), pl.epgUrl)?.let { url ->
                        runCatching { withContext(Dispatchers.IO) { Epg.load(url) } }
                            .onSuccess { epg = it; status = "${pl.channels.size} channels, guide loaded" }
                            .onFailure { status = "${pl.channels.size} channels (guide unavailable)" }
                    }
                }
                .onFailure { status = "Failed: ${redact(it.message)}" }
        }
    }
    LaunchedEffect(Unit) { if (playlistUrl.isNotBlank()) load() }

    val groups = remember(channels) { channels.map { it.group }.distinct().sorted() }
    val shown = remember(channels, group, query, favorites) {
        channels.filter {
            val inGroup = when (group) {
                null -> true
                FAVORITES -> it.url in favorites
                else -> it.group == group
            }
            inGroup && it.name.contains(query, ignoreCase = true)
        }
    }

    fun step(delta: Int) {
        if (shown.isEmpty()) return
        val i = shown.indexOf(current)
        current = if (i < 0) shown[0] else shown[(i + delta).mod(shown.size)]
    }

    val firstItem = remember { FocusRequester() }
    LaunchedEffect(channels) {
        if (isTv && channels.isNotEmpty()) {
            delay(300)
            runCatching { firstItem.requestFocus() }
        }
    }

    val browser: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = playlistUrl, onValueChange = { playlistUrl = it },
                label = { Text("M3U playlist URL") }, singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::load) { Text("Load") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (channels.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyRow {
                item { FilterChip(group == null, { group = null }, { Text("All") }) }
                item {
                    Spacer(Modifier.width(6.dp))
                    FilterChip(group == FAVORITES, { group = FAVORITES }, { Text(FAVORITES) })
                }
                items(groups) { g ->
                    Spacer(Modifier.width(6.dp))
                    FilterChip(group == g, { group = g }, { Text(g) })
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                itemsIndexed(shown) { index, ch ->
                    val nowTitle = Epg.nowNext(epg[ch.tvgId.lowercase()], now).first?.title
                    ChannelRow(
                        ch, selected = ch == current, favorite = ch.url in favorites,
                        subtitle = nowTitle ?: ch.group,
                        modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                        onLongClick = { toggleFavorite(ch) },
                    ) {
                        // First press plays; pressing OK on the playing channel goes fullscreen.
                        if (current == ch) fullscreen = true else current = ch
                    }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (!fullscreen || e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { step(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { step(1); true }
                    Key.Menu -> { current?.let(::toggleFavorite); true }
                    else -> false
                }
            }
    ) {
        if (fullscreen && current != null) {
            PlayerSurface(player, showController = false, modifier = Modifier.fillMaxSize().background(Color.Black))
            if (showInfo) {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f)).padding(24.dp)
                ) {
                    val ch = current!!
                    Text((if (ch.url in favorites) "\u2605 " else "") + ch.name, style = MaterialTheme.typography.headlineSmall)
                    NowNext(ch, epg, now)
                    Text("Menu: favorite", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (isTv) {
            Row(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.weight(0.4f).fillMaxHeight()) { browser() }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(0.6f).fillMaxHeight()) {
                    if (current != null) {
                        PlayerSurface(player, showController = false, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Text(current!!.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
                        NowNext(current!!, epg, now)
                        Text("OK: play, OK again: fullscreen, hold OK: favorite. Up/Down changes channel.",
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Select a channel to start watching")
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 8.dp)) {
                if (current != null) {
                    PlayerSurface(player, showController = true, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(current!!.name, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.titleMedium)
                    NowNext(current!!, epg, now)
                }
                browser()
            }
        }
    }
}
