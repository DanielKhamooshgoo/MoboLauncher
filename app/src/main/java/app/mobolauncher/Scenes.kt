package app.mobolauncher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.sin

// Original, procedurally drawn wallpapers (no third-party artwork).

private fun ridgePath(w: Float, horizon: Float, base: Float, amp: Float, phase: Float, freq: Float): Path {
    val p = Path()
    p.moveTo(0f, horizon)
    val steps = 72
    for (i in 0..steps) {
        val x = w * i / steps
        val t = x / w
        val n = sin(t * freq + phase) * 0.55f +
            sin(t * freq * 2.3f + phase * 1.7f) * 0.30f +
            sin(t * freq * 5.1f + phase * 0.6f) * 0.15f
        p.lineTo(x, base - amp * (0.5f + 0.5f * n))
    }
    p.lineTo(w, horizon)
    p.close()
    return p
}

private fun DrawScope.drawLake(night: Boolean) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.64f
    val sky = if (night) {
        listOf(Color(0xFF0A0E2E), Color(0xFF2B2F72), Color(0xFFB0689B))
    } else {
        listOf(Color(0xFF7DB8FF), Color(0xFFBFD9FF), Color(0xFFFFD6C0))
    }
    drawRect(Brush.verticalGradient(sky, startY = 0f, endY = horizon), size = Size(w, horizon))
    val glow = if (night) Color(0xFFFFE9C4) else Color(0xFFFFF3D6)
    val gc = Offset(w * 0.72f, h * 0.26f)
    val gr = h * 0.16f
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), center = gc, radius = gr), radius = gr, center = gc)
    val ridges = listOf(
        ridgePath(w, horizon, h * 0.50f, h * 0.16f, 0.6f, 5f) to (if (night) Color(0xFF454A98) else Color(0xFF9DB9EE)),
        ridgePath(w, horizon, h * 0.57f, h * 0.14f, 2.4f, 6.5f) to (if (night) Color(0xFF2E3270) else Color(0xFF6F93D6)),
        ridgePath(w, horizon, h * 0.63f, h * 0.10f, 4.1f, 8f) to (if (night) Color(0xFF1A1D4A) else Color(0xFF4A6FB8)),
    )
    ridges.forEach { (path, color) -> drawPath(path, color) }
    val lake = if (night) listOf(Color(0xFF2A2C63), Color(0xFF0A0C26)) else listOf(Color(0xFF9CC4FF), Color(0xFF3F6BB5))
    drawRect(Brush.verticalGradient(lake, startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    clipRect(top = horizon) {
        withTransform({ scale(1f, -1f, Offset(0f, horizon)) }) {
            ridges.forEach { (path, color) -> drawPath(path, color.copy(alpha = 0.28f)) }
        }
    }
}

private fun DrawScope.drawBloom() {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFF14123A), Color(0xFF2B1655), Color(0xFF0E2B5C)),
            start = Offset(0f, 0f), end = Offset(w, h),
        )
    )
    fun blob(cx: Float, cy: Float, r: Float, c: Color) {
        val center = Offset(cx, cy)
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = 0.85f), Color.Transparent), center = center, radius = r),
            radius = r, center = center,
        )
    }
    blob(w * 0.20f, h * 0.30f, h * 0.70f, Color(0xFFFF4D8D))
    blob(w * 0.80f, h * 0.25f, h * 0.65f, Color(0xFF5B6CFF))
    blob(w * 0.55f, h * 0.85f, h * 0.75f, Color(0xFF29D3C7))
}

@Composable
fun SceneCanvas(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (id) {
            "lake_day" -> drawLake(false)
            "lake_night" -> drawLake(true)
            "bloom" -> drawBloom()
            else -> drawRect(Brush.verticalGradient(listOf(Color(0xFF101012), Color(0xFF3A3A3F))))
        }
    }
}
