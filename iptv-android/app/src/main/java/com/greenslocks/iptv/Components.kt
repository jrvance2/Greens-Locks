package com.greenslocks.iptv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import coil.request.SuccessResult
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import android.view.SoundEffectConstants
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.Crossfade
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.compose.ui.graphics.compositeOver
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface as TvSurface

/** A diagonal band of light that sweeps across a card once each time it gains focus. Costs nothing on unfocused cards. */
@Composable
fun Sheen(focused: Boolean, modifier: Modifier = Modifier) {
    if (!focused || Dimens.animLevel != 0) return
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(800, easing = Motion.ease)) }
    Box(
        modifier.fillMaxSize().drawBehind {
            val v = p.value
            if (v > 0f && v < 1f) {
                val x = (v * 1.7f - 0.45f) * size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + size.width * 0.38f, size.height * 0.38f),
                    )
                )
            }
        }
    )
}

/** Accent pill in a card corner, e.g. "NEW EPISODES". */
@Composable
fun BoxScope.NewTag(text: String) {
    Text(
        text, Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(5.dp))
            .background(Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2)))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

/** Small "4K" tag in a card corner. */
@Composable
fun BoxScope.QualityTag(title: String) {
    val is4k = remember(title) { qualityBadge(title) == "4K" }
    if (!is4k) return
    Text(
        "4K", Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.62f)).border(1.dp, Palette.accent.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
    )
}

/**
 * Focusable, clickable card on Compose for TV's Surface: it scales up, gets an accent ring and glow when focused,
 * and handles click and long-click. The animation settings only change how far it grows and whether it glows.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.corner),
    scaleOnFocus: Float = 1.08f,
    borderWidth: Dp = 3.dp,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val level = Dimens.animLevel
    val grows = scaleOnFocus != 1f && level != 2
    val target = if (!grows) 1f else if (level == 1) 1f + (scaleOnFocus - 1f) * 0.4f else scaleOnFocus
    val glowOn = level == 0 && scaleOnFocus != 1f && Dimens.glow
    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                if (it.isFocused) {
                    if (Dimens.sounds) view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                    onFocus()
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Palette.text,
            focusedContainerColor = Color.Transparent, focusedContentColor = Palette.text,
            pressedContainerColor = Color.Transparent, pressedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = target, pressedScale = target),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(borderWidth, Palette.accent), shape = shape)),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = if (glowOn) Glow(Palette.accent.copy(alpha = 0.55f), 18.dp) else Glow.None,
        ),
        interactionSource = source,
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
    val hue = remember(title) { (kotlin.math.abs(title.hashCode()) % 360).toFloat() }
    val brush = remember(title) {
        Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.40f), Color.hsv((hue + 25f) % 360f, 0.5f, 0.22f)))
    }
    BoxWithConstraints(modifier.background(brush), contentAlignment = Alignment.Center) {
        val fs = (minOf(maxWidth, maxHeight).value * 0.36f).coerceIn(13f, 44f).sp
        Text(
            remember(title) { initialsOf(title) }, fontSize = fs, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f), maxLines = 1,
        )
        if (url.isNotBlank()) {
            val ctx = LocalContext.current
            val fade = Dimens.animLevel == 0
            // Poster-sized art decodes to 16-bit colour to save memory; full-screen backdrops stay 32-bit so
            // gradients and dark scenes don't band on a 4K TV.
            val big = url.contains("/t/p/w1280") || url.contains("/t/p/original")
            val request = remember(url, fade) { ImageRequest.Builder(ctx).data(url).crossfade(fade).allowRgb565(!big).build() }
            AsyncImage(
                model = request,
                contentDescription = null, contentScale = fit, alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "BBC One" -> "BO", "ESPN" -> "E"; shown when a channel or title has no artwork. */
private val initialsLead = Regex("^\\s*(?:[|\\[(][^|\\])]{1,12}[|\\])]\\s*)+")
private val initialsSplit = Regex("[^\\p{L}\\p{N}]+")

fun initialsOf(title: String): String {
    val words = title.replace(initialsLead, "").split(initialsSplit).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> title.trim().take(1).uppercase()
        words.size == 1 -> words[0].take(if (words[0].length <= 4) words[0].length else 2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Average color of an image, used to tint the Home banner to match the poster. */
suspend fun dominantColor(ctx: Context, url: String): Color? = runCatching {
    val result = coil.Coil.imageLoader(ctx).execute(
        ImageRequest.Builder(ctx).data(url).allowHardware(false).size(48).build()
    )
    val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
    val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
    Color(px)
}.getOrNull()

/** A soft light sweeping across placeholder shapes while content loads. */
@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1400f, infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing)), label = "x")
    return Brush.linearGradient(
        listOf(Palette.surfaceHi.copy(alpha = 0.55f), Palette.surfaceHi.copy(alpha = 1f), Palette.surfaceHi.copy(alpha = 0.55f)),
        start = Offset(x, 0f), end = Offset(x + 400f, 200f),
    )
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val sh = shimmerBrush()
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(Palette.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 80.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)).background(sh))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.width(220.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(sh))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(150.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(sh))
        }
    }
}

/** Placeholder shelf shown on Home while the library loads. */
@Composable
fun SkeletonShelf(modifier: Modifier = Modifier) {
    val sh = shimmerBrush()
    Column(modifier) {
        Box(Modifier.padding(start = 24.dp, top = 20.dp).size(width = 180.dp, height = 18.dp).clip(RoundedCornerShape(5.dp)).background(sh))
        Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(7) { Box(Modifier.width(Dimens.posterWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(Dimens.corner)).background(sh)) }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, knob: Boolean = false) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.height(if (knob) 8.dp else 4.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))) {
            Box(
                Modifier.fillMaxWidth(f).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2)))
            )
        }
        if (knob) BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.offset(x = (maxWidth - 8.dp) * f).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, active: Boolean = true) {
    val a by animateFloatAsState(if (active) 1f else 0.55f, tween(Motion.MED), label = "titleDim")
    Text(
        text, modifier.padding(start = 24.dp, top = 16.dp).graphicsLayer { alpha = a },
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
    width: Dp? = Dimens.posterWidth,
    fit: ContentScale = ContentScale.Crop,
    onLongClick: () -> Unit = {},
    onFocus: () -> Unit = {},
    badge: String? = null,
    onClick: () -> Unit,
) {
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusCard(modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick, onFocus = onFocus, onClick = onClick) { focused ->
            Artwork(icon, title, Modifier.fillMaxSize(), fit)
            if (badge != null) NewTag(badge) else QualityTag(title)
            Sheen(focused)
            Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Dimens.corner)))
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
    width: Dp? = Dimens.wideWidth,
    fit: ContentScale = ContentScale.Crop,
    onLongClick: () -> Unit = {},
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    FocusCard(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth()).aspectRatio(16f / 9f),
        onLongClick = onLongClick, onFocus = onFocus, onClick = onClick,
    ) { focused ->
        Artwork(icon, title, Modifier.fillMaxSize(), fit, Alignment.TopCenter)
        Sheen(focused)
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
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Dimens.corner)))
        if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

/** After coming back from a movie page, puts focus back on the poster you opened. */
@Composable
fun restoreFocusModifier(key: String, restoreKey: String?, pending: Boolean, skip: Boolean, onRestored: () -> Unit): Modifier {
    if (!pending || skip || restoreKey != key) return Modifier
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); runCatching { fr.requestFocus() }; onRestored() }
    return Modifier.focusRequester(fr)
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
    onFocusEntry: (Entry) -> Unit = {},
    badgeOf: (Entry) -> String? = { null },
    restoreKey: String? = null,
    restorePending: Boolean = false,
    onRestored: () -> Unit = {},
) {
    var rowActive by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { rowActive = it.hasFocus }) {
        SectionTitle(title, active = rowActive)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items) { i, e ->
                val mod = (if (i == 0) firstFocus else Modifier).then(restoreFocusModifier(e.key(), restoreKey, restorePending, i == 0, onRestored))
                if (wide) {
                    WideCard(
                        titleOf(e), subtitleOf(e), e.icon, isFavorite(e), progressOf(e), mod,
                        fit = if (e.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        onLongClick = { onLongClick(e) }, onFocus = { onFocusEntry(e) },
                    ) { onOpen(e) }
                } else {
                    PosterCard(
                        titleOf(e), e.icon, subtitleOf(e), isFavorite(e), progressOf(e), mod,
                        onLongClick = { onLongClick(e) }, onFocus = { onFocusEntry(e) }, badge = badgeOf(e),
                    ) { onOpen(e) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val view = LocalView.current
    val grow = if (Dimens.animLevel == 2) 1f else if (Dimens.animLevel == 1) 1.025f else 1.06f
    FilterChip(
        selected = selected, onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { if (it.isFocused && Dimens.sounds) view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT) },
        shape = FilterChipDefaults.shape(RoundedCornerShape(50)),
        colors = FilterChipDefaults.colors(
            containerColor = Palette.surfaceHi, contentColor = Palette.text,
            focusedContainerColor = Palette.surfaceHi, focusedContentColor = Palette.text,
            pressedContainerColor = Palette.surfaceHi, pressedContentColor = Palette.text,
            selectedContainerColor = Palette.accent, selectedContentColor = Color.White,
            focusedSelectedContainerColor = Palette.accent, focusedSelectedContentColor = Color.White,
            pressedSelectedContainerColor = Palette.accent, pressedSelectedContentColor = Color.White,
        ),
        // Only the focused chip gets a ring; the library's default draws a thin light outline on every chip.
        border = FilterChipDefaults.border(
            border = Border.None, selectedBorder = Border.None, disabledBorder = Border.None, selectedDisabledBorder = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = RoundedCornerShape(50)),
            pressedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = RoundedCornerShape(50)),
            focusedSelectedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
            pressedSelectedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50)),
        ),
        scale = FilterChipDefaults.scale(focusedScale = grow),
        glow = FilterChipDefaults.glow(),
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ActionButton(
    text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit,
) {
    // A TV Surface (via FocusCard) rather than the TV Button: the Button's content slot can't paint a gradient over its
    // whole area without the first button in a Row taking all the width (see ComponentScreenshots.buttonRows).
    FocusCard(modifier, RoundedCornerShape(10.dp), 1.05f, 2.dp, onClick = { if (enabled) onClick() }) {
        // The fill covers the whole button even when the caller makes it wider than its text.
        Box(
            Modifier.matchParentSize().background(
                if (primary) Brush.horizontalGradient(listOf(Palette.accent, Palette.accent2))
                else Brush.horizontalGradient(listOf(Palette.surfaceHi, Palette.surfaceHi))
            )
        )
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) Color.White else Palette.muted)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text, maxLines = 1,
                color = if (enabled) Color.White else Palette.muted,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One highlighted row: used by menus and anywhere a plain list entry is enough. */
@OptIn(ExperimentalTvMaterial3Api::class)
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
    val view = LocalView.current
    val shape = RoundedCornerShape(10.dp)
    ListItem(
        selected = false, onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused && Dimens.sounds) view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT) },
        shape = ListItemDefaults.shape(shape),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) Palette.surfaceHi else Palette.surface, contentColor = Palette.text,
            focusedContainerColor = Palette.accent.copy(alpha = 0.22f).compositeOver(Palette.surface), focusedContentColor = Palette.text,
            pressedContainerColor = Palette.accent.copy(alpha = 0.3f).compositeOver(Palette.surface), pressedContentColor = Palette.text,
        ),
        border = ListItemDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.accent), shape = shape)),
        scale = ListItemDefaults.scale(focusedScale = 1f),
        glow = ListItemDefaults.glow(),
        leadingContent = if (icon.isBlank()) null else ({
            Artwork(icon, title, Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
        }),
        supportingContent = if (subtitle.isEmpty()) null else ({
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.muted, style = MaterialTheme.typography.bodySmall)
        }),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
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
    FocusCard(modifier.fillMaxWidth().padding(vertical = 2.dp), RoundedCornerShape(10.dp), 1f, 2.dp, onLongClick, onClick) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Palette.accent.copy(alpha = 0.2f) else if (playing) Palette.surfaceHi else Palette.surface)
                .padding((10 * Dimens.rowScale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(icon, title, Modifier.size(width = (80 * Dimens.rowScale).dp, height = (54 * Dimens.rowScale).dp).clip(RoundedCornerShape(6.dp)), ContentScale.Fit)
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

/** Friendly placeholder for screens with nothing to show yet. */
@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Palette.surfaceHi, Palette.surface))),
            contentAlignment = Alignment.Center,
        ) { Text("\u25B6", color = Palette.accent, style = MaterialTheme.typography.headlineMedium) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp))
        Text(body, color = Palette.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        if (action != null) ActionButton(action, Modifier.padding(top = 18.dp), onClick = onAction)
    }
}

@Composable
fun BellIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(color, radius = s * 0.30f, center = Offset(s * 0.5f, s * 0.40f))
        drawRect(color, topLeft = Offset(s * 0.20f, s * 0.40f), size = Size(s * 0.60f, s * 0.32f))
        drawRect(color, topLeft = Offset(s * 0.10f, s * 0.70f), size = Size(s * 0.80f, s * 0.10f))
        drawCircle(color, radius = s * 0.09f, center = Offset(s * 0.5f, s * 0.90f))
    }
}

@Composable
fun SpeakerIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val p = Path().apply {
            moveTo(s * 0.10f, s * 0.38f); lineTo(s * 0.32f, s * 0.38f); lineTo(s * 0.55f, s * 0.18f)
            lineTo(s * 0.55f, s * 0.82f); lineTo(s * 0.32f, s * 0.62f); lineTo(s * 0.10f, s * 0.62f); close()
        }
        drawPath(p, color)
        drawArc(color, -45f, 90f, false, Offset(s * 0.45f, s * 0.30f), Size(s * 0.40f, s * 0.40f), style = Stroke(s * 0.08f))
    }
}

/** Container for dialogs and panels, on Compose for TV's Surface. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Panel(shape: Shape, content: @Composable () -> Unit) {
    TvSurface(
        shape = shape,
        colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = Palette.surface, contentColor = Palette.text),
    ) { content() }
}
