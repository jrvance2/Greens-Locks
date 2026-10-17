# Vance TV changelog

## Beta (next)
- Home: real library-wide "Recently added movies" and "Recently added series" rows (replaces the old first-categories guess).
- "New episodes" row and badge for series you follow that gained episodes since you last opened them.

## 1.2.43 (stable; same code as beta 41)
- Premium look: Material icon set, rotating Home banner, ambient glow, card sheen and 4K tag, episode thumbnails, animated splash, "Who's watching?" picker.
- Speed and reliability: on-disk library cache with instant start, background refresh, stall watchdog, playback diagnostics, startup profile, newer Compose libraries.
- Smoother navigation: removed per-card regex rebuilds, cheaper glow/sheen/ambient effects.
- Under the hood: screen state moved out of the main composable (headroom for new features); beta update channel (`[beta]` commits publish "Vance TV Beta" as a separate app).

## 1.2.34-1.2.37
- Extra XMLTV guide, per-channel guide shift and picture memory, preferred audio/subtitle language, recording padding, series recording.
- Dedicated settings screen, more accents and backgrounds, layout options.
