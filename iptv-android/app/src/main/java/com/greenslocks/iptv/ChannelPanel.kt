package com.greenslocks.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Channel list that slides in over fullscreen live video (press Left). Mode 0 lists the channels of
 * one category; mode 1 lists categories so you can switch without leaving the picture.
 */
@Composable
fun BoxScope.ChannelPanel(
    visible: Boolean,
    mode: Int,
    title: String,
    channels: List<Entry>,
    categories: List<Pair<Category, String>>,
    currentKey: String?,
    currentCatId: String?,
    lastChannel: Entry?,
    now: Long,
    nameOf: (Entry) -> String,
    isFav: (Entry) -> Boolean,
    programmesOf: (Entry) -> List<Programme>,
    loadEpg: suspend (Entry) -> Unit,
    focus: FocusRequester,
    onChannel: (Entry) -> Unit,
    onCategory: (Category) -> Unit,
    onLast: () -> Unit,
) {
    val off = Dimens.animLevel == 2
    AnimatedVisibility(
        visible, Modifier.align(Alignment.CenterStart),
        enter = if (off) EnterTransition.None else slideInHorizontally { -it } + fadeIn(),
        exit = if (off) ExitTransition.None else slideOutHorizontally { -it } + fadeOut(),
    ) {
        Column(
            Modifier.width(310.dp).fillMaxHeight().background(Palette.bg.copy(alpha = 0.92f)).padding(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                if (mode == 0) "◀ categories      ▶ close" else "▶ back to channels",
                color = Palette.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            if (mode == 0) {
                val focusIdx = channels.indexOfFirst { it.key() == currentKey }.coerceAtLeast(0)
                LazyColumn(Modifier.fillMaxSize()) {
                    if (lastChannel != null) item {
                        ListRow("↺  Last channel", nameOf(lastChannel), false, Modifier.padding(bottom = 6.dp)) { onLast() }
                    }
                    itemsIndexed(channels) { i, e ->
                        LaunchedEffect(e.id) { loadEpg(e) }
                        val cur = nowNext(programmesOf(e), now).first
                        ChannelRow(
                            nameOf(e), e.icon, e.num, isFav(e), e.key() == currentKey,
                            cur?.title,
                            cur?.let { (now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1) },
                            if (i == focusIdx) Modifier.focusRequester(focus) else Modifier,
                            onLongClick = {},
                        ) { onChannel(e) }
                    }
                    if (channels.isEmpty()) item { Text("Loading channels…", color = Palette.muted, modifier = Modifier.padding(12.dp)) }
                }
            } else {
                val focusIdx = categories.indexOfFirst { it.first.id == currentCatId }.coerceAtLeast(0)
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(categories) { i, (c, name) ->
                        ListRow(
                            name, "", c.id == currentCatId,
                            if (i == focusIdx) Modifier.focusRequester(focus) else Modifier,
                        ) { onCategory(c) }
                    }
                }
            }
        }
    }
}
