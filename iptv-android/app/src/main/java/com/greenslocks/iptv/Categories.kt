package com.greenslocks.iptv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Providers usually name categories "US | SPORTS", "UK: MOVIES", "|FR| FILMS" or "ES - DEPORTES".
private val prefixRegex = Regex("""^\s*[|\[(]*\s*([A-Za-z][A-Za-z0-9+]{1,5})\s*(?:[|\]):»]+|\s[-–]\s)\s*(.+)$""")

/** The country/language prefix of a category name, or "Other" when it has none. */
fun regionOf(name: String): String = prefixRegex.find(name)?.groupValues?.get(1)?.uppercase() ?: "Other"

data class CatRow(
    val cat: Category,
    val name: String,
    val count: Int?,
    val pinned: Boolean,
    val locked: Boolean,
    val hidden: Boolean,
)

@Composable
private fun SmallLabel(text: String) {
    Text(
        text, color = Palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun CatRowItem(row: CatRow, selected: Boolean, onSelect: () -> Unit, onMenu: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(vertical = 1.dp), RoundedCornerShape(10.dp), 1f, 2.dp, onMenu, onSelect) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(
                    if (focused) Palette.accent.copy(alpha = 0.22f)
                    else if (selected) Palette.accent.copy(alpha = 0.3f) else Color.Transparent
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (row.pinned) "★ " else "") + (if (row.locked) "🔒 " else "") + row.name,
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.hidden) Palette.muted else if (selected || focused) Palette.text else Color(0xFFC8D0E6),
            )
            if (row.count != null) Text("${row.count}", color = Palette.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * Left-hand category browser: filter box, region tabs (the "US", "UK" prefixes), a pinned section,
 * and the list. Built to stay usable with hundreds of categories.
 */
@Composable
fun CategoryPanel(
    modifier: Modifier,
    filter: String,
    onFilter: (String) -> Unit,
    regions: List<Pair<String, String>>,
    region: String,
    onRegion: (String) -> Unit,
    pinned: List<CatRow>,
    rows: List<CatRow>,
    listLabel: String,
    selectedId: String?,
    onSelect: (CatRow) -> Unit,
    onMenu: (CatRow) -> Unit,
    listState: LazyListState,
    groups: List<CatRow> = emptyList(),
) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.surface).padding(10.dp)) {
        OutlinedTextField(
            value = filter, onValueChange = onFilter, singleLine = true,
            label = { Text("Filter categories") }, modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(regions) { (id, label) -> Chip(label, region == id) { onRegion(id) } }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (groups.isNotEmpty()) {
                item { SmallLabel("MY GROUPS") }
                items(groups) { r -> CatRowItem(r, r.cat.id == selectedId, { onSelect(r) }, { onMenu(r) }) }
            }
            if (pinned.isNotEmpty()) {
                item { SmallLabel("PINNED") }
                items(pinned) { r -> CatRowItem(r, r.cat.id == selectedId, { onSelect(r) }, { onMenu(r) }) }
            }
            item { SmallLabel(listLabel) }
            items(rows) { r -> CatRowItem(r, r.cat.id == selectedId, { onSelect(r) }, { onMenu(r) }) }
            if (rows.isEmpty()) item { Text("No categories match.", color = Palette.muted, modifier = Modifier.padding(12.dp)) }
        }
    }
}

// ------------------------------------------------------------------ "choose your categories"

data class PickerCat(val cat: Category, val region: String, val count: Int?, val checked: Boolean)

private sealed interface PItem {
    data class Head(val region: String, val total: Int, val on: Int) : PItem
    data class Row(val c: PickerCat) : PItem
}

@Composable
private fun CheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = Palette.accent
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        if (checked) {
            drawRoundRect(accent, cornerRadius = CornerRadius(w * 0.22f))
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.24f, w * 0.52f); lineTo(w * 0.43f, w * 0.7f); lineTo(w * 0.78f, w * 0.3f)
            }
            drawPath(p, Color.White, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round))
        } else {
            drawRoundRect(Color(0x66FFFFFF), cornerRadius = CornerRadius(w * 0.22f), style = Stroke(width = w * 0.09f))
        }
    }
}

@Composable
fun CategoryPicker(
    tabLabels: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    cats: List<PickerCat>,
    onToggle: (PickerCat) -> Unit,
    onToggleRegion: (String, Boolean) -> Unit,
    onQuick: (Int) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    title: String = "Choose your categories",
    subtitle: String = "Pick the ones you actually watch. Everything else is hidden, never deleted, and you can reopen this from Settings.",
) {
    var filter by remember(tab) { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(tab, cats.isEmpty()) {
        repeat(20) { delay(80); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    val items = remember(cats, filter) {
        val shown = cats.filter { it.cat.name.contains(filter, ignoreCase = true) }
        buildList<PItem> {
            shown.groupBy { it.region }.toList()
                .sortedWith(compareBy({ it.first == "Other" }, { -it.second.size }))
                .forEach { (r, list) ->
                    add(PItem.Head(r, list.size, list.count { it.checked }))
                    list.forEach { add(PItem.Row(it)) }
                }
        }
    }
    val firstRow = items.indexOfFirst { it is PItem.Row }
    val selected = cats.count { it.checked }
    val shownChannels = cats.filter { it.checked }.mapNotNull { it.count }.sum()

    Column(Modifier.fillMaxSize().background(Palette.bg).padding(horizontal = 28.dp, vertical = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Palette.muted, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            tabLabels.forEachIndexed { i, t -> Chip(t, tab == i) { onTab(i) }; Spacer(Modifier.width(8.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Quick pick:  ", color = Palette.muted, fontSize = 12.sp)
            Chip("Hide adult", false) { onQuick(0) }; Spacer(Modifier.width(6.dp))
            Chip("Select all", false) { onQuick(1) }; Spacer(Modifier.width(6.dp))
            Chip("Select none", false) { onQuick(2) }
        }
        Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Palette.surface).padding(12.dp)) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    label = { Text("Filter categories") }, modifier = Modifier.fillMaxWidth(),
                )
                if (cats.isEmpty()) Text("Loading categories…", color = Palette.muted, modifier = Modifier.padding(12.dp))
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    itemsIndexed(items) { i, item ->
                        when (item) {
                            is PItem.Head -> {
                                val all = item.on == item.total
                                FocusCard(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), RoundedCornerShape(10.dp), 1f, 2.dp, onClick = {
                                    onToggleRegion(item.region, !all)
                                }) { focused ->
                                    Row(
                                        Modifier.fillMaxWidth().background(if (focused) Palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${item.region}  ·  ${item.on} of ${item.total} selected", Modifier.weight(1f),
                                            color = Palette.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                        )
                                        Text(if (all) "OK: select none" else "OK: select all", color = Palette.muted, fontSize = 11.sp)
                                    }
                                }
                            }
                            is PItem.Row -> {
                                val c = item.c
                                FocusCard(
                                    (if (i == firstRow) Modifier.focusRequester(first) else Modifier).fillMaxWidth().padding(vertical = 1.dp),
                                    RoundedCornerShape(10.dp), 1f, 2.dp, onClick = { onToggle(c) },
                                ) { focused ->
                                    Row(
                                        Modifier.fillMaxWidth().background(if (focused) Palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CheckBox(c.checked)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            c.cat.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp, fontWeight = if (c.checked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (c.checked) Palette.text else Palette.muted,
                                        )
                                        if (c.count != null) Text("${c.count}", color = Palette.muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Palette.surface).padding(18.dp)) {
                Text("Your selection", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("$selected", fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, color = Palette.accent)
                Text("of ${cats.size} categories", color = Palette.muted)
                ProgressLine(if (cats.isEmpty()) 0f else selected.toFloat() / cats.size, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                if (cats.any { it.count != null }) {
                    Text("≈ $shownChannels items shown", fontSize = 13.sp)
                } else {
                    Text("Counting items…", color = Palette.muted, fontSize = 12.sp)
                }
                Text("${cats.size - selected} hidden", color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Search still covers hidden categories.", color = Palette.muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Spacer(Modifier.weight(1f))
                ActionButton("Done", Modifier.fillMaxWidth(), onClick = onDone)
                Spacer(Modifier.height(8.dp))
                ActionButton("Skip for now", Modifier.fillMaxWidth(), primary = false, onClick = onSkip)
            }
        }
    }
}
