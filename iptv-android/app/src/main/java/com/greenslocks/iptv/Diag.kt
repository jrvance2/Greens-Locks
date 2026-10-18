package com.greenslocks.iptv

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Simple playback numbers so "is it faster?" has an answer. Shown in Settings > About. */
object Diag {
    private var requestedAt = 0L
    private var starts = 0
    private var totalMs = 0L
    var lastStartMs by mutableLongStateOf(-1L)
    var avgStartMs by mutableLongStateOf(-1L)
    var stalls by mutableIntStateOf(0)
    var reconnects by mutableIntStateOf(0)
    var dropped by mutableLongStateOf(0L)
    var videoDecoder by mutableStateOf("")

    /** "hardware decoder c2.amlogic.hevc.decoder" or "software decoder ...", or a hint before anything has played. */
    fun decoderText(): String = when {
        videoDecoder.isBlank() -> "play something to see the decoder"
        videoDecoder.startsWith("OMX.google") || videoDecoder.startsWith("c2.android") || videoDecoder.contains("sw", true) && !videoDecoder.contains("swiz") ->
            "software decoder $videoDecoder"
        else -> "hardware decoder $videoDecoder"
    }

    fun requested() { requestedAt = SystemClock.elapsedRealtime() }

    fun firstFrame() {
        if (requestedAt == 0L) return
        val ms = SystemClock.elapsedRealtime() - requestedAt
        requestedAt = 0L
        if (ms !in 1..60_000) return
        lastStartMs = ms; starts++; totalMs += ms; avgStartMs = totalMs / starts
    }

    fun reset() { starts = 0; totalMs = 0; lastStartMs = -1; avgStartMs = -1; stalls = 0; reconnects = 0; dropped = 0 }

    fun summary(): String =
        if (starts == 0) "No playback yet"
        else "Last start %.1fs  ·  average %.1fs over %d  ·  stalls %d  ·  reconnects %d  ·  dropped frames %d"
            .format(lastStartMs / 1000.0, avgStartMs / 1000.0, starts, stalls, reconnects, dropped)
}
