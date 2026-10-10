@file:OptIn(UnstableApi::class)

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
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
            Store(getSharedPreferences("iptv", MODE_PRIVATE)).setUrl(it)
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

private enum class Tab(val label: String) {
    LIVE("Live"), MOVIES("Movies"), SERIES("Series"), FAVORITES("★ Favorites"), RECENT("Recent");

    fun kind(): Kind? = when (this) {
        LIVE -> Kind.LIVE
        MOVIES -> Kind.MOVIE
        SERIES -> Kind.SERIES
        else -> null
    }

    fun browsable() = kind() != null
}

private sealed interface MenuTarget {
    data class EntryT(val entry: Entry) : MenuTarget
    data class CatT(val kind: Kind, val cat: Category) : MenuTarget
}

private val RESIZES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)
private val BUFFERS = listOf("Low", "Normal", "High")
private val DIGITS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val PAD_DIGITS = listOf(
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

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
private fun PlayerSurface(player: ExoPlayer, showController: Boolean, resizeMode: Int, modifier: Modifier) {
    AndroidView(
        factory = { PlayerView(it) },
        update = {
            if (it.player !== player) it.player = player
            it.useController = showController
            it.resizeMode = resizeMode
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier,
    icon: String = "",
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
    Row(
        modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = source, indication = null,
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotBlank()) {
            AsyncImage(
                model = icon, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(end = 10.dp).width(40.dp).height(52.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, color = fg.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
private fun DetailPane(
    entry: Entry,
    name: String,
    detail: Detail?,
    resumeMs: Long,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(entry.key()) { delay(250); runCatching { playFocus.requestFocus() } }
    val poster = detail?.poster?.takeIf { it.isNotBlank() } ?: entry.icon
    Row(modifier.verticalScroll(rememberScrollState())) {
        if (poster.isNotBlank()) {
            AsyncImage(
                model = poster, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(150.dp).aspectRatio(2f / 3f),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            if (detail == null) {
                Text("Loading details…", style = MaterialTheme.typography.bodySmall)
            } else {
                if (detail.meta.isNotBlank()) Text(detail.meta, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                if (detail.plot.isNotBlank()) Text(detail.plot, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.padding(top = 16.dp)) {
                if (entry.kind == Kind.MOVIE) {
                    Button(onClick = onPlay, modifier = Modifier.focusRequester(playFocus)) {
                        Text(if (resumeMs > 15_000) "▶ Resume ${fmtTime(resumeMs)}" else "▶ Play")
                    }
                    if (resumeMs > 15_000) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onRestart) { Text("Restart") }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onFavorite) { Text(if (isFavorite) "★ Favorited" else "☆ Favorite") }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE) }
    val store = remember { Store(prefs) }
    val scope = rememberCoroutineScope()
    val isTv = remember {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }

    // ---- connection / browsing state
    var profiles by remember { mutableStateOf(store.profiles()) }
    var activeProfile by remember { mutableIntStateOf(store.active) }
    var urlText by remember { mutableStateOf(store.url()) }
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
    var allLive by remember { mutableStateOf<List<Entry>?>(null) }
    var detailEntry by remember { mutableStateOf<Entry?>(null) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    // ---- playback state
    var current by remember { mutableStateOf<Entry?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf(emptyList<Programme>()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showInfo by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var retries by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var numBuffer by remember { mutableStateOf("") }
    val liveHistory = remember { mutableListOf<Entry>() }

    // ---- user data (per profile) and settings
    var favorites by remember { mutableStateOf(store.favorites()) }
    var history by remember { mutableStateOf(store.list("hist")) }
    var hiddenCats by remember { mutableStateOf(store.list("hidCats").toSet()) }
    var hiddenEntries by remember { mutableStateOf(store.list("hidEntries").toSet()) }
    var lockedCats by remember { mutableStateOf(store.list("lockCats").toSet()) }
    var renames by remember { mutableStateOf(store.map("renames")) }
    var catOrder by remember { mutableStateOf(store.map("catOrder")) }
    var sortAz by remember { mutableStateOf(store.flag("sortAz", false)) }
    var showHidden by remember { mutableStateOf(store.flag("showHidden", false)) }
    var lockAdult by remember { mutableStateOf(store.flag("lockAdult", true)) }
    var hasPin by remember { mutableStateOf(store.hasPin()) }
    var bufferMode by remember { mutableIntStateOf(store.int("buffer", 1)) }
    var resizeIdx by remember { mutableIntStateOf(store.int("resize", 0).coerceIn(0, RESIZES.lastIndex)) }
    var unlocked by remember { mutableStateOf(false) }

    // ---- dialogs
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var textPrompt by remember { mutableStateOf<TextPrompt?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var pinRequest by remember { mutableStateOf<PinRequest?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinWrongCount by remember { mutableIntStateOf(0) }
    var guideOpen by remember { mutableStateOf(false) }

    // ---- player
    val player = remember(bufferMode) {
        val lc = when (bufferMode) {
            0 -> DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 1_500, 3_000)
            2 -> DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 90_000, 3_000, 6_000)
            else -> DefaultLoadControl.Builder()
        }.build()
        ExoPlayer.Builder(ctx).setLoadControl(lc).build()
    }
    val currentNow by rememberUpdatedState(current)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retries < 5) {
                    retries++
                    val n = retries
                    scope.launch {
                        delay(2000L * n)
                        if (currentNow?.kind == Kind.LIVE) player.seekToDefaultPosition()
                        player.prepare()
                    }
                } else {
                    playbackError = "Stream failed (${error.errorCodeName})"
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries = 0; playbackError = null }
            }

            override fun onTracksChanged(tracks: Tracks) { tracksTick++ }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    // ---- naming, locks
    fun nameOf(e: Entry) = renames[e.key()] ?: e.title
    fun catName(kind: Kind, c: Category) = renames[catKey(kind, c.id)] ?: c.name
    fun catLocked(kind: Kind, c: Category) =
        !unlocked && hasPin && (catKey(kind, c.id) in lockedCats || (lockAdult && isAdult(c.name)))
    fun entryLocked(e: Entry): Boolean {
        if (unlocked || !hasPin) return false
        if (catKey(e.kind, e.cat) in lockedCats) return true
        val t = when (e.kind) { Kind.LIVE -> Tab.LIVE; Kind.MOVIE -> Tab.MOVIES; else -> Tab.SERIES }
        val name = catCache[t]?.firstOrNull { it.id == e.cat }?.name.orEmpty()
        return lockAdult && isAdult(name)
    }

    fun requirePin(title: String, then: () -> Unit) {
        pinError = null
        pinRequest = PinRequest(title) { pin ->
            if (store.checkPin(pin)) { unlocked = true; then(); PinResult.CLOSE } else PinResult.WRONG
        }
    }

    // ---- persistence helpers
    val favKeys = remember(favorites) { favorites.mapNotNull(::decodeEntry).map { it.key() }.toSet() }
    fun toggleFavorite(e: Entry) {
        val k = e.key()
        favorites = if (k in favKeys) favorites.filter { decodeEntry(it)?.key() != k } else favorites + e.encode()
        store.setList("fav2", favorites)
    }
    fun moveFavorite(e: Entry, delta: Int) {
        val i = favorites.indexOfFirst { decodeEntry(it)?.key() == e.key() }
        val j = i + delta
        if (i < 0 || j !in favorites.indices) return
        favorites = favorites.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        store.setList("fav2", favorites)
    }
    fun pushHistory(e: Entry) {
        history = (listOf(e.encode()) + history.filter { decodeEntry(it)?.key() != e.key() }).take(40)
        store.setList("hist", history)
    }
    fun rename(key: String, name: String) {
        renames = if (name.isBlank()) renames - key else renames + (key to name.trim())
        store.setMap("renames", renames)
    }
    fun toggleHiddenEntry(k: String) {
        hiddenEntries = if (k in hiddenEntries) hiddenEntries - k else hiddenEntries + k
        store.setList("hidEntries", hiddenEntries.toList())
    }
    fun toggleHiddenCat(k: String) {
        hiddenCats = if (k in hiddenCats) hiddenCats - k else hiddenCats + k
        store.setList("hidCats", hiddenCats.toList())
    }
    fun toggleLockedCat(k: String) {
        lockedCats = if (k in lockedCats) lockedCats - k else lockedCats + k
        store.setList("lockCats", lockedCats.toList())
    }

    // ---- loading and navigation
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

    fun resetBrowsing() {
        catCache.clear()
        category = null; series = null; entries = emptyList(); episodes = emptyList()
        detailEntry = null; detail = null; allLive = null; query = ""
    }

    fun connect() {
        store.setUrl(urlText)
        launchLoad {
            status = "Connecting…"
            val p = connectProvider(urlText)
            provider = p
            resetBrowsing()
            tab = Tab.LIVE
            status = if (p.supportsVod) "Connected" else "Playlist loaded (live only)"
            showSetup = false
            loadCategories(p, Tab.LIVE)
            if (current == null) {
                current = store.string("last")?.let(::decodeEntry)?.takeIf { it.kind == Kind.LIVE }
            }
        }
    }
    LaunchedEffect(Unit) { if (urlText.isNotBlank()) connect() }

    fun loadProfileState() {
        urlText = store.url()
        favorites = store.favorites()
        history = store.list("hist")
        hiddenCats = store.list("hidCats").toSet()
        hiddenEntries = store.list("hidEntries").toSet()
        lockedCats = store.list("lockCats").toSet()
        renames = store.map("renames")
        catOrder = store.map("catOrder")
        player.stop()
        current = null; fullscreen = false; provider = null
        resetBrowsing()
    }
    fun switchProfile(id: Int) {
        store.active = id
        activeProfile = id
        loadProfileState()
        if (urlText.isNotBlank()) connect() else { showSetup = true; status = "Paste this profile's playlist URL" }
    }

    fun openTab(t: Tab) {
        tab = t; category = null; series = null; detailEntry = null; query = ""
        val p = provider ?: return
        if (t.browsable()) launchLoad { loadCategories(p, t) } else listVersion++
    }
    fun openCategory(kind: Kind, c: Category) {
        val p = provider ?: return
        fun go() {
            category = c; query = ""; entries = emptyList()
            launchLoad { entries = p.entries(kind, c); listVersion++ }
        }
        if (catLocked(kind, c)) requirePin("PIN required") { go() } else go()
    }
    fun openSeries(e: Entry) {
        val p = provider ?: return
        series = e; detailEntry = e; detail = null; query = ""; episodes = emptyList()
        launchLoad { episodes = p.episodes(e).map { it.copy(cat = e.cat) }; listVersion++ }
        scope.launch { detail = runCatching { p.detail(e) }.getOrNull() }
    }
    fun openMovie(e: Entry) {
        val p = provider ?: return
        player.pause()
        detailEntry = e; detail = null
        scope.launch { detail = runCatching { p.detail(e) }.getOrNull() }
    }
    fun closeDetail() {
        detailEntry = null; detail = null
        if (current != null) reloadTick++ // resume what was playing before the detail page paused it
        listVersion++
    }
    fun goBack() {
        if (series != null) { series = null; detailEntry = null; detail = null } else category = null
        query = ""
        listVersion++
    }
    fun playNow(e: Entry, full: Boolean) {
        detailEntry = null; detail = null
        current = e
        if (full) fullscreen = true
    }
    fun onEntry(e: Entry) {
        when (e.kind) {
            Kind.SERIES -> openSeries(e)
            Kind.MOVIE -> openMovie(e)
            Kind.EPISODE -> playNow(e, true)
            Kind.LIVE -> if (current?.key() == e.key()) fullscreen = true else playNow(e, false)
        }
    }

    // ---- derived lists
    val showingCategories = tab.browsable() && category == null && series == null
    val searchingAll = tab == Tab.LIVE && showingCategories && query.length >= 2
    val cats = remember(catCache[tab], hiddenCats, showHidden, renames, catOrder, sortAz, tab) {
        val kind = tab.kind()
        if (kind == null) emptyList() else {
            val visible = catCache[tab].orEmpty().filter { showHidden || catKey(kind, it.id) !in hiddenCats }
            if (sortAz) visible.sortedBy { catName(kind, it).lowercase() } else {
                val order = catOrder[kind.name]?.split(LSEP).orEmpty()
                val index = order.withIndex().associate { it.value to it.index }
                visible.sortedBy { index[it.id] ?: Int.MAX_VALUE }
            }
        }
    }
    val shownCats = remember(cats, query, renames) {
        val kind = tab.kind()
        if (kind == null) cats else cats.filter { catName(kind, it).contains(query, ignoreCase = true) }
    }
    val favoriteEntries = remember(favorites) { favorites.mapNotNull(::decodeEntry) }
    val recentEntries = remember(history) { history.mapNotNull(::decodeEntry) }
    val baseEntries = when {
        series != null -> episodes
        tab == Tab.FAVORITES -> favoriteEntries
        tab == Tab.RECENT -> recentEntries
        else -> entries
    }
    val shownEntries = remember(
        baseEntries, query, hiddenEntries, showHidden, renames, sortAz, unlocked, hasPin, lockAdult, lockedCats, searchingAll, allLive,
    ) {
        val source = if (searchingAll) allLive.orEmpty() else baseEntries
        val filtered = source.filter {
            (showHidden || it.key() !in hiddenEntries) && !entryLocked(it) && nameOf(it).contains(query, ignoreCase = true)
        }.let { if (searchingAll) it.take(300) else it }
        val sortable = series == null && tab.browsable() && !searchingAll
        if (sortAz && sortable) filtered.sortedBy { nameOf(it).lowercase() } else filtered
    }
    val guide = remember(provider) { provider?.let { GuideData(it) } }
    val guideChannels = shownEntries.filter { it.kind == Kind.LIVE }

    fun moveCategory(kind: Kind, c: Category, delta: Int) {
        if (sortAz) { status = "Turn off A-Z sorting in Settings to reorder"; return }
        val ids = cats.map { it.id }.toMutableList()
        val i = ids.indexOf(c.id)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return
        val t = ids[i]; ids[i] = ids[j]; ids[j] = t
        val rest = catCache[tab].orEmpty().map { it.id }.filter { it !in ids }
        catOrder = catOrder + (kind.name to (ids + rest).joinToString(LSEP))
        store.setMap("catOrder", catOrder)
    }

    fun step(delta: Int) {
        val list = guideChannels
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.key() == current?.key() }
        current = if (i < 0) list[0] else list[(i + delta).mod(list.size)]
    }
    fun lastChannel() {
        if (liveHistory.size >= 2) current = liveHistory[liveHistory.size - 2]
    }

    // ---- effects
    LaunchedEffect(current, player, reloadTick) {
        val e = current ?: return@LaunchedEffect
        val p = provider ?: return@LaunchedEffect
        retries = 0; playbackError = null
        if (e.kind == Kind.LIVE) {
            store.setString("last", e.encode())
            if (liveHistory.lastOrNull()?.key() != e.key()) {
                liveHistory += e
                if (liveHistory.size > 10) liveHistory.removeAt(0)
            }
        }
        pushHistory(e)
        player.setMediaItem(MediaItem.fromUri(p.streamUrl(e)))
        player.prepare()
        if (e.kind != Kind.LIVE) {
            val saved = store.long(posKey(e))
            if (saved > 15_000) player.seekTo(saved)
        }
        player.playWhenReady = true
    }
    LaunchedEffect(current, player) {
        val e = current ?: return@LaunchedEffect
        if (e.kind == Kind.LIVE) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val d = player.duration
            val pos = player.currentPosition
            if (d > 0 && pos > d - 30_000) store.remove(posKey(e))
            else if (player.isPlaying) store.setLong(posKey(e), pos)
        }
    }
    LaunchedEffect(current, now / 300_000) {
        val e = current
        val p = provider
        epg = if (e != null && p != null && e.kind == Kind.LIVE) {
            runCatching { p.epg(e, 3) }.getOrDefault(emptyList())
        } else emptyList()
    }
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
    // The live-channel index is only fetched when search or number entry needs it.
    LaunchedEffect(searchingAll, provider) {
        val p = provider
        if (searchingAll && p != null && allLive == null) {
            launchLoad { status = "Searching all channels…"; allLive = p.allLive(); status = "" }
        }
    }
    LaunchedEffect(numBuffer) {
        if (numBuffer.isEmpty()) return@LaunchedEffect
        delay(1500)
        val n = numBuffer.toIntOrNull()
        val p = provider
        if (n != null && p != null) {
            runCatching {
                if (allLive == null) allLive = p.allLive()
                allLive.orEmpty().firstOrNull { it.num == n && !entryLocked(it) && it.key() !in hiddenEntries }
                    ?.let { current = it }
            }
        }
        numBuffer = ""
    }

    val firstItem = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(listVersion) {
        if (isTv) { delay(250); runCatching { firstItem.requestFocus() } }
    }
    LaunchedEffect(fullscreen) { if (fullscreen) runCatching { rootFocus.requestFocus() } }

    BackHandler(enabled = series != null || category != null) { goBack() }
    BackHandler(enabled = detailEntry != null && series == null) { closeDetail() }
    BackHandler(enabled = fullscreen) { fullscreen = false; listVersion++ }
    BackHandler(enabled = guideOpen) { guideOpen = false; listVersion++ }

    // ---- dialogs UI
    pinRequest?.let { req ->
        key(req, pinWrongCount) {
            PinPadDialog(
                req.title, pinError,
                onDone = { pin ->
                    when (req.onPin(pin)) {
                        PinResult.CLOSE -> { pinRequest = null; pinError = null }
                        PinResult.KEEP -> pinError = null
                        PinResult.WRONG -> { pinError = "Wrong PIN"; pinWrongCount++ }
                    }
                },
                onCancel = { pinRequest = null; pinError = null },
            )
        }
    }
    textPrompt?.let { TextPromptDialog(it) { textPrompt = null } }

    menuTarget?.let { target ->
        when (target) {
            is MenuTarget.EntryT -> {
                val e = target.entry
                val k = e.key()
                ActionMenuDialog(nameOf(e), buildList {
                    add(MenuItem(if (k in favKeys) "Remove from favorites" else "Add to favorites") { toggleFavorite(e) })
                    add(MenuItem("Rename…") { textPrompt = TextPrompt("Rename", nameOf(e)) { rename(k, it) } })
                    if (k in renames) add(MenuItem("Reset name") { rename(k, "") })
                    add(MenuItem(if (k in hiddenEntries) "Unhide" else "Hide") { toggleHiddenEntry(k) })
                    if (tab == Tab.FAVORITES) {
                        add(MenuItem("Move up", close = false) { moveFavorite(e, -1) })
                        add(MenuItem("Move down", close = false) { moveFavorite(e, 1) })
                    }
                }) { menuTarget = null }
            }
            is MenuTarget.CatT -> {
                val kind = target.kind
                val c = target.cat
                val k = catKey(kind, c.id)
                ActionMenuDialog(catName(kind, c), buildList {
                    add(MenuItem("Rename…") { textPrompt = TextPrompt("Rename", catName(kind, c)) { rename(k, it) } })
                    if (k in renames) add(MenuItem("Reset name") { rename(k, "") })
                    add(MenuItem(if (k in hiddenCats) "Unhide" else "Hide") { toggleHiddenCat(k) })
                    add(MenuItem("Move up", close = false) { moveCategory(kind, c, -1) })
                    add(MenuItem("Move down", close = false) { moveCategory(kind, c, 1) })
                    add(
                        MenuItem(
                            if (k in lockedCats) "Remove PIN lock" else if (hasPin) "Lock with PIN" else "Lock with PIN (set a PIN in Settings first)",
                            enabled = hasPin || k in lockedCats,
                        ) {
                            if (k in lockedCats && !unlocked) requirePin("Enter PIN") { toggleLockedCat(k) } else toggleLockedCat(k)
                        }
                    )
                }) { menuTarget = null }
            }
        }
    }

    if (showOptions) {
        @Suppress("UNUSED_EXPRESSION") tracksTick
        val e = current
        ActionMenuDialog(
            "Player options",
            buildList {
                add(MenuItem(audioLabel(player), close = false) { cycleAudio(player); tracksTick++ })
                add(MenuItem(textLabel(player), close = false) { cycleText(player); tracksTick++ })
                add(MenuItem("Picture: ${RESIZES[resizeIdx].second}", close = false) {
                    resizeIdx = (resizeIdx + 1) % RESIZES.size
                    store.setInt("resize", resizeIdx)
                })
                if (e != null) {
                    add(MenuItem(if (e.key() in favKeys) "★ Remove from favorites" else "☆ Add to favorites", close = false) { toggleFavorite(e) })
                }
                add(MenuItem("Close") {})
            },
        ) { showOptions = false }
    }

    fun openSettings() {
        if (hasPin && !unlocked) requirePin("Enter PIN for settings") { showSettings = true } else showSettings = true
    }
    fun newPinFlow() {
        pinError = null
        pinRequest = PinRequest("Choose a 4-digit PIN") { first ->
            pinRequest = PinRequest("Confirm PIN") { second ->
                if (first == second) {
                    store.setPin(first); hasPin = true; unlocked = true
                    PinResult.CLOSE
                } else PinResult.WRONG
            }
            PinResult.KEEP
        }
    }
    if (showSettings) {
        val active = profiles.firstOrNull { it.id == activeProfile } ?: profiles.first()
        ActionMenuDialog(
            "Settings",
            buildList {
                profiles.forEach { pr ->
                    add(MenuItem((if (pr.id == activeProfile) "● " else "○ ") + pr.name) {
                        if (pr.id != activeProfile) switchProfile(pr.id)
                    })
                }
                add(MenuItem("+ New profile") {
                    val id = store.nextProfileId()
                    profiles = profiles + Profile(id, "Profile $id")
                    store.saveProfiles(profiles)
                    switchProfile(id)
                })
                add(MenuItem("Rename profile \"${active.name}\"") {
                    textPrompt = TextPrompt("Profile name", active.name) { n ->
                        if (n.isNotBlank()) {
                            profiles = profiles.map { if (it.id == active.id) it.copy(name = n.trim()) else it }
                            store.saveProfiles(profiles)
                        }
                    }
                })
                add(MenuItem("Delete profile \"${active.name}\"", enabled = active.id != 0) {
                    val id = active.id
                    store.deleteProfileData(id)
                    profiles = profiles.filter { it.id != id }
                    store.saveProfiles(profiles)
                    switchProfile(0)
                })
                add(MenuItem("Sort A–Z: ${if (sortAz) "on" else "off"}", close = false) {
                    sortAz = !sortAz; store.setFlag("sortAz", sortAz)
                })
                add(MenuItem("Show hidden items: ${if (showHidden) "on" else "off"}", close = false) {
                    showHidden = !showHidden; store.setFlag("showHidden", showHidden)
                })
                add(MenuItem("Buffer: ${BUFFERS[bufferMode]}  (higher = fewer stalls, slower start)", close = false) {
                    bufferMode = (bufferMode + 1) % BUFFERS.size; store.setInt("buffer", bufferMode)
                })
                add(MenuItem(if (hasPin) "Change PIN" else "Set parental PIN") {
                    if (hasPin) requirePin("Enter current PIN") { newPinFlow() } else newPinFlow()
                })
                if (hasPin) {
                    add(MenuItem("Lock adult categories: ${if (lockAdult) "on" else "off"}", close = false) {
                        lockAdult = !lockAdult; store.setFlag("lockAdult", lockAdult)
                    })
                    if (unlocked) add(MenuItem("Lock now") { unlocked = false })
                    add(MenuItem("Remove PIN") { requirePin("Enter PIN to remove it") { store.clearPin(); hasPin = false } })
                }
                add(MenuItem("Close") {})
            },
        ) { showSettings = false }
    }

    // ---- main UI
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
                TextButton(onClick = { showSetup = true }) { Text("URL") }
            }
            TextButton(onClick = ::openSettings) { Text("⚙ Settings") }
        }
        if (provider != null) {
            Row(Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                Tab.values().forEach { t ->
                    FilterChip(tab == t, { openTab(t) }, { Text(t.label) })
                    Spacer(Modifier.width(6.dp))
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text(if (tab == Tab.LIVE && showingCategories) "Search all channels" else "Search") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category != null || series != null) {
                    TextButton(onClick = ::goBack) {
                        val kind = tab.kind()
                        val label = series?.let { nameOf(it) } ?: category?.let { c -> kind?.let { catName(it, c) } }.orEmpty()
                        Text("◀  $label")
                    }
                }
                if (guideChannels.isNotEmpty() && series == null) {
                    TextButton(onClick = { guideOpen = true }) { Text("▦ TV Guide") }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                if (showingCategories && !searchingAll) {
                    itemsIndexed(shownCats) { i, c ->
                        val kind = tab.kind() ?: return@itemsIndexed
                        val k = catKey(kind, c.id)
                        ListRow(
                            (if (catLocked(kind, c)) "🔒 " else "") + catName(kind, c),
                            if (k in hiddenCats) "hidden" else "",
                            selected = false,
                            modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                            onLongClick = { menuTarget = MenuTarget.CatT(kind, c) },
                        ) { openCategory(kind, c) }
                    }
                } else {
                    itemsIndexed(shownEntries) { i, e ->
                        val sub = buildList {
                            if (tab == Tab.FAVORITES || tab == Tab.RECENT) {
                                add(e.kind.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                            if (tab == Tab.RECENT && e.kind != Kind.LIVE) {
                                val pos = store.long(posKey(e))
                                if (pos > 15_000) add("resume ${fmtTime(pos)}")
                            }
                            if (e.kind == Kind.LIVE && e.num > 0) add("Ch ${e.num}")
                            if (e.key() in hiddenEntries) add("hidden")
                        }.joinToString("  ·  ")
                        ListRow(
                            (if (e.key() in favKeys) "★ " else "") + nameOf(e), sub,
                            selected = e.key() == current?.key(),
                            modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                            icon = e.icon,
                            onLongClick = { menuTarget = MenuTarget.EntryT(e) },
                        ) { onEntry(e) }
                    }
                }
            }
        }
    }

    val isLive = current?.kind == Kind.LIVE
    val resizeMode = RESIZES[resizeIdx].first
    val playerPane: @Composable ColumnScope.(Boolean) -> Unit = { controller ->
        val e = current
        val d = detailEntry
        if (d != null) {
            DetailPane(
                d, nameOf(d), detail,
                resumeMs = store.long(posKey(d)),
                isFavorite = d.key() in favKeys,
                onPlay = { playNow(d, true) },
                onRestart = { store.remove(posKey(d)); playNow(d, true) },
                onFavorite = { toggleFavorite(d) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else if (e != null) {
            PlayerSurface(player, controller, resizeMode, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text(nameOf(e), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
            if (isLive) NowNext(epg, now)
            playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row {
                TextButton(onClick = { showOptions = true }) { Text("Options") }
                TextButton(onClick = { fullscreen = true }) { Text("Fullscreen") }
            }
            if (isTv) {
                Text(
                    "OK: play / fullscreen · hold OK: menu · Back: up a level",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Text("Pick a channel, movie or episode to start watching")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (!fullscreen || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                infoTick++
                val digit = DIGITS.indexOf(ev.key).takeIf { it >= 0 } ?: PAD_DIGITS.indexOf(ev.key)
                if (digit >= 0 && isLive) {
                    numBuffer = (numBuffer + digit).takeLast(5)
                    return@onPreviewKeyEvent true
                }
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> if (isLive) { step(-1); true } else false
                    Key.DirectionDown, Key.ChannelDown -> if (isLive) { step(1); true } else false
                    Key.DirectionLeft -> if (isLive) { lastChannel(); true } else {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> if (!isLive) { player.seekTo(player.currentPosition + 10_000); true } else false
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.Menu -> { showOptions = true; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        if (guideOpen && guide != null) {
            GuideScreen(
                channels = guideChannels, nameOf = ::nameOf, guide = guide, now = now,
                onPlay = { ch -> playNow(ch, true); guideOpen = false },
                onClose = { guideOpen = false; listVersion++ },
            )
        } else if (fullscreen && current != null) {
            PlayerSurface(player, false, resizeMode, Modifier.fillMaxSize().background(Color.Black))
            val e = current!!
            if (numBuffer.isNotEmpty()) {
                Text(
                    numBuffer, Modifier.align(Alignment.TopEnd).padding(32.dp),
                    style = MaterialTheme.typography.displayMedium,
                )
            }
            playbackError?.let {
                Text(it, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
            }
            if (showInfo) {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f)).padding(24.dp)
                ) {
                    Text(
                        (if (e.key() in favKeys) "★ " else "") + nameOf(e) + if (e.num > 0 && isLive) "   (Ch ${e.num})" else "",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (isLive) NowNext(epg, now) else {
                        Text("${fmtTime(position)} / ${fmtTime(duration)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (isLive) "Up/Down: channel · Left: last channel · digits: jump · Menu: options"
                        else "Left/Right: seek 10s · OK: pause · Menu: options",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else if (isTv) {
            Row(Modifier.fillMaxSize().padding(24.dp)) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) { browser() }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(0.58f).fillMaxHeight()) { playerPane(false) }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 8.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max = 380.dp)) { playerPane(!isLive) }
                browser()
            }
        }
    }
}
