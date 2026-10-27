package app.mobolauncher

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class Screen { Home, Library, Settings }

class AppsState(val apps: List<AppEntry>, val loading: Boolean, val error: String?)

const val DOCK_MAX = 12

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(15_000L)
    }
}

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
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState,
    dockApps: List<AppEntry>, desktopApps: List<AppEntry>,
    onOpenLibrary: () -> Unit, onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (d.tv) {
            delay(250)
            try { focus.requestFocus() } catch (e: Exception) { }
        }
    }
    val now by rememberNow()
    val timeFmt = DateTimeFormatter.ofPattern(if (prefs.clock24) "HH:mm" else "h:mm")
    val barFmt = DateTimeFormatter.ofPattern(if (prefs.clock24) "EEE d MMM  HH:mm" else "EEE d MMM  h:mm a")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")

    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        // Menu bar
        Row(
            Modifier.fillMaxWidth().glass(ui, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt("Desktop", ui, 16f * d.fs, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (!d.compact) Txt(now.format(barFmt), ui, 14f * d.fs, sub = true)
            Chip("Library", ui, d) { onOpenLibrary() }
            Chip("Settings", ui, d) { onOpenSettings() }
        }

        // Desktop area
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
            val message = when {
                st.loading -> "Loading apps…"
                st.error != null -> "Couldn't read the app list: " + st.error
                else -> null
            }
            if (message != null) {
                Txt(message, ui, 20f * d.fs, Modifier.align(Alignment.Center), align = TextAlign.Center)
            }
            Column(Modifier.fillMaxSize()) {
                if (prefs.showClock) {
                    Txt(
                        now.format(timeFmt), ui,
                        (if (d.tv) 96f else if (d.compact) 60f else 78f),
                        weight = FontWeight.Light, shadow = true,
                    )
                    Txt(now.format(dateFmt), ui, 20f * d.fs, sub = true, shadow = true)
                }
                if (desktopApps.isNotEmpty()) {
                    Spacer(Modifier.size(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(d.icon + (if (d.tv) 64.dp else 44.dp)),
                        modifier = Modifier.fillMaxWidth(if (d.compact) 1f else 0.7f).weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(desktopApps, key = { it.pkg }) { app ->
                            AppTile(app, ui, d, prefs, d.icon, true) { launchApp(ctx, app, prefs) }
                        }
                    }
                }
            }
        }

        // Dock
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Dock(dockApps, prefs, ui, d, focus, onOpenLibrary) { launchApp(ctx, it, prefs) }
        }
    }
}

@Composable
fun Dock(
    apps: List<AppEntry>, prefs: LauncherPrefs, ui: Ui, d: Dims, focus: FocusRequester,
    onLibrary: () -> Unit, onLaunch: (AppEntry) -> Unit,
) {
    val factor = when (prefs.dockSize) {
        0 -> 0.85f
        2 -> 1.2f
        else -> 1f
    }
    val iconSize = (d.dockIcon.value * factor).dp
    Row(
        Modifier.glass(ui, RoundedCornerShape(28.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DockSlot("Library", ui, d, prefs, iconSize, Modifier.focusRequester(focus), onClick = onLibrary) {
            Box(
                Modifier.size(iconSize).clip(Squircle)
                    .background(Brush.linearGradient(listOf(ui.accent, ui.accent.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(iconSize * 0.07f)) {
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.07f)) {
                            repeat(3) {
                                Box(Modifier.size(iconSize * 0.14f).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            }
        }
        if (apps.isNotEmpty()) {
            Box(Modifier.size(1.dp, iconSize * 0.8f).background(ui.text.copy(alpha = 0.25f)))
        }
        apps.forEach { app ->
            DockSlot(app.label, ui, d, prefs, iconSize, onClick = { onLaunch(app) }) {
                AppIconView(rememberIcon(app), iconSize, prefs.iconStyle, app.label)
            }
        }
    }
}

// =============================================================== Library

@Composable
fun LibraryScreen(
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (d.tv) {
            delay(250)
            try { closeFocus.requestFocus() } catch (e: Exception) { }
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
    val iconSize = (d.icon.value * factor).dp
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier.fillMaxSize().padding(vertical = 12.dp).clip(shape)
            .background(Color.Black.copy(alpha = if (ui.dark) 0.35f else 0.12f))
            .glass(ui, shape).padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Library", ui, 26f * d.fs, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Chip(if (edit) "Done" else "Edit", ui, d, selected = edit) { edit = !edit }
                Chip("Close", ui, d, Modifier.focusRequester(closeFocus)) { onClose() }
            }
            if (!d.tv) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = ui.text, fontSize = (16 * d.fs).sp),
                    cursorBrush = SolidColor(ui.text),
                    modifier = Modifier.fillMaxWidth().glass(ui, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Txt("Search apps", ui, 16f * d.fs, sub = true)
                            inner()
                        }
                    },
                )
            }
            if (edit) Txt("Select an app to pin it, add it to the desktop, or hide it.", ui, 14f * d.fs, sub = true)
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
                    items(list, key = { it.pkg }) { app ->
                        AppTile(
                            app, ui, d, prefs, iconSize, prefs.libLabels,
                            pinned = app.pkg in prefs.dock,
                            onLongClick = { selected = app },
                        ) {
                            if (edit) selected = app else launchApp(ctx, app, prefs)
                        }
                    }
                }
            }
        }
        val sel = selected
        if (sel != null) {
            androidx.activity.compose.BackHandler { selected = null }
            AppActions(sel, prefs, ui, d, Modifier.align(Alignment.BottomCenter)) { selected = null }
        }
    }
}

@Composable
fun AppActions(app: AppEntry, prefs: LauncherPrefs, ui: Ui, d: Dims, modifier: Modifier, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(22.dp)
    val pinned = app.pkg in prefs.dock
    val onDesktop = app.pkg in prefs.desktop
    Column(
        modifier.fillMaxWidth().clip(shape).background(Color.Black.copy(alpha = 0.55f)).glass(ui, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(app.label, ui, 18f * d.fs, weight = FontWeight.SemiBold, maxLines = 1)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Open", ui, d) { launchApp(ctx, app, prefs); onDone() }
            Chip(if (pinned) "Remove from Dock" else "Add to Dock", ui, d) {
                if (pinned) prefs.dock = prefs.dock - app.pkg
                else if (prefs.dock.size >= DOCK_MAX) Toast.makeText(ctx, "The Dock is full.", Toast.LENGTH_SHORT).show()
                else prefs.dock = prefs.dock + app.pkg
                onDone()
            }
            Chip(if (onDesktop) "Remove from Desktop" else "Add to Desktop", ui, d) {
                prefs.desktop = if (onDesktop) prefs.desktop - app.pkg else prefs.desktop + app.pkg
                onDone()
            }
            Chip("Hide from Library", ui, d) { prefs.hidden = prefs.hidden + app.pkg; onDone() }
            Chip("Cancel", ui, d) { onDone() }
        }
    }
}

// =============================================================== Settings

enum class Cat(val label: String) {
    General("General"), Appearance("Appearance"), DockCat("Dock"),
    DesktopCat("Desktop"), LibraryCat("Library"), About("About"),
}

@Composable
fun SettingsScreen(
    prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, deviceIsTv: Boolean,
    onPickImage: () -> Unit, onBack: () -> Unit,
) {
    var cat by remember { mutableStateOf(Cat.General) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (d.tv) {
            delay(250)
            try { backFocus.requestFocus() } catch (e: Exception) { }
        }
    }
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("Back", ui, d, Modifier.focusRequester(backFocus)) { onBack() }
            Txt("Settings", ui, 26f * d.fs, weight = FontWeight.SemiBold)
        }
        if (d.compact) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Cat.values().forEach { c -> Chip(c.label, ui, d, selected = cat == c) { cat = c } }
            }
            SettingsContent(cat, prefs, ui, d, st, deviceIsTv, onPickImage, Modifier.weight(1f).fillMaxWidth())
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.width((220 * d.fs).dp).fillMaxHeight().glass(ui, RoundedCornerShape(22.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Cat.values().forEach { c ->
                        Chip(c.label, ui, d, Modifier.fillMaxWidth(), selected = cat == c) { cat = c }
                    }
                }
                SettingsContent(cat, prefs, ui, d, st, deviceIsTv, onPickImage, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
fun SettingsContent(
    cat: Cat, prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState, deviceIsTv: Boolean,
    onPickImage: () -> Unit, modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (cat) {
            Cat.General -> generalSettings(prefs, ui, d)
            Cat.Appearance -> appearanceSettings(prefs, ui, d, onPickImage)
            Cat.DockCat -> dockSettings(prefs, ui, d, st)
            Cat.DesktopCat -> desktopSettings(prefs, ui, d, st)
            Cat.LibraryCat -> librarySettings(prefs, ui, d, st)
            Cat.About -> aboutSettings(prefs, ui, d, deviceIsTv)
        }
    }
}

private fun LazyListScope.generalSettings(prefs: LauncherPrefs, ui: Ui, d: Dims) {
    item {
        SettingRow("Layout", ui, d, "Auto picks TV or mobile layout for this device.") {
            Choice(listOf("Auto" to "auto", "TV" to "tv", "Mobile" to "mobile"), prefs.layoutMode, ui, d) { prefs.layoutMode = it }
        }
    }
    item {
        SettingRow("Clock format", ui, d) {
            Choice(listOf("24-hour" to true, "12-hour" to false), prefs.clock24, ui, d) { prefs.clock24 = it }
        }
    }
    item {
        SettingRow("Animations", ui, d, "Applies to focus, Dock and toggles.") {
            Choice(listOf("Off" to 0, "Low" to 1, "Normal" to 2), prefs.anim, ui, d) { prefs.anim = it }
        }
    }
}

private fun LazyListScope.appearanceSettings(prefs: LauncherPrefs, ui: Ui, d: Dims, onPickImage: () -> Unit) {
    item {
        SettingRow("Theme", ui, d) {
            Choice(listOf("Auto" to "auto", "Light" to "light", "Dark" to "dark"), prefs.theme, ui, d) { prefs.theme = it }
        }
    }
    item {
        SettingRow("Accent color", ui, d) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ACCENTS.forEachIndexed { i, c -> ColorDot(c, prefs.accent == i, ui, d) { prefs.accent = i } }
            }
        }
    }
    item {
        SettingRow("Glass intensity", ui, d, "How opaque panels and the Dock look.") {
            Choice(listOf("Subtle" to 0, "Regular" to 1, "Vivid" to 2), prefs.glass, ui, d) { prefs.glass = it }
        }
    }
    item {
        SettingRow("Icon style", ui, d, "Rounded plate gives every app the same shape.") {
            Choice(listOf("Rounded plate" to "plate", "Original" to "original"), prefs.iconStyle, ui, d) { prefs.iconStyle = it }
        }
    }
    item {
        SettingRow("Icon size", ui, d) {
            Choice(listOf("Small" to 0, "Medium" to 1, "Large" to 2), prefs.iconSize, ui, d) { prefs.iconSize = it }
        }
    }
    item {
        SettingRow("Wallpaper", ui, d) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                WALLPAPERS.forEach { w -> WallpaperThumb(w, prefs.wallpaper == w.id, ui, d) { prefs.wallpaper = w.id } }
                if (prefs.customUri.isNotEmpty()) {
                    Chip("My image", ui, d, selected = prefs.wallpaper == "custom") { prefs.wallpaper = "custom" }
                }
                Chip("Choose image…", ui, d) { onPickImage() }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, ui: Ui, d: Dims, prefs: LauncherPrefs, actions: @Composable () -> Unit) {
    val icon = rememberIcon(app)
    val shape = RoundedCornerShape(18.dp)
    val head: @Composable () -> Unit = {
        AppIconView(icon, (40 * d.fs).dp, prefs.iconStyle, app.label)
    }
    Column(
        Modifier.fillMaxWidth().glass(ui, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            head()
            Txt(app.label, ui, 16f * d.fs, Modifier.weight(1f), maxLines = 1)
            if (!d.compact) actions()
        }
        if (d.compact) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

private fun LazyListScope.dockSettings(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState) {
    val byPkg = st.apps.associateBy { it.pkg }
    val pinned = prefs.dock.mapNotNull { byPkg[it] }
    val candidates = st.apps.filter { it.pkg !in prefs.dock && it.pkg !in prefs.hidden }
    item {
        SettingRow("Dock size", ui, d) {
            Choice(listOf("Small" to 0, "Medium" to 1, "Large" to 2), prefs.dockSize, ui, d) { prefs.dockSize = it }
        }
    }
    item {
        SettingRow("Magnify on focus", ui, d, "Enlarge the icon under the cursor or remote focus.") {
            Toggle(prefs.dockMagnify, ui, d) { prefs.dockMagnify = it }
        }
    }
    item {
        SettingRow("Show app names", ui, d) {
            Toggle(prefs.dockLabels, ui, d) { prefs.dockLabels = it }
        }
    }
    item { SectionTitle("Pinned apps (" + pinned.size + "/" + DOCK_MAX + ")", ui, d) }
    if (pinned.isEmpty()) item { Txt("Nothing pinned yet. Add apps below.", ui, 15f * d.fs, sub = true) }
    items(pinned, key = { "p-" + it.pkg }) { app ->
        val pos = pinned.indexOf(app)
        AppRow(app, ui, d, prefs) {
            if (pos > 0) Chip("Move left", ui, d) { prefs.dock = swapPkgs(prefs.dock, app.pkg, pinned[pos - 1].pkg) }
            if (pos < pinned.size - 1) Chip("Move right", ui, d) { prefs.dock = swapPkgs(prefs.dock, app.pkg, pinned[pos + 1].pkg) }
            Chip("Remove", ui, d) { prefs.dock = prefs.dock - app.pkg }
        }
    }
    item { SectionTitle("Add to Dock", ui, d) }
    items(candidates, key = { "a-" + it.pkg }) { app ->
        AppRow(app, ui, d, prefs) {
            Chip("Add", ui, d) { if (prefs.dock.size < DOCK_MAX) prefs.dock = prefs.dock + app.pkg }
        }
    }
}

private fun LazyListScope.desktopSettings(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState) {
    val byPkg = st.apps.associateBy { it.pkg }
    val onDesktop = prefs.desktop.mapNotNull { byPkg[it] }
    val candidates = st.apps.filter { it.pkg !in prefs.desktop && it.pkg !in prefs.hidden }
    item {
        SettingRow("Show clock", ui, d, "Large clock and date on the desktop.") {
            Toggle(prefs.showClock, ui, d) { prefs.showClock = it }
        }
    }
    item { SectionTitle("Desktop shortcuts", ui, d) }
    if (onDesktop.isEmpty()) item { Txt("The desktop is clean. Add shortcuts below if you want some.", ui, 15f * d.fs, sub = true) }
    items(onDesktop, key = { "d-" + it.pkg }) { app ->
        AppRow(app, ui, d, prefs) { Chip("Remove", ui, d) { prefs.desktop = prefs.desktop - app.pkg } }
    }
    item { SectionTitle("Add to desktop", ui, d) }
    items(candidates, key = { "c-" + it.pkg }) { app ->
        AppRow(app, ui, d, prefs) { Chip("Add", ui, d) { prefs.desktop = prefs.desktop + app.pkg } }
    }
}

private fun LazyListScope.librarySettings(prefs: LauncherPrefs, ui: Ui, d: Dims, st: AppsState) {
    val byPkg = st.apps.associateBy { it.pkg }
    val hiddenApps = prefs.hidden.mapNotNull { byPkg[it] }
    item {
        SettingRow("Sort apps", ui, d) {
            Choice(listOf("A–Z" to "az", "Most used" to "used"), prefs.libSort, ui, d) { prefs.libSort = it }
        }
    }
    item {
        SettingRow("Show app names", ui, d) {
            Toggle(prefs.libLabels, ui, d) { prefs.libLabels = it }
        }
    }
    item { SectionTitle("Hidden apps", ui, d) }
    if (hiddenApps.isEmpty()) item { Txt("No hidden apps.", ui, 15f * d.fs, sub = true) }
    items(hiddenApps, key = { "h-" + it.pkg }) { app ->
        AppRow(app, ui, d, prefs) { Chip("Show", ui, d) { prefs.hidden = prefs.hidden - app.pkg } }
    }
}

private fun LazyListScope.aboutSettings(prefs: LauncherPrefs, ui: Ui, d: Dims, deviceIsTv: Boolean) {
    item {
        val ctx = LocalContext.current
        val version = remember {
            try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
        }
        SettingRow("Mobo Launcher", ui, d, "Version " + version) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt("Detected device type: " + (if (deviceIsTv) "TV / Android Box" else "Phone or tablet"), ui, 14f * d.fs, sub = true)
                Txt("This app is a normal launcher app. It is not set as the default Home app.", ui, 14f * d.fs, sub = true)
            }
        }
    }
    item {
        var confirm by remember { mutableStateOf(false) }
        SettingRow("Reset", ui, d, "Restores every launcher setting, Dock and desktop to defaults.") {
            Chip(if (confirm) "Select again to confirm" else "Reset all settings", ui, d) {
                if (confirm) { prefs.resetAll(); confirm = false } else confirm = true
            }
        }
    }
}
