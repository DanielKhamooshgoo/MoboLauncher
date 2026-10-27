package app.mobolauncher

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Squircle = RoundedCornerShape(percent = 24)

/** Translucent "glass" surface: soft gradient fill and a light edge. Cheap alternative to real blur. */
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
    align: TextAlign = TextAlign.Start, shadow: Boolean = false,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = if (sub) ui.subtext else ui.text,
            fontSize = size.sp, fontWeight = weight, textAlign = align,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 6f) else null,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

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

@Composable
fun Chip(
    text: String, ui: Ui, d: Dims, modifier: Modifier = Modifier,
    selected: Boolean = false, onClick: () -> Unit,
) {
    val hot = rememberHot()
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.05f * ui.intensity else 1f,
        tween(if (ui.intensity == 0f) 0 else 120), label = "chip",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (selected) Modifier.clip(shape).background(ui.selected) else Modifier.glass(ui, shape))
            .then(if (hot.focused) Modifier.border(3.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(horizontal = (18 * d.fs).dp, vertical = (9 * d.fs).dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(color = if (selected) Color.White else ui.text, fontSize = (15 * d.fs).sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Toggle(on: Boolean, ui: Ui, d: Dims, onChange: (Boolean) -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(50)
    val w = 52 * d.fs
    val h = 30 * d.fs
    val x by animateDpAsState(
        if (on) (w - h).dp else 0.dp,
        tween(if (ui.intensity == 0f) 0 else 150), label = "toggle",
    )
    Box(
        Modifier.size(w.dp, h.dp).clip(shape)
            .background(if (on) ui.selected else Color.Gray.copy(alpha = 0.45f))
            .then(if (hot.focused) Modifier.border(3.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null) { onChange(!on) }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size((h - 6).dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun <T> Choice(options: List<Pair<String, T>>, value: T, ui: Ui, d: Dims, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, v) -> Chip(label, ui, d, selected = v == value) { onPick(v) } }
    }
}

@Composable
fun SettingRow(title: String, ui: Ui, d: Dims, subtitle: String? = null, control: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(ui, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Txt(title, ui, 18f * d.fs, weight = FontWeight.Medium)
            if (subtitle != null) Txt(subtitle, ui, 13f * d.fs, sub = true)
        }
        control()
    }
}

@Composable
fun SectionTitle(text: String, ui: Ui, d: Dims) {
    Txt(text, ui, 20f * d.fs, Modifier.padding(top = 10.dp), weight = FontWeight.SemiBold)
}

@Composable
fun ColorDot(color: Color, selected: Boolean, ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val size = (36 * d.fs).dp
    Box(
        Modifier.size(size).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(4.dp, Color.White, CircleShape) else Modifier)
            .then(if (hot.focused) Modifier.border(3.dp, ui.ring, CircleShape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
    )
}

@Composable
fun WallpaperThumb(def: WallpaperDef, selected: Boolean, ui: Ui, d: Dims, onClick: () -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size((128 * d.fs).dp, (76 * d.fs).dp).clip(shape)
            .then(if (selected) Modifier.border(4.dp, ui.selected, shape) else Modifier)
            .then(if (hot.focused) Modifier.border(3.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick),
    ) {
        SceneCanvas(def.id, Modifier.fillMaxSize())
        Txt(
            def.name, ui, 12f * d.fs,
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            maxLines = 1, shadow = true,
        )
    }
}

/** Library / desktop tile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry, ui: Ui, d: Dims, prefs: LauncherPrefs, size: Dp, showLabel: Boolean,
    pinned: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val hot = rememberHot()
    val icon = rememberIcon(app)
    val scale by animateFloatAsState(
        if (hot.active) 1f + 0.08f * ui.intensity else 1f,
        tween(if (ui.intensity == 0f) 0 else 120), label = "tile",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (hot.active) ui.glassTop else Color.Transparent)
            .then(if (hot.focused) Modifier.border(3.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .combinedClickable(
                interactionSource = hot.src, indication = null,
                onLongClick = onLongClick, onClick = onClick,
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIconView(icon, size, prefs.iconStyle, app.label)
            if (pinned) {
                Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(ui.accent))
            }
        }
        if (showLabel) {
            Txt(
                app.label, ui, 14f * d.fs, Modifier.padding(top = 6.dp).fillMaxWidth(),
                maxLines = 1, align = TextAlign.Center, shadow = true,
            )
        }
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
        Box(Modifier.then(if (hot.focused) Modifier.border(3.dp, ui.ring, Squircle) else Modifier)) {
            content()
        }
    }
}
