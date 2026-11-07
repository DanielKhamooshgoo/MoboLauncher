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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val Squircle = RoundedCornerShape(percent = 22)

/** In-app message pill (replaces Android toasts). */
val LocalHud = staticCompositionLocalOf<(String) -> Unit> { { } }

/** Translucent "glass" surface: soft gradient fill and a light edge. */
fun Modifier.glass(ui: Ui, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(ui.glassTop, ui.glassBottom)), shape)
    .border(1.dp, Brush.verticalGradient(listOf(ui.strokeTop, ui.strokeBottom)), shape)

/** Same look as glass() but without clipping, so magnified Dock icons can rise above the bar. */
fun Modifier.glassOpen(ui: Ui, shape: Shape): Modifier = this
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
    Check, Chevron, Back, Up, Down, Plus, Minus, Sun, Moon, Power,
}

private fun DrawScope.drawGlyph(kind: Glyph, c: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mid = Offset(s * 0.5f, s * 0.5f)
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    fun poly(vararg v: Float, close: Boolean = false): Path {
        val path = Path()
        path.moveTo(s * v[0], s * v[1])
        var i = 2
        while (i + 1 < v.size) {
            path.lineTo(s * v[i], s * v[i + 1])
            i += 2
        }
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
            drawPath(poly(0.14f, 0.76f, 0.40f, 0.40f, 0.56f, 0.62f, 0.68f, 0.48f, 0.86f, 0.76f, close = true), c)
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
            drawPath(poly(0.50f, 0.16f, 0.78f, 0.82f, 0.50f, 0.66f, 0.22f, 0.82f, close = true), c)
        }
        Glyph.General, Glyph.Gear -> {
            drawCircle(c, s * 0.20f, mid, style = Stroke(width = s * 0.10f))
            for (i in 0 until 8) {
                rotate(i * 45f, mid) {
                    drawRoundRect(c, p(0.455f, 0.12f), Size(s * 0.09f, s * 0.16f), CornerRadius(s * 0.03f))
                }
            }
        }
        Glyph.Check -> drawPath(poly(0.22f, 0.54f, 0.42f, 0.72f, 0.80f, 0.28f), c, style = stroke)
        Glyph.Chevron -> drawPath(poly(0.36f, 0.20f, 0.64f, 0.50f, 0.36f, 0.80f), c, style = stroke)
        Glyph.Back -> drawPath(poly(0.64f, 0.20f, 0.36f, 0.50f, 0.64f, 0.80f), c, style = stroke)
        Glyph.Up -> drawPath(poly(0.20f, 0.64f, 0.50f, 0.36f, 0.80f, 0.64f), c, style = stroke)
        Glyph.Down -> drawPath(poly(0.20f, 0.36f, 0.50f, 0.64f, 0.80f, 0.36f), c, style = stroke)
        Glyph.Plus -> {
            drawLine(c, p(0.50f, 0.22f), p(0.50f, 0.78f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            drawLine(c, p(0.22f, 0.50f), p(0.78f, 0.50f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
        }
        Glyph.Minus -> drawLine(c, p(0.22f, 0.50f), p(0.78f, 0.50f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
        Glyph.Sun -> {
            drawCircle(c, s * 0.15f, mid)
            for (i in 0 until 8) {
                rotate(i * 45f, mid) {
                    drawLine(c, p(0.5f, 0.15f), p(0.5f, 0.26f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
                }
            }
        }
        Glyph.Moon -> {
            val outer = Path().apply { addOval(Rect(p(0.48f, 0.50f), s * 0.30f)) }
            val inner = Path().apply { addOval(Rect(p(0.62f, 0.40f), s * 0.26f)) }
            drawPath(Path.combine(PathOperation.Difference, outer, inner), c)
        }
        Glyph.Power -> {
            drawArc(c, -60f, 300f, false, topLeft = p(0.24f, 0.28f), size = Size(s * 0.52f, s * 0.52f), style = stroke)
            drawLine(c, p(0.5f, 0.14f), p(0.5f, 0.48f), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
        }
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
            AppIconFor(app, size, prefs)
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
    label: String, ui: Ui, d: Dims, iconSize: Dp,
    modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable (Dp) -> Unit,
) {
    val hot = rememberHot()
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.06f * ui.intensity else 1f,
        spring<Float>(dampingRatio = 0.7f, stiffness = 400f), label = "shortcut",
    )
    val shape = RoundedCornerShape(iconSize * 0.3f)
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (hot.active) ui.glassTop else Color.Transparent)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(iconSize * 0.14f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(iconSize)
        Txt(
            label, ui, d.labelFor(iconSize), Modifier.padding(top = iconSize * 0.1f).fillMaxWidth(),
            maxLines = 1, align = TextAlign.Center, shadow = true,
        )
    }
}

/**
 * One Dock slot. Its width and icon size follow [magnify] (a springy, macOS-style fisheye that is
 * computed by the Dock from the distance to the hovered or focused slot). Clicking makes the
 * icon hop once. The slot keeps a fixed height, so the bar never grows: icons rise above it.
 */
@Composable
fun DockSlot(
    label: String, ui: Ui, d: Dims, prefs: LauncherPrefs, baseSize: Dp, magnify: Float,
    modifier: Modifier = Modifier, onActive: (Boolean) -> Unit, onClick: () -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    val hot = rememberHot()
    LaunchedEffect(hot.active) { onActive(hot.active) }
    val animated by animateDpAsState(
        baseSize * magnify,
        if (ui.intensity == 0f) snap<Dp>() else spring<Dp>(dampingRatio = 0.62f, stiffness = 420f),
        label = "dock-size",
    )
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(0f) }
    val lift = with(LocalDensity.current) { (baseSize * 0.35f).toPx() }
    val ringShape = RoundedCornerShape(animated * 0.24f)
    Column(
        modifier
            .graphicsLayer { translationY = bounce.value }
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null) {
                if (ui.intensity > 0f) {
                    scope.launch {
                        bounce.animateTo(-lift, tween<Float>(150, easing = FastOutSlowInEasing))
                        bounce.animateTo(0f, spring<Float>(dampingRatio = 0.35f, stiffness = 300f))
                    }
                }
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (d.tv || prefs.dockLabels) {
            Box(Modifier.size(animated, (22 * d.fs).dp), contentAlignment = Alignment.Center) {
                if (hot.active || prefs.dockLabels) {
                    Txt(label, ui, 12f * d.fs, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        Box(Modifier.size(animated, baseSize).wrapContentSize(Alignment.BottomCenter, unbounded = true)) {
            Box(Modifier.then(if (hot.focused) Modifier.border(2.dp, ui.ring, ringShape) else Modifier)) {
                content(animated)
            }
        }
    }
}

// ------------------------------------------------------------------ system icons (original artwork)

/** Gray gear icon for the launcher's Settings. A lookalike, not Apple's artwork. */
@Composable
fun SystemSettingsIcon(iconSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(iconSize).clip(Squircle)
            .background(Brush.verticalGradient(listOf(Color(0xFFB7BBC2), Color(0xFF666971))))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), Squircle),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val c = Offset(s / 2f, s / 2f)
            val silver = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD3D6DC)), startY = s * 0.15f, endY = s * 0.85f)
            for (i in 0 until 12) {
                rotate(i * 30f, c) {
                    drawRoundRect(silver, Offset(s * 0.445f, s * 0.12f), androidx.compose.ui.geometry.Size(s * 0.11f, s * 0.18f), CornerRadius(s * 0.03f))
                }
            }
            drawCircle(silver, s * 0.29f, c)
            drawCircle(Color(0xFF7B7E86), s * 0.12f, c)
            drawCircle(Color(0xFFC9CCD2), s * 0.12f, c, style = Stroke(width = s * 0.012f))
        }
    }
}

/** Colorful app-grid icon for the Applications library. */
@Composable
fun LaunchpadIcon(iconSize: Dp, modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFFF5F57), Color(0xFFFF9F0A), Color(0xFFFFD60A),
        Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF0A84FF),
        Color(0xFFBF5AF2), Color(0xFFFF375F), Color(0xFF8E8E93),
    )
    Box(
        modifier.size(iconSize).clip(Squircle)
            .background(Brush.verticalGradient(listOf(Color(0xFFF3F4F7), Color(0xFFB7BCC6))))
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), Squircle),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            for (r in 0..2) {
                for (q in 0..2) {
                    drawRoundRect(
                        colors[r * 3 + q],
                        Offset(s * (0.17f + 0.23f * q), s * (0.17f + 0.23f * r)),
                        androidx.compose.ui.geometry.Size(s * 0.19f, s * 0.19f),
                        CornerRadius(s * 0.05f),
                    )
                }
            }
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

// ------------------------------------------------------------------ macOS-style stand-in icons

enum class MacKind { Settings, AppStore, Safari, Photos, Maps, Mail, Music, Files, Calculator, Camera, Clock }

private fun polyPath(s: Float, vararg v: Float, close: Boolean = false): Path {
    val path = Path()
    path.moveTo(s * v[0], s * v[1])
    var i = 2
    while (i + 1 < v.size) {
        path.lineTo(s * v[i], s * v[i + 1])
        i += 2
    }
    if (close) path.close()
    return path
}

private fun DrawScope.drawMacGlyph(kind: MacKind) {
    val s = size.minDimension
    val mid = Offset(s * 0.5f, s * 0.5f)
    val white = Color.White
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    val round = StrokeCap.Round
    when (kind) {
        MacKind.Safari -> {
            drawCircle(white.copy(alpha = 0.9f), s * 0.37f, mid, style = Stroke(width = s * 0.025f))
            for (i in 0 until 12) {
                rotate(i * 30f, mid) {
                    drawLine(white.copy(alpha = 0.9f), p(0.5f, 0.14f), p(0.5f, if (i % 3 == 0) 0.20f else 0.17f), strokeWidth = s * 0.012f)
                }
            }
            rotate(45f, mid) {
                drawPath(polyPath(s, 0.5f, 0.20f, 0.58f, 0.5f, 0.42f, 0.5f, close = true), Color(0xFFFF3B30))
                drawPath(polyPath(s, 0.5f, 0.80f, 0.58f, 0.5f, 0.42f, 0.5f, close = true), white)
            }
        }
        MacKind.AppStore -> {
            drawLine(white, p(0.50f, 0.22f), p(0.30f, 0.76f), strokeWidth = s * 0.09f, cap = round)
            drawLine(white, p(0.50f, 0.22f), p(0.70f, 0.76f), strokeWidth = s * 0.09f, cap = round)
            drawLine(white, p(0.34f, 0.62f), p(0.66f, 0.62f), strokeWidth = s * 0.08f, cap = round)
        }
        MacKind.Photos -> {
            val cols = listOf(0xFFFF5F57, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F)
            for (i in 0 until 8) {
                rotate(i * 45f, mid) {
                    drawOval(Color(cols[i]).copy(alpha = 0.78f), p(0.38f, 0.13f), Size(s * 0.24f, s * 0.37f))
                }
            }
        }
        MacKind.Maps -> {
            drawLine(white.copy(alpha = 0.9f), p(0.10f, 0.82f), p(0.90f, 0.22f), strokeWidth = s * 0.10f)
            drawLine(Color(0xFFFFD60A).copy(alpha = 0.9f), p(0.10f, 0.30f), p(0.60f, 0.92f), strokeWidth = s * 0.05f)
            drawCircle(Color(0xFFFF3B30), s * 0.17f, p(0.5f, 0.40f))
            drawPath(polyPath(s, 0.37f, 0.50f, 0.63f, 0.50f, 0.5f, 0.76f, close = true), Color(0xFFFF3B30))
            drawCircle(white, s * 0.065f, p(0.5f, 0.40f))
        }
        MacKind.Mail -> {
            drawRoundRect(white, p(0.16f, 0.28f), Size(s * 0.68f, s * 0.46f), CornerRadius(s * 0.06f))
            drawPath(
                polyPath(s, 0.19f, 0.33f, 0.5f, 0.56f, 0.81f, 0.33f), Color(0xFF1E7BF0),
                style = Stroke(width = s * 0.035f, cap = round, join = StrokeJoin.Round),
            )
        }
        MacKind.Music -> {
            drawCircle(white, s * 0.09f, p(0.38f, 0.70f))
            drawCircle(white, s * 0.09f, p(0.66f, 0.64f))
            drawLine(white, p(0.46f, 0.68f), p(0.46f, 0.30f), strokeWidth = s * 0.05f)
            drawLine(white, p(0.74f, 0.62f), p(0.74f, 0.24f), strokeWidth = s * 0.05f)
            drawLine(white, p(0.46f, 0.30f), p(0.74f, 0.24f), strokeWidth = s * 0.07f)
        }
        MacKind.Files -> {
            drawRoundRect(white, p(0.16f, 0.30f), Size(s * 0.30f, s * 0.14f), CornerRadius(s * 0.04f))
            drawRoundRect(white, p(0.16f, 0.38f), Size(s * 0.68f, s * 0.40f), CornerRadius(s * 0.06f))
        }
        MacKind.Calculator -> {
            drawRoundRect(Color(0xFF8E8E93).copy(alpha = 0.6f), p(0.16f, 0.16f), Size(s * 0.68f, s * 0.20f), CornerRadius(s * 0.04f))
            for (r in 0..2) {
                for (q in 0..3) {
                    val col = if (q == 3) Color(0xFFFF9F0A) else Color(0xFF6E6E73)
                    drawRoundRect(col, p(0.16f + 0.175f * q, 0.42f + 0.16f * r), Size(s * 0.15f, s * 0.13f), CornerRadius(s * 0.03f))
                }
            }
        }
        MacKind.Camera -> {
            drawRoundRect(white, p(0.14f, 0.32f), Size(s * 0.72f, s * 0.46f), CornerRadius(s * 0.07f))
            drawRoundRect(white, p(0.34f, 0.25f), Size(s * 0.22f, s * 0.12f), CornerRadius(s * 0.03f))
            drawCircle(Color(0xFF48484A), s * 0.15f, p(0.5f, 0.55f))
            drawCircle(Color(0xFF8E8E93), s * 0.09f, p(0.5f, 0.55f))
        }
        MacKind.Clock -> {
            drawCircle(white, s * 0.38f, mid)
            for (i in 0 until 12) {
                rotate(i * 30f, mid) {
                    drawLine(Color.Black, p(0.5f, 0.15f), p(0.5f, if (i % 3 == 0) 0.22f else 0.19f), strokeWidth = s * 0.016f)
                }
            }
            drawLine(Color.Black, mid, p(0.5f, 0.28f), strokeWidth = s * 0.04f, cap = round)
            drawLine(Color.Black, mid, p(0.66f, 0.54f), strokeWidth = s * 0.04f, cap = round)
            drawLine(Color(0xFFFF3B30), mid, p(0.5f, 0.74f), strokeWidth = s * 0.016f, cap = round)
        }
        MacKind.Settings -> Unit
    }
}

/** A macOS-style icon, drawn here (original artwork that imitates the look, not Apple's files). */
@Composable
fun MacIcon(kind: MacKind, iconSize: Dp, modifier: Modifier = Modifier) {
    if (kind == MacKind.Settings) {
        SystemSettingsIcon(iconSize, modifier)
        return
    }
    val bg: List<Color> = when (kind) {
        MacKind.Safari -> listOf(Color(0xFF63D2FF), Color(0xFF0A74F0))
        MacKind.AppStore -> listOf(Color(0xFF1FD0FF), Color(0xFF0A5CF5))
        MacKind.Photos -> listOf(Color(0xFFFFFFFF), Color(0xFFE9EBF0))
        MacKind.Maps -> listOf(Color(0xFF8BDD72), Color(0xFF3FB1EE))
        MacKind.Mail -> listOf(Color(0xFF58BEFF), Color(0xFF0A5FE8))
        MacKind.Music -> listOf(Color(0xFFFF7A8A), Color(0xFFFA2B43))
        MacKind.Files -> listOf(Color(0xFF6CC8FF), Color(0xFF1F7FF0))
        MacKind.Calculator -> listOf(Color(0xFF4A4A4E), Color(0xFF1C1C1E))
        MacKind.Camera -> listOf(Color(0xFFA0A0A8), Color(0xFF4A4A50))
        MacKind.Clock -> listOf(Color(0xFF1C1C1E), Color(0xFF000000))
        MacKind.Settings -> listOf(Color.Gray, Color.Gray)
    }
    Box(
        modifier.size(iconSize).clip(Squircle)
            .background(Brush.verticalGradient(bg))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), Squircle),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawMacGlyph(kind) }
    }
}

/** The icon shown for an installed app, following the "Icon style" setting. */
@Composable
fun AppIconFor(app: AppEntry, iconSize: Dp, prefs: LauncherPrefs) {
    val kind = if (prefs.iconStyle == "macos") macKindFor(app.pkg, app.label) else null
    if (kind != null) {
        MacIcon(kind, iconSize)
    } else {
        val style = if (prefs.iconStyle == "original") "original" else "plate"
        AppIconView(rememberIcon(app), iconSize, style, app.label)
    }
}

// ------------------------------------------------------------------ menu bar pieces

/** Small power button at the top left (opens the launcher menu). */
@Composable
fun LogoButton(ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (hot.active) ui.glassTop else Color.Transparent)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        GlyphView(Glyph.Power, ui.text, (14 * d.fs).dp)
    }
}

@Composable
fun MenuItem(text: String, ui: Ui, d: Dims, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(if (hot.active) ui.accent else Color.Transparent)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = (6 * d.fs).dp),
    ) {
        Txt(text, ui, 14f * d.fs, maxLines = 1, color = if (hot.active) Color.White else null)
    }
}

@Composable
fun LauncherMenu(
    ui: Ui, d: Dims, modifier: Modifier, firstFocus: FocusRequester,
    onSettings: () -> Unit, onShutDown: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width((230 * d.fs).dp).clip(shape).background(ui.sheet).glass(ui, shape).padding(6.dp)) {
        MenuItem("About Mobo Launcher", ui, d, Modifier.focusRequester(firstFocus), onSettings)
        MenuItem("System Settings…", ui, d, onClick = onSettings)
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp).height(1.dp).background(ui.separator))
        MenuItem("Shut Down…", ui, d, onClick = onShutDown)
    }
}
