@file:OptIn(UnstableApi::class, ExperimentalFoundationApi::class)

package com.greenslocks.iptv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Up to four live channels at once. Sound follows focus; OK opens one fullscreen, long-press removes it. */
@Composable
fun MultiViewScreen(
    entries: List<Entry>,
    urlOf: (Entry) -> String,
    nameOf: (Entry) -> String,
    warning: String?,
    onOpen: (Entry) -> Unit,
    onRemove: (Entry) -> Unit,
) {
    val ctx = LocalContext.current
    val ids = entries.map { it.id }
    val players = remember(ids) {
        entries.map { e ->
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(urlOf(e)))
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(players) { onDispose { players.forEach { it.release() } } }
    var focused by remember { mutableIntStateOf(0) }
    LaunchedEffect(focused, players) { players.forEachIndexed { i, p -> p.volume = if (i == focused) 1f else 0f } }
    val reqs = remember(ids) { entries.map { FocusRequester() } }
    LaunchedEffect(ids) { delay(250); runCatching { reqs.firstOrNull()?.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (warning != null) {
            Text(warning, color = Palette.live, modifier = Modifier.fillMaxWidth().background(Palette.surface).padding(10.dp))
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Multiview is empty.\nLong-press a live channel and choose \"Add to multiview\".",
                    color = Palette.muted,
                )
            }
        } else {
            val cols = if (entries.size <= 1) 1 else 2
            entries.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEachIndexed { c, e ->
                        val i = r * cols + c
                        Box(
                            Modifier.weight(1f).fillMaxHeight().padding(3.dp)
                                .border(if (focused == i) 3.dp else 0.dp, if (focused == i) Palette.accent else Color.Transparent)
                                .focusRequester(reqs[i])
                                .onFocusChanged { if (it.isFocused) focused = i }
                                .combinedClickable(onClick = { onOpen(e) }, onLongClick = { onRemove(e) })
                        ) {
                            AndroidView(
                                factory = { PlayerView(it).apply { useController = false } },
                                update = { it.player = players.getOrNull(i) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Row(
                                Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (focused == i) { SpeakerIcon(16.dp, Color.White); Spacer(Modifier.width(6.dp)) }
                                Text(nameOf(e), color = Color.White)
                            }
                        }
                    }
                    if (row.size < cols) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
