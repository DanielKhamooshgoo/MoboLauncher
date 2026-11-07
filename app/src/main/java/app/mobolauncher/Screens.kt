package app.mobolauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

class AppsState(val apps: List<AppEntry>, val loading: Boolean, val error: String?)

const val DOCK_MAX = 12

val SettingsBase = androidx.compose.ui.graphics.Color(0xFF8E8E93)
val AppsBase = androidx.compose.ui.graphics.Color(0xFF0A84FF)

@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(15_000L)
    }
}

fun formatTime(pattern: String, millis: Long): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(millis))

fun swapPkgs(list: List<String>, a: String, b: String): List<String> {
    val m = list.toMutableList()
    val ia = m.indexOf(a)
    val ib = m.indexOf(b)
    if (ia < 0 || ib < 0) return list
    m[ia] = b
    m[ib] = a
    return m
}

// =============================================================== Desktop

@Composable
fun DesktopScreen(
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, active: Boolean,
    dockApps: List<AppEntry>, desktopApps: List<AppEntry>,
    onOpenLibrary: () -> Unit, onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val hud = LocalHud.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (active && d.tv && prefs.autoFocus) {
            delay(300)
            try { focus.requestFocus() } catch (e: Exception) { }
        }
    }
    val tileCell = d.desktopIcon + (if (d.tv) 56.dp else 40.dp)
    val launch: (AppEntry) -> Unit = { app ->
        if (!launchApp(ctx, app, prefs)) hud("Can't open " + app.label + ".")
    }
    var menuOpen by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val now by rememberNow()
    val clockPattern = if (prefs.clock24) "EEE d MMM  HH:mm" else "EEE d MMM  h:mm a"
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 6.dp, bottom = 10.dp)) {
            // Slim menu strip: small power logo on the left, small clock on the right.
            Row(
                Modifier.fillMaxWidth().height((28 * d.fs).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoButton(ui, d) { menuOpen = !menuOpen }
                Spacer(Modifier.weight(1f))
                if (prefs.showClock) {
                    Txt(formatTime(clockPattern, now), ui, 13f * d.fs, shadow = true, maxLines = 1)
                }
            }

            // Desktop icons stack down the right edge, like a Mac desktop.
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                val message = when {
                    st.loading -> "Loading apps…"
                    st.error != null -> "Couldn't read the app list: " + st.error
                    else -> null
                }
                if (message != null) {
                    Txt(message, ui, 20f * d.fs, Modifier.align(Alignment.Center), align = TextAlign.Center)
                }
                Column(
                    Modifier.align(Alignment.TopEnd).width(tileCell).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (prefs.desktopShortcuts) {
                        ShortcutTile("Applications", ui, d, d.desktopIcon, onClick = onOpenLibrary) { s -> LaunchpadIcon(s) }
                        ShortcutTile("System Settings", ui, d, d.desktopIcon, onClick = onOpenSettings) { s -> SystemSettingsIcon(s) }
                    }
                    desktopApps.forEach { app ->
                        AppTile(app, ui, d, prefs, d.desktopIcon, true) { launch(app) }
                    }
                }
            }

            Dock(dockApps, prefs, ui, d, focus, onOpenLibrary, onOpenSettings, launch)
        }

        if (menuOpen) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { menuOpen = false } })
            LaunchedEffect(Unit) {
                if (d.tv) {
                    delay(150)
                    try { menuFocus.requestFocus() } catch (e: Exception) { }
                }
            }
            LauncherMenu(
                ui, d, Modifier.align(Alignment.TopStart).padding(top = (30 * d.fs).dp), menuFocus,
                onSettings = {
                    menuOpen = false
                    onOpenSettings()
                },
                onShutDown = {
                    menuOpen = false
                    val hint = PowerHelper.openPowerMenu(ctx)
                    if (hint != null) hud(hint)
                },
            )
        }
    }
}

@Composable
fun Dock(
    apps: List<AppEntry>, prefs: LauncherPrefs, ui: Ui, d: Dims, focus: FocusRequester,
    onLibrary: () -> Unit, onSettings: () -> Unit, onLaunch: (AppEntry) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val factor = when (prefs.dockSize) {
            0 -> 0.85f
            2 -> 1.2f
            else -> 1f
        }
        val slots = apps.size + 2
        val gap = d.dockIcon * 0.2f
        val padH = 16.dp
        // Reserve about one extra icon width so the magnified icons still fit on screen.
        val fit = (maxWidth - padH * 2 - gap * (slots - 1) - 24.dp) / (slots + 1)
        val base = minOf(d.dockIcon * factor, fit).coerceAtLeast(32.dp)
        val shape = RoundedCornerShape(base * 0.42f)
        var active by remember { mutableIntStateOf(-1) }

        fun magnify(index: Int): Float {
            if (!prefs.dockMagnify || active < 0) return 1f
            val peak = when (abs(index - active)) {
                0 -> 1.55f
                1 -> 1.27f
                2 -> 1.10f
                else -> 1f
            }
            return 1f + (peak - 1f) * ui.intensity.coerceAtMost(1f)
        }

        Row(
            Modifier.padding(bottom = base * 0.04f)
                .glassOpen(ui, shape)
                .padding(horizontal = padH, vertical = base * 0.17f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            DockSlot(
                "Applications", ui, d, prefs, base, magnify(0), Modifier.focusRequester(focus),
                onActive = { on -> if (on) { active = 0 } else if (active == 0) { active = -1 } },
                onClick = onLibrary,
            ) { s -> LaunchpadIcon(s) }
            if (apps.isNotEmpty()) {
                Box(Modifier.size(1.dp, base * 0.8f).background(ui.text.copy(alpha = 0.25f)))
            }
            apps.forEachIndexed { i, app ->
                val idx = i + 1
                DockSlot(
                    app.label, ui, d, prefs, base, magnify(idx),
                    onActive = { on -> if (on) { active = idx } else if (active == idx) { active = -1 } },
                    onClick = { onLaunch(app) },
                ) { s -> AppIconFor(app, s, prefs) }
            }
            Box(Modifier.size(1.dp, base * 0.8f).background(ui.text.copy(alpha = 0.25f)))
            val settingsIdx = apps.size + 1
            DockSlot(
                "System Settings", ui, d, prefs, base, magnify(settingsIdx),
                onActive = { on -> if (on) { active = settingsIdx } else if (active == settingsIdx) { active = -1 } },
                onClick = onSettings,
            ) { s -> SystemSettingsIcon(s) }
        }
    }
}

// =============================================================== Library

@Composable
fun LibraryPanel(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val hud = LocalHud.current
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (d.tv) {
            delay(350)
            try { firstFocus.requestFocus() } catch (e: Exception) { }
        }
    }
    val list = remember(st.apps, query, prefs.hidden, prefs.libSort, prefs.usageTick) {
        val base = st.apps.filter { it.pkg !in prefs.hidden && it.label.contains(query, ignoreCase = true) }
        if (prefs.libSort == "used") base.sortedByDescending { prefs.usageOf(it.pkg) } else base
    }
    val factor = when (prefs.iconSize) {
        0 -> 0.85f
        2 -> 1.2f
        else -> 1f
    }
    val iconSize = d.libIcon * factor
    val shape = RoundedCornerShape(d.rL)

    Box(Modifier.fillMaxSize().clip(shape).background(ui.sheet).glass(ui, shape)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloseDot(ui, d) { onClose() }
                Spacer(Modifier.weight(1f))
                Txt("Applications", ui, 18f * d.fs, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Chip(if (edit) "Done" else "Edit", ui, d, selected = edit) { edit = !edit }
            }
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = ui.text, fontSize = (16 * d.fs).sp),
                cursorBrush = SolidColor(ui.text),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(ui.text.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = (10 * d.fs).dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Txt("Search", ui, 16f * d.fs, sub = true)
                        inner()
                    }
                },
            )
            if (edit) {
                Txt("Select an app to pin it, add it to the desktop, or hide it.", ui, 14f * d.fs, sub = true)
            }
            if (list.isEmpty()) {
                Txt(
                    if (st.apps.isEmpty()) "No launchable apps found." else "No apps match.",
                    ui, 18f * d.fs, Modifier.fillMaxWidth().padding(top = 24.dp), align = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(iconSize + (if (d.tv) 64.dp else 44.dp)),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(list, key = { _, app -> app.pkg }) { index, app ->
                        AppTile(
                            app, ui, d, prefs, iconSize, prefs.libLabels,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            pinned = app.pkg in prefs.dock,
                            onLongClick = { selected = app },
                        ) {
                            if (edit) {
                                selected = app
                            } else if (launchApp(ctx, app, prefs)) {
                                onClose()
                            } else {
                                hud("Can't open " + app.label + ".")
                            }
                        }
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            BackHandler { selected = null }
            AppActions(sel, prefs, ui, d, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { selected = null }
        }
    }
}

@Composable
fun AppActions(app: AppEntry, prefs: LauncherPrefs, ui: Ui, d: Dims, modifier: Modifier, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val hud = LocalHud.current
    val shape = RoundedCornerShape(d.rM)
    val pinned = app.pkg in prefs.dock
    val onDesktop = app.pkg in prefs.desktop
    Column(
        modifier.fillMaxWidth().clip(shape).background(Color.Black.copy(alpha = 0.55f)).glass(ui, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(app.label, ui, 17f * d.fs, weight = FontWeight.SemiBold, maxLines = 1)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Open", ui, d) {
                if (!launchApp(ctx, app, prefs)) hud("Can't open " + app.label + ".")
                onDone()
            }
            Chip(if (pinned) "Remove from Dock" else "Add to Dock", ui, d) {
                if (pinned) {
                    prefs.dock = prefs.dock - app.pkg
                } else if (prefs.dock.size >= DOCK_MAX) {
                    hud("The Dock is full.")
                } else {
                    prefs.dock = prefs.dock + app.pkg
                }
                onDone()
            }
            Chip(if (onDesktop) "Remove from Desktop" else "Add to Desktop", ui, d) {
                prefs.desktop = if (onDesktop) prefs.desktop - app.pkg else prefs.desktop + app.pkg
                onDone()
            }
            Chip("Hide", ui, d) { prefs.hidden = prefs.hidden + app.pkg; onDone() }
            Chip("Cancel", ui, d) { onDone() }
        }
    }
}

// =============================================================== Idle screen

@Composable
fun IdleScreen(prefs: LauncherPrefs, ui: Ui, d: Dims) {
    val now by rememberNow()
    val timeText = formatTime(if (prefs.clock24) "H:mm" else "h:mm", now)
    val dateText = formatTime("EEE MMM d", now)
    val clockSize = if (d.compact) d.wDp * 0.24f else minOf(d.wDp * 0.17f, d.hDp * 0.30f)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(top = (d.hDp * 0.09f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (prefs.idleShowDate) {
                BasicText(
                    dateText,
                    style = TextStyle(color = Color(0xFFDDF3FF), fontSize = (22 * d.fs).sp, fontWeight = FontWeight.Medium),
                )
            }
            BasicText(
                timeText,
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFBFE0FF))),
                    fontSize = clockSize.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
        }
    }
}
