@file:OptIn(UnstableApi::class)

package com.greenslocks.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets you set the playlist from a PC without typing on the remote:
        // adb shell am start -n com.greenslocks.iptv/.MainActivity --es playlist_url "<url>"
        intent.getStringExtra("playlist_url")?.let {
            Store(getSharedPreferences("iptv", MODE_PRIVATE)).setUrl(it)
        }
        setContent { IptvTheme { App() } }
    }
}

private enum class Tab {
    HOME, LIVE, MOVIES, SERIES, FAVORITES;

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

private fun posKey(e: Entry) = "pos_${e.kind}_${e.id}"
private fun durKey(e: Entry) = "dur_${e.kind}_${e.id}"

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
    val initial = remember { xtreamFromUrl(urlText) }
    var server by remember { mutableStateOf(initial?.base.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.pass.orEmpty()) }
    var useUrl by remember { mutableStateOf(urlText.isNotBlank() && initial == null) }
    var showPass by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<Provider?>(null) }
    var showSetup by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Enter your login and press Connect") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.HOME) }
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
    var homeShelves by remember { mutableStateOf(emptyList<Shelf>()) }
    var wantGuide by remember { mutableStateOf(false) }

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
    var pausedForDetail by remember { mutableStateOf(false) }
    var pausedByNav by remember { mutableStateOf(false) }
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

    // ---- naming, locks, display helpers
    fun nameOf(e: Entry) = renames[e.key()] ?: e.title
    fun titleFor(e: Entry) = if (e.kind == Kind.EPISODE && e.parent.isNotEmpty()) e.parent else nameOf(e)
    fun subFor(e: Entry) = when (e.kind) {
        Kind.EPISODE -> e.title
        Kind.LIVE -> "Live"
        Kind.SERIES -> "Series"
        Kind.MOVIE -> "Movie"
    }
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
    fun visible(e: Entry) = (showHidden || e.key() !in hiddenEntries) && !entryLocked(e)
    fun progressOf(e: Entry): Float? {
        if (e.kind == Kind.LIVE) return null
        val pos = store.long(posKey(e))
        val dur = store.long(durKey(e))
        return if (pos > 15_000 && dur > 0) (pos.toFloat() / dur).coerceIn(0.02f, 0.98f) else null
    }

    fun requirePin(title: String, then: () -> Unit) {
        pinError = null
        pinRequest = PinRequest(title) { pin ->
            if (store.checkPin(pin)) { unlocked = true; then(); PinResult.CLOSE } else PinResult.WRONG
        }
    }

    // ---- persistence helpers
    val favKeys = remember(favorites) { favorites.mapNotNull(::decodeEntry).map { it.key() }.toSet() }
    fun isFav(e: Entry) = e.key() in favKeys
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

    suspend fun ensureCategories(p: Provider, t: Tab) {
        val k = t.kind() ?: return
        if (k != Kind.LIVE && !p.supportsVod) return
        if (catCache[t] == null) catCache[t] = p.categories(k)
    }

    suspend fun loadCategories(p: Provider, t: Tab) {
        val k = t.kind() ?: return
        if (k != Kind.LIVE && !p.supportsVod) {
            status = "Movies and series need an Xtream-style login"
            return
        }
        ensureCategories(p, t)
        listVersion++
    }

    fun orderedCats(t: Tab): List<Category> {
        val kind = t.kind() ?: return emptyList()
        val visibleCats = catCache[t].orEmpty().filter { showHidden || catKey(kind, it.id) !in hiddenCats }
        return if (sortAz) visibleCats.sortedBy { catName(kind, it).lowercase() } else {
            val order = catOrder[kind.name]?.split(LSEP).orEmpty()
            val index = order.withIndex().associate { it.value to it.index }
            visibleCats.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }
    }

    fun resetBrowsing() {
        catCache.clear()
        category = null; series = null; entries = emptyList(); episodes = emptyList()
        detailEntry = null; detail = null; allLive = null; query = ""; homeShelves = emptyList()
    }

    /** A few poster shelves for the home screen: first categories of movies and series. */
    fun loadHome(p: Provider) {
        if (!p.supportsVod) return
        scope.launch {
            runCatching {
                for ((kind, label, t, count) in listOf(
                    Quad(Kind.MOVIE, "Movies", Tab.MOVIES, 4), Quad(Kind.SERIES, "Series", Tab.SERIES, 3),
                )) {
                    ensureCategories(p, t)
                    val cats = orderedCats(t).filter { !catLocked(kind, it) && !isAdult(it.name) }.take(count)
                    for (c in cats) {
                        val items = p.entries(kind, c).take(30)
                        if (items.isNotEmpty()) homeShelves = homeShelves + Shelf("$label · ${catName(kind, c)}", items)
                    }
                }
            }
        }
    }

    fun syncFields() {
        val x = xtreamFromUrl(urlText)
        server = x?.base.orEmpty(); user = x?.user.orEmpty(); pass = x?.pass.orEmpty()
        useUrl = x == null && urlText.isNotBlank()
    }

    fun connect() {
        if (!useUrl) {
            if (server.isBlank() || user.isBlank() || pass.isEmpty()) {
                status = "Enter server, username and password"
                return
            }
            urlText = buildXtreamUrl(server, user, pass)
        }
        store.setUrl(urlText)
        launchLoad {
            status = "Connecting…"
            val p = connectProvider(urlText)
            provider = p
            resetBrowsing()
            tab = Tab.HOME
            status = if (p.supportsVod) "Connected" else "Playlist loaded (live only)"
            showSetup = false
            listVersion++
            loadHome(p)
            if (current == null) {
                current = store.string("last")?.let(::decodeEntry)?.takeIf { it.kind == Kind.LIVE }
            }
        }
    }
    LaunchedEffect(Unit) { if (urlText.isNotBlank()) connect() }

    fun loadProfileState() {
        urlText = store.url()
        syncFields()
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
        if (urlText.isNotBlank()) connect() else { showSetup = true; status = "Enter this profile's login" }
    }

    fun openCategory(kind: Kind, c: Category) {
        val p = provider ?: return
        fun go() {
            category = c; query = ""; entries = emptyList()
            launchLoad { entries = p.entries(kind, c); listVersion++ }
        }
        if (catLocked(kind, c)) requirePin("PIN required") { go() } else go()
    }
    fun openTab(t: Tab) {
        tab = t; query = ""; category = null; entries = emptyList()
        listVersion++
        val p = provider ?: return
        if (t.browsable()) launchLoad {
            loadCategories(p, t)
            val kind = t.kind() ?: return@launchLoad
            orderedCats(t).firstOrNull { !catLocked(kind, it) }?.let { c -> if (category == null) openCategory(kind, c) }
        }
    }
    fun openSeries(e: Entry) {
        val p = provider ?: return
        series = e; detailEntry = e; detail = null; episodes = emptyList()
        launchLoad { episodes = p.episodes(e).map { it.copy(cat = e.cat) } }
        scope.launch { detail = runCatching { p.detail(e) }.getOrNull() }
    }
    fun openMovie(e: Entry) {
        val p = provider ?: return
        if (player.isPlaying) { player.pause(); pausedForDetail = true }
        detailEntry = e; detail = null
        scope.launch { detail = runCatching { p.detail(e) }.getOrNull() }
    }
    fun closeDetail() {
        detailEntry = null; detail = null; series = null; episodes = emptyList()
        if (pausedForDetail && current != null) reloadTick++ // resume what the detail page paused
        pausedForDetail = false
        listVersion++
    }
    fun playNow(e: Entry, full: Boolean) {
        detailEntry = null; detail = null; series = null; pausedForDetail = false
        current = e
        if (full) fullscreen = true
    }
    fun onEntry(e: Entry) {
        when (e.kind) {
            Kind.SERIES -> openSeries(e)
            Kind.MOVIE -> openMovie(e)
            Kind.EPISODE -> playNow(e, true)
            Kind.LIVE -> if (current?.key() == e.key() && tab == Tab.LIVE) fullscreen = true else playNow(e, tab != Tab.LIVE)
        }
    }

    // ---- derived lists
    val cats = remember(catCache[tab], hiddenCats, showHidden, renames, catOrder, sortAz, tab) { orderedCats(tab) }
    val favoriteEntries = remember(favorites) { favorites.mapNotNull(::decodeEntry) }
    val recentEntries = remember(history) { history.mapNotNull(::decodeEntry) }
    val searching = tab == Tab.LIVE && query.length >= 2
    val baseEntries = if (tab == Tab.FAVORITES) favoriteEntries else entries
    val shownEntries = remember(
        baseEntries, query, hiddenEntries, showHidden, renames, sortAz, unlocked, hasPin, lockAdult, lockedCats, searching, allLive, tab,
    ) {
        val source = if (searching) allLive.orEmpty() else baseEntries
        val filtered = source.filter { visible(it) && nameOf(it).contains(query, ignoreCase = true) }
            .let { if (searching) it.take(300) else it }
        if (sortAz && tab.browsable() && !searching) filtered.sortedBy { nameOf(it).lowercase() } else filtered
    }
    val guide = remember(provider) { provider?.let { GuideData(it) } }
    val guideChannels = if (tab == Tab.LIVE) shownEntries.filter { it.kind == Kind.LIVE }
    else favoriteEntries.filter { it.kind == Kind.LIVE && visible(it) }

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
            if (d > 0) store.setLong(durKey(e), d)
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
    // Stop playback quietly when leaving the live screen; resume live when coming back.
    LaunchedEffect(tab, fullscreen) {
        if (fullscreen) return@LaunchedEffect
        if (tab == Tab.LIVE) {
            if (pausedByNav && current?.kind == Kind.LIVE) reloadTick++
            pausedByNav = false
        } else if (current != null && player.isPlaying) {
            player.pause(); pausedByNav = true
        }
    }
    // The live-channel index is only fetched when search or number entry needs it.
    LaunchedEffect(searching, provider) {
        val p = provider
        if (searching && p != null && allLive == null) {
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
                allLive.orEmpty().firstOrNull { it.num == n && visible(it) }?.let { current = it }
            }
        }
        numBuffer = ""
    }
    LaunchedEffect(entries, tab, wantGuide) {
        if (wantGuide && tab == Tab.LIVE && entries.isNotEmpty()) { wantGuide = false; guideOpen = true }
    }

    val firstItem = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(listVersion) {
        if (isTv) { delay(300); runCatching { firstItem.requestFocus() } }
    }
    LaunchedEffect(fullscreen) { if (fullscreen) runCatching { rootFocus.requestFocus() } }

    val overlayActive = detailEntry != null || guideOpen || (fullscreen && current != null)
    BackHandler(enabled = showSetup && provider != null) { showSetup = false }
    BackHandler(enabled = provider != null && !showSetup && tab != Tab.HOME && !overlayActive) { openTab(Tab.HOME) }
    BackHandler(enabled = detailEntry != null) { closeDetail() }
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
                        add(MenuItem("Move earlier", close = false) { moveFavorite(e, -1) })
                        add(MenuItem("Move later", close = false) { moveFavorite(e, 1) })
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
                    add(MenuItem("Move earlier", close = false) { moveCategory(kind, c, -1) })
                    add(MenuItem("Move later", close = false) { moveCategory(kind, c, 1) })
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
                add(MenuItem("Change login for \"${active.name}\"") { showSetup = true })
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

    // ---- reusable pieces of the main UI
    val isLive = current?.kind == Kind.LIVE
    val resizeMode = RESIZES[resizeIdx].first

    val categoryChips: @Composable (Boolean) -> Unit = { focusFirstChip ->
        val kind = tab.kind()
        if (kind != null) {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(cats) { i, c ->
                    Chip(
                        (if (catLocked(kind, c)) "🔒 " else "") + catName(kind, c),
                        selected = category?.id == c.id,
                        modifier = if (i == 0 && focusFirstChip) Modifier.focusRequester(firstItem) else Modifier,
                        onLongClick = { menuTarget = MenuTarget.CatT(kind, c) },
                    ) { openCategory(kind, c) }
                }
            }
        }
    }

    val previewPane: @Composable ColumnScope.() -> Unit = {
        val e = current
        if (e == null) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Palette.surface),
                contentAlignment = Alignment.Center,
            ) { Text("Select a channel to start watching", color = Palette.muted) }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                if (!fullscreen) PlayerSurface(player, !isTv && !isLive, resizeMode, Modifier.fillMaxSize())
            }
            Text(
                titleFor(e), Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            )
            if (isLive) {
                val (cur, next) = nowNext(epg, now)
                if (cur != null) {
                    Text("${cur.title}  ·  until ${clockText(cur.stop)}", modifier = Modifier.padding(top = 4.dp))
                    ProgressLine(((now - cur.start).toFloat() / (cur.stop - cur.start).coerceAtLeast(1)), Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (next != null) Text("Next ${clockText(next.start)}: ${next.title}", color = Palette.muted, modifier = Modifier.padding(top = 6.dp))
            }
            playbackError?.let { Text(it, color = Palette.live, modifier = Modifier.padding(top = 6.dp)) }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Fullscreen") { fullscreen = true }
                ActionButton("Options", primary = false) { showOptions = true }
            }
            if (isTv) {
                Text(
                    "OK on a channel: play, OK again: fullscreen  ·  hold OK: menu  ·  Back: home",
                    color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    // ---- main UI
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
        if (provider == null || showSetup) {
            LoginScreen(
                useUrl, urlText, server, user, pass, showPass, status, busy,
                canCancel = provider != null,
                onUrl = { urlText = it }, onServer = { server = it }, onUser = { user = it }, onPass = { pass = it },
                onTogglePass = { showPass = !showPass }, onToggleMode = { useUrl = !useUrl },
                onConnect = ::connect, onCancel = { showSetup = false },
            )
        } else {
            Row(Modifier.fillMaxSize().focusProperties { canFocus = !overlayActive }) {
                NavRail(
                    items = listOf(
                        Triple("⌂", "Home", tab == Tab.HOME),
                        Triple("●", "Live TV", tab == Tab.LIVE),
                        Triple("▶", "Movies", tab == Tab.MOVIES),
                        Triple("☰", "Series", tab == Tab.SERIES),
                        Triple("★", "Favorites", tab == Tab.FAVORITES),
                        Triple("▤", "TV Guide", false),
                        Triple("⚙", "Settings", false),
                    ),
                    footer = profiles.firstOrNull { it.id == activeProfile }?.name.orEmpty(),
                    compact = !isTv,
                    modifier = Modifier.width(if (isTv) 190.dp else 64.dp),
                ) { i ->
                    when (i) {
                        0 -> openTab(Tab.HOME)
                        1 -> openTab(Tab.LIVE)
                        2 -> openTab(Tab.MOVIES)
                        3 -> openTab(Tab.SERIES)
                        4 -> openTab(Tab.FAVORITES)
                        5 -> if (guideChannels.isNotEmpty()) guideOpen = true else { openTab(Tab.LIVE); wantGuide = true }
                        else -> openSettings()
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        Tab.HOME -> {
                            val cont = recentEntries.filter { visible(it) }.take(20)
                            val favs = favoriteEntries.filter { visible(it) }.take(20)
                            val hero = cont.firstOrNull { it.kind != Kind.LIVE } ?: cont.firstOrNull()
                                ?: homeShelves.firstNotNullOfOrNull { s -> s.items.firstOrNull { visible(it) } }
                            val shelves = buildList {
                                if (cont.isNotEmpty()) add(Triple("Continue watching", cont, true))
                                if (favs.isNotEmpty()) add(Triple("Favorites", favs, true))
                                homeShelves.forEach { add(Triple(it.title, it.items.filter { e -> visible(e) }, false)) }
                            }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                                if (hero != null) item {
                                    HeroBanner(
                                        hero, if (cont.contains(hero)) "Continue watching" else "Featured",
                                        titleFor(hero), subFor(hero),
                                        canOpenDetails = hero.kind == Kind.MOVIE || hero.kind == Kind.SERIES,
                                        playFocus = Modifier.focusRequester(firstItem),
                                        onPlay = { if (hero.kind == Kind.SERIES) onEntry(hero) else playNow(hero, true) },
                                        onDetails = { onEntry(hero) },
                                    )
                                }
                                itemsIndexed(shelves) { i, (title, items, wide) ->
                                    ShelfRow(
                                        title, items, wide, ::titleFor, ::subFor, ::progressOf, ::isFav,
                                        onLongClick = { menuTarget = MenuTarget.EntryT(it) }, onOpen = ::onEntry,
                                        firstFocus = if (hero == null && i == 0) Modifier.focusRequester(firstItem) else Modifier,
                                    )
                                }
                                if (hero == null && shelves.isEmpty()) item {
                                    Text(
                                        if (busy) "Loading…" else "Nothing here yet. Open Live TV, Movies or Series to start.",
                                        Modifier.padding(32.dp), color = Palette.muted,
                                    )
                                }
                            }
                        }

                        Tab.LIVE -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            BrowseHeader("Live TV", query, { query = it }, "Search all channels") {
                                if (guideChannels.isNotEmpty()) ActionButton("▤  Guide", primary = false) { guideOpen = true }
                            }
                            if (!searching) categoryChips(shownEntries.isEmpty())
                            Text(
                                if (busy) "Loading…" else status, color = Palette.muted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                            Row(Modifier.fillMaxSize()) {
                                LazyColumn(Modifier.weight(1.1f).fillMaxHeight()) {
                                    itemsIndexed(shownEntries) { i, e ->
                                        LaunchedEffect(e.id) { guide?.load(e) }
                                        val (cur, _) = nowNext(guide?.cache?.get(e.id).orEmpty(), now)
                                        ChannelRow(
                                            nameOf(e), e.icon, e.num, isFav(e), e.key() == current?.key(),
                                            cur?.title,
                                            cur?.let { (now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                                            if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                                            onLongClick = { menuTarget = MenuTarget.EntryT(e) },
                                        ) { onEntry(e) }
                                    }
                                }
                                Spacer(Modifier.width(20.dp))
                                Column(Modifier.weight(1f).fillMaxHeight()) { previewPane() }
                            }
                        }

                        Tab.MOVIES, Tab.SERIES -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            BrowseHeader(if (tab == Tab.MOVIES) "Movies" else "Series", query, { query = it })
                            categoryChips(shownEntries.isEmpty())
                            if (provider?.supportsVod == false) {
                                Text("Movies and series need an Xtream-style login (server, username, password).", color = Palette.muted)
                            } else if (busy && shownEntries.isEmpty()) {
                                Text("Loading…", color = Palette.muted)
                            }
                            LazyVerticalGrid(
                                GridCells.Adaptive(140.dp), Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                itemsIndexed(shownEntries) { i, e ->
                                    PosterCard(
                                        nameOf(e), e.icon, "", isFav(e), progressOf(e),
                                        if (i == 0) Modifier.focusRequester(firstItem) else Modifier, width = null,
                                        onLongClick = { menuTarget = MenuTarget.EntryT(e) },
                                    ) { onEntry(e) }
                                }
                            }
                        }

                        Tab.FAVORITES -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Text("Favorites", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            val favs = favoriteEntries.filter { visible(it) }
                            if (favs.isEmpty()) {
                                Text("Hold OK on any channel, movie or series to add it here.", Modifier.padding(top = 16.dp), color = Palette.muted)
                            }
                            LazyVerticalGrid(
                                GridCells.Adaptive(230.dp), Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                itemsIndexed(favs) { i, e ->
                                    WideCard(
                                        titleFor(e), subFor(e), e.icon, false, progressOf(e),
                                        if (i == 0) Modifier.focusRequester(firstItem) else Modifier, width = null,
                                        fit = if (e.kind == Kind.LIVE) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
                                        onLongClick = { menuTarget = MenuTarget.EntryT(e) },
                                    ) { onEntry(e) }
                                }
                            }
                        }
                    }
                }
            }

            detailEntry?.let { d ->
                Box(Modifier.fillMaxSize()) {
                    DetailScreen(
                        d, nameOf(d), detail,
                        resumeMs = store.long(posKey(d)),
                        isFavorite = isFav(d),
                        episodes = episodes,
                        episodeProgress = ::progressOf,
                        onPlay = { playNow(d, true) },
                        onRestart = { store.remove(posKey(d)); playNow(d, true) },
                        onFavorite = { toggleFavorite(d) },
                        onEpisode = { playNow(it, true) },
                    )
                }
            }

            if (guideOpen && guide != null) {
                GuideScreen(
                    channels = guideChannels, nameOf = ::nameOf, guide = guide, now = now,
                    onPlay = { ch -> playNow(ch, true); guideOpen = false },
                    onClose = { guideOpen = false; listVersion++ },
                )
            }

            if (fullscreen && current != null) {
                val e = current!!
                PlayerSurface(player, false, resizeMode, Modifier.fillMaxSize().background(Color.Black))
                if (numBuffer.isNotEmpty()) {
                    Text(
                        numBuffer, Modifier.align(Alignment.TopEnd).padding(40.dp),
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                    )
                }
                playbackError?.let {
                    Text(it, Modifier.align(Alignment.Center), color = Palette.live)
                }
                FullscreenOverlay(
                    showInfo, e, titleFor(e), isLive, isFav(e), epg, now, position, duration,
                    if (isLive) "Up/Down: channel  ·  Left: last channel  ·  digits: jump  ·  Menu: options"
                    else "Left/Right: seek 10s  ·  OK: pause  ·  Menu: options",
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
