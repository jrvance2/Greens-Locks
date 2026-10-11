package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Up/Down in the player: browse a few channels with what's on, OK tunes, Back or a pause closes it. */
@Composable
fun BoxScope.ChannelStrip(
    channels: List<Entry>,
    selected: Int,
    nameOf: (Entry) -> String,
    programmesOf: (Entry) -> List<Programme>,
    now: Long,
) {
    if (channels.isEmpty()) return
    val window = (-2..2).map { off -> (selected + off).mod(channels.size) }
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).clip(RoundedCornerShape(22.dp))
            .background(Palette.bg.copy(alpha = 0.9f)).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            window.forEachIndexed { slot, idx ->
                val e = channels[idx]
                val sel = slot == 2
                val (cur, next) = nowNext(programmesOf(e), now)
                Column(
                    Modifier.width(if (sel) 230.dp else 170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Palette.surfaceHi else Palette.surface)
                        .then(if (sel) Modifier.border(3.dp, Palette.accent, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(e.icon, nameOf(e), Modifier.size(width = 52.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            if (e.num > 0) Text("${e.num}", color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                            Text(nameOf(e), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                    Text(
                        cur?.title ?: "No guide data", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (sel) Palette.text else Palette.muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (cur != null) {
                        ProgressLine(((now - cur.start).toFloat() / (cur.stop - cur.start).coerceAtLeast(1)), Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    if (sel && next != null) {
                        Text("Next: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Text("Up/Down: browse   ·   OK: watch   ·   Back: close", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp).height(18.dp))
    }
}
