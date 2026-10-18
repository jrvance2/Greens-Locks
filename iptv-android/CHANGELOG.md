# Vance TV changelog

## Beta (next)
- Sports hub (new tab): live scores, schedules and standings for NFL, NBA, MLB, NHL, college football/basketball, WNBA, MLS, Premier League, La Liga, Bundesliga, Serie A, Champions League and UFC; Live now across leagues; follow teams (My teams); game detail with line scores and top performers; "Find on my channels" matches a game to your channels by team names and TV network. Data: ESPN public scoreboard feed.
- New setting "When you leave the app" (Playback & audio): stop playback (new default), small window (PiP), or keep playing.
- Fix: the TV no longer goes to sleep / stops playback after ~15 minutes with no remote input; the app now keeps the screen awake while something plays (a paused full-screen video holds it for 30 minutes).
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
