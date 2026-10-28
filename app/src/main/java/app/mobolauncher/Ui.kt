package app.mobolauncher

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Squircle = RoundedCornerShape(percent = 22)

/** In-app message pill (replaces Android toasts). */
val LocalHud = staticCompositionLocalOf<(String) -> Unit> { { } }

/** Translucent "glass" surface: soft gradient fill and a light edge. */
fun Modifier.glass(ui: Ui, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(ui.glassTop, ui.glassBottom)), shape)
    .border(1.dp, Brush.verticalGradient(listOf(ui.strokeTop, ui.strokeBottom)), shape)

class Hot(val src: MutableInteractionSource, focusedState: State<Boolean>, hoveredState: State<Boolean>) {
    val focused: Boolean by focusedState
    val hovered: Boolean by hoveredState
    val active: Boolean get() = focused || hovered
}

@Composable
fun rememberHot(): Hot {
    val src = remember { MutableInteractionSource() }
    val f = src.collectIsFocusedAsState()
    val h = src.collectIsHoveredAsState()
    return remember { Hot(src, f, h) }
}

@Composable
fun rememberIcon(app: AppEntry): AppIcon? {
    val ctx = LocalContext.current
    val state = produceState<AppIcon?>(null, app.pkg) { value = IconCache.get(ctx, app) }
    return state.value
}

@Composable
fun Txt(
    text: String, ui: Ui, size: Float, modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal, sub: Boolean = false, maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start, shadow: Boolean = false, color: Color? = null,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color ?: (if (sub) ui.subtext else ui.text),
            fontSize = size.sp, fontWeight = weight, textAlign = align,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 6f) else null,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------ glyphs

enum class Glyph {
    Dock, Appearance, Wallpaper, Apps, Idle, Display, Navigation, General, Gear,
    Check, Chevron, Back, Up, Down, Plus, Minus,
}

private fun DrawScope.drawGlyph(kind: Glyph, c: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mid = Offset(s * 0.5f, s * 0.5f)
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    fun poly(vararg pts: Offset, close: Boolean = false): Path {
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        if (close) path.close()
        return path
    }
    when (kind) {
        Glyph.Dock -> {
            drawRoundRect(c, p(0.16f, 0.60f), Size(s * 0.68f, s * 0.20f), CornerRadius(s * 0.08f))
            for (i in 0..2) drawCircle(c, s * 0.075f, p(0.30f + 0.20f * i, 0.38f))
        }
        Glyph.Appearance -> {
            drawCircle(c, s * 0.28f, mid, style = stroke)
            drawArc(c, -90f, 180f, true, topLeft = p(0.22f, 0.22f), size = Size(s * 0.56f, s * 0.56f))
        }
        Glyph.Wallpaper -> {
            drawPath(poly(p(0.14f, 0.76f), p(0.40f, 0.40f), p(0.56f, 0.62f), p(0.68f, 0.48f), p(0.86f, 0.76f), close = true), c)
            drawCircle(c, s * 0.07f, p(0.72f, 0.26f))
        }
        Glyph.Apps -> {
            for (r in 0..2) {
                for (q in 0..2) {
                    drawRoundRect(c, p(0.20f + 0.23f * q, 0.20f + 0.23f * r), Size(s * 0.17f, s * 0.17f), CornerRadius(s * 0.045f))
                }
            }
        }
        Glyph.Idle -> {
            drawCircle(c, s * 0.30f, mid, style = stroke)
            drawLine(c, mid, p(0.5f, 0.32f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            drawLine(c, mid, p(0.63f, 0.56f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
        }
        Glyph.Display -> {
            drawRoundRect(c, p(0.16f, 0.24f), Size(s * 0.68f, s * 0.42f), CornerRadius(s * 0.06f), style = stroke)
            drawLine(c, p(0.36f, 0.79f), p(0.64f, 0.79f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
        }
        Glyph.Navigation -> {
            drawPath(poly(p(0.50f, 0.16f), p(0.78f, 0.82f), p(0.50f, 0.66f), p(0.22f, 0.82f), close = true), c)
        }
        Glyph.General, Glyph.Gear -> {
            drawCircle(c, s * 0.20f, mid, style = Stroke(width = s * 0.10f))
            for (i in 0 until 8) {
                rotate(i * 45f, mid) {
                    drawRoundRect(c, p(0.455f, 0.12f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.03f))
                }
            }
        }
        Glyph.Check -> drawPath(poly(p(0.22f, 0.54f), p(0.42f, 0.72f), p(0.80f, 0.28f)), c, style = stroke)
        Glyph.Chevron -> drawPath(poly(p(0.36f, 0.20f), p(0.64f, 0.50f), p(0.36f, 0.80f)), c, style = stroke)
        Glyph.Back -> drawPath(poly(p(0.64f, 0.20f), p(0.36f, 0.50f), p(0.64f, 0.80f)), c, style = stroke)
        Glyph.Up -> drawPath(poly(p(0.20f, 0.64f), p(0.50f, 0.36f), p(0.80f, 0.64f)), c, style = stroke)
        Glyph.Down -> drawPath(poly(p(0.20f, 0.36f), p(0.50f, 0.64f), p(0.80f, 0.36f)), c, style = stroke)
        Glyph.Plus -> {
            drawLine(c, p(0.50f, 0.22f), p(0.50f, 0.78f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            drawLine(c, p(0.22f, 0.50f), p(0.78f, 0.50f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
        }
        Glyph.Minus -> drawLine(c, p(0.22f, 0.50f), p(0.78f, 0.50f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
    }
}

@Composable
fun GlyphView(kind: Glyph, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(kind, color) }
}

/** Rounded-square icon with a white glyph (used for system shortcuts and Settings rows). */
@Composable
fun IconTile(kind: Glyph, base: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(Squircle)
            .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), lerp(base, Color.Black, 0.18f))))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), Squircle),
        contentAlignment = Alignment.Center,
    ) {
        GlyphView(kind, Color.White, size * 0.62f)
    }
}

// ------------------------------------------------------------------ app icons

@Composable
fun AppIconView(icon: AppIcon?, size: Dp, style: String, description: String?) {
    if (icon == null) {
        Spacer(Modifier.size(size))
    } else if (style == "original") {
        Image(icon.bitmap, description, Modifier.size(size))
    } else if (icon.adaptive) {
        Image(
            icon.bitmap, description,
            Modifier.size(size).clip(Squircle).border(0.5.dp, Color.White.copy(alpha = 0.25f), Squircle),
        )
    } else {
        Box(
            Modifier.size(size).clip(Squircle)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE3E5EA))))
                .border(0.5.dp, Color.White.copy(alpha = 0.4f), Squircle),
        ) {
            Image(icon.bitmap, description, Modifier.align(Alignment.Center).fillMaxSize(0.72f))
        }
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun Chip(
    text: String, ui: Ui, d: Dims, modifier: Modifier = Modifier,
    selected: Boolean = false, onClick: () -> Unit,
) {
    val hot = rememberHot()
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.04f * ui.intensity else 1f,
        tween(if (ui.intensity == 0f) 0 else 120), label = "chip",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.clip(shape).background(ui.selected) else Modifier.glass(ui, shape))
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(horizontal = (16 * d.fs).dp, vertical = (8 * d.fs).dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(color = if (selected) Color.White else ui.text, fontSize = (15 * d.fs).sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** iOS-style switch. Pass onChange = null for a display-only switch inside a clickable row. */
@Composable
fun Toggle(on: Boolean, ui: Ui, d: Dims, onChange: ((Boolean) -> Unit)?) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(50)
    val w = 51 * d.fs
    val h = 31 * d.fs
    val x by animateDpAsState(
        if (on) (w - h).dp else 0.dp,
        tween(if (ui.intensity == 0f) 0 else 160), label = "toggle",
    )
    val base = Modifier.size(w.dp, h.dp).clip(shape)
        .background(if (on) ui.green else Color.Gray.copy(alpha = 0.40f))
    val interactive = if (onChange != null) {
        base.then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null) { onChange(!on) }
    } else base
    Box(interactive.padding(2.dp)) {
        Box(Modifier.offset(x = x).size((h - 4).dp).clip(CircleShape).background(Color.White))
    }
}

/** iOS-style segmented control. */
@Composable
fun <T> Seg(options: List<Pair<String, T>>, value: T, ui: Ui, d: Dims, onPick: (T) -> Unit) {
    val track = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(track).background(ui.text.copy(alpha = 0.08f)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, v) ->
            val hot = rememberHot()
            val sel = v == value
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier.weight(1f).clip(shape)
                    .background(if (sel) (if (ui.dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
                    .hoverable(hot.src)
                    .clickable(interactionSource = hot.src, indication = null) { onPick(v) }
                    .padding(vertical = (7 * d.fs).dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(
                    label, ui, 14f * d.fs,
                    weight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ColorDot(color: Color, selected: Boolean, ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val size = (32 * d.fs).dp
    Box(
        Modifier.size(size).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, CircleShape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
    )
}

@Composable
fun MiniButton(kind: Glyph, ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val size = (34 * d.fs).dp
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (hot.active) ui.accent.copy(alpha = 0.30f) else ui.text.copy(alpha = 0.10f))
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, CircleShape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphView(kind, ui.text, size * 0.6f)
    }
}

/** Small round window button (macOS traffic-light style). */
@Composable
fun CloseDot(ui: Ui, d: Dims, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hot = rememberHot()
    val size = (16 * d.fs).dp
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0xFFFF5F57))
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, CircleShape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
    )
}

@Composable
fun WallpaperThumb(def: WallpaperDef, selected: Boolean, ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size((150 * d.fs).dp, (90 * d.fs).dp).clip(shape)
            .then(if (selected) Modifier.border(3.dp, ui.selected, shape) else Modifier)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
    ) {
        SceneCanvas(def.id, Modifier.fillMaxSize())
        Txt(
            def.name, ui, 12f * d.fs,
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            maxLines = 1, shadow = true, color = Color.White,
        )
    }
}

// ------------------------------------------------------------------ tiles

/** Library / desktop tile for an installed app. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry, ui: Ui, d: Dims, prefs: LauncherPrefs, size: Dp, showLabel: Boolean,
    modifier: Modifier = Modifier, pinned: Boolean = false,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val hot = rememberHot()
    val icon = rememberIcon(app)
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.06f * ui.intensity else 1f,
        tween(if (ui.intensity == 0f) 0 else 120), label = "tile",
    )
    val shape = RoundedCornerShape(size * 0.3f)
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (hot.active) ui.glassTop else Color.Transparent)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .combinedClickable(
                interactionSource = hot.src, indication = null,
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(size * 0.14f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIconView(icon, size, prefs.iconStyle, app.label)
            if (pinned) {
                Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(ui.accent))
            }
        }
        if (showLabel) {
            Txt(
                app.label, ui, d.labelFor(size), Modifier.padding(top = size * 0.1f).fillMaxWidth(),
                maxLines = 1, align = TextAlign.Center, shadow = true,
            )
        }
    }
}

/** Desktop shortcut for a launcher feature (System Settings, Applications). */
@Composable
fun ShortcutTile(
    label: String, glyph: Glyph, base: Color, ui: Ui, d: Dims, size: Dp,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val hot = rememberHot()
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.06f * ui.intensity else 1f,
        tween(if (ui.intensity == 0f) 0 else 120), label = "shortcut",
    )
    val shape = RoundedCornerShape(size * 0.3f)
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (hot.active) ui.glassTop else Color.Transparent)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(size * 0.14f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(glyph, base, size)
        Txt(
            label, ui, d.labelFor(size), Modifier.padding(top = size * 0.1f).fillMaxWidth(),
            maxLines = 1, align = TextAlign.Center, shadow = true,
        )
    }
}

/** One Dock slot: hover/focus magnification, label tip, focus ring. */
@Composable
fun DockSlot(
    label: String, ui: Ui, d: Dims, prefs: LauncherPrefs, iconSize: Dp,
    modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit,
) {
    val hot = rememberHot()
    val target = if (hot.active && prefs.dockMagnify) 1f + 0.22f * ui.intensity else 1f
    val scale by animateFloatAsState(target, tween(if (ui.intensity == 0f) 0 else 140), label = "dock")
    val ringShape = RoundedCornerShape(iconSize * 0.24f)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 1f) }
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (d.tv || prefs.dockLabels) {
            Box(Modifier.size(iconSize, (22 * d.fs).dp), contentAlignment = Alignment.Center) {
                if (hot.active || prefs.dockLabels) {
                    Txt(label, ui, 12f * d.fs, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        Box(Modifier.then(if (hot.focused) Modifier.border(2.dp, ui.ring, ringShape) else Modifier)) {
            content()
        }
    }
}

// ------------------------------------------------------------------ HUD

@Composable
fun HudPill(text: String, ui: Ui, d: Dims, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.glass(ui, shape).background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = (20 * d.fs).dp, vertical = (10 * d.fs).dp),
    ) {
        Txt(text, ui, 15f * d.fs, maxLines = 2)
    }
}
