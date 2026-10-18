package com.greenslocks.iptv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/** "Added 3 days ago" and the NEW tag, from the provider's added time (seconds). */
object AddedText {
    fun days(addedSec: Long, nowMs: Long): Int? =
        if (addedSec <= 0L) null else ((nowMs / 1000 - addedSec) / 86_400).toInt().coerceAtLeast(0)

    fun label(addedSec: Long, nowMs: Long): String? {
        val d = days(addedSec, nowMs) ?: return null
        return when {
            d == 0 -> "Added today"
            d == 1 -> "Added yesterday"
            d <= 90 -> "Added $d days ago"
            else -> null
        }
    }

    fun isNew(addedSec: Long, nowMs: Long): Boolean = (days(addedSec, nowMs) ?: Int.MAX_VALUE) <= 14
}

/** A titled row of library posters on the movie page ("More like this", "Dune Collection"). */
@Composable
fun DetailRow(title: String, entries: List<Entry>, onOpen: (Entry) -> Unit) {
    if (entries.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
        itemsIndexed(entries, key = { _, e -> e.key() }) { _, e ->
            PosterCard(e.title.let { Tmdb.cleanTitle(it).first }, e.icon, width = 120.dp) { onOpen(e) }
        }
    }
}

@Composable
fun ActorDialog(name: String, movies: List<Entry>?, onOpen: (Entry) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(movies) { if (!movies.isNullOrEmpty()) { delay(100); runCatching { first.requestFocus() } } }
    Dialog(onDismissRequest = onDismiss) {
        Panel(RoundedCornerShape(20.dp)) {
            Column(Modifier.widthIn(min = 520.dp, max = 760.dp).padding(20.dp)) {
                Text("Movies with $name in your library", style = MaterialTheme.typography.titleLarge)
                when {
                    movies == null -> Text("Searching your library…", color = Palette.muted, modifier = Modifier.padding(top = 12.dp))
                    movies.isEmpty() -> {
                        Text("None of their best-known movies are in your library.", color = Palette.muted, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                        ActionButton("Close", Modifier.focusRequester(first), primary = false, onClick = onDismiss)
                    }
                    else -> LazyRow(
                        Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        itemsIndexed(movies, key = { _, e -> e.key() }) { i, e ->
                            PosterCard(
                                Tmdb.cleanTitle(e.title).first, e.icon, width = 130.dp,
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            ) { onDismiss(); onOpen(e) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrashDialog(report: String, path: String, onClear: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Panel(RoundedCornerShape(20.dp)) {
            Column(Modifier.widthIn(min = 600.dp, max = 860.dp).padding(20.dp)) {
                Text("Last crash", style = MaterialTheme.typography.titleLarge)
                Text(
                    report.take(1_600), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.text.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text("Full log: $path", color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Clear log", Modifier.focusRequester(first), onClick = onClear)
                    ActionButton("Close", primary = false, onClick = onDismiss)
                }
            }
        }
    }
}
