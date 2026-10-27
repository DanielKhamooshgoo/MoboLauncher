package app.mobolauncher

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// ---------------------------------------------------------------- apps

data class AppEntry(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val info: ResolveInfo,
)

/** Apps that expose a TV or regular launcher entry (limited by the manifest <queries>). */
fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val found = LinkedHashMap<String, ResolveInfo>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg != ctx.packageName && !found.containsKey(pkg)) found[pkg] = ri
        }
    }
    return found.values.map { ri ->
        val ai = ri.activityInfo
        val launch = Intent(Intent.ACTION_MAIN).apply {
            setClassName(ai.packageName, ai.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        AppEntry(ri.loadLabel(pm).toString(), ai.packageName, launch, ri)
    }.sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppEntry, prefs: LauncherPrefs) {
    try {
        ctx.startActivity(app.intent)
        prefs.bumpUsage(app.pkg)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open " + app.label + ".", Toast.LENGTH_SHORT).show()
    }
}

fun defaultDock(apps: List<AppEntry>): List<String> {
    val preferred = listOf(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.google.android.youtube",
        "com.android.chrome", "com.android.vending",
    )
    val present = apps.map { it.pkg }.toSet()
    val result = preferred.filter { it in present }.toMutableList()
    for (a in apps) {
        if (result.size >= 5) break
        if (a.pkg !in result) result.add(a.pkg)
    }
    return result
}

class AppIcon(val bitmap: ImageBitmap, val adaptive: Boolean)

object IconCache {
    private val map = ConcurrentHashMap<String, AppIcon>()

    suspend fun get(ctx: Context, app: AppEntry): AppIcon {
        map[app.pkg]?.let { return it }
        return withContext(Dispatchers.Default) {
            var adaptive = false
            val raw: Bitmap = try {
                val d = app.info.loadIcon(ctx.packageManager)
                adaptive = Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable
                d.toBitmap(192, 192, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            val icon = AppIcon(raw.asImageBitmap(), adaptive)
            map[app.pkg] = icon
            icon
        }
    }
}

// ---------------------------------------------------------------- preferences

/** A Compose-observable value that is written to SharedPreferences on every change. */
class StatePref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    private val default: T,
    load: (SharedPreferences, String, T) -> T,
    private val save: (SharedPreferences.Editor, String, T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    private val state = mutableStateOf(load(sp, key, default))

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        val editor = sp.edit()
        save(editor, key, value)
        editor.apply()
    }

    fun reset() { state.value = default }
}

class LauncherPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val usage = ctx.getSharedPreferences("launcher_usage", Context.MODE_PRIVATE)
    private val all = mutableListOf<StatePref<*>>()

    private fun <T> register(p: StatePref<T>): StatePref<T> { all.add(p); return p }

    private fun str(key: String, def: String): StatePref<String> = register(
        StatePref(sp, key, def, { s, k, d -> s.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })
    )

    private fun int(key: String, def: Int): StatePref<Int> = register(
        StatePref(sp, key, def, { s, k, d -> s.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })
    )

    private fun bool(key: String, def: Boolean): StatePref<Boolean> = register(
        StatePref(sp, key, def, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })
    )

    private fun list(key: String): StatePref<List<String>> = register(
        StatePref<List<String>>(
            sp, key, emptyList(),
            { s, k, _ -> (s.getString(k, "") ?: "").split(",").filter { it.isNotBlank() } },
            { e, k, v -> e.putString(k, v.joinToString(",")) },
        )
    )

    // Appearance
    var theme by str("theme", "dark")                 // auto | light | dark
    var wallpaper by str("wallpaper", "lake_night")   // scene id or "custom"
    var customUri by str("customUri", "")
    var accent by int("accent", 0)
    var glass by int("glass", 1)                      // 0 subtle, 1 regular, 2 vivid
    var iconStyle by str("iconStyle", "plate")        // plate | original
    var iconSize by int("iconSize", 1)                // 0 small, 1 medium, 2 large
    var anim by int("anim", 1)                        // 0 off, 1 low, 2 normal
    // General
    var layoutMode by str("layoutMode", "auto")       // auto | tv | mobile
    var clock24 by bool("clock24", true)
    // Dock
    var dock by list("dock")
    var dockInit by bool("dockInit", false)
    var dockSize by int("dockSize", 1)
    var dockMagnify by bool("dockMagnify", true)
    var dockLabels by bool("dockLabels", false)
    // Desktop
    var showClock by bool("showClock", true)
    var desktop by list("desktop")
    // Library
    var libSort by str("libSort", "az")               // az | used
    var libLabels by bool("libLabels", true)
    var hidden by list("hidden")

    var usageTick by mutableIntStateOf(0)

    fun usageOf(pkg: String): Int = usage.getInt(pkg, 0)

    fun bumpUsage(pkg: String) {
        usage.edit().putInt(pkg, usage.getInt(pkg, 0) + 1).apply()
        usageTick = usageTick + 1
    }

    fun resetAll() {
        all.forEach { it.reset() }
        sp.edit().clear().apply()
        usage.edit().clear().apply()
        usageTick = usageTick + 1
    }
}

// ---------------------------------------------------------------- look

val ACCENTS = listOf(
    Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF375F),
    Color(0xFFFF9F0A), Color(0xFF30D158), Color(0xFF8E8E93),
)
val ACCENT_NAMES = listOf("Blue", "Purple", "Pink", "Orange", "Green", "Graphite")

data class WallpaperDef(val id: String, val name: String)

val WALLPAPERS = listOf(
    WallpaperDef("lake_night", "Lakeside Night"),
    WallpaperDef("lake_day", "Lakeside Day"),
    WallpaperDef("bloom", "Bloom"),
    WallpaperDef("graphite", "Graphite"),
)

fun intensityOf(anim: Int): Float = when (anim) {
    0 -> 0f
    2 -> 1f
    else -> 0.6f
}

class Ui(val dark: Boolean, val accent: Color, glass: Int, val intensity: Float) {
    private val k: Float = when (glass) {
        0 -> 0.7f
        2 -> 1.4f
        else -> 1f
    }
    val text: Color = if (dark) Color.White else Color(0xFF1D1D1F)
    val subtext: Color = text.copy(alpha = 0.62f)
    val ring: Color = text.copy(alpha = 0.95f)
    val glassTop: Color = Color.White.copy(alpha = ((if (dark) 0.20f else 0.50f) * k).coerceAtMost(0.9f))
    val glassBottom: Color = Color.White.copy(alpha = ((if (dark) 0.08f else 0.28f) * k).coerceAtMost(0.9f))
    val strokeTop: Color = Color.White.copy(alpha = if (dark) 0.40f else 0.80f)
    val strokeBottom: Color = Color.White.copy(alpha = if (dark) 0.08f else 0.25f)
    val scrim: Color = if (dark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f)
    val selected: Color = accent
}

class Dims(val tv: Boolean, val compact: Boolean, val short: Boolean) {
    val fs: Float = if (tv) 1.25f else if (compact || short) 0.92f else 1f
    val icon: Dp = (if (tv) 76f else if (compact) 56f else if (short) 48f else 64f).dp
    val dockIcon: Dp = (if (tv) 68f else if (compact) 48f else if (short) 44f else 56f).dp
    val pad: Dp = (if (tv) 48f else if (compact) 14f else 24f).dp
}

fun detectTv(ctx: Context): Boolean {
    val pm = ctx.packageManager
    val modeService = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val tvMode = modeService?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    return tvMode ||
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

fun loadCustomWallpaper(ctx: Context, uri: String): ImageBitmap? = try {
    val u = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?.asImageBitmap()
} catch (e: Exception) {
    null
}
