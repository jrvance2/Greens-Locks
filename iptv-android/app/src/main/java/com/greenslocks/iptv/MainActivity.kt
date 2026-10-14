@file:OptIn(UnstableApi::class)

package com.greenslocks.iptv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.os.Build
import android.media.AudioFormat
import androidx.compose.runtime.mutableStateOf
import android.util.Rational
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
        intent.getStringExtra("tmdb_key")?.let { store.setGString("tmdbKey", it) }
        intent.getStringExtra("xmltv_url")?.let { store.setString("xmltvUrl", it); store.setString("xmltvAt", "0") }
        applyAppearance(store.int("accent", 0), store.int("bg", 0), store.int("cards", 1), store.int("text", 1), store.int("anim", 0), store.int("ui", 1))
        LayoutPrefs.load(store)
        Net.setMode(store.int("uaMode", 0))
        pendingLaunch.value = intent.getStringExtra("play_entry")
        setContent { IptvTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingLaunch.value = intent.getStringExtra("play_entry")
    }

    override fun onStop() {
        super.onStop()
        WatchNext.publish(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.ready) enterPip()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }
}

/** True while a video is fullscreen, so leaving the app shrinks it to a floating window. */
private val pendingLaunch = mutableStateOf<String?>(null)

private object PipState { @Volatile var ready = false }

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
    data class GroupT(val id: String) : MenuTarget
    data class AddToGroupT(val entry: Entry) : MenuTarget
}

private val RESIZES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)
private val LANG_NAMES = listOf("Stream default", "English", "Spanish", "French", "German", "Portuguese", "Italian", "Arabic", "Hindi", "Russian", "Turkish", "Polish")
private val LANG_CODES = listOf<String?>(null, "en", "es", "fr", "de", "pt", "it", "ar", "hi", "ru", "tr", "pl")
private val PAD_START = listOf(0, 1, 2, 5)
private val PAD_END = listOf(0, 2, 5, 10, 15)
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
        factory = { PlayerView(it).apply { setKeepContentOnPlayerReset(true) } },
        update = {
            if (it.player !== player) it.player = player
            it.useController = showController
            it.resizeMode = resizeMode
            it.setKeepContentOnPlayerReset(Dimens.keepFrame)
            it.subtitleView?.apply {
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subScale)
                setStyle(
                    CaptionStyleCompat(
                        intArrayOf(android.graphics.Color.WHITE, 0xFFFFE066.toInt(), 0xFF7DE8FF.toInt())[Dimens.subColor],
                        intArrayOf(android.graphics.Color.TRANSPARENT, 0x99000000.toInt(), 0xFF000000.toInt())[Dimens.subBg],
                        android.graphics.Color.TRANSPARENT,
                        intArrayOf(CaptionStyleCompat.EDGE_TYPE_NONE, CaptionStyleCompat.EDGE_TYPE_OUTLINE, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW)[Dimens.subEdge],
                        android.graphics.Color.BLACK, null,
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
    val scope = rememberCoroutineScope()
    val state = remember { AppState(ctx, scope) }
    state.AppBody()
}

/** All of the app's screen state in one object, so the main composable stays under the JVM method size limit. */
private class AppState(val ctx: Context, val scope: kotlinx.coroutines.CoroutineScope) {
    val prefs: android.content.SharedPreferences = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    val store = Store(prefs)
    init {
        if (!store.flag("quietHomeV1", false)) {
            store.setInt("start", 0)
            store.setFlag("autoplayLaunch", false)
            store.setFlag("rotateHomeBanner", false)
            store.setFlag("quietHomeV1", true)
        }
    }
    val isTv: Boolean =
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    var profiles by mutableStateOf(store.profiles())
    var activeProfile by mutableIntStateOf(store.active)
    var showWho by mutableStateOf(Dimens.askProfile && store.profiles().size > 1)
    var urlText by mutableStateOf(store.url())
    val initial = xtreamFromUrl(urlText)
    var server by mutableStateOf(initial?.base.orEmpty())
    var user by mutableStateOf(initial?.user.orEmpty())
    var pass by mutableStateOf(initial?.pass.orEmpty())
    var useUrl by mutableStateOf(urlText.isNotBlank() && initial == null)
    var showPass by mutableStateOf(false)
    var provider by mutableStateOf<Provider?>(null)
    var showSetup by mutableStateOf(true)
    var status by mutableStateOf("Enter your login and press Connect")
    var busy by mutableStateOf(false)
    var tab by mutableStateOf(Tab.HOME)
    val catCache = mutableStateMapOf<Tab, List<Category>>()
    var category by mutableStateOf<Category?>(null)
    var entries by mutableStateOf(emptyList<Entry>())
    var series by mutableStateOf<Entry?>(null)
    var episodes by mutableStateOf(emptyList<Entry>())
    var query by mutableStateOf("")
    var listVersion by mutableIntStateOf(0)
    var allLive by mutableStateOf<List<Entry>?>(null)
    var detailEntry by mutableStateOf<Entry?>(null)
    var detail by mutableStateOf<Detail?>(null)
    var homeShelves by mutableStateOf(emptyList<Shelf>())
    var wantGuide by mutableStateOf(false)
    var pinnedCats by mutableStateOf(store.list("pinCats").toSet())
    var counts by mutableStateOf(emptyMap<Kind, Map<String, Int>>())
    val countsLoading = mutableSetOf<Kind>()
    var catFilter by mutableStateOf("")
    var catRegion by mutableStateOf("*all")
    var pickerOpen by mutableStateOf(false)
    var pickerTab by mutableIntStateOf(0)
    var appearanceOpen by mutableStateOf(false)
    var accentIdx by mutableIntStateOf(store.int("accent", 0))
    var bgIdx by mutableIntStateOf(store.int("bg", 0))
    var cardIdx by mutableIntStateOf(store.int("cards", 1))
    var textIdx by mutableIntStateOf(store.int("text", 1))
    var uiIdx by mutableIntStateOf(store.int("ui", 1))
    var animIdx by mutableIntStateOf(store.int("anim", 0))
    var startIdx by mutableIntStateOf(store.int("start", 0).coerceIn(0, START_NAMES.lastIndex))
    var autoplayLaunch by mutableStateOf(store.flag("autoplayLaunch", false))
    var rotateHomeBanner by mutableStateOf(store.flag("rotateHomeBanner", false))
    var startTick by mutableIntStateOf(0)
    var panelOpen by mutableStateOf(false)
    var panelMode by mutableIntStateOf(0)
    var panelCat by mutableStateOf<Category?>(null)
    var panelChannels by mutableStateOf(emptyList<Entry>())
    var autoNext by mutableStateOf(store.flag("autoNext", true))
    var matchFps by mutableStateOf(store.flag("matchFps", true))
    var subSize by mutableIntStateOf(store.int("subSize", 1))
    var liveTs by mutableStateOf(store.flag("liveTs", false))
    var sortMode by mutableIntStateOf(store.int("sortMode", 0))
    var myList by mutableStateOf(store.list("mylist"))
    var groups by mutableStateOf(store.map("groups"))
    var reminders by mutableStateOf(store.list("reminders"))
    var dueReminder by mutableStateOf<Reminder?>(null)
    var remindersOpen by mutableStateOf(false)
    var kidsMode by mutableStateOf(store.isKids(store.active))
    var kidsCats by mutableStateOf(store.list("kidsCats").toSet())
    var pickerKids by mutableStateOf(false)
    var epgShift by mutableIntStateOf(store.int("epgShift", 0))
    var uaMode by mutableIntStateOf(store.int("uaMode", 0))
    var audioMode by mutableIntStateOf(store.int("audioMode", if (store.flag("pcmAudio", false)) 2 else 0))
    var xmltvUrl by mutableStateOf(store.string("xmltvUrl").orEmpty())
    var xmltvFirst by mutableStateOf(store.flag("xmltvFirst", false))
    var xmltvTick by mutableIntStateOf(0)
    var xmltvBusy by mutableStateOf(false)
    var chShifts by mutableStateOf(store.map("chShift"))
    var seriesRules by mutableStateOf(store.list("seriesRules"))
    var seriesOpen by mutableStateOf(false)
    var audioLang by mutableIntStateOf(store.int("audioLang", 0))
    var subLang by mutableIntStateOf(store.int("subLang", 0))
    var layoutTick by mutableIntStateOf(0)
    var trackPanelOpen by mutableStateOf(false)
    var trackTick by mutableIntStateOf(0)
    val trackFocus = FocusRequester()
    var zapStrip by mutableStateOf(store.flag("zapStrip", false))
    var stripIdx by mutableStateOf<Int?>(null)
    var stripTick by mutableIntStateOf(0)
    var bootDone by mutableStateOf(false)
    var sawBusy by mutableStateOf(false)
    var focusedHome by mutableStateOf<Entry?>(null)
    var pendingHome by mutableStateOf<Entry?>(null)
    var shownScreen by mutableIntStateOf(-1)
    val screenAlpha = Animatable(0f)
    var homeFilter by mutableIntStateOf(0)
    var heroBackdrop by mutableStateOf("")
    var mergeDupes by mutableStateOf(store.flag("mergeDupes", true))
    var recOpen by mutableStateOf(false)
    var recFile by mutableStateOf<java.io.File?>(null)
    var programTarget by mutableStateOf<Pair<Entry, Programme>?>(null)
    var multiList by mutableStateOf(emptyList<Entry>())
    var multiOpen by mutableStateOf(false)
    var kidsLimit by mutableIntStateOf(store.int("kidsLimit", 0))
    val dayKey = { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) }
    var kidsUsed by mutableLongStateOf(store.long("kidsUsed_${dayKey()}"))
    var timesUp by mutableStateOf(false)
    var syncServer by mutableStateOf<SyncServer?>(null)
    var syncInfo by mutableStateOf<PairInfo?>(null)
    var failoverTick by mutableIntStateOf(0)
    var triedTick by mutableIntStateOf(0)
    val triedVariants = mutableSetOf<String>()
    var tmdbKey by mutableStateOf(store.gString("tmdbKey").orEmpty())
    var favTab by mutableIntStateOf(0)
    var sleepIdx by mutableIntStateOf(0)
    var sleepAt by mutableLongStateOf(0L)
    var nextUp by mutableStateOf<Entry?>(null)
    var nextUpSecs by mutableIntStateOf(0)
    var nextUpGo by mutableStateOf(false)
    var endedTick by mutableIntStateOf(0)
    var searchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf(emptyMap<Kind, List<Entry>>())
    var indexStatus by mutableStateOf<String?>(null)
    var accountOpen by mutableStateOf(false)
    var account by mutableStateOf<AccountInfo?>(null)
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
    var updateOpen by mutableStateOf(false)
    var pairInfo by mutableStateOf<PairInfo?>(null)
    var current by mutableStateOf<Entry?>(null)
    var fullscreen by mutableStateOf(false)
    var epg by mutableStateOf(emptyList<Programme>())
    var now by mutableLongStateOf(System.currentTimeMillis())
    var showInfo by mutableStateOf(false)
    var infoTick by mutableIntStateOf(0)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var reloadTick by mutableIntStateOf(0)
    var retries by mutableIntStateOf(0)
    var playbackError by mutableStateOf<String?>(null)
    var tracksTick by mutableIntStateOf(0)
    var numBuffer by mutableStateOf("")
    var pausedForDetail by mutableStateOf(false)
    var pausedByNav by mutableStateOf(false)
    val liveHistory = mutableListOf<Entry>()
    var favorites by mutableStateOf(store.favorites())
    var history by mutableStateOf(store.list("hist"))
    var hiddenCats by mutableStateOf(store.list("hidCats").toSet())
    var hiddenEntries by mutableStateOf(store.list("hidEntries").toSet())
    var lockedCats by mutableStateOf(store.list("lockCats").toSet())
    var renames by mutableStateOf(store.map("renames"))
    var catOrder by mutableStateOf(store.map("catOrder"))
    var sortAz by mutableStateOf(store.flag("sortAz", false))
    var showHidden by mutableStateOf(store.flag("showHidden", false))
    var lockAdult by mutableStateOf(store.flag("lockAdult", true))
    var hasPin by mutableStateOf(store.hasPin())
    var bufferMode by mutableIntStateOf(store.int("buffer", 1))
    var resizeIdx by mutableIntStateOf(store.int("resize", 0).coerceIn(0, RESIZES.lastIndex))
    var unlocked by mutableStateOf(false)
    var menuTarget by mutableStateOf<MenuTarget?>(null)
    var textPrompt by mutableStateOf<TextPrompt?>(null)
    var showSettings by mutableStateOf(false)
    var showOptions by mutableStateOf(false)
    var pinRequest by mutableStateOf<PinRequest?>(null)
    var pinError by mutableStateOf<String?>(null)
    var pinWrongCount by mutableIntStateOf(0)
    var guideOpen by mutableStateOf(false)
    val firstItem = FocusRequester()
    val rootFocus = FocusRequester()
    val panelFocus = FocusRequester()
}

@Composable
private fun AppState.AppBody() {

    // ---- connection / browsing state
    val xmltv = remember(activeProfile) { XmltvStore(ctx, activeProfile) }
    val index = remember(activeProfile) { SearchIndex(ctx, activeProfile) }

    // ---- playback state

    // ---- user data (per profile) and settings

    // ---- dialogs

    // ---- player
    // Smart Auto: if the TV can't take Dolby passthrough but can take multichannel sound, decode 5.1 on the device.
    val smartMode = remember {
        runCatching {
            val caps = AudioCapabilities.getCapabilities(ctx, androidx.media3.common.AudioAttributes.DEFAULT, null)
            when {
                caps.supportsEncoding(AudioFormat.ENCODING_AC3) || caps.supportsEncoding(AudioFormat.ENCODING_E_AC3) -> 0
                caps.maxChannelCount >= 6 -> 3
                else -> 0
            }
        }.getOrDefault(0)
    }
    val effectiveAudio = if (audioMode == 0) smartMode else audioMode
    val player = remember(bufferMode, effectiveAudio, uaMode) {
        Net.setMode(uaMode)
        val lc = when (bufferMode) {
            0 -> DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 20_000, 1_500, 3_000)
            2 -> DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 90_000, 3_000, 6_000)
            else -> DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 50_000, 1_500, 4_000)
        }.build()
        // OkHttp for the network (connection reuse, redirects, IPv4 first); file:// recordings still work.
        val http = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.userAgent)
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        // 1 = tell the player the TV can take 5.1/7.1 and Dolby/DTS passthrough even if it reports stereo only;
        // 2 = decode everything to plain stereo; 3 = FFmpeg software decoder sending plain 5.1/7.1 PCM.
        val caps: AudioCapabilities? = when (effectiveAudio) {
            1 -> AudioCapabilities(
                intArrayOf(
                    AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3,
                    AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD,
                ),
                8,
            )
            3 -> AudioCapabilities(intArrayOf(AudioFormat.ENCODING_PCM_16BIT), 8)
            2 -> AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
            else -> null
        }
        val renderers = object : DefaultRenderersFactory(ctx) {
            init {
                // If a device can't decode a format or profile (some Dolby Vision, odd HEVC), try another decoder.
                setEnableDecoderFallback(true)
                if (effectiveAudio == 3) setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            }
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
                if (caps == null) super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                else DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(caps)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        ExoPlayer.Builder(ctx)
            .setLoadControl(lc)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), extractors))
            .build()
    }
    val currentNow by rememberUpdatedState(current)
    fun variantsFor(e: Entry) = variantsOf(e, entries + allLive.orEmpty(), store.string("pref_${normName(e.title)}"))
    val nextVariant = remember(current, entries, allLive, triedTick) {
        current?.takeIf { it.kind == Kind.LIVE }?.let { c ->
            variantsFor(c).firstOrNull { it.id != c.id && it.id !in triedVariants }
        }
    }
    val nextVariantNow by rememberUpdatedState(nextVariant)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val limit = if (nextVariantNow != null) 2 else 5
                if (retries < limit) {
                    retries++
                    val n = retries
                    scope.launch {
                        delay(2000L * n)
                        if (currentNow?.kind == Kind.LIVE) player.seekToDefaultPosition()
                        player.prepare()
                    }
                } else if (nextVariantNow != null) {
                    failoverTick++
                } else {
                    playbackError = "Stream failed (${error.errorCodeName})"
                }
            }

            private var stall: kotlinx.coroutines.Job? = null

            override fun onRenderedFirstFrame() { Diag.firstFrame() }

            override fun onPlaybackStateChanged(state: Int) {
                // Stuck buffering for a long time on live TV: quietly reconnect instead of waiting forever.
                stall?.cancel()
                if (state == Player.STATE_BUFFERING && currentNow?.kind == Kind.LIVE) {
                    stall = scope.launch {
                        delay(20_000)
                        if (player.playbackState == Player.STATE_BUFFERING && currentNow?.kind == Kind.LIVE) {
                            Diag.stalls++; Diag.reconnects++
                            player.seekToDefaultPosition(); player.prepare()
                        }
                    }
                }
                if (state == Player.STATE_READY) {
                    retries = 0; playbackError = null
                    val c = currentNow
                    if (c != null && c.kind == Kind.LIVE && triedVariants.isNotEmpty()) store.setString("pref_${normName(c.title)}", c.id)
                }
                if (state == Player.STATE_ENDED) endedTick++
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) { tracksTick++ }

            override fun onTracksChanged(tracks: Tracks) { tracksTick++ }
        }
        player.addListener(listener)
        val diag = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) { Diag.dropped += droppedFrames }
        }
        player.addAnalyticsListener(diag)
        onDispose { player.removeListener(listener); player.removeAnalyticsListener(diag); player.release() }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) { runCatching { BackgroundRefresh.apply(ctx, store.flag("bgRefresh", true)) } }

    SideEffect { PipState.ready = fullscreen && current != null }
    LaunchedEffect(now, reminders) {
        if (dueReminder != null) return@LaunchedEffect
        val all = reminders.mapNotNull(::decodeReminder)
        val due = all.firstOrNull { it.start - now <= 60_000 && now - it.start < 300_000 }
        val stale = all.filter { now - it.start >= 300_000 }
        if (due != null || stale.isNotEmpty()) {
            val drop = stale + listOfNotNull(due)
            reminders = reminders.filter { s ->
                decodeReminder(s)?.let { r -> drop.none { it.channel.id == r.channel.id && it.start == r.start } } ?: false
            }
            store.setList("reminders", reminders)
            dueReminder = due
        }
    }

    fun toast(msg: String) { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    LaunchedEffect(current) {
        val c = current ?: return@LaunchedEffect
        delay(7000)
        val ex = player as? ExoPlayer ?: return@LaunchedEffect
        if (c == current && player.isPlaying && ex.videoFormat != null && ex.audioFormat == null) {
            val hasAudioTracks = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
            toast(
                if (hasAudioTracks) "No sound: this stream's audio format isn't supported here. Try Settings → Audio output (Surround software decoder)."
                else "This stream has no audio track."
            )
        }
    }
    fun refreshXmltv(verbose: Boolean) {
        if (xmltvUrl.isBlank() || xmltvBusy) return
        scope.launch {
            xmltvBusy = true
            val msg = runCatching { xmltv.refresh(xmltvUrl) }.getOrElse { "Extra guide failed: ${redact(it.message)}" }
            store.setString("xmltvAt", System.currentTimeMillis().toString())
            xmltvBusy = false
            xmltvTick++
            if (verbose || msg.contains("fail", true) || msg.contains("Couldn't")) toast(msg)
        }
    }
    LaunchedEffect(provider, xmltvUrl) {
        if (provider == null || xmltvUrl.isBlank()) return@LaunchedEffect
        val last = store.string("xmltvAt")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last > 6 * 3_600_000L) { delay(10_000); refreshXmltv(false) }
    }
    LaunchedEffect(player, audioLang, subLang) {
        val b = player.trackSelectionParameters.buildUpon()
        b.setPreferredAudioLanguage(LANG_CODES[audioLang.coerceIn(0, LANG_CODES.lastIndex)])
        val sub = LANG_CODES[subLang.coerceIn(0, LANG_CODES.lastIndex)]
        if (subLang == 0) b.setPreferredTextLanguage(null)
        else b.setPreferredTextLanguage(sub).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        player.trackSelectionParameters = b.build()
    }
    LaunchedEffect(failoverTick) {
        if (failoverTick == 0) return@LaunchedEffect
        val c = current
        val next = nextVariantNow
        if (c != null && next != null) {
            triedVariants += c.id; triedTick++
            retries = 0
            toast("That version failed. Switching to ${next.title}")
            current = next
        }
    }
    fun checkConnections() {
        val p = provider ?: return
        scope.launch {
            val a = runCatching { p.account() }.getOrNull() ?: return@launch
            if (a.maxConnections > 0 && a.activeConnections >= a.maxConnections) {
                toast("All ${a.maxConnections} stream(s) on your plan look busy. Another device may be watching, so this could cut it off.")
            }
        }
    }
    fun kidsExtraMin() = store.long("kidsExtra_${dayKey()}")
    fun kidsOutOfTime() = kidsMode && kidsLimit > 0 && kidsUsed >= (kidsLimit + kidsExtraMin()) * 60
    LaunchedEffect(kidsMode, kidsLimit) {
        while (kidsMode && kidsLimit > 0) {
            delay(10_000)
            if (player.isPlaying) {
                kidsUsed += 10
                store.setLong("kidsUsed_${dayKey()}", kidsUsed)
                if (kidsOutOfTime()) { player.pause(); fullscreen = false; timesUp = true }
            }
        }
    }

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
    fun applyNow() = applyAppearance(accentIdx, bgIdx, cardIdx, textIdx, animIdx, uiIdx)
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
        val visibleCats = catCache[t].orEmpty().filter {
            (showHidden || catKey(kind, it.id) !in hiddenCats) && (!kidsMode || catKey(kind, it.id) in kidsCats)
        }
        return if (sortAz) visibleCats.sortedBy { catName(kind, it).lowercase() } else {
            val order = catOrder[kind.name]?.split(LSEP).orEmpty()
            val index = order.withIndex().associate { it.value to it.index }
            visibleCats.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }
    }

    fun setKidsMany(kind: Kind, ids: Collection<String>, allowed: Boolean) {
        val keys = ids.map { catKey(kind, it) }.toSet()
        kidsCats = if (allowed) kidsCats + keys else kidsCats - keys
        store.setList("kidsCats", kidsCats.toList())
    }
    fun setPicked(kind: Kind, ids: Collection<String>, checked: Boolean) =
        if (pickerKids) setKidsMany(kind, ids, checked) else setHiddenMany(kind, ids, !checked)

    fun groupEntries(id: String) = store.list("grp_$id").mapNotNull(::decodeEntry)
    fun newGroup(name: String): String {
        var n = 1
        while (groups.containsKey(n.toString())) n++
        val id = n.toString()
        groups = groups + (id to name)
        store.setMap("groups", groups)
        return id
    }
    fun addToGroup(id: String, e: Entry) {
        val l = store.list("grp_$id")
        if (l.mapNotNull(::decodeEntry).none { it.key() == e.key() }) store.setList("grp_$id", l + e.encode())
        toast("Added to \"${groups[id].orEmpty()}\"")
    }
    fun removeFromGroup(id: String, e: Entry) {
        store.setList("grp_$id", store.list("grp_$id").filter { decodeEntry(it)?.key() != e.key() })
        if (category?.id == "g:$id") entries = groupEntries(id)
    }
    fun deleteGroup(id: String) {
        groups = groups - id
        store.setMap("groups", groups)
        store.setList("grp_$id", emptyList())
        if (category?.id == "g:$id") { category = null; entries = emptyList() }
    }

    val reminderKeys = remember(reminders) { reminders.mapNotNull(::decodeReminder).map { "${it.channel.id}:${it.start}" }.toSet() }
    fun toggleReminder(ch: Entry, p: Programme) {
        val k = "${ch.id}:${p.start}"
        val had = k in reminderKeys
        reminders = if (had) reminders.filter { s -> decodeReminder(s)?.let { "${it.channel.id}:${it.start}" } != k }
        else reminders + encodeReminder(Reminder(ch, p.start, p.title))
        store.setList("reminders", reminders)
        toast(if (had) "Reminder removed" else "Reminder set for ${p.title}. Keep Vance TV open to be switched over.")
    }

    fun loadDetail(p: Provider, e: Entry) {
        scope.launch {
            detail = runCatching { p.detail(e) }.getOrNull()
            if (tmdbKey.isNotBlank()) {
                val t = runCatching { Tmdb.lookup(tmdbKey, e.title, e.kind == Kind.SERIES) }.getOrNull()
                if (t != null && detailEntry?.key() == e.key()) detail = Tmdb.merge(detail, t)
            }
        }
    }

    fun recUrl(e: Entry) = provider?.streamUrl(e.copy(start = 0))?.replace(".m3u8", ".ts").orEmpty()
    fun recordNow(e: Entry, durMs: Long, label: String) {
        val url = recUrl(e)
        if (url.isBlank()) { toast("Can't record this channel"); return }
        Recorder.startNow(ctx, RecJob("live:${e.id}", url, label, System.currentTimeMillis(), durMs + store.int("recPadEnd", 2) * 60_000L))
        toast("Recording started: $label")
    }
    fun scheduleRecording(ch: Entry, p: Programme, quiet: Boolean = false) {
        val url = recUrl(ch)
        if (url.isBlank()) { toast("Can't record this channel"); return }
        val padStart = store.int("recPadStart", 1) * 60_000L
        val padEnd = store.int("recPadEnd", 2) * 60_000L
        Recorder.schedule(ctx, RecJob("${ch.id}:${p.start}", url, "${nameOf(ch)} ${p.title}", p.start - padStart, p.stop - p.start + padStart + padEnd))
        if (!quiet) toast("Recording scheduled for ${p.title}")
    }
    fun addSeriesRule(ch: Entry, title: String) {
        if (seriesRules.mapNotNull(::decodeRule).none { it.first.id == ch.id && it.second.equals(title, true) }) {
            seriesRules = seriesRules + encodeRule(ch, title)
            store.setList("seriesRules", seriesRules)
        }
    }
    fun removeSeriesRule(ch: Entry, title: String) {
        seriesRules = seriesRules.filter { s -> decodeRule(s)?.let { it.first.id == ch.id && it.second.equals(title, true) } != true }
        store.setList("seriesRules", seriesRules)
    }
    fun scanSeriesRules() {
        val p = provider ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            var added = 0
            for ((ch, title) in seriesRules.mapNotNull(::decodeRule)) {
                var progs = runCatching { p.guideEpg(ch, 0, 48 * 3_600_000L) }.getOrDefault(emptyList())
                if (xmltvUrl.isNotBlank() && (xmltvFirst || progs.isEmpty())) {
                    val more = runCatching { xmltv.programmesFor(ch, now, now + 48 * 3_600_000L) }.getOrDefault(emptyList())
                    if (more.isNotEmpty()) progs = more
                }
                for (pr in progs) {
                    if (pr.start > now && pr.title.trim().equals(title.trim(), true)) {
                        if (Recorder.jobs(ctx).none { it.id == "${ch.id}:${pr.start}" }) { scheduleRecording(ch, pr, quiet = true); added++ }
                    }
                }
            }
            if (added > 0) toast("Series recording: scheduled $added new episode(s)")
        }
    }
    LaunchedEffect(provider) {
        if (provider == null) return@LaunchedEffect
        delay(12_000)
        while (true) {
            scanSeriesRules()
            delay(3 * 3_600_000L)
        }
    }
    fun addToMulti(e: Entry) {
        when {
            e.kind != Kind.LIVE -> toast("Multiview is for live channels")
            multiList.any { it.key() == e.key() } -> toast("Already in multiview")
            multiList.size >= 4 -> toast("Multiview holds 4 channels")
            else -> { multiList = multiList + e; toast("Added to multiview (${multiList.size}/4)") }
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
            val ttl = { LIB_TTL_MS[store.int("libCache", 2).coerceIn(0, LIB_TTL_MS.lastIndex)] }
            val xt = xtreamFromUrl(urlText)
            val p: Provider = if (xt != null) {
                val dir = libCacheDir(ctx, urlText)
                // With a saved copy of the library, open straight onto it and check the login in the background.
                if (ttl() > 0 && File(dir, "cat_LIVE").exists()) {
                    scope.launch {
                        runCatching { xt.verify() }.onFailure { e ->
                            val m = e.message.orEmpty()
                            if (m.contains("rejected") || m.startsWith("Account is")) { status = m; provider = null; showSetup = true }
                        }
                    }
                } else xt.verify()
                CachedProvider(xt, dir, ttl)
            } else connectProvider(urlText)
            provider = p
            resetBrowsing()
            tab = Tab.HOME
            status = if (p.supportsVod) "Connected" else "Playlist loaded (live only)"
            showSetup = false
            listVersion++
            loadHome(p)
            rebuildIndex(p, false)
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
        groups = store.map("groups")
        reminders = store.list("reminders")
        kidsMode = store.isKids(store.active)
        kidsCats = store.list("kidsCats").toSet()
        kidsUsed = store.long("kidsUsed_${dayKey()}")
        xmltvUrl = store.string("xmltvUrl").orEmpty(); xmltvFirst = store.flag("xmltvFirst", false)
        chShifts = store.map("chShift"); seriesRules = store.list("seriesRules")
        unlocked = false
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
            if (c.id.startsWith("g:")) entries = groupEntries(c.id.removePrefix("g:"))
            else launchLoad { entries = p.entries(kind, c) }
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
        loadDetail(p, e)
    }
    fun openMovie(e: Entry) {
        val p = provider ?: return
        if (player.isPlaying) { player.pause(); pausedForDetail = true }
        detailEntry = e; detail = null
        loadDetail(p, e)
    }
    fun closeDetail() {
        detailEntry = null; detail = null; series = null; episodes = emptyList()
        if (pausedForDetail && current != null) reloadTick++ // resume what the detail page paused
        pausedForDetail = false
        listVersion++
    }
    fun playNow(e: Entry, full: Boolean) {
        if (kidsOutOfTime()) { timesUp = true; return }
        if (e.kind == Kind.LIVE && !player.isPlaying) checkConnections()
        triedVariants.clear(); triedTick++
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
        title = "${nameOf(ch)} · ${p.title}", start = p.start - epgShift * 3_600_000L, durMin = ((p.stop - p.start) / 60_000).toInt() + 2,
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
        if (BuildConfig.PREVIEW) {
            if (!silent) updateState = UpdateState.Failed("Preview builds are updated manually")
            return
        }
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
    fun afterImport() {
        accentIdx = store.int("accent", 0); bgIdx = store.int("bg", 0); cardIdx = store.int("cards", 1)
        autoplayLaunch = store.flag("autoplayLaunch", false)
        rotateHomeBanner = store.flag("rotateHomeBanner", false)
        textIdx = store.int("text", 1); uiIdx = store.int("ui", 1); animIdx = store.int("anim", 0); startIdx = store.int("start", 0).coerceIn(0, START_NAMES.lastIndex)
        sortAz = store.flag("sortAz", false); showHidden = store.flag("showHidden", false)
        lockAdult = store.flag("lockAdult", true); autoNext = store.flag("autoNext", true)
        matchFps = store.flag("matchFps", true); liveTs = store.flag("liveTs", false)
        bufferMode = store.int("buffer", 1); resizeIdx = store.int("resize", 0).coerceIn(0, RESIZES.lastIndex)
        subSize = store.int("subSize", 1); sortMode = store.int("sortMode", 0)
        LayoutPrefs.load(store); zapStrip = store.flag("zapStrip", false); mergeDupes = store.flag("mergeDupes", true)
        applyNow()
        switchProfile(activeProfile)
        toast("Settings restored")
    }
    fun restore() {
        val f = backupFile()
        if (!f.exists()) { toast("No backup found at ${f.absolutePath}"); return }
        if (!store.importBackup(f.readText())) { toast("That file isn't a Vance TV backup"); return }
        afterImport()
    }

    fun startSharing() {
        syncServer?.stop()
        val code = (1000..9999).random().toString()
        val server = SyncServer(code) { store.exportBackup() }
        val port = server.start()
        if (port < 0) { toast("Couldn't open a port to share on"); return }
        syncServer = server
        syncInfo = PairInfo("${localIpAddress() ?: "?"}:$port", code)
    }
    fun stopSharing() { syncServer?.stop(); syncServer = null; syncInfo = null }
    fun pullSync(address: String, code: String) {
        scope.launch {
            val a = address.trim().let { if (':' in it) it else "$it:8776" }
            val text = fetchSync(a, code.trim())
            if (text == null) toast("Couldn't reach that device")
            else if (!store.importBackup(text)) toast("Wrong code, or that isn't a Vance TV backup")
            else afterImport()
        }
    }
    val launchEntry = pendingLaunch.value
    LaunchedEffect(provider, launchEntry) {
        if (launchEntry != null && provider != null) {
            decodeEntry(launchEntry)?.let { playNow(it, true) }
            pendingLaunch.value = null
        }
    }

    // ---- derived lists
    val cats = remember(catCache[tab], hiddenCats, showHidden, renames, catOrder, sortAz, tab, kidsMode, kidsCats) { orderedCats(tab) }
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
    val groupRows = remember(groups, entries) {
        groups.map { (id, name) -> CatRow(Category("g:$id", name), name, store.list("grp_$id").size, false, false, false) }
    }
    val favoriteEntries = remember(favorites) { favorites.mapNotNull(::decodeEntry) }
    val recentEntries = remember(history) { history.mapNotNull(::decodeEntry) }
    val searching = tab == Tab.LIVE && query.length >= 2 && !kidsMode
    val baseEntries = if (tab == Tab.FAVORITES) favoriteEntries else entries
    val shownEntries = remember(
        baseEntries, query, hiddenEntries, showHidden, renames, sortAz, unlocked, hasPin, lockAdult, lockedCats, searching, allLive, tab, sortMode, mergeDupes,
    ) {
        val source = if (searching) allLive.orEmpty() else baseEntries
        val filtered = source.filter { visible(it) && nameOf(it).contains(query, ignoreCase = true) }
            .let { if (searching) it.take(300) else it }
            .let { if (tab == Tab.LIVE && mergeDupes && !searching) dedupe(it) else it }
        val vod = tab == Tab.MOVIES || tab == Tab.SERIES
        val sorted = if (!vod) filtered else when (sortMode) {
            1 -> filtered.sortedBy { nameOf(it).lowercase() }
            2 -> filtered.sortedByDescending { it.added }
            3 -> filtered.sortedByDescending { it.rating }
            else -> filtered
        }
        if (sortMode == 0 && sortAz && tab.browsable() && !searching) sorted.sortedBy { nameOf(it).lowercase() } else sorted
    }
    val guide = remember(provider, epgShift, chShifts, xmltvUrl, xmltvFirst, xmltvTick) {
        provider?.epgOffsetMs = epgShift * 3_600_000L
        provider?.channelShiftMs = chShifts.mapNotNull { (id, h) -> h.toLongOrNull()?.let { id to it * 3_600_000L } }.toMap()
        provider?.let {
            GuideData(it, if (xmltvUrl.isBlank()) null else { e, from, to -> xmltv.programmesFor(e, from, to) }, xmltvFirst)
        }
    }
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
            resizeIdx = (store.string("ar_${e.key()}")?.toIntOrNull() ?: store.int("resize", 0)).coerceIn(0, RESIZES.lastIndex)
        }
        if (e.isLiveNow) {
            store.setString("last", e.encode())
            if (liveHistory.lastOrNull()?.key() != e.key()) {
                liveHistory += e
                if (liveHistory.size > 10) liveHistory.removeAt(0)
            }
        }
        if (!e.isCatchUp && !e.id.startsWith("file://")) pushHistory(e)
        Diag.requested()
        player.setMediaItem(MediaItem.fromUri(if (e.id.startsWith("file://")) e.id else p.streamUrl(e)))
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
    LaunchedEffect(current, now / 300_000, epgShift, chShifts, xmltvTick) {
        val e = current
        val p = provider
        epg = if (e != null && p != null && e.isLiveNow) {
            var r = runCatching { p.epg(e, 3) }.getOrDefault(emptyList())
            if (xmltvUrl.isNotBlank() && (xmltvFirst || r.isEmpty())) {
                val more = runCatching { xmltv.programmesFor(e, now - 3 * 3_600_000L, now + 9 * 3_600_000L) }.getOrDefault(emptyList())
                if (more.isNotEmpty()) r = more
            }
            if (r.isEmpty()) {
                for (v in variantsFor(e).filter { it.id != e.id }.take(3)) {
                    r = runCatching { p.epg(v, 3) }.getOrDefault(emptyList())
                    if (r.isNotEmpty()) break
                }
            }
            r
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
        if (startIdx == 1) openTab(Tab.LIVE)
        if (autoplayLaunch) {
            store.string("last")?.let(::decodeEntry)?.takeIf { it.isLiveNow && visible(it) }?.let { playNow(it, true) }
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

    val overlayActive = detailEntry != null || guideOpen || (fullscreen && current != null) || pickerOpen || appearanceOpen || searchOpen || multiOpen || showSettings
    BackHandler(enabled = showSetup && provider != null) { showSetup = false }
    BackHandler(enabled = provider != null && !showSetup && tab != Tab.HOME && !overlayActive) { openTab(Tab.HOME) }
    BackHandler(enabled = detailEntry != null) { closeDetail() }
    BackHandler(enabled = fullscreen) { fullscreen = false; listVersion++ }
    BackHandler(enabled = guideOpen) { guideOpen = false; listVersion++ }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = nextUp != null) { nextUp = null }
    BackHandler(enabled = searchOpen) { searchOpen = false; listVersion++ }
    BackHandler(enabled = pickerOpen) {
        store.setString("picked", "1"); pickerOpen = false; listVersion++
        if (pickerKids) { pickerKids = false; homeShelves = emptyList(); provider?.let { loadHome(it) } }
    }
    BackHandler(enabled = stripIdx != null) { stripIdx = null }
    BackHandler(enabled = trackPanelOpen) { trackPanelOpen = false }
    BackHandler(enabled = showSettings && !showSetup) { showSettings = false; listVersion++ }
    LaunchedEffect(trackPanelOpen) {
        if (trackPanelOpen) {
            repeat(15) { delay(80); if (runCatching { trackFocus.requestFocus() }.isSuccess) return@LaunchedEffect }
        } else if (fullscreen) {
            delay(50); runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(trackPanelOpen, trackTick) { if (trackPanelOpen) { delay(12_000); trackPanelOpen = false } }
    LaunchedEffect(fullscreen) { if (!fullscreen) trackPanelOpen = false }
    LaunchedEffect(stripIdx, stripTick) { if (stripIdx != null) { delay(6000); stripIdx = null } }
    BackHandler(enabled = multiOpen) { multiOpen = false; listVersion++ }
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

    menuTarget?.takeIf { !kidsMode }?.let { target ->
        when (target) {
            is MenuTarget.EntryT -> {
                val e = target.entry
                val k = e.key()
                ActionMenuDialog(nameOf(e), buildList {
                    add(MenuItem(if (k in favKeys) "Remove from favorites" else "Add to favorites") { toggleFavorite(e) })
                    if (e.kind == Kind.MOVIE || e.kind == Kind.SERIES) {
                        add(MenuItem(if (k in myKeys) "Remove from My list" else "Add to My list") { toggleMyList(e) })
                    }
                    if (e.kind == Kind.LIVE) add(MenuItem("Add to multiview (${multiList.size}/4)") { addToMulti(e) })
                    if (e.kind == Kind.LIVE) add(MenuItem("Add to group…", close = false) { menuTarget = MenuTarget.AddToGroupT(e) })
                    if (e.kind == Kind.LIVE && category?.id?.startsWith("g:") == true) {
                        add(MenuItem("Remove from this group") { removeFromGroup(category!!.id.removePrefix("g:"), e) })
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
            is MenuTarget.AddToGroupT -> ActionMenuDialog("Add to group", buildList {
                groups.forEach { (id, name) -> add(MenuItem(name) { addToGroup(id, target.entry) }) }
                add(MenuItem("+ New group…", close = false) {
                    menuTarget = null
                    textPrompt = TextPrompt("Group name", "") { n -> if (n.isNotBlank()) addToGroup(newGroup(n.trim()), target.entry) }
                })
            }) { menuTarget = null }
            is MenuTarget.GroupT -> ActionMenuDialog(groups[target.id] ?: "Group", listOf(
                MenuItem("Rename…") {
                    textPrompt = TextPrompt("Group name", groups[target.id].orEmpty()) { n ->
                        if (n.isNotBlank()) {
                            groups = groups + (target.id to n.trim())
                            store.setMap("groups", groups)
                            if (category?.id == "g:${target.id}") category = Category("g:${target.id}", n.trim())
                        }
                    }
                },
                MenuItem("Delete group") { deleteGroup(target.id) },
            )) { menuTarget = null }
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

    programTarget?.let { (ch, p) ->
        val id = "${ch.id}:${p.start}"
        val reminded = id in reminderKeys
        val scheduled = Recorder.jobs(ctx).any { it.id == id }
        ActionMenuDialog(p.title, buildList {
            add(MenuItem(if (reminded) "Remove reminder" else "Remind me") { toggleReminder(ch, p) })
            if (ch.kind == Kind.LIVE && !kidsMode) add(MenuItem(if (scheduled) "Cancel recording" else "Record this programme") {
                if (scheduled) { Recorder.cancel(ctx, id); toast("Recording cancelled") } else scheduleRecording(ch, p)
            })
            if (ch.kind == Kind.LIVE && !kidsMode) {
                val hasRule = seriesRules.mapNotNull(::decodeRule).any { it.first.id == ch.id && it.second.equals(p.title, true) }
                add(MenuItem(if (hasRule) "Stop recording every episode" else "Record every episode of this show") {
                    if (hasRule) { removeSeriesRule(ch, p.title); toast("Series recording stopped") }
                    else { addSeriesRule(ch, p.title); scheduleRecording(ch, p, quiet = true); scanSeriesRules(); toast("Recording every episode of ${p.title}") }
                })
            }
        }) { programTarget = null }
    }
    if (seriesOpen) {
        ActionMenuDialog("Series recordings", buildList {
            val rules = seriesRules.mapNotNull(::decodeRule)
            if (rules.isEmpty()) add(MenuItem("None yet. In the Guide, select a programme and choose \"Record every episode\".", enabled = false) {})
            rules.forEach { (ch, title) ->
                add(MenuItem("$title  ·  ${nameOf(ch)}  (select to stop)", close = false) { removeSeriesRule(ch, title) })
            }
            add(MenuItem("Check for new episodes now") { scanSeriesRules() })
            add(MenuItem("Close") {})
        }) { seriesOpen = false }
    }
    if (recOpen) {
        val fmt = remember { java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault()) }
        val files = Recorder.files(ctx)
        val jobs = Recorder.jobs(ctx)
        ActionMenuDialog("Recordings", buildList {
            Recorder.active.entries.forEach { (_, st) ->
                add(MenuItem("● Recording: ${st.name}  (select to stop)", close = false) { st.stop = true; recOpen = false })
            }
            jobs.forEach { j ->
                add(MenuItem("Scheduled ${fmt.format(java.util.Date(j.startMs))}  ${j.name}  (select to cancel)", close = false) {
                    Recorder.cancel(ctx, j.id); recOpen = false
                })
            }
            files.forEach { f ->
                add(MenuItem("${f.name.removeSuffix(".ts")}  ·  ${f.length() / 1_000_000} MB", close = false) { recFile = f; recOpen = false })
            }
            if (files.isEmpty() && jobs.isEmpty() && Recorder.active.isEmpty()) add(MenuItem("Nothing recorded yet", enabled = false) {})
            add(MenuItem("Close") {})
        }) { recOpen = false }
    }
    recFile?.let { f ->
        ActionMenuDialog(f.name, listOf(
            MenuItem("Play") { playNow(Entry(Kind.MOVIE, "file://${f.absolutePath}", f.name.removeSuffix(".ts"), "ts"), true) },
            MenuItem("Delete") { f.delete() },
        )) { recFile = null }
    }
    if (timesUp) {
        ActionMenuDialog("Screen time is up for today", listOf(
            MenuItem("Parent PIN: add 30 minutes") {
                requirePin("Parent PIN") { store.setLong("kidsExtra_${dayKey()}", kidsExtraMin() + 30); timesUp = false }
            },
            MenuItem("OK") {},
        )) { timesUp = false }
    }
    syncInfo?.let { info ->
        ActionMenuDialog("Share settings", listOf(
            MenuItem("On the other device choose \"Get settings from another device\" and enter:", enabled = false) {},
            MenuItem("Address   ${info.url}", enabled = false) {},
            MenuItem("Code   ${info.code}", enabled = false) {},
            MenuItem("Stop sharing") { stopSharing() },
        )) { stopSharing() }
    }
    dueReminder?.let { r ->
        ActionMenuDialog("Starting now: ${r.title}", listOf(
            MenuItem("Watch on ${nameOf(r.channel)}") { guideOpen = false; playNow(r.channel, true) },
            MenuItem("Dismiss") {},
        )) { dueReminder = null }
    }
    if (remindersOpen) {
        val fmt = remember { java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault()) }
        val list = reminders.mapNotNull(::decodeReminder).sortedBy { it.start }
        ActionMenuDialog("Reminders", buildList {
            if (list.isEmpty()) add(MenuItem("None yet. Open the Guide and select an upcoming programme.", enabled = false) {})
            list.forEach { r ->
                add(MenuItem("${fmt.format(java.util.Date(r.start))}  ${r.title}  ·  ${nameOf(r.channel)}  (remove)", close = false) {
                    reminders = reminders.filter { s -> decodeReminder(s)?.let { it.channel.id == r.channel.id && it.start == r.start } != true }
                    store.setList("reminders", reminders)
                })
            }
            add(MenuItem("Close") {})
        }) { remindersOpen = false }
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
                if (e != null && e.isLiveNow && !kidsMode) {
                    val rid = "live:${e.id}"
                    if (Recorder.active.containsKey(rid)) add(MenuItem("■ Stop recording") { Recorder.active[rid]?.stop = true })
                    else {
                        if (curProg != null) add(MenuItem("● Record rest of this programme") {
                            recordNow(e, curProg.stop - System.currentTimeMillis() + 120_000, "${nameOf(e)} ${curProg.title}")
                        })
                        add(MenuItem("● Record 60 minutes") { recordNow(e, 3_600_000L, nameOf(e)) })
                    }
                    add(MenuItem("Add to multiview (${multiList.size}/4)") { addToMulti(e) })
                }
                if (Build.VERSION.SDK_INT >= 26) add(MenuItem("Picture-in-picture") { (ctx as? MainActivity)?.enterPip() })
                add(MenuItem(audioLabel(player), close = false) { cycleAudio(player); tracksTick++ })
                add(MenuItem(textLabel(player), close = false) { cycleText(player); tracksTick++ })
                add(MenuItem("Picture: ${RESIZES[resizeIdx].second}${if (e != null && e.kind == Kind.LIVE) "  (remembered for this channel)" else ""}", close = false) {
                    resizeIdx = (resizeIdx + 1) % RESIZES.size
                    if (e != null && e.kind == Kind.LIVE) store.setString("ar_${e.key()}", resizeIdx.toString())
                    else store.setInt("resize", resizeIdx)
                })
                if (e != null && e.isLiveNow) {
                    val cur = chShifts[e.id]?.toIntOrNull() ?: 0
                    fun setShift(h: Int) {
                        val v = h.coerceIn(-12, 12)
                        chShifts = if (v == 0) chShifts - e.id else chShifts + (e.id to v.toString())
                        store.setMap("chShift", chShifts)
                    }
                    add(MenuItem("This channel's guide time: ${if (cur >= 0) "+" else ""}$cur h  (select = +1 h)", close = false) { setShift(cur + 1) })
                    add(MenuItem("This channel's guide time: −1 h", close = false) { setShift(cur - 1) })
                }
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

    fun setLayout(key: String, v: Int) { store.setInt(key, v); LayoutPrefs.load(store); layoutTick++ }
    fun setLayoutFlag(key: String, v: Boolean) { store.setFlag(key, v); LayoutPrefs.load(store); layoutTick++ }
    fun openSettings() {
        if (kidsMode) requirePin("Parent PIN") { showSettings = true }
        else if (hasPin && !unlocked) requirePin("Enter PIN for settings") { showSettings = true } else showSettings = true
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
    @Suppress("UNUSED_EXPRESSION") layoutTick
    fun buildSettingSections(): List<SettingSection> {
        val active = profiles.firstOrNull { it.id == activeProfile } ?: profiles.first()
        fun onOff(b: Boolean) = if (b) "On" else "Off"
        val audioOrder = listOf(0, 1, 3, 2)
        val audioNames = listOf(if (smartMode == 3) "Auto (software 5.1)" else "Auto", "Surround (passthrough)", "Surround (software decoder)", "Stereo (compatible)")
        val kidsSteps = listOf(0, 30, 60, 90, 120, 180)
        val shiftNames = (-12..12).map { if (it >= 0) "+$it h" else "$it h" }
        val themeIdx = THEME_PICKS.indexOfFirst { it.first == accentIdx && it.second == bgIdx }
        fun toggle(title: String, desc: String, on: Boolean, flip: () -> Unit) =
            SettingItem(title, onOff(on), desc, action = flip)
        return listOf(
            SettingSection("Appearance", "Colors, text and motion. Changes apply instantly.", listOf(
                SettingItem("Theme", if (themeIdx >= 0) THEME_NAMES[themeIdx] else "Custom", "A ready-made accent and background together",
                    choices = THEME_NAMES, selected = themeIdx.coerceAtLeast(0), onPick = { i ->
                        accentIdx = THEME_PICKS[i].first; bgIdx = THEME_PICKS[i].second
                        store.setInt("accent", accentIdx); store.setInt("bg", bgIdx); applyNow(); layoutTick++
                    }),
                SettingItem("Accent color", ACCENT_NAMES[accentIdx.coerceIn(0, ACCENT_NAMES.lastIndex)], "Buttons, highlights and progress bars",
                    swatch = Palette.accent, choices = ACCENT_NAMES, selected = accentIdx, onPick = { i ->
                        accentIdx = i; store.setInt("accent", i); applyNow(); layoutTick++
                    }),
                SettingItem("Background", BACKGROUNDS[bgIdx.coerceIn(0, BACKGROUNDS.lastIndex)].name, "Black suits OLED TVs; the others tint the whole app",
                    choices = BACKGROUNDS.map { it.name }, selected = bgIdx, onPick = { i ->
                        bgIdx = i; store.setInt("bg", i); applyNow(); layoutTick++
                    }),
                SettingItem("Text size", TEXT_NAMES[textIdx], "Scales every label",
                    choices = TEXT_NAMES, selected = textIdx, onPick = { i -> textIdx = i; store.setInt("text", i); applyNow(); layoutTick++ }),
                SettingItem("Interface size", UI_NAMES[uiIdx], "Dense fits the most on a 4K TV; Large is easiest from far away",
                    choices = UI_NAMES, selected = uiIdx, onPick = { i -> uiIdx = i; store.setInt("ui", i); applyNow(); layoutTick++ }),
                SettingItem("Animations", ANIM_NAMES[animIdx], "Reduced or Off is smoothest on slower devices",
                    choices = ANIM_NAMES, selected = animIdx, onPick = { i -> animIdx = i; store.setInt("anim", i); applyNow(); layoutTick++ }),
                toggle("Focus glow", "A soft glow around the selected card", Dimens.glow) { setLayoutFlag("glow", !Dimens.glow) },
                toggle("Banner color from poster", "Tint the Home banner with the poster's colors", Dimens.heroTint) { setLayoutFlag("heroTint", !Dimens.heroTint) },
                toggle("Ambient background", "Slowly moving color glow behind Home", Dimens.ambient) { setLayoutFlag("ambient", !Dimens.ambient) },
                toggle("Navigation sounds", "A soft click as you move around (uses your TV's system sounds)", Dimens.sounds) { setLayoutFlag("sounds", !Dimens.sounds) },
            )),
            SettingSection("Startup", "Playback stays off until you select a channel or press Play.", listOf(
                SettingItem("Start screen", START_NAMES[startIdx], "What opens when you launch Vance TV",
                    choices = START_NAMES, selected = startIdx, onPick = { i -> startIdx = i; store.setInt("start", i); layoutTick++ }),
                toggle("Autoplay last channel on launch", "Start the last live channel automatically", autoplayLaunch) {
                    autoplayLaunch = !autoplayLaunch; store.setFlag("autoplayLaunch", autoplayLaunch)
                },
            )),
            SettingSection("Layout", "How the app is arranged.", listOf(
                toggle("Rotate Home banner", "Automatically cycle featured artwork", rotateHomeBanner) {
                    rotateHomeBanner = !rotateHomeBanner; store.setFlag("rotateHomeBanner", rotateHomeBanner)
                },
                SettingItem("Navigation", NAV_NAMES[if (Dimens.topNav) 1 else 0], "Menu down the side or across the top",
                    choices = NAV_NAMES, selected = if (Dimens.topNav) 1 else 0, onPick = { i -> setLayoutFlag("topNav", i == 1) }),
                SettingItem("Menu style", if (Dimens.railLabels) "Icons + labels" else "Icons only", "Icons only gives the content more room",
                    choices = listOf("Icons + labels", "Icons only"), selected = if (Dimens.railLabels) 0 else 1, onPick = { i -> setLayoutFlag("railLabels", i == 0) }),
                toggle("Home banner", "The large featured title at the top of Home", Dimens.homeHero) { setLayoutFlag("homeHero", !Dimens.homeHero) },
                toggle("Home filter chips", "All, Movies, Series, Live and 4K above the rows", Dimens.homeChips) { setLayoutFlag("homeChips", !Dimens.homeChips) },
                SettingItem("Card size", CARD_NAMES[cardIdx], "Compact fits more; Large reads better from the couch",
                    choices = CARD_NAMES, selected = cardIdx, onPick = { i -> cardIdx = i; store.setInt("cards", i); applyNow(); layoutTick++ }),
                SettingItem("Corner style", CORNER_NAMES[store.int("corner", 1).coerceIn(0, 2)], "Sharp, rounded or soft corners on cards and buttons",
                    choices = CORNER_NAMES, selected = store.int("corner", 1).coerceIn(0, 2), onPick = { i -> setLayout("corner", i) }),
                SettingItem("Channel row size", ROW_NAMES[store.int("rowSize", 1).coerceIn(0, 2)], "Logo and spacing in the channel list",
                    choices = ROW_NAMES, selected = store.int("rowSize", 1).coerceIn(0, 2), onPick = { i -> setLayout("rowSize", i) }),
                SettingItem("Guide row height", GUIDE_ROW_NAMES[store.int("guideRow", 1).coerceIn(0, 2)], "Compact shows more channels at once",
                    choices = GUIDE_ROW_NAMES, selected = store.int("guideRow", 1).coerceIn(0, 2), onPick = { i -> setLayout("guideRow", i) }),
                SettingItem("Player info", OVERLAY_NAMES[Dimens.overlayStyle], "What appears when you press a button during playback",
                    choices = OVERLAY_NAMES, selected = Dimens.overlayStyle, onPick = { i -> setLayout("overlay", i) }),
                toggle("Clock in player", "Show the time in the corner during playback", Dimens.showClock) { setLayoutFlag("clock", !Dimens.showClock) },
                SettingItem("Up/Down in player", if (zapStrip) "Channel strip" else "Switch channel", "Browse a strip of channels, or change channel straight away",
                    choices = listOf("Switch channel", "Channel strip"), selected = if (zapStrip) 1 else 0, onPick = { i -> zapStrip = i == 1; store.setFlag("zapStrip", zapStrip); layoutTick++ }),
                toggle("Sort A\u2013Z", "Sort categories and channels alphabetically", sortAz) { sortAz = !sortAz; store.setFlag("sortAz", sortAz) },
                toggle("Show hidden items", "Show channels and categories you hid", showHidden) { showHidden = !showHidden; store.setFlag("showHidden", showHidden) },
            )),
            SettingSection("Playback & audio", "Sound, picture and network.", listOf(
                SettingItem("Audio output", audioNames[audioOrder.indexOf(audioMode).coerceAtLeast(0)], "Try Surround (software decoder) if 5.1 only plays as stereo",
                    choices = audioNames, selected = audioOrder.indexOf(audioMode).coerceAtLeast(0), onPick = { i -> audioMode = audioOrder[i]; store.setInt("audioMode", audioMode); layoutTick++ }),
                SettingItem("Buffer", BUFFERS[bufferMode], "Higher means fewer stalls but a slower start",
                    choices = BUFFERS, selected = bufferMode, onPick = { i -> bufferMode = i; store.setInt("buffer", i); layoutTick++ }),
                toggle("Match frame rate", "Switch the TV to the video's frame rate", matchFps) { matchFps = !matchFps; store.setFlag("matchFps", matchFps) },
                toggle("Auto-play next episode", "Start the next episode when one ends", autoNext) { autoNext = !autoNext; store.setFlag("autoNext", autoNext) },
                SettingItem("Preferred audio language", LANG_NAMES[audioLang.coerceIn(0, LANG_NAMES.lastIndex)], "Picked automatically when a stream offers it",
                    choices = LANG_NAMES, selected = audioLang, onPick = { i -> audioLang = i; store.setInt("audioLang", i); layoutTick++ }),
                SettingItem("Preferred subtitles", if (subLang == 0) "Off" else LANG_NAMES[subLang.coerceIn(0, LANG_NAMES.lastIndex)], "Turned on automatically when a stream has them",
                    choices = listOf("Off") + LANG_NAMES.drop(1), selected = subLang, onPick = { i -> subLang = i; store.setInt("subLang", i); layoutTick++ }),
                SettingItem("Default picture mode", RESIZES[resizeIdx.coerceIn(0, RESIZES.lastIndex)].second, "Each channel also remembers its own setting",
                    choices = RESIZES.map { it.second }, selected = resizeIdx.coerceIn(0, RESIZES.lastIndex),
                    onPick = { i -> resizeIdx = i; store.setInt("resize", i); layoutTick++ }),
                SettingItem("Subtitle size", SUB_NAMES[subSize], null,
                    choices = SUB_NAMES, selected = subSize, onPick = { i -> subSize = i; store.setInt("subSize", i); layoutTick++ }),
                SettingItem("Subtitle background", SUB_BG_NAMES[Dimens.subBg], null,
                    choices = SUB_BG_NAMES, selected = Dimens.subBg, onPick = { i -> setLayout("subBg", i) }),
                SettingItem("Subtitle edge", SUB_EDGE_NAMES[Dimens.subEdge], null,
                    choices = SUB_EDGE_NAMES, selected = Dimens.subEdge, onPick = { i -> setLayout("subEdge", i) }),
                SettingItem("Subtitle color", SUB_COLOR_NAMES[Dimens.subColor], null,
                    choices = SUB_COLOR_NAMES, selected = Dimens.subColor, onPick = { i -> setLayout("subColor", i) }),
                SettingItem("Skip back", "${Dimens.skipBack}s", "Left on movies and series; rewind key skips 3x this",
                    choices = SKIP_SECS.map { "$it seconds" }, selected = SKIP_SECS.indexOf(Dimens.skipBack).coerceAtLeast(0), onPick = { i -> setLayout("skipBack", i) }),
                SettingItem("Skip forward", "${Dimens.skipFwd}s", "Right on movies and series; fast-forward key skips 3x this",
                    choices = SKIP_SECS.map { "$it seconds" }, selected = SKIP_SECS.indexOf(Dimens.skipFwd).coerceAtLeast(0), onPick = { i -> setLayout("skipFwd", i) }),
                toggle("Keep picture while changing", "Hold the last frame until the new stream appears instead of going black", Dimens.keepFrame) { setLayoutFlag("keepFrame", !Dimens.keepFrame) },
                SettingItem("Live stream format", if (liveTs) "TS" else "HLS (m3u8)", "Switch if a provider streams better one way",
                    choices = listOf("HLS (m3u8)", "TS"), selected = if (liveTs) 1 else 0, onPick = { i -> liveTs = i == 1; store.setFlag("liveTs", liveTs); layoutTick++ }),
                SettingItem("Network identity", Net.UA_NAMES[uaMode], "Try VLC or TiviMate if a provider blocks or throttles streams",
                    choices = Net.UA_NAMES, selected = uaMode, onPick = { i -> uaMode = i; store.setInt("uaMode", i); Net.setMode(i); layoutTick++ }),
                toggle("Merge duplicate channels", "Combine HD, 4K and backup copies of a channel", mergeDupes) { mergeDupes = !mergeDupes; store.setFlag("mergeDupes", mergeDupes) },
            )),
            SettingSection("Guide & recording", "Guide times, movie info, reminders and recordings.", listOf(
                SettingItem("Guide time shift", shiftNames[epgShift + 12], "Fix guide times that are in the wrong time zone",
                    choices = shiftNames, selected = epgShift + 12, onPick = { i -> epgShift = i - 12; store.setInt("epgShift", epgShift); layoutTick++ }),
                SettingItem("Movie info (TMDB key)", if (tmdbKey.isBlank()) "Not set" else "On", "Cast, backdrops and trailers from a free TMDB key",
                    action = {
                        textPrompt = TextPrompt("TMDB API key (free at themoviedb.org)", tmdbKey) { k -> tmdbKey = k.trim(); store.setGString("tmdbKey", tmdbKey); layoutTick++ }
                    }),
                SettingItem("Extra guide (XMLTV URL)", if (xmltvUrl.isBlank()) "Not set" else if (xmltvBusy) "Updating…" else "On", "Adds a second guide that fills gaps. Needs the address of an XMLTV file.",
                    action = {
                        textPrompt = TextPrompt("XMLTV guide URL", xmltvUrl) { u ->
                            xmltvUrl = u.trim(); store.setString("xmltvUrl", xmltvUrl); store.setString("xmltvAt", "0")
                            if (xmltvUrl.isNotBlank()) refreshXmltv(true) else xmltvTick++
                            layoutTick++
                        }
                    }),
                SettingItem("Update extra guide now", null, "Downloads the XMLTV file again", enabled = xmltvUrl.isNotBlank(), action = { refreshXmltv(true) }),
                toggle("Prefer extra guide", "Use the extra guide first, and your provider's only where it has nothing", xmltvFirst) { xmltvFirst = !xmltvFirst; store.setFlag("xmltvFirst", xmltvFirst) },
                SettingItem("Recording padding: start", "${store.int("recPadStart", 1)} min", "Start recording this many minutes early",
                    choices = PAD_START.map { "$it min" }, selected = PAD_START.indexOf(store.int("recPadStart", 1)).coerceAtLeast(0), onPick = { i -> setLayout("recPadStart", PAD_START[i]) }),
                SettingItem("Recording padding: end", "${store.int("recPadEnd", 2)} min", "Keep recording this many minutes after the programme",
                    choices = PAD_END.map { "$it min" }, selected = PAD_END.indexOf(store.int("recPadEnd", 2)).coerceAtLeast(0), onPick = { i -> setLayout("recPadEnd", PAD_END[i]) }),
                SettingItem("Series recordings", "${seriesRules.size}", "Shows set to record every new episode", action = { seriesOpen = true }),
                SettingItem("Recordings", "${Recorder.active.size} active, ${Recorder.jobs(ctx).size} scheduled", "Play, stop or delete recordings", action = { recOpen = true }),
                SettingItem("Reminders", "${reminders.size}", "Programmes you asked to be reminded about", action = { remindersOpen = true }),
                SettingItem("Multiview", "${multiList.size}/4", "Watch up to four channels at once", closes = true, action = { loadAccount(); player.pause(); multiOpen = true }),
            )),
            SettingSection("Profiles & parental", "Profiles, Kids mode and PIN.", buildList {
                add(toggle("Ask who's watching", "Show a profile picker when the app opens (when there is more than one profile)", Dimens.askProfile) { setLayoutFlag("askProfile", !Dimens.askProfile) })
                profiles.forEach { pr ->
                    add(SettingItem(pr.name, if (pr.id == activeProfile) "Active" else "Switch", if (store.isKids(pr.id)) "Kids profile" else null,
                        action = { if (pr.id != activeProfile) { showSettings = false; switchProfile(pr.id) } }))
                }
                add(SettingItem("New profile", null, "Add a profile with its own login, favorites and layout", action = {
                    val id = store.nextProfileId()
                    profiles = profiles + Profile(id, "Profile $id")
                    store.saveProfiles(profiles)
                    showSettings = false
                    switchProfile(id)
                }))
                add(SettingItem("Change login", active.name, "Sign in with a different server or playlist", closes = true, action = { showSetup = true }))
                add(SettingItem("Rename profile", active.name, null, action = {
                    textPrompt = TextPrompt("Profile name", active.name) { n ->
                        if (n.isNotBlank()) {
                            profiles = profiles.map { if (it.id == active.id) it.copy(name = n.trim()) else it }
                            store.saveProfiles(profiles)
                        }
                    }
                }))
                add(SettingItem("Delete profile", active.name, "Removes this profile's data", enabled = active.id != 0, action = {
                    val id = active.id
                    store.deleteProfileData(id)
                    profiles = profiles.filter { it.id != id }
                    store.saveProfiles(profiles)
                    showSettings = false
                    switchProfile(0)
                }))
                add(SettingItem("Choose categories", null, "Hide the categories you never watch", closes = true, action = { pickerKids = false; pickerTab = 0; pickerOpen = true }))
                add(SettingItem("Kids profile", onOff(kidsMode), "Only allowed categories, no search, PIN to leave settings", action = {
                    if (!kidsMode) {
                        if (!hasPin) toast("Set a parental PIN first, then turn this on")
                        else {
                            store.setKids(activeProfile, true); kidsMode = true
                            resetBrowsing(); homeShelves = emptyList()
                            pickerKids = true; pickerTab = 0; pickerOpen = true; showSettings = false
                        }
                    } else {
                        store.setKids(activeProfile, false); kidsMode = false
                        resetBrowsing(); provider?.let { loadHome(it) }
                    }
                }))
                if (kidsMode) add(SettingItem("Kids: allowed categories", null, null, closes = true, action = { pickerKids = true; pickerTab = 0; pickerOpen = true }))
                add(SettingItem("Kids daily limit", if (kidsLimit == 0) "Off" else "$kidsLimit min", "Screen time per day; the parent PIN adds more",
                    choices = kidsSteps.map { if (it == 0) "Off" else "$it min" }, selected = kidsSteps.indexOf(kidsLimit).coerceAtLeast(0),
                    onPick = { i -> kidsLimit = kidsSteps[i]; store.setInt("kidsLimit", kidsLimit); layoutTick++ }))
                add(SettingItem(if (hasPin) "Change PIN" else "Set parental PIN", if (hasPin) "Set" else "Not set", null, action = {
                    if (hasPin) requirePin("Enter current PIN") { newPinFlow() } else newPinFlow()
                }))
                if (hasPin) {
                    add(toggle("Lock adult categories", "Require the PIN for adult categories", lockAdult) { lockAdult = !lockAdult; store.setFlag("lockAdult", lockAdult) })
                    if (unlocked) add(SettingItem("Lock now", null, null, action = { unlocked = false }))
                    add(SettingItem("Remove PIN", null, null, action = { requirePin("Enter PIN to remove it") { store.clearPin(); hasPin = false } }))
                }
            }),
            SettingSection("Data & backup", "Account, backups and syncing between devices.", listOf(
                SettingItem("Account & subscription", null, "Expiry, connections and server details", action = { account = null; accountOpen = true; loadAccount() }),
                SettingItem("Library cache", LIB_TTL_NAMES[store.int("libCache", 2).coerceIn(0, 3)], "Keeps your channel lists on this device so the app opens instantly",
                    choices = LIB_TTL_NAMES, selected = store.int("libCache", 2).coerceIn(0, 3), onPick = { i -> store.setInt("libCache", i); layoutTick++ }),
                SettingItem("Refresh library now", null, "Forget the saved lists and load them again", action = {
                    libCacheDir(ctx, urlText).deleteRecursively(); connect()
                }),
                toggle("Refresh in background", "Update channel lists and the extra guide while the app is closed (about every 6 hours)", store.flag("bgRefresh", true)) {
                    val on = !store.flag("bgRefresh", true); store.setFlag("bgRefresh", on); BackgroundRefresh.apply(ctx, on); layoutTick++
                },
                SettingItem("Rebuild search index", null, "Refresh the on-device search of your library", action = { provider?.let { rebuildIndex(it, true) } }),
                SettingItem("Back up settings to a file", null, null, action = { backup() }),
                SettingItem("Restore settings from backup", null, null, action = { restore() }),
                SettingItem("Share settings with another device", null, "Shows an address and code for another Vance TV", action = { startSharing() }),
                SettingItem("Get settings from another device", null, null, action = {
                    textPrompt = TextPrompt("Address shown on the other device", "") { addr ->
                        textPrompt = TextPrompt("Code shown on the other device", "") { code -> pullSync(addr, code) }
                    }
                }),
            )),
            SettingSection("About", "Version and updates.", listOf(
                SettingItem("Vance TV", BuildConfig.VERSION_NAME, "Version of this app", enabled = false),
                SettingItem("Check for updates", null, "Install the newest version from GitHub", action = { updateOpen = true; checkUpdates(false) }),
                SettingItem("Playback diagnostics", null, Diag.summary(), action = { Diag.reset(); layoutTick++ }),
                SettingItem("This device", android.os.Build.MODEL, "Android ${android.os.Build.VERSION.RELEASE}", enabled = false),
                SettingItem("Built with", null, "Media3 (playback), OkHttp (network), Coil (images), Jetpack Compose, FFmpeg decoder (jellyfin), Inter typeface. Movie data from TMDB.", enabled = false),
            )),
        )
    }
    val settingSections = if (showSettings) buildSettingSections() else emptyList()

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
    LaunchedEffect(provider) { if (provider != null) bootDone = true }
    LaunchedEffect(busy) { if (busy) sawBusy = true else if (sawBusy) bootDone = true }
    LaunchedEffect(Unit) { delay(20_000); bootDone = true }
    LaunchedEffect(pendingHome) {
        val p = pendingHome ?: return@LaunchedEffect
        delay(350)
        focusedHome = p
    }
    val showSplash = !bootDone && urlText.isNotBlank() && provider == null && !showWho
    val screenId = when {
        showWho -> 14
        showSplash -> 0
        provider == null || showSetup -> 1
        showSettings -> 12
        searchOpen -> 2
        pickerOpen -> 3
        multiOpen -> 4
        appearanceOpen -> 5
        fullscreen && current != null -> 0
        guideOpen && guide != null -> 6
        detailEntry != null -> 7
        else -> 100 + tab.ordinal
    }
    LaunchedEffect(screenId) {
        if (screenId == 0 || Dimens.animLevel == 2) {
            screenAlpha.snapTo(1f); shownScreen = screenId
        } else {
            screenAlpha.snapTo(0.25f); shownScreen = screenId
            screenAlpha.animateTo(1f, tween(if (Dimens.animLevel == 1) 100 else Motion.MED, easing = Motion.ease))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val a = if (screenId == 0) 1f else if (shownScreen == screenId) screenAlpha.value else 0.25f
                alpha = a
                if (Dimens.animLevel == 0) { val k = 0.985f + 0.015f * ((a - 0.25f) / 0.75f).coerceIn(0f, 1f); scaleX = k; scaleY = k }
            }
            .onPreviewKeyEvent { ev ->
                if (!fullscreen || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                infoTick++
                if (trackPanelOpen) {
                    trackTick++
                    return@onPreviewKeyEvent when (ev.key) {
                        Key.DirectionLeft, Key.Captions, Key.MediaAudioTrack -> { trackPanelOpen = false; true }
                        else -> false // Up/Down/OK go to the rows in the panel
                    }
                }
                val stripList = guideChannels
                stripIdx?.let { si ->
                    when (ev.key) {
                        Key.DirectionUp, Key.ChannelUp -> { stripIdx = (si - 1).mod(stripList.size.coerceAtLeast(1)); stripTick++; return@onPreviewKeyEvent true }
                        Key.DirectionDown, Key.ChannelDown -> { stripIdx = (si + 1).mod(stripList.size.coerceAtLeast(1)); stripTick++; return@onPreviewKeyEvent true }
                        Key.DirectionCenter, Key.Enter -> {
                            stripList.getOrNull(si)?.let { current = it }
                            stripIdx = null
                            return@onPreviewKeyEvent true
                        }
                        else -> {}
                    }
                }
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
                    Key.DirectionUp, Key.ChannelUp -> if (isLive) {
                        if (zapStrip && stripList.isNotEmpty()) {
                            val at = stripList.indexOfFirst { it.key() == current?.key() }.coerceAtLeast(0)
                            stripIdx = (at - 1).mod(stripList.size); stripTick++
                        } else step(-1)
                        true
                    } else { trackPanelOpen = true; true }
                    Key.DirectionDown, Key.ChannelDown -> if (isLive) {
                        if (zapStrip && stripList.isNotEmpty()) {
                            val at = stripList.indexOfFirst { it.key() == current?.key() }.coerceAtLeast(0)
                            stripIdx = (at + 1).mod(stripList.size); stripTick++
                        } else step(1)
                        true
                    } else false
                    Key.DirectionLeft -> if (isLive) { openPanel(); true } else {
                        player.seekTo((player.currentPosition - Dimens.skipBack * 1000L).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> if (!isLive) { player.seekTo(player.currentPosition + Dimens.skipFwd * 1000L); true } else { trackPanelOpen = true; true }
                    Key.Captions, Key.MediaAudioTrack -> { trackPanelOpen = true; true }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.MediaRewind -> {
                        if (isLive && !player.isCurrentMediaItemSeekable) toast("This stream can't be rewound")
                        else player.seekTo((player.currentPosition - if (isLive) 15_000 else Dimens.skipBack * 3000L).coerceAtLeast(0))
                        true
                    }
                    Key.MediaFastForward -> {
                        if (isLive) player.seekToDefaultPosition() else player.seekTo(player.currentPosition + Dimens.skipFwd * 3000L)
                        true
                    }
                    Key.Menu -> { showOptions = true; true }
                    else -> false
                }
            }
            .then(if (fullscreen) Modifier.focusRequester(rootFocus).focusable() else Modifier)
    ) {
        if (showWho) {
            ProfilePicker(profiles, store::isKids, activeProfile) { pr ->
                showWho = false
                if (pr.id != activeProfile) switchProfile(pr.id)
            }
        } else if (showSplash) {
            SplashScreen()
        } else if (showSettings && provider != null && !showSetup) {
            SettingsScreen(settingSections) { showSettings = false; listVersion++ }
        } else if (provider == null || showSetup) {
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
                PickerCat(c, regionOf(c.name), counts[pk]?.get(c.id), if (pickerKids) catKey(pk, c.id) in kidsCats else catKey(pk, c.id) !in hiddenCats)
            }
            CategoryPicker(
                tabLabels = tabs.map { t ->
                    (if (t == Tab.LIVE) "Live" else if (t == Tab.MOVIES) "Movies" else "Series") + "  ·  " + (catCache[t]?.size ?: "…")
                },
                tab = ix, onTab = { pickerTab = it }, cats = pcats,
                onToggle = { setPicked(pk, listOf(it.cat.id), !it.checked) },
                onToggleRegion = { r, check -> setPicked(pk, pcats.filter { c -> c.region == r }.map { c -> c.cat.id }, check) },
                onQuick = { which ->
                    when (which) {
                        0 -> setPicked(pk, pcats.filter { isAdult(it.cat.name) }.map { it.cat.id }, false)
                        1 -> setPicked(pk, pcats.map { it.cat.id }, true)
                        else -> setPicked(pk, pcats.map { it.cat.id }, false)
                    }
                },
                title = if (pickerKids) "Allowed for kids" else "Choose your categories",
                subtitle = if (pickerKids) "Only these show in this profile. Open Settings with your PIN to change them."
                else "Pick the ones you actually watch. Everything else is hidden, never deleted, and you can reopen this from Settings.",
                onDone = {
                    store.setString("picked", "1"); pickerOpen = false; listVersion++
                    if (pickerKids) { pickerKids = false; homeShelves = emptyList(); provider?.let { loadHome(it) } }
                },
                onSkip = { store.setString("picked", "1"); pickerOpen = false; pickerKids = false; listVersion++ },
            )
        } else if (multiOpen) {
            MultiViewScreen(
                entries = multiList,
                urlOf = { provider?.streamUrl(it.copy(start = 0)).orEmpty() },
                nameOf = ::nameOf,
                warning = account?.let { a ->
                    if (a.maxConnections in 1 until multiList.size) "Your plan allows ${a.maxConnections} stream(s); ${multiList.size} tiles may not all play." else null
                },
                onOpen = { e -> multiOpen = false; playNow(e, true) },
                onRemove = { e -> multiList = multiList.filter { it.key() != e.key() } },
            )
        } else if (appearanceOpen) {
            AppearanceScreen(
                accentIdx, bgIdx, cardIdx, textIdx, animIdx, startIdx, uiIdx,
                onUi = { uiIdx = it; store.setInt("ui", it); applyNow() },
                onTheme = { a, b -> accentIdx = a; bgIdx = b; store.setInt("accent", a); store.setInt("bg", b); applyNow() },
                railLabels = Dimens.railLabels,
                onRail = { Dimens.railLabels = it; store.setFlag("railLabels", it) },
                sounds = Dimens.sounds,
                onSounds = { Dimens.sounds = it; store.setFlag("sounds", it) },
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
                stripIdx?.let { si ->
                    ChannelStrip(guideChannels, si, ::nameOf, { c -> guide?.cache?.get(c.id).orEmpty() }, now)
                    LaunchedEffect(guideChannels.getOrNull(si)?.id) { guideChannels.getOrNull(si)?.let { guide?.load(it) } }
                }
                FullscreenOverlay(
                    showInfo && stripIdx == null, e, titleFor(e), isLive, isFav(e), epg, now, position, duration,
                    if (isLive) (if (zapStrip) "Up/Down: browse channels" else "Up/Down: channel") + "  ·  Left: channel list  ·  Right: audio & subtitles  ·  ⏪ ⏩ rewind / live  ·  Menu: options"
                    else "Left/Right: seek 10s  ·  Up: audio & subtitles  ·  OK: pause  ·  Menu: options",
                    catchUp = isCatchUp, badge = streamBadge(player), behindLiveSec = behindLive, sleepLeftMin = sleepLeft,
                )
                nextUp?.let { NextUpCard(it.title, nextUpSecs) }
                TrackPanel(
                    visible = trackPanelOpen,
                    details = run { @Suppress("UNUSED_EXPRESSION") tracksTick; streamDetails(player) },
                    audio = run { @Suppress("UNUSED_EXPRESSION") tracksTick; audioRows(player) },
                    text = run { @Suppress("UNUSED_EXPRESSION") tracksTick; textRows(player) },
                    subSize = subSize, subNames = SUB_NAMES, focus = trackFocus,
                    onAudio = { selectAudio(player, it); tracksTick++ },
                    onText = { selectText(player, it); tracksTick++ },
                    onSubSize = { subSize = it; store.setInt("subSize", it) },
                )
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
                    reminderKeys = reminderKeys,
                    onRemind = { ch, p -> programTarget = ch to p },
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
            val navItems = listOf(
                RailItem(RailIconType.HOME, "Home", tab == Tab.HOME),
                RailItem(RailIconType.SEARCH, "Search", false),
                RailItem(RailIconType.LIVE, "Live TV", tab == Tab.LIVE),
                RailItem(RailIconType.MOVIES, "Movies", tab == Tab.MOVIES),
                RailItem(RailIconType.SERIES, "Series", tab == Tab.SERIES),
                RailItem(RailIconType.FAVORITES, "Favorites", tab == Tab.FAVORITES),
                RailItem(RailIconType.GUIDE, "Guide", false),
                RailItem(RailIconType.SETTINGS, "Settings", false),
            ).filter { !kidsMode || it.icon != RailIconType.SEARCH }
            val onNav: (RailIconType) -> Unit = { icon ->
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
            val railWidth = if (!Dimens.railLabels) 64.dp else if (isTv) 84.dp else 66.dp
            Box(Modifier.fillMaxSize()) {
                if (Dimens.topNav) TopNav(navItems, Modifier.align(Alignment.TopStart), onNav)
                else IconRail(navItems, Modifier.align(Alignment.TopStart).width(railWidth), onNav)
                Box(Modifier.fillMaxSize().padding(start = if (Dimens.topNav) 0.dp else railWidth, top = if (Dimens.topNav) 60.dp else 0.dp)) {
                    when (tab) {
                        Tab.HOME -> {
                            val lastLive = store.string("last")?.let(::decodeEntry)?.takeIf { it.isLiveNow && visible(it) }
                            val cont = recentEntries.filter { it.kind != Kind.LIVE && visible(it) }.take(20)
                            val favs = favoriteEntries.filter { visible(it) }.take(20)
                            val heroPool = (cont.filter { it.kind != Kind.LIVE }.take(3) +
                                homeShelves.asSequence().flatMap { it.items.asSequence() }.filter { it.kind != Kind.LIVE && visible(it) }.take(12).toList()).distinctBy { it.key() }.take(6)
                            var heroIdx by remember { mutableIntStateOf(0) }
                            LaunchedEffect(heroPool.size, focusedHome == null, rotateHomeBanner) {
                                while (rotateHomeBanner && focusedHome == null && heroPool.size > 1 && Dimens.homeHero && Dimens.animLevel != 2) {
                                    delay(9000); heroIdx = (heroIdx + 1) % heroPool.size
                                }
                            }
                            val baseHero = heroPool.getOrNull(heroIdx) ?: cont.firstOrNull()
                                ?: homeShelves.firstNotNullOfOrNull { s -> s.items.firstOrNull { visible(it) } }
                            val hero = focusedHome ?: baseHero
                            val shelves = buildList {
                                if (cont.isNotEmpty()) add(Triple("Continue watching", cont, true))
                                val channels = favs.filter { it.kind == Kind.LIVE }
                                val titles = favs.filter { it.kind != Kind.LIVE }
                                if (channels.isNotEmpty()) add(Triple("Favorite channels", channels, true))
                                if (titles.isNotEmpty()) add(Triple("Favorite movies & series", titles, false))
                                val seed = cont.firstOrNull { it.kind == Kind.MOVIE || it.kind == Kind.SERIES || it.kind == Kind.EPISODE }
                                if (seed != null && seed.cat.isNotBlank()) {
                                    val seen = cont.map { it.key() }.toSet()
                                    val like = homeShelves.asSequence().flatMap { it.items.asSequence() }.filter { it.cat == seed.cat && it.kind != Kind.LIVE && it.key() !in seen && visible(it) }.distinctBy { it.key() }.take(20).toList()
                                    if (like.size >= 4) add(Triple("Because you watched ${if (seed.parent.isNotBlank()) seed.parent else seed.title}", like, false))
                                }

                                val mine = myList.mapNotNull(::decodeEntry).filter { visible(it) }.take(20)
                                if (mine.isNotEmpty()) add(Triple("My list", mine, false))
                                homeShelves.forEach { add(Triple(it.title, it.items.filter { e -> visible(e) }, false)) }
                            }
                            LaunchedEffect(hero?.key(), tmdbKey) {
                                heroBackdrop = ""
                                val h = hero ?: return@LaunchedEffect
                                if (tmdbKey.isNotBlank() && h.kind != Kind.LIVE) {
                                    delay(if (focusedHome == null && heroIdx == 0) 2500L else 450L) // let the library and posters load first
                                    heroBackdrop = runCatching { Tmdb.backdropFor(tmdbKey, h.title, h.kind == Kind.SERIES) }.getOrDefault("")
                                }
                            }
                            val shownShelves = shelves.map { (t, list, w) ->
                                Triple(t, list.filter { e ->
                                    when (homeFilter) {
                                        1 -> e.kind == Kind.MOVIE
                                        2 -> e.kind == Kind.SERIES || e.kind == Kind.EPISODE
                                        3 -> e.kind == Kind.LIVE
                                        4 -> e.title.contains("4k", ignoreCase = true)
                                        else -> true
                                    }
                                }, w)
                            }.filter { it.second.isNotEmpty() }
                            if (Dimens.ambient) AmbientBackground(Modifier.fillMaxSize())
                            LazyColumn(Modifier.fillMaxSize(), state = homeState, contentPadding = PaddingValues(bottom = 40.dp)) {
                                if (lastLive != null && homeFilter in listOf(0, 3)) item {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("LAST CHANNEL", color = Palette.muted, style = MaterialTheme.typography.labelLarge)
                                            Text(titleFor(lastLive), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                        }
                                        ActionButton("Resume channel", Modifier.focusRequester(firstItem), icon = Icons.Rounded.PlayArrow, onClick = { playNow(lastLive, true) })
                                    }
                                }
                                if (hero != null && Dimens.homeHero) item {
                                    HeroBanner(
                                        hero, if (cont.contains(hero)) "Continue watching" else "Featured",
                                        titleFor(hero), subFor(hero),
                                        canOpenDetails = hero.kind == Kind.MOVIE || hero.kind == Kind.SERIES,
                                        playFocus = if (lastLive == null || homeFilter !in listOf(0, 3)) Modifier.focusRequester(firstItem) else Modifier,
                                        onPlay = { if (hero.kind == Kind.SERIES) onEntry(hero) else playNow(hero, true) },
                                        onDetails = { onEntry(hero) },
                                        backdrop = heroBackdrop,
                                        dots = if (rotateHomeBanner && focusedHome == null) heroPool.size else 0, dot = heroIdx,
                                    )
                                }
                                if (Dimens.homeChips) item {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        itemsIndexed(listOf("All", "Movies", "Series", "Live", "4K")) { i, label ->
                                            Chip(label, homeFilter == i) { homeFilter = i }
                                        }
                                    }
                                }
                                itemsIndexed(shownShelves) { i, (title, items, wide) ->
                                    ShelfRow(
                                        title, items, wide, ::titleFor, ::subFor, ::progressOf, ::isFav,
                                        onLongClick = { menuTarget = MenuTarget.EntryT(it) }, onOpen = ::onEntry,
                                        firstFocus = if ((lastLive == null || homeFilter !in listOf(0, 3)) && (hero == null || !Dimens.homeHero) && i == 0) Modifier.focusRequester(firstItem) else Modifier,
                                        onFocusEntry = { pendingHome = it },
                                    )
                                }
                                if (hero == null && shelves.isEmpty()) item {
                                    if (busy) Column { repeat(2) { SkeletonShelf() } }
                                    else EmptyState("Nothing here yet", "Open Live TV, Movies or Series to start watching.", "Open Live TV") { openTab(Tab.LIVE) }
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
                                onMenu = {
                                    menuTarget = if (it.cat.id.startsWith("g:")) MenuTarget.GroupT(it.cat.id.removePrefix("g:"))
                                    else MenuTarget.CatT(Kind.LIVE, it.cat)
                                },
                                listState = catListState,
                                groups = if (catRegion == "*all" && catFilter.isBlank()) groupRows else emptyList(),
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
                                    if (busy && shownEntries.isEmpty()) items(8) { SkeletonRow() }
                                    if (!busy && shownEntries.isEmpty()) item {
                                        EmptyState(
                                            "No channels here",
                                            if (query.isNotBlank()) "Nothing matches \"$query\"." else "Pick a category on the left to see its channels.",
                                        )
                                    }
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
