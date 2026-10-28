package app.mobolauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val info: ResolveInfo,
)

/** Lists apps that expose a TV or regular launcher entry (limited by the manifest <queries>). */
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

object IconCache {
    private val map = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun get(ctx: Context, app: AppEntry): ImageBitmap {
        map[app.pkg]?.let { return it }
        return withContext(Dispatchers.Default) {
            val raw: Bitmap = try {
                app.info.loadIcon(ctx.packageManager).toBitmap(160, 160, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            val bmp = raw.asImageBitmap()
            map[app.pkg] = bmp
            bmp
        }
    }
}

data class Wallpaper(val id: String, val name: String, val top: Color, val bottom: Color)

val WALLPAPERS = listOf(
    Wallpaper("aurora", "macOS Blue", Color(0xFF80C7ED), Color(0xFF073BC0)),
    Wallpaper("dusk", "Dusk", Color(0xFF2B1B3D), Color(0xFFD9774B)),
    Wallpaper("graphite", "Graphite", Color(0xFF151515), Color(0xFF3A3A3C)),
    Wallpaper("sand", "Sand", Color(0xFFE9DFCF), Color(0xFFB8A38A)),
)

class Palette(
    val text: Color,
    val panel: Color,
    val selected: Color,
    val ring: Color,
    val scrim: Color,
)

fun palette(theme: String): Palette =
    if (theme == "light") Palette(
        text = Color(0xFF1C1C1E),
        panel = Color.White.copy(alpha = 0.35f),
        selected = Color.Black.copy(alpha = 0.18f),
        ring = Color(0xFF1C1C1E),
        scrim = Color.White.copy(alpha = 0.12f),
    ) else Palette(
        text = Color.White,
        panel = Color.White.copy(alpha = 0.12f),
        selected = Color.White.copy(alpha = 0.30f),
        ring = Color.White,
        scrim = Color.Black.copy(alpha = 0.25f),
    )

class LauncherPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    var wallpaper by mutableStateOf(sp.getString("wallpaper", "aurora") ?: "aurora"); private set
    var customUri by mutableStateOf(sp.getString("customUri", null)); private set
    var theme by mutableStateOf(sp.getString("theme", "dark") ?: "dark"); private set
    var anim by mutableStateOf(sp.getInt("anim", 1)); private set
    var idleTimeoutMinutes by mutableStateOf(sp.getInt("idleTimeoutMinutes", 2)); private set
    var favorites by mutableStateOf(
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }
    ); private set

    /** 0 = off, 1 = low, 2 = normal. */
    val intensity: Float get() = when (anim) { 0 -> 0f; 2 -> 1f; else -> 0.6f }

    fun selectIdleTimeout(minutes: Int) { idleTimeoutMinutes = minutes; sp.edit().putInt("idleTimeoutMinutes", minutes).apply() }
    fun selectWallpaper(id: String) { wallpaper = id; sp.edit().putString("wallpaper", id).apply() }
    fun setCustom(uri: String) {
        customUri = uri; wallpaper = "custom"
        sp.edit().putString("customUri", uri).putString("wallpaper", "custom").apply()
    }
    fun selectTheme(t: String) { theme = t; sp.edit().putString("theme", t).apply() }
    fun selectAnim(a: Int) { anim = a; sp.edit().putInt("anim", a).apply() }
    fun toggleFavorite(pkg: String, base: List<String>) {
        favorites = if (pkg in base) base - pkg else base + pkg
        sp.edit().putString("favs", favorites.joinToString(",")).apply()
    }
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
