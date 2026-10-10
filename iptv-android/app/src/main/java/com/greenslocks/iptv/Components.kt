package com.greenslocks.iptv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Focusable, clickable surface that scales up and gets an accent ring when focused. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.08f,
    borderWidth: Dp = 3.dp,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, tween(140), label = "focusScale")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(if (focused) borderWidth else 0.dp, Palette.accent, shape)
            .clip(shape)
            .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
    ) { content(focused) }
}

@Composable
fun Artwork(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    fit: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Palette.surfaceHi, Palette.surface))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium, color = Palette.muted,
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null, contentScale = fit, alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.accent))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 24.dp, top = 16.dp),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun PosterCard(
    title: String,
    icon: String,
    subtitle: String = "",
    favorite: Boolean = false,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    width: Dp? = 140.dp,
    fit: ContentScale = ContentScale.Crop,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusCard(modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick, onClick = onClick) {
            Artwork(icon, title, Modifier.fillMaxSize(), fit)
            if (favorite) {
                Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), color = Palette.accent)
            }
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Text(
            title, Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WideCard(
    title: String,
    subtitle: String,
    icon: String,
    favorite: Boolean = false,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    width: Dp? = 250.dp,
    fit: ContentScale = ContentScale.Crop,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusCard(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth()).aspectRatio(16f / 9f),
        onLongClick = onLongClick, onClick = onClick,
    ) {
        Artwork(icon, title, Modifier.fillMaxSize(), fit, Alignment.TopCenter)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                (if (favorite) "★ " else "") + title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

@Composable
fun ShelfRow(
    title: String,
    items: List<Entry>,
    wide: Boolean,
    titleOf: (Entry) -> String,
    subtitleOf: (Entry) -> String,
    progressOf: (Entry) -> Float?,
    isFavorite: (Entry) -> Boolean,
    onLongClick: (Entry) -> Unit,
    onOpen: (Entry) -> Unit,
    firstFocus: Modifier = Modifier,
) {
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items) { i, e ->
                val mod = if (i == 0) firstFocus else Modifier
                if (wide) {
                    WideCard(
                        titleOf(e), subtitleOf(e), e.icon, isFavorite(e), progressOf(e), mod,
                        fit = if (e.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        onLongClick = { onLongClick(e) },
                    ) { onOpen(e) }
                } else {
                    PosterCard(
                        titleOf(e), e.icon, subtitleOf(e), isFavorite(e), progressOf(e), mod,
                        onLongClick = { onLongClick(e) },
                    ) { onOpen(e) }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    FocusCard(modifier, RoundedCornerShape(50), 1.06f, 2.dp, onLongClick, onClick) {
        Text(
            text, maxLines = 1,
            modifier = Modifier.background(if (selected) Palette.accent else Palette.surfaceHi)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else Palette.text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    FocusCard(modifier, RoundedCornerShape(10.dp), 1.05f, 2.dp, onClick = { if (enabled) onClick() }) {
        Text(
            text, maxLines = 1,
            modifier = Modifier.background(if (primary) Palette.accent else Palette.surfaceHi)
                .padding(horizontal = 22.dp, vertical = 11.dp),
            color = if (enabled) Color.White else Palette.muted,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One highlighted row: used by menus and anywhere a plain list entry is enough. */
@Composable
fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier,
    icon: String = "",
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth(), RoundedCornerShape(10.dp), 1.02f, 2.dp, onLongClick, onClick) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Palette.accent.copy(alpha = 0.22f) else if (selected) Palette.surfaceHi else Palette.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon.isNotBlank()) {
                Artwork(icon, title, Modifier.padding(end = 12.dp).size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
            }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ChannelRow(
    title: String,
    icon: String,
    number: Int,
    favorite: Boolean,
    playing: Boolean,
    nowTitle: String?,
    progress: Float?,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    FocusCard(modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(10.dp), 1.02f, 2.dp, onLongClick, onClick) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Palette.accent.copy(alpha = 0.2f) else if (playing) Palette.surfaceHi else Palette.surface)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(icon, title, Modifier.size(width = 68.dp, height = 46.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (number > 0) Text("$number", color = Palette.muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        (if (favorite) "★ " else "") + title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (nowTitle != null) {
                    Text(nowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
                    if (progress != null) ProgressLine(progress, Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            if (playing) Text("▶", color = Palette.accent, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The Vance TV logo: gradient "V" tile, VANCE in white, TV in an accent badge. */
@Composable
fun BrandWordmark(textSize: TextUnit, modifier: Modifier = Modifier) {
    val tile = with(androidx.compose.ui.platform.LocalDensity.current) { (textSize.toPx() * 1.7f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(tile).clip(RoundedCornerShape(tile * 0.24f))
                .background(Brush.linearGradient(listOf(Palette.accent, Palette.accent2))),
            contentAlignment = Alignment.Center,
        ) { Text("V", color = Color.White, fontSize = textSize * 1.05f, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(tile * 0.3f))
        Text("VANCE", color = Palette.text, fontSize = textSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.width(tile * 0.18f))
        Text(
            "TV", color = Color.White, fontSize = textSize, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(tile * 0.14f))
                .background(Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2)))
                .padding(horizontal = tile * 0.16f, vertical = tile * 0.02f),
        )
    }
}
