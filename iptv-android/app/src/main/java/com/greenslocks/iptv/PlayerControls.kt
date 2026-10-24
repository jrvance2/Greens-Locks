@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.greenslocks.iptv

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

private fun Tracks.audio(): List<Tracks.Group> = groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
private fun Tracks.text(): List<Tracks.Group> = groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

private fun trackName(g: Tracks.Group, index: Int): String {
    val f = g.getTrackFormat(0)
    val lang = f.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }
    return f.label ?: lang ?: "Track ${index + 1}"
}

fun audioLabel(p: Player): String {
    val gs = p.currentTracks.audio()
    if (gs.isEmpty()) return "Audio: only one track"
    val i = gs.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    return "Audio: ${trackName(gs[i], i)}  (${gs.size} available)"
}

fun cycleAudio(p: Player) {
    val gs = p.currentTracks.audio()
    if (gs.size < 2) return
    val i = gs.indexOfFirst { it.isSelected }.coerceAtLeast(0)
    val next = gs[(i + 1) % gs.size]
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
}

private fun textOff(p: Player, gs: List<Tracks.Group>) =
    C.TRACK_TYPE_TEXT in p.trackSelectionParameters.disabledTrackTypes || gs.none { it.isSelected }

fun textLabel(p: Player): String {
    val gs = p.currentTracks.text()
    if (gs.isEmpty()) return "Subtitles: none available"
    if (textOff(p, gs)) return "Subtitles: off  (${gs.size} available)"
    val i = gs.indexOfFirst { it.isSelected }
    return "Subtitles: ${trackName(gs[i], i)}"
}

/** Off -> first track -> next ... -> off. */
fun cycleText(p: Player) {
    val gs = p.currentTracks.text()
    if (gs.isEmpty()) return
    val b = p.trackSelectionParameters.buildUpon()
    if (textOff(p, gs)) {
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(gs[0].mediaTrackGroup, 0))
    } else {
        val i = gs.indexOfFirst { it.isSelected }
        if (i + 1 < gs.size) b.setOverrideForType(TrackSelectionOverride(gs[i + 1].mediaTrackGroup, 0))
        else b.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    }
    p.trackSelectionParameters = b.build()
}

internal fun videoCodec(mime: String?): String = when (mime) {
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
    null -> ""
    else -> mime.substringAfter('/').uppercase()
}

internal fun audioCodec(mime: String?): String = when (mime) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3 -> "Dolby Digital+"
    MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_MPEG -> "MP3"
    null -> ""
    else -> mime.substringAfter('/').uppercase()
}

private fun channelLabel(n: Int): String = when (n) {
    1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> if (n > 0) "$n ch" else ""
}

/** Short badge for the fullscreen overlay, e.g. "4K · HEVC · 59 fps · HDR10". */
fun streamBadge(p: Player): String {
    val v = (p as? ExoPlayer)?.videoFormat ?: return ""
    val res = when {
        v.height >= 2160 -> "4K"; v.height >= 1440 -> "1440p"; v.height >= 1080 -> "1080p"
        v.height >= 720 -> "720p"; v.height > 0 -> "${v.height}p"; else -> ""
    }
    val hdr = when (v.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10"; C.COLOR_TRANSFER_HLG -> "HLG"; else -> ""
    }
    val fps = if (v.frameRate > 0) "%.0f fps".format(v.frameRate) else ""
    return listOf(res, videoCodec(v.sampleMimeType), fps, hdr).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** Detailed line for the Options menu. */
fun streamDetails(p: Player): String {
    val e = p as? ExoPlayer ?: return "Stream: unknown"
    val v = e.videoFormat
    val a = e.audioFormat
    val video = if (v == null) "no video yet" else listOfNotNull(
        if (v.width > 0) "${v.width}×${v.height}" else null,
        videoCodec(v.sampleMimeType).ifEmpty { null },
        if (v.frameRate > 0) "%.1f fps".format(v.frameRate) else null,
        if (v.bitrate > 0) "%.1f Mbps".format(v.bitrate / 1_000_000f) else null,
        when (v.colorInfo?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10"; C.COLOR_TRANSFER_HLG -> "HLG"; else -> null },
    ).joinToString(" · ")
    val audio = if (a == null) "" else "   |   Audio: " + listOf(audioCodec(a.sampleMimeType), channelLabel(a.channelCount)).filter { it.isNotEmpty() }.joinToString(" ")
    return "Stream: $video$audio"
}

data class TrackRow(val label: String, val selected: Boolean)

/** Audio tracks for the quick panel, e.g. "English  ·  Dolby Digital 5.1". */
fun audioRows(p: Player): List<TrackRow> = p.currentTracks.audio().mapIndexed { i, g ->
    val f = g.getTrackFormat(0)
    val detail = listOf(audioCodec(f.sampleMimeType), channelLabel(f.channelCount)).filter { it.isNotEmpty() }.joinToString(" ")
    TrackRow(trackName(g, i) + if (detail.isNotEmpty()) "  \u00B7  $detail" else "", g.isSelected)
}

fun selectAudio(p: Player, index: Int) {
    val g = p.currentTracks.audio().getOrNull(index) ?: return
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
}

/** Subtitle choices for the quick panel; the first row is always "Off". Empty when the stream has none. */
fun textRows(p: Player): List<TrackRow> {
    val gs = p.currentTracks.text()
    if (gs.isEmpty()) return emptyList()
    val off = textOff(p, gs)
    return listOf(TrackRow("Off", off)) + gs.mapIndexed { i, g -> TrackRow(trackName(g, i), !off && g.isSelected) }
}

fun selectText(p: Player, index: Int) {
    val gs = p.currentTracks.text()
    val b = p.trackSelectionParameters.buildUpon()
    if (index == 0) {
        b.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        val g = gs.getOrNull(index - 1) ?: return
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
    }
    p.trackSelectionParameters = b.build()
}
