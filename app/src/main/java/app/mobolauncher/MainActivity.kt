package app.mobolauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = LauncherPrefs(this)
        val tv = detectTv(this)
        setContent { LauncherRoot(prefs, tv, tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++ // refresh the app list when returning (installs/uninstalls)
    }
}

@Composable
fun LauncherRoot(prefs: LauncherPrefs, deviceIsTv: Boolean, tick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(Screen.Home) }

    LaunchedEffect(tick) {
        try {
            apps = withContext(Dispatchers.IO) { loadApps(ctx) }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
        loading = false
    }
    // First run (or after a reset): give the Dock a sensible starting set. Persisted afterwards.
    LaunchedEffect(apps, prefs.dockInit) {
        if (apps.isNotEmpty() && !prefs.dockInit) {
            prefs.dock = defaultDock(apps)
            prefs.dockInit = true
        }
    }

    val dark = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val ui = Ui(dark, ACCENTS[prefs.accent.coerceIn(0, ACCENTS.size - 1)], prefs.glass, intensityOf(prefs.anim))
    val tv = when (prefs.layoutMode) {
        "tv" -> true
        "mobile" -> false
        else -> deviceIsTv
    }

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            prefs.customUri = uri.toString()
            prefs.wallpaper = "custom"
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    // Back on the desktop is not intercepted: as a normal app, Back leaves it.

    val st = AppsState(apps, loading, error)
    val byPkg = apps.associateBy { it.pkg }
    val dockApps = prefs.dock.mapNotNull { byPkg[it] }
    val desktopApps = prefs.desktop.mapNotNull { byPkg[it] }

    BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        val d = Dims(tv, maxWidth < 600.dp, maxHeight < 480.dp)
        WallpaperLayer(prefs)
        Box(Modifier.fillMaxSize().background(ui.scrim))
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = d.pad)) {
            when (screen) {
                Screen.Home -> DesktopScreen(
                    prefs, ui, d, st, dockApps, desktopApps,
                    onOpenLibrary = { screen = Screen.Library },
                    onOpenSettings = { screen = Screen.Settings },
                )
                Screen.Library -> LibraryScreen(prefs, ui, d, st) { screen = Screen.Home }
                Screen.Settings -> SettingsScreen(
                    prefs, ui, d, st, deviceIsTv,
                    onPickImage = {
                        try { picker.launch(arrayOf("image/*")) }
                        catch (e: Exception) { Toast.makeText(ctx, "No image picker on this device.", Toast.LENGTH_LONG).show() }
                    },
                    onBack = { screen = Screen.Home },
                )
            }
        }
    }
}

@Composable
fun WallpaperLayer(prefs: LauncherPrefs) {
    val ctx = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, prefs.wallpaper, prefs.customUri) {
        val uri = prefs.customUri
        value = if (prefs.wallpaper == "custom" && uri.isNotEmpty())
            withContext(Dispatchers.IO) { loadCustomWallpaper(ctx, uri) } else null
    }
    val img = custom
    if (img != null) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        val id = if (WALLPAPERS.any { it.id == prefs.wallpaper }) prefs.wallpaper else "lake_night"
        SceneCanvas(id, Modifier.fillMaxSize())
    }
}
