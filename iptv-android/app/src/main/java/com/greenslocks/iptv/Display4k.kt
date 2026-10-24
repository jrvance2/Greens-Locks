package com.greenslocks.iptv

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the TV is showing and, on older Android, switching its refresh rate to match the video. */
object DisplayTools {
    private fun display(a: Activity): Display? =
        if (Build.VERSION.SDK_INT >= 30) a.display else @Suppress("DEPRECATION") a.windowManager.defaultDisplay

    private fun hz(r: Float) = if (abs(r - r.roundToInt()) < 0.02f) "${r.roundToInt()}" else "%.2f".format(r)

    /** Width of the TV's current output mode in pixels (a Fire TV renders the UI at 1080p but can still output 4K). */
    fun physicalWidth(a: Activity?): Int {
        val d = a?.let(::display) ?: return 0
        return if (Build.VERSION.SDK_INT >= 23) d.mode.physicalWidth else a!!.resources.displayMetrics.widthPixels
    }

    /** e.g. "3840x2160 at 59.94 Hz  ·  HDR10, HLG, Dolby Vision". */
    fun describe(a: Activity?): String {
        val d = a?.let(::display) ?: return "Unknown"
        val m = if (Build.VERSION.SDK_INT >= 23) d.mode else null
        val size = m?.let { "${it.physicalWidth}x${it.physicalHeight}" } ?: a.resources.displayMetrics.let { "${it.widthPixels}x${it.heightPixels}" }
        val rate = hz(m?.refreshRate ?: d.refreshRate)
        val hdr = if (Build.VERSION.SDK_INT >= 24) {
            @Suppress("DEPRECATION")
            (d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)).toList().mapNotNull {
                when (it) {
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                    else -> null
                }
            }
        } else emptyList()
        return "$size at $rate Hz" + if (hdr.isNotEmpty()) "  ·  " + hdr.joinToString(", ") else "  ·  no HDR"
    }

    data class ModeInfo(val id: Int, val width: Int, val height: Int, val rate: Float)

    /**
     * The mode at the current resolution whose refresh rate is a whole multiple of the video's frame rate
     * (24 fps films want 24 / 48 / 120 Hz, not 60). Null when the TV offers nothing better than where it is.
     */
    fun pickMode(modes: List<ModeInfo>, current: ModeInfo, fps: Float): ModeInfo? {
        if (fps < 10f) return null
        fun k(m: ModeInfo): Int {
            val n = (m.rate / fps).roundToInt()
            return if (n >= 1 && abs(m.rate / n - fps) < 0.06f) n else Int.MAX_VALUE
        }
        val same = modes.filter { it.width == current.width && it.height == current.height }
        fun miss(m: ModeInfo) = abs(m.rate / k(m) - fps)
        val best = same.filter { k(it) != Int.MAX_VALUE }.minWithOrNull(compareBy<ModeInfo>({ k(it) }, { miss(it) })) ?: return null
        // Only switch for a whole-number multiple that is a better fit than where the TV already is.
        return best.takeIf { it.id != current.id && (k(current) == Int.MAX_VALUE || k(it) < k(current) || miss(it) < miss(current) - 0.005f) }
    }

    fun bestMode(a: Activity, fps: Float): Display.Mode? {
        if (Build.VERSION.SDK_INT < 23) return null
        val d = display(a) ?: return null
        fun info(m: Display.Mode) = ModeInfo(m.modeId, m.physicalWidth, m.physicalHeight, m.refreshRate)
        val pick = pickMode(d.supportedModes.map(::info), info(d.mode), fps) ?: return null
        return d.supportedModes.firstOrNull { it.modeId == pick.id }
    }

    /**
     * Ask the window for the matching mode. The player itself only switches when the change is seamless, which a
     * Shield or Fire TV over HDMI never is, so this opt-in covers Android 6 to 13.
     */
    fun match(a: Activity, fps: Float) {
        if (Build.VERSION.SDK_INT !in 23..33) return
        val mode = bestMode(a, fps) ?: return
        val lp = a.window.attributes
        if (lp.preferredDisplayModeId != mode.modeId) { lp.preferredDisplayModeId = mode.modeId; a.window.attributes = lp }
    }

    fun clear(a: Activity) {
        if (Build.VERSION.SDK_INT !in 23..33) return
        val lp = a.window.attributes
        if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; a.window.attributes = lp }
    }
}
