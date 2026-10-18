package com.greenslocks.iptv

import androidx.compose.ui.unit.dp

val CORNER_NAMES = listOf("Sharp", "Rounded", "Soft")
val ROW_NAMES = listOf("Compact", "Comfortable", "Large")
val GUIDE_ROW_NAMES = listOf("Compact", "Comfortable", "Large")
val OVERLAY_NAMES = listOf("Card", "Minimal", "Hidden")
val NAV_NAMES = listOf("Side rail", "Top bar")
val SUB_BG_NAMES = listOf("None", "Soft", "Solid")
val SUB_EDGE_NAMES = listOf("None", "Outline", "Shadow")
val SUB_COLOR_NAMES = listOf("White", "Yellow", "Cyan")
val EXIT_NAMES = listOf("Stop playback", "Small window (PiP)", "Keep playing")
val SKIP_SECS = listOf(5, 10, 15, 30, 60)

/** Reads the saved layout choices into [Dimens] so every screen follows them immediately. */
object LayoutPrefs {
    fun load(store: Store) {
        Dimens.corner = listOf(4, 12, 22)[store.int("corner", 1).coerceIn(0, 2)].dp
        Dimens.rowScale = listOf(0.82f, 1f, 1.25f)[store.int("rowSize", 1).coerceIn(0, 2)]
        Dimens.guideRow = listOf(44, 56, 72)[store.int("guideRow", 1).coerceIn(0, 2)].dp
        Dimens.overlayStyle = store.int("overlay", 0).coerceIn(0, 2)
        Dimens.showClock = store.flag("clock", true)
        Dimens.topNav = store.flag("topNav", false)
        Dimens.homeHero = store.flag("homeHero", true)
        Dimens.homeChips = store.flag("homeChips", true)
        Dimens.glow = store.flag("glow", true)
        Dimens.heroTint = store.flag("heroTint", true)
        Dimens.railLabels = store.flag("railLabels", true)
        Dimens.sounds = store.flag("sounds", false)
        Dimens.subBg = store.int("subBg", 1).coerceIn(0, 2)
        Dimens.subEdge = store.int("subEdge", 1).coerceIn(0, 2)
        Dimens.subColor = store.int("subColor", 0).coerceIn(0, 2)
        Dimens.skipBack = SKIP_SECS[store.int("skipBack", 1).coerceIn(0, SKIP_SECS.size - 1)]
        Dimens.skipFwd = SKIP_SECS[store.int("skipFwd", 1).coerceIn(0, SKIP_SECS.size - 1)]
        Dimens.keepFrame = store.flag("keepFrame", true)
        Dimens.ambient = store.flag("ambient", true)
        Dimens.askProfile = store.flag("askProfile", true)
        Dimens.exitMode = store.int("exitMode", 0).coerceIn(0, 2)
    }
}
