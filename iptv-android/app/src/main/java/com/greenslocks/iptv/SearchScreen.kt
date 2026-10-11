package com.greenslocks.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private sealed interface SItem {
    data class Head(val label: String) : SItem
    data class Hit(val entry: Entry, val sub: String) : SItem
}

/** Instant search over the on-device index: channels, movies and series in one list. */
@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: Map<Kind, List<Entry>>,
    status: String?,
    nameOf: (Entry) -> String,
    onOpen: (Entry) -> Unit,
) {
    val field = remember { FocusRequester() }
    val firstHit = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(15) { delay(80); if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect } }
    val items = remember(results) {
        buildList<SItem> {
            val groups = listOf(Kind.LIVE to "Channels", Kind.MOVIE to "Movies", Kind.SERIES to "Series")
            for ((kind, label) in groups) {
                val list = results[kind].orEmpty()
                if (list.isEmpty()) continue
                add(SItem.Head("$label  ·  ${list.size}${if (list.size >= 40) "+" else ""}"))
                list.forEach { add(SItem.Hit(it, if (kind == Kind.LIVE && it.num > 0) "Ch ${it.num}" else "")) }
            }
        }
    }
    val firstIdx = items.indexOfFirst { it is SItem.Hit }
    Column(Modifier.fillMaxSize().background(Palette.bg).padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            label = { Text("Channels, movies and series") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(field),
        )
        if (status != null) Text(status, color = Palette.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        if (query.isNotBlank() && items.isEmpty() && status == null) {
            Text("No matches.", color = Palette.muted, modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            itemsIndexed(items) { i, item ->
                when (item) {
                    is SItem.Head -> Text(
                        item.label, color = Palette.accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                    )
                    is SItem.Hit -> ListRow(
                        nameOf(item.entry), item.sub, false,
                        (if (i == firstIdx) Modifier.focusRequester(firstHit) else Modifier).padding(vertical = 1.dp),
                        icon = item.entry.icon,
                    ) { onOpen(item.entry) }
                }
            }
        }
    }
}
