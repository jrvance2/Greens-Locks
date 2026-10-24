package com.greenslocks.iptv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.delay

/** One refresh of the troubleshooting panel; kept free of Compose so the wording is easy to follow. */
fun statsLines(p: ExoPlayer, bandwidthBps: Long, droppedNow: Long, droppedBefore: Long, display: String): List<String> {
    val v = p.videoFormat
    val a = p.audioFormat
    val video = if (v == null) "no video yet" else listOfNotNull(
        if (v.width > 0) "${v.width}x${v.height}" else null,
        videoCodec(v.sampleMimeType).ifEmpty { null },
        if (v.frameRate > 0) "%.2f fps".format(v.frameRate) else null,
        if (v.bitrate > 0) "stream %.1f Mbps".format(v.bitrate / 1_000_000f) else null,
        when (v.colorInfo?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10"; C.COLOR_TRANSFER_HLG -> "HLG"; else -> null },
    ).joinToString("  ")
    val audio = if (a == null) "no audio yet" else listOf(audioCodec(a.sampleMimeType), a.channelCount.takeIf { it > 0 }?.let { "$it ch" }.orEmpty())
        .filter { it.isNotEmpty() }.joinToString(" ")
    val buffered = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0) / 1000.0
    val state = when (p.playbackState) {
        Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> if (p.isPlaying) "playing" else "paused"
        Player.STATE_ENDED -> "ended"; else -> "idle"
    }
    return listOf(
        video,
        "Network %.1f Mbps measured  |  buffer %.1f s  |  rebuffers %d  |  %s".format(bandwidthBps / 1_000_000.0, buffered, Diag.rebuffers, state),
        "Dropped frames +${droppedNow - droppedBefore} in the last second  |  total $droppedNow",
        "Audio $audio  |  ${Diag.decoderText()}",
        "Display $display",
    )
}

/** Small panel over the video for working out why a stream stutters: network, decoder or display. */
@Composable
fun BoxScope.StreamStats(player: ExoPlayer, ctx: Context, display: () -> String) {
    var lines by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(player) {
        val meter = DefaultBandwidthMeter.getSingletonInstance(ctx)
        val shown = display()
        var before = Diag.dropped
        while (true) {
            val now = Diag.dropped
            lines = statsLines(player, meter.bitrateEstimate, now, before, shown)
            before = now
            delay(1000)
        }
    }
    Column(
        Modifier.align(Alignment.TopStart).padding(28.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        lines.forEach { Text(it, color = Color.White, fontSize = 13.sp, maxLines = 1) }
    }
}
