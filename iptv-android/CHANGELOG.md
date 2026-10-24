# Vance TV changelog

## Beta (next)
- Build: Kotlin 2.4.21 with the Compose compiler plugin (was Kotlin 1.9.24). Strong skipping is now on, so screens redraw less; if any list or row fails to update, report it.
- Build: newer Compose libraries (1.10) and Compose for TV (tv-material 1.1.0) added, Android Gradle plugin 8.13, compile SDK 36; the main screen function is split into chunks so it stays under the JVM method-size limit.
- Compose for TV: the side rail is now the TV navigation drawer (icons collapsed, slides open with labels when focused, overlays the page instead of squeezing it); On/Off settings use TV switches.
- Compose for TV, everywhere it applies: every card, row and tile now sits on the TV Surface (focus scale, ring, glow, click and long-click); buttons are TV Buttons, chips are TV filter chips, list entries are TV list items, dialogs and panels are TV surfaces, the top navigation is a TV tab row, and the app theme is also a TV theme.
- Player: Media3 1.9.4 (was 1.5.0) with the matching FFmpeg audio decoder.
- 4K: video buffer is capped by the box's memory (never below 96 MB); full-screen backdrops decode in 32-bit colour (no banding) and use TMDB's original size on a 4K display; new opt-in "Match refresh rate (Fire TV)" switches the display mode on Android 6-10 (off by default: it blanks the screen and can hitch playback).
- Settings > About > "Display and video decoder": shows the TV's current mode (resolution, refresh rate, HDR types) and whether the video decoder is hardware or software.
- "Match refresh rate" now also works on Android 11-13 boxes like the Shield (it was Android 6-10 only): the player's own frame-rate matching only switches when it is seamless, which HDMI boxes never are, so 24/25/50 fps video ran on a 59.94 Hz display and juddered. Off by default (the screen blanks for a second when it switches).
- Settings > Playback & audio > "Stream stats overlay": a panel over the video with resolution, stream bitrate, measured network speed, seconds buffered, rebuffer count, dropped frames per second, decoder and display mode. For working out whether a stutter is the network, the decoder or the display.
- Fix: buttons placed side by side without set widths no longer take all the space (the first one pushed the others off screen); standalone buttons fit their text again. Found by the new screenshot tests.
- Behind the scenes: screenshot tests of the shared components (pills, buttons, rows) and an emulator smoke test of the shrunk release build in CI.
- Fix: filter pills no longer have a thin light outline when not focused (only the focused pill gets a ring).
- Sports: "Live sports" row on Home (games in progress, followed teams first; Settings > Sports > Live sports on Home); "Remind me at kickoff" on upcoming games (pick the channel; you're switched over at the start, same as programme reminders).

## 1.2.54 (stable; same code as beta 53)
- Fix: in the audio & subtitles panel, Left now moves between the subtitle-size chips instead of closing the panel.
- Fix: the last channel no longer starts playing by itself when the app opens (it is remembered for the Live list and the "Last channel" start option only).
- Movie page: other versions of a title in your library (4K / 1080p / HD) you can switch to; format badges (HDR, Dolby Vision, Atmos, 5.1...); technical line from your provider (codec, resolution, audio, bitrate); age rating; IMDb / Rotten Tomatoes / Metacritic scores (needs a free OMDb key in Settings); director; tap an actor to see their movies in your library; More like this and collection rows (only titles you have); Mark watched/unwatched with a Watched tag; "Added N days ago" and NEW.
- Posters: larger on-disk cache that ignores no-cache headers, lighter decoding. TMDB movie pages are saved for a week so they open instantly. Back from a movie page returns to the poster you opened.
- Settings > About > Last crash: shows the last crash error on the TV (also saved as crash.log).
- Explore (new tab): Discover (TMDB trending/in theaters/coming soon/top rated/popular series, each matched to your library with an IN LIBRARY badge), Tonight (what's on now / later / prime time across your channels from the extra guide, with genre filters), My agenda (reminders, scheduled recordings and followed teams' games in time order).
- Fix: buttons now fill their full width (no dark patch showing through the glow); Sports game card focus drawing; game detail keeps the scoreboard and buttons on screen.
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
