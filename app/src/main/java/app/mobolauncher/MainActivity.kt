package app.mobolauncher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** State shared between the Activity (system events) and the Compose UI. */
class HostState {
    var tick by mutableIntStateOf(0)        // refresh the app list
    var homeTick by mutableIntStateOf(0)    // Home pressed while running
    var activity by mutableIntStateOf(0)    // user input happened
    var resumed by mutableStateOf(false)
    var idle by mutableStateOf(false)
    var isDefault by mutableStateOf(false)
}

enum class Overlay { None, Library, Settings }

class MainActivity : ComponentActivity() {
    private val host = HostState()
    private var swallowTouch = false
    private var swallowKey = -1
    private var lastBump = 0L

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        host.isDefault = isDefaultLauncher(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = LauncherPrefs(this)
        val tv = detectTv(this)
        host.isDefault = isDefaultLauncher(this)
        setContent { LauncherRoot(prefs, host, tv) { requestDefault() } }
    }

    override fun onResume() {
        super.onResume()
        host.resumed = true
        host.tick = host.tick + 1
        host.activity = host.activity + 1
        host.isDefault = isDefaultLauncher(this)
    }

    override fun onPause() {
        super.onPause()
        host.resumed = false
        host.idle = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.CATEGORY_HOME in (intent.categories ?: emptySet<String>())) {
            host.idle = false
            host.homeTick = host.homeTick + 1
            host.activity = host.activity + 1
        }
    }

    /** Restart the idle countdown (throttled so pointer movement is cheap). */
    private fun bump() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBump > 400L) {
            lastBump = now
            host.activity = host.activity + 1
        }
    }

    // Any touch, key or pointer movement counts as activity. The first input that wakes
    // the idle screen is swallowed so it cannot launch an app by accident.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        bump()
        if (host.idle) {
            host.idle = false
            swallowTouch = true
        }
        if (swallowTouch) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                swallowTouch = false
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        bump()
        if (host.idle) {
            host.idle = false
            swallowKey = event.keyCode
            return true
        }
        if (swallowKey == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) swallowKey = -1
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        bump()
        if (host.idle) {
            host.idle = false
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    /** Opens the system flow for choosing the Home app. Returns a hint if no flow was available. */
    private fun requestDefault(): String? {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return null
                }
            }
        } catch (e: Exception) { }
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) {
            try {
                startActivity(Intent(action))
                return null
            } catch (e: Exception) { }
        }
        return "Press the Home button, choose Mobo Launcher, then Always."
    }
}

@Composable
fun LauncherRoot(
    prefs: LauncherPrefs, host: HostState, deviceIsTv: Boolean, onRequestDefault: () -> String?,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(Overlay.None) }

    var hudText by remember { mutableStateOf("") }
    var hudVisible by remember { mutableStateOf(false) }
    var hudCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudCount) {
        if (hudCount > 0) {
            hudVisible = true
            delay(2000)
            hudVisible = false
        }
    }
    val showHud: (String) -> Unit = { msg ->
        hudText = msg
        hudCount = hudCount + 1
    }

    LaunchedEffect(host.tick) {
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
    // Home button while the launcher is already open: back to the plain desktop.
    LaunchedEffect(host.homeTick) {
        if (host.homeTick > 0) overlay = Overlay.None
    }
    // Idle screen: counts down only while the launcher is in front; any input restarts it.
    LaunchedEffect(host.activity, host.resumed, host.idle, prefs.idleMinutes) {
        if (host.resumed && !host.idle && prefs.idleMinutes > 0) {
            delay(prefs.idleMinutes * 60_000L)
            host.idle = true
        }
    }

    val dark = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val ui = Ui(
        dark, ACCENTS[prefs.accent.coerceIn(0, ACCENTS.size - 1)], prefs.glass,
        intensityOf(prefs.anim), prefs.focusRing,
    )
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

    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
    // As the default Home app, Back on the plain desktop does nothing.
    BackHandler(enabled = overlay == Overlay.None && host.isDefault) { }

    val st = AppsState(apps, loading, error)
    val byPkg = apps.associateBy { it.pkg }
    val dockApps = prefs.dock.mapNotNull { byPkg[it] }
    val desktopApps = prefs.desktop.mapNotNull { byPkg[it] }

    CompositionLocalProvider(LocalHud provides showHud) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { ev ->
                if (ev.key == Key.Escape && ev.type == KeyEventType.KeyUp && overlay != Overlay.None) {
                    overlay = Overlay.None
                    true
                } else {
                    false
                }
            },
        ) {
            val d = Dims(tv, maxWidth.value, maxHeight.value, prefs.uiScale)
            val dur = if (ui.intensity == 0f) 0 else 1
            val blurOn = prefs.libBlur && overlay != Overlay.None
            val blurRadius by animateDpAsState(if (blurOn) 16.dp else 0.dp, tween(260 * dur), label = "blur")

            // Desktop (kept alive under overlays so its state is preserved)
            Box(
                Modifier.fillMaxSize().then(
                    if (blurRadius > 0.dp) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier
                ),
            ) {
                WallpaperLayer(prefs)
                Box(Modifier.fillMaxSize().background(ui.scrim))
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = d.pad)
                        .focusProperties { canFocus = overlay == Overlay.None },
                ) {
                    DesktopScreen(
                        prefs, ui, d, st, overlay == Overlay.None, dockApps, desktopApps,
                        onOpenLibrary = { overlay = Overlay.Library },
                        onOpenSettings = { overlay = Overlay.Settings },
                    )
                }
            }

            // Dim layer (tap outside a window to close it)
            AnimatedVisibility(
                visible = overlay != Overlay.None,
                enter = fadeIn(tween(220 * dur)), exit = fadeOut(tween(180 * dur)),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = if (dark) 0.40f else 0.25f))
                        .pointerInput(Unit) { detectTapGestures { overlay = Overlay.None } },
                )
            }

            // Windows: Applications and Settings
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = d.pad, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val enter = fadeIn(tween(240 * dur)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(280 * dur, easing = FastOutSlowInEasing))
                val exit = fadeOut(tween(160 * dur)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180 * dur))
                val heightFraction = if (d.short) 1f else 0.88f
                AnimatedVisibility(visible = overlay == Overlay.Library, enter = enter, exit = exit) {
                    Box(
                        Modifier.fillMaxWidth(if (d.compact) 1f else 0.84f).widthIn(max = 1100.dp)
                            .fillMaxHeight(heightFraction)
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        LibraryPanel(prefs, ui, d, st) { overlay = Overlay.None }
                    }
                }
                AnimatedVisibility(visible = overlay == Overlay.Settings, enter = enter, exit = exit) {
                    Box(
                        Modifier.fillMaxWidth(if (d.compact) 1f else 0.84f).widthIn(max = 760.dp)
                            .fillMaxHeight(heightFraction)
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        SettingsPanel(
                            prefs, ui, d, st, deviceIsTv, host.isDefault,
                            onPickImage = {
                                try {
                                    picker.launch(arrayOf("image/*"))
                                } catch (e: Exception) {
                                    showHud("No image picker on this device.")
                                }
                            },
                            onSetDefault = {
                                val hint = onRequestDefault()
                                if (hint != null) showHud(hint)
                            },
                            onPreviewIdle = {
                                overlay = Overlay.None
                                host.idle = true
                            },
                            onClose = { overlay = Overlay.None },
                        )
                    }
                }
            }

            // Messages
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(top = 64.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedVisibility(visible = hudVisible, enter = fadeIn(tween(160 * dur)), exit = fadeOut(tween(220 * dur))) {
                    HudPill(hudText, ui, d)
                }
            }

            // Idle screen
            AnimatedVisibility(
                visible = host.idle,
                enter = fadeIn(tween(700 * dur)) + scaleIn(initialScale = 1.04f, animationSpec = tween(700 * dur)),
                exit = fadeOut(tween(240 * dur)),
            ) {
                IdleScreen(prefs, ui, d)
            }
        }
    }
}

@Composable
fun WallpaperLayer(prefs: LauncherPrefs) {
    val ctx = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, prefs.wallpaper, prefs.customUri) {
        val uri = prefs.customUri
        value = if (prefs.wallpaper == "custom" && uri.isNotEmpty()) {
            withContext(Dispatchers.IO) { loadCustomWallpaper(ctx, uri) }
        } else {
            null
        }
    }
    val img = custom
    if (img != null) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        val id = if (WALLPAPERS.any { it.id == prefs.wallpaper }) prefs.wallpaper else "wave"
        SceneCanvas(id, Modifier.fillMaxSize())
    }
}
