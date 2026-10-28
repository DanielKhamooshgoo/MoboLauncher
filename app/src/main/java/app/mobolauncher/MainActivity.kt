package app.mobolauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(LauncherPrefs(this), tick) }
    }
    override fun onResume() { super.onResume(); tick++ }
}

@Composable
fun LauncherApp(prefs: LauncherPrefs, tick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("home") }
    var settingsPage by remember { mutableStateOf("root") }
    var idle by remember { mutableStateOf(false) }
    var idleReturnScreen by remember { mutableStateOf("home") }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val interact: () -> Unit = { lastInteraction = System.currentTimeMillis(); if (idle) idle = false }

    LaunchedEffect(tick) {
        try { apps = withContext(Dispatchers.IO) { loadApps(ctx) }; error = null }
        catch (e: Exception) { error = e.message ?: "Unknown error" }
        loading = false
    }
    LaunchedEffect(lastInteraction, prefs.idleTimeoutMinutes, idle) {
        if (!idle && prefs.idleTimeoutMinutes > 0) {
            delay(prefs.idleTimeoutMinutes * 60_000L)
            if (System.currentTimeMillis() - lastInteraction >= prefs.idleTimeoutMinutes * 60_000L) {
                idleReturnScreen = screen
                idle = true
            }
        }
    }
    val pal = palette(prefs.theme)
    val dockApps = remember(apps, prefs.favorites) {
        if (prefs.favorites.isEmpty()) apps.take(5)
        else { val lookup = apps.associateBy { it.pkg }; prefs.favorites.mapNotNull { lookup[it] } }
    }

    Box(Modifier.fillMaxSize()
        .onPreviewKeyEvent { interact(); false }
        .pointerInput(Unit) {
            awaitPointerEventScope { while (true) { awaitPointerEvent(); interact() } }
        }) {
        WallpaperLayer(prefs)
        Box(Modifier.fillMaxSize().background(pal.scrim.copy(alpha = 0.08f)))
        HomeScreen(
            prefs, pal, apps, dockApps, loading, error,
            onSettings = { screen = "settings"; settingsPage = "root"; interact() },
            onLibrary = { screen = "library"; interact() },
            onLaunch = { app -> interact(); launchApp(ctx, app) }
        )
        AnimatedVisibility(
            visible = screen == "library" && !idle,
            enter = fadeIn(tween(190)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(170)),
            modifier = Modifier.fillMaxSize()
        ) {
            AppLibrary(apps, loading, error, pal, { screen = "home"; interact() }) { app ->
                screen = "home"; interact(); launchApp(ctx, app)
            }
        }
        AnimatedVisibility(
            visible = screen == "settings" && !idle,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)),
            exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.98f, animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreen(
                prefs, pal, apps, dockApps, settingsPage,
                onPage = { settingsPage = it; interact() },
                onBack = { if (settingsPage != "root") settingsPage = "root" else screen = "home"; interact() },
                onChooseDefault = { requestDefaultHome(ctx) },
                onActivity = interact
            )
        }
        AnimatedVisibility(visible = idle, enter = fadeIn(tween(650)), exit = fadeOut(tween(260)), modifier = Modifier.fillMaxSize()) {
            IdleScreen {
                idle = false
                screen = idleReturnScreen
                interact()
            }
        }
    }
    BackHandler(enabled = idle || screen != "home") {
        if (idle) { idle = false; screen = idleReturnScreen }
        else if (screen == "settings" && settingsPage != "root") settingsPage = "root"
        else screen = "home"
        interact()
    }
}

fun requestDefaultHome(ctx: Context) {
    try { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (ignored: Exception) { Toast.makeText(ctx, "Open Android Settings > Default apps > Home app.", Toast.LENGTH_LONG).show() }
    }
}

@Composable
fun WallpaperLayer(prefs: LauncherPrefs) {
    val ctx = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, prefs.wallpaper, prefs.customUri) {
        val uri = prefs.customUri
        value = if (prefs.wallpaper == "custom" && uri != null) withContext(Dispatchers.IO) { loadCustomWallpaper(ctx, uri) } else null
    }
    if (custom != null) Image(custom!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else {
        val wp = WALLPAPERS.firstOrNull { it.id == prefs.wallpaper } ?: WALLPAPERS[0]
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(wp.top, wp.bottom))))
    }
}

@Composable
fun HomeScreen(
    prefs: LauncherPrefs, pal: Palette, apps: List<AppEntry>, dockApps: List<AppEntry>,
    loading: Boolean, error: String?, onSettings: () -> Unit, onLibrary: () -> Unit, onLaunch: (AppEntry) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        val desktopIcon = (maxHeight * 0.085f).coerceIn(38.dp, if (compact) 56.dp else 72.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 18.dp else 40.dp, vertical = if (compact) 18.dp else 26.dp)) {
            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp), verticalAlignment = Alignment.Top) {
                DesktopShortcut("Settings", "⚙", desktopIcon, pal, onSettings)
                DesktopShortcut("Applications", "▦", desktopIcon, pal, onLibrary)
            }
            Spacer(Modifier.weight(1f))
            val message = when { loading -> "Loading applications…"; error != null -> "Couldn't read the application list"; apps.isEmpty() -> "No launchable applications found"; else -> null }
            if (message != null) BasicText(message, style = TextStyle(color = pal.text, fontSize = 14.sp))
            if (dockApps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(25.dp))
                        .background(Brush.verticalGradient(listOf(pal.panel.copy(alpha = 0.88f), pal.panel.copy(alpha = 0.52f))))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(25.dp))
                        .padding(horizontal = if (compact) 7.dp else 14.dp, vertical = if (compact) 7.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 9.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = (maxHeight * 0.073f).coerceIn(32.dp, if (compact) 45.dp else 60.dp)
                    dockApps.forEach { app -> AppTile(app, icon, false, pal, prefs.intensity) { onLaunch(app) } }
                    Box(Modifier.width(1.dp).height(icon * 0.65f).background(pal.text.copy(alpha = 0.22f)))
                    DesktopShortcut("Applications", "▦", (icon * 0.78f).coerceIn(28.dp, 44.dp), pal, onLibrary, compactLabel = true)
                }
            }
        }
    }
}

@Composable
fun DesktopShortcut(label: String, glyph: String, iconSize: Dp, pal: Palette, onClick: () -> Unit, compactLabel: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (focused || hovered) 1.06f else 1f, tween(150), label = "desktop-shortcut")
    Column(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(17.dp))
        .background(if (focused || hovered) pal.panel.copy(alpha = 0.5f) else Color.Transparent)
        .hoverable(source).clickable(interactionSource = source, indication = null, onClick = onClick)
        .padding(horizontal = 9.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.25f))
            .background(Brush.linearGradient(listOf(Color(0xFF8AB4F8), Color(0xFF6558C7))))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(iconSize * 0.25f)), contentAlignment = Alignment.Center) {
            BasicText(glyph, style = TextStyle(color = Color.White, fontSize = iconSize.value * 0.55f, fontWeight = FontWeight.Medium))
        }
        if (!compactLabel) BasicText(label, Modifier.padding(top = 6.dp), style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun AppTile(app: AppEntry, iconSize: Dp, showLabel: Boolean, pal: Palette, intensity: Float, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (focused || hovered) 1f + 0.07f * intensity else 1f, tween(if (intensity == 0f) 0 else 140), label = "app-icon-scale")
    val icon by produceState<ImageBitmap?>(null, app.pkg) { value = IconCache.get(ctx, app) }
    Column(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp))
        .background(if (focused || hovered) pal.panel.copy(alpha = 0.72f) else Color.Transparent)
        .then(if (focused) Modifier.border(2.dp, pal.ring, RoundedCornerShape(16.dp)) else Modifier)
        .hoverable(source).clickable(interactionSource = source, indication = null, onClick = onClick)
        .padding(horizontal = 7.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Image(icon!!, app.label, Modifier.size(iconSize), contentScale = ContentScale.Fit) else Spacer(Modifier.size(iconSize))
        if (showLabel) BasicText(app.label, Modifier.padding(top = 7.dp), style = TextStyle(color = pal.text, fontSize = 13.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun launchApp(ctx: Context, app: AppEntry) {
    try { ctx.startActivity(app.intent) }
    catch (e: Exception) { Toast.makeText(ctx, "Can't open ${app.label}.", Toast.LENGTH_SHORT).show() }
}

@Composable
fun AppLibrary(apps: List<AppEntry>, loading: Boolean, error: String?, pal: Palette, onDismiss: () -> Unit, onLaunch: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, ignoreCase = true) } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.91f).fillMaxHeight(0.88f).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEE35353B), Color(0xE61D1D22))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(30.dp))) {
            val compact = maxWidth < 600.dp
            Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 27.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        BasicText("Applications", style = TextStyle(color = Color.White, fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.SemiBold))
                        BasicText("${filtered.size} apps", style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp))
                    }
                    Spacer(Modifier.weight(1f))
                    BasicText("×", Modifier.clip(CircleShape).clickable { onDismiss() }.padding(horizontal = 12.dp, vertical = 4.dp), style = TextStyle(color = Color.White, fontSize = 24.sp))
                }
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("⌕", style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), decorationBox = { inner ->
                        Box { if (query.isEmpty()) BasicText("Search applications", style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)); inner() }
                    })
                }
                Spacer(Modifier.height(16.dp))
                when {
                    loading -> BasicText("Loading applications…", style = TextStyle(color = Color.White))
                    error != null -> BasicText("Couldn't load applications", style = TextStyle(color = Color.White))
                    filtered.isEmpty() -> BasicText("No matching applications", style = TextStyle(color = Color.White.copy(alpha = 0.75f)))
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(if (compact) 86.dp else 108.dp), modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.pkg }) { app -> AppTile(app, if (compact) 44.dp else 58.dp, true, pal, 0.5f) { onLaunch(app) } }
                    }
                }
            }
        }
    }
}

@Composable
fun IdleScreen(onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF283A55), Color(0xFF514A75)))).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(time, style = TextStyle(color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp))
            Spacer(Modifier.height(8.dp))
            BasicText(date, style = TextStyle(color = Color.White.copy(alpha = 0.82f), fontSize = 20.sp))
            Spacer(Modifier.height(32.dp))
            BasicText("Touch or press any key to return", style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp))
        }
    }
}

private data class SettingItem(val id: String, val title: String, val subtitle: String, val symbol: String)

@Composable
fun SettingsScreen(
    prefs: LauncherPrefs, pal: Palette, apps: List<AppEntry>, dockApps: List<AppEntry>, page: String,
    onPage: (String) -> Unit, onBack: () -> Unit, onChooseDefault: () -> Unit, onActivity: () -> Unit
) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.setCustom(uri.toString()); onActivity()
        }
    }
    val pages = listOf(
        SettingItem("dock", "Dock", "Favorites and icon sizing", "▥"),
        SettingItem("appearance", "Appearance", "Theme and motion", "◐"),
        SettingItem("wallpaper", "Wallpaper", "Desktop background", "▧"),
        SettingItem("applications", "Applications", "Library and app visibility", "▦"),
        SettingItem("idle", "Idle Screen", "Clock and inactivity timeout", "◷"),
        SettingItem("display", "Display", "Adaptive sizing", "▤"),
        SettingItem("navigation", "Navigation", "Home key and default launcher", "⌘"),
        SettingItem("general", "General", "Launcher setup and information", "⚙")
    )
    val title = pages.firstOrNull { it.id == page }?.title ?: "Settings"
    Box(Modifier.fillMaxSize().background(Color(0xB8171920)), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth(if (maxWidth < 650.dp) 0.96f else 0.78f)
            .fillMaxHeight(if (maxHeight < 500.dp) 0.96f else 0.90f).clip(RoundedCornerShape(28.dp))
            .background(if (prefs.theme == "light") Color(0xFFF2F2F7) else Color(0xFF202126))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(28.dp))) {
            val compact = maxWidth < 650.dp
            Column(Modifier.fillMaxSize().padding(if (compact) 14.dp else 25.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("‹", Modifier.clip(CircleShape).clickable { onBack() }.padding(horizontal = 12.dp, vertical = 3.dp), style = TextStyle(color = Color(0xFF72A4FF), fontSize = 28.sp))
                    Spacer(Modifier.width(7.dp))
                    BasicText(title, style = TextStyle(color = pal.text, fontSize = if (compact) 23.sp else 28.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.weight(1f))
                    BasicText("Settings", style = TextStyle(color = pal.text.copy(alpha = 0.5f), fontSize = 12.sp))
                }
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page == "root") {
                        item { SettingGroup(pal) {
                            pages.forEachIndexed { index, item ->
                                SettingRow(item.symbol, item.title, item.subtitle, pal) { onPage(item.id) }
                                if (index != pages.lastIndex) SettingSeparator(pal)
                            }
                        } }
                        item { SettingGroup(pal) { SettingRow("⌂", "Set as default Home app", "Choose this launcher in Android settings", pal, onClick = onChooseDefault) } }
                    } else {
                        item {
                            when (page) {
                                "dock" -> SettingGroup(pal) {
                                    SettingHeader("Dock favorites", pal)
                                    apps.forEachIndexed { index, app ->
                                        val chosen = app.pkg in dockApps.map { it.pkg }
                                        SettingRow(if (chosen) "●" else "○", app.label, if (chosen) "Shown in Dock" else "Tap to add to Dock", pal, chosen, false) {
                                            prefs.toggleFavorite(app.pkg, if (prefs.favorites.isEmpty()) dockApps.map { it.pkg } else prefs.favorites); onActivity()
                                        }
                                        if (index != apps.lastIndex) SettingSeparator(pal)
                                    }
                                }
                                "appearance" -> SettingGroup(pal) {
                                    SettingHeader("Appearance", pal)
                                    SettingRow("◐", "Light", "Bright translucent surfaces", pal, prefs.theme == "light", false) { prefs.selectTheme("light"); onActivity() }
                                    SettingSeparator(pal)
                                    SettingRow("◑", "Dark", "Deep graphite surfaces", pal, prefs.theme == "dark", false) { prefs.selectTheme("dark"); onActivity() }
                                    SettingSeparator(pal)
                                    SettingHeader("Motion", pal)
                                    listOf("Off", "Subtle", "Normal").forEachIndexed { index, label ->
                                        SettingRow("✧", label, "Animation intensity", pal, prefs.anim == index, false) { prefs.selectAnim(index); onActivity() }
                                        if (index < 2) SettingSeparator(pal)
                                    }
                                }
                                "wallpaper" -> SettingGroup(pal) {
                                    SettingHeader("Desktop background", pal)
                                    WALLPAPERS.forEachIndexed { index, w ->
                                        SettingRow("▧", w.name, "Built-in gradient wallpaper", pal, prefs.wallpaper == w.id, false) { prefs.selectWallpaper(w.id); onActivity() }
                                        if (index != WALLPAPERS.lastIndex) SettingSeparator(pal)
                                    }
                                    if (prefs.customUri != null) {
                                        SettingSeparator(pal)
                                        SettingRow("▧", "My image", "Use your selected image", pal, prefs.wallpaper == "custom", false) { prefs.selectWallpaper("custom"); onActivity() }
                                    }
                                    SettingSeparator(pal)
                                    SettingRow("+", "Choose image…", "Select a background image", pal) { picker.launch(arrayOf("image/*")) }
                                }
                                "applications" -> SettingGroup(pal) {
                                    SettingHeader("Application library", pal)
                                    SettingRow("▦", "Launchable apps", "${apps.size} applications found", pal, showArrow = false) {}
                                    SettingSeparator(pal)
                                    SettingRow("⌕", "Search", "Search is available in the Applications overlay", pal, showArrow = false) {}
                                }
                                "idle" -> SettingGroup(pal) {
                                    SettingHeader("Start idle screen after", pal)
                                    listOf(1, 2, 3, 5, 10, 15).forEachIndexed { index, minutes ->
                                        SettingRow("◷", if (minutes == 1) "1 minute" else "$minutes minutes", if (minutes == 2) "Recommended default" else "Inactivity timeout", pal, prefs.idleTimeoutMinutes == minutes, false) { prefs.selectIdleTimeout(minutes); onActivity() }
                                        if (index < 5) SettingSeparator(pal)
                                    }
                                    SettingSeparator(pal)
                                    SettingRow("◷", "Disabled", "Never show idle screen automatically", pal, prefs.idleTimeoutMinutes == 0, false) { prefs.selectIdleTimeout(0); onActivity() }
                                }
                                "display" -> SettingGroup(pal) {
                                    SettingHeader("Display", pal)
                                    SettingRow("↔", "Adaptive layout", "Dock and app icons scale with screen size", pal, showArrow = false) {}
                                    SettingSeparator(pal)
                                    SettingRow("◫", "TV and mobile", "Responsive portrait and landscape layout", pal, showArrow = false) {}
                                }
                                "navigation" -> SettingGroup(pal) {
                                    SettingHeader("Navigation", pal)
                                    SettingRow("⌂", "Choose default Home app", "Android may ask you to confirm", pal, onClick = onChooseDefault)
                                    SettingSeparator(pal)
                                    SettingRow("↩", "Back navigation", "Back closes overlays and returns to desktop", pal, showArrow = false) {}
                                }
                                else -> SettingGroup(pal) {
                                    SettingHeader("General", pal)
                                    SettingRow("⌂", "Set as default launcher", "Open Android Home app settings", pal, onClick = onChooseDefault)
                                    SettingSeparator(pal)
                                    SettingRow("ⓘ", "Mobo Launcher", "macOS-inspired Android home environment", pal, showArrow = false) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingGroup(pal: Palette, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.panel.copy(alpha = if (pal.text == Color.White) 0.52f else 0.92f))) { content() }
}
@Composable
fun SettingHeader(text: String, pal: Palette) {
    BasicText(text.uppercase(), Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = TextStyle(color = pal.text.copy(alpha = 0.58f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp))
}
@Composable
fun SettingSeparator(pal: Palette) {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(0.6.dp).background(pal.text.copy(alpha = 0.10f)))
}
@Composable
fun SettingRow(
    symbol: String, title: String, subtitle: String, pal: Palette,
    selected: Boolean = false, showArrow: Boolean = true, onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) Color(0xFF3978E8) else pal.text.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            BasicText(symbol, style = TextStyle(color = if (selected) Color.White else pal.text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) BasicText(subtitle, Modifier.padding(top = 3.dp), style = TextStyle(color = pal.text.copy(alpha = 0.58f), fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) BasicText("✓", style = TextStyle(color = Color(0xFF4B91FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        else if (showArrow) BasicText("›", style = TextStyle(color = pal.text.copy(alpha = 0.38f), fontSize = 23.sp))
    }
}
