package com.greenslocks.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Quick audio and subtitle picker for the player. Opens from the remote with Right (live), Up (movies and
 * episodes), or a CC / audio button if the remote has one. Back closes it; Left closes it from the track lists and moves between the size chips.
 */
@Composable
fun BoxScope.TrackPanel(
    visible: Boolean,
    details: String,
    audio: List<TrackRow>,
    text: List<TrackRow>,
    subSize: Int,
    subNames: List<String>,
    focus: FocusRequester,
    onAudio: (Int) -> Unit,
    onText: (Int) -> Unit,
    onSubSize: (Int) -> Unit,
    onSizeFocus: (Boolean) -> Unit = {},
) {
    AnimatedVisibility(
        visible, Modifier.align(Alignment.CenterEnd),
        enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        Column(
            Modifier.width(400.dp).fillMaxHeight().background(Palette.bg.copy(alpha = 0.95f))
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text("Audio & subtitles", style = MaterialTheme.typography.headlineSmall)
            Text(details, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

            Label("AUDIO")
            if (audio.isEmpty()) Text("Only one audio track in this stream", color = Palette.muted)
            audio.forEachIndexed { i, r ->
                TrackItem(r, if (i == 0) Modifier.focusRequester(focus) else Modifier) { onAudio(i) }
            }

            Label("SUBTITLES")
            if (text.isEmpty()) Text("No subtitles in this stream", color = Palette.muted)
            text.forEachIndexed { i, r ->
                TrackItem(r, if (audio.isEmpty() && i == 0) Modifier.focusRequester(focus) else Modifier) { onText(i) }
            }

            Label("SUBTITLE SIZE")
            Row(Modifier.onFocusChanged { onSizeFocus(it.hasFocus) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subNames.forEachIndexed { i, n ->
                    Chip(n, subSize == i, if (audio.isEmpty() && text.isEmpty() && i == 0) Modifier.focusRequester(focus) else Modifier) { onSubSize(i) }
                }
            }
            Text("Left (on the lists) or Back to close", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Palette.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun TrackItem(row: TrackRow, modifier: Modifier, onClick: () -> Unit) {
    ListRow((if (row.selected) "✓  " else "      ") + row.label, "", row.selected, modifier.padding(vertical = 2.dp), onClick = onClick)
}
