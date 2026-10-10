package com.greenslocks.iptv

import androidx.media3.common.C
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
