package com.greenslocks.iptv

import androidx.compose.ui.unit.dp

val CORNER_NAMES = listOf("Sharp", "Rounded", "Soft")
val ROW_NAMES = listOf("Compact", "Comfortable", "Large")
val GUIDE_ROW_NAMES = listOf("Compact", "Comfortable", "Large")
val OVERLAY_NAMES = listOf("Card", "Minimal", "Hidden")
val NAV_NAMES = listOf("Side rail", "Top bar")

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
    }
}
