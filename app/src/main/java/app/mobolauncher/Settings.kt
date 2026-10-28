package app.mobolauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class Page(val title: String) {
    Main("Settings"), Appearance("Appearance"), Wallpaper("Wallpaper"), Display("Display"),
    DockPage("Dock"), Apps("Applications"), Idle("Idle Screen"), Navigation("Navigation"),
    General("General"),
}

// ------------------------------------------------------------------ row building blocks

private fun groupShape(index: Int, count: Int): Shape {
    val r = 16.dp
    val top = if (index == 0) r else 0.dp
    val bottom = if (index == count - 1) r else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun RowFrame(
    ui: Ui, index: Int, count: Int, indent: Dp, modifier: Modifier,
    highlight: Boolean, focused: Boolean, content: @Composable BoxScope.() -> Unit,
) {
    val shape = groupShape(index, count)
    Box(
        modifier.fillMaxWidth().clip(shape).background(ui.group)
            .then(if (highlight) Modifier.background(ui.accent.copy(alpha = 0.18f)) else Modifier)
            .then(if (focused) Modifier.border(2.dp, ui.ring, shape) else Modifier),
    ) {
        content()
        if (index < count - 1) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = indent).fillMaxWidth()
                    .height(0.5.dp).background(ui.separator),
            )
        }
    }
}

@Composable
fun SetRow(
    ui: Ui, d: Dims, index: Int, count: Int, title: String, modifier: Modifier = Modifier,
    glyph: Glyph? = null, tint: Color? = null, icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null, value: String? = null, chevron: Boolean = false, check: Boolean = false,
    titleColor: Color? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    val hot = rememberHot()
    val interactive = onClick != null
    val clickMod = if (onClick != null) {
        Modifier.hoverable(hot.src).clickable(interactionSource = hot.src, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    val hasIcon = glyph != null || icon != null
    RowFrame(
        ui, index, count, if (hasIcon) 58.dp else 16.dp, modifier.then(clickMod),
        highlight = interactive && hot.active, focused = interactive && hot.focused,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = (11 * d.fs).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (glyph != null) IconTile(glyph, tint ?: ui.accent, (30 * d.fs).dp)
            if (icon != null) icon()
            Column(Modifier.weight(1f)) {
                Txt(title, ui, 16f * d.fs, color = titleColor, maxLines = 2)
                if (subtitle != null) Txt(subtitle, ui, 13f * d.fs, sub = true, maxLines = 4)
            }
            if (value != null) Txt(value, ui, 15f * d.fs, sub = true, maxLines = 1)
            if (trailing != null) trailing()
            if (check) GlyphView(Glyph.Check, ui.accent, (20 * d.fs).dp)
            if (chevron) GlyphView(Glyph.Chevron, ui.subtext, (16 * d.fs).dp)
        }
    }
}

@Composable
private fun ToggleRow(
    ui: Ui, d: Dims, index: Int, count: Int, title: String, value: Boolean,
    subtitle: String? = null, onChange: (Boolean) -> Unit,
) {
    SetRow(
        ui, d, index, count, title, subtitle = subtitle,
        onClick = { onChange(!value) },
        trailing = { Toggle(value, ui, d, null) },
    )
}

@Composable
private fun SegRow(ui: Ui, d: Dims, index: Int, count: Int, title: String, content: @Composable () -> Unit) {
    RowFrame(ui, index, count, 16.dp, Modifier, false, false) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(title, ui, 16f * d.fs)
            content()
        }
    }
}

@Composable
private fun SecHeader(text: String, ui: Ui, d: Dims) {
    Txt(text.uppercase(), ui, 12.5f * d.fs, Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), sub = true)
}

@Composable
private fun SecFooter(text: String, ui: Ui, d: Dims) {
    Txt(text, ui, 12.5f * d.fs, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), sub = true)
}

private class GroupScope {
    val rows = mutableListOf<@Composable (Int, Int) -> Unit>()
    fun row(r: @Composable (Int, Int) -> Unit) { rows.add(r) }
}

private fun LazyListScope.group(build: GroupScope.() -> Unit) {
    val g = GroupScope()
    g.build()
    val n = g.rows.size
    g.rows.forEachIndexed { i, r -> item { r(i, n) } }
}

private fun <T> LazyListScope.groupOf(list: List<T>, key: (T) -> Any, row: @Composable (T, Int, Int) -> Unit) {
    val n = list.size
    itemsIndexed(list, key = { _, x -> key(x) }) { i, x -> row(x, i, n) }
}

private fun LazyListScope.header(text: String, ui: Ui, d: Dims) { item { SecHeader(text, ui, d) } }
private fun LazyListScope.footer(text: String, ui: Ui, d: Dims) { item { SecFooter(text, ui, d) } }
private fun LazyListScope.gap() { item { Spacer(Modifier.height(20.dp)) } }

@Composable
private fun NavBack(text: String, ui: Ui, d: Dims, modifier: Modifier, onClick: () -> Unit) {
    val hot = rememberHot()
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape)
            .then(if (hot.focused) Modifier.border(2.dp, ui.ring, shape) else Modifier)
            .hoverable(hot.src)
            .clickable(interactionSource = hot.src, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphView(Glyph.Back, ui.accent, (18 * d.fs).dp)
        Txt(text, ui, 16f * d.fs, color = ui.accent, maxLines = 1)
    }
}

// ------------------------------------------------------------------ panel

@Composable
fun SettingsPanel(
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, deviceIsTv: Boolean, isDefault: Boolean,
    onPickImage: () -> Unit, onSetDefault: () -> Unit, onPreviewIdle: () -> Unit, onClose: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.Main) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }
    val navFocus = remember { FocusRequester() }
    LaunchedEffect(page) {
        if (d.tv) {
            delay(330)
            try { navFocus.requestFocus() } catch (e: Exception) { }
        }
    }
    val shape = RoundedCornerShape(d.rL)
    Column(Modifier.fillMaxSize().clip(shape).background(ui.sheet).glass(ui, shape)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (page != Page.Main) {
                NavBack("Settings", ui, d, Modifier.align(Alignment.CenterStart).focusRequester(navFocus)) { page = Page.Main }
            }
            Txt(page.title, ui, 17f * d.fs, Modifier.align(Alignment.Center), weight = FontWeight.SemiBold, maxLines = 1)
            Chip(
                "Done", ui, d,
                Modifier.align(Alignment.CenterEnd).then(if (page == Page.Main) Modifier.focusRequester(navFocus) else Modifier),
            ) { onClose() }
        }
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState != Page.Main) {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "settings-page",
        ) { p ->
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
            ) {
                when (p) {
                    Page.Main -> mainPage(prefs, ui, d, st, isDefault, onSetDefault) { page = it }
                    Page.Appearance -> appearancePage(prefs, ui, d)
                    Page.Wallpaper -> wallpaperPage(prefs, ui, d, onPickImage)
                    Page.Display -> displayPage(prefs, ui, d)
                    Page.DockPage -> dockPage(prefs, ui, d, st)
                    Page.Apps -> appsPage(prefs, ui, d, st)
                    Page.Idle -> idlePage(prefs, ui, d, onPreviewIdle)
                    Page.Navigation -> navigationPage(prefs, ui, d)
                    Page.General -> generalPage(prefs, ui, d, deviceIsTv, isDefault, onSetDefault)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ pages

private fun LazyListScope.mainPage(
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, isDefault: Boolean,
    onSetDefault: () -> Unit, go: (Page) -> Unit,
) {
    val themeName = when (prefs.theme) { "light" -> "Light"; "dark" -> "Dark"; else -> "Automatic" }
    val wallName = if (prefs.wallpaper == "custom") "My image" else (WALLPAPERS.firstOrNull { it.id == prefs.wallpaper }?.name ?: "")
    val layoutName = when (prefs.layoutMode) { "tv" -> "TV"; "mobile" -> "Phone"; else -> "Auto" }
    val idleName = if (prefs.idleMinutes == 0) "Never" else prefs.idleMinutes.toString() + " min"
    val dockCount = prefs.dock.count { pkg -> st.apps.any { it.pkg == pkg } }

    if (!isDefault) {
        group {
            row { i, n ->
                SetRow(
                    ui, d, i, n, "Make Mobo Launcher the default", glyph = Glyph.General, tint = ui.accent,
                    subtitle = "Pressing Home will then return to this launcher.", chevron = true, onClick = onSetDefault,
                )
            }
        }
        gap()
    }
    group {
        row { i, n -> SetRow(ui, d, i, n, "Appearance", glyph = Glyph.Appearance, tint = Color(0xFF5E5CE6), value = themeName, chevron = true, onClick = { go(Page.Appearance) }) }
        row { i, n -> SetRow(ui, d, i, n, "Wallpaper", glyph = Glyph.Wallpaper, tint = Color(0xFF32ADE6), value = wallName, chevron = true, onClick = { go(Page.Wallpaper) }) }
        row { i, n -> SetRow(ui, d, i, n, "Display", glyph = Glyph.Display, tint = Color(0xFF0A84FF), value = layoutName, chevron = true, onClick = { go(Page.Display) }) }
    }
    gap()
    group {
        row { i, n -> SetRow(ui, d, i, n, "Dock", glyph = Glyph.Dock, tint = Color(0xFF636366), value = dockCount.toString() + " apps", chevron = true, onClick = { go(Page.DockPage) }) }
        row { i, n -> SetRow(ui, d, i, n, "Applications", glyph = Glyph.Apps, tint = Color(0xFFFF9F0A), value = st.apps.size.toString(), chevron = true, onClick = { go(Page.Apps) }) }
        row { i, n -> SetRow(ui, d, i, n, "Idle Screen", glyph = Glyph.Idle, tint = Color(0xFFBF5AF2), value = idleName, chevron = true, onClick = { go(Page.Idle) }) }
    }
    gap()
    group {
        row { i, n -> SetRow(ui, d, i, n, "Navigation", glyph = Glyph.Navigation, tint = Color(0xFF30D158), chevron = true, onClick = { go(Page.Navigation) }) }
        row { i, n -> SetRow(ui, d, i, n, "General", glyph = Glyph.General, tint = Color(0xFF8E8E93), chevron = true, onClick = { go(Page.General) }) }
    }
}

private fun LazyListScope.appearancePage(prefs: LauncherPrefs, ui: Ui, d: Dims) {
    header("Theme", ui, d)
    group {
        row { i, n -> SetRow(ui, d, i, n, "Automatic", subtitle = "Follows the system setting", check = prefs.theme == "auto", onClick = { prefs.theme = "auto" }) }
        row { i, n -> SetRow(ui, d, i, n, "Light", check = prefs.theme == "light", onClick = { prefs.theme = "light" }) }
        row { i, n -> SetRow(ui, d, i, n, "Dark", check = prefs.theme == "dark", onClick = { prefs.theme = "dark" }) }
    }
    gap()
    header("Accent Color", ui, d)
    group {
        row { i, n ->
            SegRow(ui, d, i, n, ACCENT_NAMES[prefs.accent.coerceIn(0, ACCENT_NAMES.size - 1)]) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ACCENTS.forEachIndexed { idx, c -> ColorDot(c, prefs.accent == idx, ui, d) { prefs.accent = idx } }
                }
            }
        }
    }
    gap()
    header("Surfaces and Icons", ui, d)
    group {
        row { i, n -> SegRow(ui, d, i, n, "Glass") { Seg(listOf("Subtle" to 0, "Regular" to 1, "Vivid" to 2), prefs.glass, ui, d) { prefs.glass = it } } }
        row { i, n -> SegRow(ui, d, i, n, "Icon style") { Seg(listOf("Rounded plate" to "plate", "Original" to "original"), prefs.iconStyle, ui, d) { prefs.iconStyle = it } } }
        row { i, n -> SegRow(ui, d, i, n, "Icon size") { Seg(listOf("Small" to 0, "Medium" to 1, "Large" to 2), prefs.iconSize, ui, d) { prefs.iconSize = it } } }
        row { i, n -> SegRow(ui, d, i, n, "Animations") { Seg(listOf("Off" to 0, "Low" to 1, "Normal" to 2), prefs.anim, ui, d) { prefs.anim = it } } }
    }
    footer("Rounded plate gives every app the same shape and weight.", ui, d)
}

private fun LazyListScope.wallpaperPage(prefs: LauncherPrefs, ui: Ui, d: Dims, onPickImage: () -> Unit) {
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WALLPAPERS.forEach { w -> WallpaperThumb(w, prefs.wallpaper == w.id, ui, d) { prefs.wallpaper = w.id } }
        }
    }
    gap()
    group {
        row { i, n -> SetRow(ui, d, i, n, "Choose Image…", glyph = Glyph.Wallpaper, tint = Color(0xFF32ADE6), chevron = true, onClick = onPickImage) }
        if (prefs.customUri.isNotEmpty()) {
            row { i, n -> SetRow(ui, d, i, n, "Use My Image", check = prefs.wallpaper == "custom", onClick = { prefs.wallpaper = "custom" }) }
        }
    }
    footer("Wallpapers are drawn by the launcher. Your own image is read through the system picker.", ui, d)
}

private fun LazyListScope.displayPage(prefs: LauncherPrefs, ui: Ui, d: Dims) {
    group {
        row { i, n -> SegRow(ui, d, i, n, "Layout") { Seg(listOf("Auto" to "auto", "TV" to "tv", "Phone" to "mobile"), prefs.layoutMode, ui, d) { prefs.layoutMode = it } } }
        row { i, n -> SegRow(ui, d, i, n, "Interface size") { Seg(listOf("Small" to 0, "Default" to 1, "Large" to 2), prefs.uiScale, ui, d) { prefs.uiScale = it } } }
        row { i, n -> SegRow(ui, d, i, n, "Clock format") { Seg(listOf("24-hour" to true, "12-hour" to false), prefs.clock24, ui, d) { prefs.clock24 = it } } }
    }
    gap()
    group {
        row { i, n -> ToggleRow(ui, d, i, n, "Clock in menu bar", prefs.showClock) { prefs.showClock = it } }
        row { i, n -> ToggleRow(ui, d, i, n, "Blur behind windows", prefs.libBlur, "Blurs the desktop behind Applications and Settings. Needs Android 12 or newer.") { prefs.libBlur = it } }
    }
}

private fun LazyListScope.dockPage(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState) {
    val byPkg = st.apps.associateBy { it.pkg }
    val pinned = prefs.dock.mapNotNull { byPkg[it] }
    val candidates = st.apps.filter { it.pkg !in prefs.dock && it.pkg !in prefs.hidden }
    group {
        row { i, n -> SegRow(ui, d, i, n, "Size") { Seg(listOf("Small" to 0, "Medium" to 1, "Large" to 2), prefs.dockSize, ui, d) { prefs.dockSize = it } } }
        row { i, n -> ToggleRow(ui, d, i, n, "Magnification", prefs.dockMagnify, "Enlarge the icon under the pointer or remote focus.") { prefs.dockMagnify = it } }
        row { i, n -> ToggleRow(ui, d, i, n, "Show names", prefs.dockLabels) { prefs.dockLabels = it } }
    }
    gap()
    header("Pinned (" + pinned.size + " of " + DOCK_MAX + ")", ui, d)
    if (pinned.isEmpty()) {
        footer("Nothing pinned. Add apps below.", ui, d)
    } else {
        groupOf(pinned, { "pin-" + it.pkg }) { app, i, n ->
            SetRow(
                ui, d, i, n, app.label,
                icon = { AppIconView(rememberIcon(app), (30 * d.fs).dp, prefs.iconStyle, null) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (i > 0) MiniButton(Glyph.Up, ui, d) { prefs.dock = swapPkgs(prefs.dock, app.pkg, pinned[i - 1].pkg) }
                        if (i < n - 1) MiniButton(Glyph.Down, ui, d) { prefs.dock = swapPkgs(prefs.dock, app.pkg, pinned[i + 1].pkg) }
                        MiniButton(Glyph.Minus, ui, d) { prefs.dock = prefs.dock - app.pkg }
                    }
                },
            )
        }
    }
    gap()
    header("Add to Dock", ui, d)
    groupOf(candidates, { "add-" + it.pkg }) { app, i, n ->
        SetRow(
            ui, d, i, n, app.label,
            icon = { AppIconView(rememberIcon(app), (30 * d.fs).dp, prefs.iconStyle, null) },
            onClick = { if (prefs.dock.size < DOCK_MAX) prefs.dock = prefs.dock + app.pkg },
            trailing = { GlyphView(Glyph.Plus, ui.accent, (20 * d.fs).dp) },
        )
    }
}

private fun LazyListScope.appsPage(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState) {
    val byPkg = st.apps.associateBy { it.pkg }
    val onDesktop = prefs.desktop.mapNotNull { byPkg[it] }
    val hiddenApps = prefs.hidden.mapNotNull { byPkg[it] }
    val candidates = st.apps.filter { it.pkg !in prefs.desktop && it.pkg !in prefs.hidden }
    group {
        row { i, n -> SegRow(ui, d, i, n, "Sort") { Seg(listOf("A–Z" to "az", "Most used" to "used"), prefs.libSort, ui, d) { prefs.libSort = it } } }
        row { i, n -> ToggleRow(ui, d, i, n, "Show names", prefs.libLabels) { prefs.libLabels = it } }
    }
    gap()
    group {
        row { i, n -> ToggleRow(ui, d, i, n, "Desktop shortcuts", prefs.desktopShortcuts, "System Settings and Applications icons on the desktop.") { prefs.desktopShortcuts = it } }
    }
    gap()
    header("On the Desktop", ui, d)
    if (onDesktop.isEmpty()) {
        footer("The desktop has no app shortcuts.", ui, d)
    } else {
        groupOf(onDesktop, { "dt-" + it.pkg }) { app, i, n ->
            SetRow(
                ui, d, i, n, app.label,
                icon = { AppIconView(rememberIcon(app), (30 * d.fs).dp, prefs.iconStyle, null) },
                onClick = { prefs.desktop = prefs.desktop - app.pkg },
                trailing = { GlyphView(Glyph.Minus, ui.red, (20 * d.fs).dp) },
            )
        }
    }
    gap()
    header("Hidden from Library", ui, d)
    if (hiddenApps.isEmpty()) {
        footer("No hidden apps.", ui, d)
    } else {
        groupOf(hiddenApps, { "hd-" + it.pkg }) { app, i, n ->
            SetRow(
                ui, d, i, n, app.label, value = "Show",
                icon = { AppIconView(rememberIcon(app), (30 * d.fs).dp, prefs.iconStyle, null) },
                onClick = { prefs.hidden = prefs.hidden - app.pkg },
            )
        }
    }
    gap()
    header("Add to Desktop", ui, d)
    groupOf(candidates, { "ad-" + it.pkg }) { app, i, n ->
        SetRow(
            ui, d, i, n, app.label,
            icon = { AppIconView(rememberIcon(app), (30 * d.fs).dp, prefs.iconStyle, null) },
            onClick = { prefs.desktop = prefs.desktop + app.pkg },
            trailing = { GlyphView(Glyph.Plus, ui.accent, (20 * d.fs).dp) },
        )
    }
}

private fun LazyListScope.idlePage(prefs: LauncherPrefs, ui: Ui, d: Dims, onPreviewIdle: () -> Unit) {
    header("Start After", ui, d)
    val options = listOf(1 to "1 minute", 2 to "2 minutes", 5 to "5 minutes", 10 to "10 minutes", 0 to "Never")
    group {
        options.forEach { (minutes, label) ->
            row { i, n -> SetRow(ui, d, i, n, label, check = prefs.idleMinutes == minutes, onClick = { prefs.idleMinutes = minutes }) }
        }
    }
    footer("The idle screen appears after no touch, mouse, keyboard or remote input. Any input dismisses it. It never opens apps, locks the device or turns the display off.", ui, d)
    gap()
    group {
        row { i, n -> ToggleRow(ui, d, i, n, "Show date", prefs.idleShowDate) { prefs.idleShowDate = it } }
        row { i, n -> SetRow(ui, d, i, n, "Preview Idle Screen", chevron = true, onClick = onPreviewIdle) }
    }
}

private fun LazyListScope.navigationPage(prefs: LauncherPrefs, ui: Ui, d: Dims) {
    group {
        row { i, n -> ToggleRow(ui, d, i, n, "Focus ring", prefs.focusRing, "Outline the item selected with a remote, keyboard or D-pad.") { prefs.focusRing = it } }
        row { i, n -> ToggleRow(ui, d, i, n, "Start on the Dock", prefs.autoFocus, "On TV layouts, put the selection on the Dock when the desktop opens.") { prefs.autoFocus = it } }
    }
    footer("Mouse and air-mouse pointers highlight items under the pointer automatically.", ui, d)
}

private fun LazyListScope.generalPage(
    prefs: LauncherPrefs, ui: Ui, d: Dims, deviceIsTv: Boolean, isDefault: Boolean, onSetDefault: () -> Unit,
) {
    header("Home App", ui, d)
    group {
        row { i, n -> SetRow(ui, d, i, n, "Default launcher", value = if (isDefault) "Mobo Launcher" else "Not set") }
        if (!isDefault) {
            row { i, n -> SetRow(ui, d, i, n, "Make Mobo Launcher the default", titleColor = ui.accent, chevron = true, onClick = onSetDefault) }
        }
    }
    footer("The status is read from Android. Android may ask you to confirm. To undo it, choose another Home app in Android's own settings.", ui, d)
    gap()
    header("About", ui, d)
    group {
        row { i, n ->
            val ctx = LocalContext.current
            val version = remember {
                try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
            }
            SetRow(ui, d, i, n, "Version", value = version)
        }
        row { i, n -> SetRow(ui, d, i, n, "Device", value = if (deviceIsTv) "TV / Android Box" else "Phone or tablet") }
    }
    gap()
    group {
        row { i, n ->
            var confirm by remember { mutableStateOf(false) }
            SetRow(
                ui, d, i, n, if (confirm) "Select again to confirm" else "Reset All Settings",
                titleColor = ui.red,
                onClick = { if (confirm) { prefs.resetAll(); confirm = false } else { confirm = true } },
            )
        }
    }
    footer("Resets appearance, Dock, desktop and all other launcher settings.", ui, d)
}
