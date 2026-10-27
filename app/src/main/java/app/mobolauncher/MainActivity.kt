package app.mobolauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LauncherPrefs(this)
        setContent { LauncherApp(prefs, tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++ // refresh app list when returning (installs/uninstalls)
    }
}

@Composable
fun LauncherApp(prefs: LauncherPrefs, tick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("home") }

    LaunchedEffect(tick) {
        try {
            apps = withContext(Dispatchers.IO) { loadApps(ctx) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
        loading = false
    }

    val pal = palette(prefs.theme)
    val dockApps = remember(apps, prefs.favorites) {
        if (prefs.favorites.isEmpty()) apps.take(6)
        else { val lookup = apps.associateBy { it.pkg }; prefs.favorites.mapNotNull { lookup[it] } }
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(prefs)
        Box(Modifier.fillMaxSize().background(pal.scrim))
        if (screen == "settings") {
            SettingsScreen(prefs, pal, apps, dockApps) { screen = "home" }
        } else {
            HomeScreen(prefs, pal, apps, dockApps, loading, error) { screen = "settings" }
        }
    }
    // Back at Home is intentionally not intercepted: while this is a normal app, Back exits it.
    BackHandler(enabled = screen == "settings") { screen = "home" }
}

@Composable
fun WallpaperLayer(prefs: LauncherPrefs) {
    val ctx = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, prefs.wallpaper, prefs.customUri) {
        val uri = prefs.customUri
        value = if (prefs.wallpaper == "custom" && uri != null)
            withContext(Dispatchers.IO) { loadCustomWallpaper(ctx, uri) } else null
    }
    val img = custom
    if (img != null) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        val wp = WALLPAPERS.firstOrNull { it.id == prefs.wallpaper } ?: WALLPAPERS[0]
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(wp.top, wp.bottom))))
    }
}

@Composable
fun HomeScreen(
    prefs: LauncherPrefs, pal: Palette, apps: List<AppEntry>, dockApps: List<AppEntry>,
    loading: Boolean, error: String?, onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Apps", style = TextStyle(color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Chip("Settings", false, pal, prefs.intensity, onSettings)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val msg = when {
                loading -> "Loading apps…"
                error != null -> "Couldn't read the app list: $error"
                apps.isEmpty() -> "No launchable apps found."
                else -> null
            }
            if (msg != null) {
                BasicText(msg, style = TextStyle(color = pal.text, fontSize = 20.sp, textAlign = TextAlign.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(apps, key = { it.pkg }) { app ->
                        AppTile(app, 72.dp, true, pal, prefs.intensity) { launchApp(ctx, app) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (dockApps.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(28.dp)).background(pal.panel).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                dockApps.forEach { app ->
                    AppTile(app, 64.dp, false, pal, prefs.intensity) { launchApp(ctx, app) }
                }
            }
        }
    }
}

fun launchApp(ctx: android.content.Context, app: AppEntry) {
    try {
        ctx.startActivity(app.intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AppTile(app: AppEntry, iconSize: androidx.compose.ui.unit.Dp, showLabel: Boolean, pal: Palette, intensity: Float, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val active = focused || hovered
    val scale by animateFloatAsState(
        if (active) 1f + 0.10f * intensity else 1f,
        tween(if (intensity == 0f) 0 else 120), label = "tile-scale",
    )
    val icon by produceState<ImageBitmap?>(null, app.pkg) { value = IconCache.get(ctx, app) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (active) pal.panel else Color.Transparent)
            .then(if (focused) Modifier.border(3.dp, pal.ring, shape) else Modifier)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bmp = icon
        if (bmp != null) Image(bmp, app.label, Modifier.size(iconSize)) else Spacer(Modifier.size(iconSize))
        if (showLabel) {
            BasicText(
                app.label, Modifier.padding(top = 6.dp),
                style = TextStyle(color = pal.text, fontSize = 14.sp, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, pal: Palette, intensity: Float, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        if (focused || hovered) 1f + 0.05f * intensity else 1f,
        tween(if (intensity == 0f) 0 else 120), label = "chip-scale",
    )
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) pal.selected else pal.panel)
            .then(if (focused) Modifier.border(3.dp, pal.ring, shape) else Modifier)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        BasicText(text, style = TextStyle(color = pal.text, fontSize = 16.sp))
    }
}

@Composable
fun SettingsScreen(
    prefs: LauncherPrefs, pal: Palette, apps: List<AppEntry>, dockApps: List<AppEntry>, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            prefs.setCustom(uri.toString())
        }
    }
    val favBase = if (prefs.favorites.isEmpty()) dockApps.map { it.pkg } else prefs.favorites
    val i = prefs.intensity

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Chip("Back", false, pal, i, onBack)
                BasicText("Settings", style = TextStyle(color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        item { SectionTitle("Wallpaper", pal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WALLPAPERS.forEach { w -> Chip(w.name, prefs.wallpaper == w.id, pal, i) { prefs.selectWallpaper(w.id) } }
                if (prefs.customUri != null) {
                    Chip("My image", prefs.wallpaper == "custom", pal, i) { prefs.selectWallpaper("custom") }
                }
                Chip("Choose image…", false, pal, i) {
                    try { picker.launch(arrayOf("image/*")) }
                    catch (e: Exception) { Toast.makeText(ctx, "No image picker on this device.", Toast.LENGTH_LONG).show() }
                }
            }
        }
        item { SectionTitle("Theme", pal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Dark", prefs.theme == "dark", pal, i) { prefs.selectTheme("dark") }
                Chip("Light", prefs.theme == "light", pal, i) { prefs.selectTheme("light") }
            }
        }
        item { SectionTitle("Animation", pal) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Off", prefs.anim == 0, pal, i) { prefs.selectAnim(0) }
                Chip("Low", prefs.anim == 1, pal, i) { prefs.selectAnim(1) }
                Chip("Normal", prefs.anim == 2, pal, i) { prefs.selectAnim(2) }
            }
        }
        item { SectionTitle("Dock apps (select to add or remove)", pal) }
        items(apps.size) { idx ->
            val app = apps[idx]
            Chip(app.label, app.pkg in favBase, pal, i) { prefs.toggleFavorite(app.pkg, favBase) }
        }
    }
}

@Composable
fun SectionTitle(text: String, pal: Palette) {
    BasicText(text, Modifier.padding(top = 10.dp), style = TextStyle(color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Medium))
}
