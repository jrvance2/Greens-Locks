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

/** Focusable, clickable surface that scales up and gets an accent ring when focused. */
@OptIn(ExperimentalFoundationApi::class)
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
    val target = if (level == 1) 1f + (scaleOnFocus - 1f) * 0.4f else scaleOnFocus
    val scale by animateFloatAsState(
        if (focused && grows) target else 1f,
        if (level == 0) spring(dampingRatio = 0.62f, stiffness = 420f) else tween(if (level == 1) 60 else Motion.FAST, easing = Motion.ease),
        label = "focusScale",
    )
    val glowOn = level == 0 && scaleOnFocus != 1f && Dimens.glow
    val glowDp by animateDpAsState(if (focused && glowOn) 18.dp else 0.dp, tween(Motion.MED, easing = Motion.ease), label = "glow")
    Box(
        modifier
            .then(
                if (grows) Modifier.zIndex(if (focused) 1f else 0f).graphicsLayer { scaleX = scale; scaleY = scale }
                else Modifier
            )
            .then(
                if (glowDp > 0.5.dp)
                    Modifier.shadow(glowDp, shape, clip = false, ambientColor = Palette.accent, spotColor = Palette.accent)
                else Modifier
            )
            // Only draw the ring while focused: a 0.dp border still paints a hairline.
            .then(if (focused) Modifier.border(borderWidth, Palette.accent, shape) else Modifier)
            .clip(shape)
            .onFocusChanged {
                if (it.isFocused) {
                    if (Dimens.sounds) view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                    onFocus()
                }
            }
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
    val hue = remember(title) { (kotlin.math.abs(title.hashCode()) % 360).toFloat() }
    val brush = remember(title) {
        Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.40f), Color.hsv((hue + 25f) % 360f, 0.5f, 0.22f)))
    }
    BoxWithConstraints(modifier.background(brush), contentAlignment = Alignment.Center) {
        val fs = (minOf(maxWidth, maxHeight).value * 0.36f).coerceIn(13f, 44f).sp
        Text(
            initialsOf(title), fontSize = fs, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f), maxLines = 1,
        )
        if (url.isNotBlank()) {
            val ctx = LocalContext.current
            val fade = Dimens.animLevel == 0
            val request = remember(url, fade) { ImageRequest.Builder(ctx).data(url).crossfade(fade).build() }
            AsyncImage(
                model = request,
                contentDescription = null, contentScale = fit, alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "BBC One" -> "BO", "ESPN" -> "E"; shown when a channel or title has no artwork. */
fun initialsOf(title: String): String {
    val words = title.replace(Regex("^\\s*(?:[|\\[(][^|\\])]{1,12}[|\\])]\\s*)+"), "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
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
    onClick: () -> Unit,
) {
    Column(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()) {
        FocusCard(modifier.fillMaxWidth().aspectRatio(2f / 3f), onLongClick = onLongClick, onFocus = onFocus, onClick = onClick) {
            Artwork(icon, title, Modifier.fillMaxSize(), fit)
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
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Dimens.corner)))
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
    onFocusEntry: (Entry) -> Unit = {},
) {
    var rowActive by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { rowActive = it.hasFocus }) {
        SectionTitle(title, active = rowActive)
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
                        onLongClick = { onLongClick(e) }, onFocus = { onFocusEntry(e) },
                    ) { onOpen(e) }
                } else {
                    PosterCard(
                        titleOf(e), e.icon, subtitleOf(e), isFavorite(e), progressOf(e), mod,
                        onLongClick = { onLongClick(e) }, onFocus = { onFocusEntry(e) },
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
    FocusCard(modifier.fillMaxWidth(), RoundedCornerShape(10.dp), 1f, 2.dp, onLongClick, onClick) { focused ->
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
