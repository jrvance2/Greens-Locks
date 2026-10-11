@file:OptIn(UnstableApi::class)

package com.greenslocks.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoSize
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets you set the playlist from a PC without typing on the remote:
        // adb shell am start -n com.greenslocks.iptv/.MainActivity --es playlist_url "<url>"
        val store = Store(getSharedPreferences("iptv", MODE_PRIVATE))
        intent.getStringExtra("playlist_url")?.let { store.setUrl(it) }
        applyAppearance(store.int("accent", 0), store.int("bg", 0), store.int("cards", 1), store.int("text", 1), store.int("anim", 0))
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
private val SORT_NAMES = listOf("Default", "A–Z", "Newest", "Top rated")
private val SUB_NAMES = listOf("Small", "Medium", "Large")
private val SUB_SCALES = listOf(0.8f, 1f, 1.35f)
private val SLEEP_STEPS = listOf(0, 15, 30, 60, 90)
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
private fun PlayerSurface(
    player: ExoPlayer, showController: Boolean, resizeMode: Int, modifier: Modifier, subScale: Float = 1f,
) {
    AndroidView(
        factory = { PlayerView(it) },
        update = {
            if (it.player !== player) it.player = player
            it.useController = showController
            it.resizeMode = resizeMode
            it.subtitleView?.apply {
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subScale)
                setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE, 0x99000000.toInt(), android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
                    )
                )
            }
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
    var pinnedCats by remember { mutableStateOf(store.list("pinCats").toSet()) }
    var counts by remember { mutableStateOf(emptyMap<Kind, Map<String, Int>>()) }
    val countsLoading = remember { mutableSetOf<Kind>() }
    var catFilter by remember { mutableStateOf("") }
    var catRegion by remember { mutableStateOf("*all") }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableIntStateOf(0) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var accentIdx by remember { mutableIntStateOf(store.int("accent", 0)) }
    var bgIdx by remember { mutableIntStateOf(store.int("bg", 0)) }
    var cardIdx by remember { mutableIntStateOf(store.int("cards", 1)) }
    var textIdx by remember { mutableIntStateOf(store.int("text", 1)) }
    var animIdx by remember { mutableIntStateOf(store.int("anim", 0)) }
    var startIdx by remember { mutableIntStateOf(store.int("start", 1)) }
    var startTick by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var panelMode by remember { mutableIntStateOf(0) }
    var panelCat by remember { mutableStateOf<Category?>(null) }
    var panelChannels by remember { mutableStateOf(emptyList<Entry>()) }
    var autoNext by remember { mutableStateOf(store.flag("autoNext", true)) }
    var matchFps by remember { mutableStateOf(store.flag("matchFps", true)) }
    var subSize by remember { mutableIntStateOf(store.int("subSize", 1)) }
    var liveTs by remember { mutableStateOf(store.flag("liveTs", false)) }
    var sortMode by remember { mutableIntStateOf(store.int("sortMode", 0)) }
    var myList by remember { mutableStateOf(store.list("mylist")) }
    var favTab by remember { mutableIntStateOf(0) }
    var sleepIdx by remember { mutableIntStateOf(0) }
    var sleepAt by remember { mutableLongStateOf(0L) }
    var nextUp by remember { mutableStateOf<Entry?>(null) }
    var nextUpSecs by remember { mutableIntStateOf(0) }
    var nextUpGo by remember { mutableStateOf(false) }
    var endedTick by remember { mutableIntStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyMap<Kind, List<Entry>>()) }
    var indexStatus by remember { mutableStateOf<String?>(null) }
    var accountOpen by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var updateOpen by remember { mutableStateOf(false) }
    var pairInfo by remember { mutableStateOf<PairInfo?>(null) }
    val index = remember(activeProfile) { SearchIndex(ctx, activeProfile) }

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
                if (state == Player.STATE_ENDED) endedTick++
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) { tracksTick++ }

            override fun onTracksChanged(tracks: Tracks) { tracksTick++ }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    fun toast(msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }

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
    val myKeys = remember(myList) { myList.mapNotNull(::decodeEntry).map { it.key() }.toSet() }
    fun toggleMyList(e: Entry) {
        val k = e.key()
        myList = if (k in myKeys) myList.filter { decodeEntry(it)?.key() != k } else myList + e.encode()
        store.setList("mylist", myList)
    }
    fun togglePinnedCat(k: String) {
        pinnedCats = if (k in pinnedCats) pinnedCats - k else pinnedCats + k
        store.setList("pinCats", pinnedCats.toList())
    }
    fun setHiddenMany(kind: Kind, ids: Collection<String>, hidden: Boolean) {
        val keys = ids.map { catKey(kind, it) }.toSet()
        hiddenCats = if (hidden) hiddenCats + keys else hiddenCats - keys
        store.setList("hidCats", hiddenCats.toList())
    }
    fun applyNow() = applyAppearance(accentIdx, bgIdx, cardIdx, textIdx, animIdx)
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

    fun loadCounts(kind: Kind) {
        val p = provider ?: return
        if (counts.containsKey(kind) || kind == Kind.EPISODE) return
        if (kind != Kind.LIVE && !p.supportsVod) return
        if (!countsLoading.add(kind)) return
        scope.launch {
            val r = runCatching { p.counts(kind) }.getOrNull()
            if (r != null) counts = counts + (kind to r)
            countsLoading.remove(kind)
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
        counts = emptyMap(); countsLoading.clear(); catFilter = ""; catRegion = "*all"
    }

    /** A few poster shelves for the home screen: first categories of movies and series. */
    fun loadHome(p: Provider) {
        if (!p.supportsVod) return
        scope.launch {
            val loaded = mutableListOf<Entry>()
            runCatching {
                for ((kind, label, t, count) in listOf(
                    Quad(Kind.MOVIE, "Movies", Tab.MOVIES, 4), Quad(Kind.SERIES, "Series", Tab.SERIES, 3),
                )) {
                    ensureCategories(p, t)
                    val cats = orderedCats(t).filter { !catLocked(kind, it) && !isAdult(it.name) }.take(count)
                    for (c in cats) {
                        val items = p.entries(kind, c).take(30)
                        loaded += items
                        if (items.isNotEmpty()) homeShelves = homeShelves + Shelf("$label · ${catName(kind, c)}", items)
                    }
                }
            }
            val newest = loaded.filter { it.added > 0 }.sortedByDescending { it.added }.take(30)
            if (newest.isNotEmpty()) homeShelves = listOf(Shelf("Recently added", newest)) + homeShelves
        }
    }

    fun rebuildIndex(p: Provider, force: Boolean) {
        scope.launch {
            val stale = System.currentTimeMillis() - (store.string("indexedAt")?.toLongOrNull() ?: 0L) > 12 * 3_600_000L
            val needs = withContext(Dispatchers.IO) { force || stale || index.count() == 0 }
            if (!needs) return@launch
            indexStatus = "Indexing library…"
            runCatching {
                index.rebuild(p, if (p.supportsVod) listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES) else listOf(Kind.LIVE)) { n ->
                    indexStatus = "Indexing library… $n items"
                }
                store.setString("indexedAt", System.currentTimeMillis().toString())
            }
            indexStatus = null
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
            rebuildIndex(p, false)
            if (current == null) {
                current = store.string("last")?.let(::decodeEntry)?.takeIf { it.kind == Kind.LIVE }
            }
            startTick++
            if (store.string("picked") == null) {
                val total = runCatching {
                    ensureCategories(p, Tab.LIVE); ensureCategories(p, Tab.MOVIES); ensureCategories(p, Tab.SERIES)
                    listOf(Tab.LIVE, Tab.MOVIES, Tab.SERIES).sumOf { catCache[it]?.size ?: 0 }
                }.getOrDefault(0)
                if (total > 40) { pickerTab = 0; pickerOpen = true }
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
        pinnedCats = store.list("pinCats").toSet()
        myList = store.list("mylist")
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
            launchLoad { entries = p.entries(kind, c) }
        }
        if (catLocked(kind, c)) requirePin("PIN required") { go() } else go()
    }
    fun openTab(t: Tab) {
        tab = t; query = ""; category = null; entries = emptyList()
        catFilter = ""; catRegion = "*all"
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

    fun openPanel() {
        val p = provider ?: return
        val e = current ?: return
        panelMode = 0; panelOpen = true
        val catId = e.cat
        scope.launch {
            runCatching {
                ensureCategories(p, Tab.LIVE)
                val c = catCache[Tab.LIVE]?.firstOrNull { it.id == catId } ?: Category(catId, "")
                panelCat = c
                panelChannels = if (category?.id == catId && entries.isNotEmpty()) entries else p.entries(Kind.LIVE, c)
                if (catId.isBlank()) panelMode = 1
            }
        }
    }
    fun panelPick(c: Category) {
        val p = provider ?: return
        panelMode = 0; panelCat = c; panelChannels = emptyList()
        scope.launch { panelChannels = runCatching { p.entries(Kind.LIVE, c) }.getOrDefault(emptyList()) }
    }

    fun catchUpEntry(ch: Entry, p: Programme) = ch.copy(
        title = "${nameOf(ch)} · ${p.title}", start = p.start, durMin = ((p.stop - p.start) / 60_000).toInt() + 2,
    )
    fun playCatchUp(ch: Entry, p: Programme) {
        if (!p.archive) { toast("Catch-up isn't available for this programme"); return }
        playNow(catchUpEntry(ch, p), true)
    }
    fun openTrailer(id: String?) {
        if (id.isNullOrBlank()) return
        val url = if (id.startsWith("http")) id else "https://www.youtube.com/watch?v=$id"
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("No app on this device can open the trailer") }
    }
    fun loadAccount() {
        val p = provider ?: return
        scope.launch { account = runCatching { p.account() }.getOrNull() }
    }
    fun checkUpdates(silent: Boolean) {
        scope.launch {
            if (!silent) updateState = UpdateState.Checking
            val r = runCatching { Updater.latest() }.getOrNull()
            updateState = when {
                r == null -> if (silent) UpdateState.Idle else UpdateState.Failed("Couldn't reach GitHub")
                r.build > BuildConfig.VERSION_CODE -> {
                    if (silent && store.string("updSkip") != r.build.toString()) updateOpen = true
                    UpdateState.Available(r)
                }
                else -> UpdateState.UpToDate
            }
        }
    }
    fun downloadUpdate(r: Release) {
        scope.launch {
            updateState = UpdateState.Downloading(0)
            runCatching { Updater.download(ctx, r.url) { pct -> updateState = UpdateState.Downloading(pct) } }
                .onSuccess { f ->
                    updateState = UpdateState.Idle
                    if (!Updater.install(ctx, f)) toast("Allow Vance TV to install apps, then choose Update again")
                }
                .onFailure { updateState = UpdateState.Failed(redact(it.message)) }
        }
    }
    fun backupFile() = File(ctx.getExternalFilesDir(null), "vancetv-backup.json")
    fun backup() {
        runCatching { val f = backupFile(); f.writeText(store.exportBackup()); toast("Saved to ${f.absolutePath}") }
            .onFailure { toast("Backup failed: ${redact(it.message)}") }
    }
    fun restore() {
        val f = backupFile()
        if (!f.exists()) { toast("No backup found at ${f.absolutePath}"); return }
        if (!store.importBackup(f.readText())) { toast("That file isn't a Vance TV backup"); return }
        accentIdx = store.int("accent", 0); bgIdx = store.int("bg", 0); cardIdx = store.int("cards", 1)
        textIdx = store.int("text", 1); animIdx = store.int("anim", 0); startIdx = store.int("start", 1)
        sortAz = store.flag("sortAz", false); showHidden = store.flag("showHidden", false)
        lockAdult = store.flag("lockAdult", true); autoNext = store.flag("autoNext", true)
        matchFps = store.flag("matchFps", true); liveTs = store.flag("liveTs", false)
        bufferMode = store.int("buffer", 1); resizeIdx = store.int("resize", 0).coerceIn(0, RESIZES.lastIndex)
        subSize = store.int("subSize", 1); sortMode = store.int("sortMode", 0)
        applyNow()
        switchProfile(activeProfile)
        toast("Settings restored")
    }

    // ---- derived lists
    val cats = remember(catCache[tab], hiddenCats, showHidden, renames, catOrder, sortAz, tab) { orderedCats(tab) }
    val kindNow = tab.kind() ?: Kind.LIVE
    val regionTabs = remember(cats, pinnedCats, kindNow) {
        val byRegion = cats.groupingBy { regionOf(it.name) }.eachCount().toList()
            .sortedWith(compareBy({ it.first == "Other" }, { -it.second }))
        buildList {
            add("*all" to "All")
            if (cats.any { catKey(kindNow, it.id) in pinnedCats }) add("*pin" to "★")
            if (byRegion.size > 1) byRegion.forEach { add(it.first to it.first) }
        }
    }
    val panelRows = remember(
        cats, catFilter, catRegion, counts, pinnedCats, renames, hiddenCats, lockedCats, unlocked, hasPin, lockAdult, tab,
    ) {
        val kind = tab.kind()
        if (kind == null) emptyList<CatRow>() else cats.filter { c ->
            val k = catKey(kind, c.id)
            (catFilter.isBlank() || catName(kind, c).contains(catFilter, ignoreCase = true)) &&
                when (catRegion) { "*all" -> true; "*pin" -> k in pinnedCats; else -> regionOf(c.name) == catRegion }
        }.map { c ->
            val k = catKey(kind, c.id)
            CatRow(c, catName(kind, c), counts[kind]?.get(c.id), k in pinnedCats, catLocked(kind, c), k in hiddenCats)
        }
    }
    val favoriteEntries = remember(favorites) { favorites.mapNotNull(::decodeEntry) }
    val recentEntries = remember(history) { history.mapNotNull(::decodeEntry) }
    val searching = tab == Tab.LIVE && query.length >= 2
    val baseEntries = if (tab == Tab.FAVORITES) favoriteEntries else entries
    val shownEntries = remember(
        baseEntries, query, hiddenEntries, showHidden, renames, sortAz, unlocked, hasPin, lockAdult, lockedCats, searching, allLive, tab, sortMode,
    ) {
        val source = if (searching) allLive.orEmpty() else baseEntries
        val filtered = source.filter { visible(it) && nameOf(it).contains(query, ignoreCase = true) }
            .let { if (searching) it.take(300) else it }
        val vod = tab == Tab.MOVIES || tab == Tab.SERIES
        val sorted = if (!vod) filtered else when (sortMode) {
            1 -> filtered.sortedBy { nameOf(it).lowercase() }
            2 -> filtered.sortedByDescending { it.added }
            3 -> filtered.sortedByDescending { it.rating }
            else -> filtered
        }
        if (sortMode == 0 && sortAz && tab.browsable() && !searching) sorted.sortedBy { nameOf(it).lowercase() } else sorted
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
        if (e.isLiveNow) {
            store.setString("last", e.encode())
            if (liveHistory.lastOrNull()?.key() != e.key()) {
                liveHistory += e
                if (liveHistory.size > 10) liveHistory.removeAt(0)
            }
        }
        if (!e.isCatchUp) pushHistory(e)
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
        epg = if (e != null && p != null && e.isLiveNow) {
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
    LaunchedEffect(player, matchFps) {
        player.setVideoChangeFrameRateStrategy(
            if (matchFps) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        )
    }
    LaunchedEffect(provider, liveTs) { provider?.liveFormat = if (liveTs) "ts" else "m3u8" }
    LaunchedEffect(sleepAt) {
        if (sleepAt <= 0L) return@LaunchedEffect
        delay((sleepAt - System.currentTimeMillis()).coerceAtLeast(0))
        player.pause(); fullscreen = false; sleepAt = 0L; sleepIdx = 0
        toast("Sleep timer: playback paused")
    }
    // Episode finished: forget the resume spot and offer the next one with a countdown.
    LaunchedEffect(endedTick) {
        if (endedTick == 0) return@LaunchedEffect
        val e = current ?: return@LaunchedEffect
        val p = provider ?: return@LaunchedEffect
        store.remove(posKey(e))
        if (e.kind != Kind.EPISODE || !autoNext) return@LaunchedEffect
        val next = runCatching { p.nextEpisode(e) }.getOrNull() ?: return@LaunchedEffect
        nextUp = next; nextUpGo = false
        var secs = 10
        while (secs > 0 && nextUp != null && !nextUpGo) { nextUpSecs = secs; delay(1000); secs-- }
        if (nextUp != null) { nextUp = null; playNow(next, true) }
    }
    LaunchedEffect(searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        val p = provider ?: return@LaunchedEffect
        runCatching { listOf(Tab.LIVE, Tab.MOVIES, Tab.SERIES).forEach { ensureCategories(p, it) } }
    }
    LaunchedEffect(searchQuery, searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        delay(150)
        searchResults = withContext(Dispatchers.IO) { runCatching { index.search(searchQuery) }.getOrDefault(emptyMap()) }
    }
    LaunchedEffect(Unit) { delay(6000); checkUpdates(true) }
    // Type your login on a phone: a tiny web page on the TV, shown as a QR code while the login screen is open.
    val loginVisible = provider == null || showSetup
    DisposableEffect(loginVisible) {
        if (!loginVisible) return@DisposableEffect onDispose { }
        val code = (1000..9999).random().toString()
        val pairSrv = PairingServer(code) { data ->
            scope.launch {
                val url = data["url"].orEmpty().trim()
                if (url.isNotEmpty()) { urlText = url; useUrl = true } else {
                    server = data["server"].orEmpty().trim(); user = data["user"].orEmpty().trim()
                    pass = data["pass"].orEmpty(); useUrl = false
                }
                connect()
            }
        }
        val port = pairSrv.start()
        pairInfo = if (port > 0) localIpAddress()?.let { PairInfo("http://$it:$port", code) } else null
        onDispose { pairSrv.stop(); pairInfo = null }
    }
    LaunchedEffect(entries, tab, wantGuide) {
        if (wantGuide && tab == Tab.LIVE && entries.isNotEmpty()) { wantGuide = false; guideOpen = true }
    }

    val firstItem = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val catListState = rememberLazyListState()
    LaunchedEffect(tab) { catListState.scrollToItem(0) }
    LaunchedEffect(tab, provider) { tab.kind()?.let { loadCounts(it) } }
    LaunchedEffect(pickerOpen) {
        if (!pickerOpen) return@LaunchedEffect
        val p = provider ?: return@LaunchedEffect
        runCatching { listOf(Tab.LIVE, Tab.MOVIES, Tab.SERIES).forEach { ensureCategories(p, it) } }
        loadCounts(Kind.LIVE); loadCounts(Kind.MOVIE); loadCounts(Kind.SERIES)
    }
    LaunchedEffect(startTick) {
        if (startTick == 0) return@LaunchedEffect
        when (startIdx) {
            1 -> openTab(Tab.LIVE)
            2 -> { openTab(Tab.LIVE); current?.takeIf { it.kind == Kind.LIVE }?.let { playNow(it, true) } }
        }
    }
    LaunchedEffect(panelOpen, panelMode, panelChannels) {
        if (panelOpen) {
            repeat(15) { delay(80); if (runCatching { panelFocus.requestFocus() }.isSuccess) return@LaunchedEffect }
        } else if (fullscreen) {
            delay(50); runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(fullscreen) { if (!fullscreen) panelOpen = false }
    // Scroll positions live here so they survive the screen being swapped for an overlay.
    val homeState = rememberLazyListState()
    val liveState = rememberLazyListState()
    val moviesGrid = rememberLazyGridState()
    val seriesGrid = rememberLazyGridState()
    val favGrid = rememberLazyGridState()
    LaunchedEffect(tab, category?.id) {
        liveState.scrollToItem(0); moviesGrid.scrollToItem(0); seriesGrid.scrollToItem(0); favGrid.scrollToItem(0)
    }
    // Put focus on the first item when a screen comes up; its node may not exist yet, so retry briefly.
    LaunchedEffect(listVersion) {
        if (!isTv) return@LaunchedEffect
        repeat(20) {
            delay(80)
            if (runCatching { firstItem.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    LaunchedEffect(fullscreen) { if (fullscreen) { delay(50); runCatching { rootFocus.requestFocus() } } }
    val liveFocusIdx = shownEntries.indexOfFirst { it.key() == current?.key() }.coerceAtLeast(0)

    val overlayActive = detailEntry != null || guideOpen || (fullscreen && current != null) || pickerOpen || appearanceOpen || searchOpen
    BackHandler(enabled = showSetup && provider != null) { showSetup = false }
    BackHandler(enabled = provider != null && !showSetup && tab != Tab.HOME && !overlayActive) { openTab(Tab.HOME) }
    BackHandler(enabled = detailEntry != null) { closeDetail() }
    BackHandler(enabled = fullscreen) { fullscreen = false; listVersion++ }
    BackHandler(enabled = guideOpen) { guideOpen = false; listVersion++ }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = nextUp != null) { nextUp = null }
    BackHandler(enabled = searchOpen) { searchOpen = false; listVersion++ }
    BackHandler(enabled = pickerOpen) { store.setString("picked", "1"); pickerOpen = false; listVersion++ }
    BackHandler(enabled = appearanceOpen) { appearanceOpen = false; listVersion++ }

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
                    if (e.kind == Kind.MOVIE || e.kind == Kind.SERIES) {
                        add(MenuItem(if (k in myKeys) "Remove from My list" else "Add to My list") { toggleMyList(e) })
                    }
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
                    add(MenuItem(if (k in pinnedCats) "Unpin from top" else "Pin to top") { togglePinnedCat(k) })
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
        val curProg = nowNext(epg, now).first
        val behind = if (player.currentLiveOffset != C.TIME_UNSET) player.currentLiveOffset else 0L
        ActionMenuDialog(
            "Player options",
            buildList {
                add(MenuItem(streamDetails(player), enabled = false) {})
                add(MenuItem(audioLabel(player), close = false) { cycleAudio(player); tracksTick++ })
                add(MenuItem(textLabel(player), close = false) { cycleText(player); tracksTick++ })
                add(MenuItem("Picture: ${RESIZES[resizeIdx].second}", close = false) {
                    resizeIdx = (resizeIdx + 1) % RESIZES.size
                    store.setInt("resize", resizeIdx)
                })
                if (e != null && e.isLiveNow) {
                    if (behind > 3000) add(MenuItem("Jump to live") { player.seekToDefaultPosition() })
                    if (curProg?.archive == true) {
                        add(MenuItem("↺ Restart “${curProg.title}” (catch-up)") { playNow(catchUpEntry(e, curProg), true) })
                    }
                    add(MenuItem("Stream format: ${if (liveTs) "TS" else "HLS"}  (switch if a channel won't play)", close = false) {
                        liveTs = !liveTs; store.setFlag("liveTs", liveTs); reloadTick++
                    })
                }
                add(MenuItem("Sleep timer: ${if (SLEEP_STEPS[sleepIdx] == 0) "off" else "${SLEEP_STEPS[sleepIdx]} min"}", close = false) {
                    sleepIdx = (sleepIdx + 1) % SLEEP_STEPS.size
                    sleepAt = if (sleepIdx == 0) 0L else System.currentTimeMillis() + SLEEP_STEPS[sleepIdx] * 60_000L
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
                add(MenuItem("Account & subscription…") { account = null; accountOpen = true; loadAccount() })
                add(MenuItem("Check for updates…") { updateOpen = true; checkUpdates(false) })
                add(MenuItem("Appearance…") { appearanceOpen = true })
                add(MenuItem("Choose categories…") { pickerTab = 0; pickerOpen = true })
                add(MenuItem("Match frame rate: ${if (matchFps) "on" else "off"}", close = false) {
                    matchFps = !matchFps; store.setFlag("matchFps", matchFps)
                })
                add(MenuItem("Auto-play next episode: ${if (autoNext) "on" else "off"}", close = false) {
                    autoNext = !autoNext; store.setFlag("autoNext", autoNext)
                })
                add(MenuItem("Subtitle size: ${SUB_NAMES[subSize]}", close = false) {
                    subSize = (subSize + 1) % SUB_NAMES.size; store.setInt("subSize", subSize)
                })
                add(MenuItem("Live stream format: ${if (liveTs) "TS" else "HLS (m3u8)"}", close = false) {
                    liveTs = !liveTs; store.setFlag("liveTs", liveTs)
                })
                add(MenuItem("Rebuild search index") { provider?.let { rebuildIndex(it, true) } })
                add(MenuItem("Back up settings to a file") { backup() })
                add(MenuItem("Restore settings from backup") { restore() })
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
                add(MenuItem("Vance TV ${BuildConfig.VERSION_NAME}", enabled = false) {})
                add(MenuItem("Close") {})
            },
        ) { showSettings = false }
    }

    if (accountOpen) {
        val a = account
        ActionMenuDialog(
            "Account & subscription",
            buildList {
                if (a == null) {
                    add(MenuItem(if (provider?.supportsVod == true) "Loading…" else "Not available for plain playlists", enabled = false) {})
                } else {
                    val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    add(MenuItem("Status: ${a.status}${if (a.trial) " (trial)" else ""}", enabled = false) {})
                    val ex = a.expires
                    if (ex != null) {
                        val days = ((ex - System.currentTimeMillis()) / 86_400_000L).toInt()
                        add(MenuItem("Expires: ${df.format(Date(ex))}  (${if (days >= 0) "in $days days" else "expired"})", enabled = false) {})
                    } else add(MenuItem("Expires: no date reported", enabled = false) {})
                    add(MenuItem("Connections: ${a.activeConnections} of ${a.maxConnections} in use", enabled = false) {})
                    a.created?.let { add(MenuItem("Account created: ${df.format(Date(it))}", enabled = false) {}) }
                    if (a.timezone.isNotBlank()) add(MenuItem("Server time zone: ${a.timezone}", enabled = false) {})
                }
                add(MenuItem("Refresh", close = false) { account = null; loadAccount() })
                add(MenuItem("Close") {})
            },
        ) { accountOpen = false }
    }

    if (updateOpen) {
        val st = updateState
        ActionMenuDialog(
            "Updates",
            buildList {
                add(MenuItem("Installed: ${BuildConfig.VERSION_NAME}", enabled = false) {})
                when (st) {
                    UpdateState.Idle -> add(MenuItem("Check now", close = false) { checkUpdates(false) })
                    UpdateState.Checking -> add(MenuItem("Checking…", enabled = false) {})
                    UpdateState.UpToDate -> add(MenuItem("You're up to date", enabled = false) {})
                    is UpdateState.Available -> {
                        add(MenuItem("Update available: build ${st.release.build}", enabled = false) {})
                        add(MenuItem("Download and install", close = false) { downloadUpdate(st.release) })
                        add(MenuItem("Not now") { store.setString("updSkip", st.release.build.toString()) })
                    }
                    is UpdateState.Downloading -> add(MenuItem("Downloading…  ${st.percent}%", enabled = false) {})
                    is UpdateState.Failed -> {
                        add(MenuItem(st.message, enabled = false) {})
                        add(MenuItem("Try again", close = false) { checkUpdates(false) })
                    }
                }
                add(MenuItem("Close") {})
            },
        ) { updateOpen = false }
    }

    // ---- reusable pieces of the main UI
    val isLive = current?.isLiveNow == true
    val isCatchUp = current?.isCatchUp == true
    val resizeMode = RESIZES[resizeIdx].first

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
                if (panelOpen) {
                    return@onPreviewKeyEvent when (ev.key) {
                        Key.DirectionRight -> { if (panelMode == 1) panelMode = 0 else panelOpen = false; true }
                        Key.DirectionLeft -> if (panelMode == 0) { panelMode = 1; true } else false
                        else -> false // Up/Down/OK go to the focused row in the panel
                    }
                }
                if (nextUp != null && (ev.key == Key.DirectionCenter || ev.key == Key.Enter)) {
                    nextUpGo = true
                    return@onPreviewKeyEvent true
                }
                val digit = DIGITS.indexOf(ev.key).takeIf { it >= 0 } ?: PAD_DIGITS.indexOf(ev.key)
                if (digit >= 0 && isLive) {
                    numBuffer = (numBuffer + digit).takeLast(5)
                    return@onPreviewKeyEvent true
                }
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> if (isLive) { step(-1); true } else false
                    Key.DirectionDown, Key.ChannelDown -> if (isLive) { step(1); true } else false
                    Key.DirectionLeft -> if (isLive) { openPanel(); true } else {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> if (!isLive) { player.seekTo(player.currentPosition + 10_000); true } else false
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.MediaRewind -> {
                        if (isLive && !player.isCurrentMediaItemSeekable) toast("This stream can't be rewound")
                        else player.seekTo((player.currentPosition - if (isLive) 15_000 else 30_000).coerceAtLeast(0))
                        true
                    }
                    Key.MediaFastForward -> {
                        if (isLive) player.seekToDefaultPosition() else player.seekTo(player.currentPosition + 30_000)
                        true
                    }
                    Key.Menu -> { showOptions = true; true }
                    else -> false
                }
            }
            .then(if (fullscreen) Modifier.focusRequester(rootFocus).focusable() else Modifier)
    ) {
        if (provider == null || showSetup) {
            LoginScreen(
                useUrl, urlText, server, user, pass, showPass, status, busy,
                canCancel = provider != null, pair = pairInfo,
                onUrl = { urlText = it }, onServer = { server = it }, onUser = { user = it }, onPass = { pass = it },
                onTogglePass = { showPass = !showPass }, onToggleMode = { useUrl = !useUrl },
                onConnect = ::connect, onCancel = { showSetup = false },
            )
        } else if (searchOpen) {
            SearchScreen(
                query = searchQuery, onQuery = { searchQuery = it },
                results = searchResults.mapValues { (_, list) -> list.filter { !entryLocked(it) } },
                status = indexStatus, nameOf = ::nameOf,
                onOpen = { e ->
                    searchOpen = false
                    if (e.kind == Kind.LIVE) playNow(e, true) else onEntry(e)
                },
            )
        } else if (pickerOpen) {
            val vod = provider?.supportsVod == true
            val tabs = if (vod) listOf(Tab.LIVE, Tab.MOVIES, Tab.SERIES) else listOf(Tab.LIVE)
            val kinds = if (vod) listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES) else listOf(Kind.LIVE)
            val ix = pickerTab.coerceIn(0, tabs.lastIndex)
            val pt = tabs[ix]
            val pk = kinds[ix]
            val pcats = catCache[pt].orEmpty().map { c ->
                PickerCat(c, regionOf(c.name), counts[pk]?.get(c.id), catKey(pk, c.id) !in hiddenCats)
            }
            CategoryPicker(
                tabLabels = tabs.map { t ->
                    (if (t == Tab.LIVE) "Live" else if (t == Tab.MOVIES) "Movies" else "Series") + "  ·  " + (catCache[t]?.size ?: "…")
                },
                tab = ix, onTab = { pickerTab = it }, cats = pcats,
                onToggle = { setHiddenMany(pk, listOf(it.cat.id), it.checked) },
                onToggleRegion = { r, check -> setHiddenMany(pk, pcats.filter { c -> c.region == r }.map { c -> c.cat.id }, !check) },
                onQuick = { which ->
                    when (which) {
                        0 -> setHiddenMany(pk, pcats.filter { isAdult(it.cat.name) }.map { it.cat.id }, true)
                        1 -> setHiddenMany(pk, pcats.map { it.cat.id }, false)
                        else -> setHiddenMany(pk, pcats.map { it.cat.id }, true)
                    }
                },
                onDone = { store.setString("picked", "1"); pickerOpen = false; listVersion++ },
                onSkip = { store.setString("picked", "1"); pickerOpen = false; listVersion++ },
            )
        } else if (appearanceOpen) {
            AppearanceScreen(
                accentIdx, bgIdx, cardIdx, textIdx, animIdx, startIdx,
                onAccent = { accentIdx = it; store.setInt("accent", it); applyNow() },
                onBg = { bgIdx = it; store.setInt("bg", it); applyNow() },
                onCards = { cardIdx = it; store.setInt("cards", it); applyNow() },
                onText = { textIdx = it; store.setInt("text", it); applyNow() },
                onAnim = { animIdx = it; store.setInt("anim", it); applyNow() },
                onStart = { startIdx = it; store.setInt("start", it) },
                onClose = { appearanceOpen = false; listVersion++ },
            )
        } else if (fullscreen && current != null) {
                val e = current!!
                val behindLive = if (isLive && player.currentLiveOffset != C.TIME_UNSET) (player.currentLiveOffset / 1000).toInt() else 0
                val sleepLeft = if (sleepAt > 0) ((sleepAt - now) / 60_000 + 1).toInt().coerceAtLeast(1) else 0
                PlayerSurface(player, false, resizeMode, Modifier.fillMaxSize().background(Color.Black), SUB_SCALES[subSize])
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
                    if (isLive) "Up/Down: channel  ·  Left: channel list  ·  ⏪ ⏩ rewind / live  ·  Menu: options"
                    else "Left/Right: seek 10s  ·  OK: pause  ·  Menu: options",
                    catchUp = isCatchUp, badge = streamBadge(player), behindLiveSec = behindLive, sleepLeftMin = sleepLeft,
                )
                nextUp?.let { NextUpCard(it.title, nextUpSecs) }
                ChannelPanel(
                    visible = panelOpen && isLive, mode = panelMode,
                    title = if (panelMode == 0) (panelCat?.let { catName(Kind.LIVE, it) }?.ifBlank { null } ?: "Channels") else "Categories",
                    channels = panelChannels.filter { visible(it) },
                    categories = orderedCats(Tab.LIVE).filter { !catLocked(Kind.LIVE, it) }.map { it to catName(Kind.LIVE, it) },
                    currentKey = current?.key(), currentCatId = panelCat?.id ?: current?.cat,
                    lastChannel = if (liveHistory.size >= 2) liveHistory[liveHistory.size - 2] else null,
                    now = now, nameOf = ::nameOf, isFav = ::isFav,
                    programmesOf = { e -> guide?.cache?.get(e.id).orEmpty() },
                    loadEpg = { e -> guide?.load(e) },
                    focus = panelFocus,
                    onChannel = { e -> current = e; panelOpen = false },
                    onCategory = { c -> panelPick(c) },
                    onLast = { lastChannel(); panelOpen = false },
                )
        } else if (guideOpen && guide != null) {
                GuideScreen(
                    channels = guideChannels, nameOf = ::nameOf, guide = guide, now = now,
                    onPlay = { ch -> playNow(ch, true); guideOpen = false },
                    onCatchUp = { ch, prog -> playCatchUp(ch, prog); if (prog.archive) guideOpen = false },
                    onClose = { guideOpen = false; listVersion++ },
                )
        } else if (detailEntry != null) {
            val d = detailEntry!!
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
                        inMyList = d.key() in myKeys,
                        onMyList = { toggleMyList(d) },
                        onTrailer = { openTrailer(detail?.trailer) },
                        onEpisode = { playNow(it, true) },
                    )
                }
        } else {
            Row(Modifier.fillMaxSize()) {
                IconRail(
                    items = listOf(
                        RailItem(RailIconType.HOME, "Home", tab == Tab.HOME),
                        RailItem(RailIconType.SEARCH, "Search", false),
                        RailItem(RailIconType.LIVE, "Live TV", tab == Tab.LIVE),
                        RailItem(RailIconType.MOVIES, "Movies", tab == Tab.MOVIES),
                        RailItem(RailIconType.SERIES, "Series", tab == Tab.SERIES),
                        RailItem(RailIconType.FAVORITES, "Favorites", tab == Tab.FAVORITES),
                        RailItem(RailIconType.GUIDE, "Guide", false),
                        RailItem(RailIconType.SETTINGS, "Settings", false),
                    ),
                    modifier = Modifier.width(if (isTv) 84.dp else 66.dp),
                ) { icon ->
                    when (icon) {
                        RailIconType.HOME -> openTab(Tab.HOME)
                        RailIconType.SEARCH -> { searchQuery = ""; searchResults = emptyMap(); searchOpen = true }
                        RailIconType.LIVE -> openTab(Tab.LIVE)
                        RailIconType.MOVIES -> openTab(Tab.MOVIES)
                        RailIconType.SERIES -> openTab(Tab.SERIES)
                        RailIconType.FAVORITES -> openTab(Tab.FAVORITES)
                        RailIconType.GUIDE ->
                            if (guideChannels.isNotEmpty()) guideOpen = true else { openTab(Tab.LIVE); wantGuide = true }
                        RailIconType.SETTINGS -> openSettings()
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
                                val mine = myList.mapNotNull(::decodeEntry).filter { visible(it) }.take(20)
                                if (mine.isNotEmpty()) add(Triple("My list", mine, false))
                                homeShelves.forEach { add(Triple(it.title, it.items.filter { e -> visible(e) }, false)) }
                            }
                            LazyColumn(Modifier.fillMaxSize(), state = homeState, contentPadding = PaddingValues(bottom = 40.dp)) {
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

                        Tab.LIVE -> Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            CategoryPanel(
                                Modifier.width(if (isTv) 230.dp else 150.dp).fillMaxHeight(),
                                catFilter, { catFilter = it }, regionTabs, catRegion, { catRegion = it },
                                if (catRegion == "*all" && catFilter.isBlank()) panelRows.filter { it.pinned } else emptyList(),
                                panelRows,
                                "ALL  ·  ${panelRows.size} of ${cats.size}", category?.id,
                                onSelect = { openCategory(Kind.LIVE, it.cat) },
                                onMenu = { menuTarget = MenuTarget.CatT(Kind.LIVE, it.cat) },
                                listState = catListState,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                                BrowseHeader(
                                    category?.let { catName(Kind.LIVE, it) } ?: "Live TV", query, { query = it },
                                    "Search all channels", searchWidth = 170.dp,
                                ) {
                                    if (guideChannels.isNotEmpty()) ActionButton("Guide", primary = false) { guideOpen = true }
                                }
                                Text(
                                    if (busy) "Loading…" else status, color = Palette.muted,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                                )
                                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = liveState) {
                                    itemsIndexed(shownEntries) { i, e ->
                                        LaunchedEffect(e.id) { guide?.load(e) }
                                        val (cur, _) = nowNext(guide?.cache?.get(e.id).orEmpty(), now)
                                        ChannelRow(
                                            nameOf(e), e.icon, e.num, isFav(e), e.key() == current?.key(),
                                            cur?.title,
                                            cur?.let { (now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                                            if (i == liveFocusIdx) Modifier.focusRequester(firstItem) else Modifier,
                                            onLongClick = { menuTarget = MenuTarget.EntryT(e) },
                                        ) { onEntry(e) }
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).fillMaxHeight()) { previewPane() }
                        }

                        Tab.MOVIES, Tab.SERIES -> Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            val kind = tab.kind() ?: Kind.MOVIE
                            CategoryPanel(
                                Modifier.width(if (isTv) 230.dp else 150.dp).fillMaxHeight(),
                                catFilter, { catFilter = it }, regionTabs, catRegion, { catRegion = it },
                                if (catRegion == "*all" && catFilter.isBlank()) panelRows.filter { it.pinned } else emptyList(),
                                panelRows,
                                "ALL  ·  ${panelRows.size} of ${cats.size}", category?.id,
                                onSelect = { openCategory(kind, it.cat) },
                                onMenu = { menuTarget = MenuTarget.CatT(kind, it.cat) },
                                listState = catListState,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                BrowseHeader(
                                    category?.let { catName(kind, it) } ?: (if (tab == Tab.MOVIES) "Movies" else "Series"),
                                    query, { query = it },
                                ) {
                                    ActionButton("Sort: ${SORT_NAMES[sortMode]}", primary = false) {
                                        sortMode = (sortMode + 1) % SORT_NAMES.size; store.setInt("sortMode", sortMode)
                                    }
                                }
                                if (provider?.supportsVod == false) {
                                    Text("Movies and series need an Xtream-style login (server, username, password).", color = Palette.muted)
                                } else if (busy && shownEntries.isEmpty()) {
                                    Text("Loading…", color = Palette.muted)
                                }
                                LazyVerticalGrid(
                                    GridCells.Adaptive(Dimens.posterWidth), Modifier.fillMaxSize(),
                                    state = if (tab == Tab.MOVIES) moviesGrid else seriesGrid,
                                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        }

                        Tab.FAVORITES -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (favTab == 0) "Favorites" else "My list", Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                )
                                Chip("★ Favorites", favTab == 0) { favTab = 0 }
                                Spacer(Modifier.width(8.dp))
                                Chip("My list", favTab == 1) { favTab = 1 }
                            }
                            val favs = (if (favTab == 0) favoriteEntries else myList.mapNotNull(::decodeEntry)).filter { visible(it) }
                            if (favs.isEmpty()) {
                                Text("Hold OK on any channel, movie or series to add it here.", Modifier.padding(top = 16.dp), color = Palette.muted)
                            }
                            LazyVerticalGrid(
                                GridCells.Adaptive(Dimens.favWidth), Modifier.fillMaxSize(),
                                state = favGrid,
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

        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
